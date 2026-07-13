import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, Timestamp, type Firestore } from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import {
  ManualValuationConflictError,
  saveManualUnitValuation,
  valuationRevisionFromData,
} from '../valuation/manualValuation';

const PROJECT_ID = 'demo-arles-gestion';
const OWNER_EMAIL = 'juanestradafalla@gmail.com';
const rules = readFileSync(new URL('../../../firestore.rules', import.meta.url), 'utf8');

let testEnvironment: RulesTestEnvironment;

async function seedUser(uid: string, profile: Record<string, unknown>) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), 'usuarios', uid), profile);
    await setDoc(doc(context.firestore(), 'existencias', 'producto-prueba'), { saldo: 1 });
  });
}

function inventoryRead(uid: string, email = `${uid}@example.com`) {
  const context = testEnvironment.authenticatedContext(uid, { email });
  return getDoc(doc(context.firestore(), 'existencias', 'producto-prueba'));
}

beforeAll(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules },
  });
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

afterAll(async () => {
  await testEnvironment.cleanup();
});

describe('isActiveUser en firestore.rules', () => {
  it('permite un usuario con estado activo y rol permitido', async () => {
    await seedUser('activo', { estado: 'Activo', rol: 'almacenista' });
    await assertSucceeds(inventoryRead('activo'));
  });

  it('bloquea un usuario activo con rol no permitido', async () => {
    await seedUser('sin-rol', { activo: true, rol: 'visitante' });
    await assertFails(inventoryRead('sin-rol'));
  });

  it('bloquea un usuario con rol permitido pero sin estado activo', async () => {
    await seedUser('sin-estado', { rol: 'operador' });
    await assertFails(inventoryRead('sin-estado'));
  });

  it('bloquea usuarios desactivados aunque conserven un estado o rol permitido', async () => {
    await seedUser('inactivo', { activo: false, estado: 'activo', rol: 'admin' });
    await assertFails(inventoryRead('inactivo'));
  });

  it('mantiene el acceso del propietario sin documento de usuario', async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'existencias', 'producto-prueba'), { saldo: 1 });
    });
    await assertSucceeds(inventoryRead('propietario', OWNER_EMAIL));
  });

  it('la transacción manual no sobrescribe un cambio concurrente', async () => {
    await seedUser('activo', { activo: true, rol: 'almacenista' });
    const openedAt = Timestamp.fromMillis(1_000);
    const changedAt = Timestamp.fromMillis(2_000);
    const initialData = {
      valor_unitario: 1_500,
      modulo: 'EPP',
      codigo: 'EPP-1',
      descripcion: 'Producto',
      actualizado_por: 'equipo-a',
      actualizado_por_uid: 'equipo-a',
      actualizado_en: openedAt,
      origen_actualizacion: 'manual',
    };
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const valuationRef = doc(context.firestore(), 'valoraciones_inventario', 'existencias__producto');
      await setDoc(valuationRef, initialData);
      await setDoc(valuationRef, {
        ...initialData,
        valor_unitario: 1_700,
        actualizado_por: 'equipo-b',
        actualizado_por_uid: 'equipo-b',
        actualizado_en: changedAt,
      });
    });

    const context = testEnvironment.authenticatedContext('activo', { email: 'activo@example.com' });
    await expect(saveManualUnitValuation({
      db: context.firestore() as unknown as Firestore,
      valuationId: 'existencias__producto',
      expectedRevision: valuationRevisionFromData(true, initialData),
      rawValue: '1800',
      moduleName: 'EPP',
      code: 'EPP-1',
      description: 'Producto',
      userLabel: 'activo@example.com',
      userUid: 'activo',
      online: true,
      sourceReady: true,
    })).rejects.toBeInstanceOf(ManualValuationConflictError);

    const current = await getDoc(doc(
      context.firestore(),
      'valoraciones_inventario',
      'existencias__producto',
    ));
    expect(current.data()?.valor_unitario).toBe(1_700);
    expect(current.data()?.actualizado_por_uid).toBe('equipo-b');
  });

  it('una valoración idéntica no genera una escritura duplicada', async () => {
    await seedUser('activo', { activo: true, rol: 'almacenista' });
    const updatedAt = Timestamp.fromMillis(3_000);
    const initialData = {
      valor_unitario: 2_000,
      modulo: 'EPP',
      codigo: 'EPP-2',
      descripcion: 'Producto sin cambio',
      actualizado_por: 'equipo-a',
      actualizado_por_uid: 'equipo-a',
      actualizado_en: updatedAt,
      origen_actualizacion: 'manual',
    };
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), 'valoraciones_inventario', 'existencias__sin-cambio'),
        initialData,
      );
    });

    const context = testEnvironment.authenticatedContext('activo', { email: 'activo@example.com' });
    const result = await saveManualUnitValuation({
      db: context.firestore() as unknown as Firestore,
      valuationId: 'existencias__sin-cambio',
      expectedRevision: valuationRevisionFromData(true, initialData),
      rawValue: '2000',
      moduleName: 'EPP',
      code: 'EPP-2',
      description: 'Producto sin cambio',
      userLabel: 'activo@example.com',
      userUid: 'activo',
      online: true,
      sourceReady: true,
    });
    const current = await getDoc(doc(
      context.firestore(),
      'valoraciones_inventario',
      'existencias__sin-cambio',
    ));

    expect(result.status).toBe('unchanged');
    expect(current.data()?.actualizado_por_uid).toBe('equipo-a');
    expect(current.data()?.actualizado_en.toMillis()).toBe(updatedAt.toMillis());
  });
});
