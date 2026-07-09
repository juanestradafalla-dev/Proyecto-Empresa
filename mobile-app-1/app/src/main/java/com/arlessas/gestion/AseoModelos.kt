package com.arlessas.gestion

internal data class AseoUbicacionStock(
    val documentoId: String,
    val codigoOriginal: String,
    val item: String,
    val categoria: String,
    val ubicacion: String,
    val cantidad: Double,
    val unidad: String,
) {
    override fun toString(): String = "$ubicacion - ${cantidadTexto(cantidad)} $unidad"
}
