import { avisos as pedirAvisos } from "./api.js";
import { COLOR_LINEA } from "./estaciones.js";
import { notificar } from "./extras.js";

const INTERVALO_MS = 5 * 60 * 1000;
const CLAVE_VISTOS = "trenesArgentinos.v2.avisosVistos";

let datos = []; // [{linea, avisos: [...]}]
let alActualizar = () => {};

const nivel = (c) => (c <= 1 ? "grave" : c <= 3 ? "medio" : "info");

function vistos() {
  try {
    return new Set(JSON.parse(localStorage.getItem(CLAVE_VISTOS) || "[]"));
  } catch {
    return new Set();
  }
}

function guardarVistos(set) {
  try {
    localStorage.setItem(CLAVE_VISTOS, JSON.stringify([...set].slice(-300)));
  } catch {
    /* sin almacenamiento: puede repetir avisos, no es grave */
  }
}

function itemAviso(a, linea) {
  const li = document.createElement("li");
  li.className = `aviso-item ${nivel(a.criticidad)}`;
  if (linea) {
    const tag = document.createElement("span");
    tag.className = "aviso-linea";
    tag.style.setProperty("--color-linea", COLOR_LINEA[linea] || "var(--azul)");
    tag.textContent = linea;
    li.append(tag);
  }
  const p = document.createElement("span");
  p.textContent = a.texto;
  li.append(p);
  return li;
}

/** Tira "Estado del servicio": una pastilla por linea; al tocarla se despliegan. */
function renderEstado(contenedor) {
  contenedor.replaceChildren();
  if (!datos.length) {
    contenedor.classList.add("ocultar");
    return;
  }
  contenedor.classList.remove("ocultar");

  const titulo = document.createElement("h2");
  titulo.className = "titulo-seccion";
  titulo.textContent = "Estado del servicio";
  const fila = document.createElement("div");
  fila.className = "estado-lineas";
  const lista = document.createElement("ul");
  lista.className = "avisos-lista ocultar";

  let abierta = null;
  datos
    .filter((g) => COLOR_LINEA[g.linea] && g.linea !== "Regionales")
    .forEach((g) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "estado-linea";
      b.style.setProperty("--color-linea", COLOR_LINEA[g.linea]);
      const peor = g.avisos[0];
      b.dataset.nivel = peor ? nivel(peor.criticidad) : "ok";
      b.innerHTML = `<span class="punto"></span>`;
      b.append(document.createTextNode(g.linea));
      if (g.avisos.length) {
        const n = document.createElement("span");
        n.className = "cuenta-avisos";
        n.textContent = g.avisos.length;
        b.append(n);
      }
      b.title = g.avisos.length ? `${g.avisos.length} aviso(s)` : "Sin avisos";
      b.setAttribute("aria-expanded", "false");
      b.addEventListener("click", () => {
        const cerrar = abierta === g.linea;
        abierta = cerrar ? null : g.linea;
        fila.querySelectorAll(".estado-linea").forEach((x) => x.setAttribute("aria-expanded", "false"));
        b.setAttribute("aria-expanded", String(!cerrar));
        lista.replaceChildren(
          ...(g.avisos.length ? g.avisos.map((a) => itemAviso(a)) : [itemAviso({ texto: "Funciona con normalidad.", criticidad: 9 })])
        );
        lista.classList.toggle("ocultar", cerrar);
      });
      fila.append(b);
    });

  contenedor.append(titulo, fila, lista);
}

/** Avisos que afectan un viaje: los de su linea y los de sus estaciones. */
export function avisosDelViaje(origen, destino, linea) {
  const g = datos.find((x) => x.linea === linea);
  if (!g) return [];
  const ids = new Set([origen.id, destino.id]);
  return g.avisos.filter((a) => a.estacionId == null || ids.has(a.estacionId));
}

export function renderAvisosViaje(contenedor, origen, destino, linea) {
  const lista = origen && destino ? avisosDelViaje(origen, destino, linea) : [];
  contenedor.replaceChildren(...lista.map((a) => itemAviso(a, linea)));
  contenedor.classList.toggle("ocultar", lista.length === 0);
}

// Notifica los avisos nuevos de las lineas que el usuario usa (favoritos y
// recientes). La primera vez solo los marca como vistos, sin notificar.
function notificarNuevos(lineasDeInteres) {
  const set = vistos();
  const primeraVez = set.size === 0;
  const nuevos = [];
  for (const g of datos) {
    for (const a of g.avisos) {
      if (set.has(a.id)) continue;
      set.add(a.id);
      if (!primeraVez && lineasDeInteres.has(g.linea)) nuevos.push({ ...a, linea: g.linea });
    }
  }
  guardarVistos(set);
  nuevos.slice(0, 3).forEach((a) => notificar(`Aviso · ${a.linea}`, a.texto));
}

/* ---------- Campana y panel ---------- */

function renderCampana() {
  const total = datos.reduce((n, g) => n + (COLOR_LINEA[g.linea] ? g.avisos.length : 0), 0);
  const badge = document.getElementById("campanaBadge");
  badge.textContent = total > 9 ? "9+" : String(total);
  badge.classList.toggle("ocultar", total === 0);
  const grave = datos.some((g) => g.avisos.some((a) => nivel(a.criticidad) === "grave"));
  badge.classList.toggle("grave", grave);
}

function renderPanel(lineaPrimero) {
  const cont = document.getElementById("panelAvisosLista");
  cont.replaceChildren();
  const grupos = datos
    .filter((g) => COLOR_LINEA[g.linea] && g.avisos.length)
    .sort((a, b) => (b.linea === lineaPrimero) - (a.linea === lineaPrimero));
  if (!grupos.length) {
    const p = document.createElement("p");
    p.className = "mensaje-vacio";
    p.textContent = datos.length ? "Sin avisos: todas las líneas funcionan con normalidad." : "Cargando avisos…";
    cont.append(p);
    return;
  }
  for (const g of grupos) {
    const ul = document.createElement("ul");
    ul.className = "avisos-lista";
    ul.append(...g.avisos.map((a) => itemAviso(a, g.linea)));
    cont.append(ul);
  }
}

export function abrirPanelAvisos(lineaPrimero) {
  renderPanel(lineaPrimero);
  document.getElementById("panelAvisos").classList.remove("ocultar");
  document.getElementById("cerrarAvisos").focus();
}

function cerrarPanelAvisos() {
  document.getElementById("panelAvisos").classList.add("ocultar");
}

export function iniciarAvisos({ contenedorEstado, lineasDeInteres, onActualizar }) {
  alActualizar = onActualizar || alActualizar;
  const panel = document.getElementById("panelAvisos");
  document.getElementById("campanaBtn").addEventListener("click", () => abrirPanelAvisos());
  document.getElementById("cerrarAvisos").addEventListener("click", cerrarPanelAvisos);
  panel.addEventListener("click", (e) => e.target === panel && cerrarPanelAvisos());
  document.addEventListener("keydown", (e) => e.key === "Escape" && cerrarPanelAvisos());

  async function ciclo() {
    try {
      datos = await pedirAvisos();
      renderCampana();
      renderEstado(contenedorEstado);
      notificarNuevos(lineasDeInteres());
      alActualizar();
    } catch (error) {
      console.warn("No se pudieron cargar los avisos:", error.message);
    }
  }
  ciclo();
  setInterval(() => !document.hidden && ciclo(), INTERVALO_MS);
}
