@file:android.annotation.SuppressLint("SetTextI18n")

package com.arlessas.gestion

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import java.util.UUID

private data class SalidaDefinitivaLinea(
    val id: Long = System.nanoTime(),
    val herramienta: Herramienta,
    val documentoId: String,
    val cantidad: Double,
    val stockDisponible: Double,
) {
    fun clave(): String = documentoId
}

private data class SalidaDefinitivaResultado(
    val linea: SalidaDefinitivaLinea,
    val stockAnterior: Double,
    val stockNuevo: Double,
    val cantidadOcupada: Double,
    val salidaDefinitivaId: String,
    val fecha: String,
    val responsableEntrega: String,
    val fotoUrl: String,
)

private fun Herramienta.esConsumibleBodegaRoja(): Boolean {
    return esConsumible && TallerCanonicos.esBodegaRojaTaller(subModulo)
}

internal fun MainActivity.confirmarCambioConsumibleBodega(herramienta: Herramienta) {
    if (!TallerCanonicos.esBodegaRojaTaller(herramienta.subModulo)) return
    val marcar = !herramienta.esConsumible
    cargarMovimientosPrestamosTaller { movimientos ->
        if (!pantallaActiva()) return@cargarMovimientosPrestamosTaller
        val prestamoActivo = prestamosActivosTaller(movimientos).any {
            it.herramienta.id == herramienta.id ||
                claveHerramientaCloud(it.herramienta) == claveHerramientaCloud(herramienta)
        }
        if (marcar && (herramienta.ocupados() > 0.0 || prestamoActivo)) {
            Toast.makeText(this, "Devuelve el producto antes de marcarlo como consumible", Toast.LENGTH_LONG).show()
            return@cargarMovimientosPrestamosTaller
        }
        AlertDialog.Builder(this)
            .setTitle(if (marcar) "Marcar como consumible" else "Quitar marca de consumible")
            .setMessage(
                if (marcar) {
                    "${herramienta.nombre} podrá utilizarse en Salida definitiva y dejará de aparecer en préstamos temporales."
                } else {
                    "${herramienta.nombre} dejará de aparecer en Salida definitiva."
                },
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                if (!isNetworkAvailable()) {
                    Toast.makeText(this, "Se requiere conexión para cambiar la clasificación", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val referencia = firestore.collection("herramientas").document(claveHerramientaCloud(herramienta))
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(referencia)
                    if (!snapshot.exists()) throw IllegalStateException("El producto ya no existe en la nube")
                    val nube = herramientaDesdeDocumentoFirestore(snapshot)
                        ?: throw IllegalStateException("No se pudo validar el producto")
                    if (!TallerCanonicos.esBodegaRojaTaller(nube.subModulo)) {
                        throw IllegalStateException("El producto ya no pertenece a Bodega Roja")
                    }
                    if (marcar && numeroDocumento(snapshot, "cantidad_ocupada", "ocupados") > 0.0) {
                        throw IllegalStateException("El producto tiene cantidades prestadas; devuélvelo primero")
                    }
                    transaction.set(
                        referencia,
                        mapOf(
                            "es_consumible" to marcar,
                            "ultima_actualizacion" to now(),
                            "actualizado_por_uid" to auth.currentUser?.uid.orEmpty(),
                        ),
                        SetOptions.merge(),
                    )
                    null
                }.addOnSuccessListener {
                    db.actualizarConsumibleHerramienta(herramienta.id, marcar)
                    marcarCacheTallerTrasCambio("clasificacion_consumible_bodega")
                    saved(if (marcar) "Producto marcado como consumible" else "Marca de consumible retirada")
                    showHerramientasRegistradas(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
                }.addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        error.localizedMessage ?: "No se pudo cambiar la clasificación",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .show()
    }
}

internal fun MainActivity.showSalidaDefinitivaBodegaRoja() {
    currentScreenRenderer = { showSalidaDefinitivaBodegaRoja() }
    baseScreen("Salida definitiva", "Sincronizando consumibles de Bodega Roja...")
    prepararInventarioTaller {
        if (!pantallaActiva()) return@prepararInventarioTaller
        renderSalidaDefinitivaBodegaRoja()
    }
}

private fun MainActivity.renderSalidaDefinitivaBodegaRoja() {
    val accent = tallerSubmoduloAccent(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
    val root = baseScreen(
        "Salida definitiva",
        "Bodega Roja · entrega múltiple sin devolución",
        backAction = { showTallerSubmoduloMenu(TallerCanonicos.SUBMODULO_BODEGA_ROJA) },
    )
    val lineas = mutableListOf<SalidaDefinitivaLinea>()
    var procesando = false
    var operacionId = 0L
    var uriEvidencia = ""
    var urlEvidencia = ""

    root.addView(
        moduleHeroBanner(
            R.drawable.ic_door_open,
            "Salida definitiva",
            "Consumibles de uso rápido · descuento permanente",
            accent,
        ),
    )

    var productos = herramientasTallerFiltradas(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
        .filter { it.esConsumibleBodegaRoja() && it.disponibles() > 0.0 }
        .sortedBy { normalizarBusqueda(it.nombre) }
        .toMutableList()

    lateinit var productoSpinner: Spinner
    lateinit var stockInfo: TextView
    lateinit var cantidad: EditText
    lateinit var solicitante: EditText
    lateinit var labor: EditText
    lateinit var area: EditText
    lateinit var observaciones: EditText
    lateinit var productosAgregados: LinearLayout
    var botonAgregar: Button? = null
    var botonRegistrar: Button? = null

    fun etiquetaProducto(h: Herramienta): String {
        return "${h.nombre} · ${h.codigo.ifBlank { "Sin código" }} · ${formatoCantidadTaller(h.disponibles())} ${h.unidad}"
    }

    fun productoSeleccionado(): Herramienta? {
        if (productos.isEmpty()) return null
        return productos.getOrNull(productoSpinner.selectedItemPosition)
    }

    fun mostrarStock() {
        val h = productoSeleccionado()
        if (h == null) {
            stockInfo.text = "No hay consumibles con stock disponible"
            return
        }
        stockInfo.renderStockSummary(
            h.nombre,
            listOf(
                "Código" to h.codigo.ifBlank { "-" },
                "Stock total" to formatoCantidadTaller(h.cantidadTotal),
                "Cantidad ocupada" to formatoCantidadTaller(h.ocupados()),
                "Cantidad disponible" to formatoCantidadTaller(h.disponibles()),
                "Unidad" to h.unidad.ifBlank { "UNIDAD" },
            ),
            highlightKey = "Cantidad disponible",
            positiveColor = accent,
            emptyColor = ArlesPalette.danger,
        )
    }

    fun actualizarProductos(seleccionarClave: String = "") {
        productos = herramientasTallerFiltradas(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
            .filter { it.esConsumibleBodegaRoja() && it.disponibles() > 0.0 }
            .sortedBy { normalizarBusqueda(it.nombre) }
            .toMutableList()
        productoSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            productos.map(::etiquetaProducto).ifEmpty { listOf("Sin consumibles disponibles") },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        productoSpinner.isEnabled = productos.isNotEmpty() && !procesando
        val index = productos.indexOfFirst { claveHerramientaCloud(it) == seleccionarClave }
        if (index >= 0) productoSpinner.setSelection(index, false)
        mostrarStock()
    }

    formSectionCard(root, "1", "Producto consumible", "Selecciona y agrega cada producto de la entrega", accent) { section ->
        productoSpinner = spinner(
            section,
            "Producto consumible *",
            productos.map(::etiquetaProducto).ifEmpty { listOf("Sin consumibles disponibles") },
        )
        productoSpinner.isEnabled = productos.isNotEmpty()
        stockInfo = infoText("Selecciona un producto para consultar stock")
        section.addView(stockInfo)
        cantidad = field(section, "Cantidad individual *", "Mayor que cero", number = true)
        cantidad.setText("1")
    }
    productoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (!procesando) mostrarStock()
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    formSectionCard(root, "2", "Datos comunes", "Se aplican a todos los productos agregados", accent) { section ->
        solicitante = autoField(section, "Solicitante / persona que recibe *", "Nombre completo")
        labor = field(section, "Uso, labor o motivo *", "Ej: corte, reparación, mantenimiento")
        area = field(section, "Área / destino *", "Ej: taller, lote, equipo")
        observaciones = field(section, "Observaciones", "Opcional")
    }

    formSectionCard(root, "3", "Evidencia", "Una evidencia común para toda la entrega", accent) { section ->
        section.addView(evidenceButton {
            if (procesando) return@evidenceButton
            capturarEvidencia { uri ->
                if (procesando) return@capturarEvidencia
                uriEvidencia = uri
                urlEvidencia = ""
                mostrarPrevisualizacionEvidencia(section, uri) {
                    if (procesando) return@mostrarPrevisualizacionEvidencia
                    uriEvidencia = ""
                    urlEvidencia = ""
                }
            }
        })
    }

    fun actualizarBotones() {
        botonAgregar?.apply {
            isEnabled = !procesando && productos.isNotEmpty()
            alpha = if (isEnabled) 1f else 0.55f
        }
        botonRegistrar?.apply {
            isEnabled = !procesando && lineas.isNotEmpty()
            alpha = if (isEnabled) 1f else 0.55f
        }
    }

    fun renderLineas() {
        productosAgregados.removeAllViews()
        productosAgregados.addView(TextView(this).apply {
            text = "Productos agregados"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, dp(8), 0, dp(4))
        })
        if (lineas.isEmpty()) {
            productosAgregados.addView(TextView(this).apply {
                text = "Aún no hay productos en esta salida definitiva."
                textSize = 13f
                setTextColor(gris)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 8)
            })
            actualizarBotones()
            return
        }
        lineas.forEachIndexed { index, linea ->
            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(4), 0, dp(8)) }
            }
            tarjeta.addView(TextView(this).apply {
                text = "${index + 1}. ${linea.herramienta.nombre}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(texto)
            })
            tarjeta.addView(TextView(this).apply {
                text = "Código: ${linea.herramienta.codigo}\n" +
                    "Stock disponible: ${formatoCantidadTaller(linea.stockDisponible)} ${linea.herramienta.unidad}\n" +
                    "Cantidad: ${formatoCantidadTaller(linea.cantidad)} ${linea.herramienta.unidad}"
                textSize = 12.5f
                setTextColor(gris)
                setPadding(0, dp(4), 0, dp(6))
            })
            tarjeta.addView(outlineButton("Eliminar") {
                if (procesando) return@outlineButton
                lineas.removeAll { it.id == linea.id }
                renderLineas()
            }.apply { setTextColor(ArlesPalette.danger) })
            productosAgregados.addView(tarjeta)
        }
        actualizarBotones()
    }

    val gestionAcciones = gestionNuevoEntradaRow(
        onNuevo = {
            if (procesando) return@gestionNuevoEntradaRow
            showDialogNuevoConsumibleBodega { nuevo ->
                actualizarProductos(claveHerramientaCloud(nuevo))
                actualizarBotones()
            }
        },
        onEntrada = {
            if (procesando) return@gestionNuevoEntradaRow
            showDialogNuevaEntradaConsumibleBodega { actualizado ->
                val clave = claveHerramientaCloud(actualizado)
                lineas.replaceAll { linea ->
                    if (linea.clave() == clave) linea.copy(stockDisponible = actualizado.disponibles()) else linea
                }
                actualizarProductos(clave)
                renderLineas()
            }
        },
        textoNuevo = "Añadir producto",
    )
    root.addView(gestionAcciones)

    botonAgregar = primaryButton("Agregar a la entrega") {
        if (procesando) return@primaryButton
        val h = productoSeleccionado()
        if (h == null || !h.esConsumibleBodegaRoja() || h.disponibles() <= 0.0) {
            Toast.makeText(this, "Selecciona un consumible con stock disponible", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        val valor = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: Double.NaN
        if (!valor.isFinite() || valor <= 0.0) {
            Toast.makeText(this, "La cantidad debe ser mayor que cero", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        val clave = claveHerramientaCloud(h)
        val index = lineas.indexOfFirst { it.clave() == clave }
        val acumulada = valor + if (index >= 0) lineas[index].cantidad else 0.0
        if (!acumulada.isFinite() || acumulada > h.disponibles()) {
            Toast.makeText(
                this,
                "Stock insuficiente para ${h.nombre}. Disponible: ${formatoCantidadTaller(h.disponibles())}",
                Toast.LENGTH_LONG,
            ).show()
            return@primaryButton
        }
        if (index >= 0) {
            lineas[index] = lineas[index].copy(cantidad = acumulada, stockDisponible = h.disponibles())
            Toast.makeText(this, "Cantidad consolidada en la línea existente", Toast.LENGTH_SHORT).show()
        } else {
            lineas += SalidaDefinitivaLinea(
                herramienta = h.copy(),
                documentoId = clave,
                cantidad = valor,
                stockDisponible = h.disponibles(),
            )
            Toast.makeText(this, "Producto agregado", Toast.LENGTH_SHORT).show()
        }
        cantidad.setText("1")
        renderLineas()
    }
    root.addView(botonAgregar)

    productosAgregados = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(productosAgregados)

    botonRegistrar = primaryButton("REGISTRAR SALIDA DEFINITIVA") {
        if (procesando || lineas.isEmpty()) return@primaryButton
        if (!required(solicitante, labor, area)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Se requiere conexión para esta operación irreversible", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        val confirmadas = lineas.map { it.copy(herramienta = it.herramienta.copy()) }.toList()
        val solicitanteConfirmado = solicitante.text.toString().trim()
        val laborConfirmada = labor.text.toString().trim()
        val areaConfirmada = area.text.toString().trim()
        val observacionesConfirmadas = observaciones.text.toString().trim()
        val uriConfirmada = uriEvidencia
        val urlConfirmada = urlEvidencia
        val resumen = buildString {
            confirmadas.forEach { linea ->
                append("• ${linea.herramienta.nombre}: ${formatoCantidadTaller(linea.cantidad)} ${linea.herramienta.unidad}\n")
                append("  Stock: ${formatoCantidadTaller(linea.stockDisponible)} → ${formatoCantidadTaller(linea.stockDisponible - linea.cantidad)}\n")
            }
            append("\nRecibe: $solicitanteConfirmado\n")
            append("Esta entrega es definitiva y no tendrá devolución.")
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmar salida definitiva")
            .setMessage(resumen)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar") { _, _ ->
                if (procesando) return@setPositiveButton
                procesando = true
                val token = System.nanoTime()
                operacionId = token
                val backAnterior = currentScreenBackAction
                val superficie = ((root.parent as? View)?.parent as? View) ?: root
                val bloqueo = BloqueoFormulario.aplicar(superficie)
                currentScreenBackAction = {
                    Toast.makeText(this, "La salida definitiva se está registrando", Toast.LENGTH_SHORT).show()
                }
                botonRegistrar?.text = "REGISTRANDO..."

                fun activa(): Boolean = procesando && operacionId == token
                fun desbloquear(error: String) {
                    if (!activa()) return
                    procesando = false
                    operacionId = 0L
                    bloqueo.restaurar()
                    currentScreenBackAction = backAnterior
                    botonRegistrar?.text = "REGISTRAR SALIDA DEFINITIVA"
                    actualizarBotones()
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
                fun registrar(foto: String) {
                    if (!activa()) return
                    registrarSalidaDefinitivaMultiple(
                        confirmadas,
                        solicitanteConfirmado,
                        laborConfirmada,
                        areaConfirmada,
                        observacionesConfirmadas,
                        foto,
                        onSuccess = { resultados ->
                            if (!activa()) return@registrarSalidaDefinitivaMultiple
                            aplicarSalidaDefinitivaLocal(resultados, solicitanteConfirmado, laborConfirmada, areaConfirmada, observacionesConfirmadas)
                            procesando = false
                            operacionId = 0L
                            lineas.clear()
                            uriEvidencia = ""
                            urlEvidencia = ""
                            saved("Salida definitiva registrada con ${resultados.size} producto(s)")
                            showTallerSubmoduloMenu(TallerCanonicos.SUBMODULO_BODEGA_ROJA)
                        },
                        onFailure = { error ->
                            desbloquear(error.localizedMessage ?: "No se pudo registrar la salida definitiva")
                        },
                    )
                }

                when {
                    urlConfirmada.isNotBlank() && !evidenciaEsUriLocal(urlConfirmada) -> registrar(urlConfirmada)
                    uriConfirmada.isNotBlank() -> subirEvidenciaCloud(uriConfirmada, "bodega_roja") { url ->
                        if (!activa()) return@subirEvidenciaCloud
                        if (url.isBlank()) desbloquear("No se pudo subir la evidencia") else {
                            urlEvidencia = url
                            registrar(url)
                        }
                    }
                    else -> registrar("")
                }
            }
            .show()
    }
    root.addView(botonRegistrar)
    renderLineas()
}

private fun MainActivity.showDialogNuevoConsumibleBodega(onDone: (Herramienta) -> Unit) {
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()
    root.addView(TextView(this).apply {
        text = "Añadir producto consumible"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(tallerSubmoduloAccent(TallerCanonicos.SUBMODULO_BODEGA_ROJA))
    })
    val nombre = field(root, "Producto *", "Nombre del consumible")
    val categoria = field(root, "Categoría *", "Ej: Discos, abrasivos, repuestos")
    val codigo = field(root, "Código principal *", "QR-### o SINQR-BR-###")
    val unidad = field(root, "Unidad *", "UNIDAD, CAJA, JUEGO")
    val stock = field(root, "Stock inicial *", "Puede ser cero", number = true)
    val observaciones = field(root, "Observaciones", "Opcional")
    lateinit var crear: Button
    crear = primaryButton("Crear producto") {
        if (!required(nombre, categoria, codigo, unidad, stock)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Se requiere conexión para crear el producto", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        val total = stock.text.toString().replace(",", ".").toDoubleOrNull() ?: Double.NaN
        if (!total.isFinite() || total < 0.0) {
            Toast.makeText(this, "El stock debe ser cero o mayor", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        val codigoValor = TallerCanonicos.normalizarCodigo(codigo.text.toString())
        val herramienta = Herramienta(
            fechaRegistro = now(),
            nombre = nombre.text.toString().trim(),
            referencia = categoria.text.toString().trim(),
            codigo = codigoValor,
            estado = if (total <= 0.0) "Agotado" else "Disponible",
            ubicacion = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
            observaciones = observaciones.text.toString().trim(),
            subModulo = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
            subcategoria = categoria.text.toString().trim(),
            tipo = "CONSUMIBLE",
            unidad = unidad.text.toString().trim().uppercase(Locale.getDefault()),
            cantidadTotal = total,
            cantidadOcupada = 0.0,
            codigoQr = TallerCanonicos.codigoQrDesdeCodigoPrincipal(codigoValor, ""),
            requiereAsignarQr = codigoValor.startsWith("SINQR", true),
            esConsumible = true,
        )
        val referencia = firestore.collection("herramientas").document(claveHerramientaCloud(herramienta))
        crear.isEnabled = false
        obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
            val fecha = now()
            firestore.runTransaction { transaction ->
                if (transaction.get(referencia).exists()) throw IllegalStateException("Ya existe un producto con ese código")
                transaction.set(referencia, dataHerramientaCloud(herramienta, 0L, usuario))
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to TallerCanonicos.MODULO,
                    "tipoMovimiento" to TallerCanonicos.TIPO_MOV_INGRESO_BODEGA,
                    "item" to herramienta.nombre,
                    "referencia" to herramienta.codigo,
                    "cantidad" to total,
                    "unidad" to herramienta.unidad,
                    "submodulo_taller" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                    "es_consumible" to true,
                    "responsable_entrega" to usuario,
                    "usuario_uid" to auth.currentUser?.uid.orEmpty(),
                ))
                null
            }.addOnSuccessListener {
                val id = db.insertarHerramienta(herramienta)
                val guardada = herramienta.copy(id = id)
                db.insertarMovimiento(Movimiento(
                    fecha = fecha,
                    modulo = TallerCanonicos.MODULO,
                    tipoMovimiento = TallerCanonicos.TIPO_MOV_INGRESO_BODEGA,
                    item = guardada.nombre,
                    referencia = guardada.codigo,
                    cantidad = total.toString(),
                    unidad = guardada.unidad,
                    maquinaria = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                ))
                marcarCacheTallerTrasCambio("nuevo_consumible_bodega")
                dialog.dismiss()
                onDone(guardada)
            }.addOnFailureListener { error ->
                crear.isEnabled = true
                Toast.makeText(this, error.localizedMessage ?: "No se pudo crear el producto", Toast.LENGTH_LONG).show()
            }
        }
    }
    root.addView(crear)
    dialog.setContentView(root)
    dialog.show()
}

private fun MainActivity.showDialogNuevaEntradaConsumibleBodega(onDone: (Herramienta) -> Unit) {
    val consumibles = db.obtenerHerramientas()
        .filter { it.esConsumibleBodegaRoja() }
        .sortedBy { normalizarBusqueda(it.nombre) }
    if (consumibles.isEmpty()) {
        Toast.makeText(this, "No hay consumibles creados en Bodega Roja", Toast.LENGTH_SHORT).show()
        return
    }
    val dialog = Dialog(this)
    applyDialogWindow(dialog)
    val root = dialogShell()
    root.addView(TextView(this).apply {
        text = "Nueva entrada"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(tallerSubmoduloAccent(TallerCanonicos.SUBMODULO_BODEGA_ROJA))
    })
    val producto = spinner(root, "Producto consumible *", consumibles.map {
        "${it.nombre} · ${it.codigo} · Stock ${formatoCantidadTaller(it.cantidadTotal)}"
    })
    val cantidad = field(root, "Cantidad de entrada *", "Mayor que cero", number = true)
    val motivo = field(root, "Motivo *", "Compra, reposición, devolución de proveedor...")
    lateinit var registrar: Button
    registrar = primaryButton("Registrar entrada") {
        if (!required(cantidad, motivo)) return@primaryButton
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Se requiere conexión para actualizar el stock", Toast.LENGTH_LONG).show()
            return@primaryButton
        }
        val seleccionada = consumibles[producto.selectedItemPosition.coerceIn(0, consumibles.lastIndex)]
        val valor = cantidad.text.toString().replace(",", ".").toDoubleOrNull() ?: Double.NaN
        if (!valor.isFinite() || valor <= 0.0) {
            Toast.makeText(this, "La cantidad debe ser mayor que cero", Toast.LENGTH_SHORT).show()
            return@primaryButton
        }
        val referencia = firestore.collection("herramientas").document(claveHerramientaCloud(seleccionada))
        registrar.isEnabled = false
        obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
            val fecha = now()
            var totalNuevo = 0.0
            var ocupados = 0.0
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(referencia)
                if (!snapshot.exists()) throw IllegalStateException("El producto ya no existe")
                val nube = herramientaDesdeDocumentoFirestore(snapshot)
                    ?: throw IllegalStateException("No se pudo validar el producto")
                if (!nube.esConsumibleBodegaRoja()) throw IllegalStateException("El producto ya no está marcado como consumible")
                val total = numeroDocumentoOpcional(snapshot, "cantidad_total", "stock_total", "cantidad")
                    ?.takeIf { it >= 0.0 } ?: throw IllegalStateException("Stock inválido")
                ocupados = numeroDocumento(snapshot, "cantidad_ocupada", "ocupados").coerceAtLeast(0.0)
                totalNuevo = total + valor
                transaction.set(referencia, mapOf(
                    "cantidad_total" to totalNuevo,
                    "cantidad_disponible" to (totalNuevo - ocupados).coerceAtLeast(0.0),
                    "estado" to if (ocupados > 0.0) "En uso" else "Disponible",
                    "ultima_actualizacion" to fecha,
                ), SetOptions.merge())
                transaction.set(firestore.collection("movimientos").document(), mapOf(
                    "fecha" to fecha,
                    "modulo" to TallerCanonicos.MODULO,
                    "tipoMovimiento" to "Entrada stock",
                    "item" to seleccionada.nombre,
                    "referencia" to seleccionada.codigo,
                    "cantidad" to valor,
                    "unidad" to seleccionada.unidad,
                    "submodulo_taller" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                    "es_consumible" to true,
                    "labor" to motivo.text.toString().trim(),
                    "responsable_entrega" to usuario,
                    "usuario_uid" to auth.currentUser?.uid.orEmpty(),
                ))
                null
            }.addOnSuccessListener {
                db.actualizarCantidadTotalHerramienta(seleccionada.id, totalNuevo, ocupados)
                db.insertarMovimiento(Movimiento(
                    fecha = fecha,
                    modulo = TallerCanonicos.MODULO,
                    tipoMovimiento = "Entrada stock",
                    item = seleccionada.nombre,
                    referencia = seleccionada.codigo,
                    cantidad = valor.toString(),
                    unidad = seleccionada.unidad,
                    labor = motivo.text.toString().trim(),
                    maquinaria = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                ))
                marcarCacheTallerTrasCambio("entrada_consumible_bodega")
                dialog.dismiss()
                onDone(seleccionada.copy(cantidadTotal = totalNuevo, cantidadOcupada = ocupados, estado = if (ocupados > 0.0) "En uso" else "Disponible"))
            }.addOnFailureListener { error ->
                registrar.isEnabled = true
                Toast.makeText(this, error.localizedMessage ?: "No se pudo registrar la entrada", Toast.LENGTH_LONG).show()
            }
        }
    }
    root.addView(registrar)
    dialog.setContentView(root)
    dialog.show()
}

private fun MainActivity.registrarSalidaDefinitivaMultiple(
    lineas: List<SalidaDefinitivaLinea>,
    solicitante: String,
    labor: String,
    area: String,
    observaciones: String,
    fotoUrl: String,
    onSuccess: (List<SalidaDefinitivaResultado>) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    if (lineas.isEmpty()) {
        onFailure(IllegalStateException("No hay productos para registrar"))
        return
    }
    if (!isNetworkAvailable()) {
        onFailure(IllegalStateException("Se requiere conexión para la salida definitiva"))
        return
    }
    val claves = lineas.map { it.documentoId }
    if (claves.any { it.isBlank() } || claves.distinct().size != claves.size) {
        onFailure(IllegalStateException("La entrega contiene productos repetidos o inválidos"))
        return
    }
    obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { responsable ->
        val fecha = now()
        val salidaId = "SDR-${UUID.randomUUID()}"
        val referencias = lineas.associate { it.documentoId to firestore.collection("herramientas").document(it.documentoId) }
        val movimientos = lineas.associate { it.documentoId to firestore.collection("movimientos").document() }
        firestore.runTransaction { transaction ->
            val snapshots = linkedMapOf<String, DocumentSnapshot>()
            referencias.forEach { (id, referencia) -> snapshots[id] = transaction.get(referencia) }

            val resultados = lineas.map { linea ->
                val snapshot = snapshots[linea.documentoId]
                    ?: throw IllegalStateException("No se pudo leer ${linea.herramienta.nombre}")
                if (!snapshot.exists()) throw IllegalStateException("${linea.herramienta.nombre} ya no existe")
                val nube = herramientaDesdeDocumentoFirestore(snapshot)
                    ?: throw IllegalStateException("Documento inválido para ${linea.herramienta.nombre}")
                if (!nube.esConsumibleBodegaRoja() || !booleanoDocumento(snapshot, "es_consumible", "consumible")) {
                    throw IllegalStateException("${linea.herramienta.nombre} ya no está marcado como consumible")
                }
                if (!coincideCodigoHerramienta(linea.herramienta, nube.codigo)) {
                    throw IllegalStateException("El código de ${linea.herramienta.nombre} ya no coincide")
                }
                val total = numeroDocumentoOpcional(snapshot, "cantidad_total", "stock_total", "cantidad")
                    ?.takeIf { it >= 0.0 } ?: throw IllegalStateException("Stock inválido en ${linea.herramienta.nombre}")
                val ocupados = numeroDocumento(snapshot, "cantidad_ocupada", "ocupados").coerceAtLeast(0.0)
                val disponible = (total - ocupados).coerceAtLeast(0.0)
                if (!linea.cantidad.isFinite() || linea.cantidad <= 0.0 || linea.cantidad > disponible) {
                    throw IllegalStateException(
                        "Stock insuficiente en ${linea.herramienta.nombre}. Disponible: ${formatoCantidadTaller(disponible)}",
                    )
                }
                SalidaDefinitivaResultado(
                    linea = linea,
                    stockAnterior = total,
                    stockNuevo = total - linea.cantidad,
                    cantidadOcupada = ocupados,
                    salidaDefinitivaId = salidaId,
                    fecha = fecha,
                    responsableEntrega = responsable,
                    fotoUrl = fotoUrl,
                )
            }

            resultados.forEachIndexed { index, resultado ->
                val linea = resultado.linea
                val disponibleNuevo = (resultado.stockNuevo - resultado.cantidadOcupada).coerceAtLeast(0.0)
                transaction.set(referencias.getValue(linea.documentoId), mapOf(
                    "cantidad_total" to resultado.stockNuevo,
                    "cantidad_ocupada" to resultado.cantidadOcupada,
                    "cantidad_disponible" to disponibleNuevo,
                    "estado" to if (disponibleNuevo <= 0.0 && resultado.cantidadOcupada <= 0.0) "Agotado" else if (resultado.cantidadOcupada > 0.0) "En uso" else "Disponible",
                    "ultima_actualizacion" to fecha,
                    "actualizado_por_uid" to auth.currentUser?.uid.orEmpty(),
                ), SetOptions.merge())
                transaction.set(movimientos.getValue(linea.documentoId), mapOf(
                    "fecha" to fecha,
                    "modulo" to TallerCanonicos.MODULO,
                    "tipoMovimiento" to TallerCanonicos.TIPO_MOV_SALIDA_DEFINITIVA,
                    "es_salida_definitiva" to true,
                    "es_consumible" to true,
                    "item" to linea.herramienta.nombre,
                    "referencia" to linea.herramienta.codigo,
                    "codigo" to linea.herramienta.codigo,
                    "codigo_principal" to linea.herramienta.codigo,
                    "codigo_qr" to linea.herramienta.codigoQr,
                    "categoria" to linea.herramienta.subcategoria,
                    "submodulo_taller" to TallerCanonicos.SUBMODULO_BODEGA_ROJA,
                    "documento_id" to linea.documentoId,
                    "herramienta_clave" to linea.documentoId,
                    "herramientaId" to linea.herramienta.id.toString(),
                    "cantidad" to linea.cantidad,
                    "unidad" to linea.herramienta.unidad.ifBlank { "UNIDAD" },
                    "solicitante" to solicitante,
                    "asignado_a" to solicitante,
                    "responsable_entrega" to responsable,
                    "labor" to labor,
                    "area_destino" to area,
                    "zona_ejecucion" to area,
                    "observaciones" to observaciones,
                    "fotoUrl" to fotoUrl,
                    "stock_antes" to resultado.stockAnterior,
                    "stock_despues" to resultado.stockNuevo,
                    "cantidad_ocupada" to resultado.cantidadOcupada,
                    "salida_definitiva_id" to salidaId,
                    "linea_salida" to index + 1,
                    "total_lineas" to resultados.size,
                    "usuario_uid" to auth.currentUser?.uid.orEmpty(),
                ))
            }
            resultados
        }.addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure)
    }
}

private fun MainActivity.aplicarSalidaDefinitivaLocal(
    resultados: List<SalidaDefinitivaResultado>,
    solicitante: String,
    labor: String,
    area: String,
    observaciones: String,
) {
    resultados.forEach { resultado ->
        val h = resultado.linea.herramienta
        db.actualizarCantidadTotalHerramienta(h.id, resultado.stockNuevo, resultado.cantidadOcupada)
        db.insertarMovimiento(Movimiento(
            fecha = resultado.fecha,
            modulo = TallerCanonicos.MODULO,
            tipoMovimiento = TallerCanonicos.TIPO_MOV_SALIDA_DEFINITIVA,
            item = h.nombre,
            referencia = h.codigo,
            marca = h.marca,
            cantidad = resultado.linea.cantidad.toString(),
            unidad = h.unidad.ifBlank { "UNIDAD" },
            solicitante = solicitante,
            labor = "$labor | Área: $area",
            maquinaria = TallerCanonicos.SUBMODULO_BODEGA_ROJA,
            herramientaId = h.id.toString(),
            estado = if (resultado.stockNuevo <= 0.0) "Agotado" else "Disponible",
            observaciones = listOf(
                observaciones,
                "Salida definitiva ID: ${resultado.salidaDefinitivaId}",
                resultado.fotoUrl.takeIf { it.isNotBlank() }?.let { "__FOTO_URL__$it" }.orEmpty(),
            ).filter { it.isNotBlank() }.joinToString("\n"),
            asignadoA = solicitante,
            responsableEntrega = resultado.responsableEntrega,
        ))
    }
    marcarCacheTallerTrasCambio("salida_definitiva_bodega_roja")
}
