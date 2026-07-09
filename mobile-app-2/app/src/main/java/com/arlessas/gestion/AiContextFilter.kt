package com.arlessas.gestion

object AiContextFilter {
    fun tokens(prompt: String): List<String> = BusquedaInventarioIA.tokens(prompt)
}