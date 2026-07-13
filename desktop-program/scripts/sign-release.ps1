param(
  [string]$Thumbprint = $env:ARLES_SIGNING_CERT_THUMBPRINT,
  [string[]]$Targets
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$package = Get-Content -LiteralPath (Join-Path $repoRoot 'package.json') -Raw | ConvertFrom-Json
$version = [string]$package.version
$normalizedThumbprint = ($Thumbprint -replace '\s', '').ToUpperInvariant()

if (-not $normalizedThumbprint) {
  throw 'Configura -Thumbprint o ARLES_SIGNING_CERT_THUMBPRINT con la huella de un certificado de firma existente.'
}

$certStores = @('Cert:\CurrentUser\My', 'Cert:\LocalMachine\My')
$cert = $certStores | ForEach-Object {
  Get-ChildItem -Path $_ -CodeSigningCert -ErrorAction SilentlyContinue
} |
  Where-Object {
    ($_.Thumbprint -replace '\s', '').ToUpperInvariant() -eq $normalizedThumbprint -and
    $_.HasPrivateKey -and
    $_.NotAfter -gt (Get-Date)
  } |
  Select-Object -First 1

if (-not $cert) {
  throw 'No se encontro un certificado de firma vigente con la huella configurada en los almacenes Personal del usuario o del equipo.'
}

if (-not $Targets -or $Targets.Count -eq 0) {
  $Targets = @(
    "release\Gestion Almacen Arles $version.exe",
    "release\Gestion Almacen Arles Setup $version.exe",
    'release\win-unpacked\Gestion Almacen Arles.exe',
    'release\win-unpacked\resources\elevate.exe'
  )
}

$files = foreach ($target in $Targets) {
  $candidate = Join-Path $repoRoot $target
  if (Test-Path -LiteralPath $candidate -PathType Leaf) {
    Get-Item -LiteralPath $candidate
  }
}

$files = @($files | Sort-Object FullName -Unique)
if ($files.Count -eq 0) {
  throw 'No se encontraron ejecutables para firmar.'
}

foreach ($file in $files) {
  Write-Host "Firmando explicitamente: $($file.FullName)"
  $signature = Set-AuthenticodeSignature -FilePath $file.FullName -Certificate $cert -HashAlgorithm SHA256
  if ($signature.Status -ne 'Valid') {
    throw "No se pudo firmar $($file.Name): $($signature.StatusMessage)"
  }
}

Write-Host 'Verificacion de firmas:'
foreach ($file in $files) {
  $signature = Get-AuthenticodeSignature -FilePath $file.FullName
  Write-Host "$($file.Name): $($signature.Status) - $($signature.SignerCertificate.Subject)"
}
