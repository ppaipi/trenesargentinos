import { estacionesDeRuta, rutaCompartida } from "./estaciones.js";
import { cuentaRegresiva } from "./tarjetas.js";
import { escucharUbicacion } from "./ubicacion.js";
import { toast as avisoUbicacion } from "./extras.js";

// Mapa individual de un tren: recorrido real del ramal (siguiendo las vias,
// datos de OSM + ADIF precalculados en recorridos.json), estaciones, tramo
// origen→destino resaltado y el tren siguiendose en vivo.

const ARGENMAP = {
  claro: "https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/capabaseargenmap@EPSG%3A3857@png/{z}/{x}/{y}.png",
  oscuro: "https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/argenmap_oscuro@EPSG%3A3857@png/{z}/{x}/{y}.png",
  opciones: {
    tms: true,
    maxZoom: 18,
    attribution: '<a href="https://www.ign.gob.ar/AreaServicios/Argenmap/introduccion" target="_blank" rel="noopener">Argenmap · IGN</a> + <a href="https://www.openstreetmap.org/copyright">OSM</a>',
  },
};
const CARTO = {
  claro: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
  oscuro: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
  opciones: { subdomains: "abcd", maxZoom: 19, attribution: "&copy; OpenStreetMap &copy; CARTO" },
};
let proveedor = ARGENMAP;

// Tren visto de frente: se reconoce aun en tamaño chico.
const ICONO_TREN =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="2.5" width="14" height="15" rx="3.5" fill="#fff"/><rect x="7.2" y="5" width="9.6" height="5.2" rx="1.2" fill="currentColor"/><circle cx="8.8" cy="13.6" r="1.3" fill="currentColor"/><circle cx="15.2" cy="13.6" r="1.3" fill="currentColor"/><path d="M8 18.5 6 21.5M16 18.5l2 3" stroke="#fff" stroke-width="1.8" stroke-linecap="round"/></svg>';

let recorridos = null;
export function cargarRecorridos() {
  if (!recorridos) {
    recorridos = fetch("recorridos.json?v=5")
      .then((r) => (r.ok ? r.json() : {}))
      .catch(() => ({}));
  }
  return recorridos;
}

function temaOscuro() {
  const t = document.documentElement.dataset.theme;
  return t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
}

function colorReal(color) {
  const m = /var\((--[^)]+)\)/.exec(color);
  return m ? getComputedStyle(document.documentElement).getPropertyValue(m[1]).trim() || "#0056b3" : color;
}

// Argenmap; si falla, CARTO (y queda CARTO para los mapas siguientes).
function capaBase(map) {
  map.attributionControl.setPrefix(false);
  const capa = L.tileLayer(temaOscuro() ? proveedor.oscuro : proveedor.claro, proveedor.opciones).addTo(map);
  let errores = 0;
  capa.on("tileerror", () => {
    if (proveedor === ARGENMAP && ++errores >= 4) {
      proveedor = CARTO;
      capa.setUrl(temaOscuro() ? CARTO.oscuro : CARTO.claro);
      L.setOptions(capa, CARTO.opciones);
    }
  });
}

// Mapa de una línea programada (Urquiza, Belgrano Norte): recorrido sobre
// vías OSM y estaciones, sin tren en vivo. `estaciones`: [[nombre, lat, lon]].
export function crearMapaLinea(contenedor, { recorrido, estaciones, color }) {
  let map = null;
  return {
    async abrir() {
      if (!window.L) return;
      if (map) return setTimeout(() => map.invalidateSize(), 30);
      const c = colorReal(color);
      map = L.map(contenedor, { scrollWheelZoom: false, zoomControl: true });
      map.on("click", () => map.scrollWheelZoom.enable());
      capaBase(map);
      const puntos = estaciones.map(([, lat, lon]) => [lat, lon]);
      map.fitBounds(L.latLngBounds(puntos).pad(0.08));
      const todos = await cargarRecorridos();
      if (!map) return;
      const linea = todos[recorrido]?.length > 1 ? todos[recorrido] : puntos;
      L.polyline(linea, { color: c, weight: 5, opacity: 0.95 }).addTo(map);
      const relleno = temaOscuro() ? "#1a1e25" : "#ffffff";
      estaciones.forEach(([nombre, lat, lon], i) => {
        const extremo = i === 0 || i === estaciones.length - 1;
        L.circleMarker([lat, lon], { radius: extremo ? 6 : 3.5, color: c, weight: extremo ? 3 : 2, fillColor: relleno, fillOpacity: 1 })
          .bindTooltip(nombre, {
            direction: extremo ? "left" : "top",
            offset: extremo ? [-8, 0] : [0, -5],
            permanent: extremo,
            className: extremo ? "mapa-etiqueta fuerte" : "mapa-etiqueta",
          })
          .addTo(map);
      });
    },
    destruir() {
      map?.remove();
      map = null;
    },
  };
}

const d2 = (a, b) => (a[0] - b[0]) ** 2 + ((a[1] - b[1]) * 0.82) ** 2;

function indiceMasCercano(linea, punto) {
  let mejor = 0;
  let dist = Infinity;
  linea.forEach((p, i) => {
    const d = d2(p, punto);
    if (d < dist) {
      dist = d;
      mejor = i;
    }
  });
  return mejor;
}

// Entre los ramales que comparten origen y destino, el que nombra el
// servicio ("Constitución-Ezeiza" → ramal Ezeiza).
export function elegirRuta(origen, destino, nombreServicio) {
  const compartidas = origen.rutas.filter((ra) =>
    destino.rutas.some((rb) => ra.linea === rb.linea && ra.ramal === rb.ramal)
  );
  const texto = (nombreServicio || "").toLowerCase();
  return compartidas.find((r) => texto.includes(r.ramal.toLowerCase())) || rutaCompartida(origen, destino);
}

export function crearMapaTren(contenedor, { origen, destino, color }) {
  const c = colorReal(color);
  let map = null;
  let marcador = null;
  let datos = null;
  let siguiendo = true; // el mapa acompaña al tren hasta que el usuario lo mueve
  let dibujado = false;
  let yo = null; // punto azul del usuario
  let yoRadio = null;
  let dejarDeEscuchar = null;
  let centrarEnYo = false; // el usuario pidio verse: encuadrar al llegar la ubicacion

  function mostrarYo(pos, err) {
    if (!map) return;
    if (!pos) {
      if (err && centrarEnYo) avisoUbicacion(err.code === 1 ? "Permiso de ubicación denegado." : "No pudimos obtener tu ubicación.");
      centrarEnYo = false;
      return;
    }
    const ll = [pos.lat, pos.lon];
    if (!yo) {
      yoRadio = L.circle(ll, { radius: pos.precision, color: "#2b7bff", weight: 1, fillOpacity: 0.12, interactive: false }).addTo(map);
      yo = L.circleMarker(ll, { radius: 7, color: "#fff", weight: 2.5, fillColor: "#2b7bff", fillOpacity: 1 })
        .bindTooltip("Vos", { direction: "top", offset: [0, -6], className: "mapa-etiqueta fuerte" })
        .addTo(map);
    } else {
      yo.setLatLng(ll);
      yoRadio.setLatLng(ll).setRadius(pos.precision);
    }
    if (centrarEnYo) {
      centrarEnYo = false;
      siguiendo = false;
      const puntos = [ll];
      if (datos?.ubicacion) puntos.push([datos.ubicacion.lat, datos.ubicacion.lon]);
      map.fitBounds(L.latLngBounds(puntos).pad(0.3), { maxZoom: 15 });
    }
  }

  const ControlYo = L.Control?.extend({
    options: { position: "topleft" },
    onAdd() {
      const b = L.DomUtil.create("button", "leaflet-bar control-yo");
      b.type = "button";
      b.title = "Mi ubicación";
      b.setAttribute("aria-label", "Mostrar mi ubicación");
      b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="3.5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/><circle cx="12" cy="12" r="7.5"/></svg>';
      L.DomEvent.disableClickPropagation(b);
      L.DomEvent.on(b, "click", () => {
        if (!navigator.geolocation) return avisoUbicacion("Tu navegador no permite ubicación.");
        centrarEnYo = true;
        if (yo) mostrarYo({ lat: yo.getLatLng().lat, lon: yo.getLatLng().lng, precision: yoRadio.getRadius() });
        dejarDeEscuchar?.();
        dejarDeEscuchar = escucharUbicacion(mostrarYo, { pedir: true });
      });
      return b;
    },
  });

  function iniciar() {
    map = L.map(contenedor, { scrollWheelZoom: false, zoomControl: true });
    capaBase(map);
    map.on("dragstart zoomstart", (e) => {
      if (e.hard !== false && map._seMueveSolo !== true) siguiendo = false;
    });
    map.on("click", () => map.scrollWheelZoom.enable());
    map.setView(origen.coords || [-34.6, -58.4], 13);
    new ControlYo().addTo(map);
    dejarDeEscuchar = escucharUbicacion(mostrarYo);
  }

  async function dibujarRecorrido() {
    const ruta = elegirRuta(origen, destino, datos?.ramal);
    const todos = await cargarRecorridos();
    const estaciones = ruta ? estacionesDeRuta(ruta.linea, ruta.ramal).filter((e) => e.coords) : [];
    let linea = ruta ? todos[`${ruta.linea}|||${ruta.ramal}`] : null;
    if (!linea || linea.length < 2) linea = estaciones.map((e) => e.coords); // respaldo: une estaciones

    if (linea.length > 1) {
      L.polyline(linea, { color: c, weight: 4, opacity: 0.3 }).addTo(map);
      if (origen.coords && destino.coords) {
        const i = indiceMasCercano(linea, origen.coords);
        const j = indiceMasCercano(linea, destino.coords);
        const tramo = linea.slice(Math.min(i, j), Math.max(i, j) + 1);
        if (tramo.length > 1) L.polyline(tramo, { color: c, weight: 6, opacity: 0.95 }).addTo(map);
      }
    }

    const relleno = temaOscuro() ? "#1a1e25" : "#ffffff";
    estaciones.forEach((e) => {
      const extremo = e.id === origen.id || e.id === destino.id;
      L.circleMarker(e.coords, { radius: extremo ? 6 : 3.5, color: c, weight: extremo ? 3 : 2, fillColor: relleno, fillOpacity: 1 })
        .bindTooltip(e.nombre, {
          direction: extremo ? "left" : "top",
          offset: extremo ? [-8, 0] : [0, -5],
          permanent: extremo,
          className: extremo ? "mapa-etiqueta fuerte" : "mapa-etiqueta",
        })
        .addTo(map);
    });
    if (marcador) marcador.bringToFront?.();
  }

  function moverA(lat, lon, animar) {
    map._seMueveSolo = true;
    map.panTo([lat, lon], { animate: animar, duration: 0.8 });
    setTimeout(() => (map._seMueveSolo = false), 900);
  }

  return {
    abrir() {
      if (!window.L) return;
      if (!map) iniciar();
      setTimeout(() => map.invalidateSize(), 30);
      if (!dibujado) {
        dibujado = true;
        dibujarRecorrido();
      }
      if (datos) this.actualizar(datos);
    },

    actualizar(t) {
      datos = t;
      if (!map || !t.ubicacion) return;
      const pos = [t.ubicacion.lat, t.ubicacion.lon];
      if (!marcador) {
        marcador = L.marker(pos, {
          zIndexOffset: 1000,
          icon: L.divIcon({
            className: "",
            html: `<div class="tren-marca" style="--c:${c}"><div class="tren-circulo">${ICONO_TREN}</div><span class="tren-cuenta"></span></div>`,
            iconSize: [44, 56],
            iconAnchor: [22, 22],
          }),
        }).addTo(map);
        map.setView(pos, 13);
      } else {
        marcador.setLatLng(pos);
        if (siguiendo) moverA(pos[0], pos[1], true);
      }
      this.tick();
    },

    tick(ahora = Date.now()) {
      const el = marcador?.getElement()?.querySelector(".tren-cuenta");
      if (el && datos) el.textContent = cuentaRegresiva(datos.referencia, ahora).num;
    },

    seguir() {
      siguiendo = true;
      if (datos?.ubicacion) moverA(datos.ubicacion.lat, datos.ubicacion.lon, true);
    },

    destruir() {
      dejarDeEscuchar?.();
      map?.remove();
      map = null;
    },
  };
}
