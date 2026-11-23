package de.jellydroid.launcher.jellyfin

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.jellydroid.launcher.data.LauncherConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Seerr-Reihen für Entdecken. „Beliebt“ und die Genre-Reihen kommen aus TMDB (über Seerr) und
 * mischen Titel aus der Mediathek (Abspielen) mit noch nicht vorhandenen (Anfragen).
 */
class SeerrState(
    private val client: SeerrClient,
    private val jellyfin: JellyfinClient,
    private val cfg: LauncherConfig,
    private val scope: CoroutineScope,
) {
    var loggedIn by mutableStateOf(client.isLoggedIn); private set
    var requests by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var popular by mutableStateOf<List<MediaItem>>(emptyList()); private set
    /** „Neu bei …“ je gewähltem Streamingdienst (Services.key zu Titeln). */
    var serviceRows by mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()); private set
    val isLoggedInNow get() = client.isLoggedIn
    val url get() = client.url
    fun setUrl(u: String) { client.url = u; loggedIn = client.isLoggedIn; requests = emptyList(); popular = emptyList(); genreRows = emptyList(); serviceRows = emptyList() }
    /** Genre-ID zu Titeln, in der Reihenfolge der Konfiguration. */
    var genreRows by mutableStateOf<List<Pair<Int, List<MediaItem>>>>(emptyList()); private set
    var loginError by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    private var loadedAt = 0L
    private var loadedVersion = -1

    fun refresh(force: Boolean = false) {
        if (!client.isLoggedIn) { loggedIn = false; return }
        val fresh = System.currentTimeMillis() - loadedAt < 5 * 60_000 && loadedVersion == cfg.version
        if (!force && fresh) return
        loadedAt = System.currentTimeMillis()
        loadedVersion = cfg.version
        val f = SeerrClient.Filter(cfg.minRating, cfg.minVotes, cfg.yearsBack, cfg.homeReleaseOnly)
        val genreIds = cfg.genres
        val hideWatched = cfg.hideWatched
        scope.launch {
            val watched = if (hideWatched) runCatching { withContext(Dispatchers.IO) { jellyfin.watchedMovieTmdbIds() } }.getOrDefault(emptySet()) else emptySet()
            runCatching { withContext(Dispatchers.IO) { client.popularMovies(f, exclude = watched) } }.onSuccess { popular = it }
            runCatching { withContext(Dispatchers.IO) { client.myRequests() } }.onSuccess { requests = it }
            val rowServices = de.jellydroid.launcher.data.Services.all.filter { it.key in cfg.servicesRows }
            serviceRows = rowServices.map { s ->
                async(Dispatchers.IO) { s.key to runCatching { client.newOn(s) }.getOrDefault(emptyList()).filter { m -> m.tmdb !in watched } }
            }.awaitAll().filter { it.second.isNotEmpty() }
            // Genres parallel laden. Ein Film darf in mehreren Genres stehen (Wunsch von Elias),
            // nur was schon in „Beliebt“ steht, wird dort nicht wiederholt.
            val shown = popular.map { it.id }.toSet()
            genreRows = genreIds.map { g ->
                async(Dispatchers.IO) { g to runCatching { client.popularMovies(f, genre = g, exclude = watched, want = 24) }.getOrDefault(emptyList()) }
            }.awaitAll()
                .map { (g, items) -> g to items.filter { it.id !in shown }.take(20) }
                .filter { it.second.size >= 4 }
            loggedIn = client.isLoggedIn
        }
    }

    fun login(username: String, password: String, onDone: () -> Unit) {
        busy = true; loginError = null
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { client.login(username.trim(), password) } }
            busy = false
            r.onSuccess { loggedIn = true; refresh(force = true); onDone() }
            r.onFailure { e ->
                loginError = if (e is SeerrClient.HttpException && (e.code == 401 || e.code == 403)) "Benutzername oder Passwort stimmt nicht" else "Seerr nicht erreichbar"
            }
        }
    }

    fun request(context: Context, item: MediaItem) {
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { client.requestMedia(item) } }
            Toast.makeText(context, if (r.isSuccess) "„${item.title}“ angefragt" else "Anfrage fehlgeschlagen", Toast.LENGTH_SHORT).show()
            if (r.isSuccess) refresh(force = true)
            loggedIn = client.isLoggedIn
        }
    }

    /**
     * Öffnet den Titel im Streamingdienst: direkt, wenn Wikidata die dienstinterne Nummer kennt
     * (Netflix, Disney+), sonst wird nur die App gestartet.
     */
    fun openService(context: Context, item: MediaItem) {
        val s = de.jellydroid.launcher.data.Services.byKey(item.service) ?: return
        val pkg = de.jellydroid.launcher.data.Services.packageOf(context, s)
        if (pkg == null) { Toast.makeText(context, "${s.name} ist nicht installiert", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            val id = withContext(Dispatchers.IO) { client.serviceTitleId(item, s) }
            val link = id?.let { if (item.type == "Series") s.linkShow?.invoke(it) else s.linkMovie?.invoke(it) }
            val direct = link?.let { android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it)).setPackage(pkg) }
            val launch = context.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: context.packageManager.getLaunchIntentForPackage(pkg)
            val ok = listOfNotNull(direct, launch).any { intent ->
                runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
            if (!ok) Toast.makeText(context, "${s.name} lässt sich nicht öffnen", Toast.LENGTH_SHORT).show()
            else if (direct == null) Toast.makeText(context, "„${item.title}“ in ${s.name} suchen", Toast.LENGTH_LONG).show()
        }
    }

    fun logout() { client.logout(); loggedIn = false; requests = emptyList(); popular = emptyList(); genreRows = emptyList() }
}
