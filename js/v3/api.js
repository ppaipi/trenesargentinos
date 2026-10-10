const BASE = "https://api-servicios.sofse.gob.ar/v1";
const TIMEOUT_MS = 12000;

let token = null;
let loginEnCurso = null;

function obtenerCredenciales() {
  const fecha = new Date();
  const yyyymmdd =
    fecha.getFullYear() + String(fecha.getMonth() + 1).padStart(2, "0") + String(fecha.getDate()).padStart(2, "0");
  const user = btoa(yyyymmdd + "sofse");

  let pass = btoa(user);
  for (const [k, v] of Object.entries({ a: "#t", e: "#x", i: "#f", o: "#l", u: "#7", "=": "#g" })) {
    pass = pass.replaceAll(k, v);
  }
  pass = btoa(pass.split("").reverse().join(""));
  for (const [k, v] of Object.entries({ a: "32%j", e: "32%p", i: "32%w", o: "32%8", u: "#0", "=": "32%v" })) {
    pass = pass.replaceAll(k, v);
  }
  pass = pass.split("").reverse().join("");

  return { user, pass };
}

async function fetchConTimeout(url, opciones = {}) {
  const control = new AbortController();
  const timer = setTimeout(() => control.abort(), TIMEOUT_MS);
  try {
    return await fetch(url, { ...opciones, signal: control.signal });
  } finally {
    clearTimeout(timer);
  }
}

export class ErrorApi extends Error {
  constructor(status, mensaje) {
    super(mensaje || `Error ${status}`);
    this.status = status;
  }
}

// Un solo login a la vez: si varias consultas piden token juntas, comparten
// la misma promesa.
export function login() {
  if (!loginEnCurso) {
    loginEnCurso = (async () => {
      const { user, pass } = obtenerCredenciales();
      const res = await fetchConTimeout(`${BASE}/auth/authorize`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: user, password: pass }),
      });
      if (!res.ok) throw new ErrorApi(res.status, "No se pudo autenticar");
      token = (await leerJson(res)).token;
      if (!token) throw new ErrorApi(res.status, "No se pudo autenticar");
      return token;
    })().finally(() => {
      loginEnCurso = null;
    });
  }
  return loginEnCurso;
}

// GET autenticado. Renueva el token ante 401/403 (vence a medianoche porque
// las credenciales dependen de la fecha) y reintenta los 5xx transitorios.
async function get(ruta, { reintentos = 2 } = {}) {
  if (!token) await login();

  for (let intento = 0; ; intento++) {
    let res;
    try {
      res = await fetchConTimeout(`${BASE}${ruta}`, { headers: { Authorization: token } });
    } catch (error) {
      if (intento < reintentos) {
        await esperar(800 * (intento + 1));
        continue;
      }
      throw new ErrorApi(0, navigator.onLine ? "Sin respuesta del servidor" : "Sin conexión");
    }

    if (res.ok) {
      try {
        return await leerJson(res);
      } catch (error) {
        if (intento < reintentos) {
          await esperar(800 * (intento + 1));
          continue;
        }
        throw error;
      }
    }

    if ((res.status === 401 || res.status === 403) && intento === 0) {
      await login();
      continue;
    }
    if (res.status >= 500 && intento < reintentos) {
      await esperar(800 * (intento + 1));
      continue;
    }
    throw new ErrorApi(res.status);
  }
}

// Algunas veces la API (o un proxy en el medio) responde HTML con status 200:
// res.json() tiraba "JSON.parse: unexpected character". Lo tratamos como error.
async function leerJson(res) {
  const texto = await res.text();
  try {
    return JSON.parse(texto);
  } catch {
    console.warn("Respuesta no JSON de", res.url, texto.slice(0, 200));
    throw new ErrorApi(res.status, "Respuesta inválida del servidor");
  }
}

function esperar(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

export async function arribos(origen, destino, { fecha, hora } = {}) {
  let ruta = `/arribos/estacion/${origen}?hasta=${destino}`;
  if (fecha && hora) ruta += `&fecha=${fecha}&hora=${hora}&tipoBusqueda=partida`;
  const datos = await get(ruta);
  return datos.results || [];
}

function fechaValida(valor) {
  if (!valor) return null;
  const d = new Date(valor);
  return isNaN(d) ? null : d;
}

/**
 * Normaliza un resultado crudo de la API a un objeto plano y estable.
 * Devuelve null si el servicio no pasa por la estacion destino.
 */
export function normalizarArribo(item, destinoId) {
  const arribo = item.arribo || {};
  const servicio = item.servicio || {};
  const paradaDestino = (servicio.estaciones || []).find((e) => String(e.idElemento) === String(destinoId));
  if (!paradaDestino) return null;

  const llegadaProg = fechaValida(arribo.llegada?.programada);
  const llegadaEst = fechaValida(arribo.llegada?.estimada);
  const salidaProg = fechaValida(arribo.salida?.programada);
  const salidaEst = fechaValida(arribo.salida?.estimada);

  // Si el tren arranca en el origen (cabecera) no hay "llegada": usamos la salida.
  const esCabecera = !llegadaProg && !llegadaEst;
  const programada = esCabecera ? salidaProg : llegadaProg;
  const estimada = esCabecera ? salidaEst : llegadaEst;
  const referencia = estimada || programada;
  if (!referencia) return null;

  const demoraMin = estimada && programada ? Math.round((estimada - programada) / 60000) : null;
  const llegadaDestino =
    fechaValida(paradaDestino.llegada?.estimada) || fechaValida(paradaDestino.llegada?.programada);
  const ubicacion = servicio.location?.lat && servicio.location?.long
    ? { lat: Number(servicio.location.lat), lon: Number(servicio.location.long) }
    : null;

  const ramal = servicio.ramal?.nombre || "Servicio";
  return {
    clave: String(arribo.equipo?.id ?? `${ramal}-${programada?.getTime()}`),
    ramal,
    estacionOrigen: arribo.nombre || "",
    estacionDestino: paradaDestino.nombre || "",
    esCabecera,
    referencia,
    programada,
    enVivo: Boolean(estimada) || Boolean(ubicacion),
    demoraMin,
    anden: arribo.anden?.nombre || null,
    llegadaDestino,
    duracionMin: llegadaDestino ? Math.round((llegadaDestino - referencia) / 60000) : null,
    ubicacion,
    // Paradas del servicio en orden de recorrido (ids), para dibujarlo en el mapa.
    paradas: (servicio.estaciones || []).map((e) => Number(e.idElemento)),
  };
}

const fechaApi = (s) => (s ? new Date(s.replace(" ", "T") + "-03:00") : null);

/**
 * Avisos de servicio por linea (feriados, obras, interrupciones). La API
 * los agrupa por "gerencia" (= linea). Devuelve solo los vigentes.
 */
export async function avisos() {
  const gerencias = await get("/infraestructura/gerencias");
  const ahora = Date.now();
  return gerencias.map((g) => ({
    lineaId: g.id,
    linea: g.nombre,
    estado: g.estado?.mensaje || "",
    avisos: (g.alerta || [])
      .filter((a) => a.habilitado !== 0)
      .map((a) => ({
        id: a.id,
        texto: (a.contenido || "").trim(),
        ramalId: a.ramal_id,
        estacionId: a.estacion_id,
        desde: fechaApi(a.vigencia_desde),
        hasta: fechaApi(a.vigencia_hasta),
        criticidad: a.criticidad_orden ?? 9, // menor = mas grave
      }))
      .filter((a) => a.texto && (!a.hasta || a.hasta > ahora) && (!a.desde || a.desde <= ahora))
      .sort((a, b) => a.criticidad - b.criticidad),
  }));
}
