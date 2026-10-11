# Estado y pendientes

Sitio estático en GitHub Pages (deploy desde `main`, dominio `tapia.chiqui.ar` por CNAME).
Push: `git push git@github.com:ppaipi/trenesargentinos.git HEAD:main` (HTTPS no tiene credenciales).

## Estructura
Todo vive en `index.html` (se terminaron las vN). `clasica.html` = la versión vieja.
`v4.html`–`v6.html` solo redirigen a `/` (links compartidos y PWA instaladas); v1–v3 borrados.
Código: `js/v4/` (app, buscador, avisos, mapa…), `js/v5/` (líneas programadas), `js/v6/marca.js`;
CSS `css/v4*.css`, `css/v5-programados.css`, `css/v6-marca.css`, `css/patrocinio.css`.
`patrocinador.html` = página del meme (Sancord + Chiqui). `manifest.json` (start_url `/`).
- Marca **TrenAR**: logo en `img/trenar-*.png`; meme con el botón del ojo o 5 toques al logo
  (localStorage `trenAR.meme`).
- **Navegadores viejos** (sin `?.`/`??`): index.html carga `js/compat/` (misma app compilada a
  2017: Chrome 55, Safari 11, Firefox 57). **Después de tocar `js/` correr
  `herramientas/compatible.sh`** (esbuild + `herramientas/polyfills.js`). `?compat=1` la fuerza.

## Paridad web == app
Todo cambio va en la web y en la app Android a la vez.
- Buscador: chips de ramal + "Otras líneas" + Torneo Chiqui (meme: ramal en Zona A/B).
- Cartel al elegir estación con el aviso más grave de su línea (5 s; tocarlo abre los avisos).
- Cuenta regresiva del próximo tren: título de la pestaña (web) / notificación fija con
  cronómetro al salir de la app (Android 7+; en Android 6 muestra la hora).
- Atajos del ícono: app = hasta 4 favoritos dinámicos (`Atajos.kt`, link `?o=&d=`, Android 7.1+);
  web = estáticos del manifest (`?ir=favorito|cercanas|avisos`), no puede listar favoritos.

## Datos
- API: `api-servicios.sofse.gob.ar/v1` (token en `js/vN/api.js`).
  - Arribos: `/arribos/estacion/{id}?hasta={id}[&fecha&hora&tipoBusqueda=partida]`
  - Avisos: `/infraestructura/gerencias` (alertas por línea, vigencia, criticidad).
- `estaciones-coords.json`: coords de OSM (sin subte) + manuales. Las 252 tienen coords
  (ramal Puente Alsina y H. Yrigoyen salen de nodos `disused:railway` de OSM).
- `recorridos.json`: polilínea por `linea|||ramal`, Dijkstra sobre vías OSM + red ADIF
  (datos.transporte.gob.ar). Regenerado (oct 2026) con vías OSM (Overpass, rail+disused) +
  Dijkstra por cada estación (orden por distancia sobre la vía desde una punta), vías de la
  propia línea preferidas, sin lazos ida-y-vuelta. TdlC (`light_rail`) y Apeadero Km 12 (tramo
  `railway=razed` en Budge) rehechos con `herramientas/recorrido.py`. Al cambiarlo,
  subir `recorridos.json?v=N` en `js/v4/mapa-tren.js`.
- Mapa base: Argenmap (IGN, TMS) con respaldo CARTO.

## Tests y CI
- Tests: `npm test` (Node, `tests/`, lógica pura en `js/v4/logica.js`) y
  `cd android && JAVA_HOME=~/Android/jdk ./gradlew :app:testDebugUnitTest` (`LogicaTest.kt`).
- `.github/workflows/compatible.yml`: en cada push a main corre los tests y
  `compatible.sh`, y commitea `js/compat/` si cambió.
- App: `versionCode` = `git rev-list --count HEAD` (no compilar desde clon superficial).

## Pendientes
1. Investigar si la API trae estado de ascensores/escaleras. El repo de referencia
   `alandaitch/anden` no documenta endpoints a propósito; tantear la API lo bloquea
   el clasificador. Abierto.
0. v5: horarios programados cargados desde PDFs oficiales. Urquiza: PDF Metrovías
   "invierno 2026" (vigente 02/03/2026, frecuencia desde Lacroze). Belgrano Norte:
   Horario N° 21 (punta a punta, frecuencia desde Retiro). Al cambiar un horario
   (ej. Urquiza verano), re-parsear el PDF nuevo con `pdftotext -layout`.
2. App Android nativa **TrenAR** (Kotlin + Compose) en `android/`, paquete `ar.chiqui.trenes`.
   minSdk 23 (Android 6) con desugaring de java.time; probada en AVD `android6`. User-Agent `TrenAR/1.0`.
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

## Próximas mejoras (orden propuesto; hacer de a una, con paridad web/app)
Prioridad: 12 y 15.
12. **Widget Android** con el próximo tren de un viaje favorito (web: no aplica; ya tiene título).
13. Mapa con recorrido para Urquiza y Belgrano Norte (programados).
14. Alerta si el tren seguido desaparece o suma más de X min de demora.
15. **Modo "estoy en el andén"**: pantalla grande con la cuenta regresiva, sin apagarse
    (Wake Lock en web, `keepScreenOn` en app), legible de lejos.
