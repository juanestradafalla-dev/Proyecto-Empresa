const path = require('node:path');

const MAX_REPORT_ROWS = 20_000;
const MAX_REPORT_CATEGORIES = 50;
const MAX_TEXT_LENGTH = 2_000;
const MAX_FILENAME_LENGTH = 120;
const SAFE_REPORT_ERROR = 'El reporte contiene datos inválidos o excede el límite permitido.';

function createSecureWebPreferences(preloadPath, isDevelopment) {
  return {
    nodeIntegration: false,
    contextIsolation: true,
    sandbox: true,
    webSecurity: true,
    allowRunningInsecureContent: false,
    webviewTag: false,
    devTools: Boolean(isDevelopment),
    preload: preloadPath,
  };
}

function parseUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    return new URL(value);
  } catch {
    return null;
  }
}

function isAllowedExternalUrl(value) {
  const parsed = parseUrl(value);
  return Boolean(
    parsed
    && (parsed.protocol === 'http:' || parsed.protocol === 'https:')
    && !parsed.username
    && !parsed.password,
  );
}

function isLoopbackDevServerUrl(value) {
  const parsed = parseUrl(value);
  if (!parsed || parsed.protocol !== 'http:') return false;
  return parsed.hostname === '127.0.0.1' || parsed.hostname === 'localhost';
}

function comparableFileUrl(value) {
  const parsed = parseUrl(value);
  if (!parsed || parsed.protocol !== 'file:') return '';
  parsed.hash = '';
  parsed.search = '';
  return parsed.href;
}

function isAllowedNavigationUrl(value, { devServerUrl = '', allowedFileUrls = [] } = {}) {
  const parsed = parseUrl(value);
  if (!parsed) return false;

  if (parsed.protocol === 'file:') {
    const candidate = comparableFileUrl(parsed.href);
    return allowedFileUrls.some((allowed) => comparableFileUrl(allowed) === candidate);
  }

  if (devServerUrl && isLoopbackDevServerUrl(devServerUrl)) {
    const allowedDevServer = parseUrl(devServerUrl);
    return parsed.protocol === 'http:' && parsed.origin === allowedDevServer.origin;
  }

  return false;
}

function isAuthorizedIpcEvent(event, authorizedWindow, navigationOptions) {
  if (!authorizedWindow || authorizedWindow.isDestroyed?.()) return false;
  if (!event || event.sender !== authorizedWindow.webContents) return false;
  const senderUrl = event.senderFrame?.url || event.sender?.getURL?.() || '';
  return isAllowedNavigationUrl(senderUrl, navigationOptions);
}

function assertPlainObject(value, field) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${field} debe ser un objeto.`);
  }
  return value;
}

function safeText(value, field, { allowEmpty = true, maxLength = MAX_TEXT_LENGTH } = {}) {
  if (typeof value !== 'string') throw new TypeError(`${field} debe ser texto.`);
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(value)) {
    throw new TypeError(`${field} contiene caracteres no permitidos.`);
  }
  const normalized = value.trim();
  if ((!allowEmpty && !normalized) || normalized.length > maxLength) {
    throw new RangeError(`${field} tiene una longitud inválida.`);
  }
  return /^[=+\-@]/.test(normalized) ? `'${normalized}` : normalized;
}

function finiteNumber(value, field) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new TypeError(`${field} debe ser un número finito.`);
  }
  return value;
}

function safeFilename(value) {
  if (typeof value !== 'string' || !value || value.length > MAX_FILENAME_LENGTH) {
    throw new TypeError('El nombre sugerido no es válido.');
  }
  if (
    path.basename(value) !== value
    || !/^[^<>:"/\\|?*\u0000-\u001F]+\.xlsx$/i.test(value)
    || value === '.xlsx'
    || /^[.\s=+\-@]/.test(value)
    || /[.\s]$/.test(value)
    || /^(?:con|prn|aux|nul|com\d|lpt\d)(?:\.|$)/i.test(value)
  ) {
    throw new TypeError('El nombre sugerido no es válido.');
  }
  return value;
}

const SUMMARY_NUMBER_FIELDS = [
  'total_movimientos',
  'total_entradas',
  'total_salidas',
  'cantidad_entradas',
  'cantidad_salidas',
];

function validateSummary(value, field, includeCategoryCount = false) {
  const source = assertPlainObject(value, field);
  const output = {};
  SUMMARY_NUMBER_FIELDS.forEach((key) => {
    output[key] = finiteNumber(source[key], `${field}.${key}`);
  });
  if (includeCategoryCount) {
    output.total_categorias = finiteNumber(source.total_categorias, `${field}.total_categorias`);
  }
  return output;
}

const MOVEMENT_TEXT_FIELDS = [
  'fecha',
  'tipo_movimiento',
  'codigo',
  'nombre_producto',
  'submodulo',
  'subcategoria',
  'unidad',
  'responsable',
  'observacion',
  'documento_soporte',
  'labor',
  'zona',
  'horometro',
];
const MOVEMENT_NUMBER_FIELDS = [
  'cantidad_entrada',
  'cantidad_salida',
  'saldo_anterior',
  'saldo_nuevo',
];

function validateMovementRow(value, field) {
  const source = assertPlainObject(value, field);
  const output = {};
  MOVEMENT_TEXT_FIELDS.forEach((key) => {
    output[key] = safeText(source[key], `${field}.${key}`);
  });
  MOVEMENT_NUMBER_FIELDS.forEach((key) => {
    output[key] = finiteNumber(source[key], `${field}.${key}`);
  });
  return output;
}

const CONSOLIDATED_TEXT_FIELDS = [
  'codigo',
  'nombre_producto',
  'submodulo',
  'subcategoria',
  'unidad',
];
const CONSOLIDATED_NUMBER_FIELDS = ['total_entradas', 'total_salidas', 'saldo_neto'];

function validateConsolidatedRow(value, field) {
  const source = assertPlainObject(value, field);
  const output = {};
  CONSOLIDATED_TEXT_FIELDS.forEach((key) => {
    output[key] = safeText(source[key], `${field}.${key}`);
  });
  CONSOLIDATED_NUMBER_FIELDS.forEach((key) => {
    output[key] = finiteNumber(source[key], `${field}.${key}`);
  });
  return output;
}

function validateRowArray(value, field, validator) {
  if (!Array.isArray(value) || value.length > MAX_REPORT_ROWS) {
    throw new RangeError(`${field} excede el límite de filas.`);
  }
  return value.map((row, index) => validator(row, `${field}[${index}]`));
}

function validateCategory(value, index) {
  const field = `categorias[${index}]`;
  const source = assertPlainObject(value, field);
  const sheetName = safeText(source.sheetName, `${field}.sheetName`, { allowEmpty: false, maxLength: 31 });
  if (/[\[\]:*?/\\]/.test(sheetName)) throw new TypeError('El nombre de hoja no es válido.');

  return {
    sheetKey: safeText(source.sheetKey, `${field}.sheetKey`, { allowEmpty: false, maxLength: 200 }),
    sheetName,
    categoryLabel: safeText(source.categoryLabel, `${field}.categoryLabel`, { allowEmpty: false }),
    moduleName: safeText(source.moduleName, `${field}.moduleName`, { allowEmpty: false }),
    ...(typeof source.submodulo === 'string'
      ? { submodulo: safeText(source.submodulo, `${field}.submodulo`) }
      : {}),
    movimientos: validateRowArray(source.movimientos, `${field}.movimientos`, validateMovementRow),
    entradas: validateRowArray(source.entradas, `${field}.entradas`, validateMovementRow),
    salidas: validateRowArray(source.salidas, `${field}.salidas`, validateMovementRow),
    consolidated: validateRowArray(source.consolidated, `${field}.consolidated`, validateConsolidatedRow),
    summary: validateSummary(source.summary, `${field}.summary`),
  };
}

function validateReportPayload(payload) {
  try {
    const source = assertPlainObject(payload, 'payload');
    if (!Array.isArray(source.categorias) || source.categorias.length > MAX_REPORT_CATEGORIES) {
      throw new RangeError('La cantidad de categorías no es válida.');
    }
    for (const rowField of ['movimientos', 'entradas', 'salidas', 'consolidated']) {
      const totalRows = source.categorias.reduce((total, category) => {
        const rows = assertPlainObject(category, 'categoria')[rowField];
        if (!Array.isArray(rows)) throw new TypeError('La categoría contiene filas inválidas.');
        return total + rows.length;
      }, 0);
      if (totalRows > MAX_REPORT_ROWS) throw new RangeError('El reporte excede el límite total de filas.');
    }

    const value = {
      companyName: safeText(source.companyName, 'companyName', { allowEmpty: false }),
      title: safeText(source.title, 'title', { allowEmpty: false }),
      moduleName: safeText(source.moduleName, 'moduleName', { allowEmpty: false }),
      suggestedFileName: safeFilename(source.suggestedFileName),
      periodLabel: safeText(source.periodLabel, 'periodLabel', { allowEmpty: false }),
      exportDate: safeText(source.exportDate, 'exportDate', { allowEmpty: false }),
      generatedBy: safeText(source.generatedBy, 'generatedBy', { allowEmpty: false }),
      coverageLabel: safeText(source.coverageLabel, 'coverageLabel', { allowEmpty: false }),
      summary: validateSummary(source.summary, 'summary', true),
      categorias: source.categorias.map(validateCategory),
      movimientosGenerales: validateRowArray(source.movimientosGenerales, 'movimientosGenerales', validateMovementRow),
      entradasGenerales: validateRowArray(source.entradasGenerales, 'entradasGenerales', validateMovementRow),
      salidasGenerales: validateRowArray(source.salidasGenerales, 'salidasGenerales', validateMovementRow),
    };

    return { ok: true, value };
  } catch {
    return { ok: false, message: SAFE_REPORT_ERROR };
  }
}

module.exports = {
  MAX_REPORT_ROWS,
  SAFE_REPORT_ERROR,
  createSecureWebPreferences,
  isAllowedExternalUrl,
  isAllowedNavigationUrl,
  isAuthorizedIpcEvent,
  isLoopbackDevServerUrl,
  validateReportPayload,
};
