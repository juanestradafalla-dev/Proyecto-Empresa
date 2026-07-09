#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const rootDir = path.resolve(__dirname, "..");
const csvPath = "C:/Users/Almacen/AndroidStudioProjects/GestionAndroid/Inventario Dotacion.csv";
const firebaseRcPath = path.join(rootDir, ".firebaserc");

const args = new Set(process.argv.slice(2));
const apply = args.has("--apply");

function projectIdFromFirebaseRc() {
  try {
    const parsed = JSON.parse(fs.readFileSync(firebaseRcPath, "utf8"));
    return parsed.projects && parsed.projects.default;
  } catch (_) {
    return "arles-gestion";
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

function readProducts() {
  if (!fs.existsSync(csvPath)) {
    throw new Error(`Archivo no encontrado: ${csvPath}`);
  }

  const lines = fs.readFileSync(csvPath, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() !== "");

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
    const code = col(fields, "CODIGO_INTERNO");
    const item = col(fields, "DESCRIPCION");
    const stockStr = col(fields, "STOCK_ACTUAL").replace(",", ".");
    const stock = Number(stockStr);

    if (!code || !item) {
      throw new Error(`Fila ${rowIndex + 2}: falta CODIGO_INTERNO o DESCRIPCION.`);
    }

    return {
      modulo: "DotaciÃ³n",
      categoria: col(fields, "CATEGORIA") || "DotaciÃ³n",
      item: item,
      referencia: `Talla: ${col(fields, "TALLA")} | ${col(fields, "TIPO_PRENDA")}`,
      marca: "Institucional",
      cantidad: isNaN(stock) ? 0 : stock,
      unidad: col(fields, "UNIDAD") || "Und",
      codigo_interno: code.trim().toUpperCase(),
      observaciones: `Parte: ${col(fields, "PARTE_CUERPO")}`,
    };
  });
}

async function commitInBatches(db, products) {
  const importedAt = new Date().toISOString();
  let batch = db.batch();
  let opCount = 0;
  let written = 0;

  for (const product of products) {
    const code = product.codigo_interno;
    const nombreCompleto = [product.item, product.referencia].filter(Boolean).join(" ");

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
      ultimo_solicitante: "Importador DotaciÃ³n IA",
      observaciones: product.observaciones,
    };

    const existenciaRef = db.collection("existencias").doc(code);
    const catalogoRef = db.collection("catalogo_personalizado").doc(code);

    batch.set(existenciaRef, { ...baseData, cantidad: product.cantidad }, { merge: true });
    batch.set(catalogoRef, baseData, { merge: true });

    opCount += 2;
    written += 1;

    if (opCount >= 450) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  }

  if (opCount > 0) {
    await batch.commit();
  }
  return written;
}

async function main() {
  const products = readProducts();
  console.log(`Archivo detectado: ${csvPath}`);
  console.log(`Registros a procesar: ${products.length}`);

  if (!apply) {
    console.log("\n--- VISTA PREVIA (MODO TEST) ---");
    products.slice(0, 3).forEach(p => {
      console.log(`[${p.codigo_interno}] ${p.item} | ${p.referencia} | Stock: ${p.cantidad}`);
    });
    console.log("...");
    console.log("\nEjecuta con --apply para subir a Firestore.");
    return;
  }

  const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";

  admin.initializeApp({
    credential: fs.existsSync(credentialPath)
      ? admin.credential.cert(require(credentialPath))
      : admin.credential.applicationDefault(),
    projectId: projectIdFromFirebaseRc(),
  });

  const written = await commitInBatches(admin.firestore(), products);
  console.log(`\nÂ¡Ã‰xito! Se han actualizado ${written} artÃ­culos de dotaciÃ³n en Firestore.`);
}

main().catch(console.error);

