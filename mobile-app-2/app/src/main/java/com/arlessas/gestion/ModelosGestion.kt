@file:Suppress("unused")

package com.arlessas.gestion

internal data class BusquedaItem(
        val modulo: String,
        val categoria: String,
        val item: String,
        val referencia: String,
    ) {
        override fun toString(): String = "$item - $referencia · $modulo / $categoria"
    }

internal data class ProductoCatalogo(
        val modulo: String,
        val categoria: String,
        val item: String,
        val referencia: String,
    )

internal data class ExistenciaProducto(
        val documentoId: String,
        val codigoInterno: String,
        val codigoOriginal: String = "",
        val modulo: String,
        val categoria: String,
        val subcategoria: String = "",
        val item: String,
        val marca: String,
        val referencia: String,
        val referenciaCatalogo: String,
        val cantidad: Double,
        val unidad: String,
        val ubicacion: String = ""
    )

internal data class CodigoInternoOpcion(val producto: ExistenciaProducto) {
        override fun toString(): String {
            val ref = producto.referenciaCatalogo.ifBlank { producto.referencia.ifBlank { "N/A" } }
            val ubicacion = producto.ubicacion.ifBlank { producto.subcategoria }
            val stock = if (producto.unidad.isBlank()) producto.cantidad.toString() else "${producto.cantidad} ${producto.unidad}"
            val zona = if (ubicacion.isBlank()) "" else " · $ubicacion"
            return "${producto.codigoOriginal.ifBlank { producto.codigoInterno }} · ${producto.item} · $ref$zona · Stock: $stock"
        }
    }

internal data class QuimicoUbicacionStock(
        val documentoId: String,
        val codigoOriginal: String,
        val item: String,
        val categoria: String,
        val ubicacion: String,
        val cantidad: Double,
        val unidad: String
    ) {
        override fun toString(): String = "$ubicacion · $cantidad $unidad"
    }
