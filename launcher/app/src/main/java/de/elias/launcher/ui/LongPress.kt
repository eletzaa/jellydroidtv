package de.elias.launcher.ui

import android.view.KeyEvent as AKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private class PressState { var down = false; var long = false }

private fun isOk(code: Int) = code == AKey.KEYCODE_DPAD_CENTER || code == AKey.KEYCODE_ENTER || code == AKey.KEYCODE_NUMPAD_ENTER

/**
 * OK-Taste der Fernbedienung: kurz = [onClick], lang gedrückt = [onLongClick] (wie bei Android TV).
 * Touch/Maus laufen weiter über clickable.
 */
@Composable
fun Modifier.okKey(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val st = remember { PressState() }
    return this.onPreviewKeyEvent { e ->
        if (!isOk(e.nativeKeyEvent.keyCode)) return@onPreviewKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    st.down = true; st.long = false
                } else if (st.down && !st.long) {
                    st.long = true
                    onLongClick()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (st.down && !st.long) onClick()
                st.down = false; st.long = false
                true
            }
            else -> false
        }
    }
}

