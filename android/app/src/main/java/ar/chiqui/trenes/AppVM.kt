package ar.chiqui.trenes

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.chiqui.trenes.datos.Almacen
import ar.chiqui.trenes.datos.Api
import ar.chiqui.trenes.datos.Aviso
import ar.chiqui.trenes.datos.ErrorApi
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.GrupoAvisos
import ar.chiqui.trenes.datos.Tren
import ar.chiqui.trenes.datos.Viaje
import ar.chiqui.trenes.datos.ZONA
import ar.chiqui.trenes.ui.hora
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Modo { VIVO, PROGRAMADA }
enum class Conexion { CONECTANDO, OK, ERROR }

class Accion(val texto: String, val hacer: () -> Unit)
class Mensaje(val texto: String, val accion: Accion? = null)

private const val SONDEO_MS = 15000L // la API se consulta cada 15 s; la cuenta regresiva corre local cada 1 s
private const val AVISOS_MS = 5 * 60000L

/** Estado y lógica de la pantalla (equivale a js/v4/app.js + avisos.js). */
class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val almacen = Almacen(app)

    var listo by mutableStateOf(false); private set
    var conexion by mutableStateOf(Conexion.CONECTANDO); private set
    var online by mutableStateOf(true); private set
    var oscuro by mutableStateOf(almacen.temaOscuro); private set
    var meme by mutableStateOf(almacen.meme); private set
    var intersticial by mutableStateOf<Int?>(null) // segundos del anuncio a pantalla completa

    var origen by mutableStateOf<Estacion?>(null); private set
    var destino by mutableStateOf<Estacion?>(null); private set
    var modo by mutableStateOf(Modo.VIVO); private set
    var fecha by mutableStateOf(LocalDate.now(ZONA))
    var horaProg by mutableStateOf(LocalTime.now(ZONA).withSecond(0).withNano(0))

    var resultadoVisible by mutableStateOf(false); private set
    var trayecto by mutableStateOf(""); private set
    var trenes by mutableStateOf<List<Tren>>(emptyList()); private set
    var programado by mutableStateOf(false); private set
    var cargando by mutableStateOf(false); private set
    var mensaje by mutableStateOf<Mensaje?>(null); private set
    var estadoTexto by mutableStateOf(""); private set
    var ahora by mutableLongStateOf(System.currentTimeMillis()); private set

    var favoritos by mutableStateOf<List<Viaje>>(emptyList()); private set
    var recientes by mutableStateOf<List<Viaje>>(emptyList()); private set
    var esFavorito by mutableStateOf(false); private set
    var avisosTren by mutableStateOf<Set<String>>(emptySet()); private set

    var grupos by mutableStateOf<List<GrupoAvisos>>(emptyList()); private set
    var panelAvisos by mutableStateOf(false)
    var lineaPrimero by mutableStateOf<String?>(null); private set

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val irAResultado = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var activo = false // hay una búsqueda en vivo corriendo
    private var ultimaOk: Long? = null
    private var error: ErrorApi? = null
    private var guardado: Long? = null
    private var enPrimerPlano = false
    private var pendiente: Viaje? = null
    private var avisados = mutableSetOf<String>()
    private var sondeo: Job? = null
    private var reloj: Job? = null
    private var cicloAvisos: Job? = null

    private val red = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                online = true
                if (activo && enPrimerPlano) iniciarSondeo()
            }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch { online = false }
        }
    }

    init {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        online = cm.activeNetwork != null
        if (Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(red)
        else cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), red) // Android 6
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Estaciones.cargar(ctx) }
            listo = true
            refrescarViajes()
            if (conectar()) arrancar()
        }
    }

    override fun onCleared() {
        ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(red)
    }

    private fun toast(t: String) {
        toasts.tryEmit(t)
    }

    /* ---------- Ciclo de vida ---------- */

    fun alVolver() {
        enPrimerPlano = true
        Notificaciones.quitarProximo(ctx)
        reloj?.cancel()
        reloj = viewModelScope.launch {
            while (isActive) {
                ahora = System.currentTimeMillis()
                actualizarEstado()
                delay(1000 - ahora % 1000)
            }
        }
        if (activo) iniciarSondeo() // al volver: dato fresco inmediato
        if (listo && conexion == Conexion.OK) iniciarAvisos()
    }

    fun alSalir() {
        enPrimerPlano = false
        // Cuenta regresiva del próximo tren en una notificación fija (en la web:
        // el título de la pestaña).
        val o = origen
        val d = destino
        val t = trenes.firstOrNull { it.referencia > System.currentTimeMillis() }
        if (activo && !programado && o != null && d != null && t != null) {
            Notificaciones.mostrarProximo(
                ctx, "Próximo tren a ${d.nombre}",
                "${t.ramal} · desde ${o.nombre} · ${hora(t.referencia)}${t.anden?.let { " · Andén $it" } ?: ""}",
                t.referencia,
            )
        }
        reloj?.cancel()
        sondeo?.cancel()
        cicloAvisos?.cancel()
    }

    private suspend fun conectar(): Boolean {
        conexion = Conexion.CONECTANDO
        return try {
            Api.login()
            conexion = Conexion.OK
            if (enPrimerPlano) iniciarAvisos()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("Trenes", "Login falló", e)
            conexion = Conexion.ERROR
            false
        }
    }

    fun reintentarConexion() {
        viewModelScope.launch { if (conectar()) arrancar() }
    }

    /** Link compartido (?o=&d=): se abre apenas haya estaciones y conexión. */
    fun abrirViaje(o: Int, d: Int) {
        pendiente = Viaje(o, d)
        if (listo && conexion == Conexion.OK) arrancar()
    }

    // Prioridad: link abierto y si no, la búsqueda de los últimos 30 min.
    private fun arrancar() {
        val v = pendiente ?: almacen.recienteActivo() ?: return
        pendiente = null
        val o = Estaciones.porId(v.o)
        val d = Estaciones.porId(v.d)
        if (o != null && d != null && Estaciones.rutaCompartida(o, d) != null) {
            origen = o
            destino = d
            buscar(auto = true)
        }
    }

    /* ---------- Selección ---------- */

    /** Cartel flotante al elegir una estación: el aviso más grave de sus líneas. */
    data class AvisoEstacion(val linea: String, val aviso: Aviso, val total: Int)
    var avisoEstacion by mutableStateOf<AvisoEstacion?>(null)
    private var timerAviso: Job? = null

    // Avisos generales de las líneas de la estación o de esa estación; se va
    // solo a los 5 s (igual que la web).
    private fun mostrarAvisosDe(e: Estacion) {
        val lineas = e.rutas.map { it.linea }.toSet()
        val lista = grupos.filter { it.linea in lineas }
            .flatMap { g -> g.avisos.filter { it.estacionId == null || it.estacionId == e.id }.map { g.linea to it } }
            .sortedBy { it.second.criticidad }
        timerAviso?.cancel()
        avisoEstacion = lista.firstOrNull()?.let { (l, a) -> AvisoEstacion(l, a, lista.size) }
        if (avisoEstacion != null) timerAviso = viewModelScope.launch { delay(5000); avisoEstacion = null }
    }

    // La estación recién elegida manda: si la otra no comparte ramal se
    // borra sin avisar (la pantalla pide elegirla de nuevo).
    fun elegirOrigen(e: Estacion) {
        origen = e
        mostrarAvisosDe(e)
        if (destino?.let { Estaciones.rutaCompartida(e, it) } == null) destino = null
    }

    fun elegirDestino(e: Estacion) {
        destino = e
        mostrarAvisosDe(e)
        if (origen?.let { Estaciones.rutaCompartida(it, e) } == null) origen = null
    }

    fun intercambiar() {
        val o = origen
        origen = destino
        destino = o
        if (activo || resultadoVisible) buscar()
    }

    fun cambiarModo(m: Modo, silencioso: Boolean = false) {
        modo = m
        if (m == Modo.PROGRAMADA && !silencioso) {
            fecha = LocalDate.now(ZONA)
            horaProg = LocalTime.now(ZONA).withSecond(0).withNano(0)
        }
        if (!silencioso) {
            detener()
            resultadoVisible = false
        }
    }

    fun linea(): String? {
        val o = origen ?: return null
        val d = destino ?: return null
        return Estaciones.rutaCompartida(o, d)?.linea
    }

    /* ---------- Favoritos ---------- */

    private fun viajeActual() = origen?.let { o -> destino?.let { d -> Viaje(o.id, d.id) } }

    private fun refrescarViajes() {
        favoritos = almacen.favoritos()
        recientes = almacen.recientes()
        esFavorito = viajeActual()?.let { almacen.esFavorito(it) } ?: false
    }

    fun alternarFavorito() {
        val v = viajeActual() ?: return
        toast(if (almacen.alternarFavorito(v)) "Guardado en tus viajes" else "Quitado de tus viajes")
        refrescarViajes()
    }

    fun quitarFavorito(v: Viaje) {
        almacen.quitarFavorito(v)
        refrescarViajes()
    }

    fun abrirGuardado(v: Viaje) {
        val o = Estaciones.porId(v.o) ?: return
        val d = Estaciones.porId(v.d) ?: return
        origen = o
        destino = d
        cambiarModo(Modo.VIVO, silencioso = true)
        buscar()
    }

    /* ---------- Búsqueda ---------- */

    private fun detener() {
        activo = false
        sondeo?.cancel()
    }

    fun buscar(auto: Boolean = false) {
        val o = origen ?: return
        val d = destino ?: return
        detener()
        // Cada búsqueda a mano trae su anuncio de 3 segundos (salvo la automática al abrir).
        if (meme && !auto) intersticial = 3
        val v = Viaje(o.id, d.id)
        almacen.agregarReciente(v)
        refrescarViajes()

        trenes = emptyList()
        mensaje = null
        ultimaOk = null
        error = null
        guardado = null
        trayecto = "${o.nombre} → ${d.nombre}"
        resultadoVisible = true
        cargando = true
        if (!auto) irAResultado.tryEmit(Unit) // al reabrir no se mueve la pantalla sola

        if (modo == Modo.PROGRAMADA) {
            programado = true
            estadoTexto = "Horarios programados · ${fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} desde las ${horaProg.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            sondeo = viewModelScope.launch { consultar(o, d, programada = true) }
        } else {
            programado = false
            activo = true
            // Mientras llega la respuesta mostramos lo de la última vez (si sirve).
            almacen.leerResultado(v)?.let { (t, lista) ->
                guardado = t
                mostrar(lista, false)
            }
            iniciarSondeo()
        }
    }

    private fun iniciarSondeo() {
        val o = origen ?: return
        val d = destino ?: return
        sondeo?.cancel()
        sondeo = viewModelScope.launch {
            while (isActive && activo && enPrimerPlano) {
                consultar(o, d, programada = false)
                delay(if (error != null) SONDEO_MS * 2 else SONDEO_MS)
            }
        }
    }

    private suspend fun consultar(o: Estacion, d: Estacion, programada: Boolean) {
        try {
            val crudos = if (programada) {
                Api.arribos(o.id, d.id, fecha.toString(), horaProg.format(DateTimeFormatter.ofPattern("HH:mm")))
            } else {
                Api.arribos(o.id, d.id)
            }
            val lista = withContext(Dispatchers.Default) {
                crudos.mapNotNull { Api.normalizar(it, d.id) }
                    // La API abrevia nombres ("Plaza C."): mostramos los de nuestra lista.
                    .map { t ->
                        t.copy(
                            estacionOrigen = o.nombre,
                            estacionDestino = d.nombre,
                            hacia = t.haciaId?.let { Estaciones.porId(it)?.nombre } ?: t.hacia,
                        )
                    }
                    .sortedBy { it.referencia }
                    .take(if (programada) 12 else 8)
            }
            ultimaOk = System.currentTimeMillis()
            error = null
            if (!programada) almacen.guardarResultado(Viaje(o.id, d.id), lista)
            mostrar(lista, programada)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = e as? ErrorApi ?: ErrorApi(0, "Sin respuesta del servidor")
            error = err
            if (trenes.isEmpty()) {
                cargando = false
                mensaje = Mensaje(
                    if (err.status == 0) "${if (online) err.message else "Sin conexión"}. Reintentamos solos en unos segundos."
                    else "El servicio de Trenes Argentinos no respondió bien (${err.message}).",
                    Accion("Reintentar ahora") { buscar() },
                )
            }
        }
        actualizarEstado()
    }

    private fun mostrar(lista: List<Tren>, programada: Boolean) {
        cargando = false
        trenes = lista
        mensaje = null
        if (lista.isEmpty()) {
            // Respuesta vacía no significa cancelación: si hay un aviso de la
            // línea (p. ej. "servicio suspendido") lo mostramos como posible causa.
            val ln = linea()
            val aviso = avisosDelViaje().firstOrNull()
            mensaje = when {
                aviso != null -> Mensaje("No hay trenes en este tramo. Aviso de $ln: ${aviso.texto}", Accion("Ver avisos") { abrirAvisos(ln) })
                programada -> Mensaje("No hay trenes programados para ese horario en este tramo.")
                else -> Mensaje(
                    "No hay trenes en vivo para este tramo ahora mismo. Puede que el servicio aún no haya salido de cabecera.",
                    Accion("Ver horarios programados") {
                        cambiarModo(Modo.PROGRAMADA, silencioso = true)
                        fecha = LocalDate.now(ZONA)
                        horaProg = LocalTime.now(ZONA).withSecond(0).withNano(0)
                        buscar()
                    },
                )
            }
        }
        if (!programada) reprogramarAvisos()
    }

    private fun actualizarEstado() {
        if (modo != Modo.VIVO || !resultadoVisible) return
        val ok = ultimaOk
        val g = guardado
        estadoTexto = when {
            ok == null && g != null -> {
                val min = maxOf(1, ((System.currentTimeMillis() - g) / 60000.0).roundToInt())
                "${if (error != null) "Sin conexión" else "Actualizando…"} · mostrando datos de hace $min min"
            }
            ok == null -> if (error != null) "Sin datos todavía · reintentando…" else "Buscando trenes…"
            else -> {
                val seg = ((System.currentTimeMillis() - ok) / 1000).toInt()
                val hace = if (seg < 5) "recién" else if (seg < 60) "hace $seg s" else "hace ${seg / 60} min"
                "${if (error != null) "Error al actualizar · " else "EN VIVO · "}actualizado $hace"
            }
        }
    }

    /* ---------- "Avisame" ---------- */

    fun alternarAvisoTren(t: Tren) {
        if (t.clave in avisosTren) {
            avisosTren = avisosTren - t.clave
            Notificaciones.cancelar(ctx, t.clave)
            return
        }
        if (!Notificaciones.permitidas(ctx)) {
            toast("Activá las notificaciones de la app para recibir el aviso.")
            return
        }
        avisosTren = avisosTren + t.clave
        avisados.remove(t.clave)
        reprogramarAvisos()
        toast("Te avisamos ${Notificaciones.MINUTOS_ANTES} min antes, aunque cierres la app.")
    }

    private fun reprogramarAvisos() {
        val ahora = System.currentTimeMillis()
        for (t in trenes) {
            if (t.clave !in avisosTren || t.clave in avisados) continue
            val verbo = if (t.esCabecera) "sale de" else "llega a"
            val en = t.referencia - Notificaciones.MINUTOS_ANTES * 60000L
            val cuerpo = "${t.ramal} · ${hora(t.referencia)}${t.anden?.let { " · Andén $it" } ?: ""}"
            if (en <= ahora) {
                avisados += t.clave
                val min = maxOf(0, ((t.referencia - ahora) / 60000.0).roundToInt())
                Notificaciones.mostrar(ctx, "Tu tren $verbo ${t.estacionOrigen} en $min min", cuerpo, t.clave.hashCode())
            } else {
                Notificaciones.programar(ctx, t.clave, en, "Tu tren $verbo ${t.estacionOrigen} en ${Notificaciones.MINUTOS_ANTES} min", cuerpo)
            }
        }
    }

    /* ---------- Avisos del servicio ---------- */

    private fun iniciarAvisos() {
        cicloAvisos?.cancel()
        cicloAvisos = viewModelScope.launch {
            while (isActive) {
                try {
                    grupos = Api.avisos()
                    notificarNuevos()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                delay(AVISOS_MS)
            }
        }
    }

    /** Avisos que afectan el viaje: los de su línea y los de sus estaciones. */
    fun avisosDelViaje(): List<Aviso> {
        val o = origen ?: return emptyList()
        val d = destino ?: return emptyList()
        val g = grupos.find { it.linea == linea() } ?: return emptyList()
        return g.avisos.filter { it.estacionId == null || it.estacionId == o.id || it.estacionId == d.id }
    }

    fun abrirAvisos(linea: String? = null) {
        lineaPrimero = linea
        panelAvisos = true
    }

    // Notifica avisos nuevos de las líneas que el usuario usa (favoritos y
    // recientes). La primera vez solo los marca como vistos.
    private fun notificarNuevos() {
        val vistos = almacen.avisosVistos.toMutableSet()
        val primeraVez = vistos.isEmpty()
        val interes = (almacen.favoritos() + almacen.recientes()).mapNotNull { v ->
            val o = Estaciones.porId(v.o)
            val d = Estaciones.porId(v.d)
            if (o != null && d != null) Estaciones.rutaCompartida(o, d)?.linea else null
        }.toSet()
        val nuevos = mutableListOf<Pair<String, Aviso>>()
        for (g in grupos) for (a in g.avisos) {
            if (!vistos.add(a.id)) continue
            if (!primeraVez && g.linea in interes) nuevos += g.linea to a
        }
        almacen.avisosVistos = vistos
        nuevos.take(3).forEach { (ln, a) -> Notificaciones.mostrar(ctx, "Aviso · $ln", a.texto, a.id.hashCode(), servicio = true) }
    }

    /* ---------- Varios ---------- */

    var panelAcerca by mutableStateOf(false)

    fun alternarMeme() {
        meme = !meme
        almacen.meme = meme
        if (meme) intersticial = 5
        toast(if (meme) "No trates de entenderla, disfrutala." else "Modo serio. Aburrido, pero puntual.")
    }

    // Easter egg: 5 toques seguidos al logo activan el patrocinio (como las
    // opciones de desarrollador de Android).
    private var toques = 0
    private var ultimoToque = 0L

    fun toqueLogo() {
        val ahora = System.currentTimeMillis()
        toques = if (ahora - ultimoToque < 1500) toques + 1 else 1
        ultimoToque = ahora
        when {
            toques >= 5 -> {
                toques = 0
                alternarMeme()
            }
            toques >= 3 -> toast("Te faltan ${5 - toques} para disfrutarla")
        }
    }

    fun alternarTema() {
        oscuro = !oscuro
        almacen.temaOscuro = oscuro
    }

    fun intentCompartir(): Intent? {
        val o = origen ?: return null
        val d = destino ?: return null
        val url = "https://tapia.chiqui.ar/?o=${o.id}&d=${d.id}"
        val texto = "¿Cuándo llega el tren de ${o.nombre} a ${d.nombre}? Miralo en TrenAR: $url"
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null)
    }

    fun toastPublico(t: String) = toast(t)
}
