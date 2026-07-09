# Estructura de datos

La app usa una base local SQLite llamada:

```text
gestion.db
```

## Tabla: movimientos

Guarda salidas, entradas y registros operativos.

Columnas principales:

| Campo | Descripción |
|---|---|
| id | Consecutivo automático |
| fecha | Fecha y hora del registro |
| modulo | Consumibles, Combustible, Químico o Herramientas |
| tipoMovimiento | Salida, Entrada o Registro |
| item | Nombre del producto, insumo o herramienta |
| referencia | Referencia, presentación, concentración o modelo |
| marca | Marca del producto o herramienta |
| cantidad | Cantidad registrada |
| unidad | Unidad de medida |
| solicitante | Persona que solicita o recibe |
| labor | Labor, destino o ubicación |
| maquinaria | Equipo o maquinaria, usado en combustible |
| horometro | Horómetro opcional |
| herramientaId | Relación con herramienta registrada |
| estado | Estado de la herramienta |
| observaciones | Comentarios adicionales |

## Tabla: herramientas

Guarda el inventario base de herramientas.

| Campo | Descripción |
|---|---|
| id | Consecutivo automático |
| fechaRegistro | Fecha de creación del registro |
| nombre | Nombre de la herramienta |
| referencia | Modelo o descripción |
| marca | Marca |
| codigo | Código interno, placa o serial |
| estado | Disponible, En uso, Mantenimiento, Dañada o Perdida |
| ubicacion | Lugar donde se encuentra |
| responsable | Persona responsable |
| observaciones | Comentarios adicionales |

## Exportación a Excel

La app exporta en CSV separado por punto y coma.

Se usa punto y coma porque en muchas configuraciones regionales en español Excel interpreta mejor este separador.

El archivo exportado incluye una marca BOM UTF-8 para que Excel reconozca correctamente tildes y caracteres como ñ.

## Por qué no se conecta directo a Drive en esta primera versión

Una sincronización directa con Google Drive o Google Sheets requiere autenticación OAuth, permisos de cuenta y configuración en Google Cloud Console.

Para una primera versión estable, se usa el selector de documentos de Android. Esto permite guardar el CSV en Google Drive si la app de Drive está instalada, sin programar todavía una integración compleja.
