package de.elias.launcher.data

import android.content.Context

/**
 * Streamingdienst: TMDB-Anbieter-ID (für „Neu bei …“), App-Paket am Fernseher und, wo möglich,
 * die Wikidata-Eigenschaft der dienstinternen Titelnummer samt Link zum direkten Öffnen.
 */
data class StreamService(
    val key: String,
    val name: String,
    val tmdbProvider: Int,
    val packages: List<String>,
    val wdMovie: String? = null,
    val wdShow: String? = null,
    val linkMovie: ((String) -> String)? = null,
    val linkShow: ((String) -> String)? = null,
)

/**
 * Katalog bekannter Dienste. Angezeigt wird zur Laufzeit nur, was auf dem jeweiligen Fernseher
 * installiert ist (je Dienst mehrere mögliche Paketnamen, z. B. TV- und Handy-Version).
 */
object Services {
    val all = listOf(
        StreamService("netflix", "Netflix", 8, listOf("com.netflix.ninja", "com.netflix.mediaclient"), "P1874", "P1874",
            { "https://www.netflix.com/title/$it" }, { "https://www.netflix.com/title/$it" }),
        StreamService("prime", "Prime Video", 9, listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
        StreamService("paramount", "Paramount+", 531, listOf("com.cbs.ott")),
        StreamService("crunchyroll", "Crunchyroll", 283, listOf("com.crunchyroll.crunchyroid")),
        StreamService("mubi", "MUBI", 11, listOf("com.mubi")),
        StreamService("disney", "Disney+", 337, listOf("com.disney.disneyplus"), "P7595", "P7596",
            { "https://www.disneyplus.com/movies/_/$it" }, { "https://www.disneyplus.com/series/_/$it" }),
        StreamService("wow", "WOW", 30, listOf("de.sky.online")),
        StreamService("joyn", "Joyn", 304, listOf("de.prosiebensat1digital.seventv")),
        StreamService("rtl", "RTL+", 298, listOf("de.rtli.tvnow")),
        StreamService("appletv", "Apple TV+", 350, listOf("com.apple.atve.androidtv.appletv")),
        StreamService("magenta", "MagentaTV", 178, listOf("de.telekom.magentatv.tv")),
    )

    fun byKey(key: String?) = all.firstOrNull { it.key == key }

    /** Nur Dienste, deren App auf dem Fernseher installiert ist. */
    fun installed(context: Context) = all.filter { s -> s.packages.any { isInstalled(context, it) } }

    fun packageOf(context: Context, s: StreamService) = s.packages.firstOrNull { isInstalled(context, it) }

    fun isInstalled(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}

/** Jellyfin-Apps, mit denen Titel aus der Mediathek geöffnet werden können. */
data class JellyfinApp(val pkg: String, val name: String, val canOpenTitle: Boolean)

object JellyfinApps {
    val all = listOf(
        JellyfinApp("com.edde746.plezy", "Plezy", canOpenTitle = true),
        JellyfinApp("org.jellyfin.androidtv", "Jellyfin", canOpenTitle = false),
        JellyfinApp("com.shady.reefin", "Reefin", canOpenTitle = false),
        JellyfinApp("dev.jdtech.jellyfin", "Findroid", canOpenTitle = false),
        JellyfinApp("com.github.damontecres.wholphin", "Wholphin", canOpenTitle = false),
    )

    fun installed(context: Context) = all.filter { Services.isInstalled(context, it.pkg) }
}

/** Länder für „Dynamisch“ (Sonnenauf- und -untergang), je Landesmitte: einfacher, auf einige Minuten genau. */
object Places {
    val all = listOf(
        Triple("Deutschland", 51.16, 10.45), Triple("Österreich", 47.59, 14.12), Triple("Schweiz", 46.80, 8.23),
        Triple("Niederlande", 52.13, 5.29), Triple("Belgien", 50.50, 4.47), Triple("Luxemburg", 49.82, 6.13),
        Triple("Frankreich", 46.60, 2.21), Triple("Italien", 42.50, 12.57), Triple("Spanien", 40.46, -3.75),
        Triple("Portugal", 39.56, -8.00), Triple("Vereinigtes Königreich", 54.00, -2.00), Triple("Irland", 53.41, -8.24),
        Triple("Dänemark", 56.26, 9.50), Triple("Schweden", 62.00, 15.00), Triple("Norwegen", 61.00, 9.00),
        Triple("Finnland", 64.00, 26.00), Triple("Polen", 51.92, 19.15), Triple("Tschechien", 49.82, 15.47),
        Triple("Ungarn", 47.16, 19.50), Triple("Kroatien", 45.10, 15.20), Triple("Griechenland", 39.07, 21.82),
        Triple("Türkei", 38.96, 35.24), Triple("USA", 39.83, -98.58), Triple("Kanada", 56.13, -106.35),
    )
}
