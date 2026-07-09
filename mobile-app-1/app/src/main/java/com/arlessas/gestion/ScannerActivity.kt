package com.arlessas.gestion

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiDisplayConfig.wrapContext(newBase))
    }

    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val isScanning = AtomicBoolean(true)
    private val resultadoEnviado = AtomicBoolean(false)
    private var isFlashOn = false
    private var modoTaller = false
    private lateinit var previewView: PreviewView
    private lateinit var flashBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modoTaller = intent.getBooleanExtra(EXTRA_MODO_TALLER, false)

        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView)

        val hint = TextView(this).apply {
            text = if (modoTaller) {
                "Apunta al QR — captura automatica"
            } else {
                "Apunta al codigo — captura automatica"
            }
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor("#99000000".toColorInt())
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            },
        )

        val overlay = View(this).apply {
            val size = (resources.displayMetrics.widthPixels * 0.7).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setStroke(dp(3), Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
        }
        root.addView(overlay)

        flashBtn = Button(this).apply {
            text = "Linterna"
            isEnabled = false
            alpha = 0.6f
            setTextColor(Color.WHITE)
            setBackgroundColor("#80000000".toColorInt())
            setOnClickListener { toggleFlash() }
        }
        root.addView(
            flashBtn,
            FrameLayout.LayoutParams(dp(120), dp(48)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, dp(64))
            },
        )

        setContentView(root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera(previewView)
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun actualizarBotonLinterna() {
        val cam = camera
        val disponible = cam?.cameraInfo?.hasFlashUnit() == true
        flashBtn.isEnabled = disponible
        flashBtn.alpha = if (disponible) 1f else 0.45f
        flashBtn.text = if (isFlashOn) "Linterna ON" else "Linterna"
    }

    private fun toggleFlash() {
        val cam = camera
        if (cam == null) {
            Toast.makeText(this, "Camara aun no lista", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Linterna no disponible en este equipo", Toast.LENGTH_SHORT).show()
            return
        }

        val nuevoEstado = !isFlashOn
        try {
            val future = cam.cameraControl.enableTorch(nuevoEstado)
            future.addListener({
                try {
                    future.get()
                    isFlashOn = nuevoEstado
                    runOnUiThread { actualizarBotonLinterna() }
                } catch (_: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "No se pudo activar la linterna", Toast.LENGTH_SHORT).show()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {
            Toast.makeText(this, "Linterna no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun apagarLinternaSilenciosa() {
        val cam = camera ?: return
        if (!isFlashOn) return
        try {
            cam.cameraControl.enableTorch(false)
        } catch (_: Exception) {
        } finally {
            isFlashOn = false
        }
    }

    private fun detenerCamaraSegura() {
        isScanning.set(false)
        try {
            imageAnalysis?.clearAnalyzer()
        } catch (_: Exception) {
        }
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        apagarLinternaSilenciosa()
        camera = null
        imageAnalysis = null
    }

    private fun entregarResultado(valor: String) {
        if (valor.isBlank() || !resultadoEnviado.compareAndSet(false, true)) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            detenerCamaraSegura()
            try {
                vibrarCaptura()
            } catch (_: Exception) {
            }
            setResult(RESULT_OK, Intent().putExtra(SCAN_RESULT, valor))
            finish()
        }
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                if (isFinishing || isDestroyed) return@addListener
                val provider: ProcessCameraProvider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis = analysis

                val scanner = BarcodeScanning.getClient()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isScanning.get() || resultadoEnviado.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    @androidx.camera.core.ExperimentalGetImage
                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (!isScanning.get() || resultadoEnviado.get()) return@addOnSuccessListener
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue?.trim().orEmpty()
                                if (rawValue.isBlank()) continue
                                val valorFinal = resolverValorEscaneado(rawValue)
                                if (valorFinal.isBlank()) continue
                                entregarResultado(valorFinal)
                                break
                            }
                        }
                        .addOnFailureListener {
                            // Ignorar fallos puntuales del lector.
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(this, cameraSelector, preview, analysis)
                    runOnUiThread { actualizarBotonLinterna() }
                } catch (_: Exception) {
                    Toast.makeText(this, "Error al iniciar camara", Toast.LENGTH_SHORT).show()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun resolverValorEscaneado(raw: String): String {
        if (!modoTaller) return raw
        val numeros = TallerCanonicos.extraerNumerosQr(raw)
        if (numeros.isNotBlank()) return numeros
        val codigo = TallerCanonicos.normalizarCodigo(raw)
        if (codigo.startsWith("SINQR") || codigo.startsWith("QR-")) return codigo
        // Fallback: algunos QR traen solo texto con digitos mezclados.
        return raw.filter { it.isDigit() }.ifBlank { raw }
    }

    @Suppress("DEPRECATION")
    private fun vibrarCaptura() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(120)
        }
    }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onPause() {
        apagarLinternaSilenciosa()
        super.onPause()
    }

    override fun onDestroy() {
        detenerCamaraSegura()
        try {
            cameraExecutor.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(previewView)
            } else {
                Toast.makeText(this, "Permisos de camara denegados", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_MODO_TALLER = "MODO_TALLER"
        const val SCAN_RESULT = "SCAN_RESULT"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}