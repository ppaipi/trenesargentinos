package ar.chiqui.trenes.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.LatLon
import ar.chiqui.trenes.datos.Ruta
import ar.chiqui.trenes.datos.Tren
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Mapa individual de un tren: recorrido real del ramal (recorridos.json),
// estaciones, tramo origen→destino resaltado y el tren siguiéndose en vivo.
// Base: Argenmap (IGN), que publica TMS (la y va invertida).

private class Argenmap(private val capa: String) : OnlineTileSourceBase(
    "Argenmap-$capa", 0, 18, 256, ".png",
    arrayOf("https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/"),
    "Argenmap · IGN + OSM",
) {
    override fun getTileURLString(indice: Long): String {
        val z = MapTileIndex.getZoom(indice)
        val x = MapTileIndex.getX(indice)
        val y = (1 shl z) - 1 - MapTileIndex.getY(indice)
        return "${baseUrl}$capa@EPSG%3A3857@png/$z/$x/$y.png"
    }
}

private val LatLon.geo get() = GeoPoint(lat, lon)

private fun d2(a: LatLon, b: LatLon) = (a.lat - b.lat).let { it * it } + ((a.lon - b.lon) * 0.82).let { it * it }

private fun masCercano(linea: List<LatLon>, p: LatLon) = linea.indices.minBy { d2(linea[it], p) }

// Entre los ramales que comparten origen y destino, el que nombra el
// servicio ("Constitución-Ezeiza" → ramal Ezeiza).
internal fun elegirRuta(o: Estacion, d: Estacion, servicio: String): Ruta? {
    val comunes = o.rutas.filter { it in d.rutas }
    return comunes.firstOrNull { servicio.contains(it.ramal, ignoreCase = true) } ?: comunes.firstOrNull()
}

private class EstadoMapa {
    var siguiendo = true // el mapa acompaña al tren hasta que el usuario lo mueve
    var tren: Marker? = null
    var yo: MyLocationNewOverlay? = null
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapaTren(tren: Tren, origen: Estacion, destino: Estacion, color: Color, oscuro: Boolean, ahora: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    val estado = remember { EstadoMapa() }
    val c = color.toArgb()
    val mapa = remember {
        MapView(ctx).apply {
            setTileSource(Argenmap(if (oscuro) "argenmap_oscuro" else "capabaseargenmap"))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(13.0)
            controller.setCenter((tren.ubicacion ?: origen.coords)?.geo ?: GeoPoint(-34.6, -58.4))
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    estado.siguiendo = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
            dibujarRecorrido(this, ctx, origen, destino, tren.ramal, c, oscuro)
        }
    }
    DisposableEffect(Unit) {
        mapa.onResume()
        onDispose {
            estado.yo?.disableMyLocation()
            mapa.onPause()
            mapa.onDetach()
        }
    }

    // Tren: se mueve con cada consulta; la cuenta regresiva se repinta cada segundo.
    LaunchedEffect(tren.ubicacion) {
        val u = tren.ubicacion ?: return@LaunchedEffect
        val m = estado.tren ?: Marker(mapa).also {
            it.setAnchor(Marker.ANCHOR_CENTER, 0.36f)
            it.setInfoWindow(null)
            mapa.overlays.add(it)
            estado.tren = it
        }
        m.position = u.geo
        if (estado.siguiendo) mapa.controller.animateTo(u.geo)
        mapa.invalidate()
    }
    LaunchedEffect(ahora / 1000) {
        estado.tren?.let {
            it.icon = iconoTren(ctx, c, cuentaRegresiva(tren.referencia, ahora).num)
            mapa.invalidate()
        }
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) mostrarYo(mapa, ctx, estado, tren)
    }

    Box(modifier) {
        AndroidView({ mapa }, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            SmallFloatingActionButton(onClick = {
                estado.siguiendo = true
                tren.ubicacion?.let { mapa.controller.animateTo(it.geo) }
            }, containerColor = MaterialTheme.colorScheme.surface) { Text("Tren", style = MaterialTheme.typography.labelSmall) }
            SmallFloatingActionButton(onClick = {
                val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (ok) mostrarYo(mapa, ctx, estado, tren)
                else pedirPermiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, containerColor = MaterialTheme.colorScheme.surface) { Text("Yo", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// Punto azul del usuario; al llegar la ubicación encuadra al usuario y al tren.
private fun mostrarYo(mapa: MapView, ctx: Context, estado: EstadoMapa, tren: Tren) {
    estado.siguiendo = false
    val yo = estado.yo ?: MyLocationNewOverlay(GpsMyLocationProvider(ctx), mapa).also {
        mapa.overlays.add(it)
        estado.yo = it
    }
    yo.enableMyLocation()
    yo.runOnFirstFix {
        val p = yo.myLocation ?: return@runOnFirstFix
        mapa.post {
            val puntos = listOfNotNull(p, tren.ubicacion?.geo)
            if (puntos.size == 1) mapa.controller.animateTo(p)
            else mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(puntos).increaseByScale(1.6f), true)
        }
    }
}

private fun dibujarRecorrido(mapa: MapView, ctx: Context, o: Estacion, d: Estacion, servicio: String, c: Int, oscuro: Boolean) {
    val dp = ctx.resources.displayMetrics.density
    val ruta = elegirRuta(o, d, servicio)
    val estaciones = ruta?.let { Estaciones.deRuta(it).filter { e -> e.coords != null } } ?: emptyList()
    val linea = ruta?.let { Estaciones.recorrido(it) }?.takeIf { it.size > 1 }
        ?: estaciones.mapNotNull { it.coords } // respaldo: une estaciones

    fun trazo(puntos: List<LatLon>, ancho: Float, alfa: Int) = Polyline(mapa).apply {
        setPoints(puntos.map { it.geo })
        outlinePaint.color = (c and 0x00FFFFFF) or (alfa shl 24)
        outlinePaint.strokeWidth = ancho * dp
        outlinePaint.strokeCap = Paint.Cap.ROUND
        setOnClickListener { _, _, _ -> false }
        mapa.overlays.add(this)
    }

    if (linea.size > 1) {
        trazo(linea, 4f, 0x4D)
        val oc = o.coords
        val dc = d.coords
        if (oc != null && dc != null) {
            val i = masCercano(linea, oc)
            val j = masCercano(linea, dc)
            val tramo = linea.subList(minOf(i, j), maxOf(i, j) + 1)
            if (tramo.size > 1) trazo(tramo, 6f, 0xF2)
        }
    }

    val relleno = if (oscuro) 0xFF1A1E25.toInt() else 0xFFFFFFFF.toInt()
    estaciones.forEach { e ->
        val extremo = e.id == o.id || e.id == d.id
        Marker(mapa).apply {
            position = e.coords!!.geo
            title = e.nombre
            icon = if (extremo) etiquetaEstacion(ctx, c, relleno, e.nombre, oscuro) else circulo(ctx, c, relleno)
            if (extremo) setAnchor(9 * dp / icon.intrinsicWidth, 0.5f) else setAnchor(0.5f, 0.5f)
            mapa.overlays.add(this)
        }
    }
}

private fun circulo(ctx: Context, c: Int, relleno: Int): BitmapDrawable {
    val dp = ctx.resources.displayMetrics.density
    val lado = (11 * dp).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = relleno
    cv.drawCircle(lado / 2f, lado / 2f, 3.5f * dp, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 2 * dp
    p.color = c
    cv.drawCircle(lado / 2f, lado / 2f, 3.5f * dp, p)
    return BitmapDrawable(ctx.resources, bmp)
}

// Origen y destino: círculo grande con el nombre siempre visible.
private fun etiquetaEstacion(ctx: Context, c: Int, relleno: Int, nombre: String, oscuro: Boolean): BitmapDrawable {
    val dp = ctx.resources.displayMetrics.density
    val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * dp
        typeface = Typeface.DEFAULT_BOLD
        color = if (oscuro) 0xFFE8EBF1.toInt() else 0xFF2B2F38.toInt()
    }
    val anchoTexto = texto.measureText(nombre)
    val alto = (22 * dp).toInt()
    val ancho = (18 * dp + 6 * dp + anchoTexto + 8 * dp).toInt()
    val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = 9 * dp
    val cy = alto / 2f
    p.color = relleno
    cv.drawCircle(cx, cy, 6 * dp, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3 * dp
    p.color = c
    cv.drawCircle(cx, cy, 6 * dp, p)
    val caja = RectF(18 * dp, 2 * dp, ancho.toFloat(), alto - 2 * dp)
    p.style = Paint.Style.FILL
    p.color = (relleno and 0x00FFFFFF) or (0xE6 shl 24)
    cv.drawRoundRect(caja, 6 * dp, 6 * dp, p)
    cv.drawText(nombre, 18 * dp + 4 * dp, cy + 4.2f * dp, texto)
    return BitmapDrawable(ctx.resources, bmp)
}

// Tren visto de frente (mismo dibujo que la web) + cuenta regresiva abajo.
private fun iconoTren(ctx: Context, c: Int, cuenta: String): BitmapDrawable {
    val dp = ctx.resources.displayMetrics.density
    val ancho = (64 * dp).toInt()
    val alto = (62 * dp).toInt()
    val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = ancho / 2f
    val cy = 22 * dp
    p.color = 0xFFFFFFFF.toInt()
    cv.drawCircle(cx, cy, 20 * dp, p)
    p.color = c
    cv.drawCircle(cx, cy, 17.5f * dp, p)

    // Dibujo en una grilla de 24 unidades, centrada en el círculo.
    val u = 1.25f * dp
    val ox = cx - 12 * u
    val oy = cy - 12 * u
    fun r(x: Float, y: Float, w: Float, h: Float, rad: Float, color: Int) {
        p.color = color
        cv.drawRoundRect(RectF(ox + x * u, oy + y * u, ox + (x + w) * u, oy + (y + h) * u), rad * u, rad * u, p)
    }
    r(5f, 2.5f, 14f, 15f, 3.5f, 0xFFFFFFFF.toInt())
    r(7.2f, 5f, 9.6f, 5.2f, 1.2f, c)
    p.color = c
    cv.drawCircle(ox + 8.8f * u, oy + 13.6f * u, 1.3f * u, p)
    cv.drawCircle(ox + 15.2f * u, oy + 13.6f * u, 1.3f * u, p)
    p.color = 0xFFFFFFFF.toInt()
    p.strokeWidth = 1.8f * u
    p.strokeCap = Paint.Cap.ROUND
    cv.drawLine(ox + 8 * u, oy + 18.5f * u, ox + 6 * u, oy + 21.5f * u, p)
    cv.drawLine(ox + 16 * u, oy + 18.5f * u, ox + 18 * u, oy + 21.5f * u, p)

    val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    val w = texto.measureText(cuenta) + 10 * dp
    p.color = c
    cv.drawRoundRect(RectF(cx - w / 2, 44 * dp, cx + w / 2, 60 * dp), 8 * dp, 8 * dp, p)
    cv.drawText(cuenta, cx, 56 * dp, texto)
    return BitmapDrawable(ctx.resources, bmp)
}
