package ar.chiqui.trenes.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.chiqui.trenes.datos.Tren
import kotlinx.coroutines.delay
import kotlin.random.Random

// Chicanas futboleras del modo meme (mismas ideas que la web): tarjetas por
// demora, VAR mientras carga, "postergado" si el tren seguido se esfuma y
// festejo de gol cuando llega.

private val Amarilla = Color(0xFFFFD400)
private val Roja = Color(0xFFD7141A)

/** false si el usuario apagó las animaciones del sistema. */
@Composable
fun animacionesActivas(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** Tarjeta amarilla (demora >= 5) o roja con sello "EXPULSADO" (>= 15). */
@Composable
fun BoxScope.TarjetaDisciplina(demoraMin: Int?, arriba: Boolean) {
    val dm = demoraMin ?: return
    if (dm < 5) return
    val roja = dm >= 15
    Box(
        Modifier.align(Alignment.TopEnd).padding(top = if (arriba) 8.dp else 0.dp).offset(x = 4.dp, y = (-6).dp)
            .rotate(14f).size(20.dp, 28.dp).clip(RoundedCornerShape(3.dp))
            .background(if (roja) Roja else Amarilla).border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
    )
    if (roja) {
        Text(
            "EXPULSADO",
            Modifier.align(Alignment.Center).rotate(-12f).border(3.dp, Roja.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            color = Roja.copy(alpha = 0.85f), fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
        )
    }
}

/** Pantalla tipo VAR en lugar del esqueleto mientras se consultan arribos. */
@Composable
fun PantallaVar() {
    val anim = animacionesActivas()
    val barrido = if (anim) {
        rememberInfiniteTransition(label = "var").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "barrido",
        ).value
    } else 0.5f
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0F14))
            .drawBehind {
                val x = size.width * barrido
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0x5574ACDF), Color.Transparent), x - 60f, x + 60f),
                    topLeft = Offset(x - 60f, 0f), size = Size(120f, size.height),
                )
                drawLine(Color(0xFF74ACDF), Offset(x, 0f), Offset(x, size.height), 2f)
            }
            .padding(14.dp),
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp)))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "VAR", Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color(0xFF0B0F14), fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp,
                )
                Text("Revisando posible demora", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                "La jugada se revisa. El tren, también.",
                Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
            )
        }
    }
}

/** Sello rojo "POSTERGADO" cuando un tren seguido deja de aparecer. */
@Composable
fun SelloPostergado(t: Tren, onFin: () -> Unit) {
    val anim = animacionesActivas()
    val escala = remember { Animatable(if (anim) 3f else 1f) }
    LaunchedEffect(t.clave) {
        if (anim) escala.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        delay(3500)
        onFin()
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onFin),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "POSTERGADO",
                Modifier.scale(escala.value).rotate(-14f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.92f))
                    .border(5.dp, Roja, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                color = Roja, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
            )
            Text(
                "Cancelado no, postergado.\n${t.ramal} · ${hora(t.referencia)}",
                Modifier.padding(top = 26.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp),
                color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
    }
}

private class Papelito(val x: Float, val y0: Float, val vel: Float, val giro: Float, val color: Color, val ancho: Float)

private val COLORES_PAPELITOS = listOf(Color(0xFF74ACDF), Color.White, Color(0xFFFFD23F), Color(0xFF74ACDF), Color.White)

/** "¡GOOOL!" con papelitos (~3 s); lo cierra el festejo que lo contiene. */
@Composable
fun FestejoGol() {
    val anim = animacionesActivas()
    val papelitos = remember {
        List(90) {
            Papelito(
                Random.nextFloat(), Random.nextFloat() * -0.6f, 0.7f + Random.nextFloat() * 0.8f,
                Random.nextFloat() * 720f - 360f, COLORES_PAPELITOS.random(), 6f + Random.nextFloat() * 10f,
            )
        }
    }
    val t = remember { Animatable(if (anim) 0f else 0.6f) }
    LaunchedEffect(Unit) { if (anim) t.animateTo(1f, tween(3000, easing = LinearEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        for (q in papelitos) {
            val y = (q.y0 + q.vel * p * 1.4f) * size.height
            if (y < -20f || y > size.height + 20f) continue
            val x = q.x * size.width
            rotate(q.giro * p, Offset(x, y)) {
                drawRect(q.color, Offset(x - q.ancho / 2, y - q.ancho / 4), Size(q.ancho, q.ancho / 2))
            }
        }
    }
}

@Composable
fun TextoGol() {
    Text(
        "¡GOOOL!",
        Modifier.rotate(-6f).padding(bottom = 10.dp),
        color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF0B3D91), Offset(4f, 4f), 6f)),
    )
}
