@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
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

private const val BUG_SPINNERS_TAG = "BugSpinners"
private const val BUG_STOCK_EPP_TAG = "BugStockEPP"

private data class ConsumibleEntregaLinea(
    val id: Long = System.nanoTime(),
    val categoria: String,
    val item: String,
    val referencia: String,
    val codigoInterno: String,
    val documentoId: String,
    val cantidad: Double,
    val unidad: String,
    val stockDisponible: Double,
    val unidadStock: String,
    val ubicacion: String = "",
) {
    fun clave(): String = listOf(
        normalizarBusqueda(categoria),
        normalizarBusqueda(item),
        normalizarBusqueda(referencia),
        normalizarCodigoInternoSinActividad(codigoInterno.ifBlank { documentoId }),
    ).joinToString("|")
}

private data class ConsumibleDocumentoResuelto(
    val linea: ConsumibleEntregaLinea,
    val doc: DocumentSnapshot,
)

private val consumiblesEntregaLineas = mutableListOf<ConsumibleEntregaLinea>()
private var consumiblesEntregaEditandoId: Long? = null
private var consumiblesEntregaProcesando = false
private var consumiblesDraftCategoria = ""
private var consumiblesDraftItem = ""
private var consumiblesDraftReferencia = ""
private var consumiblesDraftCantidad = ""
private var consumiblesDraftSolicitante = ""
private var consumiblesDraftLabor = ""
private var consumiblesDraftObservaciones = ""
private var consumiblesDraftUrlEvidencia = ""
private var consumiblesDraftUriEvidencia = ""

private fun normalizarCodigoInternoSinActividad(valor: String): String {
    val limpio = valor.trim().uppercase(Locale.getDefault())
    return limpio.replace(Regex("[^A-Z0-9]+"), "")
}

private fun cantidadConsumibleLegible(valor: Double): String {
    return if (valor % 1.0 == 0.0) valor.toLong().toString()
    else String.format(Locale.getDefault(), "%.2f", valor).trimEnd('0').trimEnd(',', '.')
}

private fun esPlaceholderSpinnerDependiente(valor: String): Boolean {
    val normalizado = normalizarBusqueda(valor)
    return valor.isBlank() ||
        normalizado.startsWith("selecciona") ||
        normalizado.startsWith("sin-") ||
        normalizado.startsWith("no-hay")
}

private fun MainActivity.adapterSpinnerDependiente(valores: List<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, android.R.layout.simple_spinner_item, valores).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
}

private fun seleccionarValorSpinnerDependiente(spinner: Spinner, valores: List<String>, valor: String): Boolean {
    if (valor.isBlank()) return false
    val pos = valores.indexOfFirst { it == valor || referenciasInventarioCoinciden(valor, it) }
    if (pos < 0) return false
    spinner.setSelection(pos, false)
    return true
}

private fun limpiarTagSpinnerDependiente(spinner: Spinner) {
    spinner.postDelayed({ if (spinner.tag == "SINCRO") spinner.tag = null }, 100)
}

internal fun MainActivity.showDotacionForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        currentScreenRenderer = { showDotacionForm(pItem, pCant, pSol, pCat, pRef) }
        val root = baseScreen("Entrega de Dotación", "Control de vestuario y calzado para el personal.")

        val categoriasDotacionPermitidas = listOf(
            "Parte Superior",
            "Parte Inferior",
            "Conjunto",
            "Calzado"
        )

        fun crearCatalogoDotacion(): Map<String, MutableMap<String, MutableList<String>>> {
            val base = catalogoCargado["Dotación"] ?: emptyMap()
            return categoriasDotacionPermitidas.associateWith { categoria ->
                base[categoria]?.mapValues { (_, refs) ->
                    refs.map { referenciaDotacionPresentable(it) }.distinct().toMutableList()
                }?.toMutableMap() ?: mutableMapOf()
            }
        }

        val catalogoFormulario = crearCatalogoDotacion()
        fun getCatalogo() = catalogoFormulario
        fun catalogoDotacionComoMapAny(): Map<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return catalogoFormulario as Map<String, Any>
        }

        val parteSpinner = spinner(root, "Parte del cuerpo *", getCatalogo().keys.toList())
        val itemSpinner = spinner(root, "Prenda *", listOf("Selecciona parte"))
        val tallaSpinner = spinner(root, "Talla *", listOf("Selecciona prenda"))
        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe el código si existe", scan = false)
        var codigoInternoSeleccionado = ""
        var actualizandoSpinnerDotacion = false
        var parteSeleccionada = pCat
        var prendaSeleccionada = pItem
        var tallaSeleccionada = pRef

        val stockDisponible = stockInfoCard("Disponible: selecciona una prenda para consultar stock")
        root.addView(stockDisponible)

        fun cantidadLegible(valor: Double): String {
            return if (valor % 1.0 == 0.0) valor.toInt().toString()
            else String.format(Locale.getDefault(), "%.2f", valor)
        }

        fun opcionDotacionValida(valor: String): Boolean {
            return valor.isNotBlank() && !valor.startsWith("Selecciona") && !valor.startsWith("Sin ")
        }

        fun prendasDotacion(parte: String): List<String> {
            val itemsMap = getCatalogo()[parte] ?: mapOf()
            val prendas = itemsMap.keys.toList().sortedBy { normalizarBusqueda(it) }
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion categoria=$parte items=${prendas.size}")
            return prendas.ifEmpty { listOf("Sin prendas registradas") }
        }

        fun tallasDotacion(parte: String, prenda: String): List<String> {
            val tallas = getCatalogo()[parte]?.get(prenda)
                ?.map { referenciaDotacionPresentable(it) }
                ?.distinct()
                .orEmpty()
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion item=$prenda referencias=${tallas.size}")
            return tallas.ifEmpty { listOf("Sin tallas registradas") }
        }

        fun actualizarTallasDotacion(origen: String, preservar: Boolean = true) {
            val parte = parteSeleccionada.ifBlank { parteSpinner.selectedItem?.toString().orEmpty() }
            val prenda = prendaSeleccionada.ifBlank { itemSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerDotacion = true
            tallaSpinner.tag = "SINCRO"
            val tallas = tallasDotacion(parte, prenda)
            tallaSpinner.adapter = adapterSpinnerDependiente(tallas)
            val preservada = preservar && seleccionarValorSpinnerDependiente(tallaSpinner, tallas, tallaSeleccionada)
            if (!preservada) {
                tallaSpinner.setSelection(0, false)
                tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty()
            } else {
                tallaSeleccionada = tallaSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerDotacion = false
            limpiarTagSpinnerDependiente(tallaSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion referenciaPreservada=$preservada origen=$origen")
        }

        fun actualizarPrendasDotacion(origen: String, preservar: Boolean = true) {
            val parte = parteSeleccionada.ifBlank { parteSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerDotacion = true
            itemSpinner.tag = "SINCRO"
            val prendas = prendasDotacion(parte)
            itemSpinner.adapter = adapterSpinnerDependiente(prendas)
            val preservada = preservar && seleccionarValorSpinnerDependiente(itemSpinner, prendas, prendaSeleccionada)
            if (!preservada) {
                itemSpinner.setSelection(0, false)
                prendaSeleccionada = itemSpinner.selectedItem?.toString().orEmpty()
                tallaSeleccionada = ""
            } else {
                prendaSeleccionada = itemSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerDotacion = false
            limpiarTagSpinnerDependiente(itemSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion itemPreservado=$preservada origen=$origen")
            actualizarTallasDotacion(origen, preservar = preservada)
        }

        fun mostrarStockDotacion(producto: ExistenciaProducto?) {
            if (producto == null) {
                stockDisponible.text = "Disponible: sin stock registrado para esta selección"
                stockDisponible.setTextColor(Color.rgb(160, 80, 0))
                return
            }
            val unidad = producto.unidad.ifBlank { "Unidad" }
            val referencia = producto.referenciaCatalogo.ifBlank { producto.referencia }
            val detalle = listOf(producto.codigoInterno, producto.item, referencia).filter { it.isNotBlank() }.joinToString(" - ")
            stockDisponible.text = "Disponible: ${cantidadLegible(producto.cantidad)} $unidad\n$detalle"
            stockDisponible.setTextColor(if (producto.cantidad > 0.0) verdeOscuro else Color.rgb(180, 40, 40))
        }

        fun actualizarInfoStockDotacion() {
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val tallaVal = tallaSpinner.selectedItem?.toString() ?: ""
            if (opcionDotacionValida(itemVal) && opcionDotacionValida(tallaVal)) {
                
                stockDisponible.text = "Disponible: consultando..."
                stockDisponible.setTextColor(gris)

                buscarExistenciaPorProducto("Dotación", itemVal, tallaVal, { producto ->
                    mostrarStockDotacion(producto)
                }, {
                    stockDisponible.text = "Disponible: no se pudo consultar stock"
                    stockDisponible.setTextColor(Color.rgb(180, 40, 40))
                })
            } else {
                stockDisponible.text = "Disponible: selecciona una prenda para consultar stock"
                stockDisponible.setTextColor(gris)
            }
        }

        setupCodigoInternoSalida(root, codigoInterno, "Dotación") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            seleccionarProductoDesdeExistencia(producto, parteSpinner, itemSpinner, tallaSpinner, catalogoDotacionComoMapAny())
            mostrarStockDotacion(producto)
        }

        setupSearchBar(root, "Dotación") { _, c, i, r ->
            seleccionarProductoEnSpinners(parteSpinner, itemSpinner, tallaSpinner, catalogoDotacionComoMapAny(), c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    mostrarStockDotacion(producto)
                }
            }
        }

        if (pCat.isNotBlank() && pItem.isNotBlank()) {
            seleccionarProductoEnSpinners(parteSpinner, itemSpinner, tallaSpinner, catalogoDotacionComoMapAny(), pCat, pItem, pRef) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", pItem, pRef) { encontrado ->
                    codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                    mostrarStockDotacion(encontrado)
                }
            }
        }

        parteSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerDotacion || parteSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion seleccion inicial categoria ignorada")
                    return
                }
                parteSeleccionada = parteSpinner.selectedItem?.toString().orEmpty()
                val itemActual = itemSpinner.selectedItem?.toString().orEmpty()
                val tallaActual = tallaSpinner.selectedItem?.toString().orEmpty()
                prendaSeleccionada = if (!esPlaceholderSpinnerDependiente(itemActual)) itemActual else ""
                tallaSeleccionada = if (!esPlaceholderSpinnerDependiente(tallaActual)) tallaActual else ""
                actualizarPrendasDotacion("categoria", preservar = true)
                actualizarInfoStockDotacion()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerDotacion || itemSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion seleccion inicial item ignorada")
                    return
                }
                prendaSeleccionada = itemSpinner.selectedItem?.toString().orEmpty()
                val tallaActual = tallaSpinner.selectedItem?.toString().orEmpty()
                tallaSeleccionada = if (!esPlaceholderSpinnerDependiente(tallaActual)) tallaActual else tallaSeleccionada
                actualizarTallasDotacion("item", preservar = true)

                if (itemSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    codigoInternoSeleccionado = ""
                    codigoInterno.setText("", false)
                }
                actualizarInfoStockDotacion()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        tallaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerDotacion || tallaSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=Dotacion seleccion inicial referencia ignorada")
                    return
                }
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val tallaVal = tallaSpinner.selectedItem?.toString() ?: ""
                tallaSeleccionada = tallaVal
                if (opcionDotacionValida(itemVal) && opcionDotacionValida(tallaVal)) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", itemVal, tallaVal) { producto ->
                        codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    }
                }
                actualizarInfoStockDotacion()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        parteSeleccionada = parteSpinner.selectedItem?.toString().orEmpty().ifBlank { parteSeleccionada }
        val prendaInicio = itemSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(prendaInicio)) prendaSeleccionada = prendaInicio
        val tallaInicio = tallaSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(tallaInicio)) tallaSeleccionada = tallaInicio
        actualizarPrendasDotacion("inicio", preservar = true)

        val cantidad = field(root, "Cantidad *", "Ej: 1", number = true)
        cantidad.setText(pCant.ifBlank { "1" })
        val solicitante = autoField(root, "Solicitante *", "Nombre de quien recibe")
        solicitante.setText(pSol)
        val cargo = spinner(root, "Cargo *", listOf(
            "Auxiliar de campo",
            "Auxiliar de vivero",
            "Tractorista",
            "Asistente",
            "Jefe de campo",
            "Cocinera",
            "Ingeniero"
        ))
        val observaciones = field(root, "Observaciones", "Opcional")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(evidenceButton {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, "dotacion") { url -> urlEvidencia = url }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                showDialogNuevoProducto("Dotación") { c, i, r ->
                    showDotacionForm(pItem = i, pCant = pCant, pSol = pSol, pCat = c, pRef = r)
                }
            },
            onEntrada = {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val tallaVal = tallaSpinner.selectedItem?.toString() ?: ""
                if (opcionDotacionValida(itemVal)) {
                    showDialogEntradaStock("Dotación", itemVal, tallaVal) { showDotacionForm(pItem, pCant, pSol) }
                } else {
                    Toast.makeText(this, "Selecciona una prenda primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Registrar Dotación") {
            if (!required(cantidad, solicitante)) return@primaryButton
            val codigoPreferido = codigoInternoValidadoParaSalida(codigoInterno, codigoInternoSeleccionado)
            
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val tallaVal = tallaSpinner.selectedItem?.toString() ?: ""

            if (!opcionDotacionValida(itemVal) || !opcionDotacionValida(tallaVal)) {
                Toast.makeText(this, "Selecciona una prenda y talla válida", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val tallaPresentable = referenciaDotacionPresentable(tallaVal)
            val nombreItem = "$itemVal ($tallaPresentable)"
            val cantVal = cantidad.text.toString().toDoubleOrNull() ?: 0.0

            val mov = Movimiento(
                id = 0,
                fecha = now(),
                modulo = "Dotación",
                tipoMovimiento = "Salida",
                item = nombreItem,
                referencia = tallaVal,
                marca = "",
                cantidad = cantidad.text.toString(),
                unidad = "Unidad",
                solicitante = solicitante.text.toString(),
                labor = cargo.selectedItem?.toString().orEmpty(),
                maquinaria = "",
                horometro = "",
                herramientaId = "",
                estado = "",
                observaciones = observaciones.text.toString()
            )

            registrarSalidaCloudPrimero(
                mov = mov,
                cantidadNumerica = cantVal,
                codigoInternoPreferido = codigoPreferido,
                itemBase = itemVal,
                fotoUrl = urlEvidencia.ifBlank { uriLocalEvidencia },
                onSuccess = {
                    saved("Dotación guardada con evidencia")
                    // No retroceder al inicio
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                }
            )
        })
    }

internal fun MainActivity.showQuimicoForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        showQuimicoInventarioForm(ModulosInventario.AGROQUIMICOS, pItem, pCant, pSol, pCat, pRef)
    }

internal fun MainActivity.showLubricantesTallerForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        showQuimicoInventarioForm(ModulosInventario.LUBRICANTES_TALLER, pItem, pCant, pSol, pCat, pRef)
    }

private fun MainActivity.showQuimicoInventarioForm(
        moduloOperativo: String,
        pItem: String = "",
        pCant: String = "",
        pSol: String = "",
        pCat: String = "",
        pRef: String = "",
        catalogoRefrescado: Boolean = false,
    ) {
        val usaAreasCop = ModulosInventario.esModuloLubricantesTaller(moduloOperativo)
        currentScreenRenderer = {
            if (usaAreasCop) showLubricantesTallerForm(pItem, pCant, pSol, pCat, pRef)
            else showQuimicoForm(pItem, pCant, pSol, pCat, pRef)
        }
        val titulo = if (usaAreasCop) "Lubricantes taller" else "Salida de agroqu\u00edmico"
        val subtitulo = if (usaAreasCop) {
            "Traslada a \u00e1reas COP y registra el consumo final desde el \u00e1rea real."
        } else {
            "Registra categor\u00eda, uso y stock real por ubicaci\u00f3n."
        }
        val root = baseScreen(titulo, subtitulo)

        fun catalogoQuimicoActual() = catalogoCargado[moduloOperativo] ?: mapOf()
        fun esValido(valor: String) = valor.isNotBlank() && !valor.startsWith("Selecciona")

        if (!catalogoRefrescado && catalogoQuimicoActual().isEmpty()) {
            root.addView(infoText("Sincronizando inventario..."))
            sincronizarCatalogo {
                if (pantallaActiva()) {
                    showQuimicoInventarioForm(moduloOperativo, pItem, pCant, pSol, pCat, pRef, catalogoRefrescado = true)
                }
            }
            return
        }

        val catalogoFormulario = catalogoQuimicoActual()
        fun getCatalogo() = catalogoFormulario
        fun catalogoQuimicoComoMapAny(): Map<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return catalogoFormulario as Map<String, Any>
        }

        val catSpinner = spinner(root, "Categor\u00eda *", getCatalogo().keys.toList())
        val subcatSpinner = spinner(root, "Subcategor\u00eda / uso *", listOf("Selecciona categor\u00eda"))
        val itemSpinner = spinner(root, "\u00cdtem *", listOf("Selecciona subcategor\u00eda"))
        val codigoInterno = codigoInternoField(root, "C\u00f3digo", "Escribe o selecciona un producto", scan = false)

        val stockLabel = TextView(this).apply {
            text = "Selecciona un producto para ver ubicaci\u00f3n y stock"
            textSize = 13f
            setTextColor(verdeOscuro)
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(stockLabel)

        var codigoInternoSeleccionado = ""
        var codigoOriginalSeleccionado = ""
        var ubicacionesActuales = listOf<QuimicoUbicacionStock>()
        var ubicacionSeleccionada: QuimicoUbicacionStock? = null
        var actualizandoSpinnerQuimico = false
        var categoriaSeleccionada = pCat
        var subcategoriaSeleccionada = pItem
        var itemSeleccionado = pRef.ifBlank { pItem }

        fun setUnidad(unidadSpinner: Spinner, unidad: String) {
            val adapter = unidadSpinner.adapter as? ArrayAdapter<String> ?: return
            val pos = adapter.getPosition(unidad.ifBlank { "UNIDAD" })
            if (pos >= 0) unidadSpinner.setSelection(pos)
        }

        fun subcategoriasQuimico(categoria: String): List<String> {
            val subcategorias = (getCatalogo()[categoria] as? Map<*, *>)?.keys
                ?.map { it.toString() }
                ?.filterNot { esEntradaCatalogoSulcamagVieja(it) }
                ?.sortedBy { normalizarBusqueda(it) }
                .orEmpty()
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo categoria=$categoria items=${subcategorias.size}")
            return subcategorias.ifEmpty { listOf("Selecciona categoría") }
        }

        fun itemsQuimico(categoria: String, subcategoria: String): List<String> {
            val items = ((getCatalogo()[categoria] as? Map<*, *>)?.get(subcategoria) as? List<*>)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty()
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo item=$subcategoria referencias=${items.size}")
            return items.ifEmpty { listOf("Selecciona subcategoría") }
        }

        fun actualizarItemsQuimico(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            val subcategoria = subcategoriaSeleccionada.ifBlank { subcatSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerQuimico = true
            itemSpinner.tag = "SINCRO"
            val items = itemsQuimico(categoria, subcategoria)
            itemSpinner.adapter = adapterSpinnerDependiente(items)
            val preservado = preservar && seleccionarValorSpinnerDependiente(itemSpinner, items, itemSeleccionado)
            if (!preservado) {
                itemSpinner.setSelection(0, false)
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
            } else {
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerQuimico = false
            limpiarTagSpinnerDependiente(itemSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo itemPreservado=$preservado origen=$origen")
        }

        fun actualizarSubcategoriasQuimico(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerQuimico = true
            subcatSpinner.tag = "SINCRO"
            val subcategorias = subcategoriasQuimico(categoria)
            subcatSpinner.adapter = adapterSpinnerDependiente(subcategorias)
            val preservada = preservar && seleccionarValorSpinnerDependiente(subcatSpinner, subcategorias, subcategoriaSeleccionada)
            if (!preservada) {
                subcatSpinner.setSelection(0, false)
                subcategoriaSeleccionada = subcatSpinner.selectedItem?.toString().orEmpty()
                itemSeleccionado = ""
            } else {
                subcategoriaSeleccionada = subcatSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerQuimico = false
            limpiarTagSpinnerDependiente(subcatSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo subcategoriaPreservada=$preservada origen=$origen")
            actualizarItemsQuimico(origen, preservar = preservada)
        }

        val unidadSpinner = spinner(root, "Unidad", listOf("GRAMO", "ML", "UNIDAD", "LITRO", "GALON", "KG"))

        lateinit var btnUbicacion: Button

        fun actualizarResumenUbicacion() {
            val total = ubicacionesActuales.sumOf { it.cantidad }
            val unidad = ubicacionSeleccionada?.unidad
                ?: ubicacionesActuales.firstOrNull { it.unidad.isNotBlank() }?.unidad
                ?: ""
            val seleccion = ubicacionSeleccionada
            val detalleUbicaciones = ubicacionesActuales.joinToString("\n") { opcion ->
                "${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}"
            }
            stockLabel.text = when {
                ubicacionesActuales.isEmpty() -> "Ubicaci\u00f3n no disponible"
                ubicacionesActuales.size > 1 && seleccion != null ->
                    "$detalleUbicaciones\nTotal acumulado: ${cantidadTexto(total)} $unidad\nSalida desde: ${seleccion.ubicacion}"
                else -> "$detalleUbicaciones\nTotal acumulado: ${cantidadTexto(total)} $unidad"
            }
            stockLabel.setTextColor(if ((seleccion?.cantidad ?: total) <= 0.0) Color.RED else verdeOscuro)
            btnUbicacion.isEnabled = ubicacionesActuales.size > 1 || (usaAreasCop && ubicacionesActuales.isNotEmpty())
            btnUbicacion.alpha = if (btnUbicacion.isEnabled) 1f else 0.55f
            btnUbicacion.text = when {
                usaAreasCop && ubicacionesActuales.isNotEmpty() -> "\u00c1rea COP / traslado"
                ubicacionesActuales.size > 1 -> "Elegir ubicaci\u00f3n / traslado"
                ubicacionesActuales.size == 1 -> "Ubicaci\u00f3n \u00fanica"
                else -> "Ubicaci\u00f3n"
            }
        }

        fun seleccionarUbicacion(opcion: QuimicoUbicacionStock?) {
            ubicacionSeleccionada = opcion
            if (opcion != null) {
                codigoInternoSeleccionado = opcion.documentoId
                codigoOriginalSeleccionado = opcion.codigoOriginal
                codigoInterno.tag = "auto_fill"
                codigoInterno.setText(opcion.codigoOriginal, false)
                codigoInterno.setSelection(codigoInterno.text.length)
                codigoInterno.tag = null
                setUnidad(unidadSpinner, opcion.unidad)
            }
            actualizarResumenUbicacion()
        }

        fun cargarUbicaciones(codigoOriginal: String, item: String, ubicacionPreferida: String = "") {
            codigoOriginalSeleccionado = codigoOriginal
            cargarUbicacionesQuimico(codigoOriginal, item, { opciones ->
                ubicacionesActuales = opciones
                val preferida = opciones.find { it.documentoId == codigoInternoSeleccionado }
                    ?: opciones.find { it.ubicacion.equals(ubicacionPreferida, ignoreCase = true) }
                    ?: opciones.firstOrNull()
                seleccionarUbicacion(preferida)
            }, {
                stockLabel.text = "Stock no disponible en l\u00ednea"
            }, moduloFiltro = moduloOperativo)
        }

        fun aplicarCanonico(producto: QuimicoCanonico?) {
            if (producto == null) {
                codigoInternoSeleccionado = ""
                codigoOriginalSeleccionado = ""
                ubicacionesActuales = emptyList()
                ubicacionSeleccionada = null
                codigoInterno.tag = "auto_fill"
                codigoInterno.setText("", false)
                codigoInterno.tag = null
                actualizarResumenUbicacion()
                return
            }
            codigoInternoSeleccionado = producto.documentoId
            codigoOriginalSeleccionado = producto.codigoOriginal
            codigoInterno.tag = "auto_fill"
            codigoInterno.setText(producto.codigoOriginal, false)
            codigoInterno.setSelection(codigoInterno.text.length)
            codigoInterno.tag = null
            setUnidad(unidadSpinner, producto.unidad)
            cargarUbicaciones(producto.codigoOriginal, producto.item, producto.ubicacion)
        }

        btnUbicacion = primaryButton("Ubicaci\u00f3n") { }.apply {
            isEnabled = false
            setBackgroundColor(Color.rgb(0, 120, 200))
            setOnClickListener {
                if (!usaAreasCop && ubicacionesActuales.size <= 1) return@setOnClickListener
                showDialogUbicacionesQuimico(
                    opciones = ubicacionesActuales,
                    onSelected = { opcion ->
                        val canonico = QuimicosCanonicos.buscarPorCodigoUbicacion(opcion.codigoOriginal, opcion.ubicacion, opcion.item)
                        if (canonico != null) {
                            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, catalogoQuimicoComoMapAny(), canonico.categoria, canonico.subcategoria, canonico.item) {
                                seleccionarUbicacion(opcion)
                            }
                        } else {
                            seleccionarUbicacion(opcion)
                        }
                    },
                    onTransferDone = {
                        cargarUbicaciones(codigoOriginalSeleccionado, itemSpinner.selectedItem?.toString().orEmpty(), ubicacionSeleccionada?.ubicacion.orEmpty())
                    },
                    permitirAreasCop = usaAreasCop
                )
            }
        }
        root.addView(btnUbicacion)

        setupCodigoInternoSalida(root, codigoInterno, moduloOperativo) { producto ->
            codigoInternoSeleccionado = producto.documentoId.ifBlank { producto.codigoInterno }
            codigoOriginalSeleccionado = producto.codigoOriginal.ifBlank { producto.codigoInterno }
            val subcategoria = producto.subcategoria.ifBlank { producto.ubicacion }
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, catalogoQuimicoComoMapAny(), producto.categoria, subcategoria, producto.item) {
                setUnidad(unidadSpinner, producto.unidad)
                cargarUbicaciones(codigoOriginalSeleccionado, producto.item, producto.ubicacion)
            }
        }

        setupSearchBar(root, moduloOperativo) { _, c, sub, item ->
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, catalogoQuimicoComoMapAny(), c, sub, item) {
                aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, c, sub, item))
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerQuimico || catSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo seleccion inicial categoria ignorada")
                    return
                }
                categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty()
                val subActual = subcatSpinner.selectedItem?.toString().orEmpty()
                val itemActual = itemSpinner.selectedItem?.toString().orEmpty()
                subcategoriaSeleccionada = if (!esPlaceholderSpinnerDependiente(subActual)) subActual else ""
                itemSeleccionado = if (!esPlaceholderSpinnerDependiente(itemActual)) itemActual else ""
                actualizarSubcategoriasQuimico("categoria", preservar = true)
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        subcatSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerQuimico || subcatSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo seleccion inicial subcategoria ignorada")
                    return
                }
                subcategoriaSeleccionada = subcatSpinner.selectedItem?.toString().orEmpty()
                val itemActual = itemSpinner.selectedItem?.toString().orEmpty()
                itemSeleccionado = if (!esPlaceholderSpinnerDependiente(itemActual)) itemActual else itemSeleccionado
                actualizarItemsQuimico("subcategoria", preservar = true)
                if (subcatSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    aplicarCanonico(null)
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerQuimico || itemSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=$titulo seleccion inicial item ignorada")
                    return
                }
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                val sub = subcatSpinner.selectedItem?.toString().orEmpty()
                val item = itemSpinner.selectedItem?.toString().orEmpty()
                itemSeleccionado = item
                if (esValido(cat) && esValido(sub) && esValido(item)) {
                    aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, cat, sub, item))
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        if (pCat.isNotBlank() && pRef.isNotBlank()) {
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, catalogoQuimicoComoMapAny(), pCat, pItem, pRef) {
                aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, pCat, pItem, pRef))
            }
        } else if (pItem.isNotBlank()) {
            resolverProductoCatalogo(moduloOperativo, pItem)?.let { p ->
                seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, catalogoQuimicoComoMapAny(), p.categoria, p.item, p.referencia) {
                    aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, p.categoria, p.item, p.referencia))
                }
            }
        }

        categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty().ifBlank { categoriaSeleccionada }
        val subInicio = subcatSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(subInicio)) subcategoriaSeleccionada = subInicio
        val itemInicio = itemSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(itemInicio)) itemSeleccionado = itemInicio
        actualizarSubcategoriasQuimico("inicio", preservar = true)

        val cantidad = field(root, "Cantidad *", "Ej: 1.5", number = true)
        cantidad.setText(pCant)
        val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol)
        val labor = field(root, if (usaAreasCop) "\u00c1rea / equipo / labor *" else "Labor / aplicaci\u00f3n *", if (usaAreasCop) "Ej: taller, tractor, mantenimiento" else "Ej: vivero, fertilizaci\u00f3n, control sanitario")
        val observaciones = field(root, "Observaciones", if (usaAreasCop) "Orden de trabajo, equipo o responsable" else "Lote, dosis, \u00e1rea o recomendaci\u00f3n")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(primaryButton("Tomar evidencia") {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, moduloOperativo) { url -> urlEvidencia = url }
                }
            }
        }.apply { setBackgroundColor(Color.rgb(100, 100, 110)) })

        root.addView(primaryButton("Nueva entrada") {
            val seleccion = ubicacionSeleccionada
            if (seleccion == null) {
                Toast.makeText(this, "Selecciona un qu\u00edmico y ubicaci\u00f3n primero", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            showDialogEntradaStockQuimico(seleccion) {
                cargarUbicaciones(seleccion.codigoOriginal, seleccion.item, seleccion.ubicacion)
            }
        }.apply { setBackgroundColor(Color.rgb(0, 120, 200)) })

        root.addView(primaryButton(if (usaAreasCop) "Guardar consumo final" else "Guardar salida") {
            if (!required(cantidad, solicitante, labor)) return@primaryButton

            val catVal = catSpinner.selectedItem?.toString().orEmpty()
            val subVal = subcatSpinner.selectedItem?.toString().orEmpty()
            val itemVal = itemSpinner.selectedItem?.toString().orEmpty()
            val seleccion = ubicacionSeleccionada
            if (!esValido(catVal) || !esValido(subVal) || !esValido(itemVal) || seleccion == null) {
                Toast.makeText(this, "Selecciona producto y ubicaci\u00f3n v\u00e1lidos", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val uniVal = unidadSpinner.selectedItem.toString()
            val obsBase = observaciones.text.toString()
            val obsConUbicacion = listOf("Ubicaci\u00f3n: ${seleccion.ubicacion}", obsBase)
                .filter { it.isNotBlank() }
                .joinToString(". ")

            val mov = Movimiento(
                fecha = now(),
                modulo = moduloOperativo,
                tipoMovimiento = if (usaAreasCop) "Consumo final" else "Salida",
                item = itemVal,
                referencia = seleccion.codigoOriginal,
                cantidad = cantidad.text.toString(),
                unidad = uniVal,
                solicitante = solicitante.text.toString(),
                labor = labor.text.toString(),
                observaciones = obsConUbicacion
            )

            registrarSalidaCloudPrimero(
                mov = mov,
                cantidadNumerica = cantVal,
                codigoInternoPreferido = seleccion.documentoId,
                itemBase = itemVal,
                fotoUrl = urlEvidencia.ifBlank { uriLocalEvidencia },
                onSuccess = {
                    saved(if (usaAreasCop) "Consumo registrado desde ${seleccion.ubicacion}" else "Agroqu\u00edmico guardado desde ${seleccion.ubicacion}")
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    cargarUbicaciones(seleccion.codigoOriginal, seleccion.item, seleccion.ubicacion)
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                }
            )
        })
    }

internal fun MainActivity.showEPPForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        currentScreenRenderer = { showEPPForm(pItem, pCant, pSol, pCat, pRef) }
        val root = baseScreen("Entrega de EPP", "Registro obligatorio de implementos de seguridad con control de frecuencia.")
        
        val categoriasEppPermitidas = listOf(
            "Protección Cabeza y Rostro",
            "Protección Visual",
            "Protección Respiratoria",
            "Protección Manual (Guantes)",
            "Protección Auditiva",
            "Cuerpo y Extremidades"
        )

        fun crearCatalogoEpp(): Map<String, MutableMap<String, MutableList<String>>> {
            val base = catalogoCargado["EPP"] ?: return emptyMap()
            return categoriasEppPermitidas.mapNotNull { categoria ->
                base[categoria]?.takeIf { it.isNotEmpty() }?.let { categoria to it }
            }.toMap()
        }
        val catalogoFormulario = crearCatalogoEpp()
        fun getCatalogo() = catalogoFormulario
        fun catalogoEppComoMapAny(): Map<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return catalogoFormulario as Map<String, Any>
        }

        val catSpinner = spinner(root, "Categoría *", getCatalogo().keys.toList())
        val itemSpinner = spinner(root, "Item *", listOf("Selecciona categoria"))
        val refSpinner = spinner(root, "Talla / referencia *", listOf("Selecciona item"))
        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe o elige el código interno", scan = false)
        var codigoInternoSeleccionado = ""

        var actualizandoSpinnerEpp = false
        var categoriaSeleccionada = pCat
        var itemSeleccionado = pItem
        var referenciaSeleccionada = pRef

        val stockDisponible = stockInfoCard("Disponible: selecciona un item para consultar stock")
        root.addView(stockDisponible)
        var stockConsultaActual = 0

        fun cantidadLegible(valor: Double): String {
            return if (valor % 1.0 == 0.0) valor.toInt().toString()
            else String.format(Locale.getDefault(), "%.2f", valor)
        }

        fun itemsEpp(categoria: String): List<String> {
            val items = getCatalogo()[categoria]?.keys?.toList()?.sortedBy { normalizarBusqueda(it) }.orEmpty()
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP categoria=$categoria items=${items.size}")
            return items.ifEmpty { listOf("Sin items registrados") }
        }

        fun referenciasEpp(categoria: String, item: String): List<String> {
            val refs = getCatalogo()[categoria]?.get(item)?.filter { it.isNotBlank() }?.distinct().orEmpty()
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP item=$item referencias=${refs.size}")
            return refs.ifEmpty { listOf("N/A") }
        }

        fun actualizarReferenciasEpp(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            val item = itemSeleccionado.ifBlank { itemSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerEpp = true
            refSpinner.tag = "SINCRO"
            val refs = referenciasEpp(categoria, item)
            refSpinner.adapter = adapterSpinnerDependiente(refs)
            val preservada = preservar && seleccionarValorSpinnerDependiente(refSpinner, refs, referenciaSeleccionada)
            if (!preservada) {
                refSpinner.setSelection(0, false)
                referenciaSeleccionada = refSpinner.selectedItem?.toString().orEmpty()
            } else {
                referenciaSeleccionada = refSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerEpp = false
            limpiarTagSpinnerDependiente(refSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP referenciaPreservada=$preservada origen=$origen")
        }

        fun actualizarItemsEpp(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            actualizandoSpinnerEpp = true
            itemSpinner.tag = "SINCRO"
            val items = itemsEpp(categoria)
            itemSpinner.adapter = adapterSpinnerDependiente(items)
            val preservado = preservar && seleccionarValorSpinnerDependiente(itemSpinner, items, itemSeleccionado)
            if (!preservado) {
                itemSpinner.setSelection(0, false)
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
                referenciaSeleccionada = ""
            } else {
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerEpp = false
            limpiarTagSpinnerDependiente(itemSpinner)
            android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP itemPreservado=$preservado origen=$origen")
            actualizarReferenciasEpp(origen, preservar = preservado)
        }

        fun mostrarStock(producto: ExistenciaProducto?) {
            if (producto == null) {
                android.util.Log.d(BUG_STOCK_EPP_TAG, "stock registros=0 stock=0 unidad=UNIDAD")
                stockDisponible.text = "Disponible: 0 UNIDAD"
                stockDisponible.setTextColor(Color.rgb(160, 80, 0))
                return
            }
            val unidad = producto.unidad.ifBlank { "Unidad" }
            val referencia = producto.referenciaCatalogo.ifBlank { producto.referencia }
            val detalle = listOf(producto.codigoInterno, producto.item, referencia).filter { it.isNotBlank() }.joinToString(" - ")
            android.util.Log.d(BUG_STOCK_EPP_TAG, "stock registros=1 stock=${producto.cantidad} unidad=$unidad codigo=${producto.codigoInterno}")
            stockDisponible.text = "Disponible: ${cantidadLegible(producto.cantidad)} $unidad\n$detalle"
            stockDisponible.setTextColor(if (producto.cantidad > 0.0) verdeOscuro else Color.rgb(180, 40, 40))
        }

        fun actualizarStockEpp(codigoPreferido: String = "") {
            val consulta = ++stockConsultaActual
            val codigoCampo = normalizarCodigoInterno(codigoInterno.text?.toString().orEmpty())
            val itemVal = itemSpinner.selectedItem?.toString()?.takeUnless { esPlaceholderSpinnerDependiente(it) }.orEmpty()
            val refVal = refSpinner.selectedItem?.toString()?.takeUnless { esPlaceholderSpinnerDependiente(it) }.orEmpty()
            val categoriaVal = catSpinner.selectedItem?.toString().orEmpty()
            val codigo = normalizarCodigoInterno(codigoPreferido).ifBlank {
                if (itemVal.isBlank() || refVal.isBlank()) codigoCampo else ""
            }
            android.util.Log.d(
                BUG_STOCK_EPP_TAG,
                "consulta categoria=$categoriaVal item=$itemVal referencia=$refVal codigo=$codigo codigoSeleccionado=$codigoInternoSeleccionado"
            )
            stockDisponible.text = "Disponible: consultando..."
            stockDisponible.setTextColor(gris)

            if (codigo.isNotBlank()) {
                buscarExistenciaPorCodigoInterno(codigo, "EPP", { producto ->
                    if (consulta == stockConsultaActual) {
                        if (producto == null) {
                            android.util.Log.d(BUG_STOCK_EPP_TAG, "sin coincidencias por codigo=$codigo categoria=$categoriaVal item=$itemVal referencia=$refVal")
                        }
                        mostrarStock(producto)
                    }
                }, {
                    if (consulta == stockConsultaActual) {
                        android.util.Log.d(BUG_STOCK_EPP_TAG, "fallo consulta codigo=$codigo error=${it.localizedMessage ?: "desconocido"}")
                        stockDisponible.text = "Disponible: no se pudo consultar Firestore"
                        stockDisponible.setTextColor(Color.rgb(180, 40, 40))
                    }
                })
                return
            }

            if (itemVal.isBlank() || refVal.isBlank()) {
                android.util.Log.d(BUG_STOCK_EPP_TAG, "sin coincidencias filtros incompletos categoria=$categoriaVal item=$itemVal referencia=$refVal codigo=$codigoCampo")
                stockDisponible.text = "Disponible: selecciona un item para consultar stock"
                stockDisponible.setTextColor(gris)
                return
            }

            buscarExistenciaPorProducto("EPP", itemVal, refVal, { producto ->
                if (consulta == stockConsultaActual) {
                    if (producto == null) {
                        android.util.Log.d(BUG_STOCK_EPP_TAG, "sin coincidencias categoria=$categoriaVal item=$itemVal referencia=$refVal")
                    }
                    mostrarStock(producto)
                }
            }, {
                if (consulta == stockConsultaActual) {
                    android.util.Log.d(BUG_STOCK_EPP_TAG, "fallo consulta categoria=$categoriaVal item=$itemVal referencia=$refVal error=${it.localizedMessage ?: "desconocido"}")
                    stockDisponible.text = "Disponible: no se pudo consultar Firestore"
                    stockDisponible.setTextColor(Color.rgb(180, 40, 40))
                }
            })
        }

        setupCodigoInternoSalida(root, codigoInterno, "EPP") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            android.util.Log.d(BUG_STOCK_EPP_TAG, "codigo interno seleccionado=$codigoInternoSeleccionado item=${producto.item} referencia=${producto.referenciaCatalogo}")
            seleccionarProductoDesdeExistencia(producto, catSpinner, itemSpinner, refSpinner, catalogoEppComoMapAny())
            mostrarStock(producto)
        }

        setupSearchBar(root, "EPP") { _, c, i, r ->
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, catalogoEppComoMapAny(), c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "EPP", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    android.util.Log.d(BUG_STOCK_EPP_TAG, "codigo interno seleccionado=$codigoInternoSeleccionado item=$i referencia=$r")
                    mostrarStock(producto)
                }
            }
        }

        if (pCat.isNotBlank() && pItem.isNotBlank()) {
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, catalogoEppComoMapAny(), pCat, pItem, pRef) {
                autocompletarCodigoDesdeProducto(codigoInterno, "EPP", pItem, pRef) { encontrado ->
                    codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                    android.util.Log.d(BUG_STOCK_EPP_TAG, "codigo interno seleccionado=$codigoInternoSeleccionado item=$pItem referencia=$pRef")
                    mostrarStock(encontrado)
                }
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerEpp || catSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP seleccion inicial categoria ignorada")
                    return
                }
                categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty()
                val itemActual = itemSpinner.selectedItem?.toString().orEmpty()
                val refActual = refSpinner.selectedItem?.toString().orEmpty()
                itemSeleccionado = if (!esPlaceholderSpinnerDependiente(itemActual)) itemActual else ""
                referenciaSeleccionada = if (!esPlaceholderSpinnerDependiente(refActual)) refActual else ""
                android.util.Log.d(BUG_STOCK_EPP_TAG, "categoria seleccionada=$categoriaSeleccionada")
                actualizarItemsEpp("categoria", preservar = true)
                codigoInternoSeleccionado = ""
                codigoInterno.setText("", false)
                actualizarStockEpp()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerEpp || itemSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP seleccion inicial item ignorada")
                    return
                }
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
                val refActual = refSpinner.selectedItem?.toString().orEmpty()
                referenciaSeleccionada = if (!esPlaceholderSpinnerDependiente(refActual)) refActual else referenciaSeleccionada
                android.util.Log.d(BUG_STOCK_EPP_TAG, "item seleccionado=$itemSeleccionado")
                actualizarReferenciasEpp("item", preservar = true)
                
                // Limpieza de código previo si es manual
                if (itemSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    codigoInternoSeleccionado = ""
                    codigoInterno.setText("", false)
                }
                actualizarStockEpp()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        refSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerEpp || refSpinner.tag == "SINCRO") {
                    android.util.Log.d(BUG_SPINNERS_TAG, "pantalla=EPP seleccion inicial referencia ignorada")
                    return
                }
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                referenciaSeleccionada = refVal
                android.util.Log.d(BUG_STOCK_EPP_TAG, "tallaReferencia seleccionada=$referenciaSeleccionada item=$itemVal")
                if (!esPlaceholderSpinnerDependiente(itemVal) && !esPlaceholderSpinnerDependiente(refVal)) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "EPP", itemVal, refVal) { producto ->
                        codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                        android.util.Log.d(BUG_STOCK_EPP_TAG, "codigo interno seleccionado=$codigoInternoSeleccionado")
                        mostrarStock(producto)
                    }
                } else {
                    actualizarStockEpp()
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty().ifBlank { categoriaSeleccionada }
        val itemInicio = itemSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(itemInicio)) itemSeleccionado = itemInicio
        val refInicio = refSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderSpinnerDependiente(refInicio)) referenciaSeleccionada = refInicio
        actualizarItemsEpp("inicio", preservar = true)
        actualizarStockEpp()

        val cantidad = field(root, "Cantidad *", "Ej: 1", number = true)
        cantidad.setText(pCant.ifBlank { "1" })
        val solicitante = autoField(root, "Solicitante *", "Nombre de quien recibe")
        solicitante.setText(pSol)
        val observaciones = field(root, "Observaciones", "Opcional")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(evidenceButton {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, "epp") { url -> urlEvidencia = url }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                showDialogNuevoProducto("EPP") { c, i, r ->
                    showEPPForm(pItem = i, pCant = pCant, pSol = pSol, pCat = c, pRef = r)
                }
            },
            onEntrada = {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (!esPlaceholderSpinnerDependiente(itemVal)) {
                    showDialogEntradaStock("EPP", itemVal, refVal) { showEPPForm(pItem, pCant, pSol) }
                } else {
                    Toast.makeText(this, "Selecciona un ítem primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Registrar Entrega") {
            val codigoPreferido = codigoInternoValidadoParaSalida(codigoInterno, codigoInternoSeleccionado)
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""
            
            if (esPlaceholderSpinnerDependiente(itemVal) || esPlaceholderSpinnerDependiente(refVal)) {
                Toast.makeText(this, "Selecciona un ítem y referencia válidos", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val nombreItem = "$itemVal - $refVal"
            val nombreSolicitante = solicitante.text.toString().trim()
            if (!required(solicitante, cantidad)) return@primaryButton

            val registrar: (String) -> Unit = { fotoFinal ->
            val mov = Movimiento(
                fecha = now(),
                modulo = "EPP",
                tipoMovimiento = "Salida",
                item = nombreItem,
                referencia = refVal,
                cantidad = cantidad.text.toString(),
                unidad = "Unidad",
                solicitante = nombreSolicitante,
                labor = "Protección Personal",
                observaciones = observaciones.text.toString()
            )
            registrarSalidaCloudPrimero(
                mov = mov,
                cantidadNumerica = cantidad.text.toString().toDoubleOrNull() ?: 0.0,
                codigoInternoPreferido = codigoPreferido,
                itemBase = itemVal,
                fotoUrl = fotoFinal,
                onSuccess = {
                    saved("EPP entregado con evidencia")
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    actualizarStockEpp(codigoPreferido)
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                }
            )
        }

            fun registrarConEvidenciaLista() {
                when {
                    urlEvidencia.isNotBlank() -> registrar(urlEvidencia)
                    uriLocalEvidencia.isNotBlank() && isNetworkAvailable() -> {
                        Toast.makeText(this, "Subiendo evidencia EPP...", Toast.LENGTH_SHORT).show()
                        subirEvidenciaCloud(uriLocalEvidencia, "epp") { url ->
                            urlEvidencia = url
                            registrar(url)
                        }
                    }
                    uriLocalEvidencia.isNotBlank() -> registrar(uriLocalEvidencia)
                    else -> registrar("")
                }
            }

            val ultimaFechaStr = db.buscarUltimaEntregaEPP(nombreSolicitante, nombreItem)
            if (ultimaFechaStr != null) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val ultimaFecha = sdf.parse(ultimaFechaStr)
                    val hoy = Date()
                    if (ultimaFecha != null) {
                        val diff = hoy.time - ultimaFecha.time
                        val dias = diff / (1000 * 60 * 60 * 24)
                        if (dias < 30) {
                            AlertDialog.Builder(this)
                                .setTitle("¡Alerta de frecuencia!")
                                .setMessage("El trabajador $nombreSolicitante ya recibió $nombreItem hace solo $dias días (Fecha: $ultimaFechaStr).\n\n¿Deseas registrar la entrega de todas formas?")
                                .setPositiveButton("Sí, registrar") { _, _ -> registrarConEvidenciaLista() }
                                .setNegativeButton("No, cancelar", null)
                                .show()
                            return@primaryButton
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EPP", "Error validando fecha", e)
                }
            }
            registrarConEvidenciaLista()
        })
    }

internal fun MainActivity.guardarEPP(
        item: String,
        cantidad: String,
        solicitante: String,
        obs: String,
        codigoInternoPreferido: String = "",
        itemBase: String = item,
        referencia: String = ""
    ) {
        val cantVal = cantidad.toDoubleOrNull() ?: 0.0
        val mov = Movimiento(
            fecha = now(),
            modulo = "EPP",
            tipoMovimiento = "Salida",
            item = item,
            referencia = referencia,
            cantidad = cantidad,
            unidad = "Unidad",
            solicitante = solicitante,
            labor = "Protección Personal",
            observaciones = obs
        )
        registrarSalidaCloudPrimero(
            mov = mov,
            cantidadNumerica = cantVal,
            codigoInternoPreferido = codigoInternoPreferido,
            itemBase = itemBase,
            onSuccess = {
                saved("EPP guardado con stock trazable")
                // Permanecer en la misma pantalla (no llamamos a showMainMenu())
                // En esta función modularizada los argumentos son Strings, no Views
            }
        )
    }

internal fun MainActivity.showAseoForm(pItem: String = "", pCant: String = "", pSol: String = "", pLab: String = "", pCat: String = "") {
        try {
            showAseoFormInterno(pItem, pCant, pSol, pLab, pCat)
        } catch (e: Exception) {
            android.util.Log.e("ArlesGestion", "Error abriendo ASEO", e)
            Toast.makeText(this, "No se pudo abrir ASEO: ${e.localizedMessage ?: "error inesperado"}", Toast.LENGTH_LONG).show()
            showMainMenu()
        }
    }

private fun MainActivity.showAseoFormInterno(pItem: String = "", pCant: String = "", pSol: String = "", pLab: String = "", pCat: String = "") {
        currentScreenRenderer = { showAseoForm(pItem, pCant, pSol, pLab, pCat) }
        val root = baseScreen("ASEO", "Registra salidas, entradas y traslados internos por area.")

        fun getCatalogo() = catalogoCargado[AseoCanonicos.MODULO] ?: mapOf()
        fun categoriasAseo(): List<String> {
            val desdeCatalogo = getCatalogo().keys.toList().sorted()
            if (desdeCatalogo.isNotEmpty()) return desdeCatalogo
            return AseoCanonicos.items.map { it.categoria }.distinct().sorted()
        }
        fun itemsAseo(categoria: String): List<String> {
            @Suppress("UNCHECKED_CAST")
            val desdeCatalogo = (getCatalogo()[categoria] as? Map<String, Any>)?.keys?.toList()?.sorted().orEmpty()
            if (desdeCatalogo.isNotEmpty()) return desdeCatalogo
            return AseoCanonicos.items.filter { it.categoria == categoria }.map { it.producto }.distinct().sorted()
        }
        fun esValido(valor: String) = valor.isNotBlank() && !valor.startsWith("Selecciona")

        val catSpinner = spinner(root, "Categor\u00eda *", categoriasAseo().ifEmpty { listOf("Sin categor\u00edas") })
        val itemSpinner = spinner(root, "\u00cdtem *", listOf("Selecciona categor\u00eda"))
        val codigoInterno = codigoInternoField(root, "C\u00f3digo", "Escribe o selecciona producto", scan = false)

        val stockLabel = TextView(this).apply {
            text = "Selecciona un producto para ver stock y ubicaciones"
            textSize = 13f
            setTextColor(verdeOscuro)
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(stockLabel)

        var codigoInternoSeleccionado = ""
        var codigoOriginalSeleccionado = ""
        var ubicacionesActuales = listOf<AseoUbicacionStock>()
        var ubicacionSeleccionada: AseoUbicacionStock? = null
        var eventosSpinnerActivos = false
        var botonesListos = false
        lateinit var btnAreas: Button
        lateinit var btnEntrada: Button

        fun actualizarResumen() {
            val total = ubicacionesActuales.sumOf { it.cantidad }
            val unidad = ubicacionSeleccionada?.unidad
                ?: ubicacionesActuales.firstOrNull { it.unidad.isNotBlank() }?.unidad
                ?: ""
            val detalle = ubicacionesActuales.joinToString("\n") { opcion ->
                "${opcion.ubicacion}: ${cantidadTexto(opcion.cantidad)} ${opcion.unidad}"
            }
            stockLabel.text = when {
                ubicacionesActuales.isEmpty() -> "Stock no disponible"
                else -> "$detalle\nTotal: ${cantidadTexto(total)} $unidad"
            }
            stockLabel.setTextColor(if ((ubicacionSeleccionada?.cantidad ?: total) <= 0.0) Color.RED else verdeOscuro)
            if (!botonesListos) return
            val activo = ubicacionesActuales.isNotEmpty()
            btnAreas.isEnabled = activo
            btnEntrada.isEnabled = ubicacionSeleccionada != null
            listOf(btnAreas, btnEntrada).forEach { it.alpha = if (it.isEnabled) 1f else 0.55f }
        }

        fun seleccionarUbicacion(opcion: AseoUbicacionStock?) {
            ubicacionSeleccionada = opcion
            if (opcion != null) {
                codigoInternoSeleccionado = opcion.documentoId
                codigoOriginalSeleccionado = opcion.codigoOriginal
                codigoInterno.tag = "auto_fill"
                codigoInterno.setText(opcion.codigoOriginal, false)
                codigoInterno.setSelection(codigoInterno.text.length)
                codigoInterno.tag = null
            }
            actualizarResumen()
        }

        fun cargarUbicaciones(codigoOriginal: String, item: String, ubicacionPreferida: String = "") {
            if (!pantallaActiva()) return
            codigoOriginalSeleccionado = codigoOriginal
            cargarUbicacionesAseo(codigoOriginal, item, { opciones ->
                if (!pantallaActiva()) return@cargarUbicacionesAseo
                ubicacionesActuales = opciones
                val preferida = opciones.find { it.documentoId == codigoInternoSeleccionado }
                    ?: opciones.find { it.ubicacion.equals(ubicacionPreferida, ignoreCase = true) }
                    ?: opciones.firstOrNull()
                seleccionarUbicacion(preferida)
            }, {
                if (!pantallaActiva()) return@cargarUbicacionesAseo
                stockLabel.text = "Stock no disponible en l\u00ednea"
            })
        }

        fun seleccionarAseoSpinners(categoria: String, item: String, onDone: () -> Unit = {}) {
            seleccionarSpinnerPorTexto(catSpinner, categoria) {
                val items = itemsAseo(categoria).ifEmpty { listOf("Sin \u00edtems") }
                val adapter = ArrayAdapter(this@showAseoFormInterno, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                itemSpinner.adapter = adapter
                seleccionarSpinnerPorTexto(itemSpinner, item) { onDone() }
            }
        }

        fun aplicarCanonico(producto: AseoCanonico?) {
            if (producto == null) {
                codigoInternoSeleccionado = ""
                codigoOriginalSeleccionado = ""
                ubicacionesActuales = emptyList()
                ubicacionSeleccionada = null
                codigoInterno.tag = "auto_fill"
                codigoInterno.setText("", false)
                codigoInterno.tag = null
                actualizarResumen()
                return
            }
            codigoInternoSeleccionado = producto.documentoId
            codigoOriginalSeleccionado = producto.codigoOriginal
            codigoInterno.tag = "auto_fill"
            codigoInterno.setText(producto.codigoOriginal, false)
            codigoInterno.setSelection(codigoInterno.text.length)
            codigoInterno.tag = null
            cargarUbicaciones(producto.codigoOriginal, producto.item, producto.ubicacion)
        }

        fun poblarItemsCategoria(categoria: String) {
            val items = itemsAseo(categoria).ifEmpty { listOf("Selecciona \u00edtem") }
            val adapter = ArrayAdapter(this@showAseoFormInterno, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemSpinner.tag = "SINCRO"
            itemSpinner.adapter = adapter
            itemSpinner.post { itemSpinner.tag = null }
        }

        btnAreas = secondaryButton("Traslado interno") {
            showDialogUbicacionesAseo(
                opciones = ubicacionesActuales,
                onSelected = { seleccionarUbicacion(it) },
                onTransferDone = {
                    cargarUbicaciones(
                        codigoOriginalSeleccionado,
                        itemSpinner.selectedItem?.toString().orEmpty(),
                        ubicacionSeleccionada?.ubicacion.orEmpty(),
                    )
                },
            )
        }.apply { isEnabled = false; alpha = 0.55f }
        root.addView(btnAreas)

        btnEntrada = secondaryButton("Nueva entrada") {
            val seleccion = ubicacionSeleccionada ?: return@secondaryButton
            showDialogEntradaStockAseo(seleccion) {
                cargarUbicaciones(seleccion.codigoOriginal, seleccion.item, seleccion.ubicacion)
            }
        }.apply { isEnabled = false; alpha = 0.55f }
        botonesListos = true

        setupCodigoInternoAseo(codigoInterno) { producto ->
            if (!eventosSpinnerActivos) return@setupCodigoInternoAseo
            codigoInternoSeleccionado = producto.documentoId.ifBlank { producto.codigoInterno }
            codigoOriginalSeleccionado = producto.codigoOriginal.ifBlank { producto.codigoInterno }
            seleccionarAseoSpinners(producto.categoria, producto.item) {
                cargarUbicaciones(codigoOriginalSeleccionado, producto.item, producto.ubicacion)
            }
        }

        setupSearchBar(root, AseoCanonicos.MODULO) { _, c, item, _ ->
            if (!eventosSpinnerActivos) return@setupSearchBar
            seleccionarAseoSpinners(c, item) {
                aplicarCanonico(AseoCanonicos.buscar(c, item))
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (!eventosSpinnerActivos || catSpinner.adapter?.count == 0 || catSpinner.tag == "SINCRO") return
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                if (!esValido(cat)) return
                poblarItemsCategoria(cat)
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (!eventosSpinnerActivos || itemSpinner.adapter?.count == 0 || itemSpinner.tag == "SINCRO") return
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                val item = itemSpinner.selectedItem?.toString().orEmpty()
                if (esValido(cat) && esValido(item)) {
                    val canonico = AseoCanonicos.buscar(cat, item)
                    if (canonico != null) {
                        aplicarCanonico(canonico)
                    } else {
                        ubicacionesActuales = emptyList()
                        ubicacionSeleccionada = null
                        actualizarResumen()
                    }
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        root.post {
            val primeraCategoria = catSpinner.selectedItem?.toString().orEmpty()
            if (esValido(primeraCategoria)) poblarItemsCategoria(primeraCategoria)
            eventosSpinnerActivos = true
            actualizarResumen()

            if (pCat.isNotBlank() && pItem.isNotBlank()) {
                seleccionarAseoSpinners(pCat, pItem) {
                    aplicarCanonico(AseoCanonicos.buscar(pCat, pItem))
                }
            } else if (pItem.isNotBlank()) {
                resolverProductoCatalogo(AseoCanonicos.MODULO, pItem)?.let { p ->
                    seleccionarAseoSpinners(p.categoria, p.item) {
                        aplicarCanonico(AseoCanonicos.buscar(p.categoria, p.item))
                    }
                }
            }
        }

        val cantidad = field(root, "Cantidad *", "Ej: 1", number = true)
        cantidad.setText(pCant)
        val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol)
        val labor = field(root, "\u00c1rea / destino *", "Ej: cocina, comedor, ba\u00f1os")
        labor.setText(pLab)
        val observaciones = field(root, "Observaciones", "Opcional")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(primaryButton("Tomar evidencia") {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, AseoCanonicos.MODULO) { url -> urlEvidencia = url }
                }
            }
        }.apply { setBackgroundColor(Color.rgb(100, 100, 110)) })

        val gestionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            setPadding(0, dp(10), 0, dp(10))
        }
        gestionRow.addView(btnEntrada, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(gestionRow)

        root.addView(primaryButton("Guardar salida") {
            if (!required(cantidad, solicitante, labor)) return@primaryButton
            val catVal = catSpinner.selectedItem?.toString().orEmpty()
            val itemVal = itemSpinner.selectedItem?.toString().orEmpty()
            val seleccion = ubicacionSeleccionada
            if (!esValido(catVal) || !esValido(itemVal) || seleccion == null) {
                Toast.makeText(this, "Selecciona producto y ubicaci\u00f3n v\u00e1lidos", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val obsBase = observaciones.text.toString()
            val obs = listOf("Ubicaci\u00f3n: ${seleccion.ubicacion}", obsBase)
                .filter { it.isNotBlank() }
                .joinToString(". ")
            val mov = Movimiento(
                fecha = now(),
                modulo = AseoCanonicos.MODULO,
                tipoMovimiento = "Salida",
                item = itemVal,
                referencia = "N/A",
                cantidad = cantidad.text.toString(),
                unidad = seleccion.unidad,
                solicitante = solicitante.text.toString(),
                labor = labor.text.toString(),
                observaciones = obs
            )
            registrarSalidaAseoFirestore(
                mov = mov,
                opcion = seleccion,
                cantidadNumerica = cantVal,
                fotoUrl = urlEvidencia.ifBlank { uriLocalEvidencia },
                onSuccess = {
                    saved("Salida ASEO guardada")
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    cargarUbicaciones(seleccion.codigoOriginal, seleccion.item, seleccion.ubicacion)
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this, "No se pudo guardar salida ASEO: ${e.localizedMessage ?: "error desconocido"}", Toast.LENGTH_LONG).show()
                }
            )
        })
    }

internal fun MainActivity.showConsumiblesForm(pItem: String = "", pCant: String = "", pSol: String = "", pLab: String = "", pCat: String = "", pRef: String = "", catalogoRefrescado: Boolean = false) {
        if (pCat.isNotBlank()) consumiblesDraftCategoria = pCat
        if (pItem.isNotBlank()) consumiblesDraftItem = pItem
        if (pRef.isNotBlank()) consumiblesDraftReferencia = pRef
        if (pCant.isNotBlank()) consumiblesDraftCantidad = pCant
        if (pSol.isNotBlank()) consumiblesDraftSolicitante = pSol
        if (pLab.isNotBlank()) consumiblesDraftLabor = pLab
        currentScreenRenderer = {
            showConsumiblesForm(
                pItem = consumiblesDraftItem,
                pCant = consumiblesDraftCantidad,
                pSol = consumiblesDraftSolicitante,
                pLab = consumiblesDraftLabor,
                pCat = consumiblesDraftCategoria,
                pRef = consumiblesDraftReferencia,
                catalogoRefrescado = true,
            )
        }
        val root = baseScreen("Salida de consumibles", "Registra materiales de uso diario: repuestos, ferretería, aseo y más.")
        
        fun catalogoConsumiblesActual() = catalogoCargado["Consumibles"] ?: mapOf()

        if (!catalogoRefrescado && catalogoConsumiblesActual().isEmpty()) {
            root.addView(infoText("Sincronizando inventario de consumibles..."))
            sincronizarCatalogo {
                if (!isFinishing && !isDestroyed) {
                    showConsumiblesForm(pItem, pCant, pSol, pLab, pCat, pRef, catalogoRefrescado = true)
                }
            }
            return
        }

        if (catalogoConsumiblesActual().isEmpty()) {
            root.addView(infoText("Sincronizando inventario de consumibles..."))
            sincronizarCatalogo {
                if (!isFinishing && !isDestroyed) {
                    if ((catalogoCargado["Consumibles"] ?: mapOf()).isNotEmpty()) {
                        showConsumiblesForm(pItem, pCant, pSol, pLab, pCat, pRef, catalogoRefrescado = true)
                    } else {
                        Toast.makeText(this, "No se pudo cargar el inventario de consumibles.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        val catalogoFormulario = catalogoConsumiblesActual()
            .mapValues { (_, itemsMap) ->
                itemsMap.mapValues { (_, refs) ->
                    refs.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                }
            }
        val catalogoFormularioHash = catalogoFormulario.hashCode()

        fun getCatalogo() = catalogoFormulario

        fun catalogoFormularioComoMapAny(): Map<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return catalogoFormulario as Map<String, Any>
        }

        fun verificarCambioCatalogoConsumibles(origen: String) {
            if (catalogoConsumiblesActual().hashCode() != catalogoFormularioHash) {
                android.util.Log.d("BugConsumibles", "catalogo cambio mientras formulario activo origen=$origen")
            }
        }

        val catSpinner = spinner(root, "Categoría *", getCatalogo().keys.filter { it.isNotBlank() }.sortedBy { normalizarBusqueda(it) })
        val itemSpinner = spinner(root, "Ítem *", listOf("Selecciona categoría"))
        val refSpinner = spinner(root, "Marca y Referencia *", listOf("Selecciona ítem"))
        
        val stockLabel = TextView(this).apply {
            text = "Selecciona un producto para ver stock"
            textSize = 13f
            setTextColor(verdeOscuro)
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(stockLabel)

        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe o elige el código interno", scan = false)
        var codigoInternoSeleccionado = ""
        var documentoExistenciaSeleccionado = ""
        var stockDisponible = 0.0
        var unidadStockSeleccionada = "Unidad"
        var stockVerificado = false
        var actualizandoSpinnerConsumibles = false
        var categoriaSeleccionada = pCat.ifBlank { consumiblesDraftCategoria }
        var itemSeleccionado = pItem.ifBlank { consumiblesDraftItem }
        var referenciaSeleccionada = pRef.ifBlank { consumiblesDraftReferencia }

        fun esPlaceholderConsumibles(valor: String): Boolean {
            val normalizado = normalizarBusqueda(valor)
            return valor.isBlank() ||
                normalizado.startsWith("selecciona") ||
                normalizado.startsWith("no-hay-items")
        }

        fun crearAdapterConsumibles(valores: List<String>): ArrayAdapter<String> {
            return ArrayAdapter(this@showConsumiblesForm, android.R.layout.simple_spinner_item, valores).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        fun seleccionarValorSiExiste(spinner: Spinner, valores: List<String>, valor: String): Boolean {
            if (valor.isBlank()) return false
            val pos = valores.indexOfFirst { it == valor || referenciasInventarioCoinciden(valor, it) }
            if (pos < 0) return false
            spinner.setSelection(pos, false)
            return true
        }

        fun itemsConsumibles(categoria: String, origen: String): List<String> {
            val catKey = claveCatalogoPorTexto(getCatalogo().keys, categoria)
            val itemsMap = getCatalogo()[catKey] ?: mapOf()
            val items = itemsMap.keys
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { normalizarBusqueda(it) }
            android.util.Log.d("BugConsumibles", "categoria seleccionada=$categoria items=${items.size} origen=$origen")
            return items.ifEmpty { listOf("No hay ítems disponibles para esta categoría") }
        }

        fun referenciasConsumibles(categoria: String, item: String, origen: String): List<String> {
            val catKey = claveCatalogoPorTexto(getCatalogo().keys, categoria)
            val itemsMap = getCatalogo()[catKey] ?: mapOf()
            val itemKey = claveCatalogoPorTexto(itemsMap.keys, item)
            val refs = (itemsMap[itemKey] ?: listOf())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            android.util.Log.d("BugConsumibles", "item seleccionado=$item refs=${refs.size} origen=$origen")
            return refs.ifEmpty { listOf("N/A") }
        }

        fun actualizarReferenciasConsumibles(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            val item = itemSeleccionado.ifBlank { itemSpinner.selectedItem?.toString().orEmpty() }
            if (esPlaceholderConsumibles(item)) {
                actualizandoSpinnerConsumibles = true
                refSpinner.tag = "SINCRO"
                refSpinner.adapter = crearAdapterConsumibles(listOf("N/A"))
                refSpinner.setSelection(0, false)
                referenciaSeleccionada = "N/A"
                actualizandoSpinnerConsumibles = false
                refSpinner.postDelayed({ if (refSpinner.tag == "SINCRO") refSpinner.tag = null }, 100)
                android.util.Log.d("BugConsumibles", "referencias omitidas por item placeholder origen=$origen")
                return
            }

            val refs = referenciasConsumibles(categoria, item, origen)
            actualizandoSpinnerConsumibles = true
            refSpinner.tag = "SINCRO"
            refSpinner.adapter = crearAdapterConsumibles(refs)
            val preservada = preservar && seleccionarValorSiExiste(refSpinner, refs, referenciaSeleccionada)
            if (!preservada) {
                refSpinner.setSelection(0, false)
                referenciaSeleccionada = refs.firstOrNull().orEmpty()
            } else {
                referenciaSeleccionada = refSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerConsumibles = false
            refSpinner.postDelayed({ if (refSpinner.tag == "SINCRO") refSpinner.tag = null }, 100)
            android.util.Log.d("BugConsumibles", "referencia preservada=$preservada valor=$referenciaSeleccionada origen=$origen")
        }

        fun actualizarItemsConsumibles(origen: String, preservar: Boolean = true) {
            val categoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            val items = itemsConsumibles(categoria, origen)
            actualizandoSpinnerConsumibles = true
            itemSpinner.tag = "SINCRO"
            itemSpinner.adapter = crearAdapterConsumibles(items)
            val preservado = preservar && seleccionarValorSiExiste(itemSpinner, items, itemSeleccionado)
            if (!preservado) {
                itemSpinner.setSelection(0, false)
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
                referenciaSeleccionada = ""
            } else {
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
            }
            actualizandoSpinnerConsumibles = false
            itemSpinner.postDelayed({ if (itemSpinner.tag == "SINCRO") itemSpinner.tag = null }, 100)
            android.util.Log.d("BugConsumibles", "item preservado=$preservado valor=$itemSeleccionado origen=$origen")
            actualizarReferenciasConsumibles(origen, preservar = preservado)
        }

        fun actualizarInfoStock() {
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""
            if (!esPlaceholderConsumibles(itemVal) && !esPlaceholderConsumibles(refVal)) {

                stockLabel.text = "Consultando stock..."
                stockLabel.setTextColor(verdeOscuro)
                stockVerificado = false

                val codigoValido = if (codigoInterno.text.toString().isNotBlank()) codigoInternoSeleccionado else ""
                val consultaItem = codigoValido.ifBlank { itemVal }
                
                consultarStockExistencia(consultaItem, refVal, { cant, unidadStock, productoVisible ->
                    if (codigoValido.isNotBlank() && !productoVisible.lowercase().contains(itemVal.lowercase())) {
                        consultarStockExistencia(itemVal, refVal, { c2, u2, _ ->
                            stockDisponible = c2
                            unidadStockSeleccionada = u2.ifBlank { "Unidad" }
                            stockVerificado = true
                            stockLabel.text = "Stock actual: $c2 $u2"
                            stockLabel.setTextColor(if (c2 <= 0) Color.RED else verdeOscuro)
                        }, {
                            stockDisponible = 0.0
                            unidadStockSeleccionada = "Unidad"
                            stockVerificado = false
                            stockLabel.text = "Stock no disponible"
                            stockLabel.setTextColor(Color.RED)
                        })
                        return@consultarStockExistencia
                    }
                    
                    stockDisponible = cant
                    unidadStockSeleccionada = unidadStock.ifBlank { "Unidad" }
                    stockVerificado = true
                    stockLabel.text = "Stock actual: $cant $unidadStock"
                    stockLabel.setTextColor(if (cant <= 0) Color.RED else verdeOscuro)
                }, {
                    stockLabel.text = "Stock no disponible (offline)"
                    stockDisponible = 0.0
                    unidadStockSeleccionada = "Unidad"
                    stockVerificado = false
                })
            } else {
                stockDisponible = 0.0
                unidadStockSeleccionada = "Unidad"
                stockVerificado = false
                stockLabel.text = "Selecciona un producto para ver stock"
                stockLabel.setTextColor(verdeOscuro)
            }
        }
        
        setupCodigoInternoSalida(root, codigoInterno, "Consumibles") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            documentoExistenciaSeleccionado = producto.documentoId
            stockDisponible = producto.cantidad
            unidadStockSeleccionada = producto.unidad.ifBlank { "Unidad" }
            stockVerificado = true
            seleccionarProductoDesdeExistencia(producto, catSpinner, itemSpinner, refSpinner, catalogoFormularioComoMapAny()) {
                val stockTxt = if (producto.unidad.isBlank()) "${producto.cantidad}" else "${producto.cantidad} ${producto.unidad}"
                stockLabel.text = "Stock actual: $stockTxt"
                stockLabel.setTextColor(if (producto.cantidad <= 0.0) Color.RED else verdeOscuro)
            }
        }

        setupSearchBar(root, "Consumibles") { _, c, i, r ->
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, catalogoFormularioComoMapAny(), c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    if (producto != null) {
                        documentoExistenciaSeleccionado = producto.documentoId
                        stockDisponible = producto.cantidad
                        unidadStockSeleccionada = producto.unidad.ifBlank { "Unidad" }
                        stockVerificado = true
                        val stockTxt = if (producto.unidad.isBlank()) "${producto.cantidad}" else "${producto.cantidad} ${producto.unidad}"
                        stockLabel.text = "Stock actual: $stockTxt\n${producto.item} · ${producto.referenciaCatalogo}"
                        stockLabel.setTextColor(if (producto.cantidad <= 0.0) Color.RED else verdeOscuro)
                    } else {
                        documentoExistenciaSeleccionado = ""
                        actualizarInfoStock()
                    }
                }
            }
        }

        if (pItem.isNotBlank() || pCat.isNotBlank()) {
            val producto = if (pCat.isNotBlank()) {
                 // Si venimos de un registro rápido, ya tenemos los datos exactos
                 ProductoCatalogo("Consumibles", pCat, pItem, pRef)
            } else {
                 resolverProductoCatalogo("Consumibles", pItem)
            }

            producto?.let { p ->
                seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, catalogoFormularioComoMapAny(), p.categoria, p.item, p.referencia) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", p.item, p.referencia) { encontrado ->
                        codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                        if (encontrado != null) {
                            documentoExistenciaSeleccionado = encontrado.documentoId
                            stockDisponible = encontrado.cantidad
                            unidadStockSeleccionada = encontrado.unidad.ifBlank { "Unidad" }
                            stockVerificado = true
                            val stockTxt = if (encontrado.unidad.isBlank()) "${encontrado.cantidad}" else "${encontrado.cantidad} ${encontrado.unidad}"
                            stockLabel.text = "Stock actual: $stockTxt\n${encontrado.item} · ${encontrado.referenciaCatalogo}"
                            stockLabel.setTextColor(if (encontrado.cantidad <= 0.0) Color.RED else verdeOscuro)
                        } else {
                            documentoExistenciaSeleccionado = ""
                            actualizarInfoStock()
                        }
                    }
                }
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerConsumibles || catSpinner.tag == "SINCRO") {
                    android.util.Log.d("BugConsumibles", "seleccion inicial categoria ignorada")
                    return
                }
                verificarCambioCatalogoConsumibles("categoria")
                categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty()
                consumiblesDraftCategoria = categoriaSeleccionada
                val itemActual = itemSpinner.selectedItem?.toString().orEmpty()
                val refActual = refSpinner.selectedItem?.toString().orEmpty()
                itemSeleccionado = if (!esPlaceholderConsumibles(itemActual)) itemActual else ""
                referenciaSeleccionada = if (!esPlaceholderConsumibles(refActual)) refActual else ""
                consumiblesDraftItem = itemSeleccionado
                consumiblesDraftReferencia = referenciaSeleccionada
                actualizarItemsConsumibles("categoria", preservar = true)
                actualizarInfoStock()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerConsumibles || itemSpinner.tag == "SINCRO") {
                    android.util.Log.d("BugConsumibles", "seleccion inicial item ignorada")
                    return
                }
                verificarCambioCatalogoConsumibles("item")
                itemSeleccionado = itemSpinner.selectedItem?.toString().orEmpty()
                consumiblesDraftItem = if (!esPlaceholderConsumibles(itemSeleccionado)) itemSeleccionado else ""
                if (esPlaceholderConsumibles(itemSeleccionado)) {
                    referenciaSeleccionada = ""
                    consumiblesDraftReferencia = ""
                    actualizarReferenciasConsumibles("item-placeholder", preservar = false)
                    actualizarInfoStock()
                    return
                }
                val refActual = refSpinner.selectedItem?.toString().orEmpty()
                referenciaSeleccionada = if (!esPlaceholderConsumibles(refActual)) refActual else referenciaSeleccionada
                consumiblesDraftReferencia = referenciaSeleccionada
                actualizarReferenciasConsumibles("item", preservar = true)
                
                // Si el usuario cambia manualmente el item, borramos el codigo previo
                if (itemSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    codigoInternoSeleccionado = ""
                    documentoExistenciaSeleccionado = ""
                    codigoInterno.setText("", false)
                }
                actualizarInfoStock()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }
        
        refSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (actualizandoSpinnerConsumibles || refSpinner.tag == "SINCRO") {
                    android.util.Log.d("BugConsumibles", "seleccion inicial referencia ignorada")
                    return
                }
                verificarCambioCatalogoConsumibles("referencia")
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                referenciaSeleccionada = refVal
                consumiblesDraftReferencia = if (!esPlaceholderConsumibles(refVal)) refVal else ""
                android.util.Log.d("BugConsumibles", "referencia seleccionada=$referenciaSeleccionada")
                if (!esPlaceholderConsumibles(itemVal) && !esPlaceholderConsumibles(refVal)) {
                    
                    // Solo autocompletar si NO es una carga automatica
                    if (refSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
        autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", itemVal, refVal) { producto ->
            codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
            documentoExistenciaSeleccionado = producto?.documentoId.orEmpty()
            actualizarInfoStock()
        }
                    } else {
                        actualizarInfoStock()
                    }
                } else {
                    actualizarInfoStock()
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        categoriaSeleccionada = catSpinner.selectedItem?.toString().orEmpty().ifBlank { categoriaSeleccionada }
        val itemActualInicio = itemSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderConsumibles(itemActualInicio)) itemSeleccionado = itemActualInicio
        val refActualInicio = refSpinner.selectedItem?.toString().orEmpty()
        if (!esPlaceholderConsumibles(refActualInicio)) referenciaSeleccionada = refActualInicio
        actualizarItemsConsumibles("inicio", preservar = true)

        val cantidad = field(root, "Cantidad *", "Ej: 2", number = true)
        cantidad.setText(pCant.ifBlank { consumiblesDraftCantidad })
        val unidad = spinner(root, "Unidad", listOf("Unidad", "Kg", "Litro", "Metro", "Caja", "Paquete", "Galón"))
        val solicitante = field(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol.ifBlank { consumiblesDraftSolicitante })
        val labor = field(root, "Labor / destino *", "Ej: limpieza bodega, vivero, mantenimiento")
        labor.setText(pLab.ifBlank { consumiblesDraftLabor })
        val observaciones = field(root, "Observaciones", "Opcional")
        observaciones.setText(consumiblesDraftObservaciones)
        var urlEvidencia = consumiblesDraftUrlEvidencia
        var uriLocalEvidencia = consumiblesDraftUriEvidencia

        fun guardarDraftConsumibles() {
            consumiblesDraftCategoria = categoriaSeleccionada.ifBlank { catSpinner.selectedItem?.toString().orEmpty() }
            consumiblesDraftItem = itemSeleccionado.ifBlank { itemSpinner.selectedItem?.toString().orEmpty() }
                .takeUnless { esPlaceholderConsumibles(it) }.orEmpty()
            consumiblesDraftReferencia = referenciaSeleccionada.ifBlank { refSpinner.selectedItem?.toString().orEmpty() }
                .takeUnless { esPlaceholderConsumibles(it) }.orEmpty()
            consumiblesDraftCantidad = cantidad.text.toString()
            consumiblesDraftSolicitante = solicitante.text.toString()
            consumiblesDraftLabor = labor.text.toString()
            consumiblesDraftObservaciones = observaciones.text.toString()
            consumiblesDraftUrlEvidencia = urlEvidencia
            consumiblesDraftUriEvidencia = uriLocalEvidencia
        }

        fun observarDraft(campo: android.widget.EditText, accion: (String) -> Unit) {
            campo.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    accion(s?.toString().orEmpty())
                }
            })
        }

        observarDraft(cantidad) { consumiblesDraftCantidad = it }
        observarDraft(solicitante) { consumiblesDraftSolicitante = it }
        observarDraft(labor) { consumiblesDraftLabor = it }
        observarDraft(observaciones) { consumiblesDraftObservaciones = it }

        root.addView(evidenceButton {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                consumiblesDraftUriEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                    consumiblesDraftUriEvidencia = ""
                    consumiblesDraftUrlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, "consumibles") { url ->
                        urlEvidencia = url
                        consumiblesDraftUrlEvidencia = url
                    }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                guardarDraftConsumibles()
                showDialogNuevoProducto("Consumibles") { c, i, r ->
                    showConsumiblesForm(
                        pItem = i,
                        pCant = consumiblesDraftCantidad,
                        pSol = consumiblesDraftSolicitante,
                        pLab = consumiblesDraftLabor,
                        pCat = c,
                        pRef = r,
                    )
                }
            },
            onEntrada = {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (!esPlaceholderConsumibles(itemVal)) {
                    guardarDraftConsumibles()
                    showDialogEntradaStock("Consumibles", itemVal, refVal) {
                        showConsumiblesForm(
                            pItem = itemVal,
                            pCant = consumiblesDraftCantidad,
                            pSol = consumiblesDraftSolicitante,
                            pLab = consumiblesDraftLabor,
                            pCat = categoriaSeleccionada,
                            pRef = refVal,
                        )
                    }
                } else {
                    Toast.makeText(this, "Selecciona un producto primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        fun seleccionarUnidadConsumible(valor: String) {
            val adapter = unidad.adapter ?: return
            for (idx in 0 until adapter.count) {
                if (adapter.getItem(idx)?.toString().equals(valor, ignoreCase = true)) {
                    unidad.setSelection(idx, false)
                    return
                }
            }
        }

        fun limpiarPreviewEvidenciaConsumibles() {
            var indice = 0
            while (indice < root.childCount) {
                if (root.getChildAt(indice).tag == "PREVIEW_FOTO") {
                    root.removeViewAt(indice)
                } else {
                    indice++
                }
            }
        }

        fun lineaDesdeFormulario(): ConsumibleEntregaLinea? {
            val codigoPreferido = codigoInternoValidadoParaSalida(codigoInterno, codigoInternoSeleccionado)
            val categoriaVal = catSpinner.selectedItem?.toString() ?: ""
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""

            if (esPlaceholderConsumibles(categoriaVal) || esPlaceholderConsumibles(itemVal) || esPlaceholderConsumibles(refVal)) {
                Toast.makeText(this, "Selecciona un producto valido", Toast.LENGTH_SHORT).show()
                return null
            }

            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            if (cantidad.text.toString().isBlank() || cantVal <= 0.0) {
                Toast.makeText(this, "La cantidad debe ser mayor a cero", Toast.LENGTH_SHORT).show()
                return null
            }

            if (!stockVerificado) {
                Toast.makeText(this, "Espera la consulta de stock antes de agregar", Toast.LENGTH_SHORT).show()
                return null
            }

            if (cantVal > stockDisponible) {
                Toast.makeText(this, "Stock insuficiente para $itemVal. Disponible: ${cantidadConsumibleLegible(stockDisponible)} $unidadStockSeleccionada", Toast.LENGTH_LONG).show()
                return null
            }

            return ConsumibleEntregaLinea(
                categoria = categoriaVal,
                item = itemVal,
                referencia = refVal,
                codigoInterno = codigoPreferido,
                documentoId = documentoExistenciaSeleccionado,
                cantidad = cantVal,
                unidad = unidad.selectedItem?.toString() ?: "Unidad",
                stockDisponible = stockDisponible,
                unidadStock = unidadStockSeleccionada,
            )
        }

        val productosAgregados = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }

        var botonAgregar: Button? = null
        var botonRegistrarTodos: Button? = null

        fun actualizarBotonesEntrega() {
            botonAgregar?.text = if (consumiblesEntregaEditandoId != null) "Actualizar producto" else "Agregar a la entrega"
            botonRegistrarTodos?.isEnabled = consumiblesEntregaLineas.isNotEmpty() && !consumiblesEntregaProcesando
            botonRegistrarTodos?.alpha = if (consumiblesEntregaLineas.isNotEmpty() && !consumiblesEntregaProcesando) 1f else 0.55f
        }

        fun aplicarLineaEnFormulario(linea: ConsumibleEntregaLinea) {
            consumiblesEntregaEditandoId = linea.id
            categoriaSeleccionada = linea.categoria
            itemSeleccionado = linea.item
            referenciaSeleccionada = linea.referencia
            consumiblesDraftCategoria = linea.categoria
            consumiblesDraftItem = linea.item
            consumiblesDraftReferencia = linea.referencia
            seleccionarProductoEnSpinners(
                catSpinner,
                itemSpinner,
                refSpinner,
                catalogoFormularioComoMapAny(),
                linea.categoria,
                linea.item,
                linea.referencia,
            ) {
                codigoInternoSeleccionado = linea.codigoInterno
                documentoExistenciaSeleccionado = linea.documentoId
                codigoInterno.setText(linea.codigoInterno, false)
                stockDisponible = linea.stockDisponible
                unidadStockSeleccionada = linea.unidadStock
                stockVerificado = true
                stockLabel.text = "Stock actual: ${cantidadConsumibleLegible(linea.stockDisponible)} ${linea.unidadStock}"
                stockLabel.setTextColor(if (linea.stockDisponible <= 0.0) Color.RED else verdeOscuro)
            }
            cantidad.setText(cantidadConsumibleLegible(linea.cantidad))
            seleccionarUnidadConsumible(linea.unidad)
            actualizarBotonesEntrega()
            Toast.makeText(this, "Edita la linea y actualiza el producto", Toast.LENGTH_SHORT).show()
        }

        fun renderProductosAgregados() {
            productosAgregados.removeAllViews()
            productosAgregados.addView(TextView(this).apply {
                text = "Productos agregados"
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(verdeOscuro)
                setPadding(0, dp(8), 0, dp(4))
            })

            if (consumiblesEntregaLineas.isEmpty()) {
                productosAgregados.addView(TextView(this).apply {
                    text = "Aun no hay productos en esta entrega."
                    textSize = 13f
                    setTextColor(gris)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
                })
                actualizarBotonesEntrega()
                return
            }

            consumiblesEntregaLineas.forEachIndexed { index, linea ->
                val cardLinea = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 12)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { setMargins(0, dp(4), 0, dp(8)) }
                }
                cardLinea.addView(TextView(this).apply {
                    text = "${index + 1}. ${linea.item}"
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(texto)
                })
                cardLinea.addView(TextView(this).apply {
                    text = listOf(
                        "Ref: ${linea.referencia}",
                        if (linea.codigoInterno.isNotBlank()) "Codigo: ${linea.codigoInterno}" else "",
                        "Cantidad: ${cantidadConsumibleLegible(linea.cantidad)} ${linea.unidad}",
                        "Stock: ${cantidadConsumibleLegible(linea.stockDisponible)} ${linea.unidadStock}",
                    ).filter { it.isNotBlank() }.joinToString("\n")
                    textSize = 12.5f
                    setTextColor(gris)
                    setPadding(0, dp(4), 0, dp(8))
                })
                val acciones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val editar = outlineButton("Editar") { aplicarLineaEnFormulario(linea) }
                val eliminar = outlineButton("Eliminar") {
                    consumiblesEntregaLineas.removeAll { it.id == linea.id }
                    if (consumiblesEntregaEditandoId == linea.id) consumiblesEntregaEditandoId = null
                    renderProductosAgregados()
                }.apply { setTextColor(Color.RED) }
                acciones.addView(editar, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(6), 0) })
                acciones.addView(eliminar, LinearLayout.LayoutParams(0, dp(44), 1f))
                cardLinea.addView(acciones)
                productosAgregados.addView(cardLinea)
            }
            actualizarBotonesEntrega()
        }

        fun limpiarSeleccionParaSiguienteProducto() {
            cantidad.setText("")
            codigoInternoSeleccionado = ""
            documentoExistenciaSeleccionado = ""
            codigoInterno.setText("", false)
            stockVerificado = false
            stockDisponible = 0.0
            unidadStockSeleccionada = "Unidad"
            stockLabel.text = "Selecciona un producto para ver stock"
            stockLabel.setTextColor(verdeOscuro)
            consumiblesDraftCantidad = ""
            guardarDraftConsumibles()
        }

        botonAgregar = primaryButton("Agregar a la entrega") {
            guardarDraftConsumibles()
            val nuevaLinea = lineaDesdeFormulario() ?: return@primaryButton
            val idEditando = consumiblesEntregaEditandoId
            val indiceEditando = idEditando?.let { id -> consumiblesEntregaLineas.indexOfFirst { it.id == id } } ?: -1
            val indiceDuplicado = consumiblesEntregaLineas.indexOfFirst { it.clave() == nuevaLinea.clave() && it.id != idEditando }
            val cantidadTotal = nuevaLinea.cantidad + if (indiceDuplicado >= 0) consumiblesEntregaLineas[indiceDuplicado].cantidad else 0.0

            if (cantidadTotal > nuevaLinea.stockDisponible) {
                Toast.makeText(this, "Stock insuficiente para la cantidad acumulada de ${nuevaLinea.item}", Toast.LENGTH_LONG).show()
                return@primaryButton
            }

            when {
                indiceEditando >= 0 && indiceDuplicado >= 0 -> {
                    val acumulada = consumiblesEntregaLineas[indiceDuplicado].copy(
                        cantidad = cantidadTotal,
                        unidad = nuevaLinea.unidad,
                        stockDisponible = nuevaLinea.stockDisponible,
                        unidadStock = nuevaLinea.unidadStock,
                    )
                    consumiblesEntregaLineas[indiceDuplicado] = acumulada
                    consumiblesEntregaLineas.removeAt(indiceEditando)
                }
                indiceEditando >= 0 -> {
                    consumiblesEntregaLineas[indiceEditando] = nuevaLinea.copy(id = consumiblesEntregaLineas[indiceEditando].id)
                }
                indiceDuplicado >= 0 -> {
                    consumiblesEntregaLineas[indiceDuplicado] = consumiblesEntregaLineas[indiceDuplicado].copy(
                        cantidad = cantidadTotal,
                        unidad = nuevaLinea.unidad,
                        stockDisponible = nuevaLinea.stockDisponible,
                        unidadStock = nuevaLinea.unidadStock,
                    )
                }
                else -> consumiblesEntregaLineas.add(nuevaLinea)
            }

            consumiblesEntregaEditandoId = null
            renderProductosAgregados()
            limpiarSeleccionParaSiguienteProducto()
            Toast.makeText(this, "Producto agregado a la entrega", Toast.LENGTH_SHORT).show()
        }
        botonAgregar?.let { root.addView(it) }
        root.addView(productosAgregados)

        botonRegistrarTodos = primaryButton("Registrar todos") { view ->
            if (consumiblesEntregaProcesando) return@primaryButton
            guardarDraftConsumibles()
            if (consumiblesEntregaLineas.isEmpty()) {
                Toast.makeText(this, "Agrega al menos un producto", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            if (!required(solicitante, labor)) return@primaryButton

            fun finalizarBloqueo() {
                consumiblesEntregaProcesando = false
                (view as? Button)?.text = "Registrar todos"
                actualizarBotonesEntrega()
            }

            fun registrarConEvidencia(fotoFinal: String) {
                val lineas = consumiblesEntregaLineas.toList()
                registrarSalidaMultipleConsumibles(
                    lineas = lineas,
                    solicitante = solicitante.text.toString(),
                    labor = labor.text.toString(),
                    observaciones = observaciones.text.toString(),
                    fotoUrl = fotoFinal,
                    onSuccess = {
                        saved("Entrega registrada con ${lineas.size} producto(s)")
                        consumiblesEntregaLineas.clear()
                        consumiblesEntregaEditandoId = null
                        consumiblesDraftCantidad = ""
                        consumiblesDraftObservaciones = ""
                        consumiblesDraftUrlEvidencia = ""
                        consumiblesDraftUriEvidencia = ""
                        urlEvidencia = ""
                        uriLocalEvidencia = ""
                        cantidad.setText("")
                        observaciones.setText("")
                        limpiarPreviewEvidenciaConsumibles()
                        renderProductosAgregados()
                        finalizarBloqueo()
                    },
                    onFailure = { e ->
                        Toast.makeText(this, e.localizedMessage ?: "No se pudo registrar la entrega", Toast.LENGTH_LONG).show()
                        finalizarBloqueo()
                    },
                )
            }

            consumiblesEntregaProcesando = true
            (view as? Button)?.apply {
                isEnabled = false
                alpha = 0.55f
                text = "Registrando..."
            }

            val evidenciaLocal = uriLocalEvidencia.ifBlank {
                urlEvidencia.takeIf { evidenciaEsUriLocal(it) }.orEmpty()
            }
            if (urlEvidencia.isNotBlank() && !evidenciaEsUriLocal(urlEvidencia)) {
                registrarConEvidencia(urlEvidencia)
            } else if (evidenciaLocal.isNotBlank()) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(this, "Conecta a internet para subir la evidencia y registrar todo", Toast.LENGTH_LONG).show()
                    finalizarBloqueo()
                } else {
                    subirEvidenciaCloud(evidenciaLocal, "consumibles") { url ->
                        if (url.isBlank()) {
                            Toast.makeText(this, "No se pudo subir la evidencia", Toast.LENGTH_LONG).show()
                            finalizarBloqueo()
                        } else {
                            urlEvidencia = url
                            consumiblesDraftUrlEvidencia = url
                            registrarConEvidencia(url)
                        }
                    }
                }
            } else {
                registrarConEvidencia("")
            }
        }
        botonRegistrarTodos?.let { root.addView(it) }
        renderProductosAgregados()
    }

private fun MainActivity.resolverDocumentoSalidaConsumible(
    linea: ConsumibleEntregaLinea,
    onResult: (DocumentSnapshot) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    fun entregarSiValido(doc: DocumentSnapshot?): Boolean {
        if (doc != null && doc.exists() && doc.getBoolean("activo") != false && moduloCoincide(doc, "Consumibles")) {
            onResult(doc)
            return true
        }
        return false
    }

    fun fallbackPorBusqueda() {
        buscarExistenciaPorProducto("Consumibles", linea.item, linea.referencia, { producto ->
            if (producto == null) {
                buscarDocumentoExistencia(
                    linea.codigoInterno.ifBlank { linea.item },
                    "Consumibles",
                    { doc ->
                        if (!entregarSiValido(doc)) {
                            onFailure(IllegalStateException("No se encontro inventario para ${linea.item} / ${linea.referencia}"))
                        }
                    },
                    onFailure,
                )
            } else {
                firestore.collection("existencias").document(producto.documentoId).get()
                    .addOnSuccessListener { doc ->
                        if (!entregarSiValido(doc)) {
                            onFailure(IllegalStateException("No se encontro inventario activo para ${linea.item} / ${linea.referencia}"))
                        }
                    }
                    .addOnFailureListener(onFailure)
            }
        }, onFailure)
    }

    val docId = linea.documentoId.ifBlank { normalizarCodigoInternoSinActividad(linea.codigoInterno) }
    if (docId.isBlank()) {
        fallbackPorBusqueda()
        return
    }

    firestore.collection("existencias").document(docId).get()
        .addOnSuccessListener { doc ->
            if (!entregarSiValido(doc)) fallbackPorBusqueda()
        }
        .addOnFailureListener { fallbackPorBusqueda() }
}

private fun MainActivity.resolverDocumentosSalidaConsumibles(
    lineas: List<ConsumibleEntregaLinea>,
    indice: Int = 0,
    acumuladas: MutableList<ConsumibleDocumentoResuelto> = mutableListOf(),
    onResult: (List<ConsumibleDocumentoResuelto>) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    if (indice >= lineas.size) {
        onResult(acumuladas)
        return
    }

    resolverDocumentoSalidaConsumible(
        linea = lineas[indice],
        onResult = { doc ->
            acumuladas.add(ConsumibleDocumentoResuelto(lineas[indice], doc))
            resolverDocumentosSalidaConsumibles(lineas, indice + 1, acumuladas, onResult, onFailure)
        },
        onFailure = onFailure,
    )
}

private fun MainActivity.registrarSalidaMultipleConsumibles(
    lineas: List<ConsumibleEntregaLinea>,
    solicitante: String,
    labor: String,
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
        onFailure?.invoke(IllegalStateException("Sin conexion no se puede validar stock y registrar todo o nada"))
        return
    }

    resolverDocumentosSalidaConsumibles(
        lineas = lineas,
        onResult = { resueltos ->
            val fechaEntrega = now()
            val uid = auth.currentUser?.uid ?: ""
            val entregaId = "CONSUMIBLES-${System.currentTimeMillis()}"

            firestore.runTransaction { transaction ->
                val refsPorDoc = resueltos.associate { it.doc.id to firestore.collection("existencias").document(it.doc.id) }
                val snapshotsPorDoc = mutableMapOf<String, DocumentSnapshot>()
                val stockInicialPorDoc = mutableMapOf<String, Double>()
                val totalSolicitadoPorDoc = resueltos
                    .groupBy { it.doc.id }
                    .mapValues { (_, docs) -> docs.sumOf { it.linea.cantidad } }

                refsPorDoc.forEach { (docId, refDoc) ->
                    val snapshot = transaction.get(refDoc)
                    if (!snapshot.exists() || snapshot.getBoolean("activo") == false || !moduloCoincide(snapshot, "Consumibles")) {
                        throw IllegalStateException("Inventario no disponible para ${docId}")
                    }
                    val stockActual = numeroDocumento(snapshot, "cantidad", "stock_actual")
                    val solicitado = totalSolicitadoPorDoc[docId] ?: 0.0
                    if (solicitado <= 0.0) {
                        throw IllegalStateException("Cantidad invalida en la entrega")
                    }
                    if (solicitado > stockActual) {
                        val lineaError = resueltos.firstOrNull { it.doc.id == docId }?.linea
                        val nombre = lineaError?.let { "${it.item} / ${it.referencia}" } ?: docId
                        throw IllegalStateException("Stock insuficiente en $nombre. Disponible: ${cantidadConsumibleLegible(stockActual)}, solicitado: ${cantidadConsumibleLegible(solicitado)}")
                    }
                    snapshotsPorDoc[docId] = snapshot
                    stockInicialPorDoc[docId] = stockActual
                }

                val stockRestantePorDoc = stockInicialPorDoc.toMutableMap()
                resueltos.forEachIndexed { index, resuelto ->
                    val linea = resuelto.linea
                    val docId = resuelto.doc.id
                    val snapshot = snapshotsPorDoc[docId] ?: throw IllegalStateException("Inventario no disponible para ${linea.item}")
                    val refDoc = refsPorDoc[docId] ?: throw IllegalStateException("Inventario no disponible para ${linea.item}")
                    val stockAnterior = stockRestantePorDoc[docId] ?: 0.0
                    val stockNuevo = stockAnterior - linea.cantidad
                    val codigoVisible = codigoOriginalExistencia(snapshot).ifBlank { codigoExistencia(snapshot) }
                    val ubicacion = ubicacionExistencia(snapshot)
                    val nombreCompleto = "${linea.item} (${linea.referencia})"

                    transaction.set(refDoc, mapOf(
                        "cantidad" to stockNuevo,
                        "stock_actual" to stockNuevo,
                        "ultima_fecha" to fechaEntrega,
                        "ultimo_solicitante" to solicitante,
                    ), SetOptions.merge())

                    val movData = mutableMapOf<String, Any?>(
                        "fecha" to fechaEntrega,
                        "modulo" to "Consumibles",
                        "tipoMovimiento" to "Salida",
                        "item" to nombreCompleto,
                        "item_base" to linea.item,
                        "categoria" to linea.categoria,
                        "referencia" to linea.referencia,
                        "cantidad" to cantidadConsumibleLegible(linea.cantidad),
                        "unidad" to linea.unidad,
                        "solicitante" to solicitante,
                        "labor" to labor,
                        "observaciones" to observaciones,
                        "usuario" to uid,
                        "fotoUrl" to fotoUrl,
                        "codigo_interno" to codigoVisible,
                        "codigo_original" to codigoVisible,
                        "documento_id" to docId,
                        "producto_id" to docId,
                        "ubicacion" to ubicacion,
                        "stock_actualizado" to true,
                        "stock_anterior" to stockAnterior,
                        "stock_nuevo" to stockNuevo,
                        "entrega_id" to entregaId,
                        "linea_entrega" to (index + 1),
                        "total_lineas" to resueltos.size,
                    )

                    transaction.set(firestore.collection("movimientos").document(), movData)
                    stockRestantePorDoc[docId] = stockNuevo
                }
                resueltos.size
            }.addOnSuccessListener {
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                onFailure?.invoke(e)
            }
        },
        onFailure = { e -> onFailure?.invoke(e) },
    )
}

internal fun MainActivity.showCombustibleForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        val tipos = tiposCombustible()
        var tipoSeleccionado = tipos.firstOrNull { it.equals(pCat, ignoreCase = true) } ?: "Gasolina"
        currentScreenRenderer = {
            showCombustibleForm(
                pItem = pItem,
                pCant = pCant,
                pSol = pSol,
                pCat = tipoSeleccionado,
                pRef = pRef,
            )
        }
        val root = baseScreen("Salida de combustible", "Registra gasolina, ACPM o urea por maquinaria y solicitante.")

        val botonesTipo = linkedMapOf<String, Button>()
        var alCambiarTipo: () -> Unit = {}

        fun pintarBotonesTipo() {
            botonesTipo.forEach { (nombre, boton) ->
                val seleccionado = nombre == tipoSeleccionado
                boton.text = if (seleccionado) "$nombre ✓" else nombre
                boton.setTextColor(if (seleccionado) Color.WHITE else texto)
                boton.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (seleccionado) verdeOscuro else Color.WHITE)
                    setStroke(dp(1), if (seleccionado) verdeOscuro else Color.rgb(190, 196, 192))
                    cornerRadius = dp(12).toFloat()
                }
            }
        }

        fun seleccionarTipo(nuevoTipo: String) {
            if (nuevoTipo == tipoSeleccionado) return
            tipoSeleccionado = nuevoTipo
            pintarBotonesTipo()
            alCambiarTipo()
        }

        root.addView(TextView(this).apply {
            text = "Tipo *"
            textSize = 14f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        })

        val selectorTipo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = tipos.size.toFloat()
            setPadding(0, 0, 0, dp(8))
        }
        tipos.forEachIndexed { index, nombre ->
            val boton = secondaryButton(nombre) { seleccionarTipo(nombre) }.apply {
                textSize = 13f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
            }
            botonesTipo[nombre] = boton
            selectorTipo.addView(
                boton,
                LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    setMargins(
                        if (index == 0) 0 else dp(4),
                        0,
                        if (index == tipos.lastIndex) 0 else dp(4),
                        0,
                    )
                },
            )
        }
        root.addView(selectorTipo)
        pintarBotonesTipo()

        val stockResumen = stockInfoCard("Stock actual: consultando...")
        root.addView(stockResumen)
        val stockPorTipo = mutableMapOf<String, Double>()

        fun pintarStockCombustible() {
            stockResumen.renderStockSummary(
                title = "Stock actual",
                lines = tiposCombustible().map { combustible ->
                    combustible to "${cantidadGalonesLegible(stockPorTipo[combustible] ?: 0.0)} gal"
                },
                highlightKey = tipoSeleccionado,
            )
        }

        fun actualizarStockCombustible() {
            stockResumen.text = "Stock actual: consultando..."
            stockResumen.setTextColor(gris)
            cargarResumenStockCombustible({ stock ->
                stockPorTipo.clear()
                stockPorTipo.putAll(stock)
                pintarStockCombustible()
            }, {
                stockResumen.text = "Stock actual: no se pudo consultar"
                stockResumen.setTextColor(Color.rgb(180, 40, 40))
            })
        }
        actualizarStockCombustible()

        val cantidad = field(root, "Cantidad *", "Ej: 5", number = true)
        cantidad.setText(pCant)
        val unidad = spinner(root, "Unidad", listOf("Galones"))

        val contenedorHorometro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val horometro = field(contenedorHorometro, "Horómetro", "Opcional", number = true)
        root.addView(
            contenedorHorometro,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        fun actualizarVisibilidadHorometro() {
            val mostrarHorometro = tipoSeleccionado == "ACPM"
            contenedorHorometro.visibility = if (mostrarHorometro) View.VISIBLE else View.GONE
            if (!mostrarHorometro) horometro.setText("")
        }

        alCambiarTipo = {
            actualizarVisibilidadHorometro()
            if (stockPorTipo.isNotEmpty()) pintarStockCombustible()
        }
        actualizarVisibilidadHorometro()
        
        // Sugerencias para maquinaria/equipo
        val equiposExistentes = listOf("Tractor", "Guadaña", "Bomba Espalda", "Camioneta", "Motosierra", "Planta Eléctrica")
        val maquinaria = autoField(root, "Maquinaria / equipo *", "Ej: tractor, guadaña, bomba", suggestions = equiposExistentes)
        maquinaria.setText(pItem)
        val solicitante = field(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol)
        val labor = field(root, "Labor / frente", "Ej: mantenimiento, cosecha, riego")
        val observaciones = field(root, "Observaciones", "Opcional")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(evidenceButton {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, "combustible") { url -> urlEvidencia = url }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                showDialogNuevoCombustible(tipoSeleccionado) {
                    actualizarStockCombustible()
                    showCombustibleForm(
                        pItem = pItem,
                        pCant = pCant,
                        pSol = pSol,
                        pCat = tipoSeleccionado,
                    )
                }
            },
            onEntrada = {
                if (tipoSeleccionado.isNotBlank()) {
                    showDialogEntradaStock("Combustible", tipoSeleccionado, "N/A") {
                        actualizarStockCombustible()
                        showCombustibleForm(
                            pItem = pItem,
                            pCant = pCant,
                            pSol = pSol,
                            pCat = tipoSeleccionado,
                        )
                    }
                } else {
                    Toast.makeText(this, "Selecciona el tipo de combustible primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Guardar salida") {
            if (!required(cantidad, maquinaria, solicitante)) return@primaryButton
            
            if (tipoSeleccionado.isBlank()) {
                Toast.makeText(this, "Selecciona el tipo de combustible", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val uniVal = unidad.selectedItem.toString()

            val mov = Movimiento(
                fecha = now(),
                modulo = "Combustible",
                tipoMovimiento = "Salida",
                item = tipoSeleccionado,
                cantidad = cantidad.text.toString(),
                unidad = uniVal,
                solicitante = solicitante.text.toString(),
                labor = labor.text.toString(),
                maquinaria = maquinaria.text.toString(),
                horometro = if (tipoSeleccionado == "ACPM") horometro.text.toString() else "",
                observaciones = observaciones.text.toString()
            )

            registrarSalidaCombustibleFirestore(
                mov = mov,
                cantidadNumerica = cantVal,
                fotoUrl = urlEvidencia.ifBlank { uriLocalEvidencia },
                onSuccess = {
                    saved("Combustible guardado con evidencia")
                    actualizarStockCombustible()
                    // Permanecer en la misma pantalla limpiando campos
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this, "No se pudo guardar combustible: ${e.localizedMessage ?: "error desconocido"}", Toast.LENGTH_LONG).show()
                }
            )
        })
    }

private fun MainActivity.showQuimicoFormLegacy(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        currentScreenRenderer = { showQuimicoForm(pItem, pCant, pSol, pCat, pRef) }
        val root = baseScreen("Salida de químico", "Registra agroquímicos, fertilizantes, fungicidas o productos similares.")

        fun getCatalogo() = catalogoCargado["Químico"] ?: mapOf()

        val catSpinner = spinner(root, "Categoría *", getCatalogo().keys.toList())
        val itemSpinner = spinner(root, "Ítem / Tipo *", listOf("Selecciona categoría"))
        val refSpinner = spinner(root, "Producto y Ref *", listOf("Selecciona ítem"))
        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe o elige el código interno", scan = false)
        var codigoInternoSeleccionado = ""
        val unidadSpinner = spinner(root, "Unidad", listOf("Gramos", "ML", "Unidad", "Litros", "Kg"))

        setupCodigoInternoSalida(root, codigoInterno, "Químico") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            seleccionarProductoDesdeExistencia(producto, catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>) {
                val sugerida = producto.unidad.ifBlank { sugerirUnidadGeneral("Químico", producto.referenciaCatalogo) }
                val adapter = unidadSpinner.adapter as? ArrayAdapter<String>
                val pos = adapter?.getPosition(sugerida) ?: -1
                if (pos >= 0) unidadSpinner.setSelection(pos)
            }
        }

        setupSearchBar(root, "Químico") { _, c, i, r ->
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, c, i, r) {
                val sugerida = sugerirUnidadGeneral("Químico", r)
                val adapter = unidadSpinner.adapter as ArrayAdapter<String>
                val pos = adapter.getPosition(sugerida)
                if (pos >= 0) unidadSpinner.setSelection(pos)
                autocompletarCodigoDesdeProducto(codigoInterno, "Químico", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    val unidadEncontrada = producto?.unidad.orEmpty()
                    if (unidadEncontrada.isNotBlank()) {
                        val posUnidad = adapter.getPosition(unidadEncontrada)
                        if (posUnidad >= 0) unidadSpinner.setSelection(posUnidad)
                    }
                }
            }
        }

        if (pItem.isNotBlank() || pCat.isNotBlank()) {
            val producto = if (pCat.isNotBlank()) {
                ProductoCatalogo("Químico", pCat, pItem, pRef)
            } else {
                resolverProductoCatalogo("Químico", pItem)
            }

            producto?.let { p ->
                seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, p.categoria, p.item, p.referencia) {
                    val sugerida = sugerirUnidadGeneral("Químico", p.referencia)
                    val adapter = unidadSpinner.adapter as ArrayAdapter<String>
                    val pos = adapter.getPosition(sugerida)
                    if (pos >= 0) unidadSpinner.setSelection(pos)
                    autocompletarCodigoDesdeProducto(codigoInterno, "Químico", p.item, p.referencia) { encontrado ->
                        codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                        val unidadEncontrada = encontrado?.unidad.orEmpty()
                        if (unidadEncontrada.isNotBlank()) {
                            val posUnidad = adapter.getPosition(unidadEncontrada)
                            if (posUnidad >= 0) unidadSpinner.setSelection(posUnidad)
                        }
                    }
                }
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val items = (getCatalogo()[cat] as? Map<String, Any>)?.keys?.toList() ?: listOf()
                val adapter = ArrayAdapter(this@showQuimicoFormLegacy, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                itemSpinner.adapter = adapter
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString() ?: ""
                val itemKey = itemSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val refs = (getCatalogo()[cat] as? Map<String, Any>)?.get(itemKey) as? List<String> ?: listOf<Any>()
                val adapter = ArrayAdapter(this@showQuimicoFormLegacy, android.R.layout.simple_spinner_item, refs)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                refSpinner.adapter = adapter
                
                // Limpieza de código previo si es manual
                if (itemSpinner.tag == null && codigoInterno.tag == null) {
                    codigoInternoSeleccionado = ""
                    codigoInterno.setText("", false)
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        refSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val prod = refSpinner.selectedItem?.toString() ?: ""
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                
                val sugerida = sugerirUnidadGeneral("Químico", prod)
                @Suppress("UNCHECKED_CAST")
                val adapter = unidadSpinner.adapter as? ArrayAdapter<String>
                val pos = adapter?.getPosition(sugerida) ?: -1
                if (pos >= 0) unidadSpinner.setSelection(pos)

                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona") &&
                    prod.isNotBlank() && !prod.startsWith("Selecciona")) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "Químico", itemVal, prod) { producto ->
                        codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                        val unidadEncontrada = producto?.unidad.orEmpty()
                        if (unidadEncontrada.isNotBlank()) {
                            val posUnidad = adapter?.getPosition(unidadEncontrada) ?: -1
                            if (posUnidad >= 0) unidadSpinner.setSelection(posUnidad)
                        }
                    }
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        val cantidad = field(root, "Cantidad *", "Ej: 1.5", number = true)
        cantidad.setText(pCant)
        
        // Sugerencias para solicitante
        val solicitante = autoField(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol)
        val labor = field(root, "Labor / aplicación *", "Ej: vivero, fertilización, control sanitario")
        val observaciones = field(root, "Observaciones", "Lote, dosis, área o recomendación")
        var urlEvidencia = ""
        var uriLocalEvidencia = ""

        root.addView(evidenceButton {
            capturarEvidencia { uri ->
                uriLocalEvidencia = uri
                mostrarPrevisualizacionEvidencia(root, uri) {
                    uriLocalEvidencia = ""
                    urlEvidencia = ""
                }
                if (isNetworkAvailable()) {
                    subirEvidenciaCloud(uri, "quimico") { url -> urlEvidencia = url }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                showDialogNuevoProducto("Químico") { c, i, r ->
                    showQuimicoForm(pItem = i, pCant = pCant, pSol = pSol, pCat = c, pRef = r)
                }
            },
            onEntrada = {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona")) {
                    showDialogEntradaStock("Químico", itemVal, refVal) { showQuimicoForm(pItem, pCant, pSol) }
                } else {
                    Toast.makeText(this, "Selecciona un químico primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Guardar salida") {
            if (!required(cantidad, solicitante, labor)) return@primaryButton
            val codigoPreferido = codigoInternoValidadoParaSalida(codigoInterno, codigoInternoSeleccionado)
            
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""
            
            if (itemVal.isBlank() || itemVal.startsWith("Selecciona") || 
                refVal.isBlank() || refVal.startsWith("Selecciona")) {
                Toast.makeText(this, "Selecciona un producto válido", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val nombreCompleto = "$itemVal - $refVal"
            val cantVal = cantidad.text.toString().toDoubleOrNull() ?: 0.0
            val uniVal = unidadSpinner.selectedItem.toString()

            val mov = Movimiento(
                fecha = now(),
                modulo = "Químico",
                tipoMovimiento = "Salida",
                item = nombreCompleto,
                referencia = refVal,
                cantidad = cantidad.text.toString(),
                unidad = uniVal,
                solicitante = solicitante.text.toString(),
                labor = labor.text.toString(),
                observaciones = observaciones.text.toString()
            )

            registrarSalidaCloudPrimero(
                mov = mov,
                cantidadNumerica = cantVal,
                codigoInternoPreferido = codigoPreferido,
                itemBase = itemVal,
                fotoUrl = urlEvidencia.ifBlank { uriLocalEvidencia },
                onSuccess = {
                    saved("Químico guardado con evidencia")
                    // Permanecer en la misma pantalla limpiando campos
                    cantidad.setText("")
                    observaciones.setText("")
                    urlEvidencia = ""
                    uriLocalEvidencia = ""
                    for (i in 0 until root.childCount) {
                        if (root.getChildAt(i).tag == "PREVIEW_FOTO") {
                            root.removeViewAt(i)
                            break
                        }
                    }
                }
            )
        })
    }
