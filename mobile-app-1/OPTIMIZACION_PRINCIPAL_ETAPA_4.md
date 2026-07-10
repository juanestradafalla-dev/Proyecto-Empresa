# Optimizacion principal etapa 4

Fecha: 2026-07-09
Rama: optimizacion-principal-etapa-4

## Limpieza segura aplicada

- La app principal tiene `BuildConfig.INCLUDE_TALLER=false`.
- El asistente IA ya no consulta Firestore `herramientas` dentro de stock general cuando Taller esta desactivado.
- El asistente IA ya no lee herramientas locales para resultados de herramientas cuando Taller esta desactivado.
- Se agregaron logs `PerfPrincipal` para confirmar omisiones de Taller en IA:
  - `modulo taller omitido contexto=ia_stock_general`
  - `modulo taller omitido contexto=ia_herramientas_local`
  - `consulta IA stock herramientas omitida motivo=taller_desactivado`

## Codigo de Taller detectado que aun compila

- `HerramientasGestion.kt`: pantallas de Taller, herramientas, prestamos, historiales, traslados y Bodega Roja.
- `TallerCanonicos.kt`: catalogo canonico y normalizadores del modulo Taller.
- `ArlesDesign.kt`: estilos, tarjetas, iconos y colores compartidos para Taller.
- `AsistenteIA.kt`: rutas de IA para herramientas y Taller, ahora protegidas por `AppMode.incluyeTaller` en los caminos revisados.
- `SistemaGestion.kt`: importacion CSV de herramientas y compatibilidad de pendientes `herramientas`.
- `SyncGestion.kt`: sincronizacion de pendientes en coleccion `herramientas`.
- `MainActivity.kt`: estado de escaneo de Taller usado por `HerramientasGestion.kt`.
- `ScannerActivity.kt` y `UiEstilos.kt`: modo QR numerico usa normalizacion compatible con Taller, pero tambien se usa desde formularios de la app principal.

## Menus revisados

- `showMainMenu()` no muestra entradas de Taller ni Lubricantes taller cuando `AppMode.incluyeTaller=false`.
- `AppMode.modulosInventarioNube()` y `AppMode.modulosMovimientosNube()` no incluyen Taller en la app principal.
- La navegacion IA hacia herramientas/Taller ya responde que Taller esta en app independiente cuando `AppMode.incluyeTaller=false`.

## Dependencias revisadas

No se elimino ninguna dependencia porque siguen teniendo usos en la app principal:

- Firebase Analytics, Firestore, Auth, Storage y Functions: usados por login, datos, evidencias, perfil, IA e importaciones.
- ML Kit Barcode Scanning y CameraX: usados por el escaner QR principal.
- AppCompat y Material: usados por UI y dialogos.
- Gson: usado por sincronizacion offline, cache/IA y serializacion local.

## Assets revisados

- `logo_andes.jpg` y `ic_app_launcher.png`: assets grandes pero activos.
- `ic_tools.xml`, `ic_lubricants.xml`, `ic_warehouse.xml`: relacionados con Taller, pero aun referenciados por codigo que compila y por vistas compartidas. No se borraron.
- `ic_scanner.xml`, `ic_camera.xml`, `ic_ai.xml`: activos en flujos principales.

## Pendientes riesgosos

- Separar `HerramientasGestion.kt` y `TallerCanonicos.kt` por source set/flavor para que mobile-app-1 deje de compilarlos fisicamente.
- Crear stubs o interfaces minimas si se excluye codigo de Taller, porque hoy varias funciones compartidas aun referencian tipos y helpers de Taller.
- Revisar si los lubricantes de taller deben quedar en Quimicos canonicos de la app principal como historial/compatibilidad o moverse completamente a mobile-app-2.
- Revisar `SyncGestion.kt` antes de omitir pendientes `herramientas`; podria haber pendientes antiguos de usuarios con versiones previas.
- Revisar `ScannerActivity.EXTRA_MODO_TALLER`: tambien se usa para QR numerico en formularios principales, no solo para Taller.
