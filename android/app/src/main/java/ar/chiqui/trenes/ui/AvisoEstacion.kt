package ar.chiqui.trenes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.chiqui.trenes.AppVM

/**
 * Cartel flotante arriba al elegir una estación: el aviso más grave de sus
 * líneas, con la pastilla de la línea. Se va solo (el VM lo borra a los 5 s);
 * tocarlo abre el panel de avisos. Igual que la web.
 */
@Composable
fun CartelAvisoEstacion(cartel: AppVM.AvisoEstacion?, onAbrir: (String) -> Unit, onCerrar: () -> Unit) {
    // Guarda el último para que la animación de salida tenga qué mostrar.
    var ultimo by remember { mutableStateOf(cartel) }
    if (cartel != null) ultimo = cartel
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            cartel != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val c = ultimo ?: return@AnimatedVisibility
            val acento = colorNivel(c.aviso.nivel, cs.primary)
            Surface(
                Modifier.fillMaxWidth().clickable { onAbrir(c.linea) },
                shape = RoundedCornerShape(14.dp),
                color = cs.surface,
                shadowElevation = 10.dp,
            ) {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(acento))
                    Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            c.linea.uppercase(),
                            Modifier.background(colorLinea(c.linea) ?: cs.primary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        )
                        Text(c.aviso.texto, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                        if (c.total > 1) Text("+${c.total - 1} avisos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                    }
                    TextButton(onCerrar) { Text("✕", color = cs.onSurfaceVariant) }
                }
            }
        }
    }
}
