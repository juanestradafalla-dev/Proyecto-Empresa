const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  exportarReporteMovimientos: (payload) => ipcRenderer.invoke('exportar-reporte-movimientos', payload),
});