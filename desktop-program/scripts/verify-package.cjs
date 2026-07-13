const fs = require('node:fs');
const path = require('node:path');
const {
  validatePackageMetadata,
  validatePackagingSourceTexts,
} = require('./package-verification.cjs');

const projectRoot = path.resolve(__dirname, '..');
const repositoryRoot = path.resolve(projectRoot, '..');

function readJson(relativePath) {
  return JSON.parse(fs.readFileSync(path.join(projectRoot, relativePath), 'utf8'));
}

function collectFiles(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    return entry.isDirectory() ? collectFiles(fullPath) : [fullPath];
  });
}

const packageJson = readJson('package.json');
const packageLock = readJson('package-lock.json');
const storeConfig = readJson('electron-builder.store.json');
const errors = validatePackageMetadata(packageJson, packageLock, storeConfig);

const requiredFiles = [
  'assets/icon.ico',
  'dist/index.html',
  'electron/main.cjs',
  'electron/preload.cjs',
  'electron/reporteMovimientosExcel.cjs',
  'electron/security.cjs',
  'electron/startup-error.html',
  'package-lock.json',
];
requiredFiles.forEach((relativePath) => {
  if (!fs.existsSync(path.join(projectRoot, relativePath))) {
    errors.push(`Falta el archivo requerido ${relativePath}.`);
  }
});

const brandingLogo = path.join(repositoryRoot, 'branding', 'logo_gestion_arles.png');
if (!fs.existsSync(brandingLogo)) {
  errors.push('Falta branding/logo_gestion_arles.png.');
}

const iconPath = path.join(projectRoot, 'assets', 'icon.ico');
if (fs.existsSync(iconPath)) {
  const iconHeader = fs.readFileSync(iconPath).subarray(0, 4);
  if (iconHeader.length !== 4 || !iconHeader.equals(Buffer.from([0, 0, 1, 0]))) {
    errors.push('assets/icon.ico no tiene una cabecera ICO válida.');
  }
}

const sourceTextFiles = [
  'package.json',
  'electron-builder.store.json',
  'electron/main.cjs',
  'scripts/build-trusted-release.ps1',
  'scripts/create-arles-icon.ps1',
  'scripts/sign-release.ps1',
];
const sourceTexts = Object.fromEntries(sourceTextFiles.map((relativePath) => [
  relativePath,
  fs.readFileSync(path.join(projectRoot, relativePath), 'utf8'),
]));
errors.push(...validatePackagingSourceTexts(sourceTexts));

const forbiddenExtensions = new Set(['.cer', '.crt', '.key', '.p12', '.pem', '.pfx', '.pvk']);
const packagedSourceRoots = ['assets', 'electron', 'dist'].map((entry) => path.join(projectRoot, entry));
packagedSourceRoots.flatMap(collectFiles).forEach((filePath) => {
  const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/');
  const insidePackagedRoot = packagedSourceRoots.some((root) => (
    filePath === root || filePath.startsWith(`${root}${path.sep}`)
  ));
  if (insidePackagedRoot && forbiddenExtensions.has(path.extname(filePath).toLowerCase())) {
    errors.push(`El paquete incluiría material de certificado: ${relativePath}.`);
  }
  if (insidePackagedRoot && /(^|\/)\.env(?:\.|$)/i.test(relativePath)) {
    errors.push(`El paquete incluiría un archivo de entorno: ${relativePath}.`);
  }
});

if (errors.length > 0) {
  console.error('La verificación de empaquetado encontró problemas:');
  errors.forEach((error) => console.error(`- ${error}`));
  process.exitCode = 1;
} else {
  console.log(`Empaquetado verificado para ${packageJson.name} ${packageJson.version}.`);
  console.log('Icono, archivos mínimos, versión, firma opcional y exclusiones: correctos.');
}
