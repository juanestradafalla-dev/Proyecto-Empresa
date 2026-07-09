# Configuración de Asistente IA con OpenAI mini

La app llama a una Firebase Cloud Function llamada `asistenteOpenAI`, y esa función llama a OpenAI usando el modelo `gpt-5-mini`.

## 1. Instalar herramientas

```bash
npm install -g firebase-tools
firebase login
```

## 2. Instalar dependencias del backend

Desde la raíz del proyecto:

```bash
cd functions
npm install
cd ..
```

## 3. Guardar la API key de OpenAI como secreto

No pegues la API key dentro de Android.

```bash
firebase functions:secrets:set OPENAI_API_KEY
```

Pega tu clave cuando Firebase la pida.

## 4. Desplegar la función

```bash
firebase deploy --only functions
```

## 5. Probar la app

Abre el proyecto en Android Studio, sincroniza Gradle y ejecuta la app. El botón de Asistente IA llamará a `asistenteOpenAI`.

## Archivo principal del backend

```text
functions/index.js
```

El modelo está definido en esta línea:

```js
const OPENAI_MODEL = "gpt-5-mini";
```

Si después quieres usar otro modelo compatible, cambia solo esa línea y vuelve a desplegar.
