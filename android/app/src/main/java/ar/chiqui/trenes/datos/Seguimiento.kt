package ar.chiqui.trenes.datos

import kotlin.math.roundToInt

/** Igual que `detectarCambioTren` de js/v4/logica.js. */
const val UMBRAL_DEMORA_MIN = 5

sealed class CambioTren {
    object Desaparecio : CambioTren()
    data class Demora(val sumaMin: Int) : CambioTren()
}

/**
 * Tren seguido ("Avisame"): compara la hora de referencia base (al activar el
 * aviso o en la última alerta) con la de la respuesta nueva (null = no vino).
 * Desaparecer con menos de 1 min para su hora es normal (llegó o salió).
 * Quien llama mueve la base a la hora nueva tras avisar (no repite el aviso).
 */
fun detectarCambioTren(anterior: Long?, actual: Long?, ahora: Long, umbralMin: Int = UMBRAL_DEMORA_MIN): CambioTren? {
    if (anterior == null) return null
    if (actual == null) return if (anterior - ahora > 60_000) CambioTren.Desaparecio else null
    val sumaMin = ((actual - anterior) / 60000.0).roundToInt()
    return if (sumaMin >= umbralMin) CambioTren.Demora(sumaMin) else null
}
