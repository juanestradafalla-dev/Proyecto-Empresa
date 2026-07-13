import { createRequire } from 'node:module';
import { mkdtemp, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const ExcelJS = require('exceljs');
const { generarReporteMovimientosExcel } = require('../electron/reporteMovimientosExcel.cjs') as {
  generarReporteMovimientosExcel: (options: { filePath: string; payload: Record<string, unknown> }) => Promise<void>;
};

function reportPayload() {
  return {
    companyName: 'ARLES S.A.S.',
    title: 'Reporte de movimientos',
    moduleName: 'TALLER',
    periodLabel: 'Histórico completo',
    exportDate: '13 de julio de 2026',
    generatedBy: 'usuario@example.com',
    coverageLabel: 'Historial completo',
    summary: {
      total_categorias: 0,
      total_movimientos: 0,
      total_entradas: 0,
      total_salidas: 0,
      cantidad_entradas: 0,
      cantidad_salidas: 0,
    },
    categorias: [],
    movimientosGenerales: [],
    entradasGenerales: [],
    salidasGenerales: [],
  };
}

describe('exportación Excel con dependencias corregidas', () => {
  it('resuelve para ExcelJS una versión corregida de uuid', () => {
    const excelPackageDirectory = dirname(require.resolve('exceljs/package.json'));
    const uuidPackagePath = require.resolve('uuid/package.json', { paths: [excelPackageDirectory] });
    const uuidPackage = require(uuidPackagePath) as { version: string };

    expect(uuidPackage.version).toBe('11.1.1');
  });

  it('genera un archivo XLSX que puede abrirse nuevamente', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'arles-excel-'));
    const filePath = join(directory, 'reporte.xlsx');

    try {
      await generarReporteMovimientosExcel({ filePath, payload: reportPayload() });

      expect((await stat(filePath)).size).toBeGreaterThan(0);

      const workbook = new ExcelJS.Workbook();
      await workbook.xlsx.readFile(filePath);
      expect(workbook.worksheets.map((sheet: { name: string }) => sheet.name)).toEqual([
        'Resumen',
        'Movimientos generales',
        'Entradas',
        'Salidas',
        'Consolidado por producto',
      ]);
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  });
});
