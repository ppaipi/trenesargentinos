package ar.chiqui.trenes.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ar.chiqui.trenes.AppVM
import ar.chiqui.trenes.CLAVE_LLEGADA
import ar.chiqui.trenes.Notificaciones
import ar.chiqui.trenes.datos.Api
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.Fuente
import ar.chiqui.trenes.datos.LatLon
import ar.chiqui.trenes.datos.ParadaViaje
import ar.chiqui.trenes.datos.Tren
import ar.chiqui.trenes.datos.distanciaKm
import ar.chiqui.trenes.datos.progresoViaje
import kotlinx.coroutines.delay

// Modo "estoy en el andén" y "a bordo", como js/v4/anden.js: pantalla completa
// legible de lejos que no se apaga (keepScreenOn solo mientras está abierta).

private val CELESTE = Color(0xFF74ACDF)
private val FONDO = Color(0xFF05111F)
private val SUAVE = Color(0xFFC9D8EA)
private val AMARILLO = Color(0xFFFFD23F)
private val PASADA = Color(0xFF6F8197)
private const val SUBIDA_KM = 0.3 // con el tren ya en el andén, alejarse esto del origen = subió
private const val REFRESCO_BORDO_MS = 30_000L
private val ID_LLEGADA = CLAVE_LLEGADA.hashCode() // mismo id que la alarma: no se duplica
private const val TOLERANCIA_MS = 120_000L

// Respuesta nueva del tren abordado: por equipo si lo trae (la clave es estable);
// si no, la clave cambia por estación y se empareja por ramal + hora en el origen (±2 min).
private fun esElMismo(abordo: Tren, r: Tren, origenId: Int, salida: Long?): Boolean {
    if (tieneEquipo(abordo)) return r.clave == abordo.clave
    if (r.ramal != abordo.ramal || salida == null) return false
    val h = r.horarios.firstOrNull { it.id == origenId }?.hora ?: return false
    return kotlin.math.abs(h - salida) <= TOLERANCIA_MS
}

private fun tieneEquipo(t: Tren) = t.clave != "${t.ramal}-${t.programada}"

private class Bordo(val tren: Tren, val paradas: List<ParadaViaje>, val linea: List<LatLon>?)

private fun tienePermisoUbicacion(ctx: Context) = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private fun tramo(t: Tren, o: Estacion, d: Estacion): List<ParadaViaje> {
    val h = t.horarios
    val i = h.indexOfFirst { it.id == o.id }
    val j = h.indexOfFirst { it.id == d.id }
    val base = if (i >= 0 && j > i) h.subList(i, j + 1).map { Triple(it.id, it.nombre, it.hora) }
    else listOf(Triple(o.id, o.nombre, t.referencia), Triple(d.id, d.nombre, t.llegadaDestino))
    return base.map { (id, nombre, hora) ->
        val e = Estaciones.porId(id)
        ParadaViaje(id, e?.nombre ?: nombre, e?.coords, hora)
    }
}

private fun vibrar(ctx: Context) {
    val patron = longArrayOf(0, 200, 100, 200)
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else ctx.getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(patron, -1))
        else @Suppress("DEPRECATION") vib.vibrate(patron, -1)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PantallaAnden(vm: AppVM, clave: String, color: Color, onCerrar: () -> Unit) {
    val o = vm.origen
    val d = vm.destino
    if (o == null || d == null) {
        LaunchedEffect(Unit) { onCerrar() }
        return
    }
    val ctx = LocalContext.current
    val vista = LocalView.current
    BackHandler(onBack = onCerrar)
    // La pantalla no se apaga mientras esto está abierto; íconos claros arriba.
    DisposableEffect(Unit) {
        vista.keepScreenOn = true
        val ventana = (ctx as? Activity)?.window
        val control = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        val antes = control?.isAppearanceLightStatusBars
        control?.isAppearanceLightStatusBars = false
        onDispose {
            vista.keepScreenOn = false
            if (antes != null) control.isAppearanceLightStatusBars = antes
        }
    }

    var permiso by remember { mutableStateOf(tienePermisoUbicacion(ctx)) }
    var pos by remember { mutableStateOf<LatLon?>(null) }
    // Estado a bordo en el VM: sobrevive a la rotación (la pantalla se recrea).
    val estado = vm.bordo
    val bordo = remember(estado?.tren) {
        estado?.tren?.let { t -> Bordo(t, tramo(t, o, d), elegirRuta(o, d, t.ramal)?.let { Estaciones.recorrido(it) }?.takeIf { it.size > 1 }) }
    }
    val horas = estado?.horas.orEmpty() // refrescos de la API a bordo
    val avisado = estado?.avisado == true
    // GPS y refresco solo con la app visible (no en segundo plano).
    val visible = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)

    // GPS solo con permiso ya dado (sirve también para detectar que subió).
    DisposableEffect(permiso, visible) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val oyente = LocationListenerCompat { pos = LatLon(it.latitude, it.longitude) }
        val activo = permiso && visible
        if (activo) {
            val pedido = LocationRequestCompat.Builder(3000L).setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }.forEach {
                runCatching { LocationManagerCompat.requestLocationUpdates(lm, it, pedido, oyente, Looper.getMainLooper()) }
            }
        }
        onDispose { if (activo) LocationManagerCompat.removeUpdates(lm, oyente) }
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permiso = r.values.any { it }
    }

    fun subir(t: Tren, pedirGps: Boolean) {
        vm.bordo = AppVM.EstadoBordo(t)
        if (pedirGps && !permiso) {
            pedirPermiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Sin GPS: horas nuevas de la API para este tren, consultando la próxima parada.
    LaunchedEffect(bordo, visible) {
        val b = bordo ?: return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        while (true) {
            delay(REFRESCO_BORDO_MS)
            val e = vm.bordo ?: break
            if (e.avisado) break
            val paradas = b.paradas.map { p -> e.horas[p.id]?.let { p.copy(hora = it) } ?: p }
            val p = progresoViaje(paradas, pos, b.linea, System.currentTimeMillis())
            val sig = maxOf(1, p.ultima + 1)
            if (sig >= paradas.size - 1) continue
            val salida = paradas.first().hora
            runCatching {
                Api.arribos(paradas[sig].id, d.id).firstNotNullOfOrNull { r ->
                    Api.normalizar(r, d.id)?.takeIf { esElMismo(b.tren, it, o.id, salida) }
                }
            }.getOrNull()?.let { t ->
                vm.bordo?.let { act -> vm.bordo = act.copy(horas = act.horas + t.horarios.mapNotNull { h -> h.hora?.let { h.id to it } }) }
            }
        }
    }

    val ahora = vm.ahora
    Box(
        Modifier
            .fillMaxSize()
            .background(FONDO)
            // Tapa la pantalla de abajo: nada pasa a la lista.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(color))
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (bordo != null) "A BORDO" else "EN EL ANDÉN", color = CELESTE, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                OutlinedButton(onClick = onCerrar, border = androidx.compose.foundation.BorderStroke(2.dp, CELESTE)) {
                    Text("Salir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            val b = bordo
            if (b == null) {
                ContenidoAnden(vm.trenes, clave, o, ahora, onSubir = { subir(it, true) })
                // Subió solo: el tren ya está (YA) y el GPS se alejó del origen.
                LaunchedEffect(ahora / 1000) {
                    val t = principal(vm.trenes, clave, ahora) ?: return@LaunchedEffect
                    val p = pos
                    if (t.referencia <= ahora && p != null && o.coords != null && distanciaKm(p, o.coords) > SUBIDA_KM) subir(t, false)
                }
            } else {
                val paradas = b.paradas.map { p -> horas[p.id]?.let { p.copy(hora = it) } ?: p }
                val p = progresoViaje(paradas, pos, b.linea, ahora)
                val ultima = maxOf(0, p.ultima) // ya subió: el origen cuenta como pasado
                val siguiente = if (p.llego || ultima + 1 >= paradas.size) null else ultima + 1
                val destino = paradas.last()
                // Alarma para la hora de llegada: avisa aunque la pantalla esté bloqueada.
                LaunchedEffect(destino.hora, avisado) {
                    vm.programarLlegada(destino.hora, "Llegaste a ${destino.nombre}", "${b.tren.ramal} · bajate acá")
                }
                LaunchedEffect(siguiente == null) {
                    if (siguiente == null && !avisado) {
                        vm.bordo = vm.bordo?.copy(avisado = true)
                        vibrar(ctx)
                        Notificaciones.mostrar(ctx, "Llegaste a ${destino.nombre}", "${b.tren.ramal} · bajate acá", ID_LLEGADA)
                    }
                }
                ContenidoBordo(paradas, ultima, siguiente, p.enEstacion, p.fuente, ahora)
            }
        }
    }
}

private fun lista(trenes: List<Tren>, ahora: Long) = trenes.filter { it.referencia > ahora - 120_000 }

// El tren elegido mientras siga en la lista; si se fue, el próximo.
private fun principal(trenes: List<Tren>, clave: String, ahora: Long): Tren? {
    val l = lista(trenes, ahora)
    return l.firstOrNull { it.clave == clave } ?: l.firstOrNull()
}

@Composable
private fun NumeroGrande(c: Cuenta) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val factor = if (c.num.length > 5) 0.17f else 0.24f
        val tam = minOf(maxWidth.value * factor, maxHeight.value.takeIf { it.isFinite() } ?: Float.MAX_VALUE, 220f)
        Text(
            c.num, color = if (c.ya) AMARILLO else CELESTE, fontSize = tam.sp, lineHeight = tam.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
    }
    Text(c.uni, color = SUAVE, fontSize = 17.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
}

@Composable
private fun ContenidoAnden(trenes: List<Tren>, clave: String, o: Estacion, ahora: Long, onSubir: (Tren) -> Unit) {
    val t = principal(trenes, clave, ahora)
    Column(
        Modifier.fillMaxSize().padding(16.dp, 8.dp, 16.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (t == null) {
            NumeroGrande(Cuenta("--:--", "sin trenes por ahora"))
            return@Column
        }
        val hacia = t.hacia?.takeIf { it != t.estacionDestino }?.let { " · hacia $it" } ?: ""
        Text("${t.ramal}$hacia", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${o.nombre} → ${t.estacionDestino}", color = SUAVE, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        NumeroGrande(cuentaRegresiva(t.referencia, ahora))
        Text(
            "${if (t.esCabecera) "Sale" else "Llega"} ${hora(t.referencia)}${t.anden?.let { " · Andén $it" } ?: ""}",
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp),
        )
        val l = lista(trenes, ahora)
        l.getOrNull(l.indexOf(t) + 1)?.let { s ->
            Text("Siguiente: ${hora(s.referencia)} · en ${cuentaRegresiva(s.referencia, ahora).num}", color = SUAVE, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Button(
            onClick = { onSubir(t) },
            colors = ButtonDefaults.buttonColors(containerColor = CELESTE, contentColor = FONDO),
            modifier = Modifier.padding(top = 28.dp),
        ) { Text("Ya subí", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
    }
}

@Composable
private fun ContenidoBordo(paradas: List<ParadaViaje>, ultima: Int, siguiente: Int?, enEstacion: Boolean, fuente: Fuente, ahora: Long) {
    val destino = paradas.last()
    val estadoLista = rememberLazyListState()
    LaunchedEffect(siguiente) { siguiente?.let { estadoLista.animateScrollToItem(maxOf(0, it - 1)) } }
    Column(Modifier.fillMaxSize().padding(16.dp, 8.dp, 16.dp, 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val gps = fuente == Fuente.GPS
        Text(
            if (gps) "Posición por GPS" else "Posición estimada",
            color = if (gps) CELESTE else SUAVE, fontSize = 13.sp,
            modifier = Modifier.border(1.dp, if (gps) CELESTE else SUAVE, RoundedCornerShape(50)).padding(12.dp, 4.dp),
        )
        if (siguiente == null) {
            Text("Llegaste a ${destino.nombre}", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
            NumeroGrande(Cuenta("YA", "bajate acá", ya = true))
        } else {
            val sig = paradas[siguiente]
            val ult = paradas[ultima].nombre
            Text(if (enEstacion) "En $ult" else "Última: $ult", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Text("Próxima: ${sig.nombre}", color = SUAVE, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            val c = sig.hora?.let { cuentaRegresiva(it, ahora) } ?: Cuenta("--:--", "sin hora")
            NumeroGrande(if (c.ya) Cuenta(c.num, "llegando", ya = true) else c)
            Text("${destino.nombre} ${hora(destino.hora)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(state = estadoLista, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(max = 320.dp)) {
            itemsIndexed(paradas) { i, e ->
                val pasada = i <= ultima
                val proxima = i == siguiente
                val col = if (pasada) PASADA else if (proxima) CELESTE else Color.White
                val fuerte = proxima || i == paradas.lastIndex
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(if (proxima) 14.dp else 10.dp)
                            .background(if (pasada) Color(0xFF3A4A5E) else if (proxima) CELESTE else FONDO, CircleShape)
                            .border(2.dp, if (pasada) Color(0xFF3A4A5E) else CELESTE, CircleShape),
                    )
                    Text(
                        e.nombre, color = col, fontSize = if (proxima) 21.sp else 18.sp,
                        fontWeight = if (fuerte) FontWeight.ExtraBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 14.dp).weight(1f),
                    )
                    Text(hora(e.hora), color = col, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
