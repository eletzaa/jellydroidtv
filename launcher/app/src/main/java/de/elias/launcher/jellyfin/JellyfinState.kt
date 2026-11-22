package de.elias.launcher.jellyfin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface JellyfinStatus {
    data object Loading : JellyfinStatus
    data object LoggedOut : JellyfinStatus
    data class WaitingForCode(val code: String) : JellyfinStatus
    data object Ready : JellyfinStatus
    data class Error(val message: String) : JellyfinStatus
}

/** Hält Anmeldung und Reihen; die Oberfläche beobachtet nur diese Werte. */
class JellyfinState(val client: JellyfinClient, private val scope: CoroutineScope, val marks: LocalMarks? = null) {
    var status by mutableStateOf<JellyfinStatus>(if (client.isLoggedIn) JellyfinStatus.Loading else JellyfinStatus.LoggedOut)
        private set
    var continueWatching by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var popular by mutableStateOf<List<MediaItem>>(emptyList()); private set
    /** Genre-Reihen für Entdecken (Name + Titel). */
    var genreRows by mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()); private set
    private var genresLoadedAt = 0L
    /** Verbindungsproblem trotz Anmeldung (für den Hinweis in der Leiste). */
    var offline by mutableStateOf(false); private set

    private var qcJob: Job? = null

    fun refresh() {
        if (!client.isLoggedIn) {
            if (status !is JellyfinStatus.WaitingForCode) status = JellyfinStatus.LoggedOut
            return
        }
        scope.launch {
            try {
                val (cw, pop) = withContext(Dispatchers.IO) { client.continueWatching() to client.latest() }
                val hidden = marks?.hiddenSeries ?: emptySet()
                continueWatching = cw.filter { it.seriesId == null || it.seriesId !in hidden }
                popular = pop
                offline = false
                status = JellyfinStatus.Ready
                // Genres ändern sich selten: höchstens alle 30 Minuten neu laden
                if (System.currentTimeMillis() - genresLoadedAt > 30 * 60_000) {
                    genreRows = withContext(Dispatchers.IO) {
                        client.genres().mapNotNull { g -> runCatching { g to client.byGenre(g) }.getOrNull()?.takeIf { it.second.size >= 4 } }
                    }
                    genresLoadedAt = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                if (!client.isLoggedIn) status = JellyfinStatus.LoggedOut
                else {
                    offline = true
                    if (status !is JellyfinStatus.Ready) status = JellyfinStatus.Error("Jellyfin nicht erreichbar")
                }
            }
        }
    }

    fun startQuickConnect() {
        qcJob?.cancel()
        // Keine vorbelegte Adresse: ohne eingetragenen Server gibt es nichts zu verbinden
        if (client.baseUrl.isBlank()) {
            status = JellyfinStatus.Error("Keine Jellyfin-Adresse eingetragen (Zahnrad › Konfiguration › Konten)")
            return
        }
        qcJob = scope.launch {
            try {
                val start = withContext(Dispatchers.IO) { client.quickConnectStart() }
                status = JellyfinStatus.WaitingForCode(start.code)
                val until = System.currentTimeMillis() + 10 * 60_000
                while (isActive && System.currentTimeMillis() < until) {
                    delay(3000)
                    if (withContext(Dispatchers.IO) { client.quickConnectApproved(start.secret) }) {
                        withContext(Dispatchers.IO) { client.quickConnectFinish(start.secret) }
                        status = JellyfinStatus.Loading
                        refresh()
                        return@launch
                    }
                }
                status = JellyfinStatus.LoggedOut
            } catch (e: JellyfinClient.HttpException) {
                status = JellyfinStatus.Error(if (e.code == 401 || e.code == 403) "Quick Connect ist auf dem Server aus" else "Anmeldung fehlgeschlagen (${e.code})")
            } catch (e: Exception) {
                status = JellyfinStatus.Error("Jellyfin nicht erreichbar")
            }
        }
    }

    /** „Abschließen“: als gesehen markieren, sofort aus Weiterschauen nehmen, dann neu laden. */
    fun complete(context: android.content.Context, item: MediaItem) {
        continueWatching = continueWatching.filter { it.id != item.id }
        scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { client.markPlayed(item.id) } }.isSuccess
            if (!ok) android.widget.Toast.makeText(context, "Konnte nicht als gesehen markieren", android.widget.Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    /** Serie dieses Titels (Folge oder Serie selbst), sonst null. */
    private fun seriesOf(item: MediaItem): String? = item.seriesId ?: item.id.takeIf { item.type == "Series" }

    /** „Serie ausblenden“: verschwindet lokal aus Weiterschauen, in Jellyfin ändert sich nichts. */
    fun hideSeries(context: android.content.Context, item: MediaItem) {
        val sid = seriesOf(item) ?: return
        marks?.hideSeries(sid)
        continueWatching = continueWatching.filter { it.seriesId != sid }
        android.widget.Toast.makeText(context, "„${item.title}“ ausgeblendet", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** „Serie gesehen“: alle Folgen in Jellyfin als gesehen markieren. */
    fun seriesWatched(context: android.content.Context, item: MediaItem) {
        val sid = seriesOf(item) ?: return
        continueWatching = continueWatching.filter { it.seriesId != sid }
        scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { client.markPlayed(sid) } }.isSuccess
            android.widget.Toast.makeText(context, if (ok) "„${item.title}“ als gesehen markiert" else "Konnte nicht als gesehen markieren", android.widget.Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    /** Bild und Ton für das Detail-Fenster (leer, wenn unbekannt). */
    suspend fun techInfo(item: MediaItem): List<String> =
        runCatching { withContext(Dispatchers.IO) { client.techInfo(item) } }.getOrDefault(emptyList())

    /** Startet Plezy; bei Folgen werden die Kennungen der Serie nachgeladen. */
    /** Konfiguration › Jellyfin: neue Adresse (meldet ab) bzw. neu per Quick Connect verbinden. */
    fun setUrl(url: String) {
        client.baseUrl = url
        continueWatching = emptyList(); popular = emptyList(); genreRows = emptyList(); genresLoadedAt = 0L
        status = if (client.isLoggedIn) JellyfinStatus.Loading else JellyfinStatus.LoggedOut
        refresh()
    }

    fun reconnect() {
        client.logout()
        continueWatching = emptyList(); popular = emptyList()
        status = JellyfinStatus.LoggedOut
        startQuickConnect()
    }

    /** Paket der App, mit der Mediathek-Titel geöffnet werden (Konfiguration › Jellyfin). */
    var playerApp: String = "com.edde746.plezy"

    fun play(context: android.content.Context, item: MediaItem) {
        if (playerApp != "com.edde746.plezy") {
            // Andere Jellyfin-Apps bieten keinen Link auf einen einzelnen Titel: App öffnen, Titel nennen
            val pm = context.packageManager
            val intent = pm.getLeanbackLaunchIntentForPackage(playerApp) ?: pm.getLaunchIntentForPackage(playerApp)
            if (intent != null) {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                android.widget.Toast.makeText(context, "„${item.title}“ in der App auswählen", android.widget.Toast.LENGTH_LONG).show()
                return
            }
        }
        // Bevorzugt: exakter Link über Server- und Titel-ID. Die Suche über TMDB/IMDb fand
        // Titel auf dem eigenen Server nicht ("Couldn't find this title in your library").
        scope.launch {
            val server = runCatching { withContext(Dispatchers.IO) { client.serverId() } }.getOrNull()
            // Seerr-Titel tragen die Jellyfin-ID nur, wenn sie schon in der Mediathek sind
            val itemId = if (item.isSeerr) item.jellyfinId else item.id
            if (server != null && itemId != null) MediaActions.playItem(context, server, itemId)
            else playByCatalog(context, item)
        }
    }

    private fun playByCatalog(context: android.content.Context, item: MediaItem) {
        if (item.type == "Movie") {
            MediaActions.play(context, "movie", item.tmdb, item.imdb, null)
            return
        }
        scope.launch {
            val ids = if (item.type == "Episode" && item.seriesId != null) {
                runCatching { withContext(Dispatchers.IO) { client.seriesIds(item.seriesId) } }.getOrNull()
            } else Triple(item.tmdb, item.imdb, item.tvdb)
            MediaActions.play(context, "show", ids?.first, ids?.second, ids?.third)
        }
    }
}
