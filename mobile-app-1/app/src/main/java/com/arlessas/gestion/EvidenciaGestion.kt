package com.arlessas.gestion

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import android.app.Dialog
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.util.UUID

/**
 * Gestión de evidencia fotográfica para entregas de dotación y EPP.
 * Centraliza la captura, compresión y subida a Firebase Storage.
 */

internal fun MainActivity.capturarEvidencia(onResult: (String) -> Unit) {
    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 101)
        return
    }

    try {
        // Usar cacheDir (interno) es más seguro que externalCacheDir
        val photoFile = File(cacheDir, "temp_evidencia_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
        
        fotoUriActual = uri
        onEvidenciaCapturada = onResult
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    } catch (e: Exception) {
        android.util.Log.e("ArlesGestión", "Error abriendo cámara", e)
        Toast.makeText(this, "Fallo al abrir cámara: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.subirEvidenciaCloud(uriString: String, folder: String, onUrl: (String) -> Unit) {
    try {
        val carpeta = carpetaEvidencia(folder)
        val uri = uriString.toUri()
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            runOnUiThread { Toast.makeText(this, "Error: No se pudo leer la foto", Toast.LENGTH_SHORT).show() }
            onUrl("")
            return
        }
        inputStream.close()

        // Usar options para evitar OOM si la imagen es gigante
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val headerStream = contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(headerStream, null, options)
        headerStream?.close()

        options.inSampleSize = calcularInSampleSize(options, 1280, 1280)
        options.inJustDecodeBounds = false
        
        val bitmapStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(bitmapStream, null, options)
        bitmapStream?.close()
        
        if (bitmap == null) {
            runOnUiThread { Toast.makeText(this, "Error: Imagen corrupta", Toast.LENGTH_SHORT).show() }
            onUrl("")
            return
        }
        
        // 1. Compresión optimizada
        val data = comprimirEvidenciaJpeg(bitmap)
        
        if (data.isEmpty()) {
            runOnUiThread { Toast.makeText(this, "Error: Datos vacíos", Toast.LENGTH_SHORT).show() }
            onUrl("")
            return
        }

        // 2. Subida directa con éxito confirmado
        val path = "evidencias/$carpeta/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(path)
        
        ref.putBytes(data)
            .addOnSuccessListener { 
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    onUrl(downloadUri.toString())
                }.addOnFailureListener { e ->
                    android.util.Log.e("ArlesGestión", "Error obteniendo URL: ${e.message}")
                    onUrl("")
                }
            }
            .addOnFailureListener { e ->
                val errorMsg = e.localizedMessage ?: e.message ?: "Sin conexión"
                android.util.Log.e("ArlesGestión", "Fallo subida Storage: $errorMsg")
                runOnUiThread { 
                    Toast.makeText(this, "Error subiendo foto: $errorMsg", Toast.LENGTH_LONG).show() 
                }
                onUrl("")
            }
    } catch (e: Exception) {
        android.util.Log.e("ArlesGestión", "Error en proceso: ${e.message}")
        onUrl("")
    } catch (e: OutOfMemoryError) {
        android.util.Log.e("ArlesGestión", "Error de memoria al procesar imagen")
        runOnUiThread { Toast.makeText(this, "Error de memoria: imagen demasiado grande", Toast.LENGTH_LONG).show() }
        onUrl("")
    }
}

private fun calcularInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

internal fun evidenciaEsUriLocal(valor: String): Boolean {
    return valor.startsWith("content:", ignoreCase = true) || valor.startsWith("file:", ignoreCase = true)
}

internal fun comprimirEvidenciaJpeg(bitmap: Bitmap): ByteArray {
    val maxSide = 1280
    val mayor = maxOf(bitmap.width, bitmap.height)
    val scaledBitmap = if (mayor > maxSide) {
        val ratio = maxSide.toFloat() / mayor.toFloat()
        val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val res = bitmap.scale(newWidth, newHeight, true)
        if (res != bitmap) bitmap.recycle()
        res
    } else {
        bitmap
    }

    val targetBytes = 380 * 1024
    val qualitySteps = listOf(76, 72, 68, 64)
    var best = ByteArray(0)

    for (quality in qualitySteps) {
        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val data = baos.toByteArray()
        best = data
        if (data.size <= targetBytes) break
    }

    return best
}

internal fun carpetaEvidencia(modulo: String): String {
    val limpio = Normalizer.normalize(modulo.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

    return when (limpio) {
        "epp" -> "epp"
        "dotacion" -> "dotacion"
        "quimico" -> "quimico"
        "combustible" -> "combustible"
        "consumibles" -> "consumibles"
        "herramientas" -> "herramientas"
        else -> limpio.ifBlank { "varios" }
    }
}

internal fun MainActivity.mostrarPrevisualizacionEvidencia(
    container: android.widget.LinearLayout,
    uriString: String,
    onRemove: (() -> Unit)? = null
): ImageView {
    // Eliminar previsualizaciones anteriores si existen
    for (i in 0 until container.childCount) {
        val v = container.getChildAt(i)
        if (v.tag == "PREVIEW_FOTO") {
            container.removeView(v)
            break
        }
    }

    val previewRow = android.widget.LinearLayout(this).apply {
        tag = "PREVIEW_FOTO"
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(10), 0, dp(10))
        }
    }

    val img = ImageView(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(120), dp(120)).apply {
            setMargins(0, 0, dp(10), 0)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageURI(Uri.parse(uriString))
        background = android.graphics.drawable.GradientDrawable().apply {
            setStroke(dp(2), verde)
            cornerRadius = dp(12).toFloat()
        }
    }

    val btnQuitar = primaryButton("Quitar foto") {
        container.removeView(previewRow)
        onRemove?.invoke()
        Toast.makeText(this, "Evidencia quitada", Toast.LENGTH_SHORT).show()
    }.apply {
        setBackgroundColor(Color.rgb(150, 50, 50))
    }

    previewRow.addView(img)
    previewRow.addView(btnQuitar, android.widget.LinearLayout.LayoutParams(0, dp(54), 1f))
    container.addView(previewRow, (container.childCount - 1).coerceAtLeast(0))
    return img
}

internal fun MainActivity.mostrarDialogoFotoDetalle(url: String, info: String) {
    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val root = android.widget.FrameLayout(this)
    
    val img = ImageView(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    
    // Carga de imagen simple sin librerías externas para ahorrar tokens
    Thread {
        try {
            val stream = java.net.URL(url).openStream()
            val bmp = BitmapFactory.decodeStream(stream)
            runOnUiThread { img.setImageBitmap(bmp) }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Error cargando imagen", Toast.LENGTH_SHORT).show() }
        }
    }.start()

    val label = TextView(this).apply {
        text = info
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        setPadding(dp(20), dp(20), dp(20), dp(20))
        layoutParams = android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM)
    }

    val btnCerrar = ImageButton(this).apply {
        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = android.widget.FrameLayout.LayoutParams(dp(50), dp(50), android.view.Gravity.TOP or android.view.Gravity.END)
        setOnClickListener { dialog.dismiss() }
    }

    root.addView(img)
    root.addView(label)
    root.addView(btnCerrar)
    dialog.setContentView(root)
    dialog.show()
}
