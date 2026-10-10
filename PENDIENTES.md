# Estado y pendientes

Sitio estático en GitHub Pages (deploy desde `main`, dominio `tapia.chiqui.ar` por CNAME).
Push: `git push git@github.com:ppaipi/trenesargentinos.git HEAD:main` (HTTPS no tiene credenciales).

## Regla de versiones
Cada cambio importante va en una versión nueva (`vN.html` + `js/vN/` + `css/vN*.css`),
sin tocar `index.html`. Arreglos chicos pueden ir en la versión actual.

## Versiones
- `index.html` — clásica (sin tocar).
- `v1.html` — rediseño estilo cartel de andén (LED ámbar), favoritos, cercanas, avisos 5 min.
- `v2.html` — diseño cálido (Inter, celeste), mapa en vivo compartido, estado del servicio.
- `v3.html` — Mapa individual por tarjeta con recorrido real sobre las vías
  (`recorridos.json`), ícono de tren, avisos solo por campana o cuando no hay trenes,
  botón "?" → patrocinador.
- `v4.html` — **actual**. v3 + punto azul del usuario en el mapa (botón "mi ubicación",
  `js/v4/ubicacion.js`, sin pedir permiso hasta tocarlo), último resultado guardado
  por viaje (se muestra mientras carga, 45 min), sentido "hacia X" en cada tarjeta,
  tema claro por defecto.
- `patrocinador.html` — v4 + patrocinio meme "Sancord/Sancor Seguros" (re molesto) y
  Chiqui Tapia ("No trates de entenderla, disfrutala"). Lógica en `js/v4/patrocinio.js`,
  estilos en `css/patrocinio.css`. Imágenes: `img/patrocinador.png` (logo que dio el
  usuario), `img/tapia.png` (Wikimedia Commons, Ministerio de Cultura, CC BY-SA 2.0,
  crédito en el pie).

## Datos
- API: `api-servicios.sofse.gob.ar/v1` (token en `js/vN/api.js`).
  - Arribos: `/arribos/estacion/{id}?hasta={id}[&fecha&hora&tipoBusqueda=partida]`
  - Avisos: `/infraestructura/gerencias` (alertas por línea, vigencia, criticidad).
- `estaciones-coords.json`: coords de OSM (sin subte) + manuales. 35 de 252 sin coords.
- `recorridos.json`: polilínea por `linea|||ramal`, Dijkstra sobre vías OSM + red ADIF
  (datos.transporte.gob.ar). San Martín tiene algún tramo recto (corte en los datos).
- Mapa base: Argenmap (IGN, TMS) con respaldo CARTO.

## Pendientes
1. Coordenadas de las 35 estaciones faltantes (H. Yrigoyen, Villa Lugano, Haedo…).
2. Investigar si la API trae estado de ascensores/escaleras.
3. App Android para Play Store vía TWA (PWABuilder/Bubblewrap): cuenta US$25,
   `/.well-known/assetlinks.json` (Jekyll ignora carpetas con punto: agregar
   `include: [".well-known"]` en `_config.yml`), prueba cerrada 12 testers × 14 días.
4. "Avisame" con la app cerrada (requiere app nativa o servidor push).
