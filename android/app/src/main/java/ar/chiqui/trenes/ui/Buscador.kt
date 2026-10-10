package ar.chiqui.trenes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.LINEAS_PRINCIPALES
import ar.chiqui.trenes.datos.Ruta
import ar.chiqui.trenes.datos.formatearDistancia

/** Qué muestra el buscador cuando no hay texto escrito. */
sealed interface Vista {
    data object Lineas : Vista
    data class Ramales(val linea: String) : Vista
    data class RamalesDe(val otra: Estacion) : Vista
    data class EnRuta(val ruta: Ruta) : Vista
    data class Cercanas(val items: List<Pair<Estacion, Double>>) : Vista
}

private fun vistaInicial(otra: Estacion?): Vista = when {
    otra == null -> Vista.Lineas
    otra.rutas.size == 1 -> Vista.EnRuta(otra.rutas[0])
    else -> Vista.RamalesDe(otra)
}

private fun ramalesOEstaciones(linea: String): Vista {
    val ramales = Estaciones.ramalesDeLinea(linea)
    return if (ramales.size == 1) Vista.EnRuta(Ruta(linea, ramales[0])) else Vista.Ramales(linea)
}

/**
 * Buscador de estaciones a pantalla completa: texto libre o navegación
 * línea → ramal → estaciones (en orden desde la cabecera). Si la otra estación
 * ya está elegida, arranca mostrando solo los ramales que comparte con ella.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Buscador(
    titulo: String,
    otra: Estacion?,
    inicial: Vista?,
    onElegir: (Estacion) -> Unit,
    onCerrar: () -> Unit,
    meme: Boolean = false,
) {
    var texto by remember { mutableStateOf("") }
    val pila = remember { mutableStateListOf(inicial ?: vistaInicial(otra)) }
    val foco = remember { FocusRequester() }
    val vista = pila.last()
    val ir = { v: Vista -> pila.add(v) }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (texto.isEmpty() && pila.size > 1) pila.removeAt(pila.lastIndex) else onCerrar()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.weight(1f).focusRequester(foco),
                        placeholder = { Text(titulo) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { Estaciones.buscar(texto).firstOrNull()?.let(onElegir) }),
                    )
                }

                if (texto.isNotBlank()) {
                    ListaEstaciones(Estaciones.buscar(texto).take(60).map { it to Estaciones.lineas(it) }, onElegir, Modifier.weight(1f))
                    return@Column
                }
                FilaNavegacion(
                    vista = vista,
                    puedeVolver = pila.size > 1,
                    onVolver = { pila.removeAt(pila.lastIndex) },
                    onRamal = { pila[pila.lastIndex] = Vista.EnRuta(it) },
                    onLineas = { ir(Vista.Lineas) },
                )
                when (vista) {
                    Vista.Lineas -> Chips(LINEAS_PRINCIPALES, { it }, { colorLinea(it) }) { ir(ramalesOEstaciones(it)) }
                    is Vista.Ramales -> Chips(Estaciones.ramalesDeLinea(vista.linea), { it }, { colorLinea(vista.linea) }) {
                        ir(Vista.EnRuta(Ruta(vista.linea, it)))
                    }
                    is Vista.RamalesDe -> Chips(vista.otra.rutas, { "${it.ramal} · ${it.linea}" }, { colorLinea(it.linea) }) {
                        ir(Vista.EnRuta(it))
                    }
                    is Vista.EnRuta -> {
                        val lista = Estaciones.deRuta(vista.ruta).filter { it.id != otra?.id }
                        if (meme && lista.size >= 4) ListaZonas(lista, onElegir, Modifier.weight(1f))
                        else ListaEstaciones(lista.map { it to "" }, onElegir, Modifier.weight(1f))
                    }
                    is Vista.Cercanas -> {
                        Text(
                            "Estaciones cerca tuyo",
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ListaEstaciones(vista.items.map { it.first to formatearDistancia(it.second) }, onElegir, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Fila fija arriba de la lista: "← Volver", los ramales de la línea actual
 * (para cambiar de ramal con un toque) y "Otras líneas".
 */
@Composable
private fun FilaNavegacion(vista: Vista, puedeVolver: Boolean, onVolver: () -> Unit, onRamal: (Ruta) -> Unit, onLineas: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ramales = (vista as? Vista.EnRuta)?.let { v -> Estaciones.ramalesDeLinea(v.ruta.linea).map { Ruta(v.ruta.linea, it) }.sortedByDescending { it == v.ruta } } ?: emptyList() // el elegido primero
    val mostrarLineas = vista is Vista.EnRuta || vista is Vista.RamalesDe || vista is Vista.Cercanas
    if (!puedeVolver && ramales.isEmpty() && !mostrarLineas) return
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (puedeVolver) item { FilterChip(false, onVolver, { Text("← Volver") }) }
        if (vista is Vista.EnRuta) {
            items(ramales) { r ->
                val elegido = r == vista.ruta
                FilterChip(
                    elegido, { onRamal(r) }, { Text(r.ramal) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(colorLinea(r.linea) ?: cs.primary, CircleShape)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primary, selectedLabelColor = cs.onPrimary),
                )
            }
        }
        if (mostrarLineas) item { FilterChip(false, onLineas, { Text("Otras líneas") }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(opciones: List<T>, etiqueta: (T) -> String, color: (T) -> Color?, alElegir: (T) -> Unit) {
    FlowRow(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { op ->
            AssistChip(
                onClick = { alElegir(op) },
                label = { Text(etiqueta(op)) },
                leadingIcon = color(op)?.let { c -> { Box(Modifier.size(10.dp).background(c, CircleShape)) } },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun FilaEstacion(e: Estacion, extra: String, onElegir: (Estacion) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onElegir(e) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(colorEstacion(e), CircleShape))
        Box(Modifier.width(12.dp))
        Text(e.nombre, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/**
 * Meme del torneo de 30 equipos en 2 zonas de 15: el ramal se parte en
 * "Zona A" y "Zona B" de n/2 estaciones, salteadas (no trates de entenderlo).
 * Si n es impar, la que sobra "asciende por escritorio".
 */
@Composable
private fun ListaZonas(lista: List<Estacion>, onElegir: (Estacion) -> Unit, modifier: Modifier) {
    val n = lista.size
    val mitad = n / 2
    val zonaA = lista.filterIndexed { i, _ -> i % 2 == 0 }.take(mitad)
    val zonaB = lista.filterIndexed { i, _ -> i % 2 == 1 }.take(mitad)
    val escritorio = (lista - zonaA.toSet() - zonaB.toSet()).firstOrNull()
    val oro = Color(0xFFFFD23F)
    val verde = Color(0xFF0A7D3B)
    @Composable
    fun Titulo(texto: String) {
        Text(
            texto, Modifier.fillMaxWidth().background(oro).padding(horizontal = 16.dp, vertical = 6.dp),
            color = verde, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 13.sp,
        )
    }
    LazyColumn(modifier) {
        item {
            Row(Modifier.fillMaxWidth().background(Color(0xFF75AADB)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CaraTapia(48.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Torneo Chiqui: $n estaciones, 2 zonas de $mitad." +
                        (if (escritorio != null) " ${escritorio.nombre} asciende por escritorio." else "") +
                        " No trates de entenderlo.",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
            }
        }
        item { Titulo("ZONA A · $mitad estaciones") }
        items(zonaA, key = { "a${it.id}" }) { FilaEstacion(it, "Zona A", onElegir) }
        item { Titulo("ZONA B · $mitad estaciones") }
        items(zonaB, key = { "b${it.id}" }) { FilaEstacion(it, "Zona B", onElegir) }
        if (escritorio != null) {
            item { Titulo("ASCENSO POR ESCRITORIO") }
            item { FilaEstacion(escritorio, "Invitada", onElegir) }
        }
    }
}

@Composable
private fun ListaEstaciones(items: List<Pair<Estacion, String>>, onElegir: (Estacion) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Text("No se encontraron estaciones", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(items, key = { _, it -> it.first.id }) { _, (e, extra) -> FilaEstacion(e, extra, onElegir) }
    }
}
