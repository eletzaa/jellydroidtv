package de.jellydroid.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import android.view.KeyEvent as AKey
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import de.jellydroid.launcher.data.AppEntry

data class MenuOption(val label: String, val action: () -> Unit)

/** Menü nach langem Drücken auf eine App, wie bei Android TV. */
@Composable
fun AppMenu(app: AppEntry, options: List<MenuOption>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        // Das Menü öffnet, während OK noch gedrückt ist. Das Loslassen (und weitere Wiederhol-
        // Ereignisse) darf hier nichts auslösen; erst ein neuer OK-Druck zählt.
        val armed = remember { booleanArrayOf(false) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    val ok = code == AKey.KEYCODE_DPAD_CENTER || code == AKey.KEYCODE_ENTER || code == AKey.KEYCODE_NUMPAD_ENTER
                    if (!ok || armed[0]) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        armed[0] = true
                        false
                    } else true // Loslassen bzw. Wiederholung vom langen Druck schlucken
                },
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .width(320.dp)
                    .clip(shape)
                    .background(Surface)
                    .border(1.dp, Ink.copy(alpha = .18f), shape)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(app.label, style = LauncherType.Row.copy(fontSize = 18.sp()), color = Ink)
                Spacer(Modifier.height(4.dp))
                options.forEachIndexed { i, o ->
                    PillButton(
                        o.label,
                        primary = i == 0,
                        onClick = { onDismiss(); o.action() },
                        modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                    )
                }
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
