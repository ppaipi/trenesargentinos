package ar.chiqui.trenes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.chiqui.trenes.MainActivity
import ar.chiqui.trenes.datos.Almacen
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.Viaje

private val Celeste = Color(0xFF66A6FF)
private val Tinta = Color(0xFF0A2540)

/** Elegir qué viaje favorito (o reciente) muestra el widget. */
class ConfigWidget : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        Estaciones.cargar(this)
        val almacen = Almacen(this)
        val favs = almacen.favoritos()
        val opciones = (favs + almacen.recientes().filterNot { r -> favs.any { it.o == r.o && it.d == r.d } })
            .filter { Estaciones.porId(it.o) != null && Estaciones.porId(it.d) != null }

        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Celeste).safeDrawingPadding().padding(16.dp)) {
                    Text("Widget: elegí un viaje", color = Tinta, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (opciones.isEmpty()) {
                        Text(
                            "Todavía no tenés favoritos. Buscá un viaje en TrenAR, marcalo con la estrella y volvé a agregar el widget.",
                            color = Tinta, modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Button(onClick = {
                            startActivity(Intent(this@ConfigWidget, MainActivity::class.java))
                            finish()
                        }) { Text("Abrir TrenAR") }
                    } else {
                        LazyColumn(Modifier.padding(top = 12.dp)) {
                            items(opciones) { v ->
                                val fav = favs.contains(v)
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .clickable { elegir(id, v) }
                                        .padding(14.dp),
                                ) {
                                    Text(
                                        "${Estaciones.porId(v.o)!!.nombre} → ${Estaciones.porId(v.d)!!.nombre}",
                                        color = Tinta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                    )
                                    Text(if (fav) "Favorito" else "Reciente", color = Tinta.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun elegir(id: Int, v: Viaje) {
        guardar(this, id, v)
        WidgetTren.pedirActualizacion(this)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }

    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun guardar(ctx: Context, id: Int, v: Viaje) = prefs(ctx).edit().putString("w$id", "${v.o}-${v.d}").apply()

        fun viaje(ctx: Context, id: Int): Viaje? {
            val partes = prefs(ctx).getString("w$id", null)?.split('-') ?: return null
            val o = partes.getOrNull(0)?.toIntOrNull() ?: return null
            val d = partes.getOrNull(1)?.toIntOrNull() ?: return null
            return Viaje(o, d)
        }

        fun borrar(ctx: Context, id: Int) = prefs(ctx).edit().remove("w$id").apply()
    }
}
