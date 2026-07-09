package com.arlessas.gestion

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit

enum class PerformanceMode {
    LOW,
    NORMAL,
    HIGH,
}

/**
 * Configuración centralizada de rendimiento para equipos de gama baja, media y alta.
 */
class AppPerformanceConfig(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentMode(): PerformanceMode {
        val stored = prefs.getString(KEY_MODE, null)
        if (stored != null) {
            return runCatching { PerformanceMode.valueOf(stored.uppercase()) }.getOrDefault(PerformanceMode.NORMAL)
        }
        if (prefs.contains(KEY_LOW_MODE_LEGACY)) {
            val legacyLow = prefs.getBoolean(KEY_LOW_MODE_LEGACY, false)
            val migrated = if (legacyLow) PerformanceMode.LOW else PerformanceMode.NORMAL
            persistMode(migrated)
            prefs.edit { remove(KEY_LOW_MODE_LEGACY) }
            return migrated
        }
        val recommended = when {
            shouldRecommendLowPerformanceMode() -> PerformanceMode.LOW
            shouldRecommendHighPerformanceMode() -> PerformanceMode.HIGH
            else -> PerformanceMode.NORMAL
        }
        persistMode(recommended)
        return recommended
    }

    fun isLowPerformanceMode(): Boolean = currentMode() == PerformanceMode.LOW

    fun isHighPerformanceMode(): Boolean = currentMode() == PerformanceMode.HIGH

    fun setLowPerformanceMode(enabled: Boolean) {
        setPerformanceMode(if (enabled) PerformanceMode.LOW else PerformanceMode.NORMAL)
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        persistMode(mode)
    }

    fun toggleLowPerformanceMode() {
        setPerformanceMode(if (isLowPerformanceMode()) PerformanceMode.NORMAL else PerformanceMode.LOW)
    }

    fun toggleHighPerformanceMode() {
        setPerformanceMode(if (isHighPerformanceMode()) PerformanceMode.NORMAL else PerformanceMode.HIGH)
    }

    fun shouldRecommendLowPerformanceMode(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        val ramGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        val lowRamBySystem = activityManager?.isLowRamDevice == true
        return lowRamBySystem || ramGb <= 3.25
    }

    fun shouldRecommendHighPerformanceMode(): Boolean {
        if (shouldRecommendLowPerformanceMode()) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        val ramGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        return ramGb >= 6.0
    }

    fun inventoryQueryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 350L
        PerformanceMode.NORMAL -> 1000L
        PerformanceMode.HIGH -> 2500L
    }

    fun inventoryRenderLimit(): Int = when (currentMode()) {
        PerformanceMode.LOW -> 90
        PerformanceMode.NORMAL -> 250
        PerformanceMode.HIGH -> 450
    }

    fun movementQueryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 120L
        PerformanceMode.NORMAL -> 350L
        PerformanceMode.HIGH -> 800L
    }

    fun movementRenderLimit(): Int = when (currentMode()) {
        PerformanceMode.LOW -> 80
        PerformanceMode.NORMAL -> 180
        PerformanceMode.HIGH -> 350
    }

    @Suppress("unused")
    fun localCardLimit(): Int = when (currentMode()) {
        PerformanceMode.LOW -> 80
        PerformanceMode.NORMAL -> 250
        PerformanceMode.HIGH -> 500
    }

    fun aiInventoryQueryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 60L
        PerformanceMode.NORMAL -> 120L
        PerformanceMode.HIGH -> 220L
    }

    fun aiStockLimit(): Int = when (currentMode()) {
        PerformanceMode.LOW -> 12
        PerformanceMode.NORMAL -> 18
        PerformanceMode.HIGH -> 32
    }

    fun aiHistoryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 5L
        PerformanceMode.NORMAL -> 8L
        PerformanceMode.HIGH -> 16L
    }

    fun aiMemoryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 5L
        PerformanceMode.NORMAL -> 8L
        PerformanceMode.HIGH -> 16L
    }

    fun aiContextTimeoutMs(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 3500L
        PerformanceMode.NORMAL -> 4500L
        PerformanceMode.HIGH -> 6500L
    }

    fun aiCatalogCandidateLimit(): Int = when (currentMode()) {
        PerformanceMode.LOW -> 120
        PerformanceMode.NORMAL -> 250
        PerformanceMode.HIGH -> 420
    }

    fun toolsQueryLimit(): Long = when (currentMode()) {
        PerformanceMode.LOW -> 90L
        PerformanceMode.NORMAL -> 150L
        PerformanceMode.HIGH -> 320L
    }

    fun animationsEnabled(): Boolean = currentMode() != PerformanceMode.LOW

    fun enhancedAnimationsEnabled(): Boolean = currentMode() == PerformanceMode.HIGH

    fun modeLabel(): String = when (currentMode()) {
        PerformanceMode.LOW -> "Bajo rendimiento"
        PerformanceMode.NORMAL -> "Normal"
        PerformanceMode.HIGH -> "Alto rendimiento"
    }

    fun modeDescription(): String = when (currentMode()) {
        PerformanceMode.LOW -> "Menos datos en pantalla y sin animaciones para máxima fluidez."
        PerformanceMode.NORMAL -> "Equilibrio entre velocidad y experiencia visual."
        PerformanceMode.HIGH -> "Más registros visibles, transiciones suaves y menú animado."
    }

    private fun persistMode(mode: PerformanceMode) {
        prefs.edit {
            putString(KEY_MODE, mode.name.lowercase())
            putBoolean(KEY_LOW_MODE_LEGACY, mode == PerformanceMode.LOW)
        }
    }

    companion object {
        private const val PREFS_NAME = "gestion_config"
        private const val KEY_MODE = "performance_mode"
        private const val KEY_LOW_MODE_LEGACY = "modo_bajo_rendimiento"
    }
}