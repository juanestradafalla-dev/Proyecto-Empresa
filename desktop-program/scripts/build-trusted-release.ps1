$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $repoRoot

Write-Host 'Preparando certificado interno de Arles SAS...'
& (Join-Path $PSScriptRoot 'ensure-arles-signing-cert.ps1') | Out-Host

Write-Host 'Preparando icono corporativo...'
& (Join-Path $PSScriptRoot 'create-arles-icon.ps1') | Out-Host

Write-Host 'Compilando aplicacion de escritorio...'
npm run build

Write-Host 'Generando portable e instalador...'
npx electron-builder --win portable nsis --x64 --config.directories.output=release

Write-Host 'Firmando ejecutables generados...'
& (Join-Path $PSScriptRoot 'sign-release.ps1') | Out-Host

$releaseDir = Join-Path $repoRoot 'release'
$portable = Join-Path $releaseDir 'Gestion Almacen Arles 0.1.0.exe'
$installer = Join-Path $releaseDir 'Gestion Almacen Arles Setup 0.1.0.exe'
$portableFriendly = Join-Path $releaseDir 'Gestion Almacen Arles confiable.exe'
$installerFriendly = Join-Path $releaseDir 'Instalador Gestion Almacen Arles.exe'

if (Test-Path -LiteralPath $portable) {
  Copy-Item -LiteralPath $portable -Destination $portableFriendly -Force
}

if (Test-Path -LiteralPath $installer) {
  Copy-Item -LiteralPath $installer -Destination $installerFriendly -Force
}

Write-Host 'Version confiable del panel generada en la carpeta release.'
