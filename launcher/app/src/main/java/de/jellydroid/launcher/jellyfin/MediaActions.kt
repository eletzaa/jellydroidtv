package de.jellydroid.launcher.jellyfin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Übergabe an Plezy (Wiedergabe) und SmartTube (Trailer). */
object MediaActions {
    private const val PLEZY = "com.edde746.plezy"
    private val SMARTTUBE = listOf("com.teamsmart.videomanager.tv", "org.smarttube.stable", "org.smarttube.beta")

    /**
     * Plezy versteht plezy://play?type=movie|show&tmdb=..&imdb=..&tvdb=.. und sucht den Titel
     * selbst auf seinen Servern. Bei Serien spielt es die nächste Folge (mode=next).
     */
    fun play(context: Context, kind: String, tmdb: String?, imdb: String?, tvdb: String?) {
        val b = Uri.Builder().scheme("plezy").authority("play").appendQueryParameter("type", kind)
        tmdb?.let { b.appendQueryParameter("tmdb", it) }
        imdb?.let { b.appendQueryParameter("imdb", it.lowercase()) }
        tvdb?.let { b.appendQueryParameter("tvdb", it) }
        if (tmdb == null && imdb == null && tvdb == null) {
            toast(context, "Keine TMDB/IMDb-Kennung, Plezy wird nur geöffnet")
            context.packageManager.getLeanbackLaunchIntentForPackage(PLEZY)?.let { start(context, it) }
                ?: toast(context, "Plezy ist nicht installiert")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, b.build()).setPackage(PLEZY)
        if (!start(context, intent)) toast(context, "Plezy ist nicht installiert")
    }

    /** plezy://play?server=..&id=.. spielt genau diesen Titel; bei einer Serie die nächste Folge. */
    fun playItem(context: Context, serverId: String, itemId: String) {
        val uri = Uri.Builder().scheme("plezy").authority("play")
            .appendQueryParameter("server", serverId)
            .appendQueryParameter("id", itemId)
            .build()
        if (!start(context, Intent(Intent.ACTION_VIEW, uri).setPackage(PLEZY))) toast(context, "Plezy ist nicht installiert")
    }

    fun trailer(context: Context, url: String) {
        val uri = Uri.parse(url)
        val pm = context.packageManager
        val pkg = SMARTTUBE.firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { pkg?.let { setPackage(it) } }
        if (!start(context, intent)) toast(context, "Kein Programm für Trailer gefunden")
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
