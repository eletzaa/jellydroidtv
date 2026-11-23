package de.jellydroid.launcher.jellyfin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jellyseerr (Adresse aus der Konfiguration). Anmeldung einmalig mit Jellyfin-Benutzer und Passwort;
 * gespeichert wird nur das Sitzungs-Cookie (connect.sid), nie das Passwort.
 */
class SeerrClient(context: Context, url: String = "") {
    private val prefs = context.getSharedPreferences("seerr", Context.MODE_PRIVATE)
    /** Adresse von Seerr (Konfiguration › Seerr). Beim Wechsel wird abgemeldet. */
    var url: String = url.trimEnd('/')
        set(v) { val n = v.trimEnd('/'); if (n != field) { field = n; logout() } }
    private val base get() = "$url/api/v1"
    private var cookie: String? = prefs.getString("cookie", null)
    var userId: Int = prefs.getInt("user_id", -1); private set
    val isLoggedIn get() = cookie != null && userId >= 0

    class HttpException(val code: Int, msg: String) : IOException(msg)

    companion object {
        /** Anbieter in DE (Kauf/Leihe/Abo): Apple, Google Play, Amazon Video, Rakuten, Sky Store, Netflix, Prime, Disney+, WOW. */
        const val HOME_PROVIDERS = "2|3|10|35|130|8|9|337|30"
    }

    fun logout() {
        cookie = null; userId = -1
        prefs.edit().clear().apply()
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            // Sitzung erneuert sich bei jeder Antwort; neues Cookie übernehmen
            conn.headerFields["Set-Cookie"]?.firstOrNull { it.startsWith("connect.sid=") }?.let {
                cookie = it.substringBefore(';')
                prefs.edit().putString("cookie", cookie).apply()
            }
            if (code == 401 || code == 403) { if (path != "/auth/jellyfin") logout() }
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw HttpException(code, "HTTP $code für $path ${err ?: ""}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Einmalige Anmeldung. Wirft HttpException(401/403) bei falschen Daten. */
    fun login(username: String, password: String) {
        cookie = null
        val o = JSONObject(request("/auth/jellyfin", "POST", JSONObject().put("username", username).put("password", password)))
        userId = o.optInt("id", -1)
        prefs.edit().putInt("user_id", userId).apply()
        if (cookie == null) throw IOException("Keine Sitzung erhalten")
    }

    /** Trends von TMDB über Seerr (Filme und Serien, ohne Personen). */
    fun trending(): List<MediaItem> {
        val a = JSONObject(request("/discover/trending?page=1&language=de")).optJSONArray("results") ?: JSONArray()
        return (0 until a.length()).mapNotNull { runCatching { parseDiscover(a.getJSONObject(it)) }.getOrNull() }
    }

    /** Filter aus der Konfiguration für „Beliebt“ und die Genre-Reihen. */
    data class Filter(val minRating: Float, val minVotes: Int, val yearsBack: Int, val homeRelease: Boolean)

    /**
     * Beliebte Filme nach Filter, optional je Genre, ohne [exclude] (TMDB-IDs, z. B. schon gesehen).
     * „Heimkino“: Seerr kennt keinen Blu-ray-Filter. Stattdessen: mindestens 60 Tage alt und in
     * Deutschland digital zu kaufen, leihen oder streamen. Das deckt sich fast immer mit der Blu-ray.
     */
    fun popularMovies(f: Filter, genre: Int? = null, exclude: Set<String> = emptySet(), want: Int = 20): List<MediaItem> {
        val q = buildString {
            append("/discover/movies?sortBy=popularity.desc&voteAverageGte=${f.minRating}&voteCountGte=${f.minVotes}")
            if (f.yearsBack > 0) append("&primaryReleaseDateGte=${daysAgo(365L * f.yearsBack)}")
            if (f.homeRelease) append("&primaryReleaseDateLte=${daysAgo(60)}&watchRegion=DE&watchProviders=$HOME_PROVIDERS")
            genre?.let { append("&genre=$it") }
        }
        val out = mutableListOf<MediaItem>()
        for (page in 1..3) {
            val batch = discover(q, "movie", page = page)
            out += batch.filter { it.tmdb !in exclude && out.none { o -> o.id == it.id } }
            if (out.size >= want || batch.size < 20) break
        }
        return out.take(want)
    }

    private fun discover(path: String, mediaType: String, service: String? = null, page: Int = 1): List<MediaItem> {
        val sep = if (path.contains('?')) '&' else '?'
        // Achtung: "language" filtert bei Seerr-Discover nach Originalsprache, deshalb hier weglassen
        val a = JSONObject(request("$path${sep}page=$page")).optJSONArray("results") ?: JSONArray()
        android.util.Log.i("JellydroidSeerr", "$path -> ${a.length()} Treffer")
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                if (!o.has("mediaType")) o.put("mediaType", mediaType)
                parseDiscover(o)?.copy(service = service)
            }.getOrNull()
        }
    }

    private fun daysAgo(d: Long) = java.time.LocalDate.now().minusDays(d).toString()

    /** Gut bewertete Neuheiten (letzte 2 Jahre) bei einem Streamingdienst in DE, Filme und Serien gemischt. */
    fun newOn(service: de.jellydroid.launcher.data.StreamService): List<MediaItem> {
        fun load(days: Long?, votes: Int): List<MediaItem> {
            val q = "watchRegion=DE&watchProviders=${service.tmdbProvider}&sortBy=popularity.desc&voteAverageGte=6.5&voteCountGte=$votes"
            val m = runCatching { discover("/discover/movies?$q" + (days?.let { "&primaryReleaseDateGte=${daysAgo(it)}" } ?: ""), "movie", service = service.key) }.getOrDefault(emptyList())
            val s = runCatching { discover("/discover/tv?$q" + (days?.let { "&firstAirDateGte=${daysAgo(it)}" } ?: ""), "tv", service = service.key) }.getOrDefault(emptyList())
            // abwechselnd Film und Serie
            return (0 until maxOf(m.size, s.size)).flatMap { listOfNotNull(m.getOrNull(it), s.getOrNull(it)) }
        }
        val fresh = load(730, 20)
        // zu wenige Neuheiten: mit den beliebtesten gut bewerteten Titeln des Dienstes auffüllen
        return (if (fresh.size >= 10) fresh else (fresh + load(null, 100)).distinctBy { it.id }).take(20)
    }

    /**
     * Dienstinterne Titelnummer über Wikidata (TMDB-ID -> z. B. Netflix-ID), damit der Titel direkt
     * aufgeht. Kein Schlüssel nötig; fehlt der Eintrag, wird nur die App geöffnet.
     */
    fun serviceTitleId(item: MediaItem, s: de.jellydroid.launcher.data.StreamService): String? {
        val tmdb = item.tmdb ?: return null
        val target = (if (item.type == "Series") s.wdShow else s.wdMovie) ?: return null
        val prop = if (item.type == "Series") "P4983" else "P4947"
        val sparql = "SELECT ?n WHERE { ?i wdt:$prop \"$tmdb\". ?i wdt:$target ?n } LIMIT 1"
        val conn = URL("https://query.wikidata.org/sparql?format=json&query=" + java.net.URLEncoder.encode(sparql, "UTF-8")).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6000; conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Jellydroid/0.3 (Android TV)")
            conn.setRequestProperty("Accept", "application/sparql-results+json")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            o.getJSONObject("results").getJSONArray("bindings").optJSONObject(0)?.getJSONObject("n")?.optString("value")?.ifBlank { null }
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    /** Eigene Anfragen, neueste zuerst, mit Titel und Bild aus den Detaildaten. */
    fun myRequests(limit: Int = 20): List<MediaItem> {
        if (userId < 0) return emptyList()
        val a = JSONObject(request("/request?take=$limit&skip=0&filter=all&sort=added&requestedBy=$userId")).optJSONArray("results") ?: JSONArray()
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val r = a.getJSONObject(i)
                val media = r.getJSONObject("media")
                val type = media.optString("mediaType").ifBlank { r.optString("type") }
                val tmdb = media.getInt("tmdbId")
                val detail = JSONObject(request(if (type == "tv") "/tv/$tmdb?language=de" else "/movie/$tmdb?language=de"))
                detail.put("mediaType", type)
                if (!detail.has("mediaInfo")) detail.put("mediaInfo", media)
                parseDiscover(detail)?.copy(seerrRequestStatus = r.optInt("status", 0))
            }.getOrNull()
        }
    }

    /** Neue Anfrage: Filme direkt, Serien mit allen Staffeln. */
    fun requestMedia(item: MediaItem) {
        val tmdb = item.tmdb?.toIntOrNull() ?: throw IOException("Keine TMDB-Kennung")
        val body = JSONObject().put("mediaId", tmdb)
        if (item.type == "Series") body.put("mediaType", "tv").put("seasons", "all") else body.put("mediaType", "movie")
        request("/request", "POST", body)
    }

    private fun parseDiscover(o: JSONObject): MediaItem? {
        val mediaType = o.optString("mediaType")
        if (mediaType != "movie" && mediaType != "tv") return null
        val tmdb = o.getInt("id")
        val title = o.optString(if (mediaType == "tv") "name" else "title").ifBlank { return null }
        val backdrop = o.optString("backdropPath").ifBlank { null }?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val date = o.optString(if (mediaType == "tv") "firstAirDate" else "releaseDate")
        val info = o.optJSONObject("mediaInfo")
        val genres = o.optJSONArray("genres")
        return MediaItem(
            id = "seerr-$mediaType-$tmdb",
            type = if (mediaType == "tv") "Series" else "Movie",
            name = title,
            seriesName = null,
            seriesId = null,
            episodeLabel = null,
            year = date.take(4).toIntOrNull(),
            overview = o.optString("overview").ifBlank { null },
            runtimeMin = o.optInt("runtime", 0).takeIf { it > 0 },
            progress = null,
            communityRating = o.optDouble("voteAverage").takeIf { !it.isNaN() && it > 0 },
            criticRating = null,
            officialRating = null,
            genres = if (genres == null) emptyList() else (0 until genres.length()).mapNotNull { genres.optJSONObject(it)?.optString("name") },
            tmdb = tmdb.toString(),
            imdb = null,
            tvdb = null,
            trailerUrl = null,
            cast = emptyList(),
            landscapeImage = backdrop?.replace("/w1280", "/w780"), // Kachel: kleineres Bild, schneller
            backdropImage = backdrop,
            seerrMediaStatus = info?.optInt("status", 1) ?: 1,
            jellyfinId = info?.optString("jellyfinMediaId")?.ifBlank { null },
        )
    }
}
