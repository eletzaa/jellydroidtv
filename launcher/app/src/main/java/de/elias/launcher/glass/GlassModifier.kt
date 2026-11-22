package de.elias.launcher.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/**
 * Macht das Element zu einer Glasfläche. Die Brechung zeichnet der Shader dahinter;
 * im matten Ersatzmodus zeichnet Compose eine halbtransparente Fläche.
 */
fun Modifier.glass(
    state: GlassState,
    key: String,
    strength: Float = 1f,
    tint: Float = 0f,
    alpha: Float = 1f,
): Modifier = composed {
    DisposableEffect(key) { onDispose { state.remove(key) } }
    val matte = state.mode == GlassMode.Matte
    val shape = RoundedCornerShape(percent = 50)
    this
        .onGloballyPositioned { c ->
            val p = c.positionInWindow()
            state.set(
                key,
                GlassPanel(p.x, p.y, c.size.width.toFloat(), c.size.height.toFloat(), alpha, strength, tint),
            )
        }
        .then(
            if (matte) {
                // Grundfarbe folgt dem Design. Die Linse (tint) ist im dunklen Modus hell, im hellen
                // Modus dunkel, damit die Schrift darauf (InkInverse) lesbar bleibt.
                val lens = if (de.elias.launcher.ui.Pal.light) Color(0xFF14171C) else Color.White
                Modifier
                    .background(de.elias.launcher.ui.GlassBase.copy(alpha = 0.55f * alpha), shape)
                    .background(lens.copy(alpha = (if (de.elias.launcher.ui.Pal.light) 0.04f else 0.10f) + tint * 0.75f * alpha), shape)
                    .border(1.dp, de.elias.launcher.ui.GlassEdge.copy(alpha = de.elias.launcher.ui.GlassEdge.alpha * alpha), shape)
            } else {
                Modifier
            },
        )
}
