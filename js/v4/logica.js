// Logica pura (sin DOM): la usan tarjetas/estaciones/buscador y los tests de tests/.

const fmtHora = new Intl.DateTimeFormat("es-AR", { hour: "2-digit", minute: "2-digit", hour12: false });

export const hora = (d) => (d ? fmtHora.format(d) : "--:--");

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

// Punto de referencia para elegir la cabecera de cada ramal: la estacion
// del ramal mas cercana al centro porteño (Retiro, Once, Constitucion, etc).
export const CENTRO = [-34.6037, -58.3816];

export function distanciaKm([lat1, lon1], [lat2, lon2]) {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(a));
}

// Ordena las estaciones de un ramal desde la cabecera hacia afuera. Las que
// no tienen coordenadas quedan al final en orden alfabetico.
export function ordenarGeografico(lista) {
  const conCoords = lista.filter((e) => e.coords);
  const sinCoords = lista.filter((e) => !e.coords).sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  if (conCoords.length === 0) return sinCoords;

  const cabecera = conCoords.reduce((mejor, e) =>
    distanciaKm(e.coords, CENTRO) < distanciaKm(mejor.coords, CENTRO) ? e : mejor
  );
  conCoords.sort((a, b) => distanciaKm(a.coords, cabecera.coords) - distanciaKm(b.coords, cabecera.coords));
  return [...conCoords, ...sinCoords];
}

// Meme del torneo de 30 equipos en 2 zonas de 15: el ramal se parte en
// "Zona A" y "Zona B" de n/2 estaciones, salteadas (no trates de entenderlo).
// Si n es impar, la que sobra "asciende por escritorio". Igual que la app.
export function zonasTorneo(items) {
  const mitad = Math.floor(items.length / 2);
  const zonaA = items.filter((_, i) => i % 2 === 0).slice(0, mitad);
  const zonaB = items.filter((_, i) => i % 2 === 1).slice(0, mitad);
  const escritorio = items.find((e) => !zonaA.includes(e) && !zonaB.includes(e));
  return { mitad, zonaA, zonaB, escritorio };
}

/* ---------- Modo a bordo: dónde va el tren respecto de las paradas ---------- */

// Plano local en km alrededor de una latitud (alcanza para un ramal).
const aPlano = (punto, cosLat) => [punto[1] * 111.32 * cosLat, punto[0] * 110.574];

// Proyecta `punto` sobre la polilínea: km recorridos desde su primer punto
// hasta el punto más cercano, y la distancia (km) del punto a la línea.
export function proyectarEnLinea(linea, punto) {
  if (!linea || linea.length < 2 || !punto) return null;
  const cosLat = Math.cos((punto[0] * Math.PI) / 180);
  const p = aPlano(punto, cosLat);
  let acumulado = 0;
  let mejor = { km: 0, distKm: Infinity };
  for (let i = 0; i < linea.length - 1; i++) {
    const a = aPlano(linea[i], cosLat);
    const b = aPlano(linea[i + 1], cosLat);
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const largo2 = dx * dx + dy * dy;
    const t = largo2 ? Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / largo2)) : 0;
    const dist = Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    const largo = Math.sqrt(largo2);
    if (dist < mejor.distKm) mejor = { km: acumulado + t * largo, distKm: dist };
    acumulado += largo;
  }
  return mejor;
}

export const TOLERANCIA_ESTACION_KM = 0.15; // a menos de esto, "en la estación"
export const MAX_FUERA_DE_VIA_KM = 1; // más lejos de la vía, el GPS no sirve

/**
 * Progreso del viaje a bordo. `paradas` va del origen al destino:
 * [{ nombre, coords: [lat, lon] | null, hora: ms | null }].
 * Con `pos` (GPS) proyecta sobre `linea` (recorrido real; si no hay, une las
 * paradas); sin GPS o lejos de la vía, estima con las horas.
 * Devuelve { ultima, siguiente, enEstacion, llego, fuente: "gps" | "estimado" }:
 * `ultima` = índice de la última parada pasada (-1 ninguna), `siguiente` = la
 * próxima (null al llegar).
 */
export function progresoViaje(paradas, { pos = null, linea = null, ahora = Date.now() } = {}) {
  const n = paradas.length;
  const fin = (ultima, enEstacion, fuente) => ({
    ultima,
    siguiente: ultima + 1 < n ? ultima + 1 : null,
    enEstacion,
    llego: n > 0 && ultima === n - 1,
    fuente,
  });
  const conCoords = paradas.filter((p) => p.coords);
  const via = linea && linea.length > 1 ? linea : conCoords.map((p) => p.coords);
  const yo = pos ? proyectarEnLinea(via, pos) : null;
  if (yo && yo.distKm <= MAX_FUERA_DE_VIA_KM && conCoords.length >= 2) {
    const km = paradas.map((p) => (p.coords ? proyectarEnLinea(via, p.coords).km : null));
    const conocidos = km.filter((k) => k != null);
    const sentido = conocidos[conocidos.length - 1] >= conocidos[0] ? 1 : -1;
    let ultima = -1;
    km.forEach((k, i) => {
      if (k != null && sentido * k <= sentido * yo.km + TOLERANCIA_ESTACION_KM) ultima = i;
    });
    const enEstacion = ultima >= 0 && km[ultima] != null && Math.abs(km[ultima] - yo.km) <= TOLERANCIA_ESTACION_KM;
    return fin(ultima, enEstacion, "gps");
  }
  let ultima = -1;
  paradas.forEach((p, i) => {
    if (p.hora != null && p.hora <= ahora) ultima = i;
  });
  const enEstacion = ultima >= 0 && ahora - paradas[ultima].hora < 30000;
  return fin(ultima, enEstacion, "estimado");
}
