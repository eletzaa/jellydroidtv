package de.elias.launcher.ui

import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object LauncherColors {
    val Accent = Color(0xFF7AA7FF)
    val Ok = Color(0xFF5FD39A)
    val Text = Color(0xFFF4F6FB)
    val TextDim = Color(0xC7FFFFFF)
    val TextOnLight = Color(0xFF0D1220)
}

/** Schriften: Standard ist Inter (mitgeliefert, OFL). Eine eigene Schrift aus dem Installer ersetzt beide (siehe AppFonts). */
val DisplayFont: FontFamily get() = AppFonts.display
val TextFont: FontFamily get() = AppFonts.text

object LauncherType {
    val Tab get() = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val Clock get() = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val Headline get() = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.028).em, lineHeight = 42.sp)
    val Eyebrow get() = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em)
    val Row get() = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val Body get() = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val Tile get() = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
}

/** Apples Standardfeder: kritisch gedämpft (1,0), Antwortzeit 0,35 s → Steifigkeit (2π/0,35)² ≈ 322. */
fun <T> appleSpring() = spring<T>(dampingRatio = 1f, stiffness = 322f)
