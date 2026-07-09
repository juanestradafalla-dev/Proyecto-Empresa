@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Archivo modularizado con funciones de extensión de MainActivity.
// Mantiene el comportamiento original, pero separa responsabilidades para facilitar mantenimiento.

internal fun MainActivity.sincronizarCatalogo(
    usarCacheSiReciente: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val start = android.os.SystemClock.elapsedRealtime()
    android.util.Log.d(
        "PerfPrincipal",
        "sincronizarCatalogo inicio cache=${catalogoCargado.isNotEmpty()} diferible=$usarCacheSiReciente"
    )
    if (
        usarCacheSiReciente &&
        catalogoCargado.isNotEmpty() &&
        start - lastCatalogSyncAtMs < 60_000L
    ) {
        android.util.Log.d(
            "PerfPrincipal",
            "sincronizarCatalogo fin ${android.os.SystemClock.elapsedRealtime() - start}ms omitida=cache_reciente"
        )
        onDone?.invoke()
        return
    }
    onDone?.let { catalogoSyncCallbacks.add(it) }
    if (catalogoSincronizando) {
        catalogoSyncRepetirAlFinalizar = catalogoSyncRepetirAlFinalizar || !usarCacheSiReciente
        android.util.Log.d(
            "PerfPrincipal",
            "sincronizarCatalogo fin ${android.os.SystemClock.elapsedRealtime() - start}ms omitida=en_curso"
        )
        return
    }
    catalogoSincronizando = true

    fun finalizarCatalogo(resultado: String) {
        lastCatalogSyncAtMs = android.os.SystemClock.elapsedRealtime()
        catalogoSincronizando = false
        val callbacks = catalogoSyncCallbacks.toList()
        catalogoSyncCallbacks.clear()
        callbacks.forEach { callback -> runCatching { callback() } }
        val repetir = catalogoSyncRepetirAlFinalizar
        catalogoSyncRepetirAlFinalizar = false
        android.util.Log.d(
            "PerfPrincipal",
            "sincronizarCatalogo fin ${android.os.SystemClock.elapsedRealtime() - start}ms resultado=$resultado callbacks=${callbacks.size}"
        )
        if (repetir && pantallaActiva()) {
            sincronizarCatalogo()
        }
    }

    val catalogoFinal: MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>> = mutableMapOf()

    fun cargarCatalogoEstatico(destino: MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>>) {
        catalogoEstatico.forEach { (mod, catMap) ->
            if (mod.equals("EPP", ignoreCase = true) ||
                mod.equals("Dotación", ignoreCase = true) ||
                ModulosInventario.esModuloQuimicoOperativo(mod)
            ) return@forEach
            val moduloMap = destino.getOrPut(mod) { mutableMapOf() }
            @Suppress("UNCHECKED_CAST")
            (catMap as Map<String, Any>).forEach { (cat, itemMap) ->
                // Solo cargar si la categoría tiene items en el catálogo estático
                if (itemMap is Map<*, *> && itemMap.isNotEmpty()) {
                    val categoriaMap = moduloMap.getOrPut(cat) { mutableMapOf() }
                    @Suppress("UNCHECKED_CAST")
                    (itemMap as Map<String, List<String>>).forEach { (item, refList) ->
                        val listaRefs = categoriaMap.getOrPut(item) { mutableListOf() }
                        refList.forEach { ref ->
                            val refFinal = if (mod.equals("Dotación", ignoreCase = true)) referenciaDotacionPresentable(ref) else ref
                            if (!listaRefs.contains(refFinal)) listaRefs.add(refFinal)
                        }
                    }
                }
            }
        }
    }

    // 1. Iniciar con catálogo estático (Estructura base)
    cargarCatalogoEstatico(catalogoFinal)
    val dotacionEstructura = catalogoFinal.getOrPut("Dotación") { mutableMapOf() }
    listOf("Parte Superior", "Parte Inferior", "Conjunto", "Calzado").forEach { categoria ->
        dotacionEstructura.getOrPut(categoria) { mutableMapOf() }
    }
    QuimicosCanonicos.agregarAlCatalogo(catalogoFinal)
    AseoCanonicos.agregarAlCatalogo(catalogoFinal)
    if (catalogoCargado.isEmpty()) {
        catalogoCargado = catalogoFinal
    }

    // 2. Sincronizar desde Firestore con FILTRADO OBLIGATORIO
    firestore.collection("existencias").limit(2000).get()
        .addOnSuccessListener { existencias ->
            for (doc in existencias) {
                if (doc.getBoolean("activo") == false) continue
                val itemOriginal = nombreItemExistencia(doc)
                val referenciaOriginal = referenciaCatalogoExistencia(doc)
                val catOriginal = doc.getString("categoria").orEmpty()
                val moduloOriginal = doc.getString("modulo").orEmpty()
                val canonicoExistente = QuimicosCanonicos.buscarPorDocumento(doc.id)
                    ?: QuimicosCanonicos.buscarPorCodigoUbicacion(codigoOriginalExistencia(doc), ubicacionExistencia(doc), itemOriginal)
                if (canonicoExistente != null) {
                    agregarProductoAlCatalogo(catalogoFinal, canonicoExistente.modulo, canonicoExistente.categoria, canonicoExistente.subcategoria, canonicoExistente.item)
                    continue
                }
                val aseoCanonico = AseoCanonicos.buscarPorDocumento(doc.id)
                    ?: AseoCanonicos.buscarPorCodigoUbicacion(codigoOriginalExistencia(doc), ubicacionExistencia(doc), itemOriginal)
                    ?: AseoCanonicos.ubicaciones(codigoOriginalExistencia(doc), itemOriginal).firstOrNull()
                if (aseoCanonico != null) {
                    agregarProductoAlCatalogo(catalogoFinal, AseoCanonicos.MODULO, aseoCanonico.categoria, aseoCanonico.item, "N/A")
                    continue
                }
                val esDotacionCodificada = moduloNormalizado(moduloOriginal).contains("dotacion") ||
                    codigoExistencia(doc).startsWith("DOT", ignoreCase = true)
                val esConsumibleDeclarado = moduloNormalizado(moduloOriginal).contains("consumible")
                
                // REORDENAMIENTO INTELIGENTE OBLIGATORIO: Ignora lo que diga la DB si está mal
                val categoriaCorrecta = when {
                    esDotacionCodificada -> resolverCategoriaDotacion(itemOriginal, referenciaOriginal)
                    esConsumibleDeclarado && catOriginal.isNotBlank() -> catOriginal
                    else -> resolverCategoriaInteligente(itemOriginal, referenciaOriginal, catOriginal)
                }
                val moduloCorrecto = when {
                    esDotacionCodificada -> "Dotación"
                    esConsumibleDeclarado -> "Consumibles"
                    else -> resolverModuloInteligente(itemOriginal, referenciaOriginal, categoriaCorrecta)
                }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   // ESTRUCTURA SEGÚN MÓDULO: Agroquímicos usan subcategoría en el nivel 3
                val (item, referencia) = when {
                    ModulosInventario.esModuloQuimicoOperativo(moduloCorrecto) -> {
                        val subcat = subcategoriaExistencia(doc).ifBlank { "General" }
                        subcat to itemOriginal
                    }
                    moduloCorrecto.equals("EPP", ignoreCase = true) -> {
                        eppItemBaseYTalla(itemOriginal, referenciaOriginal)
                    }
                    else -> itemOriginal to referenciaOriginal
                }
                
                val tieneCodigo = codigoExistencia(doc).isNotBlank()
                val requiereCodigo = moduloCorrecto.equals("EPP", ignoreCase = true) ||
                    moduloCorrecto.equals("Dotación", ignoreCase = true)
                if (item.isNotBlank() && (!requiereCodigo || tieneCodigo)) {
                    agregarProductoAlCatalogo(catalogoFinal, moduloCorrecto, categoriaCorrecta, item, referencia.ifBlank { "N/A" })
                }
            }

            // 3. Limpiar categorías vacías y actualizar global
            catalogoFinal.forEach { (_, catMap) ->
                val iterator = catMap.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value.isEmpty()) iterator.remove()
                }
            }

            catalogoCargado = catalogoFinal
            db.guardarMemoria("catalogo_completo", Gson().toJson(catalogoFinal))
            android.util.Log.d("ArlesGestión", "CATÁLOGO: Sincronización limpia completada.")
            finalizarCatalogo("ok docs=${existencias.size()}")
        }
        .addOnFailureListener { e ->
            android.util.Log.e("ArlesGestión", "Error sincronizando catálogo: ${e.message}")
            try {
                val memoriaJson = db.obtenerMemoriaValor("catalogo_completo")
                if (memoriaJson.isNotBlank()) {
                    val tipo = object : TypeToken<MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>>>() {}.type
                    catalogoCargado = Gson().fromJson(memoriaJson, tipo)
                }
            } catch (ex: Exception) {
                android.util.Log.e("ArlesGestión", "Error cargando memoria offline: ${ex.message}")
            }
            finalizarCatalogo("error=${e.localizedMessage ?: "desconocido"}")
        }
}

internal fun MainActivity.agregarProductoAlCatalogo(
        catalogo: MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>>,
        modulo: String,
        categoria: String,
        item: String,
        referencia: String
    ) {
        // 1. LISTA NEGRA: Evitar basura de importación sin bloquear categorías válidas de Consumibles.
        val catNorm = normalizarBusqueda(categoria).uppercase(Locale.getDefault())
        val moduloNorm = normalizarBusqueda(modulo).uppercase(Locale.getDefault())
        if (esEntradaCatalogoSulcamagVieja(categoria) ||
            esEntradaCatalogoSulcamagVieja(item) ||
            esEntradaCatalogoSulcamagVieja(referencia)
        ) {
            android.util.Log.d("ArlesGestiÃ³n", "BLOQUEO UI: Ignorando entrada vieja de SULCAMAG")
            return
        }
        val prohibidas = listOf("IMPORTADO", "INVENTARIO IMPORTADO")
        if (prohibidas.contains(catNorm) || catNorm.contains("IMPORTADO")) {
            android.util.Log.d("ArlesGestión", "BLOQUEO UI: Ignorando categoría prohibida '$categoria'")
            return 
        }

        if (moduloNorm.contains("DOTACION")) {
            val dotacionPermitidas = setOf("PARTE SUPERIOR", "PARTE INFERIOR", "CONJUNTO", "CALZADO")
            if (catNorm !in dotacionPermitidas) {
                android.util.Log.d("ArlesGestión", "BLOQUEO UI: Ignorando categoría '$categoria' fuera de Dotación")
                return
            }
        }

        val moduloMap = catalogo.getOrPut(modulo.ifBlank { "General" }) { mutableMapOf() }
        val categoriaMap = moduloMap.getOrPut(categoria.ifBlank { "General" }) { mutableMapOf() }
        val listaRefs = categoriaMap.getOrPut(item.trim()) { mutableListOf() }
        val refFinal = if (moduloNorm.contains("DOTACION")) {
            referenciaDotacionPresentable(referencia)
        } else {
            referencia.trim().ifBlank { "N/A" }
        }
        
        // 2. FILTRO INTELIGENTE ANTI-DUPLICADOS:
        val refNorm = refFinal.uppercase(Locale.getDefault())
        val duplicadoExistente = listaRefs.find { 
            val existenteNorm = it.uppercase(Locale.getDefault())
            ((existenteNorm.contains(refNorm)) || (refNorm.contains(existenteNorm))) && (it != refFinal)
        }
        
        if (duplicadoExistente != null) {
            if (refFinal.length > duplicadoExistente.length) {
                listaRefs.remove(duplicadoExistente)
                listaRefs.add(refFinal)
            } else {
                return
            }
        } else {
            if (!listaRefs.contains(refFinal)) listaRefs.add(refFinal)
        }
    }

internal fun esEntradaCatalogoSulcamagVieja(valor: String): Boolean {
        val clave = normalizarBusqueda(valor).uppercase(Locale.getDefault()).replace(" ", "")
        return clave == "90SULCAMAG50KG" || clave.contains("90SULCAMAG50KG")
    }

internal fun MainActivity.obtenerInfoUsuario(uid: String, onResult: (String) -> Unit) {
        firestore.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val nombres = doc.getString("nombres") ?: "Usuario"
                val cargo = doc.getString("cargo") ?: ""
                val primerNombre = nombres.split(" ").getOrNull(0) ?: nombres
                onResult("$primerNombre ($cargo)")
            }
            .addOnFailureListener { onResult("Usuario") }
    }

internal fun MainActivity.nombreItemExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "item", "producto", "nombre").ifBlank { doc.id }
    }

internal fun MainActivity.codigoExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "codigo_interno", "id", "code").ifBlank { doc.id }
    }

internal fun MainActivity.codigoOriginalExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "codigo_original", "codigoOriginal", "codigo_excel", "codigo").ifBlank { codigoExistencia(doc) }
    }

internal fun MainActivity.subcategoriaExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "subcategoria", "subcategoría", "tipo", "grupo")
    }

internal fun MainActivity.ubicacionExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "ubicacion", "ubicación", "bodega", "zona").ifBlank { subcategoriaExistencia(doc) }
    }

internal fun MainActivity.referenciaExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "referencia", "ref", "modelo").ifBlank { "N/A" }
    }

internal fun MainActivity.marcaExistencia(doc: DocumentSnapshot): String {
        return campoDoc(doc, "marca", "brand", "fabricante")
    }

internal fun MainActivity.referenciaCatalogoExistencia(doc: DocumentSnapshot): String {
        val marca = marcaExistencia(doc)
        val ref = referenciaExistencia(doc)
        if (moduloNormalizado(doc.getString("modulo") ?: "").contains("dotacion")) {
            return referenciaDotacionPresentable(ref)
        }
        return listOf(marca, ref).filter { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }.joinToString(" - ").ifBlank { ref.ifBlank { "N/A" } }
    }

internal fun MainActivity.textoBusquedaExistencia(doc: DocumentSnapshot): String {
        val item = nombreItemExistencia(doc)
        val marca = marcaExistencia(doc)
        val ref = referenciaExistencia(doc)
        val cod = codigoExistencia(doc)
        val codigoOriginal = codigoOriginalExistencia(doc)
        val ubicacion = ubicacionExistencia(doc)
        val cat = doc.getString("categoria") ?: ""
        val mod = doc.getString("modulo") ?: ""
        
        return "$cod $codigoOriginal $item $marca $ref $ubicacion $cat $mod".lowercase(Locale.getDefault())
    }

internal fun MainActivity.productoVisibleExistencia(doc: DocumentSnapshot): String {
        val item = nombreItemExistencia(doc)
        val ref = referenciaCatalogoExistencia(doc)
        val ubicacion = ubicacionExistencia(doc)
        return listOf(item, ref, ubicacion).filter { it.isNotBlank() }.joinToString(" · ")
    }

internal fun MainActivity.puntuarCoincidenciaExistencia(doc: DocumentSnapshot, queryNorm: String, codNorm: String): Int {
        var puntos = 0
        val textoDoc = textoBusquedaExistencia(doc)
        val codDoc = normalizarCodigoInterno(codigoExistencia(doc))
        val codOriginalDoc = normalizarCodigoInterno(codigoOriginalExistencia(doc))
        if (codNorm.isNotBlank() && codDoc == codNorm) return 1000
        if (codNorm.isNotBlank() && codOriginalDoc == codNorm) puntos += 900
        if (codNorm.isNotBlank() && codDoc.contains(codNorm)) puntos += 500
        if (codNorm.isNotBlank() && codOriginalDoc.contains(codNorm)) puntos += 450
        val terminos = queryNorm.split(" ").filter { it.length > 2 }
        terminos.forEach { term -> if (textoDoc.contains(term)) puntos += 100 }
        if (textoDoc.startsWith(queryNorm)) puntos += 200
        return puntos
    }

internal fun MainActivity.buscarDocumentoExistencia(
        codigoOTexto: String,
        modulo: String,
        onFound: (DocumentSnapshot?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val queryNorm = normalizarBusqueda(codigoOTexto)
        val codNorm = normalizarCodigoInterno(codigoOTexto)
        val codigosCandidatos = candidatosCodigoInterno(codigoOTexto)

        fun busquedaFuzzy() {
            firestore.collection("existencias")
                .limit(100)
                .get()
                .addOnSuccessListener { snapshot ->
                    val docs = snapshot.documents.filter { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                    val mejor = docs.maxByOrNull { puntuarCoincidenciaExistencia(it, queryNorm, codNorm) }
                    if (mejor != null && puntuarCoincidenciaExistencia(mejor, queryNorm, codNorm) > 50) onFound(mejor)
                    else onFound(null)
                }
                .addOnFailureListener(onFailure)
        }

        fun buscarPorDocumento(indice: Int) {
            if (indice >= codigosCandidatos.size) {
                busquedaFuzzy()
                return
            }
            firestore.collection("existencias").document(codigosCandidatos[indice]).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && moduloCoincide(doc, modulo) && doc.getBoolean("activo") != false) onFound(doc)
                    else buscarPorDocumento(indice + 1)
                }
                .addOnFailureListener { buscarPorDocumento(indice + 1) }
        }

        fun buscarPorCodigoOriginal() {
            if (codigosCandidatos.isEmpty()) {
                busquedaFuzzy()
                return
            }
            firestore.collection("existencias")
                .whereIn("codigo_original", codigosCandidatos.take(10))
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents
                        .filter { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                        .maxByOrNull { puntuarCoincidenciaExistencia(it, queryNorm, codNorm) }
                    if (doc != null) onFound(doc)
                    else buscarPorDocumento(0)
                }
                .addOnFailureListener { buscarPorDocumento(0) }
        }

        fun buscarPorCodigoInterno() {
            if (codigosCandidatos.isEmpty()) {
                busquedaFuzzy()
                return
            }
            firestore.collection("existencias")
                .whereIn("codigo_interno", codigosCandidatos.take(10))
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.find { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                    if (doc != null) onFound(doc)
                    else buscarPorCodigoOriginal()
                }
                .addOnFailureListener { buscarPorCodigoOriginal() }
        }

        if (codNorm.length >= 2 || codigosCandidatos.any { it.length >= 2 }) buscarPorCodigoInterno() else busquedaFuzzy()
    }

internal fun MainActivity.moduloNormalizado(valor: String): String = normalizarBusqueda(valor).replace(" ", "")

internal fun MainActivity.moduloCoincide(doc: DocumentSnapshot, moduloEsperado: String): Boolean {
        val modDoc = moduloNormalizado(doc.getString("modulo") ?: "")
        val modEsp = moduloNormalizado(moduloEsperado)
        if (ModulosInventario.esModuloQuimicoOperativo(doc.getString("modulo") ?: "") ||
            ModulosInventario.esModuloQuimicoOperativo(moduloEsperado)
        ) {
            return ModulosInventario.modulosCompatibles(doc.getString("modulo") ?: "", moduloEsperado)
        }
        return modDoc == modEsp || modDoc.isBlank() || modEsp.isBlank()
    }

internal fun MainActivity.existenciaProductoDesdeDoc(doc: DocumentSnapshot, modulo: String): ExistenciaProducto {
        val item = nombreItemExistencia(doc)
        val referencia = referenciaExistencia(doc)
        val referenciaCatalogo = referenciaCatalogoExistencia(doc)
        val categoriaOriginal = doc.getString("categoria") ?: "Sin categoria"
        val subcategoriaOriginal = subcategoriaExistencia(doc)
        val ubicacion = ubicacionExistencia(doc)
        val codigoOriginal = codigoOriginalExistencia(doc)
        val moduloDoc = doc.getString("modulo") ?: modulo
        val esQuimico = ModulosInventario.esModuloQuimicoOperativo(moduloDoc) ||
            ModulosInventario.esModuloQuimicoOperativo(modulo)
        val esDotacion = moduloNormalizado(moduloDoc).contains("dotacion") ||
            codigoExistencia(doc).startsWith("DOT", ignoreCase = true)
        val categoriaCorregida = when {
            esQuimico -> categoriaOriginal
            esDotacion -> resolverCategoriaDotacion(item, referenciaCatalogo.ifBlank { referencia })
            else -> resolverCategoriaInteligente(item, referenciaCatalogo.ifBlank { referencia }, categoriaOriginal)
        }
        val esEpp = (doc.getString("modulo") ?: modulo).equals("EPP", ignoreCase = true)
        val (itemVisible, referenciaVisible) = if (esEpp) eppItemBaseYTalla(item, referenciaCatalogo.ifBlank { referencia }) else item to referenciaCatalogo
        return ExistenciaProducto(
            codigoInterno = codigoExistencia(doc),
            codigoOriginal = codigoOriginal,
            item = itemVisible,
            referencia = referencia,
            marca = marcaExistencia(doc),
            referenciaCatalogo = referenciaVisible,
            cantidad = numeroDocumento(doc, "cantidad", "stock_actual"),
            unidad = doc.getString("unidad") ?: sugerirUnidadGeneral(modulo, item),
            categoria = categoriaCorregida,
            subcategoria = subcategoriaOriginal.ifBlank { ubicacion },
            ubicacion = ubicacion,
            modulo = moduloDoc,
            documentoId = doc.id
        )
    }

internal fun MainActivity.buscarExistenciaPorCodigoInterno(
        codigo: String,
        modulo: String,
        onFound: (ExistenciaProducto?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val codigosCandidatos = candidatosCodigoInterno(codigo)
        fun entregarSiCoincide(doc: DocumentSnapshot?): Boolean {
            if (doc != null && doc.exists() && moduloCoincide(doc, modulo) && doc.getBoolean("activo") != false) {
                onFound(existenciaProductoDesdeDoc(doc, modulo))
                return true
            }
            return false
        }
        fun busquedaFuzzy() {
            buscarDocumentoExistencia(codigo, modulo, { doc ->
            if (doc != null) onFound(existenciaProductoDesdeDoc(doc, modulo))
            else onFound(null)
        }, onFailure)
        }
        fun buscarPorCodigoOriginal() {
            if (codigosCandidatos.isEmpty()) {
                busquedaFuzzy()
                return
            }
            firestore.collection("existencias").whereIn("codigo_original", codigosCandidatos.take(10)).get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents
                        .filter { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                        .maxByOrNull { puntuarCoincidenciaExistencia(it, normalizarBusqueda(codigo), normalizarCodigoInterno(codigo)) }
                    if (!entregarSiCoincide(doc)) busquedaFuzzy()
                }
                .addOnFailureListener { busquedaFuzzy() }
        }
        fun buscarPorCodigoCampo() {
            if (codigosCandidatos.isEmpty()) {
                busquedaFuzzy()
                return
            }
            firestore.collection("existencias").whereIn("codigo_interno", codigosCandidatos.take(10)).get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.find { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                    if (!entregarSiCoincide(doc)) buscarPorCodigoOriginal()
                }
                .addOnFailureListener { buscarPorCodigoOriginal() }
        }
        fun buscarPorDocumento(indice: Int) {
            if (indice >= codigosCandidatos.size) {
                buscarPorCodigoCampo()
                return
            }
            firestore.collection("existencias").document(codigosCandidatos[indice]).get()
                .addOnSuccessListener { doc -> if (!entregarSiCoincide(doc)) buscarPorDocumento(indice + 1) }
                .addOnFailureListener { buscarPorDocumento(indice + 1) }
        }
        if (codigosCandidatos.isNotEmpty()) buscarPorDocumento(0) else onFound(null)
    }

internal fun MainActivity.seleccionarProductoDesdeExistencia(
        producto: ExistenciaProducto,
        catSpinner: Spinner,
        itemSpinner: Spinner,
        refSpinner: Spinner,
        catalogo: Map<String, Any>,
        onDone: () -> Unit = {}
    ) {
        seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, catalogo, producto.categoria, producto.item, producto.referenciaCatalogo) {
            onDone()
        }
    }

internal fun MainActivity.buscarSugerenciasCodigoInterno(
        texto: String,
        modulo: String,
        onResult: (List<ExistenciaProducto>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val queryNorm = normalizarBusqueda(texto)
        val codNorm = normalizarCodigoInterno(texto)
        firestore.collection("existencias").limit(200).get()
            .addOnSuccessListener { snapshot ->
                val productos = snapshot.documents.asSequence()
                    .filter { moduloCoincide(it, modulo) && it.getBoolean("activo") != false }
                    .map { it to puntuarCoincidenciaExistencia(it, queryNorm, codNorm) }
                    .filter { it.second > 10 }
                    .sortedByDescending { it.second }
                    .take(15)
                    .map { existenciaProductoDesdeDoc(it.first, modulo) }
                    .toList()
                onResult(productos)
            }
            .addOnFailureListener(onFailure)
    }

internal fun tiposCombustible(): List<String> = listOf("Gasolina", "ACPM", "Urea")

internal fun itemBaseCombustible(): String = "Líquidos"

internal fun codigoDocumentoCombustible(tipo: String): String {
    val clave = normalizarBusqueda(tipo).uppercase(Locale.getDefault()).replace(" ", "-")
    return "COMB-${clave.ifBlank { "GENERAL" }}"
}

internal fun cantidadGalonesLegible(valor: Double): String {
    return if (valor % 1.0 == 0.0) valor.toInt().toString()
    else String.format(java.util.Locale.getDefault(), "%.2f", valor)
}

internal fun textoResumenStockCombustible(stock: Map<String, Double>): String {
    val lineas = tiposCombustible().map { tipo ->
        val galones = stock[tipo] ?: 0.0
        "$tipo: ${cantidadGalonesLegible(galones)} gal"
    }
    return "Stock actual:\n${lineas.joinToString("\n")}"
}

internal fun MainActivity.cargarResumenStockCombustible(
    onResult: (Map<String, Double>) -> Unit,
    onFailure: (Exception) -> Unit = { _ -> onResult(emptyMap()) }
) {
    firestore.collection("existencias")
        .whereEqualTo("modulo", "Combustible")
        .get()
        .addOnSuccessListener { snapshot ->
            val stock = tiposCombustible().associateWith { 0.0 }.toMutableMap()
            snapshot.documents.forEach { doc ->
                if (doc.getBoolean("activo") == false) return@forEach
                val cantidad = numeroDocumento(doc, "cantidad", "stock_actual")
                val tipo = tiposCombustible().find { coincideTipoCombustible(it, doc) } ?: return@forEach
                stock[tipo] = (stock[tipo] ?: 0.0) + cantidad
            }
            onResult(stock)
        }
        .addOnFailureListener(onFailure)
}

internal fun MainActivity.coincideTipoCombustible(tipo: String, doc: DocumentSnapshot): Boolean {
        val tipoNorm = normalizarBusqueda(tipo)
        if (tipoNorm.isBlank()) return false
        val textos = listOf(
            doc.id,
            nombreItemExistencia(doc),
            referenciaExistencia(doc),
            doc.getString("tipo").orEmpty(),
            doc.getString("combustible").orEmpty(),
            doc.getString("categoria").orEmpty(),
            doc.getString("subcategoria").orEmpty(),
            doc.getString("ubicacion").orEmpty(),
            codigoExistencia(doc),
            codigoOriginalExistencia(doc)
        ).map { normalizarBusqueda(it) }
        return textos.any { it == tipoNorm || it.contains(tipoNorm) }
    }

internal fun MainActivity.buscarExistenciaPorProducto(
        modulo: String,
        item: String,
        referencia: String,
        onResult: (ExistenciaProducto?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (modulo.equals("Combustible", ignoreCase = true)) {
            val tipo = item.trim().ifBlank { referencia.trim() }
            firestore.collection("existencias")
                .whereEqualTo("modulo", modulo)
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.find { doc ->
                        doc.getBoolean("activo") != false && coincideTipoCombustible(tipo, doc)
                    }
                    if (doc != null) onResult(existenciaProductoDesdeDoc(doc, modulo))
                    else onResult(null)
                }
                .addOnFailureListener(onFailure)
            return
        }

        if (modulo.equals("EPP", ignoreCase = true)) {
            firestore.collection("existencias")
                .whereEqualTo("modulo", modulo)
                .get()
                .addOnSuccessListener { snapshot -> 
                    val doc = snapshot.documents.find { doc ->
                        if (doc.getBoolean("activo") == false) return@find false
                        val (itemBase, talla) = eppItemBaseYTalla(nombreItemExistencia(doc), referenciaCatalogoExistencia(doc))
                        itemBase.equals(item, ignoreCase = true) && talla.equals(referencia, ignoreCase = true)
                    }
                    if (doc != null) onResult(existenciaProductoDesdeDoc(doc, modulo))
                    else onResult(null)
                }
                .addOnFailureListener(onFailure)
            return
        }

        if (modulo.equals(AseoCanonicos.MODULO, ignoreCase = true)) {
            val canonico = AseoCanonicos.buscarPorCodigoUbicacion(referencia, item = item)
                ?: AseoCanonicos.items.firstOrNull { it.producto.equals(item, ignoreCase = true) }
            val codigoDoc = canonico?.codigoInterno?.takeIf { it.isNotBlank() }
                ?: normalizarCodigoInterno(referencia).takeIf { it.isNotBlank() }
                ?: ""
            if (codigoDoc.isBlank()) {
                onResult(null)
                return
            }
            firestore.collection(AseoCanonicos.COLECCION).document(codigoDoc)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val ubicacion = aseoUbicacionDesdeDoc(doc)
                        onResult(
                            ExistenciaProducto(
                                documentoId = ubicacion?.documentoId ?: codigoDoc,
                                codigoInterno = codigoDoc,
                                codigoOriginal = codigoDoc,
                                modulo = AseoCanonicos.MODULO,
                                categoria = ubicacion?.categoria ?: canonico?.categoria.orEmpty(),
                                subcategoria = ubicacion?.ubicacion.orEmpty(),
                                item = ubicacion?.item ?: item,
                                marca = "",
                                referencia = "N/A",
                                referenciaCatalogo = "N/A",
                                cantidad = ubicacion?.cantidad ?: canonico?.stockOperativo ?: 0.0,
                                unidad = ubicacion?.unidad ?: canonico?.unidad.orEmpty(),
                                ubicacion = ubicacion?.ubicacion.orEmpty(),
                            ),
                        )
                    } else if (canonico != null) {
                        onResult(aseoExistenciaDesdeCanonico(canonico))
                    } else {
                        onResult(null)
                    }
                }
                .addOnFailureListener(onFailure)
            return
        }

        firestore.collection("existencias")
            .whereEqualTo("modulo", modulo)
                .whereEqualTo("item", item)
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.find { doc ->
                        val refDoc = referenciaCatalogoExistencia(doc)
                    doc.getBoolean("activo") != false && moduloCoincide(doc, modulo) && referenciasInventarioCoinciden(referencia, refDoc)
                }
                if (doc != null) onResult(existenciaProductoDesdeDoc(doc, modulo))
                else {
                    firestore.collection("existencias")
                        .whereEqualTo("item", item)
                        .get()
                        .addOnSuccessListener { fallbackSnapshot ->
                            val fallbackDoc = fallbackSnapshot.documents.find { doc ->
                                doc.getBoolean("activo") != false &&
                                    moduloCoincide(doc, modulo) &&
                                    referenciasInventarioCoinciden(referencia, referenciaCatalogoExistencia(doc))
                            }
                            if (fallbackDoc != null) onResult(existenciaProductoDesdeDoc(fallbackDoc, modulo))
                            else onResult(null)
                        }
                        .addOnFailureListener(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

internal fun MainActivity.autocompletarCodigoDesdeProducto(
        codigoField: AutoCompleteTextView,
        modulo: String,
        item: String,
        referencia: String,
        onResult: (ExistenciaProducto?) -> Unit = {}
    ) {
        buscarExistenciaPorProducto(modulo, item, referencia, { producto ->
            codigoField.tag = "auto_fill"
            if (producto != null) {
                codigoField.setText(producto.codigoInterno, false)
                codigoField.setSelection(codigoField.text.length)
            } else {
                codigoField.setText("", false)
            }
            codigoField.tag = null
            onResult(producto)
        }, { onResult(null) })
    }

internal fun MainActivity.setupCodigoInternoSalida(
        root: LinearLayout,
        codigoField: AutoCompleteTextView,
        moduloEsperado: String,
        onFound: (ExistenciaProducto) -> Unit
    ) {
        val opciones = mutableListOf<CodigoInternoOpcion>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, opciones)
        codigoField.threshold = 1
        codigoField.setAdapter(adapter)
        codigoField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && codigoField.text.isNotEmpty() && opciones.isNotEmpty()) codigoField.showDropDown()
        }

        val handler = Handler(Looper.getMainLooper())
        var ultimaConsulta = ""
        var busquedaPendiente: Runnable? = null
        var seleccionandoOpcion = false

        fun cargarSugerencias() {
            val texto = codigoField.text?.toString().orEmpty()
            val codigo = normalizarCodigoInterno(texto)
            val textoNormal = normalizarBusqueda(texto)
            if (codigo.isEmpty() && textoNormal.isEmpty()) {
                opciones.clear()
                adapter.notifyDataSetChanged()
                return
            }
            val llave = "$moduloEsperado|$codigo|$textoNormal"
            if (llave == ultimaConsulta) return
            ultimaConsulta = llave
            buscarSugerenciasCodigoInterno(texto, moduloEsperado, { productos ->
                if (ultimaConsulta != llave) return@buscarSugerenciasCodigoInterno
                opciones.clear()
                opciones.addAll(productos.map { CodigoInternoOpcion(it) })
                adapter.notifyDataSetChanged()
                if (productos.isNotEmpty()) {
                    if (codigoField.hasFocus()) codigoField.showDropDown()
                }
            }, { 
                // Error silencioso en sugerencias
            })
        }

        fun aplicarProducto(producto: ExistenciaProducto, actualizarTexto: Boolean = true) {
            seleccionandoOpcion = true
            if (actualizarTexto) {
                codigoField.setText(producto.codigoInterno, false)
                codigoField.setSelection(codigoField.text.length)
            }
            codigoField.dismissDropDown()
            onFound(producto)
            seleccionandoOpcion = false
        }

        fun buscarExacto(mostrarSinResultado: Boolean = false) {
            val codigo = normalizarCodigoInterno(codigoField.text.toString())
            if (codigo.length < 2) return
            buscarExistenciaPorCodigoInterno(codigo, moduloEsperado, { producto ->
                if (producto != null) aplicarProducto(producto)
                else if (mostrarSinResultado) Toast.makeText(this, "No encontré el código $codigo", Toast.LENGTH_LONG).show()
            }, {})
        }

        codigoField.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position) as? CodigoInternoOpcion ?: return@setOnItemClickListener
            aplicarProducto(seleccion.producto)
        }

        codigoField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (seleccionandoOpcion || codigoField.tag == "auto_fill") return
                busquedaPendiente?.let { handler.removeCallbacks(it) }
                busquedaPendiente = Runnable {
                    cargarSugerencias()
                    buscarExacto(false)
                }
                busquedaPendiente?.let { handler.postDelayed(it, 350) }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

internal fun MainActivity.codigoInternoValidadoParaSalida(codigoField: EditText, codigoSeleccionado: String): String {
    return codigoSeleccionado.trim().ifBlank { codigoField.text.toString().trim() }
}

internal fun MainActivity.sincronizarQuimicosCanonicosFirebase() {
        val start = android.os.SystemClock.elapsedRealtime()
        if (quimicosCanonicosVerificados) {
            android.util.Log.d("PerfPrincipal", "sincronizarQuimicosCanonicosFirebase fin 0ms omitida=cache_version")
            return
        }
        if (quimicosCanonicosSyncEnProceso) {
            android.util.Log.d("PerfPrincipal", "sincronizarQuimicosCanonicosFirebase fin 0ms omitida=en_curso")
            return
        }
        quimicosCanonicosSyncEnProceso = true
        android.util.Log.d("PerfPrincipal", "sincronizarQuimicosCanonicosFirebase inicio")

        fun finalizarQuimicosCanonicos(resultado: String, verificado: Boolean = false) {
            quimicosCanonicosSyncEnProceso = false
            if (verificado) quimicosCanonicosVerificados = true
            android.util.Log.d(
                "PerfPrincipal",
                "sincronizarQuimicosCanonicosFirebase fin ${android.os.SystemClock.elapsedRealtime() - start}ms resultado=$resultado"
            )
        }

        val markerRef = firestore.collection("sistema").document(QuimicosCanonicos.VERSION)
        markerRef.get()
            .addOnSuccessListener { marker ->
                if (marker.exists()) {
                    finalizarQuimicosCanonicos("marker_existente", verificado = true)
                    return@addOnSuccessListener
                }

                firestore.collection("existencias").limit(2000).get()
                    .addOnSuccessListener { snapshot ->
                        android.util.Log.d("PerfPrincipal", "sincronizarQuimicosCanonicosFirebase existencias docs=${snapshot.size()}")
                        val canonicos = QuimicosCanonicos.items
                        val canonIds = canonicos.map { it.documentoId }.toSet()
                        val operaciones = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

                        snapshot.documents
                            .filter {
                                ModulosInventario.esModuloQuimicoOperativo(it.getString("modulo").orEmpty()) &&
                                    it.id !in canonIds &&
                                    it.getBoolean("activo") != false
                            }
                            .forEach { doc ->
                                operaciones.add { batch ->
                                    batch.set(doc.reference, mapOf(
                                        "activo" to false,
                                        "modulo" to "Qu\u00edmico hist\u00f3rico",
                                        "archivado_por" to QuimicosCanonicos.VERSION,
                                        "archivado_fecha" to now()
                                    ), SetOptions.merge())
                                }
                            }

                        canonicos.forEach { producto ->
                            val data = mapOf(
                                "modulo" to producto.modulo,
                                "categoria" to producto.categoria,
                                "subcategoria" to producto.subcategoria,
                                "item" to producto.item,
                                "referencia" to producto.codigoOriginal,
                                "codigo_interno" to producto.codigoOriginal,
                                "documento_id" to producto.documentoId,
                                "producto_id" to producto.documentoId,
                                "codigo_original" to producto.codigoOriginal,
                                "ubicacion" to producto.ubicacion,
                                "cantidad" to producto.stockInicial,
                                "unidad" to producto.unidad,
                                "activo" to true,
                                "origen_canonico" to QuimicosCanonicos.FUENTE,
                                "version_canonica" to QuimicosCanonicos.VERSION,
                                "stock_base_columna_g" to producto.stockInicial,
                                "filas_excel" to producto.filasExcel,
                                "sistema_ignorado" to true,
                                "familia_operativa" to producto.modulo,
                                "ultima_fecha" to now(),
                                "ultimo_solicitante" to "Migraci\u00f3n qu\u00edmicos columna G",
                                "busqueda" to "${producto.documentoId} ${producto.codigoOriginal} ${producto.item} ${producto.categoria} ${producto.ubicacion}".lowercase(Locale.getDefault())
                            )
                            operaciones.add { batch ->
                                batch.set(firestore.collection("existencias").document(producto.documentoId), data, SetOptions.merge())
                                batch.set(firestore.collection("catalogo_personalizado").document(producto.documentoId), data, SetOptions.merge())
                            }
                        }

                        operaciones.add { batch ->
                            batch.set(markerRef, mapOf(
                                "version" to QuimicosCanonicos.VERSION,
                                "fuente" to QuimicosCanonicos.FUENTE,
                                "productos" to canonicos.size,
                                "agroquimicos" to canonicos.count { it.modulo == ModulosInventario.AGROQUIMICOS },
                                "lubricantes_taller" to canonicos.count { it.modulo == ModulosInventario.LUBRICANTES_TALLER },
                                "fecha" to now()
                            ), SetOptions.merge())
                        }

                        fun ejecutarChunk(indice: Int) {
                            if (indice >= operaciones.size) {
                                db.borrarMemoria("catalogo_completo")
                                sincronizarCatalogo()
                                android.util.Log.d("ArlesGesti\u00f3n", "Qu\u00edmicos can\u00f3nicos sincronizados: ${canonicos.size}")
                                finalizarQuimicosCanonicos("migrado operaciones=${operaciones.size}", verificado = true)
                                return
                            }
                            val batch = firestore.batch()
                            operaciones.drop(indice).take(240).forEach { it(batch) }
                            batch.commit()
                                .addOnSuccessListener { ejecutarChunk(indice + 240) }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("ArlesGesti\u00f3n", "Error migrando qu\u00edmicos: ${e.message}")
                                    finalizarQuimicosCanonicos("error_batch=${e.localizedMessage ?: "desconocido"}")
                                }
                        }

                        ejecutarChunk(0)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ArlesGesti\u00f3n", "No se pudo revisar existencias para qu\u00edmicos: ${e.message}")
                        finalizarQuimicosCanonicos("error_existencias=${e.localizedMessage ?: "desconocido"}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ArlesGesti\u00f3n", "No se pudo revisar marcador qu\u00edmicos: ${e.message}")
                finalizarQuimicosCanonicos("error_marker=${e.localizedMessage ?: "desconocido"}")
            }
    }

internal fun MainActivity.sincronizarAseoCanonicoFirebase() {
        val start = android.os.SystemClock.elapsedRealtime()
        if (aseoCanonicoVerificado) {
            android.util.Log.d("PerfPrincipal", "sincronizarAseoCanonicoFirebase fin 0ms omitida=cache_version")
            return
        }
        if (aseoCanonicoSyncEnProceso) {
            android.util.Log.d("PerfPrincipal", "sincronizarAseoCanonicoFirebase fin 0ms omitida=en_curso")
            return
        }
        aseoCanonicoSyncEnProceso = true
        android.util.Log.d("PerfPrincipal", "sincronizarAseoCanonicoFirebase inicio")

        fun finalizarAseoCanonico(resultado: String, verificado: Boolean = false) {
            aseoCanonicoSyncEnProceso = false
            if (verificado) aseoCanonicoVerificado = true
            android.util.Log.d(
                "PerfPrincipal",
                "sincronizarAseoCanonicoFirebase fin ${android.os.SystemClock.elapsedRealtime() - start}ms resultado=$resultado"
            )
        }

        val markerRef = firestore.collection("sistema").document(AseoCanonicos.VERSION)
        markerRef.get()
            .addOnSuccessListener { marker ->
                if (marker.exists()) {
                    finalizarAseoCanonico("marker_existente", verificado = true)
                    return@addOnSuccessListener
                }

                firestore.collection("existencias").limit(2000).get()
                    .addOnSuccessListener { existenciasSnapshot ->
                        android.util.Log.d("PerfPrincipal", "sincronizarAseoCanonicoFirebase existencias docs=${existenciasSnapshot.size()}")
                        firestore.collection(AseoCanonicos.COLECCION).get()
                            .addOnSuccessListener { aseoSnapshot ->
                                android.util.Log.d("PerfPrincipal", "sincronizarAseoCanonicoFirebase aseo docs=${aseoSnapshot.size()}")
                                val canonicos = AseoCanonicos.items
                                val canonIds = canonicos.map { it.codigoInterno }.toSet()
                                val stockExistente = mutableMapOf<String, Double>()
                                val operaciones = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

                                fun archivarProductoAseo(doc: DocumentSnapshot, motivo: String) {
                                    operaciones.add { batch ->
                                        val respaldo = doc.data?.toMutableMap() ?: mutableMapOf()
                                        respaldo["archivado_por"] = AseoCanonicos.VERSION
                                        respaldo["archivado_fecha"] = now()
                                        respaldo["archivado_motivo"] = motivo
                                        batch.set(
                                            firestore.collection("${AseoCanonicos.COLECCION}_archivados").document(doc.id),
                                            respaldo,
                                            SetOptions.merge()
                                        )
                                        batch.delete(doc.reference)
                                    }
                                }

                                existenciasSnapshot.documents
                                    .filter {
                                        it.getString("modulo").orEmpty().equals(AseoCanonicos.MODULO, ignoreCase = true) &&
                                            it.getBoolean("activo") != false
                                    }
                                    .forEach { doc ->
                                        operaciones.add { batch ->
                                            batch.set(doc.reference, mapOf(
                                                "activo" to false,
                                                "modulo" to "ASEO historico",
                                                "archivado_por" to AseoCanonicos.VERSION,
                                                "archivado_fecha" to now()
                                            ), SetOptions.merge())
                                        }
                                    }

                                aseoSnapshot.documents.forEach { doc ->
                                    val codigoNormalizado = AseoCanonicos.normalizarCodigo(doc.getString("codigo_interno").orEmpty().ifBlank { doc.id })
                                    val stock = when (val raw = doc.get("stock_actual")) {
                                        is Number -> raw.toDouble()
                                        is String -> raw.toDoubleOrNull()
                                        else -> null
                                    }
                                    if (codigoNormalizado in canonIds) {
                                        if (stock != null) stockExistente[codigoNormalizado] = stock
                                        if (doc.id != codigoNormalizado) archivarProductoAseo(doc, "codigo_normalizado_a_$codigoNormalizado")
                                    } else {
                                        archivarProductoAseo(doc, "fuera_del_catalogo_canonico")
                                    }
                                }

                                canonicos.forEach { producto ->
                                    val stock = stockExistente[producto.codigoInterno] ?: producto.stockOperativo
                                    operaciones.add { batch ->
                                        batch.set(
                                            firestore.collection(AseoCanonicos.COLECCION).document(producto.codigoInterno),
                                            producto.firestoreData(stock)
                                        )
                                    }
                                }
                                operaciones.add { batch ->
                                    batch.delete(firestore.collection(AseoCanonicos.COLECCION).document("H00-004"))
                                }

                                operaciones.add { batch ->
                                    batch.set(markerRef, mapOf(
                                        "version" to AseoCanonicos.VERSION,
                                        "fuente" to AseoCanonicos.FUENTE,
                                        "coleccion" to AseoCanonicos.COLECCION,
                                        "categoria_principal" to AseoCanonicos.CATEGORIA_PRINCIPAL,
                                        "productos" to canonicos.size,
                                        "fecha" to now()
                                    ), SetOptions.merge())
                                }

                                fun ejecutarChunk(indice: Int) {
                                    if (indice >= operaciones.size) {
                                        db.borrarMemoria("catalogo_completo")
                                        sincronizarCatalogo()
                                        android.util.Log.d("ArlesGestion", "ASEO canonico sincronizado: ${canonicos.size}")
                                        finalizarAseoCanonico("migrado operaciones=${operaciones.size}", verificado = true)
                                        return
                                    }
                                    val batch = firestore.batch()
                                    operaciones.drop(indice).take(240).forEach { it(batch) }
                                    batch.commit()
                                        .addOnSuccessListener { ejecutarChunk(indice + 240) }
                                        .addOnFailureListener { e ->
                                            android.util.Log.e("ArlesGestion", "Error migrando ASEO: ${e.message}")
                                            finalizarAseoCanonico("error_batch=${e.localizedMessage ?: "desconocido"}")
                                        }
                                }

                                ejecutarChunk(0)
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("ArlesGestion", "No se pudo revisar productos ASEO: ${e.message}")
                                finalizarAseoCanonico("error_aseo=${e.localizedMessage ?: "desconocido"}")
                            }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ArlesGestion", "No se pudo revisar existencias para ASEO: ${e.message}")
                        finalizarAseoCanonico("error_existencias=${e.localizedMessage ?: "desconocido"}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ArlesGestion", "No se pudo revisar marcador ASEO: ${e.message}")
                finalizarAseoCanonico("error_marker=${e.localizedMessage ?: "desconocido"}")
            }
    }

internal fun aseoUbicacionDesdeCanonico(producto: AseoCanonico): AseoUbicacionStock {
        return AseoUbicacionStock(
            documentoId = producto.codigoInterno,
            codigoOriginal = producto.codigoInterno,
            item = producto.producto,
            categoria = producto.categoria,
            ubicacion = producto.ubicacion,
            cantidad = producto.stockOperativo,
            unidad = producto.unidad
        )
    }

internal data class FilaInventarioAseo(
    val codigo: String,
    val producto: String,
    val piso: String,
    val stock: Double,
    val unidad: String,
    val categoria: String,
)

internal fun filasInventarioAseoUnificadas(docs: List<DocumentSnapshot>): List<FilaInventarioAseo> {
    val remotos = mutableMapOf<String, FilaInventarioAseo>()
    docs.forEach { doc ->
        try {
            val codigo = AseoCanonicos.normalizarCodigo(doc.getString("codigo_interno").orEmpty().ifBlank { doc.id })
            if (codigo.isBlank()) return@forEach
            val canonico = AseoCanonicos.buscarPorDocumento(codigo)
            val pisoInt = enteroDocumento(doc, "piso") ?: canonico?.piso ?: AseoCanonicos.pisoDesdeCodigo(codigo)
            remotos[codigo] = FilaInventarioAseo(
                codigo = codigo,
                producto = doc.getString("producto").orEmpty().ifBlank { canonico?.producto ?: codigo },
                piso = pisoInt.toString().padStart(2, '0'),
                stock = numeroDocumento(doc, "stock_actual"),
                unidad = doc.getString("unidad").orEmpty().ifBlank { canonico?.unidad ?: "UNIDAD" },
                categoria = doc.getString("categoria").orEmpty().ifBlank { canonico?.categoria ?: AseoCanonicos.CATEGORIA_PRINCIPAL },
            )
        } catch (_: Exception) {
        }
    }
    return AseoCanonicos.items.map { canonico ->
        remotos[canonico.codigoInterno] ?: FilaInventarioAseo(
            codigo = canonico.codigoInterno,
            producto = canonico.producto,
            piso = canonico.piso.toString().padStart(2, '0'),
            stock = canonico.stockOperativo,
            unidad = canonico.unidad,
            categoria = canonico.categoria,
        )
    }
}

internal fun aseoUbicacionDesdeDoc(doc: DocumentSnapshot): AseoUbicacionStock? {
        val codigo = AseoCanonicos.normalizarCodigo(doc.getString("codigo_interno").orEmpty().ifBlank { doc.id })
        if (codigo.isBlank()) return null
        val canonico = AseoCanonicos.buscarPorDocumento(codigo)
        val piso = enteroDocumento(doc, "piso") ?: canonico?.piso ?: AseoCanonicos.pisoDesdeCodigo(codigo)
        val productoDoc = doc.getString("producto").orEmpty()
        val producto = when {
            canonico != null && (productoDoc.isBlank() || AseoCanonicos.productoCoincide(canonico.producto, productoDoc)) -> canonico.producto
            else -> productoDoc.ifBlank { canonico?.producto ?: codigo }
        }
        val categoria = doc.getString("categoria").orEmpty().ifBlank { canonico?.categoria ?: AseoCanonicos.CATEGORIA_PRINCIPAL }
        val unidad = doc.getString("unidad").orEmpty().ifBlank { canonico?.unidad ?: "unidad" }
        return AseoUbicacionStock(
            documentoId = codigo,
            codigoOriginal = codigo,
            item = producto,
            categoria = categoria,
            ubicacion = "Piso $piso",
            cantidad = numeroDocumento(doc, "stock_actual"),
            unidad = unidad
        )
    }

internal fun aseoExistenciaDesdeCanonico(producto: AseoCanonico): ExistenciaProducto {
        return ExistenciaProducto(
            documentoId = producto.codigoInterno,
            codigoInterno = producto.codigoInterno,
            codigoOriginal = producto.codigoInterno,
            modulo = AseoCanonicos.MODULO,
            categoria = producto.categoria,
            subcategoria = producto.ubicacion,
            item = producto.producto,
            marca = "",
            referencia = "N/A",
            referenciaCatalogo = "N/A",
            cantidad = producto.stockOperativo,
            unidad = producto.unidad,
            ubicacion = producto.ubicacion
        )
    }

internal fun aseoFirestoreDataDesdeUbicacion(opcion: AseoUbicacionStock, stock: Double): Map<String, Any?> {
        return mapOf(
            "codigo_interno" to opcion.codigoOriginal,
            "piso" to AseoCanonicos.pisoDesdeCodigo(opcion.codigoOriginal),
            "categoria" to opcion.categoria,
            "producto" to opcion.item,
            "unidad" to opcion.unidad,
            "stock_actual" to stock
        )
    }

internal fun MainActivity.setupCodigoInternoAseo(
        codigoField: AutoCompleteTextView,
        onFound: (ExistenciaProducto) -> Unit
    ) {
        val opciones = mutableListOf<CodigoInternoOpcion>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, opciones)
        codigoField.threshold = 1
        codigoField.setAdapter(adapter)
        codigoField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && codigoField.text.isNotEmpty() && opciones.isNotEmpty()) codigoField.showDropDown()
        }

        val handler = Handler(Looper.getMainLooper())
        var busquedaPendiente: Runnable? = null
        var seleccionandoOpcion = false

        fun aplicar(producto: AseoCanonico, actualizarTexto: Boolean = true) {
            seleccionandoOpcion = true
            if (actualizarTexto) {
                codigoField.setText(producto.codigoInterno, false)
                codigoField.setSelection(codigoField.text.length)
            }
            codigoField.dismissDropDown()
            onFound(aseoExistenciaDesdeCanonico(producto))
            seleccionandoOpcion = false
        }

        fun cargarSugerencias() {
            val texto = codigoField.text?.toString().orEmpty()
            val productos = AseoCanonicos.buscarTexto(texto).take(15)
            opciones.clear()
            opciones.addAll(productos.map { CodigoInternoOpcion(aseoExistenciaDesdeCanonico(it)) })
            adapter.notifyDataSetChanged()
            if (productos.isNotEmpty() && codigoField.hasFocus()) codigoField.showDropDown()
            val codigo = normalizarCodigoInterno(texto)
            productos.firstOrNull { normalizarCodigoInterno(it.codigoInterno) == codigo }?.let { aplicar(it, actualizarTexto = false) }
        }

        codigoField.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position) as? CodigoInternoOpcion ?: return@setOnItemClickListener
            AseoCanonicos.buscarPorDocumento(seleccion.producto.codigoInterno)?.let { aplicar(it) }
        }

        codigoField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (seleccionandoOpcion || codigoField.tag == "auto_fill") return
                busquedaPendiente?.let { handler.removeCallbacks(it) }
                busquedaPendiente = Runnable { cargarSugerencias() }
                busquedaPendiente?.let { handler.postDelayed(it, 250) }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

internal fun MainActivity.cargarUbicacionesAseo(
        codigoOriginal: String,
        item: String,
        onResult: (List<AseoUbicacionStock>) -> Unit,
        onFailure: (() -> Unit)? = null,
    ) {
        val codigo = codigoOriginal.trim()
        if (codigo.isBlank()) {
            onResult(emptyList())
            return
        }

        val canonico = AseoCanonicos.buscarPorDocumento(codigo)
            ?: AseoCanonicos.buscarPorCodigoUbicacion(codigo, item = item)
            ?: AseoCanonicos.ubicaciones(codigo, item).firstOrNull()
        val codigoFirestore = canonico?.codigoInterno ?: codigo.uppercase(Locale.getDefault())

        fun desdeCatalogoCanonico(): List<AseoUbicacionStock> {
            return listOfNotNull(canonico ?: AseoCanonicos.buscarPorDocumento(codigoFirestore)).map { aseoUbicacionDesdeCanonico(it) }
        }

        if (!isNetworkAvailable()) {
            onResult(desdeCatalogoCanonico())
            return
        }

        firestore.collection(AseoCanonicos.COLECCION).document(codigoFirestore)
            .get()
            .addOnSuccessListener { doc ->
                if (!pantallaActiva()) return@addOnSuccessListener
                val opcion = if (doc.exists()) aseoUbicacionDesdeDoc(doc) else null
                val coincide = opcion != null && (item.isBlank() || AseoCanonicos.productoCoincide(opcion.item, item))
                onResult(if (coincide) listOf(opcion!!) else desdeCatalogoCanonico())
            }
            .addOnFailureListener {
                onFailure?.invoke()
                onResult(desdeCatalogoCanonico())
            }
    }

internal val AREAS_ASEO_BASE = listOf(
        "COCINA",
        "COMEDOR",
        "BA\u00d1OS",
        "OFICINA",
        "BODEGA ROJA",
        "LAVANDER\u00cdA",
        "TALLER"
    )

internal fun MainActivity.cargarAreasAseo(onResult: (List<String>) -> Unit) {
        if (!isNetworkAvailable()) {
            onResult(AREAS_ASEO_BASE)
            return
        }
        firestore.collection("areas_aseo")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val remotas = snapshot.documents.mapNotNull { it.getString("nombre")?.trim() }.filter { it.isNotBlank() }
                onResult((AREAS_ASEO_BASE + remotas).distinctBy { normalizarBusqueda(it) }.sortedBy { normalizarBusqueda(it) })
            }
            .addOnFailureListener { onResult(AREAS_ASEO_BASE) }
    }

internal fun MainActivity.guardarAreaAseo(nombre: String, onDone: () -> Unit = {}) {
        val limpio = nombre.trim().uppercase(Locale.getDefault())
        if (limpio.isBlank()) {
            Toast.makeText(this, "Escribe el nombre del \u00e1rea", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexi\u00f3n para crear \u00e1reas", Toast.LENGTH_LONG).show()
            return
        }
        val areaId = normalizarBusqueda(limpio).uppercase(Locale.getDefault()).replace(" ", "-")
        firestore.collection("areas_aseo").document(areaId)
            .set(mapOf("nombre" to limpio, "activo" to true, "fecha" to now()), SetOptions.merge())
            .addOnSuccessListener {
                saved("\u00c1rea ASEO guardada: $limpio")
                onDone()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage ?: "No se pudo guardar el \u00e1rea", Toast.LENGTH_LONG).show()
            }
    }

internal fun MainActivity.showDialogNuevaAreaAseo(onDone: () -> Unit = {}) {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        root.addView(TextView(this).apply {
            text = "Nueva \u00e1rea ASEO"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })
        val nombre = field(root, "\u00c1rea *", "Ej: cocina, comedor, ba\u00f1os")
        root.addView(primaryButton("Guardar \u00e1rea") {
            guardarAreaAseo(nombre.text.toString()) {
                dialog.dismiss()
                onDone()
            }
        })
        dialog.setContentView(root)
        dialog.show()
    }

internal fun idAseoArea(codigoOriginal: String, ubicacion: String): String {
        val cod = normalizarBusqueda(codigoOriginal).uppercase(Locale.getDefault()).replace(" ", "-")
        val ubi = normalizarBusqueda(ubicacion).uppercase(Locale.getDefault()).replace(" ", "-")
        return "ASEO-$cod-$ubi"
    }

internal fun MainActivity.showDialogUbicacionesAseo(
        opciones: List<AseoUbicacionStock>,
        onSelected: (AseoUbicacionStock) -> Unit,
        onTransferDone: () -> Unit
    ) {
        if (opciones.isEmpty()) {
            Toast.makeText(this, "No hay ubicaciones para este producto", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        val total = opciones.sumOf { it.cantidad }
        val unidad = opciones.firstOrNull { it.unidad.isNotBlank() }?.unidad.orEmpty()
        root.addView(TextView(this).apply {
            text = "ASEO: total ${cantidadTexto(total)} $unidad"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        opciones.forEach { opcion ->
            root.addView(primaryButton("${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}") {
                onSelected(opcion)
                dialog.dismiss()
            }.apply {
                setBackgroundColor(if (opcion.cantidad <= 0.0) Color.rgb(170, 65, 65) else verde)
            })
        }
        root.addView(primaryButton("Trasladar a \u00e1rea") {
            dialog.dismiss()
            showDialogTrasladoAseoArea(opciones, onTransferDone)
        }.apply { setBackgroundColor(Color.rgb(0, 120, 200)) })
        dialog.setContentView(root)
        dialog.show()
    }

internal fun MainActivity.showDialogTrasladoAseoArea(opciones: List<AseoUbicacionStock>, onDone: () -> Unit = {}) {
        cargarAreasAseo { areas ->
            val dialog = Dialog(this)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(20), dp(20), dp(20))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            }
            root.addView(TextView(this).apply {
                text = "Traslado interno ASEO"
                textSize = 18f
                setTextColor(verdeOscuro)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            })
            val desde = spinner(root, "Desde", opciones.map { it.toString() })
            val hasta = spinner(root, "\u00c1rea destino", areas)
            val cantidad = field(root, "Cantidad entregada *", "Ej: 1", number = true)
            val obs = field(root, "Observaciones", "Persona, cocina, turno o responsable")
            root.addView(primaryButton("A\u00f1adir \u00e1rea") {
                dialog.dismiss()
                showDialogNuevaAreaAseo { showDialogTrasladoAseoArea(opciones, onDone) }
            }.apply { setBackgroundColor(Color.rgb(100, 100, 110)) })
            root.addView(primaryButton("Confirmar traslado") {
                if (!required(cantidad)) return@primaryButton
                val desdeOpcion = opciones.getOrNull(desde.selectedItemPosition) ?: return@primaryButton
                val destino = hasta.selectedItem?.toString().orEmpty()
                val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                registrarTrasladoAseoArea(desdeOpcion, destino, cantVal, obs.text.toString(), {
                    saved("Traslado ASEO registrado")
                    dialog.dismiss()
                    onDone()
                }, { e ->
                    Toast.makeText(this, e.localizedMessage ?: "No se pudo registrar el traslado", Toast.LENGTH_LONG).show()
                })
            })
            dialog.setContentView(root)
            dialog.show()
        }
    }

internal fun MainActivity.registrarTrasladoAseoArea(
        desde: AseoUbicacionStock,
        areaDestino: String,
        cantidad: Double,
        observaciones: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val destino = areaDestino.trim().uppercase(Locale.getDefault())
        if (cantidad <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (destino.isBlank() || normalizarBusqueda(destino) == normalizarBusqueda(desde.ubicacion)) {
            onFailure?.invoke(IllegalArgumentException("Selecciona un \u00e1rea destino diferente"))
            return
        }
        if (!isNetworkAvailable()) {
            onFailure?.invoke(IllegalStateException("Necesitas conexi\u00f3n para trasladar ASEO"))
            return
        }
        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            val refDesde = firestore.collection(AseoCanonicos.COLECCION).document(desde.documentoId)
            val fecha = now()
            firestore.runTransaction { transaction ->
                val snapDesde = transaction.get(refDesde)
                val stockDesde = if (snapDesde.exists()) numeroDocumento(snapDesde, "stock_actual", "cantidad") else desde.cantidad
                if (stockDesde < cantidad) throw IllegalStateException("Stock insuficiente en ${desde.ubicacion}")
                val nuevoDesde = stockDesde - cantidad
                transaction.set(refDesde, aseoFirestoreDataDesdeUbicacion(desde, nuevoDesde))
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to AseoCanonicos.MODULO,
                    "tipoMovimiento" to "Traslado interno ASEO",
                    "item" to desde.item,
                    "referencia" to desde.codigoOriginal,
                    "codigo_interno" to desde.documentoId,
                    "ubicacion_origen" to desde.ubicacion,
                    "ubicacion_destino" to destino,
                    "cantidad" to cantidad,
                    "unidad" to desde.unidad,
                    "stock_origen_anterior" to stockDesde,
                    "stock_origen_nuevo" to nuevoDesde,
                    "usuario" to usuario,
                    "observaciones" to observaciones
                ))
                null
            }.addOnSuccessListener { onSuccess?.invoke() }
                .addOnFailureListener { onFailure?.invoke(it) }
        }
    }

internal fun MainActivity.registrarEntradaAseoFirestore(
        opcion: AseoUbicacionStock,
        cantidad: Double,
        observaciones: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (cantidad <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (!isNetworkAvailable()) {
            onFailure?.invoke(IllegalStateException("Necesitas conexi\u00f3n para ingresar ASEO"))
            return
        }
        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            val refDoc = firestore.collection(AseoCanonicos.COLECCION).document(opcion.documentoId)
            val fecha = now()
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refDoc)
                val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "stock_actual", "cantidad") else opcion.cantidad
                val stockNuevo = stockAnterior + cantidad
                transaction.set(refDoc, aseoFirestoreDataDesdeUbicacion(opcion, stockNuevo))
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to AseoCanonicos.MODULO,
                    "tipoMovimiento" to "Entrada",
                    "item" to opcion.item,
                    "referencia" to "N/A",
                    "codigoInterno" to opcion.documentoId,
                    "codigo_interno" to opcion.documentoId,
                    "ubicacion" to opcion.ubicacion,
                    "cantidad" to cantidad,
                    "unidad" to opcion.unidad,
                    "usuario" to usuario,
                    "observaciones" to observaciones,
                    "stock_anterior" to stockAnterior,
                    "stock_nuevo" to stockNuevo,
                    "stock_actualizado" to true
                ))
                mapOf("ant" to stockAnterior, "nve" to stockNuevo)
            }.addOnSuccessListener { res ->
                registrarCambioLocal(
                    "ENTRADA_STOCK",
                    AseoCanonicos.MODULO,
                    opcion.documentoId,
                    "Ingreso",
                    res["ant"].toString(),
                    res["nve"].toString()
                )
                onSuccess?.invoke()
            }.addOnFailureListener { onFailure?.invoke(it) }
        }
    }

internal fun MainActivity.registrarSalidaAseoFirestore(
        mov: Movimiento,
        opcion: AseoUbicacionStock,
        cantidadNumerica: Double,
        fotoUrl: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (fotoUrl.isNotBlank() && evidenciaEsUriLocal(fotoUrl) && isNetworkAvailable()) {
            Toast.makeText(this, "Subiendo evidencia antes de guardar...", Toast.LENGTH_SHORT).show()
            subirEvidenciaCloud(fotoUrl, AseoCanonicos.MODULO) { url ->
                if (url.isNotBlank()) {
                    registrarSalidaAseoFirestore(mov, opcion, cantidadNumerica, url, onSuccess, onFailure)
                } else {
                    val error = IllegalStateException("No se pudo subir la evidencia del movimiento")
                    Toast.makeText(this, "No se guardo: la evidencia no pudo subirse", Toast.LENGTH_LONG).show()
                    onFailure?.invoke(error)
                }
            }
            return
        }
        if (cantidadNumerica <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (!isNetworkAvailable()) {
            encolarMovimientoOffline(mov, cantidadNumerica, opcion.item, fotoUrl)
            Toast.makeText(this, "Salida guardada offline; stock ASEO pendiente de sincronizar", Toast.LENGTH_LONG).show()
            onSuccess?.invoke()
            return
        }

        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            val refDoc = firestore.collection(AseoCanonicos.COLECCION).document(opcion.documentoId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refDoc)
                val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "stock_actual", "cantidad") else opcion.cantidad
                val stockNuevo = stockAnterior - cantidadNumerica
                transaction.set(refDoc, aseoFirestoreDataDesdeUbicacion(opcion, stockNuevo))
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to mov.fecha,
                    "modulo" to mov.modulo,
                    "tipoMovimiento" to mov.tipoMovimiento,
                    "item" to mov.item,
                    "referencia" to mov.referencia,
                    "codigo_interno" to opcion.documentoId,
                    "ubicacion" to opcion.ubicacion,
                    "cantidad" to mov.cantidad,
                    "unidad" to mov.unidad,
                    "solicitante" to mov.solicitante,
                    "labor" to mov.labor,
                    "observaciones" to mov.observaciones,
                    "usuario" to usuario,
                    "fotoUrl" to fotoUrl,
                    "stock_actualizado" to true,
                    "stock_anterior" to stockAnterior,
                    "stock_nuevo" to stockNuevo
                ))
                mapOf("ant" to stockAnterior, "nve" to stockNuevo)
            }.addOnSuccessListener { res ->
                registrarCambioLocal(
                    "SALIDA_STOCK",
                    AseoCanonicos.MODULO,
                    opcion.documentoId,
                    "Salida: ${opcion.item}",
                    res["ant"].toString(),
                    res["nve"].toString()
                )
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                android.util.Log.e("ArlesGestion", "Fallo salida ASEO: ${e.message}")
                onFailure?.invoke(e)
            }
        }
    }

internal fun MainActivity.showDialogEntradaStockAseo(opcion: AseoUbicacionStock, onDone: () -> Unit = {}) {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        root.addView(TextView(this).apply {
            text = "Ingresar stock ASEO"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "${opcion.item}\n${opcion.codigoOriginal} · ${opcion.ubicacion}"
            textSize = 13f
            setTextColor(gris)
            setPadding(0, 0, 0, dp(12))
        })
        val cant = field(root, "Cantidad a sumar *", "Ej: 1", number = true)
        val obs = field(root, "Observaciones", "Opcional")
        root.addView(primaryButton("Confirmar ingreso") {
            if (!required(cant)) return@primaryButton
            val cantVal = cant.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val observaciones = listOf("Ubicaci\u00f3n: ${opcion.ubicacion}", obs.text.toString()).filter { it.isNotBlank() }.joinToString(". ")
            registrarEntradaAseoFirestore(opcion, cantVal, observaciones, {
                saved("Stock ASEO actualizado: +${cantidadTexto(cantVal)} ${opcion.unidad}")
                dialog.dismiss()
                onDone()
            }, { e ->
                Toast.makeText(this, e.localizedMessage ?: "No se pudo actualizar stock", Toast.LENGTH_LONG).show()
            })
        })
        dialog.setContentView(root)
        dialog.show()
    }

internal fun cantidadTexto(valor: Double): String {
        return if (valor % 1.0 == 0.0) valor.toLong().toString() else String.format(Locale.getDefault(), "%.2f", valor)
    }

internal fun MainActivity.cargarUbicacionesQuimico(
        codigoOriginal: String,
        item: String,
        onResult: (List<QuimicoUbicacionStock>) -> Unit,
        onFailure: (Exception) -> Unit = {},
        moduloFiltro: String = ""
    ) {
        val codigo = codigoOriginal.trim()
        if (codigo.isBlank()) {
            onResult(emptyList())
            return
        }

        fun desdeCatalogoCanonico(): List<QuimicoUbicacionStock> {
            return QuimicosCanonicos.ubicaciones(codigo, item).map {
                QuimicoUbicacionStock(
                    documentoId = it.documentoId,
                    codigoOriginal = it.codigoOriginal,
                    item = it.item,
                    categoria = it.categoria,
                    ubicacion = it.ubicacion,
                    cantidad = it.stockInicial,
                    unidad = it.unidad
                )
            }.filter { ModulosInventario.modulosCompatibles(ModulosInventario.moduloQuimico(it.categoria, it.ubicacion), moduloFiltro) }
        }

        if (!isNetworkAvailable()) {
            onResult(desdeCatalogoCanonico())
            return
        }

        firestore.collection("existencias")
            .whereEqualTo("codigo_original", codigo)
            .get()
            .addOnSuccessListener { snapshot ->
                val itemNorm = normalizarBusqueda(item)
                val opciones = snapshot.documents
                    .filter { doc ->
                        val moduloDoc = doc.getString("modulo").orEmpty()
                        doc.getBoolean("activo") != false &&
                            ModulosInventario.esModuloQuimicoOperativo(moduloDoc) &&
                            ModulosInventario.modulosCompatibles(moduloDoc, moduloFiltro) &&
                            (itemNorm.isBlank() || normalizarBusqueda(nombreItemExistencia(doc)) == itemNorm)
                    }
                    .map { doc ->
                        QuimicoUbicacionStock(
                            documentoId = doc.id,
                            codigoOriginal = codigoOriginalExistencia(doc),
                            item = nombreItemExistencia(doc),
                            categoria = doc.getString("categoria").orEmpty(),
                            ubicacion = ubicacionExistencia(doc),
                            cantidad = numeroDocumento(doc, "cantidad", "stock_actual"),
                            unidad = doc.getString("unidad").orEmpty()
                        )
                    }
                    .sortedBy { it.ubicacion }
                onResult(opciones.ifEmpty { desdeCatalogoCanonico() })
            }
            .addOnFailureListener { e ->
                onFailure(e)
                onResult(desdeCatalogoCanonico())
            }
    }

internal val AREAS_COP_BASE = listOf(
        "TALLER",
        "MAQUINARIA",
        "CAMPO",
        "VIVERO",
        "LAVADO / DESINFECCI\u00d3N",
        "OFICINA / LABORATORIO"
    )

internal fun MainActivity.cargarAreasCop(onResult: (List<String>) -> Unit) {
        if (!isNetworkAvailable()) {
            onResult(AREAS_COP_BASE)
            return
        }
        firestore.collection("areas_cop")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val remotas = snapshot.documents.mapNotNull { it.getString("nombre")?.trim() }.filter { it.isNotBlank() }
                onResult((AREAS_COP_BASE + remotas).distinctBy { normalizarBusqueda(it) }.sortedBy { normalizarBusqueda(it) })
            }
            .addOnFailureListener { onResult(AREAS_COP_BASE) }
    }

internal fun MainActivity.guardarAreaCop(nombre: String, onDone: () -> Unit = {}) {
        val limpio = nombre.trim().uppercase(Locale.getDefault())
        if (limpio.isBlank()) {
            Toast.makeText(this, "Escribe el nombre del \u00e1rea", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexi\u00f3n para crear \u00e1reas", Toast.LENGTH_LONG).show()
            return
        }
        val areaId = normalizarBusqueda(limpio).uppercase(Locale.getDefault()).replace(" ", "-")
        firestore.collection("areas_cop").document(areaId)
            .set(mapOf("nombre" to limpio, "activo" to true, "fecha" to now()), SetOptions.merge())
            .addOnSuccessListener {
                saved("\u00c1rea COP guardada: $limpio")
                onDone()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage ?: "No se pudo guardar el \u00e1rea", Toast.LENGTH_LONG).show()
            }
    }

internal fun MainActivity.showDialogNuevaAreaCop(onDone: () -> Unit = {}) {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        root.addView(TextView(this).apply {
            text = "Nueva \u00e1rea COP"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })
        val nombre = field(root, "\u00c1rea *", "Ej: Taller motos, cuarto bombas, lavado")
        root.addView(primaryButton("Guardar \u00e1rea") {
            guardarAreaCop(nombre.text.toString()) {
                dialog.dismiss()
                onDone()
            }
        })
        dialog.setContentView(root)
        dialog.show()
    }

internal fun idQuimicoArea(codigoOriginal: String, ubicacion: String): String {
        val cod = normalizarBusqueda(codigoOriginal).uppercase(Locale.getDefault()).replace(" ", "-")
        val ubi = normalizarBusqueda(ubicacion).uppercase(Locale.getDefault()).replace(" ", "-")
        return "Q-$cod-$ubi"
    }

internal fun MainActivity.showDialogUbicacionesQuimico(
        opciones: List<QuimicoUbicacionStock>,
        onSelected: (QuimicoUbicacionStock) -> Unit,
        onTransferDone: () -> Unit,
        permitirAreasCop: Boolean = false
    ) {
        if (opciones.isEmpty()) {
            Toast.makeText(this, "No hay ubicaciones para este producto", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        val total = opciones.sumOf { it.cantidad }
        val unidad = opciones.firstOrNull { it.unidad.isNotBlank() }?.unidad.orEmpty()
        root.addView(TextView(this).apply {
            text = "Ubicaciones: total ${cantidadTexto(total)} $unidad"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })

        opciones.forEach { opcion ->
            root.addView(primaryButton("${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}") {
                onSelected(opcion)
                dialog.dismiss()
            }.apply {
                setBackgroundColor(if (opcion.cantidad <= 0.0) Color.rgb(170, 65, 65) else verde)
            })
        }

        if (opciones.size > 1) {
            root.addView(primaryButton("Registrar traslado") {
                dialog.dismiss()
                showDialogTrasladoQuimico(opciones, onTransferDone)
            }.apply { setBackgroundColor(Color.rgb(0, 120, 200)) })
        }

        if (permitirAreasCop) {
            root.addView(primaryButton("Trasladar a \u00e1rea COP") {
                dialog.dismiss()
                showDialogTrasladoAreaCop(opciones, onTransferDone)
            }.apply { setBackgroundColor(Color.rgb(0, 120, 200)) })
        }

        dialog.setContentView(root)
        dialog.show()
    }

internal fun MainActivity.showDialogTrasladoQuimico(opciones: List<QuimicoUbicacionStock>, onDone: () -> Unit = {}) {
        if (opciones.size < 2) {
            Toast.makeText(this, "Este producto solo tiene una ubicación", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }
        root.addView(TextView(this).apply {
            text = "Traslado entre ubicaciones"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })
        val desde = spinner(root, "Desde", opciones.map { it.toString() })
        val hasta = spinner(root, "Hacia", opciones.map { it.toString() })
        if (opciones.size > 1) hasta.setSelection(1)
        val cantidad = field(root, "Cantidad a trasladar *", "Ej: 1000", number = true)
        val obs = field(root, "Observaciones", "Opcional")

        root.addView(primaryButton("Confirmar traslado") {
            if (!required(cantidad)) return@primaryButton
            val desdeOpcion = opciones.getOrNull(desde.selectedItemPosition) ?: return@primaryButton
            val hastaOpcion = opciones.getOrNull(hasta.selectedItemPosition) ?: return@primaryButton
            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            if (desdeOpcion.documentoId == hastaOpcion.documentoId) {
                Toast.makeText(this, "Selecciona dos ubicaciones diferentes", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            registrarTrasladoQuimico(desdeOpcion, hastaOpcion, cantVal, obs.text.toString(), {
                saved("Traslado registrado")
                dialog.dismiss()
                onDone()
            }, { e ->
                Toast.makeText(this, e.localizedMessage ?: "No se pudo registrar el traslado", Toast.LENGTH_LONG).show()
            })
        })
        dialog.setContentView(root)
        dialog.show()
    }

internal fun MainActivity.showDialogTrasladoAreaCop(opciones: List<QuimicoUbicacionStock>, onDone: () -> Unit = {}) {
        if (opciones.isEmpty()) {
            Toast.makeText(this, "Selecciona primero un producto", Toast.LENGTH_SHORT).show()
            return
        }
        cargarAreasCop { areas ->
            val dialog = Dialog(this)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(20), dp(20), dp(20))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            }
            root.addView(TextView(this).apply {
                text = "Traslado interno COP"
                textSize = 18f
                setTextColor(verdeOscuro)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            })
            val desde = spinner(root, "Desde", opciones.map { it.toString() })
            val hasta = spinner(root, "\u00c1rea destino", areas.ifEmpty { AREAS_COP_BASE })
            val cantidad = field(root, "Cantidad a trasladar *", "Ej: 1", number = true)
            val obs = field(root, "Observaciones", "Equipo, orden de trabajo o responsable")

            root.addView(primaryButton("A\u00f1adir \u00e1rea") {
                dialog.dismiss()
                showDialogNuevaAreaCop { showDialogTrasladoAreaCop(opciones, onDone) }
            }.apply { setBackgroundColor(Color.rgb(100, 100, 110)) })

            root.addView(primaryButton("Confirmar traslado interno") {
                if (!required(cantidad)) return@primaryButton
                val desdeOpcion = opciones.getOrNull(desde.selectedItemPosition) ?: return@primaryButton
                val areaDestino = hasta.selectedItem?.toString().orEmpty()
                val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                registrarTrasladoQuimicoArea(desdeOpcion, areaDestino, cantVal, obs.text.toString(), {
                    saved("Traslado interno registrado")
                    dialog.dismiss()
                    onDone()
                }, { e ->
                    Toast.makeText(this, e.localizedMessage ?: "No se pudo registrar el traslado", Toast.LENGTH_LONG).show()
                })
            })

            dialog.setContentView(root)
            dialog.show()
        }
    }

internal fun MainActivity.registrarTrasladoQuimico(
        desde: QuimicoUbicacionStock,
        hasta: QuimicoUbicacionStock,
        cantidad: Double,
        observaciones: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (cantidad <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (!isNetworkAvailable()) {
            onFailure?.invoke(IllegalStateException("Necesitas conexión para trasladar entre ubicaciones"))
            return
        }

        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            val refDesde = firestore.collection("existencias").document(desde.documentoId)
            val refHasta = firestore.collection("existencias").document(hasta.documentoId)
            val fecha = now()
            val modulo = ModulosInventario.moduloQuimico(desde.categoria, desde.ubicacion)
            firestore.runTransaction { transaction ->
                val snapDesde = transaction.get(refDesde)
                val snapHasta = transaction.get(refHasta)
                val stockDesde = numeroDocumento(snapDesde, "cantidad", "stock_actual")
                val stockHasta = numeroDocumento(snapHasta, "cantidad", "stock_actual")
                if (stockDesde < cantidad) {
                    throw IllegalStateException("Stock insuficiente en ${desde.ubicacion}: ${cantidadTexto(stockDesde)} ${desde.unidad}")
                }
                val nuevoDesde = stockDesde - cantidad
                val nuevoHasta = stockHasta + cantidad
                transaction.update(refDesde, "cantidad", nuevoDesde, "ultima_fecha", fecha, "ultimo_solicitante", usuario)
                transaction.update(refHasta, "cantidad", nuevoHasta, "ultima_fecha", fecha, "ultimo_solicitante", usuario)
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to modulo,
                    "tipoMovimiento" to "Traslado",
                    "item" to desde.item,
                    "referencia" to desde.codigoOriginal,
                    "codigo_interno_origen" to desde.documentoId,
                    "codigo_interno_destino" to hasta.documentoId,
                    "codigo_original" to desde.codigoOriginal,
                    "ubicacion_origen" to desde.ubicacion,
                    "ubicacion_destino" to hasta.ubicacion,
                    "cantidad" to cantidad,
                    "unidad" to desde.unidad,
                    "stock_origen_anterior" to stockDesde,
                    "stock_origen_nuevo" to nuevoDesde,
                    "stock_destino_anterior" to stockHasta,
                    "stock_destino_nuevo" to nuevoHasta,
                    "usuario" to usuario,
                    "observaciones" to observaciones
                ))
                null
            }.addOnSuccessListener {
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
        }
    }

internal fun MainActivity.registrarTrasladoQuimicoArea(
        desde: QuimicoUbicacionStock,
        areaDestino: String,
        cantidad: Double,
        observaciones: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val destino = areaDestino.trim().uppercase(Locale.getDefault())
        if (cantidad <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (destino.isBlank()) {
            onFailure?.invoke(IllegalArgumentException("Selecciona un \u00e1rea destino"))
            return
        }
        if (normalizarBusqueda(destino) == normalizarBusqueda(desde.ubicacion)) {
            onFailure?.invoke(IllegalArgumentException("El \u00e1rea destino debe ser diferente al origen"))
            return
        }
        if (!isNetworkAvailable()) {
            onFailure?.invoke(IllegalStateException("Necesitas conexi\u00f3n para trasladar entre \u00e1reas"))
            return
        }

        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            val destinoId = idQuimicoArea(desde.codigoOriginal, destino)
            val refDesde = firestore.collection("existencias").document(desde.documentoId)
            val refHasta = firestore.collection("existencias").document(destinoId)
            val fecha = now()
            firestore.runTransaction { transaction ->
                val snapDesde = transaction.get(refDesde)
                val snapHasta = transaction.get(refHasta)
                val stockDesde = numeroDocumento(snapDesde, "cantidad", "stock_actual")
                val stockHasta = numeroDocumento(snapHasta, "cantidad", "stock_actual")
                if (stockDesde < cantidad) {
                    throw IllegalStateException("Stock insuficiente en ${desde.ubicacion}: ${cantidadTexto(stockDesde)} ${desde.unidad}")
                }
                val nuevoDesde = stockDesde - cantidad
                val nuevoHasta = stockHasta + cantidad
                val modulo = ModulosInventario.LUBRICANTES_TALLER
                transaction.update(refDesde, "cantidad", nuevoDesde, "ultima_fecha", fecha, "ultimo_solicitante", usuario)
                if (snapHasta.exists()) {
                    transaction.update(refHasta, "cantidad", nuevoHasta, "ultima_fecha", fecha, "ultimo_solicitante", usuario)
                } else {
                    val base = snapDesde.data?.toMutableMap() ?: mutableMapOf<String, Any>()
                    base["modulo"] = modulo
                    base["codigo_interno"] = desde.codigoOriginal
                    base["codigo_original"] = desde.codigoOriginal
                    base["documento_id"] = destinoId
                    base["producto_id"] = destinoId
                    base["ubicacion"] = destino
                    base["cantidad"] = nuevoHasta
                    base["activo"] = true
                    base["ultima_fecha"] = fecha
                    base["ultimo_solicitante"] = usuario
                    base["origen_area_cop"] = true
                    base["documento_origen_area"] = desde.documentoId
                    transaction.set(refHasta, base, SetOptions.merge())
                    transaction.set(firestore.collection("catalogo_personalizado").document(destinoId), base, SetOptions.merge())
                }
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to modulo,
                    "tipoMovimiento" to "Traslado interno COP",
                    "item" to desde.item,
                    "referencia" to desde.codigoOriginal,
                    "codigo_interno_origen" to desde.documentoId,
                    "codigo_interno_destino" to destinoId,
                    "codigo_original" to desde.codigoOriginal,
                    "ubicacion_origen" to desde.ubicacion,
                    "ubicacion_destino" to destino,
                    "cantidad" to cantidad,
                    "unidad" to desde.unidad,
                    "stock_origen_anterior" to stockDesde,
                    "stock_origen_nuevo" to nuevoDesde,
                    "stock_destino_anterior" to stockHasta,
                    "stock_destino_nuevo" to nuevoHasta,
                    "usuario" to usuario,
                    "observaciones" to observaciones
                ))
                null
            }.addOnSuccessListener {
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
        }
    }

internal fun MainActivity.showDialogEntradaStockQuimico(opcion: QuimicoUbicacionStock, onDone: () -> Unit = {}) {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }

        root.addView(TextView(this).apply {
            text = "Ingresar stock"
            textSize = 18f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "${opcion.item}\n${opcion.codigoOriginal} · ${opcion.ubicacion}"
            textSize = 13f
            setTextColor(gris)
            setPadding(0, 0, 0, dp(12))
        })

        val cant = field(root, "Cantidad a sumar *", "Ej: 50", number = true)
        val obs = field(root, "Observaciones", "Opcional")

        root.addView(primaryButton("Confirmar ingreso") {
            if (!required(cant)) return@primaryButton
            val cantVal = cant.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val modulo = ModulosInventario.moduloQuimico(opcion.categoria, opcion.ubicacion)
            val ent = Entrada(
                fecha = now(),
                modulo = modulo,
                item = opcion.item,
                referencia = opcion.codigoOriginal,
                codigoInterno = opcion.documentoId,
                cantidad = cantVal,
                unidad = opcion.unidad,
                observaciones = listOf("Ubicaci\u00f3n: ${opcion.ubicacion}", obs.text.toString())
                    .filter { it.isNotBlank() }
                    .joinToString(". ")
            )
            registrarEntradaFirestoreYStock(ent, opcion.documentoId, opcion.categoria, opcion.item, opcion.codigoOriginal, {
                saved("Stock actualizado en ${opcion.ubicacion}: +${cantidadTexto(cantVal)} ${opcion.unidad}")
                dialog.dismiss()
                onDone()
            }, { e ->
                Toast.makeText(this, e.localizedMessage ?: "No se pudo actualizar stock", Toast.LENGTH_LONG).show()
            })
        })

        dialog.setContentView(root)
        dialog.show()
    }

internal fun MainActivity.textoBusquedaProducto(producto: ProductoCatalogo): String {
        return "${producto.modulo} ${producto.categoria} ${producto.item} ${producto.referencia} ${nombreProductoCatalogo(producto.modulo, producto.item, producto.referencia)}"
    }

internal fun MainActivity.detectarModuloDesdePrompt(prompt: String): String {
        val norm = normalizarBusqueda(prompt)
        val esAgroquimico = norm.contains("agroquimico") || norm.contains("agroquimicos") ||
            norm.contains("quimico") || norm.contains("quimicos") ||
            Regex("""\b(fertilizante|herbicida|fungicida|insecticida|plaguicida|abono|glifosato|roundup|yaramila|npk|urea|veneno|cultivo|foliar|edafico)\b""")
                .containsMatchIn(norm)
        return when {
            norm.contains("bodega roja") ||
                (norm.contains("herramienta") && !norm.contains("lubricante")) -> TallerCanonicos.MODULO
            Regex("""\b(aseo|limpieza|cafeteria|desengrasante|detergente|jabon|trapero|escoba|bolsa|bolsas|basura|residuo|residuos|caneca)\b""").containsMatchIn(norm) ->
                ModulosInventario.ASEO
            norm.contains("epp") || norm.contains("proteccion personal") || norm.contains("proteccion") -> "EPP"
            norm.contains("dotacion") || norm.contains("uniforme") || norm.contains("overol") -> "Dotación"
            norm.contains("combustible") || norm.contains("gasolina") || norm.contains("acpm") || norm.contains("diesel") -> "Combustible"
            esAgroquimico -> ModulosInventario.AGROQUIMICOS
            norm.contains("lubricante") || norm.contains("aceite") || norm.contains("grasa") ||
                norm.contains("valvulina") || norm.contains("refrigerante") -> ModulosInventario.LUBRICANTES_TALLER
            norm.contains("taller") -> {
                if (norm.contains("herramienta") || norm.contains("bodega roja") ||
                    Regex("""\b(alicate|llave|destornillador|pinza|copa|bristol|dado|torque|martillo|hombre solo|diablo)\b""")
                        .containsMatchIn(norm)
                ) {
                    TallerCanonicos.MODULO
                } else {
                    ModulosInventario.LUBRICANTES_TALLER
                }
            }
            else -> {
                val frase = BusquedaInventarioIA.extraerFraseProducto(prompt)
                if (frase.isBlank()) return ""
                val tokens = BusquedaInventarioIA.tokensNucleo(frase)
                
                // Si la frase es muy corta (ej: "Papel"), no forzamos módulo para no inducir error
                if (tokens.size <= 1 && !esAgroquimico) return ""

                candidatosCatalogoInteligentes(modulo = null, consulta = frase, limiteCandidatos = 3)
                    .firstOrNull()
                    ?.modulo
                    .orEmpty()
            }
        }
    }

internal fun MainActivity.documentoCoincideModuloIA(doc: DocumentSnapshot, moduloHint: String): Boolean {
        if (moduloHint.isBlank()) return true
        val modDoc = doc.getString("modulo").orEmpty()
        if (modDoc.isBlank()) {
            val cat = doc.getString("categoria").orEmpty()
            return ModulosInventario.esModuloAgroquimico(moduloHint) &&
                Regex("fertilizante|herbicida|fungicida|insecticida|quimico", RegexOption.IGNORE_CASE).containsMatchIn(cat)
        }
        return moduloCatalogoCoincide(modDoc, moduloHint)
    }

internal fun moduloParaConsultaFirestore(modulo: String): String {
        val norm = modulo.trim()
        if (norm.isBlank()) return ""
        if (ModulosInventario.esModuloAgroquimico(norm)) return ModulosInventario.AGROQUIMICOS
        if (ModulosInventario.esModuloLubricantesTaller(norm)) return ModulosInventario.LUBRICANTES_TALLER
        return norm
    }

internal fun MainActivity.resolverCodigosAlmacenPorFraseIA(consulta: String, soloTaller: Boolean = false): List<String> {
        if (consulta.isBlank()) return emptyList()
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val codigos = linkedSetOf<String>()
        QuimicosCanonicos.items.forEach { canonico ->
            val esLubricanteTaller = ModulosInventario.esTallerQuimico(canonico.categoria, canonico.ubicacion)
            if (soloTaller && !esLubricanteTaller) return@forEach
            val base = "${canonico.item} ${canonico.codigoOriginal} ${canonico.ubicacion}"
            if (BusquedaInventarioIA.puntuarTexto(consulta, base, tokens) >= 0.30) {
                codigos.add(canonico.codigoOriginal)
            }
        }
        candidatosCatalogoInteligentes(modulo = null, consulta = consulta, limiteCandidatos = 10).forEach { producto ->
            val itemNorm = normalizarBusqueda(producto.item)
            QuimicosCanonicos.items
                .filter {
                    normalizarBusqueda(it.item) == itemNorm &&
                        (!soloTaller || ModulosInventario.esTallerQuimico(it.categoria, it.ubicacion))
                }
                .forEach { codigos.add(it.codigoOriginal) }
        }
        return codigos.toList()
    }

internal fun MainActivity.cargarExistenciasAlmacenParaIA(
        consulta: String,
        prompt: String,
        onSuccess: (List<DocumentSnapshot>) -> Unit,
        onFailure: () -> Unit,
        moduloForzado: String = "",
        soloTaller: Boolean = false,
    ) {
        val moduloHint = moduloParaConsultaFirestore(
            moduloForzado.ifBlank { detectarModuloDesdePrompt(prompt) },
        )
        val codigos = resolverCodigosAlmacenPorFraseIA(consulta, soloTaller)

        fun activos(docs: List<DocumentSnapshot>) = docs.filter { it.getBoolean("activo") != false }

        fun filtrarPorFrase(docs: List<DocumentSnapshot>, umbral: Double = 0.42): List<DocumentSnapshot> {
            if (docs.isEmpty() || consulta.isBlank()) return docs
            val tokens = BusquedaInventarioIA.tokens(consulta)
            val tokensNucleo = BusquedaInventarioIA.tokensNucleo(consulta)
            return docs
                .map { doc ->
                    val texto = textoBusquedaExistencia(doc)
                    val prefiltro = tokensNucleo.isEmpty() ||
                        tokensNucleo.any { token -> BusquedaInventarioIA.tokenPresenteFuzzy(token, texto) }
                    doc to if (prefiltro) BusquedaInventarioIA.puntuarTexto(consulta, texto, tokens) else 0.0
                }
                .filter { it.second >= umbral }
                .sortedByDescending { it.second }
                .map { it.first }
        }

        fun consultaAmplia(sinModulo: Boolean = false) {
            val limite = maxOf(performanceConfig.inventoryQueryLimit(), 2000L)
            val query = if (moduloHint.isNotBlank() && !sinModulo) {
                firestore.collection("existencias").whereEqualTo("modulo", moduloHint)
            } else {
                firestore.collection("existencias")
            }
            query.limit(limite).get()
                .addOnSuccessListener { snapshot ->
                    val documentos = snapshot.documents
                    val docsActivos = activos(documentos)
                    val docs = filtrarPorFrase(docsActivos)
                    if (docs.isEmpty() && moduloHint.isNotBlank() && !sinModulo && consulta.isNotBlank()) {
                        consultaAmplia(sinModulo = true)
                    } else {
                        val resultado = docs
                            .ifEmpty { filtrarPorFrase(docsActivos, umbral = 0.24).take(8) }
                            .ifEmpty { filtrarPorFrase(documentos, umbral = 0.30).take(8) }
                        onSuccess(resultado)
                    }
                }
                .addOnFailureListener { onFailure() }
        }

        if (codigos.isEmpty()) {
            consultaAmplia()
            return
        }

        val chunks = codigos.distinct().chunked(10)
        val acumulado = mutableListOf<DocumentSnapshot>()
        var pendientes = chunks.size

        fun finalizarPorCodigo() {
            val docsActivos = activos(acumulado)
            if (docsActivos.isEmpty()) {
                consultaAmplia()
                return
            }
            val docs = filtrarPorFrase(docsActivos)
            onSuccess(if (docs.isNotEmpty()) docs else docsActivos)
        }

        chunks.forEach { chunk ->
            firestore.collection("existencias")
                .whereIn("codigo_original", chunk)
                .get()
                .addOnSuccessListener { snapshot ->
                    acumulado.addAll(snapshot.documents)
                    pendientes--
                    if (pendientes == 0) finalizarPorCodigo()
                }
                .addOnFailureListener {
                    pendientes--
                    if (pendientes == 0) finalizarPorCodigo()
                }
        }
    }

internal fun MainActivity.textoStockAgregadoIA(consulta: String, docs: List<DocumentSnapshot>): String {
        if (docs.isEmpty()) return ""
        val tokensNucleo = BusquedaInventarioIA.tokensNucleo(consulta)
        val coincidenciasExactas = if (tokensNucleo.isEmpty()) {
            docs
        } else {
            docs.filter { doc ->
                val texto = textoBusquedaExistencia(doc)
                tokensNucleo.all { token -> BusquedaInventarioIA.tokenPresenteFuzzy(token, texto) }
            }
        }
        val sonParecidos = coincidenciasExactas.isEmpty()
        val todosInactivos = docs.all { it.getBoolean("activo") == false }
        val docsParaTotal = if (todosInactivos) docs else docs.filter { it.getBoolean("activo") != false }
        val lineas = docs.map { doc ->
            val cantidad = numeroDocumento(doc, "cantidad", "stock_actual")
            val unidad = doc.getString("unidad").orEmpty().ifBlank { "unidad" }
            val ubicacion = ubicacionExistencia(doc).ifBlank { "sin ubicación" }
            val nombre = productoVisibleExistencia(doc)
            val codigo = codigoExistencia(doc)
            val estado = if (doc.getBoolean("activo") == false) " · historico/inactivo" else ""
            "$ubicacion: ${cantidadTexto(cantidad)} $unidad ($codigo · $nombre$estado)"
        }
        val total = docsParaTotal.sumOf { numeroDocumento(it, "cantidad", "stock_actual") }
        val unidad = docsParaTotal.firstOrNull()?.getString("unidad").orEmpty().ifBlank {
            docs.first().getString("unidad").orEmpty().ifBlank { "unidad" }
        }
        return if (todosInactivos) {
            "No encontré stock activo de $consulta. Sí aparece en registros históricos/inactivos:\n${lineas.joinToString("\n")}"
        } else if (sonParecidos) {
            "No encontré una coincidencia exacta para $consulta. Parecidos en inventario:\n${lineas.joinToString("\n")}"
        } else if (lineas.size > 1) {
            "Stock de $consulta: ${cantidadTexto(total)} $unidad en total, en ${lineas.size} ubicaciones:\n${lineas.joinToString("\n")}"
        } else {
            "Stock de $consulta: ${cantidadTexto(total)} $unidad. ${lineas.first()}."
        }
    }

internal fun esCoincidenciaProductoSegura(sugerencias: List<Pair<ProductoCatalogo, Double>>): Boolean {
        if (sugerencias.isEmpty()) return false
        val mejor = sugerencias.first()
        if (mejor.second < 0.76) return false
        if (sugerencias.size == 1) return true
        return (mejor.second - sugerencias[1].second) >= 0.12
    }

internal fun MainActivity.candidatosCatalogoInteligentes(
        modulo: String? = null,
        consulta: String,
        tokens: List<String> = BusquedaInventarioIA.tokens(consulta),
        limiteCandidatos: Int = performanceConfig.aiCatalogCandidateLimit(),
        moduloHint: String = "",
    ): List<ProductoCatalogo> {
        val moduloDetectado = modulo?.trim().orEmpty().ifBlank { moduloHint.trim() }
        val moduloFiltro = moduloDetectado.ifBlank { null }
        val todos = todosProductosCatalogo(moduloFiltro)
        if (consulta.isBlank() && tokens.isEmpty()) return todos.take(limiteCandidatos)

        val tokensNucleo = BusquedaInventarioIA.tokensNucleo(consulta)
        val tokensBusqueda = if (tokens.isNotEmpty()) tokens else BusquedaInventarioIA.tokens(consulta)

        val prefiltrados = if (tokensBusqueda.isEmpty()) {
            todos.take(limiteCandidatos * 2)
        } else {
            todos.filter { producto ->
                BusquedaInventarioIA.coincidePrefiltroRapido(textoBusquedaProducto(producto), tokensBusqueda)
            }
        }

        val pool = if (prefiltrados.isEmpty() && tokensBusqueda.isEmpty()) {
            todos.take(limiteCandidatos * 2)
        } else {
            prefiltrados
        }

        if (pool.isEmpty()) return emptyList()

        return pool
            .asSequence()
            .map { producto ->
                producto to BusquedaInventarioIA.puntajePrefiltroRapido(
                    textoBusquedaProducto(producto),
                    tokensNucleo,
                    tokensBusqueda,
                    moduloDetectado,
                )
            }
            .sortedByDescending { it.second }
            .take(limiteCandidatos)
            .map { it.first }
            .toList()
    }

internal fun MainActivity.catalogoComoTextoFiltrado(prompt: String, limite: Int = 80, moduloHint: String = ""): String {
        val consulta = BusquedaInventarioIA.extraerFraseProducto(prompt).ifBlank { prompt }
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val modulo = moduloHint.ifBlank { detectarModuloDesdePrompt(prompt) }
        val limiteEfectivo = when {
            ModulosInventario.esModuloAgroquimico(modulo) -> maxOf(limite, 55)
            modulo.isNotBlank() -> maxOf(limite, 40)
            else -> limite
        }
        val candidatos = candidatosCatalogoInteligentes(
            modulo = modulo.ifBlank { null },
            consulta = consulta,
            tokens = tokens,
            moduloHint = modulo,
        )
        val productos = if (tokens.isEmpty() && consulta.isBlank()) {
            candidatos.take(limiteEfectivo)
        } else {
            candidatos
                .map { producto ->
                    val base = textoBusquedaProducto(producto)
                    producto to BusquedaInventarioIA.puntuarTexto(consulta, base, tokens, consulta)
                }
                .filter { it.second >= 0.45 }
                .sortedByDescending { it.second }
                .take(limiteEfectivo)
                .map { it.first }
        }

        val b = StringBuilder()
        if (modulo.isNotBlank()) {
            b.append("MODULO_FILTRADO: $modulo\n")
        }
        productos.forEach { p ->
            b.append("- [${p.modulo}] ${p.item} (${p.referencia}) [CAT: ${p.categoria}]\n")
        }
        return b.toString().take(12000)
    }

internal fun MainActivity.consultarStockExistencia(codigoOItem: String, referencia: String, onResult: (Double, String, String) -> Unit, onFailure: (Exception) -> Unit) {
        buscarExistenciaPorCodigoInterno(codigoOItem, "", { producto ->
            if (producto != null) onResult(producto.cantidad, producto.unidad, "${producto.item} · ${producto.referenciaCatalogo}")
            else {
                buscarExistenciaPorProducto("", codigoOItem, referencia, { p2 ->
                    if (p2 != null) onResult(p2.cantidad, p2.unidad, "${p2.item} · ${p2.referenciaCatalogo}")
                    else onResult(0.0, "Unidad", "")
                }, onFailure)
            }
        }, onFailure)
    }

internal fun MainActivity.showInventarioScreen() {
        try {
            showInventarioScreenInterno()
        } catch (e: Exception) {
            android.util.Log.e("ArlesGestion", "Error abriendo inventario", e)
            Toast.makeText(this, "No se pudo abrir inventario: ${e.localizedMessage ?: "error inesperado"}", Toast.LENGTH_LONG).show()
            showMainMenu()
        }
    }

private fun MainActivity.showInventarioScreenInterno() {
        val activity = this
        currentScreenRenderer = { showInventarioScreen() }
        val root = baseScreen("Inventario (Nube)", "Consulta de stock real en tiempo real por categoría.")

        val modulos = AppMode.modulosInventarioNube()
        var moduloSeleccionado = modulos.first()
        fun accentActual() = moduloInventarioAccent(moduloSeleccionado)

        val tabsScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
        }
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        tabsScroll.addView(tabsLayout)
        root.addView(tabsScroll)

        val heroBanner = moduleHeroBanner(
            R.drawable.ic_warehouse,
            moduloSeleccionado,
            "Stock sincronizado en tiempo real desde la nube.",
            accentActual(),
        )
        root.addView(heroBanner)

        val ubicacionTabsScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            visibility = android.view.View.GONE
        }
        val ubicacionTabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        ubicacionTabsScroll.addView(ubicacionTabsLayout)
        root.addView(ubicacionTabsScroll)

        var ubicacionAgroFiltro = "TODOS"
        val tabsUbicacionAgro = listOf("TODOS") + QuimicosCanonicos.UBICACIONES_AGROQUIMICOS

        fun esModuloAgroquimicosActual(): Boolean =
            ModulosInventario.esModuloAgroquimico(moduloSeleccionado)

        fun actualizarEstilosUbicacionAgro() {
            for (i in 0 until ubicacionTabsLayout.childCount) {
                val btn = ubicacionTabsLayout.getChildAt(i) as? Button ?: continue
                val valor = btn.tag as? String ?: continue
                aplicarEstiloChipTaller(btn, valor == ubicacionAgroFiltro, accentActual())
            }
        }

        root.addView(sectionHeader("Búsqueda", "Filtra por código, producto, marca o categoría."))
        val search = tallerSearchField("Escribe código, producto o marca")
        root.addView(search)
        val resumenLabel = catalogoResumenBar("Conectando...", accentActual())
        root.addView(resumenLabel)

        val listaCard = card()
        val listaContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listaCard.addView(listaContainer)
        root.addView(listaCard)

        fun limpiarLista() {
            listaContainer.removeAllViews()
        }

        var currentListener: com.google.firebase.firestore.ListenerRegistration? = null
        var cacheAseoDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var cacheExistenciasDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

        fun agregarFilaInventario(
            codigo: String,
            producto: String,
            referencia: String,
            marca: String,
            stock: Double,
            unidad: String,
            categoria: String,
        ) {
            val detalle = "Producto: $producto\n" +
                "Codigo: $codigo\n" +
                "Referencia/ubicacion: ${referencia.ifBlank { "N/A" }}\n" +
                "Marca: ${marca.ifBlank { "N/A" }}\n" +
                "Stock actual: ${cantidadTexto(stock)}\n" +
                "Unidad: ${unidad.ifBlank { "Unidad" }}\n" +
                "Categoria: ${categoria.ifBlank { "General" }}"
            listaContainer.addView(
                catalogoInventarioCard(
                    codigo = codigo,
                    producto = producto,
                    referencia = referencia,
                    marca = marca,
                    stock = stock,
                    unidad = unidad,
                    categoria = categoria,
                    accent = accentActual(),
                ) {
                    if (pantallaActiva()) {
                        AlertDialog.Builder(activity)
                            .setTitle("Detalle del Producto")
                            .setMessage(detalle)
                            .setPositiveButton("Cerrar", null)
                            .show()
                    }
                },
            )
        }

        fun mostrarInventarioVacio(mensaje: String) {
            listaContainer.addView(tallerEmptyState("Sin resultados", mensaje))
        }

        fun ejecutarEnUi(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else runOnUiThread(block)
        }

        fun actualizarCabeceraModulo() {
            val accent = accentActual()
            heroBanner.background = arlesRoundedBackground(accent, null, 18)
            resumenLabel.setTextColor(accent)
            actualizarChipsModulo(tabsLayout, moduloSeleccionado, accent)
            val esAgro = esModuloAgroquimicosActual()
            ubicacionTabsScroll.visibility = if (esAgro) android.view.View.VISIBLE else android.view.View.GONE
            if (!esAgro) ubicacionAgroFiltro = "TODOS"
            actualizarEstilosUbicacionAgro()
        }

        fun pintarInventarioAseoActualizado(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
            if (!pantallaActiva()) return
            ejecutarEnUi {
                if (!pantallaActiva()) return@ejecutarEnUi
                try {
                    limpiarLista()
                    val filas = filasInventarioAseoUnificadas(docs)
                    val queryText = normalizarBusqueda(search.text.toString())
                    val filtrados = if (queryText.isBlank()) filas
                    else filas.filter { fila ->
                        normalizarBusqueda("${fila.codigo} ${fila.producto} ${fila.categoria} ${fila.unidad} piso ${fila.piso}")
                            .contains(queryText)
                    }

                    if (filtrados.isEmpty()) {
                        val mensaje = if (queryText.isNotBlank()) {
                            "No hay coincidencias para la búsqueda en ASEO."
                        } else {
                            "Sin productos visibles en ASEO."
                        }
                        mostrarInventarioVacio(mensaje)
                        resumenLabel.text = "$moduloSeleccionado: 0 items | Total: 0"
                        return@ejecutarEnUi
                    }

                    val (visible, ocultos) = RenderLimiter.applyLimit(filtrados, performanceConfig.inventoryRenderLimit())
                    var totalStock = 0.0
                    visible.forEach { fila ->
                        try {
                            totalStock += fila.stock
                            agregarFilaInventario(
                                fila.codigo,
                                fila.producto,
                                "Piso ${fila.piso}",
                                "",
                                fila.stock,
                                fila.unidad,
                                fila.categoria
                            )
                        } catch (rowError: Exception) {
                            android.util.Log.e("ArlesGestion", "Error fila ASEO ${fila.codigo}", rowError)
                        }
                    }
                    val ocultosTxt = if (ocultos > 0) " | +$ocultos ocultos (usa el filtro)" else ""
                    val fuenteTxt = if (docs.isEmpty()) " · catálogo local" else ""
                    resumenLabel.text = "$moduloSeleccionado: ${filtrados.size} items | Total: ${cantidadTexto(totalStock)}$ocultosTxt$fuenteTxt"
                } catch (e: Exception) {
                    android.util.Log.e("ArlesGestion", "Error pintando inventario ASEO", e)
                    try {
                        limpiarLista()
                        var totalStock = 0.0
                        filasInventarioAseoUnificadas(emptyList()).forEach { fila ->
                            totalStock += fila.stock
                            agregarFilaInventario(fila.codigo, fila.producto, "Piso ${fila.piso}", "", fila.stock, fila.unidad, fila.categoria)
                        }
                        resumenLabel.text = "$moduloSeleccionado: ${AseoCanonicos.items.size} items | Total: ${cantidadTexto(totalStock)} · modo seguro"
                    } catch (fallbackError: Exception) {
                        android.util.Log.e("ArlesGestion", "Fallback ASEO también falló", fallbackError)
                        resumenLabel.text = "ASEO: no se pudo mostrar el inventario"
                        mostrarInventarioVacio("Error al cargar ASEO. Cierra y vuelve a abrir la pantalla.")
                    }
                }
            }
        }

        fun pintarInventarioExistenciasActualizado(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
            if (!pantallaActiva()) return
            limpiarLista()
            val queryText = normalizarBusqueda(search.text.toString())
            val filtradosBusqueda = if (queryText.isBlank()) docs
            else docs.filter { textoBusquedaExistencia(it).contains(queryText) }
            val filtrados = if (esModuloAgroquimicosActual()) {
                filtradosBusqueda.filter { doc ->
                    QuimicosCanonicos.coincideUbicacionAgroquimico(ubicacionExistencia(doc), ubicacionAgroFiltro)
                }
            } else {
                filtradosBusqueda
            }

            if (filtrados.isEmpty()) {
                val mensaje = when {
                    docs.isEmpty() -> "Sin referencias en $moduloSeleccionado."
                    esModuloAgroquimicosActual() && ubicacionAgroFiltro != "TODOS" ->
                        "No hay productos en ${QuimicosCanonicos.etiquetaTabUbicacion(ubicacionAgroFiltro)}."
                    queryText.isNotBlank() -> "No hay coincidencias para la búsqueda."
                    else -> "No hay coincidencias para la búsqueda."
                }
                mostrarInventarioVacio(mensaje)
                resumenLabel.text = "$moduloSeleccionado: 0 items | Total: 0"
                return
            }

            var totalStock = 0.0
            filtrados.forEach { doc ->
                val cant = numeroDocumento(doc, "cantidad", "stock_actual")
                totalStock += cant
                agregarFilaInventario(
                    codigoExistencia(doc),
                    productoVisibleExistencia(doc),
                    referenciaExistencia(doc),
                    marcaExistencia(doc),
                    cant,
                    doc.getString("unidad").orEmpty(),
                    doc.getString("categoria").orEmpty()
                )
            }
            val filtroUbicacionTxt = if (esModuloAgroquimicosActual() && ubicacionAgroFiltro != "TODOS") {
                " · ${QuimicosCanonicos.etiquetaTabUbicacion(ubicacionAgroFiltro)}"
            } else {
                ""
            }
            resumenLabel.text = "$moduloSeleccionado$filtroUbicacionTxt: ${filtrados.size} items | Total: ${cantidadTexto(totalStock)}"
        }

        fun renderInventarioDesdeCacheFinal() {
            if (moduloSeleccionado.equals(AseoCanonicos.MODULO, ignoreCase = true)) pintarInventarioAseoActualizado(cacheAseoDocs)
            else pintarInventarioExistenciasActualizado(cacheExistenciasDocs)
        }

        fun construirTabsUbicacionAgro() {
            ubicacionTabsLayout.removeAllViews()
            tabsUbicacionAgro.forEach { tab ->
                val btn = Button(this).apply {
                    text = QuimicosCanonicos.etiquetaTabUbicacion(tab)
                    tag = tab
                    textSize = 11f
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                        setMargins(0, 0, dp(8), 0)
                    }
                    setOnClickListener {
                        ubicacionAgroFiltro = tab
                        actualizarEstilosUbicacionAgro()
                        renderInventarioDesdeCacheFinal()
                    }
                }
                ubicacionTabsLayout.addView(btn)
            }
            actualizarEstilosUbicacionAgro()
        }

        construirTabsUbicacionAgro()

        fun attachInventoryListener() {
            firestoreListeners.remove(currentListener)
            currentListener = null
            limpiarLista()
            resumenLabel.text = "Sincronizando $moduloSeleccionado..."

            val limite = performanceConfig.inventoryQueryLimit()
            val listenerModulo = moduloSeleccionado
            val listenerKey = "inventario:$listenerModulo"
            val listenerStart = android.os.SystemClock.elapsedRealtime()
            android.util.Log.d("PerfPrincipal", "listener inventario crear modulo=$listenerModulo limite=$limite")
            val registration = if (listenerModulo.equals(AseoCanonicos.MODULO, ignoreCase = true)) {
                cacheAseoDocs = emptyList()
                pintarInventarioAseoActualizado(emptyList())
                firestore.collection(AseoCanonicos.COLECCION)
                    .limit(limite)
                    .addSnapshotListener { snapshot, e ->
                        val callbackStart = android.os.SystemClock.elapsedRealtime()
                        ejecutarEnUi {
                            if (!pantallaActiva() || listenerModulo != moduloSeleccionado) return@ejecutarEnUi
                            try {
                                if (e != null) {
                                    android.util.Log.e("ArlesGestion", "Error leyendo productos_aseo: ${e.message}")
                                    resumenLabel.text = "ASEO sin conexión · mostrando catálogo local"
                                    pintarInventarioAseoActualizado(emptyList())
                                    return@ejecutarEnUi
                                }
                                cacheAseoDocs = snapshot?.documents ?: emptyList()
                                pintarInventarioAseoActualizado(cacheAseoDocs)
                                android.util.Log.d(
                                    "PerfPrincipal",
                                    "listener inventario modulo=$listenerModulo docs=${cacheAseoDocs.size} callback=${android.os.SystemClock.elapsedRealtime() - callbackStart}ms desde_creacion=${android.os.SystemClock.elapsedRealtime() - listenerStart}ms"
                                )
                            } catch (listenerError: Exception) {
                                android.util.Log.e("ArlesGestion", "Error en listener ASEO", listenerError)
                                Toast.makeText(
                                    activity,
                                    "ASEO: ${listenerError.localizedMessage ?: "error al sincronizar"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                pintarInventarioAseoActualizado(emptyList())
                            }
                        }
                    }
            } else {
                firestore.collection("existencias")
                    .whereEqualTo("modulo", listenerModulo)
                    .limit(limite)
                    .addSnapshotListener { snapshot, e ->
                        val callbackStart = android.os.SystemClock.elapsedRealtime()
                        ejecutarEnUi {
                            if (!pantallaActiva() || listenerModulo != moduloSeleccionado) return@ejecutarEnUi
                            if (e != null) {
                                limpiarLista()
                                android.util.Log.d("PerfPrincipal", "listener inventario modulo=$listenerModulo error=${e.localizedMessage ?: "desconocido"}")
                                resumenLabel.text = "Error de conexion."
                                mostrarInventarioVacio("No se pudo leer existencias de $listenerModulo.")
                                return@ejecutarEnUi
                            }
                            cacheExistenciasDocs = snapshot?.documents ?: emptyList()
                            pintarInventarioExistenciasActualizado(cacheExistenciasDocs)
                            android.util.Log.d(
                                "PerfPrincipal",
                                "listener inventario modulo=$listenerModulo docs=${cacheExistenciasDocs.size} callback=${android.os.SystemClock.elapsedRealtime() - callbackStart}ms desde_creacion=${android.os.SystemClock.elapsedRealtime() - listenerStart}ms"
                            )
                        }
                    }
            }

            currentListener = registration
            firestoreListeners.add(listenerKey, registration)
        }

        modulos.forEach { mod ->
            tabsLayout.addView(
                crearChipTabModulo(mod, moduloSeleccionado == mod, accentActual()) {
                    moduloSeleccionado = mod
                    actualizarCabeceraModulo()
                    val heroTexts = heroBanner.getChildAt(1) as LinearLayout
                    (heroTexts.getChildAt(0) as TextView).text = mod
                    attachInventoryListener()
                },
            )
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderInventarioDesdeCacheFinal() }
            override fun afterTextChanged(s: Editable?) {}
        })

        attachInventoryListener()
        root.addView(outlineButton("Exportar este inventario a CSV") {
            if (moduloSeleccionado == AseoCanonicos.MODULO) {
                firestore.collection(AseoCanonicos.COLECCION).get().addOnSuccessListener { exportarInventarioAseoCsv(it.documents) }
            } else {
                firestore.collection("existencias").whereEqualTo("modulo", moduloSeleccionado).get().addOnSuccessListener { exportarInventarioActualCsv(it.documents) }
            }
        })
    }

internal fun MainActivity.deltaStockDesdeMovimiento(doc: DocumentSnapshot): Double {
        val cantidad = numeroDocumento(doc, "cantidad")
        if (cantidad <= 0.0) throw IllegalArgumentException("Cantidad invalida para reprocesar")
        val tipo = normalizarBusqueda(doc.getString("tipoMovimiento") ?: doc.getString("tipo") ?: "Salida")
        return when {
            tipo.contains("entrada") || tipo.contains("ingreso") || tipo.contains("devolucion") -> cantidad
            tipo.contains("salida") || tipo.contains("entrega") || tipo.contains("consumo") || tipo.contains("prestamo") -> -cantidad
            else -> throw IllegalArgumentException("Tipo de movimiento no reprocesable: $tipo")
        }
    }

internal fun datosMovimientoReprocesado(stockAnterior: Double, stockNuevo: Double): Map<String, Any?> {
        return mapOf(
            "stock_actualizado" to true,
            "requiere_ajuste_stock" to false,
            "error_stock" to "",
            "stock_anterior" to stockAnterior,
            "stock_nuevo" to stockNuevo,
            "reprocesado_stock" to true,
            "reprocesado_fecha" to now()
        )
    }

internal fun MainActivity.reprocesarMovimientoAseo(
        movimiento: DocumentSnapshot,
        delta: Double,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val codigoBase = campoDoc(movimiento, "documento_id", "producto_id", "codigo_interno", "codigoInterno", "codigo_original", "codigo")
        val canonico = AseoCanonicos.buscarPorDocumento(codigoBase)
            ?: AseoCanonicos.items.firstOrNull { AseoCanonicos.productoCoincide(it.producto, movimiento.getString("item").orEmpty()) }
        val codigo = (canonico?.codigoInterno ?: AseoCanonicos.normalizarCodigo(codigoBase)).trim()
        if (codigo.isBlank()) {
            onFailure(IllegalArgumentException("Movimiento ASEO sin codigo de producto"))
            return
        }
        val opcion = AseoUbicacionStock(
            documentoId = codigo,
            codigoOriginal = canonico?.codigoOriginal ?: codigo,
            item = canonico?.item ?: movimiento.getString("item").orEmpty().ifBlank { codigo },
            categoria = canonico?.categoria ?: movimiento.getString("categoria").orEmpty().ifBlank { AseoCanonicos.CATEGORIA_PRINCIPAL },
            ubicacion = canonico?.ubicacion ?: movimiento.getString("ubicacion").orEmpty().ifBlank { "Piso ${AseoCanonicos.pisoDesdeCodigo(codigo)}" },
            cantidad = canonico?.stockOperativo ?: 0.0,
            unidad = movimiento.getString("unidad").orEmpty().ifBlank { canonico?.unidad ?: "UNIDAD" }
        )
        val refStock = firestore.collection(AseoCanonicos.COLECCION).document(codigo)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(refStock)
            val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "stock_actual", "cantidad") else opcion.cantidad
            val stockNuevo = stockAnterior + delta
            transaction.set(refStock, aseoFirestoreDataDesdeUbicacion(opcion, stockNuevo), SetOptions.merge())
            transaction.set(movimiento.reference, datosMovimientoReprocesado(stockAnterior, stockNuevo), SetOptions.merge())
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

internal fun MainActivity.reprocesarMovimientoCombustible(
        movimiento: DocumentSnapshot,
        delta: Double,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val tipo = movimiento.getString("item").orEmpty()
            .ifBlank { movimiento.getString("referencia").orEmpty() }
            .ifBlank { movimiento.getString("tipo").orEmpty() }
        if (tipo.isBlank()) {
            onFailure(IllegalArgumentException("Movimiento combustible sin tipo"))
            return
        }

        fun aplicar(docActual: DocumentSnapshot?) {
            val refStock = docActual?.reference
                ?: firestore.collection("existencias").document(codigoDocumentoCombustible(tipo))
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refStock)
                val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "cantidad", "stock_actual") else 0.0
                val stockNuevo = stockAnterior + delta
                val codigoVisible = if (snapshot.exists()) codigoExistencia(snapshot) else refStock.id
                val codigoOriginal = if (snapshot.exists()) codigoOriginalExistencia(snapshot) else codigoVisible
                val unidad = movimiento.getString("unidad").orEmpty().ifBlank { "Galones" }
                transaction.set(refStock, mapOf(
                    "modulo" to "Combustible",
                    "categoria" to "Combustibles",
                    "item" to itemBaseCombustible(),
                    "referencia" to tipo,
                    "tipo" to tipo,
                    "codigo_interno" to codigoVisible,
                    "codigo_original" to codigoOriginal,
                    "documento_id" to refStock.id,
                    "producto_id" to refStock.id,
                    "unidad" to unidad,
                    "cantidad" to stockNuevo,
                    "stock_actual" to stockNuevo,
                    "activo" to true,
                    "ultima_fecha" to now(),
                    "ultimo_solicitante" to movimiento.getString("solicitante").orEmpty()
                ), SetOptions.merge())
                transaction.set(movimiento.reference, datosMovimientoReprocesado(stockAnterior, stockNuevo), SetOptions.merge())
                null
            }.addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        }

        firestore.collection("existencias")
            .whereEqualTo("modulo", "Combustible")
            .get()
            .addOnSuccessListener { snapshot ->
                aplicar(snapshot.documents.firstOrNull { it.getBoolean("activo") != false && coincideTipoCombustible(tipo, it) })
            }
            .addOnFailureListener { onFailure(it) }
    }

internal fun MainActivity.reprocesarMovimientoExistencia(
        movimiento: DocumentSnapshot,
        modulo: String,
        delta: Double,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val busqueda = campoDoc(movimiento, "documento_id", "producto_id", "codigo_interno", "codigoInterno", "codigo_original", "codigo")
            .ifBlank { movimiento.getString("item").orEmpty() }
        if (busqueda.isBlank()) {
            onFailure(IllegalArgumentException("Movimiento sin codigo o producto"))
            return
        }
        buscarDocumentoExistencia(busqueda, modulo, { docStock ->
            if (docStock == null) {
                onFailure(IllegalStateException("No se encontro existencia para $busqueda"))
                return@buscarDocumentoExistencia
            }
            val refStock = docStock.reference
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refStock)
                val stockAnterior = numeroDocumento(snapshot, "cantidad", "stock_actual")
                val stockNuevo = stockAnterior + delta
                transaction.set(refStock, mapOf(
                    "cantidad" to stockNuevo,
                    "stock_actual" to stockNuevo,
                    "ultima_fecha" to now(),
                    "ultimo_solicitante" to movimiento.getString("solicitante").orEmpty()
                ), SetOptions.merge())
                transaction.set(movimiento.reference, datosMovimientoReprocesado(stockAnterior, stockNuevo), SetOptions.merge())
                null
            }.addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        }, onFailure)
    }

internal fun MainActivity.reprocesarMovimientoPendienteStock(
        movimiento: DocumentSnapshot,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (movimiento.getBoolean("requiere_ajuste_stock") != true) {
            onSuccess()
            return
        }
        val modulo = movimiento.getString("modulo").orEmpty()
        val delta = try {
            deltaStockDesdeMovimiento(movimiento)
        } catch (e: Exception) {
            onFailure(e)
            return
        }
        when {
            modulo.equals(AseoCanonicos.MODULO, ignoreCase = true) -> reprocesarMovimientoAseo(movimiento, delta, onSuccess, onFailure)
            modulo.equals("Combustible", ignoreCase = true) -> reprocesarMovimientoCombustible(movimiento, delta, onSuccess, onFailure)
            else -> reprocesarMovimientoExistencia(movimiento, modulo, delta, onSuccess, onFailure)
        }
    }

internal fun MainActivity.reprocesarAjustesPendientesStock(
        modulo: String,
        onDone: (Int, Int) -> Unit
    ) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para reprocesar ajustes", Toast.LENGTH_LONG).show()
            onDone(0, 0)
            return
        }
        firestore.collection("movimientos")
            .whereEqualTo("modulo", modulo)
            .whereEqualTo("requiere_ajuste_stock", true)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    onDone(0, 0)
                    return@addOnSuccessListener
                }
                fun procesar(indice: Int, ok: Int, fallos: Int) {
                    if (indice >= docs.size) {
                        onDone(ok, fallos)
                        return
                    }
                    val doc = docs[indice]
                    reprocesarMovimientoPendienteStock(doc, {
                        procesar(indice + 1, ok + 1, fallos)
                    }, { e ->
                        android.util.Log.e("ArlesGestion", "Fallo reprocesando ajuste ${doc.id}: ${e.message}")
                        doc.reference.set(mapOf(
                            "error_stock" to "Reproceso fallo: ${e.localizedMessage ?: "error desconocido"}",
                            "ultimo_reproceso_fallido" to now()
                        ), SetOptions.merge()).addOnCompleteListener {
                            procesar(indice + 1, ok, fallos + 1)
                        }
                    })
                }
                procesar(0, 0, 0)
            }
            .addOnFailureListener {
                Toast.makeText(this, it.localizedMessage ?: "No se pudieron consultar pendientes", Toast.LENGTH_LONG).show()
                onDone(0, 1)
            }
    }

internal fun MainActivity.showTablaMovimientos() {
        val activity = this
        currentScreenRenderer = { showTablaMovimientos() }
        val root = baseScreen("Movimientos (Nube)", "Historial de entradas y salidas en tiempo real.")

        val modulos = AppMode.modulosMovimientosNube()
        var moduloSeleccionado = modulos.first()
        fun accentActual() = moduloInventarioAccent(moduloSeleccionado)

        val tabsScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
        }
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        tabsScroll.addView(tabsLayout)
        root.addView(tabsScroll)

        val heroBanner = moduleHeroBanner(
            R.drawable.ic_inbox,
            moduloSeleccionado,
            "Entradas y salidas sincronizadas en tiempo real.",
            accentActual(),
        )
        root.addView(heroBanner)

        root.addView(sectionHeader("Filtro", "Busca por código, producto, solicitante o referencia."))
        val search = tallerSearchField("Escribe código, nombre, solicitante o referencia")
        root.addView(search)

        root.addView(outlineButton("Reprocesar ajustes pendientes") {
            AlertDialog.Builder(activity)
                .setTitle("Reprocesar ajustes")
                .setMessage("Se intentaran descontar o sumar los movimientos amarillos de $moduloSeleccionado. Se procesan maximo 50 por vez.")
                .setPositiveButton("Reprocesar") { _, _ ->
                    reprocesarAjustesPendientesStock(moduloSeleccionado) { ok, fallos ->
                        val mensaje = when {
                            ok == 0 && fallos == 0 -> "No hay ajustes pendientes en $moduloSeleccionado"
                            fallos == 0 -> "Ajustes reprocesados: $ok"
                            else -> "Reprocesados: $ok | Fallidos: $fallos"
                        }
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        })

        val resumenLabel = catalogoResumenBar("Sincronizando movimientos...", accentActual())
        root.addView(resumenLabel)

        val listaCard = card()
        val listaContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listaCard.addView(listaContainer)
        root.addView(listaCard)

        fun limpiarLista() {
            listaContainer.removeAllViews()
        }

        fun actualizarCabeceraModulo() {
            val accent = accentActual()
            heroBanner.background = arlesRoundedBackground(accent, null, 18)
            resumenLabel.setTextColor(accent)
            actualizarChipsModulo(tabsLayout, moduloSeleccionado, accent)
            val heroTexts = heroBanner.getChildAt(1) as LinearLayout
            (heroTexts.getChildAt(0) as TextView).text = moduloSeleccionado
        }

        var currentListener: com.google.firebase.firestore.ListenerRegistration? = null
        val movimientosScreenId = currentScreenId
        val movimientosSearchHandler = Handler(Looper.getMainLooper())
        var movimientosSearchRunnable: Runnable? = null

        fun mostrarDetalleMovimiento(doc: com.google.firebase.firestore.DocumentSnapshot) {
            val tipo = doc.getString("tipoMovimiento") ?: doc.getString("tipo") ?: "Salida"
            val cantObj = doc.get("cantidad")
            val cantStr = cantObj?.toString() ?: "0"
            val fotoUrl = doc.getString("fotoUrl").orEmpty()
            val requiereAjusteStock = doc.getBoolean("requiere_ajuste_stock") == true
            val stockInfo = if (requiereAjusteStock) {
                "\nStock: PENDIENTE DE AJUSTE (${doc.getString("error_stock") ?: "sin detalle"})"
            } else {
                ""
            }
            val codigoMov = doc.getString("codigo_original")
                ?: doc.getString("codigo_interno")
                ?: doc.getString("codigoInterno")
                ?: doc.getString("codigo")
                ?: ""
            val detalle = "Producto: ${doc.getString("item") ?: "Desconocido"}\n" +
                "Codigo: ${codigoMov.ifBlank { "N/A" }}\n" +
                "Cantidad: $cantStr ${doc.getString("unidad") ?: ""}\n" +
                "Tipo: $tipo\n" +
                "Solicitante: ${doc.getString("solicitante") ?: doc.getString("responsable") ?: "—"}\n" +
                "Labor: ${doc.getString("labor") ?: "—"}\n" +
                "Fecha: ${doc.getString("fecha") ?: "—"}\n" +
                "Obs: ${doc.getString("observaciones") ?: ""}$stockInfo"
            if (fotoUrl.isNotBlank()) {
                mostrarDialogoFotoDetalle(fotoUrl, detalle)
            } else {
                AlertDialog.Builder(activity).setTitle("Detalle del Registro").setMessage(detalle).show()
            }
        }

        fun updateTableRealTime() {
            if (currentScreenId != movimientosScreenId) {
                android.util.Log.d("PerfPrincipal", "listener movimientos omitido=pantalla_inactiva")
                return
            }
            firestoreListeners.remove(currentListener)
            currentListener = null
            limpiarLista()
            resumenLabel.text = "Sincronizando $moduloSeleccionado..."

            val queryText = normalizarBusqueda(search.text.toString())
            val listenerModulo = moduloSeleccionado
            val listenerKey = "movimientos:$listenerModulo"
            val listenerStart = android.os.SystemClock.elapsedRealtime()
            android.util.Log.d("PerfPrincipal", "listener movimientos crear modulo=$listenerModulo limite=${performanceConfig.movementQueryLimit()} filtro=${queryText.isNotBlank()}")
            val queryBase = firestore.collection("movimientos")
            val registration = if (TallerCanonicos.esModuloTaller(listenerModulo)) {
                queryBase
                    .whereIn("modulo", listOf(TallerCanonicos.MODULO, TallerCanonicos.MODULO_LEGACY))
                    .limit(performanceConfig.movementQueryLimit())
            } else {
                queryBase
                    .whereEqualTo("modulo", listenerModulo)
                    .limit(performanceConfig.movementQueryLimit())
            }.addSnapshotListener { snapshot, e ->
                    val callbackStart = android.os.SystemClock.elapsedRealtime()
                    if (!pantallaActiva() || listenerModulo != moduloSeleccionado) return@addSnapshotListener
                    limpiarLista()

                    if (e != null) {
                        android.util.Log.e("ArlesGestión", "Error en movimientos real-time: ${e.message}")
                        android.util.Log.d("PerfPrincipal", "listener movimientos modulo=$listenerModulo error=${e.localizedMessage ?: "desconocido"}")
                        resumenLabel.text = "Error de conexión en $listenerModulo"
                        listaContainer.addView(tallerEmptyState("Sin conexión", "No se pudo leer movimientos de $listenerModulo."))
                        return@addSnapshotListener
                    }

                    val rawDocs = snapshot?.documents ?: emptyList()
                    android.util.Log.d(
                        "PerfPrincipal",
                        "listener movimientos modulo=$listenerModulo docs=${rawDocs.size} callback=${android.os.SystemClock.elapsedRealtime() - callbackStart}ms desde_creacion=${android.os.SystemClock.elapsedRealtime() - listenerStart}ms"
                    )
                    val docs = rawDocs
                        .sortedByDescending { it.getString("fecha") ?: "" }
                        .filter { doc ->
                            if (queryText.isBlank()) true
                            else {
                                val busqueda = (doc.getString("item") ?: "") +
                                    (doc.getString("codigo_interno") ?: "") +
                                    (doc.getString("codigoInterno") ?: "") +
                                    (doc.getString("codigo_original") ?: "") +
                                    (doc.getString("codigo") ?: "") +
                                    doc.id +
                                    (doc.getString("solicitante") ?: "") +
                                    (doc.getString("referencia") ?: "") +
                                    (doc.getString("labor") ?: "")
                                normalizarBusqueda(busqueda).contains(queryText)
                            }
                        }

                    if (docs.isEmpty()) {
                        val mensaje = if (queryText.isNotBlank()) {
                            "No hay coincidencias para la búsqueda en $moduloSeleccionado."
                        } else {
                            "No hay registros visibles en $moduloSeleccionado."
                        }
                        resumenLabel.text = "$moduloSeleccionado: 0 movimientos"
                        listaContainer.addView(tallerEmptyState("Sin movimientos", mensaje))
                        return@addSnapshotListener
                    }

                    val limiteVisible = if (queryText.isBlank()) {
                        performanceConfig.movementQueryLimit().toInt()
                    } else {
                        performanceConfig.movementRenderLimit()
                    }
                    val (visible, ocultos) = RenderLimiter.applyLimit(docs, limiteVisible)
                    resumenLabel.text = buildString {
                        append("$moduloSeleccionado: ${docs.size} movimientos")
                        if (ocultos > 0) append(" | +$ocultos ocultos (usa el filtro)")
                    }

                    visible.forEach { doc ->
                        val tipo = doc.getString("tipoMovimiento") ?: doc.getString("tipo") ?: "Salida"
                        val cantStr = doc.get("cantidad")?.toString() ?: "0"
                        val codigoMov = doc.getString("codigo_original")
                            ?: doc.getString("codigo_interno")
                            ?: doc.getString("codigoInterno")
                            ?: doc.getString("codigo")
                            ?: ""
                        listaContainer.addView(
                            catalogoMovimientoCard(
                                fecha = doc.getString("fecha")?.takeLast(11) ?: "—",
                                tipo = tipo,
                                codigo = codigoMov,
                                producto = doc.getString("item") ?: "—",
                                cantidad = cantStr,
                                unidad = doc.getString("unidad") ?: "",
                                solicitante = doc.getString("solicitante") ?: doc.getString("responsable") ?: "",
                                labor = doc.getString("labor") ?: "",
                                observaciones = doc.getString("observaciones") ?: "",
                                requiereAjuste = doc.getBoolean("requiere_ajuste_stock") == true,
                                tieneFoto = doc.getString("fotoUrl").orEmpty().isNotBlank(),
                                accent = accentActual(),
                            ) { mostrarDetalleMovimiento(doc) },
                        )
                    }
                }

            currentListener = registration
            firestoreListeners.add(listenerKey, registration)
        }

        modulos.forEach { mod ->
            tabsLayout.addView(
                crearChipTabModulo(mod, moduloSeleccionado == mod, accentActual()) {
                    moduloSeleccionado = mod
                    actualizarCabeceraModulo()
                    updateTableRealTime()
                },
            )
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                movimientosSearchRunnable?.let { movimientosSearchHandler.removeCallbacks(it) }
                movimientosSearchRunnable = Runnable {
                    if (currentScreenId != movimientosScreenId) return@Runnable
                    android.util.Log.d("PerfPrincipal", "listener movimientos filtro debounce modulo=$moduloSeleccionado")
                    updateTableRealTime()
                }
                movimientosSearchHandler.postDelayed(movimientosSearchRunnable!!, 450L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateTableRealTime()
        root.addView(outlineButton("Exportar historial completo a CSV") { exportarCsv() })
    }

internal fun MainActivity.registrarSalidaCombustibleFirestore(
        mov: Movimiento,
        cantidadNumerica: Double,
        fotoUrl: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (fotoUrl.isNotBlank() && evidenciaEsUriLocal(fotoUrl) && isNetworkAvailable()) {
            Toast.makeText(this, "Subiendo evidencia antes de guardar...", Toast.LENGTH_SHORT).show()
            subirEvidenciaCloud(fotoUrl, "combustible") { url ->
                if (url.isNotBlank()) {
                    registrarSalidaCombustibleFirestore(mov, cantidadNumerica, url, onSuccess, onFailure)
                } else {
                    onFailure?.invoke(IllegalStateException("No se pudo subir la evidencia del movimiento"))
                }
            }
            return
        }
        if (cantidadNumerica <= 0.0) {
            onFailure?.invoke(IllegalArgumentException("La cantidad debe ser mayor a cero"))
            return
        }
        if (!isNetworkAvailable()) {
            encolarMovimientoOffline(mov, cantidadNumerica, mov.item, fotoUrl)
            Toast.makeText(this, "Salida guardada offline; combustible pendiente de sincronizar", Toast.LENGTH_LONG).show()
            onSuccess?.invoke()
            return
        }

        val tipo = mov.item.trim().ifBlank { mov.referencia.trim() }
        if (tipo.isBlank()) {
            onFailure?.invoke(IllegalArgumentException("Selecciona el tipo de combustible"))
            return
        }

        fun guardarConDocumento(docActual: DocumentSnapshot?, usuario: String) {
            val refDoc = docActual?.reference
                ?: firestore.collection("existencias").document(codigoDocumentoCombustible(tipo))

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refDoc)
                val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "cantidad", "stock_actual") else 0.0
                val stockNuevo = stockAnterior - cantidadNumerica
                val codigoVisible = if (snapshot.exists()) codigoExistencia(snapshot) else refDoc.id
                val codigoOriginal = if (snapshot.exists()) codigoOriginalExistencia(snapshot) else codigoVisible
                val unidad = mov.unidad.ifBlank { "Galones" }

                transaction.set(refDoc, mapOf(
                    "modulo" to "Combustible",
                    "categoria" to "Combustibles",
                    "item" to itemBaseCombustible(),
                    "referencia" to tipo,
                    "tipo" to tipo,
                    "codigo_interno" to codigoVisible,
                    "codigo_original" to codigoOriginal,
                    "documento_id" to refDoc.id,
                    "producto_id" to refDoc.id,
                    "unidad" to unidad,
                    "cantidad" to stockNuevo,
                    "stock_actual" to stockNuevo,
                    "activo" to true,
                    "ultima_fecha" to mov.fecha,
                    "ultimo_solicitante" to mov.solicitante
                ), SetOptions.merge())

                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to mov.fecha,
                    "modulo" to "Combustible",
                    "tipoMovimiento" to mov.tipoMovimiento,
                    "item" to tipo,
                    "referencia" to tipo,
                    "codigo_interno" to codigoVisible,
                    "codigo_original" to codigoOriginal,
                    "documento_id" to refDoc.id,
                    "producto_id" to refDoc.id,
                    "ubicacion" to "Combustibles",
                    "cantidad" to mov.cantidad,
                    "unidad" to unidad,
                    "solicitante" to mov.solicitante,
                    "labor" to mov.labor,
                    "maquinaria" to mov.maquinaria,
                    "horometro" to mov.horometro,
                    "observaciones" to mov.observaciones,
                    "usuario" to usuario,
                    "fotoUrl" to fotoUrl,
                    "stock_actualizado" to true,
                    "stock_anterior" to stockAnterior,
                    "stock_nuevo" to stockNuevo
                ))
                mapOf("ant" to stockAnterior, "nve" to stockNuevo, "codigo" to codigoVisible)
            }.addOnSuccessListener { res ->
                registrarCambioLocal(
                    "SALIDA_STOCK",
                    "Combustible",
                    res["codigo"].toString(),
                    "Salida: $tipo",
                    res["ant"].toString(),
                    res["nve"].toString()
                )
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                android.util.Log.e("ArlesGestion", "Fallo salida combustible: ${e.message}")
                onFailure?.invoke(e)
            }
        }

        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
            firestore.collection("existencias")
                .whereEqualTo("modulo", "Combustible")
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.firstOrNull {
                        it.getBoolean("activo") != false && coincideTipoCombustible(tipo, it)
                    }
                    guardarConDocumento(doc, usuario)
                }
                .addOnFailureListener { onFailure?.invoke(it) }
        }
    }

internal fun MainActivity.registrarSalidaCloudPrimero(
        mov: Movimiento,
        cantidadNumerica: Double,
        codigoInternoPreferido: String = "",
        itemBase: String = "",
        fotoUrl: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (fotoUrl.isNotBlank() && evidenciaEsUriLocal(fotoUrl) && isNetworkAvailable()) {
            Toast.makeText(this, "Subiendo evidencia antes de guardar...", Toast.LENGTH_SHORT).show()
            subirEvidenciaCloud(fotoUrl, mov.modulo) { url ->
                if (url.isNotBlank()) {
                    registrarSalidaCloudPrimero(
                        mov = mov,
                        cantidadNumerica = cantidadNumerica,
                        codigoInternoPreferido = codigoInternoPreferido,
                        itemBase = itemBase,
                        fotoUrl = url,
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                } else {
                    val error = IllegalStateException("No se pudo subir la evidencia del movimiento")
                    Toast.makeText(this, "No se guardó: la evidencia no pudo subirse", Toast.LENGTH_LONG).show()
                    onFailure?.invoke(error)
                }
            }
            return
        }

        if (!isNetworkAvailable()) {
            encolarMovimientoOffline(mov, cantidadNumerica, itemBase, fotoUrl)
            onSuccess?.invoke()
            return
        }

        val uid = auth.currentUser?.uid ?: ""
        val busqueda = codigoInternoPreferido.ifBlank { itemBase.ifBlank { mov.item } }
        buscarDocumentoExistencia(busqueda, mov.modulo, { doc ->
            
            val movData = mutableMapOf<String, Any?>(
                "fecha" to mov.fecha, "modulo" to mov.modulo, "tipoMovimiento" to mov.tipoMovimiento,
                "item" to mov.item, "referencia" to mov.referencia, "cantidad" to mov.cantidad,
                "unidad" to mov.unidad, "solicitante" to mov.solicitante, "labor" to mov.labor,
                "maquinaria" to mov.maquinaria, "horometro" to mov.horometro, "herramientaId" to mov.herramientaId,
                "estado" to mov.estado, "observaciones" to mov.observaciones, "usuario" to uid,
                "fotoUrl" to fotoUrl
            )

            if (doc != null) {
                val codigoVisible = codigoOriginalExistencia(doc).ifBlank { codigoExistencia(doc) }
                movData["codigo_interno"] = codigoVisible
                movData["codigo_original"] = codigoVisible
                movData["documento_id"] = doc.id
                movData["producto_id"] = doc.id
                movData["ubicacion"] = ubicacionExistencia(doc)
                
                // Intentar descontar stock y guardar movimiento
                val refDoc = firestore.collection("existencias").document(doc.id)
                
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(refDoc)
                    val stockActual = numeroDocumento(snapshot, "cantidad", "stock_actual")
                    val stockNuevo = stockActual - cantidadNumerica
                    
                    // Actualización de stock
                    transaction.set(refDoc, mapOf(
                        "cantidad" to stockNuevo,
                        "stock_actual" to stockNuevo,
                        "ultima_fecha" to mov.fecha,
                        "ultimo_solicitante" to mov.solicitante
                    ), SetOptions.merge())
                    
                    // Registro del movimiento
                    val movConStock = movData.toMutableMap().apply {
                        put("stock_actualizado", true)
                        put("stock_anterior", stockActual)
                        put("stock_nuevo", stockNuevo)
                    }
                    transaction.set(firestore.collection("movimientos").document(), movConStock)
                    
                    null // Resultado de la transacción
                }.addOnSuccessListener { 
                    onSuccess?.invoke() 
                }.addOnFailureListener { e -> 
                    android.util.Log.e("ArlesGestión", "Fallo descuento de stock: ${e.message}")
                    val movPendienteStock = movData.toMutableMap().apply {
                        put("stock_actualizado", false)
                        put("requiere_ajuste_stock", true)
                        put("error_stock", e.localizedMessage ?: "No se pudo actualizar stock")
                    }
                    firestore.collection("movimientos").add(movPendienteStock)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Movimiento guardado; stock pendiente de ajuste", Toast.LENGTH_LONG).show()
                            onSuccess?.invoke()
                        }
                        .addOnFailureListener { onFailure?.invoke(it) }
                }
            } else {
                // Producto manual o no encontrado en DB
                movData["observaciones"] = (mov.observaciones + " (Manual/No en DB)").trim()
                movData["stock_actualizado"] = false
                movData["requiere_ajuste_stock"] = true
                movData["error_stock"] = "Producto no encontrado en existencias"
                firestore.collection("movimientos").add(movData)
                    .addOnSuccessListener { onSuccess?.invoke() }
                    .addOnFailureListener { onFailure?.invoke(it) }
            }
        }, { 
            // Fallback total
            val fallbackData = mapOf(
                "fecha" to mov.fecha,
                "item" to mov.item,
                "referencia" to mov.referencia,
                "cantidad" to mov.cantidad,
                "unidad" to mov.unidad,
                "modulo" to mov.modulo,
                "tipoMovimiento" to "Salida",
                "solicitante" to mov.solicitante,
                "labor" to mov.labor,
                "observaciones" to mov.observaciones,
                "fotoUrl" to fotoUrl,
                "stock_actualizado" to false,
                "requiere_ajuste_stock" to true,
                "error_stock" to (it.localizedMessage ?: "No se pudo validar existencia")
            )
            firestore.collection("movimientos").add(fallbackData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Movimiento guardado; stock pendiente de ajuste", Toast.LENGTH_LONG).show()
                    onSuccess?.invoke()
                }
                .addOnFailureListener { e -> onFailure?.invoke(e) }
        })
    }

internal fun MainActivity.actualizarStock(
        codigo: String,
        delta: Double,
        unidad: String,
        esEntrada: Boolean,
        item: String = "",
        referencia: String = "",
        modulo: String = "",
        solicitante: String = ""
    ) {
        val refDoc = firestore.collection("existencias").document(codigo)
        val fecha = now()
        val solFinal = solicitante.ifBlank { auth.currentUser?.email ?: "Sistema" }

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(refDoc)
            val anterior = numeroDocumento(snapshot, "cantidad", "stock_actual")
            val nuevo = if (esEntrada) anterior + delta else anterior - delta
            
            if (!snapshot.exists()) {
                val data = mapOf(
                    "codigo_interno" to codigo,
                    "item" to item,
                    "referencia" to referencia,
                    "cantidad" to nuevo,
                    "unidad" to unidad,
                    "modulo" to modulo,
                    "ultima_fecha" to fecha,
                    "ultimo_solicitante" to solFinal
                )
                transaction.set(refDoc, data)
            } else {
                transaction.set(refDoc, mapOf(
                    "cantidad" to nuevo,
                    "stock_actual" to nuevo,
                    "ultima_fecha" to fecha,
                    "ultimo_solicitante" to solFinal
                ), SetOptions.merge())
            }
            anterior to nuevo
        }.addOnSuccessListener { (ant, n) ->
            registrarCambioLocal(
                "ACTUALIZAR_STOCK", 
                modulo, 
                codigo, 
                "Stock actualizado por ${if (esEntrada) "Entrada" else "Salida"}: $item", 
                "$ant $unidad", 
                "$n $unidad"
            )
        }
    }


internal fun MainActivity.registrarEntradaFirestoreYStock(
        entrada: Entrada, codigoInterno: String, categoria: String, itemBase: String, referenciaCatalogo: String,
        onSuccess: (() -> Unit)? = null, onFailure: ((Exception) -> Unit)? = null
    ) {
        val codigo = candidatosCodigoInterno(codigoInterno).firstOrNull() ?: normalizarCodigoInterno(codigoInterno)
        val canonicoEntrada = QuimicosCanonicos.buscarPorDocumento(codigo)
        obtenerInfoUsuario(auth.currentUser?.uid ?: "") { infoUsuario ->
            val refDoc = firestore.collection("existencias").document(codigo)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(refDoc)
                val stockAnterior = numeroDocumento(snapshot, "cantidad", "stock_actual")
                val stockNuevo = stockAnterior + entrada.cantidad
                val ubicacion = snapshot.getString("ubicacion").orEmpty()
                val codigoOriginal = snapshot.getString("codigo_original").orEmpty().ifBlank { entrada.referencia }
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to entrada.fecha, "modulo" to entrada.modulo, "tipoMovimiento" to "Entrada", "item" to entrada.item,
                    "referencia" to entrada.referencia, "codigoInterno" to codigo, "cantidad" to entrada.cantidad,
                    "unidad" to entrada.unidad, "usuario" to infoUsuario, "observaciones" to entrada.observaciones,
                    "codigo_original" to codigoOriginal, "documento_id" to codigo, "producto_id" to codigo, "ubicacion" to ubicacion
                ))
                if (snapshot.exists()) {
                    transaction.set(refDoc, mapOf(
                        "cantidad" to stockNuevo,
                        "stock_actual" to stockNuevo,
                        "ultima_fecha" to entrada.fecha,
                        "ultimo_solicitante" to infoUsuario
                    ), SetOptions.merge())
                } else {
                    val codigoVisible = canonicoEntrada?.codigoOriginal ?: entrada.referencia.ifBlank { codigo }
                    transaction.set(refDoc, mapOf(
                        "modulo" to entrada.modulo, "categoria" to categoria, "item" to itemBase.ifBlank { entrada.item },
                        "referencia" to entrada.referencia, "codigo_interno" to codigoVisible, "unidad" to entrada.unidad,
                        "cantidad" to stockNuevo, "stock_actual" to stockNuevo, "ultima_fecha" to entrada.fecha, "ultimo_solicitante" to infoUsuario,
                        "codigo_original" to codigoVisible,
                        "documento_id" to codigo,
                        "producto_id" to codigo,
                        "subcategoria" to (canonicoEntrada?.subcategoria ?: ""),
                        "ubicacion" to (canonicoEntrada?.ubicacion ?: "")
                    ))
                }
                val codigoVisibleCatalogo = canonicoEntrada?.codigoOriginal ?: entrada.referencia.ifBlank { codigo }
                transaction.set(firestore.collection("catalogo_personalizado").document(codigo), mapOf(
                    "modulo" to entrada.modulo, "categoria" to categoria, "item" to itemBase.ifBlank { entrada.item },
                    "referencia" to entrada.referencia, "codigo_interno" to codigoVisibleCatalogo,
                    "codigo_original" to codigoVisibleCatalogo,
                    "documento_id" to codigo,
                    "producto_id" to codigo,
                    "subcategoria" to (canonicoEntrada?.subcategoria ?: ""),
                    "ubicacion" to (canonicoEntrada?.ubicacion ?: "")
                ), SetOptions.merge())
                mapOf("ant" to stockAnterior, "nve" to stockNuevo)
            }.addOnSuccessListener { res ->
            registrarCambioLocal(
                "ENTRADA_STOCK", 
                entrada.modulo, 
                codigo, 
                "Ingreso", 
                res["ant"].toString(), 
                res["nve"].toString()
            )
                onSuccess?.invoke()
            }.addOnFailureListener { onFailure?.invoke(it) }
        }
    }


internal fun sugerirUnidadGeneral(modulo: String, item: String): String {
        val i = item.uppercase()
        return when {
            i.contains("GALON") -> "GALON"
            i.contains("ACEITE") || i.contains("COMBUSTIBLE") || i.contains("LIQUIDO") -> "Litro"
            i.contains("GRASA") || i.contains("ABONO") || i.contains("FERTILIZANTE") -> "Kg"
            i.contains("CABLE") || i.contains("MANGUERA") || i.contains("SOGA") -> "Metro"
            else -> "Unidad"
        }
    }

internal fun normalizarBusqueda(valor: String): String {
        return Normalizer.normalize(valor.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    }

internal fun MainActivity.normalizarCodigoInterno(valor: String): String {
        return valor.trim().uppercase(Locale.getDefault()).replace(" ", "").replace("-", "").replace("_", "")
    }

internal fun MainActivity.candidatosCodigoInterno(valor: String): List<String> {
        val escrito = valor.trim().uppercase(Locale.getDefault()).replace(" ", "").replace("_", "-")
        val normalizado = normalizarCodigoInterno(valor)
        val conGuion = Regex("^([A-Z]+)(\\d+)$").matchEntire(normalizado)?.let { match ->
            "${match.groupValues[1]}-${match.groupValues[2]}"
        }
        return listOfNotNull(escrito, normalizado, conGuion)
            .filter { it.isNotBlank() }
            .distinct()
    }

internal fun extraerTallaReferencia(valor: String): String {
        val normal = normalizarBusqueda(valor)
        Regex("\\btalla\\s+([a-z0-9]+)\\b").find(normal)?.let { return it.groupValues[1].uppercase(Locale.getDefault()) }
        return if (normal.matches(Regex("[a-z0-9]{1,5}"))) normal.uppercase(Locale.getDefault()) else ""
    }

internal fun referenciaDotacionPresentable(valor: String): String {
        val talla = extraerTallaReferencia(valor)
        if (talla.isNotBlank()) return "Talla: $talla"
        return valor.trim().ifBlank { "N/A" }
    }

internal fun referenciasInventarioCoinciden(esperada: String, registrada: String): Boolean {
        val espNorm = normalizarBusqueda(esperada)
        val regNorm = normalizarBusqueda(registrada)
        if (espNorm.isNotBlank() && espNorm == regNorm) return true

        val tallaEsperada = extraerTallaReferencia(esperada)
        val tallaRegistrada = extraerTallaReferencia(registrada)
        if (tallaEsperada.isNotBlank() && tallaEsperada == tallaRegistrada) return true

        return espNorm.length > 2 && regNorm.contains(espNorm)
    }

internal fun eppItemBaseYTalla(item: String, referencia: String = ""): Pair<String, String> {
        val limpio = item.trim().replace(Regex("\\s+"), " ")
        val reglas = listOf(
            Regex("\\s+T\\s*UNICA$", RegexOption.IGNORE_CASE) to "Unica",
            Regex("\\s+(TXXL|TXL|TL|TM|T10|T9|T8|T44|T40|T38)$", RegexOption.IGNORE_CASE) to null,
            Regex("\\s+(XXL|XL|L|M)$", RegexOption.IGNORE_CASE) to null,
            Regex("\\s+(C25|C16)$", RegexOption.IGNORE_CASE) to null
        )

        for ((regex, tallaFija) in reglas) {
            val match = regex.find(limpio) ?: continue
            val talla = tallaFija ?: match.value.trim().uppercase(Locale.getDefault())
            val base = regex.replace(limpio, "").trim()
            return base.ifBlank { limpio } to talla
        }

        return limpio to "Unica"
    }

internal fun MainActivity.moduloCatalogoCoincide(registrado: String, esperado: String): Boolean {
        if (esperado.isBlank()) return true
        if (registrado.equals(esperado, ignoreCase = true)) return true
        val regCanon = moduloCanonicoInventario(registrado)
        val espCanon = moduloCanonicoInventario(esperado)
        if (regCanon.equals(espCanon, ignoreCase = true)) return true
        return ModulosInventario.modulosCompatibles(registrado, esperado)
    }

internal fun MainActivity.todosProductosCatalogo(modulo: String? = null): List<ProductoCatalogo> {
        val lista = mutableListOf<ProductoCatalogo>()
        val moduloFiltro = modulo?.trim().orEmpty()
        catalogoCargado.forEach { (mod, catMap) ->
            if (moduloFiltro.isBlank() || moduloCatalogoCoincide(mod, moduloFiltro)) {
                catMap.forEach { (cat, itemMap) ->
                    itemMap.forEach { (item, refs) ->
                        refs.forEach { ref -> lista.add(ProductoCatalogo(mod, cat, item, ref)) }
                    }
                }
            }
        }
        return lista
    }

internal fun MainActivity.nombreProductoCatalogo(modulo: String, item: String, referencia: String): String {
        return when {
            moduloNormalizado(modulo) == "dotacion" -> "$item - ${referenciaDotacionPresentable(referencia)}"
            ModulosInventario.esModuloQuimicoOperativo(modulo) -> "$referencia - $item"
            modulo.equals("Combustible", ignoreCase = true) -> referencia.ifBlank { item }
            else -> "$item - $referencia"
        }
    }

internal fun MainActivity.resolverProductoCatalogo(modulo: String, item: String, referencia: String = ""): ProductoCatalogo? {
        val itemBusqueda = item.trim()
        val refBusqueda = referencia.trim()
        if (itemBusqueda.isBlank() && refBusqueda.isBlank()) return null

        val moduloNorm = moduloCanonicoInventario(modulo, itemBusqueda, refBusqueda).ifBlank { modulo.trim() }

        if (moduloNorm.equals("Combustible", ignoreCase = true)) {
            val tipo = itemBusqueda.ifBlank { refBusqueda }
            return todosProductosCatalogo(moduloNorm).find {
                it.referencia.equals(tipo, ignoreCase = true) || it.item.equals(tipo, ignoreCase = true)
            } ?: todosProductosCatalogo(null).find {
                it.modulo.equals("Combustible", ignoreCase = true) &&
                    (it.referencia.equals(tipo, ignoreCase = true) || it.item.equals(tipo, ignoreCase = true))
            }
        }
        if (ModulosInventario.esModuloQuimicoOperativo(moduloNorm)) {
            val itemNorm = normalizarBusqueda(itemBusqueda)
            val refNorm = normalizarBusqueda(refBusqueda)
            val exactoQuimico = todosProductosCatalogo(moduloNorm).find {
                normalizarBusqueda(it.referencia) == itemNorm ||
                    normalizarBusqueda(it.referencia) == refNorm ||
                    normalizarBusqueda(nombreProductoCatalogo(it.modulo, it.item, it.referencia)) == itemNorm
            }
            if (exactoQuimico != null) return exactoQuimico
        }

        val candidatos = if (moduloNorm.isBlank()) todosProductosCatalogo(null) else todosProductosCatalogo(moduloNorm)
        val q = if (refBusqueda.isBlank()) itemBusqueda.uppercase() else nombreProductoCatalogo(moduloNorm.ifBlank { modulo }, itemBusqueda, refBusqueda).uppercase()
        val exacto = candidatos.find {
            nombreProductoCatalogo(it.modulo, it.item, it.referencia).uppercase() == q ||
                normalizarBusqueda(it.item) == normalizarBusqueda(itemBusqueda) &&
                (refBusqueda.isBlank() || referenciasInventarioCoinciden(refBusqueda, it.referencia)) ||
                normalizarBusqueda(it.referencia) == normalizarBusqueda(itemBusqueda) ||
                normalizarBusqueda(it.referencia) == normalizarBusqueda(refBusqueda)
        }
        if (exacto != null) return exacto

        return resolverProductoFuzzy(moduloNorm, itemBusqueda, refBusqueda).firstOrNull { it.second >= 0.68 }?.first
            ?: resolverProductoFuzzy("", itemBusqueda, refBusqueda).firstOrNull { it.second >= 0.58 }?.first
            ?: resolverProductoPorTokens(itemBusqueda, refBusqueda, moduloNorm)
    }

internal fun MainActivity.resolverProductoDesdePrompt(prompt: String, modulo: String = ""): ProductoCatalogo? {
        val consulta = BusquedaInventarioIA.extraerFraseProducto(prompt).ifBlank { prompt }
        if (consulta.isBlank()) return null
        val moduloDetectado = modulo.trim().ifBlank { detectarModuloDesdePrompt(prompt) }
        val moduloNorm = moduloCanonicoInventario(moduloDetectado, consulta, "").ifBlank { moduloDetectado }
        val sugerencias = resolverSugerenciasProductoIA(moduloNorm, consulta, "", 4)
        return resolverProductoCatalogo(moduloNorm, consulta, "")
            ?: if (esCoincidenciaProductoSegura(sugerencias)) sugerencias.first().first else null
    }

internal fun MainActivity.resolverProductoPorTokens(item: String, referencia: String = "", modulo: String = ""): ProductoCatalogo? {
        val consulta = listOf(item, referencia).filter { it.isNotBlank() }.joinToString(" ")
        if (consulta.isBlank()) return null
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val frase = BusquedaInventarioIA.extraerFraseProducto(consulta)
        val moduloDetectado = modulo.trim().ifBlank { detectarModuloDesdePrompt(consulta) }
        val candidatos = candidatosCatalogoInteligentes(
            modulo = moduloDetectado.ifBlank { null },
            consulta = consulta,
            tokens = tokens,
            moduloHint = moduloDetectado,
        )
        return candidatos.asSequence()
            .map { producto ->
                producto to BusquedaInventarioIA.puntuarTexto(consulta, textoBusquedaProducto(producto), tokens, frase)
            }
            .filter { it.second >= 0.45 }
            .maxByOrNull { it.second }
            ?.first
    }

internal fun MainActivity.resolverSugerenciasProductoIA(
        modulo: String,
        item: String,
        referencia: String = "",
        limite: Int = 5,
    ): List<Pair<ProductoCatalogo, Double>> {
        val consulta = listOf(item, referencia).filter { it.isNotBlank() }.joinToString(" ")
        if (consulta.isBlank()) return emptyList()
        val moduloDetectado = modulo.trim().ifBlank { detectarModuloDesdePrompt("$item $referencia $consulta") }
        val moduloNorm = moduloCanonicoInventario(moduloDetectado, item, referencia).ifBlank { moduloDetectado }
        val locales = resolverProductoFuzzy(moduloNorm, item, referencia, limite)
        if (locales.isNotEmpty()) return locales
        return resolverProductoFuzzy("", item, referencia, limite)
    }

internal fun MainActivity.levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1].uppercaseChar() == s2[j - 1].uppercaseChar()) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

internal fun MainActivity.stringSimilarity(s1: String, s2: String): Double {
        val n1 = normalizarBusqueda(s1)
        val n2 = normalizarBusqueda(s2)
        val maxLen = maxOf(n1.length, n2.length)
        if (maxLen == 0) return 1.0
        return (maxLen - levenshteinDistance(n1, n2)).toDouble() / maxLen
    }

internal fun MainActivity.resolverProductoFuzzy(modulo: String, item: String, referencia: String = "", limite: Int = 3): List<Pair<ProductoCatalogo, Double>> {
        val itemBusqueda = item.trim()
        val refBusqueda = referencia.trim()
        if (itemBusqueda.isBlank() && refBusqueda.isBlank()) return emptyList()
        val consulta = listOf(itemBusqueda, refBusqueda).filter { it.isNotBlank() }.joinToString(" ")
        val frase = BusquedaInventarioIA.extraerFraseProducto(consulta)
        val tokens = BusquedaInventarioIA.tokens(consulta)
        val moduloDetectado = modulo.trim().ifBlank { detectarModuloDesdePrompt(consulta) }
        return candidatosCatalogoInteligentes(
            modulo = moduloDetectado.ifBlank { null },
            consulta = consulta,
            tokens = tokens,
            moduloHint = moduloDetectado,
        )
            .map { producto ->
                val base = textoBusquedaProducto(producto)
                val simItem = BusquedaInventarioIA.similitudTexto(itemBusqueda, producto.item)
                val simRef = if (refBusqueda.isNotBlank()) BusquedaInventarioIA.similitudTexto(refBusqueda, producto.referencia) else 0.0
                val ptosTexto = BusquedaInventarioIA.puntuarTexto(consulta, base, tokens, frase)
                
                // Ponderación más estricta: el ítem y la puntuación general deben ser altos
                val score = (simItem * 0.40) + (ptosTexto * 0.45) + (simRef * 0.15)
                
                producto to score
            }
            .filter { it.second >= 0.48 }
            .sortedByDescending { it.second }
            .take(limite)
    }


internal fun MainActivity.showDialogNuevoProducto(modulo: String, onDone: (String, String, String) -> Unit = { _, _, _ -> }) {
    val activity = this
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()

    root.addView(TextView(this).apply {
        text = "Nuevo Producto ($modulo)"
        textSize = 18f
        setTextColor(verdeOscuro)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(12))
    })

    fun itemsPorCategoria(categoria: String): List<String> {
        return (catalogoCargado[modulo]?.get(categoria) as? Map<*, *>)
            ?.keys
            ?.map { it.toString() }
            ?.sorted()
            ?: emptyList()
    }

    val categorias = catalogoCargado[modulo]?.keys?.toList()?.sorted() ?: listOf("Varios")
    val catSpinner = spinner(root, "Categoría *", categorias)
    val itemSpinner = spinner(root, "Ítem (Existente) *", listOf("Selecciona categoría"))
    val itemNuevo = autoField(root, "Ítem NUEVO (opcional)", "Escribe solo si no aparece en la lista")

    catSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            val cat = catSpinner.selectedItem?.toString().orEmpty()
            val items = itemsPorCategoria(cat).ifEmpty { listOf("Nuevo ítem") }
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemSpinner.adapter = adapter
        }
        override fun onNothingSelected(p0: AdapterView<*>?) {}
    }

    // Inicialización del ítem spinner
    val initialCat = catSpinner.selectedItem?.toString().orEmpty()
    val initialItems = itemsPorCategoria(initialCat).ifEmpty { listOf("Nuevo ítem") }
    itemSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, initialItems).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    val labelRef = if (modulo == "Dotación") "Talla *" else "Referencia / Marca"
    val ref = field(root, labelRef, if (modulo == "Dotación") "Ej: M, L, 42" else "Ej: Donaldson - P5500")
    val cod = field(root, "Código / QR *", "Escanea o escribe")
    val uni = field(root, "Unidad", "Ej: Unidad, Kg, Litro")
    
    val btnCrear = primaryButton("Crear en la Nube") {
        if (!required(cod)) return@primaryButton
        if (modulo == "Dotación" && !required(ref)) return@primaryButton

        val codigo = candidatosCodigoInterno(cod.text.toString()).firstOrNull() ?: normalizarCodigoInterno(cod.text.toString())
        val catVal = catSpinner.selectedItem?.toString().orEmpty()
        val itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
        val itemVal = itemNuevo.text.toString().trim().ifBlank { itemSeleccionado }

        if (itemVal.isBlank() || itemVal.startsWith("Selecciona") || itemVal == "Nuevo ítem") {
            Toast.makeText(this, "Selecciona o escribe un ítem válido", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }

        val refVal = if (moduloNormalizado(modulo) == "dotacion") {
            referenciaDotacionPresentable(ref.text.toString())
        } else {
            ref.text.toString().trim().ifBlank { "N/A" }
        }

        val data = mapOf(
            "modulo" to modulo,
            "categoria" to catVal,
            "item" to itemVal,
            "referencia" to refVal,
            "codigo_interno" to codigo,
            "unidad" to uni.text.toString().ifBlank { sugerirUnidadGeneral(modulo, itemVal) },
            "cantidad" to 0.0,
            "ultima_fecha" to now(),
            "ultimo_solicitante" to "Registro rápido"
        )
        firestore.collection("existencias").document(codigo).set(data).addOnSuccessListener {
            saved("Producto creado exitosamente")
            
            // Actualización local inmediata del catálogo
            val moduloMap = catalogoCargado.getOrPut(modulo) { mutableMapOf() }
            val categoriaMap = moduloMap.getOrPut(catVal) { mutableMapOf() }
            val listaRefs = categoriaMap.getOrPut(itemVal) { mutableListOf() }
            if (!listaRefs.contains(refVal)) listaRefs.add(refVal)

            sincronizarCatalogo()
            dialog.dismiss()
            onDone(catVal, itemVal, refVal)
        }
    }
    root.addView(btnCrear)
    dialog.setContentView(root)
    dialog.show()
}

internal fun MainActivity.showDialogNuevoCombustible(tipoPreseleccionado: String = "", onDone: () -> Unit = {}) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()

    root.addView(TextView(this).apply {
        text = "Nuevo combustible"
        textSize = 18f
        setTextColor(verdeOscuro)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(12))
    })

    val tipoSpinner = spinner(root, "Tipo *", tiposCombustible())
    if (tipoPreseleccionado.isNotBlank()) {
        val index = tiposCombustible().indexOfFirst { it.equals(tipoPreseleccionado, ignoreCase = true) }
        if (index >= 0) tipoSpinner.setSelection(index)
    }
    val marca = field(root, "Marca / proveedor", "Ej: TERPEL, Mobil")
    val cod = field(root, "Código interno *", "Ej: B-COMB-001")
    val uni = field(root, "Unidad", "Galones")
    uni.setText("Galones")

    root.addView(primaryButton("Crear en la nube") {
        if (!required(cod)) return@primaryButton
        val tipoVal = tipoSpinner.selectedItem?.toString().orEmpty()
        if (tipoVal.isBlank()) {
            Toast.makeText(this, "Selecciona el tipo de combustible", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }

        val codigo = candidatosCodigoInterno(cod.text.toString()).firstOrNull()
            ?: normalizarCodigoInterno(cod.text.toString())
        val marcaVal = marca.text.toString().trim()
        val data = mapOf(
            "modulo" to "Combustible",
            "categoria" to "Combustibles",
            "item" to itemBaseCombustible(),
            "referencia" to tipoVal,
            "marca" to marcaVal,
            "codigo_interno" to codigo,
            "unidad" to uni.text.toString().ifBlank { "Galones" },
            "cantidad" to 0.0,
            "ultima_fecha" to now(),
            "ultimo_solicitante" to "Registro rápido"
        )
        firestore.collection("existencias").document(codigo).set(data).addOnSuccessListener {
            saved("Combustible registrado: $tipoVal")
            val moduloMap = catalogoCargado.getOrPut("Combustible") { mutableMapOf() }
            val categoriaMap = moduloMap.getOrPut("Combustibles") { mutableMapOf() }
            val listaRefs = categoriaMap.getOrPut(itemBaseCombustible()) { mutableListOf() }
            if (!listaRefs.contains(tipoVal)) listaRefs.add(tipoVal)
            sincronizarCatalogo()
            dialog.dismiss()
            onDone()
        }.addOnFailureListener {
            Toast.makeText(this, "No se pudo crear el combustible", Toast.LENGTH_LONG).show()
        }
    })

    dialog.setContentView(root)
    dialog.show()
}

internal fun MainActivity.showDialogEntradaStock(modulo: String, item: String, ref: String, onDone: () -> Unit = {}) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()

    root.addView(TextView(this).apply {
        text = "Ingresar Stock"; textSize = 18f
        setTextColor(verdeOscuro); typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(4))
    })
    root.addView(TextView(this).apply {
        text = "$item - $ref"; textSize = 13f; setTextColor(gris); setPadding(0, 0, 0, dp(12))
    })

    val cant = field(root, "Cantidad a sumar *", "Ej: 50", number = true)
    val obs = field(root, "Observaciones", "Opcional")
    
    val btnSumar = primaryButton("Confirmar Ingreso") {
        if (!required(cant)) return@primaryButton
        val cantVal = cant.text.toString().toDoubleOrNull() ?: 0.0
        
        buscarExistenciaPorProducto(modulo, item, ref, { p ->
            if (p != null) {
                val ent = Entrada(
                    fecha = now(), modulo = modulo, item = item, referencia = ref,
                    codigoInterno = p.codigoInterno, cantidad = cantVal,
                    unidad = p.unidad, observaciones = obs.text.toString()
                )
                registrarEntradaFirestoreYStock(ent, p.codigoInterno, p.categoria, item, ref, {
                    saved("Stock actualizado: +$cantVal"); dialog.dismiss(); onDone()
                })
            } else {
                Toast.makeText(this, "Primero crea el producto usando el botón 'Nuevo'", Toast.LENGTH_LONG).show()
            }
        }, { dialog.dismiss() })
    }
    root.addView(btnSumar)
    dialog.setContentView(root)
    dialog.show()
}

internal fun MainActivity.catalogoComoTexto(): String {
        val b = StringBuilder()
        catalogoCargado.forEach { (mod, catMap) ->
            b.append("\n[$mod]\n")
            catMap.forEach { (cat, itemMap) -> b.append("  $cat: ${itemMap.keys.joinToString(", ")}\n") }
        }
        return b.toString()
    }
