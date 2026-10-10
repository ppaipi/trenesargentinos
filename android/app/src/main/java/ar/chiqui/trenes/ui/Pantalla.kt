package ar.chiqui.trenes.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import ar.chiqui.trenes.AppVM
import ar.chiqui.trenes.Conexion
import ar.chiqui.trenes.Modo
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.LatLon
import ar.chiqui.trenes.datos.Viaje
import java.time.format.DateTimeFormatter

private enum class Campo { ORIGEN, DESTINO }

@Composable
fun App(vm: AppVM) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    TemaTrenes(vm.oscuro) {
        Box(Modifier.fillMaxSize().background(fondo(vm.oscuro))) {
            Pantalla(vm)
        }
        if (vm.panelAvisos) PanelAvisos(vm.grupos, vm.lineaPrimero) { vm.panelAvisos = false }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pantalla(vm: AppVM) {
    val ctx = LocalContext.current
    val tinta = sobreFondo(vm.oscuro)
    val lista = rememberLazyListState()
    var campo by remember { mutableStateOf<Campo?>(null) }
    var vistaInicial by remember { mutableStateOf<Vista?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.irAResultado.collect { lista.animateScrollToItem(2) } }

    fun ubicar() {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val proveedor = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
            ?: return vm.toastPublico("Activá la ubicación del teléfono.")
        buscandoUbicacion = true
        val alUbicar = androidx.core.util.Consumer<android.location.Location?> { loc ->
            buscandoUbicacion = false
            val cercanas = loc?.let { Estaciones.cercanas(LatLon(it.latitude, it.longitude)) }
            when {
                loc == null -> vm.toastPublico("No pudimos obtener tu ubicación.")
                cercanas.isNullOrEmpty() -> vm.toastPublico("No encontramos estaciones cerca.")
                else -> {
                    vistaInicial = Vista.Cercanas(cercanas)
                    campo = Campo.ORIGEN
                }
            }
        }
        LocationManagerCompat.getCurrentLocation(lm, proveedor, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(ctx), alUbicar)
    }
    val pedirUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ubicar() else vm.toastPublico("Permiso de ubicación denegado.")
    }
    val pedirNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val primario = MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        state = lista,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trenes en vivo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tinta)
                    Text("¿Cuándo llega tu tren?", style = MaterialTheme.typography.bodyMedium, color = tinta.copy(alpha = 0.8f))
                }
                val total = vm.grupos.filter { lineaConocida(it.linea) }.sumOf { it.avisos.size }
                IconButton(onClick = { vm.abrirAvisos() }) {
                    BadgedBox(badge = {
                        if (total > 0) {
                            val grave = vm.grupos.any { g -> g.avisos.any { it.nivel == ar.chiqui.trenes.datos.Nivel.GRAVE } }
                            Badge(containerColor = if (grave) ColorGrave else ColorDemora) { Text(if (total > 9) "9+" else "$total") }
                        }
                    }) { Icon(Icons.Default.Notifications, "Avisos del servicio", tint = tinta) }
                }
                TextButton(onClick = vm::alternarTema) { Text(if (vm.oscuro) "Claro" else "Oscuro", color = tinta) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!vm.online) Aviso("Sin conexión: los datos pueden estar desactualizados.")
                when (vm.conexion) {
                    Conexion.CONECTANDO -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), color = tinta, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando con Trenes Argentinos…", color = tinta)
                    }
                    Conexion.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No se pudo conectar con Trenes Argentinos.", Modifier.weight(1f), color = tinta)
                        OutlinedButton(onClick = vm::reintentarConexion) { Text("Reintentar", color = tinta) }
                    }
                    Conexion.OK -> {}
                }
                if (vm.listo) Formulario(
                    vm,
                    onCampo = { vistaInicial = null; campo = it },
                    buscandoUbicacion = buscandoUbicacion,
                    onCercana = {
                        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (ok) ubicar() else pedirUbicacion.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                )
                Guardados(vm)
            }
        }

        if (vm.resultadoVisible) {
            item { CabeceraResultado(vm, tinta) }
            val o = vm.origen
            val d = vm.destino
            val m = vm.mensaje
            when {
                vm.cargando && vm.trenes.isEmpty() -> items(3) { Esqueleto() }
                m != null -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(m.texto)
                            m.accion?.let { a -> OutlinedButton(onClick = a.hacer, Modifier.padding(top = 10.dp)) { Text(a.texto) } }
                        }
                    }
                }
                o != null && d != null -> {
                    val linea = vm.linea()
                    val color = colorLinea(linea) ?: primario
                    items(vm.trenes, key = { it.clave }) { t ->
                        Tarjeta(
                            t, linea, color,
                            proximo = t == vm.trenes.first() && !vm.programado,
                            ahora = vm.ahora,
                            avisoActivo = t.clave in vm.avisosTren,
                            origen = o, destino = d, oscuro = vm.oscuro,
                            onAvisar = {
                                if (Build.VERSION.SDK_INT >= 33 && t.clave !in vm.avisosTren &&
                                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.alternarAvisoTren(t)
                                }
                            },
                        )
                    }
                }
            }
        }

        item { SeccionProgramados(tinta) }
        item {
            Text(
                "Datos de Trenes Argentinos. App no oficial.",
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = tinta.copy(alpha = 0.8f),
            )
        }
    }

    campo?.let { cual ->
        Buscador(
            titulo = if (cual == Campo.ORIGEN) "¿Desde dónde?" else "¿Hasta dónde?",
            otra = if (cual == Campo.ORIGEN) vm.destino else vm.origen,
            inicial = vistaInicial,
            onElegir = { e ->
                campo = null
                if (cual == Campo.ORIGEN) {
                    vm.elegirOrigen(e)
                    if (vm.destino == null) {
                        vistaInicial = null
                        campo = Campo.DESTINO
                    }
                } else {
                    vm.elegirDestino(e)
                }
            },
            onCerrar = { campo = null },
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ColorDemora).padding(10.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Formulario(vm: AppVM, onCampo: (Campo) -> Unit, buscandoUbicacion: Boolean, onCercana: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val chipColores = androidx.compose.material3.FilterChipDefaults.filterChipColors(
        selectedContainerColor = cs.primary,
        selectedLabelColor = cs.onPrimary,
    )
    Card(colors = CardDefaults.cardColors(containerColor = cs.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vm.modo == Modo.VIVO, { vm.cambiarModo(Modo.VIVO) }, { Text("En vivo") }, colors = chipColores)
                FilterChip(vm.modo == Modo.PROGRAMADA, { vm.cambiarModo(Modo.PROGRAMADA) }, { Text("Programado") }, colors = chipColores)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoEstacion("Origen", vm.origen?.nombre) { onCampo(Campo.ORIGEN) }
                    CampoEstacion("Destino", vm.destino?.nombre) { onCampo(Campo.DESTINO) }
                }
                TextButton(onClick = vm::intercambiar) { Text("⇅", style = MaterialTheme.typography.headlineSmall) }
            }
            TextButton(onClick = onCercana, enabled = !buscandoUbicacion) {
                Text(if (buscandoUbicacion) "Buscando tu ubicación…" else "Usar estación cercana")
            }
            if (vm.modo == Modo.PROGRAMADA) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(ctx, { _, a, m, d -> vm.fecha = java.time.LocalDate.of(a, m + 1, d) },
                            vm.fecha.year, vm.fecha.monthValue - 1, vm.fecha.dayOfMonth).show()
                    }) { Text(vm.fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m -> vm.horaProg = java.time.LocalTime.of(h, m) },
                            vm.horaProg.hour, vm.horaProg.minute, true).show()
                    }) { Text(vm.horaProg.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
            }
            Button(
                onClick = vm::buscar,
                enabled = vm.origen != null && vm.destino != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.modo == Modo.VIVO) "Buscar horarios" else "Ver horarios programados") }
        }
    }
}

@Composable
private fun CampoEstacion(etiqueta: String, valor: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Text(valor ?: "Elegí una estación", fontWeight = if (valor != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (valor != null) cs.onSurface else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Favoritos y últimos viajes como accesos rápidos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Guardados(vm: AppVM) {
    val esFav = { v: Viaje -> vm.favoritos.any { it.o == v.o && it.d == v.d } }
    val recs = vm.recientes.filterNot(esFav).take(3)
    if (vm.favoritos.isEmpty() && recs.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (vm.favoritos.map { it to true } + recs.map { it to false }).forEach { (v, fav) ->
            val o = Estaciones.porId(v.o) ?: return@forEach
            val d = Estaciones.porId(v.d) ?: return@forEach
            val color = colorLinea(Estaciones.rutaCompartida(o, d)?.linea) ?: cs.primary
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surface).clickable { vm.abrirGuardado(v) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(36.dp).background(color))
                Text(if (fav) "★" else "Últ.", Modifier.padding(start = 8.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${o.nombre} → ${d.nombre}", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
                if (fav) Text("✕", Modifier.clickable { vm.quitarFavorito(v) }.padding(end = 10.dp), color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CabeceraResultado(vm: AppVM, tinta: Color) {
    val ctx = LocalContext.current
    val fav = vm.origen?.let { o -> vm.destino?.let { d -> vm.favoritos.any { it.o == o.id && it.d == d.id } } } ?: false
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.trayecto, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tinta)
            TextButton(onClick = vm::alternarFavorito) { Text(if (fav) "★" else "☆", style = MaterialTheme.typography.titleLarge, color = tinta) }
            IconButton(onClick = { vm.intentCompartir()?.let(ctx::startActivity) }) { Icon(Icons.Default.Share, "Compartir", tint = tinta) }
        }
        Text(vm.estadoTexto, style = MaterialTheme.typography.bodySmall, color = tinta.copy(alpha = 0.85f))
    }
}
