const admin = require("../shared/firebase-functions/node_modules/firebase-admin");
const fs = require("fs");

const credentialPath = "C:/Users/Almacen/AppData/Roaming/firebase/archivo-credencial.json";

admin.initializeApp({
  credential: admin.credential.cert(require(credentialPath)),
  projectId: "arles-gestion"
});

const db = admin.firestore();

async function audit() {
  console.log("Auditando colecciones para el modulo 'DotaciÃ³n'...");

  const collections = ["existencias", "catalogo_personalizado"];

  for (const colName of collections) {
    console.log(`\n--- ColecciÃ³n: ${colName} ---`);
    const snapshot = await db.collection(colName).where("modulo", "==", "DotaciÃ³n").get();

    const categories = {};
    snapshot.docs.forEach(doc => {
      const data = doc.data();
      const cat = data.categoria || "SIN CATEGORIA";
      if (!categories[cat]) categories[cat] = [];
      categories[cat].push({ id: doc.id, item: data.item });
    });

    for (const [cat, items] of Object.entries(categories)) {
      console.log(`CategorÃ­a: ${cat} (${items.length} items)`);
      if (items.length < 5) {
        items.forEach(it => console.log(`  - [${it.id}] ${it.item}`));
      }
    }
  }
}

audit().catch(console.error);

