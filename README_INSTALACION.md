# Gestión - Proyecto Android Studio

## Qué es este archivo

Este ZIP contiene el **proyecto fuente** de una aplicación Android llamada **Gestión**. No es un APK final ni un documento Word.

La app está pensada para registrar movimientos de finca o bodega desde un celular Android y luego exportar la información en formato compatible con Excel.

## Qué trae la app

- Menú principal con módulos.
- Formularios para consumibles.
- Formularios para combustible: gasolina, ACPM y urea.
- Formularios para químicos.
- Herramientas: registro, salida, entrada, movimientos y registros.
- Consulta de movimientos guardados.
- Exportación CSV compatible con Excel.
- Pantalla interna de información de la app.
- Base local SQLite en el celular.
- Asistente IA conectado mediante Firebase Functions y OpenAI mini.

## Cómo abrirlo en Android Studio

1. Descomprime el ZIP.
2. Abre Android Studio.
3. Selecciona **Open**.
4. Abre la carpeta llamada **GestionAndroid**.
5. Espera a que Android Studio sincronice Gradle.
6. Conecta el celular o usa un emulador.
7. Pulsa **Run**.

## Archivos importantes

```text
GestionAndroid/
├── LEER_PRIMERO.txt
├── README_INSTALACION.md
├── app/
│   └── src/main/java/com/arlessas/gestion/
│       ├── MainActivity.kt
│       └── GestionDbHelper.kt
├── documentacion/
│   ├── DOCUMENTACION_FUNCIONAL.md
│   └── ESTRUCTURA_BASE_DE_DATOS.md
├── functions/
│   └── index.js
├── OPENAI_SETUP.md
└── ejemplos_csv/
    └── EJEMPLO_EXPORTACION.csv
```

## Dónde está el código principal

La pantalla principal, formularios y navegación están en:

```text
app/src/main/java/com/arlessas/gestion/MainActivity.kt
```

La base de datos local SQLite y la exportación CSV están en:

```text
app/src/main/java/com/arlessas/gestion/GestionDbHelper.kt
```

## Asistente con OpenAI mini

El asistente no guarda la API key dentro de Android. La app llama a la función `asistenteOpenAI` en Firebase Functions. Revisa `OPENAI_SETUP.md` para configurar el secreto `OPENAI_API_KEY` y desplegar el backend.

## Cómo se guardan los datos

Los datos se guardan primero en una base local SQLite dentro del celular. Esto permite usar la aplicación sin internet.

Luego se puede exportar la información en CSV. El archivo CSV se puede abrir en Excel y también se puede guardar en Google Drive desde el selector de archivos de Android.

## Limitaciones de esta primera versión

- No sincroniza automáticamente con Google Sheets.
- Requiere desplegar Firebase Functions para que el asistente responda.
- No importa datos desde Excel.

Estas funciones se pueden agregar en una segunda versión.

## Siguiente mejora recomendada

La mejora más importante sería agregar un catálogo maestro de productos con código interno, categoría y unidad base. Después de eso se puede hacer un Kardex real con entradas, salidas y saldo.
