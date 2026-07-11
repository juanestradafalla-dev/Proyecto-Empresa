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
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import java.util.Locale

private data class AgroEntregaLinea(
    val id: Long = System.nanoTime(),
    val categoria: String,
    val subcategoria: String,
    val producto: String,
    val codigoOriginal: String,
    val documentoId: String,
    val ubicacion: String,
    val cantidad: Double,
    val unidad: String,
    val stockDisponible: Double,
) {
    fun clave(): String = documentoId
}

private data class AgroResultadoSalida(
    val documentoId: String,
    val producto: String,
    val stockAnterior: Double,
    val stockNuevo: Double,
)

private data class AgroProductoMeta(
    val documentoId: String,
    val codigoOriginal: String,
    val categoria: String,
    val subcategoria: String,
    val producto: String,
    val ubicacion: String,
    val unidad: String,
)

private val agroEntregaLineas = mutableListOf<AgroEntregaLinea>()
private val agroProductosPersonalizados = linkedMapOf<String, AgroProductoMeta>()
private var agroEntregaEditandoId: Long? = null
private var agroEntregaProcesando = false
private var agroDraftCategoria = ""
private var agroDraftSubcategoria = ""
private var agroDraftProducto = ""
private var agroDraftCodigo = ""
private var agroDraftDocumento = ""
private var agroDraftUbicacion = ""
private var agroDraftCantidad = "1"
private var agroDraftSolicitante = ""
private var agroDraftLabor = ""
private var agroDraftObservaciones = ""
private var agroDraftUrlEvidencia = ""
private var agroDraftUriEvidencia = ""
private var agroPersonalizadosCargados = false
private var agroPersonalizadosCargando = false

private fun MainActivity.registrarAgroEnCatalogoLocal(producto: AgroProductoMeta) {
    if (ModulosInventario.moduloQuimico(producto.categoria, producto.ubicacion) != ModulosInventario.AGROQUIMICOS) return
    agroProductosPersonalizados[producto.documentoId] = producto
    val modulo = catalogoCargado.getOrPut(ModulosInventario.AGROQUIMICOS) { mutableMapOf() }
    val categoria = modulo.getOrPut(producto.categoria) { mutableMapOf() }
    val productos = categoria.getOrPut(producto.subcategoria) { mutableListOf() }
    if (productos.none { normalizarBusqueda(it) == normalizarBusqueda(producto.producto) }) {
        productos.add(producto.producto)
    }
}

private fun MainActivity.agroMetaDesdeDocumento(doc: DocumentSnapshot): AgroProductoMeta? {
    if (!doc.exists() || doc.getBoolean("activo") == false) return null
    val categoria = doc.getString("categoria").orEmpty().trim()
    val ubicacion = ubicacionExistencia(doc).trim()
    val modulo = doc.getString("modulo").orEmpty()
    val moduloCalculado = ModulosInventario.moduloQuimico(categoria, ubicacion)
    if (ModulosInventario.esModuloLubricantesTaller(modulo) ||
        ModulosInventario.esModuloLubricantesTaller(moduloCalculado)
    ) return null
    if (!ModulosInventario.esModuloAgroquimico(modulo) && modulo.isNotBlank()) return null
    val subcategoria = subcategoriaExistencia(doc).trim().ifBlank { "General" }
    val producto = nombreItemExistencia(doc).trim()
    val codigo = codigoOriginalExistencia(doc).trim()
    val unidad = doc.getString("unidad").orEmpty().trim()
    if (categoria.isBlank() || producto.isBlank() || codigo.isBlank() || ubicacion.isBlank() || unidad.isBlank()) return null
    return AgroProductoMeta(doc.id, codigo, categoria, subcategoria, producto, ubicacion, unidad)
}

private fun MainActivity.cargarAgroPersonalizados() {
    if (agroPersonalizadosCargados || agroPersonalizadosCargando || !isNetworkAvailable()) return
    agroPersonalizadosCargando = true
    firestore.collection("existencias")
        .whereEqualTo("modulo", ModulosInventario.AGROQUIMICOS)
        .limit(500)
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.mapNotNull { agroMetaDesdeDocumento(it) }.forEach { registrarAgroEnCatalogoLocal(it) }
            agroPersonalizadosCargando = false
            agroPersonalizadosCargados = true
            if (currentScreenId.contains("agro", ignoreCase = true)) currentScreenRenderer?.invoke()
        }
        .addOnFailureListener {
            agroPersonalizadosCargando = false
        }
}

private fun limpiarEstadoEntregaAgro() {
    agroEntregaLineas.clear()
    agroEntregaEditandoId = null
    agroEntregaProcesando = false
    agroDraftCategoria = ""
    agroDraftSubcategoria = ""
    agroDraftProducto = ""
    agroDraftCodigo = ""
    agroDraftDocumento = ""
    agroDraftUbicacion = ""
    agroDraftCantidad = "1"
    agroDraftSolicitante = ""
    agroDraftLabor = ""
    agroDraftObservaciones = ""
    agroDraftUrlEvidencia = ""
    agroDraftUriEvidencia = ""
}

private fun seleccionAgroValida(valor: String): Boolean {
    val normalizado = normalizarBusqueda(valor)
    return valor.isNotBlank() &&
        !normalizado.startsWith("selecciona") &&
        !normalizado.startsWith("sin-") &&
        !normalizado.startsWith("no-hay")
}

internal fun MainActivity.showAgroquimicosMultipleInterno(
    pItem: String = "",
    pCant: String = "",
    pSol: String = "",
    pCat: String = "",
    pRef: String = "",
) {
    cargarAgroPersonalizados()
    if (pCat.isNotBlank()) agroDraftCategoria = pCat
    if (pItem.isNotBlank()) agroDraftSubcategoria = pItem
    if (pRef.isNotBlank()) agroDraftProducto = pRef
    if (pCant.isNotBlank()) agroDraftCantidad = pCant
    if (pSol.isNotBlank()) agroDraftSolicitante = pSol

    fun salirAgro() {
        if (agroEntregaProcesando) {
            Toast.makeText(this, "Espera a que termine el registro de la entrega", Toast.LENGTH_SHORT).show()
            return
        }
        if (agroEntregaLineas.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Descartar entrega")
                .setMessage("Hay agroqu\u00edmicos agregados sin registrar. Si sales, se descartara esta entrega.")
                .setPositiveButton("Descartar entrega") { _, _ ->
                    limpiarEstadoEntregaAgro()
                    showMainMenu()
                }
                .setNegativeButton("Seguir editando", null)
                .show()
        } else {
            limpiarEstadoEntregaAgro()
            showMainMenu()
        }
    }

    currentScreenRenderer = {
        showQuimicoForm(
            pItem = agroDraftSubcategoria,
            pCant = agroDraftCantidad,
            pSol = agroDraftSolicitante,
            pCat = agroDraftCategoria,
            pRef = agroDraftProducto,
        )
    }
    val root = baseScreen(
        "Salida de agroqu\u00edmicos",
        "Registra varios productos con su ubicacion y stock exactos.",
        backAction = { salirAgro() },
    )

    fun catalogoAgro() = catalogoCargado[ModulosInventario.AGROQUIMICOS] ?: mapOf()
    fun categorias(): List<String> = (
        catalogoAgro().keys +
            QuimicosCanonicos.items.filter { it.modulo == ModulosInventario.AGROQUIMICOS }.map { it.categoria } +
            agroProductosPersonalizados.values.map { it.categoria }
        ).distinctBy { normalizarBusqueda(it) }.sortedBy { normalizarBusqueda(it) }

    fun subcategorias(categoria: String): List<String> {
        val catalogo = (catalogoAgro()[categoria] as? Map<*, *>)?.keys?.map { it.toString() }.orEmpty()
        return (catalogo +
            QuimicosCanonicos.items.filter {
                it.modulo == ModulosInventario.AGROQUIMICOS && normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria)
            }.map { it.subcategoria } +
            agroProductosPersonalizados.values.filter {
                normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria)
            }.map { it.subcategoria })
            .filterNot { esEntradaCatalogoSulcamagVieja(it) }
            .distinctBy { normalizarBusqueda(it) }
            .sortedBy { normalizarBusqueda(it) }
    }

    fun productos(categoria: String, subcategoria: String): List<String> {
        val catalogo = ((catalogoAgro()[categoria] as? Map<*, *>)?.get(subcategoria) as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }.orEmpty()
        return (catalogo +
            QuimicosCanonicos.items.filter {
                it.modulo == ModulosInventario.AGROQUIMICOS &&
                    normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                    normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria)
            }.map { it.item } +
            agroProductosPersonalizados.values.filter {
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
    val stockLabel = TextView(this).apply {
        text = "Selecciona un producto para ver ubicacion y stock"
        textSize = 13f
        setTextColor(verdeOscuro)
        setPadding(0, dp(4), 0, dp(6))
    }
    root.addView(stockLabel)

    var codigoOriginalSeleccionado = ""
    var documentoSeleccionado = ""
    var ubicacionesActuales = listOf<QuimicoUbicacionStock>()
    var ubicacionSeleccionada: QuimicoUbicacionStock? = null
    var stockVerificado = false
    var tokenStockVerificado = ""
    var consultaStockActual = 0
    var eventosSpinnerActivos = false
    var actualizandoSpinners = false
    var botonUbicacion: Button? = null
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

    fun tokenSeleccion(codigo: String = codigoInterno.text?.toString().orEmpty()): String = listOf(
        categoriaSpinner.selectedItem?.toString().orEmpty(),
        subcategoriaSpinner.selectedItem?.toString().orEmpty(),
        productoSpinner.selectedItem?.toString().orEmpty(),
        normalizarCodigoInterno(codigo),
    ).joinToString("|") { normalizarBusqueda(it) }

    fun limpiarSeleccionStock() {
        codigoOriginalSeleccionado = ""
        documentoSeleccionado = ""
        agroDraftCodigo = ""
        agroDraftDocumento = ""
        agroDraftUbicacion = ""
        ubicacionesActuales = emptyList()
        ubicacionSeleccionada = null
        stockVerificado = false
        tokenStockVerificado = ""
        unidadLabel.text = "Unidad: selecciona un producto"
    }

    fun actualizarResumen() {
        val total = ubicacionesActuales.sumOf { it.cantidad }
        val detalle = ubicacionesActuales.joinToString("\n") { opcion ->
            val marca = if (opcion.documentoId == ubicacionSeleccionada?.documentoId) "Seleccionado - " else ""
            "$marca${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}"
        }
        stockLabel.text = if (ubicacionesActuales.isEmpty()) "Stock no disponible" else "$detalle\nTotal: ${cantidadTexto(total)} ${ubicacionSeleccionada?.unidad.orEmpty()}"
        stockLabel.setTextColor(if ((ubicacionSeleccionada?.cantidad ?: total) <= 0.0) Color.RED else verdeOscuro)
        val puedeElegir = ubicacionesActuales.size > 1
        botonUbicacion?.isEnabled = puedeElegir
        botonUbicacion?.alpha = if (puedeElegir) 1f else 0.55f
        botonUbicacion?.text = when {
            ubicacionesActuales.size > 1 -> "Elegir ubicacion / traslado"
            ubicacionesActuales.size == 1 -> "Ubicacion unica"
            else -> "Ubicacion"
        }
        vistaEntrada?.let { vista ->
            val habilitada = ubicacionSeleccionada != null
            vista.isEnabled = habilitada
            vista.alpha = if (habilitada) 1f else 0.55f
        }
    }

    fun seleccionarUbicacion(opcion: QuimicoUbicacionStock?, token: String = tokenSeleccion()) {
        if (opcion != null &&
            ModulosInventario.moduloQuimico(opcion.categoria, opcion.ubicacion) != ModulosInventario.AGROQUIMICOS
        ) {
            limpiarSeleccionStock()
            actualizarResumen()
            stockLabel.text = "La ubicacion encontrada no pertenece a Agroquimicos"
            stockLabel.setTextColor(Color.RED)
            return
        }
        ubicacionSeleccionada = opcion
        if (opcion == null) {
            documentoSeleccionado = ""
            stockVerificado = false
            tokenStockVerificado = ""
            unidadLabel.text = "Unidad: selecciona un producto"
        } else {
            documentoSeleccionado = opcion.documentoId
            codigoOriginalSeleccionado = opcion.codigoOriginal
            agroDraftDocumento = opcion.documentoId
            agroDraftCodigo = opcion.codigoOriginal
            agroDraftUbicacion = opcion.ubicacion
            escribirCodigoSinBusqueda(opcion.codigoOriginal)
            unidadLabel.text = "Unidad: ${opcion.unidad}"
            stockVerificado = true
            tokenStockVerificado = token
        }
        actualizarResumen()
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
        val token = tokenSeleccion(codigoOriginal)
        cargarUbicacionesQuimico(codigoOriginal, producto, { opciones ->
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenSeleccion()) return@cargarUbicacionesQuimico
            val opcionesAgro = opciones.filter {
                ModulosInventario.moduloQuimico(it.categoria, it.ubicacion) == ModulosInventario.AGROQUIMICOS
            }
            ubicacionesActuales = opcionesAgro
            val seleccion = opcionesAgro.find { it.documentoId == documentoPreferido }
                ?: opcionesAgro.find { it.documentoId == documentoSeleccionado }
                ?: opcionesAgro.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda(ubicacionPreferida) }
                ?: opcionesAgro.firstOrNull()
            seleccionarUbicacion(seleccion, token)
        }, {
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenSeleccion()) return@cargarUbicacionesQuimico
            limpiarSeleccionStock()
            stockLabel.text = "Stock no disponible en linea"
            stockLabel.setTextColor(Color.RED)
            actualizarResumen()
        }, moduloFiltro = ModulosInventario.AGROQUIMICOS)
    }

    fun metaPersonalizado(categoria: String, subcategoria: String, producto: String): AgroProductoMeta? =
        agroProductosPersonalizados.values.firstOrNull {
            normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria) &&
                normalizarBusqueda(it.producto) == normalizarBusqueda(producto)
        }

    fun aplicarMeta(meta: AgroProductoMeta?) {
        if (meta == null) {
            consultaStockActual++
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            actualizarResumen()
            return
        }
        if (ModulosInventario.moduloQuimico(meta.categoria, meta.ubicacion) != ModulosInventario.AGROQUIMICOS) {
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
        if (!seleccionAgroValida(categoria) || !seleccionAgroValida(subcategoria) || !seleccionAgroValida(producto)) return
        val canonico = QuimicosCanonicos.buscar(ModulosInventario.AGROQUIMICOS, categoria, subcategoria, producto)
        if (canonico != null && canonico.modulo == ModulosInventario.AGROQUIMICOS) {
            aplicarMeta(AgroProductoMeta(canonico.documentoId, canonico.codigoOriginal, canonico.categoria, canonico.subcategoria, canonico.item, canonico.ubicacion, canonico.unidad))
            return
        }
        val local = agroProductosPersonalizados[documentoPreferido]?.takeIf {
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
        val token = tokenSeleccion()
        stockLabel.text = "Consultando producto..."
        firestore.collection("existencias").whereEqualTo("item", producto).limit(50).get()
            .addOnSuccessListener { snapshot ->
                if (!pantallaActiva() || consulta != consultaStockActual || token != tokenSeleccion()) return@addOnSuccessListener
                val metas = snapshot.documents.mapNotNull { agroMetaDesdeDocumento(it) }.filter {
                    normalizarBusqueda(it.categoria) == normalizarBusqueda(categoria) &&
                        normalizarBusqueda(it.subcategoria) == normalizarBusqueda(subcategoria)
                }
                metas.forEach { registrarAgroEnCatalogoLocal(it) }
                val meta = metas.find { it.documentoId == documentoPreferido }
                    ?: metas.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda(ubicacionPreferida) }
                    ?: metas.firstOrNull()
                if (meta == null) aplicarMeta(null) else aplicarMeta(meta)
            }
            .addOnFailureListener {
                if (consulta == consultaStockActual && token == tokenSeleccion()) aplicarMeta(null)
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

    botonUbicacion = primaryButton("Ubicacion") { }.apply {
        isEnabled = false
        setBackgroundColor(Color.rgb(0, 120, 200))
        setOnClickListener {
            if (ubicacionesActuales.size <= 1) return@setOnClickListener
            showDialogUbicacionesQuimico(
                opciones = ubicacionesActuales,
                onSelected = { seleccionarUbicacion(it, tokenSeleccion(it.codigoOriginal)) },
                onTransferDone = {
                    cargarUbicaciones(
                        codigoOriginalSeleccionado,
                        productoSpinner.selectedItem?.toString().orEmpty(),
                        ubicacionSeleccionada?.ubicacion.orEmpty(),
                        ubicacionSeleccionada?.documentoId.orEmpty(),
                    )
                },
                permitirAreasCop = false,
            )
        }
    }
    root.addView(botonUbicacion)

    setupCodigoInternoSalida(root, codigoInterno, ModulosInventario.AGROQUIMICOS) { producto ->
        if (!eventosSpinnerActivos) return@setupCodigoInternoSalida
        consultaStockActual++
        if (ModulosInventario.moduloQuimico(producto.categoria, producto.ubicacion) != ModulosInventario.AGROQUIMICOS) {
            aplicarMeta(null)
            Toast.makeText(this, "El codigo corresponde a Lubricantes taller", Toast.LENGTH_LONG).show()
            return@setupCodigoInternoSalida
        }
        val subcategoria = producto.subcategoria.ifBlank { "General" }
        val meta = AgroProductoMeta(
            producto.documentoId.ifBlank { producto.codigoInterno },
            producto.codigoOriginal.ifBlank { producto.codigoInterno },
            producto.categoria,
            subcategoria,
            producto.item,
            producto.ubicacion,
            producto.unidad,
        )
        registrarAgroEnCatalogoLocal(meta)
        seleccionarSpinners(meta.categoria, meta.subcategoria, meta.producto) {
            agroDraftCategoria = meta.categoria
            agroDraftSubcategoria = meta.subcategoria
            agroDraftProducto = meta.producto
            aplicarMeta(meta)
        }
    }

    setupSearchBar(root, ModulosInventario.AGROQUIMICOS) { _, categoria, subcategoria, producto ->
        if (!eventosSpinnerActivos) return@setupSearchBar
        consultaStockActual++
        seleccionarSpinners(categoria, subcategoria, producto) {
            agroDraftCategoria = categoria
            agroDraftSubcategoria = subcategoria
            agroDraftProducto = producto
            resolverProductoSeleccionado()
        }
    }

    categoriaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || actualizandoSpinners || categoriaSpinner.tag == "SINCRO") return
            val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
            if (!seleccionAgroValida(categoria)) return
            agroDraftCategoria = categoria
            agroDraftSubcategoria = ""
            agroDraftProducto = ""
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
            if (!seleccionAgroValida(categoria) || !seleccionAgroValida(subcategoria)) return
            agroDraftSubcategoria = subcategoria
            agroDraftProducto = ""
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
            if (!seleccionAgroValida(producto)) return
            agroDraftProducto = producto
            limpiarSeleccionStock()
            resolverProductoSeleccionado()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    root.post {
        val categoriaInicial = agroDraftCategoria.ifBlank { categoriaSpinner.selectedItem?.toString().orEmpty() }
        if (seleccionAgroValida(categoriaInicial)) {
            seleccionarSpinners(categoriaInicial, agroDraftSubcategoria, agroDraftProducto) {
                eventosSpinnerActivos = true
                val categoriaActual = categoriaSpinner.selectedItem?.toString().orEmpty()
                val subcategoriaActual = subcategoriaSpinner.selectedItem?.toString().orEmpty()
                val productoActual = productoSpinner.selectedItem?.toString().orEmpty()
                if (seleccionAgroValida(categoriaActual) &&
                    seleccionAgroValida(subcategoriaActual) &&
                    seleccionAgroValida(productoActual)
                ) {
                    agroDraftCategoria = categoriaActual
                    agroDraftSubcategoria = subcategoriaActual
                    agroDraftProducto = productoActual
                    resolverProductoSeleccionado(agroDraftDocumento, agroDraftUbicacion)
                }
            }
        } else {
            eventosSpinnerActivos = true
        }
        actualizarResumen()
    }

    val cantidad = field(root, "Cantidad *", "Ej: 1.5", number = true)
    cantidad.setText(pCant.ifBlank { agroDraftCantidad.ifBlank { "1" } })
    val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
    solicitante.setText(pSol.ifBlank { agroDraftSolicitante })
    val labor = field(root, "Labor / aplicacion *", "Ej: vivero, fertilizacion, control sanitario")
    labor.setText(agroDraftLabor)
    val observaciones = field(root, "Observaciones", "Lote, dosis, area o recomendacion")
    observaciones.setText(agroDraftObservaciones)
    var urlEvidencia = agroDraftUrlEvidencia
    var uriLocalEvidencia = agroDraftUriEvidencia

    fun guardarDraft() {
        agroDraftCategoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        agroDraftSubcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty().takeIf { seleccionAgroValida(it) }.orEmpty()
        agroDraftProducto = productoSpinner.selectedItem?.toString().orEmpty().takeIf { seleccionAgroValida(it) }.orEmpty()
        agroDraftCodigo = codigoOriginalSeleccionado
        agroDraftDocumento = documentoSeleccionado
        agroDraftUbicacion = ubicacionSeleccionada?.ubicacion.orEmpty()
        agroDraftCantidad = cantidad.text.toString().ifBlank { "1" }
        agroDraftSolicitante = solicitante.text.toString()
        agroDraftLabor = labor.text.toString()
        agroDraftObservaciones = observaciones.text.toString()
        agroDraftUrlEvidencia = urlEvidencia
        agroDraftUriEvidencia = uriLocalEvidencia
    }

    fun observar(campo: android.widget.EditText, accion: (String) -> Unit) {
        campo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = accion(s?.toString().orEmpty())
        })
    }
    observar(cantidad) { agroDraftCantidad = it.ifBlank { "1" } }
    observar(solicitante) { agroDraftSolicitante = it }
    observar(labor) { agroDraftLabor = it }
    observar(observaciones) { agroDraftObservaciones = it }

    root.addView(evidenceButton {
        capturarEvidencia { uri ->
            uriLocalEvidencia = uri
            urlEvidencia = ""
            agroDraftUriEvidencia = uri
            agroDraftUrlEvidencia = ""
            mostrarPrevisualizacionEvidencia(root, uri) {
                uriLocalEvidencia = ""
                urlEvidencia = ""
                agroDraftUriEvidencia = ""
                agroDraftUrlEvidencia = ""
            }
        }
    })
    if (uriLocalEvidencia.isNotBlank()) {
        mostrarPrevisualizacionEvidencia(root, uriLocalEvidencia) {
            uriLocalEvidencia = ""
            urlEvidencia = ""
            agroDraftUriEvidencia = ""
            agroDraftUrlEvidencia = ""
        }
    }

    val gestionAcciones = gestionNuevoEntradaRow(
        onNuevo = {
            guardarDraft()
            showDialogNuevoAgroquimicoMultiple { nuevo ->
                registrarAgroEnCatalogoLocal(nuevo)
                agroDraftCategoria = nuevo.categoria
                agroDraftSubcategoria = nuevo.subcategoria
                agroDraftProducto = nuevo.producto
                agroDraftCodigo = nuevo.codigoOriginal
                agroDraftDocumento = nuevo.documentoId
                agroDraftUbicacion = nuevo.ubicacion
                showQuimicoForm()
            }
        },
        onEntrada = {
            guardarDraft()
            val seleccion = ubicacionSeleccionada
            if (seleccion == null) {
            Toast.makeText(this, "Selecciona un agroqu\u00edmico y ubicacion primero", Toast.LENGTH_SHORT).show()
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

    fun lineaDesdeFormulario(): AgroEntregaLinea? {
        val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        val subcategoria = subcategoriaSpinner.selectedItem?.toString().orEmpty()
        val producto = productoSpinner.selectedItem?.toString().orEmpty()
        val seleccion = ubicacionSeleccionada
        if (!seleccionAgroValida(categoria) || !seleccionAgroValida(subcategoria) || !seleccionAgroValida(producto) || seleccion == null) {
            Toast.makeText(this, "Selecciona producto y ubicacion validos", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!stockVerificado || tokenStockVerificado != tokenSeleccion() || documentoSeleccionado != seleccion.documentoId) {
            Toast.makeText(this, "Espera la consulta de stock antes de agregar", Toast.LENGTH_SHORT).show()
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
        return AgroEntregaLinea(
            categoria = categoria,
            subcategoria = subcategoria,
            producto = producto,
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
        botonAgregar?.text = if (agroEntregaEditandoId != null) "Actualizar agroqu\u00edmico" else "Agregar a la entrega"
        val habilitado = agroEntregaLineas.isNotEmpty() && !agroEntregaProcesando
        botonRegistrar?.isEnabled = habilitado
        botonRegistrar?.alpha = if (habilitado) 1f else 0.55f
    }

    fun aplicarLinea(linea: AgroEntregaLinea) {
        agroEntregaEditandoId = linea.id
        agroDraftCategoria = linea.categoria
        agroDraftSubcategoria = linea.subcategoria
        agroDraftProducto = linea.producto
        agroDraftCodigo = linea.codigoOriginal
        agroDraftDocumento = linea.documentoId
        agroDraftUbicacion = linea.ubicacion
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
            text = "Agroqu\u00edmicos agregados"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verdeOscuro)
            setPadding(0, dp(8), 0, dp(4))
        })
        if (agroEntregaLineas.isEmpty()) {
            productosAgregados.addView(TextView(this).apply {
                text = "Aun no hay agroqu\u00edmicos en esta entrega."
                textSize = 13f
                setTextColor(gris)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
            })
            actualizarBotones()
            return
        }
        agroEntregaLineas.forEachIndexed { index, linea ->
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
                    "Codigo: ${linea.codigoOriginal}",
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
                agroEntregaLineas.removeAll { it.id == linea.id }
                if (agroEntregaEditandoId == linea.id) agroEntregaEditandoId = null
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
        val idEditando = agroEntregaEditandoId
        val indiceEditando = idEditando?.let { id -> agroEntregaLineas.indexOfFirst { it.id == id } } ?: -1
        val indiceDuplicado = agroEntregaLineas.indexOfFirst { it.clave() == nueva.clave() && it.id != idEditando }
        val total = nueva.cantidad + if (indiceDuplicado >= 0) agroEntregaLineas[indiceDuplicado].cantidad else 0.0
        if (!total.isFinite() || total > nueva.stockDisponible) {
            Toast.makeText(this, "Stock insuficiente para la cantidad acumulada de ${nueva.producto}", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        when {
            indiceEditando >= 0 && indiceDuplicado >= 0 -> {
                agroEntregaLineas[indiceDuplicado] = agroEntregaLineas[indiceDuplicado].copy(cantidad = total, stockDisponible = nueva.stockDisponible)
                agroEntregaLineas.removeAt(indiceEditando)
            }
            indiceEditando >= 0 -> agroEntregaLineas[indiceEditando] = nueva.copy(id = agroEntregaLineas[indiceEditando].id)
            indiceDuplicado >= 0 -> agroEntregaLineas[indiceDuplicado] = agroEntregaLineas[indiceDuplicado].copy(cantidad = total, stockDisponible = nueva.stockDisponible)
            else -> agroEntregaLineas.add(nueva)
        }
        agroEntregaEditandoId = null
        cantidad.setText("1")
        agroDraftCantidad = "1"
        renderProductos()
        stockVerificado = false
        tokenStockVerificado = ""
        cargarUbicaciones(nueva.codigoOriginal, nueva.producto, nueva.ubicacion, nueva.documentoId)
        Toast.makeText(this, "Agroqu\u00edmico agregado a la entrega", Toast.LENGTH_SHORT).show()
    }
    botonAgregar?.let { root.addView(it) }
    root.addView(productosAgregados)

    botonRegistrar = primaryButton("Registrar todos") { view ->
        if (agroEntregaProcesando) return@primaryButton
        guardarDraft()
        if (agroEntregaLineas.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un agroqu\u00edmico", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        if (!required(solicitante, labor)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para registrar toda la entrega", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        fun finalizarBloqueo() {
            agroEntregaProcesando = false
            (view as? Button)?.text = "Registrar todos"
            actualizarBotones()
        }

        fun registrar(foto: String) {
            val lineas = agroEntregaLineas.toList()
            registrarSalidaMultipleAgro(
                lineas,
                solicitante.text.toString().trim(),
                labor.text.toString().trim(),
                observaciones.text.toString(),
                foto,
                onSuccess = {
                    finalizarBloqueo()
                    limpiarEstadoEntregaAgro()
                    saved("Salida de Agroqu\u00edmicos registrada con ${lineas.size} linea(s)")
                    showQuimicoForm()
                },
                onFailure = { error ->
                    Toast.makeText(this, error.localizedMessage ?: "No se pudo registrar la salida", Toast.LENGTH_LONG).show()
                    finalizarBloqueo()
                },
            )
        }

        agroEntregaProcesando = true
        (view as? Button)?.apply {
            isEnabled = false
            alpha = 0.55f
            text = "Registrando..."
        }
        val evidenciaLocal = uriLocalEvidencia.ifBlank { urlEvidencia.takeIf { evidenciaEsUriLocal(it) }.orEmpty() }
        when {
            urlEvidencia.isNotBlank() && !evidenciaEsUriLocal(urlEvidencia) -> registrar(urlEvidencia)
            evidenciaLocal.isNotBlank() -> {
                Toast.makeText(this, "Subiendo evidencia de Agroqu\u00edmicos...", Toast.LENGTH_SHORT).show()
                subirEvidenciaCloud(evidenciaLocal, ModulosInventario.AGROQUIMICOS) { url ->
                    if (url.isBlank()) {
                        Toast.makeText(this, "No se pudo subir la evidencia", Toast.LENGTH_LONG).show()
                        finalizarBloqueo()
                    } else {
                        urlEvidencia = url
                        agroDraftUrlEvidencia = url
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

private fun MainActivity.showDialogNuevoAgroquimicoMultiple(onDone: (AgroProductoMeta) -> Unit) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()
    root.addView(TextView(this).apply {
        text = "Nuevo agroqu\u00edmico"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(verdeOscuro)
        setPadding(0, 0, 0, dp(12))
    })

    val categorias = (QuimicosCanonicos.items.filter { it.modulo == ModulosInventario.AGROQUIMICOS }.map { it.categoria } +
        agroProductosPersonalizados.values.map { it.categoria })
        .distinctBy { normalizarBusqueda(it) }
        .sortedBy { normalizarBusqueda(it) }
        .ifEmpty { listOf("Fertilizante quimico") }
    val categoriaExistente = spinner(root, "Categoria existente", categorias)
    val categoriaNueva = field(root, "Categoria nueva", "Opcional")
    val subcategoria = field(root, "Subcategoria / uso *", "Ej: Foliar, Drench, Control sanitario")
    val producto = field(root, "Producto *", "Nombre del agroqu\u00edmico")
    val codigo = field(root, "Codigo original *", "Ej: FER200")
    val ubicacion = field(root, "Ubicacion *", "BODEGA AZUL, COP, PORTUGUESA u otra")
    ubicacion.setText(QuimicosCanonicos.UBICACION_BODEGA_AZUL)
    val unidad = field(root, "Unidad *", "ML, GRAMO, LITRO, KG...")

    lateinit var crear: Button
    fun desbloquear() {
        crear.isEnabled = true
        crear.alpha = 1f
        crear.text = "Crear agroqu\u00edmico"
    }
    crear = primaryButton("Crear agroqu\u00edmico") {
        if (!required(subcategoria, producto, codigo, ubicacion, unidad)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para crear el agroqu\u00edmico", Toast.LENGTH_LONG).show()
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
        if (ModulosInventario.moduloQuimico(categoriaValor, ubicacionValor) != ModulosInventario.AGROQUIMICOS) {
            Toast.makeText(this, "La categoria o ubicacion corresponde a Lubricantes taller", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        val documentoId = idQuimicoArea(codigoValor, ubicacionValor)
        val nuevo = AgroProductoMeta(
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
                "modulo" to ModulosInventario.AGROQUIMICOS,
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
                registrarAgroEnCatalogoLocal(nuevo)
                saved("Agroqu\u00edmico creado")
                dialog.dismiss()
                onDone(nuevo)
            }.addOnFailureListener { error ->
                desbloquear()
                Toast.makeText(this, error.localizedMessage ?: "No se pudo crear el agroqu\u00edmico", Toast.LENGTH_LONG).show()
            }
        }
    }
    root.addView(crear)
    dialog.setContentView(root)
    dialog.show()
}

private fun MainActivity.registrarSalidaMultipleAgro(
    lineas: List<AgroEntregaLinea>,
    solicitante: String,
    labor: String,
    observaciones: String,
    fotoUrl: String,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null,
) {
    if (lineas.isEmpty()) {
        onFailure?.invoke(IllegalStateException("No hay agroqu\u00edmicos para registrar"))
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
        val entregaId = "AGRO-${System.currentTimeMillis()}"
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
                val moduloCalculado = ModulosInventario.moduloQuimico(categoriaDoc, ubicacionDoc)
                val esLubricante = ModulosInventario.esModuloLubricantesTaller(moduloDoc) ||
                    ModulosInventario.esModuloLubricantesTaller(moduloCalculado)
                val esAgro = ModulosInventario.esModuloAgroquimico(moduloDoc) ||
                    (moduloDoc.isBlank() && moduloCalculado == ModulosInventario.AGROQUIMICOS)
                if (esLubricante || !esAgro || !ModulosInventario.modulosCompatibles(moduloDoc, ModulosInventario.AGROQUIMICOS)) {
                    throw IllegalStateException("El documento $documentoId no pertenece a Agroquimicos")
                }
                val coincide = snapshot.id == linea.documentoId &&
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
            val resultados = mutableListOf<AgroResultadoSalida>()
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
                    "modulo" to ModulosInventario.AGROQUIMICOS,
                    "tipoMovimiento" to "Salida",
                    "item" to linea.producto,
                    "categoria" to linea.categoria,
                    "subcategoria" to linea.subcategoria,
                    "referencia" to linea.codigoOriginal,
                    "codigo_interno" to codigoExistencia(snapshot),
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
                resultados += AgroResultadoSalida(linea.documentoId, linea.producto, stockAnterior, stockNuevo)
            }
            resultados
        }.addOnSuccessListener { resultados ->
            resultados.forEach { resultado ->
                registrarCambioLocal(
                    "SALIDA_STOCK",
                    ModulosInventario.AGROQUIMICOS,
                    resultado.documentoId,
                    "Salida multiple: ${resultado.producto}",
                    resultado.stockAnterior.toString(),
                    resultado.stockNuevo.toString(),
                )
            }
            onSuccess?.invoke()
        }.addOnFailureListener { onFailure?.invoke(it) }
    }
}
