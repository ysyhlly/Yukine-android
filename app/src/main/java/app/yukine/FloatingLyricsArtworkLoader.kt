package app.yukine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FloatingLyricsArtworkLoader(context: Context) {
    private val appContext = context.applicationContext
    private val cache = object : LruCache<String, Bitmap>(4) {}

    suspend fun load(uriValue: String?, targetSizePx: Int): Bitmap? {
        val value = uriValue?.takeIf(String::isNotBlank) ?: return null
        cache.get(value)?.let { return it }
        val uri = runCatching(Uri::parse).getOrNull() ?: return null
        if (uri.scheme == "http" || uri.scheme == "https") return null
        return withContext(Dispatchers.IO) {
            decode(uri, targetSizePx)?.also { cache.put(value, it) }
        }
    }

    private fun decode(uri: Uri, targetSizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetSizePx.coerceAtLeast(1))
        }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }
}
