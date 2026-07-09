# Proyecto Empresa

Repositorio limpio para las aplicaciones separadas de Gestion Arles.

## Estructura

```text
Proyecto Empresa/
├── mobile-app-1/       # Android principal: com.arlessas.gestion
├── mobile-app-2/       # Android Taller: com.arlessas.gestion.taller
├── desktop-program/    # Programa de computador Electron/Vite
└── shared/             # Backend y codigo compartido real
```

## Archivos locales no versionados

Cada maquina debe colocar sus credenciales/configuracion local sin subirlas a Git:

- `mobile-app-1/app/google-services.json`, basado en `mobile-app-1/app/google-services.example.json`.
- Variables Firebase para `mobile-app-2`, usando `mobile-app-2/firebase.example.properties` como referencia.
- `desktop-program/.env`, basado en `desktop-program/.env.example`.
- `local.properties` en cada proyecto Android si Android Studio lo necesita.
- `signing/` y keystores solo en la maquina de release.

## App movil principal

```powershell
cd "C:\Users\Almacen\Documents\GitHub\Proyecto Empresa\mobile-app-1"
gradle :app:assembleDebug --warning-mode all --no-daemon --console=plain
gradle :app:assembleRelease --warning-mode all --no-daemon --console=plain
```

## App movil Taller

Para compilar con Firebase real, define las propiedades indicadas en `firebase.example.properties` como variables de entorno o pasalas con `-P`.

```powershell
cd "C:\Users\Almacen\Documents\GitHub\Proyecto Empresa\mobile-app-2"
gradle :app:assembleDebug --warning-mode all --no-daemon --console=plain
gradle :app:assembleRelease --warning-mode all --no-daemon --console=plain
```

## Programa de computador

Antes de ejecutar, crea `desktop-program/.env` desde `.env.example`.

```powershell
cd "C:\Users\Almacen\Documents\GitHub\Proyecto Empresa\desktop-program"
npm install
npm run dev
npm run build
npm run dist:exe
```

El icono fuente de Electron esta en:

```text
desktop-program/assets/icon.ico
```

## Backend compartido

Las funciones Firebase comunes quedaron en:

```text
shared/firebase-functions/
```

Para instalar dependencias y desplegar:

```powershell
cd "C:\Users\Almacen\Documents\GitHub\Proyecto Empresa\shared\firebase-functions"
npm install

cd "C:\Users\Almacen\Documents\GitHub\Proyecto Empresa"
firebase deploy --only functions
```

## Scripts raiz

```powershell
npm run mobile1:debug
npm run mobile1:release
npm run mobile2:debug
npm run mobile2:release
npm run desktop:build
npm run desktop:exe
```

## Notas de rendimiento

- La app principal aun contiene archivos Kotlin grandes y mixtos: `InventarioCatalogo.kt`, `HerramientasGestion.kt`, `AsistenteIA.kt`, `FormulariosOperativos.kt`, `QuimicosCanonicos.kt`, `UiEstilos.kt`, `ArlesDesign.kt`, `GestionDbHelper.kt` y `SistemaGestion.kt`.
- La app principal todavia compila referencias internas a Taller aunque la navegacion visible este separada por `AppMode`.
- `MainActivity` inicializa Firebase, Storage, Functions, TextToSpeech, catalogo y sincronizaciones al arrancar.
- `ic_app_launcher.png` pesa cerca de 1.6 MB.
- El desktop debe revisarse para carga diferida si el bundle de Vite vuelve a superar el limite recomendado.

## Pendientes

- Separar de verdad los archivos Kotlin mixtos por dominio para que `mobile-app-1` no compile codigo de Taller.
- Revisar scripts antiguos de importacion antes de ejecutarlos; usan credenciales locales fuera del repo.
- Decidir si los CSV/JSON de inventario se documentan como datos externos o se agregan como muestras en `shared/sample-data/`.
