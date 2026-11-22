package de.jellydroid.launcher.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Aktionen der Symbole oben rechts und der Suche. */
class SystemActions(private val context: Context) {

    fun openSearch() = startFirst(
        Intent(Intent.ACTION_ASSIST),
        Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
        failMessage = "Suche nicht gefunden",
    )

    fun openSettings() = startFirst(Intent(Settings.ACTION_SETTINGS), failMessage = "Einstellungen nicht gefunden")

    /**
     * Profilbild: öffnet die Android-Einstellungen (dieselbe Seite wie Philips-Menü › Android
     * Einstellungen, am TV mitgelesen). ACTION_SETTINGS öffnet am Philips nur dessen eigenes Menü.
     */
    fun openAccount() = startFirst(
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
        Intent(Settings.ACTION_SETTINGS),
        failMessage = "Konto-Einstellungen nicht gefunden",
    )

    /**
     * Quellen-Menü (HDMI usw.). Wie Philips das Menü startet, prüfen wir am Fernseher;
     * bis dahin werden die üblichen Android-TV-Wege probiert.
     */
    fun openSources() = startFirst(
        Intent("com.android.tv.action.VIEW_INPUTS"),
        Intent("android.settings.TV_INPUT_SETTINGS"),
        failMessage = "Quellen-Menü noch nicht verbunden. Nutze vorerst die SOURCES-Taste.",
    )

    /** WLAN-Einstellungen; ein- und ausschalten dürfen Apps unter Android 11 nicht selbst. */
    fun openWifiSettings() = startFirst(
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"),
        Intent(Settings.ACTION_SETTINGS),
        failMessage = "WLAN-Einstellungen nicht gefunden",
    )

    private fun startFirst(vararg intents: Intent, failMessage: String) {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // nächsten Weg probieren
            }
        }
        Toast.makeText(context, failMessage, Toast.LENGTH_LONG).show()
    }
}
