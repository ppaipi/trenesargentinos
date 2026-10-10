package ar.chiqui.trenes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.ZONA
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

val ColorOk = Color(0xFF1E7F3C)
val ColorDemora = Color(0xFFB45309)
val ColorGrave = Color(0xFFB91C1C)
val ColorAcento = Color(0xFFD4421E)

private val COLOR_LINEA = mapOf(
    "Mitre" to Color(0xFF2EA0DA),
    "Sarmiento" to Color(0xFF1F4E9C),
    "Roca" to Color(0xFF00A99D),
    "San Martín" to Color(0xFF43A047),
    "Belgrano Sur" to Color(0xFF8E24AA),
    "Tren de la Costa" to Color(0xFFFB8C00),
    "Belgrano Norte" to Color(0xFF6B8F00),
    "Regionales" to Color(0xFF757575),
    "Trenes de Terceros" to Color(0xFF6D4C41),
)

fun colorLinea(linea: String?): Color? = linea?.let { COLOR_LINEA[it] }

fun colorEstacion(e: Estacion): Color = colorLinea(e.rutas.firstOrNull()?.linea) ?: Color.Gray

fun lineaConocida(linea: String) = linea in COLOR_LINEA

private val claro = lightColorScheme(
    primary = Color(0xFF0056B3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F1FC),
    onPrimaryContainer = Color(0xFF0056B3),
    surface = Color.White,
    onSurface = Color(0xFF2B2F38),
    surfaceVariant = Color(0xFFF6F8FC),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFE1E6EF),
    background = Color(0xFF66A6FF),
)

private val oscuroEsquema = darkColorScheme(
    primary = Color(0xFF5B9BFF),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1F2B40),
    onPrimaryContainer = Color(0xFF9CC2FF),
    surface = Color(0xFF1A1E25),
    onSurface = Color(0xFFE8EBF1),
    surfaceVariant = Color(0xFF222731),
    onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF2D3440),
    background = Color(0xFF101216),
)

// Fondo celeste como en la web (oscuro: azul noche).
fun fondo(oscuro: Boolean) = if (oscuro) {
    Brush.linearGradient(listOf(Color(0xFF0F1A2E), Color(0xFF101216)))
} else {
    Brush.linearGradient(listOf(Color(0xFF89F7FE), Color(0xFF66A6FF)))
}

fun sobreFondo(oscuro: Boolean) = if (oscuro) Color(0xFFE8EBF1) else Color(0xFF0B2547)

@Composable
fun TemaTrenes(oscuro: Boolean, contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) oscuroEsquema else claro, content = contenido)
}

/* ---------- Formatos ---------- */

private val fmtHora = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA)
private val fmtDia = DateTimeFormatter.ofPattern("EEE", Locale("es", "AR")).withZone(ZONA)

fun hora(ms: Long?): String = ms?.let { fmtHora.format(Instant.ofEpochMilli(it)) } ?: "--:--"

fun duracion(min: Int?): String = when {
    min == null || min < 0 -> ""
    min < 60 -> "$min min"
    else -> "${min / 60} h ${(min % 60).toString().padStart(2, '0')}"
}

class Cuenta(val num: String, val uni: String, val ya: Boolean = false)

// Cuenta regresiva con segundos siempre: MM:SS, o H:MM:SS si falta más de 1 h.
fun cuentaRegresiva(referencia: Long, ahora: Long): Cuenta {
    val seg = Math.floorDiv(referencia - ahora, 1000L)
    if (seg < -120) return Cuenta(hora(referencia), "ya pasó")
    if (seg <= 0) return Cuenta("YA", "en andén", ya = true)
    val dos = { n: Long -> n.toString().padStart(2, '0') }
    val h = seg / 3600
    val m = (seg % 3600) / 60
    val s = seg % 60
    if (h >= 24) return Cuenta(hora(referencia), fmtDia.format(Instant.ofEpochMilli(referencia)))
    if (h > 0) return Cuenta("$h:${dos(m)}:${dos(s)}", "h  min  seg")
    return Cuenta("${dos(m)}:${dos(s)}", "min  seg")
}
