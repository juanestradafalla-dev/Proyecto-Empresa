const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");
const modulo = "Lubricantes taller";
const ubicacion = "CASETA DE LUBRICANTES";
const usuarioActualizacion = "Actualizacion manual lubricantes";

const nuevos = [
  { item: "PINTURA EVERY ALUMINIO", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "PINTURA EVERY AMARILLO", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "PINTURA EPOXICA AMARILLO", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "PINTURA ANTICORROSIVA BLANCA", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "PINTURA 4 EN 1 NEGRA", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "PINTURA ESMALTE", cantidad: 1, codigoPrefix: "PIN", categoria: "Taller y mantenimiento", subcategoria: "Pinturas" },
  { item: "RESINA 750GR", cantidad: 2, codigoPrefix: "RES", categoria: "Taller y mantenimiento", subcategoria: "Resinas" },
];

const actualizaciones = [
  {
    busqueda: "CELERITY",
    cantidad: 4,
    categoria: "Lubricantes y fluidos",
    subcategoria: "Aceites",
  },
];

function normalizar(valor) {
  return String(valor || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, " ")
    .trim();
}

function codigoDoc(data, fallbackId) {
  return String(data.codigo_original || data.codigo_interno || data.codigo || fallbackId || "")
    .trim()
    .toUpperCase();
}

function moduloDoc(data) {
  return normalizar(data.modulo);
}

function esLubricante(data) {
  return moduloDoc(data) === normalizar(modulo);
}

function docId(codigo, destinoUbicacion = ubicacion) {
  return `Q-${codigo}-${normalizar(destinoUbicacion).replace(/\s+/g, "-")}`;
}

function busqueda(docIdValue, codigo, producto, categoria, destinoUbicacion) {
  return `${docIdValue} ${codigo} ${producto} ${categoria} ${destinoUbicacion}`.toLowerCase();
}

function fechaLocal() {
  const now = new Date();
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Bogota",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(now);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day} ${value.hour}:${value.minute}`;
}

function nextCode(prefix, existingDocs, reserved) {
  let max = 0;
  const used = new Set(reserved);
  for (const doc of existingDocs) {
    const code = codigoDoc(doc.data, doc.id);
    used.add(code);
    const match = new RegExp(`^${prefix}(\\d+)$`).exec(code);
    if (match) max = Math.max(max, Number(match[1]) || 0);
  }

  while (true) {
    max += 1;
    const code = `${prefix}${String(max).padStart(3, "0")}`;
    if (!used.has(code)) return code;
  }
}

function buildNewData(producto, codigo, fecha) {
  const id = docId(codigo);
  return {
    modulo,
    categoria: producto.categoria,
    subcategoria: producto.subcategoria,
    item: producto.item,
    referencia: codigo,
    codigo_interno: codigo,
    documento_id: id,
    producto_id: id,
    codigo_original: codigo,
    ubicacion,
    cantidad: Number(producto.cantidad) || 0,
    unidad: "UNIDAD",
    activo: true,
    origen_canonico: "Alta manual lubricantes 2026-06-23",
    version_canonica: "alta_manual_lubricantes_2026_06_23",
    stock_base_columna_g: Number(producto.cantidad) || 0,
    filas_excel: [],
    sistema_ignorado: true,
    familia_operativa: modulo,
    ultima_fecha: fecha,
    ultimo_solicitante: usuarioActualizacion,
    busqueda: busqueda(id, codigo, producto.item, producto.categoria, ubicacion),
  };
}

function buildUpdateData(doc, actualizacion, fecha) {
  const data = doc.data;
  const codigo = codigoDoc(data, doc.id);
  const item = String(data.item || data.producto || actualizacion.busqueda || "").trim();
  return {
    modulo,
    categoria: data.categoria || actualizacion.categoria,
    subcategoria: data.subcategoria || actualizacion.subcategoria,
    item,
    referencia: data.referencia || codigo,
    codigo_interno: data.codigo_interno || codigo,
    documento_id: data.documento_id || doc.id,
    producto_id: data.producto_id || doc.id,
    codigo_original: data.codigo_original || codigo,
    ubicacion: data.ubicacion || ubicacion,
    cantidad: Number(actualizacion.cantidad) || 0,
    unidad: data.unidad || "UNIDAD",
    activo: true,
    familia_operativa: modulo,
    ultima_fecha: fecha,
    ultimo_solicitante: usuarioActualizacion,
    busqueda: busqueda(doc.id, codigo, item, data.categoria || actualizacion.categoria, data.ubicacion || ubicacion),
  };
}

async function readCollection(db, name) {
  const snap = await db.collection(name).get();
  const docs = [];
  snap.forEach((doc) => docs.push({ id: doc.id, ref: doc.ref, data: doc.data() }));
  return docs;
}

async function createBackup(existencias, catalogo) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `lubricantes-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Actualizacion manual Lubricantes taller",
        fecha: new Date().toISOString(),
        existencias: existencias.filter((doc) => esLubricante(doc.data)).map((doc) => ({ id: doc.id, data: doc.data })),
        catalogo_personalizado: catalogo.filter((doc) => esLubricante(doc.data)).map((doc) => ({ id: doc.id, data: doc.data })),
      },
      null,
      2,
    ),
    "utf8",
  );
  return backupPath;
}

function findByItem(docs, item) {
  const target = normalizar(item);
  return docs.find((doc) => esLubricante(doc.data) && normalizar(doc.data.item || doc.data.producto || "") === target);
}

function findByText(docs, text) {
  const target = normalizar(text);
  return docs.find((doc) => {
    if (!esLubricante(doc.data)) return false;
    const value = normalizar(`${doc.id} ${codigoDoc(doc.data, doc.id)} ${doc.data.item || ""}`);
    return value.includes(target);
  });
}

async function main() {
  if (!admin.apps.length) {
    const serviceAccount = require(credentialPath);
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  }

  const db = admin.firestore();
  const [existencias, catalogo] = await Promise.all([
    readCollection(db, "existencias"),
    readCollection(db, "catalogo_personalizado"),
  ]);
  const allDocs = [...existencias, ...catalogo];
  const fecha = fechaLocal();
  const reservedCodes = new Set();
  const operations = [];
  const preview = [];

  for (const actualizacion of actualizaciones) {
    const existing = findByText(existencias, actualizacion.busqueda) || findByText(catalogo, actualizacion.busqueda);
    if (!existing) {
      throw new Error(`No encontre producto para actualizar: ${actualizacion.busqueda}`);
    }
    const data = buildUpdateData(existing, actualizacion, fecha);
    operations.push({ id: existing.id, data, action: "update" });
    preview.push({
      accion: "actualizar",
      id: existing.id,
      codigo: codigoDoc(existing.data, existing.id),
      item: data.item,
      cantidad_anterior: existing.data.cantidad,
      cantidad_nueva: data.cantidad,
    });
  }

  for (const producto of nuevos) {
    const existing = findByItem(existencias, producto.item) || findByItem(catalogo, producto.item);
    if (existing) {
      const data = buildUpdateData(existing, { ...producto, busqueda: producto.item }, fecha);
      operations.push({ id: existing.id, data, action: "update" });
      preview.push({
        accion: "actualizar",
        id: existing.id,
        codigo: codigoDoc(existing.data, existing.id),
        item: data.item,
        cantidad_anterior: existing.data.cantidad,
        cantidad_nueva: data.cantidad,
      });
      continue;
    }

    const codigo = nextCode(producto.codigoPrefix, allDocs, reservedCodes);
    reservedCodes.add(codigo);
    const data = buildNewData(producto, codigo, fecha);
    operations.push({ id: data.documento_id, data, action: "create" });
    preview.push({
      accion: "crear",
      id: data.documento_id,
      codigo,
      item: data.item,
      cantidad_nueva: data.cantidad,
    });
  }

  console.log(JSON.stringify({ apply, operaciones: preview }, null, 2));

  if (!apply) {
    console.log("\nVista previa. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  const backupPath = await createBackup(existencias, catalogo);
  const batch = db.batch();
  for (const operation of operations) {
    batch.set(db.collection("existencias").doc(operation.id), operation.data, { merge: true });
    batch.set(db.collection("catalogo_personalizado").doc(operation.id), operation.data, { merge: true });
  }
  await batch.commit();

  const verify = [];
  for (const operation of operations) {
    const doc = await db.collection("existencias").doc(operation.id).get();
    const data = doc.data() || {};
    verify.push({
      id: doc.id,
      codigo: data.codigo_original || data.codigo_interno,
      item: data.item,
      cantidad: data.cantidad,
      unidad: data.unidad,
      modulo: data.modulo,
      ubicacion: data.ubicacion,
    });
  }

  console.log(JSON.stringify({ backupPath, escritos: verify.length, verify }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

