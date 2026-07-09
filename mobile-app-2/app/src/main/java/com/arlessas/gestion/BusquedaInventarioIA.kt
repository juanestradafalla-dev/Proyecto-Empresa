package com.arlessas.gestion

import java.util.Locale

/**
 * Búsqueda flexible por similitud (no literal) para el asistente y el catálogo.
 */
object BusquedaInventarioIA {

    private const val MAX_LEVENSHTEIN_LEN = 48

    private val palabrasIgnoradas = setOf(
        "saca", "sacar", "salida", "entrada", "registra", "registrar", "registro", "dame", "del", "de", "la", "el", "los", "las",
        "un", "una", "unos", "unas", "por", "para", "con", "sin", "stock", "inventario", "cuanto", "cuanta", "cuantos", "cuantas",
        "necesito", "busco", "quiero", "tienes", "hay", "esta", "estan", "donde", "como", "cual", "cuales", "eso", "esa", "este",
        "esta", "ese", "esos", "esas", "algo", "algun", "alguna", "otro", "otra", "mas", "menos", "solo", "solamente", "favor",
        "tenemos", "tengo", "tiene", "tienen", "tenia", "tenian", "habia", "habian",
        "haz", "hacer", "haga", "pon", "poner", "entregar", "entrega", "entregarle", "prestar", "prestamo", "devolver", "devolucion",
        "consumo", "consumir", "usar", "uso", "persona", "empleado", "trabajador", "responsable", "solicitante", "cantidad", "unidad",
        "modulo", "modulos", "taller", "bodega", "roja", "herramienta", "herramientas", "producto", "productos", "item", "items",
        "abrir", "muestrame", "mostrar", "ver", "consultar", "consulta", "dime", "decir", "dame", "damele",
        "lo", "que", "busca", "encuentra", "puedes", "podrias", "porfa", "necesitaria", "quisiera", "traeme", "verificar", "chequear",
    )

    private val sinonimos = mapOf(
        "guante" to listOf("guantes", "glove", "nitrilo", "nitrile", "latex", "vaqueta", "anticorte"),
        "gafa" to listOf("gafas", "lente", "lentes", "ocular", "proteccion visual"),
        "casco" to listOf("cascos", "helmet", "proteccion cabeza"),
        "mascarilla" to listOf("mascara", "tapabocas", "respirador", "filtro"),
        "botas" to listOf("bota", "calzado", "zapato", "zapatos"),
        "overol" to listOf("overall", "pantalon", "camisa", "dotacion", "uniforme"),
        "detergente" to listOf("jabon", "liquido", "limpieza", "aseo", "desengrasante", "clorox", "fabuloso"),
        "bolsa" to listOf("bolsas", "basura", "residuo", "residuos", "negra", "negras", "caneca"),
        "basura" to listOf("bolsa", "bolsas", "residuo", "residuos", "caneca"),
        "trapero" to listOf("trapeador", "mopa", "limpieza"),
        "escoba" to listOf("cepillo", "barrendero"),
        "papel" to listOf("toalla", "higienico", "servilleta"),
        "aceite" to listOf("lubricante", "grasa", "hidraulico", "valvulina", "fluido"),
        "combustible" to listOf("gasolina", "acpm", "diesel", "urea", "galon"),
        "fertilizante" to listOf("abono", "fertilizer", "yaramila", "npk", "agroquimico", "agroquimicos"),
        "herbicida" to listOf("glifosato", "roundup", "weed", "agroquimico"),
        "fungicida" to listOf("fungicide", "mancozeb", "agroquimico"),
        "insecticida" to listOf("insecticide", "plaguicida", "agroquimico"),
        "agroquimico" to listOf("agroquimicos", "quimico", "quimicos", "veneno", "plaguicida", "cultivo"),
        "quimico" to listOf("quimicos", "agroquimico", "agroquimicos"),
        "compresor" to listOf("aire", "compresora", "wolfox"),
        "soldadura" to listOf("soldador", "esmeril", "inversor"),
        "gato" to listOf("hidraulico", "elevador"),
        "bomba" to listOf("pump", "suministro"),
        "filtro" to listOf("filter", "cartucho"),
        "rodamiento" to listOf("bearing", "chumacera", "cojinete"),
        "manguera" to listOf("hose", "tubo"),
    )

    fun tokensNucleo(prompt: String): List<String> {
        return normalizar(prompt)
            .split(" ")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in palabrasIgnoradas }
            .filter { 
                // Aceptamos palabras de 2+ letras, o identificadores numéricos (ej: "1", "2" para Tractor 1)
                it.length >= 2 || it.any { c -> c.isDigit() }
            }
            .distinct()
            .take(12)
            .toList()
    }

    fun tokens(prompt: String): List<String> {
        return expandirSinonimos(tokensNucleo(prompt)).distinct().take(20)
    }

    fun extraerFraseProducto(prompt: String): String {
        var texto = normalizar(prompt)
        val reemplazos = listOf(
            Regex("""\b(registra|registrar|saca|sacar|dame|necesito|quiero|busco|consulta|consultar|muestrame|mostrar|ver|abrir|verificar|chequear)\b"""),
            Regex("""\b(salida|entrada|ingreso|consumo|prestamo|devolucion|entrega|traslado)\b"""),
            Regex("""\b(de|del|la|el|los|las|un|una|unos|unas|para|por|con|en|lo|que)\b"""),
            // Solo borramos números si parecen ser cantidades aisladas al inicio o fin, o seguidos de unidades de medida comunes
            Regex("""\b\d+([.,]\d+)?\s*(unidades|unds?|und|kgs?|kls?|gramos|grs?|litros|lts?|cc|ml|gls?|galones|mts?|metros|paquetes|pqs?|bolsas|bultos|latas|frascos|sobres)\b"""),
            // Borramos números aislados que no estén pegados a palabras (ej: "saca 5 de tractor 1" -> quita el 5 pero deja el 1 si está cerca de tractor)
            // En realidad, es mejor dejar que los tokens filtren. Pero para la frase, queremos evitar "5" si es una cantidad.
            Regex("""\b\d+([.,]\d+)?\s+(unidades|unds?|und|kgs?|kls?|gramos|grs?|litros|lts?|cc|ml|gls?|galones|mts?|metros|paquetes|pqs?|bolsas|bultos|latas|frascos|sobres)\b"""),
            Regex("""\b(persona|empleado|trabajador|solicitante|responsable)\s+\w+"""),
            Regex("""\b(para|a)\s+[\wáéíóúñ]+"""),
            Regex("""\b(inventario|catalogo|existencias|stock|modulo|modulos|bodega|roja)\b"""),
            Regex("""\b(aseo|epp|dotacion|combustible|taller|agroquimico|agroquimicos|lubricante|lubricantes|consumibles|quimico|herramienta|herramientas)\b"""),
        )
        reemplazos.forEach { regex ->
            texto = texto.replace(regex, " ")
        }
        // No borramos todos los números ciegamente para preservar nombres como "Glifosato 480"
        // Pero si el prompt tiene algo como "saca 5 unidades de x", el regex de arriba ya quitó "5 unidades"
        
        texto = texto.replace(Regex("\\s+"), " ").trim()
        val tokensLimpios = tokensNucleo(texto)
        return if (tokensLimpios.isNotEmpty()) tokensLimpios.joinToString(" ") else texto
    }

    fun puntajePrefiltroRapido(
        candidato: String,
        tokensNucleo: List<String>,
        tokensAmpliados: List<String>,
        moduloHint: String = "",
    ): Int {
        val candidatoNorm = normalizar(candidato)
        if (candidatoNorm.isBlank()) return 0
        var score = 0
        tokensNucleo.forEach { token ->
            if (token.length < 2) return@forEach
            when {
                candidatoNorm.contains(token) -> score += 14
                candidatoNorm.split(" ").any { it.startsWith(token) || token.startsWith(it) } -> score += 9
            }
        }
        tokensAmpliados.filter { it !in tokensNucleo }.forEach { token ->
            if (token.length >= 3 && candidatoNorm.contains(token)) score += 4
        }
        val moduloNorm = normalizar(moduloHint)
        if (moduloNorm.isNotBlank() && candidatoNorm.contains(moduloNorm)) score += 18
        return score
    }

    fun coincidePrefiltroRapido(candidato: String, tokensConsulta: List<String>): Boolean {
        if (tokensConsulta.isEmpty()) return true
        val candidatoNorm = normalizar(candidato)
        if (candidatoNorm.isBlank()) return false
        val palabras = candidatoNorm.split(" ")
        return tokensConsulta.any { token ->
            val esDigito = token.any { it.isDigit() }
            if (token.length < 2 && !esDigito) return@any false
            
            candidatoNorm.contains(token) ||
                palabras.any { palabra ->
                    (palabra.length >= 2 || palabra.any { it.isDigit() }) && 
                    (palabra.startsWith(token) || token.startsWith(palabra))
                }
        }
    }

    fun distanciaLevenshtein(s1: String, s2: String, maxDist: Int = Int.MAX_VALUE): Int {
        var a = s1.uppercase(Locale.getDefault())
        var b = s2.uppercase(Locale.getDefault())
        if (a.length > MAX_LEVENSHTEIN_LEN) a = a.take(MAX_LEVENSHTEIN_LEN)
        if (b.length > MAX_LEVENSHTEIN_LEN) b = b.take(MAX_LEVENSHTEIN_LEN)
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, curr[j])
            }
            if (rowMin > maxDist) return maxDist + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    fun similitudTexto(a: String, b: String): Double {
        val n1 = normalizar(a)
        val n2 = normalizar(b)
        if (n1.isBlank() || n2.isBlank()) return 0.0
        if (n1 == n2) return 1.0
        if (n1.contains(n2) || n2.contains(n1)) {
            val menor = minOf(n1.length, n2.length)
            val mayor = maxOf(n1.length, n2.length)
            // Si la diferencia de longitud es muy grande, penalizamos la coincidencia de subcadena
            val ratioLongitud = menor.toDouble() / mayor
            return if (ratioLongitud < 0.4) (ratioLongitud * 0.5) else ratioLongitud.coerceAtLeast(0.72)
        }
        val maxLen = maxOf(n1.length, n2.length)
        // Reducimos la tolerancia de Levenshtein para mayor precisión
        val maxDist = (maxLen * 0.35).toInt().coerceAtLeast(1)
        val dist = distanciaLevenshtein(n1, n2, maxDist)
        if (dist > maxDist) return 0.0
        return (maxLen - dist).toDouble() / maxLen
    }

    fun tokenPresenteFuzzy(token: String, texto: String, umbral: Double = 0.75): Boolean {
        val esDigito = token.any { it.isDigit() }
        if (token.length < 2 && !esDigito) return false
        
        val textoNorm = normalizar(texto)
        if (textoNorm.contains(token)) {
            // Si el token es muy corto comparado con la palabra que lo contiene, validamos mejor
            val palabras = textoNorm.split(" ")
            if (palabras.any { it == token }) return true
            if (token.length >= 4 || esDigito) return true // Confiamos en números exactos o tokens largos
            // Para tokens cortos, solo aceptamos si la palabra empieza por el token
            if (palabras.any { it.startsWith(token) && it.length <= token.length + 3 }) return true
            return false
        }
        if (token.length >= 4) {
            if (textoNorm.split(" ").any { palabra ->
                    palabra.startsWith(token) ||
                        (palabra.length >= 4 && token.startsWith(palabra)) ||
                        (palabra.length >= 4 && token.length >= 4 && similitudTexto(token, palabra) >= umbral)
                }
            ) return true
        }
        return textoNorm.split(" ").any { palabra ->
            palabra.length >= 2 && similitudTexto(token, palabra) >= umbral
        }
    }

    fun puntuarTexto(
        consulta: String,
        candidato: String,
        tokensConsulta: List<String> = tokens(consulta),
        fraseProducto: String? = null,
    ): Double {
        val consultaNorm = normalizar(consulta)
        val candidatoNorm = normalizar(candidato)
        if (consultaNorm.isBlank() || candidatoNorm.isBlank()) return 0.0

        var score = similitudTexto(consultaNorm, candidatoNorm) * 0.35

        val frase = fraseProducto ?: extraerFraseProducto(consulta)
        if (frase.isNotBlank() && frase != consultaNorm) {
            score += similitudTexto(frase, candidatoNorm) * 0.25
        } else if (frase.isNotBlank() && frase == consultaNorm) {
            score += score * 0.12
        }

        val tokensActivos = if (tokensConsulta.isNotEmpty()) tokensConsulta else tokens(consulta)
        val nucleo = tokensNucleo(fraseProducto ?: consulta)
        if (nucleo.isNotEmpty()) {
            val aciertosNucleo = nucleo.count { tokenPresenteFuzzy(it, candidatoNorm) }
            score += (aciertosNucleo.toDouble() / nucleo.size) * 0.40
        }
        if (tokensActivos.isNotEmpty()) {
            val aciertos = tokensActivos.count { tokenPresenteFuzzy(it, candidatoNorm) }
            val ratio = aciertos.toDouble() / tokensActivos.size
            score += ratio * 0.10
        }

        return score.coerceIn(0.0, 1.0)
    }

    fun mejorCoincidencia(
        consulta: String,
        candidatos: List<String>,
        umbral: Double = 0.55,
    ): Pair<String, Double>? {
        if (consulta.isBlank() || candidatos.isEmpty()) return null
        val tokensConsulta = tokens(consulta)
        return candidatos.asSequence()
            .map { candidato -> candidato to puntuarTexto(consulta, candidato, tokensConsulta) }
            .filter { it.second >= umbral }
            .maxByOrNull { it.second }
    }

    private fun expandirSinonimos(tokensBase: List<String>): List<String> {
        val expandidos = mutableListOf<String>()
        tokensBase.forEach { token ->
            expandidos.add(token)
            sinonimos[token]?.let { expandidos.addAll(it) }
            sinonimos.entries.firstOrNull { (_, aliases) ->
                aliases.any { alias -> similitudTexto(token, alias) >= 0.82 }
            }?.let { (clave, aliases) ->
                expandidos.add(clave)
                expandidos.addAll(aliases)
            }
        }
        return expandidos
    }

    private fun normalizar(valor: String): String = normalizarBusqueda(valor)
}
