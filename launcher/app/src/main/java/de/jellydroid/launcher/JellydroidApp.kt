package de.jellydroid.launcher

import android.app.Application

/**
 * Läuft bei jedem Start des Launcher-Prozesses zuerst, egal ob Android die Oberfläche oder
 * die Bedienungshilfe startet. Nach "am force-stop" bindet Android oft zuerst die Bedienungshilfe;
 * stand die Vorkonfiguration nur in MainActivity, lasen Home Assistant & Co. vorher die alten Werte.
 */
class JellydroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Der AirPlay-Empfänger läuft im eigenen Prozess (":airplay"), dort nichts importieren
        if (getProcessName() != packageName) return
        de.jellydroid.launcher.data.SetupImport.run(this)
        de.jellydroid.launcher.ui.AppFonts.load(this)
    }
}
