const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");

const modulo = "DotaciÃ³n";
const item = "ZAPATILLA ANTI DEZLISANTE";
const categoria = "Calzado";
const unidad = "Par";
const usuario = "Correccion manual Dotacion";
const observacionEntradaIncorrecta = "Entrada manual de zapatillas antideslizante por tallas.";
const observacionAjuste = "Correccion: reemplazo de saldo de zapatillas antideslizante por inventario informado.";

const saldosObjetivo = [
  { talla: "40", cantidad: 0 },
  { talla: "38", cantidad: 1 },
  { talla: "39", cantidad: 1 },
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

async function createBackup({ productos, catalogo, movimientos }) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `dotacion-zapatillas-reemplazo-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Reemplazo saldo Dotacion Zapatilla antideslizante",
        fecha: new Date().toISOString(),
        existencias: productos.map((doc) => ({ id: doc.id, data: doc.data })),
        catalogo_personalizado: catalogo.map((doc) => ({ id: doc.id, data: doc.data })),
        movimientos_borrados: movimientos.map((doc) => ({ id: doc.id, data: doc.data })),
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
  const [existencias, catalogo, movimientosSnap] = await Promise.all([
    readCollection(db, "existencias"),
    readCollection(db, "catalogo_personalizado"),
    db.collection("movimientos").where("modulo", "==", modulo).get(),
  ]);

  const movimientos = [];
  movimientosSnap.forEach((doc) => {
    const data = doc.data();
    if (String(data.observaciones || "") === observacionEntradaIncorrecta) {
      movimientos.push({ id: doc.id, ref: doc.ref, data });
    }
  });

  const operaciones = saldosObjetivo.map((objetivo) => {
    const existencia = findProducto(existencias, objetivo.talla);
    if (!existencia) throw new Error(`No encontre ${item} talla ${objetivo.talla}`);
    const catalogoDoc = catalogo.find((doc) => doc.id === existencia.id);
    const anterior = numero(existencia.data.cantidad ?? existencia.data.stock_actual);
    return {
      ...objetivo,
      id: existencia.id,
      codigo: codigoDoc(existencia.data, existencia.id),
      referencia: existencia.data.referencia || `Talla: ${objetivo.talla} | Zapatilla antideslizante`,
      anterior,
      nuevo: objetivo.cantidad,
      existencia,
      catalogo: catalogoDoc,
    };
  });

  console.log(JSON.stringify({
    apply,
    reemplazos: operaciones.map((op) => ({
      id: op.id,
      codigo: op.codigo,
      talla: op.talla,
      anterior: op.anterior,
      nuevo: op.nuevo,
    })),
    movimientos_entrada_a_borrar: movimientos.map((mov) => ({
      id: mov.id,
      codigo: mov.data.codigoInterno,
      cantidad: mov.data.cantidad,
      stock_anterior: mov.data.stock_anterior,
      stock_nuevo: mov.data.stock_nuevo,
    })),
  }, null, 2));

  if (!apply) {
    console.log("\nVista previa. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  const backupPath = await createBackup({
    productos: operaciones.map((op) => op.existencia),
    catalogo: operaciones.map((op) => op.catalogo).filter(Boolean),
    movimientos,
  });

  const fecha = fechaLocal();
  const batch = db.batch();

  for (const mov of movimientos) {
    batch.delete(mov.ref);
  }

  for (const op of operaciones) {
    const existenciaRef = db.collection("existencias").doc(op.id);
    const catalogoRef = db.collection("catalogo_personalizado").doc(op.id);
    batch.set(existenciaRef, {
      cantidad: op.nuevo,
      stock_actual: op.nuevo,
      ultima_fecha: fecha,
      ultimo_solicitante: usuario,
    }, { merge: true });
    batch.set(catalogoRef, {
      modulo,
      categoria,
      item,
      referencia: op.referencia,
      codigo_interno: op.codigo,
      unidad,
      ultima_fecha: fecha,
      ultimo_solicitante: usuario,
    }, { merge: true });
    if (op.anterior !== op.nuevo) {
      batch.set(db.collection("movimientos").doc(), {
        fecha,
        modulo,
        tipoMovimiento: "Ajuste stock",
        tipo: "Ajuste stock",
        item,
        descripcion: item,
        referencia: op.referencia,
        codigoInterno: op.codigo,
        codigo_interno: op.codigo,
        cantidad: op.nuevo - op.anterior,
        unidad,
        usuario,
        solicitante: usuario,
        observaciones: observacionAjuste,
        documento_id: op.id,
        producto_id: op.id,
        categoria,
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

  const movVerifySnap = await db.collection("movimientos").where("modulo", "==", modulo).get();
  let entradasIncorrectasRestantes = 0;
  let ajustesCreados = 0;
  movVerifySnap.forEach((doc) => {
    const data = doc.data();
    if (String(data.observaciones || "") === observacionEntradaIncorrecta) entradasIncorrectasRestantes += 1;
    if (String(data.observaciones || "") === observacionAjuste) ajustesCreados += 1;
  });

  console.log(JSON.stringify({
    backupPath,
    escritos: verify.length,
    entradas_incorrectas_restantes: entradasIncorrectasRestantes,
    ajustes_stock_total_con_esta_observacion: ajustesCreados,
    verify,
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

