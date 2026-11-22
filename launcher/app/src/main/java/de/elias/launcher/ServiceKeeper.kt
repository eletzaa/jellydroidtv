package de.elias.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Die Philips-Firmware löscht beim Neustart (und nach Updates) die Freischaltung
 * des Home-Tasten-Dienstes. Mit WRITE_SECURE_SETTINGS (einmalig per adb vergeben)
 * trägt sich der Launcher selbst wieder ein.
 */
object ServiceKeeper {
    private const val TAG = "EliasHome"

    fun ensure(context: Context) {
        val ctx = context.applicationContext
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS fehlt, Selbstheilung nicht möglich")
            return
        }
        val me = ComponentName(ctx, HomeButtonService::class.java).flattenToString()
        try {
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = current.split(':').filter { it.isNotBlank() }
            val enabled = Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (me in list && enabled) {
                // Eingetragen heißt nicht gebunden: Nach einem "am force-stop" (z. B. durch den
                // Installer) führt Android den Dienst als abgestürzt und bindet ihn nicht neu.
                // Dann kurz austragen und wieder eintragen, das bindet ihn frisch.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!HomeButtonService.running) rebind(ctx, me)
                }, 3_000)
                return
            }
            if (me !in list) {
                val newValue = (list + me).joinToString(":")
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newValue)
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Dienst wieder eingetragen (vorher: '$current', an=$enabled)")
            // Android setzt den Hauptschalter kurz danach wieder auf 0, solange der Dienst
            // noch nicht gebunden ist. Deshalb den Schalter mehrmals nachziehen.
            val h = Handler(Looper.getMainLooper())
            longArrayOf(800, 2_000, 4_000, 8_000).forEach { d ->
                h.postDelayed({
                    try {
                        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                            Log.i(TAG, "Hauptschalter nach $d ms wieder auf 1 gesetzt")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Hauptschalter: ${e.message}")
                    }
                }, d)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Selbstheilung fehlgeschlagen: ${e.message}")
        }
    }

    private fun rebind(ctx: Context, me: String) {
        try {
            val cr = ctx.contentResolver
            val others = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() && it != me }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me).joinToString(":"))
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                    Log.i(TAG, "Dienst war eingetragen, aber nicht gebunden: neu eingetragen")
                } catch (e: Exception) { Log.w(TAG, "Neu eintragen: ${e.message}") }
            }, 500)
        } catch (e: Exception) {
            Log.w(TAG, "Neu binden fehlgeschlagen: ${e.message}")
        }
    }

    /** Nach dem Boot mehrmals prüfen, falls die Firmware erst später zurücksetzt. */
    fun ensureRepeatedly(context: Context) {
        val h = Handler(Looper.getMainLooper())
        longArrayOf(0, 5_000, 20_000, 60_000).forEach { d -> h.postDelayed({ ensure(context) }, d) }
    }
}
