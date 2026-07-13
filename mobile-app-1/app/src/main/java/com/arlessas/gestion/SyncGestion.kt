package com.arlessas.gestion

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestión de sincronización offline.
 * Permite encolar registros cuando no hay internet y subirlos automáticamente después.
 */

private val isSyncing = AtomicBoolean(false)

private fun esUriLocalEvidencia(valor: String): Boolean {
    return valor.startsWith("content:", ignoreCase = true) || valor.startsWith("file:", ignoreCase = true)
}

private fun normalizarIdDocumento(valor: String): String {
    val normalizado = Normalizer.normalize(valor.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalizado
}

private fun documentoHerramientaPendiente(data: Map<String, Any?>): String {
    val clave = data["clave"]?.toString().orEmpty()
    if (clave.isNotBlank()) return normalizarIdDocumento(clave)

    val codigo = data["codigo"]?.toString().orEmpty()
    val base = codigo.ifBlank {
        listOf(
            data["nombre"]?.toString().orEmpty(),
            data["marca"]?.toString().orEmpty(),
            data["referencia"]?.toString().orEmpty()
        ).filter { it.isNotBlank() }.joinToString("-")
    }
    return normalizarIdDocumento(base).ifBlank { "herramienta-${System.currentTimeMillis()}" }
}

private fun documentoCatalogoPendiente(data: Map<String, Any?>): String {
    return normalizarIdDocumento(
        data["codigo_interno"]?.toString().orEmpty()
            .ifBlank { data["codigoInterno"]?.toString().orEmpty() }
            .ifBlank { data["codigo"]?.toString().orEmpty() }
            .ifBlank { data["documentoId"]?.toString().orEmpty() }
    )
}

internal fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

internal fun MainActivity.encolarMovimientoOffline(
    mov: Movimiento, 
    cantidadNumerica: Double, 
    itemBase: String, 
    fotoLocalUri: String = "",
) {
    val data = mutableMapOf<String, Any?>(
        "fecha" to mov.fecha,
        "modulo" to mov.modulo,
        "tipoMovimiento" to mov.tipoMovimiento,
        "item" to mov.item,
        "referencia" to mov.referencia,
        "cantidad" to mov.cantidad,
        "unidad" to mov.unidad,
        "solicitante" to mov.solicitante,
        "labor" to mov.labor,
        "maquinaria" to mov.maquinaria,
        "horometro" to mov.horometro,
        "observaciones" to mov.observaciones,
        "usuario" to (auth.currentUser?.uid ?: ""),
        "itemBase" to itemBase,
        "cantidadNumerica" to cantidadNumerica,
        "fotoLocalUri" to fotoLocalUri,
        "offline_sync" to true,
        "client_timestamp" to now(),
    )
    
    db.insertarPendienteSync("movimientos", data)
    Toast.makeText(this, "Sin conexión. Registro guardado localmente.", Toast.LENGTH_LONG).show()
}

internal fun MainActivity.intentarSincronizarPendientes() {
    val start = android.os.SystemClock.elapsedRealtime()
    android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes inicio")
    if (!isNetworkAvailable()) {
        android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes fin ${android.os.SystemClock.elapsedRealtime() - start}ms omitida=sin_red")
        return
    }
    if (!isSyncing.compareAndSet(false, true)) {
        android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes fin ${android.os.SystemClock.elapsedRealtime() - start}ms omitida=en_curso")
        return
    }
    pendingSyncRetryScheduled = false
    
    val pendientes = db.obtenerPendientesSync(10) // Procesar de 10 en 10
    if (pendientes.isEmpty()) {
        isSyncing.set(false)
        android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes fin ${android.os.SystemClock.elapsedRealtime() - start}ms pendientes=0")
        return
    }
    
    Thread {
        try {
            pendientes.forEach { pendiente ->
                try {
                    val type = object : TypeToken<Map<String, Any?>>() {}.type
                    val data: MutableMap<String, Any?> = Gson().fromJson(pendiente.payloadJson, type)
                    val coleccion = pendiente.coleccion.trim().ifBlank { "movimientos" }

                    val fotoUrlActual = data["fotoUrl"]?.toString().orEmpty()
                    val fotoLocal = data["fotoLocalUri"]?.toString().orEmpty()
                        .ifBlank { fotoUrlActual.takeIf { esUriLocalEvidencia(it) }.orEmpty() }
                    var cloudUrl = ""

                    // 1. Si hay foto local, subirla primero
                    if (fotoLocal.isNotBlank()) {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        subirEvidenciaCloud(fotoLocal, data["modulo"]?.toString() ?: "varios") { url ->
                            cloudUrl = url
                            latch.countDown()
                        }
                        latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                        if (cloudUrl.isBlank()) {
                            val errorFoto = "Timeout o fallo subiendo evidencia local (60s)"
                            db.actualizarPendienteSyncError(pendiente.id, errorFoto)
                            android.util.Log.e("Sync", "$errorFoto del pendiente ${pendiente.id}")
                            return@forEach
                        }
                    }

                    // 2. Registrar en Firestore con reintentos controlados
                    val payloadFinal = data.toMutableMap()
                    payloadFinal.remove("fotoLocalUri")
                    payloadFinal.remove("itemBase")
                    payloadFinal.remove("cantidadNumerica")
                    if (cloudUrl.isNotBlank()) payloadFinal["fotoUrl"] = cloudUrl

                    val latchFire = java.util.concurrent.CountDownLatch(1)
                    var exito = false
                    var errorMsg = ""

                    val task = when (coleccion) {
                        PENDIENTE_ENTRADA_EXISTENCIA -> sincronizarEntradaExistenciaPendiente(payloadFinal)
                        PENDIENTE_ENTRADA_HERRAMIENTA -> sincronizarEntradaHerramientaPendiente(payloadFinal)
                        "herramientas" -> {
                            firestore.collection(coleccion)
                                .document(documentoHerramientaPendiente(payloadFinal))
                                .set(payloadFinal, SetOptions.merge())
                        }
                        "existencias", "catalogo_personalizado" -> {
                            val docId = documentoCatalogoPendiente(payloadFinal)
                            if (docId.isNotBlank()) {
                                firestore.collection(coleccion).document(docId).set(payloadFinal, SetOptions.merge())
                            } else {
                                firestore.collection(coleccion).add(payloadFinal)
                            }
                        }
                        else -> firestore.collection(coleccion).add(payloadFinal)
                    }

                    task
                        .addOnSuccessListener {
                            exito = true
                            latchFire.countDown()
                        }
                        .addOnFailureListener { e ->
                            errorMsg = e.localizedMessage ?: "Error desconocido"
                            latchFire.countDown()
                        }

                    latchFire.await(20, java.util.concurrent.TimeUnit.SECONDS)

                    if (exito) {
                        db.eliminarPendienteSync(pendiente.id)
                        android.util.Log.d("Sync", "Sincronizado registro ${pendiente.id} en $coleccion")
                    } else {
                        db.actualizarPendienteSyncError(pendiente.id, errorMsg)
                        android.util.Log.e("Sync", "Fallo al subir ${pendiente.id}: $errorMsg")
                    }
                } catch (e: Exception) {
                    db.actualizarPendienteSyncError(pendiente.id, e.localizedMessage ?: "Error sincronizando pendiente")
                    android.util.Log.e("Sync", "Error sincronizando ${pendiente.id}: ${e.message}")
                }
            }
        } finally {
            isSyncing.set(false)
            android.util.Log.d(
                "PerfPrincipal",
                "intentarSincronizarPendientes fin ${android.os.SystemClock.elapsedRealtime() - start}ms lote=${pendientes.size}"
            )
        }
        
        // Si quedan mÃ¡s, reintentar despuÃ©s de un pequeÃ±o respiro
        if (db.contarPendientesSync() > 0) {
            runOnUiThread {
                if (pendingSyncRetryScheduled) {
                    android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes reintento omitido=ya_programado")
                    return@runOnUiThread
                }
                pendingSyncRetryScheduled = true
                android.util.Log.d("PerfPrincipal", "intentarSincronizarPendientes reintento programado 15000ms")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    pendingSyncRetryScheduled = false
                    if (pantallaActiva()) intentarSincronizarPendientes()
                }, 15_000L)
            }
        }
    }.start()
}
