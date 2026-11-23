package de.jellydroid.launcher.jellyfin

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ein Film, eine Serie oder eine Folge aus Jellyfin, so weit der Launcher sie braucht. */
data class MediaItem(
    val id: String,
    val type: String, // Movie, Series, Episode
    val name: String,
    val seriesName: String?,
    val seriesId: String?,
    val episodeLabel: String?, // z. B. "S2 · F5"
    val seasonNo: Int = -1,
    val episodeNo: Int = -1,
    val year: Int?,
    val overview: String?,
    val runtimeMin: Int?,
    val progress: Float?, // 0..1, nur bei Weiterschauen
    val remainingSec: Long? = null, // Restlaufzeit, nur bei angefangenen Titeln
    val communityRating: Double?, // IMDb-ähnlich, 0..10
    val criticRating: Int?, // Rotten Tomatoes in Prozent
    val officialRating: String?,
    val genres: List<String>,
    val tmdb: String?,
    val imdb: String?,
    val tvdb: String?,
    val trailerUrl: String?,
    val cast: List<String>,
    /** Querformat-Bild: Thumb, sonst Backdrop (bei Folgen das der Serie). */
    val landscapeImage: String?,
    val backdropImage: String?,
    /** Nur bei Seerr-Titeln: 1 unbekannt, 2 angefragt, 3 in Arbeit, 4 teilweise, 5 verfügbar. */
    val seerrMediaStatus: Int? = null,
    /** Nur bei eigenen Anfragen: 1 wartet auf Freigabe, 2 freigegeben, 3 abgelehnt. */
    val seerrRequestStatus: Int? = null,
    /** Jellyfin-ID, falls ein Seerr-Titel schon in der Mediathek ist. */
    val jellyfinId: String? = null,
    /** Läuft im Netflix-Abo (Reihe „Neu auf Netflix“): wird in Netflix geöffnet statt in Plezy. */
    /** Streamingdienst (Services.key), bei dem der Titel im Abo läuft: wird dort geöffnet statt in Plezy. */
    val service: String? = null,
) {
    val isSeerr: Boolean get() = seerrMediaStatus != null
    /** Kurzer Status für Seerr-Titel, z. B. in der Kachel-Unterzeile. */
    val seerrLabel: String?
        get() = when {
            seerrMediaStatus == null -> null
            seerrMediaStatus >= 5 -> "Verfügbar"
            seerrMediaStatus == 4 -> "Teilweise verfügbar"
            seerrRequestStatus == 3 -> "Abgelehnt"
            seerrMediaStatus == 3 -> "Wird geladen"
            seerrMediaStatus == 2 || seerrRequestStatus == 1 -> "Angefragt"
            else -> null
        }
    val title: String get() = seriesName ?: name
    val subtitle: String?
        get() = when {
            type == "Episode" -> listOfNotNull(episodeLabel, name).joinToString(" · ")
            else -> listOfNotNull(de.jellydroid.launcher.data.Services.byKey(service)?.name ?: seerrLabel, year?.toString(), runtimeMin?.let { "$it Min." }, genres.firstOrNull()).joinToString(" · ").ifBlank { null }
        }
}

data class QuickConnectStart(val code: String, val secret: String)

/**
 * Schlanker Jellyfin-Client ohne zusätzliche Bibliothek (HttpURLConnection + org.json).
 * Anmeldung über Quick Connect, damit kein Passwort im Code oder auf dem Fernseher getippt werden muss.
 */
class JellyfinClient(context: Context, url: String = "") {
    private val prefs = context.getSharedPreferences("jellyfin", Context.MODE_PRIVATE)
    /** Adresse des Jellyfin-Servers (Konfiguration › Jellyfin). Beim Wechsel wird abgemeldet. */
    var baseUrl: String = url.trimEnd('/')
        set(v) {
            val n = v.trimEnd('/')
            if (n != field) {
                field = n
                logout()
                cachedServerId = null
                prefs.edit().remove("server_id").apply()
            }
        }
    private val deviceId: String = prefs.getString("device_id", null) ?: run {
        val id = "jellydroid-" + (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: System.currentTimeMillis().toString())
        prefs.edit().putString("device_id", id).apply()
        id
    }

    var token: String? = prefs.getString("token", null); private set
    var userId: String? = prefs.getString("user_id", null); private set
    val isLoggedIn get() = token != null && userId != null

    fun logout() {
        token = null; userId = null
        prefs.edit().remove("token").remove("user_id").apply()
    }

    private fun authHeader(): String {
        val t = token?.let { ", Token=\"$it\"" } ?: ""
        return "MediaBrowser Client=\"Jellydroid\", Device=\"Philips TV\", DeviceId=\"$deviceId\", Version=\"0.2\"$t"
    }

    class HttpException(val code: Int, msg: String) : IOException(msg)

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Authorization", authHeader())
            conn.setRequestProperty("X-Emby-Authorization", authHeader())
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            } else if (method == "POST") {
                conn.setFixedLengthStreamingMode(0)
            }
            val code = conn.responseCode
            if (code == 401) logout()
            if (code !in 200..299) throw HttpException(code, "HTTP $code für $path")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Quick Connect ----------

    fun quickConnectStart(): QuickConnectStart {
        val o = JSONObject(request("/QuickConnect/Initiate", "POST"))
        return QuickConnectStart(o.getString("Code"), o.getString("Secret"))
    }

    /** true, sobald der Code in einer anderen Jellyfin-App bestätigt wurde. */
    fun quickConnectApproved(secret: String): Boolean =
        JSONObject(request("/QuickConnect/Connect?secret=" + enc(secret))).optBoolean("Authenticated")

    fun quickConnectFinish(secret: String) {
        val o = JSONObject(request("/Users/AuthenticateWithQuickConnect", "POST", JSONObject().put("Secret", secret)))
        token = o.getString("AccessToken")
        userId = o.getJSONObject("User").getString("Id")
        prefs.edit().putString("token", token).putString("user_id", userId).apply()
    }

    // ---------- Inhalte ----------

    private val fields = "Overview,Genres,ProviderIds,RemoteTrailers,People,CommunityRating,CriticRating,OfficialRating"
    private val imageParams = "&EnableImageTypes=Thumb,Backdrop,Primary&ImageTypeLimit=1"

    /** Weiterschauen: angefangene Filme/Folgen, danach nächste Folgen laufender Serien. */
    fun continueWatching(limit: Int = 16): List<MediaItem> {
        val uid = userId ?: return emptyList()
        // Titel mit höchstens 1 Minute Rest gelten als fertig geschaut (Abspann) und fallen raus
        val resume = items(request("/Users/$uid/Items/Resume?Limit=$limit&MediaTypes=Video&Fields=$fields$imageParams"))
            .filter { (it.remainingSec ?: Long.MAX_VALUE) > 60 }
        val nextUp = runCatching {
            items(request("/Shows/NextUp?UserId=$uid&Limit=$limit&Fields=$fields$imageParams&EnableResumable=false"))
        }.getOrDefault(emptyList())
        // Pro Serie nur eine Kachel: die neueste angefangene Folge. Wer ab dem Abspann zur
        // nächsten Folge springt, hat sonst vier, fünf halb gesehene Folgen derselben Serie.
        // Angefangene und "nächste" Folgen zusammen betrachten, die mit der höchsten Nummer gewinnt.
        val newestPerSeries = (resume + nextUp).filter { it.seriesId != null }
            .groupBy { it.seriesId }
            .mapValues { (_, eps) -> eps.maxWith(compareBy({ it.seasonNo }, { it.episodeNo })) }
        val seen = mutableSetOf<String>()
        val merged = (resume + nextUp).mapNotNull { item ->
            val sid = item.seriesId ?: return@mapNotNull item // Filme bleiben einzeln
            if (!seen.add(sid)) null else newestPerSeries[sid] ?: item
        }
        return merged.take(limit)
    }

    /** Neu in der Mediathek (Filme + Serien), bis Seerr die Trends liefert. */
    fun latest(limit: Int = 20): List<MediaItem> {
        val uid = userId ?: return emptyList()
        // /Items/Latest lieferte am TV eine leere Liste; die normale Suche nach Datum ist verlässlicher
        return items(request("/Users/$uid/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=$limit&Fields=$fields$imageParams"))
    }

    /** Genres der Mediathek, die meistgenutzten zuerst. */
    fun genres(max: Int = 8): List<String> {
        val uid = userId ?: return emptyList()
        val o = JSONObject(request("/Genres?UserId=$uid&IncludeItemTypes=Movie,Series&Recursive=true&Fields=ItemCounts&EnableTotalRecordCount=false"))
        val a = o.optJSONArray("Items") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { it.optString("Name") to (it.optInt("MovieCount") + it.optInt("SeriesCount")) }
            .filter { it.first.isNotBlank() && it.second >= 4 }
            .sortedByDescending { it.second }
            .take(max)
            .map { it.first }
    }

    /** Filme und Serien eines Genres, bestbewertete zuerst. */
    fun byGenre(genre: String, limit: Int = 20): List<MediaItem> {
        val uid = userId ?: return emptyList()
        return items(request("/Users/$uid/Items?Genres=${enc(genre)}&IncludeItemTypes=Movie,Series&Recursive=true&SortBy=CommunityRating,SortName&SortOrder=Descending&Limit=$limit&Fields=$fields$imageParams"))
    }

    private fun items(json: String): List<MediaItem> = parseArray(JSONObject(json).optJSONArray("Items") ?: JSONArray())

    private fun parseArray(a: JSONArray): List<MediaItem> = (0 until a.length()).mapNotNull { runCatching { parse(a.getJSONObject(it)) }.getOrNull() }

    private fun parse(o: JSONObject): MediaItem {
        val id = o.getString("Id")
        val type = o.optString("Type")
        val provider = o.optJSONObject("ProviderIds")
        val tags = o.optJSONObject("ImageTags")
        val backdropTags = o.optJSONArray("BackdropImageTags")
        val parentBackdropId = o.optString("ParentBackdropItemId").ifBlank { null }
        val parentThumbId = o.optString("ParentThumbItemId").ifBlank { null }
        val seriesId = o.optString("SeriesId").ifBlank { null }

        val thumb = when {
            tags?.has("Thumb") == true -> img(id, "Thumb")
            type == "Episode" && parentThumbId != null -> img(parentThumbId, "Thumb")
            else -> null
        }
        val backdrop = when {
            backdropTags != null && backdropTags.length() > 0 -> img(id, "Backdrop")
            parentBackdropId != null -> img(parentBackdropId, "Backdrop")
            seriesId != null -> img(seriesId, "Backdrop")
            else -> null
        }
        val episodeImage = if (type == "Episode" && tags?.has("Primary") == true) img(id, "Primary") else null

        val ticks = o.optLong("RunTimeTicks", 0L)
        val pos = o.optJSONObject("UserData")?.optLong("PlaybackPositionTicks", 0L) ?: 0L
        val people = o.optJSONArray("People")
        val cast = if (people == null) emptyList() else (0 until people.length())
            .map { people.getJSONObject(it) }
            .filter { it.optString("Type") == "Actor" }
            .take(8)
            .map { it.optString("Name") }
        val genresJson = o.optJSONArray("Genres")
        val trailers = o.optJSONArray("RemoteTrailers")
        val season = o.optInt("ParentIndexNumber", -1)
        val episode = o.optInt("IndexNumber", -1)

        return MediaItem(
            id = id,
            type = type,
            name = o.optString("Name"),
            seriesName = o.optString("SeriesName").ifBlank { null },
            seriesId = seriesId,
            episodeLabel = if (type == "Episode" && season >= 0 && episode >= 0) "S$season · F$episode" else null,
            seasonNo = season,
            episodeNo = episode,
            year = o.optInt("ProductionYear", 0).takeIf { it > 0 },
            overview = o.optString("Overview").ifBlank { null },
            runtimeMin = if (ticks > 0) (ticks / 600_000_000L).toInt() else null,
            progress = if (ticks > 0 && pos > 0) (pos.toFloat() / ticks).coerceIn(0f, 1f) else null,
            remainingSec = if (ticks > 0 && pos > 0) (ticks - pos) / 10_000_000L else null,
            communityRating = o.optDouble("CommunityRating").takeIf { !it.isNaN() },
            criticRating = o.optInt("CriticRating", -1).takeIf { it >= 0 },
            officialRating = o.optString("OfficialRating").ifBlank { null },
            genres = if (genresJson == null) emptyList() else (0 until genresJson.length()).map { genresJson.getString(it) },
            tmdb = provider?.optString("Tmdb")?.ifBlank { null },
            imdb = provider?.optString("Imdb")?.ifBlank { null },
            tvdb = provider?.optString("Tvdb")?.ifBlank { null },
            trailerUrl = trailers?.optJSONObject(0)?.optString("Url")?.ifBlank { null },
            cast = cast,
            // Kacheln sind höchstens ~530 px breit: kleinere Bilder laden und dekodieren schneller
            landscapeImage = (thumb ?: episodeImage ?: backdrop)?.replace("maxWidth=720", "maxWidth=560"),
            backdropImage = backdrop ?: thumb,
        )
    }

    /** Für Folgen die Kennungen der Serie, damit Plezy die nächste Folge findet. */
    fun seriesIds(seriesId: String): Triple<String?, String?, String?> {
        val uid = userId ?: return Triple(null, null, null)
        val o = JSONObject(request("/Users/$uid/Items/$seriesId"))
        val p = o.optJSONObject("ProviderIds")
        return Triple(p?.optString("Tmdb")?.ifBlank { null }, p?.optString("Imdb")?.ifBlank { null }, p?.optString("Tvdb")?.ifBlank { null })
    }

    /** TMDB-IDs aller gesehenen Filme (für „Gesehenes ausblenden“ auf Entdecken). */
    fun watchedMovieTmdbIds(): Set<String> {
        val uid = userId ?: return emptySet()
        val a = JSONObject(request("/Users/$uid/Items?IsPlayed=true&IncludeItemTypes=Movie&Recursive=true&Fields=ProviderIds&EnableImages=false&EnableUserData=false"))
            .optJSONArray("Items") ?: return emptySet()
        return (0 until a.length()).mapNotNull { a.getJSONObject(it).optJSONObject("ProviderIds")?.optString("Tmdb")?.ifBlank { null } }.toSet()
    }

    /**
     * Bild und Ton für das Detail-Fenster, z. B. ["4K", "Dolby Vision", "Dolby Atmos", "7.1"].
     * Bei Serien zählt die erste Folge. Leer, wenn der Titel nicht in der Mediathek ist.
     */
    fun techInfo(item: MediaItem): List<String> {
        val uid = userId ?: return emptyList()
        val id = (if (item.isSeerr) item.jellyfinId else item.id) ?: return emptyList()
        var o = JSONObject(request("/Users/$uid/Items/$id?Fields=MediaStreams,MediaSources"))
        if (o.optString("Type") == "Series") {
            o = JSONObject(request("/Shows/$id/Episodes?UserId=$uid&Limit=1&Fields=MediaStreams,MediaSources"))
                .optJSONArray("Items")?.optJSONObject(0) ?: return emptyList()
        }
        val streams = o.optJSONArray("MediaStreams")?.takeIf { it.length() > 0 }
            ?: o.optJSONArray("MediaSources")?.optJSONObject(0)?.optJSONArray("MediaStreams")
            ?: return emptyList()
        val all = (0 until streams.length()).map { streams.getJSONObject(it) }
        val out = mutableListOf<String>()
        all.firstOrNull { it.optString("Type") == "Video" }?.let { v ->
            val w = v.optInt("Width"); val h = v.optInt("Height")
            when {
                w >= 3200 || h >= 1800 -> "4K"
                w >= 1800 || h >= 1000 -> "1080p"
                w >= 1200 || h >= 700 -> "720p"
                w > 0 -> "SD"
                else -> null
            }?.let(out::add)
            val rt = v.optString("VideoRangeType")
            when {
                rt.startsWith("DOVI", true) -> "Dolby Vision"
                rt.equals("HDR10Plus", true) -> "HDR10+"
                rt.equals("HDR10", true) -> "HDR10"
                rt.equals("HLG", true) -> "HLG"
                v.optString("VideoRange").equals("HDR", true) -> "HDR"
                else -> null
            }?.let(out::add)
        }
        val audios = all.filter { it.optString("Type") == "Audio" }
        (audios.firstOrNull { it.optBoolean("IsDefault") } ?: audios.firstOrNull())?.let { a ->
            val codec = a.optString("Codec").lowercase()
            val desc = listOf("Profile", "DisplayTitle", "Title").joinToString(" ") { a.optString(it) }
            when {
                desc.contains("Atmos", true) -> "Dolby Atmos"
                desc.contains("DTS:X", true) || desc.contains("DTS-X", true) -> "DTS:X"
                codec == "truehd" -> "Dolby TrueHD"
                codec == "eac3" -> "Dolby Digital+"
                codec == "ac3" -> "Dolby Digital"
                codec == "dts" && (desc.contains("MA", false) || desc.contains("HD", true)) -> "DTS-HD MA"
                codec == "dts" -> "DTS"
                codec.isNotBlank() -> codec.uppercase()
                else -> null
            }?.let(out::add)
            when (val c = a.optInt("Channels")) {
                0 -> null
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "$c Kanäle"
            }?.let(out::add)
        }
        return out
    }

    /** Markiert einen Titel (Film/Folge) in Jellyfin als gesehen. */
    fun markPlayed(itemId: String) {
        val uid = userId ?: return
        request("/Users/$uid/PlayedItems/$itemId", "POST")
    }

    /** Server-Kennung (Id aus /System/Info/Public); Plezy findet Titel damit exakt per server+id. */
    private var cachedServerId: String? = prefs.getString("server_id", null)
    fun serverId(): String? {
        cachedServerId?.let { return it }
        val id = JSONObject(request("/System/Info/Public")).optString("Id").ifBlank { null } ?: return null
        cachedServerId = id
        prefs.edit().putString("server_id", id).apply()
        return id
    }

    fun img(itemId: String, type: String, width: Int = 720) = "$baseUrl/Items/$itemId/Images/$type?maxWidth=$width&quality=85"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
