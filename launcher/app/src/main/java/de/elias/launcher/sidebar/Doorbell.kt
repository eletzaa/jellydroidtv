package de.elias.launcher.sidebar

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.elias.launcher.MainActivity
import de.elias.launcher.data.SystemActions
import de.elias.launcher.home.HaPrefs
import de.elias.launcher.home.HomeAssistant
import de.elias.launcher.ui.BellIcon
import de.elias.launcher.ui.DisplayFont
import de.elias.launcher.ui.TextFont
import android.view.KeyEvent as AKey

/** Karte oben rechts, wenn es klingelt. Schließt sich nach 30 Sekunden. */
@Composable
fun DoorbellCard(name: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val canOpen = HaPrefs(context).doorButton.isNotBlank()
    val progress = remember { Animatable(1f) }
    val wiggle = remember { Animatable(0f) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
        progress.animateTo(0f, tween(30_000, easing = LinearEasing))
        onClose()
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            for (a in listOf(14f, -12f, 8f, -4f, 0f)) wiggle.animateTo(a, tween(200))
        }
    }
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier
            .padding(19.dp)
            .width(260.dp)
            .clip(shape)
            .background(Color(0xF2161A26))
            .border(1.dp, Color(0x29FFFFFF), shape)
            .onPreviewKeyEvent { e ->
                val c = e.nativeKeyEvent.keyCode
                if (c == AKey.KEYCODE_BACK || c == AKey.KEYCODE_ESCAPE) { if (e.type == KeyEventType.KeyUp) onClose(); true } else false
            }
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(35.dp).clip(CircleShape).background(Color(0x33FFD08A)), contentAlignment = Alignment.Center) {
                BellIcon(Color(0xFFFFD08A), Modifier.size(19.dp).rotate(wiggle.value))
            }
            Column {
                Text("Es klingelt", style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = Color(0xFFF4F6FB))
                Text(name, style = TextStyle(fontFamily = TextFont, fontSize = 11.sp), color = Color(0x9EFFFFFF))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canOpen) BellButton("Tür öffnen", primary = true, Modifier.weight(1f).focusRequester(first)) {
                HomeAssistant.openDoor(context)
                Toast.makeText(context, "Tür geöffnet", Toast.LENGTH_SHORT).show()
                onClose()
            }
            BellButton("Schließen", primary = false, Modifier.weight(1f).then(if (canOpen) Modifier else Modifier.focusRequester(first)), onClose)
        }
        Box(Modifier.fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x1FFFFFFF))) {
            Box(Modifier.fillMaxWidth().height(2.5.dp).graphicsLayer { scaleX = progress.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(Color(0x9EFFFFFF)))
        }
    }
}

@Composable
private fun BellButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when { focused -> Color.White; primary -> Color(0x40FFFFFF); else -> Color(0x12FFFFFF) }
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp), color = if (focused) Color(0xFF0D1220) else Color(0xFFF4F6FB))
    }
}

/** Standard-Aktionen der Sidebar, gleich für Overlay und Launcher. */
class DefaultSidebarActions(private val context: Context, private val close: () -> Unit) : SidebarActions {
    private val sys = SystemActions(context)
    override fun openLauncherConfig() {
        close()
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_CONFIG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }
    private fun quiet() { de.elias.launcher.HomeButtonService.suppressWindowUntil = android.os.SystemClock.uptimeMillis() + 4_000 }
    override fun openTvSettings() { close(); quiet(); sys.openSettings() }
    override fun openSources() { close(); quiet(); sys.openSources() }
    override fun openWifiSettings() { close(); quiet(); sys.openWifiSettings() }
    override fun airplayEnabled() = context.getSharedPreferences("config", Context.MODE_PRIVATE).getBoolean("airplay_enabled", true)
    override fun setAirplay(on: Boolean) {
        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit().putBoolean("airplay_enabled", on).commit()
        de.elias.launcher.data.syncAirPlay(context)
    }
}
