package de.elias.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Plan B für den Philips-Fernseher: Die Firmware startet bei der Home-Taste immer den
 * Google-Launcher, egal was als Standard gesetzt ist. Diese Bedienungshilfe fängt die
 * Home-Taste ab und öffnet stattdessen unseren Launcher. Taucht der Google-Launcher
 * trotzdem auf, wird er ersetzt.
 *
 * Android blockiert nach einem Home-Druck fremde App-Starts für etwa 5 Sekunden
 * ("App-Switch-Sperre"). Deshalb wird nachgefasst, solange der Google-Launcher vorne ist.
 *
 * Protokoll ansehen: adb logcat -s EliasHome
 */
class HomeButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recentLaunches = ArrayDeque<Long>()
    private var foregroundPackage: String? = null

    private val sidebar by lazy { de.elias.launcher.sidebar.OverlayWindow(this, fullScreen = true) }
    private val bell by lazy { de.elias.launcher.sidebar.OverlayWindow(this, fullScreen = false) }
    private val sidebarVisible = androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
        de.elias.launcher.sidebar.SidebarController.overlayHost = null
        de.elias.launcher.home.HomeAssistant.onDoorbell = null
        sidebar.hide(); bell.hide()
        super.onDestroy()
    }

    override fun onServiceConnected() {
        running = true
        Log.i(TAG, "Bedienungshilfe verbunden, Filter=${serviceInfo?.flags}")
        de.elias.launcher.sidebar.SidebarController.overlayHost = { view -> showSidebar(view); true }
        de.elias.launcher.home.HomeAssistant.onDoorbell = { name -> showDoorbell(name) }
        // Hält die Verbindung zu Home Assistant, damit die Klingel auch in anderen Apps ankommt
        de.elias.launcher.home.HomeAssistant.start(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(TAG, "Taste code=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}) action=${event.action}")
        // Menü-Taste anlernen (Launcher Konfiguration › Sidebar)
        learnKey?.let { cb ->
            if (event.keyCode !in NAV_KEYS) {
                if (event.action == KeyEvent.ACTION_UP) {
                    learnKey = null
                    getSharedPreferences("config", MODE_PRIVATE).edit()
                        .putInt("sidebar_key", event.keyCode).remove("sidebar_window").apply()
                    val label = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
                    handler.post { cb(label) }
                }
                return true
            }
        }
        if (event.keyCode in sidebarKeys()) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (sidebar.showing) closeSidebar() else showSidebar(de.elias.launcher.sidebar.SideView.Main)
            }
            return true
        }
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) {
            if (sidebar.showing) closeSidebar()
            openLauncher("Home-Taste")
        }
        return true
    }

    /** Gelernte Taste, sonst die üblichen Codes der Menü-/Einstellungstaste. */
    private fun sidebarKeys(): Set<Int> {
        val learned = getSharedPreferences("config", MODE_PRIVATE).getInt("sidebar_key", -1)
        return if (learned > 0) setOf(learned) else DEFAULT_SIDEBAR_KEYS
    }

    fun showSidebar(view: de.elias.launcher.sidebar.SideView) {
        if (sidebar.showing) closeSidebar(immediately = true)
        sidebarVisible.value = false
        sidebar.show {
            androidx.compose.runtime.LaunchedEffect(Unit) { sidebarVisible.value = true }
            val actions = androidx.compose.runtime.remember { de.elias.launcher.sidebar.DefaultSidebarActions(this) { closeSidebar() } }
            de.elias.launcher.sidebar.Sidebar(view, actions, onClose = { closeSidebar() }, visible = sidebarVisible.value)
        }
    }

    private fun closeSidebar(immediately: Boolean = false) {
        sidebarVisible.value = false
        if (immediately) sidebar.hide() else handler.postDelayed({ if (!sidebarVisible.value) sidebar.hide() }, 380)
    }

    private fun showDoorbell(name: String) {
        bell.hide()
        bell.show { de.elias.launcher.sidebar.DoorbellCard(name, onClose = { bell.hide() }) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val window = "$pkg/${event.className}"
        // Fängt die Firmware die Taste ab, bevor wir sie sehen (Philips-Schnelleinstellungen), lernen wir
        // stattdessen das Fenster, das sie öffnet, schließen es künftig und zeigen die Sidebar.
        if (pkg != packageName && !pkg.contains("inputmethod")) {
            learnKey?.let { cb ->
                learnKey = null
                getSharedPreferences("config", MODE_PRIVATE).edit().putString("sidebar_window", window).remove("sidebar_key").apply()
                Log.i(TAG, "Sidebar-Fenster gelernt: $window")
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.post { cb("Schnelleinstellungen") }
                return
            }
            val learned = getSharedPreferences("config", MODE_PRIVATE).getString("sidebar_window", null)
            if (learned != null && learned == window && SystemClock.uptimeMillis() > suppressWindowUntil) {
                Log.i(TAG, "Sidebar statt $window")
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ showSidebar(de.elias.launcher.sidebar.SideView.Main) }, 250)
                return
            }
        }
        // Tastatur- und Systemfenster ändern den Vordergrund nicht
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        foregroundPackage = pkg
        Log.i(TAG, "Fenster vorne: $pkg / ${event.className}")
        if (pkg == STOCK_LAUNCHER) {
            openLauncher("Google-Launcher erschienen")
            // Nachfassen, falls die App-Switch-Sperre den ersten Start verzögert hat
            for (delay in RETRY_DELAYS_MS) {
                handler.postDelayed({
                    if (foregroundPackage == STOCK_LAUNCHER) openLauncher("Nachfassen nach $delay ms", countsTowardLimit = false)
                }, delay)
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun openLauncher(reason: String, countsTowardLimit: Boolean = true) {
        // Schutz gegen eine Endlosschleife, falls unser Launcher abstürzt:
        // höchstens 4 Auslöser in 15 Sekunden, danach bleibt der Google-Launcher stehen.
        val now = SystemClock.elapsedRealtime()
        while (recentLaunches.isNotEmpty() && now - recentLaunches.first() > 15_000) recentLaunches.removeFirst()
        if (countsTowardLimit) {
            if (recentLaunches.size >= 4) {
                Log.w(TAG, "Zu viele Starts hintereinander, gebe auf ($reason)")
                return
            }
            recentLaunches.addLast(now)
        }

        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(intent)
            Log.i(TAG, "Launcher gestartet ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Start fehlgeschlagen ($reason)", e)
        }
    }

    companion object {
        const val TAG = "EliasHome"
        /** true, solange Android den Dienst gebunden hat (gleicher Prozess wie der Launcher). */
        @Volatile var running = false
        const val STOCK_LAUNCHER = "com.google.android.tvlauncher"
        private val RETRY_DELAYS_MS = listOf(1_200L, 3_000L, 5_600L)
        /** Philips "Menu"-Taste: welcher Code genau kommt, wird am TV angelernt; bis dahin diese. */
        val DEFAULT_SIDEBAR_KEYS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS)
        private val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        )
        /** Gesetzt, solange die Konfiguration auf die nächste Taste wartet. */
        @Volatile var learnKey: ((String) -> Unit)? = null
        /** Kurz nach "TV-Einstellungen" o. ä. aus der Sidebar das gelernte Fenster nicht ersetzen. */
        @Volatile var suppressWindowUntil = 0L
    }
}
