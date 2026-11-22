package de.jellydroid.launcher.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** Ein Gerät aus Home Assistant mit Zustand und Attributen. */
data class HaEntity(val id: String, val state: String, val attrs: JSONObject) {
    val domain get() = id.substringBefore('.')
    val name: String get() = attrs.optString("friendly_name").ifBlank { id.substringAfter('.') }
    val on get() = state == "on"
    val available get() = state != "unavailable" && state != "unknown"
}

enum class HaStatus { Off, Connecting, Ready, AuthFailed, Error }

/**
 * Verbindung zu Home Assistant über die WebSocket-API, ein Mal pro Prozess.
 * Hält die Geräte des Raums aktuell (state_changed) und meldet die Türklingel.
 *
 * Raum = Bereich des gewählten media_player (Bereich der Entität, sonst des Geräts).
 * Protokoll: adb logcat -s JellydroidHA
 */
object HomeAssistant {
    private const val TAG = "JellydroidHA"
    private val main = Handler(Looper.getMainLooper())

    var status by mutableStateOf(HaStatus.Off); private set
    var roomName by mutableStateOf(""); private set
    /** Gerade gezeigter Bereich (area_id) und der Bereich des Fernsehers. */
    var roomId by mutableStateOf<String?>(null); private set
    var homeRoomId by mutableStateOf<String?>(null); private set
    /** Bereiche mit steuerbaren Geräten für die Raumauswahl: (area_id, Name). */
    var rooms by mutableStateOf(emptyList<Pair<String, String>>()); private set
    /** Entitäten des Raums (Licht, Schalter, Szenen, Heizung), sortiert. */
    var roomIds by mutableStateOf(emptyList<String>()); private set
    val states = mutableStateMapOf<String, HaEntity>()
    /** Für die Auswahl in der Konfiguration: (entity_id, Anzeigename). */
    var mediaPlayers by mutableStateOf(emptyList<Pair<String, String>>()); private set
    var doorbellEvents by mutableStateOf(emptyList<Pair<String, String>>()); private set
    var doorOpeners by mutableStateOf(emptyList<Pair<String, String>>()); private set

    /** Wird auf dem Hauptthread gerufen, wenn die Klingel läutet (setzt die Bedienungshilfe). */
    var onDoorbell: ((String) -> Unit)? = null

    private var app: Context? = null
    private var ws: WebSocket? = null
    private var nextId = 1
    private var generation = 0
    private var retryMs = 5_000L
    private var retriedAuth = false
    private val pending = HashMap<Int, (JSONObject) -> Unit>()
    private var areas = JSONArray(); private var devices = JSONArray(); private var entities = JSONArray()
    private var entityArea = HashMap<String, String>()
    private var hiddenIds = HashSet<String>()
    private var areaNames = HashMap<String, String>()
    /** Symbol aus dem Entitätsregister (in HA selbst gesetzt), hat Vorrang vor dem Attribut. */
    private var registryIcons = HashMap<String, String>()
    private val roomDomains = listOf("climate", "scene", "light", "switch")

    fun prefs(context: Context) = HaPrefs(context)

    /** Verbinden, falls angemeldet. Mehrfacher Aufruf ist harmlos. */
    fun start(context: Context) {
        app = context.applicationContext
        if (ws != null || status == HaStatus.Connecting) return
        connect()
    }

    /** Nach neuer Anmeldung oder geänderter Auswahl neu verbinden. */
    fun restart(context: Context) {
        app = context.applicationContext
        main.post {
            generation++
            ws?.close(1000, null); ws = null
            status = HaStatus.Off
            retryMs = 5_000L
            connect()
        }
    }

    fun stop() {
        main.post { generation++; ws?.close(1000, null); ws = null; status = HaStatus.Off; states.clear(); roomIds = emptyList() }
    }

    private fun connect() {
        val ctx = app ?: return
        val prefs = HaPrefs(ctx)
        if (!prefs.configured) { status = HaStatus.Off; return }
        status = HaStatus.Connecting
        val gen = ++generation
        Thread {
            try {
                val token = prefs.accessToken(HaPrefs.http) ?: run { post(gen) { status = HaStatus.AuthFailed }; return@Thread }
                val wsUrl = prefs.url.replaceFirst("http", "ws") + "/api/websocket"
                val req = Request.Builder().url(wsUrl).build()
                val socket = HaPrefs.http.newWebSocket(req, Listener(gen, token))
                post(gen) { ws = socket }
            } catch (e: HaPrefs.AuthExpired) {
                Log.w(TAG, "Anmeldung abgelaufen")
                post(gen) { status = HaStatus.AuthFailed }
            } catch (e: Exception) {
                Log.w(TAG, "Verbindung fehlgeschlagen: ${e.message}")
                post(gen) { failed() }
            }
        }.start()
    }

    private fun post(gen: Int, block: () -> Unit) = main.post { if (gen == generation) block() }

    private fun failed() {
        ws = null
        status = HaStatus.Error
        val gen = generation
        main.postDelayed({ if (gen == generation && ws == null) connect() }, retryMs)
        retryMs = (retryMs * 2).coerceAtMost(120_000L)
    }

    private class Listener(private val gen: Int, private val token: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val m = runCatching { JSONObject(text) }.getOrNull() ?: return
            post(gen) { handle(webSocket, m, token) }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket getrennt: ${t.message}")
            post(gen) { failed() }
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            post(gen) { if (status != HaStatus.AuthFailed) failed() }
        }
    }

    private fun handle(socket: WebSocket, m: JSONObject, token: String) {
        when (m.optString("type")) {
            "auth_required" -> socket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            "auth_invalid" -> {
                Log.w(TAG, "Token abgelehnt")
                status = HaStatus.AuthFailed
                socket.close(1000, null); ws = null
                // OAuth: Zugriffstoken einmal neu holen, vielleicht war es nur abgelaufen
                val ctx = app
                if (ctx != null && !retriedAuth && HaPrefs(ctx).p.getString("refresh_token", "")!!.isNotBlank()) {
                    retriedAuth = true
                    HaPrefs(ctx).p.edit().remove("access_token").apply()
                    main.postDelayed({ if (ws == null) connect() }, 1_000)
                }
            }
            "auth_ok" -> {
                Log.i(TAG, "Verbunden")
                retryMs = 5_000L
                retriedAuth = false
                loadAll(socket)
            }
            "result" -> pending.remove(m.optInt("id"))?.invoke(m)
            "event" -> {
                val ev = m.optJSONObject("event") ?: return
                if (ev.optString("event_type") != "state_changed") return
                val d = ev.optJSONObject("data") ?: return
                val id = d.optString("entity_id")
                val ns = d.optJSONObject("new_state")
                val os = d.optJSONObject("old_state")
                if (ns != null) states[id] = ns.toEntity() else states.remove(id)
                checkDoorbell(id, os, ns)
            }
        }
    }

    private fun send(socket: WebSocket, type: String, extra: JSONObject = JSONObject(), onResult: (JSONObject) -> Unit = {}) {
        val id = nextId++
        pending[id] = onResult
        socket.send(extra.put("id", id).put("type", type).toString())
    }

    private fun loadAll(socket: WebSocket) {
        var left = 4
        fun done() { if (--left == 0) { computeRoom(); status = HaStatus.Ready } }
        send(socket, "get_states") { r ->
            val a = r.optJSONArray("result") ?: JSONArray()
            states.clear()
            for (i in 0 until a.length()) a.getJSONObject(i).toEntity().let { states[it.id] = it }
            done()
        }
        send(socket, "config/area_registry/list") { r -> areas = r.optJSONArray("result") ?: JSONArray(); done() }
        send(socket, "config/device_registry/list") { r -> devices = r.optJSONArray("result") ?: JSONArray(); done() }
        send(socket, "config/entity_registry/list") { r -> entities = r.optJSONArray("result") ?: JSONArray(); done() }
        send(socket, "subscribe_events", JSONObject().put("event_type", "state_changed"))
    }

    private fun JSONObject.toEntity() = HaEntity(optString("entity_id"), optString("state"), optJSONObject("attributes") ?: JSONObject())

    private fun computeRoom() {
        val ctx = app ?: return
        val prefs = HaPrefs(ctx)
        val deviceArea = HashMap<String, String>()
        for (i in 0 until devices.length()) devices.getJSONObject(i).let { d ->
            d.optString("area_id").takeIf { it.isNotBlank() && it != "null" }?.let { deviceArea[d.optString("id")] = it }
        }
        val areaName = HashMap<String, String>()
        for (i in 0 until areas.length()) areas.getJSONObject(i).let { areaName[it.optString("area_id")] = it.optString("name") }
        val entityArea = HashMap<String, String>()
        val hidden = HashSet<String>()
        val icons = HashMap<String, String>()
        for (i in 0 until entities.length()) entities.getJSONObject(i).let { e ->
            val id = e.optString("entity_id")
            val a = e.optString("area_id").takeIf { it.isNotBlank() && it != "null" } ?: deviceArea[e.optString("device_id")]
            if (a != null) entityArea[id] = a
            if (!e.isNull("hidden_by") || !e.isNull("disabled_by") || !e.isNull("entity_category")) hidden += id
            e.optString("icon").takeIf { !e.isNull("icon") && it.isNotBlank() }?.let { icons[id] = it }
        }
        this.entityArea = entityArea; hiddenIds = hidden; areaNames = areaName; registryIcons = icons
        fun label(id: String) = (states[id]?.name ?: id) + (entityArea[id]?.let { areaName[it] }?.let { " · $it" } ?: "")
        val all = states.keys.sorted()
        mediaPlayers = all.filter { it.startsWith("media_player.") }.map { it to label(it) }
        doorbellEvents = all.filter { it.startsWith("event.") }.map { it to label(it) }
        doorOpeners = all.filter { it.startsWith("button.") || it.startsWith("lock.") }.map { it to label(it) }

        homeRoomId = entityArea[prefs.mediaPlayer]
        val used = states.keys.filter { it !in hidden && it.substringBefore('.') in roomDomains }.mapNotNull { entityArea[it] }.toSet()
        // Reihenfolge wie in HA (Bereichsregister), der Raum des Fernsehers zuerst
        val areaOrder = (0 until areas.length()).map { areas.getJSONObject(it).optString("area_id") }.filter { it in used }
        rooms = (listOfNotNull(homeRoomId?.takeIf { it in used }) + areaOrder.filter { it != homeRoomId }).map { it to (areaName[it] ?: it) }
        val chosen = prefs.defaultRoom.takeIf { it.isNotBlank() && it in used } ?: prefs.room.takeIf { it.isNotBlank() && it in used }
        showRoom(chosen ?: homeRoomId)
    }

    private fun showRoom(room: String?) {
        roomId = room
        roomName = room?.let { areaNames[it] } ?: ""
        roomIds = if (room == null) emptyList() else states.keys
            .filter { entityArea[it] == room && it !in hiddenIds && it.substringBefore('.') in roomDomains }
            .sortedWith(compareBy({ roomDomains.indexOf(it.substringBefore('.')) }, { states[it]?.name?.lowercase() }))
        Log.i(TAG, "Raum '$roomName' mit ${roomIds.size} Geräten")
    }

    /** Beim Öffnen der Sidebar: gesetzten Standardraum wieder wählen. */
    fun applyDefaultRoom(context: Context) {
        val d = HaPrefs(context).defaultRoom
        if (d.isBlank() || d == roomId || rooms.none { it.first == d }) return
        selectRoom(context, d)
    }

    /** Raumauswahl in der Sidebar; der Raum des Fernsehers setzt die Auswahl zurück. */
    fun selectRoom(context: Context, areaId: String) {
        HaPrefs(context).set("room", if (areaId == homeRoomId) "" else areaId)
        showRoom(areaId)
    }

    /** HA-Symbol der Entität ("mdi:..."), falls gesetzt. */
    fun iconOf(e: HaEntity): String? = registryIcons[e.id] ?: e.attrs.optString("icon").ifBlank { null }

    private fun checkDoorbell(id: String, old: JSONObject?, new: JSONObject?) {
        val ctx = app ?: return
        val prefs = HaPrefs(ctx)
        if (id != prefs.doorbellEvent || !prefs.doorbellPopup || new == null || old == null) return
        val ns = new.optString("state"); val os = old.optString("state")
        // event-Entitäten: Zustand = Zeitpunkt des letzten Ereignisses; ändert er sich, hat es geklingelt
        if (ns == os || ns == "unavailable" || ns == "unknown" || os == "unavailable") return
        Log.i(TAG, "Türklingel: $id")
        onDoorbell?.invoke(states[id]?.name ?: "Haustür")
    }

    // ---- Steuerung ----

    fun call(domain: String, service: String, entityId: String, data: JSONObject = JSONObject()) {
        val socket = ws ?: return
        send(socket, "call_service", JSONObject()
            .put("domain", domain).put("service", service)
            .put("service_data", data)
            .put("target", JSONObject().put("entity_id", entityId))) { r ->
            if (!r.optBoolean("success", true)) Log.w(TAG, "$domain.$service fehlgeschlagen: ${r.optJSONObject("error")}")
        }
    }

    fun toggle(e: HaEntity) {
        // Sofort umschalten, die Bestätigung von HA überschreibt es gleich wieder
        states[e.id] = e.copy(state = if (e.on) "off" else "on")
        call(e.domain, "toggle", e.id)
    }

    fun setBrightness(e: HaEntity, pct: Int) {
        val v = pct.coerceIn(0, 100)
        if (v == 0) { call("light", "turn_off", e.id); return }
        call("light", "turn_on", e.id, JSONObject().put("brightness_pct", v))
    }

    fun setKelvin(e: HaEntity, k: Int) = call("light", "turn_on", e.id, JSONObject().put("color_temp_kelvin", k))

    fun activateScene(id: String) = call("scene", "turn_on", id)

    fun setTemperature(id: String, t: Double) = call("climate", "set_temperature", id, JSONObject().put("temperature", t))

    fun openDoor(context: Context): Boolean {
        val id = HaPrefs(context).doorButton.takeIf { it.isNotBlank() } ?: return false
        if (id.startsWith("lock.")) call("lock", "unlock", id) else call("button", "press", id)
        return true
    }
}

/** Helligkeit in Prozent (0, wenn aus). */
val HaEntity.brightnessPct: Int get() = if (!on) 0 else ((attrs.optDouble("brightness", 255.0) / 255.0) * 100).toInt().coerceIn(1, 100)
val HaEntity.dimmable: Boolean get() = attrs.optJSONArray("supported_color_modes")?.let { a -> (0 until a.length()).any { a.optString(it) != "onoff" } } ?: attrs.has("brightness")
val HaEntity.supportsKelvin: Boolean get() = attrs.optJSONArray("supported_color_modes")?.let { a ->
    (0 until a.length()).any { a.optString(it) == "color_temp" }
} ?: false
