package de.jellydroid.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import de.jellydroid.launcher.jellyfin.ImageCache
import de.jellydroid.launcher.jellyfin.SeerrState
import de.jellydroid.launcher.data.Genres
import de.jellydroid.launcher.data.LauncherConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import de.jellydroid.launcher.jellyfin.JellyfinState
import de.jellydroid.launcher.jellyfin.JellyfinStatus
import de.jellydroid.launcher.jellyfin.MediaActions
import de.jellydroid.launcher.jellyfin.MediaItem
import de.jellydroid.launcher.jellyfin.RemoteImage

val WatchTileWidth = 264.dp
val MediaTileWidth = 200.dp

/** Hintergrundbild des Titelbilds, hinter der ganzen Oberfläche (folgt der fokussierten Kachel). */
@Composable
fun HeroBackdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        // Weiche Überblendung beim Wechsel (Bilder werden vorab geladen, siehe DiscoverScreen)
        Crossfade(targetState = url, animationSpec = tween(900), label = "backdrop") { u ->
            RemoteImage(u, Modifier.fillMaxSize())
        }
        // Verläufe im Grundton des Designs (dunkel bzw. hell), damit die Schrift lesbar bleibt
        val b = Base
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to b.copy(alpha = .95f), 0.45f to b.copy(alpha = .6f), 1f to b.copy(alpha = .13f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to b.copy(alpha = .53f), 0.3f to Color.Transparent, 0.62f to b.copy(alpha = .8f), 1f to b)))
    }
}

@Composable
fun DiscoverScreen(
    jf: JellyfinState,
    seerr: SeerrState,
    cfg: LauncherConfig,
    onHeroImage: (String?) -> Unit,
    onScrolledDown: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var hero by remember { mutableStateOf<Pair<MediaItem, String>?>(null) } // fokussierte Kachel + Reihe
    var details by remember { mutableStateOf<MediaItem?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var rowsFocused by remember { mutableStateOf(false) }
    var focusedRow by remember { mutableIntStateOf(0) }
    val lastVertical = remember { longArrayOf(0L) }
    val scroll = rememberScrollState()
    // Die Leiste bleibt auf Entdecken immer sichtbar; das Titelbild steht fest, nur die Reihen scrollen.
    LaunchedEffect(Unit) { onScrolledDown(false); jf.refresh(); seerr.refresh() }
    // Konfiguration geändert: Reihen neu laden
    LaunchedEffect(cfg.version) { seerr.refresh() }
    // „Interessiert mich nicht“ (lokal gemerkt) blendet Titel überall auf Entdecken aus
    val marks = jf.marks
    fun List<MediaItem>.visible() = if (marks == null || marks.notInterested.isEmpty()) this else filter { !marks.isHidden(it) }
    val popularItems = (if (seerr.loggedIn && seerr.popular.isNotEmpty()) seerr.popular else jf.popular).visible()

    Column(Modifier.fillMaxSize()) {
        when (val s = jf.status) {
            JellyfinStatus.Ready -> {
                FeaturedHero(
                    items = popularItems,
                    eyebrow = "Beliebt",
                    focused = hero,
                    paused = rowsFocused,
                    onPlay = { primaryAction(context, it, jf, seerr) },
                    onDetails = { details = it },
                    onHeroImage = onHeroImage,
                    // erste Reihe = kompakt, ab der zweiten Reihe ist der Banner ganz weg
                    stage = if (!rowsFocused) 0 else if (focusedRow == 0) 1 else 2,
                )
                // Reihen rasten oben ein, damit Überschrift, Kachel und Titel immer ganz sichtbar sind
                val fadeTop by remember { derivedStateOf { scroll.value > 0 } }
                Box(Modifier.fillMaxWidth().weight(1f).fadeEdges(top = if (fadeTop) 24.dp else 0.dp)) { RowSnapScroll {
                Column(
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged { rowsFocused = it.hasFocus }
                        // Gegen „Doppelsprünge“: solange die Reihe noch einrastet, zählt ein weiterer
                        // Druck nach oben/unten nicht (der Fernseher meldet bei Ruckeln oft zwei Drücke).
                        .onPreviewKeyEvent { e ->
                            val code = e.nativeKeyEvent.keyCode
                            if (e.type != KeyEventType.KeyDown || (code != android.view.KeyEvent.KEYCODE_DPAD_DOWN && code != android.view.KeyEvent.KEYCODE_DPAD_UP)) return@onPreviewKeyEvent false
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastVertical[0] < 280) true else { lastVertical[0] = now; false }
                        }
                        .verticalScroll(scroll),
                ) {
                    // null als Reihe = Hinweis „Seerr verbinden“.
                    // Mit Seerr: Beliebt + Genres aus TMDB (Mediathek und Anfragen gemischt, Gesehenes raus).
                    // Ohne Seerr: wie bisher nur die eigene Mediathek.
                    val rows: List<Triple<String, String, List<MediaItem>>?> = buildList {
                        if (seerr.loggedIn) {
                            if (popularItems.isNotEmpty()) add(Triple("Beliebt", "Gerade gefragt", popularItems))
                            if (cfg.showRequests && seerr.requests.isNotEmpty()) add(Triple("Deine Anfragen", "Seerr", seerr.requests))
                            seerr.serviceRows.forEach { (key, items) ->
                                items.visible().takeIf { it.isNotEmpty() }?.let { add(Triple("Neu bei ${de.jellydroid.launcher.data.Services.byKey(key)?.name ?: key}", "Gut bewertet", it)) }
                            }
                            seerr.genreRows.forEach { (g, items) -> items.visible().takeIf { it.isNotEmpty() }?.let { add(Triple(Genres.name(g), "Beliebt im Genre", it)) } }
                        } else {
                            if (popularItems.isNotEmpty()) add(Triple("Neu in deiner Mediathek", "Jellyfin", popularItems))
                            add(null)
                            jf.genreRows.forEach { (genre, items) -> items.visible().takeIf { it.isNotEmpty() }?.let { add(Triple(genre, "Genre", it)) } }
                        }
                    }
                    rows.forEachIndexed { i, row ->
                        Column(Modifier.onFocusChanged { if (it.hasFocus) focusedRow = i }) {
                            if (row == null) SeerrConnectRow(onConnect = { showLogin = true })
                            else MediaRow(row.first, row.second, row.third, MediaTileWidth,
                                onFocus = { hero = it to row.first }, onClick = { details = it }, fadeEdge = false)
                        }
                    }
                    // Platz unten, damit auch die letzte Reihe oben einrasten kann
                    Spacer(Modifier.height(300.dp))
                }
                } }
            }
            JellyfinStatus.Loading -> Message("Entdecken", "Lade deine Mediathek …")
            else -> ConnectPanel(
                code = (s as? JellyfinStatus.WaitingForCode)?.code,
                error = (s as? JellyfinStatus.Error)?.message,
                onStart = { if (s is JellyfinStatus.Error && s.message.contains("erreichbar")) jf.refresh() else jf.startQuickConnect() },
            )
        }
    }

    details?.let { item ->
        DetailDialog(
            item = item,
            onDismiss = { details = null },
            onPlay = { details = null; primaryAction(context, item, jf, seerr) },
            onTrailer = { url -> MediaActions.trailer(context, url) },
            jf = jf,
            actions = if (marks == null) emptyList() else listOf("Interessiert mich nicht" to {
                marks.markNotInterested(item)
                details = null
                android.widget.Toast.makeText(context, "„${item.title}“ wird nicht mehr empfohlen", android.widget.Toast.LENGTH_SHORT).show()
            }),
        )
    }
    if (showLogin) SeerrLoginDialog(seerr, onDismiss = { showLogin = false })
}

@Composable
private fun SeerrConnectRow(onConnect: () -> Unit) {
    Column(Modifier.padding(start = ScreenStart, top = 10.dp, bottom = 18.dp)) {
        Text("Deine Anfragen und Trends", style = LauncherType.Row, color = Ink.copy(alpha = .92f))
        Spacer(Modifier.height(4.dp))
        Text("Melde dich einmal bei Seerr an, dann erscheinen hier deine Anfragen und was gerade beliebt ist.",
            style = LauncherType.Body, color = InkDim)
        Spacer(Modifier.height(12.dp))
        PillButton("Seerr verbinden", primary = true, onClick = onConnect)
    }
}

/** Einmalige Seerr-Anmeldung mit Jellyfin-Benutzer und Passwort. Gespeichert wird nur die Sitzung. */
@Composable
fun SeerrLoginDialog(seerr: SeerrState, onDismiss: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Oben angedockt und flach (Felder nebeneinander), damit das Fenster über der Bildschirmtastatur
        // ganz sichtbar bleibt; die Tastatur belegt am TV etwa die untere Hälfte.
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.TopCenter) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .padding(top = 28.dp)
                    .width(760.dp)
                    .clip(shape)
                    .background(Surface)
                    .border(1.dp, Ink.copy(alpha = .18f), shape)
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Text("Bei Seerr anmelden", style = LauncherType.Headline.copy(fontSize = 26.sp), color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("Mit deinem Jellyfin-Benutzer. Das Passwort wird nicht gespeichert, nur die Sitzung.",
                    style = LauncherType.Body, color = InkDim)
                Spacer(Modifier.height(14.dp))
                val passFr = remember { FocusRequester() }
                val submit = { if (!seerr.busy && user.isNotBlank() && pass.isNotEmpty()) seerr.login(user, pass, onDismiss) }
                // Tastatur: ✓ im Benutzernamen springt zum Passwort, ✓ im Passwort meldet an
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1f)) {
                        LoginField("Benutzername", user, { user = it }, password = false, modifier = Modifier.focusRequester(fr),
                            onDone = { runCatching { passFr.requestFocus() } })
                    }
                    Box(Modifier.weight(1f)) {
                        LoginField("Passwort", pass, { pass = it }, password = true, modifier = Modifier.focusRequester(passFr),
                            onDone = { submit() })
                    }
                }
                seerr.loginError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = LauncherType.Body, color = Color(0xFFFFB4A8))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(if (seerr.busy) "Melde an …" else "Anmelden", primary = true,
                        onClick = { submit() })
                    PillButton("Abbrechen", primary = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun LoginField(label: String, value: String, onChange: (String) -> Unit, password: Boolean, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column {
        Text(label, style = LauncherType.Body.copy(fontSize = 12.sp), color = Ink.copy(alpha = .7f))
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = LauncherType.Body.copy(color = Ink, fontSize = 18.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (password) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = if (password) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { onDone() }, onDone = { onDone() }),
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Ink.copy(alpha = if (focused) .16f else .08f))
                .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .9f else .2f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.padding(start = ScreenStart, end = ScreenEnd, top = 28.dp)) {
        Text(title, style = LauncherType.Headline, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(body, style = LauncherType.Body, color = InkDim)
    }
}

@Composable
private fun ConnectPanel(code: String?, error: String?, onStart: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(Modifier.padding(start = ScreenStart, end = ScreenEnd, top = 28.dp).widthIn(max = 600.dp)) {
        Text("JELLYFIN", style = LauncherType.Eyebrow, color = Ink.copy(alpha = .85f))
        Spacer(Modifier.height(6.dp))
        Text("Mit Jellyfin verbinden", style = LauncherType.Headline, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                code != null -> "Öffne Jellyfin auf dem Handy oder im Browser, geh zu Profil › Quick Connect und gib diesen Code ein:"
                error != null -> error
                else -> "Damit Weiterschauen und Beliebt hier erscheinen, meldest du dich einmal mit Quick Connect an. Ein Passwort brauchst du dafür nicht."
            },
            style = LauncherType.Body,
            color = if (error != null && code == null) Color(0xFFFFB4A8) else InkDim,
        )
        if (code != null) {
            Spacer(Modifier.height(14.dp))
            Text(code, style = LauncherType.Headline.copy(fontSize = 56.sp, letterSpacing = 6.sp), color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Der Code bleibt 10 Minuten gültig. Der Fernseher wartet auf deine Bestätigung …", style = LauncherType.Body, color = InkDim)
        }
        Spacer(Modifier.height(18.dp))
        // Immer derselbe Knopf, damit der Fokus beim Wechsel nicht verloren geht
        PillButton(
            when { code != null -> "Neuen Code holen"; error != null -> "Erneut versuchen"; else -> "Code anzeigen" },
            primary = true, onClick = onStart, modifier = Modifier.focusRequester(fr),
        )
    }
}

private const val HERO_INTERVAL_MS = 10_000

/** Beschriftung des Hauptknopfs: Seerr-Titel ohne Jellyfin-Kopie werden angefragt statt abgespielt. */
fun playLabelFor(item: MediaItem): String = when {
    item.service != null -> "▶  In ${de.jellydroid.launcher.data.Services.byKey(item.service)?.name ?: "App"} öffnen"
    item.isSeerr && item.jellyfinId == null -> if ((item.seerrMediaStatus ?: 1) <= 1) "＋  Anfragen" else "◷  Angefragt"
    item.type == "Episode" -> "▶  Weiterschauen"
    else -> "▶  Abspielen"
}

/** Hauptaktion passend zu [playLabelFor]. */
fun primaryAction(context: android.content.Context, item: MediaItem, jf: JellyfinState, seerr: SeerrState?) {
    when {
        item.service != null -> seerr?.openService(context, item)
        item.isSeerr && item.jellyfinId == null ->
            if ((item.seerrMediaStatus ?: 1) <= 1) seerr?.request(context, item)
            else android.widget.Toast.makeText(context, "Schon angefragt", android.widget.Toast.LENGTH_SHORT).show()
        else -> jf.play(context, item)
    }
}

/**
 * Banner mit Durchlauf (Entdecken und Startseite): bis zu 6 Titel mit Hintergrundbild,
 * Wechsel alle 10 s mit Überblendung. [focused] (fokussierte Kachel) hat Vorrang, solange [paused].
 * Rechts neben „Details“ lassen sich die Punkte anwählen und mit ‹ › manuell durchklicken.
 */
@Composable
fun FeaturedHero(
    items: List<MediaItem>,
    eyebrow: String,
    focused: Pair<MediaItem, String>?,
    paused: Boolean,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    onHeroImage: (String?) -> Unit,
    stage: Int = 0, // 0 = voll, 1 = kompakt, 2 = ausgeblendet
    onComplete: ((MediaItem) -> Unit)? = null, // nur Startseite: „Abschließen“
) {
    val featured = remember(items) { items.filter { it.backdropImage != null }.ifEmpty { items }.take(6) }
    var index by remember(featured) { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    var restart by remember { mutableIntStateOf(0) } // manuelles Blättern startet den Balken neu
    val scope = rememberCoroutineScope()
    LaunchedEffect(featured, paused, restart) {
        if (featured.size < 2 || paused) return@LaunchedEffect
        while (true) {
            // nächstes Bild schon laden, damit die Überblendung sofort sitzt
            featured[(index + 1) % featured.size].backdropImage?.let { ImageCache.load(it) }
            // nach einer Pause (Fokus in den Reihen) läuft der Balken vom alten Stand weiter
            val rest = (HERO_INTERVAL_MS * (1f - progress.value)).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(rest, easing = LinearEasing))
            progress.snapTo(0f)
            index = (index + 1) % featured.size
        }
    }
    val carousel = featured.getOrNull(index)?.let { it to eyebrow }
    // Beim schnellen Durchklicken nicht bei jeder Kachel Bild und Text überblenden:
    // erst wenn der Fokus kurz liegen bleibt (das war der Hauptgrund fürs Ruckeln auf Entdecken).
    var settled by remember { mutableStateOf(focused) }
    LaunchedEffect(focused?.first?.id) {
        if (focused != null && settled != null) kotlinx.coroutines.delay(260)
        settled = focused
    }
    val shown = if (paused && settled != null) settled else carousel ?: settled
    LaunchedEffect(shown?.first?.id) { onHeroImage(shown?.first?.backdropImage) }

    Hero(
        shown,
        dots = if (featured.size > 1) featured.size else 0,
        activeDot = index,
        dotProgress = { progress.value },
        dotsDimmed = paused,
        playLabel = shown?.first?.let { playLabelFor(it) } ?: "▶  Abspielen",
        onPlay = { shown?.let { onPlay(it.first) } },
        onDetails = { shown?.let { onDetails(it.first) } },
        onStep = { d ->
            val next = index + d
            if (next !in featured.indices) false
            else {
                index = next
                scope.launch { progress.snapTo(0f); restart++ }
                true
            }
        },
        stage = stage,
        onComplete = onComplete?.let { f -> { shown?.let { f(it.first) } } },
    )
}

@Composable
private fun Hero(
    shown: Pair<MediaItem, String>?,
    dots: Int,
    activeDot: Int,
    dotProgress: () -> Float,
    dotsDimmed: Boolean,
    playLabel: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onStep: (Int) -> Boolean,
    stage: Int,
    onComplete: (() -> Unit)? = null,
) {
    // Zwei Stufen beim Runterscrollen: 1 = kompakt (Titel, graue Zeile, Knöpfe), 2 = ganz weg.
    // Feste Höhen je Stufe, damit nichts springt; der Übergang ist animiert.
    val height by animateDpAsState(when (stage) { 0 -> 212.dp; 1 -> 136.dp; else -> 0.dp }, appleSpring(), label = "heroH")
    // auch in Stufe 2 bleibt Abstand zur Leiste, die oberste Reihe liegt nie bündig an
    val top by animateDpAsState(if (stage >= 2) 26.dp else 22.dp, appleSpring(), label = "heroTop")
    val alpha by animateFloatAsState(if (stage >= 2) 0f else 1f, tween(250), label = "heroA")
    Column(
        Modifier
            .padding(top = top)
            .height(height)
            .graphicsLayer { this.alpha = alpha; clip = true }
            // Seitenrand erst innerhalb des Ausschnitts, sonst wird der vergrößerte Fokus-Knopf links abgeschnitten
            .padding(start = ScreenStart, end = ScreenEnd),
    ) {
        Crossfade(targetState = shown, animationSpec = tween(600), label = "hero", modifier = Modifier.weight(1f)) { s ->
            val item = s?.first ?: return@Crossfade
            Column {
                androidx.compose.animation.AnimatedVisibility(visible = stage == 0) {
                    Column {
                        Text(s.second.uppercase(), style = LauncherType.Eyebrow, color = Ink.copy(alpha = .85f))
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Text(item.title, style = LauncherType.Headline, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 620.dp))
                Spacer(Modifier.height(6.dp))
                if (stage == 0) {
                    MetaLine(item)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.overview ?: "",
                        style = LauncherType.Body, color = InkDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                } else {
                    // kompakt: bei Folgen "S1 · F3 · Episodentitel", sonst Jahr · Dauer · Genre
                    item.subtitle?.let {
                        Text(it, style = LauncherType.Body, color = Ink.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (shown != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(playLabel, primary = true, onClick = onPlay)
                    PillButton("Details", primary = false, onClick = onDetails)
                    onComplete?.let { PillButton("✓  Gesehen", primary = false, onClick = it) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (dots > 0) CarouselDots(dots, activeDot, dotProgress, dimmed = dotsDimmed, onStep = onStep)
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Punkte wie bei Android TV: der laufende Titel ist ein breiter Balken, der sich in 10 s füllt. */
@Composable
private fun CarouselDots(count: Int, active: Int, progress: () -> Float, dimmed: Boolean, onStep: (Int) -> Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val alpha by animateFloatAsState(if (dimmed && !focused) .45f else 1f, label = "dots")
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(Ink.copy(alpha = if (focused) .18f else 0f))
            .border(1.dp, Ink.copy(alpha = if (focused) .6f else 0f), shape)
            // ‹ › blättern; links am ersten Punkt geht der Fokus zurück zu „Details“
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> onStep(-1)
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(1); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == active) 28.dp else 6.dp, appleSpring(), label = "dot")
            Box(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Ink.copy(alpha = if (i == active) .28f else .45f)),
            ) {
                if (i == active) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .drawBehind {
                                drawRect(Ink, size = size.copy(width = size.width * progress().coerceIn(0f, 1f)))
                            }
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaLine(item: MediaItem, tech: List<String> = emptyList()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item.communityRating?.let { Badge(if (item.isSeerr) "TMDB" else "IMDb", String.format("%.1f", it), Color(0xFFF5C518), Color.Black) }
        item.criticRating?.let { Badge(if (it >= 60) "🍅" else "🤢", "$it %", Color(0x33FFFFFF), Ink) }
        val sub = if (item.type == "Episode") null else item.subtitle
        sub?.let { Text(it, style = LauncherType.Body, color = Ink.copy(alpha = .8f)) }
        if (tech.isNotEmpty()) {
            if (sub != null) Text("·", style = LauncherType.Body, color = Ink.copy(alpha = .6f))
            TechLine(tech)
        }
        item.officialRating?.let { Text(it, style = LauncherType.Body, color = Ink.copy(alpha = .6f)) }
    }
}

/** Bild und Ton als schmale Rahmen-Etiketten, z. B. 4K · Dolby Vision · Dolby Atmos · 7.1. */
@Composable
private fun TechLine(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { t ->
            Box(Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, Ink.copy(alpha = .45f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(t, style = LauncherType.Tile.copy(fontSize = 11.sp), color = Ink.copy(alpha = .9f))
            }
        }
    }
}

@Composable
private fun Badge(label: String, value: String, bg: Color, fg: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text(label, style = LauncherType.Tile.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = fg)
        }
        Text(" $value", style = LauncherType.Body.copy(fontWeight = FontWeight.SemiBold), color = Ink)
    }
}

/** Runder Schließen-Knopf mit Kreuz (Detail-Popup oben rechts). */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, appleSpring(), label = "close")
    Box(
        modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Ink else Color(0x66000000))
            .border(1.dp, Ink.copy(alpha = if (focused) 0f else .3f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CloseIcon(if (focused) InkInverse else Ink, Modifier.size(16.dp))
    }
}

@Composable
fun PillButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, appleSpring(), label = "pill")
    val shape = RoundedCornerShape(50)
    val bg = when {
        focused -> Ink
        primary -> Ink.copy(alpha = .22f)
        else -> Ink.copy(alpha = .10f)
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(1.dp, Ink.copy(alpha = if (focused) 0f else .25f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, style = LauncherType.Tab, color = if (focused) InkInverse else Ink)
    }
}

@Composable
fun MediaRow(
    title: String,
    source: String,
    items: List<MediaItem>,
    tileWidth: Dp,
    onFocus: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
    focusId: String? = null, // diese Kachel holt sich einmal den Fokus (z. B. nach „Abschließen“)
    fadeEdge: Boolean = true,
) {
    Row(Modifier.padding(start = ScreenStart, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = LauncherType.Row, color = Ink.copy(alpha = .92f))
        Text("  $source", style = LauncherType.Body.copy(fontSize = 10.sp), color = Ink.copy(alpha = .55f))
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Fällt die erste Kachel weg (Abschließen), bleibt die Reihe sonst um den Rand verschoben
    LaunchedEffect(items.firstOrNull()?.id) { state.scrollToItem(0) }
    MinimalScroll {
    LazyRow(
        state = state,
        // Auf Entdecken blendet der Container einmal für alle Reihen aus (eine Ebene statt zehn)
        modifier = Modifier.fillMaxWidth().then(if (fadeEdge) Modifier.fadeRightEdge() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = ScreenStart, end = ScreenEnd + 96.dp, top = 12.dp, bottom = 16.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
            MediaTile(item, tileWidth,
                onFocus = {
                    // erste Kachel: Reihe ganz an den Anfang, damit sie bündig mit den Favoriten steht
                    if (i == 0 && state.firstVisibleItemScrollOffset != 0) scope.launch { state.animateScrollToItem(0) }
                    onFocus(item)
                },
                onClick = { onClick(item) },
                takeFocus = focusId == item.id)
        }
    }
    }
}

@Composable
private fun MediaTile(item: MediaItem, width: Dp, onFocus: () -> Unit, onClick: () -> Unit, takeFocus: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(takeFocus) { if (takeFocus) { kotlinx.coroutines.delay(150); runCatching { fr.requestFocus() } } }
    LaunchedEffect(focused) { if (focused) onFocus() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, appleSpring(), label = "media")
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (focused) 18.dp else 0.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
                .clip(shape)
                .background(TilePlaceholder)
                .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .9f else .12f), shape)
                .focusRequester(fr)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            RemoteImage(item.landscapeImage, Modifier.fillMaxSize())
            item.progress?.let { p ->
                // liegt auf dem Bild, deshalb in beiden Designs weiß
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .35f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(item.title, style = LauncherType.Tile, color = Ink.copy(alpha = if (focused) 1f else .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Unterzeile immer gleich hoch (auch leer): sonst ändert sich die Reihenhöhe beim
        // Durchzappen und der Bildschirm wackelt senkrecht
        Text(item.subtitle ?: "", style = LauncherType.Body.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Ink.copy(alpha = .55f), maxLines = 1, minLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DetailDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onTrailer: (String) -> Unit,
    onComplete: (() -> Unit)? = null,
    jf: JellyfinState? = null,
    /** Weitere Knöpfe in derselben Reihe (Markierungen wie „Serie ausblenden“). */
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var tech by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(item.id) { if (jf != null) tech = jf.techInfo(item) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .width(800.dp)
                    .clip(shape)
                    .background(Surface)
                    .border(1.dp, Ink.copy(alpha = .18f), shape),
            ) {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    RemoteImage(item.backdropImage, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Surface)))
                }
                Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 150.dp, bottom = 26.dp)) {
                    Text(item.title, style = LauncherType.Headline.copy(fontSize = 32.sp), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.type == "Episode") {
                        Text(item.subtitle ?: "", style = LauncherType.Body, color = Ink.copy(alpha = .75f))
                    }
                    Spacer(Modifier.height(8.dp))
                    MetaLine(item, tech)
                    Spacer(Modifier.height(12.dp))
                    Text(item.overview ?: "Keine Beschreibung vorhanden.", style = LauncherType.Body, color = InkDim, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (item.cast.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Mit " + item.cast.joinToString(", "), style = LauncherType.Body.copy(fontSize = 12.sp), color = Ink.copy(alpha = .6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(playLabelFor(item), primary = true, onClick = onPlay, modifier = Modifier.focusRequester(fr))
                        onComplete?.let { PillButton("✓  Gesehen", primary = false, onClick = it) }
                        item.trailerUrl?.let { url -> PillButton("Trailer ansehen", primary = false, onClick = { onTrailer(url) }) }
                        actions.forEach { (label, run) -> PillButton(label, primary = false, onClick = run) }
                    }
                }
                CloseButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(16.dp))
            }
        }
    }
}


/** Startseite oben: Banner mit Durchlauf über alles, was du gerade schaust. */
@Composable
fun StartHero(jf: JellyfinState, focused: MediaItem?, paused: Boolean, stage: Int, onDetails: (MediaItem) -> Unit, onHeroImage: (String?) -> Unit) {
    val context = LocalContext.current
    if (jf.status != JellyfinStatus.Ready || jf.continueWatching.isEmpty()) {
        LaunchedEffect(Unit) { onHeroImage(null) }
        Spacer(Modifier.height(24.dp))
        return
    }
    FeaturedHero(
        items = jf.continueWatching,
        eyebrow = "Weiterschauen",
        focused = focused?.let { it to "Weiterschauen" },
        paused = paused,
        onPlay = { jf.play(context, it) },
        onDetails = onDetails,
        onHeroImage = onHeroImage,
        stage = stage,
        onComplete = { jf.complete(context, it) },
    )
}

/** Weiterschauen für die Startseite: große Kacheln, der Banner folgt dem Fokus, Klick öffnet Details. */
@Composable
fun ContinueWatchingRow(jf: JellyfinState, onFocusItem: (MediaItem?) -> Unit, details: MediaItem?, onDetails: (MediaItem?) -> Unit) {
    val context = LocalContext.current
    var focusAfter by remember { mutableStateOf<String?>(null) } // Kachel, die nach „Abschließen“ den Fokus bekommt
    if (jf.status == JellyfinStatus.Ready && jf.continueWatching.isNotEmpty()) {
        Column(Modifier.onFocusChanged { if (!it.hasFocus) onFocusItem(null) }) {
            MediaRow("Weiterschauen", "Jellyfin", jf.continueWatching, WatchTileWidth,
                onFocus = { onFocusItem(it); if (it.id == focusAfter) focusAfter = null },
                onClick = { onDetails(it) }, focusId = focusAfter)
        }
    } else if (jf.status != JellyfinStatus.Ready && jf.status != JellyfinStatus.Loading) {
        Text(
            "Melde dich unter Entdecken bei Jellyfin an, dann erscheint hier Weiterschauen.",
            style = LauncherType.Body, color = InkDim,
            modifier = Modifier.padding(start = ScreenStart, top = 4.dp, bottom = 8.dp),
        )
    }
    details?.let { item ->
        val series = item.seriesId != null || item.type == "Series"
        DetailDialog(item, onDismiss = { onDetails(null) }, onPlay = { onDetails(null); jf.play(context, item) },
            onTrailer = { url -> MediaActions.trailer(context, url) },
            jf = jf,
            actions = if (!series) emptyList() else listOf(
                "Serie gesehen" to { onDetails(null); jf.seriesWatched(context, item) },
                "Serie ausblenden" to { onDetails(null); jf.hideSeries(context, item) },
            ),
            onComplete = {
                // nächste Kachel merken (sonst die davor), bevor die abgeschlossene verschwindet
                val list = jf.continueWatching
                val i = list.indexOfFirst { it.id == item.id }
                focusAfter = (list.getOrNull(i + 1) ?: list.getOrNull(i - 1))?.id
                onDetails(null); jf.complete(context, item)
            })
    }
}
