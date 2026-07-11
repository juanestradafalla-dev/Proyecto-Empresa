package com.arlessas.gestion

import java.util.Locale

internal data class AseoCanonico(
    val codigoInterno: String,
    val piso: Int,
    val categoria: String,
    val producto: String,
    val unidad: String,
    val stockActual: Double?,
) {
    val documentoId: String get() = codigoInterno
    val codigoOriginal: String get() = codigoInterno
    val item: String get() = producto
    val ubicacion: String get() = "Piso $piso"
    val stockOperativo: Double get() = stockActual ?: 0.0

    fun firestoreData(stock: Double? = stockActual): Map<String, Any?> = mapOf(
        "codigo_interno" to codigoInterno,
        "piso" to piso,
        "categoria" to categoria,
        "producto" to producto,
        "unidad" to unidad,
        "stock_actual" to (stock ?: 0.0)
    )
}

internal object AseoCanonicos {
    const val VERSION = "aseo_2026_06_24_productos_aseo_h_v7"
    const val FUENTE = "Inventario_Aseo.xlsx y complemento pegado por el usuario"
    const val MODULO = ModulosInventario.ASEO
    const val COLECCION = "productos_aseo"
    const val CATEGORIA_PRINCIPAL = "Productos de aseo"

    private val itemsBase: List<AseoCanonico> = listOf(
        AseoCanonico("H02-001", 2, "Desengrasantes", "DESENGRASANTE INDUSTRIAL MIDIA 500ML", "ML", 3.0),
        AseoCanonico("H02-002", 2, "Ceras y pisos", "CERA ROJA ALKOSTO", "UNIDAD", 2.0),
        AseoCanonico("H02-003", 2, "Jabones y detergentes", "JABON REY EN BARRA", "UNIDAD", 17.0),
        AseoCanonico("H02-004", 2, "Desengrasantes", "QUITAGRASA JABON LIQUIDO AXION LIMON 1.5L", "UNIDAD", 7.0),
        AseoCanonico("H02-005", 2, "Desengrasantes", "QUITAGRASA JABON LIQUIDO EASY OFF 500ML", "UNIDAD", 3.0),
        AseoCanonico("H02-006", 2, "Desengrasantes", "QUITAGRASA JABON LIQUIDO MISTER MUSCULO 500ML", "UNIDAD", 5.0),
        AseoCanonico("H02-007", 2, "Jabones y detergentes", "DETERGENTE EN POLVO 3KG", "UNIDAD", 4.0),
        AseoCanonico("H03-001", 3, "Fibras y esponjas", "Fibra esponja amarilla", "PAR", 27.0),
        AseoCanonico("H03-002", 3, "Fibras y esponjas", "Fibra verde", "PAR", 1.0),
        AseoCanonico("H03-003", 3, "Fibras y esponjas", "Bombril brilla ollas", "PAR", 12.0),
        AseoCanonico("H03-004", 3, "Fibras y esponjas", "Esponja metalica", "PAR", 21.0),
        AseoCanonico("H03-005", 3, "Utensilios de cocina", "Alambre con sujetador", "PAR", 19.0),
        AseoCanonico("H03-006", 3, "Guantes", "Guante industrial cocina talla 8", "PAR", 10.0),
        AseoCanonico("H03-007", 3, "Guantes", "Guante industrial cocina talla 9", "PAR", null),
        AseoCanonico("H03-008", 3, "Guantes", "Guantes manipulacion de alimentos transparentes", "CAJA", 50.0),
        AseoCanonico("H03-009", 3, "Fibras y esponjas", "Esponja suave", "PAR", null),
        AseoCanonico("H03-010", 3, "Fibras y esponjas", "Esponja acero", "PAR", null),
        AseoCanonico("H04-001", 4, "Bolsas de basura", "Bolsa de basura negra 51x76 cm", "UNIDAD", 90.0),
        AseoCanonico("H04-002", 4, "Bolsas de basura", "Bolsa de basura blanca 51x76 cm", "UNIDAD", 60.0),
        AseoCanonico("H04-003", 4, "Bolsas de basura", "Bolsa de basura enorme gruesa negra", "UNIDAD", 60.0),
        AseoCanonico("H04-004", 4, "Bolsas de basura", "Bolsa de basura negra 65x100 cm", "UNIDAD", 60.0),
        AseoCanonico("H04-005", 4, "Bolsas de basura", "Bolsa de basura negra 65x80 cm", "UNIDAD", 3.0),
        AseoCanonico("H04-006", 4, "Bolsas de basura", "Bolsa de basura verde gruesa enorme", "UNIDAD", 48.0),
        AseoCanonico("H04-007", 4, "Bolsas de basura", "Bolsa de basura verde 65x80 cm", "UNIDAD", 180.0),
        AseoCanonico("H04-008", 4, "Bolsas de basura", "Bolsa de basura roja enorme gruesa", "UNIDAD", 42.0),
        AseoCanonico("H04-009", 4, "Bolsas de basura", "Bolsa de basura roja 53x52 cm", "UNIDAD", 82.0),
        AseoCanonico("H04-010", 4, "Bolsas de basura", "Bolsa naranja", "UNIDAD", 27.0),
        AseoCanonico("H05-001", 5, "Desechables y papel", "Cofia desechable x4", "PAQUETE", 25.0),
        AseoCanonico("H05-002", 5, "Desechables y papel", "Servilletas tipo Z", "PAQUETE", 7.0),
        AseoCanonico("H05-003", 5, "Desechables y papel", "Toallas multiusos enorme", "UNIDAD", 2.0),
        AseoCanonico("H05-004", 5, "Desechables y papel", "Filtros de papel para cafe", "UNIDAD", 40.0),
        AseoCanonico("H05-005", 5, "Otros aseo y cocina", "Encendedores", "UNIDAD", 3.0),
        AseoCanonico("H05-006", 5, "Menaje", "Juego de loza Corona", "PAQUETE", 1.0),
        AseoCanonico("H05-007", 5, "Desechables y papel", "Papel higienico triple hoja", "ROLLO", 24.0),
        AseoCanonico("H05-008", 5, "Guantes", "Guante multiproposito", "PAR", 91.0),
        AseoCanonico("H05-009", 5, "Utensilios de aseo", "Coladera mediana", "UNIDAD", 3.0),
        AseoCanonico("H05-010", 5, "Utensilios de aseo", "Cabeza de escoba", "UNIDAD", 3.0),
        AseoCanonico("H05-011", 5, "Utensilios de aseo", "Chupa sanitaria", "UNIDAD", 4.0),
        AseoCanonico("H00-001", 0, "Utensilios de aseo", "Palo de escoba", "UNIDAD", null),
        AseoCanonico("H00-002", 0, "Aditivos", "HYDROSEP 8Oz", "ML", 4.0),
        AseoCanonico("H00-003", 0, "Desengrasantes", "JABON LIQUIDO DESENGRASANTE ARTEMISA", "ML", 6000.0),
        AseoCanonico("H00-005", 0, "Desinfectantes", "CLOROX", "UNIDAD", 6.0),
        AseoCanonico("H00-006", 0, "Jabones y detergentes", "DETERGENTE LIQUIDO ARCOIRIS", "UNIDAD", 2.0),
        AseoCanonico("H00-007", 0, "Limpieza general", "FABULOSO", "UNIDAD", 3.0),
        AseoCanonico("H00-008", 0, "Jabones y detergentes", "JABON LIQUIDO CAPIBEL", "UNIDAD", 5.0),
    )

    private val itemsPersonalizados = linkedMapOf<String, AseoCanonico>()
    val items: List<AseoCanonico>
        get() = itemsBase + itemsPersonalizados.values

    fun registrarProductoPersonalizado(producto: AseoCanonico) {
        val codigo = normalizarCodigo(producto.codigoInterno)
        if (itemsBase.any { normalizarCodigo(it.codigoInterno) == codigo }) return
        itemsPersonalizados[codigo] = producto.copy(codigoInterno = codigo)
    }

    private fun norm(value: String): String = value.trim().uppercase(Locale.getDefault())
    private fun codigoNorm(value: String): String = norm(value).replace("-", "")
    private fun productoClave(value: String): String {
        return normalizarBusqueda(value)
            .replace(Regex("\\bpalos\\b"), "palo")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizarCodigo(codigo: String): String {
        val compacto = codigo.trim().uppercase(Locale.getDefault()).replace(Regex("[^A-Z0-9]"), "")
        val match = Regex("^H(\\d{2})(\\d{3})$").find(compacto)
        return if (match != null) "H${match.groupValues[1]}-${match.groupValues[2]}" else codigo.trim().uppercase(Locale.getDefault())
    }

    private fun AseoCanonico.codigoCoincide(codigo: String): Boolean {
        val cod = codigoNorm(codigo)
        return codigoNorm(codigoInterno) == cod
    }

    fun pisoDesdeCodigo(codigo: String): Int {
        val normalizado = codigoNorm(codigo)
        return Regex("^H(\\d{2})\\d{3}$").find(normalizado)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: items.firstOrNull { it.codigoCoincide(codigo) }?.piso
            ?: 0
    }

    fun agregarAlCatalogo(catalogo: MutableMap<String, MutableMap<String, MutableMap<String, MutableList<String>>>>) {
        val moduloMap = catalogo.getOrPut(MODULO) { mutableMapOf() }
        items.forEach { producto ->
            val categoriaMap = moduloMap.getOrPut(producto.categoria) { mutableMapOf() }
            val listaRefs = categoriaMap.getOrPut(producto.producto) { mutableListOf() }
            if (!listaRefs.contains("N/A")) listaRefs.add("N/A")
        }
    }

    fun buscar(categoria: String, item: String): AseoCanonico? {
        val cat = norm(categoria)
        return items.firstOrNull { norm(it.categoria) == cat && productoCoincide(it.producto, item) }
    }

    fun buscarPorDocumento(documentoId: String): AseoCanonico? {
        return items.firstOrNull { it.codigoCoincide(documentoId) || norm(it.documentoId) == norm(documentoId) }
    }

    fun buscarPorCodigoUbicacion(codigo: String, ubicacion: String = "", item: String = ""): AseoCanonico? {
        val itemClave = productoClave(item)
        return items.firstOrNull {
            it.codigoCoincide(codigo) && (itemClave.isBlank() || productoCoincide(it.producto, item))
        }
    }

    fun ubicaciones(codigo: String, item: String = ""): List<AseoCanonico> {
        val itemClave = productoClave(item)
        return items.filter { it.codigoCoincide(codigo) && (itemClave.isBlank() || productoCoincide(it.producto, item)) }
    }

    fun productoCoincide(esperado: String, registrado: String): Boolean {
        val esperadoClave = productoClave(esperado)
        val registradoClave = productoClave(registrado)
        return esperadoClave.isNotBlank() && esperadoClave == registradoClave
    }

    fun buscarTexto(texto: String): List<AseoCanonico> {
        val query = normalizarBusqueda(texto)
        val cod = codigoNorm(texto)
        if (query.isBlank() && cod.isBlank()) return emptyList()
        return items.map { producto ->
            val codigoPuntos = when {
                cod.isNotBlank() && codigoNorm(producto.codigoInterno) == cod -> 1000
                cod.isNotBlank() && codigoNorm(producto.codigoInterno).contains(cod) -> 600
                else -> 0
            }
            val textoProducto = normalizarBusqueda("${producto.codigoInterno} ${producto.categoria} ${producto.producto} ${producto.unidad}")
            val textoPuntos = query.split(" ").filter { it.length > 1 }.sumOf { term ->
                if (textoProducto.contains(term)) 120 else 0
            }
            producto to (codigoPuntos + textoPuntos)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
