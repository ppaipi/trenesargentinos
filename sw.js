const CACHE_NAME = "trenes-arg-v2";
const ARCHIVOS_PRECACHE = [
  "/index.html",
  "/gps.html",
  "/css/style.css",
  "/js/app.js",
  "/js/gps.js",
  "/js/buscador.js",
  "/js/mapa.js",
  "/estaciones.json",
  "/tren.png",
  "/icon-192.png",
  "/manifest.json",
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
      return abierta ? abierta.focus() : self.clients.openWindow("/v1.html");
    })
  );
});
