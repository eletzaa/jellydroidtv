package de.jellydroid.launcher.home

import android.content.Context
import android.content.SharedPreferences
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Anmeldedaten und Auswahl für Home Assistant, privat in den SharedPreferences "ha".
 * Die Adresse wird nur zur Laufzeit eingegeben (Handy oder Installer), nie im Code abgelegt.
 *
 * Zwei Wege:
 *  - OAuth (Anmeldung per Handy/Installer): client_id + refresh_token, Zugriffstoken wird bei Bedarf erneuert.
 *  - Langlebiger Token aus dem HA-Profil: token.
 */
class HaPrefs(context: Context) {
    val p: SharedPreferences = context.applicationContext.getSharedPreferences("ha", Context.MODE_PRIVATE)

    val url get() = p.getString("url", "")!!
    val configured get() = url.isNotBlank() && (p.getString("token", "")!!.isNotBlank() || p.getString("refresh_token", "")!!.isNotBlank())
    val mediaPlayer get() = p.getString("media_player", "")!!
    val doorbellEvent get() = p.getString("doorbell_event", "")!!
    val doorButton get() = p.getString("door_button", "")!!
    /** In der Sidebar gewählter Raum (area_id); leer = Raum des Fernsehers. */
    val room get() = p.getString("room", "")!!
    /** Standardraum aus der Launcher Konfiguration (area_id); leer = zuletzt gewählten Raum behalten. */
    val defaultRoom get() = p.getString("default_room", "")!!
    val doorbellPopup get() = p.getBoolean("doorbell_popup", true)

    fun set(key: String, value: String) = p.edit().putString(key, value).apply()
    fun setPopup(v: Boolean) = p.edit().putBoolean("doorbell_popup", v).apply()

    fun saveOAuth(url: String, clientId: String, json: JSONObject) {
        p.edit()
            .putString("url", url.trim().trimEnd('/'))
            .putString("client_id", clientId)
            .putString("refresh_token", json.getString("refresh_token"))
            .putString("access_token", json.optString("access_token"))
            .putLong("access_exp", System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000)
            .remove("token")
            .apply()
    }

    fun saveToken(url: String, token: String) {
        p.edit()
            .putString("url", url.trim().trimEnd('/'))
            .putString("token", token.trim())
            .remove("refresh_token").remove("client_id").remove("access_token")
            .apply()
    }

    /** Abmelden: Zugang weg, Auswahl (Fernseher, Klingel) bleibt für ein erneutes Verbinden. */
    fun logout() {
        p.edit().remove("url").remove("token").remove("refresh_token").remove("client_id")
            .remove("access_token").remove("access_exp").apply()
    }

    /**
     * Gültiges Zugriffstoken. Blockiert (Netz), also nie auf dem Hauptthread aufrufen.
     * null = nicht angemeldet oder Anmeldung abgelaufen.
     */
    fun accessToken(http: OkHttpClient, forceRefresh: Boolean = false): String? {
        p.getString("token", "")!!.takeIf { it.isNotBlank() }?.let { return it }
        val refresh = p.getString("refresh_token", "")!!.takeIf { it.isNotBlank() } ?: return null
        val cached = p.getString("access_token", "")!!
        if (!forceRefresh && cached.isNotBlank() && p.getLong("access_exp", 0) - 60_000 > System.currentTimeMillis()) return cached
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", p.getString("client_id", "")!!)
            .build()
        http.newCall(Request.Builder().url("$url/auth/token").post(body).build()).execute().use { r ->
            if (r.code == 400 || r.code == 403) throw AuthExpired()
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val j = JSONObject(r.body!!.string())
            val token = j.getString("access_token")
            p.edit().putString("access_token", token)
                .putLong("access_exp", System.currentTimeMillis() + j.optLong("expires_in", 1800) * 1000).apply()
            return token
        }
    }

    class AuthExpired : Exception("Anmeldung abgelaufen")

    companion object {
        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }

        /** Code aus dem OAuth-Ablauf gegen Tokens tauschen. */
        fun exchangeCode(url: String, clientId: String, code: String): JSONObject {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", clientId)
                .build()
            http.newCall(Request.Builder().url(url.trimEnd('/') + "/auth/token").post(body).build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("Home Assistant antwortet mit ${r.code}")
                return JSONObject(r.body!!.string())
            }
        }
    }
}
