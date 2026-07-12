@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused")

package com.arlessas.gestion

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import java.util.Locale

private data class LubricanteEntregaLinea(
    val id: Long = System.nanoTime(),
    val categoria: String,
    val subcategoria: String,
    val producto: String,
    val codigoInterno: String,
    val codigoOriginal: String,
    val documentoId: String,
    val ubicacion: String,
    val cantidad: Double,
    val unidad: String,
    val stockDisponible: Double,
) {
    fun clave(): String = documentoId
}

private data class LubricanteResultadoSalida(
    val documentoId: String,
    val producto: String,
    val stockAnterior: Double,
    val stockNuevo: Double,
)

private data class LubricanteProductoMeta(
    val documentoId: String,
    val codigoOriginal: String,
    val categoria: String,
    val subcategoria: String,
    val producto: String,
    val ubicacion: String,
    val unidad: String,
)

private val lubricanteEntregaLineas = mutableListOf<LubricanteEntregaLinea>()
private val lubricanteProductosPersonalizados = linkedMapOf<String, LubricanteProductoMeta>()
private var lubricanteEntregaEditandoId: Long? = null
private var lubricanteEntregaProcesando = false
private var lubricanteDraftCategoria = ""
private var lubricanteDraftSubcategoria = ""
private var lubricanteDraftProducto = ""
private var lubricanteDraftCodigo = ""
private var lubricanteDraftDocumento = ""
private var lubricanteDraftUbicacion = ""
private var lubricanteDraftCantidad = "1"
private var lubricanteDraftSolicitante = ""
private var lubricanteDraftLabor = ""
private var lubricanteDraftObservaciones = ""
private var lubricanteDraftUrlEvidencia = ""
private var lubricanteDraftUriEvidencia = ""
private var lubricantePersonalizadosCargados = false
private var lubricantePersonalizadosCargando = false

private fun esDocumentoLubricanteTaller(modulo: String, categoria: String, ubicacion: String): Boolean {
    if (ModulosInventario.moduloQuimico(categoria, ubicacion) != ModulosInventario.LUBRICANTES_TALLER) return false
    return ModulosInventario.esModuloLubricantesTaller(modulo) ||
        normalizarBusqueda(modulo) == normalizarBusqueda(ModulosInventario.QUIMICO_LEGACY)
}

private fun MainActivity.registrarLubricanteEnCatalogoLocal(producto: LubricanteProductoMeta) {
    if (ModulosInventario.moduloQuimico(producto.categoria, producto.ubicacion) != ModulosInventario.LUBRICANTES_TALLER) return
    lubricanteProductosPersonalizados[producto.documentoId] = producto
    val modulo = catalogoCargado.getOrPut(ModulosInventario.LUBRICANTES_TALLER) { mutableMapOf() }
    val categoria = modulo.getOrPut(producto.categoria) { mutableMapOf() }
    val productos = categoria.getOrPut(producto.subcategoria) { mutableListOf() }
    if (productos.none { normalizarBusqueda(it) == normalizarBusqueda(producto.producto) }) {
        productos.add(producto.producto)
    }
}

private fun MainActivity.lubricanteMetaDesdeDocumento(doc: DocumentSnapshot): LubricanteProductoMeta? {
    if (!doc.exists() || doc.getBoolean("activo") == false) return null
    val categoria = doc.getString("categoria").orEmpty().trim()
    val ubicacion = ubicacionExistencia(doc).trim()
    val modulo = doc.getString("modulo").orEmpty()
    if (!esDocumentoLubricanteTaller(modulo, categoria, ubicacion)) return null
    val subcategoria = subcategoriaExistencia(doc).trim().ifBlank { "General" }
    val producto = nombreItemExistencia(doc).trim()
    val codigo = codigoOriginalExistencia(doc).trim()
    val unidad = doc.getString("unidad").orEmpty().trim()
    if (categoria.isBlank() || producto.isBlank() || codigo.isBlank() || ubicacion.isBlank() || unidad.isBlank()) return null
    return LubricanteProductoMeta(doc.id, codigo, categoria, subcategoria, producto, ubicacion, unidad)
}

private fun MainActivity.cargarLubricantePersonalizados() {
    if (lubricantePersonalizadosCargados || lubricantePersonalizadosCargando || !isNetworkAvailable()) return
    lubricantePersonalizadosCargando = true
    firestore.collection("existencias")
        .whereEqualTo("modulo", ModulosInventario.LUBRICANTES_TALLER)
        .limit(500)
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.mapNotNull { lubricanteMetaDesdeDocumento(it) }.forEach { registrarLubricanteEnCatalogoLocal(it) }
            lubricantePersonalizadosCargando = false
            lubricantePersonalizadosCargados = true
            if (currentScreenId.contains("lubricante", ignoreCase = true)) currentScreenRenderer?.invoke()
        }
        .addOnFailureListener {
            lubricantePersonalizadosCargando = false
        }
}

private fun limpiarEstadoEntregaLubricante() {
    lubricanteEntregaLineas.clear()
    lubricanteEntregaEditandoId = null
    lubricanteEntregaProcesando = false
    lubricanteDraftCategoria = ""
    lubricanteDraftSubcategoria = ""
    lubricanteDraftProducto = ""
    lubricanteDraftCodigo = ""
    lubricanteDraftDocumento = ""
    lubricanteDraftUbicacion = ""
    lubricanteDraftCantidad = "1"
    lubricanteDraftSolicitante = ""
    lubricanteDraftLabor = ""
    lubricanteDraftObservaciones = ""
    lubricanteDraftUrlEvidencia = ""
    lubricanteDraftUriEvidencia = ""
}

private fun seleccionLubricanteValida(valor: String): Boolean {
    val normalizado = normalizarBusqueda(valor)
    return valor.isNotBlank() &&
        !normalizado.startsWith("selecciona") &&
        !normalizado.startsWith("sin-") &&
        !normalizado.startsWith("no-hay")
}

internal fun MainActivity.showLubricantesMultipleInterno(
    pItem: String = "",
    pCant: String = "",
    pSol: String = "",
    pCat: String = "",
    pRef: String = "",
) {
    cargarLubricantePersonalizados()
    if (pCat.isNotBlank()) lubricanteDraftCategoria = pCat
    if (pItem.isNotBlank()) lubricanteDraftSubcategoria = pItem
    if (pRef.isNotBlank()) lubricanteDraftProducto = pRef
    if (pCant.isNotBlank()) lubricanteDraftCantidad = pCant
    if (pSol.isNotBlank()) lubricanteDraftSolicitante = pSol

    fun salirLubricante() {
        if (lubricanteEntregaProcesando) {
            Toast.makeText(this, "Espera a que termine el registro de la entrega", Toast.LENGTH_SHORT).show()
            return
        }
        if (lubricanteEntregaLineas.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Descartar entrega")
                .setMessage("Hay lubricantes agregados sin registrar. Si sales, se descartara esta entrega.")
                .setPositiveButton("Descartar entrega") { _, _ ->
                    limpiarEstadoEntregaLubricante()
                    showMainMenu()
                }
                .setNegativeButton("Seguir editando", null)
                .show()
        } else {
            limpiarEstadoEntregaLubricante()
            showMainMenu()
        }
    }

    currentScreenRenderer = {
        showLubricantesTallerForm(
            pItem = lubricanteDraftSubcategoria,
            pCant = lubricanteDraftCantidad,
            pSol = lubricanteDraftSolicitante,
            pCat = lubricanteDraftCategoria,
            pRef = lubricanteDraftProducto,
        )
    }
    val root = baseScreen(
        "Lubricantes taller",
        "Registra consumos de aceites, grasas y fluidos desde su inventario real.",
        backAction = { salirLubricante() },
    )

    fun catalogoLubricante() = catalogoCargado[ModulosInventario.LUBRICANTES_TALLER] ?: mapOf()
    fun categorias(): List<String> = (
        catalogoLubricante().keys +
            QuimicosCanonicos.items.filter { it.modulo == ModulosInventario.LUBRICANTES_TALLER }.map { it.categoria } +
            lubricanteProductosPersonalizados.values.map { it.categoria }
        ).distinctBy { normalizarBusqueda(it) }.sortedBy { normalizarBusqueda(it) }

    fun subcategorias(categoria: String): List<String> {
        val catalogo = (catalogoLubricante()[categoria] as? Map<*, *>)?.keys?.map { it.toString() }.orEmpty()
        return (catalogo +
            QuimicosCanonicos.items.filter {
                it.modulo == ModulosInventario.LUBRICANTES_TALLER && normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria)
            }.map { it.subcategoria } +
            lubricanteProductosPersonalizados.values.filter {
                normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria)
            }.map { it.subcategoria })
            .filterNot { esEntradaCatalogoSulcamagVieja(it) }
            .distinctBy { normalizarBusqueda(it) }
            .sortedBy { normalizarBusqueda(it) }
    }

    fun productos(categoria: String, subcategoria: String): List<String> {
        val catalogo = ((catalogoLubricante()[categoria] as? Map<*, *>)?.get(subcategoria) as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }.orEmpty()
        return (catalogo +
            QuimicosCanonicos.items.filter {
                it.modulo == ModulosInventario.LUBRICANTES_TALLER &&
                    normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                    normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria)
            }.map { it.item } +
            lubricanteProductosPersonalizados.values.filter {
                normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                    normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria)
            }.map { it.producto })
            .filter { it.isNotBlank() }
            .distinctBy { normalizarBusqueda(it) }
            .sortedBy { normalizarBusqueda(it) }
    }

    fun catalogoComoMapAny(): Map<String, Any> {
        val combinado = linkedMapOf<String, Any>()
        categorias().forEach { categoria ->
            val subcategoriasMap = linkedMapOf<String, Any>()
            subcategorias(categoria).forEach { subcategoria ->
                subcategoriasMap[subcategoria] = productos(categoria, subcategoria)
            }
            combinado[categoria] = subcategoriasMap
        }
        return combinado
    }

    val categoriaSpinner = spinner(root, "Categoria *", categorias().ifEmpty { listOf("Sin categorias") })
    val subcategoriaSpinner = spinner(root, "Subcategoria / uso *", listOf("Selecciona categoria"))
    val productoSpinner = spinner(root, "Producto *", listOf("Selecciona subcategoria"))
    val codigoInterno = codigoInternoField(root, "Codigo", "Escribe o selecciona un producto", scan = false)

    val unidadLabel = TextView(this).apply {
        text = "Unidad: selecciona un producto"
        textSize = 13f
        setTextColor(gris)
        setPadding(0, dp(4), 0, dp(2))
    }
    root.addView(unidadLabel)
    val ubicacionSpinner = spinner(root, "Documento / ubicacion *", listOf("Selecciona un producto"))
    val stockLabel = TextView(this).apply {
        text = "Selecciona un producto para ver ubicacion y stock"
        textSize = 13f
        setTextColor(verdeOscuro)
        setPadding(0, dp(4), 0, dp(6))
    }
    root.addView(stockLabel)

    var codigoOriginalSeleccionado = ""
    var codigoInternoSeleccionado = ""
    var documentoSeleccionado = ""
    var ubicacionesActuales = listOf<QuimicoUbicacionStock>()
    var ubicacionSeleccionada: QuimicoUbicacionStock? = null
    var stockVerificado = false
    var tokenStockVerificado = ""
    var consultaStockActual = 0
    var eventosSpinnerActivos = false
    var actualizandoSpinners = false
    var actualizandoUbicaciones = false
    var vistaEntrada: View? = null

    fun escribirCodigoSinBusqueda(codigo: String) {
        val actual = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
        val nuevo = normalizarCodigoInterno(codigo)
        if (actual == nuevo) return
        codigoInterno.tag = "auto_fill"
        try {
            codigoInterno.setText(codigo, false)
            codigoInterno.setSelection(codigoInterno.text?.length ?: 0)
        } finally {
            codigoInterno.tag = null
        }
    }

    fun tokenProducto(codigo: String = codigoInterno.text?.toString().orEmpty()): String = listOf(
        categoriaSpinner.selectedItem?.toString().orEmpty(),
        subcategoriaSpinner.selectedItem?.toString().orEmpty(),
        productoSpinner.selectedItem?.toString().orEmpty(),
        normalizarCodigoInterno(codigo),
    ).joinToString("|") { normalizarBusqueda(it) }

    fun tokenSeleccion(): String = "${tokenProducto()}|${normalizarBusqueda(ubicacionSeleccionada?.documentoId.orEmpty())}"

    fun etiquetaUbicacion(opcion: QuimicoUbicacionStock): String =
        "${opcion.ubicacion} - ${opcion.documentoId} - ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}"

    fun poblarUbicaciones(opciones: List<QuimicoUbicacionStock>, documentoPreferido: String = "") {
        actualizandoUbicaciones = true
        ubicacionSpinner.tag = "SINCRO"
        val ordenadas = opciones.sortedWith(
            compareByDescending<QuimicoUbicacionStock> {
                normalizarBusqueda(it.ubicacion) == normalizarBusqueda("CASETA DE LUBRICANTES")
            }.thenBy { normalizarBusqueda(it.ubicacion) }.thenBy { it.documentoId }
        )
        ubicacionesActuales = ordenadas
        val etiquetas = ordenadas.map(::etiquetaUbicacion).ifEmpty { listOf("Sin inventario disponible") }
        ubicacionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, etiquetas).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val posicion = ordenadas.indexOfFirst { it.documentoId == documentoPreferido }.takeIf { it >= 0 } ?: 0
        ubicacionSpinner.setSelection(posicion, false)
        actualizandoUbicaciones = false
        ubicacionSpinner.post { if (ubicacionSpinner.tag == "SINCRO") ubicacionSpinner.tag = null }
    }

    fun limpiarSeleccionStock() {
        codigoOriginalSeleccionado = ""
        codigoInternoSeleccionado = ""
        documentoSeleccionado = ""
        lubricanteDraftCodigo = ""
        lubricanteDraftDocumento = ""
        lubricanteDraftUbicacion = ""
        ubicacionesActuales = emptyList()
        ubicacionSeleccionada = null
        poblarUbicaciones(emptyList())
        stockVerificado = false
        tokenStockVerificado = ""
        unidadLabel.text = "Unidad: selecciona un producto"
    }

    fun actualizarResumen() {
        val seleccion = ubicacionSeleccionada
        stockLabel.text = if (seleccion == null) {
            "Stock no disponible"
        } else {
            "Disponible en ${seleccion.ubicacion}: ${cantidadTexto(seleccion.cantidad)} ${seleccion.unidad}\nDocumento: ${seleccion.documentoId}"
        }
        stockLabel.setTextColor(if ((seleccion?.cantidad ?: 0.0) <= 0.0) Color.RED else verdeOscuro)
        ubicacionSpinner.isEnabled = ubicacionesActuales.isNotEmpty()
        ubicacionSpinner.alpha = if (ubicacionesActuales.isNotEmpty()) 1f else 0.55f
        vistaEntrada?.let { vista ->
            val habilitada = seleccion != null
            vista.isEnabled = habilitada
            vista.alpha = if (habilitada) 1f else 0.55f
        }
    }

    fun seleccionarUbicacion(opcion: QuimicoUbicacionStock?) {
        if (opcion != null &&
            ModulosInventario.moduloQuimico(opcion.categoria, opcion.ubicacion) != ModulosInventario.LUBRICANTES_TALLER
        ) {
            limpiarSeleccionStock()
            actualizarResumen()
            stockLabel.text = "La ubicacion encontrada no pertenece a Lubricantes"
            stockLabel.setTextColor(Color.RED)
            return
        }
        ubicacionSeleccionada = opcion
        if (opcion == null) {
            codigoInternoSeleccionado = ""
            documentoSeleccionado = ""
            stockVerificado = false
            tokenStockVerificado = ""
            unidadLabel.text = "Unidad: selecciona un producto"
            actualizarResumen()
            return
        }

        documentoSeleccionado = opcion.documentoId
        codigoOriginalSeleccionado = opcion.codigoOriginal
        codigoInternoSeleccionado = ""
        stockVerificado = false
        tokenStockVerificado = ""
        stockLabel.text = "Consultando documento exacto..."
        stockLabel.setTextColor(gris)
        val consulta = ++consultaStockActual
        val token = "${tokenProducto(opcion.codigoOriginal)}|${normalizarBusqueda(opcion.documentoId)}"
        firestore.collection("existencias").document(opcion.documentoId).get()
            .addOnSuccessListener { snapshot ->
                val tokenActual = "${tokenProducto(opcion.codigoOriginal)}|${normalizarBusqueda(ubicacionSeleccionada?.documentoId.orEmpty())}"
                if (!pantallaActiva() || consulta != consultaStockActual || token != tokenActual) return@addOnSuccessListener
                val meta = lubricanteMetaDesdeDocumento(snapshot)
                val coincide = meta != null &&
                    snapshot.id == opcion.documentoId &&
                    normalizarBusqueda(meta.categoria) == normalizarBusqueda(categoriaSpinner.selectedItem?.toString().orEmpty()) &&
                    normalizarBusqueda(meta.producto) == normalizarBusqueda(productoSpinner.selectedItem?.toString().orEmpty()) &&
                    normalizarBusqueda(meta.ubicacion) == normalizarBusqueda(opcion.ubicacion) &&
                    normalizarCodigoInterno(meta.codigoOriginal) == normalizarCodigoInterno(opcion.codigoOriginal)
                if (!coincide) {
                    stockVerificado = false
                    actualizarResumen()
                    stockLabel.text = "El documento ya no coincide con el producto seleccionado"
                    stockLabel.setTextColor(Color.RED)
                    return@addOnSuccessListener
                }
                val verificada = opcion.copy(
                    cantidad = numeroDocumento(snapshot, "cantidad", "stock_actual"),
                    unidad = snapshot.getString("unidad").orEmpty().ifBlank { opcion.unidad },
                )
                ubicacionesActuales = ubicacionesActuales.map { if (it.documentoId == verificada.documentoId) verificada else it }
                ubicacionSeleccionada = verificada
                codigoInternoSeleccionado = codigoExistencia(snapshot)
                codigoOriginalSeleccionado = meta!!.codigoOriginal
                documentoSeleccionado = verificada.documentoId
                lubricanteDraftDocumento = verificada.documentoId
                lubricanteDraftCodigo = meta.codigoOriginal
                lubricanteDraftUbicacion = verificada.ubicacion
                escribirCodigoSinBusqueda(meta.codigoOriginal)
                unidadLabel.text = "Unidad: ${verificada.unidad}"
                stockVerificado = true
                tokenStockVerificado = tokenSeleccion()
                actualizarResumen()
            }
            .addOnFailureListener {
                val tokenActual = "${tokenProducto(opcion.codigoOriginal)}|${normalizarBusqueda(ubicacionSeleccionada?.documentoId.orEmpty())}"
                if (consulta != consultaStockActual || token != tokenActual) return@addOnFailureListener
                stockVerificado = false
                actualizarResumen()
                stockLabel.text = "No se pudo verificar el documento exacto"
                stockLabel.setTextColor(Color.RED)
            }
    }

    fun cargarUbicaciones(
        codigoOriginal: String,
        producto: String,
        ubicacionPreferida: String = "",
        documentoPreferido: String = "",
    ) {
        if (!pantallaActiva()) return
        escribirCodigoSinBusqueda(codigoOriginal)
        codigoOriginalSeleccionado = codigoOriginal
        stockVerificado = false
        tokenStockVerificado = ""
        stockLabel.text = "Consultando stock..."
        stockLabel.setTextColor(gris)
        val consulta = ++consultaStockActual
        val token = tokenProducto(codigoOriginal)
        cargarUbicacionesQuimico(codigoOriginal, producto, { opciones ->
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenProducto(codigoOriginal)) return@cargarUbicacionesQuimico
            val opcionesLubricante = opciones.filter {
                ModulosInventario.moduloQuimico(it.categoria, it.ubicacion) == ModulosInventario.LUBRICANTES_TALLER
            }
            val seleccion = opcionesLubricante.find { it.documentoId == documentoPreferido }
                ?: opcionesLubricante.find { it.documentoId == documentoSeleccionado }
                ?: opcionesLubricante.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda(ubicacionPreferida) }
                ?: opcionesLubricante.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda("CASETA DE LUBRICANTES") }
                ?: opcionesLubricante.firstOrNull()
            poblarUbicaciones(opcionesLubricante, seleccion?.documentoId.orEmpty())
            seleccionarUbicacion(seleccion)
        }, {
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenProducto(codigoOriginal)) return@cargarUbicacionesQuimico
            limpiarSeleccionStock()
            stockLabel.text = "Stock no disponible en linea"
            stockLabel.setTextColor(Color.RED)
            actualizarResumen()
        }, moduloFiltro = ModulosInventario.LUBRICANTES_TALLER)
    }

    fun metaPersonalizado(categoria: String, subcategoria: String, producto: String): LubricanteProductoMeta? =
        lubricanteProductosPersonalizados.values.firstOrNull {
            normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria) &&
                normalizarBusqueda(it.producto) == normalizarBusqueda(producto)
        }

    fun aplicarMeta(meta: LubricanteProductoMeta?) {
        if (meta == null) {
            consultaStockActual++
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            actualizarResumen()
            return
        }
        if (ModulosInventario.moduloQuimico(meta.categoria, meta.ubicacion) != ModulosInventario.LUBRICANTES_TALLER) {
            aplicarMeta(null)
            return
        }
        documentoSeleccionado = meta.documentoId
        codigoOriginalSeleccionado = meta.codigoOriginal
        escribirCodigoSinBusqueda(meta.codigoOriginal)
        cargarUbicaciones(meta.codigoOriginal, meta.producto, meta.ubicacion, meta.documentoId)
    }

    fun resolverProductoSeleccionado(documentoPreferido: String = "", ubicacionPreferida: String = "") {
        val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        val subcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty()
        val producto = productoSpinner.selectedItem?.toString().orEmpty()
        if (!seleccionLubricanteValida(categoria) || !seleccionLubricanteValida(subcategoria) || !seleccionLubricanteValida(producto)) return
        val canonico = QuimicosCanonicos.buscar(ModulosInventario.LUBRICANTES_TALLER, categoria, subcategoria, producto)
        if (canonico != null && canonico.modulo == ModulosInventario.LUBRICANTES_TALLER) {
            aplicarMeta(LubricanteProductoMeta(canonico.documentoId, canonico.codigoOriginal, canonico.categoria, canonico.subcategoria, canonico.item, canonico.ubicacion, canonico.unidad))
            return
        }
        val local = lubricanteProductosPersonalizados[documentoPreferido]?.takeIf {
            normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria) &&
                normalizarBusqueda(it.producto) == normalizarBusqueda(producto)
        } ?: metaPersonalizado(categoria, subcategoria, producto)
        if (local != null) {
            aplicarMeta(local.copy(
                ubicacion = ubicacionPreferida.ifBlank { local.ubicacion },
            ))
            return
        }
        val consulta = ++consultaStockActual
        val token = tokenProducto()
        stockLabel.text = "Consultando producto..."
        firestore.collection("existencias").whereEqualTo("item", producto).limit(50).get()
            .addOnSuccessListener { snapshot ->
                if (!pantallaActiva() || consulta != consultaStockActual || token != tokenProducto()) return@addOnSuccessListener
                val metas = snapshot.documents.mapNotNull { lubricanteMetaDesdeDocumento(it) }.filter {
                    normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                        normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria)
                }
                metas.forEach { registrarLubricanteEnCatalogoLocal(it) }
                val meta = metas.find { it.documentoId == documentoPreferido }
                    ?: metas.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda(ubicacionPreferida) }
                    ?: metas.firstOrNull()
                if (meta == null) aplicarMeta(null) else aplicarMeta(meta)
            }
            .addOnFailureListener {
                if (consulta == consultaStockActual && token == tokenProducto()) aplicarMeta(null)
            }
    }

    fun poblarProductos(categoria: String, subcategoria: String, preservar: String = "") {
        actualizandoSpinners = true
        productoSpinner.tag = "SINCRO"
        val opciones = productos(categoria, subcategoria).ifEmpty { listOf("Selecciona subcategoria") }
        productoSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opciones).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val posicion = opciones.indexOfFirst { normalizarBusqueda(it) == normalizarBusqueda(preservar) }
        productoSpinner.setSelection(if (posicion >= 0) posicion else 0, false)
        actualizandoSpinners = false
        productoSpinner.post { if (productoSpinner.tag == "SINCRO") productoSpinner.tag = null }
    }

    fun poblarSubcategorias(categoria: String, preservarSub: String = "", preservarProducto: String = "") {
        actualizandoSpinners = true
        subcategoriaSpinner.tag = "SINCRO"
        val opciones = subcategorias(categoria).ifEmpty { listOf("Selecciona categoria") }
        subcategoriaSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opciones).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val posicion = opciones.indexOfFirst { normalizarBusqueda(it) == normalizarBusqueda(preservarSub) }
        subcategoriaSpinner.setSelection(if (posicion >= 0) posicion else 0, false)
        actualizandoSpinners = false
        subcategoriaSpinner.post { if (subcategoriaSpinner.tag == "SINCRO") subcategoriaSpinner.tag = null }
        poblarProductos(categoria, subcategoriaSpinner.selectedItem?.toString().orEmpty(), preservarProducto)
    }

    fun seleccionarSpinners(categoria: String, subcategoria: String, producto: String, onDone: () -> Unit = {}) {
        seleccionarProductoEnSpinners(
            categoriaSpinner,
            subcategoriaSpinner,
            productoSpinner,
            catalogoComoMapAny(),
            categoria,
            subcategoria,
            producto,
            onDone,
        )
    }

    ubicacionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (actualizandoUbicaciones || ubicacionSpinner.tag == "SINCRO") return
            val opcion = ubicacionesActuales.getOrNull(p2) ?: return
            seleccionarUbicacion(opcion)
        }

        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    setupCodigoInternoSalida(root, codigoInterno, ModulosInventario.LUBRICANTES_TALLER) { producto ->
        if (!eventosSpinnerActivos) return@setupCodigoInternoSalida
        consultaStockActual++
        if (!esDocumentoLubricanteTaller(producto.modulo, producto.categoria, producto.ubicacion)) {
            aplicarMeta(null)
            Toast.makeText(this, "El codigo no pertenece a Lubricantes taller", Toast.LENGTH_LONG).show()
            return@setupCodigoInternoSalida
        }
        val subcategoria = producto.subcategoria.ifBlank { "General" }
        val meta = LubricanteProductoMeta(
            producto.documentoId.ifBlank { producto.codigoInterno },
            producto.codigoOriginal.ifBlank { producto.codigoInterno },
            producto.categoria,
            subcategoria,
            producto.item,
            producto.ubicacion,
            producto.unidad,
        )
        registrarLubricanteEnCatalogoLocal(meta)
        seleccionarSpinners(meta.categoria, meta.subcategoria, meta.producto) {
            lubricanteDraftCategoria = meta.categoria
            lubricanteDraftSubcategoria = meta.subcategoria
            lubricanteDraftProducto = meta.producto
            aplicarMeta(meta)
        }
    }

    setupSearchBar(root, ModulosInventario.LUBRICANTES_TALLER) { _, categoria, subcategoria, producto ->
        if (!eventosSpinnerActivos) return@setupSearchBar
        consultaStockActual++
        seleccionarSpinners(categoria, subcategoria, producto) {
            lubricanteDraftCategoria = categoria
            lubricanteDraftSubcategoria = subcategoria
            lubricanteDraftProducto = producto
            resolverProductoSeleccionado()
        }
    }

    categoriaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || actualizandoSpinners || categoriaSpinner.tag == "SINCRO") return
            val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
            if (!seleccionLubricanteValida(categoria)) return
            lubricanteDraftCategoria = categoria
            lubricanteDraftSubcategoria = ""
            lubricanteDraftProducto = ""
            consultaStockActual++
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            poblarSubcategorias(categoria)
            actualizarResumen()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    subcategoriaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || actualizandoSpinners || subcategoriaSpinner.tag == "SINCRO") return
            val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
            val subcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty()
            if (!seleccionLubricanteValida(categoria) || !seleccionLubricanteValida(subcategoria)) return
            lubricanteDraftSubcategoria = subcategoria
            lubricanteDraftProducto = ""
            consultaStockActual++
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            poblarProductos(categoria, subcategoria)
            actualizarResumen()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    productoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || actualizandoSpinners || productoSpinner.tag == "SINCRO") return
            val producto = productoSpinner.selectedItem?.toString().orEmpty()
            if (!seleccionLubricanteValida(producto)) return
            lubricanteDraftProducto = producto
            limpiarSeleccionStock()
            resolverProductoSeleccionado()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    root.post {
        val categoriaInicial = lubricanteDraftCategoria.ifBlank { categoriaSpinner.selectedItem?.toString().orEmpty() }
        if (seleccionLubricanteValida(categoriaInicial)) {
            seleccionarSpinners(categoriaInicial, lubricanteDraftSubcategoria, lubricanteDraftProducto) {
                eventosSpinnerActivos = true
                val categoriaActual = categoriaSpinner.selectedItem?.toString().orEmpty()
                val subcategoriaActual = subcategoriaSpinner.selectedItem?.toString().orEmpty()
                val productoActual = productoSpinner.selectedItem?.toString().orEmpty()
                if (seleccionLubricanteValida(categoriaActual) &&
                    seleccionLubricanteValida(subcategoriaActual) &&
                    seleccionLubricanteValida(productoActual)
                ) {
                    lubricanteDraftCategoria = categoriaActual
                    lubricanteDraftSubcategoria = subcategoriaActual
                    lubricanteDraftProducto = productoActual
                    resolverProductoSeleccionado(lubricanteDraftDocumento, lubricanteDraftUbicacion)
                }
            }
        } else {
            eventosSpinnerActivos = true
        }
        actualizarResumen()
    }

    val cantidad = field(root, "Cantidad *", "Ej: 1.5", number = true)
    cantidad.setText(pCant.ifBlank { lubricanteDraftCantidad.ifBlank { "1" } })
    val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
    solicitante.setText(pSol.ifBlank { lubricanteDraftSolicitante })
    val labor = field(root, "Area / equipo / labor *", "Ej: taller, tractor, mantenimiento")
    labor.setText(lubricanteDraftLabor)
    val observaciones = field(root, "Observaciones", "Orden de trabajo, equipo o responsable")
    observaciones.setText(lubricanteDraftObservaciones)
    var urlEvidencia = lubricanteDraftUrlEvidencia
    var uriLocalEvidencia = lubricanteDraftUriEvidencia

    fun guardarDraft() {
        lubricanteDraftCategoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        lubricanteDraftSubcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty().takeIf { seleccionLubricanteValida(it) }.orEmpty()
        lubricanteDraftProducto = productoSpinner.selectedItem?.toString().orEmpty().takeIf { seleccionLubricanteValida(it) }.orEmpty()
        lubricanteDraftCodigo = codigoOriginalSeleccionado
        lubricanteDraftDocumento = documentoSeleccionado
        lubricanteDraftUbicacion = ubicacionSeleccionada?.ubicacion.orEmpty()
        lubricanteDraftCantidad = cantidad.text.toString().ifBlank { "1" }
        lubricanteDraftSolicitante = solicitante.text.toString()
        lubricanteDraftLabor = labor.text.toString()
        lubricanteDraftObservaciones = observaciones.text.toString()
        lubricanteDraftUrlEvidencia = urlEvidencia
        lubricanteDraftUriEvidencia = uriLocalEvidencia
    }

    fun observar(campo: android.widget.EditText, accion: (String) -> Unit) {
        campo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = accion(s?.toString().orEmpty())
        })
    }
    observar(cantidad) { lubricanteDraftCantidad = it.ifBlank { "1" } }
    observar(solicitante) { lubricanteDraftSolicitante = it }
    observar(labor) { lubricanteDraftLabor = it }
    observar(observaciones) { lubricanteDraftObservaciones = it }

    root.addView(evidenceButton {
        capturarEvidencia { uri ->
            uriLocalEvidencia = uri
            urlEvidencia = ""
            lubricanteDraftUriEvidencia = uri
            lubricanteDraftUrlEvidencia = ""
            mostrarPrevisualizacionEvidencia(root, uri) {
                uriLocalEvidencia = ""
                urlEvidencia = ""
                lubricanteDraftUriEvidencia = ""
                lubricanteDraftUrlEvidencia = ""
            }
        }
    })
    if (uriLocalEvidencia.isNotBlank()) {
        mostrarPrevisualizacionEvidencia(root, uriLocalEvidencia) {
            uriLocalEvidencia = ""
            urlEvidencia = ""
            lubricanteDraftUriEvidencia = ""
            lubricanteDraftUrlEvidencia = ""
        }
    }

    val gestionAcciones = gestionNuevoEntradaRow(
        onNuevo = {
            guardarDraft()
            showDialogNuevoLubricanteMultiple { nuevo ->
                registrarLubricanteEnCatalogoLocal(nuevo)
                lubricanteDraftCategoria = nuevo.categoria
                lubricanteDraftSubcategoria = nuevo.subcategoria
                lubricanteDraftProducto = nuevo.producto
                lubricanteDraftCodigo = nuevo.codigoOriginal
                lubricanteDraftDocumento = nuevo.documentoId
                lubricanteDraftUbicacion = nuevo.ubicacion
                showLubricantesTallerForm()
            }
        },
        onEntrada = {
            guardarDraft()
            val seleccion = ubicacionSeleccionada
            if (seleccion == null) {
            Toast.makeText(this, "Selecciona un lubricante y su documento primero", Toast.LENGTH_SHORT).show()
            } else {
                showDialogEntradaStockQuimico(seleccion) {
                    cargarUbicaciones(seleccion.codigoOriginal, seleccion.item, seleccion.ubicacion, seleccion.documentoId)
                }
            }
        },
    )
    vistaEntrada = gestionAcciones.getChildAt(1)
    root.addView(gestionAcciones)
    actualizarResumen()

    fun lineaDesdeFormulario(): LubricanteEntregaLinea? {
        val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        val subcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty()
        val producto = productoSpinner.selectedItem?.toString().orEmpty()
        val seleccion = ubicacionSeleccionada
        if (!seleccionLubricanteValida(categoria) || !seleccionLubricanteValida(subcategoria) || !seleccionLubricanteValida(producto) || seleccion == null) {
            Toast.makeText(this, "Selecciona producto y ubicacion validos", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!stockVerificado || tokenStockVerificado != tokenSeleccion() || documentoSeleccionado != seleccion.documentoId) {
            Toast.makeText(this, "Espera la consulta de stock antes de agregar", Toast.LENGTH_SHORT).show()
            return null
        }
        if (ModulosInventario.moduloQuimico(categoria, seleccion.ubicacion) != ModulosInventario.LUBRICANTES_TALLER) {
            Toast.makeText(this, "El documento seleccionado no pertenece a Lubricantes taller", Toast.LENGTH_LONG).show()
            return null
        }
        if (normalizarBusqueda(seleccion.categoria) != normalizarBusqueda(categoria) ||
            normalizarBusqueda(seleccion.item) != normalizarBusqueda(producto)
        ) {
            Toast.makeText(this, "El documento consultado no coincide con el producto seleccionado", Toast.LENGTH_LONG).show()
            return null
        }
        val codigoEscrito = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
        val codigoOriginal = normalizarCodigoInterno(seleccion.codigoOriginal)
        val codigoDocumento = normalizarCodigoInterno(seleccion.documentoId)
        if (codigoEscrito.isNotBlank() && codigoEscrito != codigoOriginal && codigoEscrito != codigoDocumento) {
            Toast.makeText(this, "El codigo no coincide con el producto seleccionado", Toast.LENGTH_SHORT).show()
            return null
        }
        val cantidadValor = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: Double.NaN
        if (!cantidadValor.isFinite() || cantidadValor <= 0.0) {
            Toast.makeText(this, "La cantidad debe ser un decimal mayor a cero", Toast.LENGTH_SHORT).show()
            return null
        }
        if (cantidadValor > seleccion.cantidad) {
            Toast.makeText(this, "Stock insuficiente. Disponible: ${cantidadTexto(seleccion.cantidad)} ${seleccion.unidad}", Toast.LENGTH_LONG).show()
            return null
        }
        return LubricanteEntregaLinea(
            categoria = categoria,
            subcategoria = subcategoria,
            producto = producto,
            codigoInterno = codigoInternoSeleccionado,
            codigoOriginal = seleccion.codigoOriginal,
            documentoId = seleccion.documentoId,
            ubicacion = seleccion.ubicacion,
            cantidad = cantidadValor,
            unidad = seleccion.unidad,
            stockDisponible = seleccion.cantidad,
        )
    }

    val productosAgregados = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(4))
    }
    var botonAgregar: Button? = null
    var botonRegistrar: Button? = null

    fun actualizarBotones() {
        botonAgregar?.text = if (lubricanteEntregaEditandoId != null) "Actualizar lubricante" else "Agregar a la entrega"
        val habilitado = lubricanteEntregaLineas.isNotEmpty() && !lubricanteEntregaProcesando
        botonRegistrar?.isEnabled = habilitado
        botonRegistrar?.alpha = if (habilitado) 1f else 0.55f
    }

    fun aplicarLinea(linea: LubricanteEntregaLinea) {
        lubricanteEntregaEditandoId = linea.id
        lubricanteDraftCategoria = linea.categoria
        lubricanteDraftSubcategoria = linea.subcategoria
        lubricanteDraftProducto = linea.producto
        lubricanteDraftCodigo = linea.codigoOriginal
        lubricanteDraftDocumento = linea.documentoId
        lubricanteDraftUbicacion = linea.ubicacion
        seleccionarSpinners(linea.categoria, linea.subcategoria, linea.producto) {
            escribirCodigoSinBusqueda(linea.codigoOriginal)
            cargarUbicaciones(linea.codigoOriginal, linea.producto, linea.ubicacion, linea.documentoId)
        }
        cantidad.setText(cantidadTexto(linea.cantidad))
        actualizarBotones()
    }

    fun renderProductos() {
        productosAgregados.removeAllViews()
        productosAgregados.addView(TextView(this).apply {
            text = "Productos agregados"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verdeOscuro)
            setPadding(0, dp(8), 0, dp(4))
        })
        if (lubricanteEntregaLineas.isEmpty()) {
            productosAgregados.addView(TextView(this).apply {
                text = "Aun no hay lubricantes en esta entrega."
                textSize = 13f
                setTextColor(gris)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
            })
            actualizarBotones()
            return
        }
        lubricanteEntregaLineas.forEachIndexed { index, linea ->
            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(4), 0, dp(8)) }
            }
            tarjeta.addView(TextView(this).apply {
                text = "${index + 1}. ${linea.producto}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(texto)
            })
            tarjeta.addView(TextView(this).apply {
                text = listOf(
                    "Categoria: ${linea.categoria}",
                    "Uso: ${linea.subcategoria}",
                    "Codigo interno: ${linea.codigoInterno}",
                    "Codigo original: ${linea.codigoOriginal}",
                    "Documento: ${linea.documentoId}",
                    "Ubicacion: ${linea.ubicacion}",
                    "Cantidad: ${cantidadTexto(linea.cantidad)} ${linea.unidad}",
                    "Stock: ${cantidadTexto(linea.stockDisponible)} ${linea.unidad}",
                ).joinToString("\n")
                textSize = 12.5f
                setTextColor(gris)
                setPadding(0, dp(4), 0, dp(8))
            })
            val acciones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val editar = outlineButton("Editar") { aplicarLinea(linea) }
            val eliminar = outlineButton("Eliminar") {
                lubricanteEntregaLineas.removeAll { it.id == linea.id }
                if (lubricanteEntregaEditandoId == linea.id) lubricanteEntregaEditandoId = null
                renderProductos()
            }.apply { setTextColor(Color.RED) }
            acciones.addView(editar, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(6), 0) })
            acciones.addView(eliminar, LinearLayout.LayoutParams(0, dp(44), 1f))
            tarjeta.addView(acciones)
            productosAgregados.addView(tarjeta)
        }
        actualizarBotones()
    }

    botonAgregar = primaryButton("Agregar a la entrega") {
        guardarDraft()
        val nueva = lineaDesdeFormulario() ?: return@primaryButton
        val idEditando = lubricanteEntregaEditandoId
        val indiceEditando = idEditando?.let { id -> lubricanteEntregaLineas.indexOfFirst { it.id == id } } ?: -1
        val indiceDuplicado = lubricanteEntregaLineas.indexOfFirst { it.clave() == nueva.clave() && it.id != idEditando }
        val total = nueva.cantidad + if (indiceDuplicado >= 0) lubricanteEntregaLineas[indiceDuplicado].cantidad else 0.0
        if (!total.isFinite() || total > nueva.stockDisponible) {
            Toast.makeText(this, "Stock insuficiente para la cantidad acumulada de ${nueva.producto}", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        when {
            indiceEditando >= 0 && indiceDuplicado >= 0 -> {
                lubricanteEntregaLineas[indiceDuplicado] = lubricanteEntregaLineas[indiceDuplicado].copy(cantidad = total, stockDisponible = nueva.stockDisponible)
                lubricanteEntregaLineas.removeAt(indiceEditando)
            }
            indiceEditando >= 0 -> lubricanteEntregaLineas[indiceEditando] = nueva.copy(id = lubricanteEntregaLineas[indiceEditando].id)
            indiceDuplicado >= 0 -> lubricanteEntregaLineas[indiceDuplicado] = lubricanteEntregaLineas[indiceDuplicado].copy(cantidad = total, stockDisponible = nueva.stockDisponible)
            else -> lubricanteEntregaLineas.add(nueva)
        }
        lubricanteEntregaEditandoId = null
        cantidad.setText("1")
        lubricanteDraftCantidad = "1"
        renderProductos()
        stockVerificado = false
        tokenStockVerificado = ""
        cargarUbicaciones(nueva.codigoOriginal, nueva.producto, nueva.ubicacion, nueva.documentoId)
        Toast.makeText(this, "Lubricante agregado a la entrega", Toast.LENGTH_SHORT).show()
    }
    botonAgregar?.let { root.addView(it) }
    root.addView(productosAgregados)

    botonRegistrar = primaryButton("Registrar todos") { view ->
        if (lubricanteEntregaProcesando) return@primaryButton
        guardarDraft()
        if (lubricanteEntregaLineas.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un lubricante", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        if (!required(solicitante, labor)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para registrar toda la entrega", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        fun finalizarBloqueo() {
            lubricanteEntregaProcesando = false
            (view as? Button)?.text = "Registrar todos"
            actualizarBotones()
        }

        fun registrar(foto: String) {
            val lineas = lubricanteEntregaLineas.toList()
            registrarSalidaMultipleLubricante(
                lineas,
                solicitante.text.toString().trim(),
                labor.text.toString().trim(),
                observaciones.text.toString(),
                foto,
                onSuccess = {
                    finalizarBloqueo()
                    limpiarEstadoEntregaLubricante()
                    saved("Consumo de Lubricantes registrado con ${lineas.size} linea(s)")
                    showLubricantesTallerForm()
                },
                onFailure = { error ->
                    Toast.makeText(this, error.localizedMessage ?: "No se pudo registrar la salida", Toast.LENGTH_LONG).show()
                    finalizarBloqueo()
                },
            )
        }

        lubricanteEntregaProcesando = true
        (view as? Button)?.apply {
            isEnabled = false
            alpha = 0.55f
            text = "Registrando..."
        }
        val evidenciaLocal = uriLocalEvidencia.ifBlank { urlEvidencia.takeIf { evidenciaEsUriLocal(it) }.orEmpty() }
        when {
            urlEvidencia.isNotBlank() && !evidenciaEsUriLocal(urlEvidencia) -> registrar(urlEvidencia)
            evidenciaLocal.isNotBlank() -> {
                Toast.makeText(this, "Subiendo evidencia de Lubricantes...", Toast.LENGTH_SHORT).show()
                subirEvidenciaCloud(evidenciaLocal, ModulosInventario.LUBRICANTES_TALLER) { url ->
                    if (url.isBlank()) {
                        Toast.makeText(this, "No se pudo subir la evidencia", Toast.LENGTH_LONG).show()
                        finalizarBloqueo()
                    } else {
                        urlEvidencia = url
                        lubricanteDraftUrlEvidencia = url
                        registrar(url)
                    }
                }
            }
            else -> registrar("")
        }
    }
    botonRegistrar?.let { root.addView(it) }
    renderProductos()
}

private fun MainActivity.showDialogNuevoLubricanteMultiple(onDone: (LubricanteProductoMeta) -> Unit) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()
    root.addView(TextView(this).apply {
        text = "Nuevo lubricante"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(verdeOscuro)
        setPadding(0, 0, 0, dp(12))
    })

    val categorias = (QuimicosCanonicos.items.filter { it.modulo == ModulosInventario.LUBRICANTES_TALLER }.map { it.categoria } +
        lubricanteProductosPersonalizados.values.map { it.categoria })
        .distinctBy { normalizarBusqueda(it) }
        .sortedBy { normalizarBusqueda(it) }
        .ifEmpty { listOf("Lubricantes y fluidos") }
    val categoriaExistente = spinner(root, "Categoria existente", categorias)
    val categoriaNueva = field(root, "Categoria nueva", "Opcional")
    val subcategoria = field(root, "Subcategoria / uso *", "Ej: Aceites, grasas, refrigerantes")
    val producto = field(root, "Producto *", "Nombre del lubricante o fluido")
    val codigo = field(root, "Codigo original *", "Ej: LUB200")
    val ubicacion = field(root, "Ubicacion *", "Caseta o ubicacion real del inventario")
    ubicacion.setText("CASETA DE LUBRICANTES")
    val unidad = field(root, "Unidad *", "ML, LITRO, GALON, KG...")

    lateinit var crear: Button
    fun desbloquear() {
        crear.isEnabled = true
        crear.alpha = 1f
        crear.text = "Crear lubricante"
    }
    crear = primaryButton("Crear lubricante") {
        if (!required(subcategoria, producto, codigo, ubicacion, unidad)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para crear el lubricante", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        val categoriaValor = categoriaNueva.text.toString().trim()
            .ifBlank { categoriaExistente.selectedItem?.toString().orEmpty().trim() }
        val subcategoriaValor = subcategoria.text.toString().trim()
        val productoValor = producto.text.toString().trim()
        val codigoValor = codigo.text.toString().trim().uppercase(Locale.getDefault()).replace(Regex("\\s+"), "")
        val ubicacionValor = ubicacion.text.toString().trim().uppercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
        val unidadValor = unidad.text.toString().trim().uppercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
        if (categoriaValor.isBlank() || codigoValor.isBlank() || ubicacionValor.isBlank() || unidadValor.isBlank()) {
            Toast.makeText(this, "Completa categoria, codigo, ubicacion y unidad validos", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        if (ModulosInventario.moduloQuimico(categoriaValor, ubicacionValor) != ModulosInventario.LUBRICANTES_TALLER) {
            Toast.makeText(this, "La categoria y ubicacion deben pertenecer a Lubricantes taller", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        val documentoId = idQuimicoArea(codigoValor, ubicacionValor)
        val nuevo = LubricanteProductoMeta(
            documentoId,
            codigoValor,
            categoriaValor,
            subcategoriaValor,
            productoValor,
            ubicacionValor,
            unidadValor,
        )
        crear.isEnabled = false
        crear.alpha = 0.55f
        crear.text = "Creando..."
        obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
            val fecha = now()
            val data = mapOf(
                "modulo" to ModulosInventario.LUBRICANTES_TALLER,
                "categoria" to categoriaValor,
                "subcategoria" to subcategoriaValor,
                "item" to productoValor,
                "referencia" to codigoValor,
                "codigo_interno" to documentoId,
                "codigo_original" to codigoValor,
                "documento_id" to documentoId,
                "producto_id" to documentoId,
                "ubicacion" to ubicacionValor,
                "unidad" to unidadValor,
                "cantidad" to 0.0,
                "stock_actual" to 0.0,
                "activo" to true,
                "ultima_fecha" to fecha,
                "ultimo_solicitante" to usuario,
                "busqueda" to "$documentoId $codigoValor $productoValor $categoriaValor $subcategoriaValor $ubicacionValor".lowercase(Locale.getDefault()),
            )
            val existenciaRef = firestore.collection("existencias").document(documentoId)
            val catalogoRef = firestore.collection("catalogo_personalizado").document(documentoId)
            firestore.runTransaction { transaction ->
                if (transaction.get(existenciaRef).exists()) {
                    throw IllegalStateException("Ya existe un documento para $codigoValor en $ubicacionValor")
                }
                transaction.set(existenciaRef, data)
                transaction.set(catalogoRef, data, SetOptions.merge())
                null
            }.addOnSuccessListener {
                registrarLubricanteEnCatalogoLocal(nuevo)
                saved("Lubricante creado")
                dialog.dismiss()
                onDone(nuevo)
            }.addOnFailureListener { error ->
                desbloquear()
                Toast.makeText(this, error.localizedMessage ?: "No se pudo crear el lubricante", Toast.LENGTH_LONG).show()
            }
        }
    }
    root.addView(crear)
    dialog.setContentView(root)
    dialog.show()
}

private fun MainActivity.registrarSalidaMultipleLubricante(
    lineas: List<LubricanteEntregaLinea>,
    solicitante: String,
    labor: String,
    observaciones: String,
    fotoUrl: String,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null,
) {
    if (lineas.isEmpty()) {
        onFailure?.invoke(IllegalStateException("No hay lubricantes para registrar"))
        return
    }
    if (!isNetworkAvailable()) {
        onFailure?.invoke(IllegalStateException("Sin conexion no se puede registrar la entrega completa"))
        return
    }
    if (lineas.any { !it.cantidad.isFinite() || it.cantidad <= 0.0 }) {
        onFailure?.invoke(IllegalArgumentException("Todas las cantidades deben ser decimales positivos"))
        return
    }

    obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
        val fecha = now()
        val entregaId = "LUB-${System.currentTimeMillis()}"
        firestore.runTransaction { transaction ->
            val referencias = lineas.associate { linea ->
                if (linea.documentoId.isBlank()) throw IllegalStateException("Documento faltante para ${linea.producto}")
                linea.documentoId to firestore.collection("existencias").document(linea.documentoId)
            }
            val snapshots = mutableMapOf<String, DocumentSnapshot>()
            val stocks = mutableMapOf<String, Double>()
            val cantidades = lineas.groupBy { it.documentoId }.mapValues { (_, productos) -> productos.sumOf { it.cantidad } }

            referencias.forEach { (documentoId, referencia) ->
                val snapshot = transaction.get(referencia)
                if (!snapshot.exists() || snapshot.getBoolean("activo") == false) {
                    throw IllegalStateException("Inventario no disponible para $documentoId")
                }
                val linea = lineas.first { it.documentoId == documentoId }
                val categoriaDoc = snapshot.getString("categoria").orEmpty()
                val subcategoriaDoc = subcategoriaExistencia(snapshot).ifBlank { "General" }
                val productoDoc = nombreItemExistencia(snapshot)
                val codigoDoc = codigoOriginalExistencia(snapshot)
                val ubicacionDoc = ubicacionExistencia(snapshot)
                val unidadDoc = snapshot.getString("unidad").orEmpty()
                val moduloDoc = snapshot.getString("modulo").orEmpty()
                if (!esDocumentoLubricanteTaller(moduloDoc, categoriaDoc, ubicacionDoc)) {
                    throw IllegalStateException("El documento $documentoId no pertenece a Lubricantes")
                }
                val coincide = snapshot.id == linea.documentoId &&
                    normalizarCodigoInterno(codigoExistencia(snapshot)) == normalizarCodigoInterno(linea.codigoInterno) &&
                    normalizarCodigoInterno(codigoDoc) == normalizarCodigoInterno(linea.codigoOriginal) &&
                    normalizarBusqueda(categoriaDoc) == normalizarBusqueda(linea.categoria) &&
                    normalizarBusqueda(subcategoriaDoc) == normalizarBusqueda(linea.subcategoria) &&
                    normalizarBusqueda(productoDoc) == normalizarBusqueda(linea.producto) &&
                    normalizarBusqueda(ubicacionDoc) == normalizarBusqueda(linea.ubicacion) &&
                    normalizarBusqueda(unidadDoc) == normalizarBusqueda(linea.unidad)
                if (!coincide) {
                    throw IllegalStateException("El documento $documentoId ya no coincide exactamente con ${linea.producto}")
                }
                val solicitado = cantidades[documentoId] ?: Double.NaN
                val stockActual = numeroDocumento(snapshot, "cantidad", "stock_actual")
                if (!solicitado.isFinite() || solicitado <= 0.0) {
                    throw IllegalStateException("Cantidad invalida en ${linea.producto}")
                }
                if (!stockActual.isFinite() || solicitado > stockActual) {
                    throw IllegalStateException(
                        "Stock insuficiente en ${linea.producto}. Disponible: ${cantidadTexto(stockActual)} ${linea.unidad}, " +
                            "solicitado: ${cantidadTexto(solicitado)} ${linea.unidad}"
                    )
                }
                snapshots[documentoId] = snapshot
                stocks[documentoId] = stockActual
            }

            val stockRestante = stocks.toMutableMap()
            val resultados = mutableListOf<LubricanteResultadoSalida>()
            lineas.forEachIndexed { index, linea ->
                val snapshot = snapshots[linea.documentoId]
                    ?: throw IllegalStateException("Inventario no disponible para ${linea.producto}")
                val referencia = referencias[linea.documentoId]
                    ?: throw IllegalStateException("Inventario no disponible para ${linea.producto}")
                val stockAnterior = stockRestante[linea.documentoId]
                    ?: throw IllegalStateException("Stock no disponible para ${linea.producto}")
                val stockNuevo = stockAnterior - linea.cantidad
                if (!stockNuevo.isFinite() || stockNuevo < 0.0) {
                    throw IllegalStateException("El descuento produciria stock negativo en ${linea.producto}")
                }
                transaction.set(referencia, mapOf(
                    "cantidad" to stockNuevo,
                    "stock_actual" to stockNuevo,
                    "ultima_fecha" to fecha,
                    "ultimo_solicitante" to solicitante,
                ), SetOptions.merge())
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to ModulosInventario.LUBRICANTES_TALLER,
                    "tipoMovimiento" to "Consumo final",
                    "item" to linea.producto,
                    "categoria" to linea.categoria,
                    "subcategoria" to linea.subcategoria,
                    "referencia" to linea.codigoOriginal,
                    "codigo_interno" to linea.codigoInterno,
                    "codigo_original" to linea.codigoOriginal,
                    "documento_id" to linea.documentoId,
                    "producto_id" to snapshot.getString("producto_id").orEmpty().ifBlank { linea.documentoId },
                    "ubicacion" to linea.ubicacion,
                    "cantidad" to linea.cantidad,
                    "unidad" to linea.unidad,
                    "solicitante" to solicitante,
                    "labor" to labor,
                    "observaciones" to observaciones,
                    "usuario" to usuario,
                    "fotoUrl" to fotoUrl,
                    "stock_actualizado" to true,
                    "stock_anterior" to stockAnterior,
                    "stock_nuevo" to stockNuevo,
                    "entrega_id" to entregaId,
                    "linea_entrega" to (index + 1),
                    "total_lineas" to lineas.size,
                ))
                stockRestante[linea.documentoId] = stockNuevo
                resultados += LubricanteResultadoSalida(linea.documentoId, linea.producto, stockAnterior, stockNuevo)
            }
            resultados
        }.addOnSuccessListener { resultados ->
            resultados.forEach { resultado ->
                registrarCambioLocal(
                    "SALIDA_STOCK",
                    ModulosInventario.LUBRICANTES_TALLER,
                    resultado.documentoId,
                    "Consumo multiple: ${resultado.producto}",
                    resultado.stockAnterior.toString(),
                    resultado.stockNuevo.toString(),
                )
            }
            onSuccess?.invoke()
        }.addOnFailureListener { onFailure?.invoke(it) }
    }
}
