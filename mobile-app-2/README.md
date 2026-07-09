# Mobile App 2 - Taller

## Configuracion local de Firebase

Esta app compila con el `applicationId`:

```text
com.arlessas.gestion.taller
```

Para que Firebase/Firestore funcione despues de separar esta app de `mobile-app-1`, el proyecto Firebase debe tener registrada una app Android con ese mismo paquete. No sirve reutilizar el `google-services.json` de `mobile-app-1` si ese archivo fue generado para `com.arlessas.gestion`.

Pasos locales:

1. En Firebase, abre el proyecto `arles-gestion`.
2. Registra una app Android con paquete `com.arlessas.gestion.taller`.
3. Descarga el `google-services.json` de esa app Android.
4. Coloca el archivo aqui:

```text
mobile-app-2/app/google-services.json
```

No subas `google-services.json` al repositorio. El `.gitignore` raiz ya ignora `**/google-services.json`.

Nota: el modulo `app` aplica `com.google.gms.google-services` automaticamente cuando existe `app/google-services.json`. Si el JSON no existe, se pueden pasar las propiedades `FIREBASE_*` indicadas en `firebase.example.properties` como variables de entorno o propiedades Gradle.

Las reglas de Firestore tambien exigen que el usuario este autenticado y tenga permiso activo en la coleccion `usuarios`, salvo el owner configurado en reglas.
