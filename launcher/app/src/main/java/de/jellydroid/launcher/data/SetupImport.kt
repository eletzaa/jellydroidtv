package de.jellydroid.launcher.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Übernimmt die Vorkonfiguration aus dem TV-Installer.
 *
 * Der Installer legt per adb die Datei
 *   /sdcard/Android/data/de.jellydroid.launcher/files/setup.json
 * ab (für andere Apps nicht lesbar), beendet den Launcher und startet ihn neu.
 * Hier werden die Werte in die vorhandenen SharedPreferences geschrieben und die Datei gelöscht.
 *
 * Läuft in JellydroidApp.onCreate (jeder Prozessstart) und zur Sicherheit in MainActivity.onCreate VOR setContent, damit LauncherConfig, JellyfinClient und
 * SeerrClient die neuen Werte schon beim Erzeugen lesen:
 *     SetupImport.run(this)
 */
object SetupImport {
    private const val TAG = "SetupImport"

    @Synchronized
    fun run(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: return
        val file = File(dir, "setup.json")
        if (!file.exists()) return
        var result = "ok"
        try {
            val o = JSONObject(file.readText())
            if (o.optInt("version", 0) != 1) { Log.w(TAG, "Unbekannte Version"); result = "fehler: unbekannte Version ${o.optInt("version", 0)}"; return }

            o.optJSONObject("config")?.let { c ->
                val e = context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                c.optString("jellyfin_url").takeIf { it.isNotBlank() }?.let { e.putString("jellyfin_url", it.trim().trimEnd('/')) }
                c.optString("seerr_url").takeIf { it.isNotBlank() }?.let { e.putString("seerr_url", it.trim().trimEnd('/')) }
                c.optString("theme").takeIf { it in setOf("DARK", "LIGHT", "AUTO") }?.let { e.putString("theme", it) }
                c.optString("jellyfin_app").takeIf { it.isNotBlank() }?.let { e.putString("jellyfin_app", it) }
                if (c.has("place")) e.putInt("place", c.optInt("place", 0))
                c.optJSONArray("services_used")?.let { a -> e.putStringSet("services_used", (0 until a.length()).map { a.getString(it) }.toSet()) }
                c.optJSONArray("services_rows")?.let { a -> e.putStringSet("services_rows", (0 until a.length()).map { a.getString(it) }.toSet()) }
                if (c.has("airplay_enabled")) e.putBoolean("airplay_enabled", c.optBoolean("airplay_enabled", true))
                c.optString("airplay_name").takeIf { it.isNotBlank() }?.let { e.putString("airplay_name", it.trim()) }
                e.apply()
            }

            o.optJSONArray("favorites")?.let { a ->
                val order = (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }
                if (order.isNotEmpty()) context.getSharedPreferences("favorites", Context.MODE_PRIVATE).edit()
                    .putString("order", order.joinToString(",")).apply()
            }

            // Jellyfin-Anmeldung aus dem Quick Connect am PC. device_id gehört zum Token, daher mit übernehmen.
            o.optJSONObject("jellyfin")?.let { j ->
                val token = j.optString("token"); val user = j.optString("user_id"); val dev = j.optString("device_id")
                if (token.isNotBlank() && user.isNotBlank() && dev.isNotBlank()) {
                    val e = context.getSharedPreferences("jellyfin", Context.MODE_PRIVATE).edit()
                        .putString("token", token).putString("user_id", user).putString("device_id", dev)
                    j.optString("server_id").takeIf { it.isNotBlank() }?.let { e.putString("server_id", it) } ?: e.remove("server_id")
                    e.apply()
                }
            }

            // Seerr: nur das Sitzungs-Cookie, nie das Passwort (wie SeerrClient.login).
            o.optJSONObject("seerr")?.let { s ->
                val cookie = s.optString("cookie"); val id = s.optInt("user_id", -1)
                if (cookie.startsWith("connect.sid=") && id >= 0) {
                    context.getSharedPreferences("seerr", Context.MODE_PRIVATE).edit()
                        .putString("cookie", cookie).putInt("user_id", id).apply()
                }
            }
            // Home Assistant: Adresse + (OAuth: client_id und refresh_token) oder langlebiger token,
            // dazu die Auswahl für Raum und Türklingel. Alles optional.
            o.optJSONObject("homeassistant")?.let { h ->
                val e = context.getSharedPreferences("ha", Context.MODE_PRIVATE).edit()
                val url = h.optString("url").trim().trimEnd('/')
                val refresh = h.optString("refresh_token"); val client = h.optString("client_id"); val token = h.optString("token")
                if (url.isNotBlank() && refresh.isNotBlank() && client.isNotBlank()) {
                    e.putString("url", url).putString("client_id", client).putString("refresh_token", refresh)
                        .remove("token").remove("access_token").remove("access_exp")
                } else if (url.isNotBlank() && token.isNotBlank()) {
                    e.putString("url", url).putString("token", token)
                        .remove("refresh_token").remove("client_id").remove("access_token").remove("access_exp")
                }
                for (k in listOf("media_player", "doorbell_event", "door_button", "default_room")) if (h.has(k)) e.putString(k, h.optString(k).trim())
                if (h.has("doorbell_popup")) e.putBoolean("doorbell_popup", h.optBoolean("doorbell_popup", true))
                e.apply()
            }
            o.optJSONObject("config")?.let { c ->
                if (c.has("sidebar_key")) context.getSharedPreferences("config", Context.MODE_PRIVATE).edit().putInt("sidebar_key", c.optInt("sidebar_key", -1)).apply()
            }
            // Schrift: "standard" = Inter, "eigene" = Dateien, die der Installer vorher nach files/schrift-neu/ geschoben hat
            o.optJSONObject("config")?.optString("font")?.takeIf { it.isNotBlank() }?.let { font ->
                importFont(context, dir, font)?.let { result = it }
            }
            // Läuft schon etwas mit den alten Werten (Home Assistant, AirPlay), neu starten.
            if (o.has("homeassistant")) de.jellydroid.launcher.home.HomeAssistant.restart(context)
            runCatching { de.jellydroid.launcher.data.syncAirPlay(context) }
            Log.i(TAG, "Vorkonfiguration übernommen")
        } catch (e: Exception) {
            Log.w(TAG, "setup.json nicht lesbar", e)
            result = "fehler: " + (e.message ?: e.javaClass.simpleName).replace('\n', ' ')
        } finally {
            file.delete()
            File(dir, "schrift-neu").deleteRecursively()
            // Rückmeldung für den Installer (liest und löscht die Datei)
            runCatching { File(dir, "setup-ergebnis.txt").writeText(result + "\n") }
        }
    }

    /** Übernimmt die Schriftwahl. Gibt bei Problemen eine Fehlermeldung für setup-ergebnis.txt zurück. */
    private fun importFont(context: Context, dir: File, font: String): String? {
        val target = de.jellydroid.launcher.ui.AppFonts.dir(context)
        var error: String? = null
        when (font) {
            "standard" -> target.deleteRecursively()
            "eigene" -> {
                val files = File(dir, "schrift-neu").listFiles()
                    ?.filter { de.jellydroid.launcher.ui.AppFonts.isFontFile(it) && it.length() in 1..40_000_000 }
                    ?.take(24).orEmpty()
                if (files.isEmpty()) error = "fehler: keine Schriftdatei gefunden"
                else {
                    target.deleteRecursively(); target.mkdirs()
                    files.forEach { it.copyTo(File(target, it.name.replace(Regex("[^A-Za-z0-9._-]"), "_")), overwrite = true) }
                }
            }
            else -> return null
        }
        de.jellydroid.launcher.ui.AppFonts.load(context)
        if (error == null && font == "eigene" && !de.jellydroid.launcher.ui.AppFonts.custom) {
            target.deleteRecursively(); de.jellydroid.launcher.ui.AppFonts.load(context)
            error = "fehler: Schriftdatei nicht lesbar"
        }
        return error
    }
}
