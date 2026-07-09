const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");

const modulo = "DotaciÃ³n";
const itemExistente = "ZAPATILLA ANTI DEZLISANTE";
const referenciaBase = "Zapatilla antideslizante";
const categoria = "Calzado";
const unidad = "Par";
const marca = "Institucional";
const observacionesProducto = "Parte: Pies";
const usuario = "Actualizacion manual Dotacion";
const observacionesMovimiento = "Entrada manual de zapatillas antideslizante por tallas.";

const entradasRaw = [
  { talla: "40", cantidad: 0 },
  { talla: "38", cantidad: 1 },
  { talla: "39", cantidad: 1 },
  { talla: "38", cantidad: 1 },
  { talla: "36", cantidad: 3 },
  { talla: "35", cantidad: 1 },
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

function referenciaTalla(talla) {
  return `Talla: ${talla} | ${referenciaBase}`;
}

function nombreCompleto(talla) {
  return `${itemExistente} ${referenciaTalla(talla)}`;
}

function findProducto(docs, talla) {
  return docs.find((doc) => {
    const data = doc.data;
    return esDotacion(data) &&
      normalizar(data.item || data.producto) === normalizar(itemExistente) &&
      tallaDesdeReferencia(data.referencia) === String(talla);
  });
}

function nextDotCode(docs, reservedCodes) {
  let max = 0;
  const used = new Set(reservedCodes);
  for (const doc of docs) {
    const code = codigoDoc(doc.data, doc.id);
    used.add(code);
    const match = /^DOT-(\d+)$/.exec(code);
    if (match) max = Math.max(max, Number(match[1]) || 0);
  }

  while (true) {
    max += 1;
    const code = `DOT-${String(max).padStart(3, "0")}`;
    if (!used.has(code)) return code;
  }
}

function buildBaseData({ codigo, talla, cantidad, fecha, includeCantidad }) {
  const base = {
    modulo,
    categoria,
    item: itemExistente,
    referencia: referenciaTalla(talla),
    marca,
    nombre_completo: nombreCompleto(talla),
    busqueda: `${codigo} ${nombreCompleto(talla)}`.toLowerCase(),
    codigo_interno: codigo,
    unidad,
    ultima_fecha: fecha,
    ultimo_solicitante: usuario,
    observaciones: observacionesProducto,
  };
  if (includeCantidad) {
    base.cantidad = cantidad;
    base.stock_actual = cantidad;
  }
  return base;
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
  const backupPath = path.join(backupDir, `dotacion-zapatillas-antideslizante-entrada-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Entrada manual Dotacion Zapatilla antideslizante",
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

function consolidarEntradas() {
  const map = new Map();
  for (const entrada of entradasRaw) {
    const talla = String(entrada.talla);
    map.set(talla, (map.get(talla) || 0) + numero(entrada.cantidad));
  }
  return [...map.entries()].map(([talla, cantidad]) => ({ talla, cantidad }));
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
  const reservedCodes = new Set();
  const entradas = consolidarEntradas();

  const operaciones = entradas.map((entrada) => {
    const existencia = findProducto(existencias, entrada.talla);
    const catalogoDoc = existencia ? catalogo.find((doc) => doc.id === existencia.id) : null;
    const anterior = existencia ? numero(existencia.data.cantidad ?? existencia.data.stock_actual) : 0;
    const nuevo = anterior + entrada.cantidad;
    const codigo = existencia ? codigoDoc(existencia.data, existencia.id) : nextDotCode(allDocs, reservedCodes);
    reservedCodes.add(codigo);

    return {
      ...entrada,
      id: existencia?.id || codigo,
      codigo,
      referencia: existencia?.data.referencia || referenciaTalla(entrada.talla),
      anterior,
      nuevo,
      existencia,
      catalogo: catalogoDoc,
      crear: !existencia,
    };
  });

  console.log(JSON.stringify({
    apply,
    operaciones: operaciones.map((op) => ({
      accion: op.crear ? "crear" : "sumar",
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
    if (op.existencia) backupDocs.push({ coleccion: "existencias", id: op.existencia.id, data: op.existencia.data });
    if (op.catalogo) backupDocs.push({ coleccion: "catalogo_personalizado", id: op.catalogo.id, data: op.catalogo.data });
  }
  const backupPath = await createBackup(backupDocs);
  const fecha = fechaLocal();

  for (const op of operaciones) {
    const existenciaRef = db.collection("existencias").doc(op.id);
    const catalogoRef = db.collection("catalogo_personalizado").doc(op.id);

    await db.runTransaction(async (transaction) => {
      const snap = await transaction.get(existenciaRef);
      const stockAnterior = snap.exists ? numero((snap.data() || {}).cantidad ?? (snap.data() || {}).stock_actual) : 0;
      const stockNuevo = stockAnterior + op.cantidad;

      if (op.crear) {
        transaction.set(existenciaRef, buildBaseData({
          codigo: op.codigo,
          talla: op.talla,
          cantidad: stockNuevo,
          fecha,
          includeCantidad: true,
        }), { merge: true });
      } else {
        transaction.set(existenciaRef, {
          cantidad: stockNuevo,
          stock_actual: stockNuevo,
          ultima_fecha: fecha,
          ultimo_solicitante: usuario,
        }, { merge: true });
      }

      transaction.set(catalogoRef, buildBaseData({
        codigo: op.codigo,
        talla: op.talla,
        cantidad: stockNuevo,
        fecha,
        includeCantidad: false,
      }), { merge: true });

      if (op.cantidad > 0) {
        transaction.set(db.collection("movimientos").doc(), {
          fecha,
          modulo,
          tipoMovimiento: "Entrada",
          tipo: "Entrada",
          item: itemExistente,
          descripcion: itemExistente,
          referencia: op.referencia,
          codigoInterno: op.codigo,
          codigo_interno: op.codigo,
          cantidad: op.cantidad,
          unidad,
          usuario,
          solicitante: usuario,
          observaciones: observacionesMovimiento,
          documento_id: op.id,
          producto_id: op.id,
          categoria,
          stock_anterior: stockAnterior,
          stock_nuevo: stockNuevo,
        });
      }
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

