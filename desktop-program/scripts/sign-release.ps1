param(
  [string]$Subject = 'CN=Arles SAS Internal Code Signing, O=Arles SAS',
  [string[]]$Targets
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

& (Join-Path $PSScriptRoot 'ensure-arles-signing-cert.ps1') -Subject $Subject | Out-Host

$cert = Get-ChildItem -Path Cert:\CurrentUser\My -CodeSigningCert |
  Where-Object {
    $_.Subject -eq $Subject -and
    $_.HasPrivateKey -and
    $_.NotAfter -gt (Get-Date)
  } |
  Sort-Object NotAfter -Descending |
  Select-Object -First 1

if (-not $cert) {
  throw 'No se encontro el certificado de firma interna de Arles SAS.'
}

if (-not $Targets -or $Targets.Count -eq 0) {
  $Targets = @(
    'release\Gestion Almacen Arles 0.1.0.exe',
    'release\Gestion Almacen Arles Setup 0.1.0.exe',
    'release\win-unpacked\Gestion Almacen Arles.exe',
    'release\win-unpacked\resources\elevate.exe'
  )
}

$files = foreach ($target in $Targets) {
  Get-ChildItem -Path (Join-Path $repoRoot $target) -File -ErrorAction SilentlyContinue
}

$files = $files | Sort-Object FullName -Unique
if (-not $files -or $files.Count -eq 0) {
  throw 'No se encontraron ejecutables para firmar.'
}

foreach ($file in $files) {
  Write-Host "Firmando: $($file.FullName)"
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
