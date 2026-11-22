package de.elias.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import de.elias.launcher.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.elias.launcher.data.SystemActions
import de.elias.launcher.glass.GlassState
import de.elias.launcher.glass.glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.tv.material3.Text

@Composable
fun TopBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    glass: GlassState,
    hidden: Boolean,
    actions: SystemActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Beim Runterscrollen gleitet die Leiste nach oben weg, damit nie Kacheln unter dem Glas liegen.
    val hideOffset by animateFloatAsState(if (hidden) -140f else 0f, appleSpring(), label = "bar")
    Row(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(0, with(density) { hideOffset.dp.roundToPx() }) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCapsule(tabs, selected, onSelect, glass, actions)
        SystemCapsule(glass, actions)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TabCapsule(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    glass: GlassState,
    actions: SystemActions,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val tabX = remember { mutableStateMapOf<Int, Float>() }
    val tabW = remember { mutableStateMapOf<Int, Float>() }
    val density = LocalDensity.current

    // Kommt der Fokus von unten in die Leiste, landet er immer auf dem aktiven Tab,
    // statt auf dem nächstgelegenen (das hat bisher ungewollt den Tab gewechselt).
    val tabFocus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }

    val lensX by animateFloatAsState(tabX[selected] ?: 0f, appleSpring(), label = "lensX")
    val lensW by animateFloatAsState(tabW[selected] ?: 0f, appleSpring(), label = "lensW")

    Row(
        Modifier
            .glass(glass, "a_tabs")
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .onFocusChanged { rowFocused = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            if (lensW > 0f) {
                Box(
                    Modifier
                        .offset { IntOffset(lensX.roundToInt(), 0) }
                        .width(with(density) { lensW.toDp() })
                        .height(34.dp)
                        .glass(glass, "b_lens", strength = 1.3f, tint = if (rowFocused) 0.78f else 0.06f),
                )
            }
            Row(
                Modifier
                    .focusProperties { enter = { tabFocus.getOrNull(selected) ?: FocusRequester.Default } }
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, title ->
                    TabItem(
                        title = title,
                        active = i == selected,
                        lensBright = rowFocused && i == selected,
                        onFocus = { onSelect(i) },
                        modifier = Modifier.focusRequester(tabFocus[i]).onGloballyPositioned {
                            tabX[i] = it.positionInParent().x
                            tabW[i] = it.size.width.toFloat()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(title: String, active: Boolean, lensBright: Boolean, onFocus: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        when {
            lensBright -> InkInverse
            active -> Ink
            else -> Ink.copy(alpha = .78f)
        },
        label = "tabColor",
    )
    Box(
        modifier
            .height(34.dp)
            .clip(CircleShape)
            .onFocusChanged { if (it.isFocused && de.elias.launcher.NavKeys.recent()) onFocus() }
            // ohne Standard-Fokusschimmer: der hat beim Tabwechsel kurz dunkel aufgeblitzt
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFocus)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = LauncherType.Tab.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium), color = color)
    }
}

@Composable
private fun SystemCapsule(glass: GlassState, actions: SystemActions) {
    Row(
        Modifier
            .glass(glass, "a_system")
            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        Box(
            Modifier
                .padding(start = 8.dp, end = 6.dp)
                .size(4.dp)
                .background(Ink.copy(alpha = .55f), CircleShape),
        )
        CircleButton(onClick = actions::openAccount) { color ->
            // Profilbild legt der Installer ab: /sdcard/Android/data/de.elias.launcher/files/avatar.jpg
            // (oder .png). Ohne Datei erscheint ein neutrales Symbol.
            val context = androidx.compose.ui.platform.LocalContext.current
            val avatar = rememberAvatar(context)
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = "Konto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(26.dp).clip(CircleShape),
                )
            } else {
                Box(Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Text("•", style = LauncherType.Tab, color = color)
                }
            }
        }
        // Sidebar (Einstellungen, Quelle, WLAN, AirPlay) und direkt Home Assistant
        CircleButton(onClick = { de.elias.launcher.sidebar.SidebarController.open(de.elias.launcher.sidebar.SideView.Main) }) { color -> SidebarIcon(color) }
        CircleButton(onClick = { de.elias.launcher.sidebar.SidebarController.open(de.elias.launcher.sidebar.SideView.Ha) }) { color -> HouseIcon(color) }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) Ink.copy(alpha = .9f) else Color.Transparent, label = "btn")
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (focused) InkInverse else Ink)
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(
        SimpleDateFormat("HH:mm", Locale.GERMANY).format(now),
        style = LauncherType.Clock,
        color = Ink,
    )
}
