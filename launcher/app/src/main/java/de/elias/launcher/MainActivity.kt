package de.elias.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import de.elias.launcher.ui.LauncherApp

class MainActivity : ComponentActivity() {

    /** Wird bei jedem Druck auf die Home-Taste erhöht, damit der Launcher zur Startseite springt. */
    private val homePresses = mutableIntStateOf(0)
    /** Wird erhöht, wenn die Sidebar "Launcher Konfiguration" öffnen will. */
    private val configRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vorkonfiguration vom TV-Installer (setup.json) übernehmen, bevor die Oberfläche die Werte liest
        de.elias.launcher.data.SetupImport.run(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Ein Startbildschirm lässt sich nicht mit "Zurück" schließen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        ServiceKeeper.ensure(this)
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_CONFIG) configRequests.intValue++
        setContent { LauncherApp(homePresses = homePresses.intValue, configRequests = configRequests.intValue) }
    }

    override fun onResume() {
        super.onResume()
        ServiceKeeper.ensure(this)
        de.elias.launcher.data.syncAirPlay(this)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                -> NavKeys.lastNavKey = android.os.SystemClock.uptimeMillis()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_CONFIG) configRequests.intValue++
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_CONFIG = "config"
    }
}

/** Zeitpunkt des letzten Steuerkreuz-Drucks. Tabs wechseln nur, wenn der Fokus wirklich per Steuerkreuz kam. */
object NavKeys {
    @Volatile var lastNavKey = 0L
    fun recent(ms: Long = 400) = android.os.SystemClock.uptimeMillis() - lastNavKey < ms
}
