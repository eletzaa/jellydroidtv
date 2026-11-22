package de.jellydroid.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/** Netzwerk für die Sidebar. [ssid] fehlt, wenn Android den Namen nicht verrät (Standortberechtigung). */
data class NetInfo(val kind: Kind, val ssid: String?, val level: Int, val band: String?) {
    enum class Kind { Wifi, Ethernet, None }
    val signalText get() = when (level) { 4 -> "sehr gut"; 3 -> "gut"; 2 -> "mittel"; else -> "schwach" }
}

@Suppress("DEPRECATION")
fun readNet(context: Context): NetInfo {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) return NetInfo(NetInfo.Kind.Ethernet, null, 4, null)
    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return NetInfo(NetInfo.Kind.None, null, 0, null)
    val wm = context.applicationContext.getSystemService(WifiManager::class.java)
    val info = wm?.connectionInfo
    val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID.trim('<', '>') && it != "<unknown ssid>" }
    val level = info?.let { WifiManager.calculateSignalLevel(it.rssi, 5) } ?: 0
    val band = info?.frequency?.let { if (it >= 4900) "5 GHz" else if (it > 0) "2,4 GHz" else null }
    return NetInfo(NetInfo.Kind.Wifi, ssid, level, band)
}
