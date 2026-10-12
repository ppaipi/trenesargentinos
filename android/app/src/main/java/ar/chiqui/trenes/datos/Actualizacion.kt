package ar.chiqui.trenes.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Versión nueva del APK: el workflow apk.yml publica cada build como Release de GitHub
 * con tag "v1.0.N", donde N = versionCode (cantidad de commits). Si N supera al instalado,
 * la app muestra "Actualizar": Instalador baja "TrenAR-vX.Y.N.apk" y abre el instalador.
 * Si la descarga falla, DESCARGA abre la página de la última Release en el navegador.
 */
object Actualizacion {
    const val DESCARGA = "https://github.com/ppaipi/trenesargentinos/releases/latest"
    private const val ULTIMA = "https://api.github.com/repos/ppaipi/trenesargentinos/releases/latest"

    /** Cada versión con su nombre: sin "TrenAR (1).apk", "TrenAR (2).apk". */
    fun archivo(tag: String) = "TrenAR-$tag.apk"

    fun descarga(tag: String) = "https://github.com/ppaipi/trenesargentinos/releases/download/$tag/${archivo(tag)}"

    /** "v1.0.204" -> 204 (lógica pura, probada en LogicaTest). */
    fun numero(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()

    /** Tag de la última Release, o null sin conexión o si GitHub no responde. */
    suspend fun ultima(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(ULTIMA).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode != 200) null
                else JSONObject(c.inputStream.bufferedReader().readText()).optString("tag_name").ifEmpty { null }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
