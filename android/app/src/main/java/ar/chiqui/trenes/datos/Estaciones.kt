package ar.chiqui.trenes.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val LINEAS_PRINCIPALES = listOf("Mitre", "Sarmiento", "Roca", "San Martín", "Belgrano Sur", "Tren de la Costa")

// Punto de referencia para elegir la cabecera de cada ramal: la estación del
// ramal más cercana al centro porteño (Retiro, Once, Constitución, etc).
private val CENTRO = LatLon(-34.6037, -58.3816)

private val COLLATOR: Collator = Collator.getInstance(Locale("es", "AR"))

fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

fun distanciaKm(a: LatLon, b: LatLon): Double {
    val rad = Math.PI / 180
    val dLat = (b.lat - a.lat) * rad
    val dLon = (b.lon - a.lon) * rad
    val h = sin(dLat / 2).pow(2) + cos(a.lat * rad) * cos(b.lat * rad) * sin(dLon / 2).pow(2)
    return 12742 * asin(sqrt(h))
}

fun formatearDistancia(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()} m" else String.format(Locale("es", "AR"), "%.1f km", km)

// Ordena un ramal desde la cabecera hacia afuera. Las que no tienen
// coordenadas quedan al final en orden alfabético.
fun ordenarGeografico(lista: List<Estacion>): List<Estacion> {
    val con = lista.filter { it.coords != null }
    val sin = lista.filter { it.coords == null }.sortedWith(compareBy(COLLATOR) { it.nombre })
    if (con.isEmpty()) return sin
    val cabecera = con.minBy { distanciaKm(it.coords!!, CENTRO) }.coords!!
    return con.sortedBy { distanciaKm(it.coords!!, cabecera) } + sin
}

/**
 * Meme del torneo de 30 equipos en 2 zonas de 15: el ramal se parte en
 * "Zona A" y "Zona B" de n/2 estaciones, salteadas (no trates de entenderlo).
 * Si n es impar, la que sobra "asciende por escritorio". Igual que la web.
 */
data class Zonas<T>(val mitad: Int, val zonaA: List<T>, val zonaB: List<T>, val escritorio: T?)

fun <T> zonasTorneo(lista: List<T>): Zonas<T> {
    val mitad = lista.size / 2
    val zonaA = lista.filterIndexed { i, _ -> i % 2 == 0 }.take(mitad)
    val zonaB = lista.filterIndexed { i, _ -> i % 2 == 1 }.take(mitad)
    val escritorio = (lista - zonaA.toSet() - zonaB.toSet()).firstOrNull()
    return Zonas(mitad, zonaA, zonaB, escritorio)
}

/** Estaciones y recorridos, leídos de los mismos JSON del sitio (assets). */
object Estaciones {
    var todas: List<Estacion> = emptyList()
        private set
    private var porId: Map<Int, Estacion> = emptyMap()
    private var indice: Map<Ruta, List<Estacion>> = emptyMap()
    private var recorridos: JSONObject? = null
    private lateinit var contexto: Context

    private fun leerAsset(nombre: String) = contexto.assets.open(nombre).bufferedReader().use { it.readText() }

    fun cargar(ctx: Context) {
        if (todas.isNotEmpty()) return
        contexto = ctx.applicationContext
        val coords = JSONObject(leerAsset("estaciones-coords.json"))
        val crudo = JSONArray(leerAsset("estaciones.json"))
        val lista = crudo.objetos().map { e ->
            val id = e.getInt("id")
            val nombre = e.getString("nombre")
            Estacion(
                id = id,
                nombre = nombre,
                rutas = e.optJSONArray("rutas").objetos().map { Ruta(it.getString("linea"), it.getString("ramal")) },
                coords = coords.optJSONArray(id.toString())?.let { LatLon(it.getDouble(0), it.getDouble(1)) },
                busqueda = normalizar(nombre),
            )
        }
        val rutas = LinkedHashMap<Ruta, MutableList<Estacion>>()
        lista.forEach { e -> e.rutas.forEach { rutas.getOrPut(it) { mutableListOf() }.add(e) } }
        indice = rutas.mapValues { ordenarGeografico(it.value) }
        porId = lista.associateBy { it.id }
        todas = lista
    }

    fun porId(id: Int): Estacion? = porId[id]

    fun ramalesDeLinea(linea: String): List<String> =
        indice.keys.filter { it.linea == linea }.map { it.ramal }.distinct().sortedWith(COLLATOR)

    fun deRuta(ruta: Ruta): List<Estacion> = indice[ruta] ?: emptyList()

    fun rutaCompartida(a: Estacion, b: Estacion): Ruta? = a.rutas.firstOrNull { it in b.rutas }

    fun lineas(e: Estacion) = e.rutas.map { it.linea }.distinct().joinToString(", ")

    // Primero las que empiezan con el texto, después las que lo contienen.
    fun buscar(texto: String): List<Estacion> {
        val objetivo = normalizar(texto.trim())
        if (objetivo.isEmpty()) return emptyList()
        val porNombre = compareBy(COLLATOR) { e: Estacion -> e.nombre }
        val (empiezan, resto) = todas.partition { it.busqueda.startsWith(objetivo) }
        return empiezan.sortedWith(porNombre) + resto.filter { objetivo in it.busqueda }.sortedWith(porNombre)
    }

    fun cercanas(pos: LatLon, cantidad: Int = 6): List<Pair<Estacion, Double>> =
        todas.mapNotNull { e -> e.coords?.let { e to distanciaKm(pos, it) } }.sortedBy { it.second }.take(cantidad)

    /** Polilínea real sobre las vías (recorridos.json), o null si no hay. */
    @Synchronized
    fun recorrido(ruta: Ruta): List<LatLon>? = recorrido("${ruta.linea}|||${ruta.ramal}")

    /** Por clave `linea|||ramal` (también las líneas programadas). */
    @Synchronized
    fun recorrido(clave: String): List<LatLon>? {
        val todos = recorridos ?: JSONObject(leerAsset("recorridos.json")).also { recorridos = it }
        val arr = todos.optJSONArray(clave) ?: return null
        return (0 until arr.length()).map { arr.getJSONArray(it).let { p -> LatLon(p.getDouble(0), p.getDouble(1)) } }
    }
}
