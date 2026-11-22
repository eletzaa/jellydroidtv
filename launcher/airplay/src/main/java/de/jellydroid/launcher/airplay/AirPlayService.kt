// AirPlay-Empfänger für Jellydroid. Ablauf (Start, Callbacks, MAC-Adresse) nach android-airplay-server
// von jqssun (GPL-3.0), auf das Nötige für Bildschirmsynchronisierung gekürzt.
package de.jellydroid.launcher.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.net.NetworkInterface
import java.security.SecureRandom
import kotlin.math.pow

/**
 * Läuft im eigenen Prozess ":airplay" (siehe Manifest). Stürzt der native Teil ab, bleibt der
 * Startbildschirm davon unberührt.
 */
class AirPlayService : Service(), RaopCallbackHandler {

    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("airplay", Context.MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private var handle = 0L
    private var nsd: NsdServiceManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connections = 0
    private var runningConfig: Triple<Boolean, String, Boolean>? = null

    lateinit var video: VideoRenderer; private set
    private val audio = AudioRenderer()

    override fun onCreate() {
        super.onCreate()
        instance = this
        video = VideoRenderer(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { i ->
            if (i.hasExtra(AirPlay.EXTRA_NAME)) {
                prefs.edit()
                    .putString("name", i.getStringExtra(AirPlay.EXTRA_NAME) ?: AirPlay.DEFAULT_NAME)
                    .putBoolean("pin", i.getBooleanExtra(AirPlay.EXTRA_PIN, false))
                    .apply()
            }
        }
        val name = prefs.getString("name", null)?.ifBlank { null } ?: AirPlay.DEFAULT_NAME
        val pin = prefs.getBoolean("pin", false)
        promoteToForeground(name)
        val cfg = Triple(true, name, pin)
        if (handle != 0L && cfg != runningConfig) shutdown()
        if (handle == 0L) startServer(name, pin)
        return START_STICKY
    }

    private fun startServer(name: String, requirePin: Boolean) {
        nsd = NsdServiceManager(this).apply { acquireMulticastLock() }

        NativeBridge.nativeSetDefaultStreamValues(
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0,
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0,
        )
        val keyFile = filesDir.resolve("airplay.pem").absolutePath
        handle = NativeBridge.nativeInit(this, hwAddr(), name, keyFile, true, requirePin)
        if (handle == 0L) {
            Log.e(TAG, "nativeInit fehlgeschlagen")
            shutdown()
            return
        }
        audio.attachEngine(handle)

        val (w, h) = displaySize()
        val h265 = video.selectDecoders(w, h, MAX_FPS, null)
        val (maxW, maxH) = video.maxResolution()
        val sw = w.coerceAtMost(maxW)
        val sh = h.coerceAtMost(maxH)
        NativeBridge.nativeSetH265Enabled(handle, h265)
        // Ohne FFmpeg kein ALAC: iPhone und Mac schicken dann AAC.
        NativeBridge.nativeSetCodecs(handle, false, true)
        NativeBridge.nativeSetHlsEnabled(handle, false)
        NativeBridge.nativeSetLang(handle, resources.configuration.locales.toLanguageTags().replace(',', ':'))
        NativeBridge.nativeSetFeature(handle, NativeBridge.FT_MIRROR, true)
        // Nur Spiegeln: Foto (1), FairPlay-Video (2) und Diashow (5) aus. Sonst schicken iPad-Apps
        // (z. B. ImmoScout) Bilder/Inhalte als eigene Ansicht statt den Bildschirm zu spiegeln.
        NativeBridge.nativeSetFeature(handle, 1, false)
        NativeBridge.nativeSetFeature(handle, 2, false)
        NativeBridge.nativeSetFeature(handle, 5, false)
        // Audio-Bit bleibt gesetzt wie im getesteten Original: ohne es beendet iOS die Spiegelung vermutlich nach etwa 60 s.
        // Musik-Lautsprecher wird der TV trotzdem nicht, weil kein _raop._tcp angemeldet wird.
        NativeBridge.nativeSetFeature(handle, NativeBridge.FT_AUDIO, true)
        NativeBridge.nativeSetPlist(handle, "maxFPS", MAX_FPS)
        NativeBridge.nativeSetPlist(handle, "overscanned", 0)
        video.setResolution(sw, sh)
        NativeBridge.nativeSetDisplaySize(handle, sw, sh, MAX_FPS)

        val port = NativeBridge.nativeStart(handle, 0)
        if (port < 0) {
            Log.e(TAG, "Server-Start fehlgeschlagen")
            shutdown()
            return
        }
        val airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(handle) ?: emptyMap()
        val serverName = NativeBridge.nativeGetServerName(handle) ?: name
        nsd?.register(listOf(
            MdnsService(serverName, "_airplay._tcp", port, airplayTxt),
        ))
        runningConfig = Triple(true, name, requirePin)
        AirPlayState.running = true
        Log.i(TAG, "AirPlay läuft als „$serverName“ auf Port $port (${sw}x$sh, H.265=$h265)")
    }

    private fun shutdown() {
        audio.detachEngine()
        if (handle != 0L) {
            NativeBridge.nativeStop(handle)
            NativeBridge.nativeDestroy(handle)
            handle = 0L
        }
        nsd?.release(); nsd = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        video.release()
        connections = 0
        runningConfig = null
        main.post { AirPlayState.reset() }
    }

    override fun onDestroy() {
        shutdown()
        instance = null
        super.onDestroy()
    }

    // ---- Bildfläche der Vollbild-Ansicht ----
    fun attachSurface(s: Surface) = video.setSurface(s)
    fun detachSurface(s: Surface) = video.clearSurface(s)

    /** Verbindung vom Fernseher aus beenden (Zurück-Taste in der Vollbild-Ansicht). */
    fun disconnect() {
        val cfg = runningConfig ?: return
        shutdown()
        startServer(cfg.second, cfg.third)
    }

    // ---- Callbacks aus dem nativen Teil (fremde Threads) ----

    // Muss void zurückgeben: die native Seite sucht onLog(String)V
    fun onLog(msg: String) { Log.d(TAG, msg) }

    override fun onVideoData(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) =
        video.feedFrame(data, ntpTimeNs, isH265)

    override fun onAudioFormat(ct: Int, spf: Int, usingScreen: Boolean) {
        main.post { AirPlayState.pin = null }
        audio.start()
        audio.setFormat(ct, spf)
    }

    override fun onVideoSize(srcW: Float, srcH: Float, w: Float, h: Float) {
        if (w > 0 && h > 0) {
            video.setResolution(w.toInt(), h.toInt())
            main.post {
                AirPlayState.pin = null
                AirPlayState.aspect = w / h
                AirPlayState.mirroring = true
                openScreen()
            }
        }
    }

    override fun onClientVolume(db: Float) {
        val gain = if (db <= -144f) 0f else 10f.pow(db.coerceIn(-30f, 0f) / 20f)
        audio.setClientGain(gain)
    }

    override fun onClientVolumeQuery(): Float = 0f

    override fun onAudioTeardown() = Unit

    override fun onConnectionInit() {
        connections++
        // Nur während einer Verbindung wach halten, sonst stört es den Standby des Fernsehers.
        if (wakeLock == null) wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jellydroid:airplay").apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
        main.post { AirPlayState.connected = true }
    }

    override fun onConnectionDestroy() {
        connections = (connections - 1).coerceAtLeast(0)
        if (connections == 0) {
            audio.stop()
            wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
            main.post { AirPlayState.reset(); AirPlayState.running = handle != 0L }
        }
    }

    override fun onConnectionReset(reason: Int) { Log.w(TAG, "Verbindung zurückgesetzt, Grund $reason") }

    override fun onDisplayPin(pin: String) {
        main.post { AirPlayState.pin = pin; openScreen() }
    }

    override fun onMetadata(data: ByteArray) = Unit
    override fun onCoverArt(data: ByteArray) = Unit
    override fun onProgress(start: Long, curr: Long, end: Long) = Unit
    override fun onDacpId(dacpId: String, activeRemote: String) = Unit

    override fun onMirrorRunning(running: Boolean) {
        if (running) video.startSession() else video.stopSession()
        main.post { AirPlayState.mirroring = running }
    }

    // AirPlay-Video (HLS-Links) ist nicht angemeldet; iPhone-Apps spiegeln dann den Bildschirm.
    override fun onVideoPlay(location: String, startPositionSeconds: Float) = Unit
    override fun onVideoScrub(positionSeconds: Float) = Unit
    override fun onVideoRate(rate: Float) = Unit
    override fun onVideoStop() = Unit
    override fun onVideoSessionPoll() = Unit

    // ---- Hilfen ----

    private fun openScreen() {
        if (AirPlayState.screenVisible) return
        runCatching {
            startActivity(Intent(this, AirPlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }.onFailure { Log.w(TAG, "Vollbild konnte nicht geöffnet werden", it) }
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): Pair<Int, Int> {
        val m = android.util.DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        val w = maxOf(m.widthPixels, m.heightPixels)
        val h = minOf(m.widthPixels, m.heightPixels)
        // Mehr als 1080p bringt beim Spiegeln nichts und kostet Bandbreite.
        return if (h > 1080) 1920 to 1080 else w to h
    }

    private fun hwAddr(): ByteArray {
        runCatching {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val mac = iface.hardwareAddress
                    if (usable(mac)) return mac!!
                }
            }
        }
        // Android liefert Apps meist keine echte MAC: feste Zufallsadresse pro Installation.
        prefs.getString("mac", null)?.split(":")?.mapNotNull { it.toIntOrNull(16)?.toByte() }
            ?.toByteArray()?.takeIf { usable(it) }?.let { return it }
        val mac = ByteArray(6).also { SecureRandom().nextBytes(it) }
        mac[0] = ((mac[0].toInt() and 0xF0) or 0x0A).toByte()
        prefs.edit().putString("mac", mac.joinToString(":") { "%02x".format(it) }).apply()
        return mac
    }

    private fun usable(mac: ByteArray?) = mac != null && mac.size == 6 && mac.any { it != 0.toByte() } &&
        !(mac[0] == 0x02.toByte() && mac.drop(1).all { it == 0.toByte() })

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "AirPlay", NotificationManager.IMPORTANCE_MIN)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun promoteToForeground(name: String) {
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("AirPlay")
            .setContentText(name)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, 41, n, type)
    }

    companion object {
        private const val TAG = "JellydroidAirPlay"
        private const val CHANNEL = "airplay"
        private const val MAX_FPS = 60

        @Volatile var instance: AirPlayService? = null; private set
    }
}
