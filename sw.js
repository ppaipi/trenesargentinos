const CACHE_NAME = "trenes-arg-v4";
// Lo que usa index.html para abrir sin conexión (las vistas con ?v= se
// sirven igual: el respaldo busca con ignoreSearch).
const ARCHIVOS_PRECACHE = [
  "/",
  "/index.html",
  "/manifest.json",
  "/css/v4.css",
  "/css/v4-avisos-mapa.css",
  "/css/v5-programados.css",
  "/css/patrocinio.css",
  "/css/v6-marca.css",
  "/css/anden.css",
  "/js/v4/anden.js",
  "/js/v4/app.js",
  "/js/v4/api.js",
  "/js/v4/avisos.js",
  "/js/v4/buscador.js",
  "/js/v4/estaciones.js",
  "/js/v4/extras.js",
  "/js/v4/logica.js",
  "/js/v4/mapa-tren.js",
  "/js/v4/patrocinio.js",
  "/js/v4/tarjetas.js",
  "/js/v4/ubicacion.js",
  "/js/v5/programados.js",
  "/js/v5/programados-data.js",
  "/js/v6/marca.js",
  "/js/compat/app.js",
  "/js/compat/patrocinio.js",
  "/estaciones.json",
  "/estaciones-coords.json",
  "/recorridos.json",
  "/img/trenar-32.png",
  "/img/trenar-192.png",
  "/img/trenar-512.png",
  "/icon-192.png",
  "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
  "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(ARCHIVOS_PRECACHE)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET") return;

  // Los horarios son datos en vivo: nunca se cachean, van directo a red.
  if (url.hostname.includes("sofse.gob.ar")) return;

  // Archivos propios: primero red (asi las actualizaciones llegan siempre) y
  // cache como respaldo sin conexion. Antes era cache-first y la app quedaba
  // congelada en la version del primer ingreso.
  if (url.origin === self.location.origin) {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          if (res.ok) {
            const copia = res.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copia));
          }
          return res;
        })
        .catch(() => caches.match(event.request, { ignoreSearch: true }))
    );
    return;
  }

  event.respondWith(caches.match(event.request).then((cacheado) => cacheado || fetch(event.request)));
});

// Al tocar una notificacion de aviso, vuelve a la app abierta (o la abre).
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((lista) => {
      const abierta = lista.find((c) => "focus" in c);
      return abierta ? abierta.focus() : self.clients.openWindow("/");
    })
  );
});
