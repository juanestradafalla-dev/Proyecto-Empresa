const fs = require("fs");
const path = require("path");

function loadFirebaseAdmin() {
  try {
    return require("firebase-admin");
  } catch (_) {
    return require("../shared/firebase-functions/node_modules/firebase-admin");
  }
}

const admin = loadFirebaseAdmin();

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");
const prefix = "SU";

const nuevos = [
  {
    categoria: "Mecanica y Rodamientos",
    subcategoria: "General",
    item: "GUAYA ACELERADOR",
    marca: "",
    referencia: "B45",
    cantidad: 2,
  },
  {
    categoria: "Mecanica y Rodamientos",
    subcategoria: "General",
    item: "GUAYA ACELERADOR",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 3,
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "JOHN DEERE",
    marca: "",
    referencia: "SJ32220",
    cantidad: 1,
  },
  {
    categoria: "Mecanica y Rodamientos",
    subcategoria: "General",
    item: "POLEA DENTADA",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 3,
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "TUBO SANITARIO",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 12,
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "GRIFO",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 2,
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "TARRO",
    marca: "GENERIC",
    referencia: "GALON",
    cantidad: 22,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "TARRO",
    marca: "GENERIC",
    referencia: "1/2 GALON",
    cantidad: 2,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "TARRO",
    marca: "GENERIC",
    referencia: "1/4 GALON",
    cantidad: 12,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "TARRO",
    marca: "GENERIC",
    referencia: "500MM",
    cantidad: 19,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "TAPA ROSCA TARRO",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 26,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "ALAMBRE PUA GALVANIZADO",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 2500,
    unidad: "Metro",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "ALAMBRE",
    marca: "GENERIC",
    referencia: "CALIBRE 11",
    cantidad: 140,
    unidad: "Kg",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "ALAMBRE",
    marca: "GENERIC",
    referencia: "CALIBRE 14",
    cantidad: 230,
    unidad: "Kg",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "ALAMBRE",
    marca: "GENERIC",
    referencia: "CALIBRE 19",
    cantidad: 30,
    unidad: "Kg",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "LONA BLANCA",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 108,
    unidad: "Unidad",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA REFORZADA",
    marca: "GENERIC",
    referencia: "1\"",
    cantidad: 100,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA REFORZADA",
    marca: "GENERIC",
    referencia: "1/2\"",
    cantidad: 100,
    unidad: "Metro",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "POLI SOMBRA",
    marca: "GENERIC",
    referencia: "VERDE 1X50MTS",
    cantidad: 2,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "POLI SOMBRA",
    marca: "GENERIC",
    referencia: "NEGRO 1.5X50MT",
    cantidad: 1,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "POLI SOMBRA",
    marca: "GENERIC",
    referencia: "NEGRO 2X50MT",
    cantidad: 7,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "MALLA PLASTICA",
    marca: "GENERIC",
    referencia: "BLANCA 1X25MT",
    cantidad: 2,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "FIBRA",
    marca: "GENERIC",
    referencia: "1X15MT",
    cantidad: 1,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "ROLLO PAPEL SELLO",
    marca: "GENERIC",
    referencia: "1X5MTS",
    cantidad: 1,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "ROLLO PAPEL SELLO",
    marca: "GENERIC",
    referencia: "2X2MTS",
    cantidad: 1,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "POLI CLIP",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 70,
    unidad: "Unidad",
  },
  {
    categoria: "Oficina y Empaque",
    subcategoria: "Empaque",
    item: "BOLSA PALMA",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 4500,
    unidad: "Unidad",
  },
  {
    categoria: "Mecanica y Rodamientos",
    subcategoria: "General",
    item: "PALOS PARA CARRETILLA",
    marca: "GENERIC",
    referencia: "GRANDE",
    cantidad: 4,
    unidad: "Par",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "TENSOR",
    marca: "GENERIC",
    referencia: "1/2\"",
    cantidad: 230,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "TENSOR",
    marca: "GENERIC",
    referencia: "3/8\"",
    cantidad: 167,
    unidad: "Unidad",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "TENSOR PERRO",
    marca: "GENERIC",
    referencia: "3/8\"",
    cantidad: 414,
    unidad: "Unidad",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA",
    marca: "GENERIC",
    referencia: "1 1/2\"",
    cantidad: 20,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA FLEXIBLE",
    marca: "GENERIC",
    referencia: "3\"",
    cantidad: 50,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA FLEXIBLE",
    marca: "GENERIC",
    referencia: "3/4\"",
    cantidad: 100,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA",
    marca: "GENERIC",
    referencia: "1\"",
    cantidad: 15,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA REFORZADA",
    marca: "GENERIC",
    referencia: "2\"",
    cantidad: 5,
    unidad: "Metro",
  },
  {
    categoria: "Plomeria y Riego",
    subcategoria: "Plomeria y riego",
    item: "MANGUERA ORUGA",
    marca: "GENERIC",
    referencia: "1/2\"",
    cantidad: 10,
    unidad: "Metro",
  },
  {
    categoria: "Ferreteria y Tornilleria",
    subcategoria: "General",
    item: "AMARRA TECHO",
    marca: "GENERIC",
    referencia: "N/A",
    cantidad: 120,
    unidad: "Unidad",
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
  return String(data.codigo_interno || data.codigoInterno || data.codigo || fallbackId || "")
    .trim()
    .toUpperCase();
}

function esConsumibleActivo(data) {
  return normalizar(data.modulo).includes("CONSUMIBLE") && data.activo !== false;
}

function etiquetaReferencia(producto) {
  const partes = [producto.marca, producto.referencia]
    .map((valor) => String(valor || "").trim())
    .filter((valor) => valor && normalizar(valor) !== "N A");
  return partes.join(" - ") || "N/A";
}

function nombreCompleto(producto) {
  return [producto.item, etiquetaReferencia(producto)]
    .filter((valor) => valor && normalizar(valor) !== "N A")
    .join(" ");
}

function claveProducto(producto) {
  return [producto.item, producto.marca, producto.referencia]
    .map(normalizar)
    .join("|");
}

async function readCollection(db, collectionName) {
  const snap = await db.collection(collectionName).get();
  const docs = [];
  snap.forEach((doc) => docs.push({ id: doc.id, ref: doc.ref, data: doc.data() }));
  return docs;
}

function nextCodes(existingDocs, amount) {
  let max = 0;
  const used = new Set();
  for (const doc of existingDocs) {
    const code = codigoDoc(doc.data, doc.id);
    used.add(code);
    const match = new RegExp(`^${prefix}-(\\d+)$`).exec(code);
    if (match) max = Math.max(max, Number(match[1]) || 0);
  }

  const codes = [];
  while (codes.length < amount) {
    max += 1;
    const code = `${prefix}-${String(max).padStart(3, "0")}`;
    if (!used.has(code)) {
      used.add(code);
      codes.push(code);
    }
  }
  return codes;
}

function buildData(producto, code, fecha) {
  const refVisible = etiquetaReferencia(producto);
  const nombre = nombreCompleto(producto);
  const busqueda = [
    code,
    producto.categoria,
    producto.subcategoria,
    producto.item,
    producto.marca,
    producto.referencia,
    nombre,
    "sin ubicacion",
  ].join(" ").toLowerCase();

  const base = {
    modulo: "Consumibles",
    categoria: producto.categoria,
    subcategoria: producto.subcategoria,
    item: producto.item,
    referencia: producto.referencia,
    marca: producto.marca,
    nombre_completo: nombre,
    busqueda,
    codigo_interno: code,
    codigo_original: code,
    codigo_excel: "",
    documento_id: code,
    producto_id: code,
    unidad: producto.unidad || "Unidad",
    ubicacion: "",
    activo: true,
    ultima_fecha: fecha,
    ultimo_solicitante: "Alta manual Consumibles",
    observaciones: "Alta manual sin estanteria asignada. Pendiente asignar ubicacion fisica.",
    fuente_importacion: "alta_manual_sin_ubicacion",
    fila_excel: "",
    stock_excel_original: "",
    codigo_generado: true,
    requiere_revision_stock: false,
    requiere_asignar_ubicacion: true,
    referencia_catalogo: refVisible,
  };

  return {
    existencias: {
      ...base,
      cantidad: Number(producto.cantidad) || 0,
      stock_actual: Number(producto.cantidad) || 0,
    },
    catalogo: base,
  };
}

async function createBackup(existingDocs) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `consumibles-sin-ubicacion-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Alta manual Consumibles sin ubicacion",
        fecha: new Date().toISOString(),
        existencias: existingDocs.existencias
          .filter((doc) => esConsumibleActivo(doc.data))
          .map((doc) => ({ id: doc.id, data: doc.data })),
        catalogo_personalizado: existingDocs.catalogo
          .filter((doc) => esConsumibleActivo(doc.data))
          .map((doc) => ({ id: doc.id, data: doc.data })),
      },
      null,
      2,
    ),
    "utf8",
  );
  return backupPath;
}

async function main() {
  if (!fs.existsSync(credentialPath)) {
    throw new Error(`No existe credencial Firebase: ${credentialPath}`);
  }

  admin.initializeApp({
    credential: admin.credential.cert(require(credentialPath)),
  });
  const db = admin.firestore();

  const [existencias, catalogo] = await Promise.all([
    readCollection(db, "existencias"),
    readCollection(db, "catalogo_personalizado"),
  ]);

  const existentesActivos = existencias.filter((doc) => esConsumibleActivo(doc.data));
  const existentesPorClave = new Map(
    existentesActivos.map((doc) => [
      claveProducto({
        item: doc.data.item,
        marca: doc.data.marca,
        referencia: doc.data.referencia,
      }),
      doc,
    ]),
  );

  const pendientes = [];
  const yaExistentes = [];
  for (const producto of nuevos) {
    const existente = existentesPorClave.get(claveProducto(producto));
    if (existente) {
      yaExistentes.push({
        codigo: codigoDoc(existente.data, existente.id),
        item: producto.item,
        marca: producto.marca,
        referencia: producto.referencia,
        cantidad_actual: existente.data.cantidad,
      });
    } else {
      pendientes.push(producto);
    }
  }

  const codigos = nextCodes([...existencias, ...catalogo], pendientes.length);
  const fecha = new Date().toISOString();
  const plan = pendientes.map((producto, index) => ({
    codigo: codigos[index],
    ...producto,
    etiqueta_referencia: etiquetaReferencia(producto),
    nombre_completo: nombreCompleto(producto),
  }));

  console.log(JSON.stringify(
    {
      modo: apply ? "APLICAR" : "PREVIEW",
      consumibles_activos_actuales: existentesActivos.length,
      nuevos_a_crear: plan.length,
      ya_existentes: yaExistentes,
      plan,
    },
    null,
    2,
  ));

  if (!apply) {
    console.log("\nPreview solamente. Ejecuta con --apply para escribir en Firestore.");
    await admin.app().delete();
    return;
  }

  const backupPath = await createBackup({ existencias, catalogo });
  const batch = db.batch();
  for (const producto of plan) {
    const data = buildData(producto, producto.codigo, fecha);
    batch.set(db.collection("existencias").doc(producto.codigo), data.existencias, { merge: true });
    batch.set(db.collection("catalogo_personalizado").doc(producto.codigo), data.catalogo, { merge: true });
  }
  await batch.commit();

  const verificacion = await db.collection("existencias").get();
  const activosFinal = [];
  verificacion.forEach((doc) => {
    const data = doc.data();
    if (esConsumibleActivo(data)) activosFinal.push({ id: doc.id, data });
  });

  console.log(JSON.stringify(
    {
      escrito: plan.length,
      backup: backupPath,
      consumibles_activos_finales: activosFinal.length,
      codigos_creados: plan.map((producto) => producto.codigo),
    },
    null,
    2,
  ));
  await admin.app().delete();
}

main().catch(async (error) => {
  console.error(error);
  try {
    await admin.app().delete();
  } catch (_) {
    // ignore cleanup errors
  }
  process.exit(1);
});

