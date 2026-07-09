const { app, BrowserWindow, Menu, shell, dialog, ipcMain } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const { generarReporteMovimientosExcel } = require('./reporteMovimientosExcel.cjs');

const isDev = !app.isPackaged;
const distIndex = path.join(__dirname, '..', 'dist', 'index.html');
const useDevServer = isDev && process.env.ELECTRON_DEV === '1';

function getAppIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icon.ico')
    : path.join(__dirname, '..', 'build', 'icon.ico');
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
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  }

  app.setPath('userData', dataDir);
  app.setPath('sessionData', dataDir);
  app.setPath('cache', cacheDir);
}

configurePortablePaths();

function createWindow() {
  const window = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 1180,
    minHeight: 720,
    title: 'Arles S.A.S. Gestion de Almacen',
    icon: getAppIconPath(),
    backgroundColor: '#dbe8db',
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.cjs'),
    },
  });

  window.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  if (useDevServer) {
    window.loadURL('http://127.0.0.1:5174');
  } else if (fs.existsSync(distIndex)) {
    window.loadFile(distIndex);
  } else {
    window.loadURL('http://127.0.0.1:5174');
  }
}

ipcMain.handle('exportar-reporte-movimientos', async (_event, payload) => {
  const result = await dialog.showSaveDialog({
    title: 'Guardar reporte de movimientos',
    defaultPath: payload?.suggestedFileName || 'Reporte_Movimientos_ARLES.xlsx',
    filters: [{ name: 'Excel', extensions: ['xlsx'] }],
  });

  if (result.canceled || !result.filePath) {
    return { canceled: true };
  }

  let filePath = result.filePath;
  if (!filePath.toLowerCase().endsWith('.xlsx')) {
    filePath = `${filePath}.xlsx`;
  }

  await generarReporteMovimientosExcel({
    filePath,
    payload: payload ?? {},
  });

  return { canceled: false, filePath };
});

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
