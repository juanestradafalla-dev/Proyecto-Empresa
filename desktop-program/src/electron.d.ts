import type { ExportarReporteResult, ReporteMovimientosPayload } from './reporteMovimientosExcel';

export {};

declare global {
  interface Window {
    electronAPI?: {
      exportarReporteMovimientos: (payload: ReporteMovimientosPayload) => Promise<ExportarReporteResult>;
    };
  }
}
