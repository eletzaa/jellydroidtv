package de.elias.launcher.airplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Zustand für die Vollbild-Ansicht (nur im Prozess ":airplay", nur vom Hauptthread ändern). */
object AirPlayState {
    var running by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var mirroring by mutableStateOf(false)
    var pin by mutableStateOf<String?>(null)
    var aspect by mutableStateOf(16f / 9f)
    @Volatile var screenVisible = false

    fun reset() {
        connected = false; mirroring = false; pin = null
    }
}
