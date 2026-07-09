# Script para importar Dotación usando Firebase CLI
$csvPath = "C:/Users/Almacen/AndroidStudioProjects/GestionAndroid/Inventario Dotacion.csv"
$data = Import-Csv -Path $csvPath -Delimiter ","

$importedAt = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
$count = 0

foreach ($row in $data) {
    $code = $row.codigo_interno.Trim().ToUpper()
    $item = $row.descripcion.Trim()
    $talla = $row.talla.Trim()
    $tipo = $row.tipo_prenda.Trim()
    $cantidad = [double]$row.stock_actual.Replace(",", ".")
    $referencia = "Talla: $talla | $tipo"
    $nombre_completo = "$item $referencia"
    $busqueda = ("$code $nombre_completo").ToLower()
    $categoria = $row.categoria.Trim()
    $parte = $row.parte_cuerpo.Trim()

    # Construir JSON para Firestore
    $json = @{
        modulo = "Dotación"
        categoria = $categoria
        item = $item
        referencia = $referencia
        marca = "Institucional"
        nombre_completo = $nombre_completo
        busqueda = $busqueda
        codigo_interno = $code
        unidad = $row.unidad
        cantidad = $cantidad
        ultima_fecha = $importedAt
        ultimo_solicitante = "Importador IA PowerShell"
        observaciones = "Parte: $parte"
    } | ConvertTo-Json -Compress

    # Subir a existencias
    Write-Host "Subiendo [$code] $item ..."
    firebase firestore:documents:set "existencias/$code" "$json" --project arles-gestion

    # Subir a catalogo_personalizado (sin cantidad)
    $jsonCat = @{
        modulo = "Dotación"
        categoria = $categoria
        item = $item
        referencia = $referencia
        marca = "Institucional"
        nombre_completo = $nombre_completo
        busqueda = $busqueda
        codigo_interno = $code
        unidad = $row.unidad
        ultima_fecha = $importedAt
        ultimo_solicitante = "Importador IA PowerShell"
        observaciones = "Parte: $parte"
    } | ConvertTo-Json -Compress
    firebase firestore:documents:set "catalogo_personalizado/$code" "$jsonCat" --project arles-gestion

    $count++
}

Write-Host "`nImportación completada: $count artículos de dotación procesados."
