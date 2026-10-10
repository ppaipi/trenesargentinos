export const COLOR_LINEA = {
  "Mitre": "var(--linea-mitre)",
  "Sarmiento": "var(--linea-sarmiento)",
  "Roca": "var(--linea-roca)",
  "San Martín": "var(--linea-sanmartin)",
  "Belgrano Sur": "var(--linea-belgranosur)",
  "Tren de la Costa": "var(--linea-trendelacosta)",
  "Belgrano Norte": "var(--linea-belgranonorte)",
  "Regionales": "var(--linea-regionales)",
  "Trenes de Terceros": "var(--linea-trenesterceros)",
};

export const LINEAS_PRINCIPALES = ["Mitre", "Sarmiento", "Roca", "San Martín", "Belgrano Sur", "Tren de la Costa"];

// Punto de referencia para elegir la cabecera de cada ramal: la estacion
// del ramal mas cercana al centro porteño (Retiro, Once, Constitucion, etc).
const CENTRO = [-34.6037, -58.3816];

let estaciones = [];
let porId = new Map();
let rutaIndice = new Map(); // "linea|||ramal" -> estaciones[] en orden geografico

export function normalizar(texto) {
  return texto.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
}

export function distanciaKm([lat1, lon1], [lat2, lon2]) {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(a));
}

// Ordena las estaciones de un ramal desde la cabecera hacia afuera. Las que
// no tienen coordenadas quedan al final en orden alfabetico.
function ordenarGeografico(lista) {
  const conCoords = lista.filter((e) => e.coords);
  const sinCoords = lista.filter((e) => !e.coords).sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  if (conCoords.length === 0) return sinCoords;

  const cabecera = conCoords.reduce((mejor, e) =>
    distanciaKm(e.coords, CENTRO) < distanciaKm(mejor.coords, CENTRO) ? e : mejor
  );
  conCoords.sort((a, b) => distanciaKm(a.coords, cabecera.coords) - distanciaKm(b.coords, cabecera.coords));
  return [...conCoords, ...sinCoords];
}

export async function cargarEstaciones() {
  const [resEst, resCoords] = await Promise.all([fetch("estaciones.json"), fetch("estaciones-coords.json?v=3")]);
  if (!resEst.ok) throw new Error("Error al cargar estaciones: " + resEst.status);
  estaciones = await resEst.json();
  const coords = resCoords.ok ? await resCoords.json().catch(() => ({})) : {};

  porId = new Map();
  rutaIndice = new Map();
  estaciones.forEach((estacion) => {
    estacion.coords = coords[estacion.id] || null;
    estacion.busqueda = normalizar(estacion.nombre);
    porId.set(estacion.id, estacion);
    estacion.rutas.forEach((ruta) => {
      const key = `${ruta.linea}|||${ruta.ramal}`;
      if (!rutaIndice.has(key)) rutaIndice.set(key, []);
      rutaIndice.get(key).push(estacion);
    });
  });

  for (const [key, lista] of rutaIndice) rutaIndice.set(key, ordenarGeografico(lista));
  return estaciones;
}

export function buscarEstacionPorId(id) {
  return porId.get(Number(id));
}

export function ramalesDeLinea(linea) {
  const ramales = new Set();
  estaciones.forEach((e) => e.rutas.forEach((r) => r.linea === linea && ramales.add(r.ramal)));
  return [...ramales].sort((a, b) => a.localeCompare(b, "es"));
}

export function estacionesDeRuta(linea, ramal) {
  return rutaIndice.get(`${linea}|||${ramal}`) || [];
}

export function rutaCompartida(a, b) {
  return a.rutas.find((ra) => b.rutas.some((rb) => ra.linea === rb.linea && ra.ramal === rb.ramal)) || null;
}

export function colorDeEstacion(estacion) {
  return COLOR_LINEA[estacion.rutas[0].linea] || "#999";
}

export function lineasDeEstacion(estacion) {
  return [...new Set(estacion.rutas.map((r) => r.linea))].join(", ");
}

// Busqueda por texto: primero las que empiezan con el texto, despues las que
// lo contienen en cualquier palabra.
export function buscarPorTexto(texto) {
  const objetivo = normalizar(texto.trim());
  if (!objetivo) return [];
  const empiezan = [];
  const contienen = [];
  for (const e of estaciones) {
    if (e.busqueda.startsWith(objetivo)) empiezan.push(e);
    else if (e.busqueda.includes(objetivo)) contienen.push(e);
  }
  const porNombre = (a, b) => a.nombre.localeCompare(b.nombre, "es");
  return [...empiezan.sort(porNombre), ...contienen.sort(porNombre)];
}

export function estacionesCercanas(posicion, cantidad = 6) {
  return estaciones
    .filter((e) => e.coords)
    .map((e) => ({ estacion: e, km: distanciaKm(posicion, e.coords) }))
    .sort((a, b) => a.km - b.km)
    .slice(0, cantidad);
}

export function formatearDistancia(km) {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1).replace(".", ",")} km`;
}
