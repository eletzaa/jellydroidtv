package de.elias.launcher.jellyfin

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Kleiner Bildlader mit Speicher-Cache, damit keine weitere Bibliothek nötig ist. */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val parallel = Semaphore(4)

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return parallel.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 12000
                    try {
                        conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()?.also { cache.put(url, it) }
            }
        }
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(url) { mutableStateOf(url?.let { ImageCache.peek(it) }) }
    LaunchedEffect(url) { if (url != null && bmp == null) bmp = ImageCache.load(url) }
    bmp?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = modifier) }
}
