$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $repoRoot

Write-Host 'Generando APK Android firmado...'
& (Join-Path $PSScriptRoot 'build-android-release.ps1') | Out-Host

Write-Host 'Generando programa de computador firmado...'
Push-Location (Join-Path $repoRoot 'desktop-program')
try {
  npm run dist:trusted
} finally {
  Pop-Location
}

Write-Host 'Entregables confiables generados para Android y computador.'
