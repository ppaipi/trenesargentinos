package ar.chiqui.trenes.datos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

// Modo a bordo: dónde va el tren respecto de las paradas. Igual que
// proyectarEnLinea/progresoViaje de js/v4/logica.js (mismos tests).

const val TOLERANCIA_ESTACION_KM = 0.15 // a menos de esto, "en la estación"
const val MAX_FUERA_DE_VIA_KM = 1.0 // más lejos de la vía, el GPS no sirve

data class Proyeccion(val km: Double, val distKm: Double)

/** Parada del tramo origen → destino, con coordenadas y hora de paso (ms). */
data class ParadaViaje(val id: Int, val nombre: String, val coords: LatLon?, val hora: Long?)

enum class Fuente { GPS, ESTIMADO }

data class Progreso(val ultima: Int, val siguiente: Int?, val enEstacion: Boolean, val llego: Boolean, val fuente: Fuente)

private fun aPlano(p: LatLon, cosLat: Double) = doubleArrayOf(p.lon * 111.32 * cosLat, p.lat * 110.574)

/** Km recorridos sobre la polilínea hasta el punto más cercano a [punto], y su distancia a la vía. */
fun proyectarEnLinea(linea: List<LatLon>?, punto: LatLon?): Proyeccion? {
    if (linea == null || linea.size < 2 || punto == null) return null
    val cosLat = cos(Math.toRadians(punto.lat))
    val p = aPlano(punto, cosLat)
    var acumulado = 0.0
    var mejor = Proyeccion(0.0, Double.POSITIVE_INFINITY)
    for (i in 0 until linea.size - 1) {
        val a = aPlano(linea[i], cosLat)
        val b = aPlano(linea[i + 1], cosLat)
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 > 0) (((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / largo2).coerceIn(0.0, 1.0) else 0.0
        val dist = hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
        val largo = sqrt(largo2)
        if (dist < mejor.distKm) mejor = Proyeccion(acumulado + t * largo, dist)
        acumulado += largo
    }
    return mejor
}

/**
 * Con [pos] (GPS) proyecta sobre [linea] (recorrido real; si no hay, une las
 * paradas); sin GPS o lejos de la vía, estima con las horas.
 */
fun progresoViaje(paradas: List<ParadaViaje>, pos: LatLon?, linea: List<LatLon>?, ahora: Long): Progreso {
    val n = paradas.size
    fun fin(ultima: Int, enEstacion: Boolean, fuente: Fuente) =
        Progreso(ultima, if (ultima + 1 < n) ultima + 1 else null, enEstacion, n > 0 && ultima == n - 1, fuente)
    val conCoords = paradas.mapNotNull { it.coords }
    val via = if (linea != null && linea.size > 1) linea else conCoords
    val yo = proyectarEnLinea(via, pos)
    if (yo != null && yo.distKm <= MAX_FUERA_DE_VIA_KM && conCoords.size >= 2) {
        val km = paradas.map { p -> p.coords?.let { proyectarEnLinea(via, it)!!.km } }
        val conocidos = km.filterNotNull()
        val sentido = if (conocidos.last() >= conocidos.first()) 1 else -1
        var ultima = -1
        km.forEachIndexed { i, k -> if (k != null && sentido * k <= sentido * yo.km + TOLERANCIA_ESTACION_KM) ultima = i }
        val ku = km.getOrNull(ultima)
        return fin(ultima, ku != null && abs(ku - yo.km) <= TOLERANCIA_ESTACION_KM, Fuente.GPS)
    }
    var ultima = -1
    paradas.forEachIndexed { i, p -> if (p.hora != null && p.hora <= ahora) ultima = i }
    val h = paradas.getOrNull(ultima)?.hora
    return fin(ultima, h != null && ahora - h < 30000, Fuente.ESTIMADO)
}
