import { login, arribos, normalizarArribo } from "./api.js";
import {
  cargarEstaciones,
  buscarEstacionPorId,
  rutaCompartida,
  estacionesCercanas,
  COLOR_LINEA,
} from "./estaciones.js";
import { crearBuscadorEstacion } from "./buscador.js";
import { crearTarjeta, skeletons, mensaje, hora, cuentaRegresiva } from "./tarjetas.js";

const TITULO = document.title;
import * as extras from "./extras.js";
import { abrirAnden } from "./anden.js";
import { iniciarAvisos, avisosDelViaje, abrirPanelAvisos, mostrarAvisoEstacion } from "./avisos.js";

const INTERVALO_SONDEO_MS = 15000; // la API se consulta cada 15 s; la cuenta regresiva corre local cada 1 s
const AVISO_MINUTOS = 5;

const $ = (id) => document.getElementById(id);
const originInput = $("origin-input");
const destinationInput = $("destination-input");
const buscarBtn = $("buscarBtn");
const resultadoSec = $("resultado");
const resultDiv = $("result");
const estadoAct = $("estadoActualizacion");
const fechaInput = $("fecha-input");
const horaInput = $("hora-input");
const favBtn = $("favBtn");
const modoChips = document.querySelectorAll(".modo-chip");

const estado = {
  origen: null,
  destino: null,
  modo: "vivo", // "vivo" | "programada"
  activo: false, // hay una busqueda en vivo corriendo
  ultimaOk: null,
  error: null,
  consulta: 0, // id incremental: descarta respuestas de busquedas viejas
};

const tarjetas = new Map(); // clave de formacion -> tarjeta
let timerSondeo = null;
let timerTick = null;
let buscadorOrigen;

let buscadorDestino;

// Otros modulos (p. ej. el patrocinio) escuchan estos eventos.
const emitir = (nombre, detalle) => document.dispatchEvent(new CustomEvent(`trenes:${nombre}`, { detail: detalle }));

/* ---------- Seleccion de estaciones ---------- */

function viajeActual() {
  return { o: estado.origen.id, d: estado.destino.id };
}

function actualizarBotonBuscar() {
  buscarBtn.disabled = !(estado.origen && estado.destino);
}

function fijarEstacion(cual, estacion) {
  const input = cual === "origen" ? originInput : destinationInput;
  estado[cual] = estacion;
  if (estacion) {
    input.value = estacion.nombre;
    input.dataset.id = estacion.id;
  } else {
    input.value = "";
    delete input.dataset.id;
  }
}

function alSeleccionar(cual, otra) {
  return (estacion) => {
    estado[cual] = estacion;
    if (estacion) mostrarAvisoEstacion(estacion);
    if (estacion && estado[otra] && !rutaCompartida(estacion, estado[otra])) {
      fijarEstacion(otra, null);
      extras.toast("Esas estaciones no comparten ramal: elegí otra.");
    }
    actualizarBotonBuscar();
    if (estacion && cual === "origen" && !estado.destino) destinationInput.focus();
  };
}

function intercambiar() {
  const { origen, destino } = estado;
  fijarEstacion("origen", destino);
  fijarEstacion("destino", origen);
  actualizarBotonBuscar();
  if (estado.activo || !resultadoSec.classList.contains("ocultar")) buscar();
}

async function usarCercana() {
  const boton = $("cercanaBtn");
  if (!navigator.geolocation) return extras.toast("Tu navegador no permite ubicación.");
  boton.classList.add("cargando");
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      boton.classList.remove("cargando");
      const cercanas = estacionesCercanas([pos.coords.latitude, pos.coords.longitude]);
      if (!cercanas.length) return extras.toast("No encontramos estaciones cerca.");
      originInput.focus();
      buscadorOrigen.mostrarCercanas(cercanas);
    },
    (err) => {
      boton.classList.remove("cargando");
      extras.toast(err.code === 1 ? "Permiso de ubicación denegado." : "No pudimos obtener tu ubicación.");
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
  );
}

/* ---------- Favoritos y recientes ---------- */

function renderFavoritos() {
  const cont = $("favoritosLista");
  cont.replaceChildren();
  const favs = extras.favoritos();
  const recs = extras.recientes().filter((r) => !extras.esFavorito(r)).slice(0, 3);

  const crear = (viaje, esFav) => {
    const o = buscarEstacionPorId(viaje.o);
    const d = buscarEstacionPorId(viaje.d);
    if (!o || !d) return;
    const b = document.createElement("button");
    b.type = "button";
    b.className = "fav-chip";
    const ruta = rutaCompartida(o, d);
    b.style.borderLeft = `4px solid ${COLOR_LINEA[ruta?.linea] || "var(--marca)"}`;
    const tipo = document.createElement("span");
    tipo.className = "tipo";
    tipo.textContent = esFav ? "★" : "Últ.";
    const texto = document.createElement("span");
    texto.className = "texto";
    texto.textContent = `${o.nombre} → ${d.nombre}`;
    b.append(tipo, texto);
    if (esFav) {
      const quitar = document.createElement("span");
      quitar.className = "quitar";
      quitar.textContent = "✕";
      quitar.title = "Quitar de favoritos";
      quitar.addEventListener("click", (e) => {
        e.stopPropagation();
        extras.quitarFavorito(viaje);
        renderFavoritos();
        actualizarBotonFav();
      });
      b.append(quitar);
    }
    b.addEventListener("click", () => {
      fijarEstacion("origen", o);
      fijarEstacion("destino", d);
      actualizarBotonBuscar();
      cambiarModo("vivo", { silencioso: true });
      buscar();
    });
    cont.append(b);
  };

  favs.forEach((f) => crear(f, true));
  recs.forEach((r) => crear(r, false));
  $("favoritos").classList.toggle("ocultar", cont.children.length === 0);
}

function actualizarBotonFav() {
  if (!estado.origen || !estado.destino) return;
  const fav = extras.esFavorito(viajeActual());
  favBtn.textContent = fav ? "★" : "☆";
  favBtn.setAttribute("aria-pressed", String(fav));
}

/* ---------- Modo ---------- */

function fechaLocal(d = new Date()) {
  // toISOString() da la fecha UTC: despues de las 21 h en Argentina devolvia "mañana".
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function cambiarModo(modo, { silencioso } = {}) {
  estado.modo = modo;
  modoChips.forEach((c) => {
    c.classList.toggle("activo", c.dataset.modo === modo);
    c.setAttribute("aria-selected", String(c.dataset.modo === modo));
  });
  $("fechaHora").classList.toggle("ocultar", modo !== "programada");
  if (modo === "programada") {
    const ahora = new Date();
    if (!fechaInput.value) fechaInput.value = fechaLocal(ahora);
    if (!horaInput.value) horaInput.value = hora(ahora);
  }
  buscarBtn.textContent = modo === "vivo" ? "Buscar horarios" : "Ver horarios programados";
  if (!silencioso) {
    detener();
    resultadoSec.classList.add("ocultar");
  }
}

/* ---------- Busqueda ---------- */

function limpiarTarjetas() {
  for (const t of tarjetas.values()) t.destruir();
  tarjetas.clear();
}

function detener() {
  estado.activo = false;
  clearTimeout(timerSondeo);
  clearInterval(timerTick);
  estado.proximos = [];
  document.title = TITULO;
}

function buscar() {
  if (!estado.origen || !estado.destino) return;
  detener();
  buscadorOrigen.cerrar();
  buscadorDestino.cerrar();

  const viaje = viajeActual();
  extras.agregarReciente(viaje);
  renderFavoritos();
  actualizarBotonFav();
  history.replaceState(null, "", `?o=${viaje.o}&d=${viaje.d}`);
  const ayuda = $("ayudaBtn"); // el "?" lleva al patrocinador con el mismo viaje
  if (ayuda) ayuda.href = `patrocinador.html?o=${viaje.o}&d=${viaje.d}`;

  limpiarTarjetas();
  estado.ultimaOk = null;
  estado.error = null;
  estado.guardado = null;
  $("trayecto").textContent = `${estado.origen.nombre} → ${estado.destino.nombre}`;
  resultadoSec.classList.remove("ocultar");
  skeletons(resultDiv);
  resultadoSec.scrollIntoView({ behavior: "smooth", block: "start" });
  emitir("busqueda", { origen: estado.origen, destino: estado.destino, modo: estado.modo });

  if (estado.modo === "programada") {
    if (!fechaInput.value || !horaInput.value) return mensaje(resultDiv, "Elegí fecha y hora para buscar.");
    estadoAct.textContent = `Horarios programados · ${fechaInput.value.split("-").reverse().join("/")} desde las ${horaInput.value}`;
    consultar({ fecha: fechaInput.value, hora: horaInput.value });
  } else {
    estado.activo = true;
    // Mientras llega la respuesta mostramos lo de la ultima vez (si sirve).
    const previo = extras.leerResultado(viaje);
    if (previo) {
      estado.guardado = previo.guardado;
      render(previo.trenes, false);
    }
    consultar();
  }
  timerTick = setInterval(tick, 1000);
}

async function consultar(programada) {
  const id = ++estado.consulta;
  const { origen, destino } = estado;
  try {
    const crudos = await arribos(origen.id, destino.id, programada);
    if (id !== estado.consulta) return;
    const trenes = crudos
      .map((r) => normalizarArribo(r, destino.id))
      .filter(Boolean)
      // La API abrevia nombres ("Plaza C."): mostramos los de nuestra lista.
      .map((t) => ({
        ...t,
        estacionOrigen: origen.nombre,
        estacionDestino: destino.nombre,
        hacia: t.hacia && (buscarEstacionPorId(t.hacia.id)?.nombre || t.hacia.nombre),
      }))
      .sort((a, b) => a.referencia - b.referencia)
      .slice(0, programada ? 12 : 8);
    estado.ultimaOk = Date.now();
    estado.error = null;
    if (!programada) extras.guardarResultado({ o: origen.id, d: destino.id }, trenes);
    render(trenes, Boolean(programada));
  } catch (error) {
    if (id !== estado.consulta) return;
    estado.error = error;
    if (tarjetas.size === 0) {
      mensaje(
        resultDiv,
        error.status === 0
          ? `${error.message}. Reintentamos solos en unos segundos.`
          : `El servicio de Trenes Argentinos no respondió bien (${error.message}).`,
        { texto: "Reintentar ahora", onClick: () => buscar() }
      );
    }
  }
  if (estado.activo && id === estado.consulta && !document.hidden) {
    timerSondeo = setTimeout(() => consultar(), estado.error ? INTERVALO_SONDEO_MS * 2 : INTERVALO_SONDEO_MS);
  }
  actualizarEstado();
}

function render(trenes, programada) {
  estado.proximos = programada ? [] : trenes;
  const ruta = rutaCompartida(estado.origen, estado.destino);
  const linea = ruta?.linea;
  const vistos = new Set();

  if (trenes.length === 0) {
    limpiarTarjetas();
    // Respuesta vacia no significa cancelacion: si hay un aviso de la linea
    // (p. ej. "servicio suspendido") lo mostramos como posible causa.
    const aviso = avisosDelViaje(estado.origen, estado.destino, linea)[0];
    const texto = aviso
      ? `No hay trenes en este tramo. Aviso de ${linea}: ${aviso.texto}`
      : programada
        ? "No hay trenes programados para ese horario en este tramo."
        : "No hay trenes en vivo para este tramo ahora mismo. Puede que el servicio aún no haya salido de cabecera.";
    const accion = aviso
      ? { texto: "Ver avisos", onClick: () => abrirPanelAvisos(linea) }
      : programada
        ? null
        : { texto: "Ver horarios programados", onClick: () => {
          cambiarModo("programada", { silencioso: true });
          buscar();
        } };
    mensaje(resultDiv, texto, accion);
    emitir("resultados", { trenes, programada });
    return;
  }

  if (tarjetas.size === 0) resultDiv.replaceChildren();

  trenes.forEach((t, i) => {
    vistos.add(t.clave);
    let tarjeta = tarjetas.get(t.clave);
    if (!tarjeta) {
      tarjeta = crearTarjeta({
        color: COLOR_LINEA[linea] || "var(--azul)",
        linea,
        onAvisar: alternarAviso,
        onAnden: (t) =>
          abrirAnden({
            clave: t.clave,
            trenes: () => estado.proximos || [],
            origen: estado.origen,
            destino: estado.destino,
            linea,
            color: COLOR_LINEA[linea] || "var(--azul)",
          }),
        viaje: { origen: estado.origen, destino: estado.destino },
      });
      tarjetas.set(t.clave, tarjeta);
    }
    tarjeta.actualizar(t);
    tarjeta.div.classList.toggle("proximo", i === 0 && !programada);
    tarjeta.botonAnden.classList.toggle("ocultar", Boolean(programada));
    ubicarEnOrden(tarjeta.div, i === 0 ? null : tarjetas.get(trenes[i - 1].clave).div);
  });

  for (const [clave, t] of tarjetas) {
    if (!vistos.has(clave)) {
      t.destruir();
      tarjetas.delete(clave);
    }
  }
  emitir("resultados", { trenes, programada, tarjetas: [...tarjetas.values()] });
}

// Mueve la tarjeta solo si no esta ya en su lugar. Reinsertarla en cada
// sondeo reiniciaba su animacion de entrada y la pantalla titilaba.
function ubicarEnOrden(div, anterior) {
  let esperado = anterior ? anterior.nextElementSibling : resultDiv.firstElementChild;
  while (esperado && !esperado.classList.contains("horario")) esperado = esperado.nextElementSibling;
  if (esperado === div) return;
  if (anterior) anterior.after(div);
  else resultDiv.prepend(div);
}

// Cuenta regresiva del próximo tren en el título de la pestaña, para verla
// desde otra pestaña (en la app: notificación fija).
function actualizarTitulo(ahora) {
  const t = estado.activo && estado.modo === "vivo" && (estado.proximos || []).find((x) => x.referencia > ahora - 60000);
  if (!t) {
    document.title = TITULO;
    return;
  }
  const c = cuentaRegresiva(t.referencia, ahora);
  document.title = `${c.ya ? "YA en andén" : c.num} · ${estado.origen.nombre} → ${estado.destino.nombre}`;
}

function tick() {
  const ahora = Date.now();
  actualizarTitulo(ahora);
  for (const t of tarjetas.values()) {
    t.tick(ahora);
    revisarAviso(t, ahora);
  }
  actualizarEstado();
}

function actualizarEstado() {
  if (estado.modo !== "vivo") return;
  if (!estado.ultimaOk && estado.guardado) {
    const min = Math.max(1, Math.round((Date.now() - estado.guardado) / 60000));
    estadoAct.textContent = `${estado.error ? "Sin conexión" : "Actualizando…"} · mostrando datos de hace ${min} min`;
    return;
  }
  if (!estado.ultimaOk) {
    estadoAct.textContent = estado.error ? "Sin datos todavía · reintentando…" : "Buscando trenes…";
    return;
  }
  const seg = Math.round((Date.now() - estado.ultimaOk) / 1000);
  const hace = seg < 5 ? "recién" : seg < 60 ? `hace ${seg} s` : `hace ${Math.floor(seg / 60)} min`;
  estadoAct.textContent = `${estado.error ? "Error al actualizar · " : "EN VIVO · "}actualizado ${hace}`;
}

/* ---------- Avisos ---------- */

async function alternarAviso(tarjeta) {
  if (tarjeta.aviso) {
    tarjeta.marcarAviso(false);
    return;
  }
  const permitido = await extras.pedirPermisoNotificaciones();
  tarjeta.marcarAviso(true);
  tarjeta.avisado = false;
  extras.toast(
    permitido
      ? `Te avisamos ${AVISO_MINUTOS} min antes. Dejá la app abierta.`
      : "Sin permiso de notificaciones: te avisamos con vibración y un cartel."
  );
}

function revisarAviso(tarjeta, ahora) {
  if (!tarjeta.aviso || tarjeta.avisado || !tarjeta.datos) return;
  const min = (tarjeta.datos.referencia - ahora) / 60000;
  if (min <= AVISO_MINUTOS) {
    tarjeta.avisado = true;
    const t = tarjeta.datos;
    const verbo = t.esCabecera ? "sale de" : "llega a";
    extras.notificar(
      `Tu tren ${verbo} ${t.estacionOrigen} en ${Math.max(0, Math.round(min))} min`,
      `${t.ramal} · ${hora(t.referencia)}${t.anden ? ` · Andén ${t.anden}` : ""}`
    );
  }
}

/* ---------- Compartir ---------- */

async function compartir() {
  if (!estado.origen || !estado.destino) return;
  const url = `${location.origin}${location.pathname}?o=${estado.origen.id}&d=${estado.destino.id}`;
  const texto = `¿Cuándo llega el tren de ${estado.origen.nombre} a ${estado.destino.nombre}?`;
  try {
    if (navigator.share) return await navigator.share({ title: "Trenes en vivo", text: texto, url });
    await navigator.clipboard.writeText(url);
    extras.toast("Link copiado");
  } catch {
    /* el usuario cancelo */
  }
}

/* ---------- Inicio ---------- */

document.addEventListener("visibilitychange", () => {
  if (!estado.activo) return;
  clearTimeout(timerSondeo);
  if (!document.hidden) consultar(); // al volver: dato fresco inmediato
});

window.addEventListener("online", () => {
  $("avisoOffline").classList.add("ocultar");
  if (estado.activo) consultar();
});
window.addEventListener("offline", () => $("avisoOffline").classList.remove("ocultar"));

async function conectar() {
  const status = $("loginStatus");
  status.innerHTML = '<span class="spinner"></span> Conectando con Trenes Argentinos...';
  try {
    await login();
    status.classList.add("ocultar");
    return true;
  } catch {
    status.innerHTML = "";
    const p = document.createElement("p");
    p.textContent = "No se pudo conectar con Trenes Argentinos.";
    const b = document.createElement("button");
    b.className = "boton-secundario";
    b.textContent = "Reintentar";
    b.addEventListener("click", async () => (await conectar()) && arrancarDesdeUrl());
    status.append(p, b);
    return false;
  }
}

function arrancarDesdeUrl() {
  // Prioridad: link compartido/recarga (?o=&d=) y si no, la busqueda de
  // los ultimos 30 min (al cerrar y volver a abrir la app).
  const params = new URLSearchParams(location.search);
  const viaje = params.has("o") ? { o: params.get("o"), d: params.get("d") } : extras.recienteActivo();
  if (!viaje) return;
  const o = buscarEstacionPorId(viaje.o);
  const d = buscarEstacionPorId(viaje.d);
  if (o && d && rutaCompartida(o, d)) {
    fijarEstacion("origen", o);
    fijarEstacion("destino", d);
    actualizarBotonBuscar();
    buscar();
  }
}

async function iniciar() {
  extras.iniciarTema($("temaBtn"));
  extras.iniciarPWA(document.querySelector(".pie"));
  if (!navigator.onLine) $("avisoOffline").classList.remove("ocultar");

  // Estaciones y login en paralelo: el menu aparece apenas hay estaciones.
  const conexion = conectar();
  await cargarEstaciones();
  $("menu").hidden = false;

  buscadorOrigen = crearBuscadorEstacion({
    input: originInput,
    panel: $("origin-panel"),
    chips: $("origin-chips"),
    lista: $("origin-list"),
    obtenerOtra: () => estado.destino,
    onSeleccionar: alSeleccionar("origen", "destino"),
  });
  buscadorDestino = crearBuscadorEstacion({
    input: destinationInput,
    panel: $("destination-panel"),
    chips: $("destination-chips"),
    lista: $("destination-list"),
    obtenerOtra: () => estado.origen,
    onSeleccionar: alSeleccionar("destino", "origen"),
  });

  $("swapBtn").addEventListener("click", intercambiar);
  $("cercanaBtn").addEventListener("click", usarCercana);
  $("compartirBtn").addEventListener("click", compartir);
  favBtn.addEventListener("click", () => {
    const agregado = extras.alternarFavorito(viajeActual());
    extras.toast(agregado ? "Guardado en tus viajes" : "Quitado de tus viajes");
    actualizarBotonFav();
    renderFavoritos();
  });
  modoChips.forEach((c) => c.addEventListener("click", () => cambiarModo(c.dataset.modo)));

  buscarBtn.addEventListener("click", buscar);
  destinationInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !buscarBtn.disabled && destinationInput.dataset.id) buscar();
  });

  renderFavoritos();
  iniciarAvisos({
    // Lineas de los viajes guardados y recientes: de esas se notifican avisos nuevos.
    lineasDeInteres: () =>
      new Set(
        [...extras.favoritos(), ...extras.recientes()]
          .map((v) => {
            const o = buscarEstacionPorId(v.o);
            const d = buscarEstacionPorId(v.d);
            return o && d ? rutaCompartida(o, d)?.linea : null;
          })
          .filter(Boolean)
      ),
  });
  emitir("listo", {});
  if (await conexion) arrancarDesdeUrl();
}

iniciar();
