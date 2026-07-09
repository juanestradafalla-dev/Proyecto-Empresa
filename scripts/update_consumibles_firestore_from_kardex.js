#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const rootDir = path.resolve(__dirname, "..");
const firebaseRcPath = path.join(rootDir, ".firebaserc");
const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const jsonArg = args.find((arg) => arg.startsWith("--json="));
const jsonPath = jsonArg ? jsonArg.slice("--json=".length) : path.join(rootDir, "outputs", "consumibles_kardex_firestore", "consumibles_normalized.json");
const sourceArg = args.find((arg) => arg.startsWith("--source="));
const sourceName = sourceArg ? path.basename(sourceArg.slice("--source=".length)) : "Kardex_Consumibles_Seccion_Z_corregido.xlsx";

function projectIdFromFirebaseRc() {
  try {
    const parsed = JSON.parse(fs.readFileSync(firebaseRcPath, "utf8"));
    return parsed.projects && parsed.projects.default;
  } catch (_) {
    return "arles-gestion";
  }
}

function normalizeText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

function docCode(data, fallbackId) {
  return String(data.codigo_interno || data.codigoInterno || data.codigo || fallbackId || "")
    .trim()
    .toUpperCase();
}

function isConsumiblesDoc(doc) {
  const data = doc.data();
  const modulo = normalizeText(data.modulo);
  return modulo.includes("consumible") && data.activo !== false;
}

function serializable(value) {
  if (value && typeof value.toDate === "function") return value.toDate().toISOString();
  if (Array.isArray(value)) return value.map(serializable);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, serializable(entry)]));
  }
  return value;
}

function loadProducts() {
  if (!fs.existsSync(jsonPath)) throw new Error(`JSON normalizado no encontrado: ${jsonPath}`);
  const parsed = JSON.parse(fs.readFileSync(jsonPath, "utf8"));
  const products = Array.isArray(parsed.products) ? parsed.products : [];
  if (products.length === 0) throw new Error("El JSON no contiene productos.");
  const codes = new Set();
  const duplicates = [];
  for (const product of products) {
    const code = String(product.codigo_interno || "").trim().toUpperCase();
    if (!code) throw new Error(`Producto sin codigo: ${JSON.stringify(product)}`);
    if (codes.has(code)) duplicates.push(code);
    codes.add(code);
  }
  if (duplicates.length) throw new Error(`Codigos duplicados: ${duplicates.join(", ")}`);
  return { parsed, products, codes };
}

async function readCollection(db, name) {
  const snapshot = await db.collection(name).get();
  return snapshot.docs;
}

async function backupRelevant(db, products, codes, nowStamp) {
  const [existenciasDocs, catalogoDocs] = await Promise.all([
    readCollection(db, "existencias"),
    readCollection(db, "catalogo_personalizado"),
  ]);

  const relevantExistencias = existenciasDocs.filter((doc) => {
    const code = docCode(doc.data(), doc.id);
    return codes.has(code) || isConsumiblesDoc(doc);
  });
  const relevantCatalogo = catalogoDocs.filter((doc) => {
    const code = docCode(doc.data(), doc.id);
    return codes.has(code) || isConsumiblesDoc(doc);
  });

  const backup = {
    accion: "Actualizar Consumibles desde Kardex Excel",
    fecha: nowStamp,
    fuente: sourceName,
    productos_nuevos: products.length,
    existencias: relevantExistencias.map((doc) => ({ id: doc.id, data: serializable(doc.data()) })),
    catalogo_personalizado: relevantCatalogo.map((doc) => ({ id: doc.id, data: serializable(doc.data()) })),
  };

  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const backupPath = path.join(backupDir, `consumibles-kardex-backup-${nowStamp.replace(/[:.]/g, "-")}.json`);
  fs.writeFileSync(backupPath, JSON.stringify(backup, null, 2), "utf8");
  return { backupPath, existenciasDocs, catalogoDocs, relevantExistencias, relevantCatalogo };
}

function buildFirestoreData(product, importedAt) {
  const baseData = {
    modulo: "Consumibles",
    categoria: product.categoria,
    subcategoria: product.subcategoria,
    item: product.item,
    referencia: product.referencia,
    marca: product.marca || "",
    nombre_completo: product.nombre_completo,
    busqueda: product.busqueda,
    codigo_interno: product.codigo_interno,
    codigo_original: product.codigo_original,
    codigo_excel: product.codigo_excel,
    documento_id: product.documento_id || product.codigo_interno,
    producto_id: product.producto_id || product.codigo_interno,
    unidad: product.unidad,
    ubicacion: product.ubicacion || "",
    activo: true,
    ultima_fecha: importedAt,
    ultimo_solicitante: "Importador Kardex Consumibles",
    observaciones: product.observaciones || "",
    fuente_importacion: sourceName,
    fila_excel: product.fila_excel,
    stock_excel_original: product.stock_excel_original || "",
    codigo_generado: Boolean(product.codigo_generado),
    requiere_revision_stock: Boolean(product.requiere_revision_stock),
  };
  return {
    existencias: {
      ...baseData,
      cantidad: Number(product.cantidad) || 0,
      stock_actual: Number(product.cantidad) || 0,
    },
    catalogo: baseData,
  };
}

async function commitPlan(db, products, codes, existingDocs, nowStamp) {
  const importedAt = new Date().toISOString();
  const archiveData = {
    activo: false,
    modulo: "Consumibles historico",
    archivado_por: "kardex_consumibles_firestore_update",
    archivado_fecha: importedAt,
    archivado_motivo: `No aparece en ${sourceName}`,
  };

  const operations = [];
  for (const product of products) {
    const code = product.codigo_interno;
    const data = buildFirestoreData(product, importedAt);
    operations.push((batch) => batch.set(db.collection("existencias").doc(code), data.existencias, { merge: true }));
    operations.push((batch) => batch.set(db.collection("catalogo_personalizado").doc(code), data.catalogo, { merge: true }));
  }

  for (const doc of existingDocs.existenciasDocs) {
    if (!isConsumiblesDoc(doc)) continue;
    const code = docCode(doc.data(), doc.id);
    if (!codes.has(code)) {
      operations.push((batch) => batch.set(doc.ref, archiveData, { merge: true }));
    }
  }

  for (const doc of existingDocs.catalogoDocs) {
    if (!isConsumiblesDoc(doc)) continue;
    const code = docCode(doc.data(), doc.id);
    if (!codes.has(code)) {
      operations.push((batch) => batch.set(doc.ref, archiveData, { merge: true }));
    }
  }

  operations.push((batch) => batch.set(db.collection("importaciones").doc(`consumibles_kardex_${nowStamp.replace(/[:.]/g, "-")}`), {
    tipo: "inventario_consumibles_kardex_excel",
    archivo: sourceName,
    json_normalizado: jsonPath,
    productos: products.length,
    cantidad_total: products.reduce((sum, product) => sum + (Number(product.cantidad) || 0), 0),
    codigos_generados: products.filter((product) => product.codigo_generado).map((product) => product.codigo_interno),
    requiere_revision_stock: products.filter((product) => product.requiere_revision_stock).map((product) => ({
      codigo: product.codigo_interno,
      fila_excel: product.fila_excel,
      valor: product.stock_excel_original,
    })),
    fecha: importedAt,
  }, { merge: true }));

  let committed = 0;
  for (let i = 0; i < operations.length; i += 450) {
    const batch = db.batch();
    operations.slice(i, i + 450).forEach((op) => op(batch));
    await batch.commit();
    committed += operations.slice(i, i + 450).length;
  }
  return committed;
}

async function verify(db, codes) {
  const [existenciasDocs, catalogoDocs] = await Promise.all([
    readCollection(db, "existencias"),
    readCollection(db, "catalogo_personalizado"),
  ]);
  const activeConsumibles = existenciasDocs.filter(isConsumiblesDoc);
  const activeCatalogo = catalogoDocs.filter(isConsumiblesDoc);
  const activeCodes = new Set(activeConsumibles.map((doc) => docCode(doc.data(), doc.id)));
  const missing = [...codes].filter((code) => !activeCodes.has(code)).sort();
  const totalCantidad = activeConsumibles.reduce((sum, doc) => sum + (Number(doc.data().cantidad) || 0), 0);
  return {
    existencias_consumibles_activos: activeConsumibles.length,
    catalogo_consumibles_activos: activeCatalogo.length,
    cantidad_total_activa: totalCantidad,
    faltantes_en_existencias: missing,
    muestras_generadas: activeConsumibles
      .filter((doc) => docCode(doc.data(), doc.id).startsWith("TOR-"))
      .slice(0, 5)
      .map((doc) => ({ id: doc.id, item: doc.data().item, referencia: doc.data().referencia, cantidad: doc.data().cantidad })),
  };
}

async function main() {
  const { parsed, products, codes } = loadProducts();
  console.log("Resumen JSON:");
  console.log(JSON.stringify(parsed.summary || {}, null, 2));

  admin.initializeApp({
    credential: fs.existsSync(credentialPath)
      ? admin.credential.cert(require(credentialPath))
      : admin.credential.applicationDefault(),
    projectId: projectIdFromFirebaseRc(),
  });
  const db = admin.firestore();
  const nowStamp = new Date().toISOString();

  const backup = await backupRelevant(db, products, codes, nowStamp);
  const currentCodes = new Set(backup.existenciasDocs.filter(isConsumiblesDoc).map((doc) => docCode(doc.data(), doc.id)));
  const newCodes = [...codes].filter((code) => !currentCodes.has(code)).sort();
  const missingInExcel = [...currentCodes].filter((code) => !codes.has(code)).sort();
  const revisedStock = products.filter((product) => product.requiere_revision_stock);
  const generatedCodes = products.filter((product) => product.codigo_generado);

  console.log("\nPreview Firestore:");
  console.log(JSON.stringify({
    actuales_consumibles_activos: currentCodes.size,
    productos_excel: products.length,
    nuevos_en_excel: newCodes.length,
    activos_actuales_fuera_del_excel: missingInExcel.length,
    codigos_generados: generatedCodes.length,
    requieren_revision_stock: revisedStock.length,
    backup_path: backup.backupPath,
    primeros_nuevos: newCodes.slice(0, 15),
    primeros_fuera_del_excel: missingInExcel.slice(0, 15),
    primeros_generados: generatedCodes.slice(0, 10).map((p) => ({ codigo: p.codigo_interno, item: p.item, referencia: p.referencia })),
    revision_stock: revisedStock.map((p) => ({ codigo: p.codigo_interno, fila_excel: p.fila_excel, valor: p.stock_excel_original })),
  }, null, 2));

  if (!apply) {
    console.log("\nVista previa solamente. Ejecuta con --apply para escribir en Firestore.");
    await admin.app().delete();
    return;
  }

  const committed = await commitPlan(db, products, codes, backup, nowStamp);
  const verification = await verify(db, codes);
  console.log(`\nActualizacion aplicada. Operaciones escritas: ${committed}`);
  console.log("Verificacion:");
  console.log(JSON.stringify(verification, null, 2));
  await admin.app().delete();
}

main().catch(async (error) => {
  console.error(`Error: ${error.stack || error.message}`);
  try {
    await admin.app().delete();
  } catch (_) {}
  process.exitCode = 1;
});

