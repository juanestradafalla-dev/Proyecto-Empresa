# Panel de escritorio Gestion Almacen

Panel web local para revisar la informacion de Firestore desde el computador.

## Iniciar

```powershell
cd C:\Users\Almacen\AndroidStudioProjects\GestionAndroid\desktop-program
npm run dev -- --port 5174
```

Luego abre:

```text
http://127.0.0.1:5174
```

## Programa para Windows

El ejecutable portable queda en:

```text
C:\Users\Almacen\Desktop\Gestion Almacen.exe
```

Para reconstruirlo despues de cambios:

```powershell
cd C:\Users\Almacen\AndroidStudioProjects\GestionAndroid\desktop-program
npm run dist:exe
```

Ese comando genera la salida completa en:

```text
C:\Users\Almacen\Desktop\GestionAlmacenRelease
```

## Acceso

Usa un usuario de Firebase Authentication que tambien exista como usuario activo en la coleccion `usuarios`.

## Modulos

El panel lee en vivo:

- `existencias` para inventario general.
- `productos_aseo` para el inventario actual del modulo ASEO.
- `movimientos` para entradas y salidas.
- `herramientas` para el inventario base del modulo Herramientas.

En salidas, cada movimiento muestra un boton de evidencia para abrir la foto guardada en `fotoUrl`.
