# Estado y pendientes

Sitio estático en GitHub Pages (deploy desde `main`, dominio `tapia.chiqui.ar` por CNAME).
Push: `git push git@github.com:ppaipi/trenesargentinos.git HEAD:main` (HTTPS no tiene credenciales).

## Estructura
Todo vive en `index.html`. Se borraron vN.html, clasica.html, gps.html y patrocinador.html;
`404.html` redirige cualquier link viejo a `/`.
Código plano en `js/` (app, buscador, avisos, mapa-tren, anden, programados, marca…);
CSS `css/base.css`, `avisos-mapa.css`, `programados.css`, `marca.css`, `anden.css`, `patrocinio.css`.
APK: `.github/workflows/apk.yml` lo compila firmado (secrets ANDROID_*) en cada push a main que toque
`android/` o los JSON, y lo publica como Release; botón del pie (oculto en iOS) y README usan
`releases/latest/download/TrenAR.apk`.
Íconos web: `apple-touch-icon.png` (180, iOS) y `favicon.ico` en la raíz, desde el logo nuevo.
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
- Tren con "Avisame": alerta si desaparece antes de su hora o suma >= 5 min de demora
  (`detectarCambioTren` en `logica.js` / `datos/Seguimiento.kt`; web `revisarSeguidos`, app `Seguidos.kt`).
- Widget Android (solo app, `android/.../widget/`): próximo y siguiente tren de un favorito o
  reciente elegido al agregarlo (`ConfigWidget`), cuenta regresiva en vivo (Android 7+), se
  redibuja cada minuto con la pantalla prendida, consulta la API si el dato tiene más de 5 min
  (`Api.proximos`, compartido con la app); tocarlo abre el viaje (link `?o=&d=`).
- "Modo andén" (botón en cada tarjeta en vivo; `js/anden.js` + `css/anden.css` / `ui/Anden.kt`): cuenta regresiva enorme, pantalla siempre encendida (Wake Lock / `keepScreenOn`); "Ya subí" (o solo al llegar el tren y alejarse el GPS) pasa a "a bordo": paradas hasta el destino con la próxima resaltada, posición por GPS sobre `recorridos.json` (`progresoViaje`, `ABordo.kt`) o estimada con horas de la API, aviso al llegar.

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
  subir `recorridos.json?v=N` en `js/mapa-tren.js`.
- Programados (Urquiza, Belgrano Norte): estaciones en orden con coords de nodos OSM dentro
  de `js/programados-data.js` y `datos/Programados.kt` (`estaciones`, `recorrido`); no
  tienen id SOFSE ni van en `estaciones-coords.json`. Polilíneas `Urquiza|||Urquiza` y
  `Belgrano Norte|||Belgrano Norte` hechas con `recorrido.py` pasando un coords.json temporal
  (ids 1..N) como 6.º argumento. Mapa: `crearMapaLinea` (web) / `MapaLinea` (app).
- Mapa base: Argenmap (IGN, TMS) con respaldo CARTO.

## Tests y CI
- Tests: `npm test` (Node, `tests/`, lógica pura en `js/logica.js`) y
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
Ninguna por ahora (8–15 hechas en oct 2026).
