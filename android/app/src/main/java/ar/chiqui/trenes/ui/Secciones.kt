package ar.chiqui.trenes.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.chiqui.trenes.datos.Aviso
import ar.chiqui.trenes.datos.DIAS
import ar.chiqui.trenes.datos.GrupoAvisos
import ar.chiqui.trenes.datos.LINEAS_PROGRAMADAS
import ar.chiqui.trenes.datos.LineaProgramada
import ar.chiqui.trenes.datos.Nivel

internal fun colorNivel(n: Nivel, primario: Color) = when (n) {
    Nivel.GRAVE -> ColorGrave
    Nivel.MEDIO -> ColorDemora
    Nivel.INFO -> primario
}

@Composable
fun ItemAviso(a: Aviso, linea: String?) {
    val c = colorNivel(a.nivel, MaterialTheme.colorScheme.primary)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.1f)).padding(10.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            linea?.let {
                Text(it, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = colorLinea(it) ?: c)
            }
            Text(a.texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Panel de avisos del servicio (campana), con la línea del viaje primero. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelAvisos(grupos: List<GrupoAvisos>, lineaPrimero: String?, onCerrar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Avisos del servicio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val conAvisos = grupos.filter { lineaConocida(it.linea) && it.avisos.isNotEmpty() }
                .sortedByDescending { it.linea == lineaPrimero }
            if (conAvisos.isEmpty()) {
                Text(
                    if (grupos.isEmpty()) "Cargando avisos…" else "Sin avisos: todas las líneas funcionan con normalidad.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            conAvisos.forEach { g -> g.avisos.forEach { ItemAviso(it, g.linea) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "Acerca de": qué es TrenAR y de dónde salen los datos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelAcerca(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Wordmark(tamano = 30f, claro = false)
            Text("Tu tren, en vivo.", fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
            Text(
                "Arribos en tiempo real, demoras, andén y avisos del servicio de las líneas de Trenes Argentinos " +
                    "(Mitre, Sarmiento, Roca, San Martín, Belgrano Sur y Tren de la Costa).",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "Los datos son de Trenes Argentinos (SOFSE). TrenAR es una app no oficial y no está asociada a ninguna empresa ferroviaria.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Text(
                "tapia.chiqui.ar",
                Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tapia.chiqui.ar/"))) },
                color = cs.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Versión $version", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** "Otras líneas (horario programado)": plegada por defecto, como en v5. */
@Composable
fun SeccionProgramados(tinta: Color) {
    var abierta by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloSeccion(
            "Otras líneas (horario programado) ${if (abierta) "▾" else "▸"}",
            tinta,
            Modifier.clickable { abierta = !abierta }.padding(vertical = 6.dp),
        )
        if (abierta) {
            Text(
                "No las opera Trenes Argentinos: no hay arribos en vivo, solo horario oficial.",
                style = MaterialTheme.typography.bodySmall,
                color = tinta,
            )
            LINEAS_PROGRAMADAS.forEach { TarjetaProgramada(it) }
        }
    }
}

@Composable
private fun TarjetaProgramada(ln: LineaProgramada) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val color = Color(ln.color)
    Card(colors = CardDefaults.cardColors(containerColor = cs.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Text(ln.linea, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(ln.operador, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Etiqueta("programado · no en vivo", ColorDemora.copy(alpha = 0.15f), ColorDemora)
            Text("${ln.cabeceras.joinToString(" ↔ ")} · ${ln.trayectoMin} min de punta a punta", style = MaterialTheme.typography.bodyMedium)
            DIAS.forEach { (clave, nombre) ->
                val horas = ln.horarios[clave] ?: return@forEach
                val detalle = horas.entries.joinToString(" · ") { (cab, h) -> "$cab: ${h.first}–${h.second}" } +
                    (ln.frecuenciaMin[clave]?.let { " · cada $it min" } ?: "")
                Column {
                    Text(nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(detalle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Text(
                "Horario oficial de ${ln.fuenteNombre} · vigente desde ${ln.vigencia}. Los horarios programados no confirman circulación.",
                Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ln.fuenteUrl))) },
                style = MaterialTheme.typography.bodySmall,
                color = cs.primary,
            )
        }
    }
}
