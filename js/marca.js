// v6: panel "Acerca de" (botón ?) y el modo meme (botón del ojo, o 5 toques
// seguidos al logo). El meme se guarda y se aplica recargando: el patrocinio
// (js/patrocinio.js) se engancha al arranque de app.js.
const $ = (id) => document.getElementById(id);
const panel = $("panelAcerca");
const CLAVE = "trenAR.meme";

const abrir = () => panel.classList.remove("ocultar");
const cerrar = () => panel.classList.add("ocultar");
$("acercaBtn").addEventListener("click", abrir);
$("cerrarAcerca").addEventListener("click", cerrar);
panel.addEventListener("click", (e) => e.target === panel && cerrar());
document.addEventListener("keydown", (e) => e.key === "Escape" && cerrar());

function memeActivo() {
  try {
    return localStorage.getItem(CLAVE) === "1";
  } catch {
    return false;
  }
}

// La URL ya tiene ?o=&d= (app.js), así que al recargar vuelve al mismo viaje.
function alternarMeme() {
  try {
    localStorage.setItem(CLAVE, memeActivo() ? "0" : "1");
  } catch {
    return;
  }
  location.reload();
}

const boton = $("memeBtn");
boton.setAttribute("aria-pressed", String(memeActivo()));
boton.addEventListener("click", alternarMeme);

let toques = 0;
let ultimo = 0;
document.querySelector(".cabecera .marca").addEventListener("click", () => {
  const ahora = Date.now();
  toques = ahora - ultimo < 1500 ? toques + 1 : 1;
  ultimo = ahora;
  if (toques >= 5) alternarMeme();
});
