package ar.chiqui.trenes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.Tren

@Composable
fun Etiqueta(texto: String, fondo: Color, tinta: Color) {
    Text(
        texto,
        Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 10.dp, vertical = 3.dp),
        color = tinta,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Tarjeta de un tren: cuenta regresiva con segundos, horas, etiquetas y mapa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Tarjeta(
    t: Tren,
    linea: String?,
    color: Color,
    proximo: Boolean,
    ahora: Long,
    avisoActivo: Boolean,
    origen: Estacion,
    destino: Estacion,
    oscuro: Boolean,
    onAvisar: () -> Unit,
) {
    var mapaAbierto by rememberSaveable(t.clave) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val c = cuentaRegresiva(t.referencia, ahora)
    val urgente = !c.ya && t.referencia - ahora in 0..120000
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (proximo) BorderStroke(2.dp, color) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(t.ramal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        val sentido = if (t.hacia != null && t.hacia != t.estacionDestino) "hacia ${t.hacia} · " else ""
                        Text(
                            "${linea?.let { "$it · " } ?: ""}$sentido${if (t.esCabecera) "sale de" else "llega a"} ${t.estacionOrigen}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            c.num,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = when {
                                c.ya -> ColorOk
                                urgente -> ColorAcento
                                else -> cs.primary
                            },
                        )
                        Text(c.uni, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColHora(hora(t.referencia), t.estacionOrigen, Modifier.weight(1f))
                    Text(
                        if (t.duracionMin != null) "${duracion(t.duracionMin)} →" else "→",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ColHora(hora(t.llegadaDestino), t.estacionDestino, Modifier.weight(1f), TextAlign.End)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.enVivo) Etiqueta("En vivo", ColorOk.copy(alpha = 0.15f), ColorOk)
                    else Etiqueta("Programado", cs.surfaceVariant, cs.onSurfaceVariant)
                    val dm = t.demoraMin
                    if (dm != null && dm >= 2) {
                        val col = if (dm >= 10) ColorGrave else ColorDemora
                        Etiqueta("Demora +$dm min", col.copy(alpha = 0.15f), col)
                    } else if (dm != null && t.enVivo) {
                        Etiqueta("A horario", cs.surfaceVariant, cs.onSurfaceVariant)
                    }
                    t.anden?.let { Etiqueta("Andén $it", cs.primaryContainer, cs.onPrimaryContainer) }
                }

                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAvisar) { Text(if (avisoActivo) "Aviso activo" else "Avisame") }
                    if (t.ubicacion != null) {
                        OutlinedButton(onClick = { mapaAbierto = !mapaAbierto }) { Text(if (mapaAbierto) "Ocultar" else "Ver mapa") }
                    }
                }
                if (mapaAbierto && t.ubicacion != null) {
                    MapaTren(
                        t, origen, destino, color, oscuro, ahora,
                        Modifier.padding(top = 10.dp).fillMaxWidth().height(280.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColHora(h: String, est: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(h, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
        Text(est, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
    }
}

@Composable
fun Esqueleto() {
    Box(
        Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
    )
}
