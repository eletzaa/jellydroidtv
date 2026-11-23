package de.jellydroid.launcher.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import de.jellydroid.launcher.R

/**
 * Home-Assistant-Symbole ("mdi:lightbulb-group" usw.) über die Schrift der Material Design Icons
 * (Pictogrammers, Lizenz in assets/mdi-LICENSE.txt). Zuordnung Name → Zeichen in assets/mdi.txt.
 */
object MdiIcons {
    private val family = FontFamily(Font(R.font.mdi_icons))
    @Volatile private var map: Map<String, Int>? = null

    private fun load(context: Context): Map<String, Int> = map ?: synchronized(this) {
        map ?: runCatching {
            context.assets.open("mdi.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { l ->
                    val i = l.indexOf(' ')
                    if (i <= 0) null else l.substring(0, i) to l.substring(i + 1).trim().toInt(16)
                }.toMap()
            }
        }.getOrDefault(emptyMap()).also { map = it }
    }

    /** Zeichen für "mdi:name", null wenn unbekannt oder kein mdi-Symbol. */
    fun glyph(context: Context, icon: String?): String? {
        val name = icon?.takeIf { it.startsWith("mdi:") }?.removePrefix("mdi:") ?: return null
        val cp = load(context)[name] ?: return null
        return String(Character.toChars(cp))
    }

    /** Zeichnet das Symbol; ohne bekanntes Symbol [fallback]. */
    @Composable
    fun Icon(icon: String?, color: Color, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
        val g = glyph(LocalContext.current, icon)
        if (g == null) { fallback(); return }
        val fs = with(LocalDensity.current) { size.toSp() }
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                g, color = color,
                style = TextStyle(
                    fontFamily = family, fontSize = fs, lineHeight = fs,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}
