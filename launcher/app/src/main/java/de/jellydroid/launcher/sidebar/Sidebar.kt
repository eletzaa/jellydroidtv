package de.jellydroid.launcher.sidebar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import de.jellydroid.launcher.data.LauncherConfig
import de.jellydroid.launcher.data.NetInfo
import de.jellydroid.launcher.data.Places
import de.jellydroid.launcher.data.Sun
import de.jellydroid.launcher.data.ThemeMode
import de.jellydroid.launcher.home.MdiIcons
import de.jellydroid.launcher.ui.ChevronDownIcon
import de.jellydroid.launcher.ui.Pal
import de.jellydroid.launcher.data.readNet
import de.jellydroid.launcher.home.HaEntity
import de.jellydroid.launcher.home.HaPrefs
import de.jellydroid.launcher.home.HaSetupServer
import de.jellydroid.launcher.home.HaStatus
import de.jellydroid.launcher.home.HomeAssistant
import de.jellydroid.launcher.home.brightnessPct
import de.jellydroid.launcher.home.dimmable
import de.jellydroid.launcher.home.qrBitmap
import de.jellydroid.launcher.home.supportsKelvin
import de.jellydroid.launcher.ui.AirPlayIcon
import de.jellydroid.launcher.ui.BackIcon
import de.jellydroid.launcher.ui.BulbIcon
import de.jellydroid.launcher.ui.GearIcon
import de.jellydroid.launcher.ui.HouseIcon
import de.jellydroid.launcher.ui.PlugIcon
import de.jellydroid.launcher.ui.SceneIcon
import de.jellydroid.launcher.ui.DisplayFont
import de.jellydroid.launcher.ui.TextFont
import de.jellydroid.launcher.ui.SlidersIcon
import de.jellydroid.launcher.ui.SourceIcon
import de.jellydroid.launcher.ui.ThermoIcon
import de.jellydroid.launcher.ui.TvIcon
import de.jellydroid.launcher.ui.WifiIcon
import de.jellydroid.launcher.ui.appleSpring
import de.jellydroid.launcher.ui.okKey
import de.jellydroid.launcher.ui.rememberAvatar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import android.view.KeyEvent as AKey

/** Ansichten der Sidebar. */
enum class SideView { Main, Settings, Wifi, Ha }

/** Was die Sidebar draußen auslöst (Launcher-Fenster, Android-Einstellungen …). */
interface SidebarActions {
    fun openLauncherConfig()
    fun openTvSettings()
    fun openSources()
    fun openWifiSettings()
    fun airplayEnabled(): Boolean
    fun setAirplay(on: Boolean)
}

// Farben und Maße nach der abgenommenen Vorschau (1 cqw ≈ 9,6 dp auf dem 960-dp-Fernseher)
// Hell/Dunkel folgt dem Launcher-Design (Pal.light, bei "Dynamisch" nach Sonnenstand).
private object S {
    private val l get() = Pal.light
    val Text get() = if (l) Color(0xFF14171C) else Color(0xFFF4F6FB)
    val Dim get() = if (l) Color(0x9914171C) else Color(0x9EFFFFFF)
    val Panel get() = if (l) Color(0xF2F2F4F7) else Color(0xF0161A26)
    val Edge get() = if (l) Color(0x1F000000) else Color(0x29FFFFFF)
    val Tile get() = if (l) Color(0x0F000000) else Color(0x12FFFFFF)
    val TileOn get() = if (l) Color(0x47FFC266) else Color(0x2EFFD68C)
    val QTile get() = if (l) Color(0x1F3C78C8) else Color(0x2978AADC)
    val QTileOn get() = if (l) Color(0x5C5B8FF5) else Color(0x527AA7FF)
    val Warm get() = if (l) Color(0xFFE08A1E) else Color(0xFFFFD08A)
    val Heat get() = if (l) Color(0x24FF7A3D) else Color(0x24FF8C5A)
    val HeatIcon get() = if (l) Color(0xFFE5672F) else Color(0xFFFF9A6A)
    val Accent = Color(0xFF7AA7FF)
    /** Gewählter Filter: volle Fläche und Schrift darauf. */
    val Selected get() = if (l) Color(0xFF14171C) else Color.White
    val OnSelected get() = if (l) Color.White else Color(0xFF0D1220)
    val Focus get() = if (l) Color(0xFF14171C) else Color.White
    val Button get() = if (l) Color(0x14000000) else Color(0x1FFFFFFF)
    val Track get() = if (l) Color(0x1A000000) else Color(0x1FFFFFFF)
    val Shade get() = if (l) Color(0x33000000) else Color(0x73000000)
    val Overlay get() = if (l) Color(0xF5EEF1F5) else Color(0xF00E111A)
    val HeatGradient = listOf(Color(0xFF6AA8FF), Color(0xFFFFD08A), Color(0xFFFF7A4A))
}

/** Hell/Dunkel wie im Launcher (Konfiguration › Design); gilt auch, wenn die Sidebar über einer App liegt. */
@Composable
private fun FollowLauncherTheme() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    LaunchedEffect(tick) {
        val cfg = LauncherConfig(context.applicationContext)
        Pal.light = when (cfg.theme) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.AUTO -> {
                val pl = Places.all[cfg.place.coerceIn(0, Places.all.lastIndex)]
                Sun.isDaylight(pl.second, pl.third)
            }
        }
    }
}

private fun disp(size: Float, w: FontWeight = FontWeight.SemiBold) = TextStyle(fontFamily = DisplayFont, fontWeight = w, fontSize = size.sp)
private fun txt(size: Float, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = TextFont, fontWeight = w, fontSize = size.sp)

/**
 * Die Sidebar: Leiste von rechts mit Uhrzeit, Einstellungen und Kacheln.
 * Wird über allen Apps (Bedienungshilfe) und als Rückfall im Launcher gezeigt.
 */
@Composable
fun Sidebar(start: SideView, actions: SidebarActions, onClose: () -> Unit, visible: Boolean = true) {
    var view by remember { mutableStateOf(start) }
    var detail by remember { mutableStateOf<String?>(null) }
    var lastDetail by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(false) }
    var lastMainFocus by remember { mutableIntStateOf(0) }
    var roomsOpen by remember { mutableStateOf(false) }
    FollowLauncherTheme()
    val slide by animateFloatAsState(if (visible) 0f else 1f, tween(380), label = "side")
    val context = LocalContext.current
    LaunchedEffect(Unit) { HomeAssistant.start(context); HomeAssistant.applyDefaultRoom(context) }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                if (code != AKey.KEYCODE_BACK && code != AKey.KEYCODE_ESCAPE) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) when {
                    detail != null -> detail = null
                    setup -> setup = false
                    roomsOpen -> roomsOpen = false
                    view != SideView.Main -> view = SideView.Main
                    else -> onClose()
                }
                true
            },
    ) {
        // Abdunkeln nach rechts, damit die Leiste auch über hellen Bildern lesbar bleibt
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - slide }
                .background(Brush.horizontalGradient(0.3f to Color.Transparent, 1f to S.Shade)),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(15.dp)
                .width(298.dp)
                .fillMaxHeight()
                .graphicsLayer { translationX = size.width * 1.1f * slide }
                .clip(RoundedCornerShape(21.dp))
                .background(S.Panel)
                .border(1.dp, S.Edge, RoundedCornerShape(21.dp)),
        ) {
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    val forward = targetState != SideView.Main
                    (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(250)))
                        .togetherWith(slideOutHorizontally(tween(380)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                },
                label = "view",
            ) { v ->
                when (v) {
                    SideView.Main -> MainView(actions, lastMainFocus, onFocusIndex = { lastMainFocus = it }, go = { view = it })
                    SideView.Settings -> SettingsView(actions, onBack = { view = SideView.Main })
                    SideView.Wifi -> WifiView(actions, onBack = { view = SideView.Main })
                    SideView.Ha -> HaView(
                        onBack = { view = SideView.Main },
                        onDetail = { detail = it; lastDetail = it },
                        returnTo = lastDetail,
                        onSetup = { setup = true },
                        detailOpen = detail != null || setup,
                        roomsOpen = roomsOpen,
                        onRoomsOpen = { roomsOpen = it },
                    )
                }
            }
            detail?.let { id -> LightDetail(id, onClose = { detail = null }) }
            if (setup) SetupCard(onDone = { setup = false })
        }
    }
}

// ---------- Bausteine ----------

/** Fokus wie auf dem TV: leicht größer, weißer Rand. */
@Composable
private fun Modifier.sideFocus(interaction: MutableInteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, appleSpring(), label = "f")
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 2.dp else 0.dp, if (focused) S.Focus else Color.Transparent, shape)
}

@Composable
private fun Pressable(
    shape: Shape,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus?.invoke() }
    Box(
        modifier
            .sideFocus(interaction, shape)
            .clip(shape)
            .background(background)
            .then(if (onLongClick != null) Modifier.okKey(onClick, onLongClick) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

@Composable
private fun BackHeader(title: String, sub: String? = null, onBack: () -> Unit, focus: FocusRequester? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pressable(CircleShape, S.Button, onBack, Modifier.size(31.dp).then(focus?.let { Modifier.focusRequester(it) } ?: Modifier)) {
            BackIcon(S.Text, Modifier.align(Alignment.Center).size(16.dp))
        }
        Column {
            Text(title, style = disp(19f, FontWeight.Bold).copy(letterSpacing = (-0.02).em), color = S.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = txt(11f), color = S.Dim)
        }
    }
}

@Composable
private fun ViewColumn(scroll: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 18.dp, vertical = 21.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

// ---------- Hauptansicht ----------

@Composable
private fun MainView(actions: SidebarActions, lastFocus: Int, onFocusIndex: (Int) -> Unit, go: (SideView) -> Unit) {
    val context = LocalContext.current
    val focus = remember { List(5) { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { focus[lastFocus.coerceIn(0, 4)].requestFocus() } }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000L - System.currentTimeMillis() % 60_000L) } }
    var net by remember { mutableStateOf(readNet(context)) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); net = readNet(context) } }
    var airOn by remember { mutableStateOf(actions.airplayEnabled()) }

    ViewColumn {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("EE, d. MMM", Locale.GERMANY).format(now), style = disp(13f, FontWeight.Medium), color = S.Dim)
                Text(SimpleDateFormat("HH:mm", Locale.GERMANY).format(now), style = disp(25f, FontWeight.Bold).copy(letterSpacing = (-0.03).em), color = S.Text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Pressable(CircleShape, S.Button, { go(SideView.Settings) }, Modifier.size(40.dp).focusRequester(focus[0]), onFocus = { onFocusIndex(0) }) {
                        GearIcon(S.Text, Modifier.align(Alignment.Center).size(20.dp))
                    }
                    Text("Einstellungen", style = txt(10.5f), color = S.Dim)
                }
                val avatar = rememberAvatar(context)
                Box(Modifier.padding(top = 4.dp).size(33.dp).clip(CircleShape).background(Color(0xFF7A5AF5)), contentAlignment = Alignment.Center) {
                    if (avatar != null) Image(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(33.dp))
                }
            }
        }
        val ha = HomeAssistant
        val haSub = when {
            !HaPrefs(context).configured -> "Verbinden"
            ha.status == HaStatus.Ready && ha.roomName.isNotBlank() -> {
                val n = ha.roomIds.count { it.startsWith("light.") && ha.states[it]?.on == true }
                "${ha.roomName} · $n an"
            }
            ha.status == HaStatus.Ready -> "Raum wählen"
            ha.status == HaStatus.AuthFailed -> "Neu anmelden"
            else -> "Verbindet …"
        }
        val netName = when (net.kind) { NetInfo.Kind.Ethernet -> "Netzwerk"; else -> "WLAN" }
        val netSub = when (net.kind) {
            NetInfo.Kind.Ethernet -> "Kabel"
            NetInfo.Kind.None -> "Nicht verbunden"
            NetInfo.Kind.Wifi -> net.ssid ?: "Verbunden"
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Home Assistant", haSub, { HouseIcon(it, Modifier.size(21.dp)) }, on = false, Modifier.weight(1f).focusRequester(focus[1]),
                    onFocus = { onFocusIndex(1) }) { go(SideView.Ha) }
                QuickTile(netName, netSub, { WifiIcon(it, Modifier.size(21.dp), level = if (net.kind == NetInfo.Kind.Wifi) net.level else 4) }, on = false,
                    Modifier.weight(1f).focusRequester(focus[2]), onFocus = { onFocusIndex(2) }) { go(SideView.Wifi) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Quelle", "Eingang wählen", { SourceIcon(it, Modifier.size(21.dp)) }, on = false, Modifier.weight(1f).focusRequester(focus[3]),
                    onFocus = { onFocusIndex(3) }) { actions.openSources() }
                QuickTile("AirPlay", if (airOn) "An" else "Aus", { AirPlayIcon(it, Modifier.size(21.dp)) }, on = airOn, Modifier.weight(1f).focusRequester(focus[4]),
                    onFocus = { onFocusIndex(4) }, offColor = S.Tile) {
                    airOn = !airOn; actions.setAirplay(airOn)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    name: String,
    sub: String,
    icon: @Composable (Color) -> Unit,
    on: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    offColor: Color = S.QTile,
    onClick: () -> Unit,
) {
    // Umschalten: Blau weich einblenden, Kachel federt kurz
    val bg by animateColorAsState(if (on) S.QTileOn else offColor, tween(420), label = "qt")
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Pressable(RoundedCornerShape(17.dp), bg, {
        scope.launch {
            bounce.animateTo(.93f, tween(90))
            bounce.animateTo(1f, spring(dampingRatio = .42f, stiffness = 380f))
        }
        onClick()
    }, modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }.heightIn(min = 77.dp), onFocus = onFocus) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon(S.Text)
            Column {
                Text(name, style = disp(13.5f), color = S.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = txt(10.5f), color = S.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- Einstellungen ----------

@Composable
private fun SettingsView(actions: SidebarActions, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ViewColumn {
        BackHeader("Einstellungen", onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ListRow("Launcher Konfiguration", { SlidersIcon(it, Modifier.size(19.dp)) }, Modifier.focusRequester(first)) { actions.openLauncherConfig() }
            ListRow("TV-Einstellungen", { TvIcon(it, Modifier.size(19.dp)) }) { actions.openTvSettings() }
        }
    }
}

@Composable
private fun ListRow(label: String, icon: (@Composable (Color) -> Unit)?, modifier: Modifier = Modifier, meta: String? = null, onClick: () -> Unit) {
    Pressable(RoundedCornerShape(13.dp), S.Tile, onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            icon?.invoke(S.Dim)
            Text(label, style = disp(13.5f), color = S.Text, modifier = Modifier.weight(1f))
            if (meta != null) Text(meta, style = txt(11f, FontWeight.Medium), color = S.Dim)
        }
    }
}

// ---------- WLAN ----------

@Composable
private fun WifiView(actions: SidebarActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var net by remember { mutableStateOf(readNet(context)) }
    LaunchedEffect(Unit) { while (true) { delay(3_000); net = readNet(context) } }
    ViewColumn {
        BackHeader(if (net.kind == NetInfo.Kind.Ethernet) "Netzwerk" else "WLAN", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WifiIcon(S.Text, Modifier.size(48.dp), level = if (net.kind == NetInfo.Kind.Wifi) net.level else 4)
            val (title, sub) = when (net.kind) {
                NetInfo.Kind.Ethernet -> "Kabel" to "Verbunden"
                NetInfo.Kind.None -> "Nicht verbunden" to ""
                NetInfo.Kind.Wifi -> (net.ssid ?: "WLAN") to "Verbunden · Signal ${net.signalText}"
            }
            Text(title, style = disp(17f, FontWeight.Bold), color = S.Text)
            if (sub.isNotBlank()) Text(sub, style = txt(11f), color = S.Dim)
        }
        ListRow("Netzwerk wechseln", null, Modifier.focusRequester(first), meta = net.band) { actions.openWifiSettings() }
    }
}

// ---------- Home Assistant ----------

private enum class HaFilter(val label: String) { All("Alle"), Light("Licht"), Switch("Schalter") }

@Composable
private fun HaView(
    onBack: () -> Unit, onDetail: (String) -> Unit, onSetup: () -> Unit, detailOpen: Boolean, returnTo: String?,
    roomsOpen: Boolean, onRoomsOpen: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val ha = HomeAssistant
    val back = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    var filter by remember { mutableStateOf(HaFilter.All) }
    val configured = HaPrefs(context).configured
    val ids = ha.roomIds
    val entities = ids.mapNotNull { ha.states[it] }
    val lights = entities.filter { it.domain == "light" }
    val switches = entities.filter { it.domain == "switch" }
    val scenes = entities.filter { it.domain == "scene" }
    val climates = entities.filter { it.domain == "climate" }
    val devices = when (filter) { HaFilter.All -> lights + switches; HaFilter.Light -> lights; HaFilter.Switch -> switches }
    var focusedOnce by remember { mutableStateOf(false) }
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    val chevron = remember { FocusRequester() }
    // Raumwechsel: Fokus wieder auf die erste Kachel des neuen Raums
    LaunchedEffect(ha.roomId) { focusedOnce = false; tileFocus.clear() }
    LaunchedEffect(detailOpen, devices.isNotEmpty(), roomsOpen, ha.roomId) {
        if (detailOpen || roomsOpen) return@LaunchedEffect
        delay(30)
        // nach der Feinsteuerung zurück auf dieselbe Lampe, beim ersten Öffnen auf die erste Kachel
        val back2 = returnTo?.let { tileFocus[it] }
        runCatching {
            when {
                focusedOnce && back2 != null -> back2.requestFocus()
                devices.isNotEmpty() && !focusedOnce -> firstTile.requestFocus()
                !focusedOnce -> back.requestFocus()
            }
        }
        if (devices.isNotEmpty()) focusedOnce = true
    }

    ViewColumn(scroll = true) {
        if (configured && ha.status == HaStatus.Ready && ha.roomName.isNotBlank() && ha.rooms.size > 1) {
            RoomHeader(ha.roomName, roomsOpen, onBack, back, chevron, onToggle = { onRoomsOpen(!roomsOpen) })
            AnimatedVisibility(roomsOpen, enter = expandVertically(tween(260)) + fadeIn(tween(220)), exit = shrinkVertically(tween(220)) + fadeOut(tween(160))) {
                RoomList(ha.rooms, ha.roomId, ha.homeRoomId) { id ->
                    if (id != ha.roomId) { HomeAssistant.selectRoom(context, id); filter = HaFilter.All }
                    onRoomsOpen(false)
                }
            }
            LaunchedEffect(roomsOpen) { if (!roomsOpen && focusedOnce) runCatching { chevron.requestFocus() } }
        } else {
            BackHeader(ha.roomName.ifBlank { "Home Assistant" }, if (ha.roomName.isNotBlank()) "Home Assistant" else null, onBack, back)
        }
        when {
            !configured || ha.status == HaStatus.AuthFailed -> {
                Text(if (configured) "Anmeldung abgelaufen" else "Nicht verbunden", style = txt(12f), color = S.Dim)
                ListRow("Mit dem Handy verbinden", { HouseIcon(it, Modifier.size(19.dp)) }) { onSetup() }
            }
            ha.status != HaStatus.Ready -> Text("Verbindet …", style = txt(12f), color = S.Dim)
            ha.roomName.isBlank() -> Text("Fernseher in der Launcher Konfiguration wählen", style = txt(12f), color = S.Dim)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HaFilter.entries.forEach { f ->
                        val sel = f == filter
                        Pressable(RoundedCornerShape(50), if (sel) S.Selected else S.Tile, { filter = f }) {
                            Text(f.label, style = disp(11f, FontWeight.Medium), color = if (sel) S.OnSelected else S.Text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
                if (filter == HaFilter.All) {
                    scenes.chunked(3).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            line.forEach { s -> SceneButton(s, Modifier.weight(1f)) }
                            repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    climates.forEach { HeatTile(it) }
                }
                devices.chunked(2).forEachIndexed { r, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEachIndexed { c, e ->
                            val fr = if (r == 0 && c == 0) firstTile else tileFocus.getOrPut(e.id) { FocusRequester() }
                            tileFocus[e.id] = fr
                            DeviceTile(e, Modifier.weight(1f).focusRequester(fr), onDetail)
                        }
                        if (line.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (entities.isEmpty()) Text("Keine Geräte in diesem Raum", style = txt(12f), color = S.Dim)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** Kopf mit Raumname im Kasten und rundem Pfeil für die Raumauswahl. */
@Composable
private fun RoomHeader(name: String, open: Boolean, onBack: () -> Unit, back: FocusRequester, chevron: FocusRequester, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, appleSpring(), label = "chev")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pressable(CircleShape, S.Button, onBack, Modifier.size(31.dp).focusRequester(back)) {
            BackIcon(S.Text, Modifier.align(Alignment.Center).size(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f, fill = false)) {
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(10.dp))
                    .background(S.Tile)
                    .border(1.dp, S.Edge, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(name, style = disp(19f, FontWeight.Bold).copy(letterSpacing = (-0.02).em), color = S.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pressable(CircleShape, S.Button, onToggle, Modifier.size(24.dp).focusRequester(chevron)) {
                ChevronDownIcon(S.Text, Modifier.align(Alignment.Center).size(13.dp).graphicsLayer { rotationZ = turn })
            }
        }
    }
}

@Composable
private fun RoomList(rooms: List<Pair<String, String>>, current: String?, home: String?, onPick: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxWidth().background(S.Tile, RoundedCornerShape(13.dp)).border(1.dp, S.Edge, RoundedCornerShape(13.dp)).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rooms.forEachIndexed { i, (id, label) ->
            val sel = id == current
            Pressable(RoundedCornerShape(9.dp), if (sel) S.QTileOn else Color.Transparent, { onPick(id) },
                Modifier.fillMaxWidth().then(if (sel || (current !in rooms.map { it.first } && i == 0)) Modifier.focusRequester(first) else Modifier)) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = disp(13f, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = S.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (id == home) TvIcon(S.Dim, Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SceneButton(s: HaEntity, modifier: Modifier) {
    var flash by remember { mutableLongStateOf(0L) }
    LaunchedEffect(flash) { if (flash > 0) { delay(450); flash = 0 } }
    Pressable(RoundedCornerShape(11.dp), if (flash > 0) Color(0x527AA7FF) else S.Tile, {
        HomeAssistant.activateScene(s.id); flash = System.currentTimeMillis()
    }, modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MdiIcons.Icon(HomeAssistant.iconOf(s), S.Text, 18.dp) { SceneIcon(S.Text, Modifier.size(17.dp)) }
            Text(s.name, style = disp(11.5f), color = S.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceTile(e: HaEntity, modifier: Modifier, onDetail: (String) -> Unit) {
    val isLight = e.domain == "light"
    val on = e.on
    val pct = e.brightnessPct
    val status = when {
        !e.available -> "Nicht erreichbar"
        !on -> "Aus"
        isLight && e.dimmable -> "An · $pct %"
        else -> "An"
    }
    Pressable(
        RoundedCornerShape(13.dp), if (on) S.TileOn else S.Tile,
        onClick = { if (e.available) HomeAssistant.toggle(e) },
        modifier = modifier.heightIn(min = 71.dp),
        onLongClick = if (isLight && e.dimmable) ({ onDetail(e.id) }) else null,
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val ic = if (on) S.Warm else S.Dim
            if (isLight) BulbIcon(ic, Modifier.size(20.dp)) else PlugIcon(ic, Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(e.name, style = disp(12.5f), color = S.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = txt(10.5f), color = S.Dim, maxLines = 1)
            if (isLight && e.dimmable) {
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(S.Track)) {
                    if (on) Box(Modifier.fillMaxWidth(pct / 100f).height(3.dp).background(S.Warm))
                }
            }
        }
    }
}

private fun fmtTemp(v: Double) = String.format(Locale.GERMANY, "%.1f °C", v)

/** Heizung: breite Kachel, links/rechts ändert die Solltemperatur (Schritt aus HA, sonst 0,5 °C). */
@Composable
private fun HeatTile(e: HaEntity) {
    val min = e.attrs.optDouble("min_temp", 7.0)
    val max = e.attrs.optDouble("max_temp", 30.0)
    val step = e.attrs.optDouble("target_temp_step", 0.5).takeIf { it > 0 } ?: 0.5
    val haTarget = e.attrs.optDouble("temperature", Double.NaN)
    val ist = e.attrs.optDouble("current_temperature", Double.NaN)
    var target by remember(e.id) { mutableDoubleStateOf(if (haTarget.isNaN()) (min + max) / 2 else haTarget) }
    var edited by remember(e.id) { mutableLongStateOf(0L) }
    // Fremde Änderungen übernehmen, solange hier gerade niemand dreht
    LaunchedEffect(haTarget) { if (!haTarget.isNaN() && System.currentTimeMillis() - edited > 2_000) target = haTarget }
    LaunchedEffect(edited) {
        if (edited == 0L) return@LaunchedEffect
        delay(700)
        HomeAssistant.setTemperature(e.id, target)
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(13.dp)
    val heating = !ist.isNaN() && target > ist
    Column(
        Modifier
            .fillMaxWidth()
            .sideFocus(interaction, shape)
            .clip(shape)
            .background(S.Heat)
            .onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = when (k.nativeKeyEvent.keyCode) { AKey.KEYCODE_DPAD_LEFT -> -1; AKey.KEYCODE_DPAD_RIGHT -> 1; else -> return@onPreviewKeyEvent false }
                target = (Math.round((target + d * step) / step) * step).coerceIn(min, max)
                edited = System.currentTimeMillis()
                true
            }
            .focusable(interactionSource = interaction)
            .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThermoIcon(S.HeatIcon, Modifier.size(23.dp))
            Spacer(Modifier.width(9.dp))
            Text(fmtTemp(target), style = disp(19f, FontWeight.Bold).copy(letterSpacing = (-0.03).em), color = S.Text)
            Box(Modifier.padding(start = 8.dp, end = 1.dp).size(4.dp).background(S.Dim, CircleShape))
            Text(if (heating) "Heizen" else "Halten", style = txt(11f, FontWeight.Medium), color = S.Dim, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            if (!ist.isNaN()) Text("Ist " + fmtTemp(ist), style = txt(10.5f), color = S.Dim)
        }
        HeatTrack(min, max, target, ist)
    }
}

@Composable
private fun HeatTrack(min: Double, max: Double, target: Double, ist: Double) {
    val pT by animateFloatAsState(((target - min) / (max - min)).toFloat().coerceIn(0f, 1f), tween(150), label = "t")
    Canvas(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(21.dp)) {
        val h = 10.dp.toPx()
        val top = (size.height - h) / 2
        val brush = Brush.horizontalGradient(0f to S.HeatGradient[0], .55f to S.HeatGradient[1], 1f to S.HeatGradient[2], startX = 0f, endX = size.width)
        drawRoundRect(brush, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2), alpha = .32f)
        val pI = if (ist.isNaN()) Float.NaN else ((ist - min) / (max - min)).toFloat().coerceIn(0f, 1f)
        if (!pI.isNaN() && pT > pI) {
            drawRoundRect(brush, Offset(pI * size.width, top), Size((pT - pI) * size.width, h), CornerRadius(h / 2))
        }
        if (!pI.isNaN()) {
            val c = Offset(pI * size.width, size.height / 2)
            drawCircle(Color(0xFF2A2F3D), 4.5.dp.toPx() + 1.7.dp.toPx(), c)
            drawCircle(Color(0xD9FFFFFF), 4.5.dp.toPx(), c)
        }
        val tc = Offset(pT * size.width, size.height / 2)
        drawCircle(Color(0x66000000), 11.dp.toPx(), tc + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White, 10.5.dp.toPx(), tc)
    }
}

// ---------- Feinsteuerung Lampe ----------

@Composable
private fun LightDetail(id: String, onClose: () -> Unit) {
    val e = HomeAssistant.states[id] ?: return
    var pct by remember(id) { mutableIntStateOf(e.brightnessPct) }
    var edited by remember(id) { mutableLongStateOf(0L) }
    LaunchedEffect(e.brightnessPct) { if (System.currentTimeMillis() - edited > 1_500) pct = e.brightnessPct }
    LaunchedEffect(edited) { if (edited > 0) { delay(250); HomeAssistant.setBrightness(e, pct) } }
    val bar = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { bar.requestFocus() } }
    val barInteraction = remember { MutableInteractionSource() }
    var okArmed by remember(id) { mutableStateOf(false) }
    val temps = remember(id) {
        val lo = e.attrs.optInt("min_color_temp_kelvin", 2000); val hi = e.attrs.optInt("max_color_temp_kelvin", 6500)
        listOf(2700 to Color(0xFFFFB46B), 3500 to Color(0xFFFFE1B8), 5000 to Color(0xFFEEF3FF)).map { (k, c) -> k.coerceIn(lo, hi) to c }
    }
    Column(
        Modifier.fillMaxSize().background(S.Overlay),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically),
    ) {
        Text(e.name, style = disp(17f, FontWeight.Bold), color = S.Text)
        Box(
            Modifier
                .focusRequester(bar)
                .sideFocus(barInteraction, RoundedCornerShape(23.dp))
                .size(77.dp, 192.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(S.Track)
                .onPreviewKeyEvent { k ->
                    val code = k.nativeKeyEvent.keyCode
                    val ok = code == AKey.KEYCODE_DPAD_CENTER || code == AKey.KEYCODE_ENTER || code == AKey.KEYCODE_NUMPAD_ENTER
                    if (k.type == KeyEventType.KeyUp) {
                        if (ok && okArmed) onClose()
                        return@onPreviewKeyEvent ok
                    }
                    if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (code) {
                        AKey.KEYCODE_DPAD_UP -> { pct = (pct + 10).coerceAtMost(100); edited = System.currentTimeMillis(); true }
                        AKey.KEYCODE_DPAD_DOWN -> { pct = (pct - 10).coerceAtLeast(0); edited = System.currentTimeMillis(); true }
                        // Die OK-Taste ist vom langen Drücken noch unten (Wiederholungen + Loslassen kommen hier an).
                        // Erst ein neuer Druck schließt die Feinsteuerung, beim Loslassen.
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                            if (k.nativeKeyEvent.repeatCount == 0) okArmed = true
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = barInteraction),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val h by animateFloatAsState(pct / 100f, tween(180), label = "b")
            Box(Modifier.fillMaxWidth().fillMaxHeight(h).background(Brush.verticalGradient(listOf(Color(0xFFFFE2AD), Color(0xFFFFB85C)))))
        }
        Text(if (pct == 0) "Aus" else "$pct %", style = disp(34f, FontWeight.Bold).copy(letterSpacing = (-0.03).em), color = S.Text)
        if (e.supportsKelvin) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                temps.forEach { (k, c) ->
                    Pressable(CircleShape, c, { HomeAssistant.setKelvin(e, k) }, Modifier.size(29.dp)) {}
                }
            }
        }
    }
}

// ---------- Anmeldung per QR ----------

@Composable
private fun SetupCard(onDone: () -> Unit) {
    val context = LocalContext.current
    var addr by remember { mutableStateOf<String?>(null) }
    val server = remember { HaSetupServer(context) { onDone() } }
    androidx.compose.runtime.DisposableEffect(Unit) {
        addr = server.start()
        onDispose { server.stop() }
    }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(S.Panel).padding(19.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterVertically),
    ) {
        Text("Home Assistant verbinden", style = disp(18f, FontWeight.Bold), color = S.Text)
        val a = addr
        if (a != null) {
            val bmp = remember(a) { qrBitmap(a).asImageBitmap() }
            Image(bmp, "QR-Code", Modifier.size(150.dp).clip(RoundedCornerShape(11.dp)).background(Color.White).padding(8.dp))
            Text(a.removePrefix("http://").trimEnd('/'), style = txt(11f), color = S.Dim)
        } else {
            Text("Kein Netzwerk", style = txt(12f), color = S.Dim)
        }
        Pressable(RoundedCornerShape(50), S.Tile, onDone, Modifier.focusRequester(close)) {
            Text("Schließen", style = disp(11.5f, FontWeight.Medium), color = S.Text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}
