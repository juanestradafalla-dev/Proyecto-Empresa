package com.arlessas.gestion

internal object AppMode {
    val incluyeTaller: Boolean
        get() = BuildConfig.INCLUDE_TALLER

    val esTallerIndependiente: Boolean
        get() = BuildConfig.TALLER_STANDALONE

    fun modulosInventarioNube(): List<String> {
        return if (esTallerIndependiente) {
            listOf(ModulosInventario.LUBRICANTES_TALLER)
        } else {
            mutableListOf(
                "Consumibles",
                ModulosInventario.AGROQUIMICOS,
                ModulosInventario.ASEO,
                "EPP",
                "Dotación",
                "Combustible",
            ).apply {
                if (incluyeTaller) add(3, ModulosInventario.LUBRICANTES_TALLER)
            }
        }
    }

    fun modulosMovimientosNube(): List<String> {
        return if (esTallerIndependiente) {
            listOf(TallerCanonicos.MODULO, ModulosInventario.LUBRICANTES_TALLER)
        } else {
            mutableListOf(
                "Consumibles",
                ModulosInventario.AGROQUIMICOS,
                ModulosInventario.ASEO,
                "EPP",
                "Dotación",
                "Combustible",
            ).apply {
                if (incluyeTaller) {
                    add(3, TallerCanonicos.MODULO)
                    add(4, ModulosInventario.LUBRICANTES_TALLER)
                }
            }
        }
    }
}
