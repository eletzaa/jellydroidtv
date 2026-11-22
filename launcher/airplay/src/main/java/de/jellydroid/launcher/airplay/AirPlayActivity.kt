package de.jellydroid.launcher.airplay

import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text

/** Vollbild für Bildschirmsynchronisierung und den Verbindungs-Code. */
class AirPlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { Screen(onEnd = { finish() }) }
    }

    override fun onStart() { super.onStart(); AirPlayState.screenVisible = true }
    override fun onStop() { AirPlayState.screenVisible = false; super.onStop() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Zurück beendet die Verbindung; das iPhone zeigt dann wieder seinen eigenen Bildschirm.
            AirPlayService.instance?.disconnect()
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun Screen(onEnd: () -> Unit) {
    val s = AirPlayState
    // Alles vorbei (Verbindung weg und kein Code offen): zurück zum Startbildschirm.
    val idle = !s.mirroring && s.pin == null
    LaunchedEffect(idle, s.connected) {
        if (idle) {
            kotlinx.coroutines.delay(if (s.connected) 4000 else 600)
            if (!AirPlayState.mirroring && AirPlayState.pin == null) onEnd()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            s.pin != null -> Pin(s.pin!!)
            s.mirroring -> Mirror(s.aspect)
        }
    }
}

@Composable
private fun Mirror(aspect: Float) {
    AndroidView(
        modifier = Modifier.aspectRatio(aspect.coerceIn(0.3f, 4f), matchHeightConstraintsFirst = aspect >= 16f / 9f),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) { AirPlayService.instance?.attachSurface(h.surface) }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { AirPlayService.instance?.attachSurface(h.surface) }
                    override fun surfaceDestroyed(h: SurfaceHolder) { AirPlayService.instance?.detachSurface(h.surface) }
                })
            }
        },
    )
}

@Composable
private fun Pin(pin: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AirPlay-Code", style = TextStyle(fontSize = 22.sp, color = Color(0xB3FFFFFF)))
        Spacer(Modifier.height(18.dp))
        Text(pin, style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 18.sp, color = Color.White))
    }
}
