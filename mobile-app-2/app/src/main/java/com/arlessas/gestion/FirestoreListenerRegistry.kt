package com.arlessas.gestion

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration

/**
 * Evita fugas de memoria por listeners en tiempo real que quedan vivos al salir
 * de una pantalla. Cada pantalla nueva reemplaza los listeners activos.
 */
class FirestoreListenerRegistry {
    private val activeListeners = linkedMapOf<String, ListenerRegistration>()

    fun add(registration: ListenerRegistration) {
        add("listener-${System.identityHashCode(registration)}", registration)
    }

    fun add(key: String, registration: ListenerRegistration) {
        activeListeners.remove(key)?.let { previous ->
            Log.d("PerfTaller", "listener reemplazado: $key")
            runCatching { previous.remove() }
        }
        activeListeners[key] = registration
        Log.d("PerfTaller", "listener creado: $key activos=${activeListeners.size}")
    }

    fun remove(registration: ListenerRegistration?) {
        if (registration == null) return
        val entry = activeListeners.entries.firstOrNull { it.value === registration }
        if (entry != null) {
            remove(entry.key)
        } else {
            runCatching { registration.remove() }
            Log.d("PerfTaller", "listener eliminado externo")
        }
    }

    fun remove(key: String) {
        val removed = activeListeners.remove(key) ?: return
        runCatching { removed.remove() }
        Log.d("PerfTaller", "listener eliminado: $key activos=${activeListeners.size}")
    }

    fun count(): Int = activeListeners.size

    fun clear() {
        if (activeListeners.isEmpty()) return
        Log.d("PerfTaller", "listeners clear: count=${activeListeners.size} keys=${activeListeners.keys.joinToString()}")
        activeListeners.values.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }
}
