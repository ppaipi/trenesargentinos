// v6: panel "Acerca de" (botón ?) y el easter egg de la marca: 5 toques
// seguidos al logo llevan al patrocinio meme (como en la app Android).
const $ = (id) => document.getElementById(id);
const panel = $("panelAcerca");

const abrir = () => panel.classList.remove("ocultar");
const cerrar = () => panel.classList.add("ocultar");
$("acercaBtn").addEventListener("click", abrir);
$("cerrarAcerca").addEventListener("click", cerrar);
panel.addEventListener("click", (e) => e.target === panel && cerrar());
document.addEventListener("keydown", (e) => e.key === "Escape" && cerrar());

// Mantiene el viaje actual al ir al patrocinio (app.js deja ?o=&d= en la URL).
function irAlMeme() {
  location.href = `patrocinador.html${location.search}`;
}
for (const id of ["acercaMeme", "memeBtn"]) $(id).addEventListener("click", (e) => {
  e.preventDefault();
  irAlMeme();
});

let toques = 0;
let ultimo = 0;
document.querySelector(".cabecera .marca").addEventListener("click", () => {
  const ahora = Date.now();
  toques = ahora - ultimo < 1500 ? toques + 1 : 1;
  ultimo = ahora;
  if (toques >= 5) irAlMeme();
});
