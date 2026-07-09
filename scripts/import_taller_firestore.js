#!/usr/bin/env node
/**
 * Importa inventario de Taller a la colecciÃ³n `herramientas` con submÃ³dulos.
 *
 * Uso:
 *   node scripts/import_taller_firestore.js ruta/al/archivo.csv
 *   node scripts/import_taller_firestore.js ruta/al/archivo.csv --apply
 */

const fs = require("fs");
const path = require("path");
const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const SUBMODULOS = [
  "HERRAMIENTAS TALLER",
  "EQUIPOS COSECHA",
  "HERRAMIENTAS MECANICAS",
  "VEHICULOS",
  "IMPLEMENTOS AGRICOLAS",
  "BODEGA ROJA",
];

const ALIAS = {
  "HERRAMIENTAS DE TALLER": "HERRAMIENTAS TALLER",
  "HERRAMIENTA TALLER": "HERRAMIENTAS TALLER",
  "EQUIPOS DE COSECHA": "EQUIPOS COSECHA",
  "EQUIPO DE COSECHA": "EQUIPOS COSECHA",
  "HERRAMIENTA MECANICA": "HERRAMIENTAS MECANICAS",
  "HERRAMIENTAS MECANICA": "HERRAMIENTAS MECANICAS",
  VEHICULO: "VEHICULOS",
  "IMPLEMENTO AGRICOLA": "IMPLEMENTOS AGRICOLAS",
  "IMPLEMENTOS AGRICOLA": "IMPLEMENTOS AGRICOLAS",
};

function sinAcentos(texto) {
  return texto
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .trim();
}

function normalizarSubmodulo(raw) {
  const limpio = (raw || "").trim();
  if (!limpio) return "HERRAMIENTAS TALLER";
  const exacto = SUBMODULOS.find((s) => s.toUpperCase() === limpio.toUpperCase());
  if (exacto) return exacto;
  const compacto = sinAcentos(limpio);
  if (ALIAS[compacto]) return ALIAS[compacto];
  const porAlias = SUBMODULOS.find((s) => sinAcentos(s) === compacto);
  if (porAlias) return porAlias;
  return limpio.toUpperCase();
}

function normalizarCodigo(raw) {
  const limpio = (raw || "").trim().toUpperCase();
  if (!limpio) return "";
  if (limpio.startsWith("QR-")) return limpio;
  if (/^\d+$/.test(limpio)) return `QR-${limpio}`;
  return limpio;
}

function claveDocumento(codigo) {
  return codigo
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || `taller-${Date.now()}`;
}

function parseCsvLine(line, separator) {
  const result = [];
  let current = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (char === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
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

function idx(headers, ...names) {
  const normalized = headers.map((h) => sinAcentos(h).replace(/[^A-Z0-9]/g, ""));
  for (const name of names) {
    const target = sinAcentos(name).replace(/[^A-Z0-9]/g, "");
    const found = normalized.indexOf(target);
    if (found >= 0) return found;
  }
  return -1;
}

function col(row, headers, ...names) {
  const i = idx(headers, ...names);
  return i >= 0 ? (row[i] || "").trim() : "";
}

function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  const hasSep = lines[0] && lines[0].toLowerCase().includes("sep=");
  const dataLines = hasSep ? lines.slice(1) : lines;
  if (dataLines.length < 2) return { headers: [], rows: [] };

  const separator = hasSep
    ? lines[0].split("=").pop().trim().charAt(0) || ";"
    : dataLines[0].includes(";") && dataLines[0].split(";").length >= dataLines[0].split(",").length
      ? ";"
      : ",";

  const headers = parseCsvLine(dataLines[0], separator).map((h) => h.trim());
  const rows = dataLines.slice(1).map((line) => parseCsvLine(line, separator));
  return { headers, rows, separator };
}

function rowToTool(headers, row) {
  const item = col(row, headers, "ITEM", "PRODUCTO", "NOMBRE");
  if (!item) return null;

  const categoria = col(row, headers, "CATEGORIA", "CATEGORÃA");
  const subModulo = normalizarSubmodulo(
    col(row, headers, "SUBMODULO_TALLER", "SUBMODULO", "SECCION", "AREA", "ÃREA") ||
      categoria ||
      col(row, headers, "UBICACION", "UBICACIÃ“N"),
  );
  const subcategoria = col(row, headers, "SUBCATEGORIA", "SUB CATEGORIA") ||
    (SUBMODULOS.includes(normalizarSubmodulo(categoria)) ? col(row, headers, "REFERENCIA", "REF") : categoria) ||
    col(row, headers, "REFERENCIA", "REF");

  const codigoQr = col(row, headers, "CODIGO_QR", "QR").replace(/^qr-/i, "");
  const codigoRaw = col(row, headers, "CODIGO", "CODIGO_INTERNO", "CODE", "ID");
  const codigo = codigoQr
    ? normalizarCodigo(codigoQr)
    : codigoRaw
      ? normalizarCodigo(codigoRaw)
      : `SINQR-IMP-${Date.now()}`;

  const cantidad = Number(col(row, headers, "CANTIDAD", "EXISTENCIA", "STOCK").replace(",", ".")) || 1;

  return {
    id: claveDocumento(codigo),
    data: {
      modulo: "Taller",
      submodulo_taller: subModulo,
      categoria: subModulo,
      subcategoria,
      nombre: item,
      tipo: col(row, headers, "TIPO", "TIPO_HERRAMIENTA"),
      tamano: col(row, headers, "TAMANO", "TAMAÃ‘O", "TALLA"),
      marca: col(row, headers, "MARCA", "BRAND"),
      codigo,
      codigo_principal: codigo,
      codigo_qr: codigoQr,
      requiere_asignar_qr: codigo.startsWith("SINQR"),
      cantidad_total: cantidad,
      cantidad_ocupada: 0,
      cantidad_disponible: cantidad,
      unidad: col(row, headers, "UNIDAD", "UNIDAD MEDIDA") || "UNIDAD",
      estado: "Disponible",
      ubicacion: subModulo,
      referencia: subcategoria,
      observaciones: col(row, headers, "OBSERVACIONES", "NOTAS", "COMENTARIO"),
      importado_script: true,
      ultima_actualizacion: new Date().toISOString(),
    },
  };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes("--apply");
  const csvPath = args.find((a) => !a.startsWith("--"));
  if (!csvPath) {
    console.error("Uso: node scripts/import_taller_firestore.js <archivo.csv> [--apply]");
    process.exit(1);
  }

  const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
  admin.initializeApp({
    credential: admin.credential.cert(require(credentialPath)),
    projectId: "arles-gestion",
  });
  const db = admin.firestore();
  const text = fs.readFileSync(path.resolve(csvPath), "utf8");
  const { headers, rows } = parseCsv(text);
  const tools = rows.map((row) => rowToTool(headers, row)).filter(Boolean);

  const resumen = {};
  tools.forEach((tool) => {
    const sm = tool.data.submodulo_taller;
    resumen[sm] = (resumen[sm] || 0) + 1;
  });

  console.log(`Filas vÃ¡lidas: ${tools.length}`);
  console.log("Por submÃ³dulo:", resumen);
  if (!apply) {
    console.log("\nVista previa (sin escribir). Ejecuta con --apply para importar.");
    tools.slice(0, 5).forEach((tool) => {
      console.log(`- [${tool.data.submodulo_taller}] ${tool.data.nombre} (${tool.data.codigo})`);
    });
    return;
  }

  const batchSize = 400;
  for (let i = 0; i < tools.length; i += batchSize) {
    const batch = db.batch();
    tools.slice(i, i + batchSize).forEach((tool) => {
      batch.set(db.collection("herramientas").doc(tool.id), tool.data, { merge: true });
    });
    await batch.commit();
  }
  console.log(`ImportaciÃ³n completada: ${tools.length} documentos en herramientas.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
