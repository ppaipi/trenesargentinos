package ar.chiqui.trenes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import ar.chiqui.trenes.MainActivity
import ar.chiqui.trenes.R
import ar.chiqui.trenes.datos.Almacen
import ar.chiqui.trenes.datos.Api
import ar.chiqui.trenes.datos.Estaciones
import ar.chiqui.trenes.datos.Tren
import ar.chiqui.trenes.datos.Viaje
import ar.chiqui.trenes.ui.hora
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** "en 4 min" / "ahora" / null si ya pasó (lógica pura, probada en LogicaTest). */
fun faltanTexto(referencia: Long, ahora: Long): String? {
    val seg = Math.floorDiv(referencia - ahora, 1000L)
    return when {
        seg < -60 -> null
        seg < 60 -> "ahora"
        seg < 3600 -> "en ${seg / 60} min"
        else -> "en ${seg / 3600} h ${(seg % 3600 / 60).toString().padStart(2, '0')}"
    }
}

enum class Tamano { CHICO, MEDIANO, GRANDE }

/** Qué entra según el alto en dp: GRANDE todo, MEDIANO sin pastilla ni estado, CHICO una fila. */
fun tamano(altoDp: Int): Tamano = when {
    altoDp <= 0 || altoDp >= 120 -> Tamano.GRANDE
    altoDp >= 85 -> Tamano.MEDIANO
    else -> Tamano.CHICO
}

/** Cuándo volver a dibujar: al minuto, o apenas sale el próximo tren (nunca antes de 15 s). */
fun proximoTick(trenes: List<Tren>, ahora: Long): Long {
    val sale = trenes.map { it.referencia + 5000 }.filter { it > ahora }.minOrNull() ?: Long.MAX_VALUE
    return minOf(ahora + 60000, sale).coerceAtLeast(ahora + 15000)
}

/**
 * Widget de pantalla de inicio: próximo tren (con cuenta regresiva en vivo en Android 7+)
 * y el siguiente de un viaje favorito. RemoteViews clásico, sin dependencias nuevas.
 * Se redibuja cada minuto mientras la pantalla está prendida (alarma no despertadora) y
 * consulta la API si el último resultado tiene más de 5 min; tocarlo abre el viaje en la app.
 */
class WidgetTren : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = trabajar(ctx, red = true)

    // Lo achicaron o agrandaron: se redibuja con lo que entra.
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opciones: android.os.Bundle) =
        trabajar(ctx, red = false)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACCION_REFRESCAR -> trabajar(ctx, red = true)
            ACCION_TICK -> trabajar(ctx, red = false)
            ACCION_INVERTIR -> {
                // Ida <-> vuelta: el mismo widget pasa a mostrar el viaje opuesto.
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                ConfigWidget.viaje(ctx, id)?.let { ConfigWidget.guardar(ctx, id, Viaje(it.d, it.o)) }
                trabajar(ctx, red = false)
            }
            ACCION_PROXIMO -> {
                // Siguiente favorito (o reciente), en ronda; el viaje invertido cuenta como el mismo.
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                Estaciones.cargar(ctx)
                val lista = ConfigWidget.opciones(ctx)
                val actual = ConfigWidget.viaje(ctx, id)
                val i = lista.indexOfFirst { actual != null && setOf(it.o, it.d) == setOf(actual.o, actual.d) }
                lista.getOrNull((i + 1) % lista.size.coerceAtLeast(1))?.let { ConfigWidget.guardar(ctx, id, it) }
                trabajar(ctx, red = false)
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { ConfigWidget.borrar(ctx, it) }

    // Restauración de backup / cambio de launcher: los ids cambian, se mudan los viajes.
    override fun onRestored(ctx: Context, oldIds: IntArray, newIds: IntArray) {
        val viajes = oldIds.map { ConfigWidget.viaje(ctx, it) }
        oldIds.forEach { ConfigWidget.borrar(ctx, it) }
        newIds.forEachIndexed { i, id -> viajes.getOrNull(i)?.let { ConfigWidget.guardar(ctx, id, it) } }
    }

    override fun onDisabled(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intentTick(ctx))
    }

    private fun trabajar(ctx: Context, red: Boolean) {
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9000) { actualizarTodos(ctx.applicationContext, red) }
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        const val ACCION_REFRESCAR = "ar.chiqui.trenes.widget.REFRESCAR"
        private const val ACCION_TICK = "ar.chiqui.trenes.widget.TICK"
        private const val ACCION_INVERTIR = "ar.chiqui.trenes.widget.INVERTIR"
        private const val ACCION_PROXIMO = "ar.chiqui.trenes.widget.PROXIMO"
        private const val VIEJO_MS = 5 * 60000L

        private fun ids(ctx: Context) =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WidgetTren::class.java))

        private fun intentTick(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, WidgetTren::class.java).setAction(ACCION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Pide a todos los widgets que se actualicen (por ejemplo, recién configurado). */
        fun pedirActualizacion(ctx: Context) {
            ctx.sendBroadcast(Intent(ctx, WidgetTren::class.java).setAction(ACCION_REFRESCAR))
        }

        suspend fun actualizarTodos(ctx: Context, red: Boolean) {
            val ids = ids(ctx)
            if (ids.isEmpty()) return
            Estaciones.cargar(ctx)
            val almacen = Almacen(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val porViaje = ids.groupBy { ConfigWidget.viaje(ctx, it) }
            val ahora0 = System.currentTimeMillis()
            var tick = ahora0 + 60000
            for ((v, grupo) in porViaje) {
                if (v == null) {
                    grupo.forEach { mgr.updateAppWidget(it, sinConfigurar(ctx, it)) }
                    continue
                }
                // Primero lo guardado (instantáneo), después la API si hace falta.
                var guardado = almacen.leerResultado(v)
                grupo.forEach { mgr.updateAppWidget(it, vista(ctx, it, v, guardado, cargando = red)) }
                var error = false
                if (red || guardado == null || ahora0 - guardado.first > VIEJO_MS || guardado.second.size < 2) {
                    val o = Estaciones.porId(v.o)
                    val d = Estaciones.porId(v.d)
                    if (o != null && d != null) {
                        val lista = runCatching { Api.proximos(o, d, 8) }.getOrNull()
                        if (lista != null) {
                            almacen.guardarResultado(v, lista)
                            guardado = System.currentTimeMillis() to lista
                        } else {
                            error = true
                        }
                    }
                }
                grupo.forEach { mgr.updateAppWidget(it, vista(ctx, it, v, guardado, error = error)) }
                tick = minOf(tick, proximoTick(guardado?.second.orEmpty(), System.currentTimeMillis()))
            }
            // RTC sin despertar: con la pantalla apagada no consume, se dispara al prenderla.
            ctx.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, tick, intentTick(ctx))
        }

        private fun abrirApp(ctx: Context, id: Int, v: Viaje): PendingIntent {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://tapia.chiqui.ar/?o=${v.o}&d=${v.d}"), ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun refrescar(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, WidgetTren::class.java).setAction(ACCION_REFRESCAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun accionWidget(ctx: Context, accion: String, codigo: Int, id: Int) = PendingIntent.getBroadcast(
            ctx, codigo,
            Intent(ctx, WidgetTren::class.java).setAction(accion).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Abre el selector de viaje para este widget (tocar el trayecto). */
        private fun elegirViaje(ctx: Context, id: Int): PendingIntent {
            val conf = Intent(ctx, ConfigWidget::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("trenar://widget/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(ctx, id, conf, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun sinConfigurar(ctx: Context, id: Int) = RemoteViews(ctx.packageName, R.layout.widget_tren).apply {
            setTextViewText(R.id.w_trayecto, "TrenAR")
            setTextViewText(R.id.w_faltan, "Elegí un viaje")
            setViewVisibility(R.id.w_faltan, View.VISIBLE)
            setViewVisibility(R.id.w_cuenta, View.GONE)
            setTextViewText(R.id.w_hora, "Tu próximo tren, en vivo")
            setTextViewText(R.id.w_siguiente, "Tocá para elegir un favorito")
            setTextViewText(R.id.w_estado, "")
            setViewVisibility(R.id.w_invertir, View.GONE)
            setViewVisibility(R.id.w_proximo, View.GONE)
            setOnClickPendingIntent(R.id.w_raiz, elegirViaje(ctx, id))
            setOnClickPendingIntent(R.id.w_refrescar, refrescar(ctx))
        }

        /** Alto actual del widget en dp (en vertical el launcher informa el máximo). */
        private fun altoDp(ctx: Context, id: Int): Int {
            val op = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
            val vertical = ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            return op.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        }

        private fun vista(
            ctx: Context, id: Int, v: Viaje, guardado: Pair<Long, List<Tren>>?,
            cargando: Boolean = false, error: Boolean = false,
        ) = RemoteViews(ctx.packageName, R.layout.widget_tren).apply {
            val t = tamano(altoDp(ctx, id))
            val o = Estaciones.porId(v.o)?.nombre ?: "?"
            val d = Estaciones.porId(v.d)?.nombre ?: "?"
            setTextViewText(R.id.w_trayecto, "$o → $d")
            setOnClickPendingIntent(R.id.w_raiz, abrirApp(ctx, id, v))
            setOnClickPendingIntent(R.id.w_trayecto, elegirViaje(ctx, id))
            setOnClickPendingIntent(R.id.w_invertir, accionWidget(ctx, ACCION_INVERTIR, 1_000_000 + id, id))
            setOnClickPendingIntent(R.id.w_proximo, accionWidget(ctx, ACCION_PROXIMO, 2_000_000 + id, id))
            setOnClickPendingIntent(R.id.w_refrescar, refrescar(ctx))

            val ahora = System.currentTimeMillis()
            val trenes = guardado?.second.orEmpty().filter { faltanTexto(it.referencia, ahora) != null }
            val primero = trenes.getOrNull(0)
            val segundo = trenes.getOrNull(1)
            if (primero == null) {
                setTextViewText(R.id.w_faltan, "--:--")
                setViewVisibility(R.id.w_faltan, View.VISIBLE)
                setViewVisibility(R.id.w_cuenta, View.GONE)
                setTextViewText(R.id.w_hora, if (cargando) "Buscando trenes…" else "Sin próximos trenes")
                setTextViewText(R.id.w_siguiente, "Tocá para ver el viaje")
            } else {
                val sale = hora(primero.referencia)
                setTextViewText(
                    R.id.w_hora,
                    when (t) {
                        Tamano.GRANDE -> "Sale a las $sale" + (primero.anden?.let { " · andén $it" } ?: "")
                        Tamano.MEDIANO -> "Sale $sale" + (segundo?.let { " · después ${hora(it.referencia)}" } ?: "")
                        Tamano.CHICO -> "$o → $d · $sale"
                    },
                )
                val falta = primero.referencia - ahora
                if (Build.VERSION.SDK_INT >= 24 && falta > 0 && falta < 3600000) {
                    // Cuenta regresiva en vivo con segundos, la corre el launcher.
                    setChronometer(R.id.w_cuenta, SystemClock.elapsedRealtime() + falta, null, true)
                    setChronometerCountDown(R.id.w_cuenta, true)
                    setViewVisibility(R.id.w_cuenta, View.VISIBLE)
                    setViewVisibility(R.id.w_faltan, View.GONE)
                } else {
                    setViewVisibility(R.id.w_cuenta, View.GONE)
                    setViewVisibility(R.id.w_faltan, View.VISIBLE)
                    setTextViewText(R.id.w_faltan, faltanTexto(primero.referencia, ahora))
                }
                setTextViewText(
                    R.id.w_siguiente,
                    segundo?.let { "Después: ${hora(it.referencia)} · ${faltanTexto(it.referencia, ahora)}" } ?: "Sin otro tren por ahora",
                )
            }
            // Achicado: la cuenta regresiva manda; lo demás se esconde.
            val chico = t == Tamano.CHICO
            setViewVisibility(R.id.w_cabecera, if (chico) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.w_siguiente, if (t == Tamano.GRANDE) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.w_estado, if (t == Tamano.GRANDE) View.VISIBLE else View.GONE)
            setTextViewTextSize(R.id.w_cuenta, TypedValue.COMPLEX_UNIT_SP, if (chico) 28f else 38f)
            setTextViewTextSize(R.id.w_faltan, TypedValue.COMPLEX_UNIT_SP, if (chico) 24f else 30f)
            val pad = (if (chico) 8 else 12) * ctx.resources.displayMetrics.density
            setViewPadding(R.id.w_raiz, (16 * ctx.resources.displayMetrics.density).toInt(), pad.toInt(), (10 * ctx.resources.displayMetrics.density).toInt(), pad.toInt())
            setTextViewText(
                R.id.w_estado,
                when {
                    cargando -> "Actualizando…"
                    error -> "Sin conexión" + (guardado?.let { " · datos de las ${hora(it.first)}" } ?: "")
                    guardado != null -> "Actualizado ${hora(guardado.first)}"
                    else -> ""
                },
            )
        }
    }
}
