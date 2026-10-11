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
