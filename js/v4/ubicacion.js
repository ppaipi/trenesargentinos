// Ubicacion del usuario compartida entre los mapas: un solo watchPosition
// para todos. Solo se pide permiso cuando el usuario toca "mi ubicación";
// si ya lo habia dado antes, el punto aparece solo.

const oyentes = new Set();
let watchId = null;
let ultima = null; // { lat, lon, precision }

function arrancar() {
  if (watchId != null || !navigator.geolocation) return;
  watchId = navigator.geolocation.watchPosition(
    (pos) => {
      ultima = { lat: pos.coords.latitude, lon: pos.coords.longitude, precision: pos.coords.accuracy };
      oyentes.forEach((fn) => fn(ultima));
    },
    (err) => {
      navigator.geolocation.clearWatch(watchId);
      watchId = null;
      oyentes.forEach((fn) => fn(null, err));
    },
    { enableHighAccuracy: true, maximumAge: 15000, timeout: 20000 }
  );
}

export async function permisoDado() {
  try {
    return (await navigator.permissions?.query({ name: "geolocation" }))?.state === "granted";
  } catch {
    return false;
  }
}

// Escucha la ubicacion. `pedir` fuerza el pedido de permiso.
export function escucharUbicacion(fn, { pedir = false } = {}) {
  oyentes.add(fn);
  if (ultima) fn(ultima);
  if (pedir) arrancar();
  else permisoDado().then((si) => si && arrancar());
  return () => {
    oyentes.delete(fn);
    if (!oyentes.size && watchId != null) {
      navigator.geolocation.clearWatch(watchId);
      watchId = null;
    }
  };
}

export const ubicacionActual = () => ultima;
