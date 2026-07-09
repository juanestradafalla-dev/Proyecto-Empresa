package com.arlessas.gestion

import com.google.firebase.firestore.ListenerRegistration

/**
 * Evita fugas de memoria por listeners en tiempo real que quedan vivos al salir
 * de una pantalla. Cada pantalla nueva reemplaza los listeners activos.
 */
class FirestoreListenerRegistry {
    private val activeListeners = linkedMapOf<String, ListenerRegistration>()
    private var anonymousCounter = 0

    fun add(registration: ListenerRegistration) {
        add("listener-${++anonymousCounter}", registration)
    }

    fun add(key: String, registration: ListenerRegistration) {
        val existingKey = activeListeners.entries.firstOrNull { it.value === registration }?.key
        if (existingKey != null && existingKey != key) {
            activeListeners.remove(existingKey)
        }

        val previous = activeListeners.put(key, registration)
        if (previous != null && previous !== registration) {
            runCatching { previous.remove() }
            android.util.Log.d("PerfPrincipal", "listener limpiado key=$key motivo=reemplazo")
        }
        android.util.Log.d("PerfPrincipal", "listener creado key=$key activos=${activeListeners.size}")
    }

    fun remove(registration: ListenerRegistration?) {
        if (registration == null) return
        val key = activeListeners.entries.firstOrNull { it.value === registration }?.key
        if (key != null) {
            activeListeners.remove(key)
        }
        runCatching { registration.remove() }
        android.util.Log.d("PerfPrincipal", "listener limpiado key=${key ?: "directo"} activos=${activeListeners.size}")
    }

    fun remove(key: String) {
        val registration = activeListeners.remove(key) ?: return
        runCatching { registration.remove() }
        android.util.Log.d("PerfPrincipal", "listener limpiado key=$key activos=${activeListeners.size}")
    }

    fun clear() {
        if (activeListeners.isEmpty()) return
        val count = activeListeners.size
        val keys = activeListeners.keys.joinToString(",")
        activeListeners.values.forEach { runCatching { it.remove() } }
        activeListeners.clear()
        android.util.Log.d("PerfPrincipal", "listeners limpiados total=$count keys=$keys")
    }

    fun count(): Int = activeListeners.size
}
