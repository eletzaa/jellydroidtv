package de.jellydroid.launcher.jellyfin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Markierungen, die Jellyfin und Seerr nicht kennen, lokal in den SharedPreferences "marks":
 *  - ausgeblendete Serien (Startseite, Weiterschauen): Jellyfin-Serien-ID
 *  - "Interessiert mich nicht" (Entdecken): "movie:<tmdb>", "tv:<tmdb>" oder "jf:<id>"
 */
class LocalMarks(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("marks", Context.MODE_PRIVATE)

    var hiddenSeries by mutableStateOf(p.getStringSet("hidden_series", emptySet())!!.toSet()); private set
    var notInterested by mutableStateOf(p.getStringSet("not_interested", emptySet())!!.toSet()); private set

    fun hideSeries(seriesId: String) {
        hiddenSeries = hiddenSeries + seriesId
        p.edit().putStringSet("hidden_series", hiddenSeries).apply()
    }

    fun markNotInterested(item: MediaItem) {
        notInterested = notInterested + keysOf(item)
        p.edit().putStringSet("not_interested", notInterested).apply()
    }

    fun isHidden(item: MediaItem): Boolean = keysOf(item).any { it in notInterested }

    /** Alle Kennungen eines Titels: derselbe Film kann aus Seerr (TMDB) und aus Jellyfin kommen. */
    private fun keysOf(item: MediaItem): List<String> {
        val kind = when (item.type) { "Movie" -> "movie"; "Series" -> "tv"; else -> if (item.seriesId != null) "tv" else "movie" }
        return listOfNotNull(
            item.tmdb?.let { "$kind:$it" },
            item.jellyfinId?.let { "jf:$it" },
            if (!item.isSeerr) "jf:${item.seriesId ?: item.id}" else null,
        )
    }

    /** Für die Konfiguration: alles wieder einblenden. */
    fun reset() {
        hiddenSeries = emptySet(); notInterested = emptySet()
        p.edit().clear().apply()
    }
}
