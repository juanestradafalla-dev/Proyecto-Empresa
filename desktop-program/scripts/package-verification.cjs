const EXPECTED_PACKAGE_FILES = [
  'assets/icon.ico',
  'dist/**/*',
  'electron/**/*',
  'package.json',
];

function sameStringArray(left, right) {
  return Array.isArray(left)
    && Array.isArray(right)
    && [...left].sort().join('\n') === [...right].sort().join('\n');
}

function sameRecord(left = {}, right = {}) {
  return JSON.stringify(Object.fromEntries(Object.entries(left).sort()))
    === JSON.stringify(Object.fromEntries(Object.entries(right).sort()));
}

function validatePackageMetadata(packageJson, packageLock, storeConfig) {
  const errors = [];
  const rootLock = packageLock?.packages?.[''] ?? {};

  if (!/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(packageJson?.version ?? '')) {
    errors.push('package.json no contiene una versión semántica válida.');
  }
  if (packageLock?.version !== packageJson?.version || rootLock.version !== packageJson?.version) {
    errors.push('package.json y package-lock.json no tienen la misma versión.');
  }
  if (packageLock?.name !== packageJson?.name || rootLock.name !== packageJson?.name) {
    errors.push('package.json y package-lock.json no tienen el mismo nombre.');
  }
  if (!sameRecord(packageJson?.dependencies, rootLock.dependencies)) {
    errors.push('Las dependencias de producción no están sincronizadas con package-lock.json.');
  }
  if (!sameRecord(packageJson?.devDependencies, rootLock.devDependencies)) {
    errors.push('Las dependencias de desarrollo no están sincronizadas con package-lock.json.');
  }
  if (packageJson?.scripts?.['verify:package'] !== 'node scripts/verify-package.cjs') {
    errors.push('Falta el comando verify:package esperado.');
  }
  for (const scriptName of ['dist:exe', 'dist:installer', 'dist:store']) {
    if (!packageJson?.scripts?.[scriptName]?.includes('npm run verify:package')) {
      errors.push(`${scriptName} debe ejecutar verify:package antes de empaquetar.`);
    }
  }
  if ('cert:trust' in (packageJson?.scripts ?? {})) {
    errors.push('No se permite un comando que instale certificados de confianza.');
  }
  if (!sameStringArray(packageJson?.build?.files, EXPECTED_PACKAGE_FILES)) {
    errors.push('La lista build.files debe ser la lista mínima aprobada.');
  }
  if (!sameStringArray(storeConfig?.files, EXPECTED_PACKAGE_FILES)) {
    errors.push('La lista Store files debe ser la lista mínima aprobada.');
  }
  const packageIconResource = packageJson?.build?.extraResources?.[0];
  const storeIconResource = storeConfig?.extraResources?.[0];
  if (packageJson?.build?.extraResources?.length !== 1
    || packageIconResource?.from !== 'assets/icon.ico'
    || packageIconResource?.to !== 'icon.ico'
    || storeConfig?.extraResources?.length !== 1
    || storeIconResource?.from !== 'assets/icon.ico'
    || storeIconResource?.to !== 'icon.ico') {
    errors.push('El único recurso de icono debe ser assets/icon.ico.');
  }
  if (packageJson?.build?.win?.icon !== 'assets/icon.ico'
    || packageJson?.build?.nsis?.installerIcon !== 'assets/icon.ico'
    || packageJson?.build?.nsis?.uninstallerIcon !== 'assets/icon.ico'
    || packageJson?.build?.nsis?.installerHeaderIcon !== 'assets/icon.ico'
    || storeConfig?.win?.icon !== 'assets/icon.ico') {
    errors.push('Todos los objetivos de Windows deben usar assets/icon.ico.');
  }
  if (packageJson?.build?.nsis?.include) {
    errors.push('El instalador no debe incluir macros que instalen certificados.');
  }
  if (packageJson?.build?.win?.signtoolOptions) {
    errors.push('La firma automática no debe estar activa en el build normal.');
  }

  return errors;
}

function validatePackagingSourceTexts(sourceTexts) {
  const errors = [];
  const legacyVersionPattern = new RegExp(['0', '1', '0'].join('\\.'));
  const legacyIconPattern = new RegExp(['build', 'icon[.]ico'].join('[\\\\/]'), 'i');
  for (const [name, text] of Object.entries(sourceTexts)) {
    if (legacyVersionPattern.test(text)) errors.push(`${name} conserva una versión heredada hardcodeada.`);
    if (legacyIconPattern.test(text)) errors.push(`${name} referencia la ruta antigua del icono.`);
    if (/ensure-arles-signing-cert|addstore\s+(?:Root|TrustedPublisher)/i.test(text)) {
      errors.push(`${name} intenta crear o instalar confianza de certificados.`);
    }
  }
  return errors;
}

module.exports = {
  EXPECTED_PACKAGE_FILES,
  validatePackageMetadata,
  validatePackagingSourceTexts,
};
