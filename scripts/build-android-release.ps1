$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$mobileRoot = Join-Path $repoRoot 'mobile-app-1'
Set-Location $mobileRoot

& (Join-Path $PSScriptRoot 'create-android-release-keystore.ps1') | Out-Host

gradle :app:assembleRelease

$apk = Join-Path $mobileRoot 'app\build\outputs\apk\release\app-release.apk'
$outDir = Join-Path $repoRoot 'release\android'
$friendlyApk = Join-Path $outDir 'Gestion Arles firmado.apk'

if (-not (Test-Path -LiteralPath $apk)) {
  throw "No se encontro APK release en $apk"
}

if (-not (Test-Path -LiteralPath $outDir)) {
  New-Item -ItemType Directory -Path $outDir | Out-Null
}

Copy-Item -LiteralPath $apk -Destination $friendlyApk -Force

Write-Host "APK firmado listo: $friendlyApk"
