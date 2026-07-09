package com.arlessas.gestion

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

data class UiScalePreset(
    val id: String,
    val label: String,
    val scale: Float,
)

object UiDisplayConfig {
    private const val PREFS_NAME = "gestion_config"
    private const val KEY_UI_SCALE = "ui_scale_factor"

    /** -1 = automática según tamaño de pantalla */
    private const val SCALE_AUTO = -1f

    val presets = listOf(
        UiScalePreset("normal", "Normal", 1.0f),
        UiScalePreset("compacta", "Compacta", 0.90f),
        UiScalePreset("pequena", "Pequeña", 0.82f),
        UiScalePreset("muy_pequena", "Muy pequeña", 0.74f),
    )

    fun getStoredScaleRaw(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_UI_SCALE, SCALE_AUTO)
    }

    fun isAutoScale(context: Context): Boolean {
        return getStoredScaleRaw(context) <= 0f
    }

    fun getScale(context: Context): Float {
        val stored = getStoredScaleRaw(context)
        if (stored > 0f) return stored.coerceIn(0.65f, 1.0f)
        return suggestDefaultScale(context)
    }

    fun setPreset(context: Context, presetId: String) {
        val preset = presets.firstOrNull { it.id == presetId } ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_UI_SCALE, preset.scale)
        }
    }

    fun setAutoScale(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_UI_SCALE, SCALE_AUTO)
        }
    }

    fun currentPresetId(context: Context): String? {
        if (isAutoScale(context)) return null
        val scale = getStoredScaleRaw(context)
        return presets.firstOrNull { kotlin.math.abs(it.scale - scale) < 0.02f }?.id
    }

    fun scaleLabel(context: Context): String {
        if (isAutoScale(context)) {
            val applied = getScale(context)
            return "Automática (${(applied * 100).toInt()}%)"
        }
        val preset = presets.firstOrNull { it.id == currentPresetId(context) }
        return preset?.label ?: "${(getScale(context) * 100).toInt()}%"
    }

    fun suggestDefaultScale(context: Context): Float {
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        return when {
            widthDp >= 430f || heightDp >= 900f -> 0.88f
            widthDp >= 400f || heightDp >= 850f -> 0.92f
            else -> 1.0f
        }
    }

    fun wrapContext(base: Context): Context {
        val scale = getScale(base)
        if (scale >= 0.999f) return base

        val config = Configuration(base.resources.configuration)
        config.fontScale = scale
        config.densityDpi = (config.densityDpi * scale).toInt().coerceAtLeast(120)
        return base.createConfigurationContext(config)
    }
}