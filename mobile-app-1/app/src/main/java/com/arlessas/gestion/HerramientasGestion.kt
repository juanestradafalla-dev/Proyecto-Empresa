@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.ColorDrawable
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
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

// Archivo modularizado con funciones de extensión de MainActivity.
// Mantiene el comportamiento original, pero separa responsabilidades para facilitar mantenimiento.

internal fun claveHerramientaCloud(herramienta: Herramienta): String {
        val base = herramienta.codigo.ifBlank {
            listOf(herramienta.nombre, herramienta.marca, herramienta.referencia).joinToString("-")
        }
        val normalizado = Normalizer.normalize(base.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalizado.ifBlank { "herramienta-${System.currentTimeMillis()}" }
    }

internal fun MainActivity.dataHerramientaCloud(h: Herramienta, idLocal: Long, usuario: String): Map<String, Any?> {
        return mapOf(
            "id_local" to idLocal,
            "clave" to claveHerramientaCloud(h),
            "fechaRegistro" to h.fechaRegistro,
            "nombre" to h.nombre,
            "referencia" to h.referencia,
            "marca" to h.marca,
            "codigo" to h.codigo,
            "codigo_principal" to h.codigo,
            "codigo_qr" to h.codigoQr,
            "requiere_asignar_qr" to h.requiereAsignarQr,
            "modulo" to TallerCanonicos.MODULO,
            "categoria" to h.subModulo,
            "submodulo_taller" to h.subModulo,
            "subcategoria" to h.subcategoria,
            "tipo" to h.tipo,
            "tamano" to h.tamano,
            "unidad" to h.unidad,
            "cantidad_total" to h.cantidadTotal,
            "cantidad_ocupada" to h.cantidadOcupada,
            "cantidad_disponible" to h.disponibles(),
            "estado" to h.estado,
            "ubicacion" to h.ubicacion,
            "responsable" to h.responsable,
            "observaciones" to h.observaciones,
            "registrado_por" to usuario,
            "usuario_uid" to (auth.currentUser?.uid ?: ""),
            "ultima_actualizacion" to now()
        )
    }

internal fun MainActivity.actualizarHerramientaCloudEstado(h: Herramienta, estado: String, responsable: String, ocupados: Double = h.cantidadOcupada) {
        firestore.collection("herramientas")
            .document(claveHerramientaCloud(h))
            .set(mapOf(
                "estado" to estado,
                "responsable" to responsable,
                "cantidad_ocupada" to ocupados,
                "cantidad_disponible" to (h.cantidadTotal - ocupados).coerceAtLeast(0.0),
                "cantidad_total" to h.cantidadTotal,
                "ultima_actualizacion" to now(),
                "actualizado_por_uid" to (auth.currentUser?.uid ?: "")
            ), SetOptions.merge())
    }

internal fun Herramienta.codigoPrincipalNormalizado(): String = TallerCanonicos.normalizarCodigo(codigo.ifBlank { codigoQr })

internal fun Herramienta.detalleTaller(): String {
    return listOf(tipo, tamano, marca)
        .filter { it.isNotBlank() && !it.equals("NO ESPECIFICADO", true) }
        .distinct()
        .joinToString(" / ")
}

internal fun formatoCantidadTaller(valor: Double): String {
    val entero = valor.toLong()
    return if (valor == entero.toDouble()) entero.toString() else "%.2f".format(Locale.getDefault(), valor)
}

internal fun coincideCodigoHerramienta(h: Herramienta, texto: String): Boolean {
    return TallerCanonicos.coincideCodigoQr(h.codigo, h.codigoQr, texto)
        || TallerCanonicos.normalizarCodigo(h.codigoPrincipalNormalizado())
            .equals(TallerCanonicos.normalizarCodigo(texto), ignoreCase = true)
}

internal fun MainActivity.herramientasTallerActivas(): List<Herramienta> {
    return db.obtenerHerramientas().filterNot { esHerramientaRetirada(it) }
}

internal fun MainActivity.buscarHerramientaTallerPorCodigo(texto: String): Herramienta? {
    val buscado = TallerCanonicos.normalizarEntradaQrEscaneada(texto.trim())
        .ifBlank { texto.trim() }
    if (buscado.isBlank()) return null
    return herramientasTallerActivas().firstOrNull { coincideCodigoHerramienta(it, buscado) }
        ?: herramientasTallerActivas().firstOrNull { coincideCodigoHerramienta(it, texto.trim()) }
}

internal fun Herramienta.categoriaTaller(): String = subcategoria.trim().ifBlank { "Sin categoría" }

internal fun Herramienta.estaEnPrestamo(): Boolean = ocupados() > 0.0

internal fun Herramienta.etiquetaBusquedaQr(): String {
    val numero = TallerCanonicos.codigoQrDesdeCodigoPrincipal(codigo, codigoQr)
    return when {
        numero.isNotBlank() -> "$numero · $nombre"
        codigo.isNotBlank() -> "$codigo · $nombre"
        else -> nombre
    }
}

internal fun Herramienta.etiquetaTrasladoBodega(): String {
    val detalle = listOf(tipo, tamano)
        .filter { it.isNotBlank() && !it.equals("NO ESPECIFICADO", true) }
        .joinToString(" ")
    val codigoTexto = codigo.ifBlank { "Sin código" }
    return "${categoriaTaller()} · $nombre" +
        (if (detalle.isNotBlank()) " · $detalle" else "") +
        " · ${formatoCantidadTaller(disponibles())} disp. ($codigoTexto)"
}

internal fun Herramienta.etiquetaEntradaPrestamo(): String {
    val detalle = listOf(tipo, tamano)
        .filter { it.isNotBlank() && !it.equals("NO ESPECIFICADO", true) }
        .joinToString(" ")
    val codigoTexto = codigo.ifBlank { "Sin código" }
    val asignacion = if (vehiculoAsignado.isNotBlank()) " · con $vehiculoAsignado" else ""
    return "${categoriaTaller()} · $nombre" +
        (if (detalle.isNotBlank()) " · $detalle" else "") +
        " · Devolver ${formatoCantidadTaller(ocupados())} ($codigoTexto)$asignacion"
}

internal fun Herramienta.textoDisponibilidad(): String {
    return "Codigo: ${codigo.ifBlank { "SIN CODIGO" }}\n" +
        "Disponibles: ${formatoCantidadTaller(disponibles())} ${unidad.ifBlank { "UNIDAD" }}\n" +
        "Ocupados: ${formatoCantidadTaller(ocupados())} ${unidad.ifBlank { "UNIDAD" }}"
}

internal fun esCodigoTallerRetirado(codigo: String): Boolean {
    val raw = codigo.trim().uppercase(Locale.getDefault())
    val normalizado = TallerCanonicos.normalizarCodigo(raw)
    return TallerCanonicos.CODIGOS_RETIRADOS.any {
        val retiradoRaw = it.trim().uppercase(Locale.getDefault())
        raw == retiradoRaw || normalizado == TallerCanonicos.normalizarCodigo(retiradoRaw)
    }
}

internal fun esHerramientaRetirada(h: Herramienta): Boolean {
    return esCodigoTallerRetirado(h.codigo) || esCodigoTallerRetirado(h.codigoQr)
}

internal fun herramientaDesdeCanonico(item: TallerItemCanonico, existente: Herramienta? = null): Herramienta {
    val ocupados = existente?.cantidadOcupada ?: 0.0
    return Herramienta(
        id = existente?.id ?: 0,
        fechaRegistro = existente?.fechaRegistro?.ifBlank { null } ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
        nombre = item.nombre,
        referencia = item.subcategoria,
        marca = item.marca,
        codigo = item.codigoPrincipal,
        estado = if (ocupados <= 0.0) "Disponible" else "En uso",
        ubicacion = item.subModulo,
        responsable = existente?.responsable ?: "",
        observaciones = existente?.observaciones ?: "",
        subModulo = item.subModulo,
        subcategoria = item.subcategoria,
        tipo = item.tipo,
        tamano = item.tamano,
        unidad = item.unidad,
        cantidadTotal = item.cantidad,
        cantidadOcupada = ocupados,
        codigoQr = item.codigoQr,
        requiereAsignarQr = item.requiereAsignarQr,
    )
}

internal fun MainActivity.sincronizarHerramientasTallerCanonicas() {
    val prefs = getSharedPreferences("gestion_config", Context.MODE_PRIVATE)
    val versionLocal = prefs.getString("taller_canonico_version", "")
    TallerCanonicos.CODIGOS_RETIRADOS.forEach { codigoRetirado ->
        db.eliminarHerramientaPorCodigo(codigoRetirado)
        db.eliminarHerramientaPorCodigo(TallerCanonicos.normalizarCodigo(codigoRetirado))
    }
    val existentes = db.obtenerHerramientas().associateBy { it.codigoPrincipalNormalizado() }
    TallerCanonicos.herramientasTaller.forEach { item ->
        val existente = existentes[TallerCanonicos.normalizarCodigo(item.codigoPrincipal)]
        db.insertarOActualizarHerramientaCanonica(herramientaDesdeCanonico(item, existente))
    }

    if (versionLocal == TallerCanonicos.VERSION || !isNetworkAvailable()) return
    TallerCanonicos.CODIGOS_RETIRADOS.forEach { codigoRetirado ->
        firestore.collection("herramientas")
            .document(TallerCanonicos.claveDocumento(codigoRetirado))
            .delete()
    }
    TallerCanonicos.herramientasTaller.forEach { item ->
        val existente = existentes[TallerCanonicos.normalizarCodigo(item.codigoPrincipal)]
        // NOTA: No incluimos cantidad_ocupada para evitar sobrescribir el estado real de la nube 
        // con el estado inicial (cero) de un celular reciÃ©n instalado.
        val data = TallerCanonicos.firestoreData(item, 0.0).toMutableMap()
        data.remove("cantidad_ocupada")
        data.remove("cantidad_disponible")
        data.putAll(mapOf(
            "fechaRegistro" to (existente?.fechaRegistro ?: now()),
            "ubicacion" to item.subModulo,
            "referencia" to item.subcategoria,
            "ultima_actualizacion" to now(),
            "catalogo_version" to TallerCanonicos.VERSION,
        ))
        firestore.collection("herramientas")
            .document(TallerCanonicos.claveDocumento(item.codigoPrincipal))
            .set(data, SetOptions.merge())
    }
    prefs.edit().putString("taller_canonico_version", TallerCanonicos.VERSION).apply()
}

internal fun MainActivity.herramientasTallerFiltradas(subModulo: String): List<Herramienta> {
    val todas = db.obtenerHerramientas()
    val activas = todas.filterNot { esHerramientaRetirada(it) }
    return if (subModulo.isBlank()) activas else activas.filter { TallerCanonicos.coincideSubmoduloTaller(it.subModulo, subModulo) }
}

internal fun movimientoPerteneceSubmoduloTaller(m: Movimiento, subModulo: String): Boolean {
    if (subModulo.isBlank()) return true
    val subNorm = TallerCanonicos.normalizarSubmoduloTaller(subModulo)
    if (TallerCanonicos.esBodegaRojaTaller(subNorm)) {
        return TallerCanonicos.coincideSubmoduloTaller(m.maquinaria, subNorm) ||
            m.labor.contains("Origen: $subModulo", ignoreCase = true) ||
            m.labor.contains("Origen: $subNorm", ignoreCase = true) ||
            (
                m.tipoMovimiento.equals(TallerCanonicos.TIPO_MOV_INGRESO_BODEGA, ignoreCase = true) &&
                    (
                        m.labor.contains("Producto nuevo", ignoreCase = true) ||
                            m.labor.contains("Destino: ${TallerCanonicos.SUBMODULO_BODEGA_ROJA}", ignoreCase = true) ||
                            m.labor.contains("Destino: $subNorm", ignoreCase = true)
                        )
                )
    }
    return TallerCanonicos.coincideSubmoduloTaller(m.maquinaria, subNorm) ||
        m.labor.contains("Destino: $subModulo", ignoreCase = true) ||
        m.labor.contains("Destino: $subNorm", ignoreCase = true)
}

internal fun movimientoDesdeDocTaller(doc: com.google.firebase.firestore.DocumentSnapshot): Movimiento {
    val tipo = docTexto(doc, "tipoMovimiento", "tipo", "movimiento")
    val submoduloOrigen = docTexto(doc, "submodulo_origen")
    val submodulo = TallerCanonicos.resolverSubmoduloDesdeCampos(
        submoduloTaller = docTexto(doc, "submodulo_taller"),
        categoria = docTexto(doc, "categoria"),
        ubicacion = docTexto(doc, "ubicacion"),
    )
    val laborBase = docTexto(doc, "labor", "tipo_labor", "zona_ejecucion")
    val labor = if (tipo.equals(TallerCanonicos.TIPO_MOV_TRASLADO, ignoreCase = true) && submoduloOrigen.isNotBlank()) {
        "Origen: $submoduloOrigen | $laborBase"
    } else {
        laborBase
    }
    return Movimiento(
        id = 0,
        fecha = docTexto(doc, "fecha"),
        modulo = docTexto(doc, "modulo"),
        tipoMovimiento = tipo,
        item = docTexto(doc, "item", "producto", "herramientaNombre"),
        referencia = docTexto(doc, "codigo_principal", "codigo", "referencia"),
        marca = docTexto(doc, "marca"),
        cantidad = docTexto(doc, "cantidad").ifBlank { "1" },
        unidad = docTexto(doc, "unidad"),
        solicitante = docTexto(doc, "solicitante"),
        labor = labor,
        maquinaria = submodulo,
        horometro = "",
        herramientaId = docTexto(doc, "herramientaId"),
        estado = docTexto(doc, "responsable_entrega"),
        observaciones = listOf(
            docTexto(doc, "observaciones"),
            docTexto(doc, "fotoUrl").takeIf { it.isNotBlank() }?.let { "__FOTO_URL__$it" }.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n"),
    )
}

internal fun ordenarMovimientosTallerPorFecha(movimientos: List<Movimiento>): List<Movimiento> =
    movimientos.sortedByDescending { it.fecha }

internal fun MainActivity.buscarHerramientaDestinoTraslado(
    origen: Herramienta,
    destinoSubmodulo: String,
): Herramienta? {
    val candidatos = herramientasTallerFiltradas(destinoSubmodulo)
    return candidatos.firstOrNull { it.codigo.isNotBlank() && it.codigo.equals(origen.codigo, ignoreCase = true) }
        ?: candidatos.firstOrNull {
            it.nombre.equals(origen.nombre, ignoreCase = true) &&
                it.tipo.equals(origen.tipo, ignoreCase = true) &&
                it.tamano.equals(origen.tamano, ignoreCase = true)
        }
}

internal data class ResultadoTrasladoInventario(
    val origen: Herramienta?,
    val destino: Herramienta,
    val movimientoCompleto: Boolean,
)

internal fun MainActivity.aplicarTrasladoInventario(
    origen: Herramienta,
    destinoSubmodulo: String,
    categoriaDestino: String,
    cant: Double,
): ResultadoTrasladoInventario {
    val cantidadTraslado = cant.coerceIn(0.0, origen.disponibles())
    require(cantidadTraslado > 0.0) { "Cantidad inválida para traslado" }
    require(origen.ocupados() <= 0.0) { "No se puede trasladar herramienta en préstamo" }

    val categoria = categoriaDestino.trim().ifBlank { origen.subcategoria }
    val destinoExistente = buscarHerramientaDestinoTraslado(origen, destinoSubmodulo)
    val trasladoTotal = cantidadTraslado >= origen.disponibles()

    if (trasladoTotal && destinoExistente == null) {
        return ResultadoTrasladoInventario(
            origen = null,
            destino = origen.copy(
                subModulo = destinoSubmodulo,
                subcategoria = categoria,
                ubicacion = destinoSubmodulo,
                estado = "Disponible",
                responsable = "",
            ),
            movimientoCompleto = true,
        )
    }

    if (destinoExistente != null) {
        return ResultadoTrasladoInventario(
            origen = origen.copy(cantidadTotal = origen.cantidadTotal - cantidadTraslado),
            destino = destinoExistente.copy(
                cantidadTotal = destinoExistente.cantidadTotal + cantidadTraslado,
                subcategoria = categoria.ifBlank { destinoExistente.subcategoria },
            ),
            movimientoCompleto = false,
        )
    }

    val sufijo = (System.currentTimeMillis() % 100000).toString()
    val nuevoCodigo = when {
        origen.codigo.isBlank() -> "TR-$sufijo"
        else -> "${origen.codigo}-TR$sufijo"
    }
    return ResultadoTrasladoInventario(
        origen = origen.copy(cantidadTotal = origen.cantidadTotal - cantidadTraslado),
        destino = origen.copy(
            id = 0,
            codigo = nuevoCodigo,
            codigoQr = "",
            requiereAsignarQr = true,
            subModulo = destinoSubmodulo,
            subcategoria = categoria,
            ubicacion = destinoSubmodulo,
            cantidadTotal = cantidadTraslado,
            cantidadOcupada = 0.0,
            estado = "Disponible",
            responsable = "",
        ),
        movimientoCompleto = false,
    )
}

internal fun MainActivity.persistirTrasladoInventario(resultado: ResultadoTrasladoInventario) {
    if (resultado.movimientoCompleto) {
        db.actualizarHerramientaCanonica(resultado.destino)
        db.actualizarOcupacionHerramienta(resultado.destino.id, 0.0, "")
        sincronizarHerramientaCloudCompleta(resultado.destino)
        return
    }

    resultado.origen?.let { origen ->
        if (origen.cantidadTotal > 0.0) {
            db.actualizarHerramientaCanonica(origen)
            sincronizarHerramientaCloudCompleta(origen)
        } else {
            db.eliminarHerramienta(origen.id)
        }
    }

    if (resultado.destino.id > 0L) {
        db.actualizarHerramientaCanonica(resultado.destino)
        sincronizarHerramientaCloudCompleta(resultado.destino)
    } else {
        val nuevoId = db.insertarHerramienta(resultado.destino)
        sincronizarHerramientaCloudCompleta(resultado.destino.copy(id = nuevoId))
    }
}

internal fun MainActivity.sincronizarHerramientaCloudCompleta(herramienta: Herramienta) {
    val uid = auth.currentUser?.uid ?: ""
    obtenerInfoUsuario(uid) { usuario ->
        val data = dataHerramientaCloud(herramienta, herramienta.id, usuario)
        if (isNetworkAvailable()) {
            firestore.collection("herramientas")
                .document(claveHerramientaCloud(herramienta))
                .set(data, SetOptions.merge())
        } else {
            guardarFirestoreOffline("herramientas", data)
        }
    }
}

internal fun MainActivity.herramientasEnPrestamoTaller(subModulo: String): List<Herramienta> {
    return herramientasTallerFiltradas(subModulo)
        .filter { it.estaEnPrestamo() }
        .sortedWith(
            compareBy<Herramienta> { it.categoriaTaller() }
                .thenBy { it.nombre }
                .thenBy { it.tamano },
        )
}

internal data class PrestamoTallerActivo(
    val herramienta: Herramienta,
    val movimientoSalida: Movimiento?,
)

internal fun Movimiento.esSalidaPrestamoTaller(): Boolean {
    return TallerCanonicos.esModuloTaller(modulo) &&
        tipoMovimiento.contains("salida", ignoreCase = true)
}

internal fun Movimiento.coincideConHerramientaPrestada(herramienta: Herramienta): Boolean {
    val idCoincide = herramientaId.isNotBlank() && herramientaId == herramienta.id.toString()
    val codigoCoincide = referencia.isNotBlank() && coincideCodigoHerramienta(herramienta, referencia)
    val nombreCoincide = item.equals(herramienta.nombre, ignoreCase = true)
    val submoduloCoincide = maquinaria.isBlank() ||
        TallerCanonicos.coincideSubmoduloTaller(maquinaria, herramienta.subModulo)
    return idCoincide || codigoCoincide || (nombreCoincide && submoduloCoincide)
}

internal fun MainActivity.movimientosLocalesTaller(): List<Movimiento> {
    return ordenarMovimientosTallerPorFecha(
        db.obtenerMovimientos().filter { TallerCanonicos.esModuloTaller(it.modulo) },
    )
}

internal fun MainActivity.cargarMovimientosPrestamosTaller(onReady: (List<Movimiento>) -> Unit) {
    fun usarLocales() {
        onReady(movimientosLocalesTaller())
    }

    if (!isNetworkAvailable()) {
        usarLocales()
        return
    }

    firestore.collection("movimientos")
        .whereIn("modulo", listOf(TallerCanonicos.MODULO, TallerCanonicos.MODULO_LEGACY))
        .limit(performanceConfig.movementQueryLimit())
        .get()
        .addOnSuccessListener { snapshot ->
            val movimientosNube = ordenarMovimientosTallerPorFecha(
                snapshot.documents
                    .map(::movimientoDesdeDocTaller)
                    .filter { TallerCanonicos.esModuloTaller(it.modulo) },
            )
            if (movimientosNube.isEmpty()) usarLocales() else onReady(movimientosNube)
        }
        .addOnFailureListener {
            usarLocales()
        }
}

internal fun MainActivity.prestamosActivosTaller(movimientos: List<Movimiento>): List<PrestamoTallerActivo> {
    val salidas = movimientos.filter { it.esSalidaPrestamoTaller() }
    return herramientasTallerActivas()
        .filter { it.estaEnPrestamo() || it.estado.equals("En uso", ignoreCase = true) }
        .map { herramienta ->
            PrestamoTallerActivo(
                herramienta = herramienta,
                movimientoSalida = salidas.firstOrNull { it.coincideConHerramientaPrestada(herramienta) },
            )
        }
        .sortedWith(
            compareBy<PrestamoTallerActivo> { it.herramienta.subModulo }
                .thenBy { it.herramienta.categoriaTaller() }
                .thenBy { it.herramienta.nombre },
        )
}

internal fun tipoHerramientaPrestamoTaller(herramienta: Herramienta): String {
    return herramienta.detalleTaller()
        .ifBlank { herramienta.tipo }
        .ifBlank { herramienta.subcategoria }
        .ifBlank { herramienta.nombre }
}

internal fun ubicacionPrestamoTaller(herramienta: Herramienta, movimiento: Movimiento?): String {
    val labor = movimiento?.labor.orEmpty()
    Regex("""Zona:\s*([^|]+)""", RegexOption.IGNORE_CASE)
        .find(labor)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    Regex("""Ubicacion:\s*([^|]+)""", RegexOption.IGNORE_CASE)
        .find(labor)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return movimiento?.maquinaria?.takeIf { it.isNotBlank() }
        ?: herramienta.ubicacion.ifBlank { herramienta.subModulo.ifBlank { "Taller" } }
}

internal fun MainActivity.abrirDialogoPrestamosActivosTaller() {
    Toast.makeText(this, "Actualizando prestamos...", Toast.LENGTH_SHORT).show()
    prepararInventarioTaller {
        if (!pantallaActiva()) return@prepararInventarioTaller
        cargarMovimientosPrestamosTaller { movimientos ->
            if (!pantallaActiva()) return@cargarMovimientosPrestamosTaller
            mostrarDialogoPrestamosActivosTaller(movimientos)
        }
    }
}

internal fun MainActivity.prestamoDialogInfoRow(label: String, value: String, accent: Int): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(3), 0, dp(3))
        addView(TextView(context).apply {
            text = label
            textSize = 10f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        addView(TextView(context).apply {
            text = value.ifBlank { "No especificado" }
            textSize = 12f
            setTextColor(ArlesPalette.ink)
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }
}

internal fun MainActivity.prestamoActivoCardTaller(prestamo: PrestamoTallerActivo): LinearLayout {
    val herramienta = prestamo.herramienta
    val movimiento = prestamo.movimientoSalida
    val accent = tallerSubmoduloAccent(herramienta.subModulo)
    val prestadoA = movimiento?.solicitante
        ?.ifBlank { herramienta.responsable }
        ?: herramienta.responsable
    val ubicacion = ubicacionPrestamoTaller(herramienta, movimiento)
    val cantidadTexto = formatoCantidadTaller(herramienta.ocupados())

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text = herramienta.nombre.ifBlank { "Herramienta" }
            textSize = 13.5f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(statusBadge(cantidadTexto, ArlesPalette.warning))
        addView(top)

        addView(TextView(context).apply {
            text = herramienta.subModulo.ifBlank { "Taller" }
            textSize = 10f
            setTextColor(accent)
            setPadding(0, dp(3), 0, dp(8))
        })

        addView(prestamoDialogInfoRow("Tipo", tipoHerramientaPrestamoTaller(herramienta), accent))
        addView(prestamoDialogInfoRow("Prestado a", prestadoA, accent))
        addView(prestamoDialogInfoRow("Ubicacion", ubicacion, accent))
    }
}

internal fun MainActivity.mostrarDialogoPrestamosActivosTaller(movimientos: List<Movimiento>) {
    val prestamos = prestamosActivosTaller(movimientos)
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(ArlesPalette.soft, null, 18)
        setPadding(dp(18), dp(18), dp(18), dp(14))
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(ImageView(this).apply {
        setImageResource(R.drawable.ic_tools)
        setColorFilter(Color.WHITE)
        background = arlesRoundedBackground(ArlesPalette.warning, null, 14)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            setMargins(0, 0, dp(12), 0)
        }
    })
    header.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = "Prestamos activos"
            textSize = 18f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        addView(TextView(context).apply {
            text = "${prestamos.size} herramienta(s) en prestamo"
            textSize = 11.5f
            setTextColor(ArlesPalette.muted)
            setPadding(0, dp(2), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    content.addView(header)

    if (prestamos.isEmpty()) {
        content.addView(
            TextView(this).apply {
                text = "No hay herramientas prestadas en este momento."
                textSize = 13f
                setTextColor(ArlesPalette.muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(28), dp(12), dp(22))
            },
        )
    } else {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(2))
        }
        prestamos.forEach { prestamo ->
            list.addView(prestamoActivoCardTaller(prestamo))
        }
        content.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (prestamos.size > 3) dp(420) else LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        })
    }

    val dialog = AlertDialog.Builder(this)
        .setView(content)
        .setPositiveButton("Cerrar", null)
        .create()
    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ArlesPalette.accentTaller)
    }
    dialog.show()
}

internal fun docTexto(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): String {
    keys.forEach { key ->
        val value = doc.get(key)
        when (value) {
            is String -> if (value.isNotBlank()) return value
            is Number -> return value.toString()
            is Boolean -> return value.toString()
        }
    }
    return ""
}

internal fun numeroDocumento(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): Double {
    keys.forEach { key ->
        when (val value = doc.get(key)) {
            is Number -> return value.toDouble()
            is String -> value.toDoubleOrNull()?.let { return it }
        }
    }
    return 0.0
}

internal fun enteroDocumento(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): Int? {
    keys.forEach { key ->
        when (val value = doc.get(key)) {
            is Number -> return value.toInt()
            is String -> value.toIntOrNull()?.let { return it }
        }
    }
    return null
}

internal fun herramientaDesdeDocumentoFirestore(doc: com.google.firebase.firestore.DocumentSnapshot): Herramienta? {
    val nombre = docTexto(doc, "nombre", "item", "producto", "descripcion")
    if (nombre.isBlank()) return null
    val codigo = docTexto(doc, "codigo_principal", "codigo", "codigo_qr", "codigo_interno", "clave").ifBlank { doc.id }
    val cantidadTotal = numeroDocumento(doc, "cantidad_total", "stock_total", "cantidad").let { if (it > 0.0) it else 1.0 }
    val subModulo = TallerCanonicos.resolverSubmoduloDesdeCampos(
        submoduloTaller = docTexto(doc, "submodulo_taller", "submodulo", "seccion"),
        categoria = docTexto(doc, "categoria"),
        ubicacion = docTexto(doc, "ubicacion"),
        seccion = docTexto(doc, "seccion", "area", "zona"),
    )
    return Herramienta(
        fechaRegistro = docTexto(doc, "fechaRegistro", "fecha_registro"),
        nombre = nombre,
        referencia = docTexto(doc, "referencia", "subcategoria"),
        marca = docTexto(doc, "marca"),
        codigo = codigo,
        estado = docTexto(doc, "estado").ifBlank { "Disponible" },
        ubicacion = docTexto(doc, "ubicacion"),
        responsable = docTexto(doc, "responsable"),
        observaciones = docTexto(doc, "observaciones"),
        subModulo = subModulo,
        subcategoria = docTexto(doc, "subcategoria", "referencia").ifBlank {
            val categoria = docTexto(doc, "categoria")
            if (TallerCanonicos.esSubmoduloTaller(categoria)) "" else categoria
        },
        tipo = docTexto(doc, "tipo", "tipo_herramienta"),
        tamano = docTexto(doc, "tamano"),
        unidad = docTexto(doc, "unidad").ifBlank { "UNIDAD" },
        cantidadTotal = cantidadTotal,
        cantidadOcupada = numeroDocumento(doc, "cantidad_ocupada", "ocupados"),
        codigoQr = TallerCanonicos.codigoQrDesdeCodigoPrincipal(
            codigo,
            docTexto(doc, "codigo_qr"),
        ),
        requiereAsignarQr = doc.getBoolean("requiere_asignar_qr")
            ?: (codigo.startsWith("SINQR", true) && docTexto(doc, "codigo_qr").isBlank()),
        vehiculoAsignado = docTexto(doc, "vehiculo_asignado", "vehiculoAsignado"),
    )
}

internal fun MainActivity.prepararInventarioTaller(onReady: () -> Unit) {
    // Primero descargamos el estado actual de la nube (disponibilidad/ocupados)
    sincronizarHerramientasDesdeNube {
        // Luego sincronizamos la estructura canÃ³nica localmente
        sincronizarHerramientasTallerCanonicas()
        onReady()
    }
}

internal fun MainActivity.registrarTrasladoBodegaRoja(
        herramienta: Herramienta,
        destinoSubmodulo: String,
        categoriaDestino: String,
        solicitante: String,
        motivo: String,
        observaciones: String,
        cantidad: String,
        fotoUrl: String = "",
    ) {
        val uid = auth.currentUser?.uid ?: ""
        obtenerInfoUsuario(uid) { responsable ->
            val fecha = now()
            val cantVal = cantidad.replace(",", ".").toDoubleOrNull() ?: 1.0
            val origen = herramienta
            val destino = destinoSubmodulo

            try {
                val resultado = aplicarTrasladoInventario(origen, destino, categoriaDestino, cantVal)
                persistirTrasladoInventario(resultado)

                val laborDetalle = listOf(
                    "Origen: ${origen.subModulo}",
                    "Destino: $destino",
                    "Categoría: ${categoriaDestino.trim().ifBlank { origen.subcategoria }}",
                    motivo.takeIf { it.isNotBlank() }?.let { "Motivo: $it" },
                ).filterNotNull().joinToString(" | ")

                val mov = Movimiento(
                    id = 0,
                    fecha = fecha,
                    modulo = TallerCanonicos.MODULO,
                    tipoMovimiento = TallerCanonicos.TIPO_MOV_TRASLADO,
                    item = origen.nombre,
                    referencia = origen.codigo,
                    marca = origen.marca,
                    cantidad = cantVal.toString(),
                    unidad = origen.unidad.ifBlank { "UNIDAD" },
                    solicitante = solicitante,
                    labor = laborDetalle,
                    maquinaria = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                    horometro = "",
                    herramientaId = origen.id.toString(),
                    estado = "Disponible",
                    observaciones = observaciones,
                )
                val idMov = db.insertarMovimiento(mov)

                val dataMov = mapOf(
                    "id_local" to idMov,
                    "fecha" to mov.fecha,
                    "modulo" to mov.modulo,
                    "tipoMovimiento" to mov.tipoMovimiento,
                    "item" to mov.item,
                    "referencia" to mov.referencia,
                    "marca" to mov.marca,
                    "codigo" to origen.codigo,
                    "codigo_principal" to origen.codigo,
                    "codigo_qr" to origen.codigoQr,
                    "categoria" to destino,
                    "submodulo_taller" to destino,
                    "submodulo_origen" to origen.subModulo,
                    "subcategoria" to categoriaDestino,
                    "tipo_herramienta" to origen.tipo,
                    "tamano" to origen.tamano,
                    "cantidad" to mov.cantidad,
                    "unidad" to mov.unidad,
                    "solicitante" to mov.solicitante,
                    "labor" to mov.labor,
                    "tipo_labor" to motivo,
                    "zona_ejecucion" to destino,
                    "responsable_entrega" to responsable,
                    "usuario_uid" to uid,
                    "herramientaId" to mov.herramientaId,
                    "herramienta_clave" to claveHerramientaCloud(origen),
                    "estado" to mov.estado,
                    "fotoUrl" to fotoUrl,
                    "observaciones" to mov.observaciones,
                )

                fun finalizar() {
                    saved("Traslado registrado hacia $destino")
                    showTallerSubmoduloMenu(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
                }
                fun encolarPendiente() {
                    guardarFirestoreOffline("movimientos", dataMov)
                    finalizar()
                }

                if (isNetworkAvailable()) {
                    firestore.collection("movimientos")
                        .add(dataMov)
                        .addOnSuccessListener { finalizar() }
                        .addOnFailureListener { encolarPendiente() }
                } else {
                    encolarPendiente()
                }
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, e.message ?: "No se pudo trasladar", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("ArlesGestion", "Error en traslado Bodega Roja", e)
                Toast.makeText(
                    this,
                    "Error al trasladar: ${e.localizedMessage ?: "error inesperado"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

internal fun MainActivity.registrarMovimientoIngresoBodegaNuevo(
    herramienta: Herramienta,
    registradoPor: String,
    motivo: String,
    observaciones: String,
    responsableEntrega: String,
    uid: String,
): Map<String, Any?> {
    val fecha = now()
    val laborDetalle = listOf(
        "Producto nuevo",
        "Categoría: ${herramienta.categoriaTaller()}",
        motivo.takeIf { it.isNotBlank() }?.let { "Motivo: $it" },
    ).filterNotNull().joinToString(" | ")
    val mov = Movimiento(
        id = 0,
        fecha = fecha,
        modulo = TallerCanonicos.MODULO,
        tipoMovimiento = TallerCanonicos.TIPO_MOV_INGRESO_BODEGA,
        item = herramienta.nombre,
        referencia = herramienta.codigo,
        marca = herramienta.marca,
        cantidad = formatoCantidadTaller(herramienta.cantidadTotal),
        unidad = herramienta.unidad.ifBlank { "UNIDAD" },
        solicitante = registradoPor,
        labor = laborDetalle,
        maquinaria = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        horometro = "",
        herramientaId = herramienta.id.toString(),
        estado = "Disponible",
        observaciones = observaciones,
    )
    val idMov = db.insertarMovimiento(mov)
    return mapOf(
        "id_local" to idMov,
        "fecha" to mov.fecha,
        "modulo" to mov.modulo,
        "tipoMovimiento" to mov.tipoMovimiento,
        "item" to mov.item,
        "referencia" to mov.referencia,
        "marca" to mov.marca,
        "codigo" to herramienta.codigo,
        "codigo_principal" to herramienta.codigo,
        "codigo_qr" to herramienta.codigoQr,
        "categoria" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        "submodulo_taller" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        "subcategoria" to herramienta.subcategoria,
        "tipo_herramienta" to herramienta.tipo,
        "tamano" to herramienta.tamano,
        "cantidad" to mov.cantidad,
        "unidad" to mov.unidad,
        "solicitante" to mov.solicitante,
        "labor" to mov.labor,
        "tipo_labor" to motivo,
        "zona_ejecucion" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        "responsable_entrega" to responsableEntrega,
        "usuario_uid" to uid,
        "herramientaId" to mov.herramientaId,
        "herramienta_clave" to claveHerramientaCloud(herramienta),
        "estado" to mov.estado,
        "observaciones" to mov.observaciones,
    )
}

internal fun MainActivity.showIngresoProductoBodegaRojaForm(
    prefCodigo: String = "",
    prefCodigoQr: String = "",
) {
    showRegistroHerramientaForm(
        prefSubModulo = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        prefUbicacion = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
        prefCodigo = prefCodigo,
        prefCodigoQr = prefCodigoQr,
        esIngresoBodegaNuevo = true,
    )
}

internal fun MainActivity.registrarMovimientoHerramienta(
        tipo: String,
        herramienta: Herramienta,
        solicitante: String,
        labor: String,
        observaciones: String,
        cantidad: String,
        fotoUrl: String = "",
        zona: String = "",
        estado: String = "",  // para entradas: estado elegido por el usuario al devolver
        vehiculoAsignado: String = "",  // para implementos: nombre del vehículo al que se asigna en la salida
        auto: Boolean = false,  // si es una acción automática (ej. implemento ligado a vehículo), suprime toasts y navegación
    ) {
        val uid = auth.currentUser?.uid ?: ""
        obtenerInfoUsuario(uid) { responsable ->
            val fecha = now()
            val cantVal = cantidad.toDoubleOrNull() ?: 1.0
            val esSalida = tipo.equals("Salida", ignoreCase = true)
            val nuevaOcupacion = if (esSalida) {
                (herramienta.ocupados() + cantVal).coerceAtMost(herramienta.cantidadTotal)
            } else {
                (herramienta.ocupados() - cantVal).coerceAtLeast(0.0)
            }
            val nuevoEstado = if (!esSalida && estado.isNotBlank()) {
                estado
            } else if (nuevaOcupacion <= 0.0) "Disponible" else "En uso"
            val laborCompleta = listOf(
                labor.takeIf { it.isNotBlank() }?.let { "Tipo labor: $it" },
                zona.takeIf { it.isNotBlank() }?.let { "Zona: $it" },
            ).filterNotNull().joinToString(" | ")

            // 1. Guardar en Movimientos Generales (SQLite + Firestore)
            val mov = Movimiento(
                id = 0,
                fecha = fecha,
                modulo = TallerCanonicos.MODULO,
                tipoMovimiento = tipo,
                item = herramienta.nombre,
                referencia = herramienta.codigo,
                marca = herramienta.marca,
                cantidad = cantidad, // Movimiento usa String para cantidad
                unidad = herramienta.unidad.ifBlank { "UNIDAD" },
                solicitante = solicitante,
                labor = laborCompleta.ifBlank { labor },
                maquinaria = herramienta.subModulo,
                horometro = "",
                herramientaId = herramienta.id.toString(),
                estado = nuevoEstado,
                observaciones = observaciones
            )
            val idMov = db.insertarMovimiento(mov)
            
            val dataMov = mapOf(
                "id_local" to idMov,
                "fecha" to mov.fecha,
                "modulo" to mov.modulo,
                "tipoMovimiento" to mov.tipoMovimiento,
                "item" to mov.item,
                "referencia" to mov.referencia,
                "marca" to mov.marca,
                "codigo" to herramienta.codigo,
                "codigo_principal" to herramienta.codigo,
                "codigo_qr" to herramienta.codigoQr,
                "requiere_asignar_qr" to herramienta.requiereAsignarQr,
                "categoria" to herramienta.subModulo,
                "submodulo_taller" to herramienta.subModulo,
                "subcategoria" to herramienta.subcategoria,
                "tipo_herramienta" to herramienta.tipo,
                "tamano" to herramienta.tamano,
                "cantidad" to mov.cantidad,
                "unidad" to mov.unidad,
                "solicitante" to mov.solicitante,
                "labor" to mov.labor,
                "tipo_labor" to labor,
                "zona_ejecucion" to zona,
                "responsable_entrega" to responsable, // Campo solicitado
                "usuario_uid" to uid,
                "herramientaId" to mov.herramientaId,
                "herramienta_clave" to claveHerramientaCloud(herramienta),
                "estado" to mov.estado,
                "fecha_salida" to if (esSalida) fecha else "",
                "fecha_entrada" to if (esSalida) "" else fecha,
                "cantidad_total" to herramienta.cantidadTotal,
                "cantidad_ocupada" to nuevaOcupacion,
                "cantidad_disponible" to (herramienta.cantidadTotal - nuevaOcupacion).coerceAtLeast(0.0),
                "fotoUrl" to fotoUrl,
                "observaciones" to mov.observaciones,
                "vehiculo_asignado" to if (vehiculoAsignado.isNotBlank()) vehiculoAsignado else null
            )

            // 2. Actualizar estado de la herramienta
            db.actualizarOcupacionHerramienta(herramienta.id, nuevaOcupacion, responsable)

            // 3. Si es un implemento asignado a un vehículo, guardar la asociación
            if (vehiculoAsignado.isNotBlank()) {
                db.actualizarAsignacionVehiculo(herramienta.id, vehiculoAsignado)
            } else if (!esSalida && vehiculoAsignado.isBlank()) {
                // En entrada, limpiar la asignación si se pasa explícitamente vacío
                db.actualizarAsignacionVehiculo(herramienta.id, "")
            }

            // Sincronizar el campo de asignación a la nube para que otros usuarios lo vean
            if (isNetworkAvailable()) {
                try {
                    firestore.collection("herramientas")
                        .document(claveHerramientaCloud(herramienta))
                        .update(
                            mapOf(
                                "vehiculo_asignado" to vehiculoAsignado,
                                "ultima_actualizacion" to now()
                            )
                        )
                } catch (_: Exception) {
                    // Se sincronizará en el próximo refresh completo
                }
            }

            fun finalizar() {
                if (!auto) {
                    saved("Movimiento registrado: $tipo")
                    val destino = herramienta.subModulo.ifBlank { TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER }
                    showTallerSubmoduloMenu(destino)
                }
            }
            fun encolarPendiente() {
                guardarFirestoreOffline("movimientos", dataMov)
                // TAMBIÃ‰N encolamos la actualizaciÃ³n del estado de la herramienta
                val dataEstado = mapOf(
                    "clave" to claveHerramientaCloud(herramienta),
                    "estado" to mov.estado,
                    "responsable" to responsable,
                    "cantidad_ocupada" to nuevaOcupacion,
                    "cantidad_disponible" to (herramienta.cantidadTotal - nuevaOcupacion).coerceAtLeast(0.0),
                    "ultima_actualizacion" to now(),
                    "vehiculo_asignado" to vehiculoAsignado
                )
                guardarFirestoreOffline("herramientas", dataEstado)
                finalizar()
            }

            if (isNetworkAvailable()) {
                firestore.collection("movimientos")
                    .add(dataMov)
                    .addOnSuccessListener {
                        actualizarHerramientaCloudEstado(herramienta, mov.estado, responsable, nuevaOcupacion)
                        finalizar()
                    }
                    .addOnFailureListener {
                        encolarPendiente()
                    }
            } else {
                encolarPendiente()
            }
        }
    }

internal fun inferirTipoMovimientoTaller(herramienta: Herramienta): String {
    return when {
        herramienta.ocupados() > 0.0 -> "Entrada"
        herramienta.disponibles() > 0.0 -> "Salida"
        else -> "Entrada"
    }
}

/**
 * Diálogo para seleccionar múltiples implementos agrícolas disponibles
 * para asignar a un vehículo al momento de la salida.
 * Al seleccionar, se registrarán salidas automáticas con los mismos datos del vehículo.
 */
internal fun MainActivity.mostrarDialogoSeleccionImplementos(onSeleccionados: (List<Herramienta>) -> Unit) {
    val implementsDisponibles = herramientasTallerFiltradas("IMPLEMENTOS AGRICOLAS")
        .filter { it.disponibles() > 0.0 }
        .sortedBy { it.nombre }

    if (implementsDisponibles.isEmpty()) {
        Toast.makeText(this, "No hay implementos agrícolas disponibles para asignar", Toast.LENGTH_SHORT).show()
        return
    }

    val nombres = implementsDisponibles.map { impl ->
        val detalle = listOf(impl.tipo, impl.tamano).filter { it.isNotBlank() }.joinToString(" ")
        "${impl.nombre}${if (detalle.isNotBlank()) " · $detalle" else ""} (${impl.codigo.ifBlank { impl.subcategoria }})"
    }.toTypedArray()

    val checked = BooleanArray(nombres.size) { false }

    AlertDialog.Builder(this)
        .setTitle("Implementos a llevar con el vehículo")
        .setMultiChoiceItems(nombres, checked) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setPositiveButton("Asignar seleccionados") { _, _ ->
            val seleccionados = implementsDisponibles.filterIndexed { index, _ -> checked[index] }
            onSeleccionados(seleccionados)
        }
        .setNegativeButton("Cancelar", null)
        .setNeutralButton("Todos") { _, _ ->
            onSeleccionados(implementsDisponibles)
        }
        .show()
}

internal fun MainActivity.lanzarEscannerTaller(onCodigo: (String) -> Unit) {
    onScanResultCallback = onCodigo
    currentScannerTarget = null
    scanLauncher.launch(
        Intent(this, ScannerActivity::class.java).apply {
            putExtra(ScannerActivity.EXTRA_MODO_TALLER, true)
        },
    )
}

internal fun MainActivity.iniciarEscaneoTallerMovimiento(
    tipoMovimiento: String,
    subModulo: String = "",
) {
    tallerScanTipoMovimiento = tipoMovimiento
    tallerScanSubModulo = subModulo.ifBlank { TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER }
    Toast.makeText(this, "Apunta al QR para $tipoMovimiento...", Toast.LENGTH_SHORT).show()
    prepararInventarioTaller {
        lanzarEscannerTaller { codigo -> procesarCodigoEscaneadoTaller(codigo) }
    }
}

internal fun MainActivity.iniciarEscaneoTallerRapido() {
    tallerScanTipoMovimiento = null
    tallerScanSubModulo = ""
    Toast.makeText(this, "Apunta al QR...", Toast.LENGTH_SHORT).show()
    prepararInventarioTaller {
        lanzarEscannerTaller { codigo -> procesarCodigoEscaneadoTaller(codigo) }
    }
}

internal fun MainActivity.procesarCodigoEscaneadoTaller(codigoEscaneado: String) {
    if (isFinishing || isDestroyed) return

    val codigo = codigoEscaneado.trim()
    val tipoForzado = tallerScanTipoMovimiento
    val subFiltro = tallerScanSubModulo
    tallerScanTipoMovimiento = null
    tallerScanSubModulo = ""

    try {
        if (codigo.isBlank()) {
            Toast.makeText(this, "No se detecto ningun codigo", Toast.LENGTH_SHORT).show()
            return
        }

        if (tipoForzado.equals(TallerCanonicos.TIPO_MOV_INGRESO_BODEGA, ignoreCase = true)) {
            val existente = buscarHerramientaTallerPorCodigo(codigo)
            if (existente != null) {
                AlertDialog.Builder(this)
                    .setTitle("Código ya registrado")
                    .setMessage(
                        "${existente.nombre} ya existe en ${existente.subModulo}.\n\n" +
                            "Ingreso a bodega es solo para productos nuevos.",
                    )
                    .setPositiveButton("Escanear otro") { _, _ ->
                        iniciarEscaneoTallerMovimiento(TallerCanonicos.TIPO_MOV_INGRESO_BODEGA, subFiltro)
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
                return
            }
            val qrNumero = TallerCanonicos.codigoQrDesdeCodigoPrincipal(codigo, codigo)
            Toast.makeText(this, "Código libre: registrando producto nuevo en Bodega Roja", Toast.LENGTH_SHORT).show()
            showIngresoProductoBodegaRojaForm(
                prefCodigo = TallerCanonicos.normalizarCodigo(codigo),
                prefCodigoQr = qrNumero,
            )
            return
        }

        val herramienta = buscarHerramientaTallerPorCodigo(codigo)
        if (herramienta == null) {
            AlertDialog.Builder(this)
                .setTitle("Codigo no encontrado")
                .setMessage("No existe ningun item en Taller con el codigo \"$codigo\".")
                .setPositiveButton("Escanear de nuevo") { _, _ ->
                    if (tipoForzado != null) {
                        iniciarEscaneoTallerMovimiento(tipoForzado, subFiltro)
                    } else {
                        iniciarEscaneoTallerRapido()
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
            return
        }

        if (tipoForzado.equals(TallerCanonicos.TIPO_MOV_TRASLADO, ignoreCase = true)) {
            if (!TallerCanonicos.esBodegaRojaTaller(herramienta.subModulo)) {
                AlertDialog.Builder(this)
                    .setTitle("Ítem fuera de Bodega Roja")
                    .setMessage("${herramienta.nombre} está en ${herramienta.subModulo}. Solo puedes trasladar ítems guardados en Bodega Roja.")
                    .setPositiveButton("Escanear otro", null)
                    .show()
                return
            }
            if (herramienta.disponibles() <= 0.0) {
                Toast.makeText(this, "Este ítem no tiene stock disponible en bodega", Toast.LENGTH_LONG).show()
                return
            }
            abrirFormularioTrasladoBodegaRojaTrasEscaneo(herramienta = herramienta)
            return
        }

        val tipoMovimiento = tipoForzado ?: inferirTipoMovimientoTaller(herramienta)
        val subModulo = herramienta.subModulo.ifBlank {
            subFiltro.ifBlank { TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER }
        }

        if (tipoMovimiento.equals("Entrada", ignoreCase = true) && !herramienta.estaEnPrestamo()) {
            AlertDialog.Builder(this)
                .setTitle("Sin préstamo activo")
                .setMessage(
                    "${herramienta.nombre} no tiene cantidad ocupada.\n\n" +
                        "Solo puedes registrar entrada de ítems que estén en préstamo.",
                )
                .setPositiveButton("Escanear otro", null)
                .show()
            return
        }

        if (tipoMovimiento.equals("Salida", ignoreCase = true) && herramienta.disponibles() <= 0.0) {
            AlertDialog.Builder(this)
                .setTitle("Sin stock disponible")
                .setMessage(
                    "${herramienta.nombre} no tiene unidades disponibles para prestar.\n\n" +
                        "Todas las existencias de este ítem ya fueron prestadas en el submódulo.",
                )
                .setPositiveButton("Escanear otro", null)
                .show()
            return
        }

        val subModuloActivo = subFiltro.ifBlank { subModulo }
        if (
            tipoMovimiento.equals("Entrada", ignoreCase = true) &&
            subFiltro.isNotBlank() &&
            !TallerCanonicos.coincideSubmoduloTaller(herramienta.subModulo, subFiltro)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Submódulo distinto")
                .setMessage(
                    "${herramienta.nombre} pertenece a ${herramienta.subModulo}.\n\n" +
                        "Este escaneo es para devoluciones en $subFiltro.",
                )
                .setPositiveButton("Escanear otro", null)
                .show()
            return
        }

        Toast.makeText(
            this,
            "QR capturado: abriendo $tipoMovimiento de ${herramienta.nombre}",
            Toast.LENGTH_SHORT,
        ).show()

        abrirFormularioMovimientoTallerTrasEscaneo(
            tipoMovimiento = tipoMovimiento,
            herramienta = herramienta,
            subModulo = subModuloActivo,
        )
    } catch (e: Exception) {
        android.util.Log.e("ArlesGestion", "Error tras escaneo Taller", e)
        Toast.makeText(
            this,
            "No se pudo abrir el formulario: ${e.localizedMessage ?: "error inesperado"}",
            Toast.LENGTH_LONG,
        ).show()
    }
}

internal fun MainActivity.abrirFormularioMovimientoTallerTrasEscaneo(
    tipoMovimiento: String,
    herramienta: Herramienta,
    subModulo: String,
) {
    currentScreenRenderer = {
        showMovimientoHerramientaForm(
            tipoMovimiento = tipoMovimiento,
            herramientaIdPreseleccionada = herramienta.id,
            subModulo = subModulo,
            sincronizar = false,
        )
    }
    currentScreenBackAction = { showTallerSubmoduloMenu(subModulo) }
    renderMovimientoHerramientaForm(
        tipoMovimiento = tipoMovimiento,
        herramientaIdPreseleccionada = herramienta.id,
        subModulo = subModulo,
    )
}

internal fun MainActivity.abrirFormularioTrasladoBodegaRojaTrasEscaneo(
    herramienta: Herramienta,
) {
    currentScreenRenderer = {
        showTrasladoBodegaRojaForm(
            herramientaIdPreseleccionada = herramienta.id,
            sincronizar = false,
        )
    }
    currentScreenBackAction = { showTallerSubmoduloMenu(TallerCanonicos.SUBMODULO_BODEGA_ROJA) }
    renderTrasladoBodegaRojaForm(herramientaIdPreseleccionada = herramienta.id)
}

internal fun MainActivity.navegarTallerPorCodigoEscaneado(codigoEscaneado: String) {
    procesarCodigoEscaneadoTaller(codigoEscaneado)
}

internal fun MainActivity.conteoHerramientasPorSubmodulo(): Map<String, Int> {
    return herramientasTallerActivas()
        .groupingBy { TallerCanonicos.normalizarSubmoduloTaller(it.subModulo) }
        .eachCount()
}

internal fun MainActivity.showHerramientasMenu() {
        currentScreenRenderer = { showHerramientasMenu() }
        val root = baseScreen(
            "TALLER",
            "Control de préstamos, devoluciones y stock por submódulo.",
            showBack = !AppMode.esTallerIndependiente,
            backAction = { showMainMenu() },
        )
        prepararInventarioTaller {}
        currentScreenBackAction = if (AppMode.esTallerIndependiente) null else ({ showMainMenu() })

        val herramientas = herramientasTallerActivas()
        val enPrestamo = herramientas.count { it.ocupados() > 0.0 || it.estado.equals("En uso", ignoreCase = true) }
        val disponibles = herramientas.sumOf { it.disponibles() }
        val conteoSubmodulos = conteoHerramientasPorSubmodulo()

        root.addView(
            moduleHeroBanner(
                R.drawable.ic_tools,
                "Módulo Taller",
                "Escanea QR, registra salidas temporales y consulta disponibilidad en tiempo real.",
                ArlesPalette.accentTaller,
            ),
        )
        val etiquetaPrestamos = "En prestamo"
        root.addView(
            kpiMetricsRow(
                listOf(
                    Triple("Ítems", "${herramientas.size}", ArlesPalette.accentTaller),
                    Triple("Disponibles", formatoCantidadTaller(disponibles), ArlesPalette.green700),
                    Triple(etiquetaPrestamos, "$enPrestamo", ArlesPalette.warning),
                ),
                actions = mapOf(etiquetaPrestamos to { _: View -> abrirDialogoPrestamosActivosTaller() }),
            ),
        )
        root.addView(
            actionCard(
                "Escanear QR rápido",
                "Identifica herramienta y abre salida o entrada según estado",
                R.drawable.ic_scanner,
                ArlesPalette.accentTaller,
            ) { iniciarEscaneoTallerRapido() },
        )

        root.addView(sectionHeader("Submódulos", "Selecciona el área de trabajo"))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        TallerCanonicos.SUBMODULOS.chunked(2).forEach { fila ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            fila.forEachIndexed { index, subModulo ->
                val count = conteoSubmodulos[subModulo] ?: 0
                row.addView(
                    tallerSubmoduloTile(
                        title = etiquetaTabSubmoduloTaller(subModulo),
                        subtitle = subModulo,
                        itemCount = count,
                        iconRes = tallerSubmoduloIcon(subModulo),
                        accent = tallerSubmoduloAccent(subModulo),
                    ) { showTallerSubmoduloMenu(subModulo) },
                    LinearLayout.LayoutParams(0, dp(118), 1f).apply {
                        setMargins(if (index == 0) 0 else dp(5), 0, if (index == 0) dp(5) else 0, dp(8))
                    },
                )
            }
            if (fila.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(118), 1f))
            }
            grid.addView(row)
        }
        root.addView(grid)

        root.addView(sectionHeader("Consultas y sincronización"))
        root.addView(
            actionCard(
                "Inventario general",
                "Planilla completa con filtros por submódulo y ocupados",
                R.drawable.ic_tools,
                ArlesPalette.accentTaller,
            ) { showHerramientasRegistradas() },
        )
        root.addView(
            actionCard(
                "Movimientos del taller",
                "Historial de salidas y entradas con evidencia fotográfica",
                R.drawable.ic_changelog,
                ArlesPalette.slateAction,
            ) { showHistorialMovimientosHerramientas() },
        )
        if (AppMode.esTallerIndependiente) {
            root.addView(
                actionCard(
                    "Lubricantes taller",
                    "Salida y traslado de aceites, grasas y fluidos del taller",
                    R.drawable.ic_lubricants,
                    ArlesPalette.accentLubricantes,
                ) { showLubricantesTallerForm() },
            )
            root.addView(
                actionCard(
                    "Movimientos de taller y lubricantes",
                    "Consulta en nube filtrada para la app Taller",
                    R.drawable.ic_inbox,
                    ArlesPalette.slateAction,
                ) { showTablaMovimientos() },
            )
            root.addView(
                actionCard(
                    "Stock lubricantes taller",
                    "Inventario en nube solo para lubricantes del taller",
                    R.drawable.ic_warehouse,
                    ArlesPalette.accentLubricantes,
                ) { showInventarioScreen() },
            )
        }
        root.addView(styledButton("Sincronizar catálogo a la nube", ArlesButtonStyle.OUTLINE) {
            forzarSincronizacionHerramientas()
        })
        if (AppMode.esTallerIndependiente) {
            root.addView(primaryButton("Cerrar Sesión") {
                auth.signOut()
                showMainMenu()
            })
        }
    }

internal fun MainActivity.showTallerSubmoduloMenu(subModulo: String) {
        currentScreenRenderer = { showTallerSubmoduloMenu(subModulo) }
        val accent = tallerSubmoduloAccent(subModulo)
        val root = baseScreen(
            etiquetaTabSubmoduloTaller(subModulo),
            subModulo,
            backAction = { showHerramientasMenu() },
        )
        prepararInventarioTaller {}
        currentScreenBackAction = { showHerramientasMenu() }

        val items = herramientasTallerFiltradas(subModulo)
        val esBodegaRoja = TallerCanonicos.esBodegaRojaTaller(subModulo)
        val ocupados = items.count { it.ocupados() > 0.0 }
        val stockBodega = formatoCantidadTaller(items.sumOf { it.disponibles() })
        root.addView(
            moduleHeroBanner(
                tallerSubmoduloIcon(subModulo),
                etiquetaTabSubmoduloTaller(subModulo),
                if (esBodegaRoja) {
                    "${items.size} ítems en reserva · $stockBodega guardados para soporte o cambio"
                } else {
                    "${items.size} ítems registrados · $stockBodega disponibles · $ocupados en préstamo"
                },
                accent,
            ),
        )

        if (esBodegaRoja) {
            root.addView(sectionHeader("Operaciones", "Bodega de soporte y reserva"))
            root.addView(
                actionCard(
                    "Traslado a submódulo",
                    "Enviar herramienta guardada hacia otro submódulo y categoría",
                    R.drawable.ic_door_open,
                    accent,
                ) { showTrasladoBodegaRojaForm() },
            )
            root.addView(
                actionCard(
                    "Ingreso a bodega",
                    "Registrar productos nuevos para soporte o reserva",
                    R.drawable.ic_warehouse,
                    ArlesPalette.blueAction,
                ) { showIngresoProductoBodegaRojaForm() },
            )
            val hayStockParaPrestamo = items.any { it.disponibles() > 0.0 }
            if (hayStockParaPrestamo) {
                root.addView(
                    actionCard(
                        "Prestar desde bodega",
                        "Registrar prestamo temporal con solicitante, destino y evidencia",
                        R.drawable.ic_door_open,
                        accent,
                    ) { showMovimientoHerramientaForm("Salida", subModulo = subModulo) },
                )
            } else {
                val noStockMsg = android.widget.TextView(this).apply {
                    text = "Sin stock para nuevos prestamos desde Bodega Roja. Todas las existencias estan prestadas o trasladadas."
                    setTextColor(ArlesPalette.danger)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    textSize = 14f
                }
                root.addView(noStockMsg)
            }
            root.addView(
                actionCard(
                    "Devolver a bodega",
                    "Registrar entrada de herramientas prestadas desde Bodega Roja",
                    R.drawable.ic_warehouse,
                    ArlesPalette.blueAction,
                ) { showMovimientoHerramientaForm("Entrada", subModulo = subModulo) },
            )
        } else {
            root.addView(sectionHeader("Operaciones", "Flujo de préstamo temporal"))

            val hayStockParaSalida = items.any { it.disponibles() > 0.0 }
            if (hayStockParaSalida) {
                root.addView(
                    actionCard(
                        "Salida temporal",
                        "Registrar préstamo con solicitante, zona y evidencia",
                        R.drawable.ic_door_open,
                        accent,
                    ) { showMovimientoHerramientaForm("Salida", subModulo = subModulo) },
                )
            } else {
                val noStockMsg = android.widget.TextView(this).apply {
                    text = "⚠ Sin stock para nuevas salidas. Todas las existencias de este submódulo ya fueron prestadas."
                    setTextColor(ArlesPalette.danger)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    textSize = 14f
                }
                root.addView(noStockMsg)
            }

            root.addView(
                actionCard(
                    "Entrada / devolución",
                    "Devolver herramientas ocupadas al inventario",
                    R.drawable.ic_warehouse,
                    ArlesPalette.blueAction,
                ) { showMovimientoHerramientaForm("Entrada", subModulo = subModulo) },
            )
        }

        root.addView(sectionHeader("Consultas"))
        root.addView(
            actionCard(
                "Inventario del submódulo",
                "Ver stock, disponibles y ocupados",
                R.drawable.ic_tools,
                accent,
            ) { showHerramientasRegistradas(subModulo) },
        )
        root.addView(
            actionCard(
                "Movimientos del submódulo",
                if (esBodegaRoja) "Historial de traslados e ingresos" else "Historial filtrado de salidas y entradas",
                R.drawable.ic_changelog,
                ArlesPalette.slateAction,
            ) { showHistorialMovimientosHerramientas(subModulo) },
        )
        if (!esBodegaRoja) {
            root.addView(styledButton("Registrar nuevo ítem", ArlesButtonStyle.TALLER) {
                showRegistroHerramientaForm(prefSubModulo = subModulo)
            })
        }
    }

internal fun MainActivity.confirmarLimpiezaPruebasHerramientas() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar Pruebas")
            .setMessage("¿Estás seguro de eliminar todos los movimientos de herramientas? Esto borrará el historial de salidas y entradas tanto en el celular como en Firestore.")
            .setPositiveButton("Eliminar todo") { _, _ ->
                // 1. Eliminar en SQLite
                val eliminados = db.eliminarMovimientosHerramientas()
                
                // 2. Eliminar en Firestore
                firestore.collection("movimientos")
                    .whereIn("modulo", listOf(TallerCanonicos.MODULO, TallerCanonicos.MODULO_LEGACY))
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val batch = firestore.batch()
                        snapshot.documents.forEach { batch.delete(it.reference) }
                        batch.commit().addOnSuccessListener {
                            Toast.makeText(this, "Historial de herramientas limpio ($eliminados registros)", Toast.LENGTH_LONG).show()
                            showHerramientasMenu()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al limpiar en la nube, pero se borró del celular.", Toast.LENGTH_LONG).show()
                        showHerramientasMenu()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

internal fun MainActivity.forzarSincronizacionHerramientas() {
        val activity = this
        val herramientasLocales = db.obtenerHerramientas()
        if (herramientasLocales.isEmpty()) {
            Toast.makeText(this, "No hay herramientas locales para sincronizar", Toast.LENGTH_SHORT).show()
            return
        }

        val loading = Toast.makeText(this, "Sincronizando catálogo de herramientas...", Toast.LENGTH_LONG)
        loading.show()

        var exito = 0
        val total = herramientasLocales.size
        
        val uid = auth.currentUser?.uid ?: ""
        obtenerInfoUsuario(uid) { info ->
            herramientasLocales.forEach { h ->
                val data = dataHerramientaCloud(h, h.id, info) + mapOf("sync_manual" to true)
                firestore.collection("herramientas")
                    .document(claveHerramientaCloud(h))
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        exito++
                        if (exito >= total) {
                            loading.cancel()
                            Toast.makeText(activity, "Sincronización completa", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        exito++
                        if (exito >= total) {
                            loading.cancel()
                            Toast.makeText(activity, "Sincronización finalizada con errores parciales", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

internal fun MainActivity.showHistorialMovimientosHerramientas(subModulo: String = "") {
        currentScreenRenderer = { showHistorialMovimientosHerramientas(subModulo) }
        val titulo = if (subModulo.isBlank()) "Movimientos Taller" else "Movimientos $subModulo"
        val subtituloHistorial = if (TallerCanonicos.esBodegaRojaTaller(subModulo)) {
            "Seguimiento de traslados e ingresos a bodega."
        } else {
            "Seguimiento temporal de salidas y entradas."
        }
        val root = baseScreen(titulo, subtituloHistorial, backAction = { if (subModulo.isBlank()) showHerramientasMenu() else showTallerSubmoduloMenu(subModulo) })
        val historialScreenId = currentScreenId

        root.addView(
            moduleHeroBanner(
                R.drawable.ic_changelog,
                titulo,
                if (subModulo.isBlank()) "Historial completo del módulo" else "Filtrado por ${etiquetaTabSubmoduloTaller(subModulo)}",
                if (subModulo.isBlank()) ArlesPalette.accentTaller else tallerSubmoduloAccent(subModulo),
            ),
        )

        val resumenLabel = catalogoResumenBar("Sincronizando movimientos...", ArlesPalette.accentTaller)
        root.addView(resumenLabel)

        val filterInput = tallerSearchField("Buscar por código, responsable, herramienta, solicitante, zona o fecha...")
        root.addView(filterInput)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(listContainer)

        var listaActual: List<Movimiento> = emptyList()

        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {
                updateTableFromList(listaActual, s?.toString() ?: "", listContainer, resumenLabel)
            }
            override fun afterTextChanged(p0: Editable?) {}
        })

        val listenerKey = "taller_historial:${subModulo.ifBlank { "todos" }}"
        val listenerStart = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d("PerfPrincipal", "listener taller_historial crear subModulo=${subModulo.ifBlank { "todos" }} limite=${performanceConfig.movementQueryLimit()}")
        val herramientasListener = firestore.collection("movimientos")
            .whereIn("modulo", listOf(TallerCanonicos.MODULO, TallerCanonicos.MODULO_LEGACY))
            .limit(performanceConfig.movementQueryLimit())
            .addSnapshotListener { snapshot, e ->
                val callbackStart = android.os.SystemClock.elapsedRealtime()
                if (!pantallaActiva() || currentScreenId != historialScreenId) return@addSnapshotListener
                if (e != null) {
                    android.util.Log.e("ArlesGestion", "Error en movimientos taller: ${e.message}")
                    android.util.Log.d("PerfPrincipal", "listener taller_historial subModulo=${subModulo.ifBlank { "todos" }} error=${e.localizedMessage ?: "desconocido"}")
                    listaActual = ordenarMovimientosTallerPorFecha(
                        db.obtenerMovimientos()
                            .filter { TallerCanonicos.esModuloTaller(it.modulo) }
                            .filter { movimientoPerteneceSubmoduloTaller(it, subModulo) },
                    ).take(performanceConfig.movementQueryLimit().toInt())
                    updateTableFromList(listaActual, filterInput.text.toString(), listContainer, resumenLabel)
                    return@addSnapshotListener
                }
                listaActual = ordenarMovimientosTallerPorFecha(
                    (snapshot?.documents ?: emptyList()).map(::movimientoDesdeDocTaller)
                        .filter { TallerCanonicos.esModuloTaller(it.modulo) }
                        .filter { movimientoPerteneceSubmoduloTaller(it, subModulo) },
                )
                android.util.Log.d(
                    "PerfPrincipal",
                    "listener taller_historial subModulo=${subModulo.ifBlank { "todos" }} docs=${snapshot?.size() ?: 0} filtrados=${listaActual.size} callback=${android.os.SystemClock.elapsedRealtime() - callbackStart}ms desde_creacion=${android.os.SystemClock.elapsedRealtime() - listenerStart}ms"
                )
                updateTableFromList(listaActual, filterInput.text.toString(), listContainer, resumenLabel)
            }
        firestoreListeners.add(listenerKey, herramientasListener)
    }

internal fun MainActivity.updateTableFromList(
        listaMov: List<Movimiento>,
        filter: String,
        container: LinearLayout,
        resumenLabel: TextView? = null,
    ) {
        val activity = this
        container.removeAllViews()

        val filtered = listaMov.filter { m ->
            val dataText = "${m.referencia} ${m.item} ${m.solicitante} ${m.fecha} ${m.estado} ${m.tipoMovimiento} ${m.labor} ${m.maquinaria}".lowercase()
            dataText.contains(filter.lowercase())
        }

        if (filtered.isEmpty()) {
            resumenLabel?.text = "0 movimientos"
            container.addView(tallerEmptyState("Sin movimientos", "No hay registros que coincidan con el filtro actual."))
        } else {
            val limiteVisible = if (filter.isBlank()) {
                performanceConfig.movementQueryLimit().toInt()
            } else {
                performanceConfig.movementRenderLimit()
            }
            val (visibleRows, hiddenRows) = RenderLimiter.applyLimit(filtered, limiteVisible)
            resumenLabel?.text = buildString {
                append("${filtered.size} movimientos")
                if (hiddenRows > 0) append(" · +$hiddenRows ocultos (usa el filtro)")
            }
            visibleRows.forEach { m ->
                val observacionesLimpias = m.observaciones.lineSequence()
                    .filterNot { it.startsWith("__FOTO_URL__") }
                    .joinToString("\n")
                val detalleDialogo = "Herramienta: ${m.item}\n" +
                    "Codigo: ${m.referencia.ifBlank { "No especificado" }}\n" +
                    "Submodulo: ${m.maquinaria.ifBlank { "Taller" }}\n" +
                    "Tipo: ${m.tipoMovimiento}\n" +
                    "Fecha: ${m.fecha}\n" +
                    "Solicitante: ${m.solicitante.ifBlank { "No especificado" }}\n" +
                    "Responsable: ${m.estado.ifBlank { "—" }}\n" +
                    "Labor: ${m.labor.ifBlank { "—" }}\n" +
                    "Observaciones: ${observacionesLimpias.ifBlank { "Sin observaciones" }}"
                val fotoUrl = m.observaciones.lineSequence()
                    .firstOrNull { it.startsWith("__FOTO_URL__") }
                    ?.removePrefix("__FOTO_URL__")
                    .orEmpty()
                container.addView(
                    tallerMovimientoCard(
                        fecha = m.fecha,
                        tipo = m.tipoMovimiento,
                        codigo = m.referencia.ifBlank { "-" },
                        herramienta = m.item,
                        responsable = m.estado,
                        solicitante = m.solicitante,
                        observaciones = observacionesLimpias,
                    ) {
                        if (fotoUrl.isNotBlank()) {
                            mostrarDialogoFotoDetalle(fotoUrl, detalleDialogo)
                        } else {
                            AlertDialog.Builder(activity)
                                .setTitle("Detalle del movimiento")
                                .setMessage(detalleDialogo)
                                .setPositiveButton("Cerrar", null)
                                .show()
                        }
                    },
                )
            }
            avisoLimiteRender(container, hiddenRows)
        }
    }

internal fun MainActivity.showEliminarHerramientaForm() {
        val herramientas = db.obtenerHerramientas()
        val root = baseScreen("Eliminar herramienta", "Borra permanentemente un registro del catálogo.", backAction = { showHerramientasMenu() })
        
        if (herramientas.isEmpty()) {
            root.addView(infoText("No hay herramientas registradas para eliminar."))
            return
        }

        val herramientaSpinner = spinner(root, "Selecciona herramienta a eliminar *", herramientas.map { it.toString() })

        root.addView(primaryButton("Eliminar permanentemente") {
            val pos = herramientaSpinner.selectedItemPosition
            if (pos < 0 || pos >= herramientas.size) return@primaryButton
            
            val selectedTool = herramientas[pos]
            
            AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que deseas eliminar '${selectedTool.nombre}'?\n\nEsta acción no se puede deshacer.")
                .setPositiveButton("Sí, eliminar") { _, _ ->
                    db.eliminarHerramienta(selectedTool.id)
                    registrarCambioLocal("ELIMINAR_HERRAMIENTA", TallerCanonicos.MODULO, selectedTool.id.toString(), "Herramienta eliminada: ${selectedTool.nombre}", selectedTool.estado, "Eliminada")
                    saved("Herramienta eliminada correctamente")
                    showHerramientasMenu()
                }
                .setNegativeButton("No, cancelar", null)
                .show()
        })
    }

internal fun MainActivity.showRegistroHerramientaForm(
        prefNombre: String = "",
        prefReferencia: String = "",
        prefMarca: String = "",
        prefCodigo: String = "",
        prefEstado: String = "Disponible",
        prefUbicacion: String = "",
        prefResponsable: String = "",
        prefObservaciones: String = "",
        prefSubModulo: String = TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER,
        prefSubcategoria: String = "",
        prefTipo: String = "",
        prefTamano: String = "",
        prefUnidad: String = "UNIDAD",
        prefCantidadTotal: Double = 1.0,
        prefCodigoQr: String = "",
        prefRequiereAsignarQr: Boolean = false,
        esIngresoBodegaNuevo: Boolean = false,
    ) {
        run {
            val subModuloFijo = if (esIngresoBodegaNuevo) TallerCanonicos.SUBMODULO_BODEGA_ROJA else prefSubModulo
            val accent = tallerSubmoduloAccent(subModuloFijo)
            val tituloPantalla = if (esIngresoBodegaNuevo) "Ingreso a bodega" else "Registrar ítem Taller"
            val subtituloPantalla = if (esIngresoBodegaNuevo) {
                "Producto nuevo en Bodega Roja para soporte o reserva"
            } else {
                "Inventario base antes de préstamos temporales."
            }
            val root = baseScreen(tituloPantalla, subtituloPantalla, backAction = { showTallerSubmoduloMenu(subModuloFijo) })
            root.addView(
                moduleHeroBanner(
                    if (esIngresoBodegaNuevo) R.drawable.ic_warehouse else R.drawable.ic_add,
                    if (esIngresoBodegaNuevo) "Producto nuevo" else "Nuevo ítem",
                    if (esIngresoBodegaNuevo) {
                        "Bodega Roja · clasifica por categoría y registra stock inicial"
                    } else {
                        "${etiquetaTabSubmoduloTaller(subModuloFijo)} · usa QR o código SINQR"
                    },
                    accent,
                ),
            )

            lateinit var subModulo: Spinner
            lateinit var subcategoria: EditText
            lateinit var motivoIngreso: EditText
            lateinit var nombre: EditText
            lateinit var tipo: EditText
            lateinit var tamano: EditText
            lateinit var marca: EditText
            lateinit var codigoQr: EditText
            lateinit var codigo: EditText
            lateinit var unidad: EditText
            lateinit var cantidadTotal: EditText
            lateinit var requiereQr: android.widget.CheckBox
            lateinit var estado: Spinner
            lateinit var ubicacion: EditText
            lateinit var responsable: EditText
            lateinit var observaciones: EditText

            formSectionCard(
                root,
                "1",
                "Clasificación",
                if (esIngresoBodegaNuevo) "Categoría y descripción del producto nuevo" else "Submódulo, categoría y descripción",
                accent,
            ) { section ->
                if (esIngresoBodegaNuevo) {
                    section.addView(infoText("Submódulo: ${TallerCanonicos.SUBMODULO_BODEGA_ROJA}"))
                    subModulo = spinner(section, "Submódulo *", listOf(TallerCanonicos.SUBMODULO_BODEGA_ROJA))
                    subModulo.isEnabled = false
                } else {
                    subModulo = spinner(section, "Submódulo *", TallerCanonicos.SUBMODULOS)
                }
                val categoriasBodega = listOf("Sin categoría") +
                    herramientasTallerFiltradas(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
                        .map { it.categoriaTaller() }
                        .distinct()
                        .sorted()
                if (esIngresoBodegaNuevo && categoriasBodega.size > 1) {
                    val categoriaSpinner = spinner(section, "Categoría *", categoriasBodega)
                    categoriaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val valor = categoriaSpinner.selectedItem?.toString().orEmpty()
                            if (!valor.equals("Sin categoría", true)) {
                                subcategoria.setText(valor)
                            }
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                    subcategoria = field(section, "Categoría personalizada", "O escribe una categoría nueva")
                } else {
                    subcategoria = field(
                        section,
                        if (esIngresoBodegaNuevo) "Categoría *" else "Subcategoría",
                        "Ej: SOPORTE, REPUESTO, RESERVA, HERRAMIENTA ESPECIAL",
                    )
                }
                nombre = field(section, "Producto *", "Ej: taladro, juego de copas, compresor")
                tipo = field(section, "Tipo", "Ej: PALA, ESTRELLA, COPAS")
                tamano = field(section, "Tamaño / referencia", "Ej: GRANDE, PEQUEÑO, #8 AL #1 1/2")
                marca = field(section, "Marca", "Opcional")
            }

            formSectionCard(
                root,
                "2",
                "Códigos y stock",
                if (esIngresoBodegaNuevo) "QR del producto nuevo y cantidad inicial" else "Identificación QR y cantidad en bodega",
                accent,
            ) { section ->
                if (esIngresoBodegaNuevo) {
                    section.addView(
                        iconTextButton("Escanear QR", R.drawable.ic_scanner, accent) {
                            iniciarEscaneoTallerMovimiento(
                                TallerCanonicos.TIPO_MOV_INGRESO_BODEGA,
                                TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                            )
                        }.apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(48),
                            ).apply { setMargins(0, 0, 0, dp(8)) }
                        },
                    )
                }
                codigoQr = field(section, "Código QR", "Escanea o escribe el número QR", scan = true, qrNumerico = true)
                codigo = field(section, "Código principal / SINQR", "QR-### o SINQR-HT-###", scan = true)
                unidad = field(section, "Unidad", "UNIDAD, JUEGO, KIT")
                cantidadTotal = field(section, "Cantidad total *", "Total en bodega", number = true)
                requiereQr = android.widget.CheckBox(this).apply {
                    text = "Requiere asignar QR"
                    textSize = 14f
                    setTextColor(texto)
                    isChecked = prefRequiereAsignarQr
                }
                section.addView(requiereQr)
            }

            formSectionCard(
                root,
                "3",
                if (esIngresoBodegaNuevo) "Ingreso y control" else "Estado y ubicación",
                if (esIngresoBodegaNuevo) "Quién registra y por qué entra a bodega" else "Control operativo del ítem",
                accent,
            ) { section ->
                if (esIngresoBodegaNuevo) {
                    estado = spinner(section, "Estado", listOf("Disponible"))
                    ubicacion = field(section, "Ubicación", TallerCanonicos.SUBMODULO_BODEGA_ROJA)
                    responsable = field(section, "Registrado por *", "Nombre de quien ingresa el producto")
                    motivoIngreso = field(section, "Motivo del ingreso *", "Ej: soporte, reserva, cambio, compra nueva")
                    observaciones = field(section, "Observaciones", "Opcional")
                } else {
                    estado = spinner(section, "Estado", listOf("Disponible", "En uso", "Mantenimiento", "Danada", "Perdida"))
                    ubicacion = field(section, "Ubicación", "Ej: taller, bodega roja, cosecha")
                    responsable = field(section, "Responsable", "Opcional")
                    observaciones = field(section, "Observaciones", "Opcional")
                }
            }

            seleccionarSpinnerPorTexto(subModulo, subModuloFijo)
            subcategoria.setText(prefSubcategoria.ifBlank { prefReferencia })
            nombre.setText(prefNombre)
            tipo.setText(prefTipo)
            tamano.setText(prefTamano.ifBlank { prefReferencia })
            marca.setText(prefMarca)
            codigoQr.setText(prefCodigoQr)
            val codigoInicial = prefCodigo.ifBlank { prefCodigoQr.takeIf { it.isNotBlank() }?.let { "QR-$it" } ?: "" }
            codigo.setText(codigoInicial)
            unidad.setText(prefUnidad.ifBlank { "UNIDAD" })
            cantidadTotal.setText(formatoCantidadTaller(prefCantidadTotal))
            ubicacion.setText(prefUbicacion.ifBlank { subModuloFijo })
            responsable.setText(prefResponsable)
            observaciones.setText(prefObservaciones)
            seleccionarSpinnerPorTexto(estado, if (esIngresoBodegaNuevo) "Disponible" else prefEstado)

            val botonGuardar = if (esIngresoBodegaNuevo) "Confirmar ingreso a bodega" else "Guardar ítem en inventario"
            root.addView(styledButton(botonGuardar, ArlesButtonStyle.TALLER) {
                if (esIngresoBodegaNuevo) {
                    if (!required(subcategoria, nombre, cantidadTotal, responsable, motivoIngreso)) return@styledButton
                } else if (!required(nombre, cantidadTotal)) {
                    return@styledButton
                }
                val qrNormalizado = codigoQr.text.toString().trim()
                    .removePrefix("QR-")
                    .removePrefix("qr-")
                val codigoPrincipal = when {
                    qrNormalizado.isNotBlank() -> TallerCanonicos.normalizarCodigo(qrNormalizado)
                    codigo.text.toString().isNotBlank() -> TallerCanonicos.normalizarCodigo(codigo.text.toString())
                    else -> "SINQR-MAN-${System.currentTimeMillis()}"
                }
                val total = cantidadTotal.text.toString().replace(",", ".").toDoubleOrNull() ?: 1.0
                if (esIngresoBodegaNuevo && db.buscarHerramientaId(nombre.text.toString(), codigoPrincipal) != null) {
                    Toast.makeText(
                        this,
                        "Ya existe un ítem con ese código. Ingreso a bodega es solo para productos nuevos.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@styledButton
                }
                val herramientaNueva = Herramienta(
                    fechaRegistro = now(),
                    nombre = nombre.text.toString(),
                    referencia = subcategoria.text.toString(),
                    marca = marca.text.toString(),
                    codigo = codigoPrincipal,
                    estado = estado.selectedItem?.toString() ?: "Disponible",
                    ubicacion = ubicacion.text.toString().ifBlank { subModuloFijo },
                    responsable = responsable.text.toString(),
                    observaciones = observaciones.text.toString(),
                    subModulo = subModulo.selectedItem?.toString() ?: subModuloFijo,
                    subcategoria = subcategoria.text.toString(),
                    tipo = tipo.text.toString(),
                    tamano = tamano.text.toString(),
                    unidad = unidad.text.toString().ifBlank { "UNIDAD" },
                    cantidadTotal = total,
                    cantidadOcupada = 0.0,
                    codigoQr = qrNormalizado,
                    requiereAsignarQr = requiereQr.isChecked || codigoPrincipal.startsWith("SINQR", true),
                )
                val herramientaIdLocal = if (esIngresoBodegaNuevo) {
                    db.insertarHerramienta(herramientaNueva)
                } else {
                    db.insertarOActualizarHerramientaCanonica(herramientaNueva)
                }
                val herramientaGuardada = herramientaNueva.copy(id = herramientaIdLocal)
                val accionLocal = if (esIngresoBodegaNuevo) "INGRESO_BODEGA_NUEVO" else "CREAR_HERRAMIENTA"
                val descripcionLocal = if (esIngresoBodegaNuevo) {
                    "Producto nuevo en Bodega Roja: ${herramientaNueva.nombre}"
                } else {
                    "Item Taller registrado: ${herramientaNueva.nombre}"
                }
                registrarCambioLocal(accionLocal, TallerCanonicos.MODULO, herramientaIdLocal.toString(), descripcionLocal, "", herramientaNueva.estado)
                obtenerInfoUsuario(auth.currentUser?.uid ?: "") { usuario ->
                    val data = dataHerramientaCloud(herramientaGuardada, herramientaIdLocal, usuario)
                    val documentoId = claveHerramientaCloud(herramientaGuardada)
                    val movimientoIngreso = when {
                        esIngresoBodegaNuevo -> registrarMovimientoIngresoBodegaNuevo(
                            herramienta = herramientaGuardada,
                            registradoPor = responsable.text.toString(),
                            motivo = motivoIngreso.text.toString(),
                            observaciones = observaciones.text.toString(),
                            responsableEntrega = usuario,
                            uid = auth.currentUser?.uid.orEmpty(),
                        )
                        herramientaGuardada.cantidadTotal > 0.0 -> mapOf(
                            "fecha" to now(),
                            "modulo" to TallerCanonicos.MODULO,
                            "tipoMovimiento" to "Alta inventario",
                            "item" to herramientaGuardada.nombre,
                            "referencia" to herramientaGuardada.codigo,
                            "cantidad" to herramientaGuardada.cantidadTotal,
                            "unidad" to herramientaGuardada.unidad,
                            "submodulo_taller" to herramientaGuardada.subModulo,
                            "usuario" to usuario,
                            "usuario_uid" to auth.currentUser?.uid.orEmpty(),
                            "observaciones" to herramientaGuardada.observaciones,
                        )
                        else -> null
                    }
                    fun finalizarRegistro(mensaje: String) {
                        saved(mensaje)
                        showTallerSubmoduloMenu(herramientaGuardada.subModulo)
                    }
                    if (movimientoIngreso != null) {
                        fun encolarIngreso() {
                            encolarEntradaHerramientaPendiente(
                                documentoId,
                                data,
                                movimientoIngreso,
                                herramientaGuardada.cantidadTotal,
                                registrarSinIncremento = esIngresoBodegaNuevo,
                            )
                            finalizarRegistro(
                                if (esIngresoBodegaNuevo) "Producto ingresado localmente en Bodega Roja"
                                else "Item Taller registrado localmente",
                            )
                        }
                        if (isNetworkAvailable()) {
                            guardarEntradaHerramientaAtomica(
                                documentoId,
                                data,
                                movimientoIngreso,
                                herramientaGuardada.cantidadTotal,
                                registrarSinIncremento = esIngresoBodegaNuevo,
                            ).addOnSuccessListener {
                                finalizarRegistro(
                                    if (esIngresoBodegaNuevo) "Producto nuevo ingresado en Bodega Roja"
                                    else "Item Taller registrado en la nube",
                                )
                            }.addOnFailureListener {
                                encolarIngreso()
                            }
                        } else {
                            encolarIngreso()
                        }
                        return@obtenerInfoUsuario
                    }
                    if (isNetworkAvailable()) {
                        firestore.collection("herramientas")
                            .document(documentoId)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener {
                                finalizarRegistro(
                                    if (esIngresoBodegaNuevo) {
                                        "Producto nuevo ingresado en Bodega Roja"
                                    } else {
                                        "Item Taller registrado en la nube"
                                    },
                                )
                            }
                            .addOnFailureListener {
                                guardarFirestoreOffline("herramientas", data)
                                finalizarRegistro(
                                    if (esIngresoBodegaNuevo) {
                                        "Producto ingresado localmente en Bodega Roja"
                                    } else {
                                        "Item Taller registrado localmente"
                                    },
                                )
                            }
                    } else {
                        guardarFirestoreOffline("herramientas", data)
                        finalizarRegistro(
                            if (esIngresoBodegaNuevo) {
                                "Producto ingresado localmente en Bodega Roja"
                            } else {
                                "Item Taller registrado localmente"
                            },
                        )
                    }
                }
            })
        }
    }

internal fun MainActivity.showMovimientoHerramientaForm(
        tipoMovimiento: String,
        herramientaIdPreseleccionada: Long = -1L,
        prefSolicitante: String = "",
        prefLabor: String = "",
        prefObservaciones: String = "",
        subModulo: String = TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER,
        sincronizar: Boolean = true,
    ) {
        currentScreenRenderer = {
            showMovimientoHerramientaForm(
                tipoMovimiento,
                herramientaIdPreseleccionada,
                prefSolicitante,
                prefLabor,
                prefObservaciones,
                subModulo,
                sincronizar,
            )
        }
        if (sincronizar) {
            baseScreen("$tipoMovimiento temporal", "Sincronizando inventario de $subModulo...")
            prepararInventarioTaller {
                if (isFinishing || isDestroyed) return@prepararInventarioTaller
                renderMovimientoHerramientaForm(
                    tipoMovimiento,
                    herramientaIdPreseleccionada,
                    prefSolicitante,
                    prefLabor,
                    prefObservaciones,
                    subModulo,
                )
            }
        } else {
            renderMovimientoHerramientaForm(
                tipoMovimiento,
                herramientaIdPreseleccionada,
                prefSolicitante,
                prefLabor,
                prefObservaciones,
                subModulo,
            )
        }
    }

internal fun MainActivity.renderMovimientoHerramientaForm(
        tipoMovimiento: String,
        herramientaIdPreseleccionada: Long = -1L,
        prefSolicitante: String = "",
        prefLabor: String = "",
        prefObservaciones: String = "",
        subModulo: String = TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER,
    ) {
        if (isFinishing || isDestroyed) return
        try {
            val esEntrada = tipoMovimiento.equals("Entrada", ignoreCase = true)
            val herramientasBase = if (esEntrada) {
                herramientasEnPrestamoTaller(subModulo)
            } else {
                // Para Salidas solo mostramos ítems con stock disponible.
                // Esto bloquea las salidas una vez que todas las existencias han sido prestadas (ocupados == total).
                herramientasTallerFiltradas(subModulo)
                    .filter { it.disponibles() > 0.0 }
                    .sortedWith(compareBy<Herramienta> { it.categoriaTaller() }.thenBy { it.nombre }.thenBy { it.tamano })
            }
            val categoriasDisponibles = if (esEntrada) {
                listOf("Todas las categorías") + herramientasBase.map { it.categoriaTaller() }.distinct().sorted()
            } else {
                emptyList()
            }
            val accent = tallerSubmoduloAccent(subModulo)
            val root = baseScreen(
                "$tipoMovimiento temporal",
                "$subModulo · registro con fecha y hora automáticas",
                backAction = { showTallerSubmoduloMenu(subModulo) },
            )
            if (herramientasBase.isEmpty()) {
                if (esEntrada) {
                    root.addView(
                        tallerEmptyState(
                            "Sin préstamos activos",
                            "No hay herramientas en préstamo en $subModulo. Aquí solo aparecen ítems con cantidad ocupada.",
                        ),
                    )
                } else {
                    // Mensaje específico cuando no hay stock para salidas (todas prestadas)
                    root.addView(tallerEmptyState(
                        "Sin stock disponible",
                        "No hay herramientas con disponibilidad para salida en $subModulo. Todas las existencias están prestadas."
                    ))
                    root.addView(styledButton("Ver inventario del submódulo", ArlesButtonStyle.TALLER) {
                        showHerramientasRegistradas(subModulo)
                    })
                }
                return
            }

            root.addView(
                moduleHeroBanner(
                    if (tipoMovimiento.equals("Salida", true)) R.drawable.ic_door_open else R.drawable.ic_warehouse,
                    "$tipoMovimiento temporal",
                    if (esEntrada) {
                        "${etiquetaTabSubmoduloTaller(subModulo)} · ${herramientasBase.size} en préstamo"
                    } else {
                        etiquetaTabSubmoduloTaller(subModulo)
                    },
                    accent,
                ),
            )

            var categoriaSeleccionada = "Todas las categorías"
            var herramientasVisibles = herramientasBase.toMutableList()
            fun opcionesSpinner(): List<String> = herramientasVisibles.map { h ->
                if (esEntrada) h.etiquetaEntradaPrestamo() else h.toString()
            }
            fun etiquetasQr(): List<String> = herramientasVisibles.map { it.etiquetaBusquedaQr() }.distinct()

            lateinit var codigoBusqueda: AutoCompleteTextView
            lateinit var busquedaNombre: EditText
            lateinit var categoriaFiltro: Spinner
            lateinit var herramienta: Spinner
            lateinit var stockInfo: TextView
            lateinit var cantidad: EditText
            lateinit var solicitante: EditText
            lateinit var tipoLabor: EditText
            lateinit var zona: EditText
            lateinit var observaciones: EditText
            var manualesAdicionales: EditText? = null
            var filtroCategoriaActivo = false
            var cantidadLista = false
            var textoFiltroHerramienta = ""

            fun herramientaSeleccionada(): Herramienta {
                val pos = herramienta.selectedItemPosition
                return herramientasVisibles[pos.coerceIn(0, herramientasVisibles.lastIndex)]
            }

            fun actualizarDisponibilidad() {
                if (herramientasVisibles.isEmpty()) return
                val h = herramientaSeleccionada()
                val disponibles = h.disponibles()
                val ocupados = h.ocupados()
                
                val infoList = mutableListOf(
                    "Categoría" to h.categoriaTaller(),
                    "Tipo/tamaño" to h.detalleTaller().ifBlank { "-" },
                    "Ocupados" to formatoCantidadTaller(ocupados),
                    "Disponibles" to formatoCantidadTaller(disponibles),
                    "QR pendiente" to if (h.requiereAsignarQr) "Sí" else "No",
                )
                
                if (esEntrada && h.responsable.isNotBlank()) {
                    infoList.add(2, "Prestado a" to h.responsable)
                }

                stockInfo.renderStockSummary(
                    if (esEntrada) "Devolución · ${h.categoriaTaller()}" else h.textoDisponibilidad(),
                    infoList,
                    highlightKey = if (esEntrada) "Ocupados" else "Disponibles",
                    positiveColor = if (esEntrada) ArlesPalette.warning else ArlesPalette.green800,
                    emptyColor = ArlesPalette.danger,
                )
                if (cantidadLista && cantidad.text.isBlank()) {
                    cantidad.setText(
                        if (esEntrada && ocupados > 0.0) formatoCantidadTaller(ocupados) else "1",
                    )
                }
            }

            fun refrescarSpinnerHerramientas() {
                herramienta.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    opcionesSpinner(),
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                codigoBusqueda.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, etiquetasQr()),
                )
                if (herramientasVisibles.isNotEmpty()) {
                    herramienta.setSelection(0)
                    actualizarDisponibilidad()
                }
            }

            fun normalizarConsultaHerramienta(filtro: String): String {
                val conectores = setOf("de", "del", "la", "el", "los", "las")
                return normalizarBusqueda(filtro)
                    .split(" ")
                    .filter { it.isNotBlank() && it !in conectores }
                    .joinToString(" ")
            }

            fun textoContieneConsultaHerramienta(texto: String, query: String): Boolean {
                val base = normalizarConsultaHerramienta(texto)
                if (query.isBlank()) return true
                if (base.contains(query)) return true
                val tokens = query.split(" ").filter { it.isNotBlank() }
                return tokens.isNotEmpty() && tokens.all { base.contains(it) }
            }

            fun textoBusquedaNombreHerramienta(h: Herramienta): String {
                return listOf(
                    h.nombre,
                    h.tipo,
                    h.tamano,
                    h.marca,
                    h.referencia,
                    h.detalleTaller(),
                ).joinToString(" ")
            }

            fun ordenarHerramientasSeleccion(lista: List<Herramienta>, filtro: String = ""): List<Herramienta> {
                val query = normalizarConsultaHerramienta(filtro)
                return lista.sortedWith(
                    compareBy<Herramienta> {
                        val nombreNorm = normalizarConsultaHerramienta(it.nombre)
                        when {
                            query.isBlank() -> 3
                            nombreNorm == query -> 0
                            nombreNorm.contains(query) -> 1
                            textoContieneConsultaHerramienta(textoBusquedaHerramientaTaller(it), query) -> 2
                            else -> 3
                        }
                    }
                        .thenBy { it.categoriaTaller() }
                        .thenBy { it.nombre }
                        .thenBy { it.tamano },
                )
            }

            fun baseFiltradaPorCategoria(): List<Herramienta> {
                if (!esEntrada || categoriaSeleccionada == "Todas las categorías") return herramientasBase
                return herramientasBase.filter { it.categoriaTaller().equals(categoriaSeleccionada, ignoreCase = true) }
            }

            fun aplicarFiltroTextoHerramienta(texto: String) {
                textoFiltroHerramienta = texto
                val query = normalizarConsultaHerramienta(texto)
                val base = baseFiltradaPorCategoria()
                val filtradas = if (query.isBlank()) {
                    base
                } else {
                    base.filter { h ->
                        textoContieneConsultaHerramienta(textoBusquedaHerramientaTaller(h), query) ||
                            coincideCodigoHerramienta(h, texto)
                    }
                }
                if (filtradas.isEmpty()) return
                herramientasVisibles = ordenarHerramientasSeleccion(filtradas.distinctBy { it.id }, texto).toMutableList()
                refrescarSpinnerHerramientas()
            }

            fun aplicarFiltroNombreHerramienta(texto: String) {
                textoFiltroHerramienta = texto
                val query = normalizarConsultaHerramienta(texto)
                val base = baseFiltradaPorCategoria()
                val filtradas = if (query.isBlank()) {
                    base
                } else {
                    base.filter { h ->
                        textoContieneConsultaHerramienta(textoBusquedaNombreHerramienta(h), query)
                    }
                }
                if (filtradas.isEmpty()) return
                herramientasVisibles = ordenarHerramientasSeleccion(filtradas.distinctBy { it.id }, texto).toMutableList()
                refrescarSpinnerHerramientas()
            }

            fun aplicarFiltroCategoria() {
                if (!esEntrada) return
                categoriaSeleccionada = categoriaFiltro.selectedItem?.toString().orEmpty()
                    .ifBlank { "Todas las categorías" }
                aplicarFiltroTextoHerramienta(textoFiltroHerramienta)
            }

            val seleccionTitulo = if (esEntrada) {
                "Selección para devolución"
            } else {
                "Selección del ítem"
            }
            val seleccionSubtitulo = if (esEntrada) {
                "Solo ítems en préstamo · filtra por categoría"
            } else {
                "Escanea QR o elige de la lista"
            }

            formSectionCard(root, "1", seleccionTitulo, seleccionSubtitulo, accent) { section ->
                section.addView(
                    iconTextButton("Escanear QR", R.drawable.ic_scanner, accent) {
                        iniciarEscaneoTallerMovimiento(tipoMovimiento, subModulo)
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(48),
                        ).apply { setMargins(0, 0, 0, dp(8)) }
                    },
                )
                codigoBusqueda = codigoInternoField(
                    section,
                    "Código QR / código",
                    "Escanea QR o escribe código",
                    scan = true,
                    modoQrNumerico = false,
                )
                codigoBusqueda.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, etiquetasQr()),
                )
                busquedaNombre = tallerSearchField("Buscar por nombre: tijeras poda")
                section.addView(busquedaNombre)
                if (esEntrada) {
                    categoriaFiltro = spinner(section, "Categoría *", categoriasDisponibles)
                    filtroCategoriaActivo = true
                    categoriaFiltro.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) {
                            aplicarFiltroCategoria()
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                }
                herramienta = spinner(
                    section,
                    if (esEntrada) "Ítem en préstamo *" else "Ítem *",
                    opcionesSpinner(),
                )
                stockInfo = stockInfoCard(
                    if (esEntrada) {
                        "Selecciona categoría e ítem en préstamo"
                    } else {
                        "Selecciona un ítem para ver disponibilidad"
                    },
                )
                section.addView(stockInfo)
            }

            if (esEntrada) {
                // FORMULARIO CORTO PARA ENTRADAS / DEVOLUCIONES (por submodulo)
                // Solo: lista de herramientas prestadas (ya filtrada por submodulo), quién entrega, estado del equipo
                formSectionCard(root, "2", "Devolución rápida", "Selecciona la herramienta prestada y registra la entrega", accent) { section ->
                    // El spinner de "herramienta" ya está en la sección 1 y muestra solo las prestadas del submodulo actual
                    // Aquí solo datos mínimos de devolución
                    cantidad = field(section, "Cantidad", "Se devolverá lo ocupado", number = true)
                    cantidadLista = true
                    // Nombre de quien entrega (reutilizamos el campo solicitante)
                    solicitante = field(
                        section,
                        "Quién entrega / devuelve *",
                        "Nombre de la persona que devuelve la herramienta",
                    )
                    // Estado del equipo - lo más importante para el usuario
                    val estadosEquipo = listOf("Disponible", "Bueno", "Mantenimiento", "Dañada", "Perdida")
                    // Creamos un spinner manualmente para estado
                    val estadoLabel = android.widget.TextView(this).apply {
                        text = "Estado del equipo *"
                        setTextColor(android.graphics.Color.BLACK)
                        setPadding(0, dp(8), 0, dp(4))
                    }
                    section.addView(estadoLabel)
                    val estadoSpinner = android.widget.Spinner(this)
                    estadoSpinner.adapter = android.widget.ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        estadosEquipo
                    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    estadoSpinner.setSelection(0) // default Disponible
                    section.addView(estadoSpinner)

                    // Guardamos referencia al estado elegido usando una variable local accesible
                    // (la usaremos al guardar)
                    // Reutilizamos "tipoLabor" variable para guardar el estado elegido (hack simple para no romper mucho)
                    tipoLabor = android.widget.EditText(this).apply { setText(estadosEquipo[0]); visibility = android.view.View.GONE }
                    // Cuando cambie el spinner, actualizamos el hidden
                    estadoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                            tipoLabor.setText(estadosEquipo[pos])
                        }
                        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                    }

                    zona = android.widget.EditText(this).apply { setText(""); visibility = android.view.View.GONE } // no se usa en entrada corta
                    observaciones = field(section, "Observaciones", "Opcional (daño, motivo, etc.)")
                }
            } else {
                formSectionCard(root, "2", "Datos del movimiento", "Persona, labor y ubicación de trabajo", accent) { section ->
                    cantidad = field(section, "Cantidad *", "Cantidad temporal", number = true)
                    cantidadLista = true
                    solicitante = field(
                        section,
                        if (tipoMovimiento == "Salida") "Solicitante *" else "Entrega / recibe *",
                        "Nombre de la persona",
                    )
                    tipoLabor = field(section, "Tipo de labor *", "Ej: mantenimiento, cosecha, reparación")
                    zona = field(section, "Zona a ejecutar *", "Ej: taller, lote, bomba, invernadero")

                    if (tipoMovimiento == "Salida" && TallerCanonicos.coincideSubmoduloTaller(subModulo, "HERRAMIENTAS MECANICAS")) {
                        manualesAdicionales = field(section, "Herramientas manuales (adicionales)", "Escribe herramientas extras que no tengan QR")
                    }

                    observaciones = field(section, "Observaciones", "Opcional")
                }
            }

            var urlEvidencia = ""
            var uriLocalEvidencia = ""

            // Soporte para asignación automática de implementos a vehículos
            var implementosSeleccionados: List<Herramienta> = emptyList()
            val esVehiculoSubmodulo = subModulo.uppercase().contains("VEHICULO")

            // Solo mostrar el selector de implementos en SALIDAS de vehículos
            if (!esEntrada && esVehiculoSubmodulo) {
                formSectionCard(root, "3", "Implementos a llevar", "Al guardar la salida del vehículo se registrarán automáticamente las salidas de los implementos seleccionados con los mismos datos", accent) { section ->
                    val infoTxt = TextView(this).apply {
                        text = "Ningún implemento seleccionado aún"
                        setPadding(0, dp(4), 0, 0)
                    }

                    val btn = styledButton("Seleccionar implementos agrícolas", ArlesButtonStyle.TALLER) {
                        mostrarDialogoSeleccionImplementos { seleccionados ->
                            implementosSeleccionados = seleccionados
                            infoTxt.text = if (seleccionados.isEmpty()) "Ningún implemento seleccionado aún" else
                                "Asignados: " + seleccionados.joinToString(", ") { it.nombre }
                            Toast.makeText(this, "${seleccionados.size} implemento(s) seleccionado(s) para el vehículo", Toast.LENGTH_SHORT).show()
                        }
                    }
                    section.addView(btn)
                    section.addView(infoTxt)
                }
            }

            fun seleccionarHerramientaPorCodigo(texto: String): Boolean {
                val encontrada = buscarHerramientaTallerPorCodigo(texto) ?: return false
                if (esEntrada && !encontrada.estaEnPrestamo()) {
                    Toast.makeText(
                        this,
                        "Este ítem no está en préstamo. Usa Salida temporal para prestarlo.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return false
                }
                if (esEntrada && !TallerCanonicos.coincideSubmoduloTaller(encontrada.subModulo, subModulo)) {
                    Toast.makeText(this, "El QR pertenece a ${encontrada.subModulo}", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (esEntrada && filtroCategoriaActivo) {
                    val cat = encontrada.categoriaTaller()
                    val catIndex = categoriasDisponibles.indexOfFirst { it.equals(cat, ignoreCase = true) }
                    if (catIndex >= 0) categoriaFiltro.setSelection(catIndex)
                    aplicarFiltroCategoria()
                }
                if (herramientasVisibles.none { it.id == encontrada.id }) {
                    if (esEntrada) {
                        Toast.makeText(this, "El ítem no coincide con la categoría seleccionada", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    herramientasVisibles = (herramientasVisibles + encontrada)
                        .distinctBy { it.id }
                        .let { ordenarHerramientasSeleccion(it, texto) }
                        .toMutableList()
                    refrescarSpinnerHerramientas()
                }
                val index = herramientasVisibles.indexOfFirst { it.id == encontrada.id }
                if (index >= 0 && herramienta.selectedItemPosition != index) {
                    herramienta.setSelection(index)
                    actualizarDisponibilidad()
                    if (!esEntrada && !TallerCanonicos.coincideSubmoduloTaller(encontrada.subModulo, subModulo)) {
                        Toast.makeText(
                            this,
                            "QR encontrado en ${encontrada.subModulo}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                return true
            }

            herramienta.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    actualizarDisponibilidad()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            codigoBusqueda.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val texto = s?.toString().orEmpty()
                    if (!seleccionarHerramientaPorCodigo(texto)) {
                        aplicarFiltroTextoHerramienta(texto)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            busquedaNombre.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    aplicarFiltroNombreHerramienta(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            if (herramientaIdPreseleccionada >= 0) {
                val preseleccionada = herramientasTallerActivas().firstOrNull { it.id == herramientaIdPreseleccionada }
                if (preseleccionada != null) {
                    val numeroQr = TallerCanonicos.codigoQrDesdeCodigoPrincipal(
                        preseleccionada.codigo,
                        preseleccionada.codigoQr,
                    )
                    if (numeroQr.isNotBlank()) {
                        codigoBusqueda.setText(numeroQr)
                        seleccionarHerramientaPorCodigo(numeroQr)
                    } else {
                        val posPre = herramientasVisibles.indexOfFirst { it.id == herramientaIdPreseleccionada }
                        if (posPre >= 0) herramienta.setSelection(posPre, false)
                    }
                }
            }
            solicitante.setText(prefSolicitante)
            if (!esEntrada) {
                tipoLabor.setText(prefLabor)
            }
            observaciones.setText(prefObservaciones)
            actualizarDisponibilidad()

            formSectionCard(root, "3", "Evidencia y confirmación", "Opcional pero recomendado en préstamos", accent) { section ->
                section.addView(evidenceButton {
                    capturarEvidencia { uri ->
                        uriLocalEvidencia = uri
                        mostrarPrevisualizacionEvidencia(root, uri) {
                            uriLocalEvidencia = ""
                            urlEvidencia = ""
                        }
                        if (isNetworkAvailable()) {
                            subirEvidenciaCloud(uri, TallerCanonicos.MODULO) { url -> urlEvidencia = url }
                        }
                    }
                })
                val guardarLabel = if (tipoMovimiento.equals("Salida", true)) {
                    "Confirmar salida temporal"
                } else {
                    "Confirmar entrada"
                }
                section.addView(styledButton(guardarLabel, ArlesButtonStyle.TALLER) {
                val selectedTool = herramientaSeleccionada()
                val cant = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 1.0

                if (esEntrada) {
                    // Validación corta para entradas
                    if (solicitante.text.toString().isBlank() || tipoLabor.text.toString().isBlank()) {
                        Toast.makeText(this, "Indica quién entrega y el estado del equipo", Toast.LENGTH_SHORT).show()
                        return@styledButton
                    }
                } else {
                    if (!required(cantidad, solicitante, tipoLabor, zona)) return@styledButton
                }

                if (cant <= 0.0) {
                    Toast.makeText(this, "Cantidad invalida", Toast.LENGTH_SHORT).show()
                    return@styledButton
                }
                if (tipoMovimiento.equals("Salida", true) && cant > selectedTool.disponibles()) {
                    AlertDialog.Builder(this)
                        .setTitle("Sin disponibilidad")
                        .setMessage("${selectedTool.nombre}\nDisponibles: ${formatoCantidadTaller(selectedTool.disponibles())}\nOcupados: ${formatoCantidadTaller(selectedTool.ocupados())}")
                        .setPositiveButton("Entendido", null)
                        .show()
                    return@styledButton
                }
                if (tipoMovimiento.equals("Entrada", true) && cant > selectedTool.ocupados()) {
                    Toast.makeText(this, "No hay esa cantidad ocupada para devolver", Toast.LENGTH_SHORT).show()
                    return@styledButton
                }

                fun registrarConEvidencia(fotoFinal: String) {
                    val estadoParaEntrada = if (esEntrada) tipoLabor.text.toString() else ""
                    val obsBase = observaciones.text.toString()
                    val manualesTexto = manualesAdicionales?.text?.toString().orEmpty().trim()
                    val obsFinal = if (manualesTexto.isNotBlank()) {
                        if (obsBase.isBlank()) "Herramientas manuales: $manualesTexto"
                        else "$obsBase. Herramientas manuales: $manualesTexto"
                    } else obsBase

                    registrarMovimientoHerramienta(
                        tipo = tipoMovimiento,
                        herramienta = selectedTool,
                        solicitante = solicitante.text.toString(),
                        labor = if (esEntrada) "" else tipoLabor.text.toString(), // para entrada no usamos labor/zona extenso
                        zona = if (esEntrada) "" else zona.text.toString(),
                        observaciones = obsFinal,
                        cantidad = cant.toString(),
                        fotoUrl = fotoFinal,
                        estado = estadoParaEntrada
                    )
                }

                when {
                    urlEvidencia.isNotBlank() -> registrarConEvidencia(urlEvidencia)
                    uriLocalEvidencia.isNotBlank() && isNetworkAvailable() -> {
                        Toast.makeText(this, "Subiendo evidencia Taller...", Toast.LENGTH_SHORT).show()
                        subirEvidenciaCloud(uriLocalEvidencia, TallerCanonicos.MODULO) { url ->
                            urlEvidencia = url
                            registrarConEvidencia(url)
                        }
                    }
                    uriLocalEvidencia.isNotBlank() -> registrarConEvidencia(uriLocalEvidencia)
                    else -> registrarConEvidencia("")
                }

                // --- Lógica de asignación automática de implementos a vehículos ---
                if (!esEntrada && esVehiculoSubmodulo && implementosSeleccionados.isNotEmpty()) {
                    val vehiculoNombre = "${selectedTool.nombre} ${selectedTool.codigo.ifBlank { "" }}".trim()
                    val datosComunes = mapOf(
                        "solicitante" to solicitante.text.toString(),
                        "labor" to tipoLabor.text.toString(),
                        "zona" to zona.text.toString(),
                        "observaciones" to (observaciones.text.toString() + " (asignado a vehículo $vehiculoNombre)").trim(),
                        "fotoUrl" to (if (urlEvidencia.isNotBlank()) urlEvidencia else uriLocalEvidencia)
                    )

                    for (impl in implementosSeleccionados) {
                        try {
                            // Registrar salida automática para el implemento con los mismos datos del vehículo
                            registrarMovimientoHerramienta(
                                tipo = "Salida",
                                herramienta = impl,
                                solicitante = datosComunes["solicitante"] ?: "",
                                labor = datosComunes["labor"] ?: "",
                                zona = datosComunes["zona"] ?: "",
                                observaciones = datosComunes["observaciones"] ?: "",
                                cantidad = "1",
                                fotoUrl = datosComunes["fotoUrl"] ?: "",
                                vehiculoAsignado = vehiculoNombre,
                                auto = true
                            )
                            // Actualizar localmente el estado del implemento como asignado (por si el sync tarda)
                            db.actualizarAsignacionVehiculo(impl.id, vehiculoNombre)
                            db.actualizarOcupacionHerramienta(impl.id, impl.ocupados() + 1.0, datosComunes["solicitante"] ?: "")

                            // Push a la nube para visibilidad inmediata de otros usuarios
                            if (isNetworkAvailable()) {
                                try {
                                    firestore.collection("herramientas")
                                        .document(claveHerramientaCloud(impl))
                                        .update(
                                            mapOf(
                                                "vehiculo_asignado" to vehiculoNombre,
                                                "ultima_actualizacion" to now()
                                            )
                                        )
                                } catch (_: Exception) {}
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ArlesGestión", "Error auto-salida implemento ${impl.nombre}", e)
                        }
                    }
                    Toast.makeText(this, "${implementosSeleccionados.size} implemento(s) asignado(s) automáticamente al vehículo", Toast.LENGTH_LONG).show()
                }

                // --- Entrada automática de implementos al devolver el vehículo ---
                if (esEntrada && esVehiculoSubmodulo) {
                    val vehiculoNombre = "${selectedTool.nombre} ${selectedTool.codigo.ifBlank { "" }}".trim()
                    val implementsAsignados = herramientasTallerActivas().filter {
                        it.vehiculoAsignado.equals(vehiculoNombre, ignoreCase = true) && it.ocupados() > 0.0
                    }
                    if (implementsAsignados.isNotEmpty()) {
                        for (impl in implementsAsignados) {
                            try {
                                registrarMovimientoHerramienta(
                                    tipo = "Entrada",
                                    herramienta = impl,
                                    solicitante = solicitante.text.toString(),
                                    labor = "",
                                    zona = "",
                                    observaciones = "Entrada automática por devolución de vehículo $vehiculoNombre",
                                    cantidad = impl.ocupados().toString(),
                                    fotoUrl = (if (urlEvidencia.isNotBlank()) urlEvidencia else uriLocalEvidencia),
                                    vehiculoAsignado = "",  // limpiar la asignación
                                    auto = true
                                )
                                // Asegurar limpieza local
                                db.actualizarAsignacionVehiculo(impl.id, "")
                                db.actualizarOcupacionHerramienta(impl.id, 0.0, solicitante.text.toString())

                                // Limpiar en la nube
                                if (isNetworkAvailable()) {
                                    try {
                                        firestore.collection("herramientas")
                                            .document(claveHerramientaCloud(impl))
                                            .update(
                                                mapOf(
                                                    "vehiculo_asignado" to "",
                                                    "ultima_actualizacion" to now()
                                                )
                                            )
                                    } catch (_: Exception) {}
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ArlesGestión", "Error auto-entrada implemento ${impl.nombre}", e)
                            }
                        }
                        Toast.makeText(this, "${implementsAsignados.size} implemento(s) devueltos automáticamente con el vehículo", Toast.LENGTH_LONG).show()
                    }
                }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("ArlesGestion", "Error renderizando formulario Taller", e)
            val root = baseScreen("$tipoMovimiento temporal", "No se pudo cargar el formulario.", backAction = { showTallerSubmoduloMenu(subModulo) })
            root.addView(infoText("Error: ${e.localizedMessage ?: "fallo inesperado"}"))
            root.addView(primaryButton("Volver a Taller") { showTallerSubmoduloMenu(subModulo) })
        }
    }

internal fun MainActivity.showTrasladoBodegaRojaForm(
    herramientaIdPreseleccionada: Long = -1L,
    sincronizar: Boolean = true,
) {
    currentScreenRenderer = {
        showTrasladoBodegaRojaForm(herramientaIdPreseleccionada, sincronizar)
    }
    if (sincronizar) {
        baseScreen("Traslado a submódulo", "Sincronizando inventario de Bodega Roja...")
        prepararInventarioTaller {
            if (isFinishing || isDestroyed) return@prepararInventarioTaller
            renderTrasladoBodegaRojaForm(herramientaIdPreseleccionada)
        }
    } else {
        renderTrasladoBodegaRojaForm(herramientaIdPreseleccionada)
    }
}

internal fun MainActivity.renderTrasladoBodegaRojaForm(
    herramientaIdPreseleccionada: Long = -1L,
) {
    if (isFinishing || isDestroyed) return
    val subModulo = TallerCanonicos.SUBMODULO_BODEGA_ROJA
    val accent = tallerSubmoduloAccent(subModulo)
    val titulo = "Traslado a submódulo"
    try {
        val herramientasBase = herramientasTallerFiltradas(subModulo)
            .filter { it.disponibles() > 0.0 && it.ocupados() <= 0.0 }
            .sortedWith(compareBy<Herramienta> { it.categoriaTaller() }.thenBy { it.nombre })

        val root = baseScreen(
            titulo,
            "Bodega Roja · envío hacia otro submódulo y categoría",
            backAction = { showTallerSubmoduloMenu(subModulo) },
        )

        if (herramientasBase.isEmpty()) {
            root.addView(
                tallerEmptyState(
                    "Sin stock en bodega",
                    "No hay herramientas guardadas en Bodega Roja para trasladar.",
                ),
            )
            return
        }

        root.addView(
            moduleHeroBanner(
                R.drawable.ic_door_open,
                titulo,
                "${herramientasBase.size} ítems disponibles para traslado",
                accent,
            ),
        )

        var herramientasVisibles = herramientasBase.toMutableList()
        val destinosSubmodulo = TallerCanonicos.submodulosTrasladoDestino()
        var destinoSeleccionado = destinosSubmodulo.firstOrNull().orEmpty()
        var categoriaSeleccionada = "Sin categoría"

        lateinit var codigoBusqueda: AutoCompleteTextView
        lateinit var herramienta: Spinner
        lateinit var destinoSubmodulo: Spinner
        lateinit var categoriaDestino: Spinner
        lateinit var categoriaLibre: EditText
        lateinit var stockInfo: TextView
        lateinit var cantidad: EditText
        lateinit var solicitante: EditText
        lateinit var motivo: EditText
        lateinit var observaciones: EditText
        var cantidadLista = false

        fun opcionesSpinner(): List<String> = herramientasVisibles.map { it.etiquetaTrasladoBodega() }
        fun etiquetasQr(): List<String> = herramientasVisibles.map { it.etiquetaBusquedaQr() }.distinct()
        fun categoriasDestino(sub: String): List<String> {
            val base = listOf("Sin categoría") +
                herramientasTallerFiltradas(sub).map { it.categoriaTaller() }.distinct().sorted()
            return base.distinct()
        }

        fun herramientaSeleccionada(): Herramienta {
            val pos = herramienta.selectedItemPosition
            return herramientasVisibles[pos.coerceIn(0, herramientasVisibles.lastIndex)]
        }

        fun actualizarStockInfo() {
            if (herramientasVisibles.isEmpty()) return
            val h = herramientaSeleccionada()
            stockInfo.renderStockSummary(
                "Traslado · ${h.categoriaTaller()}",
                listOf(
                    "Origen" to h.subModulo,
                    "Destino" to destinoSeleccionado,
                    "Categoría destino" to categoriaSeleccionada,
                    "Disponibles" to formatoCantidadTaller(h.disponibles()),
                    "Tipo/tamaño" to h.detalleTaller().ifBlank { "-" },
                ),
                highlightKey = "Disponibles",
                positiveColor = ArlesPalette.green800,
                emptyColor = ArlesPalette.danger,
            )
            if (cantidadLista && cantidad.text.isBlank()) {
                cantidad.setText(formatoCantidadTaller(h.disponibles()))
            }
        }

        fun refrescarCategoriaDestino() {
            val categorias = categoriasDestino(destinoSeleccionado)
            categoriaDestino.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                categorias,
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            categoriaDestino.setSelection(0)
            categoriaSeleccionada = categorias.firstOrNull().orEmpty()
        }

        fun refrescarSpinnerHerramientas() {
            herramienta.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                opcionesSpinner(),
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            codigoBusqueda.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, etiquetasQr()),
            )
            if (herramientasVisibles.isNotEmpty()) {
                herramienta.setSelection(0)
                actualizarStockInfo()
            }
        }

        fun seleccionarHerramientaPorCodigo(texto: String) {
            val encontrada = buscarHerramientaTallerPorCodigo(texto) ?: return
            if (!TallerCanonicos.esBodegaRojaTaller(encontrada.subModulo)) {
                Toast.makeText(this, "El ítem no está en Bodega Roja", Toast.LENGTH_SHORT).show()
                return
            }
            if (encontrada.disponibles() <= 0.0) {
                Toast.makeText(this, "Sin stock disponible para mover", Toast.LENGTH_SHORT).show()
                return
            }
            if (herramientasVisibles.none { it.id == encontrada.id }) {
                herramientasVisibles = (herramientasVisibles + encontrada)
                    .distinctBy { it.id }
                    .sortedWith(compareBy<Herramienta> { it.categoriaTaller() }.thenBy { it.nombre })
                    .toMutableList()
                refrescarSpinnerHerramientas()
            }
            val index = herramientasVisibles.indexOfFirst { it.id == encontrada.id }
            if (index >= 0) {
                herramienta.setSelection(index)
                actualizarStockInfo()
            }
        }

        formSectionCard(
            root,
            "1",
            "Ítem en bodega",
            "Selecciona o escanea la herramienta a trasladar",
            accent,
        ) { section ->
            section.addView(
                iconTextButton("Escanear QR", R.drawable.ic_scanner, accent) {
                    iniciarEscaneoTallerMovimiento(TallerCanonicos.TIPO_MOV_TRASLADO, subModulo)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48),
                    ).apply { setMargins(0, 0, 0, dp(8)) }
                },
            )
            codigoBusqueda = codigoInternoField(
                section,
                "Código QR",
                "Escanea o escribe el número del QR",
                scan = true,
                modoQrNumerico = true,
            )
            codigoBusqueda.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, etiquetasQr()),
            )
            herramienta = spinner(section, "Ítem en Bodega Roja *", opcionesSpinner())
            stockInfo = stockInfoCard("Selecciona un ítem para ver origen y destino")
            section.addView(stockInfo)
        }

        formSectionCard(
            root,
            "2",
            "Destino del traslado",
            "Submódulo y categoría donde quedará el ítem",
            accent,
        ) { section ->
            destinoSubmodulo = spinner(section, "Submódulo destino *", destinosSubmodulo)
            destinoSubmodulo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    destinoSeleccionado = destinosSubmodulo.getOrNull(position).orEmpty()
                    refrescarCategoriaDestino()
                    actualizarStockInfo()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            categoriaDestino = spinner(section, "Categoría destino *", categoriasDestino(destinosSubmodulo.first()))
            categoriaDestino.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    categoriaSeleccionada = categoriaDestino.selectedItem?.toString().orEmpty()
                    actualizarStockInfo()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            categoriaLibre = field(section, "Categoría personalizada", "Opcional si no está en la lista")
        }

        formSectionCard(root, "3", "Datos del traslado", "Quién solicita y por qué se mueve", accent) { section ->
            cantidad = field(section, "Cantidad *", "Cantidad a trasladar", number = true)
            cantidadLista = true
            solicitante = field(section, "Solicitante *", "Quién pide el cambio o traslado")
            motivo = field(section, "Motivo *", "Ej: soporte, cambio, reserva, reparación")
            observaciones = field(section, "Observaciones", "Opcional")
        }

        herramienta.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                actualizarStockInfo()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        codigoBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                seleccionarHerramientaPorCodigo(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (herramientaIdPreseleccionada >= 0) {
            val preseleccionada = herramientasTallerActivas().firstOrNull { it.id == herramientaIdPreseleccionada }
            if (preseleccionada != null) {
                val numeroQr = TallerCanonicos.codigoQrDesdeCodigoPrincipal(
                    preseleccionada.codigo,
                    preseleccionada.codigoQr,
                )
                if (numeroQr.isNotBlank()) {
                    codigoBusqueda.setText(numeroQr)
                    seleccionarHerramientaPorCodigo(numeroQr)
                } else {
                    val posPre = herramientasVisibles.indexOfFirst { it.id == herramientaIdPreseleccionada }
                    if (posPre >= 0) herramienta.setSelection(posPre, false)
                }
            }
        }
        actualizarStockInfo()

        root.addView(
            styledButton("Confirmar traslado", ArlesButtonStyle.TALLER) {
                if (destinoSeleccionado.isBlank()) {
                    Toast.makeText(this, "Selecciona submódulo destino", Toast.LENGTH_SHORT).show()
                    return@styledButton
                }
                if (!required(cantidad, solicitante, motivo)) return@styledButton
                val selectedTool = herramientaSeleccionada()
                val cant = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 1.0
                if (cant <= 0.0) {
                    Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                    return@styledButton
                }
                if (cant > selectedTool.disponibles()) {
                    Toast.makeText(
                        this,
                        "Solo hay ${formatoCantidadTaller(selectedTool.disponibles())} disponibles",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@styledButton
                }
                val categoriaFinal = categoriaLibre.text.toString().trim()
                    .ifBlank { categoriaSeleccionada }
                    .ifBlank { selectedTool.subcategoria }
                registrarTrasladoBodegaRoja(
                    herramienta = selectedTool,
                    destinoSubmodulo = destinoSeleccionado,
                    categoriaDestino = categoriaFinal,
                    solicitante = solicitante.text.toString(),
                    motivo = motivo.text.toString(),
                    observaciones = observaciones.text.toString(),
                    cantidad = cant.toString(),
                )
            },
        )
    } catch (e: Exception) {
        android.util.Log.e("ArlesGestion", "Error renderizando traslado Bodega Roja", e)
        val root = baseScreen(titulo, "No se pudo cargar el formulario.", backAction = { showTallerSubmoduloMenu(subModulo) })
        root.addView(infoText("Error: ${e.localizedMessage ?: "fallo inesperado"}"))
        root.addView(primaryButton("Volver") { showTallerSubmoduloMenu(subModulo) })
    }
}

internal fun MainActivity.sincronizarHerramientasDesdeNube(onDone: () -> Unit) {
    firestore.collection("herramientas").get().addOnSuccessListener { snapshot ->
        var nuevas = 0
        var actualizadas = 0
        val conteoSubmodulos = linkedMapOf<String, Int>()
        for (doc in snapshot.documents) {
            val moduloRaw = docTexto(doc, "modulo")
            val submoduloRaw = docTexto(doc, "submodulo_taller", "submodulo", "seccion", "categoria", "ubicacion")
            val nombre = docTexto(doc, "nombre", "item", "producto", "descripcion")
            if (nombre.isBlank()) continue
            if (moduloRaw.isNotBlank() && !TallerCanonicos.esModuloTaller(moduloRaw)) continue

            val codigo = docTexto(doc, "codigo_principal", "codigo", "codigo_qr", "codigo_interno", "clave").ifBlank { doc.id }
            if (esCodigoTallerRetirado(codigo)) {
                db.eliminarHerramientaPorCodigo(codigo)
                db.eliminarHerramientaPorCodigo(TallerCanonicos.normalizarCodigo(codigo))
                continue
            }
            val h = herramientaDesdeDocumentoFirestore(doc) ?: continue
            val subFinal = h.subModulo.ifBlank { TallerCanonicos.normalizarSubmoduloTaller(submoduloRaw) }
            val herramienta = h.copy(subModulo = subFinal)
            val idLocal = db.buscarHerramientaPorCodigoNormalizado(herramienta.codigo)
                ?: db.buscarHerramientaId(herramienta.nombre, herramienta.codigo)
            if (idLocal == null) {
                db.insertarHerramienta(herramienta)
                nuevas++
            } else {
                val local = db.obtenerHerramientaPorId(idLocal)
                // Cloud (herramienta from Firestore) is authoritative for shared state like ocupada,
                // because movements from any device update the cloud document with the latest ocupación.
                // We prefer cloud values for cantidadOcupada / estado / responsable so that
                // other users see the prestamos (salidas) done by colleagues.
                val merged = herramienta.copy(
                    id = idLocal,
                    estado = if (herramienta.estado.isNotBlank()) herramienta.estado else (local?.estado ?: "Disponible"),
                    responsable = if (herramienta.responsable.isNotBlank()) herramienta.responsable else (local?.responsable ?: ""),
                    cantidadOcupada = herramienta.cantidadOcupada,
                )
                db.actualizarHerramientaCanonica(merged)
                db.actualizarOcupacionHerramienta(idLocal, merged.cantidadOcupada, merged.responsable)
                actualizadas++
            }
            conteoSubmodulos[subFinal] = (conteoSubmodulos[subFinal] ?: 0) + 1
        }
        android.util.Log.d(
            "ArlesGestión",
            "SYNC herramientas: nuevas=$nuevas actualizadas=$actualizadas submodulos=$conteoSubmodulos"
        )
        onDone()
    }.addOnFailureListener { error ->
        android.util.Log.e("ArlesGestión", "SYNC herramientas falló", error)
        Toast.makeText(this, "No se pudo descargar inventario. Revisa conexion y permisos.", Toast.LENGTH_SHORT).show()
        onDone()
    }
}

internal fun etiquetaTabSubmoduloTaller(subModulo: String): String {
    return when (subModulo) {
        "TODOS" -> "TODOS"
        TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER -> "H. TALLER"
        "EQUIPOS COSECHA" -> "COSECHA"
        "HERRAMIENTAS MECANICAS" -> "MECANICAS"
        "VEHICULOS" -> "VEHICULOS"
        "IMPLEMENTOS AGRICOLAS" -> "IMPLEMENTOS"
        "BODEGA ROJA" -> "BODEGA ROJA"
        else -> subModulo.take(16)
    }
}

internal fun textoBusquedaHerramientaTaller(h: Herramienta): String {
    return normalizarBusqueda(
        listOf(
            h.codigo,
            h.codigoQr,
            h.nombre,
            h.subModulo,
            h.subcategoria,
            h.tipo,
            h.tamano,
            h.marca,
            h.referencia,
            h.responsable,
            h.detalleTaller(),
        ).joinToString(" "),
    )
}

internal fun MainActivity.showHerramientasRegistradas(subModulo: String = "") {
    val filtroInicial = subModulo.trim()
    currentScreenRenderer = { showHerramientasRegistradas(filtroInicial) }
    baseScreen("Inventario Taller", "Sincronizando inventario...")
    prepararInventarioTaller {
        renderInventarioTallerPlanilla(filtroInicial)
    }
}

internal fun MainActivity.renderInventarioTallerPlanilla(filtroInicial: String = "") {
    val activity = this
    val tabs = listOf("TODOS") + TallerCanonicos.SUBMODULOS
    var subModuloSeleccionado = filtroInicial.ifBlank { "TODOS" }
    var soloOcupados = false
    if (subModuloSeleccionado != "TODOS" && !TallerCanonicos.SUBMODULOS.contains(subModuloSeleccionado)) {
        subModuloSeleccionado = TallerCanonicos.normalizarSubmoduloTaller(subModuloSeleccionado)
    }

    val root = baseScreen(
        "Inventario Taller",
        "Vista en tarjetas con filtros por submódulo",
        backAction = {
            if (filtroInicial.isBlank()) showHerramientasMenu() else showTallerSubmoduloMenu(filtroInicial)
        },
    )
    currentScreenBackAction = {
        if (filtroInicial.isBlank()) showHerramientasMenu() else showTallerSubmoduloMenu(filtroInicial)
    }

    root.addView(
        moduleHeroBanner(
            R.drawable.ic_tools,
            "Stock del taller",
            if (filtroInicial.isBlank()) "Todos los submódulos" else etiquetaTabSubmoduloTaller(filtroInicial),
            if (filtroInicial.isBlank()) ArlesPalette.accentTaller else tallerSubmoduloAccent(filtroInicial),
        ),
    )

    val tabsScroll = HorizontalScrollView(this).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
    }
    val tabsLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    tabsScroll.addView(tabsLayout)
    root.addView(tabsScroll)

    val searchLabel = sectionHeader("Buscar y filtrar", "Código, herramienta, tipo o subcategoría")
    root.addView(searchLabel)
    val search = tallerSearchField("Escribe para filtrar el inventario...")
    root.addView(search)

    val filtrosRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), 0, dp(16), dp(8))
    }
    val btnSoloOcupados = Button(this).apply {
        text = "Solo ocupados"
        textSize = 11f
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
    }
    filtrosRow.addView(btnSoloOcupados)
    root.addView(filtrosRow)

    val resumenLabel = tallerResumenBar("Cargando inventario...")
    root.addView(resumenLabel)

    val listContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(12))
    }
    root.addView(listContainer)

    fun herramientasVisibles(): List<Herramienta> {
        val base = if (subModuloSeleccionado == "TODOS") {
            herramientasTallerActivas()
        } else {
            herramientasTallerFiltradas(subModuloSeleccionado)
        }
        val query = normalizarBusqueda(search.text.toString())
        return base
            .asSequence()
            .filter { h ->
                !soloOcupados || h.ocupados() > 0.0 || h.estado.equals("En uso", ignoreCase = true)
            }
            .filter { h ->
                query.isBlank() || textoBusquedaHerramientaTaller(h).contains(query)
            }
            .sortedWith(
                compareBy<Herramienta> { it.subModulo }
                    .thenBy { it.subcategoria }
                    .thenBy { it.tipo }
                    .thenBy { it.tamano }
                    .thenBy { it.nombre },
            )
            .toList()
    }

    fun detalleHerramientaDialogo(h: Herramienta): String {
        return "Producto: ${h.nombre}\n" +
            "Submódulo: ${h.subModulo}\n" +
            "Subcategoría: ${h.subcategoria.ifBlank { "-" }}\n" +
            "Tipo/tamaño: ${h.detalleTaller().ifBlank { "-" }}\n" +
            h.textoDisponibilidad() + "\n" +
            "QR: ${h.codigoQr.ifBlank { "-" }}\n" +
            "Requiere asignar QR: ${if (h.requiereAsignarQr) "Sí" else "No"}\n" +
            "Responsable actual: ${h.responsable.ifBlank { "Bodega" }}\n" +
            "Observaciones: ${h.observaciones.ifBlank { "Sin notas" }}"
    }

    fun agregarTarjetaHerramienta(h: Herramienta, mostrarSubmodulo: Boolean) {
        val codigo = h.codigo.ifBlank { h.codigoQr.ifBlank { "-" } }
        val detalle = h.detalleTaller().ifBlank { h.subcategoria.ifBlank { "-" } }
        val unidad = h.unidad.ifBlank { "UNIDAD" }
        val enPrestamo = h.ocupados() > 0.0 || h.estado.equals("En uso", ignoreCase = true)
        val accent = tallerSubmoduloAccent(h.subModulo)
        listContainer.addView(
            tallerInventarioCard(
                codigo = codigo,
                nombre = h.nombre,
                detalle = detalle,
                submodulo = if (mostrarSubmodulo) h.subModulo else null,
                disponibles = formatoCantidadTaller(h.disponibles()),
                ocupados = formatoCantidadTaller(h.ocupados()),
                total = formatoCantidadTaller(h.cantidadTotal),
                unidad = unidad,
                accent = accent,
                enPrestamo = enPrestamo,
            ) {
                AlertDialog.Builder(activity)
                    .setTitle("Detalle del ítem")
                    .setMessage(detalleHerramientaDialogo(h))
                    .setPositiveButton("Cerrar", null)
                    .show()
            },
        )
    }

    fun refrescarTabla() {
        val mostrarSubmodulo = subModuloSeleccionado == "TODOS"
        val items = herramientasVisibles()
        listContainer.removeAllViews()

        if (items.isEmpty()) {
            val prefijoFiltro = buildString {
                append(etiquetaTabSubmoduloTaller(subModuloSeleccionado))
                if (soloOcupados) append(" · Solo ocupados")
            }
            resumenLabel.text = "$prefijoFiltro: sin registros"
            listContainer.addView(
                tallerEmptyState(
                    "Sin resultados",
                    if (soloOcupados) {
                        "No hay herramientas ocupadas con el filtro actual."
                    } else {
                        "No hay herramientas que coincidan con la búsqueda."
                    },
                ),
            )
            return
        }

        val (visibleItems, ocultos) = RenderLimiter.applyLimit(items, performanceConfig.inventoryRenderLimit())
        var totalUnidades = 0.0
        var totalDisponibles = 0.0
        var totalOcupados = 0.0
        items.forEach { h ->
            totalUnidades += h.cantidadTotal
            totalDisponibles += h.disponibles()
            totalOcupados += h.ocupados()
        }
        visibleItems.forEach { h ->
            agregarTarjetaHerramienta(h, mostrarSubmodulo)
        }
        if (ocultos > 0) {
            avisoLimiteRender(listContainer, ocultos)
        }

        val etiqueta = buildString {
            append(etiquetaTabSubmoduloTaller(subModuloSeleccionado))
            if (soloOcupados) append(" · Solo ocupados")
        }
        val ocultosTxt = if (ocultos > 0) " · +$ocultos ocultos" else ""
        resumenLabel.text = "$etiqueta · ${items.size} ítems · Total ${formatoCantidadTaller(totalUnidades)} · Disp. ${formatoCantidadTaller(totalDisponibles)} · Ocup. ${formatoCantidadTaller(totalOcupados)}$ocultosTxt"
    }

    fun actualizarEstiloBotonOcupados() {
        val accent = if (subModuloSeleccionado == "TODOS") {
            ArlesPalette.warning
        } else {
            tallerSubmoduloAccent(subModuloSeleccionado)
        }
        aplicarEstiloChipTaller(btnSoloOcupados, soloOcupados, accent)
    }

    fun actualizarEstilosTabs() {
        for (i in 0 until tabsLayout.childCount) {
            val btn = tabsLayout.getChildAt(i) as? Button ?: continue
            val valor = btn.tag as? String ?: continue
            val accent = if (valor == "TODOS") ArlesPalette.accentTaller else tallerSubmoduloAccent(valor)
            aplicarEstiloChipTaller(btn, valor == subModuloSeleccionado, accent)
        }
    }

    tabs.forEach { tab ->
        val btn = Button(this).apply {
            text = etiquetaTabSubmoduloTaller(tab)
            tag = tab
            textSize = 11f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            setOnClickListener {
                subModuloSeleccionado = tab
                actualizarEstilosTabs()
                refrescarTabla()
            }
        }
        tabsLayout.addView(btn)
    }
    actualizarEstilosTabs()
    actualizarEstiloBotonOcupados()
    btnSoloOcupados.setOnClickListener {
        soloOcupados = !soloOcupados
        actualizarEstiloBotonOcupados()
        refrescarTabla()
    }

    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            refrescarTabla()
        }
        override fun afterTextChanged(s: Editable?) {}
    })

    refrescarTabla()
}

internal fun MainActivity.showEditarCodigoDialog(documentoId: String, nombreProducto: String, codigoActual: String) {
        val input = EditText(this).apply {
            hint = "Ej: E1-P2-05 (Estante-Piso-Art)"
            setText(codigoActual)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        AlertDialog.Builder(this)
            .setTitle("Asignar Código de Ubicación")
            .setMessage("Producto: $nombreProducto\nDocumento: $documentoId\nIngresa el código interno o ubicación.")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoCodigo = input.text.toString().trim()
                firestore.collection("existencias").document(documentoId)
                    .update("codigo_interno", nuevoCodigo)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Código actualizado con éxito", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
