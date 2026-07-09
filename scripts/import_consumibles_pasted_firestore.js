#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const rootDir = path.resolve(__dirname, "..");
const defaultInputPath = "C:/Users/Almacen/.codex/attachments/ea2b3f42-bb07-45f0-a277-fc26e5d48c73/pasted-text.txt";
const firebaseRcPath = path.join(rootDir, ".firebaserc");
const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const inputArg = args.find((arg) => arg.startsWith("--file="));
const inputPath = inputArg ? inputArg.slice("--file=".length) : defaultInputPath;

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
    .toUpperCase()
    .replace(/\s+/g, " ")
    .trim();
}

function cleanText(value) {
  return String(value || "")
    .trim()
    .replace(/\s+/g, " ")
    .toUpperCase()
    .replace(/\bCOGINETE\b/g, "COJINETE")
    .replace(/\bEXPANCIVO\b/g, "EXPANSIVO")
    .replace(/\bVAREILLA\b/g, "VARILLA")
    .replace(/\bCOMBUSTIBE\b/g, "COMBUSTIBLE")
    .replace(/\bEMPACAQUE\b/g, "EMPAQUE")
    .replace(/\bGALVANIZASA\b/g, "GALVANIZADA")
    .replace(/\bGALBANIZADO\b/g, "GALVANIZADO")
    .replace(/\bHEMPBRA\b/g, "HEMBRA")
    .replace(/\bCORRTINA\b/g, "CORTINA")
    .replace(/\bHIDRANDE\b/g, "HIDRANTE");
}

function cleanCode(value) {
  return String(value || "").trim().toUpperCase().replace(/\s+/g, "");
}

function parseQuantity(value) {
  const text = String(value || "").replace(",", ".").trim();
  if (!text) return 0;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : 0;
}

function codeLocation(code) {
  const match = /^([A-Z])-P(\d+)-/.exec(code);
  if (!match) return "";
  return `Estanteria ${match[1]} - Piso ${match[2]}`;
}

function codePrefix(code) {
  const match = /^([A-Z]-P\d+)-(\d+)$/.exec(code);
  return match ? match[1] : "";
}

function codeNumber(code) {
  const match = /^[A-Z]-P\d+-(\d+)$/.exec(code);
  return match ? Number(match[1]) : 0;
}

function codeWidth(code) {
  const match = /^[A-Z]-P\d+-(\d+)$/.exec(code);
  return match ? match[1].length : 3;
}

function inferPrefixForBlank(itemRaw) {
  const item = normalizeText(itemRaw);
  if (item.includes("LLAVE VALVULA PVC") || item.includes("UNION T PVC")) return "D-P2";
  if (item.includes("UNION HEMBRA/ROSCA MACHO PVC")) return "D-P3";
  if (item.includes("BUJE REDUCTOR EN PVC")) return "D-P5";
  if (
    item === "UNION" ||
    item.includes("UNION HEMBRA/HEMBRA PVC") ||
    item.includes("TAPON PVC") ||
    item.includes("CODO 90") && item.includes("ROSCA")
  ) {
    return "D-P4";
  }
  return "D-P4";
}

function preferredWidthForBlank(prefix, itemRaw, knownCodes) {
  const item = normalizeText(itemRaw);
  if (prefix === "D-P5" && item.includes("BUJE REDUCTOR EN PVC")) {
    return Math.max(
      4,
      ...knownCodes.filter((code) => code.startsWith(`${prefix}-`)).map(codeWidth),
    );
  }
  return Math.max(
    3,
    ...knownCodes
      .filter((code) => code.startsWith(`${prefix}-`) && codeWidth(code) <= 3)
      .map(codeWidth),
  );
}

function inferCategory(itemRaw, refRaw, code) {
  const text = normalizeText(`${itemRaw} ${refRaw}`);
  if (/AMARRES|CABLE/.test(text)) return "Electricidad";
  if (
    code.startsWith("D-") ||
    /PVC|SANITARIO|RIEGO|GALVANIZ|ALUMINIO|LATON|MANGUERA|COLLARIN|ACOPLE|NIPLE|TAPON|BUJE|CODO|UNION|TEE|LLAVE|CHEQUE|PERA/.test(text)
  ) {
    return "PlomerÃ­a y Riego";
  }
  if (/FILTRO|BOMBA|COLADERA|SEPARATOR|FILTER/.test(text)) return "Filtros y Bombas";
  if (/SOBRE|PAPEL|ROLLO|EMPAQUE|VACIO/.test(text)) return "Oficina y Empaque";
  if (
    /NEUMATICO|RODAMIENTO|COJINETE|CHUMACERA|CRUCETA|RETENEDOR|BUJIA|EMBRAGUE|BANDA|PASTILLA|ARRASTRE|SELLO|PISTON|CILINDRO|SILENCIADOR|CARBURADOR|ARRANCADOR|ARRANQUE/.test(text)
  ) {
    return "MecÃ¡nica y Rodamientos";
  }
  return "FerreterÃ­a y TornillerÃ­a";
}

function inferSubcategory(itemRaw, refRaw) {
  const text = normalizeText(`${itemRaw} ${refRaw}`);
  if (/NEUMATICO|PARCHE|TUBELESS/.test(text)) return "Neumaticos y parches";
  if (/RODAMIENTO|COJINETE|CHUMACERA|CRUCETA/.test(text)) return "Rodamientos y chumaceras";
  if (/FILTRO|SEPARATOR|FILTER/.test(text)) return "Filtros";
  if (/BOMBA|COLADERA|PERA/.test(text)) return "Bombas y accesorios";
  if (/RETENEDOR|SELLO/.test(text)) return "Sellos y retenedores";
  if (/TORNILLO|PERNO|CHAZO|ESPARRAGO|MARIPOSA|PASADOR|CONECTOR/.test(text)) return "Tornilleria y fijacion";
  if (/PVC|SANITARIO|RIEGO|GALVANIZ|LATON|ALUMINIO|MANGUERA|COLLARIN|ACOPLE|NIPLE|TAPON|BUJE|CODO|UNION|TEE|LLAVE|CHEQUE/.test(text)) return "Plomeria y riego";
  if (/SOBRE|PAPEL|ROLLO|EMPAQUE/.test(text)) return "Empaque";
  return "General";
}

function parseRows() {
  if (!fs.existsSync(inputPath)) {
    throw new Error(`Archivo no encontrado: ${inputPath}`);
  }

  const lines = fs.readFileSync(inputPath, "utf8")
    .split(/\r?\n/)
    .filter((line) => line.trim() !== "");

  const body = lines.slice(1);
  const rawRows = [];
  const knownCodes = [];

  for (const line of body) {
    const cells = line.split("\t").map((cell) => cell.trim());
    const rawCode = cleanCode(cells[0]);
    const item = cleanText(cells[1]);
    const reference = cleanText(cells[2]);
    const unit = cleanText(cells[3]) || "UNIDAD";
    const quantity = parseQuantity(cells[4]);

    if (!item) continue;
    if (rawCode) knownCodes.push(rawCode);
    rawRows.push({
      sourceCode: rawCode,
      item,
      reference: reference || "N/A",
      unit,
      quantity,
      assigned: !rawCode,
    });
  }

  const maxByPrefix = new Map();
  const widthByPrefix = new Map();
  for (const code of knownCodes) {
    const prefix = codePrefix(code);
    if (!prefix) continue;
    maxByPrefix.set(prefix, Math.max(maxByPrefix.get(prefix) || 0, codeNumber(code)));
    widthByPrefix.set(prefix, Math.max(widthByPrefix.get(prefix) || 3, codeWidth(code)));
  }

  return rawRows.map((row) => {
    let code = row.sourceCode;
    if (!code) {
      const prefix = inferPrefixForBlank(row.item);
      const next = (maxByPrefix.get(prefix) || 0) + 1;
      maxByPrefix.set(prefix, next);
      const width = preferredWidthForBlank(prefix, row.item, knownCodes);
      widthByPrefix.set(prefix, Math.max(widthByPrefix.get(prefix) || 3, width));
      code = `${prefix}-${String(next).padStart(width, "0")}`;
      knownCodes.push(code);
    }

    const item = row.item === "UNION" && code.startsWith("D-P4-")
      ? "UNION PVC"
      : row.item;
    const category = inferCategory(item, row.reference, code);
    const subcategory = inferSubcategory(item, row.reference);
    const ubicacion = codeLocation(code);
    const nombreCompleto = [item, row.reference].filter(Boolean).join(" ");
    return {
      modulo: "Consumibles",
      categoria: category,
      subcategoria: subcategory,
      item,
      referencia: row.reference || "N/A",
      marca: "",
      cantidad: row.quantity,
      unidad: row.unit || "UNIDAD",
      codigo_interno: code,
      codigo_original: code,
      documento_id: code,
      producto_id: code,
      ubicacion,
      nombre_completo: nombreCompleto,
      busqueda: `${code} ${nombreCompleto} ${category} ${subcategory} ${ubicacion}`.toLowerCase(),
      activo: true,
      observaciones: row.assigned ? "Codigo asignado por importacion segun grupo y piso." : "",
      assigned: row.assigned,
    };
  });
}

function summarize(products) {
  const byCategory = new Map();
  const byPrefix = new Map();
  const codes = new Set();
  const duplicates = [];
  for (const product of products) {
    byCategory.set(product.categoria, (byCategory.get(product.categoria) || 0) + 1);
    const prefix = codePrefix(product.codigo_interno);
    byPrefix.set(prefix, (byPrefix.get(prefix) || 0) + 1);
    if (codes.has(product.codigo_interno)) duplicates.push(product.codigo_interno);
    codes.add(product.codigo_interno);
  }
  return { byCategory, byPrefix, duplicates };
}

async function commitInBatches(db, products) {
  const importedAt = new Date().toISOString();
  let batch = db.batch();
  let opCount = 0;
  let written = 0;

  async function flush(force = false) {
    if (opCount > 0 && (force || opCount >= 450)) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  }

  for (const product of products) {
    const code = product.codigo_interno;
    const baseData = {
      modulo: product.modulo,
      categoria: product.categoria,
      subcategoria: product.subcategoria,
      item: product.item,
      referencia: product.referencia,
      marca: product.marca,
      nombre_completo: product.nombre_completo,
      busqueda: product.busqueda,
      codigo_interno: product.codigo_interno,
      codigo_original: product.codigo_original,
      documento_id: product.documento_id,
      producto_id: product.producto_id,
      unidad: product.unidad,
      ubicacion: product.ubicacion,
      activo: true,
      ultima_fecha: importedAt,
      ultimo_solicitante: "Importador Consumibles",
      observaciones: product.observaciones,
    };

    batch.set(db.collection("existencias").doc(code), {
      ...baseData,
      cantidad: product.cantidad,
    }, { merge: true });
    batch.set(db.collection("catalogo_personalizado").doc(code), baseData, { merge: true });
    opCount += 2;
    written += 1;
    await flush();
  }

  const assigned = products.filter((product) => product.assigned).map((product) => ({
    codigo: product.codigo_interno,
    item: product.item,
    referencia: product.referencia,
    ubicacion: product.ubicacion,
  }));
  batch.set(db.collection("importaciones").doc(`consumibles_${importedAt.replace(/[:.]/g, "-")}`), {
    tipo: "inventario_consumibles_pasted_text",
    archivo: inputPath,
    productos: products.length,
    cantidad_total: products.reduce((sum, product) => sum + product.cantidad, 0),
    codigos_asignados: assigned,
    fecha: importedAt,
  });
  opCount += 1;
  await flush(true);
  return written;
}

async function main() {
  const products = parseRows().sort((a, b) => a.codigo_interno.localeCompare(b.codigo_interno, "es", { numeric: true }));
  const { byCategory, byPrefix, duplicates } = summarize(products);
  const assigned = products.filter((product) => product.assigned);

  console.log(`Archivo: ${inputPath}`);
  console.log(`Productos Consumibles: ${products.length}`);
  console.log(`Cantidad total: ${products.reduce((sum, product) => sum + product.cantidad, 0)}`);
  console.log(`Codigos asignados: ${assigned.length}`);
  console.log(`Duplicados: ${duplicates.length ? duplicates.join(", ") : "ninguno"}`);
  console.log("\nCategorias:");
  [...byCategory.entries()].sort().forEach(([category, count]) => console.log(`- ${category}: ${count}`));
  console.log("\nPrefijos:");
  [...byPrefix.entries()].sort().forEach(([prefix, count]) => console.log(`- ${prefix}: ${count}`));
  console.log("\nCodigos nuevos:");
  assigned.forEach((product) => console.log(`- ${product.codigo_interno}: ${product.item} ${product.referencia} (${product.ubicacion}) = ${product.cantidad} ${product.unidad}`));

  if (!apply) {
    console.log("\nVista previa solamente. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  admin.initializeApp({
    credential: fs.existsSync(credentialPath)
      ? admin.credential.cert(require(credentialPath))
      : admin.credential.applicationDefault(),
    projectId: projectIdFromFirebaseRc(),
  });

  const written = await commitInBatches(admin.firestore(), products);
  console.log(`\nImportacion completada: ${written} productos escritos en Firestore.`);
}

main().catch((error) => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});

