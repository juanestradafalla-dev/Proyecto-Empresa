import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const {
  EXPECTED_PACKAGE_FILES,
  validatePackageMetadata,
  validatePackagingSourceTexts,
} = require('../scripts/package-verification.cjs') as {
  EXPECTED_PACKAGE_FILES: string[];
  validatePackageMetadata: (packageJson: any, packageLock: any, storeConfig: any) => string[];
  validatePackagingSourceTexts: (texts: Record<string, string>) => string[];
};

function validMetadata() {
  const dependencies = { firebase: '^12.7.0' };
  const devDependencies = { electron: '^42.3.3' };
  const packageJson = {
    name: 'gestion-almacen-panel',
    version: '0.1.2',
    dependencies,
    devDependencies,
    scripts: {
      'verify:package': 'node scripts/verify-package.cjs',
      'dist:exe': 'npm run build && npm run verify:package && electron-builder',
      'dist:installer': 'npm run build && npm run verify:package && electron-builder',
      'dist:store': 'npm run build && npm run verify:package && electron-builder',
    },
    build: {
      files: EXPECTED_PACKAGE_FILES,
      extraResources: [{ from: 'assets/icon.ico', to: 'icon.ico' }],
      win: { icon: 'assets/icon.ico' },
      nsis: {
        installerIcon: 'assets/icon.ico',
        uninstallerIcon: 'assets/icon.ico',
        installerHeaderIcon: 'assets/icon.ico',
      },
    },
  };
  const packageLock = {
    name: packageJson.name,
    version: packageJson.version,
    packages: {
      '': {
        name: packageJson.name,
        version: packageJson.version,
        dependencies,
        devDependencies,
      },
    },
  };
  const storeConfig = {
    files: EXPECTED_PACKAGE_FILES,
    extraResources: [{ from: 'assets/icon.ico', to: 'icon.ico' }],
    win: { icon: 'assets/icon.ico' },
  };
  return { packageJson, packageLock, storeConfig };
}

describe('versión y configuración de empaquetado', () => {
  it('acepta metadata sincronizada y un único icono', () => {
    const { packageJson, packageLock, storeConfig } = validMetadata();
    expect(validatePackageMetadata(packageJson, packageLock, storeConfig)).toEqual([]);
  });

  it('detecta versiones desincronizadas y firma automática', () => {
    const { packageJson, packageLock, storeConfig } = validMetadata();
    packageLock.version = '0.1.1';
    (packageJson.build.win as { icon: string; signtoolOptions?: { publisherName: string } }).signtoolOptions = { publisherName: 'Arles SAS' };

    const errors = validatePackageMetadata(packageJson, packageLock, storeConfig);

    expect(errors.some((error) => error.includes('misma versión'))).toBe(true);
    expect(errors.some((error) => error.includes('firma automática'))).toBe(true);
  });

  it('rechaza versiones heredadas y rutas antiguas de icono en scripts', () => {
    const legacyVersion = ['0', '1', '0'].join('.');
    const legacyIcon = ['build', 'icon.ico'].join('\\');
    const errors = validatePackagingSourceTexts({
      'script.ps1': `release\\Gestion Almacen Arles ${legacyVersion}.exe; ${legacyIcon}`,
    });

    expect(errors).toHaveLength(2);
  });
});
