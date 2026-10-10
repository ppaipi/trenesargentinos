package ar.chiqui.trenes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.chiqui.trenes.ui.App
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notificaciones.crearCanales(this)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/mosaicos")
        }
        if (savedInstanceState == null) abrirLink(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirLink(intent)
    }

    // Links del sitio con ?o=&d= (compartidos desde la web o la app).
    private fun abrirLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val o = uri.getQueryParameter("o")?.toIntOrNull() ?: return
        val d = uri.getQueryParameter("d")?.toIntOrNull() ?: return
        vm.abrirViaje(o, d)
    }

    override fun onStart() {
        super.onStart()
        vm.alVolver()
    }

    override fun onStop() {
        vm.alSalir()
        super.onStop()
    }
}
