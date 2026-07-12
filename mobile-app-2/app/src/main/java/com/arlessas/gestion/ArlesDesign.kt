@file:Suppress("unused")

package com.arlessas.gestion

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

internal object ArlesPalette {
    // Marca corporativa (styles.xml: #144619, #1E6B52, #12FF3A)
    val brandVariant = Color.rgb(20, 70, 25)
    val brandAccent = Color.rgb(30, 107, 82)
    val brandVivid = Color.rgb(5, 166, 75)

    val green900 = Color.rgb(18, 63, 35)
    val green800 = Color.rgb(22, 83, 44)
    val green700 = Color.rgb(8, 123, 59)
    val green500 = brandVivid
    val green100 = Color.rgb(231, 244, 228)
    val ink = Color.rgb(23, 33, 24)
    val muted = Color.rgb(88, 108, 92)
    val line = Color.rgb(200, 220, 204)
    val soft = Color.rgb(238, 245, 238)
    val surfaceAlt = Color.rgb(225, 240, 228)
    val primaryAction = green700
    val primaryActionDark = green800
    val blueAction = Color.rgb(24, 150, 110)
    val slateAction = Color.rgb(72, 118, 82)
    val danger = Color.rgb(192, 57, 43)
    val warning = Color.rgb(201, 130, 19)
    val accentCombustible = Color.rgb(198, 122, 28)
    val accentQuimico = Color.rgb(16, 138, 102)
    val accentTaller = Color.rgb(30, 107, 82)
    val accentAseo = Color.rgb(0, 158, 150)
    val accentEpp = Color.rgb(46, 145, 78)
    val accentDotacion = Color.rgb(12, 132, 72)
    val accentLubricantes = Color.rgb(38, 128, 95)
    val accentConsumibles = green700
}

internal enum class ArlesButtonStyle {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    MUTED,
    TALLER,
}

internal fun MainActivity.arlesRoundedBackground(
    fill: Int,
    stroke: Int? = null,
    radiusDp: Int = 12,
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
}

internal fun MainActivity.styledButton(
    text: String,
    style: ArlesButtonStyle = ArlesButtonStyle.PRIMARY,
    action: (View) -> Unit,
): Button {
    val (bg, fg, stroke) = when (style) {
        ArlesButtonStyle.PRIMARY -> Triple(ArlesPalette.primaryAction, Color.WHITE, null)
        ArlesButtonStyle.SECONDARY -> Triple(ArlesPalette.blueAction, Color.WHITE, null)
        ArlesButtonStyle.OUTLINE -> Triple(Color.WHITE, ArlesPalette.primaryActionDark, ArlesPalette.line)
        ArlesButtonStyle.MUTED -> Triple(ArlesPalette.slateAction, Color.WHITE, null)
        ArlesButtonStyle.TALLER -> Triple(ArlesPalette.accentTaller, Color.WHITE, null)
    }
    return Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(fg)
        background = arlesRoundedBackground(bg, stroke, 12)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setAnimatedClick(this, action)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52),
        ).apply { setMargins(0, dp(8), 0, dp(6)) }
        animateViewIn(this)
    }
}

internal fun MainActivity.iconTextButton(
    label: String,
    iconRes: Int,
    backgroundColor: Int,
    action: (View) -> Unit,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = arlesRoundedBackground(backgroundColor, null, 12)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isClickable = true
        isFocusable = true
        setAnimatedClick(this, action)
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                setMargins(0, 0, dp(8), 0)
            }
        })
        addView(TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        animateViewIn(this)
    }
}

internal fun MainActivity.stockInfoCard(message: String): TextView {
    return TextView(this).apply {
        text = message
        textSize = 14f
        setTextColor(ArlesPalette.muted)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = arlesRoundedBackground(ArlesPalette.green100, ArlesPalette.line, 14)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(4), 0, dp(12)) }
        animateViewIn(this)
    }
}

internal fun TextView.renderStockSummary(
    title: String,
    lines: List<Pair<String, String>>,
    highlightKey: String? = null,
    positiveColor: Int = ArlesPalette.green800,
    emptyColor: Int = ArlesPalette.danger,
) {
    val body = lines.joinToString("\n") { (label, value) -> "$label: $value" }
    text = if (body.isBlank()) title else "$title\n$body"
    val highlight = lines.find { it.first.equals(highlightKey, ignoreCase = true) }?.second.orEmpty()
    val numeric = highlight.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    setTextColor(if (numeric > 0.0) positiveColor else emptyColor)
}

internal fun MainActivity.dialogShell(): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 18)
    }
}

internal fun MainActivity.applyDialogWindow(dialog: Dialog) {
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
}

internal fun MainActivity.styledSpinner(root: LinearLayout, label: String, values: List<String>): Spinner {
    val labelView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(verdeOscuro)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(4))
    }
    root.addView(labelView)
    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    val sp = Spinner(this).apply {
        this.adapter = adapter
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    root.addView(sp, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
    return sp
}

internal fun MainActivity.evidenceButton(action: (View) -> Unit): Button {
    return styledButton("Tomar evidencia", ArlesButtonStyle.MUTED, action)
}

internal fun MainActivity.gestionNuevoEntradaRow(
    onNuevo: () -> Unit,
    onEntrada: () -> Unit,
    textoNuevo: String = "Añadir nuevo",
    textoEntrada: String = "Nueva entrada",
): LinearLayout {
    return gestionRow(
        iconTextButton(textoNuevo, R.drawable.ic_add, ArlesPalette.primaryAction) { onNuevo() },
        iconTextButton(textoEntrada, R.drawable.ic_inbox, ArlesPalette.blueAction) { onEntrada() },
    )
}

internal fun moduloInventarioAccent(modulo: String): Int {
    return when {
        modulo.equals("Consumibles", ignoreCase = true) -> ArlesPalette.accentConsumibles
        modulo.equals(AseoCanonicos.MODULO, ignoreCase = true) || modulo.equals("Aseo", ignoreCase = true) ->
            ArlesPalette.accentAseo
        ModulosInventario.esModuloAgroquimico(modulo) -> ArlesPalette.accentQuimico
        TallerCanonicos.esModuloTaller(modulo) -> ArlesPalette.accentTaller
        ModulosInventario.esModuloLubricantesTaller(modulo) -> ArlesPalette.accentLubricantes
        modulo.equals("EPP", ignoreCase = true) -> ArlesPalette.accentEpp
        modulo.equals("Dotación", ignoreCase = true) || modulo.equals("Dotacion", ignoreCase = true) ->
            ArlesPalette.accentDotacion
        modulo.equals("Combustible", ignoreCase = true) -> ArlesPalette.accentCombustible
        else -> ArlesPalette.primaryAction
    }
}

internal fun MainActivity.crearChipTabModulo(
    modulo: String,
    selected: Boolean,
    accent: Int,
    onClick: () -> Unit,
): Button {
    return Button(this).apply {
        text = modulo
        textSize = 11.5f
        isAllCaps = false
        setPadding(dp(14), dp(8), dp(14), dp(8))
        aplicarEstiloChipTaller(this, selected, accent)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(36),
        ).apply { setMargins(0, 0, dp(6), 0) }
        setOnClickListener { onClick() }
    }
}

internal fun MainActivity.actualizarChipsModulo(
    tabsLayout: LinearLayout,
    moduloSeleccionado: String,
    accent: Int,
) {
    for (i in 0 until tabsLayout.childCount) {
        val child = tabsLayout.getChildAt(i)
        if (child is Button) {
            aplicarEstiloChipTaller(child, child.text == moduloSeleccionado, accent)
        }
    }
}

internal fun MainActivity.catalogoResumenBar(message: String, accent: Int = ArlesPalette.primaryAction): TextView {
    return TextView(this).apply {
        text = message
        textSize = 13.5f
        setTextColor(accent)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = arlesRoundedBackground(ArlesPalette.surfaceAlt, ArlesPalette.line, 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(10)) }
    }
}

internal fun MainActivity.catalogoInventarioCard(
    codigo: String,
    producto: String,
    referencia: String,
    marca: String,
    stock: Double,
    unidad: String,
    categoria: String,
    accent: Int,
    onClick: () -> Unit,
): LinearLayout {
    val sinStock = stock <= 0.0
    val detalleRef = listOf(referencia, marca)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "Sin referencia" }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(1).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(context).apply {
            text = codigo.ifBlank { "—" }
            textSize = 10.5f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(statusBadge(
            if (sinStock) "SIN STOCK" else "EN STOCK",
            if (sinStock) ArlesPalette.danger else ArlesPalette.green700,
        ))
        addView(topRow)

        addView(TextView(context).apply {
            text = producto
            textSize = 14.5f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(2))
        })
        addView(TextView(context).apply {
            text = detalleRef
            textSize = 11.5f
            setTextColor(ArlesPalette.muted)
            maxLines = 2
        })

        val statsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf(
            Triple("Stock", cantidadTexto(stock), if (sinStock) ArlesPalette.danger else ArlesPalette.green700),
            Triple("Unidad", unidad.ifBlank { "Unidad" }, ArlesPalette.ink),
            Triple("Categoría", categoria.ifBlank { "General" }.take(14), accent),
        ).forEachIndexed { index, (label, value, color) ->
            statsRow.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = arlesRoundedBackground(ArlesPalette.surfaceAlt, null, 10)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                addView(TextView(context).apply {
                    text = value
                    textSize = 12.5f
                    setTextColor(color)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = label
                    textSize = 9f
                    setTextColor(ArlesPalette.muted)
                    gravity = Gravity.CENTER
                })
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(if (index == 0) 0 else dp(4), 0, 0, 0)
                }
            })
        }
        addView(statsRow)
    }
}

internal fun MainActivity.catalogoMovimientoCard(
    fecha: String,
    tipo: String,
    codigo: String,
    producto: String,
    cantidad: String,
    unidad: String,
    solicitante: String,
    labor: String,
    observaciones: String,
    requiereAjuste: Boolean,
    tieneFoto: Boolean,
    accent: Int,
    onClick: () -> Unit,
): LinearLayout {
    val esEntrada = tipo.contains("Entrada", ignoreCase = true)
    val tipoColor = when {
        requiereAjuste -> ArlesPalette.warning
        esEntrada -> ArlesPalette.green700
        else -> ArlesPalette.danger
    }
    val etiquetaTipo = when {
        requiereAjuste -> "AJUSTE"
        esEntrada -> "ENTRADA"
        else -> "SALIDA"
    }
    val fechaVisible = buildString {
        if (tieneFoto) append("Foto · ")
        append(fecha)
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(1).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        setAnimatedClick(this) { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(statusBadge(etiquetaTipo, tipoColor))
        top.addView(TextView(context).apply {
            text = fechaVisible
            textSize = 10.5f
            setTextColor(ArlesPalette.muted)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(top)

        addView(TextView(context).apply {
            text = producto.ifBlank { "Producto desconocido" }
            textSize = 14f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        })
        addView(TextView(context).apply {
            text = "Código: ${codigo.ifBlank { "—" }}"
            textSize = 11f
            setTextColor(accent)
        })
        addView(TextView(context).apply {
            text = "Cantidad: $cantidad ${unidad.take(8)} · Solicitante: ${solicitante.ifBlank { "—" }}"
            textSize = 11f
            setTextColor(ArlesPalette.muted)
            setPadding(0, dp(4), 0, 0)
        })
        if (labor.isNotBlank()) {
            addView(TextView(context).apply {
                text = "Labor: $labor"
                textSize = 10.5f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(2), 0, 0)
            })
        }
        if (observaciones.isNotBlank()) {
            addView(TextView(context).apply {
                text = observaciones.take(90) + if (observaciones.length > 90) "…" else ""
                textSize = 10.5f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
            })
        }
    }
}

internal fun MainActivity.gestionRow(vararg actions: LinearLayout): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        weightSum = actions.size.toFloat()
        setPadding(0, dp(10), 0, dp(10))
        actions.forEachIndexed { index, button ->
            val marginStart = if (index == 0) 0 else dp(6)
            addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(marginStart, 0, 0, 0)
            })
        }
    }
}

internal fun tallerSubmoduloAccent(subModulo: String): Int {
    return when (subModulo.uppercase(java.util.Locale.getDefault())) {
        TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER -> ArlesPalette.accentTaller
        "EQUIPOS COSECHA" -> ArlesPalette.accentQuimico
        "HERRAMIENTAS MECANICAS" -> ArlesPalette.accentLubricantes
        "VEHICULOS" -> ArlesPalette.green700
        "IMPLEMENTOS AGRICOLAS" -> ArlesPalette.accentAseo
        "BODEGA ROJA" -> Color.rgb(168, 88, 58)
        else -> ArlesPalette.accentTaller
    }
}

internal fun tallerSubmoduloIcon(subModulo: String): Int {
    return when (subModulo.uppercase(java.util.Locale.getDefault())) {
        TallerCanonicos.SUBMODULO_HERRAMIENTAS_TALLER -> R.drawable.ic_tools
        "EQUIPOS COSECHA" -> R.drawable.ic_consumables
        "HERRAMIENTAS MECANICAS" -> R.drawable.ic_lubricants
        "VEHICULOS" -> R.drawable.ic_fuel
        "IMPLEMENTOS AGRICOLAS" -> R.drawable.ic_warehouse
        "BODEGA ROJA" -> R.drawable.ic_warehouse
        else -> R.drawable.ic_tools
    }
}

internal fun MainActivity.tallerKpiCard(message: String): TextView {
    return TextView(this).apply {
        text = message
        textSize = 13.5f
        setTextColor(ArlesPalette.ink)
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = arlesRoundedBackground(ArlesPalette.green100, ArlesPalette.line, 14)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(4), 0, dp(12)) }
        animateViewIn(this)
    }
}

internal fun MainActivity.tallerResumenBar(message: String): TextView {
    return TextView(this).apply {
        text = message
        textSize = 14f
        setTextColor(ArlesPalette.accentTaller)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = arlesRoundedBackground(ArlesPalette.green100, ArlesPalette.line, 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(10)) }
    }
}

internal fun MainActivity.aplicarEstiloChipTaller(
    button: Button,
    selected: Boolean,
    accent: Int = ArlesPalette.accentTaller,
) {
    button.setTextColor(if (selected) Color.WHITE else ArlesPalette.muted)
    button.background = arlesRoundedBackground(
        if (selected) accent else Color.WHITE,
        if (selected) null else ArlesPalette.line,
        20,
    )
}

internal fun MainActivity.tallerSearchField(hint: String): EditText {
    return EditText(this).apply {
        this.hint = hint
        textSize = 14f
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(4), 0, dp(8)) }
    }
}

internal fun MainActivity.sectionHeader(title: String, subtitle: String = ""): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(6))
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        if (subtitle.isNotBlank()) {
            addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(2), 0, 0)
            })
        }
    }
}

internal fun MainActivity.moduleHeroBanner(
    iconRes: Int,
    title: String,
    subtitle: String,
    accent: Int = ArlesPalette.accentTaller,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = arlesRoundedBackground(accent, null, 18)
        elevation = dp(4).toFloat()
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(2), 0, dp(14)) }
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                setMargins(0, 0, dp(14), 0)
            }
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(Color.argb(220, 255, 255, 255))
                setPadding(0, dp(4), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        })
        animateViewIn(this)
    }
}

internal fun MainActivity.kpiMetricChip(
    label: String,
    value: String,
    accent: Int,
    action: ((View) -> Unit)? = null,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = arlesRoundedBackground(Color.WHITE, if (action != null) accent else ArlesPalette.line, 14)
        elevation = dp(if (action != null) 3 else 1).toFloat()
        setPadding(dp(10), dp(12), dp(10), dp(12))
        if (action != null) {
            isClickable = true
            isFocusable = true
            setAnimatedClick(this, action)
        }
        addView(TextView(context).apply {
            text = value
            textSize = 20f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = label
            textSize = 10.5f
            setTextColor(ArlesPalette.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        if (action != null) {
            addView(TextView(context).apply {
                text = "Ver"
                textSize = 8.5f
                setTextColor(accent)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, 0)
            })
        }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
}

internal fun MainActivity.kpiMetricsRow(
    metrics: List<Triple<String, String, Int>>,
    actions: Map<String, (View) -> Unit> = emptyMap(),
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        weightSum = metrics.size.toFloat()
        setPadding(0, 0, 0, dp(12))
        metrics.forEachIndexed { index, (label, value, accent) ->
            addView(
                kpiMetricChip(label, value, accent, actions[label]),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(if (index == 0) 0 else dp(6), 0, 0, 0)
                },
            )
        }
        animateViewIn(this)
    }
}

internal fun MainActivity.tallerSubmoduloTile(
    title: String,
    subtitle: String,
    itemCount: Int,
    iconRes: Int,
    accent: Int,
    action: (View) -> Unit,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(accent, null, 16)
        elevation = dp(3).toFloat()
        setPadding(dp(12), dp(14), dp(12), dp(14))
        isClickable = true
        isFocusable = true
        setAnimatedClick(this, action)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(context).apply {
                text = "$itemCount"
                textSize = 11f
                setTextColor(accent)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = arlesRoundedBackground(Color.WHITE, null, 10)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
        })
        addView(TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(2))
        })
        addView(TextView(context).apply {
            text = subtitle
            textSize = 10.5f
            setTextColor(Color.argb(210, 255, 255, 255))
            maxLines = 2
        })
        animateViewIn(this)
    }
}

internal fun MainActivity.statusBadge(text: String, backgroundColor: Int, textColor: Int = Color.WHITE): TextView {
    return TextView(this).apply {
        this.text = text
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        gravity = Gravity.CENTER
        background = arlesRoundedBackground(backgroundColor, null, 8)
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
}

internal fun MainActivity.formSectionCard(
    root: LinearLayout,
    step: String,
    title: String,
    subtitle: String = "",
    accent: Int = ArlesPalette.accentTaller,
    block: (LinearLayout) -> Unit,
) {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 16)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(12)) }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(10))
    }
    header.addView(TextView(this).apply {
        text = step
        textSize = 11f
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = arlesRoundedBackground(accent, null, 10)
        setPadding(dp(10), dp(5), dp(10), dp(5))
    })
    header.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
        addView(TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        if (subtitle.isNotBlank()) {
            addView(TextView(context).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(2), 0, 0)
            })
        }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    card.addView(header)
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), 0, dp(14), dp(14))
    }
    block(body)
    card.addView(body)
    root.addView(card)
    animateViewIn(card)
}

internal fun MainActivity.tallerInventarioCard(
    codigo: String,
    nombre: String,
    detalle: String,
    submodulo: String?,
    disponibles: String,
    ocupados: String,
    total: String,
    unidad: String,
    accent: Int,
    enPrestamo: Boolean,
    onClick: () -> Unit,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(1).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(context).apply {
            text = codigo
            textSize = 10.5f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (enPrestamo) {
            topRow.addView(statusBadge("EN PRÉSTAMO", ArlesPalette.warning))
        } else {
            topRow.addView(statusBadge("DISPONIBLE", ArlesPalette.green500))
        }
        addView(topRow)

        addView(TextView(context).apply {
            text = nombre
            textSize = 14.5f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(2))
        })
        addView(TextView(context).apply {
            text = detalle
            textSize = 11.5f
            setTextColor(ArlesPalette.muted)
            maxLines = 2
        })
        if (!submodulo.isNullOrBlank()) {
            addView(TextView(context).apply {
                text = submodulo
                textSize = 10.5f
                setTextColor(accent)
                setPadding(0, dp(4), 0, 0)
            })
        }

        val statsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf(
            Triple("Total", total, ArlesPalette.ink),
            Triple("Disp.", disponibles, ArlesPalette.green800),
            Triple("Ocup.", ocupados, if (enPrestamo) ArlesPalette.warning else ArlesPalette.muted),
        ).forEachIndexed { index, (label, value, color) ->
            statsRow.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = arlesRoundedBackground(ArlesPalette.soft, null, 10)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                addView(TextView(context).apply {
                    text = value
                    textSize = 13f
                    setTextColor(color)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = "$label · $unidad"
                    textSize = 9f
                    setTextColor(ArlesPalette.muted)
                    gravity = Gravity.CENTER
                })
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(if (index == 0) 0 else dp(4), 0, 0, 0)
                }
            })
        }
        addView(statsRow)
    }
}

internal fun MainActivity.tallerMovimientoCard(
    fecha: String,
    tipo: String,
    codigo: String,
    herramienta: String,
    responsable: String,
    solicitante: String,
    observaciones: String,
    onClick: () -> Unit,
): LinearLayout {
    val esSalida = tipo.contains("Salida", ignoreCase = true)
    val tipoColor = if (esSalida) ArlesPalette.warning else ArlesPalette.green700
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(1).toFloat()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(statusBadge(tipo.uppercase(java.util.Locale.getDefault()), tipoColor))
        top.addView(TextView(context).apply {
            text = fecha
            textSize = 10.5f
            setTextColor(ArlesPalette.muted)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(top)
        addView(TextView(context).apply {
            text = herramienta
            textSize = 14f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        })
        addView(TextView(context).apply {
            text = "Código: $codigo"
            textSize = 11f
            setTextColor(ArlesPalette.accentTaller)
        })
        addView(TextView(context).apply {
            text = "Solicitante: ${solicitante.ifBlank { "—" }} · Responsable: ${responsable.ifBlank { "—" }}"
            textSize = 11f
            setTextColor(ArlesPalette.muted)
            setPadding(0, dp(4), 0, 0)
        })
        if (observaciones.isNotBlank()) {
            addView(TextView(context).apply {
                text = observaciones.take(80) + if (observaciones.length > 80) "…" else ""
                textSize = 10.5f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
            })
        }
    }
}

internal fun MainActivity.tallerEmptyState(titulo: String, mensaje: String): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = arlesRoundedBackground(ArlesPalette.soft, ArlesPalette.line, 16)
        setPadding(dp(24), dp(28), dp(24), dp(28))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(8), 0, dp(8)) }
        addView(TextView(context).apply {
            text = titulo
            textSize = 15f
            setTextColor(ArlesPalette.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = mensaje
            textSize = 12.5f
            setTextColor(ArlesPalette.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
    }
}

internal fun MainActivity.actionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    accent: Int,
    action: (View) -> Unit,
): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 14)
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        isFocusable = true
        setAnimatedClick(this, action)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(8)) }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = arlesRoundedBackground(accent, null, 12)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(ArlesPalette.ink)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(ArlesPalette.muted)
                setPadding(0, dp(2), 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        animateViewIn(this)
    }
}

internal fun MainActivity.aiAssistantFab(action: (View) -> Unit): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = arlesRoundedBackground(ArlesPalette.green700, null, 28)
        elevation = dp(8).toFloat()
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        setPadding(dp(6), dp(6), dp(14), dp(6))
        isClickable = true
        isFocusable = true
        contentDescription = "Abrir Asistente IA"
        setAnimatedClick(this, action)

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = arlesRoundedBackground(ArlesPalette.brandVivid, null, 22)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_ai)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
        })

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(TextView(context).apply {
                text = "Asistente IA"
                textSize = 13.5f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "Consultar"
                textSize = 9.5f
                setTextColor(Color.argb(215, 255, 255, 255))
                setPadding(0, dp(1), 0, 0)
            })
        })
        if (performanceConfig.enhancedAnimationsEnabled()) {
            alpha = 0f
            translationX = dp(28).toFloat()
            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(420L)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.65f))
                .start()
        } else {
            animateViewIn(this)
        }
    }
}
