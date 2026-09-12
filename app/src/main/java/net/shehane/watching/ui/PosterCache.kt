package net.shehane.watching.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.shehane.watching.data.Http
import net.shehane.watching.data.Tmdb
import java.io.File
import java.security.MessageDigest

/**
 * Posters, cached in memory and on disk.
 *
 * A whole image library would be a reasonable dependency, but posters are the only
 * images this app ever loads and they are immutable once fetched, so the useful
 * part is about forty lines. Each poster is downloaded once per phone and then
 * read from disk forever.
 */
object PosterCache {

    /** Roughly 40 small posters in memory, which is more than one screen holds. */
    private val memory = LruCache<String, ImageBitmap>(40)

    private fun keyFor(path: String, large: Boolean) = (if (large) "L" else "S") + path

    private fun fileNameFor(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    fun cached(key: String): ImageBitmap? = memory.get(key)

    suspend fun load(dir: File, path: String, large: Boolean): ImageBitmap? {
        val key = keyFor(path, large)
        memory.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(dir, fileNameFor(key))
                val bytes = if (file.exists() && file.length() > 0) {
                    file.readBytes()
                } else {
                    val base = if (large) Tmdb.IMAGE_LARGE else Tmdb.IMAGE_SMALL
                    val downloaded = Http.getBytes(base + path)
                    dir.mkdirs()
                    file.writeBytes(downloaded)
                    downloaded
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching null
                val image = bmp.asImageBitmap()
                memory.put(key, image)
                image
            }.getOrNull()
        }
    }
}

/**
 * Null on the first frame and until the bytes arrive, which is why every caller
 * draws a placeholder underneath rather than a spinner. Nothing in the list waits.
 */
@Composable
fun rememberPoster(path: String?, large: Boolean = false): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    val context = LocalContext.current
    val dir = remember(context) { File(context.cacheDir, "posters") }

    var image by remember(path, large) {
        mutableStateOf(PosterCache.cached((if (large) "L" else "S") + path))
    }
    LaunchedEffect(path, large) {
        if (image == null) image = PosterCache.load(dir, path, large)
    }
    return image
}
