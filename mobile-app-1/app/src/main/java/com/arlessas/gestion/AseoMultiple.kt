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

private data class AseoEntregaLinea(
    val id: Long = System.nanoTime(),
    val categoria: String,
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

private data class AseoResultadoSalida(
    val documentoId: String,
    val producto: String,
    val stockAnterior: Double,
    val stockNuevo: Double,
)

private val aseoEntregaLineas = mutableListOf<AseoEntregaLinea>()
private var aseoEntregaEditandoId: Long? = null
private var aseoEntregaProcesando = false
private var aseoDraftCategoria = ""
private var aseoDraftProducto = ""
private var aseoDraftCantidad = "1"
private var aseoDraftSolicitante = ""
private var aseoDraftArea = ""
private var aseoDraftObservaciones = ""
private var aseoDraftUrlEvidencia = ""
private var aseoDraftUriEvidencia = ""
private var aseoProductosRemotosCargados = false
private var aseoProductosRemotosCargando = false

private fun MainActivity.registrarProductoAseoEnCatalogoLocal(producto: AseoCanonico) {
    AseoCanonicos.registrarProductoPersonalizado(producto)
    val moduloMap = catalogoCargado.getOrPut(AseoCanonicos.MODULO) { mutableMapOf() }
    val categoriaMap = moduloMap.getOrPut(producto.categoria) { mutableMapOf() }
    val referencias = categoriaMap.getOrPut(producto.producto) { mutableListOf() }
    if (!referencias.contains("N/A")) referencias.add("N/A")
}

private fun MainActivity.cargarProductosAseoPersonalizados() {
    if (aseoProductosRemotosCargados || aseoProductosRemotosCargando || !isNetworkAvailable()) return
    aseoProductosRemotosCargando = true
    firestore.collection(AseoCanonicos.COLECCION).get()
        .addOnSuccessListener { snapshot ->
            var agregados = 0
            snapshot.documents.forEach { documento ->
                val opcion = aseoUbicacionDesdeDoc(documento) ?: return@forEach
                val yaExistia = AseoCanonicos.buscarPorDocumento(opcion.documentoId) != null
                val producto = AseoCanonico(
                    codigoInterno = opcion.documentoId,
                    piso = documento.getLong("piso")?.toInt() ?: AseoCanonicos.pisoDesdeCodigo(opcion.codigoOriginal),
                    categoria = opcion.categoria,
                    producto = opcion.item,
                    unidad = opcion.unidad,
                    stockActual = opcion.cantidad,
                )
                registrarProductoAseoEnCatalogoLocal(producto)
                if (!yaExistia) agregados++
            }
            aseoProductosRemotosCargando = false
            aseoProductosRemotosCargados = true
            if (agregados > 0 && currentScreenId.startsWith("ASEO|")) showAseoForm()
        }
        .addOnFailureListener {
            aseoProductosRemotosCargando = false
        }
}

private fun limpiarEstadoEntregaAseo() {
    aseoEntregaLineas.clear()
    aseoEntregaEditandoId = null
    aseoEntregaProcesando = false
    aseoDraftCategoria = ""
    aseoDraftProducto = ""
    aseoDraftCantidad = "1"
    aseoDraftSolicitante = ""
    aseoDraftArea = ""
    aseoDraftObservaciones = ""
    aseoDraftUrlEvidencia = ""
    aseoDraftUriEvidencia = ""
}

private fun esSeleccionAseoValida(valor: String): Boolean {
    val normalizado = normalizarBusqueda(valor)
    return valor.isNotBlank() &&
        !normalizado.startsWith("selecciona") &&
        !normalizado.startsWith("sin-") &&
        !normalizado.startsWith("no-hay")
}

internal fun MainActivity.showAseoFormMultipleInterno(
    pItem: String = "",
    pCant: String = "",
    pSol: String = "",
    pLab: String = "",
    pCat: String = "",
) {
    cargarProductosAseoPersonalizados()
    if (pCat.isNotBlank()) aseoDraftCategoria = pCat
    if (pItem.isNotBlank()) aseoDraftProducto = pItem
    if (pCant.isNotBlank()) aseoDraftCantidad = pCant
    if (pSol.isNotBlank()) aseoDraftSolicitante = pSol
    if (pLab.isNotBlank()) aseoDraftArea = pLab

    fun salirAseo() {
        if (aseoEntregaProcesando) {
            Toast.makeText(this, "Espera a que termine el registro de la entrega", Toast.LENGTH_SHORT).show()
            return
        }
        if (aseoEntregaLineas.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Descartar entrega")
                .setMessage("Hay productos agregados sin registrar. Si sales, se descartara esta entrega.")
                .setPositiveButton("Descartar entrega") { _, _ ->
                    limpiarEstadoEntregaAseo()
                    showMainMenu()
                }
                .setNegativeButton("Seguir editando", null)
                .show()
        } else {
            limpiarEstadoEntregaAseo()
            showMainMenu()
        }
    }

    currentScreenRenderer = {
        showAseoForm(
            pItem = aseoDraftProducto,
            pCant = aseoDraftCantidad,
            pSol = aseoDraftSolicitante,
            pLab = aseoDraftArea,
            pCat = aseoDraftCategoria,
        )
    }
    val root = baseScreen(
        "ASEO",
        "Registra salidas y entradas de productos por area.",
        backAction = { salirAseo() },
    )

    fun catalogoAseo() = catalogoCargado[AseoCanonicos.MODULO] ?: mapOf()
    fun categoriasAseo(): List<String> {
        val catalogo = catalogoAseo().keys.toList().sorted()
        return catalogo.ifEmpty { AseoCanonicos.items.map { it.categoria }.distinct().sorted() }
    }
    fun productosAseo(categoria: String): List<String> {
        val catalogo = (catalogoAseo()[categoria] as? Map<String, Any>)?.keys?.toList()?.sorted().orEmpty()
        return catalogo.ifEmpty {
            AseoCanonicos.items.filter { it.categoria == categoria }.map { it.producto }.distinct().sorted()
        }
    }

    val categoriaSpinner = spinner(root, "Categoria *", categoriasAseo().ifEmpty { listOf("Sin categorias") })
    val productoSpinner = spinner(root, "Producto *", listOf("Selecciona categoria"))
    val codigoInterno = codigoInternoField(root, "Codigo", "Escribe o selecciona producto", scan = false)

    val stockLabel = TextView(this).apply {
        text = "Selecciona un producto para ver stock y ubicaciones"
        textSize = 13f
        setTextColor(verdeOscuro)
        setPadding(0, dp(6), 0, dp(6))
    }
    root.addView(stockLabel)

    var codigoOriginalSeleccionado = ""
    var documentoSeleccionado = ""
    var ubicacionesActuales = listOf<AseoUbicacionStock>()
    var ubicacionSeleccionada: AseoUbicacionStock? = null
    var stockVerificado = false
    var tokenStockVerificado = ""
    var consultaStockActual = 0
    var eventosSpinnerActivos = false
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
        productoSpinner.selectedItem?.toString().orEmpty(),
        normalizarCodigoInterno(codigo),
    ).joinToString("|") { normalizarBusqueda(it) }

    fun limpiarSeleccionStock() {
        codigoOriginalSeleccionado = ""
        documentoSeleccionado = ""
        ubicacionesActuales = emptyList()
        ubicacionSeleccionada = null
        stockVerificado = false
        tokenStockVerificado = ""
    }

    fun actualizarResumen() {
        val total = ubicacionesActuales.sumOf { it.cantidad }
        val unidad = ubicacionSeleccionada?.unidad
            ?: ubicacionesActuales.firstOrNull { it.unidad.isNotBlank() }?.unidad
            ?: ""
        val detalle = ubicacionesActuales.joinToString("\n") { opcion ->
            val marca = if (opcion.documentoId == ubicacionSeleccionada?.documentoId) "Seleccionado - " else ""
            "$marca${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}"
        }
        stockLabel.text = when {
            ubicacionesActuales.isEmpty() -> "Stock no disponible"
            else -> "$detalle\nTotal: ${cantidadTexto(total)} $unidad"
        }
        stockLabel.setTextColor(if ((ubicacionSeleccionada?.cantidad ?: total) <= 0.0) Color.RED else verdeOscuro)
        vistaEntrada?.let { vista ->
            val habilitada = ubicacionSeleccionada != null
            vista.isEnabled = habilitada
            vista.alpha = if (habilitada) 1f else 0.55f
        }
    }

    fun seleccionarUbicacion(opcion: AseoUbicacionStock?, token: String = tokenSeleccion()) {
        ubicacionSeleccionada = opcion
        if (opcion == null) {
            documentoSeleccionado = ""
            stockVerificado = false
            tokenStockVerificado = ""
        } else {
            documentoSeleccionado = opcion.documentoId
            codigoOriginalSeleccionado = opcion.codigoOriginal
            escribirCodigoSinBusqueda(opcion.codigoOriginal)
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
        cargarUbicacionesAseo(codigoOriginal, producto, { opciones ->
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenSeleccion()) {
                return@cargarUbicacionesAseo
            }
            ubicacionesActuales = opciones
            val seleccion = opciones.find { it.documentoId == documentoPreferido }
                ?: opciones.find { it.documentoId == documentoSeleccionado }
                ?: opciones.find { normalizarBusqueda(it.ubicacion) == normalizarBusqueda(ubicacionPreferida) }
                ?: opciones.firstOrNull()
            seleccionarUbicacion(seleccion, token)
        }, {
            if (!pantallaActiva() || consulta != consultaStockActual || token != tokenSeleccion()) return@cargarUbicacionesAseo
            limpiarSeleccionStock()
            stockLabel.text = "Stock no disponible en linea"
            stockLabel.setTextColor(Color.RED)
            actualizarResumen()
        })
    }

    fun seleccionarSpinners(categoria: String, producto: String, onDone: () -> Unit = {}) {
        seleccionarSpinnerPorTexto(categoriaSpinner, categoria) {
            val productos = productosAseo(categoria).ifEmpty { listOf("Sin productos") }
            productoSpinner.tag = "SINCRO"
            productoSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, productos).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            seleccionarSpinnerPorTexto(productoSpinner, producto) {
                onDone()
            }
        }
    }

    fun aplicarCanonico(canonico: AseoCanonico?) {
        if (canonico == null) {
            consultaStockActual++
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            actualizarResumen()
            return
        }
        documentoSeleccionado = canonico.documentoId
        codigoOriginalSeleccionado = canonico.codigoOriginal
        escribirCodigoSinBusqueda(canonico.codigoOriginal)
        cargarUbicaciones(
            codigoOriginal = canonico.codigoOriginal,
            producto = canonico.item,
            ubicacionPreferida = canonico.ubicacion,
            documentoPreferido = canonico.documentoId,
        )
    }

    fun poblarProductos(categoria: String) {
        consultaStockActual++
        val productos = productosAseo(categoria).ifEmpty { listOf("Selecciona producto") }
        productoSpinner.tag = "SINCRO"
        productoSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, productos).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        productoSpinner.post { if (productoSpinner.tag == "SINCRO") productoSpinner.tag = null }
    }

    setupCodigoInternoAseo(codigoInterno) { producto ->
        if (!eventosSpinnerActivos) return@setupCodigoInternoAseo
        consultaStockActual++
        documentoSeleccionado = producto.documentoId.ifBlank { producto.codigoInterno }
        codigoOriginalSeleccionado = producto.codigoOriginal.ifBlank { producto.codigoInterno }
        seleccionarSpinners(producto.categoria, producto.item) {
            cargarUbicaciones(
                codigoOriginal = codigoOriginalSeleccionado,
                producto = producto.item,
                ubicacionPreferida = producto.ubicacion,
                documentoPreferido = documentoSeleccionado,
            )
        }
    }

    setupSearchBar(root, AseoCanonicos.MODULO) { _, categoria, producto, _ ->
        if (!eventosSpinnerActivos) return@setupSearchBar
        consultaStockActual++
        seleccionarSpinners(categoria, producto) {
            aseoDraftCategoria = categoria
            aseoDraftProducto = producto
            aplicarCanonico(AseoCanonicos.buscar(categoria, producto))
        }
    }

    categoriaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || categoriaSpinner.adapter?.count == 0 || categoriaSpinner.tag == "SINCRO") return
            val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
            if (!esSeleccionAseoValida(categoria)) return
            aseoDraftCategoria = categoria
            aseoDraftProducto = ""
            limpiarSeleccionStock()
            escribirCodigoSinBusqueda("")
            poblarProductos(categoria)
            actualizarResumen()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    productoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (!eventosSpinnerActivos || productoSpinner.adapter?.count == 0 || productoSpinner.tag == "SINCRO") return
            val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
            val producto = productoSpinner.selectedItem?.toString().orEmpty()
            if (!esSeleccionAseoValida(categoria) || !esSeleccionAseoValida(producto)) return
            aseoDraftCategoria = categoria
            aseoDraftProducto = producto
            limpiarSeleccionStock()
            aplicarCanonico(AseoCanonicos.buscar(categoria, producto))
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    root.post {
        val categoriaInicial = categoriaSpinner.selectedItem?.toString().orEmpty()
        if (esSeleccionAseoValida(categoriaInicial)) poblarProductos(categoriaInicial)
        eventosSpinnerActivos = true
        actualizarResumen()
        when {
            aseoDraftCategoria.isNotBlank() && aseoDraftProducto.isNotBlank() -> {
                seleccionarSpinners(aseoDraftCategoria, aseoDraftProducto) {
                    aplicarCanonico(AseoCanonicos.buscar(aseoDraftCategoria, aseoDraftProducto))
                }
            }
            pCat.isNotBlank() && pItem.isNotBlank() -> {
                seleccionarSpinners(pCat, pItem) { aplicarCanonico(AseoCanonicos.buscar(pCat, pItem)) }
            }
            pItem.isNotBlank() -> {
                resolverProductoCatalogo(AseoCanonicos.MODULO, pItem)?.let { producto ->
                    seleccionarSpinners(producto.categoria, producto.item) {
                        aplicarCanonico(AseoCanonicos.buscar(producto.categoria, producto.item))
                    }
                }
            }
        }
    }

    val cantidad = field(root, "Cantidad *", "Ej: 1", number = true)
    cantidad.setText(pCant.ifBlank { aseoDraftCantidad.ifBlank { "1" } })
    val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
    solicitante.setText(pSol.ifBlank { aseoDraftSolicitante })
    val area = field(root, "Area / destino *", "Ej: cocina, comedor, banos")
    area.setText(pLab.ifBlank { aseoDraftArea })
    val observaciones = field(root, "Observaciones", "Opcional")
    observaciones.setText(aseoDraftObservaciones)
    var urlEvidencia = aseoDraftUrlEvidencia
    var uriLocalEvidencia = aseoDraftUriEvidencia

    fun guardarDraft() {
        aseoDraftCategoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        aseoDraftProducto = productoSpinner.selectedItem?.toString().orEmpty().takeIf { esSeleccionAseoValida(it) }.orEmpty()
        aseoDraftCantidad = cantidad.text.toString().ifBlank { "1" }
        aseoDraftSolicitante = solicitante.text.toString()
        aseoDraftArea = area.text.toString()
        aseoDraftObservaciones = observaciones.text.toString()
        aseoDraftUrlEvidencia = urlEvidencia
        aseoDraftUriEvidencia = uriLocalEvidencia
    }

    fun observar(campo: android.widget.EditText, accion: (String) -> Unit) {
        campo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = accion(s?.toString().orEmpty())
        })
    }
    observar(cantidad) { aseoDraftCantidad = it.ifBlank { "1" } }
    observar(solicitante) { aseoDraftSolicitante = it }
    observar(area) { aseoDraftArea = it }
    observar(observaciones) { aseoDraftObservaciones = it }

    root.addView(evidenceButton {
        capturarEvidencia { uri ->
            uriLocalEvidencia = uri
            urlEvidencia = ""
            aseoDraftUriEvidencia = uri
            aseoDraftUrlEvidencia = ""
            mostrarPrevisualizacionEvidencia(root, uri) {
                uriLocalEvidencia = ""
                urlEvidencia = ""
                aseoDraftUriEvidencia = ""
                aseoDraftUrlEvidencia = ""
            }
        }
    })
    if (uriLocalEvidencia.isNotBlank()) {
        mostrarPrevisualizacionEvidencia(root, uriLocalEvidencia) {
            uriLocalEvidencia = ""
            urlEvidencia = ""
            aseoDraftUriEvidencia = ""
            aseoDraftUrlEvidencia = ""
        }
    }

    val gestionAcciones = gestionNuevoEntradaRow(
        onNuevo = {
            guardarDraft()
            showDialogNuevoProductoAseoMultiple { nuevo ->
                aseoDraftCategoria = nuevo.categoria
                aseoDraftProducto = nuevo.producto
                seleccionarSpinners(nuevo.categoria, nuevo.producto) {
                    documentoSeleccionado = nuevo.documentoId
                    codigoOriginalSeleccionado = nuevo.codigoOriginal
                    escribirCodigoSinBusqueda(nuevo.codigoOriginal)
                    cargarUbicaciones(
                        nuevo.codigoOriginal,
                        nuevo.producto,
                        nuevo.ubicacion,
                        nuevo.documentoId,
                    )
                }
            }
        },
        onEntrada = {
            guardarDraft()
            val seleccion = ubicacionSeleccionada
            if (seleccion == null) {
                Toast.makeText(this, "Selecciona un producto primero", Toast.LENGTH_SHORT).show()
            } else {
                showDialogEntradaStockAseo(seleccion) {
                    cargarUbicaciones(
                        seleccion.codigoOriginal,
                        seleccion.item,
                        seleccion.ubicacion,
                        seleccion.documentoId,
                    )
                }
            }
        },
    )
    vistaEntrada = gestionAcciones.getChildAt(1)
    root.addView(gestionAcciones)
    actualizarResumen()

    fun lineaDesdeFormulario(): AseoEntregaLinea? {
        val categoria = categoriaSpinner.selectedItem?.toString().orEmpty()
        val producto = productoSpinner.selectedItem?.toString().orEmpty()
        val seleccion = ubicacionSeleccionada
        if (!esSeleccionAseoValida(categoria) || !esSeleccionAseoValida(producto) || seleccion == null) {
            Toast.makeText(this, "Selecciona producto y ubicacion validos", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!stockVerificado || tokenStockVerificado != tokenSeleccion() || documentoSeleccionado != seleccion.documentoId) {
            Toast.makeText(this, "Espera la consulta de stock antes de agregar", Toast.LENGTH_SHORT).show()
            return null
        }
        if (
            !AseoCanonicos.productoCoincide(seleccion.item, producto) ||
            normalizarBusqueda(seleccion.categoria) != normalizarBusqueda(categoria)
        ) {
            Toast.makeText(this, "La ubicacion consultada no coincide con el producto seleccionado", Toast.LENGTH_LONG).show()
            return null
        }
        val codigoEscrito = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
        val codigoSeleccion = normalizarCodigoInterno(seleccion.codigoOriginal)
        if (codigoEscrito.isNotBlank() && codigoEscrito != codigoSeleccion) {
            Toast.makeText(this, "El codigo no coincide con el producto seleccionado", Toast.LENGTH_SHORT).show()
            return null
        }
        val cantidadValor = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        if (cantidadValor <= 0.0) {
            Toast.makeText(this, "La cantidad debe ser mayor a cero", Toast.LENGTH_SHORT).show()
            return null
        }
        if (cantidadValor > seleccion.cantidad) {
            Toast.makeText(
                this,
                "Stock insuficiente. Disponible: ${cantidadTexto(seleccion.cantidad)} ${seleccion.unidad}",
                Toast.LENGTH_LONG,
            ).show()
            return null
        }
        return AseoEntregaLinea(
            categoria = categoria,
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
        botonAgregar?.text = if (aseoEntregaEditandoId != null) "Actualizar producto" else "Agregar a la entrega"
        val habilitado = aseoEntregaLineas.isNotEmpty() && !aseoEntregaProcesando
        botonRegistrar?.isEnabled = habilitado
        botonRegistrar?.alpha = if (habilitado) 1f else 0.55f
    }

    fun aplicarLinea(linea: AseoEntregaLinea) {
        aseoEntregaEditandoId = linea.id
        aseoDraftCategoria = linea.categoria
        aseoDraftProducto = linea.producto
        seleccionarSpinners(linea.categoria, linea.producto) {
            documentoSeleccionado = linea.documentoId
            codigoOriginalSeleccionado = linea.codigoOriginal
            escribirCodigoSinBusqueda(linea.codigoOriginal)
            cargarUbicaciones(
                linea.codigoOriginal,
                linea.producto,
                linea.ubicacion,
                linea.documentoId,
            )
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
        if (aseoEntregaLineas.isEmpty()) {
            productosAgregados.addView(TextView(this).apply {
                text = "Aun no hay productos en esta entrega."
                textSize = 13f
                setTextColor(gris)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
            })
            actualizarBotones()
            return
        }
        aseoEntregaLineas.forEachIndexed { index, linea ->
            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(4), 0, dp(8)) }
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
                aseoEntregaLineas.removeAll { it.id == linea.id }
                if (aseoEntregaEditandoId == linea.id) aseoEntregaEditandoId = null
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
        val idEditando = aseoEntregaEditandoId
        val indiceEditando = idEditando?.let { id -> aseoEntregaLineas.indexOfFirst { it.id == id } } ?: -1
        val indiceDuplicado = aseoEntregaLineas.indexOfFirst { it.clave() == nueva.clave() && it.id != idEditando }
        val total = nueva.cantidad + if (indiceDuplicado >= 0) aseoEntregaLineas[indiceDuplicado].cantidad else 0.0
        if (total > nueva.stockDisponible) {
            Toast.makeText(this, "Stock insuficiente para la cantidad acumulada de ${nueva.producto}", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        when {
            indiceEditando >= 0 && indiceDuplicado >= 0 -> {
                aseoEntregaLineas[indiceDuplicado] = aseoEntregaLineas[indiceDuplicado].copy(
                    cantidad = total,
                    stockDisponible = nueva.stockDisponible,
                )
                aseoEntregaLineas.removeAt(indiceEditando)
            }
            indiceEditando >= 0 -> aseoEntregaLineas[indiceEditando] = nueva.copy(id = aseoEntregaLineas[indiceEditando].id)
            indiceDuplicado >= 0 -> aseoEntregaLineas[indiceDuplicado] = aseoEntregaLineas[indiceDuplicado].copy(
                cantidad = total,
                stockDisponible = nueva.stockDisponible,
            )
            else -> aseoEntregaLineas.add(nueva)
        }
        aseoEntregaEditandoId = null
        cantidad.setText("1")
        aseoDraftCantidad = "1"
        renderProductos()
        stockVerificado = false
        tokenStockVerificado = ""
        cargarUbicaciones(
            nueva.codigoOriginal,
            nueva.producto,
            nueva.ubicacion,
            nueva.documentoId,
        )
        Toast.makeText(this, "Producto agregado a la entrega", Toast.LENGTH_SHORT).show()
    }
    botonAgregar?.let { root.addView(it) }
    root.addView(productosAgregados)

    botonRegistrar = primaryButton("Registrar todos") { view ->
        if (aseoEntregaProcesando) return@primaryButton
        guardarDraft()
        if (aseoEntregaLineas.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un producto", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        if (!required(solicitante, area)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para registrar toda la entrega", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        fun finalizarBloqueo() {
            aseoEntregaProcesando = false
            (view as? Button)?.text = "Registrar todos"
            actualizarBotones()
        }

        fun registrar(foto: String) {
            val lineas = aseoEntregaLineas.toList()
            registrarSalidaMultipleAseo(
                lineas = lineas,
                solicitante = solicitante.text.toString().trim(),
                area = area.text.toString().trim(),
                observaciones = observaciones.text.toString(),
                fotoUrl = foto,
                onSuccess = {
                    finalizarBloqueo()
                    limpiarEstadoEntregaAseo()
                    saved("Salida ASEO registrada con ${lineas.size} linea(s)")
                    showAseoForm()
                },
                onFailure = { error ->
                    Toast.makeText(this, error.localizedMessage ?: "No se pudo registrar la salida ASEO", Toast.LENGTH_LONG).show()
                    finalizarBloqueo()
                },
            )
        }

        aseoEntregaProcesando = true
        (view as? Button)?.apply {
            isEnabled = false
            alpha = 0.55f
            text = "Registrando..."
        }
        val evidenciaLocal = uriLocalEvidencia.ifBlank {
            urlEvidencia.takeIf { evidenciaEsUriLocal(it) }.orEmpty()
        }
        when {
            urlEvidencia.isNotBlank() && !evidenciaEsUriLocal(urlEvidencia) -> registrar(urlEvidencia)
            evidenciaLocal.isNotBlank() -> {
                Toast.makeText(this, "Subiendo evidencia ASEO...", Toast.LENGTH_SHORT).show()
                subirEvidenciaCloud(evidenciaLocal, AseoCanonicos.MODULO) { url ->
                    if (url.isBlank()) {
                        Toast.makeText(this, "No se pudo subir la evidencia", Toast.LENGTH_LONG).show()
                        finalizarBloqueo()
                    } else {
                        urlEvidencia = url
                        aseoDraftUrlEvidencia = url
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

private fun MainActivity.showDialogNuevoProductoAseoMultiple(onDone: (AseoCanonico) -> Unit) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()
    root.addView(TextView(this).apply {
        text = "Nuevo producto ASEO"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(verdeOscuro)
        setPadding(0, 0, 0, dp(12))
    })

    val categorias = AseoCanonicos.items.map { it.categoria }.distinct().sorted()
        .ifEmpty { listOf(AseoCanonicos.CATEGORIA_PRINCIPAL) }
    val categoriaExistente = spinner(root, "Categoria existente", categorias)
    val categoriaNueva = field(root, "Categoria nueva", "Opcional")
    val producto = field(root, "Producto *", "Nombre del producto")
    val codigo = field(root, "Codigo interno *", "Ej: H06-001")
    val piso = field(root, "Piso *", "Ej: 6", number = true)
    val unidad = field(root, "Unidad *", "UNIDAD, PAR, PAQUETE, ROLLO, ML...")

    lateinit var crear: Button
    crear = primaryButton("Crear producto ASEO") {
        if (!required(producto, codigo, piso, unidad)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Necesitas conexion para crear el producto ASEO", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        val codigoNormalizado = AseoCanonicos.normalizarCodigo(codigo.text.toString())
        val pisoValor = piso.text.toString().toIntOrNull()
        val categoriaValor = categoriaNueva.text.toString().trim()
            .ifBlank { categoriaExistente.selectedItem?.toString().orEmpty() }
        val productoValor = producto.text.toString().trim()
        val unidadValor = unidad.text.toString().trim().uppercase().replace(Regex("\\s+"), " ")
        if (codigoNormalizado.isBlank() || categoriaValor.isBlank() || productoValor.isBlank() || pisoValor == null || pisoValor < 0) {
            Toast.makeText(this, "Completa codigo, categoria, producto, piso y unidad validos", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        val nuevo = AseoCanonico(
            codigoInterno = codigoNormalizado,
            piso = pisoValor,
            categoria = categoriaValor,
            producto = productoValor,
            unidad = unidadValor,
            stockActual = 0.0,
        )
        crear.isEnabled = false
        crear.alpha = 0.55f
        crear.text = "Creando..."
        val referencia = firestore.collection(AseoCanonicos.COLECCION).document(codigoNormalizado)
        referencia.get()
            .addOnSuccessListener { existente ->
                if (existente.exists()) {
                    crear.isEnabled = true
                    crear.alpha = 1f
                    crear.text = "Crear producto ASEO"
                    Toast.makeText(this, "Ya existe un producto ASEO con el codigo $codigoNormalizado", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                referencia.set(nuevo.firestoreData(0.0))
                    .addOnSuccessListener {
                        registrarProductoAseoEnCatalogoLocal(nuevo)
                        saved("Producto ASEO creado")
                        dialog.dismiss()
                        onDone(nuevo)
                    }
                    .addOnFailureListener { error ->
                        crear.isEnabled = true
                        crear.alpha = 1f
                        crear.text = "Crear producto ASEO"
                        Toast.makeText(this, error.localizedMessage ?: "No se pudo crear el producto ASEO", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { error ->
                crear.isEnabled = true
                crear.alpha = 1f
                crear.text = "Crear producto ASEO"
                Toast.makeText(this, error.localizedMessage ?: "No se pudo verificar el codigo ASEO", Toast.LENGTH_LONG).show()
            }
    }
    root.addView(crear)
    dialog.setContentView(root)
    dialog.show()
}

private fun MainActivity.registrarSalidaMultipleAseo(
    lineas: List<AseoEntregaLinea>,
    solicitante: String,
    area: String,
    observaciones: String,
    fotoUrl: String,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null,
) {
    if (lineas.isEmpty()) {
        onFailure?.invoke(IllegalStateException("No hay productos para registrar"))
        return
    }
    if (!isNetworkAvailable()) {
        onFailure?.invoke(IllegalStateException("Sin conexion no se puede registrar la entrega completa"))
        return
    }

    obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
        val fecha = now()
        val entregaId = "ASEO-${System.currentTimeMillis()}"
        firestore.runTransaction { transaction ->
            val referencias = lineas.associate { linea ->
                if (linea.documentoId.isBlank()) throw IllegalStateException("Documento faltante para ${linea.producto}")
                linea.documentoId to firestore.collection(AseoCanonicos.COLECCION).document(linea.documentoId)
            }
            val opciones = mutableMapOf<String, AseoUbicacionStock>()
            val stocks = mutableMapOf<String, Double>()
            val cantidades = lineas.groupBy { it.documentoId }
                .mapValues { (_, productos) -> productos.sumOf { it.cantidad } }

            referencias.forEach { (documentoId, referencia) ->
                val snapshot = transaction.get(referencia)
                if (!snapshot.exists()) throw IllegalStateException("No existe el documento ASEO $documentoId")
                val opcion = aseoUbicacionDesdeDoc(snapshot)
                    ?: throw IllegalStateException("No se pudo interpretar el documento ASEO $documentoId")
                val linea = lineas.first { it.documentoId == documentoId }
                val documentoCoincide = snapshot.id == linea.documentoId
                val codigoCoincide = AseoCanonicos.normalizarCodigo(opcion.codigoOriginal) ==
                    AseoCanonicos.normalizarCodigo(linea.codigoOriginal)
                val productoCoincide = AseoCanonicos.productoCoincide(opcion.item, linea.producto)
                val categoriaCoincide = normalizarBusqueda(opcion.categoria) == normalizarBusqueda(linea.categoria)
                val ubicacionCoincide = normalizarBusqueda(opcion.ubicacion) == normalizarBusqueda(linea.ubicacion)
                val unidadCoincide = normalizarBusqueda(opcion.unidad) == normalizarBusqueda(linea.unidad)
                if (!documentoCoincide || !codigoCoincide || !productoCoincide || !categoriaCoincide || !ubicacionCoincide || !unidadCoincide) {
                    throw IllegalStateException("El documento $documentoId ya no coincide con ${linea.producto}")
                }
                val solicitado = cantidades[documentoId] ?: 0.0
                val stockActual = numeroDocumento(snapshot, "stock_actual", "cantidad")
                if (solicitado <= 0.0) throw IllegalStateException("Cantidad invalida en ${linea.producto}")
                if (solicitado > stockActual) {
                    throw IllegalStateException(
                        "Stock insuficiente en ${linea.producto}. Disponible: ${cantidadTexto(stockActual)} ${linea.unidad}, " +
                            "solicitado: ${cantidadTexto(solicitado)} ${linea.unidad}"
                    )
                }
                opciones[documentoId] = opcion
                stocks[documentoId] = stockActual
            }

            val stockRestante = stocks.toMutableMap()
            val resultados = mutableListOf<AseoResultadoSalida>()
            lineas.forEachIndexed { index, linea ->
                val opcion = opciones[linea.documentoId]
                    ?: throw IllegalStateException("Inventario ASEO no disponible para ${linea.producto}")
                val referencia = referencias[linea.documentoId]
                    ?: throw IllegalStateException("Inventario ASEO no disponible para ${linea.producto}")
                val stockAnterior = stockRestante[linea.documentoId] ?: 0.0
                val stockNuevo = stockAnterior - linea.cantidad
                transaction.set(referencia, aseoFirestoreDataDesdeUbicacion(opcion, stockNuevo), SetOptions.merge())
                val observacionMovimiento = listOf("Ubicacion: ${linea.ubicacion}", observaciones)
                    .filter { it.isNotBlank() }
                    .joinToString(". ")
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to AseoCanonicos.MODULO,
                    "tipoMovimiento" to "Salida",
                    "item" to linea.producto,
                    "referencia" to "N/A",
                    "codigo_interno" to linea.codigoOriginal,
                    "codigo_original" to linea.codigoOriginal,
                    "documento_id" to linea.documentoId,
                    "categoria" to linea.categoria,
                    "ubicacion" to linea.ubicacion,
                    "cantidad" to cantidadTexto(linea.cantidad),
                    "unidad" to linea.unidad,
                    "solicitante" to solicitante,
                    "labor" to area,
                    "observaciones" to observacionMovimiento,
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
                resultados += AseoResultadoSalida(
                    documentoId = linea.documentoId,
                    producto = linea.producto,
                    stockAnterior = stockAnterior,
                    stockNuevo = stockNuevo,
                )
            }
            resultados
        }.addOnSuccessListener { resultados ->
            resultados.forEach { resultado ->
                registrarCambioLocal(
                    "SALIDA_STOCK",
                    AseoCanonicos.MODULO,
                    resultado.documentoId,
                    "Salida multiple: ${resultado.producto}",
                    resultado.stockAnterior.toString(),
                    resultado.stockNuevo.toString(),
                )
            }
            onSuccess?.invoke()
        }.addOnFailureListener { error ->
            onFailure?.invoke(error)
        }
    }
}
