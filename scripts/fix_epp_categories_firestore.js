#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const admin = require("../shared/firebase-functions/node_modules/firebase-admin");

const rootDir = path.resolve(__dirname, "..");
const firebaseRcPath = path.join(rootDir, ".firebaserc");

function projectIdFromFirebaseRc() {
  try {
    const parsed = JSON.parse(fs.readFileSync(firebaseRcPath, "utf8"));
    return parsed.projects && parsed.projects.default;
  } catch (_) {
    return undefined;
  }
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

function isHeaderDoc(data, id) {
  const code = String(data.codigo_interno || id || "").toUpperCase();
  const item = String(data.item || "").toUpperCase();
  return code === "CODIGOINTERNO" || item === "ITEM";
}

async function main() {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: projectIdFromFirebaseRc() || "arles-gestion",
  });

  const db = admin.firestore();
  const collections = ["existencias", "catalogo_personalizado"];
  let updated = 0;
  let deleted = 0;

  for (const collectionName of collections) {
    const snapshot = await db.collection(collectionName).where("modulo", "==", "EPP").get();
    const batch = db.batch();
    let batchOps = 0;

    for (const doc of snapshot.docs) {
      const data = doc.data() || {};
      if (isHeaderDoc(data, doc.id)) {
        batch.delete(doc.ref);
        batchOps += 1;
        deleted += 1;
        continue;
      }

      const item = data.item || "";
      const reference = data.referenciaCatalogo || data.referencia || "";
      const currentCategory = data.categoria || "";
      const fixedCategory = resolveEppCategory(item, reference, currentCategory);
      if (fixedCategory !== currentCategory) {
        batch.update(doc.ref, { categoria: fixedCategory });
        batchOps += 1;
        updated += 1;
      }
    }

    if (batchOps > 0) await batch.commit();
  }

  console.log(`Categorias actualizadas: ${updated}`);
  console.log(`Registros de encabezado eliminados: ${deleted}`);
}

main().catch((error) => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});

