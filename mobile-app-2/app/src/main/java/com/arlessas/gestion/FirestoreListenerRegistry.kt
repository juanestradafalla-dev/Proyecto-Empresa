package com.arlessas.gestion

import com.google.firebase.firestore.ListenerRegistration

/**
 * Evita fugas de memoria por listeners en tiempo real que quedan vivos al salir
 * de una pantalla. Cada pantalla nueva reemplaza los listeners activos.
 */
class FirestoreListenerRegistry {
    private val activeListeners = mutableListOf<ListenerRegistration>()

    fun add(registration: ListenerRegistration) {
        activeListeners.add(registration)
    }

    fun remove(registration: ListenerRegistration?) {
        if (registration == null) return
        runCatching { registration.remove() }
        activeListeners.remove(registration)
    }

    fun clear() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }
}
