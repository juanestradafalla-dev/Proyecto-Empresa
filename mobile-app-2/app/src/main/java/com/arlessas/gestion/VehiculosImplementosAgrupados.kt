package com.arlessas.gestion

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import java.util.UUID

private var secuenciaBorradorVehiculo = 0L

internal data class FotoElementoAsignacion(
    var uriLocal: String = "",
    var urlRemota: String = "",
)

internal class BorradorFotosVehiculo(
    val token: Long,
    val pantallaId: String,
) {
    val fotos = mutableMapOf<String, FotoElementoAsignacion>()
    var clavesVigentes: Set<String> = emptySet()
    var capturaToken: Long = 0L
    var procesando: Boolean = false
    var activo: Boolean = true

    fun fotoLista(clave: String): Boolean {
        val foto = fotos[clave] ?: return false
        return foto.uriLocal.isNotBlank() || foto.urlRemota.isNotBlank()
    }
}

internal data class FotoAsignacionCongelada(
    val clave: String,
    val nombre: String,
    val uriLocal: String,
    val urlRemota: String,
)

internal data class LineaAsignacionVehiculo(
    val herramienta: Herramienta,
    val clave: String,
    val cantidad: Double,
    val fotoUrl: String,
    val esVehiculoPrincipal: Boolean,
)

internal data class AsignacionVehiculoConfirmada(
    val vehiculo: Herramienta,
    val implementos: List<Herramienta>,
    val cantidadVehiculo: Double,
    val solicitante: String,
    val labor: String,
    val zona: String,
    val observaciones: String,
    val fotos: Map<String, FotoAsignacionCongelada>,
    val fecha: String,
    val asignacionId: String = "VEH-${UUID.randomUUID()}",
) {
    fun elementos(): List<Herramienta> = listOf(vehiculo) + implementos
}

internal data class ResultadoLineaAsignacion(
    val linea: LineaAsignacionVehiculo,
    val cantidadTotalActual: Double,
    val ocupacionAnterior: Double,
    val ocupacionNueva: Double,
)

internal class BloqueoFormulario private constructor(
    private val estados: Map<View, Boolean>,
) {
    fun restaurar() {
        estados.forEach { (vista, habilitada) -> vista.isEnabled = habilitada }
    }

    companion object {
        fun aplicar(root: View): BloqueoFormulario {
            val estados = linkedMapOf<View, Boolean>()
            fun recorrer(vista: View) {
                estados[vista] = vista.isEnabled
                vista.isEnabled = false
                if (vista is ViewGroup) {
                    for (index in 0 until vista.childCount) recorrer(vista.getChildAt(index))
                }
            }
            recorrer(root)
            return BloqueoFormulario(estados)
        }
    }
}

internal fun MainActivity.nuevoBorradorFotosVehiculo(): BorradorFotosVehiculo {
    return BorradorFotosVehiculo(++secuenciaBorradorVehiculo, currentScreenId)
}

internal fun BorradorFotosVehiculo.esVigente(pantallaIdActual: String): Boolean {
    return activo && token == secuenciaBorradorVehiculo && pantallaId == pantallaIdActual
}

internal fun MainActivity.renderFotosVehiculoImplementos(
    borrador: BorradorFotosVehiculo,
    container: LinearLayout,
    vehiculo: Herramienta,
    implementos: List<Herramienta>,
    onChanged: () -> Unit,
) {
    val elementos = listOf(vehiculo to "Vehículo") + implementos.map { it to "Implemento" }
    val claves = elementos.map { claveHerramientaCloud(it.first) }
    borrador.clavesVigentes = claves.toSet()
    container.removeAllViews()

    elementos.forEachIndexed { index, (elemento, tipo) ->
        val clave = claves[index]
        val foto = borrador.fotos[clave]
        val lista = borrador.fotoLista(clave)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), if (lista) ArlesPalette.green700 else ArlesPalette.line)
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }
        card.addView(TextView(this).apply {
            text = "$tipo - ${elemento.nombre}"
            textSize = 16f
            setTextColor(ArlesPalette.ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Código: ${elemento.codigo.ifBlank { "Sin código" }}"
            textSize = 13f
            setTextColor(ArlesPalette.muted)
        })
        card.addView(TextView(this).apply {
            text = if (lista) "Foto lista" else "Foto pendiente"
            textSize = 13f
            setTextColor(if (lista) ArlesPalette.green800 else ArlesPalette.danger)
            setPadding(0, dp(4), 0, dp(6))
        })

        if (!foto?.uriLocal.isNullOrBlank()) {
            card.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(150),
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(Uri.parse(foto?.uriLocal))
            })
        }

        val acciones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textoCaptura = if (lista) "Repetir foto" else "Tomar foto"
        acciones.addView(styledButton(textoCaptura, ArlesButtonStyle.TALLER) {
            if (borrador.procesando || !borrador.activo) return@styledButton
            val callbackToken = ++borrador.capturaToken
            val pantallaCaptura = currentScreenId
            capturarEvidencia { uri ->
                val vigente = borrador.esVigente(currentScreenId) && !borrador.procesando &&
                    callbackToken == borrador.capturaToken &&
                    pantallaCaptura == currentScreenId &&
                    clave in borrador.clavesVigentes
                if (!vigente) return@capturarEvidencia
                borrador.fotos[clave] = FotoElementoAsignacion(uriLocal = uri)
                onChanged()
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            setMargins(0, dp(8), if (lista) dp(6) else 0, 0)
        })
        if (lista) {
            acciones.addView(styledButton("Eliminar foto", ArlesButtonStyle.OUTLINE) {
                if (borrador.procesando || !borrador.activo) return@styledButton
                borrador.capturaToken++
                borrador.fotos.remove(clave)
                onChanged()
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(6), dp(8), 0, 0)
            })
        }
        card.addView(acciones)
        container.addView(card)
    }
}

internal fun BorradorFotosVehiculo.congelarFotos(
    elementos: List<Herramienta>,
): Map<String, FotoAsignacionCongelada>? {
    val resultado = linkedMapOf<String, FotoAsignacionCongelada>()
    elementos.forEach { elemento ->
        val clave = claveHerramientaCloud(elemento)
        val foto = fotos[clave] ?: return null
        if (foto.uriLocal.isBlank() && foto.urlRemota.isBlank()) return null
        resultado[clave] = FotoAsignacionCongelada(
            clave = clave,
            nombre = elemento.nombre,
            uriLocal = foto.uriLocal,
            urlRemota = foto.urlRemota,
        )
    }
    return resultado.toMap()
}

internal fun MainActivity.subirFotosAsignacionVehiculo(
    confirmada: AsignacionVehiculoConfirmada,
    borrador: BorradorFotosVehiculo,
    onSuccess: (Map<String, String>) -> Unit,
    onFailure: (String) -> Unit,
) {
    val fotos = confirmada.fotos.values.toList()
    val urls = linkedMapOf<String, String>()

    fun subir(index: Int) {
        if (!borrador.esVigente(currentScreenId) || !borrador.procesando) return
        if (index >= fotos.size) {
            onSuccess(urls.toMap())
            return
        }
        val foto = fotos[index]
        if (foto.urlRemota.isNotBlank()) {
            urls[foto.clave] = foto.urlRemota
            subir(index + 1)
            return
        }
        if (foto.uriLocal.isBlank()) {
            onFailure("Falta la foto de ${foto.nombre}")
            return
        }
        subirEvidenciaCloud(foto.uriLocal, TallerCanonicos.MODULO) { url ->
            if (!borrador.esVigente(currentScreenId) || !borrador.procesando) return@subirEvidenciaCloud
            if (url.isBlank()) {
                onFailure("No se pudo subir la foto de ${foto.nombre}")
                return@subirEvidenciaCloud
            }
            urls[foto.clave] = url
            borrador.fotos[foto.clave]?.takeIf { it.uriLocal == foto.uriLocal }?.urlRemota = url
            subir(index + 1)
        }
    }

    subir(0)
}

internal fun MainActivity.registrarAsignacionVehiculoImplementos(
    confirmada: AsignacionVehiculoConfirmada,
    urls: Map<String, String>,
    onSuccess: (List<ResultadoLineaAsignacion>) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    if (!isNetworkAvailable()) {
        onFailure(IllegalStateException("Se requiere conexion para registrar la asignacion completa"))
        return
    }
    val elementos = confirmada.elementos()
    val claves = elementos.map(::claveHerramientaCloud)
    if (claves.distinct().size != claves.size) {
        onFailure(IllegalStateException("Hay elementos repetidos en la asignacion"))
        return
    }
    if (urls.keys.containsAll(claves).not() || claves.any { urls[it].isNullOrBlank() }) {
        onFailure(IllegalStateException("Todas las fotografias deben estar listas"))
        return
    }

    obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { responsableEntrega ->
        val fecha = confirmada.fecha
        val vehiculoNombre = "${confirmada.vehiculo.nombre} ${confirmada.vehiculo.codigo}".trim()
        val vehiculoClave = claveHerramientaCloud(confirmada.vehiculo)
        val lineas = elementos.mapIndexed { index, elemento ->
            val esVehiculo = index == 0
            val clave = claves[index]
            LineaAsignacionVehiculo(
                herramienta = elemento.copy(),
                clave = clave,
                cantidad = if (esVehiculo) confirmada.cantidadVehiculo else 1.0,
                fotoUrl = urls.getValue(clave),
                esVehiculoPrincipal = esVehiculo,
            )
        }
        val referencias = lineas.associate { linea ->
            linea.clave to firestore.collection("herramientas").document(linea.clave)
        }
        val movimientos = lineas.associate { linea ->
            linea.clave to firestore.collection("movimientos").document()
        }

        firestore.runTransaction { transaction ->
            val snapshots = linkedMapOf<String, DocumentSnapshot>()
            referencias.forEach { (clave, referencia) ->
                snapshots[clave] = transaction.get(referencia)
            }

            val resultados = lineas.map { linea ->
                val snapshot = snapshots[linea.clave]
                    ?: throw IllegalStateException("No se pudo leer ${linea.herramienta.nombre}")
                if (!snapshot.exists()) {
                    throw IllegalStateException("No existe ${linea.herramienta.nombre} en el inventario")
                }
                val herramientaDoc = herramientaDesdeDocumentoFirestore(snapshot)
                    ?: throw IllegalStateException("Documento invalido para ${linea.herramienta.nombre}")
                val submoduloEsperado = if (linea.esVehiculoPrincipal) "VEHICULOS" else "IMPLEMENTOS AGRICOLAS"
                if (!TallerCanonicos.coincideSubmoduloTaller(herramientaDoc.subModulo, submoduloEsperado)) {
                    throw IllegalStateException("${linea.herramienta.nombre} ya no pertenece a $submoduloEsperado")
                }
                val codigoDoc = docTexto(snapshot, "codigo_principal", "codigo", "codigo_qr", "clave")
                if (!coincideCodigoHerramienta(linea.herramienta, codigoDoc)) {
                    throw IllegalStateException("El codigo de ${linea.herramienta.nombre} ya no coincide")
                }
                val total = numeroDocumento(snapshot, "cantidad_total", "stock_total", "cantidad")
                    .takeIf { it > 0.0 } ?: linea.herramienta.cantidadTotal
                val anterior = numeroDocumento(snapshot, "cantidad_ocupada", "ocupados").coerceAtLeast(0.0)
                val nueva = anterior + linea.cantidad
                val vehiculoAsignadoActual = docTexto(snapshot, "vehiculo_asignado", "vehiculoAsignado")
                if (!linea.esVehiculoPrincipal && (anterior > 0.0 || vehiculoAsignadoActual.isNotBlank())) {
                    throw IllegalStateException("${linea.herramienta.nombre} ya esta ocupado o asignado a otro vehiculo")
                }
                if (!linea.cantidad.isFinite() || linea.cantidad <= 0.0 || anterior >= total || nueva > total) {
                    throw IllegalStateException(
                        "Sin disponibilidad para ${linea.herramienta.nombre}. Disponible: ${formatoCantidadTaller((total - anterior).coerceAtLeast(0.0))}",
                    )
                }
                ResultadoLineaAsignacion(linea, total, anterior, nueva)
            }

            resultados.forEachIndexed { index, resultado ->
                val linea = resultado.linea
                val herramienta = linea.herramienta
                val disponibleNueva = (resultado.cantidadTotalActual - resultado.ocupacionNueva).coerceAtLeast(0.0)
                val referencia = referencias.getValue(linea.clave)
                val vehiculoAsignado = if (linea.esVehiculoPrincipal) "" else vehiculoNombre
                val estadoData = mutableMapOf<String, Any?>(
                    "estado" to "En uso",
                    "responsable" to confirmada.solicitante,
                    "asignado_a" to confirmada.solicitante,
                    "cantidad_ocupada" to resultado.ocupacionNueva,
                    "cantidad_disponible" to disponibleNueva,
                    "ultima_actualizacion" to fecha,
                    "actualizado_por_uid" to auth.currentUser?.uid.orEmpty(),
                )
                if (!linea.esVehiculoPrincipal) estadoData["vehiculo_asignado"] = vehiculoAsignado
                transaction.set(referencia, estadoData, SetOptions.merge())

                val observacionLinea = if (linea.esVehiculoPrincipal) {
                    confirmada.observaciones
                } else {
                    listOf(confirmada.observaciones, "Asignado a vehiculo $vehiculoNombre")
                        .filter { it.isNotBlank() }
                        .joinToString(". ")
                }
                transaction.set(movimientos.getValue(linea.clave), mapOf(
                    "fecha" to fecha,
                    "modulo" to TallerCanonicos.MODULO,
                    "tipoMovimiento" to "Salida",
                    "item" to herramienta.nombre,
                    "referencia" to herramienta.codigo,
                    "marca" to herramienta.marca,
                    "codigo" to herramienta.codigo,
                    "codigo_principal" to herramienta.codigo,
                    "codigo_qr" to herramienta.codigoQr,
                    "requiere_asignar_qr" to herramienta.requiereAsignarQr,
                    "categoria" to herramienta.subModulo,
                    "submodulo_taller" to herramienta.subModulo,
                    "subcategoria" to herramienta.subcategoria,
                    "ubicacion" to herramienta.ubicacion,
                    "tipo_herramienta" to herramienta.tipo,
                    "tamano" to herramienta.tamano,
                    "cantidad" to linea.cantidad.toString(),
                    "unidad" to herramienta.unidad.ifBlank { "UNIDAD" },
                    "solicitante" to confirmada.solicitante,
                    "asignado_a" to confirmada.solicitante,
                    "devuelto_por" to "",
                    "responsable_entrega" to responsableEntrega,
                    "usuario_uid" to auth.currentUser?.uid.orEmpty(),
                    "labor" to "Tipo labor: ${confirmada.labor} | Zona: ${confirmada.zona}",
                    "tipo_labor" to confirmada.labor,
                    "zona_ejecucion" to confirmada.zona,
                    "observaciones" to observacionLinea,
                    "fotoUrl" to linea.fotoUrl,
                    "herramientaId" to herramienta.id.toString(),
                    "herramienta_clave" to linea.clave,
                    "documento_id" to linea.clave,
                    "estado" to "En uso",
                    "fecha_salida" to fecha,
                    "fecha_entrada" to "",
                    "cantidad_total" to resultado.cantidadTotalActual,
                    "cantidad_ocupada_anterior" to resultado.ocupacionAnterior,
                    "cantidad_ocupada_nueva" to resultado.ocupacionNueva,
                    "cantidad_ocupada" to resultado.ocupacionNueva,
                    "cantidad_disponible" to disponibleNueva,
                    "asignacion_id" to confirmada.asignacionId,
                    "es_vehiculo_principal" to linea.esVehiculoPrincipal,
                    "vehiculo_asignado" to vehiculoAsignado,
                    "vehiculo_codigo" to confirmada.vehiculo.codigo,
                    "vehiculo_clave" to vehiculoClave,
                    "linea_asignacion" to index + 1,
                    "total_lineas" to lineas.size,
                ))
            }
            resultados
        }.addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure)
    }
}

internal fun MainActivity.aplicarAsignacionVehiculoLocal(
    confirmada: AsignacionVehiculoConfirmada,
    resultados: List<ResultadoLineaAsignacion>,
) {
    val fecha = confirmada.fecha
    val vehiculoNombre = "${confirmada.vehiculo.nombre} ${confirmada.vehiculo.codigo}".trim()
    resultados.forEach { resultado ->
        val linea = resultado.linea
        val herramienta = linea.herramienta
        val observacionLinea = if (linea.esVehiculoPrincipal) {
            confirmada.observaciones
        } else {
            listOf(confirmada.observaciones, "Asignado a vehiculo $vehiculoNombre")
                .filter { it.isNotBlank() }
                .joinToString(". ")
        }
        db.insertarMovimiento(Movimiento(
            fecha = fecha,
            modulo = TallerCanonicos.MODULO,
            tipoMovimiento = "Salida",
            item = herramienta.nombre,
            referencia = herramienta.codigo,
            marca = herramienta.marca,
            cantidad = linea.cantidad.toString(),
            unidad = herramienta.unidad.ifBlank { "UNIDAD" },
            solicitante = confirmada.solicitante,
            labor = "Tipo labor: ${confirmada.labor} | Zona: ${confirmada.zona}",
            maquinaria = herramienta.subModulo,
            herramientaId = herramienta.id.toString(),
            estado = "En uso",
            observaciones = listOf(observacionLinea, "__FOTO_URL__${linea.fotoUrl}")
                .filter { it.isNotBlank() }
                .joinToString("\n"),
            asignadoA = confirmada.solicitante,
        ))
        db.actualizarOcupacionHerramienta(herramienta.id, resultado.ocupacionNueva, confirmada.solicitante)
        if (!linea.esVehiculoPrincipal) {
            db.actualizarAsignacionVehiculo(herramienta.id, vehiculoNombre)
        }
    }
    marcarCacheTallerTrasCambio("salida_vehiculo_implementos_agrupada")
    Toast.makeText(this, "Asignacion registrada: ${resultados.size} elementos", Toast.LENGTH_LONG).show()
}
