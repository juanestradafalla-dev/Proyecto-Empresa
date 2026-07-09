#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const rootDir = path.resolve(__dirname, "..");
const csvPath = path.join(rootDir, "inventario_epp_organizado.csv");
const firebaseRcPath = path.join(rootDir, ".firebaserc");

const args = new Set(process.argv.slice(2));
const apply = args.has("--apply");
const modeArg = process.argv.find((arg) => arg.startsWith("--mode="));
const mode = modeArg ? modeArg.slice("--mode=".length).trim().toLowerCase() : "set";

if (!["set", "add"].includes(mode)) {
  throw new Error("Modo no valido. Usa --mode=set o --mode=add.");
}

function projectIdFromFirebaseRc() {
  try {
    const parsed = JSON.parse(fs.readFileSync(firebaseRcPath, "utf8"));
    return parsed.projects && parsed.projects.default;
  } catch (_) {
    return undefined;
  }
}

function parseCsvLine(line, separator) {
  const result = [];
  let current = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (char === "\"") {
      if (inQuotes && line[i + 1] === "\"") {
        current += "\"";
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === separator && !inQuotes) {
      result.push(current);
      current = "";
    } else {
      current += char;
    }
  }

  result.push(current);
  return result;
}

function normalizeHeader(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase();
}

function normalizeCode(value) {
  return String(value || "").trim().toUpperCase().replace(/[\s_-]+/g, "");
}

function resolveEppCategory(item, reference, current) {
  const text = `${item || ""} ${reference || ""}`.toUpperCase();
  if (/GUANTE|MITON|VAQUETA|NITRILO|LATEX|CAUCHO|GLOVE/.test(text)) return "ProtecciÃ³n Manual (Guantes)";
  if (/MASCARA|RESPIRADOR|FILTRO|TAPABOCA|KN95|MASK|RESPIRATOR/.test(text)) return "ProtecciÃ³n Respiratoria";
  if (/AUDITIVO|OIDO|TAPA OIDO|EARPLUG|HEARING/.test(text)) return "ProtecciÃ³n Auditiva";
  if (/GAFA|LENTE|MONOGAFA|VISION|VISUAL|GLASSES|LENS/.test(text)) return "ProtecciÃ³n Visual";
  if (/CASCO|CARETA|VISOR|COFIA|SOMBRERO|SAFARY|CASQUETE|PORTAVISOR|HELMET|HAT/.test(text)) return "ProtecciÃ³n Cabeza y Rostro";
  if (/CANILLERA|MANGA|POLAINA|DELANTAL|ARNES|CARNAZA|GUADAÃ‘ADOR|OVEROL|IMPERMEABLE|SLEEVE|APRON/.test(text)) return "Cuerpo y Extremidades";
  return current || "EPP";
}

function readProducts() {
  const linesBase = fs.readFileSync(csvPath, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() !== "");
  const lines = linesBase[0] && linesBase[0].trim().toLowerCase().includes("sep=")
    ? linesBase.slice(1)
    : linesBase;

  if (lines.length < 2) {
    throw new Error("El CSV no tiene filas para importar.");
  }

  const separator = (lines[0].match(/;/g) || []).length >= (lines[0].match(/,/g) || []).length ? ";" : ",";
  const headers = parseCsvLine(lines[0], separator).map(normalizeHeader);
  const index = (name) => headers.indexOf(normalizeHeader(name));
  const col = (fields, name) => {
    const i = index(name);
    return i >= 0 && i < fields.length ? fields[i].trim() : "";
  };

  return lines.slice(1).map((line, rowIndex) => {
    const fields = parseCsvLine(line, separator);
    const item = col(fields, "ITEM");
    const code = normalizeCode(col(fields, "CODIGO_INTERNO") || col(fields, "CODIGO INTERNO"));
    if (!item || !code) {
      throw new Error(`Fila ${rowIndex + 2}: falta ITEM o CODIGO_INTERNO.`);
    }

    const quantityText = col(fields, "CANTIDAD").replace(",", ".");
    const quantity = Number(quantityText);
    if (!Number.isFinite(quantity)) {
      throw new Error(`Fila ${rowIndex + 2}: cantidad invalida '${quantityText}'.`);
    }

    const modulo = col(fields, "MODULO") || "EPP";
    const reference = col(fields, "REFERENCIA");
    return {
      modulo,
      categoria: resolveEppCategory(item, reference, col(fields, "CATEGORIA")),
      item,
      referencia: reference,
      marca: col(fields, "MARCA"),
      cantidad: quantity,
      unidad: col(fields, "UNIDAD") || "Unidad",
      codigo_interno: code,
      observaciones: col(fields, "OBSERVACIONES"),
    };
  });
}

async function commitInBatches(db, products) {
  const importedAt = new Date().toISOString();
  let batch = db.batch();
  let opCount = 0;
  let written = 0;

  async function flushIfNeeded(force = false) {
    if (opCount > 0 && (force || opCount >= 450)) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  }

  for (const product of products) {
    const code = product.codigo_interno;
    const nombreCompleto = [product.item, product.marca, product.referencia].filter(Boolean).join(" ");
    const baseData = {
      modulo: product.modulo,
      categoria: product.categoria,
      item: product.item,
      referencia: product.referencia,
      marca: product.marca,
      nombre_completo: nombreCompleto,
      busqueda: `${code} ${nombreCompleto}`.toLowerCase(),
      codigo_interno: code,
      unidad: product.unidad,
      ultima_fecha: importedAt,
      ultimo_solicitante: "Importador EPP CSV",
      observaciones: product.observaciones,
    };

    const existenciaRef = db.collection("existencias").doc(code);
    const catalogoRef = db.collection("catalogo_personalizado").doc(code);
    const stockData = mode === "add"
      ? { ...baseData, cantidad: admin.firestore.FieldValue.increment(product.cantidad) }
      : { ...baseData, cantidad: product.cantidad };

    batch.set(existenciaRef, stockData, { merge: true });
    batch.set(catalogoRef, baseData, { merge: true });
    opCount += 2;
    written += 1;
    await flushIfNeeded();
  }

  const controlRef = db.collection("importaciones").doc(`epp_${importedAt.replace(/[:.]/g, "-")}`);
  batch.set(controlRef, {
    tipo: "inventario_epp_csv",
    archivo: path.basename(csvPath),
    modo: mode,
    productos: products.length,
    cantidad_total: products.reduce((sum, product) => sum + product.cantidad, 0),
    fecha: importedAt,
  });
  opCount += 1;

  await flushIfNeeded(true);
  return written;
}

async function main() {
  const products = readProducts();
  const codes = new Set();
  const duplicates = [];
  for (const product of products) {
    if (codes.has(product.codigo_interno)) duplicates.push(product.codigo_interno);
    codes.add(product.codigo_interno);
  }

  const total = products.reduce((sum, product) => sum + product.cantidad, 0);
  console.log(`Archivo: ${csvPath}`);
  console.log(`Productos EPP: ${products.length}`);
  console.log(`Cantidad total: ${total}`);
  console.log(`Codigos duplicados: ${duplicates.length ? duplicates.join(", ") : "ninguno"}`);
  console.log(`Modo: ${mode === "set" ? "dejar cantidad exacta del CSV" : "sumar cantidad al stock actual"}`);
  console.log("Muestra:");
  products.slice(0, 5).forEach((product) => {
    console.log(`- ${product.codigo_interno}: ${product.item} = ${product.cantidad} ${product.unidad}`);
  });

  if (!apply) {
    console.log("\nVista previa solamente. Ejecuta con --apply para escribir en Firestore.");
    return;
  }

  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: projectIdFromFirebaseRc() || "arles-gestion",
  });

  const written = await commitInBatches(admin.firestore(), products);
  console.log(`\nImportacion completada: ${written} productos escritos en Firestore.`);
}

main().catch((error) => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});

