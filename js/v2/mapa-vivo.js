import { buscarEstacionPorId, estacionesDeRuta } from "./estaciones.js";
import { cuentaRegresiva } from "./tarjetas.js";

// Mapa en vivo del viaje: recorrido completo del ramal con sus estaciones,
// tramo origen→destino resaltado y los trenes con su cuenta regresiva.

// Base: Argenmap, el mapa oficial del Instituto Geografico Nacional (TMS).
// Respaldo: CARTO, si el servidor del IGN no responde.
const ARGENMAP = {
  claro: "https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/capabaseargenmap@EPSG%3A3857@png/{z}/{x}/{y}.png",
  oscuro: "https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/argenmap_oscuro@EPSG%3A3857@png/{z}/{x}/{y}.png",
  opciones: {
    tms: true,
    maxZoom: 18,
    attribution: '<a href="https://www.ign.gob.ar/AreaServicios/Argenmap/IntroduccionV2" target="_blank" rel="noopener">Argenmap · IGN</a> + <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  },
};
const CARTO = {
  claro: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
  oscuro: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
  opciones: {
    subdomains: "abcd",
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
  },
};

function temaOscuro() {
  const t = document.documentElement.dataset.theme;
  return t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
}

function colorReal(color) {
  // Leaflet dibuja en SVG con atributos: necesita el color resuelto, no var(--x).
  const m = /var\((--[^)]+)\)/.exec(color);
  return m ? getComputedStyle(document.documentElement).getPropertyValue(m[1]).trim() || "#0056b3" : color;
}

export function crearMapaVivo(contenedor) {
  let map = null;
  let capaTeselas = null;
  let proveedor = ARGENMAP;
  let capaRecorrido = null;
  const trenes = new Map(); // clave -> {marker, datos}
  let claveViaje = "";

  function asegurarMapa() {
    if (map) return;
    map = L.map(contenedor, { zoomControl: true, attributionControl: true, scrollWheelZoom: false });
    map.attributionControl.setPrefix(false);
    ponerTeselas();
    // Rueda del mouse solo despues de tocar el mapa (no secuestra el scroll de la pagina).
    map.on("click", () => map.scrollWheelZoom.enable());
    map.on("mouseout", () => map.scrollWheelZoom.disable());
  }

  function ponerTeselas() {
    if (capaTeselas) capaTeselas.remove();
    capaTeselas = L.tileLayer(temaOscuro() ? proveedor.oscuro : proveedor.claro, proveedor.opciones).addTo(map);
    let errores = 0;
    capaTeselas.on("tileerror", () => {
      if (proveedor === ARGENMAP && ++errores >= 4) {
        proveedor = CARTO;
        ponerTeselas();
      }
    });
  }

  // Orden del recorrido: las paradas reales del servicio si las hay; si no,
  // el orden geografico del ramal.
  function estacionesDelRecorrido({ origen, destino, ruta, paradas }) {
    let lista = paradas?.length ? paradas.map(buscarEstacionPorId).filter(Boolean) : [];
    if (lista.length < 2 && ruta) lista = estacionesDeRuta(ruta.linea, ruta.ramal);
    if (!lista.some((e) => e.id === origen.id)) lista = [origen, ...lista];
    return lista.filter((e) => e.coords);
  }

  function dibujarRecorrido(viaje, color) {
    const c = colorReal(color);
    const estaciones = estacionesDelRecorrido(viaje);
    if (capaRecorrido) capaRecorrido.remove();
    capaRecorrido = L.layerGroup().addTo(map);
    if (estaciones.length === 0) return;

    const puntos = estaciones.map((e) => e.coords);
    L.polyline(puntos, { color: c, weight: 4, opacity: 0.35 }).addTo(capaRecorrido);

    const iO = estaciones.findIndex((e) => e.id === viaje.origen.id);
    const iD = estaciones.findIndex((e) => e.id === viaje.destino.id);
    let tramo = puntos;
    if (iO >= 0 && iD >= 0) {
      tramo = puntos.slice(Math.min(iO, iD), Math.max(iO, iD) + 1);
      L.polyline(tramo, { color: c, weight: 6, opacity: 0.95 }).addTo(capaRecorrido);
    }

    estaciones.forEach((e) => {
      const extremo = e.id === viaje.origen.id || e.id === viaje.destino.id;
      L.circleMarker(e.coords, {
        radius: extremo ? 7 : 4,
        color: c,
        weight: extremo ? 3 : 2,
        fillColor: temaOscuro() ? "#1a1e25" : "#ffffff",
        fillOpacity: 1,
      })
        .bindTooltip(e.nombre, {
          direction: "top",
          offset: [0, -6],
          permanent: extremo,
          className: extremo ? "mapa-etiqueta fuerte" : "mapa-etiqueta",
        })
        .addTo(capaRecorrido);
    });

    return tramo.length > 1 ? tramo : puntos;
  }

  function iconoTren(t, color, ahora) {
    const c = cuentaRegresiva(t.referencia, ahora);
    return L.divIcon({
      className: "",
      html: `<div class="tren-pin" style="--c:${color}"><span class="tren-pin-ico">▲</span><b>${c.num}</b></div>`,
      iconSize: null,
      iconAnchor: [14, 14],
    });
  }

  return {
    /** Muestra/actualiza el mapa con el viaje y los trenes actuales. */
    actualizar(viaje, lista, color) {
      if (!window.L) return;
      asegurarMapa();
      const conGps = lista.filter((t) => t.ubicacion);
      const paradas = lista.find((t) => t.paradas?.length > 1)?.paradas;
      const clave = `${viaje.origen.id}-${viaje.destino.id}-${paradas?.join(",") || ""}`;
      let encuadre = null;
      if (clave !== claveViaje) {
        claveViaje = clave;
        encuadre = dibujarRecorrido({ ...viaje, paradas }, color) || [];
      }

      const vistos = new Set();
      const ahora = Date.now();
      for (const t of conGps) {
        vistos.add(t.clave);
        const pos = [t.ubicacion.lat, t.ubicacion.lon];
        let tren = trenes.get(t.clave);
        if (!tren) {
          const marker = L.marker(pos, { icon: iconoTren(t, color, ahora), zIndexOffset: 1000 }).addTo(map);
          marker.bindTooltip("", { direction: "top", offset: [0, -14], className: "mapa-etiqueta" });
          tren = { marker };
          trenes.set(t.clave, tren);
        }
        tren.datos = t;
        tren.color = color;
        tren.marker.setLatLng(pos);
        tren.marker.setTooltipContent(`${t.ramal}${t.anden ? ` · Andén ${t.anden}` : ""}`);
      }
      for (const [clave, tren] of trenes) {
        if (!vistos.has(clave)) {
          tren.marker.remove();
          trenes.delete(clave);
        }
      }
      // Al cambiar de viaje: encuadra el tramo y los trenes que se acercan.
      if (encuadre) {
        const pts = [...encuadre, ...conGps.map((t) => [t.ubicacion.lat, t.ubicacion.lon])];
        if (pts.length) map.fitBounds(L.latLngBounds(pts), { padding: [30, 30], maxZoom: 15 });
      }
      return conGps.length;
    },

    /** Refresca la cuenta regresiva de cada tren (cada segundo). */
    tick(ahora = Date.now()) {
      for (const tren of trenes.values()) {
        const b = tren.marker.getElement()?.querySelector("b");
        if (b) b.textContent = cuentaRegresiva(tren.datos.referencia, ahora).num;
      }
    },

    enfocar(clave) {
      const tren = trenes.get(clave);
      if (!tren) return;
      map.flyTo(tren.marker.getLatLng(), Math.max(map.getZoom(), 14), { duration: 0.6 });
      tren.marker.openTooltip();
    },

    refrescarTamano() {
      map?.invalidateSize();
    },

    cambiarTema() {
      if (map) {
        ponerTeselas();
        claveViaje = ""; // redibuja las estaciones con el relleno del tema
      }
    },

    reiniciar() {
      for (const tren of trenes.values()) tren.marker.remove();
      trenes.clear();
      claveViaje = "";
    },
  };
}
