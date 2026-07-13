package com.arlessas.gestion

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

internal const val CLASE_MOVIMIENTO_ENTRADA_STOCK = "entrada_stock"
internal const val PENDIENTE_ENTRADA_EXISTENCIA = "operacion_entrada_stock_existencia"
internal const val PENDIENTE_ENTRADA_HERRAMIENTA = "operacion_entrada_stock_herramienta"

internal fun datosTrazabilidadEntradaStock(
    documentoId: String,
    productoId: String = documentoId,
    stockAnterior: Double,
    stockNuevo: Double,
): Map<String, Any?> = mapOf(
    "clase_movimiento" to CLASE_MOVIMIENTO_ENTRADA_STOCK,
    "stock_anterior" to stockAnterior,
    "stock_nuevo" to stockNuevo,
    "stock_actualizado" to true,
    "documento_id" to documentoId,
    "producto_id" to productoId.ifBlank { documentoId },
    "creado_en" to FieldValue.serverTimestamp(),
)

internal fun esMovimientoEntradaStockTrazable(movimiento: DocumentSnapshot): Boolean {
    val tipo = normalizarBusqueda(
        movimiento.getString("tipoMovimiento")
            ?: movimiento.getString("tipo")
            ?: "",
    )
    if (listOf("traslado", "retorno", "devolucion").any(tipo::contains)) return false

    val modulo = movimiento.getString("modulo").orEmpty()
    if (modulo.equals(TallerCanonicos.MODULO, ignoreCase = true)) {
        return tipo.contains("ingreso bodega") || tipo.contains("entrada stock")
    }
    return movimiento.getString("clase_movimiento") == CLASE_MOVIMIENTO_ENTRADA_STOCK ||
        tipo.contains("entrada") || tipo.contains("ingreso")
}

private fun mapaPendiente(valor: Any?): Map<String, Any?> = (valor as? Map<*, *>)
    ?.entries
    ?.associate { it.key.toString() to it.value }
    .orEmpty()

private fun numeroPendiente(valor: Any?): Double = when (valor) {
    is Number -> valor.toDouble()
    is String -> valor.replace(',', '.').toDoubleOrNull() ?: 0.0
    else -> 0.0
}

internal fun MainActivity.encolarEntradaExistenciaPendiente(
    documentoId: String,
    cantidad: Double,
    datosExistencia: Map<String, Any?>,
    datosMovimiento: Map<String, Any?>,
    datosCatalogo: Map<String, Any?> = emptyMap(),
) {
    db.insertarPendienteSync(PENDIENTE_ENTRADA_EXISTENCIA, mapOf(
        "documento_id" to documentoId,
        "movimiento_id" to firestore.collection("movimientos").document().id,
        "cantidad_entrada" to cantidad,
        "datos_existencia" to datosExistencia,
        "datos_movimiento" to datosMovimiento,
        "datos_catalogo" to datosCatalogo,
    ))
}

internal fun MainActivity.sincronizarEntradaExistenciaPendiente(payload: Map<String, Any?>): Task<Unit> {
    val documentoId = payload["documento_id"]?.toString().orEmpty()
    val movimientoId = payload["movimiento_id"]?.toString().orEmpty()
    val cantidad = numeroPendiente(payload["cantidad_entrada"])
    val datosExistencia = mapaPendiente(payload["datos_existencia"])
    val datosMovimiento = mapaPendiente(payload["datos_movimiento"])
    val datosCatalogo = mapaPendiente(payload["datos_catalogo"])
    require(documentoId.isNotBlank()) { "Entrada pendiente sin documento de existencia" }
    require(cantidad > 0.0) { "Entrada pendiente con cantidad invalida" }

    val referencia = firestore.collection("existencias").document(documentoId)
    val referenciaMovimiento = firestore.collection("movimientos")
        .document(movimientoId.ifBlank { firestore.collection("movimientos").document().id })
    return firestore.runTransaction { transaction ->
        if (transaction.get(referenciaMovimiento).exists()) return@runTransaction Unit
        val snapshot = transaction.get(referencia)
        val stockAnterior = if (snapshot.exists()) numeroDocumento(snapshot, "cantidad", "stock_actual") else 0.0
        val stockNuevo = stockAnterior + cantidad
        transaction.set(referencia, datosExistencia + mapOf(
            "cantidad" to stockNuevo,
            "stock_actual" to stockNuevo,
        ), SetOptions.merge())
        if (datosCatalogo.isNotEmpty()) {
            transaction.set(
                firestore.collection("catalogo_personalizado").document(documentoId),
                datosCatalogo,
                SetOptions.merge(),
            )
        }
        transaction.set(
            referenciaMovimiento,
            datosMovimiento + datosTrazabilidadEntradaStock(documentoId, documentoId, stockAnterior, stockNuevo),
        )
        Unit
    }
}

internal fun MainActivity.guardarEntradaHerramientaAtomica(
    documentoId: String,
    datosHerramienta: Map<String, Any?>,
    datosMovimiento: Map<String, Any?>,
    stockNuevo: Double,
    registrarSinIncremento: Boolean = false,
    movimientoId: String = "",
): Task<Unit> {
    val referencia = firestore.collection("herramientas").document(documentoId)
    val referenciaMovimiento = firestore.collection("movimientos")
        .document(movimientoId.ifBlank { firestore.collection("movimientos").document().id })
    return firestore.runTransaction { transaction ->
        if (movimientoId.isNotBlank() && transaction.get(referenciaMovimiento).exists()) {
            return@runTransaction Unit
        }
        val snapshot = transaction.get(referencia)
        val stockAnterior = if (snapshot.exists()) {
            numeroDocumento(snapshot, "cantidad_total", "stock_total", "cantidad")
        } else {
            0.0
        }
        transaction.set(referencia, datosHerramienta, SetOptions.merge())
        val incremento = stockNuevo - stockAnterior
        if (incremento > 0.0 || registrarSinIncremento) {
            val movimiento = datosMovimiento.toMutableMap().apply {
                if (incremento > 0.0) {
                    put("cantidad", incremento)
                    putAll(datosTrazabilidadEntradaStock(documentoId, documentoId, stockAnterior, stockNuevo))
                }
            }
            transaction.set(referenciaMovimiento, movimiento)
        }
        Unit
    }
}

internal fun MainActivity.encolarEntradaHerramientaPendiente(
    documentoId: String,
    datosHerramienta: Map<String, Any?>,
    datosMovimiento: Map<String, Any?>,
    stockNuevo: Double,
    registrarSinIncremento: Boolean = false,
) {
    db.insertarPendienteSync(PENDIENTE_ENTRADA_HERRAMIENTA, mapOf(
        "documento_id" to documentoId,
        "movimiento_id" to firestore.collection("movimientos").document().id,
        "datos_herramienta" to datosHerramienta,
        "datos_movimiento" to datosMovimiento,
        "stock_nuevo" to stockNuevo,
        "registrar_sin_incremento" to registrarSinIncremento,
    ))
}

internal fun MainActivity.sincronizarEntradaHerramientaPendiente(payload: Map<String, Any?>): Task<Unit> {
    val documentoId = payload["documento_id"]?.toString().orEmpty()
    val movimientoId = payload["movimiento_id"]?.toString().orEmpty()
    val datosHerramienta = mapaPendiente(payload["datos_herramienta"])
    val datosMovimiento = mapaPendiente(payload["datos_movimiento"])
    val stockNuevo = numeroPendiente(payload["stock_nuevo"])
    val registrarSinIncremento = payload["registrar_sin_incremento"] as? Boolean ?: false
    require(documentoId.isNotBlank()) { "Entrada pendiente sin documento de herramienta" }
    return guardarEntradaHerramientaAtomica(
        documentoId,
        datosHerramienta,
        datosMovimiento,
        stockNuevo,
        registrarSinIncremento,
        movimientoId,
    )
}
