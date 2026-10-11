package ar.chiqui.trenes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.chiqui.trenes.R
import kotlinx.coroutines.delay

// Patrocinio en joda de "Sancord Seguros" + el Chiqui Tapia (mismo meme que
// patrocinador.html / js/v4/patrocinio.js). Se activa con el botón "?".

private val Sancord = Color(0xFF0A7D3B)
private val SancordOro = Color(0xFFFFD23F)
private val TintaMeme = Color(0xFF1B2230)

val SLOGANS = listOf(
    "Asegurá tu llegada. La del tren no, eso ni Dios.",
    "Cubrimos todo menos la demora del Roca.",
    "Si el tren no llega, al menos vos llegás a fin de mes. Bah, tampoco.",
    "Sancord Seguros: más puntuales que el Sarmiento (no es difícil).",
    "Póliza “Andén Total”: te cubre de pie, sentado y apretado.",
    "Ahora con cobertura contra el “se demora por causas ajenas”.",
    "Lo único que llega a horario es la cuota.",
    "¿Te pisaron en el Mitre? Llamá al 0800-SANCORD. No atiende nadie, pero llamá.",
    "Seguro de vida, auto, hogar y paciencia ferroviaria.",
    "Sancord Seguros. Porque “ya viene” no es un plan.",
)

fun mensajeDemora(m: Int) = listOf(
    "¿+$m min de demora? Sancord te cubre la paciencia hasta $0,00.",
    "$m minutos tarde. Con Sancord Seguros los perdés igual, pero asegurado.",
    "Demora de $m min detectada. Sancord ya está preparando el formulario 47-B.",
).random()

const val FRASE_TAPIA = "No trates de entenderla, disfrutala"

private val FRASES_TAPIA = listOf(
    FRASE_TAPIA,
    "¿El tren no llega? No trates de entenderlo, disfrutalo.",
    "Te estoy mirando. Subí al tren.",
    "Esta app no se explica, se siente.",
    "Andén 4. O el 5. No trates de entenderlo.",
    "El tren viene. Cuándo, no sé. Disfrutalo.",
    "Demora de 20 minutos: es parte del show.",
    "Si este tren llega a horario lo convoco a la Selección.",
)

val FESTEJOS = listOf("¡LLEGÓ! No trates de entenderlo.", "¡Tren en andén! Disfrutalo.", "¡Subí, subí, que se va!")

@Composable
private fun LogoSancor(alto: Dp) {
    Image(painterResource(R.drawable.sancor), "Patrocinador", Modifier.height(alto), contentScale = ContentScale.FillHeight)
}

@Composable
fun CaraTapia(lado: Dp, borde: Dp = 3.dp) {
    Image(
        painterResource(R.drawable.tapia), "Chiqui Tapia",
        Modifier.size(lado).clip(CircleShape).border(borde, Color.White, CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Etiquetita(texto: String) {
    Text(texto.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Sancord)
}

/** Banner de arriba: "Este horario te lo trae" + slogan que rota cada 9 s. */
@Composable
fun BannerSancord() {
    var slogan by remember { mutableStateOf(SLOGANS.random()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(9000)
            slogan = SLOGANS.random()
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(SancordOro, Color(0xFFFFF3B0))))
            .border(BorderStroke(3.dp, Sancord), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ESTE HORARIO TE LO TRAE", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TintaMeme, letterSpacing = 0.5.sp)
            LogoSancor(34.dp)
        }
        Text(slogan, Modifier.padding(top = 8.dp), fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, color = TintaMeme)
    }
}

/** Anuncio entre trenes, con botón de "cotizar" que no cotiza nada. */
@Composable
fun AnuncioSancord(texto: String) {
    var cta by remember { mutableStateOf("Cotizar ahora (no)") }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8D6)),
        border = BorderStroke(2.dp, Sancord),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiquetita("Publicidad")
            LogoSancor(30.dp)
            Text(texto, color = TintaMeme, fontWeight = FontWeight.Medium)
            Text(
                cta,
                Modifier.clip(RoundedCornerShape(8.dp)).background(Sancord)
                    .clickable { cta = listOf("Cotización: un ojo de la cara", "Un asesor te llama en 3 a 5 siglos", "Gracias por nada").random() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** "Mensaje institucional" del Chiqui, con fondo celeste y blanco. */
@Composable
fun TarjetaTapia() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .drawBehind {
                // Rayas celestes y blancas de la camiseta.
                val ancho = size.width / 11
                for (i in 0 until 11 step 2) drawRect(Color(0xFF75AADB), Offset(i * ancho, 0f), Size(ancho, size.height))
            }
            .border(2.dp, Sancord, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Text("MENSAJE INSTITUCIONAL", Modifier.align(Alignment.TopEnd), fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFF6B5B00))
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CaraTapia(96.dp, 4.dp)
            Text(
                "“$FRASE_TAPIA”",
                Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 8.dp),
                fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TintaMeme, textAlign = TextAlign.Center,
            )
            Text(
                "— el Chiqui, sobre los horarios del Roca",
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 6.dp),
                fontSize = 12.sp, color = TintaMeme,
            )
        }
    }
}

/** Cartel a pantalla completa: el botón de cerrar tarda en habilitarse. */
@Composable
fun Intersticial(segundos: Int, onCerrar: () -> Unit) {
    var n by remember { mutableIntStateOf(segundos) }
    var cta by remember { mutableStateOf("¡Quiero mi póliza!") }
    val slogan = remember { SLOGANS.random() }
    LaunchedEffect(Unit) {
        while (n > 0) {
            delay(1000)
            n--
        }
    }
    Dialog(onDismissRequest = { if (n == 0) onCerrar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(SancordOro, Color(0xFFFFF7C7))))
                .border(4.dp, Sancord, RoundedCornerShape(16.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Etiquetita("Publicidad")
            LogoSancor(44.dp)
            Text(slogan, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TintaMeme, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaraTapia(56.dp)
                Spacer(Modifier.width(10.dp))
                Text("“$FRASE_TAPIA”", fontStyle = FontStyle.Italic, color = TintaMeme)
            }
            Text(
                cta,
                Modifier.clip(RoundedCornerShape(8.dp)).background(Sancord)
                    .clickable { cta = listOf("Un asesor te llama en 3 a 5 siglos", "Póliza aprobada: cubre nada").random() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White, fontWeight = FontWeight.Bold,
            )
            Text(
                if (n > 0) "Saltar anuncio en $n" else "Cerrar ✕",
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = n == 0, onClick = onCerrar).padding(8.dp),
                color = if (n > 0) TintaMeme.copy(alpha = 0.5f) else TintaMeme,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Capas flotantes del meme: mascota que opina (y a veces es el Chiqui), el
 * Chiqui que se asoma por el costado, el festejo cuando llega un tren y el
 * banner de abajo con una "X" que no siempre cierra.
 */
@Composable
fun BoxScope.CapasMeme(festejo: String?, onFinFestejo: () -> Unit) {
    // Mascota
    var globo by remember { mutableStateOf<String?>(null) }
    var esTapia by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12000)
            esTapia = !esTapia
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(25000)
            globo = SLOGANS.random()
        }
    }
    LaunchedEffect(globo) {
        if (globo != null) {
            delay(4500)
            globo = null
        }
    }

    // Banner fijo de abajo
    var bannerVisible by remember { mutableStateOf(true) }
    var intentos by remember { mutableIntStateOf(0) }
    var textoBanner by remember { mutableStateOf(SLOGANS.random()) }
    LaunchedEffect(intentos, bannerVisible) {
        if (!bannerVisible) {
            delay(20000)
            intentos = 0
            textoBanner = "Volvimos. Te extrañábamos."
            bannerVisible = true
        } else {
            while (intentos == 0) {
                delay(7000)
                textoBanner = SLOGANS.random()
            }
        }
    }

    // El Chiqui se asoma desde un costado, dice algo y se va.
    var asoma by remember { mutableStateOf(false) }
    var asomaDerecha by remember { mutableStateOf(true) }
    var fraseAsoma by remember { mutableStateOf(FRASE_TAPIA) }
    LaunchedEffect(Unit) {
        delay(9000)
        while (true) {
            fraseAsoma = FRASES_TAPIA.random()
            asomaDerecha = Math.random() < 0.5
            asoma = true
            delay(5000)
            asoma = false
            delay(30000)
        }
    }

    val abajo = if (bannerVisible) 72.dp else 16.dp
    AnimatedVisibility(
        asoma,
        Modifier.align(if (asomaDerecha) Alignment.CenterEnd else Alignment.CenterStart),
        enter = slideInHorizontally { if (asomaDerecha) it else -it },
        exit = slideOutHorizontally { if (asomaDerecha) it else -it },
    ) {
        Column(
            Modifier.clickable { asoma = false }.padding(8.dp),
            horizontalAlignment = if (asomaDerecha) Alignment.End else Alignment.Start,
        ) {
            Text(
                fraseAsoma,
                Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(2.dp, Color(0xFF75AADB), RoundedCornerShape(12.dp)).padding(10.dp),
                color = TintaMeme, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            CaraTapia(84.dp)
        }
    }

    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = abajo),
        horizontalAlignment = Alignment.End,
    ) {
        globo?.let {
            Text(
                it,
                Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)).background(Color.White)
                    .border(2.dp, Sancord, RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)).padding(10.dp),
                color = TintaMeme, fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(SancordOro).border(3.dp, Sancord, CircleShape)
                .clickable { globo = SLOGANS.random() },
            contentAlignment = Alignment.Center,
        ) {
            if (esTapia) CaraTapia(56.dp, 0.dp) else Text("S", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Sancord)
        }
    }

    if (bannerVisible) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(SancordOro)
                .border(BorderStroke(3.dp, Sancord)).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val x = @Composable {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.12f)).clickable {
                        intentos++
                        if (intentos == 1) textoBanner = "Jaja no. Probá de nuevo." else bannerVisible = false
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = TintaMeme, fontSize = 13.sp) }
            }
            if (intentos == 1) x() // la X se cambia de lado
            LogoSancor(28.dp)
            Text(textoBanner, Modifier.weight(1f), color = TintaMeme, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (intentos != 1) x()
        }
    }

    // Festejo del Chiqui cuando un tren llega al andén.
    if (festejo != null) {
        LaunchedEffect(festejo) {
            delay(3000)
            onFinFestejo()
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onFinFestejo),
            contentAlignment = Alignment.Center,
        ) {
            FestejoGol()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextoGol()
                CaraTapia(150.dp, 5.dp)
                Text(
                    festejo,
                    Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).background(SancordOro).padding(12.dp),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TintaMeme, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun AvisoLegalMeme(color: Color) {
    Text(
        "Patrocinio de mentira, es un meme. Ni Sancor Seguros ni el Chiqui Tapia tienen nada que ver con esta app (ni nos pagaron un cospel).",
        fontSize = 12.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Foto de C. Tapia: Ministerio de Cultura de la Nación, CC BY-SA 2.0, vía Wikimedia Commons.",
        fontSize = 11.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
