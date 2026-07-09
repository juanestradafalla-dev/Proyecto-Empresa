const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");
const submodulo = "BODEGA ROJA";
const categoria = "Apiario";
const codigoPrefix = "SINQR-BR-API";

const nuevos = [
  { nombre: "SOPORTE ALAMBRE", tipo: "SOPORTE", cantidad: 53 },
  { nombre: "TAPA METALICA", tipo: "TAPA", material: "METALICA", cantidad: 28 },
  { nombre: "CAJA GRANDE", tipo: "CAJA", tamano: "GRANDE", cantidad: 9 },
  { nombre: "CAJA MEDIANA", tipo: "CAJA", tamano: "MEDIANA", cantidad: 79 },
  { nombre: "TAPA MADERA GRANDE", tipo: "TAPA MADERA", tamano: "GRANDE", material: "MADERA", cantidad: 7 },
  { nombre: "TAPA MADERA MEDIANA", tipo: "TAPA MADERA", tamano: "MEDIANA", material: "MADERA", cantidad: 41 },
  { nombre: "CAJA PEQUENA CON SOPORTES", tipo: "CAJA", tamano: "PEQUENA", cantidad: 4, observaciones: "Incluye soportes x4." },
  { nombre: "CENTRIFUGA", tipo: "EQUIPO APIARIO", cantidad: 1 },
  { nombre: "AHUMADORES", tipo: "AHUMADOR", cantidad: 3 },
];

function normalizar(valor) {
  return String(valor || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, " ")
    .trim();
}

function claveDocumento(codigo) {
  return codigo
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function codigoDoc(data, fallbackId) {
  return String(data.codigo_principal || data.codigo || data.codigo_qr || data.codigo_interno || fallbackId || "")
    .trim()
    .toUpperCase();
}

function esTaller(data) {
  const modulo = normalizar(data.modulo);
  return !modulo || modulo === "TALLER" || modulo === "HERRAMIENTAS" || modulo.includes("TALLER");
}

function nextCodes(existingDocs, amount) {
  const used = new Set();
  let max = 0;
  for (const doc of existingDocs) {
    const code = codigoDoc(doc.data, doc.id);
    used.add(code);
    const match = new RegExp(`^${codigoPrefix}-(\\d+)$`).exec(code);
    if (match) max = Math.max(max, Number(match[1]) || 0);
  }

  const codes = [];
  while (codes.length < amount) {
    max += 1;
    const code = `${codigoPrefix}-${String(max).padStart(3, "0")}`;
    if (!used.has(code)) {
      used.add(code);
      codes.push(code);
    }
  }
  return codes;
}

function claveProducto(data) {
  return [
    normalizar(data.submodulo_taller || data.submodulo || data.categoria || data.ubicacion),
    normalizar(data.subcategoria || data.referencia),
    normalizar(data.nombre || data.item || data.producto || data.descripcion),
    normalizar(data.tamano),
  ].join("|");
}

function buildData(producto, codigo, fecha) {
  const observaciones = [
    producto.observaciones || "",
    "Alta manual en Bodega Roja / Apiario sin QR asignado.",
  ].filter(Boolean).join(" ");
  return {
    modulo: "Taller",
    submodulo_taller: submodulo,
    categoria: submodulo,
    subcategoria: categoria,
    nombre: producto.nombre,
    tipo: producto.tipo || "",
    tamano: producto.tamano || "",
    marca: "",
    color: "",
    modelo: "",
    uso: "Apiario",
    rango: "",
    codigo,
    codigo_principal: codigo,
    codigo_qr: "",
    requiere_asignar_qr: true,
    cantidad_total: Number(producto.cantidad) || 0,
    cantidad_ocupada: 0,
    cantidad_disponible: Number(producto.cantidad) || 0,
    unidad: "UNIDAD",
    estado: "Disponible",
    ubicacion: submodulo,
    referencia: categoria,
    observaciones,
    importado_script: true,
    fuente_importacion: "alta_manual_bodega_roja_apiario",
    ultima_actualizacion: fecha,
    material: producto.material || "",
  };
}

async function readHerramientas(db) {
  const snap = await db.collection("herramientas").get();
  const docs = [];
  snap.forEach((doc) => docs.push({ id: doc.id, ref: doc.ref, data: doc.data() }));
  return docs;
}

async function createBackup(docs) {
  const backupDir = path.join(rootDir, "backups", "firestore");
  fs.mkdirSync(backupDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(backupDir, `taller-bodega-roja-apiario-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Alta manual Taller Bodega Roja Apiario",
        fecha: new Date().toISOString(),
        herramientas: docs.filter((doc) => esTaller(doc.data)).map((doc) => ({ id: doc.id, data: doc.data })),
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
    projectId: "arles-gestion",
  });
  const db = admin.firestore();
  const docs = await readHerramientas(db);

  const existentesPorClave = new Map(
    docs
      .filter((doc) => esTaller(doc.data))
      .map((doc) => [claveProducto(doc.data), doc]),
  );

  const yaExistentes = [];
  const pendientes = [];
  for (const producto of nuevos) {
    const clave = claveProducto({
      submodulo_taller: submodulo,
      subcategoria: categoria,
      nombre: producto.nombre,
      tamano: producto.tamano || "",
    });
    const existente = existentesPorClave.get(clave);
    if (existente) {
      yaExistentes.push({
        codigo: codigoDoc(existente.data, existente.id),
        nombre: producto.nombre,
        cantidad_actual: existente.data.cantidad_total,
      });
    } else {
      pendientes.push(producto);
    }
  }

  const codes = nextCodes(docs, pendientes.length);
  const fecha = new Date().toISOString();
  const plan = pendientes.map((producto, index) => ({
    codigo: codes[index],
    submodulo_taller: submodulo,
    categoria,
    ...producto,
  }));

  console.log(JSON.stringify({
    modo: apply ? "APLICAR" : "PREVIEW",
    herramientas_actuales: docs.length,
    nuevos_a_crear: plan.length,
    ya_existentes: yaExistentes,
    plan,
  }, null, 2));

  if (!apply) {
    console.log("\nPreview solamente. Ejecuta con --apply para escribir en Firestore.");
    await admin.app().delete();
    return;
  }

  const backupPath = await createBackup(docs);
  const batch = db.batch();
  for (const producto of plan) {
    const data = buildData(producto, producto.codigo, fecha);
    batch.set(db.collection("herramientas").doc(claveDocumento(producto.codigo)), data, { merge: true });
  }
  await batch.commit();

  const finalDocs = await readHerramientas(db);
  const creados = [];
  for (const producto of plan) {
    const doc = await db.collection("herramientas").doc(claveDocumento(producto.codigo)).get();
    creados.push({
      codigo: producto.codigo,
      existe: doc.exists,
      nombre: doc.get("nombre"),
      submodulo_taller: doc.get("submodulo_taller"),
      subcategoria: doc.get("subcategoria"),
      cantidad_total: doc.get("cantidad_total"),
      cantidad_disponible: doc.get("cantidad_disponible"),
      requiere_asignar_qr: doc.get("requiere_asignar_qr"),
    });
  }

  console.log(JSON.stringify({
    escrito: plan.length,
    backup: backupPath,
    herramientas_finales: finalDocs.length,
    codigos_creados: plan.map((producto) => producto.codigo),
    verificacion: creados,
  }, null, 2));

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

