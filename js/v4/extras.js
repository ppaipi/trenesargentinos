// Utilidades de la app que no dependen de la API: almacenamiento local,
// tema, toast, instalacion como PWA y notificaciones.

const CLAVE_FAVORITOS = "trenesArgentinos.v1.favoritos";
const CLAVE_RECIENTES = "trenesArgentinos.v1.recientes";
const CLAVE_TEMA = "trenesArgentinos.v1.tema";
const CLAVE_VIEJA = "trenesArgentinos.ultimaBusqueda"; // la de index.html

function leer(clave, porDefecto) {
  try {
    const v = localStorage.getItem(clave);
    return v ? JSON.parse(v) : porDefecto;
  } catch {
    return porDefecto;
  }
}

function escribir(clave, valor) {
  try {
    localStorage.setItem(clave, JSON.stringify(valor));
  } catch {
    /* modo privado o almacenamiento lleno: seguimos sin guardar */
  }
}

const mismoViaje = (a, b) => a.o === b.o && a.d === b.d;

export function favoritos() {
  return leer(CLAVE_FAVORITOS, []);
}

export function esFavorito(viaje) {
  return favoritos().some((f) => mismoViaje(f, viaje));
}

export function alternarFavorito(viaje) {
  const lista = favoritos();
  const i = lista.findIndex((f) => mismoViaje(f, viaje));
  if (i >= 0) lista.splice(i, 1);
  else lista.unshift(viaje);
  escribir(CLAVE_FAVORITOS, lista.slice(0, 10));
  return i < 0;
}

export function quitarFavorito(viaje) {
  escribir(CLAVE_FAVORITOS, favoritos().filter((f) => !mismoViaje(f, viaje)));
}

export function recientes() {
  const lista = leer(CLAVE_RECIENTES, null);
  if (lista) return lista;
  // Migra la ultima busqueda guardada por la version clasica.
  const vieja = leer(CLAVE_VIEJA, null);
  return vieja?.origenId ? [{ o: vieja.origenId, d: vieja.destinoId }] : [];
}

export function agregarReciente(viaje) {
  const lista = [{ ...viaje, t: Date.now() }, ...recientes().filter((r) => !mismoViaje(r, viaje))];
  escribir(CLAVE_RECIENTES, lista.slice(0, 4));
}

// Ultimo viaje si se busco hace poco: permite retomar al reabrir la app.
export function recienteActivo(maxMin = 30) {
  const r = recientes()[0];
  return r?.t && Date.now() - r.t < maxMin * 60000 ? r : null;
}

/* ---------- Tema ---------- */

export function iniciarTema(boton) {
  const raiz = document.documentElement;
  const guardado = leer(CLAVE_TEMA, null);
  raiz.dataset.theme = guardado || "light"; // claro por defecto, aunque el sistema este en oscuro

  boton.addEventListener("click", () => {
    const oscuroAhora =
      raiz.dataset.theme === "dark" ||
      (!raiz.dataset.theme && matchMedia("(prefers-color-scheme: dark)").matches);
    raiz.dataset.theme = oscuroAhora ? "light" : "dark";
    escribir(CLAVE_TEMA, raiz.dataset.theme);
  });
}

/* ---------- Toast ---------- */

let timerToast;
export function toast(mensaje, ms = 2600) {
  const el = document.getElementById("toast");
  el.textContent = mensaje;
  el.classList.add("visible");
  clearTimeout(timerToast);
  timerToast = setTimeout(() => el.classList.remove("visible"), ms);
}

/* ---------- PWA ---------- */

export function iniciarPWA(contenedor) {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js").catch((e) => console.error("Service worker:", e));
  }

  let eventoInstalar = null;
  const boton = document.createElement("button");
  boton.type = "button";
  boton.className = "boton-secundario ocultar";
  boton.textContent = "Instalar app";
  contenedor.appendChild(boton);

  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    eventoInstalar = e;
    boton.classList.remove("ocultar");
  });

  // iOS no dispara beforeinstallprompt: mostramos como hacerlo a mano.
  const esIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const instalada = matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  if (esIOS && !instalada) boton.classList.remove("ocultar");

  boton.addEventListener("click", async () => {
    if (eventoInstalar) {
      eventoInstalar.prompt();
      await eventoInstalar.userChoice;
      eventoInstalar = null;
      boton.classList.add("ocultar");
    } else {
      toast("En Safari: tocá Compartir y después “Agregar a inicio”", 5000);
    }
  });

  window.addEventListener("appinstalled", () => boton.classList.add("ocultar"));
}

/* ---------- Notificaciones ---------- */

export async function pedirPermisoNotificaciones() {
  if (!("Notification" in window)) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  return (await Notification.requestPermission()) === "granted";
}

export async function notificar(titulo, cuerpo) {
  navigator.vibrate?.([200, 100, 200]);
  if (!("Notification" in window) || Notification.permission !== "granted") {
    toast(`${titulo}: ${cuerpo}`, 6000);
    return;
  }
  const opciones = { body: cuerpo, icon: "icon-192.png", badge: "icon-192.png", tag: "trenes-aviso", renotify: true };
  try {
    // En Android sólo funciona via service worker.
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) return reg.showNotification(titulo, opciones);
    new Notification(titulo, opciones);
  } catch {
    toast(`${titulo}: ${cuerpo}`, 6000);
  }
}

/* ---------- Ultimo resultado por viaje (para mostrar algo mientras carga) ---------- */

const CLAVE_RESULTADOS = "trenesArgentinos.v4.resultados";
const FECHAS = ["referencia", "programada", "llegadaDestino"];
const VIGENCIA_MS = 45 * 60000;

export function guardarResultado(viaje, trenes) {
  const todos = leer(CLAVE_RESULTADOS, {});
  todos[`${viaje.o}-${viaje.d}`] = { t: Date.now(), trenes };
  const claves = Object.keys(todos).sort((a, b) => todos[b].t - todos[a].t);
  claves.slice(6).forEach((k) => delete todos[k]); // solo los ultimos viajes
  escribir(CLAVE_RESULTADOS, todos);
}

// Trenes guardados que todavia no pasaron, o null si no hay nada util.
export function leerResultado(viaje, ahora = Date.now()) {
  const r = leer(CLAVE_RESULTADOS, {})[`${viaje.o}-${viaje.d}`];
  if (!r || ahora - r.t > VIGENCIA_MS) return null;
  const trenes = r.trenes
    .map((t) => {
      const c = { ...t };
      FECHAS.forEach((f) => (c[f] = t[f] ? new Date(t[f]) : null));
      return c;
    })
    .filter((t) => t.referencia > ahora - 60000);
  return trenes.length ? { guardado: r.t, trenes } : null;
}
