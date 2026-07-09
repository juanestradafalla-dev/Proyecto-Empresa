package com.arlessas.gestion

object RenderLimiter {
    fun <T> applyLimit(items: List<T>, limit: Int): Pair<List<T>, Int> {
        if ((limit <= 0) || (items.size <= limit)) return items to 0
        return items.take(limit) to (items.size - limit)
    }
}
