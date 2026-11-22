package de.jellydroid.launcher.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Kleiner Webserver für die Anmeldung per Handy (QR-Code auf dem Fernseher).
 *
 * 1. Handy öffnet http://<TV>:8765/ und gibt die Home-Assistant-Adresse ein.
 * 2. Weiterleitung zur offiziellen HA-Anmeldung (OAuth, client_id = diese Seite).
 * 3. HA schickt den Code an /cb, der Fernseher tauscht ihn gegen Tokens.
 * Alternativ kann auf der Seite ein langlebiger Token eingetragen werden.
 *
 * Läuft nur, solange die Anmeldung auf dem Bildschirm offen ist.
 */
class HaSetupServer(private val context: Context, private val onDone: () -> Unit) {
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private val state = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Volatile private var pendingUrl = ""

    /** Adresse für den QR-Code, null wenn kein Netz. */
    var address: String? = null; private set

    fun start(): String? {
        val ip = localIp(context) ?: return null
        for (port in PORTS) {
            try {
                server = ServerSocket(port)
                break
            } catch (_: Exception) { /* nächsten Port */ }
        }
        val s = server ?: return null
        address = "http://$ip:${s.localPort}/"
        Thread {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Exception) { break }
                Thread { runCatching { serve(c) }.onFailure { Log.w(TAG, "Anfrage fehlgeschlagen", it) }; runCatching { c.close() } }.start()
            }
        }.start()
        return address
    }

    fun stop() { runCatching { server?.close() }; server = null }

    private fun serve(c: Socket) {
        c.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(c.getInputStream()))
        val line = reader.readLine() ?: return
        while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
        val target = line.split(' ').getOrNull(1) ?: "/"
        val path = target.substringBefore('?')
        val q = parseQuery(target.substringAfter('?', ""))
        val base = address ?: return
        val out = c.getOutputStream()
        fun reply(status: String, html: String, location: String? = null) {
            val body = html.toByteArray()
            val head = buildString {
                append("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
                if (location != null) append("Location: $location\r\n")
                append("\r\n")
            }
            out.write(head.toByteArray()); out.write(body); out.flush()
        }
        when (path) {
            "/" -> reply("200 OK", page(FORM))
            "/start" -> {
                val url = normalize(q["url"].orEmpty())
                val token = q["token"].orEmpty().trim()
                if (url == null) { reply("200 OK", page("<p class=err>Die Adresse sieht nicht richtig aus.</p>$FORM")); return }
                if (token.isNotBlank()) {
                    HaPrefs(context).saveToken(url, token)
                    finish()
                    reply("200 OK", page(DONE))
                    return
                }
                pendingUrl = url
                val auth = "$url/auth/authorize?response_type=code" +
                    "&client_id=" + enc(base) +
                    "&redirect_uri=" + enc(base + "cb") +
                    "&state=" + state
                reply("302 Found", "", location = auth)
            }
            "/cb" -> {
                val code = q["code"].orEmpty()
                if (q["state"] != state || code.isBlank() || pendingUrl.isBlank()) { reply("200 OK", page("<p class=err>Anmeldung abgebrochen.</p>$FORM")); return }
                try {
                    val json = HaPrefs.exchangeCode(pendingUrl, base, code)
                    HaPrefs(context).saveOAuth(pendingUrl, base, json)
                    finish()
                    reply("200 OK", page(DONE))
                } catch (e: Exception) {
                    reply("200 OK", page("<p class=err>${e.message ?: "Fehler"}</p>$FORM"))
                }
            }
            // HA liest beim Anmelden diese Adresse, um den App-Namen anzuzeigen
            else -> reply("404 Not Found", page("<p>Nicht gefunden</p>"))
        }
    }

    private fun finish() = main.post { HomeAssistant.restart(context); onDone() }

    companion object {
        private const val TAG = "JellydroidHA"
        private val PORTS = listOf(8765, 8766, 8767)

        fun localIp(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        private fun parseQuery(q: String) = q.split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

        private fun normalize(raw: String): String? {
            var u = raw.trim().trimEnd('/')
            if (u.isBlank()) return null
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return runCatching { java.net.URI(u).host?.let { u } }.getOrNull()
        }

        private fun page(content: String) = """<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Jellydroid</title>
<link rel="redirect_uri" href="cb">
<style>body{font-family:-apple-system,system-ui,sans-serif;background:#0d1220;color:#f4f6fb;margin:0;padding:28px 18px;max-width:460px}
h1{font-size:22px;margin:0 0 18px}label{display:block;font-size:14px;color:#aab;margin:16px 0 6px}
input{width:100%;box-sizing:border-box;font-size:17px;padding:12px 14px;border-radius:12px;border:1px solid #334;background:#161a24;color:#fff}
button{margin-top:20px;width:100%;font-size:17px;font-weight:600;padding:13px;border:0;border-radius:99px;background:#fff;color:#0d1220}
details{margin-top:22px;color:#aab;font-size:14px}.err{color:#ff8a7a}p{line-height:1.5}</style></head>
<body><div class="h-app"><h1 class="p-name">Jellydroid</h1></div>$content</body></html>"""

        private const val FORM = """<form action="start" method="get">
<label for="u">Adresse von Home Assistant</label>
<input id="u" name="url" type="url" inputmode="url" autocapitalize="off" autocorrect="off" required>
<details><summary>Langlebigen Token verwenden</summary>
<label for="t">Token</label><input id="t" name="token" autocapitalize="off" autocorrect="off"></details>
<button type="submit">Weiter</button></form>"""

        private const val DONE = "<p>Fertig. Der Fernseher ist mit Home Assistant verbunden, du kannst dieses Fenster schließen.</p>"
    }
}
