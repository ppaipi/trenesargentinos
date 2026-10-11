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
- `v5.html` — v4 + sección "Otras líneas (horario programado)" para
  Urquiza (Metrovías) y Belgrano Norte (Ferrovías): no las opera SOFSE, no hay
  arribos en vivo. Muestra cabeceras/frecuencia según horario oficial, etiqueta
  "programado · no en vivo" y link a la fuente (modelo anden.app: `scheduled`).
  Datos en `js/v5/programados-data.js`, lógica en `js/v5/programados.js`, estilos
  en `css/v5-programados.css`. Reusa `js/v4/app.js` sin tocarlo.
- `v6.html` — **actual**. v5 + marca **TrenAR**: logo (ilustración Obelisco/andén,
  `android/marca/trenar-logo-original.png`, íconos en `img/trenar-*.png`), wordmark
  "Tren" + pastilla "AR", `manifest-v6.json`. El "?" abre "Acerca de"; el meme queda
  escondido (5 toques al logo o el renglón casi invisible de Acerca de → patrocinador.html).
  Estilos `css/v6-marca.css`, lógica `js/v6/marca.js`. Misma marca que la app Android.
- `patrocinador.html` — v4 + patrocinio meme "Sancord/Sancor Seguros" (re molesto) y
  Chiqui Tapia ("No trates de entenderla, disfrutala"). Lógica en `js/v4/patrocinio.js`,
  estilos en `css/patrocinio.css`. Imágenes: `img/patrocinador.png` (logo que dio el
  usuario), `img/tapia.png` (Wikimedia Commons, Ministerio de Cultura, CC BY-SA 2.0,
  crédito en el pie).

## Datos
- API: `api-servicios.sofse.gob.ar/v1` (token en `js/vN/api.js`).
  - Arribos: `/arribos/estacion/{id}?hasta={id}[&fecha&hora&tipoBusqueda=partida]`
  - Avisos: `/infraestructura/gerencias` (alertas por línea, vigencia, criticidad).
- `estaciones-coords.json`: coords de OSM (sin subte) + manuales. Las 252 tienen coords
  (ramal Puente Alsina y H. Yrigoyen salen de nodos `disused:railway` de OSM).
- `recorridos.json`: polilínea por `linea|||ramal`, Dijkstra sobre vías OSM + red ADIF
  (datos.transporte.gob.ar). San Martín tiene algún tramo recto (corte en los datos).
- Mapa base: Argenmap (IGN, TMS) con respaldo CARTO.

## Pendientes
1. Investigar si la API trae estado de ascensores/escaleras. El repo de referencia
   `alandaitch/anden` no documenta endpoints a propósito; tantear la API lo bloquea
   el clasificador. Abierto.
0. v5: horarios programados cargados desde PDFs oficiales. Urquiza: PDF Metrovías
   "invierno 2026" (vigente 02/03/2026, frecuencia desde Lacroze). Belgrano Norte:
   Horario N° 21 (punta a punta, frecuencia desde Retiro). Al cambiar un horario
   (ej. Urquiza verano), re-parsear el PDF nuevo con `pdftotext -layout`.
2. App Android nativa **TrenAR** (Kotlin + Compose) en `android/`, paquete `ar.chiqui.trenes`.
   Íconos: `cd android/marca && python3 -I generar_icono.py` (desde el logo original).
   Igual a v5 (sin patrocinio): buscador, arribos en vivo, favoritos, cercanas, mapa
   osmdroid + Argenmap con recorrido real, avisos, líneas programadas (plegadas).
   "Avisame" usa AlarmManager: llega con la app cerrada (último horario conocido).
   Los JSON de la raíz se copian a assets al compilar (`copiarDatos`).
   Herramientas en `~/Android` (jdk, Sdk, emulador AVD `prueba`). Build:
   `cd android && JAVA_HOME=~/Android/jdk ./gradlew assembleRelease` (o `bundleRelease` para Play).
   Firma: `~/Android/trenes-subida.jks` + `trenes-firma.properties` (FUERA del repo; hacer backup).
   Falta: pagar cuenta Play (US$25), ficha de la tienda (ícono 512: `android/marca/trenar-icono-512.png`), prueba cerrada
   12 testers × 14 días. `programados` duplicado en `datos/Programados.kt`: actualizar ambos.
3. "Avisame" con la app cerrada en la web (requiere servidor push). En la app Android ya funciona.
