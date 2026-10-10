package ar.chiqui.trenes.datos

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

val ZONA: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

data class LatLon(val lat: Double, val lon: Double)

data class Ruta(val linea: String, val ramal: String)

data class Estacion(
    val id: Int,
    val nombre: String,
    val rutas: List<Ruta>,
    val coords: LatLon?,
    val busqueda: String,
)

data class Viaje(val o: Int, val d: Int, val t: Long = 0)

/** Un tren normalizado a partir de la respuesta de arribos. Horas en epoch ms. */
data class Tren(
    val clave: String,
    val ramal: String,
    val estacionOrigen: String,
    val estacionDestino: String,
    val esCabecera: Boolean,
    val referencia: Long,
    val programada: Long?,
    val enVivo: Boolean,
    val demoraMin: Int?,
    val anden: String?,
    val llegadaDestino: Long?,
    val duracionMin: Int?,
    val ubicacion: LatLon?,
    // Cabecera donde termina el servicio: da el sentido ("hacia Ezeiza").
    val haciaId: Int?,
    val hacia: String?,
) {
    fun aJson(): JSONObject = JSONObject()
        .put("clave", clave).put("ramal", ramal)
        .put("estacionOrigen", estacionOrigen).put("estacionDestino", estacionDestino)
        .put("esCabecera", esCabecera).put("referencia", referencia)
        .putOpt("programada", programada).put("enVivo", enVivo).putOpt("demoraMin", demoraMin)
        .putOpt("anden", anden).putOpt("llegadaDestino", llegadaDestino).putOpt("duracionMin", duracionMin)
        .putOpt("lat", ubicacion?.lat).putOpt("lon", ubicacion?.lon)
        .putOpt("haciaId", haciaId).putOpt("hacia", hacia)

    companion object {
        fun deJson(j: JSONObject) = Tren(
            clave = j.getString("clave"),
            ramal = j.getString("ramal"),
            estacionOrigen = j.getString("estacionOrigen"),
            estacionDestino = j.getString("estacionDestino"),
            esCabecera = j.getBoolean("esCabecera"),
            referencia = j.getLong("referencia"),
            programada = j.largo("programada"),
            enVivo = j.getBoolean("enVivo"),
            demoraMin = j.largo("demoraMin")?.toInt(),
            anden = j.texto("anden"),
            llegadaDestino = j.largo("llegadaDestino"),
            duracionMin = j.largo("duracionMin")?.toInt(),
            ubicacion = if (j.has("lat") && j.has("lon")) LatLon(j.getDouble("lat"), j.getDouble("lon")) else null,
            haciaId = j.largo("haciaId")?.toInt(),
            hacia = j.texto("hacia"),
        )
    }
}

data class Aviso(
    val id: String,
    val texto: String,
    val estacionId: Int?,
    val criticidad: Int, // menor = más grave
) {
    val nivel get() = if (criticidad <= 1) Nivel.GRAVE else if (criticidad <= 3) Nivel.MEDIO else Nivel.INFO
}

enum class Nivel { GRAVE, MEDIO, INFO }

data class GrupoAvisos(val linea: String, val avisos: List<Aviso>)

/* ---------- Ayudas para org.json: optString devuelve "null" ante JSON null ---------- */

fun JSONObject.texto(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

fun JSONObject.largo(k: String): Long? = if (isNull(k)) null else (opt(k) as? Number)?.toLong()

fun JSONArray?.objetos(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
