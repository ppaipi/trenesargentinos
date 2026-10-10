package ar.chiqui.trenes.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Favoritos, recientes, tema y último resultado por viaje (SharedPreferences). */
class Almacen(ctx: Context) {
    private val p = ctx.getSharedPreferences("trenes", Context.MODE_PRIVATE)

    private fun leerViajes(clave: String): List<Viaje> = runCatching {
        JSONArray(p.getString(clave, "[]")).objetos().map { Viaje(it.getInt("o"), it.getInt("d"), it.optLong("t")) }
    }.getOrDefault(emptyList())

    private fun escribirViajes(clave: String, lista: List<Viaje>) {
        val arr = JSONArray()
        lista.forEach { arr.put(JSONObject().put("o", it.o).put("d", it.d).put("t", it.t)) }
        p.edit().putString(clave, arr.toString()).apply()
    }

    private fun mismo(a: Viaje, b: Viaje) = a.o == b.o && a.d == b.d

    fun favoritos() = leerViajes("favoritos")

    fun esFavorito(v: Viaje) = favoritos().any { mismo(it, v) }

    /** Devuelve true si quedó agregado. */
    fun alternarFavorito(v: Viaje): Boolean {
        val lista = favoritos().toMutableList()
        val i = lista.indexOfFirst { mismo(it, v) }
        if (i >= 0) lista.removeAt(i) else lista.add(0, v.copy(t = 0))
        escribirViajes("favoritos", lista.take(10))
        return i < 0
    }

    fun quitarFavorito(v: Viaje) = escribirViajes("favoritos", favoritos().filterNot { mismo(it, v) })

    fun recientes() = leerViajes("recientes")

    fun agregarReciente(v: Viaje) =
        escribirViajes("recientes", (listOf(v.copy(t = System.currentTimeMillis())) + recientes().filterNot { mismo(it, v) }).take(4))

    // Último viaje si se buscó hace poco: permite retomarlo al reabrir la app.
    fun recienteActivo(maxMin: Int = 30): Viaje? =
        recientes().firstOrNull()?.takeIf { System.currentTimeMillis() - it.t < maxMin * 60000L }

    var temaOscuro: Boolean
        get() = p.getBoolean("temaOscuro", false) // claro por defecto
        set(v) = p.edit().putBoolean("temaOscuro", v).apply()

    var avisosVistos: Set<String>
        get() = p.getStringSet("avisosVistos", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("avisosVistos", v.toList().takeLast(300).toSet()).apply()

    /* ---------- Último resultado por viaje (se muestra mientras carga) ---------- */

    private val vigenciaMs = 45 * 60000L

    fun guardarResultado(v: Viaje, trenes: List<Tren>) {
        val todos = runCatching { JSONObject(p.getString("resultados", "{}")!!) }.getOrDefault(JSONObject())
        todos.put("${v.o}-${v.d}", JSONObject().put("t", System.currentTimeMillis()).put("trenes", JSONArray(trenes.map { it.aJson() })))
        val claves = todos.keys().asSequence().sortedByDescending { todos.getJSONObject(it).getLong("t") }.toList()
        claves.drop(6).forEach { todos.remove(it) } // solo los últimos viajes
        p.edit().putString("resultados", todos.toString()).apply()
    }

    /** Trenes guardados que todavía no pasaron, con la hora en que se guardaron. */
    fun leerResultado(v: Viaje): Pair<Long, List<Tren>>? = runCatching {
        val ahora = System.currentTimeMillis()
        val r = JSONObject(p.getString("resultados", "{}")!!).optJSONObject("${v.o}-${v.d}") ?: return null
        val t = r.getLong("t")
        if (ahora - t > vigenciaMs) return null
        val trenes = r.getJSONArray("trenes").objetos().map { Tren.deJson(it) }.filter { it.referencia > ahora - 60000 }
        if (trenes.isEmpty()) null else t to trenes
    }.getOrNull()
}
