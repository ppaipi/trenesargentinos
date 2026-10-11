package ar.chiqui.trenes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.chiqui.trenes.AppVM
import ar.chiqui.trenes.Modo
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.Nivel
import ar.chiqui.trenes.datos.Viaje
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val sombraTitulo = TextStyle(shadow = Shadow(Color(0x40103060), Offset(0f, 2f), 8f))

/** Tira con los colores de las líneas, arriba de todo. */
@Composable
fun TiraLineas() {
    Row(Modifier.fillMaxWidth().height(5.dp)) {
        TIRA_LINEAS.forEach { Box(Modifier.weight(1f).height(5.dp).background(colorLinea(it) ?: Color.Gray)) }
    }
}

@Composable
private fun BotonRedondo(descripcion: String, fondo: Color, lado: Dp = 40.dp, onClick: () -> Unit, contenido: @Composable () -> Unit) {
    Box(
        Modifier.size(lado).clip(CircleShape).background(fondo).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

// Luna del botón de tema (como el ícono de la web).
@Composable
private fun Luna(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2.2f
        val luna = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        val corte = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.55f, center.y - r * 0.45f), r * 0.85f)) }
        drawPath(Path.combine(PathOperation.Difference, luna, corte), color)
    }
}

/** Cabecera centrada: campana a la izquierda, "?" (meme) y tema a la derecha. */
@Composable
fun Cabecera(vm: AppVM) {
    val translucido = Color.White.copy(alpha = 0.22f)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(88.dp)) {
                val total = vm.grupos.filter { lineaConocida(it.linea) }.sumOf { it.avisos.size }
                BotonRedondo("Avisos del servicio", translucido, onClick = { vm.abrirAvisos() }) {
                    Icon(Icons.Outlined.Notifications, "Avisos del servicio", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                if (total > 0) {
                    val grave = vm.grupos.any { g -> g.avisos.any { it.nivel == Nivel.GRAVE } }
                    Text(
                        if (total > 9) "9+" else "$total",
                        Modifier.padding(start = 26.dp).clip(CircleShape).background(if (grave) ColorGrave else ColorAcento)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Box(Modifier.weight(1f).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                Wordmark(Modifier.clickable(interactionSource = null, indication = null, onClick = vm::toqueLogo))
            }
            Row(Modifier.width(88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BotonRedondo("Acerca de TrenAR", translucido, onClick = { vm.panelAcerca = true }) {
                    Text("?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                BotonRedondo("Tema", translucido, onClick = vm::alternarTema) { Luna(Color.White) }
            }
        }
        Text(
            "¿Cuándo llega tu tren?",
            Modifier.padding(top = 10.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = sombraTitulo,
        )
        Text(
            "Horarios en vivo, demoras y andén de todas las líneas del AMBA.",
            Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            style = sombraTitulo,
        )
    }
}

/** Marca: logo + "Tren" + pastilla "AR". */
@Composable
fun Wordmark(modifier: Modifier = Modifier, tamano: Float = 34f, claro: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(ar.chiqui.trenes.R.drawable.logo_trenar), null,
            Modifier.size((tamano * 1.25f).dp).shadow(4.dp, RoundedCornerShape((tamano * 0.3f).dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Tren",
            color = if (claro) Color.White else MaterialTheme.colorScheme.primary,
            fontSize = tamano.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            style = if (claro) sombraTitulo else TextStyle.Default,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "AR",
            Modifier.clip(RoundedCornerShape(8.dp)).background(if (claro) Color.White else Color(0xFF74ACDF))
                .padding(horizontal = 7.dp),
            color = Color(0xFF0B2547),
            fontSize = (tamano * 0.8f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun TituloSeccion(texto: String, color: Color, modifier: Modifier = Modifier) {
    Text(texto.uppercase(), modifier, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

/** "Tus viajes": favoritos y últimos viajes como accesos rápidos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Guardados(vm: AppVM) {
    val esFav = { v: Viaje -> vm.favoritos.any { it.o == v.o && it.d == v.d } }
    val recs = vm.recientes.filterNot(esFav).take(3)
    if (vm.favoritos.isEmpty() && recs.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloSeccion("Tus viajes", sobreFondo(vm.oscuro))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (vm.favoritos.map { it to true } + recs.map { it to false }).forEach { (v, fav) ->
                val o = Estaciones.porId(v.o) ?: return@forEach
                val d = Estaciones.porId(v.d) ?: return@forEach
                val color = colorLinea(Estaciones.rutaCompartida(o, d)?.linea) ?: cs.primary
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surface).clickable { vm.abrirGuardado(v) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(40.dp).background(color))
                    Text(if (fav) "★" else "Últ.", Modifier.padding(start = 10.dp), color = if (fav) Color(0xFFF5A300) else cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${o.nombre} → ${d.nombre}", Modifier.padding(horizontal = 10.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (fav) Text("✕", Modifier.clickable { vm.quitarFavorito(v) }.padding(end = 10.dp), color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, Modifier.padding(bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Campo(valor: String?, placeholder: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant)
            .border(1.dp, cs.outline, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            valor ?: placeholder,
            color = if (valor != null) cs.onSurface else cs.onSurfaceVariant,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModoBoton(texto: String, activo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (activo) cs.primary else cs.primaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) cs.onPrimary else cs.primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** Tarjeta de búsqueda: modo, Desde (con botón de ubicación), ⇅, Hasta, fecha/hora y Buscar. */
@Composable
fun Formulario(vm: AppVM, buscandoUbicacion: Boolean, onOrigen: () -> Unit, onDestino: () -> Unit, onCercana: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(cs.surface).padding(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModoBoton("En vivo", vm.modo == Modo.VIVO, Modifier.weight(1f)) { vm.cambiarModo(Modo.VIVO) }
            ModoBoton("Programar viaje", vm.modo == Modo.PROGRAMADA, Modifier.weight(1f)) { vm.cambiarModo(Modo.PROGRAMADA) }
        }
        Spacer(Modifier.height(18.dp))
        Etiqueta("Desde")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Campo(vm.origen?.nombre, "Estación de origen", Modifier.weight(1f), onOrigen)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer).clickable(enabled = !buscandoUbicacion, onClick = onCercana),
                contentAlignment = Alignment.Center,
            ) {
                if (buscandoUbicacion) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.LocationOn, "Usar la estación más cercana", tint = cs.primary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            BotonRedondo("Intercambiar", cs.secondaryContainer, onClick = vm::intercambiar) {
                Text("⇅", color = cs.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Etiqueta("Hasta")
        Campo(vm.destino?.nombre, "Estación de destino", Modifier.fillMaxWidth(), onDestino)

        if (vm.modo == Modo.PROGRAMADA) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Etiqueta("Fecha")
                    Campo(vm.fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "", Modifier.fillMaxWidth()) {
                        DatePickerDialog(ctx, { _, a, m, d -> vm.fecha = LocalDate.of(a, m + 1, d) },
                            vm.fecha.year, vm.fecha.monthValue - 1, vm.fecha.dayOfMonth).show()
                    }
                }
                Column(Modifier.weight(1f)) {
                    Etiqueta("Hora")
                    Campo(vm.horaProg.format(DateTimeFormatter.ofPattern("HH:mm")), "", Modifier.fillMaxWidth()) {
                        TimePickerDialog(ctx, { _, h, m -> vm.horaProg = LocalTime.of(h, m) }, vm.horaProg.hour, vm.horaProg.minute, true).show()
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        val habilitado = vm.origen != null && vm.destino != null
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp))
                .background(if (habilitado) cs.primary else cs.outline)
                .clickable(enabled = habilitado) { vm.buscar() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (vm.modo == Modo.VIVO) "Buscar horarios" else "Ver horarios programados",
                color = if (habilitado) cs.onPrimary else cs.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

/** Trayecto, estado de actualización, guardar y compartir. */
@Composable
fun CabeceraResultado(vm: AppVM) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val fav = vm.origen?.let { o -> vm.destino?.let { d -> vm.favoritos.any { it.o == o.id && it.d == d.id } } } ?: false
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(vm.trayecto, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = sobreFondo(vm.oscuro))
            Row {
                Text(vm.estadoTexto, fontSize = 13.sp, color = sobreFondoSuave(vm.oscuro))
            }
            if (vm.meme && vm.modo == Modo.VIVO) {
                Text("Monitoreado por Sancord Seguros™", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15B35A))
            }
        }
        BotonRedondo("Guardar viaje", cs.surface, onClick = vm::alternarFavorito) {
            Text(if (fav) "★" else "☆", color = if (fav) Color(0xFFF5A300) else cs.primary, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        BotonRedondo("Compartir", cs.surface, onClick = { vm.intentCompartir()?.let(ctx::startActivity) }) {
            Icon(Icons.Outlined.Share, "Compartir", tint = cs.primary, modifier = Modifier.size(20.dp))
        }
    }
}
