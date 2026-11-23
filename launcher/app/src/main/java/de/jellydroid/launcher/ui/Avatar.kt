package de.jellydroid.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File

/**
 * Profilbild oben rechts. Der Installer kopiert die gewählte Datei per adb nach
 * /sdcard/Android/data/de.jellydroid.launcher/files/avatar.jpg (oder avatar.png, avatar.jpeg, avatar.webp).
 * Gelesen wird bei jedem Zurückkehren in den Launcher, ein neues Bild erscheint also ohne Neustart.
 */
object AvatarFile {
    val names = listOf("avatar.jpg", "avatar.jpeg", "avatar.png", "avatar.webp")

    fun find(context: Context): File? =
        context.getExternalFilesDir(null)?.let { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && it.length() > 0 } }

    /** Mittiger quadratischer Ausschnitt, auf 192 px verkleinert. */
    fun load(file: File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(square, 192, 192, true).asImageBitmap()
    }.getOrNull()
}

@Composable
fun rememberAvatar(context: Context): ImageBitmap? {
    var state by remember { mutableStateOf<Pair<Long, ImageBitmap?>>(-1L to null) }
    fun reload() {
        val f = AvatarFile.find(context)
        val stamp = f?.let { it.lastModified() + it.length() } ?: 0L
        if (stamp != state.first) state = stamp to f?.let { AvatarFile.load(it) }
    }
    if (state.first == -1L) reload()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    return state.second
}
