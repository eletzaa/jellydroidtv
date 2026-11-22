package de.jellydroid.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Nach Neustart oder Update: Home-Tasten-Dienst wieder einschalten, AirPlay starten. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("JellydroidHome", "Empfangen: ${intent.action}")
        val pending = goAsync()
        ServiceKeeper.ensureRepeatedly(context)
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) de.jellydroid.launcher.data.syncAirPlay(context)
        // goAsync erlaubt rund 10 s; die späteren Prüfungen laufen, solange der Prozess lebt.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pending.finish() }, 8_000)
    }
}
