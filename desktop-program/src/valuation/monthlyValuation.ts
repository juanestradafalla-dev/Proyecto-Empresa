import {
  collection,
  doc,
  getDocs,
  onSnapshot,
  runTransaction,
  serverTimestamp,
  Timestamp,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';
import type { User } from 'firebase/auth';
import { db } from '../firebase';
import { summarizeCurrentValuation } from './currentValuation';
import type { CurrentValuationRow, MonthlyValuationItem, MonthlyValuationSummary } from './models';

const MONTHLY_CLOSES_COLLECTION = 'cierres_valoracion_inventario';
const FIRESTORE_SAFE_BATCH_SIZE = 450;

export class DuplicateMonthlyCloseError extends Error {
  constructor(period: string) {
    super(`Ya existe un corte completo para ${period}.`);
    this.name = 'DuplicateMonthlyCloseError';
  }
}
export class MonthlyCloseInProgressError extends Error {
  constructor(period: string) {
    super(`El corte ${period} ya se está guardando.`);
    this.name = 'MonthlyCloseInProgressError';
  }
}

export function currentValuationPeriod(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

export function formatValuationPeriod(period: string) {
  const [year, month] = period.split('-').map(Number);
  if (!year || !month) return period;
  return new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })
    .format(new Date(year, month - 1, 1));
}

function chunkRows(rows: CurrentValuationRow[]) {
  const chunks: CurrentValuationRow[][] = [];
  for (let index = 0; index < rows.length; index += FIRESTORE_SAFE_BATCH_SIZE) {
    chunks.push(rows.slice(index, index + FIRESTORE_SAFE_BATCH_SIZE));
  }
  return chunks;
}

function toDate(value: unknown) {
  return value instanceof Timestamp ? value.toDate() : null;
}

function readMonthlySummary(period: string, data: Record<string, unknown>): MonthlyValuationSummary {
  const summary = data.resumen && typeof data.resumen === 'object'
    ? data.resumen as Record<string, unknown>
    : {};
  const moduleTotals = summary.totales_modulo && typeof summary.totales_modulo === 'object'
    ? summary.totales_modulo as Record<string, number>
    : {};
  const status = data.estado === 'guardando' || data.estado === 'error' ? data.estado : 'completo';

  return {
    period,
    totalValue: Number(summary.valor_total) || 0,
    productCount: Number(summary.cantidad_productos) || 0,
    valuedProductCount: Number(summary.productos_con_valor) || 0,
    unvaluedProductCount: Number(summary.productos_sin_valor) || 0,
    valuedPercentage: Number(summary.porcentaje_valorado) || 0,
    moduleTotals,
    createdAt: toDate(data.fecha),
    createdBy: typeof data.usuario === 'string' ? data.usuario : '',
    status,
  };
}

export function subscribeMonthlyValuationSummaries(
  onData: (summaries: MonthlyValuationSummary[]) => void,
  onError: (error: Error) => void,
) {
  return onSnapshot(
    collection(db, MONTHLY_CLOSES_COLLECTION),
    (snapshot) => {
      const summaries = snapshot.docs
        .map((snapshotDoc) => readMonthlySummary(snapshotDoc.id, snapshotDoc.data()))
        .filter((summary) => summary.status === 'completo')
        .sort((left, right) => right.period.localeCompare(left.period));
      onData(summaries);
    },
    (error) => onError(error),
  );
}

export async function loadMonthlyValuationItems(period: string): Promise<MonthlyValuationItem[]> {
  const snapshot = await getDocs(collection(db, MONTHLY_CLOSES_COLLECTION, period, 'items'));
  return snapshot.docs.map((itemDoc) => {
    const data = itemDoc.data();
    return {
      id: itemDoc.id,
      moduleName: typeof data.modulo === 'string' ? data.modulo : 'Sin módulo',
      code: typeof data.codigo === 'string' ? data.codigo : '',
      reference: typeof data.referencia === 'string' && data.referencia.trim() ? data.referencia : 'N/A',
      product: typeof data.producto === 'string' ? data.producto : '',
      quantity: Number(data.cantidad) || 0,
      unit: typeof data.unidad === 'string' ? data.unidad : '',
      unitValue: Number(data.valor_unitario) || 0,
      totalValue: Number(data.valor_total) || 0,
    };
  }).sort((left, right) => (
    left.moduleName.localeCompare(right.moduleName)
    || left.code.localeCompare(right.code, undefined, { numeric: true })
    || left.product.localeCompare(right.product)
  ));
}

export async function saveMonthlyValuationClose({
  period,
  rows,
  moduleOptions,
  user,
  onProgress,
}: {
  period: string;
  rows: CurrentValuationRow[];
  moduleOptions: string[];
  user: User;
  onProgress: (completedSteps: number, totalSteps: number) => void;
}) {
  const summary = summarizeCurrentValuation(rows, moduleOptions);
  const chunks = chunkRows(rows);
  const totalSteps = chunks.length + 2;
  const closeRef = doc(db, MONTHLY_CLOSES_COLLECTION, period);
  const userLabel = user.email || user.displayName || user.uid;
  const summaryPayload = {
    valor_total: summary.inventoryGrandTotal,
    cantidad_productos: summary.productCount,
    productos_con_valor: summary.valuedProductCount,
    productos_sin_valor: summary.unvaluedProductCount,
    porcentaje_valorado: summary.valuedPercentage,
    totales_modulo: Object.fromEntries(summary.moduleTotals.map((entry) => [entry.moduleName, entry.total])),
  };

  await runTransaction(db, async (transaction) => {
    const existing = await transaction.get(closeRef);
    if (existing.exists()) {
      const status = existing.data().estado;
      if (status === 'completo') throw new DuplicateMonthlyCloseError(period);
      if (status === 'guardando') throw new MonthlyCloseInProgressError(period);
    }

    transaction.set(closeRef, {
      periodo: period,
      resumen: summaryPayload,
      fecha: serverTimestamp(),
      usuario: userLabel,
      usuario_uid: user.uid,
      estado: 'guardando',
    });
  });
  onProgress(1, totalSteps);

  try {
    for (let index = 0; index < chunks.length; index += 1) {
      const batch = writeBatch(db);
      chunks[index].forEach((row) => {
        const itemRef = doc(db, MONTHLY_CLOSES_COLLECTION, period, 'items', row.valuationId);
        batch.set(itemRef, {
          modulo: row.moduleName,
          codigo: row.code,
          referencia: row.reference || 'N/A',
          producto: row.product,
          cantidad: row.quantity,
          unidad: row.unit,
          valor_unitario: row.unitValue,
          valor_total: row.totalValue,
        });
      });
      await batch.commit();
      onProgress(index + 2, totalSteps);
    }

    await updateDoc(closeRef, { estado: 'completo' });
    onProgress(totalSteps, totalSteps);
  } catch (error) {
    try {
      await updateDoc(closeRef, { estado: 'error' });
    } catch (statusError) {
      console.error('No se pudo marcar el corte mensual como fallido:', statusError);
    }
    throw error;
  }
}
