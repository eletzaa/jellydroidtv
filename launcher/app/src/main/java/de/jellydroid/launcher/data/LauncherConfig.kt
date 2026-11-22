package de.jellydroid.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.jellydroid.launcher.airplay.AirPlay

/** TMDB-Filmgenres (IDs), wie Seerr sie zum Filtern erwartet. */
object Genres {
    val all = listOf(
        28 to "Action", 12 to "Abenteuer", 16 to "Animation", 35 to "Komödie", 80 to "Krimi",
        99 to "Dokumentation", 18 to "Drama", 10751 to "Familie", 14 to "Fantasy", 36 to "Historie",
        27 to "Horror", 10402 to "Musik", 9648 to "Mystery", 10749 to "Liebesfilm", 878 to "Science-Fiction",
        53 to "Thriller", 10752 to "Kriegsfilm", 37 to "Western",
    )
    fun name(id: Int) = all.firstOrNull { it.first == id }?.second ?: "Genre"
}

enum class ThemeMode(val label: String) { DARK("Dunkel"), LIGHT("Hell"), AUTO("Dynamisch") }

/**
 * Zentrale Konfiguration des Launchers (Zahnrad › Konfiguration). Alle Werte sind Compose-State,
 * die Oberfläche reagiert also sofort; gespeichert wird in SharedPreferences.
 */
class LauncherConfig(context: Context) {
    private val app = context.applicationContext
    private val p = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(p.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK)); private set
    /** Mindestbewertung bei TMDB (0–10). */
    var minRating by mutableStateOf(p.getFloat("min_rating", 7.0f)); private set
    /** Mindestanzahl an Bewertungen, damit die Note aussagekräftig ist. */
    var minVotes by mutableStateOf(p.getInt("min_votes", 300)); private set
    /** Wie weit zurück (Jahre, 0 = egal). */
    var yearsBack by mutableStateOf(p.getInt("years_back", 3)); private set
    /** Nur Filme, die schon fürs Heimkino erschienen sind (Blu-ray/digital zu kaufen oder leihen). */
    var homeReleaseOnly by mutableStateOf(p.getBoolean("home_release", true)); private set
    /** Bereits Gesehenes ausblenden. */
    var hideWatched by mutableStateOf(p.getBoolean("hide_watched", true)); private set
    var showRequests by mutableStateOf(p.getBoolean("show_requests", true)); private set
    var genres by mutableStateOf(p.getString("genres", "28,35,18,53,878,80")!!.split(',').mapNotNull { it.toIntOrNull() }); private set

    /** Streamingdienste, die Elias nutzt, und davon die mit eigener Reihe „Neu bei …“. */
    var servicesUsed by mutableStateOf(p.getStringSet("services_used", setOf("netflix"))!!.toSet()); private set
    var servicesRows by mutableStateOf(p.getStringSet("services_rows", setOf("netflix"))!!.toSet()); private set
    /** App, mit der Titel aus der Jellyfin-Mediathek geöffnet werden. */
    var jellyfinApp by mutableStateOf(p.getString("jellyfin_app", "com.edde746.plezy")!!); private set
    /** Ort für „Dynamisch“ (Index in Places.all). */
    var place by mutableStateOf(p.getInt("place", 0)); private set
    var jellyfinUrl by mutableStateOf(p.getString("jellyfin_url", "")!!); private set
    var seerrUrl by mutableStateOf(p.getString("seerr_url", "")!!); private set

    fun toggleServiceUsed(key: String) {
        servicesUsed = if (key in servicesUsed) servicesUsed - key else servicesUsed + key
        if (key !in servicesUsed) servicesRows = servicesRows - key
        p.edit().putStringSet("services_used", servicesUsed).putStringSet("services_rows", servicesRows).apply(); changed()
    }
    fun toggleServiceRow(key: String) {
        servicesRows = if (key in servicesRows) servicesRows - key else servicesRows + key
        if (key in servicesRows) servicesUsed = servicesUsed + key
        p.edit().putStringSet("services_used", servicesUsed).putStringSet("services_rows", servicesRows).apply(); changed()
    }
    fun updateJellyfinApp(pkg: String) { jellyfinApp = pkg; p.edit().putString("jellyfin_app", pkg).apply() }
    fun updatePlace(i: Int) { place = i.coerceIn(0, Places.all.lastIndex); p.edit().putInt("place", place).apply() }
    fun updateJellyfinUrl(u: String) { jellyfinUrl = u.trim().trimEnd('/'); p.edit().putString("jellyfin_url", jellyfinUrl).apply() }
    fun updateSeerrUrl(u: String) { seerrUrl = u.trim().trimEnd('/'); p.edit().putString("seerr_url", seerrUrl).apply() }

    /** AirPlay-Empfänger (Bildschirmsynchronisierung vom iPhone/Mac). */
    var airplayEnabled by mutableStateOf(p.getBoolean("airplay_enabled", true)); private set
    var airplayName by mutableStateOf(p.getString("airplay_name", AirPlay.DEFAULT_NAME)!!); private set
    var airplayPin by mutableStateOf(p.getBoolean("airplay_pin", false)); private set

    /** Die Sidebar schaltet AirPlay direkt in den Prefs; beim Zurückkehren neu lesen. */
    fun reloadExternal() { airplayEnabled = p.getBoolean("airplay_enabled", true) }
    fun updateAirplayEnabled(v: Boolean) { airplayEnabled = v; p.edit().putBoolean("airplay_enabled", v).apply(); syncAirPlay(app) }
    fun updateAirplayName(v: String) { airplayName = v.trim().ifBlank { AirPlay.DEFAULT_NAME }; p.edit().putString("airplay_name", airplayName).apply(); syncAirPlay(app) }
    fun updateAirplayPin(v: Boolean) { airplayPin = v; p.edit().putBoolean("airplay_pin", v).apply(); syncAirPlay(app) }

    /** Zähler, der bei jeder Änderung hochgeht (Seerr-Reihen neu laden). */
    var version by mutableStateOf(0); private set

    private fun changed() { version++ }

    fun updateTheme(t: ThemeMode) { theme = t; p.edit().putString("theme", t.name).apply() }
    fun updateMinRating(v: Float) { minRating = v.coerceIn(5f, 9f); p.edit().putFloat("min_rating", minRating).apply(); changed() }
    fun updateMinVotes(v: Int) { minVotes = v; p.edit().putInt("min_votes", v).apply(); changed() }
    fun updateYearsBack(v: Int) { yearsBack = v; p.edit().putInt("years_back", v).apply(); changed() }
    fun updateHomeReleaseOnly(v: Boolean) { homeReleaseOnly = v; p.edit().putBoolean("home_release", v).apply(); changed() }
    fun updateHideWatched(v: Boolean) { hideWatched = v; p.edit().putBoolean("hide_watched", v).apply(); changed() }
    fun updateShowRequests(v: Boolean) { showRequests = v; p.edit().putBoolean("show_requests", v).apply() }
    fun toggleGenre(id: Int) {
        genres = if (id in genres) genres - id else Genres.all.map { it.first }.filter { it in genres || it == id }
        p.edit().putString("genres", genres.joinToString(",")).apply(); changed()
    }

    companion object {
        val VOTE_STEPS = listOf(50, 100, 200, 300, 500, 1000, 2000)
        val YEAR_STEPS = listOf(1, 2, 3, 5, 10, 0)
    }
}

/** Startet oder stoppt den AirPlay-Empfänger gemäß Konfiguration (beim Start, nach Neustart, bei Änderungen). */
fun syncAirPlay(context: Context) {
    val p = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    AirPlay.apply(
        context,
        enabled = p.getBoolean("airplay_enabled", true),
        name = p.getString("airplay_name", AirPlay.DEFAULT_NAME)!!,
        requirePin = p.getBoolean("airplay_pin", false),
    )
}
