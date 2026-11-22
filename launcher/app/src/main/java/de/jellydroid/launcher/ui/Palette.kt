package de.jellydroid.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Hell/Dunkel-Umschaltung (Konfiguration › Design). Die Farben sind Getter auf einem
 * Compose-State, deshalb färbt sich die ganze Oberfläche sofort um.
 */
object Pal {
    var light by mutableStateOf(false)
}

/** Schrift- und Linienfarbe: weiß im dunklen, fast schwarz im hellen Modus. */
val Ink: Color get() = if (Pal.light) Color(0xFF14171C) else Color.White
/** Schrift auf fokussierten (vollen) Flächen. */
val InkInverse: Color get() = if (Pal.light) Color.White else LauncherColors.TextOnLight
val InkDim: Color get() = if (Pal.light) Color(0xB314171C) else LauncherColors.TextDim
/** Grundton hinter Titelbildern (Verläufe) und für Fenster. */
val Base: Color get() = if (Pal.light) Color(0xFFD6DBE1) else Color(0xFF0B0E16)
val Surface: Color get() = if (Pal.light) Color(0xF5E6E9EE) else Color(0xF0161A24)
val TilePlaceholder: Color get() = if (Pal.light) Color(0xFFC6CCD4) else Color(0xFF1E2330)
/** Schatten fokussierter Kacheln: im hellen Modus nur ein Hauch. */
val ShadowColor: Color get() = if (Pal.light) Color(0x40000000) else Color.Black
/** Glas (Leiste, Tab-Linse) im matten Modus. */
val GlassBase: Color get() = if (Pal.light) Color(0xFFF4F6F8) else Color(0xFF1C1F26)
val GlassEdge: Color get() = if (Pal.light) Color(0x1F000000) else Color(0x38FFFFFF)
