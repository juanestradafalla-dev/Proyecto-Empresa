package com.arlessas.gestion

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

internal const val PRESTAMOS_ALERTAS_PREFS = "prestamos_alertas_taller"
internal const val PREF_NOTIFICACIONES_SOLICITADAS = "notificaciones_solicitadas"
private const val PREF_EXPLICACION_NOTIFICACIONES = "explicacion_notificaciones"
private const val PREF_NOTIFICACIONES_ACTIVAS = "notificaciones_activas"
private const val PREF_ULTIMA_REVISION_DIARIA = "ultima_revision_diaria"
private const val CANAL_PRESTAMOS = "prestamos_pendientes"
private const val GRUPO_NOTIFICACIONES = "prestamos_taller"
private const val WORK_REVISION_DIARIA = "prestamos_taller_revision_diaria"
private const val INPUT_REVISION_DIARIA = "revision_diaria"
private const val INPUT_ASIGNACION = "asignacion_id"
private const val EXTRA_ABRIR_PRESTAMOS = "abrir_prestamos_activos"
private const val EXTRA_ASIGNACION = "prestamo_asignacion_id"
private const val EXTRA_AMPLIAR = "prestamo_ampliar"
private val HORA_LIMITE_PRESTAMOS: LocalTime = LocalTime.of(12, 30)

internal fun calcularLimitePrestamoEpoch(
    fechaSalidaEpochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): Long {
    val salida = Instant.ofEpochMilli(fechaSalidaEpochMs).atZone(zona)
    var limite = salida.toLocalDate().atTime(HORA_LIMITE_PRESTAMOS).atZone(zona)
    if (salida.toLocalTime().isAfter(HORA_LIMITE_PRESTAMOS)) limite = limite.plusDays(1)
    return limite.toInstant().toEpochMilli()
}

internal fun proximaRevisionDiariaEpoch(
    desdeEpochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): Long {
    val desde = Instant.ofEpochMilli(desdeEpochMs).atZone(zona)
    var proxima = desde.toLocalDate().atTime(HORA_LIMITE_PRESTAMOS).atZone(zona)
    if (!proxima.isAfter(desde)) proxima = proxima.plusDays(1)
    return proxima.toInstant().toEpochMilli()
}

internal fun ventanaRevisionActualEpoch(
    ahoraEpochMs: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): Long {
    val ahora = Instant.ofEpochMilli(ahoraEpochMs).atZone(zona)
    return ahora.toLocalDate().atTime(HORA_LIMITE_PRESTAMOS).atZone(zona).toInstant().toEpochMilli()
}

internal fun calcularAmpliacionRapidaEpoch(
    plazoActualEpochMs: Long,
    ahoraEpochMs: Long,
    minutos: Long,
): Long = maxOf(plazoActualEpochMs, ahoraEpochMs).plusSeguro(Duration.ofMinutes(minutos).toMillis())

private fun Long.plusSeguro(delta: Long): Long = if (Long.MAX_VALUE - this < delta) Long.MAX_VALUE else this + delta

internal fun esFechaAmpliacionValida(
    nuevoPlazoEpochMs: Long,
    plazoActualEpochMs: Long,
    ahoraEpochMs: Long,
): Boolean = nuevoPlazoEpochMs > ahoraEpochMs && nuevoPlazoEpochMs > plazoActualEpochMs

internal fun claveAvisoPrestamo(asignacionId: String, ventanaEpochMs: Long): String =
    "avisado_${asignacionId}_$ventanaEpochMs"

internal fun formatoPlazoPrestamo(epochMs: Long, zona: ZoneId = ZoneId.systemDefault()): String {
    if (epochMs <= 0L) return "Sin plazo registrado"
    return DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", Locale("es", "CO"))
        .format(Instant.ofEpochMilli(epochMs).atZone(zona))
}

private fun parseFechaMovimientoEpoch(texto: String, zona: ZoneId = ZoneId.systemDefault()): Long {
    val formatos = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")
    formatos.forEach { patron ->
        runCatching {
            return LocalDateTime.parse(texto.trim(), DateTimeFormatter.ofPattern(patron))
                .atZone(zona)
                .toInstant()
                .toEpochMilli()
        }
    }
    return 0L
}

internal data class PrestamoAlarmaElemento(
    val documentoId: String,
    val asignacionId: String,
    val nombre: String,
    val codigo: String,
    val submodulo: String,
    val asignadoA: String,
    val cantidadOcupada: Double,
    val fechaSalida: String,
    val fechaLimiteEpochMs: Long,
    val esConsumible: Boolean,
    val alarmaActiva: Boolean,
)

internal data class PrestamoAlarmaGrupo(
    val asignacionId: String,
    val asignadoA: String,
    val elementos: List<PrestamoAlarmaElemento>,
    val fechaLimiteEpochMs: Long,
)

internal fun agruparPrestamosParaAlarma(
    elementos: List<PrestamoAlarmaElemento>,
    ahoraEpochMs: Long,
): List<PrestamoAlarmaGrupo> {
    val ventanaLegacy = ventanaRevisionActualEpoch(ahoraEpochMs)
    return elementos
        .filter { it.cantidadOcupada > 0.0 && !it.esConsumible && it.alarmaActiva }
        .groupBy { it.asignacionId.ifBlank { "LEGACY-${it.documentoId}" } }
        .map { (asignacion, lineas) ->
            val plazo = lineas.map { it.fechaLimiteEpochMs }.filter { it > 0L }.maxOrNull() ?: ventanaLegacy
            PrestamoAlarmaGrupo(
                asignacionId = asignacion,
                asignadoA = lineas.firstNotNullOfOrNull { it.asignadoA.takeIf(String::isNotBlank) }
                    ?: "No identificado",
                elementos = lineas,
                fechaLimiteEpochMs = plazo,
            )
        }
        .sortedBy { it.fechaLimiteEpochMs }
}

internal fun crearCanalPrestamos(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val canal = NotificationChannel(
        CANAL_PRESTAMOS,
        "Préstamos pendientes",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Alertas de herramientas pendientes de devolución"
        enableVibration(true)
        setShowBadge(true)
    }
    manager.createNotificationChannel(canal)
}

internal fun programarRevisionDiariaPrestamos(context: Context, desdeWorker: Boolean = false) {
    val ahora = System.currentTimeMillis()
    val ventanaActual = ventanaRevisionActualEpoch(ahora)
    val ultimaRevision = context.getSharedPreferences(PRESTAMOS_ALERTAS_PREFS, Context.MODE_PRIVATE)
        .getLong(PREF_ULTIMA_REVISION_DIARIA, 0L)
    val revisionPendienteHoy = !desdeWorker && ahora >= ventanaActual && ultimaRevision < ventanaActual
    val demora = if (revisionPendienteHoy) 1_000L
    else (proximaRevisionDiariaEpoch(ahora) - ahora).coerceAtLeast(1_000L)
    val request = OneTimeWorkRequestBuilder<PrestamosPendientesWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(demora, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf(INPUT_REVISION_DIARIA to true))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        WORK_REVISION_DIARIA,
        if (desdeWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
        request,
    )
}

internal fun programarAlarmaAsignacion(context: Context, asignacionId: String, fechaEpochMs: Long) {
    if (asignacionId.isBlank() || fechaEpochMs <= 0L) return
    val demora = (fechaEpochMs - System.currentTimeMillis()).coerceAtLeast(1_000L)
    val request = OneTimeWorkRequestBuilder<PrestamosPendientesWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(demora, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf(INPUT_ASIGNACION to asignacionId))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        nombreTrabajoAsignacion(asignacionId),
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

internal fun cancelarAlarmaAsignacion(context: Context, asignacionId: String) {
    if (asignacionId.isBlank()) return
    WorkManager.getInstance(context).cancelUniqueWork(nombreTrabajoAsignacion(asignacionId))
    NotificationManagerCompat.from(context).cancel(idNotificacion(asignacionId))
    val prefs = context.getSharedPreferences(PRESTAMOS_ALERTAS_PREFS, Context.MODE_PRIVATE)
    val activas = prefs.getStringSet(PREF_NOTIFICACIONES_ACTIVAS, emptySet()).orEmpty().toMutableSet()
    prefs.edit {
        activas.remove(asignacionId)
        putStringSet(PREF_NOTIFICACIONES_ACTIVAS, activas)
    }
}

private fun nombreTrabajoAsignacion(asignacionId: String): String =
    "prestamo_asignacion_${asignacionId.hashCode().absoluteValue}"

private fun idNotificacion(asignacionId: String): Int = 20_000 + (asignacionId.hashCode().absoluteValue % 700_000)

class PrestamosPendientesWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val esRevisionDiaria = inputData.getBoolean(INPUT_REVISION_DIARIA, false)
        return try {
            crearCanalPrestamos(applicationContext)
            val snapshot = Tasks.await(
                FirebaseFirestore.getInstance().collection("herramientas").get(Source.SERVER),
            )
            val ahora = System.currentTimeMillis()
            val elementos = snapshot.documents.mapNotNull { doc -> elementoAlarmaDesdeDocumento(doc) }
            val grupos = agruparPrestamosParaAlarma(elementos, ahora)
            val asignacionObjetivo = inputData.getString(INPUT_ASIGNACION).orEmpty()
            val vencidos = grupos.filter {
                it.fechaLimiteEpochMs <= ahora &&
                    (asignacionObjetivo.isBlank() || it.asignacionId == asignacionObjetivo)
            }
            reconciliarNotificaciones(applicationContext, grupos, vencidos, ahora)
            if (esRevisionDiaria) {
                applicationContext.getSharedPreferences(PRESTAMOS_ALERTAS_PREFS, Context.MODE_PRIVATE).edit {
                    putLong(PREF_ULTIMA_REVISION_DIARIA, ventanaRevisionActualEpoch(ahora))
                }
                programarRevisionDiariaPrestamos(applicationContext, desdeWorker = true)
            }
            Result.success()
        } catch (error: Exception) {
            android.util.Log.e("AlarmasTaller", "No se pudo verificar Firestore", error)
            Result.retry()
        }
    }
}

private fun elementoAlarmaDesdeDocumento(doc: DocumentSnapshot): PrestamoAlarmaElemento? {
    val modulo = docTexto(doc, "modulo")
    val submodulo = docTexto(doc, "submodulo_taller", "submodulo", "categoria", "ubicacion")
    val perteneceTaller = TallerCanonicos.esModuloTaller(modulo) ||
        TallerCanonicos.SUBMODULOS.any { TallerCanonicos.coincideSubmoduloTaller(submodulo, it) }
    if (!perteneceTaller) return null
    val nombre = docTexto(doc, "nombre", "item", "producto", "descripcion")
    if (nombre.isBlank()) return null
    val alarmaRaw = doc.get("alarma_prestamo_activa")
    val alarmaActiva = when (alarmaRaw) {
        is Boolean -> alarmaRaw
        is Number -> alarmaRaw.toInt() != 0
        is String -> !alarmaRaw.equals("false", true) && alarmaRaw != "0"
        else -> true
    }
    return PrestamoAlarmaElemento(
        documentoId = doc.id,
        asignacionId = docTexto(doc, "asignacion_activa_id", "asignacion_id"),
        nombre = nombre,
        codigo = docTexto(doc, "codigo_principal", "codigo", "codigo_qr").ifBlank { doc.id },
        submodulo = submodulo,
        asignadoA = docTexto(doc, "asignado_a", "responsable").ifBlank { "No identificado" },
        cantidadOcupada = numeroDocumento(doc, "cantidad_ocupada", "ocupados"),
        fechaSalida = docTexto(doc, "fecha_salida_prestamo", "fecha_salida"),
        fechaLimiteEpochMs = numeroDocumento(doc, "fecha_limite_devolucion_epoch_ms").toLong(),
        esConsumible = booleanoDocumento(doc, "es_consumible", "consumible"),
        alarmaActiva = alarmaActiva,
    )
}

private fun reconciliarNotificaciones(
    context: Context,
    gruposActivos: List<PrestamoAlarmaGrupo>,
    gruposVencidos: List<PrestamoAlarmaGrupo>,
    ahoraEpochMs: Long,
) {
    val prefs = context.getSharedPreferences(PRESTAMOS_ALERTAS_PREFS, Context.MODE_PRIVATE)
    val manager = NotificationManagerCompat.from(context)
    val activasAnteriores = prefs.getStringSet(PREF_NOTIFICACIONES_ACTIVAS, emptySet()).orEmpty()
    val vencidasIds = gruposVencidos.map { it.asignacionId }.toSet()
    activasAnteriores.filter { it !in vencidasIds }.forEach { manager.cancel(idNotificacion(it)) }

    val puedeNotificar = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (puedeNotificar) {
        gruposVencidos.forEach { grupo ->
            val ventana = if (grupo.fechaLimiteEpochMs == ventanaRevisionActualEpoch(ahoraEpochMs)) {
                grupo.fechaLimiteEpochMs
            } else if (grupo.fechaLimiteEpochMs < ventanaRevisionActualEpoch(ahoraEpochMs)) {
                ventanaRevisionActualEpoch(ahoraEpochMs)
            } else {
                grupo.fechaLimiteEpochMs
            }
            val claveAviso = claveAvisoPrestamo(grupo.asignacionId, ventana)
            if (!prefs.getBoolean(claveAviso, false)) {
                mostrarNotificacionPrestamo(context, grupo)
                prefs.edit { putBoolean(claveAviso, true) }
            }
        }
        if (gruposVencidos.size > 1) mostrarResumenNotificaciones(context, gruposVencidos)
        else manager.cancel(19_999)
    }
    prefs.edit { putStringSet(PREF_NOTIFICACIONES_ACTIVAS, vencidasIds) }

    if (gruposActivos.isEmpty()) {
        activasAnteriores.forEach { manager.cancel(idNotificacion(it)) }
        manager.cancel(19_999)
    }
}

private fun mostrarNotificacionPrestamo(context: Context, grupo: PrestamoAlarmaGrupo) {
    val nombres = grupo.elementos.map { it.nombre }.distinct()
    val resumen = nombres.take(3).joinToString(", ") + if (nombres.size > 3) " y ${nombres.size - 3} más" else ""
    val texto = "${grupo.asignadoA} tiene ${nombres.size} elemento(s) pendiente(s): $resumen."
    val verIntent = intentPrestamos(context, grupo.asignacionId, false)
    val ampliarIntent = intentPrestamos(context, grupo.asignacionId, true)
    val verPending = PendingIntent.getActivity(
        context,
        idNotificacion(grupo.asignacionId),
        verIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val ampliarPending = PendingIntent.getActivity(
        context,
        idNotificacion(grupo.asignacionId) + 1,
        ampliarIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CANAL_PRESTAMOS)
        .setSmallIcon(R.drawable.ic_tools)
        .setContentTitle("Préstamo pendiente de devolución")
        .setContentText(texto)
        .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setVibrate(longArrayOf(0, 250, 150, 250))
        .setContentIntent(verPending)
        .addAction(R.drawable.ic_tools, "Ver préstamos", verPending)
        .addAction(R.drawable.ic_changelog, "Ampliar tiempo", ampliarPending)
        .setGroup(GRUPO_NOTIFICACIONES)
        .build()
    NotificationManagerCompat.from(context).notify(idNotificacion(grupo.asignacionId), notification)
}

private fun mostrarResumenNotificaciones(context: Context, grupos: List<PrestamoAlarmaGrupo>) {
    val style = NotificationCompat.InboxStyle()
        .setSummaryText("${grupos.size} asignaciones pendientes")
    grupos.take(5).forEach { style.addLine("${it.asignadoA}: ${it.elementos.size} elemento(s)") }
    val summary = NotificationCompat.Builder(context, CANAL_PRESTAMOS)
        .setSmallIcon(R.drawable.ic_tools)
        .setContentTitle("Préstamos del Taller")
        .setContentText("${grupos.size} asignaciones pendientes de devolución")
        .setStyle(style)
        .setGroup(GRUPO_NOTIFICACIONES)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    NotificationManagerCompat.from(context).notify(19_999, summary)
}

private fun intentPrestamos(context: Context, asignacionId: String, ampliar: Boolean): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_ABRIR_PRESTAMOS, true)
        putExtra(EXTRA_ASIGNACION, asignacionId)
        putExtra(EXTRA_AMPLIAR, ampliar)
    }
}

internal fun MainActivity.inicializarAlertasPrestamosTrasLogin() {
    crearCanalPrestamos(this)
    programarRevisionDiariaPrestamos(this)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    val prefs = getSharedPreferences(PRESTAMOS_ALERTAS_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(PREF_NOTIFICACIONES_SOLICITADAS, false) ||
        prefs.getBoolean(PREF_EXPLICACION_NOTIFICACIONES, false)
    ) return
    prefs.edit { putBoolean(PREF_EXPLICACION_NOTIFICACIONES, true) }
    AlertDialog.Builder(this)
        .setTitle("Alertas de préstamos")
        .setMessage("Permite las notificaciones para recibir alertas de herramientas pendientes de devolución")
        .setPositiveButton("Permitir") { _, _ ->
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        .setNegativeButton("Ahora no") { _, _ ->
            prefs.edit { putBoolean(PREF_NOTIFICACIONES_SOLICITADAS, true) }
        }
        .show()
}

internal fun MainActivity.capturarNavegacionPrestamo(intent: Intent?) {
    if (intent?.getBooleanExtra(EXTRA_ABRIR_PRESTAMOS, false) != true) return
    prestamoAsignacionPendiente = intent.getStringExtra(EXTRA_ASIGNACION).orEmpty()
    prestamoAmpliacionPendiente = intent.getBooleanExtra(EXTRA_AMPLIAR, false)
    intent.removeExtra(EXTRA_ABRIR_PRESTAMOS)
    intent.removeExtra(EXTRA_ASIGNACION)
    intent.removeExtra(EXTRA_AMPLIAR)
}

internal fun MainActivity.abrirNavegacionPrestamoPendiente() {
    if (auth.currentUser == null) return
    if (prestamoAsignacionPendiente.isBlank() && !prestamoAmpliacionPendiente) return
    val asignacion = prestamoAsignacionPendiente
    val ampliar = prestamoAmpliacionPendiente
    prestamoAsignacionPendiente = ""
    prestamoAmpliacionPendiente = false
    window.decorView.postDelayed({
        if (!isFinishing && !isDestroyed) abrirDialogoPrestamosActivosTaller(asignacion, ampliar)
    }, 500L)
}

internal data class PrestamoTallerGrupoUi(
    val asignacionId: String,
    val prestamos: List<PrestamoTallerActivo>,
    val asignadoA: String,
    val fechaSalida: String,
    val fechaLimiteEpochMs: Long,
    val ampliado: Boolean,
)

internal fun agruparPrestamosTallerUi(prestamos: List<PrestamoTallerActivo>): List<PrestamoTallerGrupoUi> {
    return prestamos.groupBy { prestamo ->
        prestamo.herramienta.asignacionActivaId
            .ifBlank { prestamo.movimientoSalida?.asignacionId.orEmpty() }
            .ifBlank { "LEGACY-${claveHerramientaCloud(prestamo.herramienta)}" }
    }.map { (asignacionId, lineas) ->
        val fechaSalida = lineas.mapNotNull { it.movimientoSalida?.fecha?.takeIf(String::isNotBlank) }.maxOrNull().orEmpty()
        val plazoRegistrado = lineas.map { it.herramienta.fechaLimiteDevolucionEpochMs }
            .plus(lineas.map { it.movimientoSalida?.fechaLimiteDevolucionEpochMs ?: 0L })
            .filter { it > 0L }
            .maxOrNull() ?: 0L
        val salidaEpoch = parseFechaMovimientoEpoch(fechaSalida)
        val plazo = plazoRegistrado.takeIf { it > 0L }
            ?: salidaEpoch.takeIf { it > 0L }?.let(::calcularLimitePrestamoEpoch)
            ?: ventanaRevisionActualEpoch(System.currentTimeMillis())
        val limiteBase = salidaEpoch.takeIf { it > 0L }?.let(::calcularLimitePrestamoEpoch) ?: plazo
        PrestamoTallerGrupoUi(
            asignacionId = asignacionId,
            prestamos = lineas,
            asignadoA = lineas.firstNotNullOfOrNull { it.nombreAsignado().takeIf(String::isNotBlank) }
                ?: "No identificado",
            fechaSalida = fechaSalida,
            fechaLimiteEpochMs = plazo,
            ampliado = plazo > limiteBase,
        )
    }.sortedBy { it.fechaLimiteEpochMs }
}

private fun estadoPlazo(grupo: PrestamoTallerGrupoUi, ahora: Long = System.currentTimeMillis()): Pair<String, Int> {
    if (grupo.fechaLimiteEpochMs <= ahora) return "Vencido" to ArlesPalette.danger
    if (grupo.ampliado) return "Tiempo ampliado" to ArlesPalette.blueAction
    if (grupo.fechaLimiteEpochMs - ahora <= TimeUnit.HOURS.toMillis(1)) return "Vence pronto" to ArlesPalette.warning
    return "En plazo" to ArlesPalette.green800
}

internal fun MainActivity.mostrarDialogoPrestamosActivosAgrupados(
    movimientos: List<Movimiento>,
    asignacionDestacada: String = "",
    abrirAmpliacion: Boolean = false,
) {
    val grupos = agruparPrestamosTallerUi(prestamosActivosTaller(movimientos))
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(ArlesPalette.soft, null, 18)
        setPadding(dp(18), dp(18), dp(18), dp(14))
    }
    content.addView(TextView(this).apply {
        text = "Préstamos activos"
        textSize = 20f
        setTextColor(ArlesPalette.ink)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    })
    content.addView(TextView(this).apply {
        text = "${grupos.size} asignación(es) · ${grupos.sumOf { it.prestamos.size }} elemento(s)"
        textSize = 12f
        setTextColor(ArlesPalette.muted)
        setPadding(0, dp(2), 0, dp(12))
    })

    if (!notificacionesPrestamosHabilitadas()) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = arlesRoundedBackground(ArlesPalette.green100, ArlesPalette.warning, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(TextView(context).apply {
                text = "Las notificaciones están desactivadas. Actívalas para recibir avisos de devolución."
                textSize = 12f
                setTextColor(ArlesPalette.ink)
            })
            addView(styledButton("Abrir configuración", ArlesButtonStyle.OUTLINE) {
                abrirConfiguracionNotificacionesPrestamos()
            })
        })
    }

    val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    if (grupos.isEmpty()) {
        lista.addView(infoText("No hay herramientas prestadas en este momento."))
    } else {
        grupos.forEach { grupo -> lista.addView(tarjetaPrestamoAgrupado(grupo, grupo.asignacionId == asignacionDestacada)) }
    }
    content.addView(ScrollView(this).apply {
        addView(lista)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(500))
    })
    val dialog = AlertDialog.Builder(this).setView(content).setPositiveButton("Cerrar", null).create()
    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val objetivo = grupos.firstOrNull { it.asignacionId == asignacionDestacada }
        if (abrirAmpliacion && objetivo != null) mostrarDialogoAmpliarPrestamo(objetivo) {
            dialog.dismiss()
            abrirDialogoPrestamosActivosTaller(objetivo.asignacionId, false)
        }
    }
    dialog.show()
}

private fun MainActivity.tarjetaPrestamoAgrupado(grupo: PrestamoTallerGrupoUi, destacada: Boolean): LinearLayout {
    val (estado, colorEstado) = estadoPlazo(grupo)
    val submodulos = grupo.prestamos.map { it.herramienta.subModulo }.distinct().joinToString(" · ")
    val zonas = grupo.prestamos.map { ubicacionPrestamoTaller(it.herramienta, it.movimientoSalida) }.distinct().joinToString(" · ")
    val elementos = grupo.prestamos.joinToString("\n") {
        "• ${it.herramienta.nombre} · ${formatoCantidadTaller(it.herramienta.ocupados())} ${it.herramienta.unidad}"
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, if (destacada) ArlesPalette.warning else ArlesPalette.line, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(10)) }
        addView(TextView(context).apply {
            text = "Prestado a: ${grupo.asignadoA}"
            textSize = 15f
            setTextColor(ArlesPalette.ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(statusBadge(estado, colorEstado))
        addView(prestamoDialogInfoRow("Elementos", elementos, colorEstado))
        addView(prestamoDialogInfoRow("Submódulo", submodulos, colorEstado))
        addView(prestamoDialogInfoRow("Área", zonas, colorEstado))
        addView(prestamoDialogInfoRow("Salida", grupo.fechaSalida.ifBlank { "Histórica" }, colorEstado))
        addView(prestamoDialogInfoRow("Hora límite", formatoPlazoPrestamo(grupo.fechaLimiteEpochMs), colorEstado))
        addView(styledButton("Ampliar tiempo", ArlesButtonStyle.OUTLINE) {
            mostrarDialogoAmpliarPrestamo(grupo) {
                abrirDialogoPrestamosActivosTaller(grupo.asignacionId, false)
            }
        })
    }
}

private fun MainActivity.notificacionesPrestamosHabilitadas(): Boolean {
    val permiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permiso && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

private fun MainActivity.abrirConfiguracionNotificacionesPrestamos() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        data = Uri.parse("package:$packageName")
    })
}

private fun MainActivity.mostrarDialogoAmpliarPrestamo(
    grupo: PrestamoTallerGrupoUi,
    onSuccess: () -> Unit,
) {
    if (!isNetworkAvailable()) {
        Toast.makeText(this, "Se requiere conexión para ampliar el plazo", Toast.LENGTH_LONG).show()
        return
    }
    val opciones = arrayOf("+30 minutos", "+1 hora", "+2 horas", "Elegir fecha y hora")
    AlertDialog.Builder(this)
        .setTitle("Ampliar tiempo")
        .setItems(opciones) { _, posicion ->
            when (posicion) {
                0 -> solicitarMotivoAmpliacion(grupo, calcularAmpliacionRapidaEpoch(grupo.fechaLimiteEpochMs, System.currentTimeMillis(), 30), onSuccess)
                1 -> solicitarMotivoAmpliacion(grupo, calcularAmpliacionRapidaEpoch(grupo.fechaLimiteEpochMs, System.currentTimeMillis(), 60), onSuccess)
                2 -> solicitarMotivoAmpliacion(grupo, calcularAmpliacionRapidaEpoch(grupo.fechaLimiteEpochMs, System.currentTimeMillis(), 120), onSuccess)
                else -> elegirFechaAmpliacion(grupo, onSuccess)
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

private fun MainActivity.elegirFechaAmpliacion(grupo: PrestamoTallerGrupoUi, onSuccess: () -> Unit) {
    val base = Instant.ofEpochMilli(maxOf(grupo.fechaLimiteEpochMs, System.currentTimeMillis()))
        .atZone(ZoneId.systemDefault())
        .plusDays(1)
    DatePickerDialog(this, { _, year, month, day ->
        TimePickerDialog(this, { _, hour, minute ->
            val epoch = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            if (!esFechaAmpliacionValida(epoch, grupo.fechaLimiteEpochMs, System.currentTimeMillis())) {
                Toast.makeText(this, "La nueva fecha debe ser futura y posterior al plazo actual", Toast.LENGTH_LONG).show()
                return@TimePickerDialog
            }
            solicitarMotivoAmpliacion(grupo, epoch, onSuccess)
        }, base.hour, base.minute, false).show()
    }, base.year, base.monthValue - 1, base.dayOfMonth).show()
}

private fun MainActivity.solicitarMotivoAmpliacion(
    grupo: PrestamoTallerGrupoUi,
    nuevoPlazo: Long,
    onSuccess: () -> Unit,
) {
    val motivo = EditText(this).apply {
        hint = "Motivo obligatorio"
        minLines = 2
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle("Motivo de la ampliación")
        .setView(motivo)
        .setPositiveButton("Continuar", null)
        .setNegativeButton("Cancelar", null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val texto = motivo.text.toString().trim()
            if (texto.isBlank()) {
                motivo.error = "Escribe el motivo"
                return@setOnClickListener
            }
            dialog.dismiss()
            confirmarAmpliacion(grupo, nuevoPlazo, texto, onSuccess)
        }
    }
    dialog.show()
}

private fun MainActivity.confirmarAmpliacion(
    grupo: PrestamoTallerGrupoUi,
    nuevoPlazo: Long,
    motivo: String,
    onSuccess: () -> Unit,
) {
    val elementos = grupo.prestamos.joinToString("\n") { "• ${it.herramienta.nombre}" }
    val dialog = AlertDialog.Builder(this)
        .setTitle("Confirmar ampliación")
        .setMessage(
            "Persona: ${grupo.asignadoA}\n\nElementos:\n$elementos\n\n" +
                "Plazo anterior: ${formatoPlazoPrestamo(grupo.fechaLimiteEpochMs)}\n" +
                "Plazo nuevo: ${formatoPlazoPrestamo(nuevoPlazo)}\n\nMotivo: $motivo",
        )
        .setPositiveButton("Guardar ampliación", null)
        .setNegativeButton("Cancelar", null)
        .create()
    dialog.setOnShowListener {
        val guardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        guardar.setOnClickListener {
            if (!esFechaAmpliacionValida(nuevoPlazo, grupo.fechaLimiteEpochMs, System.currentTimeMillis())) {
                Toast.makeText(this, "El nuevo plazo ya no es válido", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            guardar.isEnabled = false
            guardar.text = "Guardando..."
            ampliarPrestamoEnFirestore(grupo, nuevoPlazo, motivo,
                onSuccess = {
                    dialog.dismiss()
                    onSuccess()
                },
                onFailure = { error ->
                    guardar.isEnabled = true
                    guardar.text = "Guardar ampliación"
                    Toast.makeText(this, error.localizedMessage ?: "No se pudo ampliar el plazo", Toast.LENGTH_LONG).show()
                },
            )
        }
    }
    dialog.show()
}

private fun MainActivity.ampliarPrestamoEnFirestore(
    grupo: PrestamoTallerGrupoUi,
    nuevoPlazo: Long,
    motivo: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit,
) {
    if (!isNetworkAvailable()) {
        onFailure(IllegalStateException("Se requiere conexión para ampliar el plazo"))
        return
    }
    val lineas = grupo.prestamos.map { it.herramienta.copy() }
    val claves = lineas.map(::claveHerramientaCloud)
    if (claves.distinct().size != claves.size) {
        onFailure(IllegalStateException("La asignación contiene documentos repetidos"))
        return
    }
    obtenerInfoUsuario(auth.currentUser?.uid.orEmpty()) { usuario ->
        val referencias = claves.associateWith { firestore.collection("herramientas").document(it) }
        val auditoria = firestore.collection("movimientos").document()
        val fecha = now()
        firestore.runTransaction { transaction ->
            val snapshots = referencias.mapValues { transaction.get(it.value) }
            snapshots.forEach { (clave, snapshot) ->
                if (!snapshot.exists()) throw IllegalStateException("Ya no existe el documento $clave")
                if (numeroDocumento(snapshot, "cantidad_ocupada", "ocupados") <= 0.0) {
                    throw IllegalStateException("Uno de los elementos ya fue devuelto")
                }
                if (booleanoDocumento(snapshot, "es_consumible", "consumible")) {
                    throw IllegalStateException("Un consumible no puede tener alarma de préstamo")
                }
                val asignacionActual = docTexto(snapshot, "asignacion_activa_id", "asignacion_id")
                if (asignacionActual.isNotBlank() && asignacionActual != grupo.asignacionId) {
                    throw IllegalStateException("La asignación cambió en otro dispositivo")
                }
            }
            referencias.forEach { (_, referencia) ->
                transaction.set(referencia, mapOf(
                    "asignacion_activa_id" to grupo.asignacionId,
                    "fecha_limite_devolucion" to formatoPlazoPrestamo(nuevoPlazo),
                    "fecha_limite_devolucion_epoch_ms" to nuevoPlazo,
                    "alarma_prestamo_activa" to true,
                    "ultima_actualizacion" to fecha,
                ), SetOptions.merge())
            }
            transaction.set(auditoria, mapOf(
                "fecha" to fecha,
                "modulo" to TallerCanonicos.MODULO,
                "tipoMovimiento" to "Ampliación préstamo",
                "asignacion_id" to grupo.asignacionId,
                "asignado_a" to grupo.asignadoA,
                "elementos" to lineas.map { it.nombre },
                "plazo_anterior" to formatoPlazoPrestamo(grupo.fechaLimiteEpochMs),
                "plazo_anterior_epoch_ms" to grupo.fechaLimiteEpochMs,
                "plazo_nuevo" to formatoPlazoPrestamo(nuevoPlazo),
                "plazo_nuevo_epoch_ms" to nuevoPlazo,
                "motivo_ampliacion" to motivo,
                "ampliado_por" to usuario,
                "ampliado_por_uid" to auth.currentUser?.uid.orEmpty(),
                "fecha_ampliacion" to fecha,
            ))
        }.addOnSuccessListener {
            lineas.forEach { db.activarAlertaPrestamo(it.id, grupo.asignacionId, nuevoPlazo) }
            cancelarAlarmaAsignacion(this, grupo.asignacionId)
            programarAlarmaAsignacion(this, grupo.asignacionId, nuevoPlazo)
            marcarCacheTallerTrasCambio("ampliacion_prestamo")
            onSuccess()
        }.addOnFailureListener(onFailure)
    }
}
