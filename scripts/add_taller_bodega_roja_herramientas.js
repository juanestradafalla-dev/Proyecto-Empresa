const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
const rootDir = path.resolve(__dirname, "..");
const apply = process.argv.includes("--apply");
const submodulo = "BODEGA ROJA";
const categoria = "Herramientas";
const codigoPrefix = "SINQR-BR-HER";

const nuevos = [
  { nombre: "TIJERAS PODA", tipo: "TIJERA", cantidad: 9 },
  { nombre: "PALUSTRE", tipo: "PALUSTRE", cantidad: 5 },
  { nombre: "SONDA METALICA", tipo: "SONDA", material: "METALICA", cantidad: 1 },
  { nombre: "ESCUADRA TOTAL 90", tipo: "ESCUADRA", marca: "TOTAL", tamano: "90", cantidad: 1 },
  { nombre: "TIZALINA KIT", tipo: "KIT", cantidad: 1 },
  { nombre: "DISCO CORTE", tipo: "DISCO", referencia: "A60T-BF", cantidad: 7 },
  { nombre: "DISCO AFILAR CIRCULAR", tipo: "DISCO", cantidad: 2 },
  { nombre: "DISCO DEVASTAR", tipo: "DISCO", cantidad: 10 },
  { nombre: "DISCO FLAP", tipo: "DISCO", cantidad: 8 },
  { nombre: "DISCO MULTIPROPOSITO", tipo: "DISCO", cantidad: 1 },
  { nombre: "DISCO ALAMBRE CONO", tipo: "DISCO", cantidad: 1 },
  { nombre: "DISCO ALAMBRE PLATO", tipo: "DISCO", cantidad: 1 },
  { nombre: "PUNTERO", tipo: "PUNTERO", tamano: "10\"", cantidad: 2 },
  { nombre: "BROCA DENTADA MADERA", tipo: "BROCA", tamano: "3/4\"X19MM", cantidad: 1 },
  { nombre: "BRUNIDORA PARA CILINDROS DE MOTOR", tipo: "BRUNIDORA", cantidad: 1 },
  { nombre: "FRESAS ROTATIVAS KIT", tipo: "KIT", cantidad: 1 },
  { nombre: "GRAPADORA", tipo: "GRAPADORA", marca: "STANLEY", cantidad: 1 },
  { nombre: "PINZA MASA", tipo: "PINZA", cantidad: 1 },
  { nombre: "BOQUILLA GAS PROPANO", tipo: "BOQUILLA", referencia: "PROPANO", cantidad: 1 },
  { nombre: "LIMA CIRCULAR", tipo: "LIMA", cantidad: 2 },
  { nombre: "SEGUETA MANUAL", tipo: "SEGUETA", cantidad: 1 },
  { nombre: "SERRUCHO PODA", tipo: "SERRUCHO", cantidad: 1 },
  { nombre: "LLAVE DE BANDA", tipo: "LLAVE", cantidad: 1 },
  { nombre: "BROCA MADERA KIT", tipo: "KIT", cantidad: 1 },
  { nombre: "PONCHADORA", tipo: "PONCHADORA", cantidad: 1 },
  { nombre: "CONECTOR COAXIAL", tipo: "CONECTOR", cantidad: 1 },
  { nombre: "COPA", tipo: "COPA", tamano: "1/2\"", cantidad: 1 },
  { nombre: "LLAVE", tipo: "LLAVE", tamano: "7/16\"", cantidad: 1, observaciones: "Cantidad asumida 1 porque no fue indicada en la solicitud." },
  { nombre: "LLAVE", tipo: "LLAVE", tamano: "30", cantidad: 4 },
  { nombre: "LLAVE INGLESA", tipo: "LLAVE", cantidad: 1 },
  { nombre: "LLAVE", tipo: "LLAVE", tamano: "7/8\"", cantidad: 1, observaciones: "Cantidad asumida 1 porque no fue indicada en la solicitud." },
  { nombre: "HOMBRE SOLO", tipo: "PINZA", cantidad: 1 },
  { nombre: "MANDO COPA FLEXIBLE", tipo: "MANDO COPA", tamano: "1/2", cantidad: 1 },
  { nombre: "MANDO COPA RIGIDO", tipo: "MANDO COPA", tamano: "1/2", cantidad: 3 },
  { nombre: "CALIBRADOR NEUMATICO", tipo: "CALIBRADOR", cantidad: 1 },
  { nombre: "ENGRASADORA", tipo: "ENGRASADORA", cantidad: 1 },
  { nombre: "SERRUCHO", tipo: "SERRUCHO", cantidad: 6, observaciones: "Cantidad consolidada desde SERRUCHO 1 y SERRUCHO 5." },
  { nombre: "CUCHILLA SERRUCHO", tipo: "CUCHILLA", cantidad: 1 },
  { nombre: "LLANA LIZA", tipo: "LLANA", cantidad: 1 },
  { nombre: "RODILLO MEDIANO", tipo: "RODILLO", tamano: "MEDIANO", cantidad: 1 },
  { nombre: "REGLETA BALDOSA", tipo: "REGLETA", cantidad: 1 },
  { nombre: "PASACABLES", tipo: "PASACABLES", cantidad: 1 },
  { nombre: "COPA", tipo: "COPA", tamano: "19MM", cantidad: 1, observaciones: "Cantidad asumida 1 porque no fue indicada en la solicitud." },
  { nombre: "PALITA PLASTICO", tipo: "PALITA", tamano: "GRANDE", material: "PLASTICO", cantidad: 5 },
  { nombre: "PALITA PLASTICO", tipo: "PALITA", tamano: "PEQUENO", material: "PLASTICO", cantidad: 1 },
  { nombre: "PALA METALICA", tipo: "PALA", material: "METALICA", cantidad: 11 },
  { nombre: "PALIN METALICO", tipo: "PALIN", material: "METALICO", cantidad: 6 },
  { nombre: "TARPALA METALICA", tipo: "TARPALA", material: "METALICA", cantidad: 8 },
  { nombre: "AZADON METALICO", tipo: "AZADON", material: "METALICO", cantidad: 3 },
  { nombre: "PALA ANCHA METALICA", tipo: "PALA", tamano: "ANCHA", material: "METALICA", cantidad: 3 },
  { nombre: "PALADRAGA METALICA", tipo: "PALADRAGA", material: "METALICA", cantidad: 1 },
  { nombre: "BARRA SIMPLE", tipo: "BARRA", cantidad: 1 },
  { nombre: "RASTRILLO METALICO", tipo: "RASTRILLO", material: "METALICO", cantidad: 1 },
  { nombre: "HOYADORES", tipo: "HOYADOR", cantidad: 3 },
  { nombre: "PALA JARDINERO", tipo: "PALA", cantidad: 1 },
  { nombre: "CABEZA BARRA", tipo: "REPUESTO", cantidad: 2 },
  { nombre: "CABEZA AZADON", tipo: "REPUESTO", cantidad: 1 },
  { nombre: "CABEZA PALA", tipo: "REPUESTO", cantidad: 8 },
  { nombre: "TIJERAS GRANDES PODA", tipo: "TIJERA", tamano: "GRANDE", cantidad: 5 },
  { nombre: "MACHETE", tipo: "MACHETE", cantidad: 26 },
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
    normalizar(data.referencia_detalle || data.modelo || ""),
  ].join("|");
}

function referenciaProducto(producto) {
  return producto.referencia || producto.tamano || categoria;
}

function buildData(producto, codigo, fecha) {
  const observaciones = [
    producto.observaciones || "",
    "Alta manual en Bodega Roja / Herramientas sin QR asignado.",
  ].filter(Boolean).join(" ");
  return {
    modulo: "Taller",
    submodulo_taller: submodulo,
    categoria: submodulo,
    subcategoria: categoria,
    nombre: producto.nombre,
    tipo: producto.tipo || "",
    tamano: producto.tamano || "",
    marca: producto.marca || "",
    color: "",
    modelo: producto.referencia || "",
    uso: categoria,
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
    referencia: referenciaProducto(producto),
    observaciones,
    importado_script: true,
    fuente_importacion: "alta_manual_bodega_roja_herramientas",
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
  const backupPath = path.join(backupDir, `taller-bodega-roja-herramientas-backup-${stamp}.json`);
  fs.writeFileSync(
    backupPath,
    JSON.stringify(
      {
        accion: "Alta manual Taller Bodega Roja Herramientas",
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
      referencia_detalle: producto.referencia || "",
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

