<div align="center">

<img src="img/trenar-512.png" alt="TrenAR" width="128" height="128">

# TrenAR

### Tu tren, en vivo.

**¿Cuándo llega el tren?** Arribos en tiempo real, demoras, andén y avisos del servicio
para los trenes del AMBA. En la web, en el celular y como app Android.

[![Web](https://img.shields.io/badge/Abrir_la_web-tapia.chiqui.ar-1e88e5?style=for-the-badge&logo=googlechrome&logoColor=white)](https://tapia.chiqui.ar)
[![Descargar APK](https://img.shields.io/badge/Descargar-APK_Android-3ddc84?style=for-the-badge&logo=android&logoColor=white)](https://tapia.chiqui.ar/descargas/TrenAR.apk)

![Android 6+](https://img.shields.io/badge/Android-6.0%2B-3ddc84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iPhone-app_web-000000?logo=apple&logoColor=white)
![PWA](https://img.shields.io/badge/PWA-instalable-5a0fc8?logo=pwa&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7f52ff?logo=kotlin&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-sin_frameworks-f7df1e?logo=javascript&logoColor=black)
![Sin anuncios](https://img.shields.io/badge/anuncios-cero-brightgreen)

</div>

---

## Qué hace

| | |
|---|---|
| **Arribos en vivo** | Próximos trenes entre dos estaciones, con la cuenta regresiva al segundo, demora y andén. |
| **Duración del viaje** | Cuánto tarda cada tren de origen a destino y a qué hora llegás. |
| **Avisos del servicio** | Estado de cada línea (obras, cancelaciones, demoras) con la campana y un cartel al elegir estación. |
| **Avisame** | Seguí un tren y te avisa si se demora 5 min o más, o si lo cancelan. |
| **Mapa por tren** | El recorrido real sobre las vías (OpenStreetMap + IGN) con la posición estimada del tren. |
| **Modo andén** | Cuenta regresiva gigante con la pantalla siempre prendida mientras esperás. |
| **Modo a bordo** | Las paradas que faltan hasta tu destino, la próxima resaltada, y aviso al llegar. |
| **Estaciones cercanas** | Usa tu ubicación para elegir la estación más cerca. |
| **Favoritos** | Guardá tus viajes y abrilos de un toque (también como atajos del ícono). |
| **Líneas programadas** | Urquiza y Belgrano Norte con sus horarios oficiales. |
| **Widget** *(Android)* | Próximo y siguiente tren de tu viaje favorito en la pantalla de inicio. |
| **Tema claro y oscuro** | Y funciona sin conexión con el último resultado guardado. |

**Líneas:** Mitre · Sarmiento · Roca · San Martín · Belgrano Sur · Tren de la Costa
· Urquiza y Belgrano Norte (horarios programados).

---

## Instalar

### Android: app nativa

1. Descargá **[TrenAR.apk](https://tapia.chiqui.ar/descargas/TrenAR.apk)**
   (también está el botón **Descargar app Android** al pie de la web).
2. Abrí el archivo. Si Android lo pide, permití **Instalar apps desconocidas** para tu navegador.
3. Listo. Para actualizar, descargá el APK nuevo e instalalo encima: tus favoritos quedan.

> Funciona desde Android 6. El APK está firmado siempre con la misma clave, así que las
> actualizaciones se instalan sobre la versión anterior.

### iPhone y iPad: app web

1. Abrí **[tapia.chiqui.ar](https://tapia.chiqui.ar)** en **Safari**
   (si lo abriste desde Instagram o WhatsApp, primero *Abrir en Safari*).
2. Tocá **Compartir** (el cuadrado con la flecha; en iOS 26 está dentro del botón **···**).
3. Elegí **Agregar a inicio** y tocá **Agregar**.

Queda con el ícono de TrenAR y abre a pantalla completa. Al pie de la web, el botón
**Agregar a la pantalla de inicio** muestra estos pasos.

### Computadora

Entrá a **[tapia.chiqui.ar](https://tapia.chiqui.ar)**. En Chrome o Edge podés tocar
**Instalar como app web** al pie de la página para tenerla en su propia ventana.

---

## Cómo funciona

```
 Navegador / App Android
        │
        ├── index.html + js/v4, js/v5, js/v6  ── web (GitHub Pages, sin build)
        ├── android/  (Kotlin + Jetpack Compose) ── app nativa, misma lógica
        │
        └──▶ API de Trenes Argentinos (SOFSE)
               /arribos/estacion/{id}        próximos trenes
               /infraestructura/gerencias    avisos por línea
```

- **Web estática**: HTML, CSS y JavaScript con módulos ES, sin frameworks ni paso de build.
  Para navegadores viejos (Safari 11, Chrome 55) se sirve la misma app compilada en `js/compat/`.
- **PWA**: `manifest.json` + `sw.js` (red primero, caché como respaldo sin conexión).
- **App Android**: Kotlin + Compose, mapas con osmdroid, widget, notificaciones y alarmas
  que llegan con la app cerrada. Misma lógica que la web (paridad web == app).
- **Mapas**: recorridos reales calculados sobre las vías de OpenStreetMap
  (`recorridos.json`, `herramientas/recorrido.py`), mapa base Argenmap (IGN).

### Estructura

```
index.html            la web (todo vive acá)
js/v4/                app, buscador, avisos, mapa, modo andén, lógica
js/v5/                líneas programadas (Urquiza, Belgrano Norte)
js/v6/                marca, panel "Acerca de"
js/compat/            build para navegadores viejos (lo genera CI)
css/                  estilos
android/              app nativa Kotlin + Compose
descargas/TrenAR.apk  APK publicado (lo sirve GitHub Pages)
herramientas/         compatible.sh, publicar-apk.sh, recorrido.py
tests/                tests de la lógica (node --test)
estaciones*.json      estaciones y coordenadas
recorridos.json       polilíneas de cada ramal
```

---

## Desarrollo

```bash
# Web: cualquier servidor estático
python3 -m http.server 8000        # http://localhost:8000

# Tests de la lógica
npm test

# Después de tocar js/: regenerar el build para navegadores viejos
bash herramientas/compatible.sh

# App Android (JDK 17 + Android SDK)
cd android && ./gradlew assembleDebug
cd android && ./gradlew :app:testDebugUnitTest

# Publicar un APK nuevo en la web (requiere la clave de firma)
bash herramientas/publicar-apk.sh && git add descargas && git commit -m "APK nuevo"
```

Cada push a `main` despliega la web en GitHub Pages y recompila `js/compat/` si hace falta.

---

<div align="center">

Datos de [Trenes Argentinos](https://www.argentina.gob.ar/transporte/trenes-argentinos).
**TrenAR es una app no oficial** y no está asociada a ninguna empresa ferroviaria.

</div>
