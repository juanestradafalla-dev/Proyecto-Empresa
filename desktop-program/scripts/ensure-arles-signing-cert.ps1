param(
  [string]$Subject = 'CN=Arles SAS Internal Code Signing, O=Arles SAS',
  [string]$FriendlyName = 'Arles SAS - Firma interna de aplicaciones',
  [string]$CertPath = 'build\certificates\arles-code-signing.cer'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$certOutput = Join-Path $repoRoot $CertPath
$certDir = Split-Path -Parent $certOutput

if (-not (Test-Path -LiteralPath $certDir)) {
  New-Item -ItemType Directory -Path $certDir | Out-Null
}

$cert = Get-ChildItem -Path Cert:\CurrentUser\My -CodeSigningCert |
  Where-Object {
    $_.Subject -eq $Subject -and
    $_.HasPrivateKey -and
    $_.NotAfter -gt (Get-Date).AddMonths(6)
  } |
  Sort-Object NotAfter -Descending |
  Select-Object -First 1

if (-not $cert) {
  $cert = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject $Subject `
    -FriendlyName $FriendlyName `
    -CertStoreLocation 'Cert:\CurrentUser\My' `
    -KeyAlgorithm RSA `
    -KeyLength 3072 `
    -HashAlgorithm SHA256 `
    -KeyUsage DigitalSignature `
    -NotAfter (Get-Date).AddYears(5)
}

Export-Certificate -Cert $cert -FilePath $certOutput -Force | Out-Null

$certutil = Join-Path $env:WINDIR 'System32\certutil.exe'
& $certutil -user -f -addstore TrustedPublisher $certOutput | Out-Null
& $certutil -user -f -addstore Root $certOutput | Out-Null

Write-Host "Certificado interno listo: $($cert.Subject)"
Write-Host "Huella: $($cert.Thumbprint)"
Write-Host "Publico exportado en: $certOutput"
Write-Host 'Instalado para este usuario en TrustedPublisher y Root.'
