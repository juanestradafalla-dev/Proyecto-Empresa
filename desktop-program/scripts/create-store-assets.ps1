param(
  [string]$Source = '..\Icon.png',
  [string]$Output = 'build\appx'
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $repoRoot $Source
$outputPath = Join-Path $repoRoot $Output

if (-not (Test-Path -LiteralPath $sourcePath)) {
  throw "No se encontro el logo de origen: $sourcePath"
}

if (-not (Test-Path -LiteralPath $outputPath)) {
  New-Item -ItemType Directory -Path $outputPath | Out-Null
}
else {
  Get-ChildItem -LiteralPath $outputPath -Filter '*.png' -File | Remove-Item -Force
}

function Set-RenderQuality {
  param([System.Drawing.Graphics]$Graphics)

  $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $Graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  $Graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
}

function Save-Png {
  param(
    [System.Drawing.Bitmap]$Bitmap,
    [string]$Name
  )

  $path = Join-Path $outputPath $Name
  if (Test-Path -LiteralPath $path) {
    Remove-Item -LiteralPath $path -Force
  }
  $Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-RoundedRectanglePath {
  param(
    [int]$X,
    [int]$Y,
    [int]$Width,
    [int]$Height,
    [int]$Radius
  )

  $diameter = [Math]::Max(1, $Radius * 2)
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
  $path.AddArc(($X + $Width - $diameter), $Y, $diameter, $diameter, 270, 90)
  $path.AddArc(($X + $Width - $diameter), ($Y + $Height - $diameter), $diameter, $diameter, 0, 90)
  $path.AddArc($X, ($Y + $Height - $diameter), $diameter, $diameter, 90, 90)
  $path.CloseFigure()
  return $path
}

function New-TileBitmap {
  param(
    [System.Drawing.Image]$Logo,
    [int]$Width,
    [int]$Height,
    [string]$Mode = 'square'
  )

  $bitmap = New-Object System.Drawing.Bitmap $Width, $Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  Set-RenderQuality -Graphics $graphics

  $rect = New-Object System.Drawing.Rectangle 0, 0, $Width, $Height
  $start = [System.Drawing.Color]::FromArgb(255, 5, 67, 45)
  $end = [System.Drawing.Color]::FromArgb(255, 113, 174, 30)
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $start, $end, ([System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
  $graphics.FillRectangle($brush, $rect)
  $brush.Dispose()

  $logoSide = if ($Mode -eq 'wide') {
    [int]($Height * 0.72)
  }
  elseif ($Mode -eq 'large') {
    [int]([Math]::Min($Width, $Height) * 0.58)
  }
  else {
    [int]([Math]::Min($Width, $Height) * 0.78)
  }

  $logoX = if ($Mode -eq 'wide') { [int]($Width * 0.08) } else { [int](($Width - $logoSide) / 2) }
  $logoY = if ($Mode -eq 'large') { [int]($Height * 0.11) } else { [int](($Height - $logoSide) / 2) }

  $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(55, 0, 0, 0))
  $shadowRect = New-Object System.Drawing.Rectangle ($logoX + [Math]::Max(1, [int]($logoSide * 0.035))), ($logoY + [Math]::Max(1, [int]($logoSide * 0.045))), $logoSide, $logoSide
  $graphics.FillEllipse($shadowBrush, $shadowRect)
  $shadowBrush.Dispose()

  $srcRect = New-Object System.Drawing.Rectangle 0, 0, $Logo.Width, $Logo.Height
  $dstRect = New-Object System.Drawing.Rectangle $logoX, $logoY, $logoSide, $logoSide
  $previousClip = $graphics.Clip.Clone()
  $clipPath = New-RoundedRectanglePath -X $logoX -Y $logoY -Width $logoSide -Height $logoSide -Radius ([int]($logoSide * 0.18))
  $graphics.SetClip($clipPath)
  $graphics.DrawImage($Logo, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
  $graphics.Clip = $previousClip
  $clipPath.Dispose()
  $previousClip.Dispose()

  if ($Mode -eq 'wide') {
    $titleBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $subBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230, 230, 245, 218))
    $titleFont = New-Object System.Drawing.Font 'Segoe UI', ([single]($Height * 0.14)), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $subFont = New-Object System.Drawing.Font 'Segoe UI', ([single]($Height * 0.09)), ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Near
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $format.Trimming = [System.Drawing.StringTrimming]::EllipsisWord

    $textX = [int]($Width * 0.36)
    $titleRect = New-Object System.Drawing.RectangleF $textX, ([single]($Height * 0.28)), ([single]($Width - $textX - ($Width * 0.06))), ([single]($Height * 0.24))
    $subRect = New-Object System.Drawing.RectangleF $textX, ([single]($Height * 0.52)), ([single]($Width - $textX - ($Width * 0.06))), ([single]($Height * 0.16))
    $graphics.DrawString('Gestion Almacen', $titleFont, $titleBrush, $titleRect, $format)
    $graphics.DrawString('Arles SAS', $subFont, $subBrush, $subRect, $format)

    $format.Dispose()
    $titleFont.Dispose()
    $subFont.Dispose()
    $titleBrush.Dispose()
    $subBrush.Dispose()
  }

  if ($Mode -eq 'large') {
    $titleBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $titleFont = New-Object System.Drawing.Font 'Segoe UI', ([single]($Height * 0.07)), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $format.Trimming = [System.Drawing.StringTrimming]::EllipsisWord

    $textRect = New-Object System.Drawing.RectangleF ([single]($Width * 0.08)), ([single]($Height * 0.72)), ([single]($Width * 0.84)), ([single]($Height * 0.12))
    $graphics.DrawString('Gestion Almacen Arles', $titleFont, $titleBrush, $textRect, $format)

    $format.Dispose()
    $titleFont.Dispose()
    $titleBrush.Dispose()
  }

  $graphics.Dispose()
  return $bitmap
}

function New-StoreAsset {
  param(
    [System.Drawing.Image]$Logo,
    [string]$Name,
    [int]$Width,
    [int]$Height,
    [string]$Mode
  )

  $bitmap = New-TileBitmap -Logo $Logo -Width $Width -Height $Height -Mode $Mode
  Save-Png -Bitmap $bitmap -Name $Name
  $bitmap.Dispose()
}

$logo = [System.Drawing.Image]::FromFile($sourcePath)

$assets = @(
  @{ Name = 'StoreLogo.png'; Width = 50; Height = 50; Mode = 'square' },
  @{ Name = 'StoreLogo.scale-100.png'; Width = 50; Height = 50; Mode = 'square' },
  @{ Name = 'StoreLogo.scale-200.png'; Width = 100; Height = 100; Mode = 'square' },
  @{ Name = 'Square44x44Logo.png'; Width = 44; Height = 44; Mode = 'square' },
  @{ Name = 'Square44x44Logo.scale-100.png'; Width = 44; Height = 44; Mode = 'square' },
  @{ Name = 'Square44x44Logo.scale-200.png'; Width = 88; Height = 88; Mode = 'square' },
  @{ Name = 'Square150x150Logo.png'; Width = 150; Height = 150; Mode = 'square' },
  @{ Name = 'Square150x150Logo.scale-100.png'; Width = 150; Height = 150; Mode = 'square' },
  @{ Name = 'Square150x150Logo.scale-200.png'; Width = 300; Height = 300; Mode = 'square' },
  @{ Name = 'SmallTile.png'; Width = 71; Height = 71; Mode = 'square' },
  @{ Name = 'SmallTile.scale-100.png'; Width = 71; Height = 71; Mode = 'square' },
  @{ Name = 'SmallTile.scale-200.png'; Width = 142; Height = 142; Mode = 'square' },
  @{ Name = 'Wide310x150Logo.png'; Width = 310; Height = 150; Mode = 'wide' },
  @{ Name = 'Wide310x150Logo.scale-100.png'; Width = 310; Height = 150; Mode = 'wide' },
  @{ Name = 'Wide310x150Logo.scale-200.png'; Width = 620; Height = 300; Mode = 'wide' },
  @{ Name = 'LargeTile.png'; Width = 310; Height = 310; Mode = 'large' },
  @{ Name = 'LargeTile.scale-100.png'; Width = 310; Height = 310; Mode = 'large' }
)

foreach ($asset in $assets) {
  New-StoreAsset -Logo $logo -Name $asset.Name -Width $asset.Width -Height $asset.Height -Mode $asset.Mode
}

$logo.Dispose()

Write-Host "Assets Microsoft Store creados en: $outputPath"
