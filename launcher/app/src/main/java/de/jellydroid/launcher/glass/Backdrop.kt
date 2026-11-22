package de.jellydroid.launcher.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Platzhalter-Hintergrund je Tab, bis in Schritt 3 die Jellyfin-Bilder kommen. */
data class Scene(val skyTop: Int, val skyBottom: Int, val sun: Int, val sunX: Float, val sunY: Float, val hills: List<Int>)

object Scenes {
    val start = Scene(0xFF2A1A14.toInt(), 0xFF6B3A24.toInt(), 0xFFFFCF8A.toInt(), .78f, .30f,
        listOf(0xFF3B2418.toInt(), 0xFF2A1810.toInt(), 0xFF1A0F0A.toInt()))
    val discover = Scene(0xFF1C2440.toInt(), 0xFFC47A3C.toInt(), 0xFFFFD9A0.toInt(), .74f, .36f,
        listOf(0xFFA8642C.toInt(), 0xFF7A4420.toInt(), 0xFF4A2812.toInt()))
    val apps = Scene(0xFF0E1830.toInt(), 0xFF2C4A8A.toInt(), 0xFFCFE0FF.toInt(), .80f, .25f,
        listOf(0xFF24407A.toInt(), 0xFF182C58.toInt(), 0xFF0E1A38.toInt()))
}

fun paintBackdrop(width: Int, height: Int, s: Scene): Bitmap {
    val w = max(width, 2)
    val h = max(height, 2)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wf = w.toFloat()
    val hf = h.toFloat()

    p.shader = LinearGradient(0f, 0f, 0f, hf * .75f, s.skyTop, s.skyBottom, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)

    val sx = s.sunX * wf
    val sy = s.sunY * hf
    val sr = hf * .16f
    p.shader = RadialGradient(sx, sy, sr * 4f, withAlpha(s.sun, 0xCC), withAlpha(s.sun, 0), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    p.shader = null
    p.color = s.sun
    c.drawCircle(sx, sy, sr, p)

    // feine Streifen, an denen man die Lichtbrechung sieht
    p.color = 0x1FFFFFFF
    for (i in 0 until 14) {
        val y = hf * (.06f + i * .028f)
        c.drawRect(wf * .35f, y, wf, y + max(1f, hf * .004f), p)
    }

    s.hills.forEachIndexed { i, color ->
        val path = Path()
        val base = hf * (.58f + i * .11f)
        path.moveTo(0f, hf)
        var x = 0f
        while (x <= wf) {
            val t = x / wf
            val y = base + sin(t * PI * (2 + i) + i * 1.7).toFloat() * hf * .05f +
                sin(t * PI * 7 + i).toFloat() * hf * .012f
            path.lineTo(x, y)
            x += wf / 80f
        }
        path.lineTo(wf, hf)
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    // Lesbarkeit: links und unten abdunkeln
    p.shader = LinearGradient(0f, 0f, wf, 0f, intArrayOf(0xC7040610.toInt(), 0x40040610, 0x00040610), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    p.shader = LinearGradient(0f, hf * .45f, 0f, hf, 0x00040610, 0xD9040610.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    return bmp
}

/**
 * Standard-Hintergrund ohne Bilddaten (Apps-Tab und solange nichts geladen ist):
 * fast schwarz mit weichem, grau leuchtendem Schein oben und einem schwachen unten links.
 */
/** Heller Standard-Hintergrund: helles Grau mit warmem Licht links und kühlem Ton rechts. */
fun paintGlowLight(width: Int, height: Int): Bitmap {
    val w = max(width, 2)
    val h = max(height, 2)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wf = w.toFloat()
    val hf = h.toFloat()
    p.shader = LinearGradient(0f, 0f, wf, 0f,
        // etwas gedämpfter als reines Weiß, damit der Bildschirm nicht blendet
        intArrayOf(0xFFDCD6CC.toInt(), 0xFFCFD3D7.toInt(), 0xFFC2CAD1.toInt(), 0xFFB4BEC8.toInt()),
        floatArrayOf(0f, .35f, .65f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    p.shader = RadialGradient(wf * .04f, hf * .6f, wf * .45f,
        intArrayOf(0x88F6F0E6.toInt(), 0x33F2E6D4, 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    return bmp
}

fun paintGlow(width: Int, height: Int): Bitmap {
    val w = max(width, 2)
    val h = max(height, 2)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val wf = w.toFloat()
    val hf = h.toFloat()
    // Vorlage von Elias: warmer Lichtschein links, über Petrol-Grau ins Nachtblau rechts, feine Körnung
    p.shader = LinearGradient(0f, 0f, wf, 0f,
        intArrayOf(0xFF3A464C.toInt(), 0xFF2A3C46.toInt(), 0xFF1A2A35.toInt(), 0xFF0D151D.toInt()),
        floatArrayOf(0f, .35f, .65f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    p.shader = RadialGradient(wf * .04f, hf * .6f, wf * .42f,
        intArrayOf(0xB3D2C8B8.toInt(), 0x66788C92, 0x1A4A6470, 0x00000000),
        floatArrayOf(0f, .4f, .75f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, wf, hf, p)
    // leicht abdunkeln, damit weiße Schrift überall gut lesbar bleibt
    p.shader = null
    p.color = 0x40000000
    c.drawRect(0f, 0f, wf, hf, p)
    // Körnung
    val rnd = java.util.Random(7)
    val px = IntArray(w)
    for (y in 0 until h) {
        bmp.getPixels(px, 0, w, 0, y, w, 1)
        for (x in 0 until w) {
            val n = rnd.nextInt(13) - 6
            val col = px[x]
            val r = ((col shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((col shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((col and 0xFF) + n).coerceIn(0, 255)
            px[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, y, w, 1)
    }
    return bmp
}

private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)
