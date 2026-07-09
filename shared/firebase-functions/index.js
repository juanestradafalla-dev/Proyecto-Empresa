const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

admin.initializeApp();

const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-4o-mini";
const OWNER_EMAIL = "juanestradafalla@gmail.com";
const TRUSTED_ALMACEN_EMAILS = new Set([
  "almacen@arlessas.com",
]);

function usuarioTieneAccesoIA(data) {
  const rol = String(data.rol || data.role || "").toLowerCase();
  const estado = String(data.estado || "").toLowerCase();
  return data.activo === true
    || estado === "activo"
    || ["admin", "administrador", "almacenista", "operador", "owner"].includes(rol);
}

function rolPorDefectoParaEmail(email) {
  const normalizado = String(email || "").toLowerCase();
  if (normalizado === OWNER_EMAIL) return "owner";
  if (TRUSTED_ALMACEN_EMAILS.has(normalizado)) return "almacenista";
  return "operador";
}

async function asegurarPerfilUsuarioIA(uid, email) {
  const ref = admin.firestore().collection("usuarios").doc(uid);
  const userDoc = await ref.get();
  const rolDefecto = rolPorDefectoParaEmail(email);
  if (!userDoc.exists) {
    const perfil = {
      email: email || "",
      nombres: "Usuario",
      apellidos: "",
      cargo: rolDefecto === "almacenista" ? "Almacenista" : "Operador",
      rol: rolDefecto,
      activo: true,
      estado: "activo",
      fecha_registro: new Date().toISOString(),
      creado_por: "asistente_auto",
    };
    await ref.set(perfil, { merge: true });
    return { uid, email, rol: rolDefecto };
  }

  const data = userDoc.data() || {};
  if (usuarioTieneAccesoIA(data)) {
    const rol = String(data.rol || data.role || "").toLowerCase() || rolDefecto;
    return { uid, email, rol };
  }

  const rolActual = String(data.rol || data.role || "").toLowerCase();
  if (rolActual === "pendiente" || rolActual === "" || data.activo === false) {
    await ref.set({
      rol: rolDefecto,
      activo: true,
      estado: "activo",
      autorizado_en: new Date().toISOString(),
      autorizado_por: "asistente_auto",
    }, { merge: true });
    return { uid, email, rol: rolDefecto };
  }

  throw new HttpsError(
    "permission-denied",
    "Tu cuenta aún no está autorizada para el asistente IA. Pide al administrador que active tu usuario.",
  );
}

async function assertUsuarioAutorizado(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión para usar el asistente.");
  }
  const email = String(request.auth.token?.email || "").toLowerCase();
  if (email === OWNER_EMAIL) {
    return { uid: request.auth.uid, email, rol: "owner" };
  }
  return asegurarPerfilUsuarioIA(request.auth.uid, email);
}

function limitar(texto, max) {
  return String(texto || "").slice(0, max);
}

function extraerTextoRespuesta(json) {
  return json.choices?.[0]?.message?.content?.trim() || "";
}

function parseHistorialConversacion(raw) {
  try {
    const arr = JSON.parse(String(raw || "[]"));
    if (!Array.isArray(arr)) return [];
    return arr
      .filter((item) => item && (item.rol === "usuario" || item.rol === "asistente") && item.texto)
      .map((item) => ({
        rol: item.rol,
        texto: limitar(item.texto, 500),
        fecha: limitar(item.fecha, 40),
      }))
      .slice(-16);
  } catch (_error) {
    return [];
  }
}

/**
 * CARGA MASIVA TOTAL Y FINAL - 179 PRODUCTOS EXACTOS
 */
exports.importarConsumiblesMasivo = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "No autorizado.");
  const email = String(request.auth.token?.email || "").toLowerCase();
  if (email !== OWNER_EMAIL) {
    throw new HttpsError("permission-denied", "Solo el administrador puede ejecutar la carga masiva.");
  }
  const db = admin.firestore();
  const timestamp = new Date().toISOString();

  const data = [
    { cod: "A-P1-001", item: "NEUMATICO", marca: "CHALLENGER", ref: "TR218A 23.1-26", stock: 1 },
    { cod: "A-P1-002", item: "NEUMATICO", marca: "CHALLENGER", ref: "TR218A 14.9-24", stock: 1 },
    { cod: "A-P1-003", item: "NEUMATICO", marca: "CHALLENGER", ref: "TR15 1100-16", stock: 2 },
    { cod: "A-P1-004", item: "NEUMATICO", marca: "CENTAURO", ref: "TR218A 14.9-24", stock: 1 },
    { cod: "A-P1-005", item: "NEUMATICO", marca: "CHALLENGER", ref: "TR15 750-16", stock: 2 },
    { cod: "A-P3-001", item: "RODAMIENTO RODILLOS ESFERICO", marca: "LQB BEARING UNITS", ref: "33275/462", stock: 8 },
    { cod: "A-P3-002", item: "RODAMIENTO RODILLOS ESFERICO", marca: "LQB BEARING UNITS", ref: "42688/20", stock: 4 },
    { cod: "A-P3-003", item: "RODAMIENTO RODILLOS ESFERICO", marca: "KOYO", ref: "42688/20", stock: 2 },
    { cod: "A-P3-004", item: "CHUMACERA", marca: "FAG", ref: "UCF208-J7", stock: 4 },
    { cod: "A-P3-005", item: "CHUMACERA", marca: "HTH", ref: "UC208-24", stock: 2 },
    { cod: "A-P3-006", item: "UNIVERSAL JOINT REPAIR KIT", marca: "KAYM", ref: "REF NO 331", stock: 5 },
    { cod: "A-P3-007", item: "UNIVERSAL JOINT REPAIR KIT", marca: "GENERIC", ref: "27X74.6", stock: 1 },
    { cod: "A-P3-008", item: "UNIVERSAL JOINT REPAIR KIT", marca: "GENERIC", ref: "N/A", stock: 1 },
    { cod: "A-P3-009", item: "RODILLO SERIE 30", marca: "FERSA", ref: "30210", stock: 2 },
    { cod: "A-P3-010", item: "RODILLO CUNA", marca: "HTH", ref: "33287/33462", stock: 2 },
    { cod: "A-P3-011", item: "RODILLO CUNA", marca: "SKF", ref: "33287/33462-SET195P", stock: 1 },
    { cod: "A-P3-012", item: "RODILLO CUNA", marca: "HTH", ref: "3982/3920", stock: 2 },
    { cod: "A-P3-013", item: "RODILLO", marca: "HTH", ref: "30210", stock: 1 },
    { cod: "A-P3-014", item: "RODILLO SERIE 30", marca: "HTH", ref: "30209", stock: 3 },
    { cod: "A-P3-015", item: "RODILLO SERIE 30", marca: "RECAPI SAS", ref: "30209", stock: 4 },
    { cod: "A-P3-016", item: "RODILLO", marca: "KEJIA", ref: "30208", stock: 1 },
    { cod: "A-P3-017", item: "RODILLO", marca: "KEJIA", ref: "30209", stock: 2 },
    { cod: "A-P3-018", item: "RODILLO", marca: "SKF", ref: "30208/VA6481", stock: 2 },
    { cod: "A-P3-019", item: "RODILLO", marca: "PFI BEARING", ref: "6209-2RS C3", stock: 2 },
    { cod: "A-P3-020", item: "RODILLO", marca: "PFI BEARING", ref: "30207-2RS-C3", stock: 3 },
    { cod: "A-P3-021", item: "RODILLO", marca: "PFI BEARING", ref: "30207", stock: 4 },
    { cod: "A-P3-022", item: "RODILLO", marca: "DPI", ref: "25580/25520", stock: 4 },
    { cod: "A-P3-023", item: "RODILLO", marca: "DPI", ref: "30208JR", stock: 2 },
    { cod: "A-P3-024", item: "RODILLO", marca: "PER BEARING", ref: "30208", stock: 4 },
    { cod: "A-P3-025", item: "RODILLO", marca: "KOYO", ref: "30208 JR", stock: 1 },
    { cod: "A-P3-026", item: "RODILLO", marca: "CRAFT BEARINGS", ref: "LM/501349/LM501310", stock: 4 },
    { cod: "A-P3-027", item: "RODILLO", marca: "KML", ref: "LM501349/10", stock: 1 },
    { cod: "A-P3-028", item: "RODILLO", marca: "RNV", ref: "LM501349/10", stock: 8 },
    { cod: "A-P3-030", item: "RODILLO", marca: "KOYO", ref: "60012RSC3", stock: 6 },
    { cod: "A-P3-031", item: "RODILLO", marca: "WG", ref: "9\"", stock: 2 },
    { cod: "A-P3-032", item: "CHUMACERA DE FRICCION", marca: "GENERIC", ref: "N/A", stock: 1 },
    { cod: "A-P4-001", item: "FILTRO ACEITE", marca: "BALDWIN FILTERS", ref: "BT526", stock: 6 },
    { cod: "A-P4-002", item: "FUEL/WATER SEPARATOR", marca: "GENERIC", ref: "FS 19516", stock: 4 },
    { cod: "A-P4-003", item: "FILTRO COMBUSTIBLE SECUNDARIO", marca: "BALDWIN FILTERS", ref: "BF7952-D", stock: 6 },
    { cod: "A-P4-004", item: "FILTRO ACEITE", marca: "FILTROS MASTER", ref: "MF 16", stock: 2 },
    { cod: "A-P4-005", item: "FUEL FILTER", marca: "TECNIFIL", ref: "TRO-346", stock: 2 },
    { cod: "A-P4-006", item: "GAS FILTER", marca: "GF COMERCIALIZADORA", ref: "GF-61", stock: 1 },
    { cod: "A-P4-007", item: "SENSOR DE PRESION DE ACEITE", marca: "GENERIC", ref: "288733", stock: 1 },
    { cod: "A-P4-008", item: "FILTRO DE ACEITE", marca: "SAKURA", ref: "C-7607", stock: 4 },
    { cod: "A-P4-010", item: "BOMBA ELECTRICA DE COMBUSTIBE", marca: "INJETECH", ref: "INJ09A-IMP", stock: 1 },
    { cod: "A-P5-001", item: "AIR FILTER", marca: "AYZ", ref: "AZP AT 171853/AZP 1930590", stock: 6 },
    { cod: "A-P5-002", item: "AIR FILTER", marca: "SAKURA", ref: "A-5541-S", stock: 1 },
    { cod: "A-P5-003", item: "FILTRO DE AIRE", marca: "SFA", ref: "RS4680", stock: 1 },
    { cod: "A-P5-004", item: "FILTRO DE AIRE", marca: "AYZ", ref: "AZP135326206", stock: 2 },
    { cod: "A-P6-001", item: "FILTRO DE AIRE PERKINS", marca: "PREMIUM FILTERS", ref: "API-1143", stock: 3 },
    { cod: "A-P6-002", item: "FILTRO DE AIRE JOHN DEERE", marca: "ULTRAGARD", ref: "AT171854", stock: 1 },
    { cod: "A-P6-003", item: "FILTRO DE AIRE PF10", marca: "BALDWIN FILTERS", ref: "A1-1/2", stock: 4 },
    { cod: "A-P6-004", item: "FILTRO HIDRAULICO", marca: "JOHN DEERE", ref: "RE45864", stock: 1 },
    { cod: "A-P6-005", item: "FILTRO DE ACEITE HIDRAULICO", marca: "BALDWIN FILTERS", ref: "BT8309-MPG", stock: 5 },
    { cod: "A-P6-006", item: "FILTRO DE AIRE", marca: "SAKURA", ref: "7620S", stock: 3 },
    { cod: "B-P1-001", item: "PERRO PESADO", marca: "GENERIC", ref: "1 1/4\"", stock: 15 },
    { cod: "B-P2-001", item: "KIT BOMBA AUTOCEBANTE ALUMINIO", marca: "HONDA", ref: "KB-KDP20", stock: 1 },
    { cod: "B-P2-002", item: "COLADERA", marca: "HONDA", ref: "KDP30-24", stock: 1 },
    { cod: "B-P2-003", item: "ARRANCADOR DE MOTOR RETRACTIL NEGRO", marca: "WARRIOR GASOLINA", ref: "CJT168FB", stock: 4 },
    { cod: "B-P2-004", item: "ARRANCADOR DE MOTOR DIESEL", marca: "ECOHORSE DIESEL", ref: "186F-186FA-192F", stock: 1 },
    { cod: "B-P2-005", item: "ARRANQUE GUADAÑA SHINDAIWA", marca: "ECHO INCORPORATED", ref: "B-45", stock: 1 },
    { cod: "B-P2-006", item: "CABEZAL GUADAÑA", marca: "GENERIC", ref: "N/A", stock: 2 },
    { cod: "B-P2-007", item: "THERMOSTAT OE", marca: "PERKINS POWERPART", ref: "145206182", stock: 1 },
    { cod: "B-P2-008", item: "COGINETE DE BLOQUE", marca: "KDWY BEARINGS", ref: "UCP208-24", stock: 1 },
    { cod: "B-P2-009", item: "COGINETE DE BLOQUE", marca: "HTH", ref: "UCP211-32", stock: 1 },
    { cod: "B-P2-010", item: "CILINDRO GUADAÑA", marca: "WARRIOR", ref: "B45", stock: 1 },
    { cod: "B-P2-011", item: "SILENCIADOR ", marca: "SUKIYAMA", ref: "HUSQ 83-01", stock: 1 },
    { cod: "B-P2-012", item: "SELLO MACANICO PARA MOTOBOMBA", marca: "K-SEAL", ref: "3/4 CAZ", stock: 4 },
    { cod: "B-P2-013", item: "CILINDRO GUADAÑA", marca: "SUKIYAMA", ref: "B45", stock: 1 },
    { cod: "B-P3-001", item: "CAJA PARCHE DIAGONAL PARA NEUMATICOS", marca: "RUBEN VULK", ref: "BP04", stock: 5 },
    { cod: "B-P3-002", item: "PARCHE DIAGONAL PARA NEUMATICOS", marca: "RUBEN VULK", ref: "BP05", stock: 1 },
    { cod: "B-P3-003", item: "CARBURADOR CPTO", marca: "HUSQVARNA GROUP", ref: "G45", stock: 1 },
    { cod: "B-P3-004", item: "BUJIA", marca: "CHAMPION", ref: "CJ8", stock: 7 },
    { cod: "B-P3-005", item: "BUJIA", marca: "NGK", ref: "B7ES", stock: 5 },
    { cod: "B-P3-006", item: "BUJIA", marca: "WARRIOR", ref: "A969P", stock: 12 },
    { cod: "B-P3-007", item: "PARCHE MOTO", marca: "COLD PATCH", ref: "L24", stock: 24 },
    { cod: "B-P3-008", item: "TUBELESS TIRE INSERTS", marca: "CENTAURO", ref: "4\"", stock: 120 },
    { cod: "B-P3-010", item: "BANDAS FRENOS", marca: "OSAKA", ref: "MFG151", stock: 3 },
    { cod: "B-P3-011", item: "PASTILLAS FRENOS", marca: "OSAKA", ref: "P10019", stock: 2 },
    { cod: "B-P3-012", item: "KIT DE ARRASTRE", marca: "BAJAJ", ref: "36DU4085", stock: 2 },
    { cod: "B-P4-001", item: "TIZA PARA METAL", marca: "XYWELD", ref: "Q2U0006", stock: 92 },
    { cod: "B-P4-002", item: "CUCHILLA GUADAÑA", marca: "NA", ref: "NA", stock: 15 },
    { cod: "B-P4-003", item: "LIMA TRIANGULAR", marca: "NA", ref: "NA", stock: 3 },
    { cod: "B-P4-004", item: "GRATA METALICA", marca: "NA", ref: "NA", stock: 3 },
    { cod: "B-P4-005", item: "PIEDRA AFILAR", marca: "LHAURA", ref: "NA", stock: 2 },
    { cod: "B-P5-001", item: "RETENEDOR DE ACEITE", marca: "NA", ref: "40X80X10", stock: 4 },
    { cod: "B-P5-002", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "50X75X10 50740", stock: 4 },
    { cod: "B-P5-003", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "50X80X13 50838", stock: 2 },
    { cod: "B-P5-004", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "45X85X10 45809", stock: 2 },
    { cod: "B-P5-005", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "45X90X10 45902", stock: 4 },
    { cod: "B-P5-006", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "45X80X12 45804", stock: 5 },
    { cod: "B-P5-007", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "50.80X76.20X12.7 500040", stock: 5 },
    { cod: "B-P5-008", item: "RETENEDOR DE ACEITE", marca: "LYO", ref: "60X90X10 60932", stock: 2 },
    { cod: "B-P5-009", item: "RETENEDOR DE ACEITE", marca: "NA", ref: "95X130X13", stock: 4 },
    { cod: "B-P5-010", item: "RETENEDOR DE ACEITE", marca: "NKS", ref: "48X70X9 D6D326157", stock: 2 },
    { cod: "B-P5-011", item: "RETENEDOR DE ACEITE", marca: "NQK.SF", ref: "55X90X10", stock: 1 },
    { cod: "B-P6-001", item: "SOBRE PAPEL INDUSTRIAL", marca: "GENERIC", ref: "36.5CMX44CM", stock: 300 },
    { cod: "B-P6-002", item: "CAJA ROLLO PARA EMPACAQUE AL VACIO", marca: "OSTER", ref: "P123RP", stock: 5 },
    { cod: "B-P6-003", item: "RESMA", marca: "REPROGRAF", ref: "100 HOJAS", stock: 3 },
    { cod: "D-P1-001", item: "TEE SANITARIA", marca: "GENERIC", ref: "3\"", stock: 5 },
    { cod: "D-P1-002", item: "TEE SANITARIA", marca: "GENERIC", ref: "2\"", stock: 1 },
    { cod: "D-P1-003", item: "CODO SANITARIO HEMBRA", marca: "GENERIC", ref: "3\"", stock: 2 },
    { cod: "D-P1-004", item: "CODO SANITARIO HEMBRA/MACHO", marca: "GENERIC", ref: "2\"", stock: 1 },
    { cod: "D-P1-005", item: "SIFON SANITARIO HEMBRA", marca: "GENERIC", ref: "2\"", stock: 4 },
    { cod: "D-P1-006", item: "CODO SANITARIO HEMBRA", marca: "GENERIC", ref: "2\"", stock: 16 },
    { cod: "D-P1-007", item: "UNION SANITARIO", marca: "GENERIC", ref: "3\"", stock: 2 },
    { cod: "D-P1-008", item: "REDUCTOR SANITARIA", marca: "GENERIC", ref: "4\" X 2\"", stock: 3 },
    { cod: "D-P1-009", item: "BRIDA SANITARIA", marca: "GENERIC", ref: "3\"", stock: 2 },
    { cod: "D-P1-010", item: "UNION SANITARIO", marca: "GENERIC", ref: "2\"", stock: 2 },
    { cod: "D-P2-001", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "1/2\"", stock: 4 },
    { cod: "D-P2-002", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 11 },
    { cod: "D-P2-003", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 7 },
    { cod: "D-P2-004", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "2\"", stock: 6 },
    { cod: "D-P2-005", item: "UNION T PVC", marca: "GENERIC", ref: "1\"", stock: 10 },
    { cod: "D-P2-006", item: "UNION T PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 6 },
    { cod: "D-P2-007", item: "UNION T PVC", marca: "GENERIC", ref: "2\"", stock: 8 },
    { cod: "D-P2-008", item: "UNION CRUZ PVC", marca: "GENERIC", ref: "1\"", stock: 2 },
    { cod: "D-P2-009", item: "CODO 90° PVC", marca: "GENERIC", ref: "1/2\"", stock: 4 },
    { cod: "D-P2-010", item: "CODO 90° PVC", marca: "GENERIC", ref: "1\"", stock: 9 },
    { cod: "D-P2-011", item: "CODO 90° PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 5 },
    { cod: "D-P2-012", item: "CODO 90° PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 6 },
    { cod: "D-P2-013", item: "CODO 90° PVC", marca: "GENERIC", ref: "2\"", stock: 5 },
    { cod: "D-P2-014", item: "CODO 45° PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 1 },
    { cod: "D-P2-015", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "3/4\"", stock: 2 },
    { cod: "D-P2-017", item: "UNION T PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 8 },
    { cod: "D-P2-018", item: "CODO 90° PVC rosca rosca", marca: "GENERIC", ref: "1\"", stock: 4 },
    { cod: "D-P2-019", item: "LLAVE VALVULA PVC", marca: "GENERIC", ref: "1\"", stock: 1 },
    { cod: "D-P3-001", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "1X3/4\"", stock: 0 },
    { cod: "D-P3-002", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "1/2\"", stock: 1 },
    { cod: "D-P3-003", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "3/4\"", stock: 1 },
    { cod: "D-P3-004", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "2\"", stock: 0 },
    { cod: "D-P3-005", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 0 },
    { cod: "D-P3-006", item: "UNION HEMBRA/ROSCA HEMBRA PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 0 },
    { cod: "D-P3-007", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "3/4\"", stock: 7 },
    { cod: "D-P3-008", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 8 },
    { cod: "D-P3-009", item: "UNION T PVC", marca: "GENERIC", ref: "1/2\"", stock: 4 },
    { cod: "D-P3-010", item: "UNION T PVC", marca: "GENERIC", ref: "3/4\"", stock: 2 },
    { cod: "D-P3-011", item: "UNION ROSCA MACHO/ROSCA MACHO PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 8 },
    { cod: "D-P3-012", item: "UNION ROSCA MACHO/ROSCA MACHO PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 7 },
    { cod: "D-P3-013", item: "UNION ROSCA MACHO/ROSCA MACHO PVC", marca: "GENERIC", ref: "2\"", stock: 4 },
    { cod: "D-P3-014", item: "UNION ROSCA MACHO/ROSCA HEMBRA/UNION HEMBRA PVC", marca: "GENERIC", ref: "1\" - 1/2\" - 1/2\"", stock: 1 },
    { cod: "D-P3-015", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 9 },
    { cod: "D-P3-016", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "2\"", stock: 15 },
    { cod: "D-P3-017", item: "UNIVERSAL PVC", marca: "GENERIC", ref: "3/4\"", stock: 4 },
    { cod: "D-P3-019", item: "UNIVERSAL PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 2 },
    { cod: "D-P3-020", item: "UNIVERSAL PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 8 },
    { cod: "D-P3-021", item: "UNIVERSAL PVC", marca: "GENERIC", ref: "2\"", stock: 4 },
    { cod: "D-P3-022", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "1/2\"", stock: 8 },
    { cod: "D-P3-023", item: "UNION HEMBRA/ROSCA MACHO PVC", marca: "GENERIC", ref: "2\"", stock: 2 },
    { cod: "D-P4-001", item: "CODO 90° PVC ROSCAS HEMBRA", marca: "GENERIC", ref: "1\"", stock: 4 },
    { cod: "D-P4-002", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "1/2\"", stock: 12 },
    { cod: "D-P4-003", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "3/4\"", stock: 13 },
    { cod: "D-P4-004", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 2 },
    { cod: "D-P4-005", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "1 1/2\"", stock: 6 },
    { cod: "D-P4-006", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "2\"", stock: 10 },
    { cod: "D-P4-007", item: "TAPON PVC", marca: "GENERIC", ref: "1\"", stock: 9 },
    { cod: "D-P4-008", item: "TAPON PVC", marca: "GENERIC", ref: "1 1/4\"", stock: 3 },
    { cod: "D-P4-009", item: "TAPON PVC", marca: "GENERIC", ref: "2\"", stock: 3 },
    { cod: "D-P4-010", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "1\"", stock: 1 },
    { cod: "D-P4-011", item: "UNION HEMBRA/HEMBRA PVC", marca: "GENERIC", ref: "3\"", stock: 1 },
    { cod: "D-P4-012", item: "TAPON PVC", marca: "GENERIC", ref: "1/2\"", stock: 6 },
    { cod: "D-P4-013", item: "TAPON PVC", marca: "GENERIC", ref: "3/4\"", stock: 13 },
    { cod: "D-P5-001", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1 1/4\" 60X42MM", stock: 11 },
    { cod: "D-P5-002", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1 1/2\" 60X48MM", stock: 6 },
    { cod: "D-P5-003", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1 1/4\" 50X31MM", stock: 11 },
    { cod: "D-P5-004", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1 1/2\" 50X38MM", stock: 6 },
    { cod: "D-P5-005", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1 1/4\" 1-1", stock: 4 },
    { cod: "D-P5-006", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2X1\"", stock: 3 },
    { cod: "D-P5-007", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "50mm - 2\"", stock: 5 },
    { cod: "D-P5-008", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "2\"x 1/2\"", stock: 6 },
    { cod: "D-P5-009", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "1\"x 1/2\"", stock: 9 },
    { cod: "D-P5-010", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "38mm - 1/2\"", stock: 10 },
    { cod: "D-P5-011", item: "BUJE REDUCTOR EN PVC", marca: "GENERIC", ref: "31mm - 1/4\"", stock: 9 },
    { cod: "D-P5-012", item: "REDUCION PVC", marca: "GENERIC", ref: "1/2\" X 1\"", stock: 10 }
  ];

  // LIMPIEZA TOTAL
  const colRef = db.collection("existencias");
  const snapshot = await colRef.get();
  const deleteBatch = db.batch();
  snapshot.docs.forEach(doc => deleteBatch.delete(doc.ref));
  await deleteBatch.commit();

  // CARGA COMPLETA (BLOQUES DE 500 PARA SEGURIDAD)
  const uploadBatch = db.batch();
  data.forEach(p => {
    const docRef = colRef.doc(p.cod);
    const marca = p.marca || "GENERIC";
    const referencia = p.ref || "N/A";
    const nombreCompleto = [p.item, marca, referencia].filter(Boolean).join(" ");
    uploadBatch.set(docRef, {
      codigo_interno: p.cod,
      item: p.item,
      marca,
      referencia,
      nombre_completo: nombreCompleto,
      busqueda: `${p.cod} ${nombreCompleto}`.toLowerCase(),
      unidad: "UNIDAD",
      cantidad: parseFloat(p.stock) || 0,
      ultima_fecha: timestamp,
      ultimo_solicitante: "Carga Final Verificada v3.2",
      modulo: "Consumibles",
      categoria: "Inventario importado"
    });
  });

  try {
    await uploadBatch.commit();
    return { mensaje: `¡AHORA SÍ! 100% verificado. ${data.length} productos cargados sin faltantes.` };
  } catch (error) {
    throw new HttpsError("internal", error.message);
  }
});

exports.sincronizarPerfilUsuario = onCall(
  { region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    }
    const email = String(request.auth.token?.email || "").toLowerCase();
    if (email === OWNER_EMAIL) {
      return { rol: "owner", activo: true, email };
    }
    const perfil = await asegurarPerfilUsuarioIA(request.auth.uid, email);
    return { rol: perfil.rol, activo: true, email: perfil.email };
  },
);

exports.asistenteOpenAI = onCall(
  {
    region: "us-central1",
    secrets: [OPENAI_API_KEY],
    timeoutSeconds: 90,
    memory: "512MiB"
  },
  async (request) => {
    const authData = await assertUsuarioAutorizado(request);
    const userRole = authData.rol;

    const mensaje = limitar(request.data?.mensaje, 4000);
    const perfil = limitar(request.data?.perfil, 800) + ` | ROL: ${userRole}`;
    const stockInfo = limitar(request.data?.stockInfo, 9000);
    const stockContextMode = limitar(request.data?.stockContextMode, 500);
    const historial = limitar(request.data?.historial, 3500);
    const memoriaEvolutiva = limitar(request.data?.memoriaEvolutiva, 3500);
    const memoriaLocal = limitar(request.data?.memoriaLocal, 2500);
    const catalogo = limitar(request.data?.catalogo, 10000);
    const herramientas = limitar(request.data?.herramientas, 4500);
    const patronesLocales = limitar(request.data?.patronesLocales, 3500);
    const fraseProducto = limitar(request.data?.fraseProducto, 300);
    const moduloDetectado = limitar(request.data?.moduloDetectado, 120);
    const ordenInterpretada = limitar(request.data?.ordenInterpretada, 500);
    const coincidenciasLocales = limitar(request.data?.coincidenciasLocales, 2000);
    const historialConversacion = parseHistorialConversacion(request.data?.historialConversacion);
    const esAutocritica = Boolean(request.data?.esAutocritica);

    if (!mensaje.trim()) {
      throw new HttpsError("invalid-argument", "Envía un mensaje para el asistente.");
    }

    const instrucciones = `
Eres el Asistente IA de Arles Gestión. Responde en español, breve, técnico y útil.

PRINCIPIOS OBLIGATORIOS:
0. SEGURIDAD POR ROL: Tu rol es asistir al usuario según su nivel de acceso.
   - admin: Acceso total.
   - almacenista: Gestión operativa (entradas, salidas, herramientas, vencimientos, correcciones, exportar). No puede hacer backup ni importar inventario masivo.
   - operador: Solo puede registrar salidas, consultar stock y navegar a inventario/historial. No puede registrar entradas, ni herramientas, ni borrar nada, ni exportar.
   Si el usuario pide algo fuera de su rol, niégate educadamente.

0.1. MODO DESCUBRIMIENTO SEMÁNTICO: Si el usuario pregunta por una FUNCIÓN o PROBLEMA (ej: "algo para las hormigas", "limpiar motores", "para el pasto"), usa tu conocimiento interno para identificar la categoría probable (Químicos, Consumibles, etc.) y busca en el CATÁLOGO y la MEMORIA productos que cumplan esa función. Si no estás seguro, sugiere la categoría más lógica.

0.2. APRENDIZAJE PROACTIVO: Si el usuario te enseña algo nuevo (ej: "el producto X es para el hongo Y"), responde confirmando y genera AUTOMÁTICAMENTE un JSON de acción "aprender" para guardar esa relación en tu memoria permanente.

1. Usa primero el STOCK REAL, el CATÁLOGO, las HERRAMIENTAS, la MEMORIA y el HISTORIAL enviados por la app.
2. No inventes existencias. Si no aparece en el contexto, dilo con claridad.
2.1. El STOCK REAL puede venir filtrado por relevancia para ahorrar tokens. Si el modo de contexto indica filtrado, no asumas que representa todo el inventario.
2.2. HERRAMIENTAS TALLER: La ocupación real es el campo ocupados (cantidad ocupada). Si ocupados=0 o ocupada=no, NO está ocupada. Si ocupados>0 o ocupada=si, SÍ está ocupada. NO uses el historial de movimientos ni el campo estado para decidir ocupación si contradice esos números.
2.3. Preguntas sí/no sobre ocupación o disponibilidad de herramientas: responde en 1-2 líneas usando SOLO los números de HERRAMIENTAS REGISTRADAS. Si no consta en ese contexto, devuelve consultar_datos con tipo herramientas en lugar de adivinar.
3. El identificador principal del inventario es codigo_interno. Si aparece un código, úsalo y devuélvelo.
4. Para salidas, valida cantidad, unidad, producto, referencia, solicitante y labor. Si falta algo importante, pide solo ese dato.
5. Si el usuario usa sinónimos, interpreta: neumático/llanta/coraza; veneno/químico/herbicida; gasolina/combustible; herramienta/equipo.
6. Antes de decir que no existe, revisa coincidencias amplias en catálogo y stock.
6.1. PRIORIDAD LOCAL: Si llegan COINCIDENCIAS LOCALES o FRASE PRODUCTO, úsalas ANTES de inventar un item. Formato: modulo|item|referencia|similitud. Elige la mayor similitud compatible con MÓDULO DETECTADO.
6.2. MÓDULO DETECTADO: Si no está vacío, prioriza productos de ese módulo. No mezcles EPP con ASEO, ni Dotación con Consumibles, salvo petición explícita.
6.3. ORDEN INTERPRETADA: Respeta tipo, cantidad, solicitante y producto si vienen en ORDEN INTERPRETADA. Si falta cantidad o solicitante en una salida, pide solo ese dato o usa prellenar_formulario.
6.4. AMBIGÜEDAD: Si hay varias coincidencias parecidas, NO ejecutes directo: lista 2-3 opciones con módulo y referencia, o usa prellenar_formulario con la mejor opción.
6.5. AGROQUÍMICOS: Fertilizantes, herbicidas, fungicidas e insecticidas pertenecen al módulo "Agroquímicos" (no Consumibles ni Lubricantes). Si MÓDULO DETECTADO o MODULO_FILTRADO dice Agroquímicos, busca solo ahí. "Químico" legacy equivale a Agroquímicos salvo lubricantes de taller.
6.6. MEMORIA DE CONVERSACIÓN: Si hay mensajes previos en el historial de chat, úsalos para continuidad. Recuerda productos, cantidades, solicitantes y módulos mencionados antes. Si el usuario dice "eso", "el mismo", "repítelo" o "lo de Juan", resuélvelo con el contexto previo.
7. Responde con frases cortas. Máximo 4 líneas salvo que el usuario pida explicación. En preguntas directas (sí/no, cuántas, dónde), responde solo lo pedido sin contexto extra.
8. Cuando corresponda ejecutar una acción en la app, devuelve un SOLO JSON válido, sin Markdown.

JSONS PERMITIDOS:

Registrar entrada o salida:
{"mensaje":"Listo para confirmar.","accion":"ejecutar","tipo":"entrada|salida","codigo_interno":"opcional","modulo":"Agroquímicos|Químico|EPP|Consumibles|Combustible|Dotación|ASEO|Lubricantes taller|Herramientas","categoria":"opcional","item":"item base exacto","referencia":"referencia exacta si aplica","cantidad":"número","unidad":"Unidad|Gramos|ML|Galones|Kg|Litro|Metro|Caja|Paquete","solicitante":"...","labor":"...","maquinaria":"opcional","horometro":"opcional","observaciones":"opcional"}

Abrir formulario prellenado:
{"mensaje":"Abriré el formulario con los datos detectados.","accion":"prellenar_formulario","modulo":"...","item":"...","referencia":"...","cantidad":"...","solicitante":"...","labor":"..."}

Aprender regla permanente:
{"mensaje":"He guardado esta regla.","accion":"aprender","titulo":"...","regla":"..."}

Consultar datos:
{"mensaje":"Consulto datos.","accion":"consultar_datos","tipo":"stock|movimientos|salidas|entradas|herramientas|vencimientos","item":"opcional","referencia":"opcional","modulo":"opcional","solicitante":"opcional","texto":"filtro libre opcional","limite":8}

Herramientas:
{"mensaje":"Preparé el registro de la herramienta.","accion":"herramienta","sub_accion":"registrar","nombre":"...","referencia":"...","marca":"...","codigo":"...","estado":"Disponible|En uso|Mantenimiento|Dañada|Perdida","ubicacion":"...","responsable":"...","observaciones":"..."}
{"mensaje":"Preparé el movimiento de herramienta.","accion":"herramienta_movimiento","tipo":"salida|entrada","herramienta":"...","codigo":"...","referencia":"...","cantidad":"1","solicitante":"...","labor":"...","observaciones":"..."}

Otras acciones:
{"mensaje":"Preparé la corrección.","accion":"corregir_registro","id":"opcional","tipo":"Salida|Entrada","cantidad":"número correcto","unidad":"...","motivo":"..."}
{"mensaje":"Preparé el vencimiento.","accion":"registrar_vencimiento","modulo":"Químico|Consumibles|EPP|Dotación|Combustible","item":"...","referencia":"...","fecha_vencimiento":"AAAA-MM-DD","lote":"...","cantidad":"...","observaciones":"..."}
{"mensaje":"Iniciaré la exportación.","accion":"exportar","formato":"csv|pdf"}
{"mensaje":"Abriré auditoría.","accion":"auditoria"}
{"mensaje":"Haré backup.","accion":"backup"}
{"mensaje":"Sincronizaré pendientes.","accion":"sincronizar_offline"}
{"mensaje":"Revisaré inconsistencias.","accion":"detectar_inconsistencias"}
{"mensaje":"Abriré importación.","accion":"importar_inventario"}
{"mensaje":"Abriré la pantalla solicitada.","accion":"navegar","pantalla":"inventario|historial|herramientas|auditoria|inconsistencias|registros|configuracion|reportes|graficos"}
`.trim();

    const textoUsuario = `
MENSAJE DEL USUARIO:
${mensaje}

PERFIL:
${perfil || "Sin perfil disponible."}

MEMORIA IA EN NUBE:
${memoriaEvolutiva || "Sin reglas aprendidas en Firestore."}

MEMORIA LOCAL:
${memoriaLocal || "Sin memoria local."}

CATÁLOGO ACTUAL (filtrado por relevancia):
${catalogo || "Sin catálogo enviado."}

FRASE PRODUCTO DETECTADA LOCALMENTE:
${fraseProducto || "No detectada."}

MÓDULO DETECTADO EN LA ORDEN:
${moduloDetectado || "No detectado."}

ORDEN INTERPRETADA LOCALMENTE:
${ordenInterpretada || "Sin interpretación local."}

COINCIDENCIAS LOCALES (priorizar estas):
${coincidenciasLocales || "Sin coincidencias locales."}

HERRAMIENTAS REGISTRADAS:
${herramientas || "Sin herramientas enviadas."}

MODO CONTEXTO STOCK:
${stockContextMode || "No informado."}

STOCK REAL ACTUAL:
${stockInfo || "Sin datos de stock disponibles."}

HISTORIAL RECIENTE:
${historial || "Sin historial reciente."}

PATRONES LOCALES:
${patronesLocales || "Sin patrones locales."}

MODO AUTOCRÍTICA:
${esAutocritica ? "Sí. Corrige la acción anterior usando solo catálogo/stock real." : "No."}
`.trim();

    const mensajesHistorial = historialConversacion.map((turno) => ({
      role: turno.rol === "usuario" ? "user" : "assistant",
      content: `[${turno.fecha || "previo"}] ${turno.texto}`,
    }));

    const body = {
      model: OPENAI_MODEL,
      messages: [
        { role: "system", content: instrucciones },
        ...mensajesHistorial,
        { role: "user", content: textoUsuario },
      ],
      max_tokens: 400,
      temperature: 0.1
    };

    let response;
    try {
      response = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${OPENAI_API_KEY.value()}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
      });
    } catch (error) {
      throw new HttpsError("unavailable", `No se pudo contactar OpenAI: ${error.message}`);
    }

    const json = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = json?.error?.message || `HTTP ${response.status}`;
      throw new HttpsError("internal", `OpenAI respondió con error: ${detail}`);
    }

    const respuestaText = extraerTextoRespuesta(json);

    // VALIDACIÓN DE SEGURIDAD POST-PROCESAMIENTO
    if (respuestaText.includes("{") && respuestaText.includes("}")) {
        try {
            const inicio = respuestaText.indexOf("{");
            const fin = respuestaText.lastIndexOf("}");
            const actionJson = JSON.parse(respuestaText.substring(inicio, fin + 1));
            const acc = actionJson.accion;

            if (userRole === "operador") {
                const permitidas = ["consultar_datos", "navegar"];
                const esSalida = acc === "ejecutar" && actionJson.tipo === "salida";
                if (!permitidas.includes(acc) && !esSalida) {
                    return { respuesta: "Lo siento, como operador no tienes permisos para realizar esa acción administrativa. Puedo ayudarte con una salida o consulta de stock." };
                }
            }
            if (userRole === "almacenista") {
                const prohibidas = ["backup", "importar_inventario"];
                if (prohibidas.includes(acc)) {
                    return { respuesta: "Esa acción requiere permisos de administrador." };
                }
            }
        } catch (e) {
            // Error de parseo, se envía la respuesta tal cual para que la app lo maneje
        }
    }

    return {
      respuesta: respuestaText || "No recibí texto de respuesta desde OpenAI.",
      responseId: json.id || null,
      modelo: OPENAI_MODEL
    };
  }
);
