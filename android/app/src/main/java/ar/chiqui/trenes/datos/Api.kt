package ar.chiqui.trenes.datos

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class ErrorApi(val status: Int, mensaje: String) : Exception(mensaje)

/** Cliente de api-servicios.sofse.gob.ar (misma lógica que js/v4/api.js). */
object Api {
    private const val BASE = "https://api-servicios.sofse.gob.ar/v1"
    private const val TIMEOUT_MS = 12000

    @Volatile private var token: String? = null
    private val candado = Mutex()

    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    private fun credenciales(): Pair<String, String> {
        val user = b64(LocalDate.now(ZONA).format(DateTimeFormatter.BASIC_ISO_DATE) + "sofse")
        var pass = b64(user)
        for ((k, v) in listOf("a" to "#t", "e" to "#x", "i" to "#f", "o" to "#l", "u" to "#7", "=" to "#g")) {
            pass = pass.replace(k, v)
        }
        pass = b64(pass.reversed())
        for ((k, v) in listOf("a" to "32%j", "e" to "32%p", "i" to "32%w", "o" to "32%8", "u" to "#0", "=" to "32%v")) {
            pass = pass.replace(k, v)
        }
        return user to pass.reversed()
    }

    private class Respuesta(val status: Int, val cuerpo: String)

    private suspend fun pedir(url: String, cuerpo: String? = null, auth: String? = null) = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", "TrenAR/1.0") // sin modelo de teléfono ni versión de Android
            auth?.let { c.setRequestProperty("Authorization", it) }
            if (cuerpo != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(cuerpo.toByteArray()) }
            }
            val status = c.responseCode
            val flujo = if (status < 400) c.inputStream else c.errorStream
            Respuesta(status, flujo?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    // Un login a la vez: si varias consultas piden token juntas, la segunda
    // reusa el que consiguió la primera.
    suspend fun login(anterior: String? = null): String = candado.withLock {
        token?.takeIf { it != anterior }?.let { return it }
        val (user, pass) = credenciales()
        val r = try {
            pedir("$BASE/auth/authorize", JSONObject().put("username", user).put("password", pass).toString())
        } catch (e: IOException) {
            throw ErrorApi(0, "Sin respuesta del servidor")
        }
        if (r.status !in 200..299) throw ErrorApi(r.status, "No se pudo autenticar")
        val t = (leerJson(r) as? JSONObject)?.texto("token") ?: throw ErrorApi(r.status, "No se pudo autenticar")
        token = t
        t
    }

    // GET autenticado. Renueva el token ante 401/403 (vence a medianoche porque
    // las credenciales dependen de la fecha) y reintenta los 5xx transitorios.
    private suspend fun get(ruta: String, reintentos: Int = 2): Any {
        if (token == null) login()
        var intento = 0
        while (true) {
            val usado = token
            val r = try {
                pedir("$BASE$ruta", auth = usado)
            } catch (e: IOException) {
                if (intento < reintentos) {
                    delay(800L * ++intento)
                    continue
                }
                throw ErrorApi(0, "Sin respuesta del servidor")
            }
            if (r.status in 200..299) {
                try {
                    return leerJson(r)
                } catch (e: ErrorApi) {
                    if (intento < reintentos) {
                        delay(800L * ++intento)
                        continue
                    }
                    throw e
                }
            }
            if ((r.status == 401 || r.status == 403) && intento == 0) {
                login(usado)
                intento++
                continue
            }
            if (r.status >= 500 && intento < reintentos) {
                delay(800L * ++intento)
                continue
            }
            throw ErrorApi(r.status, "Error ${r.status}")
        }
    }

    // A veces la API (o un proxy) responde HTML con status 200: lo tratamos como error.
    private fun leerJson(r: Respuesta): Any {
        val v = try {
            JSONTokener(r.cuerpo).nextValue()
        } catch (e: JSONException) {
            null
        }
        if (v is JSONObject || v is JSONArray) return v
        throw ErrorApi(r.status, "Respuesta inválida del servidor")
    }

    suspend fun arribos(origen: Int, destino: Int, fecha: String? = null, hora: String? = null): List<JSONObject> {
        var ruta = "/arribos/estacion/$origen?hasta=$destino"
        if (fecha != null && hora != null) ruta += "&fecha=$fecha&hora=$hora&tipoBusqueda=partida"
        return (get(ruta) as? JSONObject)?.optJSONArray("results").objetos()
    }

    private fun fecha(v: Any?): Long? {
        val s = (v as? String)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(ZONA).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Normaliza un resultado crudo. Devuelve null si el servicio no pasa por el destino. */
    fun normalizar(item: JSONObject, destinoId: Int): Tren? {
        val arribo = item.optJSONObject("arribo") ?: JSONObject()
        val servicio = item.optJSONObject("servicio") ?: JSONObject()
        val paradas = servicio.optJSONArray("estaciones").objetos()
        val paradaDestino = paradas.find { it.texto("idElemento") == destinoId.toString() } ?: return null

        val llegada = arribo.optJSONObject("llegada")
        val salida = arribo.optJSONObject("salida")
        val llegadaProg = fecha(llegada?.opt("programada"))
        val llegadaEst = fecha(llegada?.opt("estimada"))

        // Si el tren arranca en el origen (cabecera) no hay "llegada": usamos la salida.
        val esCabecera = llegadaProg == null && llegadaEst == null
        val programada = if (esCabecera) fecha(salida?.opt("programada")) else llegadaProg
        val estimada = if (esCabecera) fecha(salida?.opt("estimada")) else llegadaEst
        val referencia = estimada ?: programada ?: return null

        val demora = if (estimada != null && programada != null) ((estimada - programada) / 60000.0).roundToInt() else null
        val ld = paradaDestino.optJSONObject("llegada")
        val llegadaDestino = fecha(ld?.opt("estimada")) ?: fecha(ld?.opt("programada"))
        val loc = servicio.optJSONObject("location")
        val lat = loc?.texto("lat")?.toDoubleOrNull()?.takeIf { it != 0.0 }
        val lon = loc?.texto("long")?.toDoubleOrNull()?.takeIf { it != 0.0 }
        val ubicacion = if (lat != null && lon != null) LatLon(lat, lon) else null

        val ramal = servicio.optJSONObject("ramal")?.texto("nombre") ?: "Servicio"
        val hasta = servicio.optJSONObject("hasta")
        val final = paradas.lastOrNull() ?: hasta?.optJSONObject("estacion") ?: hasta
        return Tren(
            clave = arribo.optJSONObject("equipo")?.texto("id") ?: "$ramal-$programada",
            ramal = ramal,
            estacionOrigen = arribo.texto("nombre") ?: "",
            estacionDestino = paradaDestino.texto("nombre") ?: "",
            esCabecera = esCabecera,
            referencia = referencia,
            programada = programada,
            enVivo = estimada != null || ubicacion != null,
            demoraMin = demora,
            anden = arribo.optJSONObject("anden")?.texto("nombre"),
            llegadaDestino = llegadaDestino,
            duracionMin = llegadaDestino?.let { ((it - referencia) / 60000.0).roundToInt() },
            ubicacion = ubicacion,
            haciaId = final?.texto("idElemento")?.toIntOrNull(),
            hacia = final?.texto("nombre"),
            horarios = paradas.mapNotNull { e ->
                val id = e.texto("idElemento")?.toIntOrNull() ?: return@mapNotNull null
                val ll = e.optJSONObject("llegada")
                val sa = e.optJSONObject("salida")
                val h = fecha(ll?.opt("estimada")) ?: fecha(ll?.opt("programada")) ?: fecha(sa?.opt("estimada")) ?: fecha(sa?.opt("programada"))
                Parada(id, e.texto("nombre") ?: "", h)
            },
        )
    }

    private fun fechaApi(s: String?): Long? = s?.let {
        runCatching { LocalDateTime.parse(it.replace(' ', 'T')).atZone(ZONA).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Avisos vigentes por línea (feriados, obras, interrupciones), del más grave al más leve. */
    suspend fun avisos(): List<GrupoAvisos> {
        val gerencias = get("/infraestructura/gerencias") as? JSONArray ?: return emptyList()
        val ahora = System.currentTimeMillis()
        return gerencias.objetos().map { g ->
            GrupoAvisos(
                linea = g.texto("nombre") ?: "",
                avisos = g.optJSONArray("alerta").objetos()
                    .filter { it.optInt("habilitado", 1) != 0 }
                    .filter { a ->
                        val desde = fechaApi(a.texto("vigencia_desde"))
                        val hasta = fechaApi(a.texto("vigencia_hasta"))
                        (hasta == null || hasta > ahora) && (desde == null || desde <= ahora)
                    }
                    .mapNotNull { a ->
                        val texto = a.texto("contenido")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        Aviso(
                            id = a.texto("id") ?: texto.hashCode().toString(),
                            texto = texto,
                            estacionId = a.largo("estacion_id")?.toInt(),
                            criticidad = a.largo("criticidad_orden")?.toInt() ?: 9,
                        )
                    }
                    .sortedBy { it.criticidad },
            )
        }
    }
}
