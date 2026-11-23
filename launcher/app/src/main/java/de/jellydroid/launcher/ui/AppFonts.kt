package de.jellydroid.launcher.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.jellydroid.launcher.R
import java.io.File
import java.io.RandomAccessFile

/**
 * Schriften des Launchers.
 *
 * Standard: Inter (res/font/inter_*, SIL Open Font License, Lizenztext in assets/lizenzen/).
 * Eigene Schrift: Der Installer schiebt eine oder mehrere TTF/OTF-Dateien einer Schriftfamilie an den TV,
 * SetupImport kopiert sie nach filesDir/schrift/. Die Schriftstärke jeder Datei steht in ihrer OS/2-Tabelle;
 * kursive Schnitte werden übersprungen. Fehlt eine Stärke, nimmt Compose die nächstliegende.
 */
object AppFonts {
    private const val TAG = "AppFonts"
    private val EXT = setOf("ttf", "otf")

    private val interDisplay = FontFamily(
        Font(R.font.inter_display_regular, FontWeight.Normal),
        Font(R.font.inter_display_medium, FontWeight.Medium),
        Font(R.font.inter_display_semibold, FontWeight.SemiBold),
        Font(R.font.inter_display_bold, FontWeight.Bold),
    )
    private val interText = FontFamily(
        Font(R.font.inter_text_regular, FontWeight.Normal),
        Font(R.font.inter_text_medium, FontWeight.Medium),
        Font(R.font.inter_text_semibold, FontWeight.SemiBold),
        Font(R.font.inter_text_bold, FontWeight.Bold),
    )

    @Volatile var display: FontFamily = interDisplay; private set
    @Volatile var text: FontFamily = interText; private set
    /** true, wenn eine eigene Schrift aus dem Installer aktiv ist. */
    @Volatile var custom: Boolean = false; private set

    fun dir(context: Context) = File(context.filesDir, "schrift")

    fun isFontFile(f: File) = f.isFile && f.extension.lowercase() in EXT

    fun load(context: Context) {
        val family = runCatching { customFamily(dir(context)) }
            .onFailure { Log.w(TAG, "Eigene Schrift nicht lesbar", it) }.getOrNull()
        if (family != null) { display = family; text = family; custom = true }
        else { display = interDisplay; text = interText; custom = false }
    }

    private fun customFamily(dir: File): FontFamily? {
        val files = dir.listFiles()?.filter { isFontFile(it) }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return null
        // Je Stärke eine Datei, aufrechte Schnitte bevorzugt
        val byWeight = LinkedHashMap<Int, File>()
        val infos = files.filter { android.graphics.Typeface.Builder(it).build() != null }
            .mapNotNull { f -> readInfo(f)?.let { f to it } }
        val upright = infos.filter { !it.second.italic }.ifEmpty { infos }
        for ((f, i) in upright) byWeight.putIfAbsent(i.weight, f)
        if (byWeight.isEmpty()) return null
        return FontFamily(byWeight.map { (w, f) -> Font(f, FontWeight(w.coerceIn(1, 1000))) })
    }

    private class Info(val weight: Int, val italic: Boolean)

    /** Liest usWeightClass und fsSelection (kursiv) aus der OS/2-Tabelle. Ohne OS/2: normal, aufrecht. */
    private fun readInfo(f: File): Info? = RandomAccessFile(f, "r").use { r ->
        val tag = r.readInt()
        // 0x00010000 = TrueType, 'OTTO' = CFF, 'true' = altes Apple-TrueType
        if (tag != 0x00010000 && tag != 0x4F54544F && tag != 0x74727565) return null
        val n = r.readUnsignedShort()
        r.skipBytes(6)
        repeat(n) {
            val t = r.readInt(); r.readInt()
            val off = r.readInt().toLong() and 0xFFFFFFFFL; r.readInt()
            if (t == 0x4F532F32) { // 'OS/2'
                r.seek(off + 4); val weight = r.readUnsignedShort()
                r.seek(off + 62); val sel = r.readUnsignedShort()
                return Info(if (weight in 1..1000) weight else 400, sel and 0x1 != 0)
            }
        }
        Info(400, f.name.contains("italic", ignoreCase = true))
    }
}
