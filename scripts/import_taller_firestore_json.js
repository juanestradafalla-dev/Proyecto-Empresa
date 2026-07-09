#!/usr/bin/env node
/**
 * Importa inventario de Taller desde JSON a la colecciÃ³n `herramientas`.
 *
 * Formatos soportados:
 * 1) Array de objetos: [{ nombre, submodulo_taller, codigo, ... }]
 * 2) Objeto por secciones: { "EQUIPOS COSECHA": [ {...}, ... ], "VEHICULOS": [ ... ] }
 * 3) Objeto con clave items/herramientas/data: { items: [ ... ] }
 *
 * Uso:
 *   node scripts/import_taller_firestore_json.js ruta/al/archivo.json
 *   node scripts/import_taller_firestore_json.js ruta/al/archivo.json --apply
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
  "EQUIPOS POSCOSECHA": "EQUIPOS COSECHA",
  "EQUIPO DE COSECHA": "EQUIPOS COSECHA",
  "HERRAMIENTA MECANICA": "HERRAMIENTAS MECANICAS",
  "HERRAMIENTAS MECANICA": "HERRAMIENTAS MECANICAS",
  VEHICULO: "VEHICULOS",
  "IMPLEMENTO AGRICOLA": "IMPLEMENTOS AGRICOLAS",
  "IMPLEMENTOS AGRICOLA": "IMPLEMENTOS AGRICOLAS",
};

function sinAcentos(texto) {
  return String(texto || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .trim();
}

function pick(obj, ...keys) {
  for (const key of keys) {
    if (obj[key] !== undefined && obj[key] !== null && String(obj[key]).trim() !== "") {
      return String(obj[key]).trim();
    }
  }
  return "";
}

function normalizarSubmodulo(raw) {
  const limpio = String(raw || "").trim();
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
  const limpio = String(raw || "").trim().toUpperCase();
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

function numero(valor, fallback = 1) {
  if (typeof valor === "number" && !Number.isNaN(valor)) return valor;
  const parsed = Number(String(valor || "").replace(",", "."));
  return Number.isNaN(parsed) ? fallback : parsed;
}

function flattenJson(data) {
  if (Array.isArray(data)) {
    return data.map((item) => (typeof item === "object" && item ? item : null)).filter(Boolean);
  }
  if (!data || typeof data !== "object") return [];

  const arrayKeys = ["items", "herramientas", "data", "inventario", "registros", "productos"];
  for (const key of arrayKeys) {
    if (Array.isArray(data[key])) return data[key];
  }

  const rows = [];
  for (const [key, value] of Object.entries(data)) {
    if (!Array.isArray(value)) continue;
    const subModulo = normalizarSubmodulo(key);
    value.forEach((item) => {
      if (item && typeof item === "object") {
        rows.push({ ...item, submodulo_taller: pick(item, "submodulo_taller", "submodulo", "seccion", "categoria") || subModulo });
      }
    });
  }
  return rows;
}

function rowToTool(raw, index) {
  const nombre = pick(raw, "nombre", "item", "producto", "descripcion", "name");
  if (!nombre) return null;

  const categoria = pick(raw, "categoria", "category");
  const subModulo = normalizarSubmodulo(
    pick(raw, "submodulo_taller", "submodulo", "subModulo", "seccion", "secciÃ³n", "area", "ubicacion", "ubicaciÃ³n") ||
      (SUBMODULOS.includes(normalizarSubmodulo(categoria)) ? categoria : ""),
  );
  const subcategoria = pick(raw, "subcategoria", "subcategoria_taller", "referencia", "ref", "categoria_detalle");
  const subcategoriaFinal = subcategoria || (SUBMODULOS.includes(normalizarSubmodulo(categoria)) ? pick(raw, "referencia", "ref") : categoria);

  const codigoQr = pick(raw, "codigo_qr", "codigoQr", "qr").replace(/^qr-/i, "");
  const codigoRaw = pick(raw, "codigo_principal", "codigo", "codigo_interno", "code", "id");
  const codigo = codigoQr
    ? normalizarCodigo(codigoQr)
    : codigoRaw
      ? normalizarCodigo(codigoRaw)
      : `SINQR-JSON-${index + 1}`;

  const total = numero(pick(raw, "cantidad_total", "cantidad", "stock_total", "stock", "existencia"), 1);
  const ocupado = numero(pick(raw, "cantidad_ocupada", "ocupados"), 0);

  return {
    id: claveDocumento(codigo),
    data: {
      modulo: pick(raw, "modulo") || "Taller",
      submodulo_taller: subModulo,
      categoria: subModulo,
      subcategoria: subcategoriaFinal,
      nombre,
      tipo: pick(raw, "tipo", "tipo_herramienta"),
      tamano: pick(raw, "tamano", "tamaÃ±o", "talla"),
      marca: pick(raw, "marca", "brand"),
      color: pick(raw, "color"),
      modelo: pick(raw, "modelo"),
      uso: pick(raw, "uso"),
      rango: pick(raw, "rango"),
      codigo,
      codigo_principal: codigo,
      codigo_qr: codigoQr,
      requiere_asignar_qr: Boolean(raw.requiere_asignar_qr ?? raw.requiereAsignarQr) || codigo.startsWith("SINQR"),
      cantidad_total: total,
      cantidad_ocupada: ocupado,
      cantidad_disponible: Math.max(total - ocupado, 0),
      unidad: pick(raw, "unidad") || "UNIDAD",
      estado: pick(raw, "estado") || (ocupado > 0 ? "En uso" : "Disponible"),
      ubicacion: pick(raw, "ubicacion", "ubicaciÃ³n") || subModulo,
      referencia: subcategoriaFinal,
      observaciones: pick(raw, "observaciones", "notas", "comentario"),
      importado_json: true,
      ultima_actualizacion: new Date().toISOString(),
    },
  };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes("--apply");
  const jsonPath = args.find((a) => !a.startsWith("--"));
  if (!jsonPath) {
    console.error("Uso: node scripts/import_taller_firestore_json.js <archivo.json> [--apply]");
    process.exit(1);
  }

  const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";
  admin.initializeApp({
    credential: admin.credential.cert(require(credentialPath)),
    projectId: "arles-gestion",
  });
  const db = admin.firestore();

  const rawText = fs.readFileSync(path.resolve(jsonPath), "utf8");
  const parsed = JSON.parse(rawText);
  const rows = flattenJson(parsed);
  const tools = rows.map((row, index) => rowToTool(row, index)).filter(Boolean);

  const resumen = {};
  tools.forEach((tool) => {
    const sm = tool.data.submodulo_taller;
    resumen[sm] = (resumen[sm] || 0) + 1;
  });

  console.log(`Archivo: ${path.resolve(jsonPath)}`);
  console.log(`Filas vÃ¡lidas: ${tools.length}`);
  console.log("Por submÃ³dulo:", resumen);

  if (!apply) {
    console.log("\nVista previa (sin escribir). Ejecuta con --apply para importar.");
    tools.slice(0, 8).forEach((tool) => {
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
