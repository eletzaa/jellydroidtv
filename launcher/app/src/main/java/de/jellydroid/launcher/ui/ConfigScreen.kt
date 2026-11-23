package de.jellydroid.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import de.jellydroid.launcher.data.Genres
import de.jellydroid.launcher.data.JellyfinApps
import de.jellydroid.launcher.data.LauncherConfig
import de.jellydroid.launcher.data.Places
import de.jellydroid.launcher.data.Services
import de.jellydroid.launcher.data.Sun
import de.jellydroid.launcher.data.ThemeMode
import de.jellydroid.launcher.jellyfin.JellyfinState
import de.jellydroid.launcher.jellyfin.JellyfinStatus
import de.jellydroid.launcher.jellyfin.SeerrState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.view.KeyEvent as AKey

/** Zentrale Konfiguration: Design, Filter für Beliebt und Genres, sichtbare Reihen. */
@Composable
fun ConfigDialog(cfg: LauncherConfig, jf: JellyfinState, seerr: SeerrState, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf("main") } // main, jellyfin, seerr
    var seerrLogin by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (page == "main") onDismiss() else page = "main" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember(page) { FocusRequester() }
        LaunchedEffect(page) { runCatching { fr.requestFocus() } }
        val context = LocalContext.current
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .padding(vertical = 28.dp)
                    .width(860.dp)
                    .clip(shape)
                    .background(Surface)
                    .border(1.dp, Ink.copy(alpha = .18f), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp, vertical = 24.dp),
            ) {
              when (page) {
                "jellyfin" -> JellyfinPage(cfg, jf, fr, onBack = { page = "main" })
                "seerr" -> SeerrPage(cfg, seerr, fr, onLogin = { seerrLogin = true }, onBack = { page = "main" })
                "airplay" -> AirPlayPage(cfg, fr, onBack = { page = "main" })
                "ha" -> HomeAssistantPage(fr, onBack = { page = "main" })
                else -> {
                Text("Launcher Konfiguration", style = LauncherType.Headline.copy(fontSize = 28.sp), color = Ink)

                Section("Sidebar")
                val haPrefs = remember { de.jellydroid.launcher.home.HaPrefs(context) }
                NavRow("Home Assistant", when {
                    !haPrefs.configured -> "nicht verbunden"
                    de.jellydroid.launcher.home.HomeAssistant.roomName.isNotBlank() -> de.jellydroid.launcher.home.HomeAssistant.roomName
                    else -> "verbunden"
                }) { page = "ha" }
                MenuKeyRow()

                Section("Design")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEachIndexed { i, t ->
                        PillButton(
                            (if (cfg.theme == t) "✓  " else "") + t.label,
                            primary = cfg.theme == t,
                            onClick = { cfg.updateTheme(t) },
                            modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val place = Places.all[cfg.place.coerceIn(0, Places.all.lastIndex)]
                StepperRow("Standort für „Dynamisch“", place.first) { d -> cfg.updatePlace(cfg.place + d) }

                Section("Konten")
                NavRow("Jellyfin-Mediathek", cfg.jellyfinUrl.removePrefix("https://")) { page = "jellyfin" }
                NavRow("Seerr", if (seerr.loggedIn) "angemeldet" else "nicht angemeldet") { page = "seerr" }

                Section("AirPlay")
                NavRow("Bildschirmsynchronisierung", if (cfg.airplayEnabled) cfg.airplayName else "aus") { page = "airplay" }


                Section("Wiedergabe")
                val apps = remember { JellyfinApps.installed(context) }
                StepperRow("Mediathek öffnen mit", apps.firstOrNull { it.pkg == cfg.jellyfinApp }?.name ?: "Plezy") { d ->
                    if (apps.isNotEmpty()) {
                        val i = (apps.indexOfFirst { it.pkg == cfg.jellyfinApp }.takeIf { it >= 0 } ?: 0) + d
                        cfg.updateJellyfinApp(apps[i.coerceIn(0, apps.lastIndex)].pkg)
                    }
                }

                Section("Streamingdienste")
                val services = remember { Services.installed(context) }
                services.forEach { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(s.name, style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink, modifier = Modifier.width(220.dp))
                        val used = s.key in cfg.servicesUsed
                        val row = s.key in cfg.servicesRows
                        PillButton((if (used) "✓  " else "") + "Nutze ich", primary = used, onClick = { cfg.toggleServiceUsed(s.key) })
                        PillButton((if (row) "✓  " else "") + "Reihe in Entdecken", primary = row, onClick = { cfg.toggleServiceRow(s.key) })
                    }
                }

                Section("Beliebt und Genres auf Entdecken")
                StepperRow("Mindestbewertung (TMDB)", String.format("%.1f", cfg.minRating)) { d -> cfg.updateMinRating(cfg.minRating + d * 0.5f) }
                StepperRow("Mindestanzahl Bewertungen", "${cfg.minVotes}") { d ->
                    val s = LauncherConfig.VOTE_STEPS
                    val i = (s.indexOf(cfg.minVotes).takeIf { it >= 0 } ?: 3) + d
                    cfg.updateMinVotes(s[i.coerceIn(0, s.lastIndex)])
                }
                StepperRow("Zeitraum", if (cfg.yearsBack == 0) "beliebig" else if (cfg.yearsBack == 1) "letztes Jahr" else "letzte ${cfg.yearsBack} Jahre") { d ->
                    val s = LauncherConfig.YEAR_STEPS
                    val i = (s.indexOf(cfg.yearsBack).takeIf { it >= 0 } ?: 2) + d
                    cfg.updateYearsBack(s[i.coerceIn(0, s.lastIndex)])
                }
                ToggleRow("Nur Filme, die schon fürs Heimkino erschienen sind (Blu-ray/digital)", cfg.homeReleaseOnly) { cfg.updateHomeReleaseOnly(it) }
                ToggleRow("Bereits Gesehenes ausblenden", cfg.hideWatched) { cfg.updateHideWatched(it) }
                jf.marks?.let { m ->
                    val n = m.hiddenSeries.size + m.notInterested.size
                    if (n > 0) Row(Modifier.padding(bottom = 6.dp)) {
                        PillButton("Ausgeblendete Titel zurückholen", primary = false, onClick = { m.reset(); jf.refresh() })
                    }
                }

                Section("Reihen")
                ToggleRow("Deine Anfragen", cfg.showRequests) { cfg.updateShowRequests(it) }

                Section("Genres (je eine Reihe auf Entdecken)")
                Genres.all.chunked(5).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        line.forEach { (id, name) ->
                            val on = id in cfg.genres
                            PillButton((if (on) "✓  " else "") + name, primary = on, onClick = { cfg.toggleGenre(id) })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PillButton("Fertig", primary = true, onClick = onDismiss)
                }
              }
            }
        }
    }
    if (seerrLogin) SeerrLoginDialog(seerr, onDismiss = { seerrLogin = false })
}

/** Untermenü Jellyfin: Adresse ändern, neu per Quick Connect verbinden. */
@Composable
private fun JellyfinPage(cfg: LauncherConfig, jf: JellyfinState, fr: FocusRequester, onBack: () -> Unit) {
    var url by remember { mutableStateOf(cfg.jellyfinUrl) }
    Text("Jellyfin-Mediathek", style = LauncherType.Headline.copy(fontSize = 28.sp), color = Ink)
    Spacer(Modifier.height(4.dp))
    val status = when (val s = jf.status) {
        JellyfinStatus.Ready -> "verbunden"
        JellyfinStatus.Loading -> "lädt …"
        is JellyfinStatus.WaitingForCode -> "wartet auf Quick Connect"
        is JellyfinStatus.Error -> s.message
        JellyfinStatus.LoggedOut -> "nicht angemeldet"
    }
    Text("Status: $status", style = LauncherType.Body, color = InkDim)
    Section("Adresse")
    TextInput("Server-Adresse", url, { url = it }, modifier = Modifier.focusRequester(fr))
    Spacer(Modifier.height(10.dp))
    PillButton("Adresse übernehmen", primary = false, onClick = { cfg.updateJellyfinUrl(url); jf.setUrl(cfg.jellyfinUrl) })
    Section("Anmeldung")
    (jf.status as? JellyfinStatus.WaitingForCode)?.let {
        Text("Code in Jellyfin unter Profil › Quick Connect eingeben:", style = LauncherType.Body, color = InkDim)
        Text(it.code, style = LauncherType.Headline.copy(fontSize = 44.sp, letterSpacing = 6.sp), color = Ink)
        Spacer(Modifier.height(10.dp))
    }
    PillButton("Neu verbinden (Quick Connect)", primary = true, onClick = { jf.reconnect() })
    Spacer(Modifier.height(18.dp))
    PillButton("‹  Zurück", primary = false, onClick = onBack)
}

/** Untermenü Seerr: Adresse ändern, an- und abmelden. */
@Composable
private fun SeerrPage(cfg: LauncherConfig, seerr: SeerrState, fr: FocusRequester, onLogin: () -> Unit, onBack: () -> Unit) {
    var url by remember { mutableStateOf(cfg.seerrUrl) }
    Text("Seerr", style = LauncherType.Headline.copy(fontSize = 28.sp), color = Ink)
    Spacer(Modifier.height(4.dp))
    Text("Status: " + if (seerr.loggedIn) "angemeldet" else "nicht angemeldet", style = LauncherType.Body, color = InkDim)
    Section("Adresse")
    TextInput("Seerr-Adresse", url, { url = it }, modifier = Modifier.focusRequester(fr))
    Spacer(Modifier.height(10.dp))
    PillButton("Adresse übernehmen", primary = false, onClick = { cfg.updateSeerrUrl(url); seerr.setUrl(cfg.seerrUrl) })
    Section("Anmeldung")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(if (seerr.loggedIn) "Neu anmelden" else "Anmelden", primary = true, onClick = onLogin)
        if (seerr.loggedIn) PillButton("Abmelden", primary = false, onClick = { seerr.logout() })
    }
    Spacer(Modifier.height(18.dp))
    PillButton("‹  Zurück", primary = false, onClick = onBack)
}

/** Untermenü AirPlay: Empfänger an/aus, Name auf dem iPhone, Code beim Verbinden. */
@Composable
private fun AirPlayPage(cfg: LauncherConfig, fr: FocusRequester, onBack: () -> Unit) {
    var name by remember { mutableStateOf(cfg.airplayName) }
    Text("AirPlay", style = LauncherType.Headline.copy(fontSize = 28.sp), color = Ink)
    Spacer(Modifier.height(4.dp))
    Text("Status: " + if (cfg.airplayEnabled) "bereit als „${cfg.airplayName}“" else "aus", style = LauncherType.Body, color = InkDim)
    Section("Empfänger")
    Box(Modifier.focusRequester(fr)) { ToggleRow("AirPlay-Empfänger", cfg.airplayEnabled) { cfg.updateAirplayEnabled(it) } }
    ToggleRow("Code beim Verbinden verlangen", cfg.airplayPin) { cfg.updateAirplayPin(it) }
    Section("Name")
    TextInput("Name auf dem iPhone", name, { name = it })
    Spacer(Modifier.height(10.dp))
    PillButton("Name übernehmen", primary = false, onClick = { cfg.updateAirplayName(name); name = cfg.airplayName })
    Spacer(Modifier.height(18.dp))
    PillButton("‹  Zurück", primary = false, onClick = onBack)
}

/** Menü-Taste der Fernbedienung anlernen: OK drücken, dann die gewünschte Taste. */
@Composable
private fun MenuKeyRow() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("config", android.content.Context.MODE_PRIVATE) }
    fun current(): String {
        val k = prefs.getInt("sidebar_key", -1)
        return when {
            k > 0 -> AKey.keyCodeToString(k).removePrefix("KEYCODE_")
            prefs.getString("sidebar_window", null) != null -> "Schnelleinstellungen"
            else -> "Menu (Standard)"
        }
    }
    var label by remember { mutableStateOf(current()) }
    var waiting by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { de.jellydroid.launcher.HomeButtonService.learnKey = null } }
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        kotlinx.coroutines.delay(15_000)
        if (waiting) { de.jellydroid.launcher.HomeButtonService.learnKey = null; waiting = false; hint = "Keine Taste erkannt" }
    }
    NavRow("Menü-Taste anlernen", if (waiting) "jetzt Taste drücken …" else label) {
        if (!de.jellydroid.launcher.HomeButtonService.running) { hint = "Die Bedienungshilfe des Launchers ist aus"; return@NavRow }
        hint = null
        waiting = true
        de.jellydroid.launcher.HomeButtonService.learnKey = { l -> label = l; waiting = false; hint = "Gespeichert" }
    }
    if (waiting) Text("Drück jetzt die Taste, die die Sidebar öffnen soll.", style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink, modifier = Modifier.padding(start = 18.dp, bottom = 8.dp))
    hint?.let { Text(it, style = LauncherType.Body, color = InkDim, modifier = Modifier.padding(start = 18.dp, bottom = 8.dp)) }
    if (!waiting && (prefs.getInt("sidebar_key", -1) > 0 || prefs.getString("sidebar_window", null) != null)) {
        PillButton("Standard wiederherstellen", primary = false, onClick = {
            prefs.edit().remove("sidebar_key").remove("sidebar_window").apply(); label = current(); hint = null
        })
        Spacer(Modifier.height(8.dp))
    }
}

/** Untermenü Home Assistant: Anmeldung, Raum (über den Fernseher), Türklingel. */
@Composable
private fun HomeAssistantPage(fr: FocusRequester, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { de.jellydroid.launcher.home.HaPrefs(context) }
    val ha = de.jellydroid.launcher.home.HomeAssistant
    var tick by remember { mutableStateOf(0) } // nach Änderungen neu lesen
    var qr by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ha.start(context) }
    @Suppress("UNUSED_EXPRESSION") tick
    val configured = prefs.configured
    Text("Home Assistant", style = LauncherType.Headline.copy(fontSize = 28.sp), color = Ink)
    Spacer(Modifier.height(4.dp))
    val status = when {
        !configured -> "nicht verbunden"
        ha.status == de.jellydroid.launcher.home.HaStatus.Ready -> if (ha.roomName.isNotBlank()) "verbunden · ${ha.roomName}" else "verbunden"
        ha.status == de.jellydroid.launcher.home.HaStatus.AuthFailed -> "Anmeldung abgelaufen"
        ha.status == de.jellydroid.launcher.home.HaStatus.Error -> "nicht erreichbar"
        else -> "verbindet …"
    }
    Text("Status: $status", style = LauncherType.Body, color = InkDim)

    Section("Anmeldung")
    if (qr) {
        val server = remember { de.jellydroid.launcher.home.HaSetupServer(context) { qr = false; tick++ } }
        var addr by remember { mutableStateOf<String?>(null) }
        androidx.compose.runtime.DisposableEffect(Unit) { addr = server.start(); onDispose { server.stop() } }
        addr?.let { a ->
            val bmp = remember(a) { de.jellydroid.launcher.home.qrBitmap(a).asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                androidx.compose.foundation.Image(bmp, "QR-Code", Modifier.width(170.dp).height(170.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                Column {
                    Text("Mit dem Handy scannen", style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink)
                    Text(a.removePrefix("http://").trimEnd('/'), style = LauncherType.Body, color = InkDim)
                }
            }
        } ?: Text("Kein Netzwerk", style = LauncherType.Body, color = InkDim)
        Spacer(Modifier.height(10.dp))
        PillButton("Abbrechen", primary = false, onClick = { qr = false }, modifier = Modifier.focusRequester(fr))
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (configured) "Neu verbinden" else "Mit dem Handy verbinden", primary = true, onClick = { qr = true }, modifier = Modifier.focusRequester(fr))
            if (configured) PillButton("Abmelden", primary = false, onClick = { prefs.logout(); ha.stop(); tick++ })
        }
    }

    if (configured && !qr) {
        Section("Raum")
        fun pick(list: List<Pair<String, String>>, current: String, none: String?, d: Int): String {
            val ids = (if (none != null) listOf("") else emptyList()) + list.map { it.first }
            if (ids.isEmpty()) return current
            val i = (ids.indexOf(current).takeIf { it >= 0 } ?: 0) + d
            return ids[i.coerceIn(0, ids.lastIndex)]
        }
        fun label(list: List<Pair<String, String>>, id: String, none: String) = if (id.isBlank()) none else list.firstOrNull { it.first == id }?.second ?: id
        StepperRow("Fernseher in Home Assistant", label(ha.mediaPlayers, prefs.mediaPlayer, "nicht gewählt")) { d ->
            prefs.set("media_player", pick(ha.mediaPlayers, prefs.mediaPlayer, null, d)); tick++; ha.restart(context)
        }
        StepperRow("Standardraum in der Sidebar", label(ha.rooms, prefs.defaultRoom, "zuletzt gewählter")) { d ->
            prefs.set("default_room", pick(ha.rooms, prefs.defaultRoom, "", d)); tick++
        }
        Section("Türklingel")
        ToggleRow("Hinweis, wenn es klingelt", prefs.doorbellPopup) { prefs.setPopup(it); tick++ }
        if (prefs.doorbellPopup) {
            StepperRow("Klingel (Ereignis)", label(ha.doorbellEvents, prefs.doorbellEvent, "keine")) { d ->
                prefs.set("doorbell_event", pick(ha.doorbellEvents, prefs.doorbellEvent, "", d)); tick++
            }
            StepperRow("Türöffner", label(ha.doorOpeners, prefs.doorButton, "keiner")) { d ->
                prefs.set("door_button", pick(ha.doorOpeners, prefs.doorButton, "", d)); tick++
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    PillButton("‹  Zurück", primary = false, onClick = onBack)
}

/** Zeile, die ein Untermenü öffnet. */
@Composable
private fun NavRow(label: String, value: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(rowBackground(focused))
            .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .8f else .1f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink, modifier = Modifier.weight(1f))
        Text("$value   ›", style = LauncherType.Body.copy(fontSize = 15.sp), color = if (focused) Ink else InkDim)
    }
}

@Composable
private fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Text(label, style = LauncherType.Body.copy(fontSize = 12.sp), color = InkDim)
    Spacer(Modifier.height(4.dp))
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = LauncherType.Body.copy(color = Ink, fontSize = 17.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.copy(alpha = if (focused) .14f else .06f))
            .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .9f else .2f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title.uppercase(), style = LauncherType.Eyebrow, color = InkDim)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun rowBackground(focused: Boolean) = if (focused) Ink.copy(alpha = .14f) else Ink.copy(alpha = .04f)

/** Zeile mit Wert, der mit ‹ › verändert wird. */
@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(rowBackground(focused))
            .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .8f else .1f), shape)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    AKey.KEYCODE_DPAD_LEFT -> { onStep(-1); true }
                    AKey.KEYCODE_DPAD_RIGHT -> { onStep(1); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink, modifier = Modifier.weight(1f))
        Text("‹   $value   ›", style = LauncherType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = if (focused) Ink else InkDim)
    }
}

/** Zeile mit An/Aus, umschalten mit OK. */
@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(rowBackground(focused))
            .border(if (focused) 2.dp else 1.dp, Ink.copy(alpha = if (focused) .8f else .1f), shape)
            .clickable(interactionSource = interaction, indication = null) { onChange(!on) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LauncherType.Body.copy(fontSize = 15.sp), color = Ink, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (on) LauncherColors.Ok else Ink.copy(alpha = .18f))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text(if (on) "An" else "Aus", style = LauncherType.Body.copy(fontWeight = FontWeight.SemiBold), color = if (on) Color(0xFF0D1220) else Ink)
        }
    }
}
