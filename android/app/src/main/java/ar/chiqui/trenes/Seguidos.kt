package ar.chiqui.trenes

import android.content.Context
import ar.chiqui.trenes.datos.CambioTren
import ar.chiqui.trenes.datos.Tren
import ar.chiqui.trenes.datos.detectarCambioTren
import ar.chiqui.trenes.ui.hora

/**
 * Trenes con "Avisame": si dejan de venir en la respuesta antes de su hora o
 * suman UMBRAL_DEMORA_MIN de demora, notifica. La base se mueve tras avisar,
 * así el mismo evento no se repite. [viaje] = "origen-destino".
 */
class Seguidos(private val ctx: Context) {
    private class Seguido(val viaje: String, var base: Long?, var ultimo: Tren)
    private val mapa = mutableMapOf<String, Seguido>()

    fun seguir(viaje: String, t: Tren) { mapa[t.clave] = Seguido(viaje, t.referencia, t) }
    fun dejar(clave: String) { mapa.remove(clave) }

    fun revisar(viaje: String, lista: List<Tren>, ahora: Long = System.currentTimeMillis()) {
        for ((clave, s) in mapa) {
            if (s.viaje != viaje) continue
            val actual = lista.firstOrNull { it.clave == clave }
            val cambio = detectarCambioTren(s.base, actual?.referencia, ahora, ultimaVista = s.ultimo.referencia)
            if (actual != null) s.ultimo = actual
            val t = s.ultimo
            val id = "cambio-$clave".hashCode()
            when (cambio) {
                null -> {}
                CambioTren.Desaparecio -> {
                    s.base = null
                    Notificaciones.mostrar(ctx, "Tu tren ya no aparece en los arribos",
                        "${t.ramal} · ${hora(t.referencia)} · puede estar cancelado o muy demorado.", id)
                }
                is CambioTren.Demora -> {
                    s.base = t.referencia
                    val verbo = if (t.esCabecera) "sale de" else "llega a"
                    Notificaciones.mostrar(ctx, "Tu tren suma ${cambio.sumaMin} min de demora",
                        "${t.ramal} · ahora $verbo ${t.estacionOrigen} a las ${hora(t.referencia)}", id)
                }
            }
        }
    }
}
