package com.arlessas.gestion

import android.graphics.Color
import android.util.Log
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

private const val CONVERSACION_IA_LOG = "ConversacionIA"
private const val MAX_MENSAJES_LOCALES = 250
private const val MAX_MENSAJES_NUBE = 20
private const val MAX_MENSAJES_UI = 18

data class MensajeConversacionIA(
    val id: Long = 0,
    val usuarioId: String = "",
    val rol: String = "",
    val contenido: String = "",
    val fecha: String = "",
)

internal fun MainActivity.usuarioIAId(): String = auth.currentUser?.uid ?: "local"

internal fun MainActivity.guardarTurnoConversacionIA(rol: String, contenido: String) {
    val limpio = contenido.trim().take(2000)
    if (limpio.isBlank()) return
    val uid = usuarioIAId()
    try {
        db.guardarMensajeConversacionIA(uid, rol, limpio)
        db.podarConversacionIA(uid, MAX_MENSAJES_LOCALES)
        sincronizarMensajeConversacionNube(uid, rol, limpio)
    } catch (e: Exception) {
        Log.e(CONVERSACION_IA_LOG, "No se pudo guardar turno", e)
    }
}

internal fun MainActivity.sincronizarMensajeConversacionNube(uid: String, rol: String, contenido: String) {
    if (uid == "local" || auth.currentUser == null) return
    firestore.collection("conversacion_ia")
        .document(uid)
        .collection("mensajes")
        .add(
            mapOf(
                "rol" to rol,
                "texto" to contenido.take(1500),
                "fecha" to now(),
                "usuario" to (auth.currentUser?.email ?: ""),
            ),
        )
        .addOnFailureListener { e ->
            Log.w(CONVERSACION_IA_LOG, "Sync nube conversación: ${e.localizedMessage}")
        }
}

internal fun MainActivity.conversacionIAJsonParaNube(limite: Int = MAX_MENSAJES_NUBE): String {
    val mensajes = db.obtenerMensajesConversacionIA(usuarioIAId(), limite)
    val arr = JSONArray()
    mensajes.forEach { msg ->
        arr.put(
            JSONObject().apply {
                put("rol", msg.rol)
                put("texto", msg.contenido.take(500))
                put("fecha", msg.fecha)
            },
        )
    }
    return arr.toString()
}

internal fun MainActivity.restaurarConversacionIAEnChat(chatContainer: LinearLayout) {
    val mensajes = db.obtenerMensajesConversacionIA(usuarioIAId(), MAX_MENSAJES_UI)
    if (mensajes.isEmpty()) {
        sincronizarConversacionDesdeNube(chatContainer)
        return
    }
    mensajes.forEach { msg ->
        val vista = when (msg.rol) {
            "usuario" -> cardText("Tú: ${msg.contenido}").apply {
                setBackgroundColor(Color.rgb(240, 248, 255))
            }
            else -> cardText("Asistente: ${msg.contenido}")
        }
        agregarVistaChatSegura(chatContainer, vista)
    }
    (chatContainer.parent as? android.widget.ScrollView)?.post {
        (chatContainer.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
    }
}

internal fun MainActivity.sincronizarConversacionDesdeNube(chatContainer: LinearLayout) {
    val uid = usuarioIAId()
    if (uid == "local" || auth.currentUser == null) return
    if (db.obtenerMensajesConversacionIA(uid, 1).isNotEmpty()) return

    firestore.collection("conversacion_ia")
        .document(uid)
        .collection("mensajes")
        .orderBy("fecha")
        .limitToLast(MAX_MENSAJES_UI.toLong())
        .get()
        .addOnSuccessListener { snapshot ->
            if (!iaChatActivo(chatContainer) || snapshot.isEmpty) return@addOnSuccessListener
            snapshot.documents.forEach { doc ->
                val rol = doc.getString("rol").orEmpty().ifBlank { "asistente" }
                val texto = doc.getString("texto").orEmpty()
                if (texto.isBlank()) return@forEach
                db.guardarMensajeConversacionIA(uid, rol, texto)
                val vista = when (rol) {
                    "usuario" -> cardText("Tú: $texto").apply {
                        setBackgroundColor(Color.rgb(240, 248, 255))
                    }
                    else -> cardText("Asistente: $texto")
                }
                agregarVistaChatSegura(chatContainer, vista)
            }
            db.podarConversacionIA(uid, MAX_MENSAJES_LOCALES)
        }
        .addOnFailureListener { e ->
            Log.w(CONVERSACION_IA_LOG, "No se pudo restaurar desde nube: ${e.localizedMessage}")
        }
}