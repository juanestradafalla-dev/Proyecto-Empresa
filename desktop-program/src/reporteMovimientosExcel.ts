import {
  esEntradaVistaTaller,
  esSalidaVistaTaller,
  movimientoPerteneceSubmoduloTaller,
  normalizarSubmoduloTaller,
  TALLER_SUBMODULOS,
} from './tallerCanonicos';
import { modules } from './theme';

export const REPORTE_MOVIMIENTOS_FILENAME = 'Reporte_Movimientos_ARLES.xlsx';

export const MODULOS_ORDEN = modules;

export type MovimientoParaReporte = {
  id: string;
  modulo: string;
  tipo: string;
  codigo: string;
  descripcion: string;
  referencia: string;
  cantidad: number;
  unidad: string;
  fecha: string;
  solicitante: string;
  cargo: string;
  usuario: string;
  observaciones: string;
  fotoUrl: string;
  submodulo?: string;
  submoduloOrigen?: string;
  maquinaria?: string;
  labor?: string;
  frente?: string;
  zona?: string;
  horometro?: string;
  responsableEntrega?: string;
};

export type FilaMovimientoExcel = {
  fecha: string;
  tipo_movimiento: string;
  codigo: string;
  nombre_producto: string;
  submodulo: string;
  subcategoria: string;
  cantidad_entrada: number;
  cantidad_salida: number;
  unidad: string;
  saldo_anterior: number;
  saldo_nuevo: number;
  responsable: string;
  observacion: string;
  documento_soporte: string;
  labor: string;
  zona: string;
  horometro: string;
};

export type FilaConsolidadoExcel = {
  codigo: string;
  nombre_producto: string;
  submodulo: string;
  subcategoria: string;
  total_entradas: number;
  total_salidas: number;
  saldo_neto: number;
  unidad: string;
};

export type ResumenCategoria = {
  total_movimientos: number;
  total_entradas: number;
  total_salidas: number;
  cantidad_entradas: number;
  cantidad_salidas: number;
};

export type HojaCategoriaReporte = {
  sheetKey: string;
  sheetName: string;
  categoryLabel: string;
  moduleName: string;
  submodulo?: string;
  movimientos: FilaMovimientoExcel[];
  entradas: FilaMovimientoExcel[];
  salidas: FilaMovimientoExcel[];
  consolidated: FilaConsolidadoExcel[];
  summary: ResumenCategoria;
};

export type ResumenReporte = ResumenCategoria & {
  total_categorias: number;
};

export type ReporteMovimientosPayload = {
  companyName: string;
  title: string;
  moduleName: string;
  suggestedFileName: string;
  periodLabel: string;
  exportDate: string;
  generatedBy: string;
  coverageLabel: string;
  summary: ResumenReporte;
  categorias: HojaCategoriaReporte[];
  movimientosGenerales: FilaMovimientoExcel[];
  entradasGenerales: FilaMovimientoExcel[];
  salidasGenerales: FilaMovimientoExcel[];
};

export type ExportarReporteResult = {
  canceled: boolean;
  filePath?: string;
};

type UsuarioLookup = Record<string, { nombre: string; cargo: string; email: string }>;

type CategoriaMovimiento = {
  sheetKey: string;
  sheetName: string;
  categoryLabel: string;
  moduleName: string;
  submodulo?: string;
  order: number;
};

function normalizar(texto: string) {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

function normalizarModulo(texto: string) {
  return normalizar(texto).replace(/\s+/g, '');
}

function coincideModulo(valor: string, modulo: string) {
  const a = normalizarModulo(valor);
  const b = normalizarModulo(modulo);
  if (modulo === 'TALLER') return a === 'taller' || a.includes('herramienta');
  if (b === 'agroquimicos') return a === 'agroquimicos' || a.includes('agroquimico');
  if (b === 'lubricantestaller') return a === 'lubricantestaller' || (a.includes('lubricante') && a.includes('taller'));
  if (b === 'aseo') return a === 'aseo';
  return a === b || a.includes(b);
}

function sanitizarNombreHoja(nombre: string) {
  return nombre.replace(/[\\/*?:[\]]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 31) || 'Categoria';
}

function ordenCategoria(modulo: string, submodulo?: string) {
  const moduloIndex = MODULOS_ORDEN.findIndex((entry) => entry === modulo);
  const base = moduloIndex >= 0 ? moduloIndex : MODULOS_ORDEN.length + 1;
  if (modulo !== 'TALLER' || !submodulo) return base * 100;
  const subIndex = TALLER_SUBMODULOS.findIndex((entry) => entry === submodulo);
  return base * 100 + (subIndex >= 0 ? subIndex : 99);
}

function esMovimientoSistema(movimiento: MovimientoParaReporte) {
  const modulo = normalizar(movimiento.modulo);
  return modulo === 'sistema' || modulo.includes('sistema');
}

function resolverCategoria(movimiento: MovimientoParaReporte): CategoriaMovimiento {
  const moduloDetectado = MODULOS_ORDEN.find((modulo) => coincideModulo(movimiento.modulo, modulo));
  const modulo = moduloDetectado || movimiento.modulo || 'Sin módulo';

  if (modulo === 'TALLER') {
    const submodulo = normalizarSubmoduloTaller(movimiento.submodulo || movimiento.referencia || 'SIN SUBMODULO');
    const categoryLabel = `Taller · ${submodulo}`;
    return {
      sheetKey: `TALLER::${submodulo}`,
      sheetName: sanitizarNombreHoja(categoryLabel),
      categoryLabel,
      moduleName: 'TALLER',
      submodulo,
      order: ordenCategoria('TALLER', submodulo),
    };
  }

  const categoryLabel = modulo;
  return {
    sheetKey: modulo,
    sheetName: sanitizarNombreHoja(categoryLabel),
    categoryLabel,
    moduleName: modulo,
    order: ordenCategoria(modulo),
  };
}

function claveProducto(movimiento: MovimientoParaReporte, categoria: CategoriaMovimiento) {
  const modulo = normalizarModulo(categoria.moduleName || 'sinmodulo');
  const codigo = movimiento.codigo.trim();
  if (codigo && codigo.toLowerCase() !== 'sin código') return `${modulo}::${normalizar(codigo)}`;
  const descripcion = normalizar(movimiento.descripcion);
  const referencia = normalizar(movimiento.referencia);
  if (descripcion) return `${modulo}::${descripcion}|${referencia}`;
  return `${modulo}::${referencia || 'sinreferencia'}`;
}

function esEntrada(movimiento: MovimientoParaReporte, moduleName = '', tallerSubmodulo = '') {
  if (moduleName === 'TALLER' && tallerSubmodulo) return esEntradaVistaTaller(movimiento, tallerSubmodulo);
  const tipo = normalizar(movimiento.tipo);
  return tipo.includes('entrada') || tipo.includes('ingreso');
}

function esSalida(movimiento: MovimientoParaReporte, moduleName = '', tallerSubmodulo = '') {
  if (moduleName === 'TALLER' && tallerSubmodulo) return esSalidaVistaTaller(movimiento, tallerSubmodulo);
  const tipo = normalizar(movimiento.tipo);
  return tipo.includes('salida') || tipo.includes('entrega') || tipo.includes('traslado') || tipo.includes('consumo');
}

function ordenarPorFechaAsc(a: MovimientoParaReporte, b: MovimientoParaReporte) {
  return (a.fecha || '').localeCompare(b.fecha || '') || a.id.localeCompare(b.id);
}

function formatearFechaCorta(fechaIso: string) {
  const [year, month, day] = fechaIso.split('-');
  if (!year || !month || !day) return fechaIso;
  return `${day}/${month}/${year}`;
}

export function etiquetaPeriodoReporte(desde: string, hasta: string) {
  if (!desde && !hasta) return 'Histórico completo';
  if (desde && hasta) return `${formatearFechaCorta(desde)} al ${formatearFechaCorta(hasta)}`;
  if (desde) return `Desde ${formatearFechaCorta(desde)}`;
  return `Hasta ${formatearFechaCorta(hasta)}`;
}

export function fechaExportacionReporte(fecha = new Date()) {
  return new Intl.DateTimeFormat('es-CO', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(fecha);
}

function responsableMovimiento(movimiento: MovimientoParaReporte, usuarios: UsuarioLookup) {
  const candidato = movimiento.solicitante || movimiento.responsableEntrega || movimiento.usuario || '';
  const perfil = usuarios[candidato.trim()];
  if (perfil) {
    return [perfil.nombre || perfil.email, perfil.cargo].filter(Boolean).join(' - ');
  }
  return [candidato, movimiento.cargo].filter(Boolean).join(' - ') || 'Sin responsable';
}

function crearFilaMovimiento(
  movimiento: MovimientoParaReporte,
  categoria: CategoriaMovimiento,
  saldoAnterior: number,
  saldoNuevo: number,
  usuarios: UsuarioLookup,
): FilaMovimientoExcel {
  const contextoSubmodulo = categoria.moduleName === 'TALLER' ? (categoria.submodulo ?? '') : '';
  return {
    fecha: movimiento.fecha || 'Sin fecha',
    tipo_movimiento: movimiento.tipo || 'Movimiento',
    codigo: movimiento.codigo || 'Sin código',
    nombre_producto: movimiento.descripcion || 'Sin descripción',
    submodulo: categoria.submodulo || categoria.moduleName,
    subcategoria: movimiento.referencia || movimiento.submodulo || '',
    cantidad_entrada: esEntrada(movimiento, categoria.moduleName, contextoSubmodulo) ? movimiento.cantidad : 0,
    cantidad_salida: esSalida(movimiento, categoria.moduleName, contextoSubmodulo) ? movimiento.cantidad : 0,
    unidad: movimiento.unidad || 'Unidad',
    saldo_anterior: saldoAnterior,
    saldo_nuevo: saldoNuevo,
    responsable: responsableMovimiento(movimiento, usuarios),
    observacion: movimiento.observaciones || '',
    documento_soporte: movimiento.fotoUrl || '',
    labor: movimiento.labor || movimiento.frente || '',
    zona: movimiento.zona || '',
    horometro: movimiento.horometro ? String(movimiento.horometro) : '',
  };
}

function filasConSaldos(
  movimientos: MovimientoParaReporte[],
  categoria: CategoriaMovimiento,
  usuarios: UsuarioLookup,
) {
  const saldos = new Map<string, number>();
  const ordenados = [...movimientos].sort(ordenarPorFechaAsc);
  const filasPorMovimiento = new Map<string, FilaMovimientoExcel>();

  const contextoSubmodulo = categoria.moduleName === 'TALLER' ? (categoria.submodulo ?? '') : '';

  ordenados.forEach((movimiento) => {
    const clave = claveProducto(movimiento, categoria);
    const saldoAnterior = saldos.get(clave) ?? 0;
    let saldoNuevo = saldoAnterior;

    if (esEntrada(movimiento, categoria.moduleName, contextoSubmodulo)) saldoNuevo = saldoAnterior + movimiento.cantidad;
    else if (esSalida(movimiento, categoria.moduleName, contextoSubmodulo)) saldoNuevo = saldoAnterior - movimiento.cantidad;

    saldos.set(clave, saldoNuevo);
    filasPorMovimiento.set(
      movimiento.id,
      crearFilaMovimiento(movimiento, categoria, saldoAnterior, saldoNuevo, usuarios),
    );
  });

  return movimientos
    .map((movimiento) => filasPorMovimiento.get(movimiento.id))
    .filter((fila): fila is FilaMovimientoExcel => Boolean(fila));
}

function filasConsolidado(movimientos: MovimientoParaReporte[], categoria: CategoriaMovimiento) {
  const grupos = new Map<string, FilaConsolidadoExcel>();
  const contextoSubmodulo = categoria.moduleName === 'TALLER' ? (categoria.submodulo ?? '') : '';

  movimientos.forEach((movimiento) => {
    const clave = claveProducto(movimiento, categoria);
    const actual = grupos.get(clave) ?? {
      codigo: movimiento.codigo || 'Sin código',
      nombre_producto: movimiento.descripcion || 'Sin descripción',
      submodulo: categoria.submodulo || categoria.moduleName,
      subcategoria: movimiento.referencia || movimiento.submodulo || '',
      total_entradas: 0,
      total_salidas: 0,
      saldo_neto: 0,
      unidad: movimiento.unidad || 'Unidad',
    };

    if (esEntrada(movimiento, categoria.moduleName, contextoSubmodulo)) actual.total_entradas += movimiento.cantidad;
    if (esSalida(movimiento, categoria.moduleName, contextoSubmodulo)) actual.total_salidas += movimiento.cantidad;
    actual.saldo_neto = actual.total_entradas - actual.total_salidas;
    grupos.set(clave, actual);
  });

  return [...grupos.values()].sort((a, b) => (
    a.nombre_producto.localeCompare(b.nombre_producto) || a.codigo.localeCompare(b.codigo)
  ));
}

function resumenDesdeFilas(filas: FilaMovimientoExcel[]): ResumenCategoria {
  const entradas = filas.filter((fila) => fila.cantidad_entrada > 0);
  const salidas = filas.filter((fila) => fila.cantidad_salida > 0);
  return {
    total_movimientos: filas.length,
    total_entradas: entradas.length,
    total_salidas: salidas.length,
    cantidad_entradas: entradas.reduce((suma, fila) => suma + fila.cantidad_entrada, 0),
    cantidad_salidas: salidas.reduce((suma, fila) => suma + fila.cantidad_salida, 0),
  };
}

function movimientoPerteneceAlModulo(
  movimiento: MovimientoParaReporte,
  moduleName: string,
  tallerSubmodulo: string,
) {
  if (esMovimientoSistema(movimiento)) return false;
  if (!coincideModulo(movimiento.modulo, moduleName)) return false;
  if (moduleName === 'TALLER' && tallerSubmodulo) {
    return movimientoPerteneceSubmoduloTaller(movimiento, tallerSubmodulo);
  }
  return true;
}

function categoriaTaller(submodulo: string): CategoriaMovimiento {
  const submoduloNormalizado = normalizarSubmoduloTaller(submodulo || 'SIN SUBMODULO');
  const categoryLabel = `Taller · ${submoduloNormalizado}`;
  return {
    sheetKey: `TALLER::${submoduloNormalizado}`,
    sheetName: sanitizarNombreHoja(categoryLabel),
    categoryLabel,
    moduleName: 'TALLER',
    submodulo: submoduloNormalizado,
    order: ordenCategoria('TALLER', submoduloNormalizado),
  };
}

function agruparPorModuloSeleccionado(
  movimientos: MovimientoParaReporte[],
  moduleName: string,
  tallerSubmodulo: string,
) {
  const filtrados = movimientos.filter((movimiento) => (
    movimientoPerteneceAlModulo(movimiento, moduleName, tallerSubmodulo)
  ));

  if (moduleName !== 'TALLER') {
    const categoria: CategoriaMovimiento = {
      sheetKey: moduleName,
      sheetName: sanitizarNombreHoja(moduleName),
      categoryLabel: moduleName,
      moduleName,
      order: ordenCategoria(moduleName),
    };
    return [{ categoria, movimientos: filtrados }];
  }

  if (tallerSubmodulo) {
    return [{ categoria: categoriaTaller(tallerSubmodulo), movimientos: filtrados }];
  }

  const grupos = new Map<string, { categoria: CategoriaMovimiento; movimientos: MovimientoParaReporte[] }>();
  filtrados.forEach((movimiento) => {
    const categoria = categoriaTaller(movimiento.submodulo || movimiento.referencia || 'SIN SUBMODULO');
    const actual = grupos.get(categoria.sheetKey) ?? { categoria, movimientos: [] };
    actual.movimientos.push(movimiento);
    grupos.set(categoria.sheetKey, actual);
  });

  return [...grupos.values()].sort((a, b) => a.categoria.order - b.categoria.order);
}

export function nombreArchivoReporte(moduleName: string) {
  const slug = moduleName
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/\s+/g, '_')
    .replace(/[^A-Za-z0-9_]/g, '')
    .toLowerCase();
  return `Reporte_Movimientos_${slug || 'modulo'}_ARLES.xlsx`;
}

function nombresHojaUnicos(categorias: HojaCategoriaReporte[]) {
  const usados = new Map<string, number>();
  return categorias.map((categoria) => {
    const base = categoria.sheetName;
    const conteo = usados.get(base) ?? 0;
    usados.set(base, conteo + 1);
    if (conteo === 0) return categoria;
    const sufijo = ` ${conteo + 1}`;
    return {
      ...categoria,
      sheetName: sanitizarNombreHoja(`${base.slice(0, 31 - sufijo.length)}${sufijo}`),
    };
  });
}

export function crearReporteMovimientos(opciones: {
  moduleName: string;
  tallerSubmodulo?: string;
  movimientos: MovimientoParaReporte[];
  usuarios: UsuarioLookup;
  periodLabel: string;
  exportDate: string;
  generatedBy: string;
  coverageLabel: string;
}): ReporteMovimientosPayload {
  const grupos = agruparPorModuloSeleccionado(
    opciones.movimientos,
    opciones.moduleName,
    opciones.tallerSubmodulo ?? '',
  );

  const filasPorMovimiento = new Map<string, FilaMovimientoExcel>();
  const categorias = nombresHojaUnicos(grupos.map(({ categoria, movimientos }) => {
    const filas = filasConSaldos(movimientos, categoria, opciones.usuarios);
    movimientos.forEach((movimiento, index) => {
      const fila = filas[index];
      if (fila) filasPorMovimiento.set(movimiento.id, fila);
    });
    const entradas = filas.filter((fila) => fila.cantidad_entrada > 0);
    const salidas = filas.filter((fila) => fila.cantidad_salida > 0);
    return {
      sheetKey: categoria.sheetKey,
      sheetName: categoria.sheetName,
      categoryLabel: categoria.categoryLabel,
      moduleName: categoria.moduleName,
      submodulo: categoria.submodulo,
      movimientos: filas,
      entradas,
      salidas,
      consolidated: filasConsolidado(movimientos, categoria),
      summary: resumenDesdeFilas(filas),
    };
  }));

  const resumenGlobal = categorias.reduce<ResumenCategoria>(
    (acc, categoria) => ({
      total_movimientos: acc.total_movimientos + categoria.summary.total_movimientos,
      total_entradas: acc.total_entradas + categoria.summary.total_entradas,
      total_salidas: acc.total_salidas + categoria.summary.total_salidas,
      cantidad_entradas: acc.cantidad_entradas + categoria.summary.cantidad_entradas,
      cantidad_salidas: acc.cantidad_salidas + categoria.summary.cantidad_salidas,
    }),
    {
      total_movimientos: 0,
      total_entradas: 0,
      total_salidas: 0,
      cantidad_entradas: 0,
      cantidad_salidas: 0,
    },
  );

  const movimientosGenerales = opciones.movimientos
    .map((movimiento) => filasPorMovimiento.get(movimiento.id))
    .filter((fila): fila is FilaMovimientoExcel => Boolean(fila));
  const entradasGenerales = movimientosGenerales.filter((fila) => fila.cantidad_entrada > 0);
  const salidasGenerales = movimientosGenerales.filter((fila) => fila.cantidad_salida > 0);

  return {
    companyName: 'ARLES S.A.S.',
    title: 'REPORTE DE MOVIMIENTOS DE INVENTARIO',
    moduleName: opciones.moduleName,
    suggestedFileName: nombreArchivoReporte(opciones.moduleName),
    periodLabel: opciones.periodLabel,
    exportDate: opciones.exportDate,
    generatedBy: opciones.generatedBy,
    coverageLabel: opciones.coverageLabel,
    summary: {
      ...resumenGlobal,
      total_categorias: categorias.length,
    },
    categorias,
    movimientosGenerales,
    entradasGenerales,
    salidasGenerales,
  };
}

export async function exportarReporteMovimientos(
  payload: ReporteMovimientosPayload,
): Promise<ExportarReporteResult> {
  if (!window.electronAPI?.exportarReporteMovimientos) {
    throw new Error('La exportación solo funciona en la aplicación de escritorio (.exe).');
  }
  return window.electronAPI.exportarReporteMovimientos(payload);
}
