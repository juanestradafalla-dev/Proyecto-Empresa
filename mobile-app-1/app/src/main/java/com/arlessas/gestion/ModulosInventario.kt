package com.arlessas.gestion

internal object ModulosInventario {
    const val QUIMICO_LEGACY = "Qu\u00edmico"
    const val AGROQUIMICOS = "Agroqu\u00edmicos"
    const val LUBRICANTES_TALLER = "Lubricantes taller"
    const val ASEO = "ASEO"

    private val categoriasTaller = setOf(
        "Lubricantes y fluidos",
        "Taller y mantenimiento",
        "Limpieza y desinfecci\u00f3n",
        "Laboratorio"
    )

    private fun clave(valor: String): String = normalizarBusqueda(valor).replace(" ", "")

    fun esTallerQuimico(categoria: String, ubicacion: String = ""): Boolean {
        val cat = normalizarBusqueda(categoria)
        val ubi = normalizarBusqueda(ubicacion)
        return categoriasTaller.any { normalizarBusqueda(it) == cat } ||
            ubi.contains("caseta de lubricantes") ||
            ubi.contains("taller")
    }

    fun moduloQuimico(categoria: String, ubicacion: String = ""): String {
        return if (esTallerQuimico(categoria, ubicacion)) LUBRICANTES_TALLER else AGROQUIMICOS
    }

    fun esModuloAgroquimico(modulo: String): Boolean {
        val mod = clave(modulo)
        return mod == clave(AGROQUIMICOS) || mod == clave(QUIMICO_LEGACY) || mod.contains("agroquimico")
    }

    fun esModuloLubricantesTaller(modulo: String): Boolean {
        val mod = clave(modulo)
        return mod == clave(LUBRICANTES_TALLER) ||
            (mod.contains("lubricante") && mod.contains("taller"))
    }

    fun esModuloQuimicoOperativo(modulo: String): Boolean {
        return esModuloAgroquimico(modulo) || esModuloLubricantesTaller(modulo)
    }

    fun modulosCompatibles(registrado: String, esperado: String): Boolean {
        val reg = clave(registrado)
        val esp = clave(esperado)
        if (reg.isBlank() || esp.isBlank()) return true
        if (reg == esp) return true
        if (reg == clave(QUIMICO_LEGACY) && esModuloQuimicoOperativo(esperado)) return true
        if (esp == clave(QUIMICO_LEGACY) && esModuloQuimicoOperativo(registrado)) return true
        return false
    }
}
