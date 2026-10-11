package ar.chiqui.trenes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
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

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACCION_REFRESCAR -> trabajar(ctx, red = true)
            ACCION_TICK -> trabajar(ctx, red = false)
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { ConfigWidget.borrar(ctx, it) }

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

        private fun sinConfigurar(ctx: Context, id: Int) = RemoteViews(ctx.packageName, R.layout.widget_tren).apply {
            setTextViewText(R.id.w_trayecto, "TrenAR")
            setTextViewText(R.id.w_hora, "--:--")
            setViewVisibility(R.id.w_cuenta, View.GONE)
            setViewVisibility(R.id.w_faltan, View.GONE)
            setTextViewText(R.id.w_siguiente, "Tocá para elegir un favorito")
            setTextViewText(R.id.w_estado, "")
            val conf = Intent(ctx, ConfigWidget::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("trenar://widget/$id"))
            setOnClickPendingIntent(R.id.w_raiz, PendingIntent.getActivity(ctx, id, conf, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setOnClickPendingIntent(R.id.w_refrescar, refrescar(ctx))
        }

        private fun vista(
            ctx: Context, id: Int, v: Viaje, guardado: Pair<Long, List<Tren>>?,
            cargando: Boolean = false, error: Boolean = false,
        ) = RemoteViews(ctx.packageName, R.layout.widget_tren).apply {
            val o = Estaciones.porId(v.o)?.nombre ?: "?"
            val d = Estaciones.porId(v.d)?.nombre ?: "?"
            setTextViewText(R.id.w_trayecto, "$o → $d")
            setOnClickPendingIntent(R.id.w_raiz, abrirApp(ctx, id, v))
            setOnClickPendingIntent(R.id.w_refrescar, refrescar(ctx))

            val ahora = System.currentTimeMillis()
            val trenes = guardado?.second.orEmpty().filter { faltanTexto(it.referencia, ahora) != null }
            val primero = trenes.getOrNull(0)
            val segundo = trenes.getOrNull(1)
            if (primero == null) {
                setTextViewText(R.id.w_hora, "--:--")
                setViewVisibility(R.id.w_cuenta, View.GONE)
                setViewVisibility(R.id.w_faltan, View.GONE)
                setTextViewText(R.id.w_siguiente, if (cargando) "Buscando trenes…" else "Sin próximos trenes")
            } else {
                setTextViewText(R.id.w_hora, hora(primero.referencia))
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
                    segundo?.let { "Siguiente ${hora(it.referencia)} · ${faltanTexto(it.referencia, ahora)}" } ?: "Sin otro tren por ahora",
                )
            }
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
