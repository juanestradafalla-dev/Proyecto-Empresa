@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.text.TextWatcher
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : ComponentActivity() {

    internal lateinit var db: GestionDbHelper
    internal lateinit var auth: FirebaseAuth
    internal lateinit var firestore: FirebaseFirestore
    internal lateinit var storage: FirebaseStorage
    internal lateinit var functions: FirebaseFunctions
    internal lateinit var tts: TextToSpeech
    internal var ttsReady: Boolean = false
    internal lateinit var performanceConfig: AppPerformanceConfig
    internal val firestoreListeners = FirestoreListenerRegistry()
    
    internal var pendingCsv: String? = null
    internal var pendingPdf: ByteArray? = null

    internal var currentScannerTarget: EditText? = null
    internal var onScanResultCallback: ((String) -> Unit)? = null
    internal var onSmartScanResult: ((String, String, String, String) -> Unit)? = null
    internal var currentScreenBackAction: (() -> Unit)? = null
    internal var currentScreenRenderer: () -> Unit = { showMainMenu() }
    internal var currentScreenId: String = ""
    internal var lastScreenRenderAtMs: Long = 0L
    internal var lastBackPressAtMs: Long = 0L
    internal var handlingBackPress: Boolean = false
    internal var deferredStartupTasksScheduled: Boolean = false
    internal var mainMenuRequestInFlight: Boolean = false
    internal var mainMenuUserIdCache: String = ""
    internal var mainMenuSaludoCache: String = ""
    internal var lastProfileSyncAtMs: Long = 0L
    internal var lastProfileSyncUserId: String = ""
    internal var catalogoSincronizando: Boolean = false
    internal var catalogoSyncRepetirAlFinalizar: Boolean = false
    internal var lastCatalogSyncAtMs: Long = 0L
    internal val catalogoSyncCallbacks: MutableList<() -> Unit> = mutableListOf()
    internal var aiDialog: Dialog? = null
    internal var activeAIInput: EditText? = null
    internal var activeAIChatContainer: LinearLayout? = null
    internal var lastAIResponseId: String? = null
    internal var iaRequestSeq: Int = 0
    internal var iaContextoRemotoCache: IaContextoRemotoCache? = null
    
    internal var onEvidenciaCapturada: ((String) -> Unit)? = null
    internal var fotoUriActual: Uri? = null
    internal var tallerScanTipoMovimiento: String? = null
    internal var tallerScanSubModulo: String = ""


    internal val verde = ArlesPalette.green700
    internal val verdeOscuro = ArlesPalette.green900
    internal val fondo = ArlesPalette.soft
    internal val texto = ArlesPalette.ink
    internal val gris = ArlesPalette.muted

    // Activity Results
    internal val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            val csv = pendingCsv ?: return@registerForActivityResult
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(csv.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "CSV exportado correctamente", Toast.LENGTH_LONG).show()
            pendingCsv = null
        }
    }


    internal val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            val bytes = pendingPdf ?: return@registerForActivityResult
            contentResolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
            Toast.makeText(this, "PDF exportado correctamente", Toast.LENGTH_LONG).show()
            pendingPdf = null
        }
    }

        val importInventarioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importarInventarioDesdeUri(it) }
        }

    internal val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (isFinishing || isDestroyed) return@registerForActivityResult
        val callback = onScanResultCallback
        val target = currentScannerTarget
        onScanResultCallback = null
        currentScannerTarget = null

        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val scanResult = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT).orEmpty()
        val textoEscaneado = TallerCanonicos.normalizarEntradaQrEscaneada(scanResult)
            .ifBlank { scanResult.trim() }

        try {
            if (callback != null) {
                callback(textoEscaneado)
            } else {
                target?.setText(textoEscaneado)
                procesarEscaneoInteligente(scanResult)
            }
        } catch (e: Exception) {
            android.util.Log.e("ArlesGestion", "Error procesando QR escaneado", e)
            Toast.makeText(
                this,
                "Error al procesar QR: ${e.localizedMessage ?: "fallo inesperado"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }


    internal val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            fotoUriActual?.let { onEvidenciaCapturada?.invoke(it.toString()) }
        }
    }

    internal val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            val input = activeAIInput
            val chat = activeAIChatContainer
            if ((input != null) && (chat != null)) {
                input.setText(spokenText)
                if (spokenText.isNotBlank()) {
                    procesarPromptV5(spokenText, chat)
                    input.setText("")
                }
            } else {
                showAIScreen(spokenText)
            }
        }
    }

    internal val catalogoEstatico = CatalogoBase.data

    internal var catalogoCargado: MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>> = mutableMapOf()


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiDisplayConfig.wrapContext(newBase))
    }

    internal fun inicializarTextToSpeechDiferido() {
        if (::tts.isInitialized) return
        val start = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d("PerfPrincipal", "TextToSpeech inicio")
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("es", "ES")
                tts.setPitch(1.0f)
                tts.setSpeechRate(1.1f)
                ttsReady = true
                android.util.Log.d(
                    "PerfPrincipal",
                    "TextToSpeech fin ${android.os.SystemClock.elapsedRealtime() - start}ms"
                )
            } else {
                android.util.Log.w("PerfPrincipal", "TextToSpeech no disponible")
            }
        }
    }

    private fun programarTareasDiferidasArranque() {
        if (deferredStartupTasksScheduled) return
        deferredStartupTasksScheduled = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val start = android.os.SystemClock.elapsedRealtime()
            android.util.Log.d("PerfPrincipal", "tareas diferidas ejecutadas inicio")
            if (!AppMode.esTallerIndependiente) {
                sincronizarQuimicosCanonicosFirebase()
                sincronizarAseoCanonicoFirebase()
            }
            sincronizarCatalogo(usarCacheSiReciente = true)
            intentarSincronizarPendientes()
            if (!AppMode.esTallerIndependiente) {
                ejecutarBackupAutomaticoSiCorresponde()
            }
            android.util.Log.d(
                "PerfPrincipal",
                "tareas diferidas ejecutadas fin ${android.os.SystemClock.elapsedRealtime() - start}ms"
            )
        }, 750L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val start = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d("PerfPrincipal", "onCreate inicio")
        super.onCreate(savedInstanceState)
        db = GestionDbHelper(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        functions = FirebaseFunctions.getInstance("us-central1")
        performanceConfig = AppPerformanceConfig(this)
        
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            },
        )

        showMainMenu()
        programarTareasDiferidasArranque()
        android.util.Log.d("PerfPrincipal", "onCreate fin ${android.os.SystemClock.elapsedRealtime() - start}ms")
    }

    override fun onDestroy() {
        firestoreListeners.clear()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
            ttsReady = false
        }
        super.onDestroy()
    }



    // La lógica principal está distribuida en archivos de extensión:
    // UiEstilos.kt, InventarioCatalogo.kt, FormulariosOperativos.kt, AsistenteIA.kt,
    // HerramientasGestion.kt y SistemaGestion.kt.
}
