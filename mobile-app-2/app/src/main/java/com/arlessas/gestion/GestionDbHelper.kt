package com.arlessas.gestion

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

const val DATABASE_NAME = "gestion.db"
const val DATABASE_VERSION = 10

data class Movimiento(
    val id: Long = 0,
    val fecha: String = "",
    val modulo: String = "",
    val tipoMovimiento: String = "",
    val item: String = "",
    val referencia: String = "",
    val marca: String = "",
    val cantidad: String = "",
    val unidad: String = "",
    val solicitante: String = "",
    val labor: String = "",
    val maquinaria: String = "",
    val horometro: String = "",
    val herramientaId: String = "",
    val estado: String = "",
    val observaciones: String = "",
)

data class Herramienta(
    val id: Long = 0,
    val fechaRegistro: String = "",
    val nombre: String = "",
    val referencia: String = "",
    val marca: String = "",
    val codigo: String = "",
    val estado: String = "Disponible",
    val ubicacion: String = "",
    val responsable: String = "",
    val observaciones: String = "",
    val subModulo: String = TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER,
    val subcategoria: String = "",
    val tipo: String = "",
    val tamano: String = "",
    val unidad: String = "UNIDAD",
    val cantidadTotal: Double = 1.0,
    val cantidadOcupada: Double = 0.0,
    val codigoQr: String = "",
    val requiereAsignarQr: Boolean = false,
    val vehiculoAsignado: String = "",  // Para implementos: nombre/código del vehículo al que está asignado actualmente
) {
    fun disponibles(): Double = (cantidadTotal - cantidadOcupada).coerceAtLeast(0.0)
    fun ocupados(): Double = cantidadOcupada.coerceAtLeast(0.0)

    override fun toString(): String {
        val codigoTexto = codigo.ifBlank { "Sin codigo" }
        val detalle = listOf(tipo, tamano)
            .filter { it.isNotBlank() && !it.equals("NO ESPECIFICADO", true) }
            .joinToString(" ")
        return "#$id - $nombre${if (detalle.isNotBlank()) " - $detalle" else ""} ($codigoTexto)"
    }
}


data class HistorialCambio(
    val id: Long = 0,
    val fecha: String = "",
    val accion: String = "",
    val modulo: String = "",
    val registroId: String = "",
    val descripcion: String = "",
    val antes: String = "",
    val despues: String = "",
    val usuario: String = "",
)

data class Vencimiento(
    val id: Long = 0,
    val fechaRegistro: String = "",
    val modulo: String = "",
    val item: String = "",
    val referencia: String = "",
    val fechaVencimiento: String = "",
    val lote: String = "",
    val cantidad: String = "",
    val estado: String = "Pendiente",
    val observaciones: String = "",
)

data class Entrada(
    val id: Long = 0,
    val fecha: String = "",
    val modulo: String = "",
    val item: String = "",
    val referencia: String = "",
    val codigoInterno: String = "",
    val cantidad: Double = 0.0,
    val unidad: String = "",
    val observaciones: String = "",
)


data class SyncPendiente(
    val id: Long = 0,
    val fecha: String = "",
    val coleccion: String = "",
    val payloadJson: String = "",
    val estado: String = "Pendiente",
    val error: String = "",
)

class GestionDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE movimientos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                modulo TEXT NOT NULL,
                tipoMovimiento TEXT NOT NULL,
                item TEXT,
                referencia TEXT,
                marca TEXT,
                cantidad TEXT,
                unidad TEXT,
                solicitante TEXT,
                labor TEXT,
                maquinaria TEXT,
                horometro TEXT,
                herramientaId TEXT,
                estado TEXT,
                observaciones TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE herramientas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fechaRegistro TEXT NOT NULL,
                nombre TEXT NOT NULL,
                referencia TEXT,
                marca TEXT,
                codigo TEXT,
                estado TEXT,
                ubicacion TEXT,
                responsable TEXT,
                observaciones TEXT,
                subModulo TEXT DEFAULT 'HERRAMIENTAS TALLER',
                subcategoria TEXT,
                tipo TEXT,
                tamano TEXT,
                unidad TEXT DEFAULT 'UNIDAD',
                cantidadTotal REAL DEFAULT 1,
                cantidadOcupada REAL DEFAULT 0,
                codigoQr TEXT,
                requiereAsignarQr INTEGER DEFAULT 0,
                vehiculoAsignado TEXT DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE entradas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                modulo TEXT NOT NULL,
                item TEXT NOT NULL,
                referencia TEXT,
                codigoInterno TEXT,
                cantidad REAL NOT NULL,
                unidad TEXT,
                observaciones TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE memoria_ia (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                clave TEXT NOT NULL,
                valor TEXT NOT NULL,
                fecha TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE historial_cambios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                accion TEXT NOT NULL,
                modulo TEXT,
                registroId TEXT,
                descripcion TEXT,
                antes TEXT,
                despues TEXT,
                usuario TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE vencimientos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fechaRegistro TEXT NOT NULL,
                modulo TEXT,
                item TEXT,
                referencia TEXT,
                fechaVencimiento TEXT,
                lote TEXT,
                cantidad TEXT,
                estado TEXT,
                observaciones TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pendientes_sync (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                coleccion TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                estado TEXT,
                error TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE conversacion_ia (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario_id TEXT NOT NULL,
                rol TEXT NOT NULL,
                contenido TEXT NOT NULL,
                fecha TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversacion_ia_usuario ON conversacion_ia(usuario_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS entradas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fecha TEXT NOT NULL,
                    modulo TEXT NOT NULL,
                    item TEXT NOT NULL,
                    referencia TEXT,
                    codigoInterno TEXT,
                    cantidad REAL NOT NULL,
                    unidad TEXT,
                    observaciones TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memoria_ia (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clave TEXT NOT NULL,
                    valor TEXT NOT NULL,
                    fecha TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS historial_cambios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fecha TEXT NOT NULL,
                    accion TEXT NOT NULL,
                    modulo TEXT,
                    registroId TEXT,
                    descripcion TEXT,
                    antes TEXT,
                    despues TEXT,
                    usuario TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vencimientos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fechaRegistro TEXT NOT NULL,
                    modulo TEXT,
                    item TEXT,
                    referencia TEXT,
                    fechaVencimiento TEXT,
                    lote TEXT,
                    cantidad TEXT,
                    estado TEXT,
                    observaciones TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 6) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pendientes_sync (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fecha TEXT NOT NULL,
                    coleccion TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    estado TEXT,
                    error TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE entradas ADD COLUMN codigoInterno TEXT DEFAULT ''")
            } catch (_: Exception) {
                // La columna ya existe en algunas instalaciones de prueba.
            }
        }
        if (oldVersion < 8) {
            agregarColumnaSiFalta(db, "herramientas", "subModulo TEXT DEFAULT 'HERRAMIENTAS TALLER'")
            agregarColumnaSiFalta(db, "herramientas", "subcategoria TEXT DEFAULT ''")
            agregarColumnaSiFalta(db, "herramientas", "tipo TEXT DEFAULT ''")
            agregarColumnaSiFalta(db, "herramientas", "tamano TEXT DEFAULT ''")
            agregarColumnaSiFalta(db, "herramientas", "unidad TEXT DEFAULT 'UNIDAD'")
            agregarColumnaSiFalta(db, "herramientas", "cantidadTotal REAL DEFAULT 1")
            agregarColumnaSiFalta(db, "herramientas", "cantidadOcupada REAL DEFAULT 0")
            agregarColumnaSiFalta(db, "herramientas", "codigoQr TEXT DEFAULT ''")
            agregarColumnaSiFalta(db, "herramientas", "requiereAsignarQr INTEGER DEFAULT 0")
        }
        if (oldVersion < 9) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversacion_ia (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    usuario_id TEXT NOT NULL,
                    rol TEXT NOT NULL,
                    contenido TEXT NOT NULL,
                    fecha TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversacion_ia_usuario ON conversacion_ia(usuario_id, id)")
        }
        if (oldVersion < 10) {
            agregarColumnaSiFalta(db, "herramientas", "vehiculoAsignado TEXT DEFAULT ''")
        }
    }

    private fun agregarColumnaSiFalta(db: SQLiteDatabase, tabla: String, definicion: String) {
        try {
            db.execSQL("ALTER TABLE $tabla ADD COLUMN $definicion")
        } catch (_: Exception) {
            // La columna ya existe en algunas instalaciones.
        }
    }

    fun insertarMovimiento(movimiento: Movimiento): Long {
        val values = ContentValues().apply {
            put("fecha", movimiento.fecha)
            put("modulo", movimiento.modulo)
            put("tipoMovimiento", movimiento.tipoMovimiento)
            put("item", movimiento.item)
            put("referencia", movimiento.referencia)
            put("marca", movimiento.marca)
            put("cantidad", movimiento.cantidad)
            put("unidad", movimiento.unidad)
            put("solicitante", movimiento.solicitante)
            put("labor", movimiento.labor)
            put("maquinaria", movimiento.maquinaria)
            put("horometro", movimiento.horometro)
            put("herramientaId", movimiento.herramientaId)
            put("estado", movimiento.estado)
            put("observaciones", movimiento.observaciones)
        }
        return writableDatabase.insert("movimientos", null, values)
    }

    fun insertarHerramienta(herramienta: Herramienta): Long {
        val values = ContentValues().apply {
            put("fechaRegistro", herramienta.fechaRegistro)
            put("nombre", herramienta.nombre)
            put("referencia", herramienta.referencia)
            put("marca", herramienta.marca)
            put("codigo", herramienta.codigo)
            put("estado", herramienta.estado)
            put("ubicacion", herramienta.ubicacion)
            put("responsable", herramienta.responsable)
            put("observaciones", herramienta.observaciones)
            put("subModulo", herramienta.subModulo)
            put("subcategoria", herramienta.subcategoria)
            put("tipo", herramienta.tipo)
            put("tamano", herramienta.tamano)
            put("unidad", herramienta.unidad)
            put("cantidadTotal", herramienta.cantidadTotal)
            put("cantidadOcupada", herramienta.cantidadOcupada)
            put("codigoQr", herramienta.codigoQr)
            put("requiereAsignarQr", if (herramienta.requiereAsignarQr) 1 else 0)
            put("vehiculoAsignado", herramienta.vehiculoAsignado)
        }
        return writableDatabase.insert("herramientas", null, values)
    }

    fun actualizarEstadoHerramienta(id: Long, estado: String, responsable: String): Int {
        val values = ContentValues().apply {
            put("estado", estado)
            put("responsable", responsable)
        }
        return writableDatabase.update("herramientas", values, "id = ?", arrayOf(id.toString()))
    }

    fun actualizarOcupacionHerramienta(id: Long, cantidadOcupada: Double, responsable: String): Int {
        val estado = if (cantidadOcupada <= 0.0) "Disponible" else "En uso"
        val values = ContentValues().apply {
            put("estado", estado)
            put("responsable", if (cantidadOcupada <= 0.0) "" else responsable)
            put("cantidadOcupada", cantidadOcupada.coerceAtLeast(0.0))
        }
        return writableDatabase.update("herramientas", values, "id = ?", arrayOf(id.toString()))
    }

    fun actualizarAsignacionVehiculo(id: Long, vehiculoAsignado: String): Int {
        val values = ContentValues().apply {
            put("vehiculoAsignado", vehiculoAsignado)
        }
        return writableDatabase.update("herramientas", values, "id = ?", arrayOf(id.toString()))
    }

    fun actualizarHerramientaCanonica(herramienta: Herramienta): Int {
        val values = ContentValues().apply {
            put("fechaRegistro", herramienta.fechaRegistro)
            put("nombre", herramienta.nombre)
            put("referencia", herramienta.referencia)
            put("marca", herramienta.marca)
            put("codigo", herramienta.codigo)
            put("ubicacion", herramienta.ubicacion)
            put("observaciones", herramienta.observaciones)
            put("subModulo", herramienta.subModulo)
            put("subcategoria", herramienta.subcategoria)
            put("tipo", herramienta.tipo)
            put("tamano", herramienta.tamano)
            put("unidad", herramienta.unidad)
            put("cantidadTotal", herramienta.cantidadTotal)
            put("codigoQr", herramienta.codigoQr)
            put("requiereAsignarQr", if (herramienta.requiereAsignarQr) 1 else 0)
            put("vehiculoAsignado", herramienta.vehiculoAsignado)
        }
        return writableDatabase.update("herramientas", values, "id = ?", arrayOf(herramienta.id.toString()))
    }

    fun eliminarHerramienta(id: Long): Int {
        return writableDatabase.delete("herramientas", "id = ?", arrayOf(id.toString()))
    }

    fun eliminarHerramientaPorCodigo(codigo: String): Int {
        return writableDatabase.delete("herramientas", "codigo = ? OR codigoQr = ?", arrayOf(codigo, codigo))
    }

    fun obtenerMovimientos(): List<Movimiento> {
        val lista = mutableListOf<Movimiento>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM movimientos ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Movimiento(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fecha = it.getString(it.getColumnIndexOrThrow("fecha")) ?: "",
                        modulo = it.getString(it.getColumnIndexOrThrow("modulo")) ?: "",
                        tipoMovimiento = it.getString(it.getColumnIndexOrThrow("tipoMovimiento")) ?: "",
                        item = it.getString(it.getColumnIndexOrThrow("item")) ?: "",
                        referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
                        marca = it.getString(it.getColumnIndexOrThrow("marca")) ?: "",
                        cantidad = it.getString(it.getColumnIndexOrThrow("cantidad")) ?: "",
                        unidad = it.getString(it.getColumnIndexOrThrow("unidad")) ?: "",
                        solicitante = it.getString(it.getColumnIndexOrThrow("solicitante")) ?: "",
                        labor = it.getString(it.getColumnIndexOrThrow("labor")) ?: "",
                        maquinaria = it.getString(it.getColumnIndexOrThrow("maquinaria")) ?: "",
                        horometro = it.getString(it.getColumnIndexOrThrow("horometro")) ?: "",
                        herramientaId = it.getString(it.getColumnIndexOrThrow("herramientaId")) ?: "",
                        estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
                        observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
                    )
                )
            }
        }
        return lista
    }

    fun obtenerHerramientas(): List<Herramienta> {
        val lista = mutableListOf<Herramienta>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM herramientas ORDER BY nombre ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Herramienta(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fechaRegistro = it.getString(it.getColumnIndexOrThrow("fechaRegistro")) ?: "",
                        nombre = it.getString(it.getColumnIndexOrThrow("nombre")) ?: "",
                        referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
                        marca = it.getString(it.getColumnIndexOrThrow("marca")) ?: "",
                        codigo = it.getString(it.getColumnIndexOrThrow("codigo")) ?: "",
                        estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
                        ubicacion = it.getString(it.getColumnIndexOrThrow("ubicacion")) ?: "",
                        responsable = it.getString(it.getColumnIndexOrThrow("responsable")) ?: "",
                        observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
                        subModulo = cursorString(it, "subModulo", TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER),
                        subcategoria = cursorString(it, "subcategoria"),
                        tipo = cursorString(it, "tipo"),
                        tamano = cursorString(it, "tamano"),
                        unidad = cursorString(it, "unidad", "UNIDAD"),
                        cantidadTotal = cursorDouble(it, "cantidadTotal", 1.0),
                        cantidadOcupada = cursorDouble(it, "cantidadOcupada", 0.0),
                        codigoQr = cursorString(it, "codigoQr"),
                        requiereAsignarQr = cursorBool(it, "requiereAsignarQr"),
                        vehiculoAsignado = cursorString(it, "vehiculoAsignado"),
                    )
                )
            }
        }
        return lista
    }

    fun existeHerramienta(nombre: String, codigo: String): Boolean {
        val cursor = if (codigo.isNotBlank()) {
            readableDatabase.rawQuery("SELECT id FROM herramientas WHERE codigo = ?", arrayOf(codigo))
        } else {
            readableDatabase.rawQuery("SELECT id FROM herramientas WHERE nombre = ?", arrayOf(nombre))
        }
        cursor.use { return it.count > 0 }
    }

    fun buscarHerramientaId(nombre: String, codigo: String): Long? {
        buscarHerramientaPorCodigoNormalizado(codigo)?.let { return it }
        val cursor = if (codigo.isNotBlank()) {
            readableDatabase.rawQuery("SELECT id FROM herramientas WHERE codigo = ? LIMIT 1", arrayOf(codigo))
        } else {
            readableDatabase.rawQuery("SELECT id FROM herramientas WHERE nombre = ? LIMIT 1", arrayOf(nombre))
        }
        cursor.use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    fun buscarHerramientaPorCodigoNormalizado(codigo: String): Long? {
        val buscado = codigo.trim()
        if (buscado.isBlank()) return null
        return obtenerHerramientas().firstOrNull { herramienta ->
            TallerCanonicos.coincideCodigoQr(herramienta.codigo, herramienta.codigoQr, buscado)
                || TallerCanonicos.normalizarCodigo(herramienta.codigo)
                    .equals(TallerCanonicos.normalizarCodigo(buscado), ignoreCase = true)
        }?.id
    }

    fun obtenerHerramientaPorId(id: Long): Herramienta? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM herramientas WHERE id = ? LIMIT 1", arrayOf(id.toString()))
        cursor.use {
            return if (it.moveToFirst()) herramientaDesdeCursor(it) else null
        }
    }

    fun insertarOActualizarHerramientaCanonica(herramienta: Herramienta): Long {
        val existenteId = buscarHerramientaId(herramienta.nombre, herramienta.codigo)
        if (existenteId == null) return insertarHerramienta(herramienta)
        val existente = obtenerHerramientaPorId(existenteId)
        val preservada = herramienta.copy(
            id = existenteId,
            estado = existente?.estado ?: herramienta.estado,
            responsable = existente?.responsable ?: herramienta.responsable,
            cantidadOcupada = existente?.cantidadOcupada ?: herramienta.cantidadOcupada,
        )
        actualizarHerramientaCanonica(preservada)
        return existenteId
    }

    fun insertarEntrada(entrada: Entrada): Long {
        val values = ContentValues().apply {
            put("fecha", entrada.fecha)
            put("modulo", entrada.modulo)
            put("item", entrada.item)
            put("referencia", entrada.referencia)
            put("codigoInterno", entrada.codigoInterno)
            put("cantidad", entrada.cantidad)
            put("unidad", entrada.unidad)
            put("observaciones", entrada.observaciones)
        }
        return writableDatabase.insert("entradas", null, values)
    }

    fun obtenerEntradas(): List<Entrada> {
        val lista = mutableListOf<Entrada>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM entradas ORDER BY id DESC", null)
        cursor.use {
            val codigoIndex = it.getColumnIndex("codigoInterno")
            while (it.moveToNext()) {
                lista.add(
                    Entrada(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fecha = it.getString(it.getColumnIndexOrThrow("fecha")) ?: "",
                        modulo = it.getString(it.getColumnIndexOrThrow("modulo")) ?: "",
                        item = it.getString(it.getColumnIndexOrThrow("item")) ?: "",
                        referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
                        codigoInterno = if (codigoIndex >= 0) it.getString(codigoIndex) ?: "" else "",
                        cantidad = it.getDouble(it.getColumnIndexOrThrow("cantidad")),
                        unidad = it.getString(it.getColumnIndexOrThrow("unidad")) ?: "",
                        observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
                    )
                )
            }
        }
        return lista
    }

    fun buscarUltimaEntregaEPP(solicitante: String, item: String): String? {
        val query = "SELECT fecha FROM movimientos WHERE modulo = 'EPP' AND solicitante = ? AND item = ? ORDER BY id DESC LIMIT 1"
        val cursor = readableDatabase.rawQuery(query, arrayOf(solicitante, item))
        var fecha: String? = null
        cursor.use {
            if (it.moveToFirst()) {
                fecha = it.getString(0)
            }
        }
        return fecha
    }

    fun guardarMemoria(clave: String, valor: String) {
        val values = ContentValues().apply {
            put("clave", clave)
            put("valor", valor)
            put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        }
        writableDatabase.insert("memoria_ia", null, values)
    }

    fun obtenerMemoria(): String {
        val builder = StringBuilder()
        val cursor = readableDatabase.rawQuery("SELECT * FROM memoria_ia ORDER BY id DESC LIMIT 50", null)
        cursor.use {
            while (it.moveToNext()) {
                builder.append("${it.getString(it.getColumnIndexOrThrow("clave"))}: ${it.getString(it.getColumnIndexOrThrow("valor"))}; ")
            }
        }
        return builder.toString()
    }

    fun obtenerMemoriaValor(clave: String): String {
        val cursor = readableDatabase.rawQuery(
            "SELECT valor FROM memoria_ia WHERE clave = ? ORDER BY id DESC LIMIT 1",
            arrayOf(clave)
        )
        cursor.use {
            if (it.moveToFirst()) return it.getString(0) ?: ""
        }
        return ""
    }

    fun borrarMemoria(clave: String) {
        writableDatabase.delete("memoria_ia", "clave = ?", arrayOf(clave))
    }

    fun guardarMensajeConversacionIA(usuarioId: String, rol: String, contenido: String): Long {
        val values = ContentValues().apply {
            put("usuario_id", usuarioId)
            put("rol", rol)
            put("contenido", contenido)
            put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        }
        return writableDatabase.insert("conversacion_ia", null, values)
    }

    fun obtenerMensajesConversacionIA(usuarioId: String, limite: Int = 40): List<MensajeConversacionIA> {
        val lista = mutableListOf<MensajeConversacionIA>()
        val cursor = readableDatabase.rawQuery(
            "SELECT id, usuario_id, rol, contenido, fecha FROM conversacion_ia WHERE usuario_id = ? ORDER BY id DESC LIMIT ?",
            arrayOf(usuarioId, limite.toString()),
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    MensajeConversacionIA(
                        id = it.getLong(0),
                        usuarioId = it.getString(1) ?: "",
                        rol = it.getString(2) ?: "",
                        contenido = it.getString(3) ?: "",
                        fecha = it.getString(4) ?: "",
                    ),
                )
            }
        }
        return lista.asReversed()
    }

    fun podarConversacionIA(usuarioId: String, maxMensajes: Int) {
        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM conversacion_ia WHERE usuario_id = ? ORDER BY id DESC LIMIT 1 OFFSET ?",
            arrayOf(usuarioId, maxMensajes.toString()),
        )
        cursor.use {
            if (it.moveToFirst()) {
                val cutoffId = it.getLong(0)
                writableDatabase.delete(
                    "conversacion_ia",
                    "usuario_id = ? AND id < ?",
                    arrayOf(usuarioId, cutoffId.toString()),
                )
            }
        }
    }

    fun registrarHistorialCambio(
        accion: String,
        modulo: String,
        registroId: String,
        descripcion: String,
        antes: String = "",
        despues: String = "",
        usuario: String = ""
    ): Long {
        val values = ContentValues().apply {
            put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("accion", accion)
            put("modulo", modulo)
            put("registroId", registroId)
            put("descripcion", descripcion)
            put("antes", antes)
            put("despues", despues)
            put("usuario", usuario)
        }
        return writableDatabase.insert("historial_cambios", null, values)
    }

    fun obtenerHistorialCambios(limite: Int = 100): List<HistorialCambio> {
        val lista = mutableListOf<HistorialCambio>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM historial_cambios ORDER BY id DESC LIMIT ?", arrayOf(limite.toString()))
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    HistorialCambio(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fecha = it.getString(it.getColumnIndexOrThrow("fecha")) ?: "",
                        accion = it.getString(it.getColumnIndexOrThrow("accion")) ?: "",
                        modulo = it.getString(it.getColumnIndexOrThrow("modulo")) ?: "",
                        registroId = it.getString(it.getColumnIndexOrThrow("registroId")) ?: "",
                        descripcion = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        antes = it.getString(it.getColumnIndexOrThrow("antes")) ?: "",
                        despues = it.getString(it.getColumnIndexOrThrow("despues")) ?: "",
                        usuario = it.getString(it.getColumnIndexOrThrow("usuario")) ?: "",
                    )
                )
            }
        }
        return lista
    }

    fun obtenerUltimoMovimiento(tipoMovimiento: String = ""): Movimiento? {
        val where = if (tipoMovimiento.isBlank()) "" else "WHERE tipoMovimiento = ?"
        val args = if (tipoMovimiento.isBlank()) null else arrayOf(tipoMovimiento)
        val cursor = readableDatabase.rawQuery("SELECT * FROM movimientos $where ORDER BY id DESC LIMIT 1", args)
        cursor.use {
            if (it.moveToFirst()) return movimientoDesdeCursor(it)
        }
        return null
    }

    fun obtenerMovimientoPorId(id: Long): Movimiento? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM movimientos WHERE id = ? LIMIT 1", arrayOf(id.toString()))
        cursor.use {
            if (it.moveToFirst()) return movimientoDesdeCursor(it)
        }
        return null
    }

    fun actualizarMovimientoCantidad(id: Long, nuevaCantidad: String, nuevaUnidad: String, observacionCorreccion: String): Int {
        val values = ContentValues().apply {
            put("cantidad", nuevaCantidad)
            if (nuevaUnidad.isNotBlank()) put("unidad", nuevaUnidad)
            if (observacionCorreccion.isNotBlank()) put("observaciones", observacionCorreccion)
        }
        return writableDatabase.update("movimientos", values, "id = ?", arrayOf(id.toString()))
    }

    fun calcularStockLocal(item: String): Double {
        var entradas = 0.0
        var salidas = 0.0
        obtenerEntradas().filter { it.item.equals(item, ignoreCase = true) }.forEach { entradas += it.cantidad }
        obtenerMovimientos().filter { it.item.equals(item, ignoreCase = true) }.forEach {
            val cant = it.cantidad.replace(",", ".").toDoubleOrNull() ?: 0.0
            if (it.tipoMovimiento.equals("Entrada", ignoreCase = true)) entradas += cant else salidas += cant
        }
        return entradas - salidas
    }

    fun insertarVencimiento(vencimiento: Vencimiento): Long {
        val values = ContentValues().apply {
            put("fechaRegistro", vencimiento.fechaRegistro)
            put("modulo", vencimiento.modulo)
            put("item", vencimiento.item)
            put("referencia", vencimiento.referencia)
            put("fechaVencimiento", vencimiento.fechaVencimiento)
            put("lote", vencimiento.lote)
            put("cantidad", vencimiento.cantidad)
            put("estado", vencimiento.estado)
            put("observaciones", vencimiento.observaciones)
        }
        return writableDatabase.insert("vencimientos", null, values)
    }

    fun obtenerVencimientos(): List<Vencimiento> {
        val lista = mutableListOf<Vencimiento>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM vencimientos ORDER BY fechaVencimiento ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Vencimiento(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fechaRegistro = it.getString(it.getColumnIndexOrThrow("fechaRegistro")) ?: "",
                        modulo = it.getString(it.getColumnIndexOrThrow("modulo")) ?: "",
                        item = it.getString(it.getColumnIndexOrThrow("item")) ?: "",
                        referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
                        fechaVencimiento = it.getString(it.getColumnIndexOrThrow("fechaVencimiento")) ?: "",
                        lote = it.getString(it.getColumnIndexOrThrow("lote")) ?: "",
                        cantidad = it.getString(it.getColumnIndexOrThrow("cantidad")) ?: "",
                        estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
                        observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
                    )
                )
            }
        }
        return lista
    }

    private fun movimientoDesdeCursor(it: android.database.Cursor): Movimiento {
        return Movimiento(
            id = it.getLong(it.getColumnIndexOrThrow("id")),
            fecha = it.getString(it.getColumnIndexOrThrow("fecha")) ?: "",
            modulo = it.getString(it.getColumnIndexOrThrow("modulo")) ?: "",
            tipoMovimiento = it.getString(it.getColumnIndexOrThrow("tipoMovimiento")) ?: "",
            item = it.getString(it.getColumnIndexOrThrow("item")) ?: "",
            referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
            marca = it.getString(it.getColumnIndexOrThrow("marca")) ?: "",
            cantidad = it.getString(it.getColumnIndexOrThrow("cantidad")) ?: "",
            unidad = it.getString(it.getColumnIndexOrThrow("unidad")) ?: "",
            solicitante = it.getString(it.getColumnIndexOrThrow("solicitante")) ?: "",
            labor = it.getString(it.getColumnIndexOrThrow("labor")) ?: "",
            maquinaria = it.getString(it.getColumnIndexOrThrow("maquinaria")) ?: "",
            horometro = it.getString(it.getColumnIndexOrThrow("horometro")) ?: "",
            herramientaId = it.getString(it.getColumnIndexOrThrow("herramientaId")) ?: "",
            estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
            observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
        )
    }

    private fun herramientaDesdeCursor(it: android.database.Cursor): Herramienta {
        return Herramienta(
            id = it.getLong(it.getColumnIndexOrThrow("id")),
            fechaRegistro = it.getString(it.getColumnIndexOrThrow("fechaRegistro")) ?: "",
            nombre = it.getString(it.getColumnIndexOrThrow("nombre")) ?: "",
            referencia = it.getString(it.getColumnIndexOrThrow("referencia")) ?: "",
            marca = it.getString(it.getColumnIndexOrThrow("marca")) ?: "",
            codigo = it.getString(it.getColumnIndexOrThrow("codigo")) ?: "",
            estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
            ubicacion = it.getString(it.getColumnIndexOrThrow("ubicacion")) ?: "",
            responsable = it.getString(it.getColumnIndexOrThrow("responsable")) ?: "",
            observaciones = it.getString(it.getColumnIndexOrThrow("observaciones")) ?: "",
            subModulo = cursorString(it, "subModulo", TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER),
            subcategoria = cursorString(it, "subcategoria"),
            tipo = cursorString(it, "tipo"),
            tamano = cursorString(it, "tamano"),
            unidad = cursorString(it, "unidad", "UNIDAD"),
            cantidadTotal = cursorDouble(it, "cantidadTotal", 1.0),
            cantidadOcupada = cursorDouble(it, "cantidadOcupada", 0.0),
            codigoQr = cursorString(it, "codigoQr"),
            requiereAsignarQr = cursorBool(it, "requiereAsignarQr"),
            vehiculoAsignado = cursorString(it, "vehiculoAsignado"),
        )
    }

    private fun cursorString(it: android.database.Cursor, columna: String, defecto: String = ""): String {
        val index = it.getColumnIndex(columna)
        return if (index >= 0) it.getString(index) ?: defecto else defecto
    }

    private fun cursorDouble(it: android.database.Cursor, columna: String, defecto: Double = 0.0): Double {
        val index = it.getColumnIndex(columna)
        return if (index >= 0) it.getDouble(index) else defecto
    }

    private fun cursorBool(it: android.database.Cursor, columna: String): Boolean {
        val index = it.getColumnIndex(columna)
        return index >= 0 && it.getInt(index) == 1
    }

    fun insertarPendienteSync(coleccion: String, data: Map<String, Any?>, error: String = "Sin conexión"): Long {
        val json = JSONObject()
        data.forEach { (key, value) -> json.put(key, value ?: "") }
        val values = ContentValues().apply {
            put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("coleccion", coleccion)
            put("payloadJson", json.toString())
            put("estado", "Pendiente")
            put("error", error)
        }
        return writableDatabase.insert("pendientes_sync", null, values)
    }

    fun obtenerPendientesSync(limite: Int = 100): List<SyncPendiente> {
        val lista = mutableListOf<SyncPendiente>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM pendientes_sync ORDER BY id ASC LIMIT ?", arrayOf(limite.toString()))
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    SyncPendiente(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        fecha = it.getString(it.getColumnIndexOrThrow("fecha")) ?: "",
                        coleccion = it.getString(it.getColumnIndexOrThrow("coleccion")) ?: "",
                        payloadJson = it.getString(it.getColumnIndexOrThrow("payloadJson")) ?: "",
                        estado = it.getString(it.getColumnIndexOrThrow("estado")) ?: "",
                        error = it.getString(it.getColumnIndexOrThrow("error")) ?: ""
                    )
                )
            }
        }
        return lista
    }

    fun eliminarPendienteSync(id: Long): Int {
        return writableDatabase.delete("pendientes_sync", "id = ?", arrayOf(id.toString()))
    }

    fun actualizarPendienteSyncError(id: Long, error: String): Int {
        val values = ContentValues().apply {
            put("estado", "Pendiente")
            put("error", error)
        }
        return writableDatabase.update("pendientes_sync", values, "id = ?", arrayOf(id.toString()))
    }

    fun contarPendientesSync(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM pendientes_sync", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun construirReporteTextoCompleto(): String {
        val b = StringBuilder()
        b.appendLine("REPORTE GENERAL DE GESTIÓN")
        b.appendLine("Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
        b.appendLine()
        b.appendLine("MOVIMIENTOS")
        obtenerMovimientos().take(80).forEach { m ->
            b.appendLine("#${m.id} ${m.fecha} | ${m.modulo} | ${m.tipoMovimiento} | ${m.item} | ${m.cantidad} ${m.unidad} | ${m.solicitante} | ${m.labor}")
        }
        b.appendLine()
        b.appendLine("ENTRADAS")
        obtenerEntradas().take(80).forEach { e ->
            b.appendLine("#${e.id} ${e.fecha} | ${e.modulo} | ${e.item} | ${e.cantidad} ${e.unidad} | Ref: ${e.referencia}")
        }
        b.appendLine()
        b.appendLine("VENCIMIENTOS")
        obtenerVencimientos().take(80).forEach { v ->
            b.appendLine("#${v.id} vence ${v.fechaVencimiento} | ${v.modulo} | ${v.item} | lote ${v.lote} | ${v.estado}")
        }
        b.appendLine()
        b.appendLine("HISTORIAL DE CAMBIOS")
        obtenerHistorialCambios(80).forEach { h ->
            b.appendLine("#${h.id} ${h.fecha} | ${h.accion} | ${h.modulo} | ${h.descripcion}")
        }
        b.appendLine()
        b.appendLine("PENDIENTES DE SINCRONIZACIÓN")
        obtenerPendientesSync(80).forEach { p ->
            b.appendLine("#${p.id} ${p.fecha} | ${p.coleccion} | ${p.estado} | ${p.error}")
        }
        return b.toString()
    }

    fun construirCsvCompleto(): String {
        val builder = StringBuilder()
        // Instrucción para que Excel reconozca el punto y coma como separador automáticamente
        builder.appendLine("sep=;")
        // BOM para asegurar que Excel reconozca caracteres especiales (UTF-8)
        builder.insert(0, '\uFEFF')
        builder.appendLine("TIPO_TABLA;ID;FECHA;MODULO;TIPO_MOVIMIENTO;ITEM/NOMBRE;REFERENCIA;MARCA;CODIGO;CANTIDAD;UNIDAD;SOLICITANTE/RESPONSABLE;LABOR/UBICACION;MAQUINARIA;HOROMETRO;HERRAMIENTA_ID;ESTADO;OBSERVACIONES")

        obtenerMovimientos().asReversed().forEach { m ->
            builder.appendLine(
                listOf(
                    "MOVIMIENTO", m.id.toString(), m.fecha, m.modulo, m.tipoMovimiento, m.item,
                    m.referencia, m.marca, "", m.cantidad, m.unidad, m.solicitante, m.labor,
                    m.maquinaria, m.horometro, m.herramientaId, m.estado, m.observaciones
                ).joinToString(";") { csvEscape(it) }
            )
        }

        obtenerHerramientas().forEach { h ->
            builder.appendLine(
                listOf(
                    "HERRAMIENTA", h.id.toString(), h.fechaRegistro, TallerCanonicos.MODULO, h.subModulo, h.nombre,
                    h.subcategoria.ifBlank { h.referencia }, h.marca, h.codigo, h.cantidadTotal.toString(), h.unidad, h.responsable, h.ubicacion,
                    "", "", "", h.estado, h.observaciones
                ).joinToString(";") { csvEscape(it) }
            )
        }
        obtenerEntradas().asReversed().forEach { e ->
            builder.appendLine(
                listOf(
                    "ENTRADA", e.id.toString(), e.fecha, e.modulo, "Entrada", e.item,
                    e.referencia, "", "", e.cantidad.toString(), e.unidad, "", "",
                    "", "", "", "", e.observaciones
                ).joinToString(";") { csvEscape(it) }
            )
        }

        obtenerVencimientos().forEach { v ->
            builder.appendLine(
                listOf(
                    "VENCIMIENTO", v.id.toString(), v.fechaRegistro, v.modulo, v.estado, v.item,
                    v.referencia, "", v.lote, v.cantidad, "", "", v.fechaVencimiento,
                    "", "", "", v.estado, v.observaciones
                ).joinToString(";") { csvEscape(it) }
            )
        }

        obtenerHistorialCambios(500).asReversed().forEach { h ->
            builder.appendLine(
                listOf(
                    "HISTORIAL_CAMBIO", h.id.toString(), h.fecha, h.modulo, h.accion, h.descripcion,
                    "", "", "", "", "", h.usuario, "",
                    "", "", h.registroId, "", "ANTES: ${h.antes} | DESPUES: ${h.despues}"
                ).joinToString(";") { csvEscape(it) }
            )
        }

        obtenerPendientesSync(500).forEach { p ->
            builder.appendLine(
                listOf(
                    "PENDIENTE_SYNC", p.id.toString(), p.fecha, p.coleccion, p.estado, p.payloadJson,
                    "", "", "", "", "", "", "",
                    "", "", "", p.estado, p.error
                ).joinToString(";") { csvEscape(it) }
            )
        }

        return builder.toString()
    }

    private fun csvEscape(value: String): String {
        val cleaned = value.replace("\n", " ").replace("\r", " ").trim()
        val escaped = cleaned.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun eliminarMovimientosHerramientas(): Int {
        val db = writableDatabase
        return db.delete("movimientos", "modulo = ? OR modulo = ?", arrayOf("Herramientas", TallerCanonicos.MODULO))
    }
}
