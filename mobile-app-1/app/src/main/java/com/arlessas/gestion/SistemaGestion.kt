@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.widget.*
import androidx.core.content.edit
import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Archivo modularizado con funciones de extensión de MainActivity.
// Mantiene el comportamiento original, pero separa responsabilidades para facilitar mantenimiento.

internal fun MainActivity.procesarEscaneoInteligente(texto: String) {
    if (texto.isBlank()) return
    
    // Buscar en todo el catálogo cargado
    catalogoCargado.forEach { (mod, catMap) ->
        catMap.forEach { (cat, itemMap) ->
            itemMap.forEach { (item, refList) ->
                refList.forEach { ref ->
                    val nombreCompleto = "$item - $ref"
                    // Coincidencia exacta o contenida (ignora mayúsculas)
                    if (texto.equals(nombreCompleto, ignoreCase = true) || 
                        texto.equals(item, ignoreCase = true) || 
                        texto.equals(ref, ignoreCase = true)) {
                        
                        // Emitir el evento de selección inteligente si el formulario lo soporta
                        onSmartScanResult?.invoke(mod, cat, item, ref)
                        android.util.Log.d("ArlesGestión", "QR detectado como producto: $nombreCompleto")
                    }
                }
            }
        }
    }
}

internal fun MainActivity.showRegistrosMenu() {
    currentScreenRenderer = { showRegistrosMenu() }
    val root = baseScreen("Registros y exportación", "Consulta la información guardada y genera un archivo compatible con Excel.")
    
    // El botón de movimientos ahora está de primero, justo debajo de la lógica de inventario
    root.addView(primaryButton("Ver Movimientos (Tabla Nube)") { showTablaMovimientos() } )

    root.addView(primaryButton("Historial de cambios") { showHistorialCambios() } )
    root.addView(primaryButton("Panel de auditoría") { showPanelAuditoria() } )
    root.addView(primaryButton("Detector de inconsistencias") { showInconsistenciasScreen() } )
    root.addView(primaryButton("Vencimientos y alertas") { showVencimientosScreen() } )
    root.addView(primaryButton("Importar inventario Excel/CSV") { abrirImportadorInventario() } )
    root.addView(primaryButton("Sincronizar pendientes offline") { intentarSincronizarPendientes() } )
    root.addView(primaryButton("Backup manual en nube") { ejecutarBackupManual() } )
    root.addView(primaryButton("Ver herramientas registradas") { showHerramientasRegistradas() } )
    root.addView(primaryButton("Exportar CSV para Excel / Drive") { exportarCsv() } )
    root.addView(primaryButton("Exportar PDF resumido") { exportarPdf() } )
    
    root.addView(infoText("Incluye auditoría, respaldo, importación y modo offline con pendientes de sincronización."))
}

internal fun MainActivity.showPanelAuditoria() {
    val root = baseScreen("Panel de auditoría", "Respaldo, modo offline, inconsistencias e importaciones.") { showRegistrosMenu() }
    val cambios = db.obtenerHistorialCambios(200)
    val pendientes = db.contarPendientesSync()
    val inconsistencias = detectarInconsistencias()
    val prefs = getSharedPreferences("gestion_config", Context.MODE_PRIVATE)
    val ultimoBackup = prefs.getString("ultimo_backup_fecha_hora", "Sin backup registrado") ?: "Sin backup registrado"
    val ultimoImporte = prefs.getString("ultimo_importe", "Sin importaciones registradas") ?: "Sin importaciones registradas"

    root.addView(cardText(
        "Último backup: $ultimoBackup\n" +
                "Pendientes offline: $pendientes\n" +
                "Cambios auditados: ${cambios.size}\n" +
                "Inconsistencias detectadas: ${inconsistencias.size}\n" +
                "Última importación: $ultimoImporte"
    ))

    root.addView(primaryButton("Hacer backup ahora") { ejecutarBackupManual() })
    root.addView(primaryButton("Reorganizar y unificar códigos") { confirmarReorganizacionInventario() })
    root.addView(primaryButton("Sincronizar pendientes") { intentarSincronizarPendientes() })
    root.addView(primaryButton("Ver inconsistencias") { showInconsistenciasScreen() })
    root.addView(primaryButton("Importar inventario Excel/CSV") { abrirImportadorInventario() })
    root.addView(primaryButton("Ver historial de cambios") { showHistorialCambios() })
}

internal fun MainActivity.showInconsistenciasScreen() {
    val root = baseScreen("Detector de inconsistencias", "Revisión de errores comunes antes de auditoría.", backAction = { showRegistrosMenu() })
    val hallazgos = detectarInconsistencias()
    if (hallazgos.isEmpty()) {
        root.addView(cardText("Sin inconsistencias críticas detectadas."))
    } else {
        hallazgos.forEachIndexed { index, texto ->
            root.addView(cardText("${index + 1}. $texto"))
        }
    }
    root.addView(primaryButton("Exportar reporte CSV/PDF") { exportarCsv() })
}

internal fun MainActivity.detectarInconsistencias(): List<String> {
    val hallazgos = mutableListOf<String>()
    val movimientos = db.obtenerMovimientos()

    movimientos.forEach { m ->
        val cantidad = m.cantidad.replace(",", ".").toDoubleOrNull()
        if (m.item.isBlank()) hallazgos.add("Movimiento #${m.id}: producto vacío.")
        if (cantidad == null || cantidad <= 0.0) hallazgos.add("Movimiento #${m.id}: cantidad inválida (${emptyDash(m.cantidad)}).")
        val esSalida = m.tipoMovimiento.contains("salida", true) || m.tipoMovimiento.contains("entrega", true)
        if (esSalida && !TallerCanonicos.esModuloTaller(m.modulo)) {
            if (m.solicitante.isBlank()) hallazgos.add("Movimiento #${m.id}: salida sin solicitante.")
            if (m.labor.isBlank()) hallazgos.add("Movimiento #${m.id}: salida sin labor.")
        }
    }

    // Buscar items con nombres muy similares
    val items = movimientos.map { it.item }.filter { it.isNotBlank() }.distinct()
    items.forEach { item ->
        val similar = items.find { it != item && it.startsWith(item.take(4), ignoreCase = true) && it.length <= item.length + 2 }
        if (similar != null) {
            hallazgos.add("Posible duplicado por nombre: '$item' y '$similar'.")
        }
    }

    return hallazgos
}

internal fun MainActivity.abrirImportadorInventario() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }
    importInventarioLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/vnd.ms-excel"))
}

internal fun MainActivity.importarInventarioDesdeUri(uri: Uri) {
    try {
        contentResolver.openInputStream(uri)?.use { stream ->
            val texto = stream.bufferedReader().readText()
            val preview = analizarInventarioTexto(texto)
            
            AlertDialog.Builder(this)
                .setTitle("Vista previa de importación")
                .setMessage(preview.resumen() + "\n\n¿Deseas aplicar estos cambios en Firestore?")
                .setPositiveButton("Sí, importar") { _, _ ->
                    val resultado = importarInventarioDesdeTexto(texto)
                    Toast.makeText(this, resultado, Toast.LENGTH_LONG).show()
                    getSharedPreferences("gestion_config", Context.MODE_PRIVATE).edit {
                        putString("ultimo_importe", now())
                    }
                    sincronizarCatalogo()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.analizarInventarioTexto(texto: String): ImportPreview {
    val lineasBase = texto.lines().filter { it.trim().isNotBlank() }
    // Detección robusta de marcador de separador Excel (sep=;) incluso si hay texto previo o basura
    val tieneMarcador = lineasBase.firstOrNull()?.contains("sep=", ignoreCase = true) == true
    val lineas = if (tieneMarcador) lineasBase.drop(1) else lineasBase
    
    if (lineas.size < 2) return ImportPreview(0,0,0,0,0,0,0, emptyList())
    
    // Si tiene marcador, intentar extraer el carácter exacto
    val marcadorLinea = if (tieneMarcador) lineasBase.first() else ""
    val separadorSugerido = if (marcadorLinea.contains("sep=")) marcadorLinea.split("sep=").lastOrNull()?.firstOrNull() else null
    
    val separador = separadorSugerido ?: if (lineas.first().count { it == ';' } >= lineas.first().count { it == ',' }) ';' else ','
    val encabezado = parseCsvLine(lineas.first(), separador).map { normalizarBusqueda(it).uppercase(Locale.getDefault()) }
    
    fun idx(vararg nombres: String): Int = nombres.map { normalizarBusqueda(it).uppercase(Locale.getDefault()) }.map { encabezado.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
    fun col(campos: List<String>, vararg nombres: String): String {
        val i = idx(*nombres)
        return if (i >= 0 && i < campos.size) campos[i].trim() else ""
    }

    var validos = 0
    var entradas = 0
    var vencimientos = 0
    var sinProducto = 0
    val codigosVistos = mutableSetOf<String>()
    var repetidos = 0
    val muestra = mutableListOf<String>()

    lineas.drop(1).take(500).forEachIndexed { i, linea ->
        val campos = parseCsvLine(linea, separador)
        val item = col(campos, "ITEM", "PRODUCTO", "NOMBRE")
        if (item.isBlank()) { sinProducto++; return@forEachIndexed }
        
        validos++
        val cant = col(campos, "CANTIDAD", "EXISTENCIA", "STOCK").replace(",",".").toDoubleOrNull() ?: 0.0
        if (cant != 0.0) entradas++
        
        val vence = col(campos, "VENCIMIENTO", "FECHA VENCIMIENTO")
        if (vence.isNotBlank()) vencimientos++
        
        val cod = col(campos, "CODIGO INTERNO", "CÓDIGO INTERNO", "CODIGO_INTERNO", "CODIGO", "CODE", "ID")
        if (cod.isNotBlank()) {
            if (codigosVistos.contains(cod)) repetidos++
            else codigosVistos.add(cod)
        }

        if (i < 5) muestra.add(item.take(20) + (if (cant > 0) " ($cant)" else ""))
    }

    return ImportPreview(validos, entradas, vencimientos, repetidos, sinProducto, 0, 0, muestra)
}

internal fun MainActivity.importarInventarioDesdeTexto(texto: String): String {
    val lineasBase = texto.lines().filter { it.trim().isNotBlank() }
    val tieneMarcador = lineasBase.firstOrNull()?.contains("sep=", ignoreCase = true) == true
    val lineas = if (tieneMarcador) lineasBase.drop(1) else lineasBase
    
    if (lineas.size < 2) return "No hay filas para importar"

    val marcadorLinea = if (tieneMarcador) lineasBase.first() else ""
    val separadorSugerido = if (marcadorLinea.contains("sep=")) marcadorLinea.split("sep=").lastOrNull()?.firstOrNull() else null
    
    val separador = separadorSugerido ?: if (lineas.first().count { it == ';' } >= lineas.first().count { it == ',' }) ';' else ','
    val encabezado = parseCsvLine(lineas.first(), separador).map { normalizarBusqueda(it).uppercase(Locale.getDefault()) }
    
    fun idx(vararg nombres: String): Int = nombres.map { normalizarBusqueda(it).uppercase(Locale.getDefault()) }.map { encabezado.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
    fun col(campos: List<String>, vararg nombres: String): String {
        val i = idx(*nombres)
        return if (i >= 0 && i < campos.size) campos[i].trim() else ""
    }

    var productos = 0
    var entradas = 0
    var vencimientos = 0
    val fecha = now()

    lineas.drop(1).forEach { linea ->
        val campos = parseCsvLine(linea, separador)
        val modulo = col(campos, "MODULO", "MÓDULO").ifBlank { "Consumibles" }
        val item = col(campos, "ITEM", "PRODUCTO", "NOMBRE")
        if (item.isBlank()) return@forEach

        if (esModuloTallerImport(modulo)) {
            importarHerramientaTallerDesdeCsv(encabezado, campos)
            productos++
            val cantidadTaller = col(campos, "CANTIDAD", "EXISTENCIA", "STOCK").replace(",", ".").toDoubleOrNull() ?: 0.0
            if (cantidadTaller > 0) entradas++
            return@forEach
        }

        val referencia = col(campos, "REFERENCIA", "REF")
        val categoria = col(campos, "CATEGORIA", "CATEGORÍA").ifBlank { resolverCategoriaInteligente(item, referencia, "Ferretería y Tornillería") }
        val marca = col(campos, "MARCA", "BRAND")
        val moduloCanonico = moduloCanonicoInventario(modulo, item, referencia, categoria)
        val cantidadTexto = col(campos, "CANTIDAD", "EXISTENCIA", "STOCK")
        val cantidad = cantidadTexto.replace(",", ".").toDoubleOrNull() ?: 0.0
        val unidad = col(campos, "UNIDAD", "UNIDAD MEDIDA").ifBlank { sugerirUnidadGeneral(moduloCanonico, item) }
        val codigoInterno = normalizarCodigoInterno(
            col(campos, "CODIGO INTERNO", "CÓDIGO INTERNO", "CODIGO_INTERNO", "CODIGO", "CÓDIGO", "CODE", "ID", "DOCUMENTO_FIRESTORE")
        ).ifBlank { generarCodigoImportacion(moduloCanonico, categoria, item, marca, productos) }
        val observaciones = col(campos, "OBSERVACIONES", "NOTAS", "COMENTARIO")

        // 1. Guardar en catálogo y existencias
        agregarProductoImportado(moduloCanonico, categoria, item, referencia, codigoInterno, marca)
        
        // 2. Registrar entrada si hay cantidad
        if (cantidad > 0) {
            actualizarExistenciaImportada(moduloCanonico, categoria, item, referencia, codigoInterno, marca, cantidad, unidad, observaciones)
            entradas++
        }

        // 3. Registrar vencimiento si hay fecha
        val fechaVence = col(campos, "VENCIMIENTO", "FECHA VENCIMIENTO")
        if (fechaVence.isNotBlank()) {
            val lote = col(campos, "LOTE", "BATCH")
            val v = Vencimiento(
                fechaRegistro = fecha,
                modulo = moduloCanonico,
                item = item,
                referencia = referencia,
                fechaVencimiento = fechaVence,
                lote = lote,
                cantidad = cantidad.toString(),
                observaciones = "Importado desde Excel/CSV"
            )
            val id = db.insertarVencimiento(v)
            registrarCambioLocal("IMPORTAR_VENCIMIENTO", moduloCanonico, id.toString(), "Vencimiento importado", "", "Vence $fechaVence")
            vencimientos++
        }
        productos++
    }

    return "Importado: $productos productos, $entradas entradas, $vencimientos vencimientos"
}

internal fun parseCsvLine(linea: String, separador: Char): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    
    var i = 0
    while (i < linea.length) {
        val c = linea[i]
        if (c == '\"') {
            if (inQuotes && i + 1 < linea.length && linea[i + 1] == '\"') {
                current.append('\"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (c == separador && !inQuotes) {
            result.add(current.toString())
            current = StringBuilder()
        } else {
            current.append(c)
        }
        i++
    }
    result.add(current.toString())
    return result
}

internal fun MainActivity.agregarProductoImportado(modulo: String, categoria: String, item: String, referencia: String, codigo: String, marca: String) {
    val mod = moduloCanonicoInventario(modulo, item, referencia, categoria)
    val nombreCompleto = listOf(item, marca, referencia).filter { it.isNotBlank() }.joinToString(" ")
    
    val data: Map<String, Any?> = mapOf(
        "modulo" to mod,
        "categoria" to categoria,
        "item" to item,
        "referencia" to referencia,
        "marca" to marca,
        "nombre_completo" to nombreCompleto,
        "busqueda" to "$codigo $nombreCompleto".lowercase(Locale.getDefault()),
        "codigo_interno" to codigo,
        "ultima_fecha" to now(),
        "ultimo_solicitante" to "Importador Excel/CSV"
    )

    firestore.collection("catalogo_personalizado").document(codigo).set(data)
    firestore.collection("existencias").document(codigo).get().addOnSuccessListener { doc ->
        if (!doc.exists()) {
            val existenciaBase = data.toMutableMap()
            existenciaBase["cantidad"] = 0.0
            existenciaBase["unidad"] = sugerirUnidadGeneral(mod, item)
            firestore.collection("existencias").document(codigo).set(existenciaBase)
        }
    }
}

internal fun MainActivity.actualizarExistenciaImportada(modulo: String, categoria: String, item: String, referencia: String, codigo: String, marca: String, cantidad: Double, unidad: String, obs: String) {
    val ref = firestore.collection("existencias").document(codigo)
    val fecha = now()
    val mod = moduloCanonicoInventario(modulo, item, referencia, categoria)
    
    firestore.runTransaction { transaction ->
        val snapshot = transaction.get(ref)
        val anterior = snapshot.getDouble("cantidad") ?: 0.0
        val nuevoStock = anterior + cantidad

        val existenciaData = mapOf(
            "modulo" to mod,
            "categoria" to categoria,
            "item" to item,
            "referencia" to referencia,
            "marca" to marca,
            "codigo_interno" to codigo,
            "cantidad" to nuevoStock,
            "unidad" to unidad,
            "ultima_fecha" to fecha,
            "ultimo_solicitante" to "Importador Excel/CSV"
        )
        transaction.set(ref, existenciaData)
        
        mapOf("anterior" to anterior, "nuevo" to nuevoStock)
    }.addOnSuccessListener { res ->
        val anterior = res["anterior"] ?: 0.0
        val nuevoStock = res["nuevo"] ?: 0.0
        val ent = Entrada(
            fecha = fecha,
            modulo = modulo,
            item = item,
            referencia = referencia,
            codigoInterno = codigo,
            cantidad = cantidad,
            unidad = unidad,
            observaciones = "Importado desde Excel/CSV. $obs".trim()
        )
        db.insertarEntrada(ent)
        registrarCambioLocal("IMPORTAR_ENTRADA", modulo, codigo, "Entrada masiva", anterior.toString(), nuevoStock.toString())
    }
}

internal fun generarCodigoImportacion(modulo: String, categoria: String, item: String, marca: String, index: Int): String {
    val m = modulo.take(1).uppercase(Locale.getDefault())
    val c = categoria.take(1).uppercase(Locale.getDefault())
    val i = item.take(1).uppercase(Locale.getDefault())
    val timestamp = System.currentTimeMillis().toString().takeLast(4)
    return "IMP-$m$c$i-$timestamp-$index"
}

internal fun MainActivity.ejecutarBackupManual() {
    val reporte = db.construirReporteTextoCompleto()
    registrarCambioLocal("BACKUP_MANUAL", "Sistema", "", "Backup de seguridad iniciado manualmente")
    ejecutarBackupEnNube(reporte)
}

internal fun MainActivity.ejecutarBackupAutomaticoSiCorresponde() {
    val prefs = getSharedPreferences("gestion_config", Context.MODE_PRIVATE)
    val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val ultimo = prefs.getString("ultimo_backup_fecha", "")
    if (ultimo != hoy) {
        ejecutarBackupManual()
    }
}

internal fun MainActivity.ejecutarBackupEnNube(contenido: String) {
    val user = auth.currentUser?.email ?: "anonimo"
    val fechaHora = now()
    val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    
    val data = mapOf(
        "usuario" to user,
        "fecha" to fechaHora,
        "contenido" to contenido
    )

    firestore.collection("backups").add(data).addOnSuccessListener {
        getSharedPreferences("gestion_config", Context.MODE_PRIVATE).edit().apply {
            putString("ultimo_backup_fecha", hoy)
            putString("ultimo_backup_fecha_hora", fechaHora)
            apply()
        }
    }
}

internal fun MainActivity.guardarFirestoreOffline(coleccion: String, data: Map<String, Any?>) {
    db.insertarPendienteSync(coleccion, data)
}


internal fun MainActivity.showHistorialCambios() {
    currentScreenRenderer = { showHistorialCambios() }
    val root = baseScreen("Historial de auditoría", "Trazabilidad completa de acciones, usuarios y dispositivos.")
    val historial = db.obtenerHistorialCambios(100)
    
    if (historial.isEmpty()) {
        root.addView(infoText("No hay cambios registrados en el historial local."))
    } else {
        historial.forEach { h ->
            val card = card().apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            
            card.addView(TextView(this).apply {
                text = "${h.fecha} · ${h.accion}"
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(verdeOscuro)
                textSize = 13f
            })
            
            card.addView(TextView(this).apply {
                text = "${h.modulo}: ${h.descripcion}"
                setTextColor(texto)
                textSize = 14f
                setPadding(0, dp(4), 0, dp(4))
            })

            if (h.antes.isNotBlank() || h.despues.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = "Cambio: ${h.antes} → ${h.despues}"
                    setTextColor(gris)
                    textSize = 12f
                })
            }
            
            card.addView(TextView(this).apply {
                text = "Por: ${h.usuario}"
                setTextColor(gris)
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
            
            root.addView(card)
        }
    }
}

internal fun MainActivity.showVencimientosScreen() {
    currentScreenRenderer = { showVencimientosScreen() }
    val root = baseScreen("Vencimientos y alertas", "Seguimiento de productos químicos y consumibles con fecha de caducidad.")
    
    val lista = db.obtenerVencimientos()
    if (lista.isEmpty()) {
        root.addView(infoText("No hay alertas de vencimiento programadas."))
    } else {
        lista.forEach { v ->
            val estado = estadoVencimiento(v.fechaVencimiento)
            val color = when(estado) {
                "VENCIDO" -> Color.RED
                "PRÓXIMO A VENCER" -> Color.rgb(255, 165, 0)
                else -> verde
            }
            root.addView(cardText("Producto: ${v.item}\nLote: ${v.lote} | Vence: ${v.fechaVencimiento}\nESTADO: $estado").apply { setTextColor(color) })
        }
    }
}

internal fun MainActivity.exportarInventarioActualCsv(docs: List<DocumentSnapshot>) {
    val b = StringBuilder()
    b.appendLine("sep=;")
    b.insert(0, '\uFEFF')
    b.appendLine("FECHA;CODIGO_INTERNO;PRODUCTO;MARCA;REFERENCIA;STOCK;UNIDAD;ULTIMO_SOLICITANTE;DOCUMENTO_FIRESTORE")
    
    docs.forEach { doc ->
        val fecha = doc.getString("ultima_fecha") ?: "—"
        val cod = doc.getString("codigo_interno") ?: doc.id
        val item = doc.getString("item") ?: "—"
        val marca = doc.getString("marca") ?: "—"
        val ref = doc.getString("referencia") ?: "—"
        val stock = doc.getDouble("cantidad") ?: 0.0
        val unidad = doc.getString("unidad") ?: "—"
        val sol = doc.getString("ultimo_solicitante") ?: "—"
        
        b.appendLine("$fecha;$cod;$item;$marca;$ref;$stock;$unidad;$sol;${doc.id}")
    }
    
    pendingCsv = b.toString()
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/csv"
        putExtra(Intent.EXTRA_TITLE, "Inventario_Gestion_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.csv")
    }
    exportCsvLauncher.launch(intent)
}

internal fun MainActivity.exportarInventarioAseoCsv(docs: List<DocumentSnapshot>) {
    val b = StringBuilder()
    b.appendLine("sep=;")
    b.insert(0, '\uFEFF')
    b.appendLine("CODIGO_INTERNO;PISO;CATEGORIA;PRODUCTO;UNIDAD;STOCK_ACTUAL;DOCUMENTO_FIRESTORE")

    docs.forEach { doc ->
        val codigo = AseoCanonicos.normalizarCodigo(doc.getString("codigo_interno").orEmpty().ifBlank { doc.id })
        val piso = doc.getLong("piso")?.toString() ?: AseoCanonicos.pisoDesdeCodigo(codigo).toString()
        val categoria = doc.getString("categoria") ?: AseoCanonicos.CATEGORIA_PRINCIPAL
        val producto = doc.getString("producto") ?: codigo
        val unidad = doc.getString("unidad") ?: "UNIDAD"
        val stock = doc.getDouble("stock_actual") ?: 0.0
        b.appendLine("$codigo;$piso;$categoria;$producto;$unidad;$stock;${doc.id}")
    }

    pendingCsv = b.toString()
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/csv"
        putExtra(Intent.EXTRA_TITLE, "Inventario_ASEO_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.csv")
    }
    exportCsvLauncher.launch(intent)
}

internal fun MainActivity.exportarCsv() {
    registrarCambioLocal("EXPORTAR_CSV", "Sistema", "", "Exportación de inventario a CSV")
    pendingCsv = db.construirCsvCompleto()
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/csv"
        putExtra(Intent.EXTRA_TITLE, "Gestion_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}.csv")
    }
    exportCsvLauncher.launch(intent)
}

internal fun MainActivity.exportarPdf() {
    registrarCambioLocal("EXPORTAR_PDF", "Sistema", "", "Exportación de inventario a PDF")
    val reporte = db.construirReporteTextoCompleto()
    pendingPdf = crearPdfDesdeTexto(reporte)
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/pdf"
        putExtra(Intent.EXTRA_TITLE, "Reporte_Gestion_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.pdf")
    }
    exportPdfLauncher.launch(intent)
}

internal fun crearPdfDesdeTexto(texto: String): ByteArray {
    val doc = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
    var page = doc.startPage(pageInfo)
    val canvas = page.canvas
    val paint = android.graphics.Paint().apply {
        textSize = 10f
        color = Color.BLACK
    }
    
    var y = 40f
    texto.lines().forEach { linea ->
        if (y > 800) {
            doc.finishPage(page)
            page = doc.startPage(pageInfo)
            y = 40f
        }
        canvas.drawText(linea, 40f, y, paint)
        y += 15f
    }
    
    doc.finishPage(page)
    val outputStream = java.io.ByteArrayOutputStream()
    doc.writeTo(outputStream)
    doc.close()
    return outputStream.toByteArray()
}

internal fun MainActivity.registrarCambioLocal(accion: String, modulo: String, registroId: String, descripcion: String, antes: String = "", despues: String = "") {
    val email = auth.currentUser?.email ?: "local"
    val dispositivo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
    val versionApp = AppVersionInfo.VERSION_NAME
    val usuarioAudit = "$email | $dispositivo"

    db.registrarHistorialCambio(accion, modulo, registroId, descripcion, antes, despues, usuarioAudit)
    
    guardarFirestoreOffline(
        "historial_cambios",
        mapOf(
            "fecha" to now(),
            "accion" to accion,
            "modulo" to modulo,
            "registroId" to registroId,
            "descripcion" to descripcion,
            "antes" to antes,
            "despues" to despues,
            "usuario" to email,
            "dispositivo" to dispositivo,
            "version" to versionApp
        )
    )
}

internal fun fechaValida(valor: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(valor)
        true
    } catch (_: Exception) {
        false
    }
}

internal fun estadoVencimiento(fecha: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.isLenient = false
        val vence = sdf.parse(fecha) ?: return "Fecha inválida"
        val hoy = Date()
        val dias = ((vence.time - hoy.time) / (1000 * 60 * 60 * 24)).toInt()
        when {
            dias < 0 -> "VENCIDO"
            dias <= 30 -> "PRÓXIMO A VENCER"
            else -> "VIGENTE"
        }
    } catch (_: Exception) {
        "Fecha inválida"
    }
}

internal fun MainActivity.required(vararg fields: EditText): Boolean {
    fields.forEach {
        val valor = it.text.toString().trim()
        if (valor.isEmpty()) {
            it.error = "Campo obligatorio"
            Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            return false
        }
        if ((it.inputType and InputType.TYPE_CLASS_NUMBER) == InputType.TYPE_CLASS_NUMBER) {
            val numero = valor.replace(",", ".").toDoubleOrNull()
            if (numero == null || numero <= 0) {
                it.error = "Cantidad inválida"
                Toast.makeText(this, "La cantidad debe ser mayor a cero", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }
    return true
}

internal fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

internal fun MainActivity.saved(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

internal fun emptyDash(value: String): String = value.ifBlank { "—" }

internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun resolverCategoriaInteligente(item: String, ref: String, actual: String): String {
    val texto = "$item $ref".uppercase(Locale.getDefault())
    val actualNorm = normalizarBusqueda(actual).uppercase(Locale.getDefault())
    if (actualNorm.contains("DOTACION") || actualNorm.contains("PARTE") ||
        actualNorm.contains("CONJUNTO") || actualNorm.contains("CALZADO")) {
        return resolverCategoriaDotacion(item, ref)
    }
    
    return when {
        // 1. QUÍMICOS (Agroquímicos y lubricantes)
        Regex("FERTILIZANTE|ABONO|UREA|KCL|DAP|SULFAMON|YARAMILA|NITROGENO|POTASIO").containsMatchIn(texto) -> "Fertilizantes Químicos"
        Regex("FUNGICIDA|INSECTICIDA|PAGUICIDA|HERBICIDA|GLIFOSATO|ACABADO|LORSPAN|AMINA").containsMatchIn(texto) -> "Fungicidas e Insecticidas"
        Regex("COADYUVANTE|PEGANTE|ADITIVO|ACEITE AGRICOLA").containsMatchIn(texto) -> "Coadyuvantes y Aditivos"
        Regex("LUBRICANTE|ACEITE|GRASA|HIDRAULICO|VALVULINA|TERPEL|MOBIL|SHELL|CHEVRON|MAXTER").containsMatchIn(texto) -> "Lubricantes y Grasas"
        
        // 2. EPP (Protección Personal) - Evaluación priorizada para evitar "revueltos"
        Regex("GUANTE|MITON|VAQUETA|NITRILO|LATEX|CAUCHO|GLOVE").containsMatchIn(texto) -> "Protección Manual (Guantes)"
        Regex("MASCARA|RESPIRADOR|TAPABOCA|KN95|MASK|RESPIRATOR|FILTRO.*MASCARA|FILTRO.*RESPIRADOR|FILTROS PARA MASCARA").containsMatchIn(texto) -> "Protección Respiratoria"
        Regex("AUDITIVO|OIDO|TAPA OIDO|EARPLUG|HEARING").containsMatchIn(texto) -> "Protección Auditiva"
        Regex("GAFA|LENTE|MONOGAFA|VISION|VISUAL|GLASSES|LENS").containsMatchIn(texto) -> "Protección Visual"
        Regex("CASCO|CARETA|VISOR|COFIA|SOMBRERO|SAFARY|CASQUETE|PORTAVISOR|HELMET|HAT").containsMatchIn(texto) -> "Protección Cabeza y Rostro"
        Regex("CANILLERA|MANGA|POLAINA|DELANTAL|ARNES|CARNAZA|GUADAÑADOR|OVEROL|IMPERMEABLE|SLEEVE|APRON").containsMatchIn(texto) -> "Cuerpo y Extremidades"
        
        // 3. DOTACIÓN (Vestimenta)
        Regex("BOTA|ZAPATO|CALZADO|ZAPATILLA|BOOT|SHOE").containsMatchIn(texto) -> "Calzado"
        Regex("CAMISA|CHAQUETA|POLO|BLUSA|CHALECO|SACO|SHIRT|JACKET").containsMatchIn(texto) -> "Parte Superior"
        Regex("PANTALON|JEAN|PANTALÓN|PANTS").containsMatchIn(texto) -> "Parte Inferior"
        Regex("CONJUNTO|VESTUARIO|UNIFORME|VESTIDO|UNIFORM").containsMatchIn(texto) -> "Conjunto"

        // 4. FILTROS Y BOMBAS (Consumibles técnicos)
        Regex("FILTER|FILTRO|FUEL|OIL|GAS|SEPARADOR|ELEMENTO|BOMBA|MOTOR|ARRANCADOR|SENSOR|INJETECH|MASTER|PREMIUM").containsMatchIn(texto) -> "Filtros y Bombas"
        
        // 5. MECÁNICA Y RODAMIENTOS
        Regex("NEUMATICO|RODAMIENTO|CHUMACERA|RETENEDOR|RETEN|RODILLO|REPUESTO|UNIVERSAL JOINT|THERMOSTAT|SELLO MACANICO|COGINETE|CILINDRO|SILENCIADOR|CARBURADOR|CUCHILLA|LIMA|GRATA|PIEDRA AFILAR|KAYM|KDWY|FAG|SKF|KOYO|HTH|PASTILLA|FRENO|BANDA|ARRANQUE|KIT DE ARRASTRE|GUADA|SHINDAIWA|TIRE|TUBELESS|PARCHE|MOTO|CADENA|PIÑON|BUJIA|PISTON|EMPAQUE|VALVULA MOTOR").containsMatchIn(texto) -> "Mecánica y Rodamientos"
        
        // 6. PLOMERÍA Y RIEGO
        Regex("PVC|TUBO|TEE|CODO|SIFON|UNION|VALVULA|RIEGO|ACOPLE|BRIDA|REDUCTOR|TAPON|UNIVERSAL PVC|BUJE|REDUCION|MANGUERA|COLLARIN|HIDRANTE|POMA|ASPERSOR|MICROASPERSOR|GOTERO|POLIDUCTO|ADAPTADOR").containsMatchIn(texto) -> "Plomería y Riego"
        
        // 7. ELECTRICIDAD
        Regex("INTERRUPTOR|TOMA|CABLE|LED|BOMBILLO|LINTERNA|CLAVIJA|ILUMINACION|CENTELSA|BATERIA|TERMINAL|VOLTIMETRO|FUSIBLE|MULTIMETRO|ENCINTADO|EXTENSION|CINTA AISLANTE").containsMatchIn(texto) -> "Electricidad"
        
        // 8. ASEO Y CAFETERÍA
        Regex("JABON|DETERGENTE|BOLSAS|SERVILLETAS|PAPEL HIGIENICO|ASEO|SANPIC|FABULOSO|CLOROX|AXION|VARSOL|LAVA|TRAPERO|ESCOBA|DESINFECTANTE|DESENGRASANTE|HIGIENICO|GUANTES DOMESTICO|CAFETERIA|CAFE|AZUCAR").containsMatchIn(texto) -> "Aseo y Cafetería"
        
        // 9. OFICINA Y EMPAQUE
        Regex("PAPEL|RESMA|CARPETA|GANCHO|TINTA|TONER|LAPICERO|ESFERO|CUADERNO|BLOCK|SOBRE|CAJA|VINIPEL|STRETCH|FLEJE|BOLSAS EMPAQUE|ETIQUETA|PEGANTE|MARCADOR").containsMatchIn(texto) -> "Oficina y Empaque"
        
        // 10. FERRETERÍA Y TORNILLERÍA
        Regex("PERNO|TORNILLO|ABRAZADERA|TUERCA|ARANDELA|REMACHE|PERRO|TIZA|PUNTILLA|CLAVO|ADITIVO|PEGANTE|SOLDADURA|DISCO|CINTA|BROCA|LLAVE|PINZA|MARTILLO|ALICATE|HERRAMIENTA|MANILA|SOGA|CABUYA").containsMatchIn(texto) -> "Ferretería y Tornillería"
        
        // SI NO HAY MATCH, MANTENEMOS LA ACTUAL O DEFAULT
        else -> actual.ifBlank { "Varios" }
    }
}

internal fun resolverCategoriaDotacion(item: String, ref: String): String {
    val texto = "$item $ref".uppercase(Locale.getDefault())
    return when {
        Regex("BOTA|ZAPATO|CALZADO|ZAPATILLA|BOOT|SHOE").containsMatchIn(texto) -> "Calzado"
        Regex("CAMISA|CHAQUETA|POLO|BLUSA|CHALECO|SACO|SHIRT|JACKET").containsMatchIn(texto) -> "Parte Superior"
        Regex("PANTALON|JEAN|PANTALÓN|PANTS").containsMatchIn(texto) -> "Parte Inferior"
        Regex("CONJUNTO|VESTUARIO|UNIFORME|VESTIDO|UNIFORM").containsMatchIn(texto) -> "Conjunto"
        else -> "Conjunto"
    }
}

internal fun MainActivity.esModuloTallerImport(modulo: String): Boolean {
    val modNorm = normalizarBusqueda(modulo).replace(" ", "").uppercase(Locale.getDefault())
    return modNorm == "TALLER"
        || modNorm == "HERRAMIENTAS"
        || modNorm.contains("HERRAMIENTASTALLER")
        || (modNorm.contains("TALLER") && modNorm.contains("HERRAMIENTA"))
}

internal fun MainActivity.importarHerramientaTallerDesdeCsv(
    encabezado: List<String>,
    campos: List<String>,
) {
    fun idx(vararg nombres: String): Int = nombres.map { normalizarBusqueda(it).uppercase(Locale.getDefault()) }
        .map { encabezado.indexOf(it) }
        .firstOrNull { it >= 0 } ?: -1
    fun col(vararg nombres: String): String {
        val i = idx(*nombres)
        return if (i >= 0 && i < campos.size) campos[i].trim() else ""
    }

    val item = col("ITEM", "PRODUCTO", "NOMBRE")
    val categoria = col("CATEGORIA", "CATEGORÍA")
    val subModulo = TallerCanonicos.resolverSubmoduloDesdeCampos(
        submoduloTaller = col("SUBMODULO_TALLER", "SUBMODULO", "SECCION", "AREA", "ÁREA"),
        categoria = categoria,
        ubicacion = col("UBICACION", "UBICACIÓN"),
        seccion = col("SECCION", "ÁREA", "AREA"),
    )
    val subcategoria = col("SUBCATEGORIA", "SUB CATEGORIA").ifBlank {
        if (TallerCanonicos.esSubmoduloTaller(categoria)) {
            col("REFERENCIA", "REF")
        } else {
            categoria.ifBlank { col("REFERENCIA", "REF") }
        }
    }
    val marca = col("MARCA", "BRAND")
    val tipo = col("TIPO", "TIPO_HERRAMIENTA")
    val tamano = col("TAMANO", "TAMAÑO", "TALLA")
    val unidad = col("UNIDAD", "UNIDAD MEDIDA").ifBlank { "UNIDAD" }
    val cantidad = col("CANTIDAD", "EXISTENCIA", "STOCK").replace(",", ".").toDoubleOrNull() ?: 1.0
    val codigoQr = col("CODIGO_QR", "QR").trim()
        .removePrefix("QR-")
        .removePrefix("qr-")
    val codigoRaw = col("CODIGO", "CODIGO_INTERNO", "CÓDIGO", "CODE", "ID")
    val codigo = when {
        codigoQr.isNotBlank() -> TallerCanonicos.normalizarCodigo(codigoQr)
        codigoRaw.isNotBlank() -> TallerCanonicos.normalizarCodigo(codigoRaw)
        else -> "SINQR-IMP-${System.currentTimeMillis()}"
    }
    val herramienta = Herramienta(
        fechaRegistro = now(),
        nombre = item,
        referencia = subcategoria,
        marca = marca,
        codigo = codigo,
        estado = "Disponible",
        ubicacion = subModulo,
        responsable = "",
        observaciones = col("OBSERVACIONES", "NOTAS", "COMENTARIO"),
        subModulo = subModulo,
        subcategoria = subcategoria,
        tipo = tipo,
        tamano = tamano,
        unidad = unidad,
        cantidadTotal = cantidad,
        cantidadOcupada = 0.0,
        codigoQr = codigoQr,
        requiereAsignarQr = codigo.startsWith("SINQR", true),
    )
    val idLocal = db.insertarOActualizarHerramientaCanonica(herramienta)
    registrarCambioLocal(
        "IMPORTAR_HERRAMIENTA_TALLER",
        TallerCanonicos.MODULO,
        idLocal.toString(),
        "Importado Taller/$subModulo: $item",
        "",
        cantidad.toString(),
    )
    val uid = auth.currentUser?.uid ?: ""
    obtenerInfoUsuario(uid) { usuario ->
        val data = dataHerramientaCloud(herramienta, idLocal, usuario) + mapOf(
            "importado_csv" to true,
            "submodulo_taller" to subModulo,
            "categoria" to subModulo,
            "subcategoria" to subcategoria,
        )
        firestore.collection("herramientas")
            .document(claveHerramientaCloud(herramienta))
            .set(data, com.google.firebase.firestore.SetOptions.merge())
    }
}

internal fun MainActivity.moduloCanonicoInventario(modulo: String, item: String = "", ref: String = "", categoria: String = ""): String {
    val modNorm = normalizarBusqueda(modulo).replace(" ", "").uppercase(Locale.getDefault())
    if (esModuloTallerImport(modulo)) return TallerCanonicos.MODULO
    return when {
        modNorm == "EPP" || modNorm.contains("PROTECCION") -> "EPP"
        modNorm.contains("ASEO") || modNorm.contains("LIMPIEZA") -> ModulosInventario.ASEO
        modNorm.contains("LUBRICANTE") || (modNorm.contains("TALLER") && !modNorm.contains("HERRAMIENTA")) ->
            ModulosInventario.LUBRICANTES_TALLER
        modNorm.contains("AGROQUIMICO") -> ModulosInventario.AGROQUIMICOS
        modNorm.contains("QUIMICO") ->
            if (ModulosInventario.esTallerQuimico(categoria) ||
                Regex("LUBRICANTE|ACEITE|GRASA|HIDRAULICO|VALVULINA|REFRIGERANTE|LIQUIDO DE FRENOS").containsMatchIn("$item $ref".uppercase(Locale.getDefault()))
            ) ModulosInventario.LUBRICANTES_TALLER else ModulosInventario.AGROQUIMICOS
        modNorm.contains("DOTACION") || modNorm.contains("VESTUARIO") -> "Dotación"
        modNorm.contains("COMBUSTIBLE") || modNorm.contains("FUEL") -> "Combustible"
        modNorm.contains("CONSUMIBLE") -> resolverModuloInteligente(item, ref, categoria).ifBlank { "Consumibles" }
        else -> resolverModuloInteligente(item, ref, categoria)
    }
}

internal fun MainActivity.resolverModuloInteligente(item: String, ref: String, categoria: String): String {
    val catNorm = normalizarBusqueda(categoria).uppercase(Locale.getDefault())
    val texto = "$item $ref".uppercase(Locale.getDefault())

    // 1. REGLA DE EXCLUSIÓN CRÍTICA: Si el texto contiene palabras de "Repuesto" o "Pieza",
    // NUNCA es Dotación, Combustible ni Químico, sino Consumibles (o Mecánica).
    // Se usa \b para evitar que "PARTE" (de dotación) coincida con "PART" (de repuesto).
    val esRepuestoOPieza = Regex("\\b(ARRANCADOR|MOTOR|FILTRO|FILTER|BOMBA|PUMP|REPUESTO|SPARE|PIEZA|PART|BUJIA|PLUG|CABLE|VALVULA|VALVE|MANGUERA|HOSE|ACOPLE|COUPLE|DISCO|DISC|KIT|EMPAQUE|PACKING|SELLO|SEAL|RODAMIENTO|BEARING|CHUMACERA|RETEN|RODILLO|COJINETE|THERMOSTAT|TERMOSTATO)\\b").containsMatchIn(texto)

    // 2. PRIORIDAD: Si la categoría ya indica un módulo, respetarlo estrictamente,
    // excepto repuestos mecánicos, que pertenecen a Consumibles.
    if (catNorm.contains("PROTECCION") || catNorm.contains("EXTREMIDADE") || catNorm.contains("EPP")) return "EPP"
    if (catNorm.contains("ASEO") || catNorm.contains("LIMPIEZA") || catNorm.contains("CAFETERIA")) return ModulosInventario.ASEO
    if (!esRepuestoOPieza && Regex("(LUBRICANTE|ACEITE|GRASA|HIDRAULICO|VALVULINA|REFRIGERANTE|LIQUIDO DE FRENOS|ADBLUE|DEF)").containsMatchIn(texto)) return ModulosInventario.LUBRICANTES_TALLER
    if (catNorm.contains("MECANICA") || catNorm.contains("RODAMIENTO") || catNorm.contains("REPUESTO") || esRepuestoOPieza) return "Consumibles"
    if (catNorm.contains("LUBRICANTE") || catNorm.contains("GRASA") || catNorm.contains("TALLER")) return ModulosInventario.LUBRICANTES_TALLER
    if (catNorm.contains("FERTILIZANTE") || catNorm.contains("FUNGICIDA") || catNorm.contains("INSECTICIDA") || catNorm.contains("QUIMICO")) return ModulosInventario.AGROQUIMICOS
    if (catNorm.contains("PARTE") || catNorm.contains("CONJUNTO") || catNorm.contains("CALZADO") || catNorm.contains("DOTACION")) return "Dotación"
    if (catNorm.contains("FERTILIZANTE") || catNorm.contains("FUNGICIDA") || catNorm.contains("INSECTICIDA") || catNorm.contains("QUIMICO")) return "Químico"
    if (catNorm.contains("COMBUSTIBLE")) return "Combustible"
    if (Regex("(JABON|DETERGENTE|CLOROX|DESINFECTANTE|DESENGRASANTE|QUITAGRASA|FABULOSO|TRAPERO|ESCOBA|PAPEL HIGIENICO|COCINA|ASEO)").containsMatchIn(texto)) return ModulosInventario.ASEO
    if (!esRepuestoOPieza && Regex("(LUBRICANTE|ACEITE|GRASA|HIDRAULICO|VALVULINA|REFRIGERANTE|LIQUIDO DE FRENOS|ADBLUE|DEF)").containsMatchIn(texto)) return ModulosInventario.LUBRICANTES_TALLER
    if (!esRepuestoOPieza && (catNorm.contains("QUIMICO") || Regex("(FERTILIZANTE|FERTILIZER|FUNGICIDA|FUNGICIDE|HERBICIDA|HERBICIDE|INSECTICIDA|PAGUICIDA|ABONO|UREA|KCL|DAP|SULFAMON|YARAMILA)").containsMatchIn(texto))) return ModulosInventario.AGROQUIMICOS

    return when {
        // 3. EPP (Protección Personal) - Bilingüe y relajado (sin \b para captar plurales)
        catNorm.contains("EPP") || 
        Regex("(GUANTE|GLOVE|GAFA|GLASSES|CASCO|HELMET|CARETA|MASK|MASCARA|RESPIRADOR|RESPIRATOR|AUDITIVO|HEARING|EARPLUG|COFIA|VAQUETA|NITRILO|POLAINA|CANILLERA|LENTE|LENS|VISUAL|TAPABOCA)").containsMatchIn(texto) -> "EPP"
        
        // 4. DOTACIÓN (Vestimenta/Uniforme) - Bilingüe y estricto
        catNorm.contains("DOTACION") || 
        Regex("(BOTA|BOOT|OVEROL|OVERALL|IMPERMEABLE|RAINCOAT|DELANTAL|APRON|MANGA|SLEEVE|CAMISA|SHIRT|PANTALON|PANTS|ZAPATO|SHOE|CHAQUETA|JACKET|SOMBRERO|HAT|GORRA|CAP|UNIFORME|UNIFORM|JEAN|VESTUARIO|ZAPATILLA)").containsMatchIn(texto) -> "Dotación"
        
        // 5. QUÍMICO (Agroquímicos) - Evitar que repuestos de bombas caigan aquí
        !esRepuestoOPieza && (catNorm.contains("QUIMICO") || Regex("(FERTILIZANTE|FERTILIZER|FUNGICIDA|FUNGICIDE|HERBICIDA|HERBICIDE|INSECTICIDA|PAGUICIDA|ABONO|UREA|KCL|DAP|SULFAMON|YARAMILA)").containsMatchIn(texto)) -> "Químico"
        
        // 6. COMBUSTIBLE (Líquidos de energía) - MUY ESTRICTO
        !esRepuestoOPieza && (catNorm.contains("COMBUSTIBLE") || catNorm.contains("FUEL") || 
        Regex("(GASOLINA|GASOLINE|ACPM|UREA|DIESEL|ADBLUE|DEF|PETROLEO|OIL|GASOIL|BIODIESEL)").containsMatchIn(texto)) -> "Combustible"
        
        // 7. DEFAULT: Todo lo demás (incluyendo repuestos mecánicos) va a Consumibles
        else -> "Consumibles"
    }
}

internal fun MainActivity.confirmarReorganizacionInventario() {
    AlertDialog.Builder(this)
        .setTitle("Reorganización masiva")
        .setMessage("Esta herramienta realizará las siguientes acciones en Firestore:\n\n" +
                "1. UNIFICAR DUPLICADOS: Si varios productos tienen el mismo código, sumará sus cantidades en un solo registro.\n" +
                "2. RECLASIFICAR: Moverá cada producto a su categoría correcta según su nombre.\n" +
                "3. LIMPIAR: Borrará registros basura o inconsistentes.\n\n" +
                "¿Deseas iniciar la reorganización?")
        .setPositiveButton("Sí, reorganizar base de datos") { _, _ -> ejecutarReorganizacionInventario() }
        .setNegativeButton("Cancelar", null)
        .show()
}

internal fun MainActivity.ejecutarReorganizacionInventario() {
    val loading = Toast.makeText(this, "Iniciando reorganización profunda y purga de caché...", Toast.LENGTH_LONG)
    loading.show()

    // 1. PURGA DE CACHÉ LOCAL: Obligar a la app a olvidar nombres viejos
    db.borrarMemoria("catalogo_completo")
    android.util.Log.d("ArlesGestión", "REORGANIZACIÓN: Caché local eliminada.")

    var totalUnificados = 0
    var totalReclasificados = 0

    fun procesarColeccion(nombreColeccion: String, onDone: () -> Unit) {
        firestore.collection(nombreColeccion).get().addOnSuccessListener { snapshot ->
            val docs = snapshot.documents
            val grupos = docs.groupBy { 
                val cod = it.getString("codigo_interno") ?: it.id
                normalizarCodigoInterno(cod)
            }

            // Ejecución por batches reales de 500
            val batchChunks = grupos.values.toList().chunked(100) 
            var chunksProcesados = 0

            if (batchChunks.isEmpty()) { onDone(); return@addOnSuccessListener }

            fun procesarSiguienteChunk() {
                if (chunksProcesados >= batchChunks.size) {
                    onDone()
                    return
                }

                val batch = firestore.batch()
                val actualChunk = batchChunks[chunksProcesados]
                
                actualChunk.forEach { listaDocs ->
                    // ORDENAR POR LONGITUD DE NOMBRE: El que tenga marca (Challenger) suele ser más largo
                    val listaOrdenada = listaDocs.sortedByDescending { 
                        (it.getString("item").orEmpty() + it.getString("referencia").orEmpty()).length 
                    }
                    
                    val maestro = listaOrdenada.first()
                    val itemBase = nombreItemExistencia(maestro)
                    val refBase = referenciaCatalogoExistencia(maestro)
                    val catActual = maestro.getString("categoria").orEmpty()
                    val modActual = maestro.getString("modulo").orEmpty()
                    
                    val catCorrecta = resolverCategoriaInteligente(itemBase, refBase, catActual)
                    val modCorrecto = resolverModuloInteligente(itemBase, refBase, catCorrecta)
                    
                    var stockTotal = 0.0
                    listaDocs.forEach { stockTotal += it.getDouble("cantidad") ?: 0.0 }
                    
                    batch.set(maestro.reference, mapOf(
                        "cantidad" to stockTotal,
                        "categoria" to catCorrecta,
                        "modulo" to modCorrecto,
                        "ultima_fecha" to now(),
                        "ultimo_solicitante" to "Reorganizador IA"
                    ), com.google.firebase.firestore.SetOptions.merge())

                    // Borrar el resto (los que tenían nombres cortos o incompletos)
                    listaOrdenada.drop(1).forEach { docBorrar -> 
                        batch.delete(docBorrar.reference) 
                        totalUnificados++
                    }
                    
                    // BARRIDO EXTRA: Buscar si hay otros documentos con nombres similares que NO tienen el mismo código
                    // (Esta lógica se aplica al grupo actual por simplicidad técnica)
                    
                    if (catCorrecta != catActual) totalReclasificados++
                }

                batch.commit().addOnCompleteListener {
                    chunksProcesados++
                    procesarSiguienteChunk()
                }
            }

            procesarSiguienteChunk()
        }.addOnFailureListener { onDone() }
    }

    procesarColeccion("existencias") {
        loading.cancel()
        registrarCambioLocal("REORGANIZACION_INVENTARIO", "Sistema", "", "Unificación y reclasificación", "Varios", "Unificados: $totalUnificados, Reclasificados: $totalReclasificados")
        
        AlertDialog.Builder(this)
            .setTitle("Reorganización Exitosa")
            .setMessage("Se han procesado los productos en la nube:\n\n" +
                    "• Duplicados unificados: $totalUnificados\n" +
                    "• Productos reclasificados: $totalReclasificados\n\n" +
                    "El inventario ahora está ordenado.")
            .setPositiveButton("Aceptar") { _, _ ->
                sincronizarCatalogo()
                showPanelAuditoria()
            }
            .show()
    }
}

internal fun MainActivity.handleBackPress() {
    if (aiDialog?.isShowing == true) {
        aiDialog?.dismiss()
        return
    }
    val action = currentScreenBackAction
    if (action != null) {
        action()
    } else {
        finish()
    }
}
