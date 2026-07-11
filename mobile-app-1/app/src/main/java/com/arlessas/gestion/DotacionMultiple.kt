@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused")

package com.arlessas.gestion

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import java.util.Locale

private data class DotacionEntregaLinea(
    val id: Long = System.nanoTime(),
    val categoria: String,
    val prenda: String,
    val referencia: String,
    val codigoInterno: String,
    val documentoId: String,
    val cantidad: Int,
    val stockDisponible: Double,
    val unidad: String = "Unidad",
) {
    fun clave(): String = listOf(
        normalizarBusqueda(categoria),
        normalizarBusqueda(prenda),
        normalizarBusqueda(referencia),
        codigoInterno.normalizarCodigoDotacion(),
        documentoId.trim(),
    ).joinToString("|")
}

private val dotacionEntregaLineas = mutableListOf<DotacionEntregaLinea>()
private var dotacionEntregaEditandoId: Long? = null
private var dotacionEntregaProcesando = false
private var dotacionDraftCategoria = ""
private var dotacionDraftPrenda = ""
private var dotacionDraftReferencia = ""
private var dotacionDraftCantidad = "1"
private var dotacionDraftSolicitante = ""
private var dotacionDraftCargo = ""
private var dotacionDraftObservaciones = ""
private var dotacionDraftUrlEvidencia = ""
private var dotacionDraftUriEvidencia = ""

private fun String.normalizarCodigoDotacion(): String = trim()
    .uppercase(Locale.getDefault())
    .replace(Regex("[^A-Z0-9]+"), "")

private fun cantidadDotacionLegible(valor: Double): String {
    return if (valor % 1.0 == 0.0) valor.toInt().toString()
    else String.format(Locale.getDefault(), "%.2f", valor).trimEnd('0').trimEnd(',', '.')
}

private fun limpiarEstadoEntregaDotacion() {
    dotacionEntregaLineas.clear()
    dotacionEntregaEditandoId = null
    dotacionEntregaProcesando = false
    dotacionDraftCategoria = ""
    dotacionDraftPrenda = ""
    dotacionDraftReferencia = ""
    dotacionDraftCantidad = "1"
    dotacionDraftSolicitante = ""
    dotacionDraftCargo = ""
    dotacionDraftObservaciones = ""
    dotacionDraftUrlEvidencia = ""
    dotacionDraftUriEvidencia = ""
}

private fun esPlaceholderDotacion(valor: String): Boolean {
    val normalizado = normalizarBusqueda(valor)
    return valor.isBlank() ||
        normalizado.startsWith("selecciona") ||
        normalizado.startsWith("sin-") ||
        normalizado.startsWith("no-hay")
}

internal fun MainActivity.showDotacionForm(
    pItem: String = "",
    pCant: String = "",
    pSol: String = "",
    pCat: String = "",
    pRef: String = "",
) {
    if (pCat.isNotBlank()) dotacionDraftCategoria = pCat
    if (pItem.isNotBlank()) dotacionDraftPrenda = pItem
    if (pRef.isNotBlank()) dotacionDraftReferencia = pRef
    if (pCant.isNotBlank()) dotacionDraftCantidad = pCant
    if (pSol.isNotBlank()) dotacionDraftSolicitante = pSol

    fun salirDotacion() {
        if (dotacionEntregaLineas.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Descartar entrega")
                .setMessage("Hay prendas agregadas sin registrar. Si sales, se descartara esta entrega.")
                .setPositiveButton("Descartar") { _, _ ->
                    limpiarEstadoEntregaDotacion()
                    showMainMenu()
                }
                .setNegativeButton("Seguir editando", null)
                .show()
        } else {
            limpiarEstadoEntregaDotacion()
            showMainMenu()
        }
    }

    currentScreenRenderer = {
        showDotacionForm(
            pItem = dotacionDraftPrenda,
            pCant = dotacionDraftCantidad,
            pSol = dotacionDraftSolicitante,
            pCat = dotacionDraftCategoria,
            pRef = dotacionDraftReferencia,
        )
    }
    val root = baseScreen(
        "Entrega de Dotaci\u00f3n",
        "Control de vestuario y calzado para el personal.",
        backAction = { salirDotacion() },
    )

    val categoriasPermitidas = listOf("Parte Superior", "Parte Inferior", "Conjunto", "Calzado")
    val cargos = listOf(
        "Auxiliar de campo",
        "Auxiliar de vivero",
        "Tractorista",
        "Asistente",
        "Jefe de campo",
        "Cocinera",
        "Ingeniero",
    )
    val catalogoBase = catalogoCargado["Dotaci\u00f3n"] ?: emptyMap()
    val catalogoFormulario = categoriasPermitidas.associateWith { categoria ->
        catalogoBase[categoria]?.mapValues { (_, referencias) ->
            referencias.map { referenciaDotacionPresentable(it) }.distinct().toMutableList()
        }?.toMutableMap() ?: mutableMapOf()
    }
    fun catalogoComoMapAny(): Map<String, Any> = catalogoFormulario as Map<String, Any>

    val parteSpinner = spinner(root, "Parte del cuerpo *", catalogoFormulario.keys.toList())
    val prendaSpinner = spinner(root, "Prenda *", listOf("Selecciona parte"))
    val tallaSpinner = spinner(root, "Talla o referencia *", listOf("Selecciona prenda"))
    val codigoInterno = codigoInternoField(root, "C\u00f3digo interno", "Escribe el c\u00f3digo si existe", scan = false)
    var codigoSeleccionado = ""
    var documentoSeleccionado = ""
    var productoSeleccionado: ExistenciaProducto? = null
    var stockDisponibleValor = 0.0
    var stockVerificado = false
    var tokenVerificado = ""
    var consultaStockActual = 0
    var actualizandoSpinners = false
    var parteSeleccionada = pCat.ifBlank { dotacionDraftCategoria }
    var prendaSeleccionada = pItem.ifBlank { dotacionDraftPrenda }
    var tallaSeleccionada = pRef.ifBlank { dotacionDraftReferencia }

    val posicionParte = catalogoFormulario.keys.indexOfFirst {
        normalizarBusqueda(it) == normalizarBusqueda(parteSeleccionada)
    }
    if (posicionParte >= 0) parteSpinner.setSelection(posicionParte, false)

    val stockDisponible = stockInfoCard("Disponible: selecciona una prenda para consultar stock")
    root.addView(stockDisponible)

    fun prendas(parte: String): List<String> {
        val valores = catalogoFormulario[parte]?.keys?.sortedBy { normalizarBusqueda(it) }.orEmpty()
        return valores.ifEmpty { listOf("Sin prendas registradas") }
    }

    fun tallas(parte: String, prenda: String): List<String> {
        val valores = catalogoFormulario[parte]?.get(prenda)
            ?.map { referenciaDotacionPresentable(it) }
            ?.distinct()
            .orEmpty()
        return valores.ifEmpty { listOf("Sin tallas registradas") }
    }

    fun seleccionarValor(spinner: android.widget.Spinner, valores: List<String>, valor: String): Boolean {
        val posicion = valores.indexOfFirst { it == valor || referenciasInventarioCoinciden(valor, it) }
        if (posicion < 0) return false
        spinner.setSelection(posicion, false)
        return true
    }

    fun actualizarTallas(preservar: Boolean = true) {
        val parte = parteSeleccionada.ifBlank { parteSpinner.selectedItem?.toString().orEmpty() }
        val prenda = prendaSeleccionada.ifBlank { prendaSpinner.selectedItem?.toString().orEmpty() }
        val valores = tallas(parte, prenda)
        actualizandoSpinners = true
        tallaSpinner.tag = "SINCRO"
        tallaSpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, valores).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val preservada = preservar && seleccionarValor(tallaSpinner, valores, tallaSeleccionada)
        if (!preservada) tallaSpinner.setSelection(0, false)
        tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty()
        actualizandoSpinners = false
        tallaSpinner.postDelayed({ if (tallaSpinner.tag == "SINCRO") tallaSpinner.tag = null }, 100)
    }

    fun actualizarPrendas(preservar: Boolean = true) {
        val parte = parteSeleccionada.ifBlank { parteSpinner.selectedItem?.toString().orEmpty() }
        val valores = prendas(parte)
        actualizandoSpinners = true
        prendaSpinner.tag = "SINCRO"
        prendaSpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, valores).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val preservada = preservar && seleccionarValor(prendaSpinner, valores, prendaSeleccionada)
        if (!preservada) {
            prendaSpinner.setSelection(0, false)
            tallaSeleccionada = ""
        }
        prendaSeleccionada = prendaSpinner.selectedItem?.toString().orEmpty()
        actualizandoSpinners = false
        prendaSpinner.postDelayed({ if (prendaSpinner.tag == "SINCRO") prendaSpinner.tag = null }, 100)
        actualizarTallas(preservada)
    }

    fun tokenSeleccion(): String = listOf(
        parteSpinner.selectedItem?.toString().orEmpty(),
        prendaSpinner.selectedItem?.toString().orEmpty(),
        tallaSpinner.selectedItem?.toString().orEmpty(),
    ).joinToString("|") { normalizarBusqueda(it) }

    fun limpiarProductoSeleccionado() {
        codigoSeleccionado = ""
        documentoSeleccionado = ""
        productoSeleccionado = null
        stockDisponibleValor = 0.0
        stockVerificado = false
        tokenVerificado = ""
    }

    fun productoCoincide(producto: ExistenciaProducto): Boolean {
        val referenciaProducto = producto.referenciaCatalogo.ifBlank { producto.referencia }
        val categoriaProducto = resolverCategoriaDotacion(producto.item, referenciaProducto)
        return normalizarBusqueda(parteSpinner.selectedItem?.toString().orEmpty()) == normalizarBusqueda(categoriaProducto) &&
            normalizarBusqueda(prendaSpinner.selectedItem?.toString().orEmpty()) == normalizarBusqueda(producto.item) &&
            referenciasInventarioCoinciden(tallaSpinner.selectedItem?.toString().orEmpty(), referenciaProducto)
    }

    fun escribirCodigoInternoSinBusqueda(codigo: String) {
        val codigoActual = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
        val codigoNuevo = normalizarCodigoInterno(codigo)
        if (codigoActual == codigoNuevo) return

        codigoInterno.tag = "auto_fill"
        try {
            codigoInterno.setText(codigo, false)
            codigoInterno.setSelection(codigoInterno.text?.length ?: 0)
        } finally {
            codigoInterno.tag = null
        }
    }

    fun mostrarStock(producto: ExistenciaProducto?, consulta: Int, token: String) {
        if (consulta != consultaStockActual || token != tokenSeleccion()) return
        if (producto == null || producto.documentoId.isBlank() || !productoCoincide(producto)) {
            limpiarProductoSeleccionado()
            stockDisponible.text = "Disponible: 0 Unidad"
            stockDisponible.setTextColor(Color.rgb(160, 80, 0))
            return
        }
        productoSeleccionado = producto
        documentoSeleccionado = producto.documentoId
        codigoSeleccionado = producto.codigoInterno
        stockDisponibleValor = producto.cantidad
        stockVerificado = true
        tokenVerificado = token
        escribirCodigoInternoSinBusqueda(producto.codigoInterno)
        val referencia = producto.referenciaCatalogo.ifBlank { producto.referencia }
        val detalle = listOf(producto.codigoInterno, producto.item, referencia).filter { it.isNotBlank() }.joinToString(" - ")
        stockDisponible.text = "Disponible: ${cantidadDotacionLegible(producto.cantidad)} Unidad\n$detalle"
        stockDisponible.setTextColor(if (producto.cantidad > 0.0) verdeOscuro else Color.rgb(180, 40, 40))
    }

    fun mostrarFalloStock(consulta: Int, token: String) {
        if (consulta != consultaStockActual || token != tokenSeleccion()) return
        limpiarProductoSeleccionado()
        stockDisponible.text = "Disponible: no se pudo consultar stock"
        stockDisponible.setTextColor(Color.rgb(180, 40, 40))
    }

    fun consultarDocumentoExacto(documentoId: String, consulta: Int, token: String) {
        firestore.collection("existencias").document(documentoId).get()
            .addOnSuccessListener { snapshot ->
                if (consulta != consultaStockActual || token != tokenSeleccion()) return@addOnSuccessListener
                val producto = if (
                    snapshot.exists() && snapshot.getBoolean("activo") != false && moduloCoincide(snapshot, "Dotaci\u00f3n")
                ) existenciaProductoDesdeDoc(snapshot, "Dotaci\u00f3n") else null
                mostrarStock(producto?.takeIf { it.documentoId == documentoId }, consulta, token)
            }
            .addOnFailureListener { mostrarFalloStock(consulta, token) }
    }

    fun actualizarStock(documentoPreferido: String = "") {
        val consulta = ++consultaStockActual
        val token = tokenSeleccion()
        val prenda = prendaSpinner.selectedItem?.toString().orEmpty()
        val talla = tallaSpinner.selectedItem?.toString().orEmpty()
        stockVerificado = false
        tokenVerificado = ""
        if (esPlaceholderDotacion(prenda) || esPlaceholderDotacion(talla)) {
            limpiarProductoSeleccionado()
            stockDisponible.text = "Disponible: selecciona una prenda para consultar stock"
            stockDisponible.setTextColor(gris)
            return
        }

        stockDisponible.text = "Disponible: consultando..."
        stockDisponible.setTextColor(gris)
        val documentoExacto = documentoPreferido.ifBlank { documentoSeleccionado }
        if (documentoExacto.isNotBlank()) {
            consultarDocumentoExacto(documentoExacto, consulta, token)
            return
        }
        val codigo = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
        if (codigo.isNotBlank()) {
            buscarExistenciaPorCodigoInterno(codigo, "Dotaci\u00f3n", { mostrarStock(it, consulta, token) }, {
                mostrarFalloStock(consulta, token)
            })
        } else {
            buscarExistenciaPorProducto("Dotaci\u00f3n", prenda, talla, { mostrarStock(it, consulta, token) }, {
                mostrarFalloStock(consulta, token)
            })
        }
    }

    setupCodigoInternoSalida(root, codigoInterno, "Dotaci\u00f3n") { producto ->
        consultaStockActual++
        seleccionarProductoDesdeExistencia(producto, parteSpinner, prendaSpinner, tallaSpinner, catalogoComoMapAny())
        root.post {
            parteSeleccionada = parteSpinner.selectedItem?.toString().orEmpty()
            prendaSeleccionada = prendaSpinner.selectedItem?.toString().orEmpty()
            tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty()
            documentoSeleccionado = producto.documentoId
            actualizarStock(producto.documentoId)
        }
    }

    setupSearchBar(root, "Dotaci\u00f3n") { _, categoria, item, referencia ->
        seleccionarProductoEnSpinners(
            parteSpinner,
            prendaSpinner,
            tallaSpinner,
            catalogoComoMapAny(),
            categoria,
            item,
            referencia,
        ) {
            parteSeleccionada = categoria
            prendaSeleccionada = item
            tallaSeleccionada = referenciaDotacionPresentable(referencia)
            dotacionDraftCategoria = parteSeleccionada
            dotacionDraftPrenda = prendaSeleccionada
            dotacionDraftReferencia = tallaSeleccionada
            limpiarProductoSeleccionado()
            codigoInterno.setText("", false)
            actualizarStock()
        }
    }

    parteSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (actualizandoSpinners || parteSpinner.tag == "SINCRO") return
            parteSeleccionada = parteSpinner.selectedItem?.toString().orEmpty()
            dotacionDraftCategoria = parteSeleccionada
            prendaSeleccionada = prendaSpinner.selectedItem?.toString().orEmpty().takeUnless { esPlaceholderDotacion(it) }.orEmpty()
            tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty().takeUnless { esPlaceholderDotacion(it) }.orEmpty()
            actualizarPrendas(true)
            limpiarProductoSeleccionado()
            codigoInterno.setText("", false)
            actualizarStock()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    prendaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (actualizandoSpinners || prendaSpinner.tag == "SINCRO") return
            prendaSeleccionada = prendaSpinner.selectedItem?.toString().orEmpty()
            dotacionDraftPrenda = prendaSeleccionada.takeUnless { esPlaceholderDotacion(it) }.orEmpty()
            tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty().takeUnless { esPlaceholderDotacion(it) }.orEmpty()
            actualizarTallas(true)
            limpiarProductoSeleccionado()
            codigoInterno.setText("", false)
            actualizarStock()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    tallaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            if (actualizandoSpinners || tallaSpinner.tag == "SINCRO") return
            tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty()
            dotacionDraftReferencia = tallaSeleccionada.takeUnless { esPlaceholderDotacion(it) }.orEmpty()
            limpiarProductoSeleccionado()
            codigoInterno.setText("", false)
            actualizarStock()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    parteSeleccionada = parteSpinner.selectedItem?.toString().orEmpty().ifBlank { parteSeleccionada }
    actualizarPrendas(true)
    dotacionDraftCategoria = parteSeleccionada
    dotacionDraftPrenda = prendaSeleccionada.takeUnless { esPlaceholderDotacion(it) }.orEmpty()
    dotacionDraftReferencia = tallaSeleccionada.takeUnless { esPlaceholderDotacion(it) }.orEmpty()
    actualizarStock()

    val cantidad = field(root, "Cantidad *", "Ej: 1", number = true)
    cantidad.setText(pCant.ifBlank { dotacionDraftCantidad.ifBlank { "1" } })
    val solicitante = autoField(root, "Solicitante *", "Nombre de quien recibe")
    solicitante.setText(pSol.ifBlank { dotacionDraftSolicitante })
    val cargo = spinner(root, "Cargo *", cargos)
    cargos.indexOf(dotacionDraftCargo).takeIf { it >= 0 }?.let { cargo.setSelection(it, false) }
    if (dotacionDraftCargo.isBlank()) dotacionDraftCargo = cargo.selectedItem?.toString().orEmpty()
    val observaciones = field(root, "Observaciones", "Opcional")
    observaciones.setText(dotacionDraftObservaciones)
    var urlEvidencia = dotacionDraftUrlEvidencia
    var uriLocalEvidencia = dotacionDraftUriEvidencia

    fun guardarDraft() {
        dotacionDraftCategoria = parteSpinner.selectedItem?.toString().orEmpty()
        dotacionDraftPrenda = prendaSpinner.selectedItem?.toString().orEmpty().takeUnless { esPlaceholderDotacion(it) }.orEmpty()
        dotacionDraftReferencia = tallaSpinner.selectedItem?.toString().orEmpty().takeUnless { esPlaceholderDotacion(it) }.orEmpty()
        dotacionDraftCantidad = cantidad.text.toString().ifBlank { "1" }
        dotacionDraftSolicitante = solicitante.text.toString()
        dotacionDraftCargo = cargo.selectedItem?.toString().orEmpty()
        dotacionDraftObservaciones = observaciones.text.toString()
        dotacionDraftUrlEvidencia = urlEvidencia
        dotacionDraftUriEvidencia = uriLocalEvidencia
    }

    fun observar(campo: android.widget.EditText, accion: (String) -> Unit) {
        campo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = accion(s?.toString().orEmpty())
        })
    }
    observar(cantidad) { dotacionDraftCantidad = it.ifBlank { "1" } }
    observar(solicitante) { dotacionDraftSolicitante = it }
    observar(observaciones) { dotacionDraftObservaciones = it }
    cargo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            dotacionDraftCargo = cargo.selectedItem?.toString().orEmpty()
        }
        override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
    }

    root.addView(evidenceButton {
        capturarEvidencia { uri ->
            uriLocalEvidencia = uri
            urlEvidencia = ""
            dotacionDraftUriEvidencia = uri
            dotacionDraftUrlEvidencia = ""
            mostrarPrevisualizacionEvidencia(root, uri) {
                uriLocalEvidencia = ""
                urlEvidencia = ""
                dotacionDraftUriEvidencia = ""
                dotacionDraftUrlEvidencia = ""
            }
        }
    })
    if (uriLocalEvidencia.isNotBlank()) {
        mostrarPrevisualizacionEvidencia(root, uriLocalEvidencia) {
            uriLocalEvidencia = ""
            urlEvidencia = ""
            dotacionDraftUriEvidencia = ""
            dotacionDraftUrlEvidencia = ""
        }
    }

    root.addView(gestionNuevoEntradaRow(
        onNuevo = {
            guardarDraft()
            showDialogNuevoProducto("Dotaci\u00f3n") { categoria, item, referencia ->
                showDotacionForm(item, dotacionDraftCantidad, dotacionDraftSolicitante, categoria, referencia)
            }
        },
        onEntrada = {
            val prenda = prendaSpinner.selectedItem?.toString().orEmpty()
            val talla = tallaSpinner.selectedItem?.toString().orEmpty()
            if (!esPlaceholderDotacion(prenda)) {
                guardarDraft()
                showDialogEntradaStock("Dotaci\u00f3n", prenda, talla) {
                    showDotacionForm(prenda, dotacionDraftCantidad, dotacionDraftSolicitante, parteSeleccionada, talla)
                }
            } else {
                Toast.makeText(this, "Selecciona una prenda primero", Toast.LENGTH_SHORT).show()
            }
        },
    ))

    fun lineaDesdeFormulario(): DotacionEntregaLinea? {
        val producto = productoSeleccionado
        val categoria = parteSpinner.selectedItem?.toString().orEmpty()
        val prenda = prendaSpinner.selectedItem?.toString().orEmpty()
        val talla = tallaSpinner.selectedItem?.toString().orEmpty()
        if (esPlaceholderDotacion(categoria) || esPlaceholderDotacion(prenda) || esPlaceholderDotacion(talla)) {
            Toast.makeText(this, "Selecciona una prenda y talla validas", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!stockVerificado || tokenVerificado != tokenSeleccion() || producto == null || documentoSeleccionado.isBlank()) {
            Toast.makeText(this, "Espera la consulta de stock antes de agregar", Toast.LENGTH_SHORT).show()
            return null
        }
        if (producto.documentoId != documentoSeleccionado || !productoCoincide(producto)) {
            Toast.makeText(this, "El documento consultado no coincide con la prenda seleccionada", Toast.LENGTH_LONG).show()
            return null
        }
        val decimal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        val entero = decimal.takeIf { it > 0.0 && it % 1.0 == 0.0 }?.toInt()
        if (entero == null) {
            Toast.makeText(this, "La cantidad debe ser un numero entero mayor a cero", Toast.LENGTH_SHORT).show()
            return null
        }
        if (entero > stockDisponibleValor) {
            Toast.makeText(this, "Stock insuficiente. Disponible: ${cantidadDotacionLegible(stockDisponibleValor)} Unidad", Toast.LENGTH_LONG).show()
            return null
        }
        val codigoEscrito = codigoInterno.text?.toString().orEmpty().normalizarCodigoDotacion()
        if (codigoEscrito.isNotBlank() && codigoEscrito != producto.codigoInterno.normalizarCodigoDotacion()) {
            Toast.makeText(this, "El codigo no coincide con la prenda seleccionada", Toast.LENGTH_SHORT).show()
            return null
        }
        return DotacionEntregaLinea(
            categoria = categoria,
            prenda = prenda,
            referencia = referenciaDotacionPresentable(talla),
            codigoInterno = producto.codigoInterno,
            documentoId = producto.documentoId,
            cantidad = entero,
            stockDisponible = stockDisponibleValor,
        )
    }

    val prendasAgregadas = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(4))
    }
    var botonAgregar: Button? = null
    var botonRegistrar: Button? = null

    fun actualizarBotones() {
        botonAgregar?.text = if (dotacionEntregaEditandoId != null) "Actualizar prenda" else "Agregar a la entrega"
        val habilitado = dotacionEntregaLineas.isNotEmpty() && !dotacionEntregaProcesando
        botonRegistrar?.isEnabled = habilitado
        botonRegistrar?.alpha = if (habilitado) 1f else 0.55f
    }

    fun aplicarLinea(linea: DotacionEntregaLinea) {
        dotacionEntregaEditandoId = linea.id
        parteSeleccionada = linea.categoria
        prendaSeleccionada = linea.prenda
        tallaSeleccionada = linea.referencia
        dotacionDraftCategoria = linea.categoria
        dotacionDraftPrenda = linea.prenda
        dotacionDraftReferencia = linea.referencia
        seleccionarProductoEnSpinners(
            parteSpinner,
            prendaSpinner,
            tallaSpinner,
            catalogoComoMapAny(),
            linea.categoria,
            linea.prenda,
            linea.referencia,
        ) {
            codigoSeleccionado = linea.codigoInterno
            documentoSeleccionado = linea.documentoId
            escribirCodigoInternoSinBusqueda(linea.codigoInterno)
            actualizarStock(linea.documentoId)
        }
        cantidad.setText(linea.cantidad.toString())
        actualizarBotones()
    }

    fun renderPrendas() {
        prendasAgregadas.removeAllViews()
        prendasAgregadas.addView(TextView(this).apply {
            text = "Prendas agregadas"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(verdeOscuro)
            setPadding(0, dp(8), 0, dp(4))
        })
        if (dotacionEntregaLineas.isEmpty()) {
            prendasAgregadas.addView(TextView(this).apply {
                text = "Aun no hay prendas en esta entrega."
                textSize = 13f
                setTextColor(gris)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
            })
            actualizarBotones()
            return
        }
        dotacionEntregaLineas.forEachIndexed { index, linea ->
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
                text = "${index + 1}. ${linea.prenda}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(texto)
            })
            tarjeta.addView(TextView(this).apply {
                text = listOf(
                    "Parte: ${linea.categoria}",
                    "Talla/ref: ${linea.referencia}",
                    "Codigo: ${linea.codigoInterno}",
                    "Documento: ${linea.documentoId}",
                    "Cantidad: ${linea.cantidad} Unidad",
                    "Stock: ${cantidadDotacionLegible(linea.stockDisponible)} Unidad",
                ).joinToString("\n")
                textSize = 12.5f
                setTextColor(gris)
                setPadding(0, dp(4), 0, dp(8))
            })
            val acciones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val editar = outlineButton("Editar") { aplicarLinea(linea) }
            val eliminar = outlineButton("Eliminar") {
                dotacionEntregaLineas.removeAll { it.id == linea.id }
                if (dotacionEntregaEditandoId == linea.id) dotacionEntregaEditandoId = null
                renderPrendas()
            }.apply { setTextColor(Color.RED) }
            acciones.addView(editar, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(6), 0) })
            acciones.addView(eliminar, LinearLayout.LayoutParams(0, dp(44), 1f))
            tarjeta.addView(acciones)
            prendasAgregadas.addView(tarjeta)
        }
        actualizarBotones()
    }

    botonAgregar = primaryButton("Agregar a la entrega") {
        guardarDraft()
        val nueva = lineaDesdeFormulario() ?: return@primaryButton
        val idEditando = dotacionEntregaEditandoId
        val indiceEditando = idEditando?.let { id -> dotacionEntregaLineas.indexOfFirst { it.id == id } } ?: -1
        val indiceDuplicado = dotacionEntregaLineas.indexOfFirst { it.clave() == nueva.clave() && it.id != idEditando }
        val total = nueva.cantidad + if (indiceDuplicado >= 0) dotacionEntregaLineas[indiceDuplicado].cantidad else 0
        if (total > nueva.stockDisponible) {
            Toast.makeText(this, "Stock insuficiente para la cantidad acumulada de ${nueva.prenda}", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        when {
            indiceEditando >= 0 && indiceDuplicado >= 0 -> {
                dotacionEntregaLineas[indiceDuplicado] = dotacionEntregaLineas[indiceDuplicado].copy(
                    cantidad = total,
                    stockDisponible = nueva.stockDisponible,
                )
                dotacionEntregaLineas.removeAt(indiceEditando)
            }
            indiceEditando >= 0 -> dotacionEntregaLineas[indiceEditando] = nueva.copy(id = dotacionEntregaLineas[indiceEditando].id)
            indiceDuplicado >= 0 -> dotacionEntregaLineas[indiceDuplicado] = dotacionEntregaLineas[indiceDuplicado].copy(
                cantidad = total,
                stockDisponible = nueva.stockDisponible,
            )
            else -> dotacionEntregaLineas.add(nueva)
        }
        dotacionEntregaEditandoId = null
        cantidad.setText("1")
        dotacionDraftCantidad = "1"
        renderPrendas()
        stockVerificado = false
        tokenVerificado = ""
        actualizarStock(nueva.documentoId)
        Toast.makeText(this, "Prenda agregada a la entrega", Toast.LENGTH_SHORT).show()
    }
    botonAgregar?.let { root.addView(it) }
    root.addView(prendasAgregadas)

    botonRegistrar = primaryButton("Registrar todos") { view ->
        if (dotacionEntregaProcesando) return@primaryButton
        guardarDraft()
        if (dotacionEntregaLineas.isEmpty()) {
            Toast.makeText(this, "Agrega al menos una prenda", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        if (!required(solicitante)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Sin conexion no se puede garantizar el registro completo", Toast.LENGTH_LONG).show()
            return@primaryButton
        }

        fun finalizarBloqueo() {
            dotacionEntregaProcesando = false
            (view as? Button)?.text = "Registrar todos"
            actualizarBotones()
        }

        fun registrar(foto: String) {
            val lineas = dotacionEntregaLineas.toList()
            registrarEntregaMultipleDotacion(
                lineas = lineas,
                solicitante = solicitante.text.toString().trim(),
                cargo = cargo.selectedItem?.toString().orEmpty(),
                observaciones = observaciones.text.toString(),
                fotoUrl = foto,
                onSuccess = {
                    finalizarBloqueo()
                    limpiarEstadoEntregaDotacion()
                    saved("Entrega de Dotaci\u00f3n registrada con ${lineas.size} linea(s)")
                    showDotacionForm()
                },
                onFailure = { error ->
                    Toast.makeText(this, error.localizedMessage ?: "No se pudo registrar la entrega", Toast.LENGTH_LONG).show()
                    finalizarBloqueo()
                },
            )
        }

        dotacionEntregaProcesando = true
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
                Toast.makeText(this, "Subiendo evidencia de Dotaci\u00f3n...", Toast.LENGTH_SHORT).show()
                subirEvidenciaCloud(evidenciaLocal, "dotacion") { url ->
                    if (url.isBlank()) {
                        Toast.makeText(this, "No se pudo subir la evidencia", Toast.LENGTH_LONG).show()
                        finalizarBloqueo()
                    } else {
                        urlEvidencia = url
                        dotacionDraftUrlEvidencia = url
                        registrar(url)
                    }
                }
            }
            else -> registrar("")
        }
    }
    botonRegistrar?.let { root.addView(it) }
    renderPrendas()
}

private fun MainActivity.registrarEntregaMultipleDotacion(
    lineas: List<DotacionEntregaLinea>,
    solicitante: String,
    cargo: String,
    observaciones: String,
    fotoUrl: String,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null,
) {
    if (lineas.isEmpty()) {
        onFailure?.invoke(IllegalStateException("No hay prendas para registrar"))
        return
    }
    if (!isNetworkAvailable()) {
        onFailure?.invoke(IllegalStateException("Sin conexion no se puede validar stock y registrar todo o nada"))
        return
    }

    val fechaEntrega = now()
    val usuario = auth.currentUser?.uid.orEmpty()
    val entregaId = "DOTACION-${System.currentTimeMillis()}"
    firestore.runTransaction { transaction ->
        val referenciasPorDocumento = lineas.associate { linea ->
            if (linea.documentoId.isBlank()) throw IllegalStateException("Documento faltante para ${linea.prenda}")
            linea.documentoId to firestore.collection("existencias").document(linea.documentoId)
        }
        val snapshots = mutableMapOf<String, DocumentSnapshot>()
        val stocks = mutableMapOf<String, Double>()
        val cantidadesPorDocumento = lineas.groupBy { it.documentoId }
            .mapValues { (_, prendas) -> prendas.sumOf { it.cantidad.toDouble() } }

        referenciasPorDocumento.forEach { (documentoId, referenciaDocumento) ->
            val snapshot = transaction.get(referenciaDocumento)
            if (!snapshot.exists() || snapshot.getBoolean("activo") == false || !moduloCoincide(snapshot, "Dotaci\u00f3n")) {
                throw IllegalStateException("Inventario de Dotacion no disponible para $documentoId")
            }
            val linea = lineas.first { it.documentoId == documentoId }
            val producto = existenciaProductoDesdeDoc(snapshot, "Dotaci\u00f3n")
            val referenciaProducto = producto.referenciaCatalogo.ifBlank { producto.referencia }
            val categoriaProducto = resolverCategoriaDotacion(producto.item, referenciaProducto)
            val coincide = producto.documentoId == linea.documentoId &&
                producto.codigoInterno.normalizarCodigoDotacion() == linea.codigoInterno.normalizarCodigoDotacion() &&
                normalizarBusqueda(producto.item) == normalizarBusqueda(linea.prenda) &&
                referenciasInventarioCoinciden(linea.referencia, referenciaProducto) &&
                normalizarBusqueda(categoriaProducto) == normalizarBusqueda(linea.categoria)
            if (!coincide) {
                throw IllegalStateException("El documento $documentoId no coincide exactamente con ${linea.prenda} / ${linea.referencia}")
            }
            val stockActual = numeroDocumento(snapshot, "cantidad", "stock_actual")
            val solicitado = cantidadesPorDocumento[documentoId] ?: 0.0
            if (solicitado <= 0.0 || solicitado % 1.0 != 0.0) {
                throw IllegalStateException("Cantidad invalida en ${linea.prenda}")
            }
            if (solicitado > stockActual) {
                throw IllegalStateException(
                    "Stock insuficiente en ${linea.prenda}. Disponible: ${cantidadDotacionLegible(stockActual)}, " +
                        "solicitado: ${cantidadDotacionLegible(solicitado)}"
                )
            }
            snapshots[documentoId] = snapshot
            stocks[documentoId] = stockActual
        }

        val stockRestante = stocks.toMutableMap()
        lineas.forEachIndexed { index, linea ->
            val snapshot = snapshots[linea.documentoId] ?: throw IllegalStateException("Inventario no disponible para ${linea.prenda}")
            val referenciaStock = referenciasPorDocumento[linea.documentoId]
                ?: throw IllegalStateException("Inventario no disponible para ${linea.prenda}")
            val stockAnterior = stockRestante[linea.documentoId] ?: 0.0
            val stockNuevo = stockAnterior - linea.cantidad
            transaction.set(referenciaStock, mapOf(
                "cantidad" to stockNuevo,
                "stock_actual" to stockNuevo,
                "ultima_fecha" to fechaEntrega,
                "ultimo_solicitante" to solicitante,
            ), SetOptions.merge())
            transaction.set(firestore.collection("movimientos").document(), mapOf(
                "fecha" to fechaEntrega,
                "modulo" to "Dotaci\u00f3n",
                "tipoMovimiento" to "Salida",
                "item" to "${linea.prenda} (${referenciaDotacionPresentable(linea.referencia)})",
                "item_base" to linea.prenda,
                "categoria" to linea.categoria,
                "referencia" to linea.referencia,
                "cantidad" to linea.cantidad.toString(),
                "unidad" to "Unidad",
                "solicitante" to solicitante,
                "labor" to cargo,
                "observaciones" to observaciones,
                "usuario" to usuario,
                "fotoUrl" to fotoUrl,
                "codigo_interno" to codigoExistencia(snapshot),
                "codigo_original" to codigoOriginalExistencia(snapshot),
                "documento_id" to linea.documentoId,
                "producto_id" to linea.documentoId,
                "ubicacion" to ubicacionExistencia(snapshot),
                "stock_actualizado" to true,
                "stock_anterior" to stockAnterior,
                "stock_nuevo" to stockNuevo,
                "entrega_id" to entregaId,
                "linea_entrega" to (index + 1),
                "total_lineas" to lineas.size,
            ))
            stockRestante[linea.documentoId] = stockNuevo
        }
        lineas.size
    }.addOnSuccessListener { onSuccess?.invoke() }
        .addOnFailureListener { onFailure?.invoke(it) }
}
