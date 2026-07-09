const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");

const modulo = "DotaciÃ³n";
const item = "BOTA DE CAUCHO PUNTA DE ACERO";
const usuario = "Actualizacion manual Dotacion";
const observaciones = "Entrada manual de botas de caucho punta de acero por tallas.";

const entradas = [
  { talla: "36", cantidad: 2 },
  { talla: "38", cantidad: 2 },
  { talla: "39", cantidad: 2 },
  { talla: "40", cantidad: 5 },
  { talla: "41", cantidad: 5 },
  { talla: "43", cantidad: 5 },
];

function normalizar(valor) {
  return String(valor || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, " ")
    .trim();
}

function numero(valor, fallback = 0) {
  if (typeof valor === "number" && Number.isFinite(valor)) return valor;
  const parsed = Number(String(valor || "").replace(",", "."));
  return Number.isFinite(parsed) ? parsed : fallback;
}

function codigoDoc(data, fallbackId) {
  return String(data.codigo_interno || data.codigo || fallbackId || "").trim().toUpperCase();
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

function esDotacion(data) {
  return normalizar(data.modulo) === "DOTACION";
}

function tallaDesdeReferencia(referencia) {
  const match = String(referencia || "").match(/talla\s*:?\s*([0-9A-Z]+)/i);
  return match ? match[1].toUpperCase() : "";
}

function findProducto(docs, talla) {
  return docs.find((doc) => {
    const data = doc.data;
    return esDotacion(data) &&
      normalizar(data.item || data.producto) === normalizar(item) &&
      tallaDesdeReferencia(data.referencia) === String(talla);
  });
}

async function readCollection(db, name) {
  const snap = await db.collection(name).get();
  const docs = [];
  snap.forEach((doc) => docs.push({ id: doc.id, ref: doc.ref, data: doc.data() }));
  return docs;
}

async function createBackup(docs) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `dotacion-botas-caucho-entrada-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Entrada manual Dotacion Bota de caucho punta de acero",
        fecha: new Date().toISOString(),
        docs: docs.map((doc) => ({ coleccion: doc.coleccion, id: doc.id, data: doc.data })),
      },
      null,
      2,
    ),
    "utf8",
  );
  return backupPath;
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

  const operaciones = entradas.map((entrada) => {
    const existencia = findProducto(existencias, entrada.talla);
    if (!existencia) {
      throw new Error(`No encontre en existencias ${item} talla ${entrada.talla}`);
    }
    const catalogoDoc = catalogo.find((doc) => doc.id === existencia.id);
    const anterior = numero(existencia.data.cantidad ?? existencia.data.stock_actual);
    const nuevo = anterior + entrada.cantidad;
    return {
      ...entrada,
      id: existencia.id,
      codigo: codigoDoc(existencia.data, existencia.id),
      referencia: existencia.data.referencia || `Talla: ${entrada.talla}`,
      categoria: existencia.data.categoria || "Calzado",
      unidad: existencia.data.unidad || "Par",
      anterior,
      nuevo,
      existencia,
      catalogo: catalogoDoc,
    };
  });

  console.log(JSON.stringify({
    apply,
    operaciones: operaciones.map((op) => ({
      id: op.id,
      codigo: op.codigo,
      talla: op.talla,
      suma: op.cantidad,
      anterior: op.anterior,
      nuevo: op.nuevo,
    })),
  }, null, 2));

  if (!apply) {
    console.log("\nVista previa. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  const backupDocs = [];
  for (const op of operaciones) {
    backupDocs.push({ coleccion: "existencias", id: op.existencia.id, data: op.existencia.data });
    if (op.catalogo) backupDocs.push({ coleccion: "catalogo_personalizado", id: op.catalogo.id, data: op.catalogo.data });
  }
  const backupPath = await createBackup(backupDocs);
  const fecha = fechaLocal();

  for (const op of operaciones) {
    const existenciaRef = db.collection("existencias").doc(op.id);
    const catalogoRef = db.collection("catalogo_personalizado").doc(op.id);
    const movimientoRef = db.collection("movimientos").doc();

    await db.runTransaction(async (transaction) => {
      const snap = await transaction.get(existenciaRef);
      if (!snap.exists) throw new Error(`El documento desaparecio antes de actualizar: ${op.id}`);
      const data = snap.data() || {};
      const stockAnterior = numero(data.cantidad ?? data.stock_actual);
      const stockNuevo = stockAnterior + op.cantidad;

      transaction.set(existenciaRef, {
        cantidad: stockNuevo,
        stock_actual: stockNuevo,
        ultima_fecha: fecha,
        ultimo_solicitante: usuario,
      }, { merge: true });

      transaction.set(catalogoRef, {
        modulo,
        categoria: op.categoria,
        item,
        referencia: op.referencia,
        codigo_interno: op.codigo,
        unidad: op.unidad,
        ultima_fecha: fecha,
        ultimo_solicitante: usuario,
      }, { merge: true });

      transaction.set(movimientoRef, {
        fecha,
        modulo,
        tipoMovimiento: "Entrada",
        tipo: "Entrada",
        item,
        descripcion: item,
        referencia: op.referencia,
        codigoInterno: op.codigo,
        codigo_interno: op.codigo,
        cantidad: op.cantidad,
        unidad: op.unidad,
        usuario,
        solicitante: usuario,
        observaciones,
        documento_id: op.id,
        producto_id: op.id,
        categoria: op.categoria,
        stock_anterior: stockAnterior,
        stock_nuevo: stockNuevo,
      });
    });
  }

  const verify = [];
  for (const op of operaciones) {
    const doc = await db.collection("existencias").doc(op.id).get();
    const data = doc.data() || {};
    verify.push({
      id: doc.id,
      codigo: codigoDoc(data, doc.id),
      item: data.item,
      referencia: data.referencia,
      cantidad: data.cantidad,
      stock_actual: data.stock_actual,
      unidad: data.unidad,
    });
  }

  console.log(JSON.stringify({ backupPath, escritos: verify.length, verify }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

