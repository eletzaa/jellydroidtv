package de.jellydroid.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.jellydroid.launcher.data.AppEntry
import de.jellydroid.launcher.data.AppRepository
import de.jellydroid.launcher.data.SystemActions
import de.jellydroid.launcher.glass.GlassState
import de.jellydroid.launcher.jellyfin.JellyfinClient
import de.jellydroid.launcher.jellyfin.JellyfinState
import androidx.compose.runtime.rememberCoroutineScope
import de.jellydroid.launcher.glass.paintGlow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TABS = listOf("Startseite", "Entdecken", "Apps")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LauncherApp(homePresses: Int, configRequests: Int = 0) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context.applicationContext) }
    val actions = remember { SystemActions(context) }
    val glass = remember { GlassState() }
    val scope = rememberCoroutineScope()
    val cfg = remember { de.jellydroid.launcher.data.LauncherConfig(context.applicationContext) }
    val jf = remember { JellyfinState(JellyfinClient(context.applicationContext, cfg.jellyfinUrl), scope, de.jellydroid.launcher.jellyfin.LocalMarks(context.applicationContext)) }
    jf.playerApp = cfg.jellyfinApp
    val seerr = remember {
        de.jellydroid.launcher.jellyfin.SeerrState(de.jellydroid.launcher.jellyfin.SeerrClient(context.applicationContext, cfg.seerrUrl), jf.client, cfg, scope)
    }
    var configOpen by remember { mutableStateOf(false) }
    // Sidebar › Einstellungen › Launcher Konfiguration
    LaunchedEffect(configRequests) { if (configRequests > 0) { de.jellydroid.launcher.sidebar.SidebarController.inApp = null; configOpen = true } }
    // Design: Dynamisch = tagsüber hell (7 bis 19 Uhr), sonst dunkel; jede Minute prüfen
    var hour by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) } }
    Pal.light = when (cfg.theme) {
        de.jellydroid.launcher.data.ThemeMode.LIGHT -> true
        de.jellydroid.launcher.data.ThemeMode.DARK -> false
        // Dynamisch: 1 Stunde nach Sonnenaufgang bis 1 Stunde vor Sonnenuntergang hell (am gewählten Ort)
        de.jellydroid.launcher.data.ThemeMode.AUTO -> {
            @Suppress("UNUSED_EXPRESSION") hour // jede Minute neu prüfen
            val pl = de.jellydroid.launcher.data.Places.all[cfg.place.coerceIn(0, de.jellydroid.launcher.data.Places.all.lastIndex)]
            de.jellydroid.launcher.data.Sun.isDaylight(pl.second, pl.third)
        }
    }
    var heroImage by remember { mutableStateOf<String?>(null) }
    var startHeroImage by remember { mutableStateOf<String?>(null) }
    var startFocus by remember { mutableStateOf<de.jellydroid.launcher.jellyfin.MediaItem?>(null) } // fokussierte Weiterschauen-Kachel
    var startSection by remember { mutableIntStateOf(-1) }
    var startDetails by remember { mutableStateOf<de.jellydroid.launcher.jellyfin.MediaItem?>(null) }
    var favOrder by remember { mutableStateOf(repo.savedFavorites()) }
    var menuFor by remember { mutableStateOf<Pair<AppEntry, Boolean>?>(null) } // App + "aus Favoriten-Reihe"
    var movingPkg by remember { mutableStateOf<String?>(null) }
    var refocus by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var refocusCounter by remember { mutableIntStateOf(0) }
    // Fokus nach dem Menü nur einmal zurückgeben. Blieb der Wert stehen, hat sich die Kachel
    // bei jedem Tabwechsel wieder den Fokus geholt (Fokus sprang z. B. auf Tidal).
    LaunchedEffect(refocus) { if (refocus != null) { kotlinx.coroutines.delay(800); refocus = null } }

    var tab by remember { mutableIntStateOf(1) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var barHidden by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }

    // Home-Taste: zurück zum Entdecken-Tab, Leiste wieder einblenden
    LaunchedEffect(homePresses) { if (homePresses > 0) { tab = 1; barHidden = false } }

    // Apps bei jedem Zurückkehren neu einlesen (neu installierte Apps erscheinen sofort)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reloadTick++; jf.refresh(); cfg.reloadExternal() }
    LaunchedEffect(reloadTick) { apps = withContext(Dispatchers.IO) { repo.loadApps() } }

    // Standard-Hintergrund für alle Tabs: grau leuchtendes Schwarz (Titelbilder liegen darüber)
    val light = Pal.light
    LaunchedEffect(size, light) {
        if (size.width > 0) backdrop = withContext(Dispatchers.Default) {
            if (light) de.jellydroid.launcher.glass.paintGlowLight(size.width, size.height) else paintGlow(size.width, size.height)
        }
    }
    LaunchedEffect(tab) { barHidden = false; refocus = null }

    // Android TV zentriert fokussierte Kacheln standardmäßig ("Pivot"), dadurch wackelt eine Reihe
    // beim Hineingehen kurz. Stattdessen nur so weit scrollen, wie nötig ist.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides MinimalBringIntoView,
    ) {
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Box(Modifier.fillMaxSize().background(if (light) Color(0xFFE4E8EC) else Color(0xFF0D151D)))
        androidx.compose.animation.AnimatedVisibility(
            visible = backdrop != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(900)),
        ) {
            backdrop?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Titelbild blendet weich ein und aus; ohne Bild (Apps, keine Daten) bleibt der Standard-Hintergrund
        val pageImage = when (tab) { 0 -> startHeroImage; 1 -> heroImage; else -> null }
        var lastImage by remember { mutableStateOf<String?>(null) } // bleibt beim Ausblenden stehen
        if (pageImage != null) lastImage = pageImage
        androidx.compose.animation.AnimatedVisibility(
            visible = pageImage != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(700)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)),
        ) {
            HeroBackdrop(pageImage ?: lastImage)
        }
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            TopBar(
                tabs = TABS,
                selected = tab,
                onSelect = { tab = it },
                glass = glass,
                hidden = barHidden,
                actions = actions,
                modifier = Modifier.padding(start = ScreenStart, end = ScreenEnd),
            )
            when (tab) {
                0 -> {
                    val favs = repo.favorites(apps, favOrder)
                    StartScreen(
                        favorites = favs,
                        onLaunch = repo::launch,
                        onLongPress = { menuFor = it to true },
                        movingPkg = movingPkg,
                        onMove = { d ->
                            val list = favs.map { it.packageName }.toMutableList()
                            val i = list.indexOf(movingPkg)
                            val j = i + d
                            if (i >= 0 && j in list.indices) {
                                list.add(j, list.removeAt(i))
                                favOrder = list; repo.saveFavorites(list)
                            }
                        },
                        onMoveDone = { movingPkg = null },
                        onAdd = { tab = 2 },
                        refocus = refocus,
                        hero = {
                            // Stufe 1 in der ersten Reihe unter dem Banner, Stufe 2 darunter
                            val firstSection = if (jf.continueWatching.isEmpty()) 1 else 0
                            val stage = when { startSection < 0 -> 0; startSection == firstSection -> 1; else -> 2 }
                            StartHero(jf, focused = startFocus, paused = startFocus != null, stage = stage,
                                onDetails = { startDetails = it }, onHeroImage = { startHeroImage = it })
                        },
                        onSection = { startSection = it },
                        continueWatching = {
                            ContinueWatchingRow(jf, onFocusItem = { startFocus = it },
                                details = startDetails, onDetails = { startDetails = it })
                        },
                    )
                }
                1 -> DiscoverScreen(jf, seerr, cfg, onHeroImage = { heroImage = it }, onScrolledDown = { barHidden = it })
                else -> AppsScreen(apps, onLaunch = repo::launch, onLongPress = { menuFor = it to false }, onScrolledDown = { barHidden = it }, refocus = refocus)
            }
        }
        menuFor?.let { (app, fromFavRow) ->
            val current = repo.favorites(apps, favOrder).map { it.packageName }
            val isFav = app.packageName in current
            fun save(list: List<String>) { favOrder = list; repo.saveFavorites(list) }
            val options = buildList {
                if (fromFavRow) add(MenuOption("Verschieben") { movingPkg = app.packageName })
                add(MenuOption("Öffnen") { repo.launch(app) })
                if (isFav) add(MenuOption("Aus Favoriten entfernen") { save(current - app.packageName) })
                else add(MenuOption("Zu Favoriten hinzufügen") { save(current + app.packageName) })
                // ganz unten und nie vorausgewählt (der Fokus startet auf der ersten Option);
                // Android fragt danach selbst noch einmal nach
                if (!app.isSystem) add(MenuOption("Deinstallieren") { repo.uninstall(app) })
            }
            AppMenu(app, options, onDismiss = {
                // (Deinstallieren ist die letzte Option; der Fokus startet immer auf der ersten)
                menuFor = null
                refocusCounter++
                // Nach dem Entfernen gibt es die Kachel nicht mehr; dann bleibt der Fokus beim System
                refocus = app.packageName to refocusCounter
            })
        }
        if (configOpen) ConfigDialog(cfg, jf, seerr, onDismiss = { configOpen = false })
        // Rückfall, falls die Bedienungshilfe nicht läuft: Sidebar im Launcher selbst
        de.jellydroid.launcher.sidebar.SidebarController.inApp?.let { start ->
            val close = { de.jellydroid.launcher.sidebar.SidebarController.inApp = null }
            val sideActions = remember { de.jellydroid.launcher.sidebar.DefaultSidebarActions(context.applicationContext) { close() } }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = close,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                de.jellydroid.launcher.sidebar.Sidebar(start, sideActions, onClose = close)
            }
        }
    }
    }
}

/** Scrollt nur so weit, bis die fokussierte Kachel ganz sichtbar ist (Standardverhalten außerhalb von TV). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
object MinimalBringIntoView : androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Für senkrechte Listen aus Reihen: die fokussierte Kachel rastet so ein, dass darüber die
 * Reihen-Überschrift ([leadPx]) sichtbar bleibt und darunter Titel und Unterzeile Platz haben.
 * So hat es Elias am 17.11. abgenommen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class RowTopBringIntoView(private val leadPx: Float) : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val d = offset - leadPx
        // Kleinste Abweichungen ignorieren (beim waagerechten Durchzappen in derselben Reihe)
        return if (kotlin.math.abs(d) < 6f) 0f else d
    }
}

/** Wrapper: senkrecht oben einrasten. Innere Reihen setzen wieder [MinimalBringIntoView]. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RowSnapScroll(content: @Composable () -> Unit) {
    val lead = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.toPx() }
    val spec = remember(lead) { RowTopBringIntoView(lead) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec, content = content)
}

/** Wrapper: waagerecht nur so weit wie nötig scrollen. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MinimalScroll(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides MinimalBringIntoView, content = content)
}
