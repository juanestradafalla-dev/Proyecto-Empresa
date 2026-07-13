param(
  [string]$Source = '..\branding\logo_gestion_arles.png',
  [string]$Output = 'assets\icon.ico'
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $repoRoot $Source
$outputPath = Join-Path $repoRoot $Output
$outputDir = Split-Path -Parent $outputPath

if (-not (Test-Path -LiteralPath $sourcePath)) {
  throw "No se encontro el logo de origen: $sourcePath"
}

if (-not (Test-Path -LiteralPath $outputDir)) {
  New-Item -ItemType Directory -Path $outputDir | Out-Null
}

function New-IconPngBytes {
  param(
    [System.Drawing.Image]$Image,
    [int]$Size
  )

  $bitmap = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $graphics.Clear([System.Drawing.Color]::Transparent)

  $side = [Math]::Min($Image.Width, $Image.Height)
  $srcX = [Math]::Floor(($Image.Width - $side) / 2)
  $srcY = [Math]::Floor(($Image.Height - $side) / 2)
  $srcRect = New-Object System.Drawing.Rectangle $srcX, $srcY, $side, $side
  $dstRect = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size

  $graphics.DrawImage($Image, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
  $graphics.Dispose()

  $stream = New-Object System.IO.MemoryStream
  $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
  $bytes = $stream.ToArray()
  $stream.Dispose()
  $bitmap.Dispose()

  return ,$bytes
}

$sourceImage = [System.Drawing.Image]::FromFile($sourcePath)
$sizes = @(16, 24, 32, 48, 64, 128, 256)
$frames = foreach ($size in $sizes) {
  [PSCustomObject]@{
    Size = $size
    Bytes = [byte[]](New-IconPngBytes -Image $sourceImage -Size $size)
  }
}
$sourceImage.Dispose()

$fileStream = [System.IO.File]::Create($outputPath)
$writer = New-Object System.IO.BinaryWriter $fileStream

$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$frames.Count)

$offset = 6 + (16 * $frames.Count)
foreach ($frame in $frames) {
  $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
  $writer.Write([Byte]$dimension)
  $writer.Write([Byte]$dimension)
  $writer.Write([Byte]0)
  $writer.Write([Byte]0)
  $writer.Write([UInt16]1)
  $writer.Write([UInt16]32)
  $writer.Write([UInt32]$frame.Bytes.Length)
  $writer.Write([UInt32]$offset)
  $offset += $frame.Bytes.Length
}

foreach ($frame in $frames) {
  $writer.Write($frame.Bytes)
}

$writer.Dispose()
$fileStream.Dispose()

Write-Host "Icono corporativo creado en: $outputPath"
