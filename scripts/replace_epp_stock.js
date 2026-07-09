const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");

const modulo = "EPP";
const usuario = "Correccion manual EPP";
const observacionAjuste = "Correccion: reemplazo de saldo EPP por inventario informado.";

const objetivos = [
  { id: "PM12", cantidad: 13, match: "GUANTE ANTICORTE", unidad: "Par" },
  { id: "PR05", cantidad: 11, match: "TAPABOCAS DESECHABLE", unidad: "Unidad" },
  { id: "PR02", cantidad: 1, match: "FILTROS PARA MASCARA 6003", unidad: "Par" },
  { id: "PR01", cantidad: 2, match: "MASCARA RESPIRADOR", unidad: "Unidad", referencia: "Unidad" },
  { id: "PF03", cantidad: 0, match: "VISOR MALLA GUADANA", unidad: "Unidad" },
  { id: "PC10", cantidad: 2, match: "OVEROL ANTIFUIDO INDUSTRIAL TXL", unidad: "Unidad" },
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

async function readDocs(db, collectionName) {
  const docs = [];
  for (const objetivo of objetivos) {
    const ref = db.collection(collectionName).doc(objetivo.id);
    const snap = await ref.get();
    if (snap.exists) docs.push({ id: snap.id, ref, data: snap.data() });
  }
  return docs;
}

async function createBackup({ existencias, catalogo }) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `epp-reemplazo-stock-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Reemplazo saldo EPP",
        fecha: new Date().toISOString(),
        existencias: existencias.map((doc) => ({ id: doc.id, data: doc.data })),
        catalogo_personalizado: catalogo.map((doc) => ({ id: doc.id, data: doc.data })),
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
    readDocs(db, "existencias"),
    readDocs(db, "catalogo_personalizado"),
  ]);

  const operaciones = objetivos.map((objetivo) => {
    const doc = existencias.find((entry) => entry.id === objetivo.id);
    if (!doc) throw new Error(`No existe en existencias: ${objetivo.id}`);
    const data = doc.data;
    const texto = normalizar(`${data.item || ""} ${data.referencia || ""} ${data.categoria || ""}`);
    if (!texto.includes(normalizar(objetivo.match))) {
      throw new Error(`El documento ${objetivo.id} no coincide con ${objetivo.match}: ${data.item || ""}`);
    }
    const anterior = numero(data.cantidad ?? data.stock_actual);
    return {
      ...objetivo,
      doc,
      catalogo: catalogo.find((entry) => entry.id === objetivo.id),
      codigo: codigoDoc(data, objetivo.id),
      item: data.item || data.producto || objetivo.match,
      categoria: data.categoria || "",
      referenciaAnterior: data.referencia || "",
      referenciaNueva: objetivo.referencia || data.referencia || objetivo.unidad || "",
      unidadNueva: objetivo.unidad || data.unidad || "Unidad",
      anterior,
      nuevo: objetivo.cantidad,
    };
  });

  console.log(JSON.stringify({
    apply,
    reemplazos: operaciones.map((op) => ({
      id: op.id,
      codigo: op.codigo,
      item: op.item,
      anterior: op.anterior,
      nuevo: op.nuevo,
      unidad: op.unidadNueva,
      referencia: op.referenciaNueva,
    })),
  }, null, 2));

  if (!apply) {
    console.log("\nVista previa. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  const backupPath = await createBackup({ existencias, catalogo });
  const fecha = fechaLocal();
  const batch = db.batch();

  for (const op of operaciones) {
    const existenciaRef = db.collection("existencias").doc(op.id);
    const catalogoRef = db.collection("catalogo_personalizado").doc(op.id);
    const patch = {
      modulo,
      categoria: op.categoria,
      item: op.item,
      referencia: op.referenciaNueva,
      codigo_interno: op.codigo,
      unidad: op.unidadNueva,
      ultima_fecha: fecha,
      ultimo_solicitante: usuario,
    };
    batch.set(existenciaRef, {
      ...patch,
      cantidad: op.nuevo,
      stock_actual: op.nuevo,
    }, { merge: true });
    batch.set(catalogoRef, patch, { merge: true });

    if (op.anterior !== op.nuevo || op.referenciaAnterior !== op.referenciaNueva) {
      batch.set(db.collection("movimientos").doc(), {
        fecha,
        modulo,
        tipoMovimiento: "Ajuste stock",
        tipo: "Ajuste stock",
        item: op.item,
        descripcion: op.item,
        referencia: op.referenciaNueva,
        codigoInterno: op.codigo,
        codigo_interno: op.codigo,
        cantidad: op.nuevo - op.anterior,
        unidad: op.unidadNueva,
        usuario,
        solicitante: usuario,
        observaciones: observacionAjuste,
        documento_id: op.id,
        producto_id: op.id,
        categoria: op.categoria,
        stock_anterior: op.anterior,
        stock_nuevo: op.nuevo,
      });
    }
  }

  await batch.commit();

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

