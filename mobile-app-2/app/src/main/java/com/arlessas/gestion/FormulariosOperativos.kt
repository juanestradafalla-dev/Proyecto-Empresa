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

internal fun MainActivity.showDotacionForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        currentScreenRenderer = { showDotacionForm(pItem, pCant, pSol, pCat, pRef) }
        val root = baseScreen("Entrega de Dotación", "Control de vestuario y calzado para el personal.")

        val categoriasDotacionPermitidas = listOf(
            "Parte Superior",
            "Parte Inferior",
            "Conjunto",
            "Calzado"
        )

        fun getCatalogo(): Map<String, MutableMap<String, MutableList<String>>> {
            val base = catalogoCargado["Dotación"] ?: emptyMap()
            return categoriasDotacionPermitidas.associateWith { categoria ->
                base[categoria]?.mapValues { (_, refs) ->
                    refs.map { referenciaDotacionPresentable(it) }.distinct().toMutableList()
                }?.toMutableMap() ?: mutableMapOf()
            }
        }

        val parteSpinner = spinner(root, "Parte del cuerpo *", getCatalogo().keys.toList())
        val itemSpinner = spinner(root, "Prenda *", listOf("Selecciona parte"))
        val tallaSpinner = spinner(root, "Talla *", listOf("Selecciona prenda"))
        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe el código si existe", scan = false)
        var codigoInternoSeleccionado = ""

        val stockDisponible = stockInfoCard("Disponible: selecciona una prenda para consultar stock")
        root.addView(stockDisponible)

        fun cantidadLegible(valor: Double): String {
            return if (valor % 1.0 == 0.0) valor.toInt().toString()
            else String.format(Locale.getDefault(), "%.2f", valor)
        }

        fun opcionDotacionValida(valor: String): Boolean {
            return valor.isNotBlank() && !valor.startsWith("Selecciona") && !valor.startsWith("Sin ")
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
            seleccionarProductoDesdeExistencia(producto, parteSpinner, itemSpinner, tallaSpinner, getCatalogo() as Map<String, Any>)
            mostrarStockDotacion(producto)
        }

        setupSearchBar(root, "Dotación") { _, c, i, r ->
            seleccionarProductoEnSpinners(parteSpinner, itemSpinner, tallaSpinner, getCatalogo() as Map<String, Any>, c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    mostrarStockDotacion(producto)
                }
            }
        }

        if (pCat.isNotBlank() && pItem.isNotBlank()) {
            seleccionarProductoEnSpinners(parteSpinner, itemSpinner, tallaSpinner, getCatalogo() as Map<String, Any>, pCat, pItem, pRef) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", pItem, pRef) { encontrado ->
                    codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                    mostrarStockDotacion(encontrado)
                }
            }
        }

        parteSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val parte = parteSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val itemsMap = (getCatalogo()[parte] as? Map<String, Any>) ?: mapOf()
                val items = itemsMap.keys.toList().ifEmpty { listOf("Sin prendas registradas") }
                val adapter = ArrayAdapter(this@showDotacionForm, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                itemSpinner.adapter = adapter

                val itemInicial = items.firstOrNull().orEmpty()
                val tallas = (itemsMap[itemInicial] as? List<String>)
                    ?.map { referenciaDotacionPresentable(it) }
                    ?.distinct()
                    ?.ifEmpty { listOf("Sin tallas registradas") }
                    ?: listOf("Sin tallas registradas")
                val tallaAdapter = ArrayAdapter(this@showDotacionForm, android.R.layout.simple_spinner_item, tallas)
                tallaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                tallaSpinner.adapter = tallaAdapter
                actualizarInfoStockDotacion()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val parte = parteSpinner.selectedItem?.toString() ?: ""
                val itemKey = itemSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val tallas = ((getCatalogo()[parte] as? Map<String, Any>)?.get(itemKey) as? List<String>)
                    ?.map { referenciaDotacionPresentable(it) }
                    ?.distinct()
                    ?.ifEmpty { listOf("Sin tallas registradas") }
                    ?: listOf("Sin tallas registradas")
                val adapter = ArrayAdapter(this@showDotacionForm, android.R.layout.simple_spinner_item, tallas)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                tallaSpinner.adapter = adapter
                
                if (pRef.isNotBlank()) {
                    val pos = adapter.getPosition(pRef)
                    if (pos >= 0) tallaSpinner.setSelection(pos)
                }

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
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val tallaVal = tallaSpinner.selectedItem?.toString() ?: ""
                if (opcionDotacionValida(itemVal) && opcionDotacionValida(tallaVal)) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "Dotación", itemVal, tallaVal) { producto ->
                        codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    }
                }
                actualizarInfoStockDotacion()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

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

private fun MainActivity.showQuimicoInventarioForm(moduloOperativo: String, pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
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

        fun getCatalogo() = catalogoCargado[moduloOperativo] ?: mapOf()
        fun esValido(valor: String) = valor.isNotBlank() && !valor.startsWith("Selecciona")

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

        fun setUnidad(unidadSpinner: Spinner, unidad: String) {
            val adapter = unidadSpinner.adapter as? ArrayAdapter<String> ?: return
            val pos = adapter.getPosition(unidad.ifBlank { "UNIDAD" })
            if (pos >= 0) unidadSpinner.setSelection(pos)
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
                            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, getCatalogo() as Map<String, Any>, canonico.categoria, canonico.subcategoria, canonico.item) {
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
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, getCatalogo() as Map<String, Any>, producto.categoria, subcategoria, producto.item) {
                setUnidad(unidadSpinner, producto.unidad)
                cargarUbicaciones(codigoOriginalSeleccionado, producto.item, producto.ubicacion)
            }
        }

        setupSearchBar(root, moduloOperativo) { _, c, sub, item ->
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, getCatalogo() as Map<String, Any>, c, sub, item) {
                aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, c, sub, item))
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val subcategorias = ((getCatalogo()[cat] as? Map<String, Any>)?.keys?.toList() ?: listOf())
                    .filterNot { esEntradaCatalogoSulcamagVieja(it) }
                val adapter = ArrayAdapter(this@showQuimicoInventarioForm, android.R.layout.simple_spinner_item, subcategorias)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                subcatSpinner.adapter = adapter
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        subcatSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                val sub = subcatSpinner.selectedItem?.toString().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val items = (getCatalogo()[cat] as? Map<String, Any>)?.get(sub) as? List<String> ?: listOf()
                val adapter = ArrayAdapter(this@showQuimicoInventarioForm, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                itemSpinner.adapter = adapter
                if (subcatSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    aplicarCanonico(null)
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString().orEmpty()
                val sub = subcatSpinner.selectedItem?.toString().orEmpty()
                val item = itemSpinner.selectedItem?.toString().orEmpty()
                if (esValido(cat) && esValido(sub) && esValido(item)) {
                    aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, cat, sub, item))
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        if (pCat.isNotBlank() && pRef.isNotBlank()) {
            seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, getCatalogo() as Map<String, Any>, pCat, pItem, pRef) {
                aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, pCat, pItem, pRef))
            }
        } else if (pItem.isNotBlank()) {
            resolverProductoCatalogo(moduloOperativo, pItem)?.let { p ->
                seleccionarProductoEnSpinners(catSpinner, subcatSpinner, itemSpinner, getCatalogo() as Map<String, Any>, p.categoria, p.item, p.referencia) {
                    aplicarCanonico(QuimicosCanonicos.buscar(moduloOperativo, p.categoria, p.item, p.referencia))
                }
            }
        }

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

        fun getCatalogo(): Map<String, MutableMap<String, MutableList<String>>> {
            val base = catalogoCargado["EPP"] ?: return emptyMap()
            return categoriasEppPermitidas.mapNotNull { categoria ->
                base[categoria]?.takeIf { it.isNotEmpty() }?.let { categoria to it }
            }.toMap()
        }

        val catSpinner = spinner(root, "Categoría *", getCatalogo().keys.toList())
        val itemSpinner = spinner(root, "Item *", listOf("Selecciona categoria"))
        val refSpinner = spinner(root, "Talla / referencia *", listOf("Selecciona item"))
        val codigoInterno = codigoInternoField(root, "Código interno", "Escribe o elige el código interno", scan = false)
        var codigoInternoSeleccionado = ""

        val stockDisponible = stockInfoCard("Disponible: selecciona un item para consultar stock")
        root.addView(stockDisponible)
        var stockConsultaActual = 0

        fun cantidadLegible(valor: Double): String {
            return if (valor % 1.0 == 0.0) valor.toInt().toString()
            else String.format(Locale.getDefault(), "%.2f", valor)
        }

        fun mostrarStock(producto: ExistenciaProducto?) {
            if (producto == null) {
                stockDisponible.text = "Disponible: sin stock registrado para esta seleccion"
                stockDisponible.setTextColor(Color.rgb(160, 80, 0))
                return
            }
            val unidad = producto.unidad.ifBlank { "Unidad" }
            val referencia = producto.referenciaCatalogo.ifBlank { producto.referencia }
            val detalle = listOf(producto.codigoInterno, producto.item, referencia).filter { it.isNotBlank() }.joinToString(" - ")
            stockDisponible.text = "Disponible: ${cantidadLegible(producto.cantidad)} $unidad\n$detalle"
            stockDisponible.setTextColor(if (producto.cantidad > 0.0) verdeOscuro else Color.rgb(180, 40, 40))
        }

        fun actualizarStockEpp(codigoPreferido: String = "") {
            val consulta = ++stockConsultaActual
            val codigo = normalizarCodigoInterno(codigoPreferido.ifBlank { codigoInterno.text?.toString().orEmpty() })
            val itemVal = itemSpinner.selectedItem?.toString()?.takeUnless { it.startsWith("Selecciona") }.orEmpty()
            val refVal = refSpinner.selectedItem?.toString()?.takeUnless { it.startsWith("Selecciona") }.orEmpty()
            stockDisponible.text = "Disponible: consultando..."
            stockDisponible.setTextColor(gris)

            if (codigo.isNotBlank()) {
                buscarExistenciaPorCodigoInterno(codigo, "EPP", { producto ->
                    if (consulta == stockConsultaActual) mostrarStock(producto)
                }, {
                    if (consulta == stockConsultaActual) {
                        stockDisponible.text = "Disponible: no se pudo consultar Firestore"
                        stockDisponible.setTextColor(Color.rgb(180, 40, 40))
                    }
                })
                return
            }

            if (itemVal.isBlank() || refVal.isBlank()) {
                stockDisponible.text = "Disponible: selecciona un item para consultar stock"
                stockDisponible.setTextColor(gris)
                return
            }

            buscarExistenciaPorProducto("EPP", itemVal, refVal, { producto ->
                if (consulta == stockConsultaActual) mostrarStock(producto)
            }, {
                if (consulta == stockConsultaActual) {
                    stockDisponible.text = "Disponible: no se pudo consultar Firestore"
                    stockDisponible.setTextColor(Color.rgb(180, 40, 40))
                }
            })
        }

        setupCodigoInternoSalida(root, codigoInterno, "EPP") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            seleccionarProductoDesdeExistencia(producto, catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>)
            mostrarStock(producto)
        }

        setupSearchBar(root, "EPP") { _, c, i, r ->
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "EPP", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    mostrarStock(producto)
                }
            }
        }

        if (pCat.isNotBlank() && pItem.isNotBlank()) {
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, pCat, pItem, pRef) {
                autocompletarCodigoDesdeProducto(codigoInterno, "EPP", pItem, pRef) { encontrado ->
                    codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                    mostrarStock(encontrado)
                }
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (catSpinner.tag == "SINCRO") return
                val cat = catSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val items = (getCatalogo()[cat] as? Map<String, Any>)?.keys?.toList() ?: listOf()
                val adapter = ArrayAdapter(this@showEPPForm, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                itemSpinner.adapter = adapter
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (itemSpinner.tag == "SINCRO") return
                val cat = catSpinner.selectedItem?.toString() ?: ""
                val itemKey = itemSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val refs = (getCatalogo()[cat] as? Map<String, Any>)?.get(itemKey) as? List<String> ?: listOf()
                val adapter = ArrayAdapter(this@showEPPForm, android.R.layout.simple_spinner_item, refs)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                refSpinner.adapter = adapter
                
                // Limpieza de código previo si es manual
                if (itemSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    codigoInternoSeleccionado = ""
                    codigoInterno.setText("", false)
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        refSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (refSpinner.tag == "SINCRO") return
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona") &&
                    refVal.isNotBlank() && !refVal.startsWith("Selecciona")) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "EPP", itemVal, refVal) { producto ->
                        codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                        mostrarStock(producto)
                    }
                } else {
                    actualizarStockEpp()
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

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
                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona")) {
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
            
            if (itemVal.isBlank() || itemVal.startsWith("Selecciona") || 
                refVal.isBlank() || refVal.startsWith("Selecciona")) {
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
        currentScreenRenderer = { showConsumiblesForm(pItem, pCant, pSol, pLab, pCat, pRef, catalogoRefrescado = true) }
        val root = baseScreen("Salida de consumibles", "Registra materiales de uso diario: repuestos, ferretería, aseo y más.")
        
        fun getCatalogo() = catalogoCargado["Consumibles"] ?: mapOf()

        if (!catalogoRefrescado) {
            root.addView(infoText("Sincronizando inventario de consumibles..."))
            sincronizarCatalogo {
                if (!isFinishing && !isDestroyed) {
                    showConsumiblesForm(pItem, pCant, pSol, pLab, pCat, pRef, catalogoRefrescado = true)
                }
            }
            return
        }

        if (getCatalogo().isEmpty()) {
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
        var stockDisponible = 0.0

        fun actualizarInfoStock() {
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""
            if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona") &&
                refVal.isNotBlank() && !refVal.startsWith("Selecciona")) {

                stockLabel.text = "Consultando stock..."
                stockLabel.setTextColor(verdeOscuro)

                val codigoValido = if (codigoInterno.text.toString().isNotBlank()) codigoInternoSeleccionado else ""
                val consultaItem = codigoValido.ifBlank { itemVal }
                
                consultarStockExistencia(consultaItem, refVal, { cant, unidadStock, productoVisible ->
                    if (codigoValido.isNotBlank() && !productoVisible.lowercase().contains(itemVal.lowercase())) {
                        consultarStockExistencia(itemVal, refVal, { c2, u2, _ ->
                            stockDisponible = c2
                            stockLabel.text = "Stock actual: $c2 $u2"
                            stockLabel.setTextColor(if (c2 <= 0) Color.RED else verdeOscuro)
                        }, {})
                        return@consultarStockExistencia
                    }
                    
                    stockDisponible = cant
                    stockLabel.text = "Stock actual: $cant $unidadStock"
                    stockLabel.setTextColor(if (cant <= 0) Color.RED else verdeOscuro)
                }, {
                    stockLabel.text = "Stock no disponible (offline)"
                    stockDisponible = 999999.0
                })
            }
        }
        
        setupCodigoInternoSalida(root, codigoInterno, "Consumibles") { producto ->
            codigoInternoSeleccionado = producto.codigoInterno
            stockDisponible = producto.cantidad
            seleccionarProductoDesdeExistencia(producto, catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>) {
                val stockTxt = if (producto.unidad.isBlank()) "${producto.cantidad}" else "${producto.cantidad} ${producto.unidad}"
                stockLabel.text = "Stock actual: $stockTxt"
                stockLabel.setTextColor(if (producto.cantidad <= 0.0) Color.RED else verdeOscuro)
            }
        }

        setupSearchBar(root, "Consumibles") { _, c, i, r ->
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, c, i, r) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", i, r) { producto ->
                    codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
                    if (producto != null) {
                        stockDisponible = producto.cantidad
                        val stockTxt = if (producto.unidad.isBlank()) "${producto.cantidad}" else "${producto.cantidad} ${producto.unidad}"
                        stockLabel.text = "Stock actual: $stockTxt\n${producto.item} · ${producto.referenciaCatalogo}"
                        stockLabel.setTextColor(if (producto.cantidad <= 0.0) Color.RED else verdeOscuro)
                    } else {
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
                seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, p.categoria, p.item, p.referencia) {
                    autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", p.item, p.referencia) { encontrado ->
                        codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                        if (encontrado != null) {
                            stockDisponible = encontrado.cantidad
                            val stockTxt = if (encontrado.unidad.isBlank()) "${encontrado.cantidad}" else "${encontrado.cantidad} ${encontrado.unidad}"
                            stockLabel.text = "Stock actual: $stockTxt\n${encontrado.item} · ${encontrado.referenciaCatalogo}"
                            stockLabel.setTextColor(if (encontrado.cantidad <= 0.0) Color.RED else verdeOscuro)
                        } else {
                            actualizarInfoStock()
                        }
                    }
                }
            }
        }

        catSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val cat = catSpinner.selectedItem?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val catKey = claveCatalogoPorTexto(getCatalogo().keys, cat)
                val itemsMap = (getCatalogo()[catKey] as? Map<String, Any>) ?: mapOf()
                val items = itemsMap.keys
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { normalizarBusqueda(it) }
                    .ifEmpty { listOf("Selecciona categoría") }
                val adapter = ArrayAdapter(this@showConsumiblesForm, android.R.layout.simple_spinner_item, items)
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
                val catKey = claveCatalogoPorTexto(getCatalogo().keys, cat)
                val itemsMap = (getCatalogo()[catKey] as? Map<String, Any>) ?: mapOf()
                val itemCatalogoKey = claveCatalogoPorTexto(itemsMap.keys, itemKey)
                val refs = ((itemsMap[itemCatalogoKey] as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: listOf())
                    .filter { it.isNotBlank() }
                    .distinct()
                    .ifEmpty { listOf("Selecciona ítem") }
                val adapter = ArrayAdapter(this@showConsumiblesForm, android.R.layout.simple_spinner_item, refs)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                refSpinner.adapter = adapter
                
                // Si el usuario cambia manualmente el ítem, borramos el código previo
                if (itemSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
                    codigoInternoSeleccionado = ""
                    codigoInterno.setText("", false)
                }
                actualizarInfoStock()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }
        
        refSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona") &&
                    refVal.isNotBlank() && !refVal.startsWith("Selecciona")) {
                    
                    // Solo autocompletar si NO es una carga automática
                    if (refSpinner.tag != "SINCRO" && codigoInterno.tag != "SINCRO") {
        autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", itemVal, refVal) { producto ->
            codigoInternoSeleccionado = producto?.codigoInterno.orEmpty()
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

        if (pCat.isNotBlank() && pItem.isNotBlank()) {
            seleccionarProductoEnSpinners(catSpinner, itemSpinner, refSpinner, getCatalogo() as Map<String, Any>, pCat, pItem, pRef) {
                autocompletarCodigoDesdeProducto(codigoInterno, "Consumibles", pItem, pRef) { encontrado ->
                    codigoInternoSeleccionado = encontrado?.codigoInterno.orEmpty()
                    if (encontrado != null) {
                        stockDisponible = encontrado.cantidad
                        val stockTxt = if (encontrado.unidad.isBlank()) "${encontrado.cantidad}" else "${encontrado.cantidad} ${encontrado.unidad}"
                        stockLabel.text = "Stock actual: $stockTxt\n${encontrado.item} · ${encontrado.referenciaCatalogo}"
                        stockLabel.setTextColor(if (encontrado.cantidad <= 0.0) Color.RED else verdeOscuro)
                    } else {
                        actualizarInfoStock()
                    }
                }
            }
        }

        val cantidad = field(root, "Cantidad *", "Ej: 2", number = true)
        cantidad.setText(pCant)
        val unidad = spinner(root, "Unidad", listOf("Unidad", "Kg", "Litro", "Metro", "Caja", "Paquete", "Galón"))
        val solicitante = field(root, "Solicitante *", "Nombre de quien solicita")
        solicitante.setText(pSol)
        val labor = field(root, "Labor / destino *", "Ej: limpieza bodega, vivero, mantenimiento")
        labor.setText(pLab)
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
                    subirEvidenciaCloud(uri, "consumibles") { url -> urlEvidencia = url }
                }
            }
        })

        root.addView(gestionNuevoEntradaRow(
            onNuevo = {
                showDialogNuevoProducto("Consumibles") { c, i, r ->
                    showConsumiblesForm(pItem = i, pCant = pCant, pSol = pSol, pLab = pLab, pCat = c, pRef = r)
                }
            },
            onEntrada = {
                val itemVal = itemSpinner.selectedItem?.toString() ?: ""
                val refVal = refSpinner.selectedItem?.toString() ?: ""
                if (itemVal.isNotBlank() && !itemVal.startsWith("Selecciona")) {
                    showDialogEntradaStock("Consumibles", itemVal, refVal) { showConsumiblesForm(pItem, pCant, pSol, pLab) }
                } else {
                    Toast.makeText(this, "Selecciona un producto primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Guardar salida") {
            if (!required(cantidad, solicitante, labor)) return@primaryButton
            val codigoPreferido = codigoInternoValidadoParaSalida(codigoInterno, codigoInternoSeleccionado)
            
            val itemVal = itemSpinner.selectedItem?.toString() ?: ""
            val refVal = refSpinner.selectedItem?.toString() ?: ""
            
            if (itemVal.isBlank() || (itemVal.startsWith("Selecciona") || 
                refVal.isBlank() || refVal.startsWith("Selecciona"))) {
                Toast.makeText(this, "Selecciona un producto válido", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val cantVal = cantidad.text.toString().toDoubleOrNull() ?: 0.0
            
            // Log de seguridad
            if (cantVal > stockDisponible) {
                android.util.Log.d("ArlesGestión", "Salida permitida con stock insuficiente: Solicitado $cantVal / Disponible $stockDisponible")
            }

            val nombreCompleto = "$itemVal ($refVal)"
            val uniVal = unidad.selectedItem.toString()

            val mov = Movimiento(
                fecha = now(),
                modulo = "Consumibles",
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
                    saved("Salida guardada con stock trazable")
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

internal fun MainActivity.showCombustibleForm(pItem: String = "", pCant: String = "", pSol: String = "", pCat: String = "", pRef: String = "") {
        currentScreenRenderer = { showCombustibleForm(pItem, pCant, pSol, pCat, pRef) }
        val root = baseScreen("Salida de combustible", "Registra gasolina, ACPM o urea por maquinaria y solicitante.")
        val tipo = spinner(root, "Tipo *", listOf("Gasolina", "ACPM", "Urea"))

        val stockResumen = stockInfoCard("Stock actual: consultando...")
        root.addView(stockResumen)
        val stockPorTipo = mutableMapOf<String, Double>()

        fun pintarStockCombustible() {
            val tipoVal = tipo.selectedItem?.toString().orEmpty()
            stockResumen.renderStockSummary(
                title = "Stock actual",
                lines = tiposCombustible().map { tipo ->
                    tipo to "${cantidadGalonesLegible(stockPorTipo[tipo] ?: 0.0)} gal"
                },
                highlightKey = tipoVal,
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

        tipo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (stockPorTipo.isNotEmpty()) pintarStockCombustible()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        actualizarStockCombustible()

        val cantidad = field(root, "Cantidad *", "Ej: 5", number = true)
        cantidad.setText(pCant)
        val unidad = spinner(root, "Unidad", listOf("Galones"))
        val horometro = field(root, "Horómetro", "Opcional", number = true)
        
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
                val tipoVal = tipo.selectedItem?.toString() ?: ""
                showDialogNuevoCombustible(tipoVal) {
                    actualizarStockCombustible()
                    showCombustibleForm(pItem, pCant, pSol)
                }
            },
            onEntrada = {
                val tipoVal = tipo.selectedItem?.toString() ?: ""
                if (tipoVal.isNotBlank() && !tipoVal.startsWith("Selecciona")) {
                    showDialogEntradaStock("Combustible", tipoVal, "N/A") {
                        actualizarStockCombustible()
                        showCombustibleForm(pItem, pCant, pSol)
                    }
                } else {
                    Toast.makeText(this, "Selecciona el tipo de combustible primero", Toast.LENGTH_SHORT).show()
                }
            },
        ))

        root.addView(primaryButton("Guardar salida") {
            if (!required(cantidad, maquinaria, solicitante)) return@primaryButton
            
            val tipoVal = tipo.selectedItem?.toString() ?: ""
            if (tipoVal.isBlank()) {
                Toast.makeText(this, "Selecciona el tipo de combustible", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val cantVal = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val uniVal = unidad.selectedItem.toString()

            val mov = Movimiento(
                fecha = now(),
                modulo = "Combustible",
                tipoMovimiento = "Salida",
                item = tipoVal,
                cantidad = cantidad.text.toString(),
                unidad = uniVal,
                solicitante = solicitante.text.toString(),
                labor = labor.text.toString(),
                maquinaria = maquinaria.text.toString(),
                horometro = horometro.text.toString(),
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
