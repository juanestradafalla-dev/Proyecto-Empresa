@file:android.annotation.SuppressLint("HardcodedText", "SetTextI18n")
@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "SpellCheckingInspection", "LocalVariableName", "MemberVisibilityCanBePrivate")

package com.arlessas.gestion

import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.text.TextWatcher
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Archivo modularizado con funciones de extensión de MainActivity.
// Mantiene el comportamiento original, pero separa responsabilidades para facilitar mantenimiento.

internal fun MainActivity.prepararPantalla(screenId: String = currentScreenId) {
        val listeners = firestoreListeners.count()
        android.util.Log.d("PerfTaller", "prepararPantalla: $screenId listeners=$listeners")
        firestoreListeners.clear()
    }

internal fun MainActivity.pantallaActiva(): Boolean = !isFinishing && !isDestroyed

internal fun MainActivity.avisoModoBajoRendimiento(root: LinearLayout, detalle: String = "") {
        if (performanceConfig.isLowPerformanceMode()) {
            root.addView(infoText("Modo bajo rendimiento activo: se muestran menos registros por pantalla para mejorar fluidez.${if (detalle.isNotBlank()) " $detalle" else ""}"))
        } else if (performanceConfig.isHighPerformanceMode()) {
            root.addView(infoText("Modo alto rendimiento activo: más registros visibles y animaciones fluidas.${if (detalle.isNotBlank()) " $detalle" else ""}"))
        }
    }

internal fun MainActivity.avisoLimiteRender(container: LinearLayout, ocultos: Int) {
        if (ocultos > 0) {
            container.addView(infoText("Se ocultaron $ocultos registros en esta vista para proteger el rendimiento. Usa el filtro para encontrar datos específicos."))
        }
    }

internal fun MainActivity.showMainMenu() {
        currentScreenBackAction = null
        
        // Si no hay usuario autenticado, mostrar login/registro
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showLoginScreen()
            return
        }

        if (isBackNavigationInProgress) {
            android.util.Log.d("PerfTaller", "showMainMenu sin sincronizarPerfilUsuarioNube por retroceso")
        } else {
            sincronizarPerfilUsuarioNube()
        }

        if (AppMode.esTallerIndependiente) {
            showHerramientasMenu()
            return
        }

        firestore.collection("usuarios").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val nombres = doc.getString("nombres") ?: "Usuario"
                val cargo = doc.getString("cargo") ?: ""
                val saludo = "Bienvenido, $nombres\n$cargo"
                setupMainUI(saludo)
            }
            .addOnFailureListener {
                setupMainUI("Gestión")
            }
    }

internal fun MainActivity.sincronizarPerfilUsuarioNube() {
        if (auth.currentUser == null) return
        functions.getHttpsCallable("sincronizarPerfilUsuario")
            .call()
            .addOnFailureListener { e ->
                android.util.Log.w("ArlesGestion", "Perfil nube no sincronizado: ${e.localizedMessage}")
            }
    }

internal fun MainActivity.setupMainUI(saludo: String) {
        currentScreenRenderer = { setupMainUI(saludo) }
        val root = baseScreen(saludo, "Control de salidas, combustible, químicos, EPP y dotación", false)

        // Mostrar versión en la parte inferior izquierda (se moverá al footer)
        /* 
        val versionTop = TextView(this).apply {
            text = getString(R.string.version_label, "2.8.0")
            textSize = 9f
            setTextColor(gris)
            gravity = Gravity.END
        }
        val topParent = root.parent.parent as FrameLayout
        val versionParamsTop = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dp(60), dp(16), 0)
        }
        topParent.addView(versionTop, versionParamsTop)
        */

        // Logo de la empresa
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_andes)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(108)).apply {
                setMargins(0, 0, 0, dp(12))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(logo, 2) // Insertar después del subtítulo

        // Contenedor para la cuadrícula 2x2
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        row1.addView(gridButton("Consumibles", R.drawable.ic_consumables, ArlesPalette.accentConsumibles, 0) { showConsumiblesForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
        row1.addView(gridButton("Combustible", R.drawable.ic_fuel, ArlesPalette.accentCombustible, 1) { showCombustibleForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        row2.addView(gridButton("Agroqu\u00edmicos", R.drawable.ic_chemical, ArlesPalette.accentQuimico, 2) { showQuimicoForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
        row2.addView(gridButton("ASEO", R.drawable.ic_cleaning, ArlesPalette.accentAseo, 3) { showAseoForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        if (AppMode.incluyeTaller) {
            row3.addView(gridButton("TALLER", R.drawable.ic_tools, ArlesPalette.accentTaller, 4) { showHerramientasMenu() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
            row3.addView(gridButton("Lubricantes taller", R.drawable.ic_lubricants, ArlesPalette.accentLubricantes, 5) { showLubricantesTallerForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })
        }

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        row4.addView(gridButton("Entrega EPP", R.drawable.ic_epp, ArlesPalette.accentEpp, 6) { showEPPForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
        row4.addView(gridButton("Entrega Dotación", R.drawable.ic_dotacion, ArlesPalette.accentDotacion, 7) { showDotacionForm() }, LinearLayout.LayoutParams(0, dp(104), 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })

        grid.addView(row1)
        grid.addView(row2)
        if (AppMode.incluyeTaller) {
            grid.addView(row3)
        }
        grid.addView(row4)
        root.addView(grid)

        root.addView(primaryButton("Ver Inventario Actual") { showInventarioScreen() })
        root.addView(primaryButton("Ver Movimientos (Tabla Nube)") { showTablaMovimientos() })
        
        // Carga masiva protegida: solo visible para el administrador del proyecto.
        if (auth.currentUser?.email.equals("juanestradafalla@gmail.com", ignoreCase = true)) {
            root.addView(primaryButton("Activar carga de inventario") {
                AlertDialog.Builder(this)
                    .setTitle("Carga masiva protegida")
                    .setMessage("Esta acción reemplaza la colección existencias con la carga verificada. Úsala solo para inicializar o reconstruir el inventario.")
                    .setPositiveButton("Continuar") { _, _ ->
                        val loading = Toast.makeText(this, "Procesando carga masiva...", Toast.LENGTH_LONG)
                        loading.show()
                        functions.getHttpsCallable("importarConsumiblesMasivo")
                            .call()
                            .addOnSuccessListener { result ->
                                loading.cancel()
                                val msg = (result.data as? Map<*, *>)?.get("mensaje")?.toString() ?: "Carga completada"
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                loading.cancel()
                                Toast.makeText(this, "Error en carga: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }.apply {
                setBackgroundColor(Color.rgb(0, 100, 200))
            },
        )
        }

        root.addView(primaryButton("Cerrar Sesión") {
            auth.signOut()
            showMainMenu()
        })

        // Contenedor horizontal para los botones de Info, Changelog y Versión (abajo a la izquierda)
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val versionText = TextView(this).apply {
            text = getString(R.string.version_label, AppVersionInfo.VERSION_NAME)
            textSize = 10f
            setTextColor(gris)
            setPadding(0, 0, dp(8), 0)
        }

        val displayBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_display)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "Tamaño de interfaz"
            setAnimatedClick(this) { showUiScaleSettings() }
        }

        val infoBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_info)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setAnimatedClick(this) { showInfoApp() }
        }
        
        val changelogBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_changelog)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setAnimatedClick(this) { showChangelog() }
        }

        footerRow.addView(versionText)
        footerRow.addView(displayBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        footerRow.addView(changelogBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        footerRow.addView(infoBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        
        root.addView(footerRow)
    }

internal fun MainActivity.showUiScaleSettings() {
        currentScreenRenderer = { showUiScaleSettings() }
        val selectedId = UiDisplayConfig.currentPresetId(this)
        val autoMode = UiDisplayConfig.isAutoScale(this)
        val root = baseScreen(
            "Tamaño de interfaz",
            "Reduce o ajusta la escala en celulares grandes. Escala actual: ${UiDisplayConfig.scaleLabel(this)}",
        )

        val preview = card()
        preview.addView(TextView(this).apply {
            text = "Vista previa"
            textSize = 20f
            setTextColor(verdeOscuro)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        preview.addView(TextView(this).apply {
            text = "Los botones, textos y espacios cambian con la escala elegida."
            textSize = 14f
            setTextColor(gris)
            setPadding(0, dp(4), 0, 0)
        })
        preview.addView(primaryButton("Botón de ejemplo") { })
        root.addView(preview)

        root.addView(primaryButton(
            if (autoMode) "Automática (activa)" else "Automática (recomendada en pantallas grandes)",
        ) {
            UiDisplayConfig.setAutoScale(this)
            recreate()
        }.apply {
            if (autoMode) setBackgroundColor(verdeOscuro)
        })

        UiDisplayConfig.presets.forEach { preset ->
            val active = !autoMode && preset.id == selectedId
            root.addView(primaryButton("${preset.label} (${(preset.scale * 100).toInt()}%)") {
                UiDisplayConfig.setPreset(this, preset.id)
                recreate()
            }.apply {
                if (active) setBackgroundColor(verdeOscuro)
            })
        }

        root.addView(infoText(
            "Consejo: en celulares muy grandes prueba Compacta o Pequeña. " +
                "La opción Automática ajusta sola según el tamaño de pantalla.",
        ))
    }

internal fun MainActivity.showLoginScreen() {
        val root = baseScreen("Bienvenido", "Inicia sesión o crea una cuenta para continuar.", false)
        val email = field(root, "Correo electrónico *", "ejemplo@correo.com")
        val password = field(root, "Contraseña *", "Ingresa tu contraseña").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        root.addView(primaryButton("Iniciar Sesión") {
            if (!required(email, password)) return@primaryButton
            auth.signInWithEmailAndPassword(email.text.toString(), password.text.toString())
                .addOnSuccessListener {
                    sincronizarPerfilUsuarioNube()
                    showMainMenu()
                }
                .addOnFailureListener { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
        })

        root.addView(primaryButton("Crear Nueva Cuenta") { showRegisterScreen() })
    }

internal fun MainActivity.showRegisterScreen() {
        val root = baseScreen("Crear Usuario", "Completa tus datos para el registro corporativo.")
        val nombres = field(root, "Nombres *", "Tus nombres")
        val apellidos = field(root, "Apellidos *", "Tus apellidos")
        val cargo = field(root, "Cargo *", "Ej: Supervisor, Almacenista")
        val email = field(root, "Correo electrónico *", "ejemplo@correo.com")
        val password = field(root, "Contraseña *", "Mínimo 6 caracteres").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        root.addView(primaryButton("Registrar y Entrar") {
            if (!required(nombres, apellidos, cargo, email, password)) return@primaryButton
            
            auth.createUserWithEmailAndPassword(email.text.toString(), password.text.toString())
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                    val userMap = mapOf(
                        "nombres" to nombres.text.toString(),
                        "apellidos" to apellidos.text.toString(),
                        "cargo" to cargo.text.toString(),
                        "email" to email.text.toString(),
                        "rol" to "operador",
                        "activo" to false,
                        "estado" to "activo",
                        "fecha_registro" to now()
                    )
                    firestore.collection("usuarios").document(uid).set(userMap)
                        .addOnSuccessListener {
                            sincronizarPerfilUsuarioNube()
                            saved("Usuario registrado en Cloud Firestore")
                            showMainMenu()
                        }
                }
                .addOnFailureListener { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
        })
    }

internal fun MainActivity.showChangelog() {
        val root = baseScreen("Historial de Cambios", "Evolución y mejoras de la aplicación.")

        root.addView(cardText("""
            v4.0.0 - Versión Profesional: Segmentación y Seguridad
            • Inventarios Segmentados: 5 tablas independientes en tiempo real (Consumibles, Químico, EPP, Dotación, Combustible).
            • Movimientos Real-time: Historial de entradas y salidas con actualización instantánea por categoría.
            • Seguridad en Herramientas: Bloqueo automático para evitar la salida de herramientas que ya están afuera.
            • Reestructuración de Dotación: Clasificación lógica por Parte Superior, Inferior, Conjunto y Calzado.
            • Catálogo Maestro: Integración de 145 productos predeterminados para registro ultra-rápido.
            • Inteligencia de Categorización: Reglas estrictas bilingües para clasificar automáticamente Urea, Fertilizantes y EPP.
            • Optimización de Costos IA: Desactivación de IA automática en clics para evitar consumo innecesario de tokens.
            • Flujo de Trabajo Continuo: Los formularios ya no retroceden al inicio, permitiendo registros masivos rápidos.
            • Rediseño Visual: Menú de herramientas optimizado según boceto operativo y nuevos iconos de alta visibilidad.
            • Cumplimiento Google Play: Transparencia de permisos y ofuscación de código reforzada.

            v3.5.0 - Optimización y Correcciones
            • Corrección de cierres inesperados (NPE) en formularios de Dotación y EPP.
            • Rediseño de botones en el menú principal para acceso rápido a movimientos.
            • Iconos en blanco para mayor contraste en condiciones de alta luminosidad.
            • Filtro de seguridad para inventario de combustible con palabra exacta.
            • Mejora en la limpieza de campos tras registros exitosos.

            v3.3.0 - Rendimiento y separación profesional
            • Modo bajo rendimiento para celulares de 2 GB o 3 GB de RAM.
            • Listeners de Firestore administrados para evitar fugas de memoria al cambiar de pantalla.
            • Inventario e historial con límites visibles y mensajes de protección de rendimiento.
            • Historial global corregido: ya no agrega TextWatchers repetidos en cada actualización de Firestore.
            • Nuevos módulos técnicos: AppPerformanceConfig, FirestoreListenerRegistry y RenderLimiter.
            • Preparación para separar gradualmente MainActivity sin romper flujos existentes.

            v3.2.0 - Seguridad operativa, IA filtrada y trazabilidad
            • IA optimizada: envía solo stock, catálogo y herramientas relacionados con la consulta.
            • Salidas generadas por IA en modo cloud-first: Firestore valida stock y movimiento en una transacción.
            • Trazabilidad reforzada: codigo_interno, producto_id, stock anterior y stock nuevo.
            • Importador con vista previa antes de confirmar cambios.
            • Preparación release: versión 3.2.0 y minificación activada.

            v3.0.0 - Inteligencia Operativa y Control Total
            • IA Proactiva: Reconoce sinónimos (neumáticos/llantas) y anticipa bajo stock.
            • Memoria de Sesión: La IA recuerda de qué están hablando sin repetir contexto.
            • Control de Ubicaciones: Edición rápida de Estantería-Piso mediante clic largo.
            • Reportes de Inventario: Nuevo botón de exportación CSV en stock real.
            • Herramientas Pro: Historial detallado estilo Excel y responsable de salida.
            • Sincronización Total: Nuevos botones para forzar subida de datos a la nube.
            • Interfaz Optimizada: Reubicación de botones de sistema y mejora de voz (TTS).
        """))

        root.addView(cardText("""
            v2.9.0 - Asistente IA limpio y cerebro operativo
            • Registros creados por el asistente quedan sin marcas de IA.
            • Nueva consulta de datos: existencias, movimientos, entradas, herramientas y vencimientos.
            • Respuestas más cortas y menor consumo de tokens.
        """))

        root.addView(cardText("""
            v2.8.0 - Backup, offline, importación y auditoría
            • Backup automático diario y backup manual en Firestore.
            • Modo sin internet con pendientes de sincronización.
            • Detector de inconsistencias para auditoría.
            • Importación de inventario desde Excel/CSV.
            • Panel de auditoría con estado general del sistema.
        """))

        root.addView(cardText("""
            v2.6.0 - Trazabilidad Total y Sincronización
            • Historial de movimientos incluye quién realizó el registro (Nombre y Cargo).
            • Nuevo botón de sincronización manual para reportes.
            • Limpieza de campos en inventario actual.
            • Actualización de identidad de versión global.
        """))

        root.addView(cardText("""
            v2.5.0 - Sincronización Total y Filtros
            • Reportes en tabla para Salidas y Entradas (Excel Style).
            • Filtros inteligentes por Personal, Fecha, Código o Producto.
            • Conexión en tiempo real multi-dispositivo vía Firestore.
            • Trazabilidad unificada entre todas las cuentas de usuario.
        """))

        root.addView(cardText("""
            v2.4.0 - Inventario Tabular (Excel Style)
            • Nueva vista de inventario organizada en columnas.
            • Trazabilidad total: Fecha, Código, Producto, Ref, Solicitante y Stock.
            • Desplazamiento horizontal para visualización completa.
        """))

        root.addView(cardText("""
            v2.3.0 - Buscador Inteligente
            • Barras de búsqueda integradas en todos los formularios.
            • Autocompletado rápido de Ítem y Referencia.
            • Selección automática de jerarquías al buscar.
        """))

        root.addView(cardText("""
            v2.2.6 - Mantenimiento AI
            • Módulo de asistente desactivado temporalmente para revisión de servidores.
        """))

        root.addView(cardText("""
            v2.2.5 - Activación Asistente IA
            • Conexión por backend seguro para el asistente.
            • Actualización del motor a OpenAI mini.
        """))

        root.addView(cardText("""
            v2.2.4 - Estabilidad Asistente IA
            • Corrección de conexión con servidores de Google.
            • Optimización del modelo de lenguaje para mayor rapidez.
        """))

        root.addView(cardText("""
            v2.2.3 - Unidades de Combustible
            • Restricción de unidades a Galones en módulo de Combustible.
            • Ajuste en formulario de salida para maquinaria.
        """))

        root.addView(cardText("""
            v2.2.2 - Combustibles en Entradas
            • Módulo de Combustible integrado en Entradas Almacén.
            • Registro unificado para Gasolina, ACPM y Urea.
            • Consistencia automática con el inventario de maquinaria.
        """))

        root.addView(cardText("""
            v2.2.1 - Ajustes Visuales
            • Icono de Dotación ahora en color blanco para consistencia.
            • Pulido de interfaz en menús principales.
        """))

        root.addView(cardText("""
            v2.2.0 - Módulo de Dotación
            • Nueva categoría "Dotación" con tallas personalizadas.
            • Jerarquía optimizada: Parte > Ítem > Talla.
            • Control de inventario automático para ropa y calzado.
        """))

        root.addView(cardText("""
            v2.1.0 - Catálogo Dinámico
            • Nueva opción para registrar productos personalizados.
            • Creación de categorías e ítems desde la app.
            • Asignación de ubicación física al producto.
            • Sincronización instantánea entre todos los módulos.
        """))

        root.addView(cardText("""
            v2.0.0 - Registro Global de Entradas
            • Módulo de Entradas Almacén ahora usa el catálogo maestro.
            • Selección jerárquica: Menú > Categoría > Ítem > Referencia.
            • Consistencia total entre entradas y salidas para inventario.
        """))

        root.addView(cardText("""
            v1.9.6 - Categorización Avanzada Químicos
            • Fertilizantes Químicos ahora agrupados por tipo (Edáficos, Foliares, Menores).
            • Refinamiento de la lógica de unidades automáticas.
        """))

        root.addView(cardText("""
            v1.9.5 - Unidades Inteligentes y Filtros
            • Fertilizantes ahora divididos en Orgánicos y Químicos.
            • Cambio automático de unidad (Gramos/ML) según el producto.
            • Optimización de la lógica de selección de químicos.
        """))

        root.addView(cardText("""
            v1.9.4 - Catálogo Total de Químicos
            • Integración de la lista completa de fertilizantes, fungicidas y más.
            • Selección jerárquica optimizada (Categoría > Ítem > Marca).
            • Soporte para trazabilidad agrícola avanzada.
        """))

        root.addView(cardText("""
            v1.9.3 - Catálogo Total de Consumibles
            • Integración de la lista completa de más de 300 ítems.
            • Organización detallada de Neumáticos, Pernos, Tuberías y más.
            • Estructura jerárquica optimizada para todo el almacén.
        """))

        root.addView(cardText("""
            v1.9.2 - Listas Maestras Consumibles
            • Reorganización inicial del módulo de Consumibles.
            • Selección jerárquica (Categoría > Ítem > Marca/Ref).
        """))

        root.addView(cardText("""
            v1.9.1 - Listas Maestras EPP
            • Selección jerárquica en el módulo de EPP.
            • Categorización por protección (Visual, Manual, etc.).
        """))

        root.addView(cardText("""
            v1.8.2 - Cerebro Activado
            • Activación oficial de Asistente IA con proveedor de IA externo.
            • Capacidad total para procesar voz y texto.
            • Llenado inteligente de formularios habilitado.
        """))

        root.addView(cardText("""
            v1.8.1 - Voz para Asistente IA
            • Botón de Micrófono para dictar instrucciones.
            • Respuesta por voz integrada (la app te habla).
            • Mejora en la interfaz del asistente.
        """))

        root.addView(cardText("""
            v1.8.0 - Asistente Asistente IA
            • Integración inicial de asistente inteligente.
            • Nuevo módulo de Asistente Inteligente en el menú.
            • Capacidad de procesar lenguaje natural para gestionar datos.
        """))

        root.addView(cardText("""
            v1.7.2 - Mejoras en Escáner
            • Cuadro de enfoque visual en el lector QR.
            • Función de Linterna integrada para sitios oscuros.
            • Optimización de precisión en la lectura.
        """))

        root.addView(cardText("""
            v1.7.1 - Escaneo Universal
            • Lector QR/Barras habilitado en TODOS los formularios.
            • Botón de escaneo rápido en Consumibles, Combustibles, Químicos y Entradas.
        """))

        root.addView(cardText("""
            v1.7.0 - Inteligencia y Escaneo
            • Integración de lector de códigos QR y Barras.
            • Botón de escaneo en formularios para llenado rápido.
            • Permisos de cámara integrados y optimizados.
        """))

        root.addView(cardText("""
            v1.6.2 - Pulido Visual y Nuevo Icono
            • Nuevo icono oficial de la aplicación (Caja y Hoja).
            • Botones de Info y Historial integrados en el scroll (ya no flotan).
            • Reubicación de botones de acción para evitar solapamientos.
        """))

        root.addView(cardText("""
            v1.6.1 - Modernización UI (Parte 1)
            • Botones redondeados en el menú principal.
            • Iconos representativos (siluetas) en cada módulo.
            • Ajuste de tamaño de fuente en saludo de bienvenida.
        """))

        root.addView(cardText("""
            v1.5.0 - Perfiles y Sincronización
            • Perfiles dinámicos: Saludo personalizado con Nombre y Cargo.
            • Preparación de inventario en la nube con Firestore.
            • Mejoras en la carga de datos en tiempo real.
        """))

        root.addView(cardText("""
            v1.4.0 - Migración a Firestore
            • Cambio de base de datos a Cloud Firestore (NoSQL).
            • Almacenamiento de usuarios en colecciones de Firestore.
            • Preparación para sincronización offline avanzada.
        """))

        root.addView(cardText("""
            v1.3.0 - Firebase y Usuarios
            • Integración con Firebase Spark (Auth).
            • Sistema de registro de usuarios (Nombres, Apellidos, Cargo, Email).
            • Pantalla de Login obligatoria para acceso seguro.
            • Limpieza visual del menú principal.
        """))

        root.addView(cardText("""
            v1.2.0 - Personalización y Versiones
            • Se añadió este historial de cambios (Changelog).
            • Visualización de versión en Splash y Menú Principal.
            • Icono corporativo de papel y lápiz para acceso rápido.
        """))

        root.addView(cardText("""
            v1.1.0 - Módulos Críticos
            • Módulo de Entrega EPP con alerta de 30 días.
            • Módulo de Entradas Almacén para inventario.
            • Corrección de compatibilidad CSV con Excel (sep=;).
            • Ajuste de teclado (adjustPan) para formularios.
        """))

        root.addView(cardText("""
            v1.0.0 - Identidad y Estructura
            • Cambio de paquete a com.arlessas.gestion.
            • Integración de Logo Andes y nueva paleta verde.
            • Diseño de menú en cuadrícula 2x2.
            • Pantalla de carga (Splash) de 1.5s.
            • Refactorización base y actualización de librerías.
        """))
    }

internal fun MainActivity.gridButton(
        text: String,
        iconRes: Int,
        accent: Int = verde,
        staggerIndex: Int = 0,
        action: (View) -> Unit,
    ): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = arlesRoundedBackground(accent, null, 16)
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            setAnimatedClick(this, action)
            setPadding(dp(10), dp(14), dp(10), dp(14))
        }

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(0, 0, 0, dp(6))
            setColorFilter(Color.WHITE)
        }
        
        val label = TextView(this).apply {
            this.text = text
            textSize = 12.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        btn.addView(icon)
        btn.addView(label)
        if (performanceConfig.enhancedAnimationsEnabled()) {
            animateViewInStagger(btn, staggerIndex)
        } else {
            animateViewIn(btn)
        }
        return btn
    }

internal fun MainActivity.showInfoApp() {
        currentScreenRenderer = { showInfoApp() }
        val root = baseScreen("Información de la app", "Resumen funcional para saber qué hace cada módulo.")
        root.addView(cardText("""
            MÓDULOS PRINCIPALES

            1. Consumibles
            Registra salidas de materiales de uso diario: aseo, tubería, repuestos menores, papelería, elementos de bodega y otros insumos no químicos.

            Campos: ítem, referencia, marca, cantidad, unidad, solicitante, labor y observaciones.

            2. Combustible
            Registra gasolina, ACPM y urea. Está pensado para maquinaria, equipos y labores de campo.

            Campos: tipo, cantidad, unidad, horómetro opcional, maquinaria, solicitante, labor y observaciones.

            3. Químico
            Registra salidas de fertilizantes, fungicidas, insecticidas, herbicidas, coadyuvantes o productos similares.

            Campos: ítem, referencia/concentración, cantidad, unidad, solicitante, labor y observaciones.

            4. Entrega EPP
            Módulo con control de frecuencia. Alerta si un trabajador solicita el mismo ítem en menos de 30 días.

            5. Dotación
            Registra entregas de dotación por talla, referencia y trabajador.

            6. Registros y exportación
            Muestra los datos guardados y permite exportarlos en CSV compatible con Excel.
        """))

        root.addView(cardText("""
            DECISIÓN TÉCNICA

            La información se guarda primero en una base local SQLite. Esto es más seguro para trabajo de finca, porque la app sigue funcionando aunque no haya internet.

            La exportación a Excel se maneja por CSV separado por punto y coma, porque Excel en español suele abrir mejor este formato.
        """))

        root.addView(cardText("""
            RENDIMIENTO

            Modo actual: ${performanceConfig.modeLabel()}
            ${performanceConfig.modeDescription()}
            Límite inventario visible: ${performanceConfig.inventoryRenderLimit()} filas
            Límite movimientos visibles: ${performanceConfig.movementRenderLimit()} filas
            Animaciones: ${when {
                performanceConfig.enhancedAnimationsEnabled() -> "fluidas (alto rendimiento)"
                performanceConfig.animationsEnabled() -> "activas"
                else -> "desactivadas"
            }}

            Bajo rendimiento: celulares de 2-3 GB o Android Go.
            Alto rendimiento: gama alta (6 GB+ RAM), más datos y transiciones suaves.
        """))

        root.addView(performanceModeToggleRow())

        root.addView(cardText("""
            TAMAÑO DE INTERFAZ

            Escala actual: ${UiDisplayConfig.scaleLabel(this)}

            Si en un celular grande todo se ve muy amplio, abre Tamaño de interfaz y elige Compacta o Pequeña.
        """))

        root.addView(primaryButton("Ajustar tamaño de interfaz") { showUiScaleSettings() })
    }

internal fun MainActivity.performanceModeToggleRow(): LinearLayout {
        val activity = this
        val isLow = performanceConfig.isLowPerformanceMode()
        val isHigh = performanceConfig.isHighPerformanceMode()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, dp(4), 0, dp(10))
            addView(
                styledButton(
                    if (isLow) "Bajo rendimiento ✓" else "Bajo rendimiento",
                    if (isLow) ArlesButtonStyle.MUTED else ArlesButtonStyle.OUTLINE,
                ) {
                    performanceConfig.toggleLowPerformanceMode()
                    Toast.makeText(activity, "Modo: ${performanceConfig.modeLabel()}", Toast.LENGTH_SHORT).show()
                    showInfoApp()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        setMargins(0, 0, dp(6), 0)
                    }
                    textSize = 13f
                },
            )
            addView(
                styledButton(
                    if (isHigh) "Alto rendimiento ✓" else "Alto rendimiento",
                    if (isHigh) ArlesButtonStyle.PRIMARY else ArlesButtonStyle.SECONDARY,
                ) {
                    performanceConfig.toggleHighPerformanceMode()
                    Toast.makeText(activity, "Modo: ${performanceConfig.modeLabel()}", Toast.LENGTH_SHORT).show()
                    showInfoApp()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                    textSize = 13f
                },
            )
            animateViewIn(this)
        }
    }

internal fun seleccionarSpinnerPorTexto(spinner: Spinner, valor: String, onDone: () -> Unit = {}) {
        val adapter = spinner.adapter as? ArrayAdapter<String>
        val pos = adapter?.getPosition(valor)?.takeIf { it >= 0 }
            ?: adapter?.let {
                (0 until it.count).firstOrNull { index ->
                    referenciasInventarioCoinciden(valor, it.getItem(index).orEmpty())
                }
            }
            ?: -1
        if (pos >= 0) {
            spinner.tag = "SINCRO"
            spinner.setSelection(pos, false)
            // Quitar el tag un poco después para que el listener sepa que fue auto
            spinner.postDelayed({ spinner.tag = null }, 100)
        }
        spinner.post { onDone() }
    }

internal fun claveCatalogoPorTexto(keys: Collection<String>, valor: String): String {
        if (keys.isEmpty()) return valor
        val limpio = valor.trim()
        return keys.firstOrNull { it == limpio }
            ?: keys.firstOrNull { normalizarBusqueda(it) == normalizarBusqueda(limpio) }
            ?: keys.firstOrNull { referenciasInventarioCoinciden(limpio, it) }
            ?: limpio.ifBlank { keys.first() }
    }

internal fun MainActivity.seleccionarProductoEnSpinners(
        catSpinner: Spinner,
        itemSpinner: Spinner,
        refSpinner: Spinner,
        catalogo: Map<String, Any>,
        categoria: String,
        item: String,
        referencia: String,
        onDone: () -> Unit = {}
    ) {
        val categoriaKey = claveCatalogoPorTexto(catalogo.keys, categoria)
        seleccionarSpinnerPorTexto(catSpinner, categoriaKey) {
            val itemsMap = (catalogo[categoriaKey] as? Map<String, Any>) ?: mapOf()
            val itemKey = claveCatalogoPorTexto(itemsMap.keys, item)
            actualizarSpinnerItem(itemSpinner, itemsMap) {
                seleccionarSpinnerPorTexto(itemSpinner, itemKey) {
                    val refs = (itemsMap[itemKey] as? List<*>)?.mapNotNull { it?.toString() } ?: listOf()
                    actualizarSpinnerTalla(refSpinner, refs) {
                        seleccionarSpinnerPorTexto(refSpinner, referencia) { onDone() }
                    }
                }
            }
        }
    }

internal fun MainActivity.seleccionarProductoEntradaSpinners(
        menuSpinner: Spinner,
        catSpinner: Spinner,
        itemSpinner: Spinner,
        refSpinner: Spinner,
        modulo: String,
        categoria: String,
        item: String,
        referencia: String,
        onDone: () -> Unit = {}
    ) {
        seleccionarSpinnerPorTexto(menuSpinner, modulo) {
            val categoriasMap = catalogoCargado[modulo] as? Map<String, Any> ?: mapOf()
            actualizarSpinnerItem(catSpinner, categoriasMap) {
                val categoriaKey = claveCatalogoPorTexto(categoriasMap.keys, categoria)
                seleccionarSpinnerPorTexto(catSpinner, categoriaKey) {
                    val itemsMap = categoriasMap[categoriaKey] as? Map<String, Any> ?: mapOf()
                    val itemKey = claveCatalogoPorTexto(itemsMap.keys, item)
                    actualizarSpinnerItem(itemSpinner, itemsMap) {
                        seleccionarSpinnerPorTexto(itemSpinner, itemKey) {
                            val refs = (itemsMap[itemKey] as? List<*>)?.mapNotNull { it?.toString() } ?: listOf()
                            actualizarSpinnerTalla(refSpinner, refs) {
                                seleccionarSpinnerPorTexto(refSpinner, referencia) { onDone() }
                            }
                        }
                    }
                }
            }
        }
    }

internal fun MainActivity.abrirFormularioDesdeIA(modulo: String, item: String, cantidad: String, solicitante: String, labor: String) {
        when {
            modulo.equals(AseoCanonicos.MODULO, ignoreCase = true) || modulo.equals("Aseo", ignoreCase = true) -> showAseoForm(item, cantidad, solicitante, labor)
            modulo.equals("Consumibles", ignoreCase = true) -> showConsumiblesForm(item, cantidad, solicitante, labor)
            modulo.equals("Combustible", ignoreCase = true) -> showCombustibleForm(item, cantidad, solicitante)
            ModulosInventario.esModuloLubricantesTaller(modulo) && AppMode.incluyeTaller -> showLubricantesTallerForm(item, cantidad, solicitante)
            ModulosInventario.esModuloLubricantesTaller(modulo) -> showMainMenu()
            ModulosInventario.esModuloAgroquimico(modulo) || modulo.equals("Quimico", ignoreCase = true) -> showQuimicoForm(item, cantidad, solicitante)
            modulo.equals("EPP", ignoreCase = true) -> showEPPForm(item, cantidad, solicitante)
            modulo.equals("Dotación", ignoreCase = true) || modulo.equals("Dotacion", ignoreCase = true) -> showDotacionForm(item, cantidad, solicitante)
            else -> showMainMenu()
        }
    }

internal fun MainActivity.setupSearchBar(root: LinearLayout, moduloFiltro: String?, onSelected: (String, String, String, String) -> Unit) {
        onSmartScanResult = onSelected // Registrar el callback para autoselección vía QR
        val label = TextView(this).apply {
            text = getString(R.string.search_label)
            textSize = 14f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(label, 0) // Al principio del formulario

        val auto = AutoCompleteTextView(this).apply {
            hint = "Escribe para buscar ítem, referencia, categoría o módulo..."
            threshold = 1
            isSingleLine = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setStroke(dp(1), gris)
                cornerRadius = dp(8).toFloat()
            }
            setAnimatedClick(this) { showDropDown() }
        }
        root.addView(auto, 1)

        val listaResultados = todosProductosCatalogo(moduloFiltro).map {
            BusquedaItem(it.modulo, it.categoria, it.item, it.referencia)
        }.distinctBy { "${it.modulo}|${it.categoria}|${it.item}|${it.referencia}" }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listaResultados)
        auto.setAdapter(adapter)

        auto.setOnItemClickListener { parent, _, position, _ ->
            val selection = parent.getItemAtPosition(position) as? BusquedaItem ?: return@setOnItemClickListener
            auto.dismissDropDown()
            onSelected(selection.modulo, selection.categoria, selection.item, selection.referencia)
            auto.setText("", false) // Limpiar después de seleccionar sin disparar otro filtrado
        }
    }

internal fun MainActivity.actualizarSpinnerItem(spinner: Spinner, itemsMap: Map<String, Any>, onDone: () -> Unit) {
        spinner.tag = "SINCRO"
        val items = itemsMap.keys
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { normalizarBusqueda(it) }
            .ifEmpty { listOf("Selecciona opción") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.post { 
            spinner.tag = null
            onDone() 
        }
    }

internal fun MainActivity.actualizarSpinnerTalla(spinner: Spinner, refs: List<String>, onDone: () -> Unit) {
        spinner.tag = "SINCRO"
        val opciones = refs
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("Selecciona opción") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opciones)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.post { 
            spinner.tag = null
            onDone() 
        }
    }

internal fun MainActivity.baseScreen(
        title: String,
        subtitle: String,
        showBack: Boolean = true,
        screenId: String = title,
        backAction: () -> Unit = { showMainMenu() },
    ): LinearLayout {
        val startMs = android.os.SystemClock.elapsedRealtime()
        val destino = screenId.ifBlank { title }
        val mismoDestino = currentScreenId == destino
        if (mismoDestino && startMs - lastScreenRenderedAtMs < 450L) {
            android.util.Log.d("PerfTaller", "baseScreen: $destino render repetido rapido")
        } else {
            android.util.Log.d("PerfTaller", "baseScreen: $destino")
        }
        prepararPantalla(destino)
        lastScreenId = currentScreenId
        currentScreenId = destino
        lastScreenRenderedAtMs = startMs
        currentScreenBackAction = if (showBack) backAction else null
        onSmartScanResult = null // Limpiar el callback al cambiar de pantalla
        
        val frame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(fondo)
        }
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(42), dp(18), dp(22))
        }
        scroll.addView(root)
        frame.addView(scroll)

        if (!title.contains("Asistente IA")) {
            val fabParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(16), dp(20))
            }
            frame.addView(aiAssistantFab { showAIDialog() }, fabParams)
        }

        if (showBack) {
            val back = Button(this).apply {
                text = getString(R.string.back_label)
                setTextColor(verdeOscuro)
                setBackgroundColor(Color.TRANSPARENT)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setAnimatedClick(this) { safeNavigateBack() }
            }
            root.addView(back, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = if (title.startsWith("Bienvenido")) 20f else 24f
            setTextColor(verdeOscuro)
            gravity = Gravity.START
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(gris)
            setPadding(0, dp(4), 0, dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        root.addView(subtitleView)

        setContentView(frame)
        android.util.Log.d("PerfTaller", "baseScreen fin: $destino ${android.os.SystemClock.elapsedRealtime() - startMs}ms")
        return root
    }

internal fun MainActivity.field(
        root: LinearLayout,
        label: String,
        hint: String,
        number: Boolean = false,
        scan: Boolean = false,
        qrNumerico: Boolean = false,
    ): EditText {
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        labelRow.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        if (scan) {
            val btnScan = ImageButton(this).apply {
                setImageResource(R.drawable.ic_scanner)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            labelRow.addView(btnScan, LinearLayout.LayoutParams(dp(40), dp(40)))
            
            // Definimos el comportamiento del botón más tarde para capturar el EditText
        }
        
        root.addView(labelRow)

        val edit = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = when {
                number -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                qrNumerico -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
        }
        
        if (scan) {
            val btnScan = labelRow.getChildAt(1) as ImageButton
            setAnimatedClick(btnScan) {
                currentScannerTarget = edit
                scanLauncher.launch(Intent(this, ScannerActivity::class.java))
            }
        }

        root.addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return edit
    }

internal fun MainActivity.codigoInternoField(
        root: LinearLayout,
        label: String,
        hint: String,
        scan: Boolean = true,
        modoQrNumerico: Boolean = false,
    ): AutoCompleteTextView {
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        labelRow.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (scan) {
            val btnScan = ImageButton(this).apply {
                setImageResource(R.drawable.ic_scanner)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            labelRow.addView(btnScan, LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        root.addView(labelRow)

        val auto = AutoCompleteTextView(this).apply {
            this.hint = hint
            textSize = 16f
            isSingleLine = true
            threshold = 1
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = if (modoQrNumerico) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            }
            background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
            setAnimatedClick(this) { if (text.isNotEmpty()) showDropDown() }
        }

        if (scan) {
            val btnScan = labelRow.getChildAt(1) as ImageButton
            setAnimatedClick(btnScan) {
                currentScannerTarget = auto
                scanLauncher.launch(
                    Intent(this, ScannerActivity::class.java).apply {
                        if (modoQrNumerico) putExtra(ScannerActivity.EXTRA_MODO_TALLER, true)
                    },
                )
            }
        }

        if (modoQrNumerico) {
            auto.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val raw = s?.toString().orEmpty()
                    val numeros = TallerCanonicos.normalizarEntradaQrEscaneada(raw)
                    if (numeros.isNotBlank() && numeros != raw) {
                        auto.setText(numeros)
                        auto.setSelection(numeros.length)
                    }
                }
            })
        }

        root.addView(auto, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return auto
    }

internal fun MainActivity.autoField(root: LinearLayout, label: String, hint: String, suggestions: List<String> = listOf()): AutoCompleteTextView {
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(texto)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(labelView)

        val auto = AutoCompleteTextView(this).apply {
            this.hint = hint
            textSize = 16f
            isSingleLine = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            threshold = 1
            background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 10)
        }
        
        if (suggestions.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
            auto.setAdapter(adapter)
        }

        root.addView(auto, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return auto
    }

internal fun MainActivity.spinner(root: LinearLayout, label: String, values: List<String>): Spinner {
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(texto)
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

internal fun MainActivity.primaryButton(text: String, action: (View) -> Unit): Button {
    return styledButton(text, ArlesButtonStyle.PRIMARY, action)
}

internal fun MainActivity.secondaryButton(text: String, action: (View) -> Unit): Button {
    return styledButton(text, ArlesButtonStyle.SECONDARY, action)
}

internal fun MainActivity.outlineButton(text: String, action: (View) -> Unit): Button {
    return styledButton(text, ArlesButtonStyle.OUTLINE, action)
}

internal fun MainActivity.infoText(message: String): TextView {
    return stockInfoCard(message)
}

internal fun MainActivity.card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = arlesRoundedBackground(Color.WHITE, ArlesPalette.line, 16)
            elevation = dp(2).toFloat()
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(6), 0, dp(12))
            layoutParams = params
            animateViewIn(this)
        }
    }

internal fun MainActivity.cardText(message: String): TextView {
        return TextView(this).apply {
            text = message.trim()
            textSize = 14f
            setTextColor(texto)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(6), 0, dp(8))
            layoutParams = params
            animateViewIn(this)
        }
    }

internal fun MainActivity.roundIconButton(iconRes: Int, color: Int, action: (View) -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            scaleType = ImageView.ScaleType.CENTER
            setAnimatedClick(this, action)
        }
    }

internal fun MainActivity.setAnimatedClick(view: View, action: (View) -> Unit) {
        view.setOnClickListener { clicked ->
            if (!performanceConfig.animationsEnabled()) {
                action(clicked)
                return@setOnClickListener
            }
            val enhanced = performanceConfig.enhancedAnimationsEnabled()
            val pressScale = if (enhanced) 0.92f else 0.96f
            val pressDuration = if (enhanced) 80L else 65L
            val releaseDuration = if (enhanced) 130L else 90L
            clicked.animate()
                .scaleX(pressScale)
                .scaleY(pressScale)
                .setDuration(pressDuration)
                .withEndAction {
                    clicked.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(releaseDuration)
                        .start()
                    action(clicked)
                }
                .start()
        }
    }

internal fun MainActivity.animateViewIn(view: View) {
        if (!performanceConfig.animationsEnabled()) return
        val enhanced = performanceConfig.enhancedAnimationsEnabled()
        val animar: () -> Unit = {
            if (view.isAttachedToWindow) {
                view.alpha = 0f
                view.translationY = dp(if (enhanced) 18 else 10).toFloat()
                if (enhanced) {
                    view.scaleX = 0.97f
                    view.scaleY = 0.97f
                }
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(if (enhanced) 320L else 180L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
        if (view.isAttachedToWindow) animar() else view.post { animar() }
    }

internal fun MainActivity.animateViewInStagger(view: View, index: Int) {
        if (!performanceConfig.enhancedAnimationsEnabled()) {
            animateViewIn(view)
            return
        }
        val delay = index * 55L
        val animar: () -> Unit = {
            if (view.isAttachedToWindow) {
                view.alpha = 0f
                view.translationY = dp(22).toFloat()
                view.scaleX = 0.94f
                view.scaleY = 0.94f
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(delay)
                    .setDuration(360L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
                    .start()
            }
        }
        if (view.isAttachedToWindow) animar() else view.post { animar() }
    }
