import {
  LINEAS_PRINCIPALES,
  COLOR_LINEA,
  ramalesDeLinea,
  estacionesDeRuta,
  colorDeEstacion,
  lineasDeEstacion,
  buscarPorTexto,
  formatearDistancia,
} from "./estaciones.js";

/**
 * Buscador de estaciones: texto libre + navegacion linea → ramal → estaciones
 * (en orden geografico, desde la cabecera). Si la otra estacion ya esta
 * elegida, arranca mostrando solo los ramales que comparte con ella.
 */
export function crearBuscadorEstacion({ input, panel, chips, lista, obtenerOtra, onSeleccionar }) {
  let activo = -1;
  let pila = [];

  const abrir = () => panel.classList.remove("ocultar");
  const cerrar = () => panel.classList.add("ocultar");

  function boton(texto, clase, alElegir) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = clase;
    b.textContent = texto;
    // mousedown + preventDefault: elige sin que el input pierda el foco antes.
    // stopPropagation: el re-render saca el boton del DOM y el listener
    // global de "click afuera" cerraria el panel.
    b.addEventListener("mousedown", (e) => {
      e.preventDefault();
      e.stopPropagation();
      alElegir();
    });
    return b;
  }

  function prepararVista(conVolver) {
    lista.innerHTML = "";
    chips.innerHTML = "";
    activo = -1;
    if (conVolver && pila.length) chips.appendChild(boton("← Volver", "chip chip-volver", volver));
  }

  function renderEstaciones(items, { detalle, numerar } = {}) {
    prepararVista(true);

    if (items.length === 0) {
      const li = document.createElement("li");
      li.className = "sin-resultados";
      li.textContent = "No se encontraron estaciones";
      lista.appendChild(li);
      abrir();
      return;
    }

    items.slice(0, 60).forEach((item, i) => {
      const estacion = item.estacion || item;
      const li = document.createElement("li");
      li.setAttribute("role", "option");

      const badge = document.createElement("span");
      badge.className = "badge-linea";
      badge.style.background = colorDeEstacion(estacion);
      badge.title = lineasDeEstacion(estacion);

      const nombre = document.createElement("span");
      nombre.className = "nombre-estacion";
      nombre.textContent = numerar ? `${i + 1}. ${estacion.nombre}` : estacion.nombre;

      const extra = document.createElement("span");
      extra.className = "linea-nombre";
      extra.style.marginLeft = "auto";
      extra.textContent = detalle ? detalle(item) : lineasDeEstacion(estacion);

      li.append(badge, nombre, extra);
      li.addEventListener("mousedown", (e) => {
        e.preventDefault();
        e.stopPropagation();
        seleccionar(estacion);
      });
      lista.appendChild(li);
    });
    abrir();
  }

  function renderChips(opciones, etiqueta, alElegir, color) {
    prepararVista(true);
    opciones.forEach((op) => {
      const chip = boton(etiqueta(op), "chip", () => alElegir(op));
      if (color) chip.style.boxShadow = `inset 4px 0 0 ${color(op)}`;
      chips.appendChild(chip);
    });
    abrir();
  }

  function vistaInicial() {
    pila = [];
    const otra = obtenerOtra();
    if (otra) mostrarRamalesDe(otra);
    else mostrarLineas();
  }

  function mostrarLineas() {
    renderChips(
      LINEAS_PRINCIPALES,
      (l) => l,
      (linea) => {
        pila.push(mostrarLineas);
        mostrarRamales(linea);
      },
      (l) => COLOR_LINEA[l]
    );
  }

  function mostrarRamales(linea) {
    const ramales = ramalesDeLinea(linea);
    if (ramales.length === 1) return mostrarEstaciones(linea, ramales[0]);
    renderChips(ramales, (r) => r, (ramal) => {
      pila.push(() => mostrarRamales(linea));
      mostrarEstaciones(linea, ramal);
    });
  }

  function mostrarRamalesDe(otra) {
    if (otra.rutas.length === 1) return mostrarEstaciones(otra.rutas[0].linea, otra.rutas[0].ramal);
    renderChips(
      otra.rutas,
      (r) => `${r.ramal} · ${r.linea}`,
      (ruta) => {
        pila.push(() => mostrarRamalesDe(otra));
        mostrarEstaciones(ruta.linea, ruta.ramal);
      },
      (r) => COLOR_LINEA[r.linea]
    );
  }

  function mostrarEstaciones(linea, ramal) {
    const otra = obtenerOtra();
    const items = estacionesDeRuta(linea, ramal).filter((e) => !otra || e.id !== otra.id);
    renderEstaciones(items, { numerar: true, detalle: () => "" });
  }

  function volver() {
    const anterior = pila.pop();
    if (anterior) anterior();
    else vistaInicial();
  }

  function seleccionar(estacion) {
    input.value = estacion.nombre;
    input.dataset.id = estacion.id;
    cerrar();
    onSeleccionar(estacion);
  }

  input.addEventListener("input", () => {
    delete input.dataset.id;
    onSeleccionar(null);
    const texto = input.value.trim();
    if (!texto) vistaInicial();
    else {
      pila = [];
      renderEstaciones(buscarPorTexto(texto));
    }
  });

  input.addEventListener("focus", () => {
    input.select();
    vistaInicial();
  });

  input.addEventListener("keydown", (e) => {
    const items = lista.querySelectorAll("li:not(.sin-resultados)");
    if (panel.classList.contains("ocultar") || items.length === 0) return;

    if (e.key === "ArrowDown") activo = (activo + 1) % items.length;
    else if (e.key === "ArrowUp") activo = (activo - 1 + items.length) % items.length;
    else if (e.key === "Enter") {
      e.preventDefault();
      items[Math.max(activo, 0)].dispatchEvent(new Event("mousedown"));
      return;
    } else if (e.key === "Escape") return cerrar();
    else return;

    e.preventDefault();
    items.forEach((li) => li.classList.remove("activo"));
    items[activo].classList.add("activo");
    items[activo].scrollIntoView({ block: "nearest" });
  });

  document.addEventListener("mousedown", (e) => {
    if (!input.parentElement.contains(e.target) && !panel.contains(e.target)) cerrar();
  });

  return {
    seleccionar,
    cerrar,
    mostrarCercanas(items) {
      pila = [];
      renderEstaciones(items, { detalle: (it) => formatearDistancia(it.km) });
      chips.prepend(Object.assign(document.createElement("span"), {
        className: "linea-nombre",
        textContent: "Estaciones cerca tuyo",
      }));
    },
  };
}
