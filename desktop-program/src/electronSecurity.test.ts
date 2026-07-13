import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const {
  MAX_REPORT_ROWS,
  createSecureWebPreferences,
  isAllowedExternalUrl,
  isAllowedNavigationUrl,
  isAuthorizedIpcEvent,
  validateReportPayload,
} = require('../electron/security.cjs') as {
  MAX_REPORT_ROWS: number;
  createSecureWebPreferences: (preloadPath: string, isDevelopment: boolean) => Record<string, unknown>;
  isAllowedExternalUrl: (url: string) => boolean;
  isAllowedNavigationUrl: (url: string, options: { devServerUrl?: string; allowedFileUrls?: string[] }) => boolean;
  isAuthorizedIpcEvent: (event: unknown, window: unknown, options: unknown) => boolean;
  validateReportPayload: (payload: unknown) => { ok: boolean; value?: unknown; message?: string };
};

function movementRow(overrides: Record<string, unknown> = {}) {
  return {
    fecha: '2026-07-13 10:00',
    tipo_movimiento: 'Salida',
    codigo: 'COD-1',
    nombre_producto: 'Producto',
    submodulo: 'Taller',
    subcategoria: 'General',
    cantidad_entrada: 0,
    cantidad_salida: 1,
    unidad: 'Unidad',
    saldo_anterior: 2,
    saldo_nuevo: 1,
    responsable: 'Usuario',
    observacion: '',
    documento_soporte: '',
    labor: '',
    zona: '',
    horometro: '',
    ...overrides,
  };
}

function summary(includeCategories = false) {
  return {
    total_movimientos: 1,
    total_entradas: 0,
    total_salidas: 1,
    cantidad_entradas: 0,
    cantidad_salidas: 1,
    ...(includeCategories ? { total_categorias: 1 } : {}),
  };
}

function validPayload() {
  const row = movementRow();
  return {
    companyName: 'ARLES S.A.S.',
    title: 'Reporte de movimientos',
    moduleName: 'TALLER',
    suggestedFileName: 'Reporte_Movimientos_ARLES.xlsx',
    periodLabel: 'Histórico completo',
    exportDate: '13 de julio de 2026',
    generatedBy: 'usuario@example.com',
    coverageLabel: 'Historial completo',
    summary: summary(true),
    categorias: [{
      sheetKey: 'TALLER',
      sheetName: 'Taller',
      categoryLabel: 'Taller',
      moduleName: 'TALLER',
      movimientos: [row],
      entradas: [],
      salidas: [row],
      consolidated: [{
        codigo: 'COD-1',
        nombre_producto: 'Producto',
        submodulo: 'Taller',
        subcategoria: 'General',
        total_entradas: 0,
        total_salidas: 1,
        saldo_neto: -1,
        unidad: 'Unidad',
      }],
      summary: summary(),
    }],
    movimientosGenerales: [row],
    entradasGenerales: [],
    salidasGenerales: [row],
  };
}

describe('seguridad de navegación de Electron', () => {
  const options = {
    devServerUrl: 'http://127.0.0.1:5174',
    allowedFileUrls: ['file:///C:/app/dist/index.html'],
  };

  it('activa aislamiento, sandbox y seguridad web, y limita DevTools a desarrollo', () => {
    expect(createSecureWebPreferences('C:/app/preload.cjs', false)).toMatchObject({
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      webviewTag: false,
      devTools: false,
    });
    expect(createSecureWebPreferences('C:/app/preload.cjs', true).devTools).toBe(true);
  });

  it('permite solo el archivo local y el servidor de desarrollo autorizados', () => {
    expect(isAllowedNavigationUrl('file:///C:/app/dist/index.html#inicio', options)).toBe(true);
    expect(isAllowedNavigationUrl('http://127.0.0.1:5174/src/main.tsx', options)).toBe(true);
    expect(isAllowedNavigationUrl('file:///C:/Windows/System32/calc.exe', options)).toBe(false);
    expect(isAllowedNavigationUrl('http://localhost:3000', options)).toBe(false);
    expect(isAllowedNavigationUrl('https://example.com', options)).toBe(false);
  });

  it('abre externamente solo HTTP y HTTPS sin credenciales embebidas', () => {
    expect(isAllowedExternalUrl('https://example.com/evidencia')).toBe(true);
    expect(isAllowedExternalUrl('http://example.com')).toBe(true);
    expect(isAllowedExternalUrl('file:///C:/secreto.txt')).toBe(false);
    expect(isAllowedExternalUrl('javascript:alert(1)')).toBe(false);
    expect(isAllowedExternalUrl('data:text/html,malicioso')).toBe(false);
    expect(isAllowedExternalUrl('https://user:pass@example.com')).toBe(false);
  });

  it('autoriza IPC únicamente desde la ventana y URL permitidas', () => {
    const webContents = { getURL: () => 'file:///C:/app/dist/index.html' };
    const window = { webContents, isDestroyed: () => false };
    expect(isAuthorizedIpcEvent({ sender: webContents, senderFrame: { url: webContents.getURL() } }, window, options)).toBe(true);
    expect(isAuthorizedIpcEvent({ sender: {}, senderFrame: { url: webContents.getURL() } }, window, options)).toBe(false);
    expect(isAuthorizedIpcEvent({ sender: webContents, senderFrame: { url: 'https://example.com' } }, window, options)).toBe(false);
  });
});

describe('validación del payload IPC del reporte', () => {
  it('acepta una estructura válida y neutraliza fórmulas en textos', () => {
    const payload = validPayload();
    payload.movimientosGenerales[0] = movementRow({ observacion: '=HYPERLINK("malicioso")' });

    const result = validateReportPayload(payload);

    expect(result.ok).toBe(true);
    expect((result.value as any).movimientosGenerales[0].observacion).toBe("'=HYPERLINK(\"malicioso\")");
  });

  it('rechaza nombres, números y estructuras inválidas con un mensaje seguro', () => {
    const invalidName = validateReportPayload({ ...validPayload(), suggestedFileName: '../reporte.xlsx' });
    const invalidNumberPayload = validPayload();
    invalidNumberPayload.movimientosGenerales[0] = movementRow({ cantidad_salida: Number.POSITIVE_INFINITY });
    const invalidNumber = validateReportPayload(invalidNumberPayload);

    expect(invalidName.ok).toBe(false);
    expect(invalidNumber.ok).toBe(false);
    expect(invalidName.message).not.toContain('..');
  });

  it('rechaza reportes que superan el límite máximo de filas', () => {
    const payload = validPayload();
    payload.movimientosGenerales = Array.from({ length: MAX_REPORT_ROWS + 1 }, () => movementRow());

    expect(validateReportPayload(payload).ok).toBe(false);
  });
});
