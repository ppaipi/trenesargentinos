package ar.chiqui.trenes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
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
fun Etiqueta(texto: String, fondo: Color, tinta: Color, punto: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punto) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(tinta))
            Spacer(Modifier.width(5.dp))
        }
        Text(texto.uppercase(), color = tinta, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

/** Botón chico celeste como .boton-secundario de la web. */
@Composable
fun BotonSecundario(texto: String, activo: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        texto,
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (activo) cs.primary else cs.primaryContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (activo) cs.onPrimary else cs.primary,
        fontSize = 14.sp,
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
    onAnden: (() -> Unit)? = null,
) {
    var mapaAbierto by rememberSaveable(t.clave) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val c = cuentaRegresiva(t.referencia, ahora)
    val urgente = !c.ya && t.referencia - ahora in 0..120000
    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp),
            border = if (proximo) BorderStroke(2.dp, color) else null,
            modifier = Modifier.fillMaxWidth().padding(top = if (proximo) 8.dp else 0.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(color))
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(t.ramal, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = cs.primary)
                            val sentido = if (t.hacia != null && t.hacia != t.estacionDestino) "hacia ${t.hacia} · " else ""
                            Text(
                                "${linea?.let { "$it · " } ?: ""}$sentido${if (t.esCabecera) "sale de" else "llega a"} ${t.estacionOrigen}",
                                fontSize = 13.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                c.num,
                                fontFamily = Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                letterSpacing = (-0.5).sp,
                                color = when {
                                    c.ya -> ColorOk
                                    urgente -> ColorAcento
                                    else -> cs.primary
                                },
                            )
                            Text(c.uni.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = cs.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outline)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ColHora(hora(t.referencia), t.estacionOrigen, Modifier.weight(1f))
                        Text(
                            if (t.duracionMin != null) "${duracion(t.duracionMin)} →" else "→",
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        ColHora(hora(t.llegadaDestino), t.estacionDestino, Modifier.weight(1f), TextAlign.End)
                    }

                    FlowRow(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (t.enVivo) Etiqueta("En vivo", ColorOk.copy(alpha = 0.12f), ColorOk, punto = true)
                        else Etiqueta("Programado", cs.secondaryContainer, cs.onSurfaceVariant)
                        val dm = t.demoraMin
                        if (dm != null && dm >= 2) {
                            if (dm >= 10) Etiqueta("Demora +$dm min", ColorGrave, Color.White)
                            else Etiqueta("Demora +$dm min", ColorDemora.copy(alpha = 0.13f), ColorDemora)
                        } else if (dm != null && t.enVivo) {
                            Etiqueta("A horario", cs.secondaryContainer, cs.onSurfaceVariant)
                        }
                        t.anden?.let { Etiqueta("Andén $it", cs.primaryContainer, cs.primary) }
                    }

                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonSecundario(if (avisoActivo) "Aviso activo" else "Avisame", activo = avisoActivo, onClick = onAvisar)
                        onAnden?.let { BotonSecundario("Modo andén", onClick = it) }
                        if (t.ubicacion != null) {
                            BotonSecundario(if (mapaAbierto) "Ocultar" else "Ver mapa") { mapaAbierto = !mapaAbierto }
                        }
                    }
                    if (mapaAbierto && t.ubicacion != null) {
                        MapaTren(
                            t, origen, destino, color, oscuro, ahora,
                            Modifier.padding(top = 12.dp).fillMaxWidth().height(280.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }
        if (proximo) {
            Text(
                "PRÓXIMO",
                Modifier.padding(start = 18.dp).clip(RoundedCornerShape(5.dp)).background(ColorAcento)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ColHora(h: String, est: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(h, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Mono)
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
