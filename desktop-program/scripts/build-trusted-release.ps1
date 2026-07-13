param(
  [string]$Thumbprint = $env:ARLES_SIGNING_CERT_THUMBPRINT
)

$ErrorActionPreference = 'Stop'

if (-not ($Thumbprint -replace '\s', '')) {
  throw 'La compilacion firmada requiere -Thumbprint o ARLES_SIGNING_CERT_THUMBPRINT. No se crean ni instalan certificados automaticamente.'
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $repoRoot
$package = Get-Content -LiteralPath (Join-Path $repoRoot 'package.json') -Raw | ConvertFrom-Json
$version = [string]$package.version

Write-Host 'Preparando icono corporativo...'
& (Join-Path $PSScriptRoot 'create-arles-icon.ps1') | Out-Host

Write-Host 'Compilando aplicacion de escritorio...'
npm run build

Write-Host 'Verificando configuracion de empaquetado...'
npm run verify:package

Write-Host 'Generando portable e instalador sin instalar certificados...'
npx electron-builder --win portable nsis --x64 --config.directories.output=release

Write-Host 'Firmando con el certificado configurado explicitamente...'
& (Join-Path $PSScriptRoot 'sign-release.ps1') -Thumbprint $Thumbprint | Out-Host

$releaseDir = Join-Path $repoRoot 'release'
$portable = Join-Path $releaseDir "Gestion Almacen Arles $version.exe"
$installer = Join-Path $releaseDir "Gestion Almacen Arles Setup $version.exe"
$portableFriendly = Join-Path $releaseDir 'Gestion Almacen Arles firmado.exe'
$installerFriendly = Join-Path $releaseDir 'Instalador Gestion Almacen Arles firmado.exe'

if (Test-Path -LiteralPath $portable) {
  Copy-Item -LiteralPath $portable -Destination $portableFriendly -Force
}

if (Test-Path -LiteralPath $installer) {
  Copy-Item -LiteralPath $installer -Destination $installerFriendly -Force
}

Write-Host 'Version firmada generada sin modificar almacenes de certificados.'
