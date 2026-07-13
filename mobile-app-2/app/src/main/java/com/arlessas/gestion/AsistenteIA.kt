@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.text.TextWatcher
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
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
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Archivo modularizado con funciones de extensión de MainActivity.
// Mantiene el comportamiento original, pero separa responsabilidades para facilitar mantenimiento.

private const val IA_LOG_TAG = "AsistenteIA"

internal fun mensajeErrorAsistenteNube(error: Exception): String {
    if (error is FirebaseFunctionsException) {
        return when (error.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Debes iniciar sesión para usar el asistente IA."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Tu usuario no está autorizado para el asistente IA. Pide al administrador que active tu cuenta."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL,
            -> "Sin conexión al asistente en la nube. Verifica internet e intenta de nuevo."
            else ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: "No pude usar el asistente en la nube."
        }
    }
    val texto = error.localizedMessage.orEmpty()
    return when {
        texto.contains("PERMISSION_DENIED", ignoreCase = true) ||
            texto.contains("pendiente de autoriz", ignoreCase = true) ->
            "Tu usuario no está autorizado para el asistente IA. Cierra sesión, vuelve a entrar e intenta otra vez."
        texto.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Debes iniciar sesión para usar el asistente IA."
        texto.contains("UNAVAILABLE", ignoreCase = true) ||
            texto.contains("network", ignoreCase = true) ||
            texto.contains("conex", ignoreCase = true) ->
            "Sin conexión al asistente en la nube. Verifica internet e intenta de nuevo."
        texto.isNotBlank() -> texto
        else -> "No pude usar el asistente en la nube."
    }
}

internal fun tokensBusquedaIA(prompt: String): List<String> {
    return AiContextFilter.tokens(prompt)
}

internal fun esConsultaInventarioProductoIA(promptNorm: String): Boolean {
    return promptNorm.contains("stock") ||
        promptNorm.contains("existencia") ||
        promptNorm.contains("inventario") ||
        promptNorm.contains("disponib") ||
        promptNorm.contains("cuanto") ||
        promptNorm.contains("cuanta") ||
        promptNorm.contains("hay") ||
        promptNorm.contains("busca") ||
        promptNorm.contains("buscar") ||
        promptNorm.contains("consult") ||
        promptNorm.contains("muestra") ||
        promptNorm.contains("mostrar") ||
        promptNorm.contains("dime") ||
        promptNorm.contains("tienes") ||
        promptNorm.contains("existe") ||
        promptNorm.contains("informacion")
}

internal fun esConsultaOperativaIA(promptNorm: String): Boolean {
    val pideStock = promptNorm.contains("stock") ||
        promptNorm.contains("existencia") ||
        promptNorm.contains("inventario") ||
        promptNorm.contains("disponib")
    if (pideStock) return false
    return promptNorm.contains("movimiento") ||
        promptNorm.contains("historial") ||
        promptNorm.contains("auditoria") ||
        promptNorm.contains("entrada") ||
        promptNorm.contains("salida")
}

internal fun MainActivity.esConsultaAseoInventarioIA(prompt: String, frase: String = ""): Boolean {
    val texto = normalizarBusqueda("$prompt $frase")
    if (detectarModuloDesdePrompt(prompt).equals(AseoCanonicos.MODULO, ignoreCase = true)) return true
    if (texto.contains("bolsa") && Regex("""\b(basura|negra|negras|blanca|blancas|roja|rojas|verde|verdes|enorme|residuo|residuos)\b""").containsMatchIn(texto)) {
        return true
    }
    return Regex("""\b(jabon|detergente|desengrasante|clorox|fabuloso|trapero|escoba|papel higienico|servilleta|aseo|limpieza)\b""")
        .containsMatchIn(texto)
}

internal fun MainActivity.cerrarAsistenteIA() {
    aiDialog?.dismiss()
    aiDialog = null
    activeAIInput = null
    activeAIChatContainer = null
}

internal fun MainActivity.navegarDesdeIA(accion: () -> Unit) {
    cerrarAsistenteIA()
    accion()
}

internal fun MainActivity.iaChatActivo(chatContainer: LinearLayout): Boolean {
    if (isFinishing || isDestroyed) return false
    if (aiDialog?.isShowing != true) return false
    return chatContainer.parent != null
}

internal fun MainActivity.agregarVistaChatSegura(chatContainer: LinearLayout, view: View) {
    if (!iaChatActivo(chatContainer)) return
    try {
        chatContainer.addView(view)
        (chatContainer.parent as? ScrollView)?.post {
            (chatContainer.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    } catch (e: Exception) {
        Log.e(IA_LOG_TAG, "No se pudo agregar vista al chat", e)
    }
}

internal fun LinearLayout.quitarVistaSiExiste(view: View) {
    if (view.parent == this) removeView(view)
}

internal fun MainActivity.interpretarOrdenLocal(prompt: String): Map<String, String> {
    val norm = normalizarBusqueda(prompt)
    val tipo = when {
        norm.contains("entrada") || norm.contains("ingreso") -> "entrada"
        norm.contains("prestamo") -> "prestamo"
        norm.contains("devolucion") -> "devolucion"
        norm.contains("salida") || norm.contains("saca") || norm.contains("sacar") || norm.contains("consumo") -> "salida"
        else -> ""
    }
    val cantidad = Regex("""\b(\d+(?:[.,]\d+)?)\b""").find(norm)?.groupValues?.get(1)?.replace(",", ".") ?: ""
    val solicitante = Regex(
        """\b(?:para|a|solicitante|empleado|trabajador|responsable)\s+([\wáéíóúñ]+)""",
        RegexOption.IGNORE_CASE,
    ).find(prompt)?.groupValues?.get(1).orEmpty()
    val modulo = detectarModuloDesdePrompt(prompt)
    val producto = BusquedaInventarioIA.extraerFraseProducto(prompt)
    return mapOf(
        "tipo" to tipo,
        "cantidad" to cantidad,
        "solicitante" to solicitante,
        "modulo" to modulo,
        "producto" to producto,
    )
}

internal fun MainActivity.hablarAsistente(texto: String) {
    val limpio = texto.trim()
    if (limpio.isBlank()) return
    if (!ttsReady) return
    try {
        tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, "ia_${System.currentTimeMillis()}")
    } catch (_: Exception) {
        // Algunos equipos fallan con TTS o aún no está inicializado.
    }
}

internal fun MainActivity.puntuarDocumentoPorPrompt(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        prompt: String,
        moduloHint: String = "",
    ): Int {
        val consulta = BusquedaInventarioIA.extraerFraseProducto(prompt).ifBlank { prompt }
        val promptNorm = normalizarBusqueda(consulta)
        val codigo = normalizarBusqueda(codigoExistencia(doc))
        val base = textoBusquedaExistencia(doc)
        var puntaje = if (promptNorm.isBlank()) 1 else 0
        if (moduloHint.isNotBlank() && documentoCoincideModuloIA(doc, moduloHint)) puntaje += 280
        else if (moduloHint.isNotBlank()) puntaje -= 150 // Penalizamos si el módulo no coincide

        if (promptNorm.isBlank()) return puntaje
        if (codigo.isNotBlank() && promptNorm.contains(codigo)) puntaje += 500
        val tokens = BusquedaInventarioIA.tokens(consulta)
        tokens.forEach { token ->
            if (codigo == token) puntaje += 250
            if (BusquedaInventarioIA.tokenPresenteFuzzy(token, base)) puntaje += 30
        }
        if (tokens.isNotEmpty() && tokens.all { BusquedaInventarioIA.tokenPresenteFuzzy(it, base) }) puntaje += 120
        puntaje += (BusquedaInventarioIA.puntuarTexto(consulta, base, tokens) * 100).toInt()
        return puntaje
    }

internal fun MainActivity.filtrarDocumentosStockIA(
        prompt: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        limite: Int = 30,
        moduloHint: String = "",
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        val modulo = moduloHint.ifBlank { detectarModuloDesdePrompt(prompt) }
        val pool = if (modulo.isNotBlank()) {
            val porModulo = docs.filter { documentoCoincideModuloIA(it, modulo) }
            if (porModulo.isNotEmpty()) porModulo else docs
        } else {
            docs
        }
        val ordenados = pool.asSequence()
            .map { it to puntuarDocumentoPorPrompt(it, prompt, modulo) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<com.google.firebase.firestore.DocumentSnapshot, Int>> { it.second }.thenBy { nombreItemExistencia(it.first) })
            .map { it.first }
            .take(limite)
            .toList()
        return ordenados
    }

internal fun MainActivity.stockInfoDesdeDocumentos(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        moduloHint: String = "",
    ): String {
        val header = if (moduloHint.isNotBlank()) "MODULO_STOCK: $moduloHint | " else ""
        return header + docs.joinToString("; ") {
            val codigo = codigoExistencia(it)
            val itemStock = nombreItemExistencia(it)
            val marcaStock = marcaExistencia(it)
            val referenciaStock = referenciaExistencia(it)
            val moduloStock = it.getString("modulo").orEmpty()
            val categoriaStock = it.getString("categoria").orEmpty()
            val ubicacionStock = ubicacionExistencia(it)
            val cantidadStock = it.getDouble("cantidad") ?: 0.0
            val unidadStock = it.getString("unidad") ?: ""
            "$codigo | $moduloStock | $categoriaStock | $itemStock | $marcaStock | $referenciaStock | ubicacion=$ubicacionStock | $cantidadStock $unidadStock"
        }
    }

internal fun esContextoInventarioTaller(prompt: String): Boolean {
    val norm = normalizarBusqueda(prompt)
    return norm.contains("taller") || norm.contains("bodega roja") ||
        TallerCanonicos.esSubmoduloTaller(prompt) ||
        norm.contains("herramienta") || norm.contains("herramientas")
}

internal fun esConsultaLubricantesTaller(prompt: String): Boolean {
    val norm = normalizarBusqueda(prompt)
    return norm.contains("lubricante") || norm.contains("aceite") || norm.contains("grasa") ||
        norm.contains("valvulina") || norm.contains("refrigerante") || norm.contains("caseta de lubricantes")
}

internal fun MainActivity.tieneHerramientasTallerCoincidentes(frase: String): Boolean {
    if (frase.isBlank()) return false
    val tokens = BusquedaInventarioIA.tokens(frase)
    return consultarHerramientasLocalesLineas(frase, tokens, 1).isNotEmpty()
}

internal fun MainActivity.responderStockHerramientasTallerIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
): Boolean {
    val consulta = frase.ifBlank { BusquedaInventarioIA.extraerFraseProducto(prompt) }
    if (consulta.isBlank()) return false
    val tokens = BusquedaInventarioIA.tokens(consulta)
    val resultados = consultarHerramientasLocalesLineas(consulta, tokens, 8)
    if (resultados.isEmpty()) return false
    if (!iaChatActivo(chatContainer)) return true
    quitarCargando()
    val texto = "Herramientas de taller:\n${resultados.joinToString("\n")}"
    guardarTurnoConversacionIA("asistente", texto)
    mostrarResultadosConsultar(chatContainer, "Herramientas de taller", resultados)
    hablarAsistente("Encontré ${resultados.size} herramienta(s) en taller.")
    return true
}

internal fun MainActivity.responderStockLubricantesTallerIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    val consulta = frase.ifBlank { BusquedaInventarioIA.extraerFraseProducto(prompt) }
    if (consulta.isBlank()) {
        quitarCargando()
        return
    }
    fun entregar(texto: String) {
        if (!iaChatActivo(chatContainer)) return
        quitarCargando()
        guardarTurnoConversacionIA("asistente", texto)
        agregarVistaChatSegura(chatContainer, cardText("Asistente: $texto"))
        hablarAsistente(texto)
    }
    cargarExistenciasAlmacenParaIA(
        consulta = consulta,
        prompt = prompt,
        moduloForzado = ModulosInventario.LUBRICANTES_TALLER,
        soloTaller = true,
        onSuccess = { docs ->
            if (!iaChatActivo(chatContainer)) {
                Unit
            } else if (docs.isEmpty()) {
                quitarCargando()
                agregarVistaChatSegura(chatContainer, cardText("No encontré '$consulta' en lubricantes de taller."))
            } else {
                entregar(textoStockAgregadoIA(consulta, docs))
            }
        },
        onFailure = {
            quitarCargando()
            if (iaChatActivo(chatContainer)) {
                agregarVistaChatSegura(chatContainer, cardText("No pude leer lubricantes de taller en el almacén."))
            }
        },
    )
}

internal fun MainActivity.responderStockTallerIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    val pideLubricante = esConsultaLubricantesTaller(prompt)
    val promptNorm = normalizarBusqueda(prompt)
    val pideHerramienta = !pideLubricante && (
        promptNorm.contains("herramienta") || tieneHerramientasTallerCoincidentes(frase)
    )
    when {
        pideHerramienta && responderStockHerramientasTallerIA(prompt, frase, chatContainer, quitarCargando) -> Unit
        pideLubricante -> responderStockLubricantesTallerIA(prompt, frase, chatContainer, quitarCargando)
        promptNorm.contains("taller") || promptNorm.contains("bodega roja") ->
            responderStockLubricantesTallerIA(prompt, frase, chatContainer, quitarCargando)
        else -> responderStockAgregadoNubeIA(prompt, frase, chatContainer, quitarCargando)
    }
}

internal fun MainActivity.filasAseoParaConsultaIA(
    consulta: String,
    docs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList(),
    limite: Int = 8,
): List<FilaInventarioAseo> {
    val filas = filasInventarioAseoUnificadas(docs)
    if (consulta.isBlank()) return filas.take(limite)
    val tokens = BusquedaInventarioIA.tokens(consulta)
    val tokensNucleo = BusquedaInventarioIA.tokensNucleo(consulta)
    return filas.map { fila ->
        val texto = "${fila.codigo} ${fila.categoria} ${fila.producto} Piso ${fila.piso} ${fila.unidad}"
        val prefiltro = tokensNucleo.isEmpty() ||
            tokensNucleo.any { token -> BusquedaInventarioIA.tokenPresenteFuzzy(token, texto) }
        val score = if (prefiltro) BusquedaInventarioIA.puntuarTexto(consulta, texto, tokens) else 0.0
        fila to score
    }
        .filter { it.second >= 0.30 }
        .sortedWith(
            compareByDescending<Pair<FilaInventarioAseo, Double>> { it.second }
                .thenBy { it.first.codigo },
        )
        .take(limite)
        .map { it.first }
}

internal fun MainActivity.textoStockAseoIA(consulta: String, filas: List<FilaInventarioAseo>): String {
    if (filas.isEmpty()) return ""
    val total = filas.sumOf { it.stock }
    val unidad = filas.first().unidad.ifBlank { "UNIDAD" }
    val mismaUnidad = filas.all { it.unidad.equals(unidad, ignoreCase = true) }
    val lineas = filas.joinToString("\n") { fila ->
        "${fila.codigo} · ${fila.producto} · Piso ${fila.piso}: ${cantidadTexto(fila.stock)} ${fila.unidad}"
    }
    return if (filas.size == 1) {
        "Stock ASEO de $consulta: ${lineas}."
    } else if (mismaUnidad) {
        "Stock ASEO de $consulta: ${cantidadTexto(total)} $unidad en ${filas.size} referencias:\n$lineas"
    } else {
        "Stock ASEO de $consulta:\n$lineas"
    }
}

internal fun MainActivity.responderStockAseoIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    val consulta = frase.ifBlank { BusquedaInventarioIA.extraerFraseProducto(prompt) }.ifBlank { prompt }

    fun entregar(filas: List<FilaInventarioAseo>) {
        if (!iaChatActivo(chatContainer)) return
        quitarCargando()
        val texto = textoStockAseoIA(consulta, filas).ifBlank {
            "No encontré stock ASEO para '$consulta'."
        }
        guardarTurnoConversacionIA("asistente", texto)
        agregarVistaChatSegura(chatContainer, cardText("Asistente: $texto"))
        hablarAsistente(texto)
    }

    if (!isNetworkAvailable()) {
        entregar(filasAseoParaConsultaIA(consulta, limite = 8))
        return
    }

    firestore.collection(AseoCanonicos.COLECCION).get()
        .addOnSuccessListener { snapshot ->
            entregar(filasAseoParaConsultaIA(consulta, snapshot.documents, 8))
        }
        .addOnFailureListener {
            entregar(filasAseoParaConsultaIA(consulta, limite = 8))
        }
}

internal data class ResultadoStockGeneralIA(
    val modulo: String,
    val codigo: String,
    val nombre: String,
    val ubicacion: String,
    val cantidad: Double,
    val unidad: String,
    val fuente: String,
    val score: Double,
    val activo: Boolean = true,
)

internal fun MainActivity.puntuarStockGeneralIA(consulta: String, texto: String): Double {
    if (consulta.isBlank() || texto.isBlank()) return 0.0
    val tokens = BusquedaInventarioIA.tokens(consulta)
    val tokensNucleo = BusquedaInventarioIA.tokensNucleo(consulta)
    val prefiltro = tokensNucleo.isEmpty() ||
        tokensNucleo.any { token -> BusquedaInventarioIA.tokenPresenteFuzzy(token, texto) }
    if (!prefiltro) return 0.0
    val base = BusquedaInventarioIA.puntuarTexto(consulta, texto, tokens)
    val todosNucleo = tokensNucleo.isNotEmpty() &&
        tokensNucleo.all { token -> BusquedaInventarioIA.tokenPresenteFuzzy(token, texto) }
    return (base + if (todosNucleo) 0.18 else 0.0).coerceAtMost(1.0)
}

internal fun MainActivity.resultadosExistenciasGeneralIA(
    consulta: String,
    docs: List<com.google.firebase.firestore.DocumentSnapshot>,
): List<ResultadoStockGeneralIA> {
    return docs.mapNotNull { doc ->
        val texto = textoBusquedaExistencia(doc)
        val activo = doc.getBoolean("activo") != false
        val score = puntuarStockGeneralIA(consulta, texto) - if (activo) 0.0 else 0.14
        if (score < 0.30) return@mapNotNull null
        ResultadoStockGeneralIA(
            modulo = doc.getString("modulo").orEmpty().ifBlank { "Almacén" },
            codigo = codigoExistencia(doc),
            nombre = productoVisibleExistencia(doc),
            ubicacion = ubicacionExistencia(doc),
            cantidad = numeroDocumento(doc, "cantidad", "stock_actual"),
            unidad = doc.getString("unidad").orEmpty().ifBlank { "unidad" },
            fuente = "existencias",
            score = score,
            activo = activo,
        )
    }
}

internal fun MainActivity.resultadosAseoGeneralIA(
    consulta: String,
    docs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList(),
): List<ResultadoStockGeneralIA> {
    return filasInventarioAseoUnificadas(docs).mapNotNull { fila ->
        val texto = "${fila.codigo} ${AseoCanonicos.MODULO} ${fila.categoria} ${fila.producto} Piso ${fila.piso} ${fila.unidad}"
        val score = puntuarStockGeneralIA(consulta, texto)
        if (score < 0.30) return@mapNotNull null
        ResultadoStockGeneralIA(
            modulo = AseoCanonicos.MODULO,
            codigo = fila.codigo,
            nombre = fila.producto,
            ubicacion = "Piso ${fila.piso}",
            cantidad = fila.stock,
            unidad = fila.unidad,
            fuente = AseoCanonicos.COLECCION,
            score = score,
        )
    }
}

internal fun MainActivity.resultadosHerramientasGeneralIA(
    consulta: String,
    docs: List<com.google.firebase.firestore.DocumentSnapshot>? = null,
): List<ResultadoStockGeneralIA> {
    val herramientas = docs
        ?.mapNotNull { herramientaDesdeDocumentoFirestore(it) }
        ?: db.obtenerHerramientas()
    return herramientas
        .filterNot { esHerramientaRetirada(it) }
        .mapNotNull { h ->
            val texto = textoBusquedaHerramientaTaller(h)
            val score = puntuarStockGeneralIA(consulta, texto)
            if (score < 0.30) return@mapNotNull null
            ResultadoStockGeneralIA(
                modulo = h.subModulo.ifBlank { TallerCanonicos.MODULO },
                codigo = h.codigo.ifBlank { h.codigoQr },
                nombre = listOf(h.nombre, h.detalleTaller()).filter { it.isNotBlank() }.joinToString(" · "),
                ubicacion = h.categoriaTaller(),
                cantidad = h.disponibles(),
                unidad = h.unidad.ifBlank { "UNIDAD" },
                fuente = "herramientas",
                score = score,
            )
        }
}

internal fun textoStockGeneralIA(consulta: String, resultados: List<ResultadoStockGeneralIA>): String {
    if (resultados.isEmpty()) return "No encontré '$consulta' en el inventario general."
    val unicosOrdenados = resultados
        .distinctBy { "${it.fuente}|${it.codigo}|${it.nombre}|${it.ubicacion}" }
        .sortedWith(
            compareByDescending<ResultadoStockGeneralIA> { it.activo }
                .thenByDescending { it.score }
                .thenBy { it.modulo }
                .thenBy { it.nombre },
        )
    val mejorScore = unicosOrdenados.maxOfOrNull { it.score } ?: 0.0
    val margen = if (mejorScore >= 0.75) 0.24 else 0.34
    val ordenados = unicosOrdenados
        .filter { it.score >= 0.30 && it.score >= mejorScore - margen }
        .take(12)
    val lineas = ordenados.joinToString("\n") { r ->
        val estado = if (r.activo) "" else " · histórico/inactivo"
        val ubicacion = r.ubicacion.ifBlank { "sin ubicación" }
        "${r.modulo} · ${r.codigo.ifBlank { "sin código" }} · ${r.nombre} · $ubicacion: ${cantidadTexto(r.cantidad)} ${r.unidad}$estado"
    }
    return "Inventario general para $consulta:\n$lineas"
}

internal fun MainActivity.responderStockGeneralIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    val consulta = frase.ifBlank { BusquedaInventarioIA.extraerFraseProducto(prompt) }.ifBlank { prompt }
    val acumulado = mutableListOf<ResultadoStockGeneralIA>()

    fun entregar() {
        if (!iaChatActivo(chatContainer)) return
        quitarCargando()
        val texto = textoStockGeneralIA(consulta, acumulado)
        guardarTurnoConversacionIA("asistente", texto)
        agregarVistaChatSegura(chatContainer, cardText("Asistente: $texto"))
        hablarAsistente(if (acumulado.isEmpty()) "No encontré coincidencias." else "Encontré ${acumulado.size} coincidencia(s).")
    }

    if (!isNetworkAvailable()) {
        acumulado.addAll(resultadosAseoGeneralIA(consulta))
        acumulado.addAll(resultadosHerramientasGeneralIA(consulta))
        entregar()
        return
    }

    var pendientes = 3
    fun parteLista() {
        pendientes--
        if (pendientes <= 0) entregar()
    }

    firestore.collection("existencias")
        .limit(maxOf(performanceConfig.inventoryQueryLimit(), 2000L))
        .get()
        .addOnSuccessListener { snapshot ->
            acumulado.addAll(resultadosExistenciasGeneralIA(consulta, snapshot.documents))
            parteLista()
        }
        .addOnFailureListener { parteLista() }

    firestore.collection(AseoCanonicos.COLECCION)
        .get()
        .addOnSuccessListener { snapshot ->
            acumulado.addAll(resultadosAseoGeneralIA(consulta, snapshot.documents))
            parteLista()
        }
        .addOnFailureListener {
            acumulado.addAll(resultadosAseoGeneralIA(consulta))
            parteLista()
        }

    firestore.collection("herramientas")
        .get()
        .addOnSuccessListener { snapshot ->
            acumulado.addAll(resultadosHerramientasGeneralIA(consulta, snapshot.documents))
            parteLista()
        }
        .addOnFailureListener {
            acumulado.addAll(resultadosHerramientasGeneralIA(consulta))
            parteLista()
        }
}

internal fun MainActivity.responderStockAgregadoNubeIA(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    try {
        responderStockAgregadoNubeIAInterno(prompt, frase, chatContainer, quitarCargando)
    } catch (e: Exception) {
        Log.e(IA_LOG_TAG, "Crash evitado en stock agregado IA", e)
        quitarCargando()
        if (iaChatActivo(chatContainer)) {
            agregarVistaChatSegura(chatContainer, cardText("No pude consultar el stock ahora. Intenta de nuevo."))
        }
    }
}

private fun MainActivity.responderStockAgregadoNubeIAInterno(
    prompt: String,
    frase: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
) {
    val consulta = frase.ifBlank { BusquedaInventarioIA.extraerFraseProducto(prompt) }
    if (consulta.isBlank()) {
        quitarCargando()
        return
    }

    fun entregarRespuesta(texto: String) {
        if (!iaChatActivo(chatContainer)) return
        quitarCargando()
        guardarTurnoConversacionIA("asistente", texto)
        agregarVistaChatSegura(chatContainer, cardText("Asistente: $texto"))
        hablarAsistente(texto)
    }

    fun fallbackCanonicoQuimico(): Boolean {
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val coincidencias = QuimicosCanonicos.items
            .map { canonico ->
                val base = "${canonico.item} ${canonico.codigoOriginal} ${canonico.ubicacion}"
                canonico to BusquedaInventarioIA.puntuarTexto(consulta, base, tokens)
            }
            .filter { it.second >= 0.38 }
            .sortedByDescending { it.second }
        if (coincidencias.isEmpty()) return false

        val lineas = coincidencias.map { (canonico, _) ->
            "${canonico.ubicacion}: ${cantidadTexto(canonico.stockInicial)} ${canonico.unidad} (${canonico.codigoOriginal})"
        }
        val total = coincidencias.sumOf { it.first.stockInicial }
        val unidad = coincidencias.first().first.unidad.ifBlank { "unidad" }
        val texto = if (lineas.size > 1) {
            "Stock de $consulta: ${cantidadTexto(total)} $unidad en total, en ${lineas.size} ubicaciones:\n${lineas.joinToString("\n")}"
        } else {
            "Stock de $consulta: ${cantidadTexto(total)} $unidad. ${lineas.first()}."
        }
        entregarRespuesta(texto)
        return true
    }

    fun fallbackStockLocal(): Boolean {
        val producto = resolverProductoDesdePrompt(prompt) ?: return false
        val stockLocal = db.calcularStockLocal(producto.item)
        if (stockLocal <= 0.0) return false
        val nombre = nombreProductoCatalogo(producto.modulo, producto.item, producto.referencia)
        val unidad = sugerirUnidadGeneral(producto.modulo, nombre)
        entregarRespuesta("Stock local de $nombre: ${formatoCantidadTaller(stockLocal)} $unidad (${producto.modulo}).")
        return true
    }

    if (!isNetworkAvailable()) {
        if (!fallbackCanonicoQuimico()) fallbackStockLocal()
        if (!iaChatActivo(chatContainer)) return
        quitarCargando()
        return
    }

    cargarExistenciasAlmacenParaIA(
        consulta = consulta,
        prompt = prompt,
        onSuccess = { docs ->
            try {
                if (iaChatActivo(chatContainer)) {
                    if (docs.isEmpty()) {
                        when {
                            fallbackCanonicoQuimico() -> Unit
                            fallbackStockLocal() -> Unit
                            else -> {
                                quitarCargando()
                                agregarVistaChatSegura(chatContainer, cardText("No encontré stock de '$consulta' en el almacén."))
                            }
                        }
                    } else {
                        entregarRespuesta(textoStockAgregadoIA(consulta, docs))
                    }
                }
            } catch (e: Exception) {
                Log.e(IA_LOG_TAG, "Error procesando stock almacén", e)
                quitarCargando()
            }
        },
        onFailure = {
            Log.w(IA_LOG_TAG, "Lectura almacén falló, usando respaldo")
            when {
                fallbackCanonicoQuimico() -> Unit
                fallbackStockLocal() -> Unit
                else -> quitarCargando()
            }
        },
    )
}

internal fun MainActivity.stockInfoIA(
        prompt: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        limite: Int = 30,
        moduloHint: String = "",
    ): String {
        val modulo = moduloHint.ifBlank { detectarModuloDesdePrompt(prompt) }
        val filtrados = filtrarDocumentosStockIA(prompt, docs, limite, modulo)
        return stockInfoDesdeDocumentos(filtrados, modulo)
    }

internal fun MainActivity.lineaHerramientaContextoIA(h: Herramienta): String {
        val resp = h.responsable.takeIf { it.isNotBlank() && h.ocupados() > 0 }.orEmpty()
        return buildString {
            append("id=${h.id} | nombre=${h.nombre} | codigo=${h.codigo}")
            if (h.referencia.isNotBlank()) append(" | referencia=${h.referencia}")
            if (h.marca.isNotBlank()) append(" | marca=${h.marca}")
            append(" | total=${h.cantidadTotal} ${h.unidad}")
            append(" | disponibles=${h.disponibles()} | ocupados=${h.ocupados()}")
            append(" | ocupada=${if (h.ocupados() > 0) "si" else "no"}")
            if (resp.isNotBlank()) append(" | responsable=$resp")
            if (h.ubicacion.isNotBlank()) append(" | ubicacion=${h.ubicacion}")
        }
    }

internal fun esPreguntaOcupacionHerramienta(prompt: String): Boolean {
        val p = prompt.lowercase(Locale.getDefault())
        return Regex("""\b(ocupad[oa]s?|disponib|en\s+uso|libre|prestad[oa]?|asignad[oa]?|hay\s+.+\s+disponib|esta\s+.+\s+ocupad)\b""")
            .containsMatchIn(p)
    }

internal fun MainActivity.resolverHerramientaEnPrompt(prompt: String): Herramienta? {
        val consulta = BusquedaInventarioIA.extraerFraseProducto(prompt).ifBlank { prompt }
        if (consulta.isBlank()) return null
        return herramientasTallerActivas().asSequence()
            .map { h ->
                val base = "${h.nombre} ${h.codigo} ${h.codigoQr} ${h.referencia} ${h.marca} ${h.subModulo} ${h.subcategoria}"
                h to BusquedaInventarioIA.puntuarTexto(consulta, base)
            }
            .filter { it.second >= 0.38 }
            .maxByOrNull { it.second }
            ?.first
    }

internal fun MainActivity.responderOcupacionHerramientaLocal(h: Herramienta): String {
        val ocupada = h.ocupados() > 0
        val estadoTexto = if (ocupada) {
            val unidad = h.unidad.lowercase(Locale.getDefault())
            val cant = if (h.ocupados() == h.ocupados().toLong().toDouble()) {
                h.ocupados().toLong().toString()
            } else {
                h.ocupados().toString()
            }
            val resp = h.responsable.takeIf { it.isNotBlank() }?.let { " con $it" }.orEmpty()
            "Sí, ${h.nombre} está ocupada: $cant $unidad en uso$resp."
        } else {
            val disp = h.disponibles()
            val unidad = h.unidad.lowercase(Locale.getDefault())
            "No, ${h.nombre} no está ocupada. Hay $disp $unidad disponible${if (disp == 1.0) "" else "s"}."
        }
        return estadoTexto
    }

internal fun MainActivity.herramientasComoTextoFiltrado(prompt: String, limite: Int = 40): String {
        if (!AppMode.incluyeTaller) return "Taller se maneja en la app Taller independiente."
        val herramientas = db.obtenerHerramientas()
        if (herramientas.isEmpty()) return "Sin herramientas registradas en la app."
        val consulta = BusquedaInventarioIA.extraerFraseProducto(prompt).ifBlank { prompt }
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val filtradas = herramientas.asSequence()
            .map { h ->
                val textoHerramienta = "${h.id} ${h.nombre} ${h.codigo} ${h.codigoQr} ${h.referencia} ${h.marca} ${h.ubicacion} ${h.responsable} ${h.subModulo}"
                val score = BusquedaInventarioIA.puntuarTexto(consulta, textoHerramienta, tokens)
                h to score
            }
            .filter { it.second >= 0.32 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limite)
            .toList()
        return filtradas.joinToString("\n") { lineaHerramientaContextoIA(it) }.take(6000)
    }

internal fun MainActivity.showAIScreen(initialText: String = "", @Suppress("UNUSED_PARAMETER") backAction: (() -> Unit)? = null) {
        showAIDialog(initialText)
    }

internal fun MainActivity.showAIDialog(initialText: String = "") {
        if (aiDialog?.isShowing == true) {
            activeAIInput?.setText(initialText)
            val chat = activeAIChatContainer
            if ((initialText.isNotBlank()) && (chat != null)) {
                procesarPromptV5(initialText, chat)
                activeAIInput?.setText("")
            }
            return
        }

        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        }
        aiDialog = dialog

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(220, 230, 224))
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "Asistente IA"
                textSize = 20f
                setTextColor(verdeOscuro)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setAnimatedClick(this) { dialog.dismiss() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        panel.addView(header)

        panel.addView(TextView(this).apply {
            text = "Listo para consultar."
            textSize = 12f
            setTextColor(gris)
            setPadding(0, 0, 0, dp(8))
        })

        val chatScroll = ScrollView(this).apply {
            isFillViewport = false
        }
        val chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        chatScroll.addView(chatContainer)
        panel.addView(chatScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340)))

        val label = TextView(this).apply {
            text = "Instrucción / Consulta"
            textSize = 13f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(label)

        val input = EditText(this).apply {
            hint = "Escribe una orden breve..."
            textSize = 15f
            isSingleLine = false
            minLines = 1
            maxLines = 3
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = GradientDrawable().apply {
                setStroke(dp(1), gris)
                cornerRadius = dp(10).toFloat()
                setColor(Color.WHITE)
            }
            setText(initialText)
        }
        panel.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val btnSpeak = roundIconButton(R.drawable.ic_mic, verde) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Te escucho...")
            }
            voiceLauncher.launch(intent)
        }
        val btnMute = roundIconButton(android.R.drawable.ic_lock_silent_mode_off, Color.rgb(200, 50, 50)) {
            if (ttsReady) {
                try {
                    tts.stop()
                } catch (_: Exception) {
                }
            }
            Toast.makeText(this, "Audio detenido", Toast.LENGTH_SHORT).show()
        }
        val btnSend = primaryButton("Enviar") {
            val prompt = input.text.toString().trim()
            if (prompt.isNotBlank()) {
                procesarPromptV5(prompt, chatContainer)
                input.setText("")
            }
        }

        buttonsRow.addView(btnSpeak, LinearLayout.LayoutParams(dp(54), dp(54)))
        buttonsRow.addView(btnMute, LinearLayout.LayoutParams(dp(54), dp(54)).apply { setMargins(dp(8), 0, 0, 0) })
        buttonsRow.addView(btnSend, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(8), 0, 0, 0) })
        panel.addView(buttonsRow)

        activeAIInput = input
        activeAIChatContainer = chatContainer
        restaurarConversacionIAEnChat(chatContainer)

        dialog.setContentView(panel)
        dialog.setOnDismissListener {
            aiDialog = null
            activeAIInput = null
            activeAIChatContainer = null
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.62f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attrs = attributes
            attrs.width = (resources.displayMetrics.widthPixels * 0.94f).toInt()
            attrs.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
            attributes = attrs
        }
        panel.post { animateViewIn(panel) }

        if (initialText.isNotBlank()) {
            procesarPromptV5(initialText, chatContainer)
            input.setText("")
        }
    }

internal fun MainActivity.intentarRespuestaRapidaIA(
    prompt: String,
    chatContainer: LinearLayout,
    quitarCargando: () -> Unit,
): Boolean {
    if (!iaChatActivo(chatContainer)) return false
    return try {
        val promptNorm = normalizarBusqueda(prompt)

        if (AppMode.incluyeTaller && esPreguntaOcupacionHerramienta(prompt)) {
            resolverHerramientaEnPrompt(prompt)?.let { herramienta ->
                quitarCargando()
                val respuesta = responderOcupacionHerramientaLocal(herramienta)
                guardarTurnoConversacionIA("asistente", respuesta)
                agregarVistaChatSegura(chatContainer, cardText(respuesta))
                hablarAsistente(respuesta)
                return true
            }
        }

        val esConsultaStock = promptNorm.contains("stock") || promptNorm.contains("cuanto") || promptNorm.contains("cuanta") ||
            promptNorm.contains("hay") || promptNorm.contains("disponib") || promptNorm.contains("existencia")
        val esConsultaProducto = esConsultaInventarioProductoIA(promptNorm)
        val esConsultaOperativa = esConsultaOperativaIA(promptNorm)
        val frase = BusquedaInventarioIA.extraerFraseProducto(prompt)
        if (!AppMode.incluyeTaller && (esContextoInventarioTaller(prompt) || esConsultaLubricantesTaller(prompt))) {
            quitarCargando()
            val respuesta = "Taller ahora se maneja desde la app Taller independiente."
            agregarVistaChatSegura(chatContainer, cardText(respuesta))
            hablarAsistente(respuesta)
            return true
        }
        if ((esConsultaStock || esConsultaProducto) && frase.isNotBlank() && !esConsultaOperativa) {
            responderStockGeneralIA(prompt, frase, chatContainer, quitarCargando)
            return true
        }

        if (AppMode.incluyeTaller && (promptNorm.contains("herramienta") || promptNorm.contains("taller"))) {
            val consulta = frase.ifBlank { prompt }
            val tokens = BusquedaInventarioIA.tokens(consulta)
            val resultados = consultarHerramientasLocalesLineas(consulta, tokens, 6)
            if (resultados.isNotEmpty()) {
                quitarCargando()
                mostrarResultadosConsultar(chatContainer, "Herramientas", resultados)
                return true
            }
        }

        false
    } catch (e: Exception) {
        Log.e(IA_LOG_TAG, "Error en respuesta rápida IA", e)
        false
    }
}

internal fun MainActivity.consultarHerramientasLocalesLineas(
    consulta: String,
    tokens: List<String>,
    limite: Int,
): List<String> {
    return db.obtenerHerramientas().map { h ->
        val ocupacion = if (h.ocupados() > 0) "ocupada (${h.ocupados()} ${h.unidad})" else "disponible (${h.disponibles()} ${h.unidad})"
        val linea = buildString {
            append("#${h.id} · ${h.nombre} · $ocupacion")
            if (h.codigo.isNotBlank()) append(" · Cód: ${h.codigo}")
            if (h.responsable.isNotBlank() && h.ocupados() > 0) append(" · ${h.responsable}")
        }
        linea to BusquedaInventarioIA.puntuarTexto(consulta, "${h.nombre} ${h.codigo} ${h.referencia} ${h.marca} $linea", tokens)
    }
        .filter { it.second >= 0.28 }
        .sortedByDescending { it.second }
        .take(limite)
        .map { it.first }
}

internal fun MainActivity.procesarPromptV5(prompt: String, chatContainer: LinearLayout, reintentos: Int = 0) {
        val requestId = ++iaRequestSeq
        val loadingMsg = cardText(if (reintentos > 0) "Corrigiendo..." else "Consultando...").apply { setTextColor(gris) }

        fun solicitudVigente(): Boolean = requestId == iaRequestSeq

        fun quitarCargando() {
            if (!iaChatActivo(chatContainer)) return
            try {
                chatContainer.quitarVistaSiExiste(loadingMsg)
            } catch (e: Exception) {
                Log.e(IA_LOG_TAG, "No se pudo quitar indicador de carga", e)
            }
        }

        fun agregarMensajeIa(texto: String) {
            if (!iaChatActivo(chatContainer)) return
            agregarVistaChatSegura(chatContainer, cardText(texto))
        }

        fun reportarErrorEtapa(etapa: String, e: Exception) {
            Log.e(IA_LOG_TAG, "Error en $etapa", e)
            if (!solicitudVigente() || !iaChatActivo(chatContainer)) return
            quitarCargando()
            agregarMensajeIa("Error ($etapa): ${e.localizedMessage ?: "fallo inesperado"}")
        }

        fun invocarAsistenteNube(contextData: HashMap<String, Any>) {
            if (!solicitudVigente() || !iaChatActivo(chatContainer)) return
            try {
                functions.getHttpsCallable("asistenteOpenAI")
                    .call(contextData)
                    .addOnSuccessListener { result ->
                        if (!solicitudVigente() || !iaChatActivo(chatContainer)) return@addOnSuccessListener
                        try {
                            quitarCargando()
                            val resultMap = result.data as? Map<*, *>
                            val resultText = resultMap?.get("respuesta")?.toString().orEmpty()
                            val newId = resultMap?.get("responseId")?.toString()
                            newId?.let { lastAIResponseId = it }

                            if (resultText.isBlank()) {
                                agregarMensajeIa("Asistente: sin respuesta.")
                            } else {
                                manejarRespuestaIA(resultText, chatContainer, prompt, reintentos)
                            }
                        } catch (e: Exception) {
                            reportarErrorEtapa("respuesta IA", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(IA_LOG_TAG, "Fallo asistenteOpenAI", e)
                        if (!solicitudVigente() || !iaChatActivo(chatContainer)) return@addOnFailureListener
                        quitarCargando()
                        val local = intentarResolverOrdenLocalIA(prompt, chatContainer)
                        if (!local) {
                            agregarMensajeIa(mensajeErrorAsistenteNube(e))
                        }
                    }
            } catch (e: Exception) {
                reportarErrorEtapa("llamada asistente", e)
            }
        }

        val contextoEnviado = AtomicBoolean(false)

        fun construirContextoYEnviar(perfil: String, stockInfo: String, stockContextMode: String, historial: String, reglasAprendidas: String) {
            if (!solicitudVigente() || !iaChatActivo(chatContainer)) return
            if (!contextoEnviado.compareAndSet(false, true)) return

            Thread {
                try {
                    iaContextoRemotoCache = IaContextoRemotoCache(
                        stockInfo = stockInfo,
                        stockContextMode = stockContextMode,
                        historial = historial,
                        reglasAprendidas = reglasAprendidas,
                    )
                    val ordenLocal = interpretarOrdenLocal(prompt)
                    val moduloDetectado = ordenLocal["modulo"].orEmpty().ifBlank { detectarModuloDesdePrompt(prompt) }
                    val fraseProducto = ordenLocal["producto"].orEmpty().ifBlank {
                        BusquedaInventarioIA.extraerFraseProducto(prompt)
                    }
                    val coincidenciasLocales = resolverSugerenciasProductoIA(
                        moduloDetectado,
                        fraseProducto.ifBlank { prompt },
                        "",
                        8,
                    ).joinToString("\n") { (producto, score) ->
                        "${producto.modulo}|${producto.item}|${producto.referencia}|similitud=${"%.2f".format(Locale.US, score)}"
                    }
                    val contextData = hashMapOf<String, Any>(
                        "mensaje" to prompt,
                        "perfil" to perfil,
                        "stockInfo" to stockInfo.take(3500),
                        "stockContextMode" to stockContextMode,
                        "historial" to historial.take(1200),
                        "memoriaEvolutiva" to reglasAprendidas.take(1200),
                        "memoriaLocal" to db.obtenerMemoria().take(1200),
                        "catalogo" to catalogoComoTextoFiltrado(prompt, limite = 45, moduloHint = moduloDetectado),
                        "herramientas" to herramientasComoTextoFiltrado(prompt, limite = 20),
                        "fraseProducto" to fraseProducto,
                        "moduloDetectado" to moduloDetectado,
                        "ordenInterpretada" to ordenLocal.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    "coincidenciasLocales" to coincidenciasLocales.take(1200),
                    "historialConversacion" to conversacionIAJsonParaNube(),
                    "esAutocritica" to (reintentos > 0),
                )
                    lastAIResponseId?.let { contextData["previousResponseId"] = it }
                    runOnUiThread {
                        if (!solicitudVigente() || !iaChatActivo(chatContainer)) {
                            contextoEnviado.set(false)
                            return@runOnUiThread
                        }
                        invocarAsistenteNube(contextData)
                    }
                } catch (e: Exception) {
                    contextoEnviado.set(false)
                    runOnUiThread {
                        reportarErrorEtapa("preparar contexto", e)
                    }
                }
            }.start()
        }

        fun iniciarCargaRemota(perfil: String) {
            val cache = iaContextoRemotoCache
            val promptNorm = normalizarBusqueda(prompt)
            val fraseProductoCache = BusquedaInventarioIA.extraerFraseProducto(prompt)
            val permitirCache = !(esConsultaInventarioProductoIA(promptNorm) && fraseProductoCache.isNotBlank())
            if (permitirCache && cache != null && cache.vigente()) {
                construirContextoYEnviar(perfil, cache.stockInfo, cache.stockContextMode, cache.historial, cache.reglasAprendidas)
                return
            }

            var stockInfo = ""
            var stockContextMode = "contexto local"
            var historial = ""
            var reglasAprendidas = ""
            val partesListas = AtomicInteger(0)

            fun intentarEnviarContexto() {
                if (partesListas.get() < 3 || !solicitudVigente()) return
                construirContextoYEnviar(perfil, stockInfo, stockContextMode, historial, reglasAprendidas)
            }

            fun marcarParteLista() {
                partesListas.incrementAndGet()
                intentarEnviarContexto()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (!solicitudVigente() || contextoEnviado.get()) return@postDelayed
                Log.w(IA_LOG_TAG, "Timeout contexto IA, enviando con datos parciales")
                construirContextoYEnviar(perfil, stockInfo, "$stockContextMode; timeout parcial", historial, reglasAprendidas)
            }, performanceConfig.aiContextTimeoutMs())

            val moduloPromptIA = detectarModuloDesdePrompt(prompt)
            val fraseStockIA = BusquedaInventarioIA.extraerFraseProducto(prompt)
            val limiteStockIA = when {
                ModulosInventario.esModuloAgroquimico(moduloPromptIA) ->
                    maxOf(performanceConfig.aiStockLimit(), 30)
                moduloPromptIA.isNotBlank() ->
                    maxOf(performanceConfig.aiStockLimit(), 22)
                else -> performanceConfig.aiStockLimit()
            }

            fun aplicarStockAlmacen(docs: List<com.google.firebase.firestore.DocumentSnapshot>, origen: String) {
                if (!solicitudVigente()) return
                try {
                    val stockDocsFiltrados = filtrarDocumentosStockIA(
                        prompt,
                        docs,
                        limiteStockIA,
                        moduloPromptIA,
                    )
                    stockInfo = stockInfoDesdeDocumentos(stockDocsFiltrados, moduloPromptIA)
                    stockContextMode = buildString {
                        append(origen)
                        append(" | filtrado ${stockDocsFiltrados.size}/${docs.size} productos")
                        if (moduloPromptIA.isNotBlank()) append(" | modulo=$moduloPromptIA")
                    }
                } catch (e: Exception) {
                    Log.w(IA_LOG_TAG, "Stock IA parcial", e)
                } finally {
                    marcarParteLista()
                }
            }

            val contextoTallerIA = AppMode.incluyeTaller && esContextoInventarioTaller(prompt)
            val lubricantesTallerIA = AppMode.incluyeTaller && esConsultaLubricantesTaller(prompt)
            cargarExistenciasAlmacenParaIA(
                consulta = fraseStockIA.ifBlank { prompt },
                prompt = prompt,
                moduloForzado = if (contextoTallerIA || lubricantesTallerIA) {
                    ModulosInventario.LUBRICANTES_TALLER
                } else {
                    ""
                },
                soloTaller = contextoTallerIA || lubricantesTallerIA,
                onSuccess = { docs -> aplicarStockAlmacen(docs, "almacen nube") },
                onFailure = {
                    Log.w(IA_LOG_TAG, "Stock IA no disponible")
                    marcarParteLista()
                },
            )

            firestore.collection("movimientos")
                .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(performanceConfig.aiHistoryLimit())
                .get()
                .addOnSuccessListener { movSnapshot ->
                    if (!solicitudVigente()) return@addOnSuccessListener
                    try {
                        historial = movSnapshot.documents.joinToString("\n") { doc ->
                            "- ${campoDoc(doc, "fecha")}: ${campoDoc(doc, "tipoMovimiento", "tipo")} ${campoDoc(doc, "cantidad")} ${campoDoc(doc, "unidad")} ${campoDoc(doc, "item", "producto")}"
                        }
                    } catch (e: Exception) {
                        Log.w(IA_LOG_TAG, "Historial IA parcial", e)
                    } finally {
                        marcarParteLista()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(IA_LOG_TAG, "Historial IA no disponible", e)
                    marcarParteLista()
                }

            firestore.collection("memoria_ia")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(performanceConfig.aiMemoryLimit())
                .get()
                .addOnSuccessListener { memSnapshot ->
                    if (!solicitudVigente()) return@addOnSuccessListener
                    try {
                        reglasAprendidas = memSnapshot.documents.joinToString("\n") {
                            "* ${it.getString("regla").orEmpty()}"
                        }
                    } catch (e: Exception) {
                        Log.w(IA_LOG_TAG, "Memoria IA parcial", e)
                    } finally {
                        marcarParteLista()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(IA_LOG_TAG, "Memoria IA no disponible", e)
                    marcarParteLista()
                }
        }

        try {
            if (!iaChatActivo(chatContainer)) return

            if (reintentos == 0) {
                guardarTurnoConversacionIA("usuario", prompt)
                agregarVistaChatSegura(
                    chatContainer,
                    cardText("Tú: $prompt").apply { setBackgroundColor(Color.rgb(240, 248, 255)) },
                )
            }

            if (!iaChatActivo(chatContainer)) return
            agregarVistaChatSegura(chatContainer, loadingMsg)

            val respondioRapido = try {
                reintentos == 0 && intentarRespuestaRapidaIA(prompt, chatContainer) { quitarCargando() }
            } catch (e: Exception) {
                Log.e(IA_LOG_TAG, "Fallo respuesta rápida, continuando con nube", e)
                false
            }
            if (respondioRapido) return

            val perfil = auth.currentUser?.email?.let { "Usuario: $it" } ?: "Usuario: invitado local"
            iniciarCargaRemota(perfil)
        } catch (e: Exception) {
            Log.e(IA_LOG_TAG, "Error inmediato al procesar orden", e)
            quitarCargando()
            agregarMensajeIa("No pude procesar la orden: ${e.localizedMessage ?: "error inesperado"}")
        }
    }

private fun extractJsonFromText(text: String): String? {
    val start = text.indexOf("{")
    val end = text.lastIndexOf("}")
    if (start in 0 until end) {
        return text.substring(start, end + 1)
    }
    return null
}

internal fun MainActivity.manejarRespuestaIA(resultText: String, chatContainer: LinearLayout, promptOriginal: String, reintentos: Int = 0) {
    try {
        val jsonStr = extractJsonFromText(resultText)
        if (jsonStr != null) {
            val json = JSONObject(jsonStr)
            val accion = json.optString("accion")

            val inicioJson = resultText.indexOf("{")
            val msgText = if (inicioJson > 0) resultText.substring(0, inicioJson).trim() else ""
            val msgJson = json.optString("mensaje").trim()
            val mensajeVisible = msgText.ifBlank { msgJson }
            if (mensajeVisible.isNotEmpty() && iaChatActivo(chatContainer)) {
                guardarTurnoConversacionIA("asistente", mensajeVisible)
                agregarVistaChatSegura(chatContainer, cardText("Asistente: $mensajeVisible"))
                hablarAsistente(mensajeVisible)
            }

            when (accion) {
                "ejecutar" -> {
                    val tipo = json.optString("tipo").lowercase()
                    val moduloSugerido = json.optString("modulo")
                    val itemSugerido = json.optString("item")
                    val refSugerida = json.optString("referencia")
                    val cantValStr = json.optString("cantidad")
                    val solVal = json.optString("solicitante")
                    val labVal = json.optString("labor")
                    val unidadSugerida = json.optString("unidad")
                    val observaciones = json.optString("observaciones")
                    val maquinaria = json.optString("maquinaria")
                    val horometro = json.optString("horometro")
                    val codigoInterno = normalizarCodigoInterno(json.optString("codigo_interno"))

                    val moduloPrompt = detectarModuloDesdePrompt(promptOriginal)
                    val moduloNorm = moduloCanonicoInventario(
                        moduloSugerido.ifBlank { moduloPrompt },
                        itemSugerido,
                        refSugerida,
                    ).ifBlank { moduloSugerido.ifBlank { moduloPrompt } }
                    var producto = resolverProductoCatalogo(moduloNorm, itemSugerido, refSugerida)
                    val requiereCatalogo = tipo in setOf(
                        "salida", "entrada", "ingreso", "consumo", "prestamo", "devolucion", "entrega",
                    )
                    val sugerenciasPrevias = if (producto == null && itemSugerido.isNotBlank()) {
                        resolverSugerenciasProductoIA(moduloNorm, itemSugerido, refSugerida, 6)
                    } else {
                        emptyList()
                    }
                    if (producto == null && esCoincidenciaProductoSegura(sugerenciasPrevias)) {
                        producto = sugerenciasPrevias.first().first
                        agregarVistaChatSegura(
                            chatContainer,
                            cardText("Interpreté '$itemSugerido' como ${nombreProductoCatalogo(producto.modulo, producto.item, producto.referencia)}.")
                                .apply { setTextColor(verdeOscuro) },
                        )
                    }

                    if (producto == null && requiereCatalogo && itemSugerido.isNotBlank()) {
                        val sugerencias = sugerenciasPrevias.ifEmpty {
                            resolverSugerenciasProductoIA(moduloNorm, itemSugerido, refSugerida, 6)
                        }
                        if (sugerencias.isNotEmpty() && reintentos == 0) {
                            agregarVistaChatSegura(
                                chatContainer,
                                cardText("Encontré varias opciones parecidas a '$itemSugerido'. Elige la correcta:").apply { setTextColor(verdeOscuro) },
                            )
                            sugerencias.forEach { (p, score) ->
                                chatContainer.addView(cardText("  ↳ ${nombreProductoCatalogo(p.modulo, p.item, p.referencia)} (${"%.0f".format(Locale.US, score * 100)}% parecido)").apply { setTextColor(gris) })
                                chatContainer.addView(sugerenciaProductoCard(tipo, p, cantValStr, solVal, labVal, observaciones, maquinaria, horometro, codigoInterno, chatContainer))
                            }
                            return
                        }

                        if (reintentos == 0) {
                            chatContainer.addView(cardText("Validando producto... no encontré una coincidencia segura. Solicito corrección automática con catálogo completo.").apply { setTextColor(Color.RED) })
                            procesarPromptV5("El producto '$itemSugerido $refSugerida' no existe en el catálogo actual. Corrígelo usando exclusivamente el catálogo enviado y devuelve un JSON de acción válido.", chatContainer, reintentos = 1)
                            return
                        }
                    }

                    val moduloFinal = producto?.modulo ?: moduloNorm.ifBlank { moduloSugerido }
                    val itemBase = producto?.item ?: itemSugerido
                    val refFinal = producto?.referencia ?: refSugerida
                    val itemFinal = if (producto != null) nombreProductoCatalogo(producto.modulo, producto.item, producto.referencia) else itemSugerido
                    val unidadFinal = unidadSugerida.ifBlank { sugerirUnidadGeneral(moduloFinal, itemFinal) }

                    db.guardarMemoria("ultimo_registro", "$tipo de $itemFinal por $solVal")
                    chatContainer.addView(confirmActionCard(tipo, moduloFinal, itemFinal, cantValStr, solVal, labVal, refFinal, unidadFinal, itemBase, observaciones, maquinaria, horometro, codigoInterno))
                }
                "aprender" -> {
                    val regla = json.optString("regla")
                    val titulo = json.optString("titulo", "Nueva Lección")
                    guardarReglaAprendida(titulo, regla, chatContainer)
                }
                "sugerir_categoria" -> {
                    val sugerencia = json.optString("sugerencia")
                    chatContainer.addView(sugerenciaCard(sugerencia))
                }
                "prellenar_formulario" -> {
                    val modulo = json.optString("modulo")
                    val item = json.optString("item")
                    val ref = json.optString("referencia")
                    val cantidad = json.optString("cantidad")
                    val solicitante = json.optString("solicitante")
                    val labor = json.optString("labor")
                    val moduloNorm = moduloCanonicoInventario(modulo, item, ref).ifBlank { modulo }
                    val producto = resolverProductoCatalogo(moduloNorm, item, ref)
                    navegarDesdeIA {
                        abrirFormularioDesdeIA(
                            producto?.modulo ?: modulo,
                            producto?.let { nombreProductoCatalogo(it.modulo, it.item, it.referencia) } ?: item,
                            cantidad,
                            solicitante,
                            labor,
                        )
                    }
                }
                "herramienta" -> {
                    manejarAccionHerramientaIA(json, chatContainer)
                }
                "herramienta_movimiento" -> {
                    manejarAccionHerramientaIA(json, chatContainer)
                }
                "exportar" -> {
                    chatContainer.addView(cardText("Iniciando exportación..."))
                    val esPdf = json.optString("formato").lowercase(Locale.getDefault()).contains("pdf")
                    navegarDesdeIA {
                        if (esPdf) exportarPdf() else exportarCsv()
                    }
                }
                "corregir_registro" -> {
                    manejarCorreccionRegistroIA(json, chatContainer)
                }
                "registrar_vencimiento" -> {
                    manejarVencimientoIA(json, chatContainer)
                }
                "backup" -> {
                    navegarDesdeIA { ejecutarBackupManual() }
                }
                "sincronizar_offline" -> {
                    navegarDesdeIA { intentarSincronizarPendientes() }
                }
                "detectar_inconsistencias" -> {
                    navegarDesdeIA { showInconsistenciasScreen() }
                }
                "auditoria" -> {
                    navegarDesdeIA { showPanelAuditoria() }
                }
                "importar_inventario" -> {
                    navegarDesdeIA { abrirImportadorInventario() }
                }
                "consultar_datos", "consultar_firestore" -> {
                    manejarConsultaDatosIA(json, chatContainer)
                }
                "navegar" -> {
                    when (json.optString("pantalla")) {
                        "inventario" -> navegarDesdeIA { showInventarioScreen() }
                        "historial" -> navegarDesdeIA { showTablaMovimientos() }
                        "herramientas", "taller" -> {
                            if (AppMode.incluyeTaller) navegarDesdeIA { showHerramientasMenu() }
                            else chatContainer.addView(cardText("Taller ahora se maneja desde la app Taller independiente."))
                        }
                        "auditoria" -> navegarDesdeIA { showPanelAuditoria() }
                        "inconsistencias" -> navegarDesdeIA { showInconsistenciasScreen() }
                        "registros" -> navegarDesdeIA { showRegistrosMenu() }
                        "configuracion", "reportes", "graficos", "informacion" -> navegarDesdeIA { showInfoApp() }
                        else -> chatContainer.addView(cardText("No encontré esa pantalla específica."))
                    }
                }
                else -> chatContainer.addView(cardText("Asistente: $resultText"))
            }
            return
        }

        if (iaChatActivo(chatContainer)) {
            guardarTurnoConversacionIA("asistente", resultText)
            agregarVistaChatSegura(chatContainer, cardText("Asistente: $resultText"))
            hablarAsistente(resultText)
        }
    } catch (e: Exception) {
        if (iaChatActivo(chatContainer)) {
            agregarVistaChatSegura(chatContainer, cardText("Asistente: $resultText"))
            chatContainer.addView(cardText("Advertencia: la acción automática no se pudo interpretar: ${e.localizedMessage}"))
        }
    }
}

internal fun MainActivity.sugerenciaProductoCard(
        tipo: String,
        p: ProductoCatalogo,
        cant: String,
        sol: String,
        lab: String,
        obs: String,
        maq: String,
        hor: String,
        cod: String,
        chatContainer: LinearLayout
    ): LinearLayout {
        val row = LinearLayout(this)
        row.apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), gris)
                cornerRadius = dp(8).toFloat()
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, dp(6))
            layoutParams = params

            addView(TextView(this@sugerenciaProductoCard).apply {
                text = nombreProductoCatalogo(p.modulo, p.item, p.referencia)
                textSize = 13f
                setTextColor(texto)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(Button(this@sugerenciaProductoCard).apply {
                text = "Usar"
                textSize = 12f
                setPadding(dp(8), 0, dp(8), 0)
                setAnimatedClick(this) {
                    val cardPadre = row.parent as? LinearLayout
                    if (cardPadre != null) {
                        val viewsToRemove = mutableListOf<View>()
                        for (i in 0 until cardPadre.childCount) {
                            val v = cardPadre.getChildAt(i)
                            if (v.tag == "sugerencia_ia") viewsToRemove.add(v)
                        }
                        viewsToRemove.forEach { cardPadre.removeView(it) }
                    }

                    chatContainer.addView(confirmActionCard(tipo, p.modulo, nombreProductoCatalogo(p.modulo, p.item, p.referencia), cant, sol, lab, p.referencia, "", p.item, obs, maq, hor, cod))
                }
            }, LinearLayout.LayoutParams(dp(70), dp(36)))
            tag = "sugerencia_ia"
        }
        return row
    }

internal fun MainActivity.guardarReglaAprendida(titulo: String, regla: String, chatContainer: LinearLayout) {
        if (regla.isBlank()) {
            chatContainer.addView(cardText("No recibí la regla para guardar."))
            return
        }
        db.guardarMemoria(titulo.ifBlank { "regla" }, regla)
        registrarCambioLocal("IA_APRENDIZAJE", "IA", "", "Nueva regla: $titulo")
        val data = hashMapOf(
            "regla" to regla,
            "titulo" to titulo.ifBlank { "Regla" },
            "timestamp" to now(),
            "usuario" to (auth.currentUser?.email ?: "local")
        )
        firestore.collection("memoria_ia").add(data)
            .addOnSuccessListener {
                chatContainer.addView(learningCard(titulo.ifBlank { "Regla" }, regla))
                hablarAsistente("Regla guardada.")
            }
            .addOnFailureListener {
                chatContainer.addView(learningCard(titulo.ifBlank { "Regla local" }, regla))
            }
    }

internal fun MainActivity.learningCard(titulo: String, regla: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(240, 255, 240)) // Verde muy claro
                setStroke(dp(1), verde)
                cornerRadius = dp(12).toFloat()
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(8), 0, dp(12))
            layoutParams = params
            
            addView(TextView(this@learningCard).apply {
                text = "Nueva regla aprendida: $titulo"
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(verdeOscuro)
                textSize = 14f
            })
            addView(TextView(this@learningCard).apply {
                text = regla
                setTextColor(this@learningCard.texto)
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

internal fun MainActivity.sugerenciaCard(texto: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 248, 225)) // Ámbar muy claro
                setStroke(dp(1), Color.rgb(255, 143, 0))
                cornerRadius = dp(12).toFloat()
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(8), 0, dp(12))
            layoutParams = params
            
            addView(TextView(this@sugerenciaCard).apply {
                text = "Sugerencia de mejora"
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(191, 54, 12))
            })
            addView(TextView(this@sugerenciaCard).apply {
                text = texto
                setTextColor(this@sugerenciaCard.texto)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

internal fun MainActivity.confirmActionCard(
        tipo: String,
        modulo: String,
        item: String,
        cant: String,
        sol: String,
        lab: String,
        referencia: String = "",
        unidad: String = "",
        itemBase: String = "",
        observaciones: String = "",
        maquinaria: String = "",
        horometro: String = "",
        codigoInterno: String = ""
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 250, 250))
                setStroke(dp(2), verde)
                cornerRadius = dp(12).toFloat()
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(8), 0, dp(12))
            layoutParams = params
        }

        val detalle = buildString {
            append("Módulo: $modulo")
            append("\nProducto: $item")
            if (codigoInterno.isNotBlank()) append("\nCódigo interno: $codigoInterno")
            append("\nReferencia: ${emptyDash(referencia)}")
            append("\nCantidad: $cant ${emptyDash(unidad)}")
            append("\nSolicitante: $sol")
            append("\nLabor: ${emptyDash(lab)}")
            if (maquinaria.isNotBlank()) append("\nMaquinaria: $maquinaria")
            if (horometro.isNotBlank()) append("\nHorómetro: $horometro")
            if (observaciones.isNotBlank()) append("\nObservaciones: $observaciones")
        }

        card.addView(TextView(this).apply { 
            text = "CONFIRMAR REGISTRO (${tipo.uppercase()})"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verde)
        })

        card.addView(TextView(this).apply {
            text = detalle
        })

        val btnConfirm = Button(this).apply {
            text = "Confirmar y guardar"
            setBackgroundColor(verde)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Guardando..."
                ejecutarRegistroIA(tipo, modulo, item, cant, sol, lab, referencia, unidad, itemBase, observaciones, maquinaria, horometro, codigoInterno)
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        }
        card.addView(btnConfirm)
        return card
    }

internal fun MainActivity.ejecutarRegistroIA(
        tipo: String,
        modulo: String,
        item: String,
        cant: String,
        sol: String,
        lab: String,
        referencia: String = "",
        unidad: String = "",
        itemBase: String = "",
        observaciones: String = "",
        maquinaria: String = "",
        horometro: String = "",
        codigoInterno: String = ""
    ) {
        val cantVal = cant.replace(",", ".").toDoubleOrNull() ?: 0.0
        val uid = auth.currentUser?.uid ?: ""
        val uniVal = unidad.ifBlank { sugerirUnidadGeneral(modulo, item) }

        if (cantVal <= 0) {
            Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_LONG).show()
            return
        }
        
        if (tipo == "entrada") {
            val entrada = Entrada(
                fecha = now(),
                modulo = modulo,
                item = item,
                referencia = referencia,
                codigoInterno = normalizarCodigoInterno(codigoInterno),
                cantidad = cantVal,
                unidad = uniVal,
                observaciones = observaciones
            )
            val entradaIdLocal = db.insertarEntrada(entrada)
            registrarCambioLocal("CREAR_ENTRADA", entrada.modulo, entradaIdLocal.toString(), "Entrada registrada: ${entrada.item}", "", "${entrada.cantidad} ${entrada.unidad}")
            obtenerInfoUsuario(uid) { info ->
                val entMap = mapOf(
                    "fecha" to entrada.fecha,
                    "modulo" to entrada.modulo,
                    "tipoMovimiento" to "Entrada",
                    "codigo_interno" to entrada.codigoInterno,
                    "item" to entrada.item,
                    "item_base" to itemBase,
                    "referencia" to entrada.referencia,
                    "cantidad" to entrada.cantidad,
                    "unidad" to entrada.unidad,
                    "observaciones" to entrada.observaciones,
                    "registrado_por" to info
                )
                firestore.collection("entradas").add(entMap).addOnSuccessListener {
                    val codigoStock = entrada.codigoInterno.ifBlank { item }
                    registrarEntradaFirestoreYStock(
                        entrada.copy(codigoInterno = codigoStock),
                        codigoStock,
                        "",
                        itemBase.ifBlank { item },
                        referencia,
                        encolarSiFalla = true,
                    )
                }
            }
            saved("Entrada guardada")
        } else {
            val mov = Movimiento(
                fecha = now(),
                modulo = modulo,
                tipoMovimiento = "Salida",
                item = item,
                referencia = referencia,
                cantidad = cant,
                unidad = uniVal,
                solicitante = sol,
                labor = lab,
                maquinaria = maquinaria,
                horometro = horometro,
                observaciones = observaciones
            )
            registrarSalidaCloudPrimero(
                mov = mov,
                cantidadNumerica = cantVal,
                codigoInternoPreferido = codigoInterno,
                itemBase = itemBase,
                onSuccess = { saved("Salida guardada con trazabilidad en nube") }
            )
        }
    }

internal fun MainActivity.manejarConsultaDatosIA(json: JSONObject, chatContainer: LinearLayout) {
        val tipo = json.optString("tipo").lowercase(Locale.getDefault())
        val modulo = json.optString("modulo")
        val item = json.optString("item").ifBlank { json.optString("producto") }
        val referencia = json.optString("referencia")
        val solicitante = json.optString("solicitante").ifBlank { json.optString("responsable") }
        val texto = json.optString("texto").ifBlank { json.optString("consulta") }
        val limite = json.optInt("limite", 8).coerceIn(1, 20)

        when {
            tipo.contains("stock") || tipo.contains("exist") || texto.contains("stock", true) || texto.contains("inventario", true) -> {
                consultarExistenciasNube(item.ifBlank { texto }, referencia, modulo, limite, chatContainer)
            }
            tipo.contains("entrada") -> {
                consultarColeccionOperativa("entradas", item, referencia, modulo, solicitante, texto, limite, chatContainer)
            }
            tipo.contains("herramient") -> {
                consultarHerramientasLocales(item.ifBlank { texto }, limite, chatContainer)
            }
            tipo.contains("venc") -> {
                consultarVencimientosLocales(item.ifBlank { texto }, limite, chatContainer)
            }
            else -> {
                consultarColeccionOperativa("movimientos", item, referencia, modulo, solicitante, texto, limite, chatContainer)
            }
        }
    }

internal fun MainActivity.consultarExistenciasNube(item: String, referencia: String, modulo: String, limite: Int, chatContainer: LinearLayout) {
        val consultaOriginal = listOf(item, referencia).filter { it.isNotBlank() }.joinToString(" ").trim()
            .ifBlank { BusquedaInventarioIA.extraerFraseProducto(item) }
        val promptConsulta = "$item $referencia $modulo".trim()

        cargarExistenciasAlmacenParaIA(
            consulta = consultaOriginal,
            prompt = promptConsulta,
            onSuccess = { docs ->
                if (iaChatActivo(chatContainer)) {
                    val resultados = docs.take(limite).map { doc ->
                        val cantidad = numeroDocumento(doc, "cantidad", "stock_actual")
                        val unidad = doc.getString("unidad") ?: ""
                        val codigo = codigoExistencia(doc)
                        val producto = productoVisibleExistencia(doc)
                        val ubicacion = ubicacionExistencia(doc)
                        val zona = if (ubicacion.isBlank()) "" else " · $ubicacion"
                        "$codigo · $producto$zona: ${cantidadTexto(cantidad)} $unidad"
                    }
                    if (resultados.isEmpty() && consultaOriginal.isNotBlank()) {
                        chatContainer.addView(cardText("No encontré '$consultaOriginal' en el almacén. Revisa Inventario → Agroquímicos."))
                    } else {
                        mostrarResultadosConsultar(chatContainer, "Existencias en almacén", resultados)
                    }
                }
            },
            onFailure = {
                if (iaChatActivo(chatContainer)) {
                    val locales = db.obtenerEntradas().groupBy { it.item }.map { (nombre, entradas) ->
                        val stock = db.calcularStockLocal(nombre)
                        val unidad = entradas.firstOrNull()?.unidad ?: ""
                        "$nombre: $stock $unidad"
                    }.filter { coincideConsulta(it, item.ifBlank { referencia }) }.take(limite)
                    mostrarResultadosConsultar(chatContainer, "Existencias locales", locales)
                }
            },
        )
    }

internal fun MainActivity.consultarColeccionOperativa(
        coleccion: String,
        item: String,
        referencia: String,
        modulo: String,
        solicitante: String,
        texto: String,
        limite: Int,
        chatContainer: LinearLayout
    ) {
        val campoFecha = if (coleccion == "entradas") "fecha" else "fecha"
        firestore.collection(coleccion)
            .orderBy(campoFecha, com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit((limite * 8).toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                if (!iaChatActivo(chatContainer)) return@addOnSuccessListener
                val filtro = listOf(item, referencia, modulo, solicitante, texto).joinToString(" ")
                val resultados = snapshot.documents.mapNotNull { doc ->
                    val linea = if (coleccion == "entradas") {
                        val fecha = campoDoc(doc, "fecha", "fechaRegistro")
                        val producto = campoDoc(doc, "item", "producto", "nombre")
                        val ref = campoDoc(doc, "referencia")
                        val cant = campoDoc(doc, "cantidad")
                        val unidad = campoDoc(doc, "unidad")
                        val mod = campoDoc(doc, "modulo")
                        val codigo = campoDoc(doc, "codigo_interno", "codigoInterno")
                        "$fecha · $mod · Entrada · $producto · $cant $unidad${if (codigo.isNotBlank()) " · Cód: $codigo" else ""}${if (ref.isNotBlank()) " · Ref: $ref" else ""}"
                    } else {
                        val fecha = campoDoc(doc, "fecha", "fechaRegistro")
                        val mod = campoDoc(doc, "modulo")
                        val tipo = campoDoc(doc, "tipoMovimiento", "tipo")
                        val producto = campoDoc(doc, "item", "producto", "nombre")
                        val cant = campoDoc(doc, "cantidad")
                        val unidad = campoDoc(doc, "unidad")
                        val sol = campoDoc(doc, "solicitante", "responsable")
                        "$fecha · $mod · $tipo · $producto · $cant $unidad${if (sol.isNotBlank()) " · $sol" else ""}"
                    }
                    if (coincideConsulta(linea, filtro)) linea else null
                }.take(limite)
                mostrarResultadosConsultar(chatContainer, if (coleccion == "entradas") "Entradas" else "Movimientos", resultados)
            }
            .addOnFailureListener {
                if (!iaChatActivo(chatContainer)) return@addOnFailureListener
                val resultados = if (coleccion == "entradas") {
                    db.obtenerEntradas().map { e ->
                        "${e.fecha} · ${e.modulo} · Entrada · ${e.item} · ${e.cantidad} ${e.unidad}${if (e.codigoInterno.isNotBlank()) " · Cód: ${e.codigoInterno}" else ""}${if (e.referencia.isNotBlank()) " · Ref: ${e.referencia}" else ""}"
                    }
                } else {
                    db.obtenerMovimientos().map { m ->
                        "${m.fecha} · ${m.modulo} · ${m.tipoMovimiento} · ${m.item} · ${m.cantidad} ${m.unidad}${if (m.solicitante.isNotBlank()) " · ${m.solicitante}" else ""}"
                    }
                }.filter { coincideConsulta(it, listOf(item, referencia, modulo, solicitante, texto).joinToString(" ")) }.take(limite)
                mostrarResultadosConsultar(chatContainer, "Consulta local", resultados)
            }
    }

internal fun MainActivity.consultarHerramientasLocales(filtro: String, limite: Int, chatContainer: LinearLayout) {
        val consulta = BusquedaInventarioIA.extraerFraseProducto(filtro).ifBlank { filtro }
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val resultados = consultarHerramientasLocalesLineas(consulta, tokens, limite)
        mostrarResultadosConsultar(chatContainer, "Herramientas", resultados)
    }

internal fun MainActivity.consultarVencimientosLocales(filtro: String, limite: Int, chatContainer: LinearLayout) {
        val resultados = db.obtenerVencimientos().map { v ->
            "${v.fechaVencimiento} · ${v.modulo} · ${v.item}${if (v.lote.isNotBlank()) " · Lote: ${v.lote}" else ""} · ${v.estado}"
        }.filter { coincideConsulta(it, filtro) }.take(limite)
        mostrarResultadosConsultar(chatContainer, "Vencimientos", resultados)
    }

internal fun MainActivity.mostrarResultadosConsultar(chatContainer: LinearLayout, titulo: String, resultados: List<String>) {
        if (!iaChatActivo(chatContainer)) return
        val textoResultado = if (resultados.isEmpty()) {
            "$titulo: sin resultados."
        } else {
            "$titulo:\n" + resultados.joinToString("\n")
        }
        chatContainer.addView(cardText(textoResultado))
    }

internal fun campoDoc(doc: com.google.firebase.firestore.DocumentSnapshot, vararg claves: String): String {
        claves.forEach { clave ->
            val valor = doc.get(clave)
            if (valor != null) return valor.toString()
        }
        return ""
    }

internal fun coincideConsulta(texto: String, filtro: String): Boolean {
        val filtroNorm = normalizarBusqueda(filtro)
        if (filtroNorm.isBlank()) return true
        return BusquedaInventarioIA.puntuarTexto(filtro, texto) >= 0.32
    }

internal fun MainActivity.manejarVencimientoIA(json: JSONObject, chatContainer: LinearLayout) {
        val modulo = json.optString("modulo").ifBlank { "Químico" }
        val item = json.optString("item")
        val referencia = json.optString("referencia")
        val fecha = json.optString("fecha_vencimiento").ifBlank { json.optString("fechaVencimiento") }
        val lote = json.optString("lote")
        val cantidad = json.optString("cantidad")
        val observaciones = json.optString("observaciones")
        if (item.isBlank() || !fechaValida(fecha)) {
            chatContainer.addView(cardText("Necesito producto y fecha válida AAAA-MM-DD."))
            showVencimientosScreen()
            return
        }
        val vencimiento = Vencimiento(
            fechaRegistro = now(),
            modulo = modulo,
            item = item,
            referencia = referencia,
            fechaVencimiento = fecha,
            lote = lote,
            cantidad = cantidad,
            estado = estadoVencimiento(fecha),
            observaciones = observaciones
        )
        chatContainer.addView(confirmarVencimientoCard(vencimiento))
    }

internal fun MainActivity.confirmarVencimientoCard(vencimiento: Vencimiento): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 250, 250))
                setStroke(dp(2), verde)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(12))
            }
        }
        card.addView(TextView(this).apply {
            text = "CONFIRMAR VENCIMIENTO"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verde)
        })
        card.addView(TextView(this).apply {
            text = "Producto: ${vencimiento.item}\nRef: ${emptyDash(vencimiento.referencia)}\nVence: ${vencimiento.fechaVencimiento}\nLote: ${emptyDash(vencimiento.lote)}\nEstado: ${vencimiento.estado}"
            setPadding(0, dp(8), 0, dp(8))
            setTextColor(texto)
        })
        card.addView(Button(this).apply {
            text = "Confirmar y guardar"
            setBackgroundColor(verde)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Guardando..."
                val id = db.insertarVencimiento(vencimiento)
                registrarCambioLocal("CREAR_VENCIMIENTO", vencimiento.modulo, id.toString(), "Vencimiento registrado para ${vencimiento.item}", "", vencimiento.fechaVencimiento)
                firestore.collection("vencimientos").add(
                    mapOf(
                        "fechaRegistro" to vencimiento.fechaRegistro,
                        "modulo" to vencimiento.modulo,
                        "item" to vencimiento.item,
                        "referencia" to vencimiento.referencia,
                        "fechaVencimiento" to vencimiento.fechaVencimiento,
                        "lote" to vencimiento.lote,
                        "cantidad" to vencimiento.cantidad,
                        "estado" to vencimiento.estado,
                        "observaciones" to vencimiento.observaciones,
                    )
                )
                saved("Vencimiento guardado")
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        })
        return card
    }

internal fun MainActivity.manejarCorreccionRegistroIA(json: JSONObject, chatContainer: LinearLayout) {
        val id = json.optLong("id", -1L)
        val tipo = json.optString("tipo")
        val nuevaCantidad = json.optString("cantidad").ifBlank { json.optString("nueva_cantidad") }
        val nuevaUnidad = json.optString("unidad")
        val motivo = json.optString("motivo").ifBlank { json.optString("observaciones") }
        val movimiento = if (id > 0) db.obtenerMovimientoPorId(id) else db.obtenerUltimoMovimiento(tipo)

        if (movimiento == null) {
            chatContainer.addView(cardText("No encontré un registro local para corregir."))
            return
        }
        if (nuevaCantidad.replace(",", ".").toDoubleOrNull() == null || nuevaCantidad.replace(",", ".").toDouble() <= 0) {
            chatContainer.addView(cardText("¿Cuál es la cantidad correcta?"))
            return
        }
        chatContainer.addView(confirmarCorreccionCard(movimiento, nuevaCantidad, nuevaUnidad, motivo))
    }

internal fun MainActivity.confirmarCorreccionCard(movimiento: Movimiento, nuevaCantidad: String, nuevaUnidad: String, motivo: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 253, 245))
                setStroke(dp(2), Color.rgb(255, 143, 0))
                cornerRadius = dp(12).toFloat()
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(8), 0, dp(12))
            layoutParams = params
        }
        card.addView(TextView(this).apply {
            text = "CONFIRMAR CORRECCIÓN"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(191, 87, 0))
        })
        card.addView(TextView(this).apply {
            text = "Registro #${movimiento.id}\nProducto: ${movimiento.item}\nAntes: ${movimiento.cantidad} ${movimiento.unidad}\nDespués: $nuevaCantidad ${nuevaUnidad.ifBlank { movimiento.unidad }}\nMotivo: ${emptyDash(motivo)}"
            setTextColor(this@confirmarCorreccionCard.texto)
            setPadding(0, dp(6), 0, dp(8))
        })
        card.addView(Button(this).apply {
            text = "Confirmar corrección"
            setBackgroundColor(verde)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Corrigiendo..."
                ejecutarCorreccionMovimiento(movimiento, nuevaCantidad, nuevaUnidad, motivo)
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        })
        return card
    }

internal fun MainActivity.ejecutarCorreccionMovimiento(movimiento: Movimiento, nuevaCantidad: String, nuevaUnidad: String, motivo: String) {
        val oldCant = movimiento.cantidad.replace(",", ".").toDoubleOrNull() ?: 0.0
        val newCant = nuevaCantidad.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (newCant <= 0) {
            Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
            return
        }
        val unidadFinal = nuevaUnidad.ifBlank { movimiento.unidad }
        val nuevaObs = listOf(movimiento.observaciones, "Corregido ${now()}: $motivo").filter { it.isNotBlank() }.joinToString(" | ")
        db.actualizarMovimientoCantidad(movimiento.id, nuevaCantidad, unidadFinal, nuevaObs)
        registrarCambioLocal(
            "CORREGIR_REGISTRO",
            movimiento.modulo,
            movimiento.id.toString(),
            "Cantidad corregida en ${movimiento.item}",
            "${movimiento.cantidad} ${movimiento.unidad}",
            "$nuevaCantidad $unidadFinal"
        )

        val ajuste = oldCant - newCant
        if (movimiento.tipoMovimiento.equals("Salida", ignoreCase = true) && ajuste != 0.0) {
            actualizarStock(movimiento.item, kotlin.math.abs(ajuste), unidadFinal, ajuste > 0, solicitante = movimiento.solicitante, referencia = movimiento.referencia)
        }
        firestore.collection("correcciones_registros").add(
            mapOf(
                "fecha" to now(),
                "movimiento_id_local" to movimiento.id,
                "modulo" to movimiento.modulo,
                "item" to movimiento.item,
                "antes" to "${movimiento.cantidad} ${movimiento.unidad}",
                "despues" to "$nuevaCantidad $unidadFinal",
                "motivo" to motivo,
                "usuario" to (auth.currentUser?.email ?: "")
            )
        )
        saved("Registro corregido")
    }

internal fun MainActivity.manejarAccionHerramientaIA(json: JSONObject, chatContainer: LinearLayout) {
        if (!AppMode.incluyeTaller) {
            chatContainer.addView(cardText("Taller ahora se maneja desde la app Taller independiente."))
            return
        }
        val accion = json.optString("accion").lowercase(Locale.getDefault())
        val sub = json.optString("sub_accion").lowercase(Locale.getDefault())
        val tipoJson = json.optString("tipo").lowercase(Locale.getDefault())
        val nombre = json.optString("nombre").ifBlank { json.optString("herramienta") }
        val referencia = json.optString("referencia")
        val marca = json.optString("marca")
        val codigo = json.optString("codigo")
        val estado = json.optString("estado", "Disponible")
        val ubicacion = json.optString("ubicacion")
        val responsable = json.optString("responsable").ifBlank { json.optString("solicitante") }
        val solicitante = json.optString("solicitante").ifBlank { responsable }
        val labor = json.optString("labor").ifBlank { json.optString("destino") }
        val observaciones = json.optString("observaciones")
        val cantidad = json.optString("cantidad", "1")

        val submoduloDestino = json.optString("submodulo")
            .ifBlank { json.optString("destino") }
            .ifBlank { json.optString("submodulo_taller") }
        val esTrasladoBodega = tipoJson.contains("traslado") || sub.contains("traslado")
        val esIngresoBodega = tipoJson.contains("ingreso bodega") ||
            (tipoJson.contains("ingreso") && (sub.contains("bodega") || submoduloDestino.contains("bodega", true)))

        if (esTrasladoBodega || esIngresoBodega) {
            val herramienta = buscarHerramientaIA(nombre, codigo, referencia)
            if (esTrasladoBodega) {
                if (herramienta == null) {
                    chatContainer.addView(cardText("No encontré en Bodega Roja la herramienta para trasladar. Indica el código QR o el nombre exacto."))
                    navegarDesdeIA { showTrasladoBodegaRojaForm() }
                    return
                }
                chatContainer.addView(cardText("Abro traslado desde Bodega Roja para ${herramienta.nombre}${if (submoduloDestino.isNotBlank()) " hacia $submoduloDestino" else ""}."))
                navegarDesdeIA {
                    showTrasladoBodegaRojaForm(herramientaIdPreseleccionada = herramienta.id)
                }
                return
            }
            if (herramienta != null) {
                chatContainer.addView(cardText("Ese código ya existe. El ingreso a bodega es solo para productos nuevos."))
                return
            }
            chatContainer.addView(cardText("Abro ingreso de producto nuevo en Bodega Roja."))
            navegarDesdeIA {
                showIngresoProductoBodegaRojaForm(
                    prefCodigo = codigo,
                    prefCodigoQr = codigo,
                )
            }
            return
        }

        val tipoMovimiento = when {
            accion == "herramienta_movimiento" && tipoJson.contains("salida") -> "Salida"
            accion == "herramienta_movimiento" && (tipoJson.contains("entrada") || tipoJson.contains("devol")) -> "Entrada"
            sub.contains("salida") || sub.contains("presta") -> "Salida"
            sub.contains("entrada") || sub.contains("devol") || sub.contains("retorn") -> "Entrada"
            else -> ""
        }

        if (tipoMovimiento.isNotBlank()) {
            val herramienta = buscarHerramientaIA(nombre, codigo, referencia)
            if (herramienta == null) {
                chatContainer.addView(cardText("No encontré esa herramienta registrada. Puedes crearla primero o escribir el código/serial exacto."))
                if (nombre.isNotBlank()) chatContainer.addView(confirmarCrearHerramientaCard(nombre, referencia, marca, codigo, "Disponible", ubicacion, "", ""))
                return
            }
            if (solicitante.isBlank() || labor.isBlank()) {
                chatContainer.addView(cardText("Para guardar la $tipoMovimiento de ${herramienta.nombre} necesito solicitante y labor/destino. Te abro el formulario con la herramienta seleccionada."))
                navegarDesdeIA {
                    showMovimientoHerramientaForm(tipoMovimiento, herramienta.id, solicitante, labor, observaciones, herramienta.subModulo)
                }
                return
            }
            chatContainer.addView(confirmarMovimientoHerramientaCard(tipoMovimiento, herramienta, solicitante, labor, observaciones, cantidad))
            return
        }

        when {
            sub.contains("eliminar") || sub.contains("borrar") -> {
                val herramienta = buscarHerramientaIA(nombre, codigo, referencia)
                if (herramienta == null) {
                    chatContainer.addView(cardText("No encontré una herramienta segura para eliminar. Abro el listado para que la selecciones manualmente."))
                    navegarDesdeIA { showEliminarHerramientaForm() }
                } else {
                    chatContainer.addView(confirmarEliminarHerramientaCard(herramienta))
                }
            }
            sub.contains("añadir") || sub.contains("agregar") || sub.contains("registrar") || sub.contains("crear") || nombre.isNotBlank() -> {
                if (nombre.isBlank()) {
                    chatContainer.addView(cardText("Para registrar la herramienta necesito mínimo el nombre."))
                    navegarDesdeIA { showRegistroHerramientaForm() }
                } else {
                    chatContainer.addView(confirmarCrearHerramientaCard(nombre, referencia, marca, codigo, estado, ubicacion, responsable, observaciones))
                }
            }
            else -> {
                chatContainer.addView(cardText("Puedo registrar herramientas, hacer salidas, entradas/devoluciones o eliminar registros. Indícame la herramienta y los datos básicos."))
            }
        }
    }

internal fun MainActivity.buscarHerramientaIA(nombre: String, codigo: String = "", referencia: String = ""): Herramienta? {
        if (codigo.isNotBlank()) {
            buscarHerramientaTallerPorCodigo(codigo)?.let { return it }
        }

        val consulta = BusquedaInventarioIA.extraerFraseProducto(
            listOf(nombre, codigo, referencia).filter { it.isNotBlank() }.joinToString(" "),
        )
        if (consulta.isBlank()) return null
        val tokens = BusquedaInventarioIA.tokens(consulta)

        return herramientasTallerActivas().asSequence()
            .map { h ->
                val base = "${h.nombre} ${h.codigo} ${h.codigoQr} ${h.referencia} ${h.marca} ${h.subModulo} ${h.subcategoria} ${h.ubicacion}"
                h to BusquedaInventarioIA.puntuarTexto(consulta, base, tokens)
            }
            .filter { it.second >= 0.36 }
            .maxByOrNull { it.second }
            ?.first
    }

internal fun MainActivity.intentarResolverOrdenLocalIA(prompt: String, chatContainer: LinearLayout): Boolean {
        if (!iaChatActivo(chatContainer)) return false
        val promptNorm = normalizarBusqueda(prompt)

        if (AppMode.incluyeTaller && esPreguntaOcupacionHerramienta(prompt)) {
            resolverHerramientaEnPrompt(prompt)?.let { herramienta ->
                val respuesta = responderOcupacionHerramientaLocal(herramienta)
                chatContainer.addView(cardText(respuesta))
                hablarAsistente(respuesta)
                return true
            }
        }

        val frase = BusquedaInventarioIA.extraerFraseProducto(prompt)
        val tokens = BusquedaInventarioIA.tokens(frase.ifBlank { prompt })
        if (tokens.isEmpty() && frase.isBlank()) return false
        if (!AppMode.incluyeTaller && (esContextoInventarioTaller(prompt) || esConsultaLubricantesTaller(prompt))) {
            val respuesta = "Taller ahora se maneja desde la app Taller independiente."
            chatContainer.addView(cardText(respuesta))
            hablarAsistente(respuesta)
            return true
        }

        val moduloDetectado = detectarModuloDesdePrompt(prompt)
        val producto = resolverProductoDesdePrompt(prompt, moduloDetectado)
            ?: resolverSugerenciasProductoIA(moduloDetectado, frase.ifBlank { tokens.joinToString(" ") }, "", 1)
                .firstOrNull { esCoincidenciaProductoSegura(listOf(it)) }?.first
        if (promptNorm.contains("stock") || promptNorm.contains("cuanto") || promptNorm.contains("hay")) {
            val consultaStock = frase.ifBlank { producto?.item.orEmpty() }
            if (consultaStock.isNotBlank()) {
                if (AppMode.incluyeTaller && (esContextoInventarioTaller(prompt) || esConsultaLubricantesTaller(prompt))) {
                    if (!esConsultaLubricantesTaller(prompt) &&
                        responderStockHerramientasTallerIA(prompt, consultaStock, chatContainer) { }
                    ) {
                        return true
                    }
                    responderStockLubricantesTallerIA(prompt, consultaStock, chatContainer) { }
                } else {
                    consultarExistenciasNube(
                        consultaStock,
                        "",
                        producto?.modulo ?: moduloDetectado,
                        8,
                        chatContainer,
                    )
                }
                return true
            }
        }

        if (AppMode.incluyeTaller && (promptNorm.contains("herramienta") || promptNorm.contains("taller") || TallerCanonicos.esSubmoduloTaller(prompt))) {
            consultarHerramientasLocales(tokens.joinToString(" "), 8, chatContainer)
            return true
        }

        val sugerencias = resolverSugerenciasProductoIA(moduloDetectado, frase.ifBlank { tokens.joinToString(" ") }, "", 5)
        if (sugerencias.isNotEmpty()) {
            chatContainer.addView(cardText("Sin conexión al asistente. Coincidencias similares en inventario:").apply { setTextColor(verdeOscuro) })
            sugerencias.forEach { (p, score) ->
                chatContainer.addView(cardText("• [${p.modulo}] ${nombreProductoCatalogo(p.modulo, p.item, p.referencia)} (${"%.0f".format(Locale.US, score * 100)}% similitud)"))
            }
            return true
        }
        return false
    }

internal fun MainActivity.confirmarCrearHerramientaCard(
        nombre: String,
        referencia: String,
        marca: String,
        codigo: String,
        estado: String,
        ubicacion: String,
        responsable: String,
        observaciones: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 250, 250))
                setStroke(dp(2), verde)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(12))
            }
        }
        card.addView(TextView(this).apply {
            text = "CONFIRMAR REGISTRO DE HERRAMIENTA"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verde)
        })
        card.addView(TextView(this).apply {
            text = "Herramienta: $nombre\nReferencia: ${emptyDash(referencia)}\nMarca: ${emptyDash(marca)}\nCódigo/serial: ${emptyDash(codigo)}\nEstado: ${estado.ifBlank { "Disponible" }}\nUbicación: ${emptyDash(ubicacion)}\nResponsable: ${emptyDash(responsable)}"
            setPadding(0, dp(8), 0, dp(8))
            setTextColor(texto)
        })
        card.addView(Button(this).apply {
            text = "Confirmar y guardar herramienta"
            setBackgroundColor(verde)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Guardando..."
                ejecutarCrearHerramientaIA(nombre, referencia, marca, codigo, estado, ubicacion, responsable, observaciones)
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        })
        card.addView(Button(this).apply {
            text = "Editar manualmente"
            setAnimatedClick(this) {
                navegarDesdeIA {
                    showRegistroHerramientaForm(nombre, referencia, marca, codigo, estado, ubicacion, responsable, observaciones)
                }
            }
        })
        return card
    }

internal fun MainActivity.ejecutarCrearHerramientaIA(nombre: String, referencia: String, marca: String, codigo: String, estado: String, ubicacion: String, responsable: String, observaciones: String) {
        if (nombre.isBlank()) {
            Toast.makeText(this, "Falta el nombre de la herramienta", Toast.LENGTH_LONG).show()
            return
        }
        val herramienta = Herramienta(
            fechaRegistro = now(),
            nombre = nombre,
            referencia = referencia,
            marca = marca,
            codigo = codigo,
            estado = estado.ifBlank { "Disponible" },
            ubicacion = ubicacion,
            responsable = responsable,
            observaciones = observaciones
        )
        val idLocal = db.insertarHerramienta(herramienta)
        registrarCambioLocal("CREAR_HERRAMIENTA", TallerCanonicos.MODULO, idLocal.toString(), "Herramienta registrada: ${herramienta.nombre}", "", herramienta.estado)
        
        val uid = auth.currentUser?.uid ?: ""
        obtenerInfoUsuario(uid) { info ->
            val data = mapOf(
                "id_local" to idLocal,
                "fechaRegistro" to herramienta.fechaRegistro,
                "nombre" to herramienta.nombre,
                "referencia" to herramienta.referencia,
                "marca" to herramienta.marca,
                "codigo" to herramienta.codigo,
                "estado" to herramienta.estado,
                "ubicacion" to herramienta.ubicacion,
                "responsable" to herramienta.responsable,
                "observaciones" to herramienta.observaciones,
                "registrado_por" to info
            )
            guardarFirestoreOffline("herramientas", data)
        }
        saved("Herramienta guardada")
    }

internal fun MainActivity.confirmarMovimientoHerramientaCard(
        tipoMovimiento: String,
        herramienta: Herramienta,
        solicitante: String,
        labor: String,
        observaciones: String,
        cantidad: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 250, 250))
                setStroke(dp(2), verde)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(12))
            }
        }
        card.addView(TextView(this).apply {
            text = "CONFIRMAR $tipoMovimiento DE HERRAMIENTA"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verde)
        })
        card.addView(TextView(this).apply {
            text = "Herramienta: ${herramienta.nombre}\nCódigo/serial: ${emptyDash(herramienta.codigo)}\nReferencia: ${emptyDash(herramienta.referencia)}\nCantidad: ${cantidad.ifBlank { "1" }} Unidad\nSolicitante/recibe: $solicitante\nLabor/destino: $labor\nObservaciones: ${emptyDash(observaciones)}"
            setPadding(0, dp(8), 0, dp(8))
            setTextColor(texto)
        })
        card.addView(Button(this).apply {
            text = "Confirmar y guardar $tipoMovimiento"
            setBackgroundColor(verde)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Registrando..."
                ejecutarMovimientoHerramientaIA(tipoMovimiento, herramienta, solicitante, labor, observaciones, cantidad)
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        })
        card.addView(Button(this).apply {
            text = "Editar manualmente"
            setAnimatedClick(this) {
                navegarDesdeIA {
                    showMovimientoHerramientaForm(tipoMovimiento, herramienta.id, solicitante, labor, observaciones, herramienta.subModulo)
                }
            }
        })
        return card
    }

internal fun MainActivity.ejecutarMovimientoHerramientaIA(tipoMovimiento: String, herramienta: Herramienta, solicitante: String, labor: String, observaciones: String, cantidad: String) {
        if (solicitante.isBlank() || labor.isBlank()) {
            Toast.makeText(this, "Faltan solicitante y labor/destino", Toast.LENGTH_LONG).show()
            navegarDesdeIA {
                showMovimientoHerramientaForm(tipoMovimiento, herramienta.id, solicitante, labor, observaciones, herramienta.subModulo)
            }
            return
        }
        registrarMovimientoHerramienta(tipoMovimiento, herramienta, solicitante, labor, observaciones, cantidad)
    }

internal fun MainActivity.confirmarEliminarHerramientaCard(herramienta: Herramienta): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 245, 245))
                setStroke(dp(2), Color.RED)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(12))
            }
        }
        card.addView(TextView(this).apply {
            text = "CONFIRMAR ELIMINACIÓN"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.RED)
        })
        card.addView(TextView(this).apply {
            text = "${herramienta.nombre}\nCódigo: ${emptyDash(herramienta.codigo)}\nReferencia: ${emptyDash(herramienta.referencia)}"
            setPadding(0, dp(8), 0, dp(8))
        })
        card.addView(Button(this).apply {
            text = "Sí, eliminar"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setAnimatedClick(this) {
                isEnabled = false
                text = "Eliminando..."
                db.eliminarHerramienta(herramienta.id)
                registrarCambioLocal("ELIMINAR_HERRAMIENTA", TallerCanonicos.MODULO, herramienta.id.toString(), "Herramienta eliminada: ${herramienta.nombre}", herramienta.estado, "Eliminada")
                saved("Herramienta eliminada")
                Handler(Looper.getMainLooper()).postDelayed({
                    (card.parent as? LinearLayout)?.removeView(card)
                }, 600)
            }
        })
        return card
    }

internal fun MainActivity.herramientasComoTexto(): String {
        val herramientas = db.obtenerHerramientas()
        if (herramientas.isEmpty()) return "Sin herramientas registradas en la app."
        return herramientas.joinToString("\n") {
            "id=${it.id} | nombre=${it.nombre} | codigo=${it.codigo} | referencia=${it.referencia} | marca=${it.marca} | estado=${it.estado} | ubicacion=${it.ubicacion} | responsable=${it.responsable}"
        }.take(8000)
    }

internal fun MainActivity.resumirPatronesLocales(): String {
        val movimientos = db.obtenerMovimientos().take(80)
        if (movimientos.isEmpty()) return "Sin historial local suficiente para detectar patrones."
        return movimientos
            .groupBy { "${it.modulo} | ${it.item} | ${it.solicitante} | ${it.labor}" }
            .map { (clave, regs) -> clave to regs.size }
            .sortedByDescending { it.second }
            .take(12)
            .joinToString("\n") { "${it.first} -> ${it.second} veces recientes" }
    }
