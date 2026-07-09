param(
  [string]$Alias = 'arles-gestion',
  [string]$DName = 'CN=Arles SAS Gestion Android, O=Arles SAS, C=CO'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$signingDir = Join-Path $repoRoot 'mobile-app-1\app\signing'
$keystorePath = Join-Path $signingDir 'arles-gestion-release.jks'
$propertiesPath = Join-Path $signingDir 'release-signing.properties'

if (-not (Test-Path -LiteralPath $signingDir)) {
  New-Item -ItemType Directory -Path $signingDir | Out-Null
}

function New-Password {
  -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
}

$storePassword = New-Password
$keyPassword = New-Password

if (-not (Test-Path -LiteralPath $keystorePath)) {
  $keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
  if (-not $keytool) {
    $androidStudioKeytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
    if (Test-Path -LiteralPath $androidStudioKeytool) {
      $keytool = Get-Item -LiteralPath $androidStudioKeytool
    }
  }
  if (-not $keytool) {
    throw 'No se encontro keytool.exe. Instala Android Studio o configura Java en PATH.'
  }

  & $keytool.Source -genkeypair `
    -v `
    -keystore $keystorePath `
    -storetype JKS `
    -storepass $storePassword `
    -keypass $keyPassword `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname $DName | Out-Host
} elseif (Test-Path -LiteralPath $propertiesPath) {
  Write-Host "Ya existe keystore Android: $keystorePath"
  Write-Host "Se conserva release-signing.properties existente."
  exit 0
} else {
  throw "Existe $keystorePath pero no existe $propertiesPath. No puedo recrear claves sin reemplazar la firma."
}

@"
storeFile=signing/arles-gestion-release.jks
storePassword=$storePassword
keyAlias=$Alias
keyPassword=$keyPassword
"@ | Set-Content -Path $propertiesPath -Encoding ASCII

Write-Host "Keystore Android creado: $keystorePath"
Write-Host "Propiedades privadas creadas: $propertiesPath"
Write-Host 'Guarda una copia de mobile-app-1/app/signing en un lugar seguro; si se pierde, futuras actualizaciones pueden requerir otra instalacion.'
