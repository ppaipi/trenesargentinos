// Panel "Otras líneas (programado)" para v5.
// No usa la API de arribos: estas líneas (Urquiza/Metrovías, Belgrano Norte/
// Ferrovías) no las opera Trenes Argentinos y no hay fuente en vivo pública.
// Mostramos cabeceras + frecuencia según horario oficial, siempre etiquetado
// como programado (no en vivo), con link a la fuente.
import { LINEAS_PROGRAMADAS } from "./programados-data.js";

const DIAS = [
  ["habiles", "Días hábiles"],
  ["sabados", "Sábados"],
  ["domingos", "Domingos y feriados"],
];

function filaHorario(nombreDia, h) {
  const li = document.createElement("li");
  li.className = "prog-dia";
  const dia = document.createElement("span");
  dia.className = "prog-dia-nombre";
  dia.textContent = nombreDia;
  li.append(dia);

  const detalle = document.createElement("span");
  detalle.className = "prog-dia-detalle";
  if (h && typeof h === "object" && !Array.isArray(h)) {
    // h: { cabecera: [primero, ultimo] }
    detalle.textContent = Object.entries(h)
      .map(([cab, [ini, fin]]) => `${cab}: ${ini}–${fin}`)
      .join(" · ");
  } else {
    detalle.textContent = h || "—";
  }
  li.append(detalle);
  return li;
}

function crearTarjeta(ln) {
  const card = document.createElement("article");
  card.className = "prog-card";
  card.style.setProperty("--color-linea", ln.color);

  const cab = document.createElement("div");
  cab.className = "prog-cabecera";
  cab.innerHTML =
    `<span class="prog-punto"></span>` +
    `<h3>${ln.linea}</h3>` +
    `<span class="prog-operador">${ln.operador}</span>` +
    `<span class="prog-badge">programado · no en vivo</span>`;
  card.append(cab);

  const recorrido = document.createElement("p");
  recorrido.className = "prog-recorrido";
  recorrido.textContent = ln.cabeceras.join(" ↔ ");
  if (ln.trayectoMin) recorrido.textContent += ` · ${ln.trayectoMin} min de punta a punta`;
  card.append(recorrido);

  const ul = document.createElement("ul");
  ul.className = "prog-lista";
  for (const [clave, nombre] of DIAS) {
    const horas = ln.horarios?.[clave] ?? null;
    const frec = ln.frecuenciaMin?.[clave] ?? null;
    if (horas) {
      ul.append(filaHorario(nombre, horas));
    } else if (frec) {
      ul.append(filaHorario(nombre, `cada ${frec} min`));
    }
  }
  if (!ul.children.length) {
    // Sin horas ni frecuencia por día: mostramos lo que haya (ej. frecuencia global)
    const li = document.createElement("li");
    li.className = "prog-dia";
    li.textContent = "Consultá el horario oficial (abajo).";
    ul.append(li);
  }
  card.append(ul);

  const pie = document.createElement("p");
  pie.className = "prog-fuente";
  const vig = ln.vigencia ? ` · vigente desde ${ln.vigencia}` : "";
  pie.innerHTML =
    `Horario oficial de <a href="${ln.fuente.url}" rel="noopener" target="_blank">${ln.fuente.nombre}</a>${vig}. ` +
    `Los horarios programados no confirman circulación.`;
  card.append(pie);

  return card;
}

export function montarProgramados() {
  const seccion = document.getElementById("programados");
  if (!seccion) return;
  const cont = seccion.querySelector(".prog-contenedor");
  cont.replaceChildren(...LINEAS_PROGRAMADAS.map(crearTarjeta));
  seccion.classList.remove("ocultar");
}

montarProgramados();
