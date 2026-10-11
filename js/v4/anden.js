// Modo "estoy en el andén" y modo "a bordo": pantalla completa, legible de
// lejos, que no se apaga (Screen Wake Lock si existe; si no, anda igual).
// Andén: cuenta regresiva enorme del tren elegido + el siguiente en chico.
// A bordo ("Ya subí", o solo cuando el tren llega y el GPS se aleja del
// origen): última estación pasada, la próxima con cuenta regresiva y la lista
// hasta el destino. Posición por GPS proyectada sobre la vía; si no hay,
// estimada con las horas de la API (se refrescan consultando la próxima parada).

import { cuentaRegresiva, hora, progresoViaje, distanciaKm } from "./logica.js";
import { arribos, normalizarArribo } from "./api.js";
import { buscarEstacionPorId } from "./estaciones.js";
import { cargarRecorridos, elegirRuta } from "./mapa-tren.js";
import { escucharUbicacion } from "./ubicacion.js";
import { notificar } from "./extras.js";

const REFRESCO_BORDO_MS = 30000;
const SUBIDA_KM = 0.3; // con el tren ya en el andén, alejarse esto del origen = subió

let actual = null;

function el(tag, clase, texto) {
  const e = document.createElement(tag);
  if (clase) e.className = clase;
  if (texto != null) e.textContent = texto;
  return e;
}

async function pedirWakeLock() {
  if (!actual || document.visibilityState !== "visible" || !("wakeLock" in navigator)) return;
  const a = actual;
  try {
    const l = await navigator.wakeLock.request("screen");
    // Si se cerró (o se reabrió) mientras esperábamos, el lock no es de nadie.
    if (actual === a && a) a.lock = l;
    else l.release().catch(() => {});
  } catch (e) {
    /* sin permiso o batería baja: sigue sin wake lock */
  }
}

function alCambiarVisibilidad() {
  // El navegador suelta el wake lock al ocultar la pestaña: se pide de nuevo.
  if (document.visibilityState === "visible") pedirWakeLock();
}

function alTeclear(e) {
  if (e.key === "Escape") cerrarAnden();
}

function alVolverAtras() {
  if (actual) cerrar();
}

/**
 * Abre la pantalla. `trenes()` devuelve los próximos trenes del viaje (se
 * refrescan con el sondeo normal de la app).
 */
export function abrirAnden({ clave, trenes, origen, destino, linea, color }) {
  if (actual) cerrar();
  const div = el("div", "modo-anden");
  div.setAttribute("role", "dialog");
  div.setAttribute("aria-modal", "true");
  div.setAttribute("aria-label", "Modo andén");
  if (color) div.style.setProperty("--color-linea", color);
  const barra = el("div", "anden-barra");
  const modo = el("span", "anden-modo", "En el andén");
  const salir = el("button", "anden-salir", "Salir");
  salir.type = "button";
  barra.append(modo, salir);
  const cuerpo = el("div", "anden-cuerpo");
  div.append(barra, cuerpo);
  document.body.append(div);
  document.body.classList.add("anden-abierto");

  actual = { div, cuerpo, modo, clave, trenes, origen, destino, linea, lock: null, pos: null, bordo: null };
  salir.addEventListener("click", cerrarAnden);
  document.addEventListener("visibilitychange", alCambiarVisibilidad);
  document.addEventListener("keydown", alTeclear);
  window.addEventListener("popstate", alVolverAtras);
  try {
    history.pushState({ trenesAnden: true }, "");
  } catch (e) {
    /* sin history: Salir y Esc alcanzan */
  }
  pedirWakeLock();
  // Sin pedir permiso: si ya lo dio, sirve para detectar que subió.
  actual.dejarUbicacion = escucharUbicacion((p) => {
    if (actual) actual.pos = p ? [p.lat, p.lon] : null;
  });
  armarAnden();
  actual.timer = setInterval(tick, 1000);
  tick();
  salir.focus();
}

export function cerrarAnden() {
  if (!actual) return;
  if (history.state && history.state.trenesAnden) history.back(); // dispara popstate → cerrar
  else cerrar();
}

function cerrar() {
  const a = actual;
  if (!a) return;
  actual = null;
  clearInterval(a.timer);
  clearTimeout(a.timerBordo);
  if (a.dejarUbicacion) a.dejarUbicacion();
  if (a.lock) a.lock.release().catch(() => {});
  document.removeEventListener("visibilitychange", alCambiarVisibilidad);
  document.removeEventListener("keydown", alTeclear);
  window.removeEventListener("popstate", alVolverAtras);
  document.body.classList.remove("anden-abierto");
  a.div.remove();
}

/* ---------- Andén ---------- */

function armarAnden() {
  const c = actual.cuerpo;
  actual.v = {
    ramal: el("p", "anden-ramal"),
    trayecto: el("p", "anden-trayecto", `${actual.origen.nombre} → ${actual.destino.nombre}`),
    num: el("div", "anden-num", "--:--"),
    uni: el("div", "anden-uni"),
    hora: el("p", "anden-hora"),
    siguiente: el("p", "anden-siguiente"),
    subi: el("button", "anden-boton", "Ya subí"),
  };
  actual.v.subi.type = "button";
  actual.v.subi.addEventListener("click", () => {
    const t = principal(Date.now());
    if (t) subir(t, true);
  });
  const v = actual.v;
  c.replaceChildren(v.ramal, v.trayecto, v.num, v.uni, v.hora, v.siguiente, v.subi);
}

// El tren elegido mientras siga en la lista; si se fue, el próximo.
function lista(ahora) {
  return (actual.trenes() || []).filter((t) => t.referencia > ahora - 120000);
}

function principal(ahora) {
  const l = lista(ahora);
  return l.find((t) => t.clave === actual.clave) || l[0] || null;
}

function tickAnden(ahora) {
  const v = actual.v;
  const l = lista(ahora);
  const t = principal(ahora);
  if (!t) {
    v.ramal.textContent = "";
    v.num.textContent = "--:--";
    v.uni.textContent = "sin trenes por ahora";
    v.hora.textContent = "";
    v.siguiente.textContent = "";
    v.subi.hidden = true;
    return;
  }
  const c = cuentaRegresiva(t.referencia, ahora);
  const haciaNom = typeof t.hacia === "string" ? t.hacia : t.hacia?.nombre;
  const hacia = haciaNom && haciaNom !== t.estacionDestino ? ` · hacia ${haciaNom}` : "";
  v.ramal.textContent = `${t.ramal}${hacia}`;
  v.num.textContent = c.num;
  v.uni.textContent = c.uni;
  v.num.classList.toggle("ya", Boolean(c.ya));
  v.hora.textContent = `${t.esCabecera ? "Sale" : "Llega"} ${hora(t.referencia)}${t.anden ? ` · Andén ${t.anden}` : ""}`;
  const sig = l[l.indexOf(t) + 1];
  v.siguiente.textContent = sig
    ? `Siguiente: ${hora(sig.referencia)} · en ${cuentaRegresiva(sig.referencia, ahora).num}`
    : "";
  v.subi.hidden = false;
  // Subió solo: estuvo en el origen, el tren ya está (YA) y el GPS se alejó.
  const o = actual.origen.coords;
  const dist = actual.pos && o ? distanciaKm(actual.pos, o) : null;
  if (dist != null && dist <= SUBIDA_KM) actual.estuvoEnOrigen = true;
  if (c.ya && actual.estuvoEnOrigen && dist != null && dist > SUBIDA_KM) subir(t, false);
}

/* ---------- A bordo ---------- */

function paradasDe(t) {
  const { origen, destino } = actual;
  const h = t.horarios || [];
  const i = h.findIndex((p) => p.id === origen.id);
  const j = h.findIndex((p) => p.id === destino.id);
  const tramo =
    i >= 0 && j > i
      ? h.slice(i, j + 1)
      : [
          { id: origen.id, nombre: origen.nombre, hora: t.referencia },
          { id: destino.id, nombre: destino.nombre, hora: t.llegadaDestino ? +new Date(t.llegadaDestino) : null },
        ];
  return tramo.map((p) => {
    const e = buscarEstacionPorId(p.id);
    return { id: p.id, nombre: (e && e.nombre) || p.nombre, coords: (e && e.coords) || null, hora: p.hora };
  });
}

function subir(t, pedirGps) {
  const a = actual;
  a.modo.textContent = "A bordo";
  a.bordo = { tren: t, paradas: paradasDe(t), linea: null, ultimaVista: null, avisado: false };
  const ruta = elegirRuta(a.origen, a.destino, t.ramal);
  if (ruta) {
    cargarRecorridos().then((todos) => {
      if (actual === a && a.bordo) a.bordo.linea = todos[`${ruta.linea}|||${ruta.ramal}`] || null;
    });
  }
  if (pedirGps) {
    if (a.dejarUbicacion) a.dejarUbicacion();
    a.dejarUbicacion = escucharUbicacion(
      (p) => {
        if (actual === a) a.pos = p ? [p.lat, p.lon] : null;
      },
      { pedir: true }
    );
  }
  armarBordo();
  a.timerBordo = setTimeout(refrescarBordo, REFRESCO_BORDO_MS);
  tick();
}

function armarBordo() {
  const v = {
    fuente: el("p", "anden-fuente"),
    actual: el("p", "anden-ramal"),
    proxima: el("p", "anden-trayecto"),
    num: el("div", "anden-num", "--:--"),
    uni: el("div", "anden-uni"),
    llegada: el("p", "anden-hora"),
    paradas: el("ol", "anden-paradas"),
  };
  actual.v = v;
  actual.cuerpo.replaceChildren(v.fuente, v.actual, v.proxima, v.num, v.uni, v.llegada, v.paradas);
}

function tickBordo(ahora) {
  const a = actual;
  const b = a.bordo;
  const v = a.v;
  const p = progresoViaje(b.paradas, { pos: a.pos, linea: b.linea, ahora });
  const ultima = Math.max(0, p.ultima); // ya subió: el origen cuenta como pasado
  const siguiente = p.llego ? null : ultima + 1 < b.paradas.length ? ultima + 1 : null;
  const llego = p.llego || siguiente == null;
  v.fuente.textContent = p.fuente === "gps" ? "Posición por GPS" : "Posición estimada";
  v.fuente.classList.toggle("gps", p.fuente === "gps");
  const ult = b.paradas[ultima];
  const destino = b.paradas[b.paradas.length - 1];
  if (llego) {
    v.actual.textContent = `Llegaste a ${destino.nombre}`;
    v.proxima.textContent = "";
    v.num.textContent = "YA";
    v.num.classList.add("ya");
    v.uni.textContent = "bajate acá";
    v.llegada.textContent = "";
    if (!b.avisado) {
      b.avisado = true;
      notificar(`Llegaste a ${destino.nombre}`, `${b.tren.ramal} · bajate acá`, { tag: `aviso-${b.tren.clave}-bajada` });
    }
  } else {
    const sig = b.paradas[siguiente];
    v.actual.textContent = p.enEstacion ? `En ${ult.nombre}` : `Última: ${ult.nombre}`;
    v.proxima.textContent = `Próxima: ${sig.nombre}`;
    const c = sig.hora != null ? cuentaRegresiva(sig.hora, ahora) : { num: "--:--", uni: "sin hora" };
    v.num.textContent = c.num;
    v.uni.textContent = c.uni === "en andén" ? "llegando" : c.uni;
    v.num.classList.toggle("ya", Boolean(c.ya));
    v.llegada.textContent = `${destino.nombre} ${hora(destino.hora)}`;
  }
  const clave = `${ultima}|${siguiente}`;
  if (b.ultimaVista !== clave) {
    b.ultimaVista = clave;
    v.paradas.replaceChildren(
      ...b.paradas.map((e, i) => {
        const li = el("li", i <= ultima ? "pasada" : i === siguiente ? "proxima" : "");
        if (i === b.paradas.length - 1) li.classList.add("destino");
        li.append(el("span", "anden-parada-nombre", e.nombre), el("span", "anden-parada-hora", hora(e.hora)));
        return li;
      })
    );
    const prox = v.paradas.querySelector(".proxima");
    if (prox && prox.scrollIntoView) prox.scrollIntoView({ block: "nearest" });
  }
}

// Sin equipo.id la clave es ramal + hora programada en la estación consultada,
// que cambia de parada a parada: ahí se empareja por ramal + hora en el origen.
const sinEquipo = (t) => t.clave === `${t.ramal}-${t.programada ? +new Date(t.programada) : undefined}`;
function mismoTren(x, b) {
  if (!sinEquipo(b.tren)) return x.clave === b.tren.clave;
  const origen = b.paradas[0];
  const h = (x.horarios || []).find((e) => e.id === origen.id)?.hora;
  return x.ramal === b.tren.ramal && h != null && origen.hora != null && Math.abs(h - origen.hora) <= 120000;
}

// Sin GPS: horas nuevas de la API para este tren, consultando la próxima parada.
async function refrescarBordo() {
  const a = actual;
  if (!a || !a.bordo || a.bordo.avisado) return;
  const b = a.bordo;
  try {
    const p = progresoViaje(b.paradas, { pos: a.pos, linea: b.linea });
    const sig = Math.max(1, p.ultima + 1);
    if (sig < b.paradas.length) {
      // Hacia el destino: "hasta" el final del servicio (consultar destino→destino no sirve).
      const fin = b.tren.paradas?.at(-1);
      const hasta = sig < b.paradas.length - 1 || fin == null ? a.destino.id : fin;
      const crudos = await arribos(b.paradas[sig].id, hasta);
      const t = crudos.map((r) => normalizarArribo(r, a.destino.id)).find((x) => x && mismoTren(x, b));
      if (t && actual === a) {
        const horas = new Map((t.horarios || []).map((h) => [h.id, h.hora]));
        b.paradas.forEach((e) => {
          if (horas.get(e.id) != null) e.hora = horas.get(e.id);
        });
      }
    }
  } catch (e) {
    /* sin conexión: seguimos con las horas que había */
  }
  if (actual === a) a.timerBordo = setTimeout(refrescarBordo, REFRESCO_BORDO_MS);
}

function tick() {
  if (!actual) return;
  const ahora = Date.now();
  if (actual.bordo) tickBordo(ahora);
  else tickAnden(ahora);
}
