import { actualizarMapa, eliminarMapa } from "../mapa.js";

const fmtHora = new Intl.DateTimeFormat("es-AR", { hour: "2-digit", minute: "2-digit", hour12: false });

export const hora = (d) => (d ? fmtHora.format(d) : "--:--");

export function duracion(min) {
  if (min == null || min < 0) return "";
  if (min < 60) return `${min} min`;
  return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, "0")}`;
}

// Cuenta regresiva con segundos siempre: MM:SS, o H:MM:SS si falta mas de 1 h.
export function cuentaRegresiva(referencia, ahora = Date.now()) {
  const seg = Math.floor((referencia - ahora) / 1000);
  if (seg < -120) return { num: hora(referencia), uni: "ya pasó" }; // horarios programados de antes
  if (seg <= 0) return { num: "YA", uni: "en andén", ya: true };
  const dos = (n) => String(n).padStart(2, "0");
  const h = Math.floor(seg / 3600);
  const m = Math.floor((seg % 3600) / 60);
  const s = seg % 60;
  if (h >= 24) return { num: hora(referencia), uni: referencia.toLocaleDateString("es-AR", { weekday: "short" }) };
  if (h > 0) return { num: `${h}:${dos(m)}:${dos(s)}`, uni: "h  min  seg" };
  return { num: `${dos(m)}:${dos(s)}`, uni: "min  seg" };
}

function el(tag, clase, texto) {
  const e = document.createElement(tag);
  if (clase) e.className = clase;
  if (texto != null) e.textContent = texto;
  return e;
}

/**
 * Tarjeta de un tren. Se crea una vez y despues solo se actualizan sus
 * textos (asi no titila ni se reinicia el mapa en cada sondeo).
 */
export function crearTarjeta({ color, linea, onAvisar }) {
  const div = el("article", "horario");
  div.style.setProperty("--color-linea", color);

  const fila = el("div", "horario-fila");
  const titulo = el("div");
  const ramal = el("p", "ramal");
  const sub = el("span", "linea-nombre");
  titulo.append(ramal, sub);
  const cuenta = el("div", "cuenta");
  const cuentaNum = el("span", "cuenta-num");
  const cuentaUni = el("span", "cuenta-uni");
  cuenta.append(cuentaNum, cuentaUni);
  fila.append(titulo, cuenta);

  const horas = el("div", "horas");
  const colOrigen = el("div");
  const horaOrigen = el("div", "hora");
  const estOrigen = el("div", "est");
  colOrigen.append(horaOrigen, estOrigen);
  const flecha = el("div", "flecha");
  const colDestino = el("div");
  const horaDestino = el("div", "hora");
  const estDestino = el("div", "est");
  colDestino.append(horaDestino, estDestino);
  horas.append(colOrigen, flecha, colDestino);

  const etiquetas = el("div", "etiquetas");
  const acciones = el("div", "acciones-tarjeta");
  const botonAviso = el("button", "boton-secundario", "Avisame");
  botonAviso.type = "button";
  const botonMapa = el("button", "boton-secundario ocultar", "Ver mapa");
  botonMapa.type = "button";
  acciones.append(botonAviso, botonMapa);
  const mapaDiv = el("div", "mapa ocultar");

  div.append(fila, horas, etiquetas, acciones, mapaDiv);

  const tarjeta = { div, datos: null, estadoMapa: { map: null, marker: null }, aviso: false };

  botonAviso.addEventListener("click", () => onAvisar(tarjeta));
  botonMapa.addEventListener("click", () => {
    const abrir = mapaDiv.classList.contains("ocultar");
    mapaDiv.classList.toggle("ocultar", !abrir);
    botonMapa.textContent = abrir ? "Ocultar mapa" : "Ver mapa";
    if (abrir && tarjeta.datos?.ubicacion) {
      const { lat, lon } = tarjeta.datos.ubicacion;
      actualizarMapa(tarjeta.estadoMapa, mapaDiv, lat, lon);
      tarjeta.estadoMapa.map.invalidateSize();
    }
  });

  tarjeta.actualizar = (t) => {
    tarjeta.datos = t;
    ramal.textContent = t.ramal;
    sub.textContent = `${linea ? linea + " · " : ""}${t.esCabecera ? "sale de" : "llega a"} ${t.estacionOrigen}`;
    horaOrigen.textContent = hora(t.referencia);
    estOrigen.textContent = t.estacionOrigen;
    horaDestino.textContent = hora(t.llegadaDestino);
    estDestino.textContent = t.estacionDestino;
    flecha.textContent = t.duracionMin != null ? `${duracion(t.duracionMin)} →` : "→";

    etiquetas.replaceChildren();
    etiquetas.append(el("span", t.enVivo ? "etiqueta vivo" : "etiqueta", t.enVivo ? "En vivo" : "Programado"));
    if (t.demoraMin != null && t.demoraMin >= 2) {
      const e = el("span", `etiqueta demora${t.demoraMin >= 10 ? " grave" : ""}`, `Demora +${t.demoraMin} min`);
      e.title = `Programado ${hora(t.programada)}`;
      etiquetas.append(e);
    } else if (t.demoraMin != null && t.enVivo) {
      etiquetas.append(el("span", "etiqueta", "A horario"));
    }
    if (t.anden) etiquetas.append(el("span", "etiqueta anden", `Andén ${t.anden}`));

    botonMapa.classList.toggle("ocultar", !t.ubicacion);
    if (t.ubicacion && tarjeta.estadoMapa.map) {
      actualizarMapa(tarjeta.estadoMapa, mapaDiv, t.ubicacion.lat, t.ubicacion.lon);
    }
    tarjeta.tick();
  };

  tarjeta.tick = (ahora = Date.now()) => {
    if (!tarjeta.datos) return;
    const c = cuentaRegresiva(tarjeta.datos.referencia, ahora);
    cuentaNum.textContent = c.num;
    cuentaUni.textContent = c.uni;
    cuenta.classList.toggle("ya", Boolean(c.ya));
  };

  tarjeta.marcarAviso = (activo) => {
    tarjeta.aviso = activo;
    botonAviso.textContent = activo ? "Aviso activo" : "Avisame";
    botonAviso.setAttribute("aria-pressed", String(activo));
  };

  tarjeta.destruir = () => {
    eliminarMapa(tarjeta.estadoMapa);
    div.remove();
  };

  return tarjeta;
}

export function skeletons(contenedor, n = 3) {
  contenedor.replaceChildren(...Array.from({ length: n }, () => el("div", "skeleton")));
}

export function mensaje(contenedor, texto, accion) {
  const p = el("div", "mensaje-vacio");
  p.append(el("p", null, texto));
  if (accion) {
    const b = el("button", "boton-secundario", accion.texto);
    b.type = "button";
    b.style.marginTop = "10px";
    b.addEventListener("click", accion.onClick);
    p.append(b);
  }
  contenedor.replaceChildren(p);
}
