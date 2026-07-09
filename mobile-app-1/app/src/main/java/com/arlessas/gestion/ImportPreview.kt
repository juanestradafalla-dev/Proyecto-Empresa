package com.arlessas.gestion

data class ImportPreview(
    val productosValidos: Int,
    val entradasConCantidad: Int,
    val vencimientosDetectados: Int,
    val codigosRepetidos: Int,
    val filasSinProducto: Int,
    val filasSinCodigoOriginal: Int,
    val cantidadesInvalidas: Int,
    val muestra: List<String>,
) {
    fun resumen(): String = buildString {
        appendLine("Productos válidos: $productosValidos")
        appendLine("Entradas con cantidad: $entradasConCantidad")
        appendLine("Vencimientos detectados: $vencimientosDetectados")
        appendLine("Códigos repetidos en archivo: $codigosRepetidos")
        appendLine("Filas sin producto: $filasSinProducto")
        appendLine("Filas sin código original: $filasSinCodigoOriginal")
        appendLine("Cantidades inválidas: $cantidadesInvalidas")
        if (muestra.isNotEmpty()) {
            appendLine()
            appendLine("Muestra revisada:")
            muestra.forEachIndexed { index, linea -> appendLine("${index + 1}. $linea") }
        }
    }.trim()
}
