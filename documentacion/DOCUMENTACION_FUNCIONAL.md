# Documentación funcional - App Gestión

## Objetivo

La aplicación **Gestión** sirve para registrar movimientos operativos de una finca o bodega desde un celular Android. La prioridad es que el registro sea rápido, claro y exportable a Excel.

## Menú principal

La app abre con cinco opciones principales:

1. Consumibles
2. Combustible
3. Químico
4. Herramientas
5. Registros y exportación
6. Información de la app

## 1. Consumibles

Este módulo registra salidas de materiales comunes que no son combustible, químicos ni herramientas.

Ejemplos:

- Jabón
- Tubería
- Cinta
- Repuestos menores
- Elementos de aseo
- Papelería
- Materiales de mantenimiento

Campos incluidos:

- Ítem
- Referencia
- Marca
- Cantidad
- Unidad
- Solicitante
- Labor o destino
- Observaciones

## 2. Combustible

Este módulo registra la salida de:

- Gasolina
- ACPM
- Urea

Campos incluidos:

- Tipo de combustible o insumo
- Cantidad
- Unidad
- Horómetro opcional
- Maquinaria o equipo
- Solicitante
- Labor o frente
- Observaciones

El horómetro se dejó opcional porque no todos los equipos lo tienen.

## 3. Químico

Este módulo registra salidas de productos químicos o agroinsumos.

Ejemplos:

- Fertilizantes
- Fungicidas
- Insecticidas
- Herbicidas
- Coadyuvantes
- Correctivos líquidos o sólidos

Campos incluidos:

- Ítem
- Referencia o concentración
- Cantidad
- Unidad
- Solicitante
- Labor o aplicación
- Observaciones

## 4. Herramientas

Este módulo tiene submenús:

### Registro de herramienta
Crea el inventario base de herramientas.

Campos:

- Herramienta
- Referencia
- Marca
- Código o serial
- Estado
- Ubicación
- Responsable
- Observaciones

### Salida de herramienta
Registra cuando una herramienta sale de almacén o bodega.

### Entrada de herramienta
Registra cuando una herramienta se devuelve.

### Movimientos de herramientas
Consulta entradas y salidas.

### Registros de herramientas
Consulta el inventario base de herramientas creadas.

## 5. Registros y exportación

Desde este módulo se pueden consultar los movimientos guardados y exportarlos a un archivo CSV compatible con Excel.

La exportación genera un archivo con nombre parecido a:

```text
Gestion_2026-05-13_14-30.csv
```

Ese archivo se puede guardar en:

- Memoria interna del celular
- Carpeta de documentos
- Google Drive, si está instalado en el celular
- Otra aplicación de archivos compatible

## Recomendaciones para una segunda versión

Para una versión más avanzada se recomienda agregar:

- Catálogo maestro de productos.
- Códigos internos por producto.
- Existencias automáticas tipo Kardex.
- Reportes por fechas.
- Filtros por solicitante, labor, maquinaria y módulo.
- Sincronización directa con Google Sheets.
- Acceso con PIN o usuario.
- Copia de seguridad automática.
