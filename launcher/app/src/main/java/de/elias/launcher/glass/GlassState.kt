package de.elias.launcher.glass

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/** Eine Glasfläche in Fensterkoordinaten (Pixel). */
data class GlassPanel(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** 0..1, wie stark die Fläche sichtbar ist */
    val alpha: Float = 1f,
    /** Stärke der Lichtbrechung, 1 = normal */
    val strength: Float = 1f,
    /** 0..1, Aufhellung zu Weiß (ausgewählte Linse = hoch) */
    val tint: Float = 0f,
)

enum class GlassMode { Refraction, Matte }

/**
 * Verbindung zwischen der Compose-Oberfläche und dem OpenGL-Renderer.
 * Compose meldet hier die Lage jeder Glasfläche, der Renderer liest sie im GL-Thread.
 */
class GlassState {
    private val panels = ConcurrentHashMap<String, GlassPanel>()

    /** Wird vom Renderer gesetzt, damit Änderungen ein neues Bild auslösen. */
    @Volatile var requestRender: (() -> Unit)? = null

    @Volatile var backdrop: Bitmap? = null
        private set
    @Volatile var backdropVersion: Int = 0
        private set

    /** Fällt auf Matt zurück, wenn der Fernseher den Shader nicht flüssig schafft. */
    var mode by mutableStateOf(GlassMode.Matte)

    fun set(key: String, panel: GlassPanel) {
        if (panels[key] != panel) {
            panels[key] = panel
            requestRender?.invoke()
        }
    }

    fun remove(key: String) {
        if (panels.remove(key) != null) requestRender?.invoke()
    }

    /** Sortiert nach Schlüssel, damit z. B. die Tab-Linse über der Leiste gezeichnet wird. */
    fun snapshot(): List<GlassPanel> =
        panels.entries.sortedBy { it.key }.map { it.value }.filter { it.alpha > 0.01f }.take(MAX_PANELS)

    fun setBackdrop(bitmap: Bitmap) {
        backdrop = bitmap
        backdropVersion++
        requestRender?.invoke()
    }

    companion object {
        const val MAX_PANELS = 8
    }
}
