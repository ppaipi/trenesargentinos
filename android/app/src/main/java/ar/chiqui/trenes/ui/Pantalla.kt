package ar.chiqui.trenes.ui

import android.Manifest
import ar.chiqui.trenes.datos.Actualizacion
import ar.chiqui.trenes.datos.Instalador
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import ar.chiqui.trenes.AppVM
import ar.chiqui.trenes.Conexion
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.LatLon

private enum class Campo { ORIGEN, DESTINO }

@Composable
fun App(vm: AppVM) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    // Íconos de la barra de estado claros en modo oscuro (si no, no se ven).
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (ctx as? android.app.Activity)?.window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, vista).isAppearanceLightStatusBars = !vm.oscuro
        }
    }
    TemaTrenes(vm.oscuro) {
        var festejo by remember { mutableStateOf<String?>(null) }
        val festejados = remember { mutableSetOf<String>() }
        // El Chiqui festeja cada tren que llega al andén (modo meme).
        LaunchedEffect(vm.ahora / 1000, vm.meme) {
            if (!vm.meme || vm.programado) return@LaunchedEffect
            val llego = vm.trenes.firstOrNull { it.referencia <= vm.ahora && it.referencia > vm.ahora - 120000 && it.clave !in festejados }
            if (llego != null) {
                festejados += llego.clave
                festejo = FESTEJOS.random()
            }
        }
        Box(Modifier.fillMaxSize().background(fondo(vm.oscuro))) {
            // La lista arranca debajo de la barra de estado y la tira de colores
            // (como la tira fija de la web); así nada se mete abajo al scrollear.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.statusBarsPadding()) { TiraLineas() }
                Box(Modifier.weight(1f)) { Pantalla(vm) }
            }
            if (vm.meme) CapasMeme(festejo) { festejo = null }
            if (vm.meme) vm.postergado?.let { SelloPostergado(it) { vm.postergado = null } }
            CartelAvisoEstacion(vm.avisoEstacion, onAbrir = { vm.avisoEstacion = null; vm.abrirAvisos(it) }) { vm.avisoEstacion = null }
            vm.anden?.let { c -> PantallaAnden(vm, c, colorLinea(vm.linea()) ?: Color(0xFF74ACDF)) { vm.anden = null } }
        }
        if (vm.panelAvisos) PanelAvisos(vm.grupos, vm.lineaPrimero) { vm.panelAvisos = false }
        if (vm.panelAcerca) PanelAcerca { vm.panelAcerca = false }
        vm.intersticial?.let { s -> Intersticial(s) { vm.intersticial = null } }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun Pantalla(vm: AppVM) {
    val ctx = LocalContext.current
    val tinta = sobreFondo(vm.oscuro)
    val lista = rememberLazyListState()
    var campo by remember { mutableStateOf<Campo?>(null) }
    var vistaInicial by remember { mutableStateOf<Vista?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }
    // Textos de los anuncios por posición: no cambian en cada sondeo.
    val anuncios = remember(vm.trayecto) { mutableStateMapOf<Int, String>() }

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
    val superficie = MaterialTheme.colorScheme.surface
    val bordes = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = lista,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 0.dp,
            bottom = bordes.calculateBottomPadding() + if (vm.meme) 90.dp else 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Cabecera(vm) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.meme) BannerSancord()
                vm.actualizacion?.let { tag -> AvisoActualizacion(tag, onCerrar = { vm.actualizacion = null }) }
                if (!vm.online) {
                    Text(
                        "Sin conexión. Los horarios se actualizan cuando vuelva internet.",
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ColorDemora).padding(12.dp),
                        color = Color.White,
                    )
                }
                when (vm.conexion) {
                    Conexion.CONECTANDO -> Tarjetita {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Conectando con Trenes Argentinos...", fontSize = 16.sp)
                    }
                    Conexion.ERROR -> Tarjetita {
                        Text("No se pudo conectar con Trenes Argentinos.", Modifier.weight(1f))
                        BotonSecundario("Reintentar", onClick = vm::reintentarConexion)
                    }
                    Conexion.OK -> {}
                }
                if (vm.listo) {
                    Guardados(vm)
                    Formulario(
                        vm,
                        buscandoUbicacion = buscandoUbicacion,
                        onOrigen = { vistaInicial = null; campo = Campo.ORIGEN },
                        onDestino = { vistaInicial = null; campo = Campo.DESTINO },
                        onCercana = {
                            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (ok) ubicar() else pedirUbicacion.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                    )
                }
            }
        }

        if (vm.resultadoVisible) {
            item { CabeceraResultado(vm) }
            val o = vm.origen
            val d = vm.destino
            val m = vm.mensaje
            when {
                vm.cargando && vm.trenes.isEmpty() -> if (vm.meme) item { PantallaVar() } else items(3) { Esqueleto() }
                m != null -> {
                    item {
                        Column(
                            Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(superficie).padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(m.texto, textAlign = TextAlign.Center)
                            m.accion?.let { a -> Spacer(Modifier.height(10.dp)); BotonSecundario(a.texto, onClick = a.hacer) }
                        }
                    }
                    if (vm.meme) item { AnuncioSancord("¿No hay trenes? Con Sancord Seguros tampoco, pero te sentís acompañado.") }
                }
                o != null && d != null -> {
                    val linea = vm.linea()
                    val color = colorLinea(linea) ?: primario
                    // En vivo, el que pasó hace más de 1 min se va sin esperar la próxima consulta.
                    val trenes = if (vm.programado) vm.trenes else vm.trenes.filter { it.referencia > vm.ahora - 60000 }
                    val demorado = trenes.firstOrNull { (it.demoraMin ?: 0) >= 5 }
                    trenes.forEachIndexed { i, t ->
                        item(key = t.clave) {
                            Tarjeta(
                                t, linea, color,
                                proximo = i == 0 && !vm.programado,
                                ahora = vm.ahora,
                                avisoActivo = t.clave in vm.avisosTren,
                                onAnden = if (vm.programado) null else ({ vm.anden = t.clave }),
                                origen = o, destino = d, oscuro = vm.oscuro,
                                meme = vm.meme,
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
                        // Modo meme: el Chiqui después del primero y un anuncio cada 2 trenes.
                        if (vm.meme && i == 0) item(key = "tapia") { TarjetaTapia() }
                        if (vm.meme && i % 2 == 1) {
                            item(key = "anuncio-$i") {
                                val texto = anuncios.getOrPut(i) {
                                    if (i == 1 && demorado != null) mensajeDemora(demorado.demoraMin ?: 5) else SLOGANS.random()
                                }
                                AnuncioSancord(texto)
                            }
                        }
                    }
                }
            }
        }

        item { SeccionProgramados(tinta) }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.meme) {
                    AvisoLegalMeme(sobreFondoSuave(vm.oscuro))
                    Spacer(Modifier.height(8.dp))
                }
                Text("Datos de Trenes Argentinos. App no oficial.", fontSize = 13.sp, color = sobreFondoSuave(vm.oscuro))
                val version = remember { versionApp(ctx) }
                if (version.isNotEmpty()) {
                    Text("TrenAR $version", fontSize = 11.sp, color = sobreFondoSuave(vm.oscuro).copy(alpha = 0.7f))
                }
            }
        }
    }

    campo?.let { cual ->
        Buscador(
            titulo = if (cual == Campo.ORIGEN) "Estación de origen" else "Estación de destino",
            // El origen se elige libre; solo si quedó vacío se guía por el destino.
            otra = if (cual == Campo.ORIGEN) vm.destino.takeIf { vm.origen == null } else vm.origen,
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
                    if (vm.origen == null) {
                        vistaInicial = null
                        campo = Campo.ORIGEN
                    }
                }
            },
            onCerrar = { campo = null },
            meme = vm.meme,
        )
    }
}

@Composable
private fun Tarjetita(contenido: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = contenido,
    )
}

/** Hay un APK más nuevo en Releases: la app lo baja y abre el instalador de Android. */
@Composable
private fun AvisoActualizacion(tag: String, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var descargando by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Hay una versión nueva", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                if (descargando) "Bajando TrenAR $tag… mirá el avance en las notificaciones" else "TrenAR $tag · tus favoritos quedan",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BotonSecundario(if (descargando) "Bajando…" else "Actualizar", activo = !descargando) {
            if (!descargando) {
                descargando = true
                Instalador.actualizar(ctx, tag) { descargando = false }
            }
        }
        TextButton(onCerrar) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
