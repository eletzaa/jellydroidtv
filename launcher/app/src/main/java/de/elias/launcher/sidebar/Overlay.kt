package de.elias.launcher.sidebar

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Lebenszyklus für Compose in einem Fenster ohne Activity (Overlay der Bedienungshilfe). */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Ein Compose-Fenster über allen Apps. Muss mit dem Context der Bedienungshilfe erzeugt werden
 * (TYPE_ACCESSIBILITY_OVERLAY braucht keine weitere Berechtigung).
 */
class OverlayWindow(
    private val context: Context,
    private val fullScreen: Boolean,
    private val focusable: Boolean = true,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    val showing get() = view != null

    fun show(content: @Composable () -> Unit) {
        if (view != null) return
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setContent(content)
        }
        val lp = WindowManager.LayoutParams(
            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END }
        wm.addView(v, lp)
        view = v; owner = o
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        owner?.destroy()
        view = null; owner = null
    }
}

/**
 * Öffnen der Sidebar von überall: Menü-Taste (Bedienungshilfe) oder Symbole in der Menüleiste.
 * Läuft die Bedienungshilfe nicht, zeigt der Launcher die Sidebar selbst ([inApp]).
 */
object SidebarController {
    /** Rückfall im Launcher: gesetzte Ansicht = Sidebar offen. */
    var inApp by mutableStateOf<SideView?>(null)

    /** Setzt die Bedienungshilfe, solange sie läuft. */
    var overlayHost: ((SideView) -> Boolean)? = null

    fun open(view: SideView) {
        if (overlayHost?.invoke(view) == true) return
        inApp = view
    }
}
