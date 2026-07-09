import type { ExportarReporteResult, ReporteMovimientosPayload } from './reporteMovimientosExcel';

export {};

declare global {
  interface Window {
    electronAPI?: {
      isElectron: boolean;
      exportarReporteMovimientos: (payload: ReporteMovimientosPayload) => Promise<ExportarReporteResult>;
    };
  }
}