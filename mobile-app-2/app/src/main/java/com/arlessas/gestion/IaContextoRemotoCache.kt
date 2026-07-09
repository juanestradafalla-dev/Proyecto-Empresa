package com.arlessas.gestion

internal data class IaContextoRemotoCache(
    val stockInfo: String,
    val stockContextMode: String,
    val historial: String,
    val reglasAprendidas: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    fun vigente(ttlMs: Long = 120_000L): Boolean = System.currentTimeMillis() - timestampMs < ttlMs
}