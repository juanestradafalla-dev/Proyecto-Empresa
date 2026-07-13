const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  exportarReporteMovimientos: (payload) => ipcRenderer.invoke('exportar-reporte-movimientos', payload),
});
