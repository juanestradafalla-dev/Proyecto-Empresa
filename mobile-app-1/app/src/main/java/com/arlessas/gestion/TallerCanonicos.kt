package com.arlessas.gestion

import java.text.Normalizer
import java.util.Locale

data class TallerItemCanonico(
    val subModulo: String,
    val subcategoria: String,
    val codigoTemporal: String,
    val codigoQr: String = "",
    val nombre: String,
    val tipo: String,
    val marca: String = "",
    val color: String = "",
    val tamano: String = "NO ESPECIFICADO",
    val rango: String = "",
    val modelo: String = "",
    val uso: String = "",
    val cantidad: Double,
    val unidad: String = "UNIDAD",
    val requiereAsignarQr: Boolean,
) {
    val codigoPrincipal: String
        get() = if (codigoQr.isNotBlank()) "QR-${codigoQr.trim()}" else codigoTemporal
}

object TallerCanonicos {
    const val VERSION = "taller_2026_06_08_herramientas_taller_v3"
    const val MODULO = "Taller"
    const val MODULO_LEGACY = "Herramientas"
    const val SUBMODULO_HERRAMIENTAS_TALLER = "HERRAMIENTAS TALLER"
    const val SUBMODULO_BODEGA_ROJA = "BODEGA ROJA"
    const val TIPO_MOV_TRASLADO = "Traslado"
    const val TIPO_MOV_INGRESO_BODEGA = "Ingreso bodega"

    val SUBMODULOS = listOf(
        SUBMODULO_HERRAMIENTAS_TALLER,
        "EQUIPOS COSECHA",
        "HERRAMIENTAS MECANICAS",
        "VEHICULOS",
        "IMPLEMENTOS AGRICOLAS",
        SUBMODULO_BODEGA_ROJA,
    )

    fun esBodegaRojaTaller(subModulo: String): Boolean =
        coincideSubmoduloTaller(subModulo, SUBMODULO_BODEGA_ROJA)

    fun submodulosTrasladoDestino(): List<String> =
        SUBMODULOS.filterNot { esBodegaRojaTaller(it) }

    val CODIGOS_RETIRADOS = listOf("001", "002", "QR-001", "QR-002")

    val herramientasTaller = listOf(
        item("ALICATES Y PINZAS", "SINQR-HT-001", "", "ALICATE NEGRO", "ALICATE", color = "NEGRO", tamano = "GRANDE", cantidad = 4.0),
        item("ALICATES Y PINZAS", "SINQR-HT-002", "", "ALICATE AMARILLO", "ALICATE", color = "AMARILLO", tamano = "GRANDE", cantidad = 1.0),
        item("ALICATES Y PINZAS", "SINQR-HT-003", "", "ALICATE NEGRO", "ALICATE", color = "NEGRO", tamano = "PEQUENO", cantidad = 1.0),
        item("ALICATES Y PINZAS", "SINQR-HT-018", "", "PINZA PARA PIN REDONDO", "PINZA", tamano = "GRANDE", cantidad = 5.0),
        item("ALICATES Y PINZAS", "SINQR-HT-019", "", "PINZA PARA PIN REDONDO", "PINZA", tamano = "MEDIANA", cantidad = 5.0),

        item("DESTORNILLADORES", "SINQR-HT-004", "", "DESTORNILLADOR AZUL DE PALA", "PALA", color = "AZUL", tamano = "GRANDE", cantidad = 3.0),
        item("DESTORNILLADORES", "SINQR-HT-005", "", "DESTORNILLADOR AZUL DE PALA", "PALA", color = "AZUL", tamano = "MEDIANO", cantidad = 2.0),
        item("DESTORNILLADORES", "SINQR-HT-006", "", "DESTORNILLADOR AZUL DE PALA", "PALA", color = "AZUL", tamano = "PEQUENO", cantidad = 1.0),
        item("DESTORNILLADORES", "SINQR-HT-007", "", "DESTORNILLADOR AZUL DE ESTRELLA", "ESTRELLA", color = "AZUL", tamano = "GRANDE", cantidad = 4.0),
        item("DESTORNILLADORES", "SINQR-HT-008", "", "DESTORNILLADOR AZUL DE ESTRELLA", "ESTRELLA", color = "AZUL", tamano = "MEDIANO", cantidad = 1.0),
        item("DESTORNILLADORES", "SINQR-HT-009", "", "DESTORNILLADOR AZUL DE ESTRELLA", "ESTRELLA", color = "AZUL", tamano = "PEQUENO", cantidad = 1.0),

        item("LLAVES MANUALES", "SINQR-HT-010", "", "LLAVES MANUALES MILIMETRICAS", "JUEGO DE LLAVES", tamano = "VARIOS", rango = "#8 AL #1 1/2", cantidad = 1.0, unidad = "JUEGO"),
        item("LLAVES MANUALES", "SINQR-HT-012", "", "LLAVE DE TUBO", "LLAVE DE TUBO", tamano = "GRANDE", cantidad = 1.0),
        item("LLAVES MANUALES", "SINQR-HT-013", "", "LLAVE DE TUBO", "LLAVE DE TUBO", tamano = "PEQUENA", cantidad = 1.0),
        item("LLAVES MANUALES", "SINQR-HT-014", "", "LLAVE EXPANSIVA", "LLAVE EXPANSIVA", tamano = "GRANDE", cantidad = 4.0),
        item("LLAVES MANUALES", "SINQR-HT-015", "", "LLAVE EXPANSIVA", "LLAVE EXPANSIVA", tamano = "PEQUENA", cantidad = 1.0),

        item("LLAVES BRISTOL Y COPAS", "SINQR-HT-016", "", "JUEGO DE LLAVES BRISTOL", "BRISTOL", tamano = "GRANDE", cantidad = 2.0, unidad = "JUEGO"),
        item("LLAVES BRISTOL Y COPAS", "SINQR-HT-017", "", "JUEGO DE LLAVES BRISTOL", "BRISTOL", tamano = "PEQUENA", cantidad = 1.0, unidad = "JUEGO"),
        item("LLAVES BRISTOL Y COPAS", "", "106", "JUEGO DE COPAS FORCE", "COPAS", marca = "FORCE", cantidad = 1.0, unidad = "JUEGO"),
        item("LLAVES BRISTOL Y COPAS", "", "107", "JUEGO DE COPAS STANLEY", "COPAS", marca = "STANLEY", cantidad = 1.0, unidad = "JUEGO"),
        item("LLAVES BRISTOL Y COPAS", "", "269", "JUEGO DE COPAS STANLEY", "COPAS", marca = "STANLEY", cantidad = 1.0, unidad = "JUEGO"),

        item("SUJECION Y PRESION", "SINQR-HT-011", "", "HOMBRE SOLO", "HOMBRE SOLO", tamano = "GRANDE", cantidad = 2.0),
        item("SUJECION Y PRESION", "SINQR-HT-020", "", "DIABLO NEGRO", "DIABLO", color = "NEGRO", tamano = "GRANDE", cantidad = 2.0),
        item("SUJECION Y PRESION", "SINQR-HT-021", "", "DIABLO AMARILLO", "DIABLO", color = "AMARILLO", tamano = "GRANDE", cantidad = 1.0),

        item("CORTE, GOLPE Y CINCELADO", "SINQR-HT-022", "", "SERRUCHO", "SERRUCHO", tamano = "GRANDE", cantidad = 1.0),
        item("CORTE, GOLPE Y CINCELADO", "SINQR-HT-023", "", "SEGUETA", "SEGUETA", tamano = "GRANDE", cantidad = 1.0),
        item("CORTE, GOLPE Y CINCELADO", "SINQR-HT-024", "", "JUEGO DE CINCELES DE ACERO MILIMETRICOS", "CINCEL", tamano = "VARIOS", rango = "#6 AL #13", cantidad = 1.0, unidad = "JUEGO"),
        item("CORTE, GOLPE Y CINCELADO", "SINQR-HT-025", "", "MARTILLO", "MARTILLO", tamano = "GRANDE", cantidad = 1.0),

        item("KITS DE ROSCA", "", "104", "KIT DE ROSCA EXTERNA", "ROSCA EXTERNA", cantidad = 1.0, unidad = "KIT"),
        item("KITS DE ROSCA", "", "105", "KIT DE ROSCA INTERNA", "ROSCA INTERNA", cantidad = 1.0, unidad = "KIT"),

        item("HERRAMIENTAS ELECTRICAS", "", "912", "TALADRO INALAMBRICO", "TALADRO", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "250", "TALADRO ELECTRICO", "TALADRO", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "249", "PULIDORA DEWALT", "PULIDORA", marca = "DEWALT", tamano = "GRANDE", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "248", "PULIDORA DEWALT", "PULIDORA", marca = "DEWALT", tamano = "PEQUENA", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "914", "TROZADORA DEWALT", "TROZADORA", marca = "DEWALT", tamano = "GRANDE", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "103", "POLICHADORA BAUKER", "POLICHADORA", marca = "BAUKER", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "416", "PISTOLA DE IMPACTO", "PISTOLA DE IMPACTO", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "1007", "MOTOSIERRA ELECTRICA AZUL", "MOTOSIERRA ELECTRICA", color = "AZUL", tamano = "PEQUENA", cantidad = 1.0),
        item("HERRAMIENTAS ELECTRICAS", "", "1023", "GRAPADORA", "GRAPADORA", cantidad = 1.0),

        item("EQUIPOS DE TALLER", "", "494", "SOPLADOR AMARILLO", "SOPLADOR", color = "AMARILLO", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "956", "EQUIPO SOLDADURA NEXT INVERSOR INV9200", "SOLDADURA", marca = "NEXT", modelo = "INV9200", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "58", "COMPRESOR WOLFOX ROJO", "COMPRESOR", marca = "WOLFOX", color = "ROJO", tamano = "PEQUENO", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "185", "COMPRESOR ROJO", "COMPRESOR", color = "ROJO", tamano = "GRANDE", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "916", "ESMERIL TRUPER", "ESMERIL", marca = "TRUPER", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "271", "INYECTOR FERTON AMARILLO", "INYECTOR", marca = "FERTON", color = "AMARILLO", tamano = "GRANDE", cantidad = 1.0),
        item("EQUIPOS DE TALLER", "", "108", "OXICORTE AZUL", "OXICORTE", color = "AZUL", tamano = "GRANDE", cantidad = 1.0),

        item("HIDRAULICOS Y SUMINISTRO", "", "948", "GATO HIDRAULICO ROJO", "GATO HIDRAULICO", color = "ROJO", tamano = "GRANDE", cantidad = 1.0),
        item("HIDRAULICOS Y SUMINISTRO", "SINQR-HT-026", "", "GATO HIDRAULICO AZUL", "GATO HIDRAULICO", color = "AZUL", cantidad = 1.0),
        item("HIDRAULICOS Y SUMINISTRO", "SINQR-HT-027", "", "BOMBA DE SUMINISTRO ACPM", "BOMBA DE SUMINISTRO", uso = "ACPM", cantidad = 1.0),
    )

    fun normalizarCodigo(raw: String): String {
        val limpio = raw.trim().uppercase(Locale.getDefault())
        if (limpio.isBlank()) return ""
        if (limpio.startsWith("QR-")) return limpio
        if (limpio.all { it.isDigit() }) return "QR-$limpio"
        return limpio
    }

    /** Extrae solo los dígitos del QR escaneado (ej: "912", "QR-912", "QR912" -> "912"). */
    fun extraerNumerosQr(raw: String): String {
        val limpio = raw.trim()
        if (limpio.isBlank()) return ""
        val upper = limpio.uppercase(Locale.getDefault())
        if (upper.startsWith("QR-")) {
            return upper.removePrefix("QR-").filter { it.isDigit() }
        }
        if (upper.startsWith("QR") && upper.length > 2) {
            val resto = upper.substring(2).trimStart('-', ' ')
            if (resto.isNotEmpty() && resto.all { it.isDigit() }) return resto
        }
        if (limpio.all { it.isDigit() }) return limpio
        val digitos = Regex("\\d+").find(limpio)?.value.orEmpty()
        return digitos
    }

    /** Lo que muestra el campo tras escanear: preferir solo números. */
    fun normalizarEntradaQrEscaneada(raw: String): String {
        return extraerNumerosQr(raw).ifBlank { raw.trim() }
    }

    fun codigoQrDesdeCodigoPrincipal(codigo: String, codigoQr: String = ""): String {
        if (codigoQr.isNotBlank()) return codigoQr.trim()
        val principal = codigo.trim().uppercase(Locale.getDefault())
        if (principal.startsWith("SINQR")) return ""
        return extraerNumerosQr(codigo)
    }

    fun esCodigoQrNumerico(codigo: String, codigoQr: String = ""): Boolean {
        if (codigoQr.isNotBlank()) return true
        val upper = codigo.trim().uppercase(Locale.getDefault())
        return upper.startsWith("QR-") || (upper.startsWith("QR") && extraerNumerosQr(codigo).isNotBlank())
    }

    fun coincideCodigoQr(
        codigo: String,
        codigoQr: String,
        textoBuscado: String,
    ): Boolean {
        val buscado = textoBuscado.trim()
        if (buscado.isBlank()) return false

        val buscadoNorm = normalizarCodigo(buscado)
        val candidatos = listOf(codigo, codigoQr, codigoQrDesdeCodigoPrincipal(codigo, codigoQr))
            .filter { it.isNotBlank() }
            .map { normalizarCodigo(it) }
            .distinct()
        if (candidatos.any { it.equals(buscadoNorm, ignoreCase = true) }) return true

        val digitosBuscados = extraerNumerosQr(buscado)
        if (digitosBuscados.isBlank() || !esCodigoQrNumerico(codigo, codigoQr)) return false

        val digitosRegistrados = listOf(codigo, codigoQr, codigoQrDesdeCodigoPrincipal(codigo, codigoQr))
            .map { extraerNumerosQr(it) }
            .filter { it.isNotBlank() }
        return digitosRegistrados.any { it == digitosBuscados }
    }

    fun esModuloTaller(modulo: String): Boolean {
        val texto = modulo.trim()
        return texto.equals(MODULO, true) || texto.equals(MODULO_LEGACY, true)
    }

    private val ALIAS_SUBMODULOS = mapOf(
        "HERRAMIENTAS DE TALLER" to SUBMODULO_HERRAMIENTAS_TALLER,
        "HERRAMIENTA TALLER" to SUBMODULO_HERRAMIENTAS_TALLER,
        "HERRAMIENTAS TALLER" to SUBMODULO_HERRAMIENTAS_TALLER,
        "EQUIPOS DE COSECHA" to "EQUIPOS COSECHA",
        "EQUIPOS POSCOSECHA" to "EQUIPOS COSECHA",
        "EQUIPO DE COSECHA" to "EQUIPOS COSECHA",
        "EQUIPO COSECHA" to "EQUIPOS COSECHA",
        "HERRAMIENTA MECANICA" to "HERRAMIENTAS MECANICAS",
        "HERRAMIENTAS MECANICA" to "HERRAMIENTAS MECANICAS",
        "HERRAMIENTAS MECANICAS" to "HERRAMIENTAS MECANICAS",
        "VEHICULO" to "VEHICULOS",
        "VEHICULOS E IMPLEMENTOS AGRICOLAS" to "VEHICULOS",
        "IMPLEMENTO AGRICOLA" to "IMPLEMENTOS AGRICOLAS",
        "IMPLEMENTOS AGRICOLA" to "IMPLEMENTOS AGRICOLAS",
        "IMPLEMENTOS AGRICOLAS" to "IMPLEMENTOS AGRICOLAS",
        "BODEGA ROJA" to "BODEGA ROJA",
    )

    fun esSubmoduloTaller(raw: String): Boolean {
        val normalizado = normalizarSubmoduloTaller(raw)
        return SUBMODULOS.any { it.equals(normalizado, ignoreCase = true) }
    }

    fun coincideSubmoduloTaller(valor: String, subModulo: String): Boolean {
        if (subModulo.isBlank()) return true
        return normalizarSubmoduloTaller(valor).equals(normalizarSubmoduloTaller(subModulo), ignoreCase = true)
    }

    fun textoSinAcentos(texto: String): String {
        return Normalizer.normalize(texto.uppercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()
    }

    fun normalizarSubmoduloTaller(raw: String): String {
        val limpio = raw.trim()
        if (limpio.isBlank()) return SUBMODULO_HERRAMIENTAS_TALLER
        SUBMODULOS.firstOrNull { it.equals(limpio, ignoreCase = true) }?.let { return it }
        val compacto = textoSinAcentos(limpio)
        ALIAS_SUBMODULOS[compacto]?.let { return it }
        SUBMODULOS.firstOrNull { textoSinAcentos(it) == compacto }?.let { return it }
        SUBMODULOS.firstOrNull { compacto.contains(textoSinAcentos(it)) || textoSinAcentos(it).contains(compacto) }?.let { return it }
        return limpio.uppercase(Locale.getDefault())
    }

    fun resolverSubmoduloDesdeCampos(
        submoduloTaller: String = "",
        categoria: String = "",
        ubicacion: String = "",
        seccion: String = "",
    ): String {
        val candidatos = listOf(submoduloTaller, seccion, categoria, ubicacion)
        candidatos.firstOrNull { esSubmoduloTaller(it) }?.let { return normalizarSubmoduloTaller(it) }
        return normalizarSubmoduloTaller(submoduloTaller.ifBlank { seccion.ifBlank { categoria.ifBlank { ubicacion } } })
    }

    fun claveDocumento(codigoPrincipal: String): String {
        val normalizado = Normalizer.normalize(codigoPrincipal.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalizado.ifBlank { "taller-${System.currentTimeMillis()}" }
    }

    fun firestoreData(item: TallerItemCanonico, ocupados: Double = 0.0): Map<String, Any?> {
        val disponibles = (item.cantidad - ocupados).coerceAtLeast(0.0)
        return mapOf(
            "modulo" to MODULO,
            "categoria" to item.subModulo,
            "submodulo_taller" to item.subModulo,
            "subcategoria" to item.subcategoria,
            "codigo" to item.codigoPrincipal,
            "codigo_principal" to item.codigoPrincipal,
            "codigo_qr" to item.codigoQr,
            "requiere_asignar_qr" to item.requiereAsignarQr,
            "nombre" to item.nombre,
            "tipo" to item.tipo,
            "marca" to item.marca,
            "color" to item.color,
            "tamano" to item.tamano,
            "rango" to item.rango,
            "modelo" to item.modelo,
            "uso" to item.uso,
            "cantidad_total" to item.cantidad,
            "cantidad_ocupada" to ocupados,
            "cantidad_disponible" to disponibles,
            "unidad" to item.unidad,
            "estado" to if (ocupados <= 0.0) "Disponible" else "En uso",
        )
    }

    private fun item(
        subcategoria: String,
        codigoTemporal: String,
        codigoQr: String,
        nombre: String,
        tipo: String,
        marca: String = "",
        color: String = "",
        tamano: String = "NO ESPECIFICADO",
        rango: String = "",
        modelo: String = "",
        uso: String = "",
        cantidad: Double,
        unidad: String = "UNIDAD",
    ): TallerItemCanonico {
        return TallerItemCanonico(
            subModulo = SUBMODULO_HERRAMIENTAS_TALLER,
            subcategoria = subcategoria,
            codigoTemporal = codigoTemporal.ifBlank { if (codigoQr.isNotBlank()) "QR-$codigoQr" else "" },
            codigoQr = codigoQr,
            nombre = nombre,
            tipo = tipo,
            marca = marca,
            color = color,
            tamano = tamano,
            rango = rango,
            modelo = modelo,
            uso = uso,
            cantidad = cantidad,
            unidad = unidad,
            requiereAsignarQr = codigoQr.isBlank(),
        )
    }
}
