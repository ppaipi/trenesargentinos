package ar.chiqui.trenes.datos

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Actualización sin navegador: DownloadManager baja el APK (progreso en la barra de
 * notificaciones, sin permisos de almacenamiento) a la carpeta propia de la app y al
 * terminar abre el instalador de Android. Si algo falla, cae al link fijo en el navegador.
 */
object Instalador {
    private const val MIME = "application/vnd.android.package-archive"

    fun actualizar(ctx: Context, tag: String, onFin: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        val carpeta = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dm = app.getSystemService(DownloadManager::class.java)
        if (carpeta == null || dm == null) return fallar(ctx, onFin)
        // Una sola copia: los APK de versiones anteriores ya no sirven.
        carpeta.listFiles { f -> f.name.startsWith("TrenAR") && f.name.endsWith(".apk") }?.forEach { it.delete() }
        val apk = File(carpeta, Actualizacion.archivo(tag))
        val pedido = DownloadManager.Request(Uri.parse(Actualizacion.descarga(tag)))
            .setTitle("TrenAR $tag")
            .setDescription("Versión nueva")
            .setMimeType(MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, apk.name)
        val id = runCatching { dm.enqueue(pedido) }.getOrNull() ?: return fallar(ctx, onFin)
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { app.unregisterReceiver(this) }
                val ok = runCatching {
                    dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
                        cur.moveToFirst() && cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                    }
                }.getOrDefault(false)
                if (ok && apk.length() > 0 && instalar(app, apk)) onFin(true) else fallar(app, onFin)
            }
        }
        ContextCompat.registerReceiver(app, receptor, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    /** La primera vez Android pide habilitar "Instalar apps desconocidas" para TrenAR; después, sólo confirmar. */
    private fun instalar(ctx: Context, apk: File): Boolean = runCatching {
        // Android 6 no acepta content:// en el instalador; desde Android 7 file:// está prohibido.
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(ctx, "${ctx.packageName}.apk", apk) else Uri.fromFile(apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    private fun fallar(ctx: Context, onFin: (Boolean) -> Unit) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Actualizacion.DESCARGA)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        onFin(false)
    }
}
