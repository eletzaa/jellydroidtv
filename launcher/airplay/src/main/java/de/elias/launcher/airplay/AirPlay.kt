package de.elias.launcher.airplay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Schnittstelle für den Launcher: Empfänger ein-/ausschalten und Einstellungen übergeben. */
object AirPlay {
    const val DEFAULT_NAME = "Jellydroid"
    internal const val EXTRA_ENABLED = "enabled"
    internal const val EXTRA_NAME = "name"
    internal const val EXTRA_PIN = "pin"

    fun apply(context: Context, enabled: Boolean, name: String, requirePin: Boolean) {
        val i = Intent(context, AirPlayService::class.java)
            .putExtra(EXTRA_ENABLED, enabled)
            .putExtra(EXTRA_NAME, name.trim().ifBlank { DEFAULT_NAME })
            .putExtra(EXTRA_PIN, requirePin)
        runCatching {
            if (enabled) ContextCompat.startForegroundService(context, i)
            else context.stopService(i)
        }
    }
}
