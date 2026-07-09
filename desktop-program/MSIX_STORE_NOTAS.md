# Paquete Microsoft Store / MSIX

Este proyecto mantiene el instalador normal y el ejecutable portable sin cambios. Para preparar el paquete de Microsoft Store usa:

```powershell
npm run dist:store
```

Tambien queda disponible el alias:

```powershell
npm run dist:msix
```

Electron Builder nombra el objetivo de Microsoft Store como `appx`. El archivo generado en `release-store` puede aparecer con extension `.appx`; eso es normal dentro del flujo AppX/MSIX que acepta Partner Center.

El build de Store ejecuta primero `npm run store:assets`, que genera los mosaicos propios en `build/appx` desde `..\Icon.png`. Esto evita que Electron Builder use los assets de ejemplo/predeterminados en `Square150x150Logo`, `Square44x44Logo`, `Wide310x150Logo` y `StoreLogo`.

Paquete corregido para el rechazo de mosaicos predeterminados:

- `release-store\Gestion Almacen Arles 0.1.2.appx`

Ese AppX puede aparecer como `NotSigned` al revisarlo localmente. Para la ruta MSIX de Microsoft Store no hace falta firmarlo manualmente; Microsoft vuelve a firmar el paquete despues de la certificacion. Si se publica como MSI/EXE fuera del flujo MSIX Store, ahi si haria falta un certificado de firma de codigo propio.

Antes de subirlo a Microsoft Partner Center, revisa los datos de identidad que entrega Microsoft al reservar el nombre de la aplicacion:

- `appx.identityName`
- `appx.publisher`
- `appx.publisherDisplayName`

Esos valores estan en `electron-builder.store.json`. Para una subida final a Store deben coincidir con los datos exactos de Partner Center. Microsoft vuelve a firmar el paquete despues del proceso de certificacion/publicacion.

Valores oficiales cargados desde Partner Center:

- `Package/Identity/Name`: `AlmacenArlesSAS.GestionAlmacenArles`
- `Package/Identity/Publisher`: `CN=35B41BDE-4BBF-48D6-95D7-0ADAF9D3433B`
- `Package/Properties/PublisherDisplayName`: `AlmacenArlesSAS`
- `Package Family Name`: `AlmacenArlesSAS.GestionAlmacenArles_2m0dzspc8gq92`
- `Store ID`: `9NDKX9WVTC1N`
