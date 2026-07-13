const { app, BrowserWindow, Menu, shell, dialog, ipcMain, session } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { generarReporteMovimientosExcel } = require('./reporteMovimientosExcel.cjs');
const {
  isAllowedExternalUrl,
  isAllowedNavigationUrl,
  isAuthorizedIpcEvent,
  isLoopbackDevServerUrl,
  createSecureWebPreferences,
  validateReportPayload,
} = require('./security.cjs');

const isDev = !app.isPackaged;
const distIndex = path.join(__dirname, '..', 'dist', 'index.html');
const startupErrorFile = path.join(__dirname, 'startup-error.html');
const requestedDevServerUrl = process.env.VITE_DEV_SERVER_URL || 'http://127.0.0.1:5174';
const useDevServer = isDev && process.env.ELECTRON_DEV === '1';
const devServerUrl = useDevServer && isLoopbackDevServerUrl(requestedDevServerUrl)
  ? requestedDevServerUrl
  : '';
let mainWindow = null;

function getAppIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icon.ico')
    : path.join(__dirname, '..', 'assets', 'icon.ico');
}

function resolvePortableRoot() {
  const fromEnv = process.env.PORTABLE_EXECUTABLE_DIR;
  if (fromEnv && fs.existsSync(fromEnv)) return fromEnv;
  return null;
}

function configurePortablePaths() {
  const portableRoot = resolvePortableRoot();
  if (!portableRoot) return;

  const dataDir = path.join(portableRoot, 'datos-portable');
  const cacheDir = path.join(dataDir, 'cache');

  for (const dir of [dataDir, cacheDir]) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  }

  app.setPath('userData', dataDir);
  app.setPath('sessionData', dataDir);
  app.setPath('cache', cacheDir);
}

configurePortablePaths();

function navigationOptions() {
  return {
    devServerUrl,
    allowedFileUrls: [
      pathToFileURL(distIndex).href,
      pathToFileURL(startupErrorFile).href,
    ],
  };
}

async function openExternalHttpUrl(url) {
  if (!isAllowedExternalUrl(url)) return;
  try {
    await shell.openExternal(url);
  } catch (error) {
    console.error('No se pudo abrir el enlace externo autorizado:', error);
  }
}

function protectWindowNavigation(window) {
  const options = navigationOptions();

  window.webContents.setWindowOpenHandler(({ url }) => {
    if (isAllowedExternalUrl(url)) void openExternalHttpUrl(url);
    return { action: 'deny' };
  });

  const blockUnauthorizedNavigation = (event, url) => {
    if (isAllowedNavigationUrl(url, options)) return;
    event.preventDefault();
    if (isAllowedExternalUrl(url)) void openExternalHttpUrl(url);
  };

  window.webContents.on('will-navigate', blockUnauthorizedNavigation);
  window.webContents.on('will-redirect', blockUnauthorizedNavigation);
  window.webContents.on('will-attach-webview', (event) => event.preventDefault());
}

async function showStartupError(window, message) {
  console.error(message);
  if (fs.existsSync(startupErrorFile)) {
    try {
      await window.loadFile(startupErrorFile);
      return;
    } catch (error) {
      console.error('No se pudo cargar la pantalla local de recuperación:', error);
    }
  }
  dialog.showErrorBox('No se pudo iniciar Gestión de Almacén', message);
}

async function loadApplication(window) {
  if (useDevServer) {
    if (!devServerUrl) {
      await showStartupError(window, 'El servidor de desarrollo configurado no es una dirección local autorizada.');
      return;
    }
    try {
      await window.loadURL(devServerUrl);
    } catch {
      await showStartupError(window, 'No se pudo conectar con el servidor local de desarrollo autorizado.');
    }
    return;
  }

  if (!fs.existsSync(distIndex)) {
    await showStartupError(window, 'No se encontró dist/index.html. Ejecuta npm run build antes de iniciar Electron.');
    return;
  }

  try {
    await window.loadFile(distIndex);
  } catch {
    await showStartupError(window, 'El archivo dist/index.html existe, pero no pudo cargarse correctamente.');
  }
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 1180,
    minHeight: 720,
    title: 'Arles S.A.S. Gestión de Almacén',
    icon: getAppIconPath(),
    backgroundColor: '#dbe8db',
    autoHideMenuBar: true,
    webPreferences: createSecureWebPreferences(path.join(__dirname, 'preload.cjs'), isDev),
  });

  mainWindow = window;
  protectWindowNavigation(window);
  window.on('closed', () => {
    if (mainWindow === window) mainWindow = null;
  });
  window.webContents.on('render-process-gone', () => {
    void showStartupError(window, 'La interfaz se detuvo inesperadamente y se abrió la pantalla local de recuperación.');
  });
  void loadApplication(window).catch((error) => {
    console.error('Fallo inesperado al cargar la ventana principal:', error);
    void showStartupError(window, 'No se pudo completar la carga segura de la ventana principal.');
  });
  return window;
}

function safeExportError(message) {
  return { canceled: true, error: message };
}

ipcMain.handle('exportar-reporte-movimientos', async (event, payload) => {
  if (!isAuthorizedIpcEvent(event, mainWindow, navigationOptions())) {
    return safeExportError('La solicitud de exportación no provino de la ventana autorizada.');
  }

  const validated = validateReportPayload(payload);
  if (!validated.ok) return safeExportError(validated.message);

  try {
    const result = await dialog.showSaveDialog(mainWindow, {
      title: 'Guardar reporte de movimientos',
      defaultPath: validated.value.suggestedFileName,
      filters: [{ name: 'Excel', extensions: ['xlsx'] }],
    });

    if (result.canceled || !result.filePath) return { canceled: true };

    const filePath = result.filePath.toLowerCase().endsWith('.xlsx')
      ? result.filePath
      : `${result.filePath}.xlsx`;

    await generarReporteMovimientosExcel({ filePath, payload: validated.value });
    return { canceled: false, filePath };
  } catch (error) {
    console.error('No se pudo completar la exportación del reporte:', error);
    return safeExportError('No se pudo crear el archivo Excel. Revisa la ubicación seleccionada e intenta nuevamente.');
  }
});

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));
  session.defaultSession.setPermissionCheckHandler(() => false);
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
}).catch((error) => {
  console.error('Electron no pudo completar el arranque:', error);
  dialog.showErrorBox('No se pudo iniciar Gestión de Almacén', 'Ocurrió un error durante el arranque seguro de Electron.');
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
