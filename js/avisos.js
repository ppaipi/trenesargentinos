import { avisos as pedirAvisos } from "./api.js";
import { COLOR_LINEA } from "./estaciones.js";
import { notificar } from "./extras.js";

const INTERVALO_MS = 5 * 60 * 1000;
const CLAVE_VISTOS = "trenesArgentinos.v2.avisosVistos"; // compartida con v2: no repite notificaciones

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

/** Avisos de las lineas de una estacion (generales o de esa estacion), el mas grave primero. */
export function avisosDeEstacion(estacion) {
  const lineas = new Set(estacion.rutas.map((r) => r.linea));
  return datos
    .filter((g) => lineas.has(g.linea))
    .flatMap((g) => g.avisos.filter((a) => a.estacionId == null || a.estacionId === estacion.id).map((a) => ({ linea: g.linea, aviso: a })))
    .sort((x, y) => x.aviso.criticidad - y.aviso.criticidad);
}

// Cartel flotante arriba al elegir una estacion: el aviso mas grave de sus
// lineas. Se va solo a los 5 s; tocarlo abre el panel de avisos. Igual que la app.
let timerCartel;
export function mostrarAvisoEstacion(estacion) {
  let cartel = document.getElementById("avisoEstacion");
  if (!cartel) {
    cartel = document.createElement("div");
    cartel.id = "avisoEstacion";
    cartel.className = "aviso-estacion";
    cartel.setAttribute("role", "status");
    cartel.setAttribute("aria-live", "polite");
    document.body.appendChild(cartel);
  }
  clearTimeout(timerCartel);
  const lista = avisosDeEstacion(estacion);
  if (!lista.length) return cartel.classList.remove("visible");

  const { linea, aviso } = lista[0];
  const item = itemAviso(aviso, linea);
  item.classList.add("aviso-estacion-item");
  if (lista.length > 1) item.append(Object.assign(document.createElement("span"), { className: "aviso-mas", textContent: `+${lista.length - 1} avisos` }));
  const cerrar = Object.assign(document.createElement("button"), { type: "button", className: "boton-icono aviso-cerrar", textContent: "✕" });
  cerrar.setAttribute("aria-label", "Cerrar");
  cerrar.addEventListener("click", (e) => {
    e.stopPropagation();
    cartel.classList.remove("visible");
  });
  cartel.replaceChildren(item, cerrar);
  cartel.onclick = () => {
    cartel.classList.remove("visible");
    abrirPanelAvisos(linea);
  };
  cartel.classList.add("visible");
  timerCartel = setTimeout(() => cartel.classList.remove("visible"), 5000);
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
  nuevos.slice(0, 3).forEach((a) => notificar(`Aviso · ${a.linea}`, a.texto, { tag: `aviso-servicio-${a.id}` }));
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

export function iniciarAvisos({ contenedorEstado = null, lineasDeInteres, onActualizar }) {
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
      if (!panel.classList.contains("ocultar")) renderPanel(); // abierto antes de que llegaran (?ir=avisos)
      if (contenedorEstado) renderEstado(contenedorEstado);
      notificarNuevos(lineasDeInteres());
      alActualizar();
    } catch (error) {
      console.warn("No se pudieron cargar los avisos:", error.message);
    }
  }
  ciclo();
  setInterval(() => !document.hidden && ciclo(), INTERVALO_MS);
}
