package ar.chiqui.trenes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.Viaje

/**
 * Atajos al mantener apretado el ícono: hasta 4 viajes favoritos. Cada uno abre
 * el mismo link que se comparte (https://tapia.chiqui.ar/?o=&d=), que MainActivity
 * ya sabe abrir. En Android < 7.1 ShortcutManagerCompat no hace nada.
 */
object Atajos {
    const val MAX = 4
    private var ultimos: List<Pair<Int, Int>>? = null

    fun actualizar(ctx: Context, favoritos: List<Viaje>) {
        val claves = favoritos.map { it.o to it.d }
        if (claves == ultimos) return
        ultimos = claves
        val icono = IconCompat.createWithResource(ctx, R.drawable.ic_atajo)
        val max = MAX.coerceAtMost(ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtLeast(1))
        val atajos = favoritos.mapNotNull { v ->
            val o = Estaciones.porId(v.o) ?: return@mapNotNull null
            val d = Estaciones.porId(v.d) ?: return@mapNotNull null
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link(v)))
                .setClass(ctx, MainActivity::class.java)
            ShortcutInfoCompat.Builder(ctx, "viaje_${v.o}_${v.d}")
                .setShortLabel(etiquetaCorta(o.nombre, d.nombre))
                .setLongLabel("${o.nombre} - ${d.nombre}")
                .setIcon(icono)
                .setIntent(intent)
                .build()
        }.take(max)
        // false o excepción = límite de frecuencia del sistema: se reintenta la próxima vez.
        val ok = try {
            ShortcutManagerCompat.setDynamicShortcuts(ctx, atajos)
        } catch (_: Exception) {
            false
        }
        if (!ok) ultimos = null
    }

    fun link(v: Viaje) = "https://tapia.chiqui.ar/?o=${v.o}&d=${v.d}"

    /** El launcher corta cerca de 10-12 caracteres: se prioriza el destino. */
    fun etiquetaCorta(origen: String, destino: String) =
        if (origen.length + destino.length + 3 <= 22) "$origen - $destino" else "a $destino"
}
