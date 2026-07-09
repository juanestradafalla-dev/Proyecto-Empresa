const admin = require("firebase-admin");

const EMAIL = (process.argv[2] || "almacen@arlessas.com").toLowerCase();
const ROL = process.argv[3] || "almacenista";

admin.initializeApp({ projectId: "arles-gestion" });

async function main() {
  const authUser = await admin.auth().getUserByEmail(EMAIL);
  const perfil = {
    email: EMAIL,
    rol: ROL,
    activo: true,
    estado: "activo",
    autorizado_en: new Date().toISOString(),
    autorizado_por: "script_admin",
  };
  await admin.firestore().collection("usuarios").doc(authUser.uid).set(perfil, { merge: true });
  console.log(`OK uid=${authUser.uid} email=${EMAIL} rol=${ROL}`);
}

main().catch((error) => {
  console.error("ERROR:", error.message);
  process.exit(1);
});