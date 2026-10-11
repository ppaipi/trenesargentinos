package ar.chiqui.trenes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Avisos al usuario. "Avisame" programa una alarma del sistema 5 min antes
 * del tren, así llega aunque la app esté cerrada (con el último horario
 * conocido: mientras la app está abierta se reprograma en cada consulta).
 */
object Notificaciones {
    private const val CANAL_TREN = "tren"
    private const val CANAL_SERVICIO = "servicio"
    private const val CANAL_PROXIMO = "proximo"
    private const val ID_PROXIMO = 4242
    const val MINUTOS_ANTES = 5

    fun crearCanales(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return // antes de Android 8 no hay canales
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_TREN, "Llegada de tu tren", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CANAL_SERVICIO, "Avisos del servicio", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CANAL_PROXIMO, "Próximo tren (cuenta regresiva)", NotificationManager.IMPORTANCE_LOW))
    }

    fun permitidas(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun mostrar(ctx: Context, titulo: String, cuerpo: String, id: Int, servicio: Boolean = false) {
        if (!permitidas(ctx)) return
        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, if (servicio) CANAL_SERVICIO else CANAL_TREN)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // sin permiso: no hay nada que hacer
        }
    }

    /**
     * Notificación fija con la cuenta regresiva del próximo tren, al salir de
     * la app con una búsqueda en vivo. El cronómetro lo corre Android solo (sin
     * servicio en segundo plano) y se borra sola un minuto después de la llegada.
     */
    fun mostrarProximo(ctx: Context, titulo: String, cuerpo: String, llega: Long) {
        if (!permitidas(ctx)) return
        val abrir = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CANAL_PROXIMO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setContentIntent(abrir)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(true)
            .setWhen(llega)
            .setTimeoutAfter(maxOf(1000L, llega - System.currentTimeMillis() + 60_000))
        // Cronómetro hacia atrás desde Android 7; antes queda la hora de llegada.
        if (Build.VERSION.SDK_INT >= 24) b.setUsesChronometer(true).setChronometerCountDown(true)
        val n = b.build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_PROXIMO, n)
        } catch (e: SecurityException) {
            // sin permiso: no hay nada que hacer
        }
    }

    fun quitarProximo(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_PROXIMO)

    private fun intentAlarma(ctx: Context, clave: String, titulo: String = "", cuerpo: String = "") = PendingIntent.getBroadcast(
        ctx,
        clave.hashCode(),
        Intent(ctx, AvisoReceiver::class.java).putExtra("titulo", titulo).putExtra("cuerpo", cuerpo).putExtra("id", clave.hashCode()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Programa (o reprograma) la alarma para [en] epoch ms. */
    fun programar(ctx: Context, clave: String, en: Long, titulo: String, cuerpo: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = intentAlarma(ctx, clave, titulo, cuerpo)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, en, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, en, pi)
        }
    }

    fun cancelar(ctx: Context, clave: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intentAlarma(ctx, clave))
    }
}

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notificaciones.mostrar(
            ctx,
            intent.getStringExtra("titulo") ?: "Tu tren",
            intent.getStringExtra("cuerpo") ?: "",
            intent.getIntExtra("id", 1),
        )
    }
}
