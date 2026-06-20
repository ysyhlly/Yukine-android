package app.yukine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.yukine.data.EmbeddedArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

object ArtworkLoader {
    private const val BYTES_PER_KIB = 1024
    private const val FALLBACK_CACHE_KIB = 8 * 1024

    /** Hard ceiling on decoded edge length so a huge source image can never blow the heap. */
    const val MAX_TARGET_PX = 1536

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKib()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / BYTES_PER_KIB).coerceAtLeast(1)
        }
    }

    suspend fun load(context: Context, albumArtUri: Uri, targetPx: Int): Bitmap? {
        val safeTargetPx = targetPx.coerceIn(1, MAX_TARGET_PX)
        val key = albumArtUri.toString() + "#" + safeTargetPx
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeSampledBitmap(context, albumArtUri, safeTargetPx)?.also { bitmap ->
                cache.put(key, bitmap)
            }
        }
    }

    /**
     * Returns any already-decoded bitmap for [albumArtUri] regardless of the size it was decoded at.
     * Used to show an instant placeholder (e.g. the now-bar's small cover) while the correctly-sized
     * bitmap decodes, so opening the full-screen player never flashes an empty square.
     */
    fun peekAnySize(albumArtUri: Uri): Bitmap? {
        val prefix = albumArtUri.toString() + "#"
        val snapshot = cache.snapshot()
        return snapshot.entries.firstOrNull { it.key.startsWith(prefix) }?.value
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            return decodeSampledEmbeddedArtwork(context, uri, targetPx)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            openArtworkStream(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            openArtworkStream(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun decodeSampledEmbeddedArtwork(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val bytes = EmbeddedArtwork.read(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun openArtworkStream(context: Context, uri: Uri): java.io.InputStream? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return context.contentResolver.openInputStream(uri)
        }
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 ECHO-NEXT-Android")
        connection.setRequestProperty("Referer", "https://music.163.com/")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            return null
        }
        return connection.inputStream
    }

    private fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sample >= targetPx && halfHeight / sample >= targetPx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun cacheSizeKib(): Int {
        val runtimeKib = (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB).toInt()
        return (runtimeKib / 8).coerceAtLeast(FALLBACK_CACHE_KIB)
    }
}

@Composable
fun AsyncArtwork(
    uri: Uri?,
    title: String,
    subtitle: String,
    modifier: Modifier,
    cornerRadius: Dp,
    fallbackTextSize: TextUnit,
    targetSize: Dp,
    backgroundColor: Color,
    @DrawableRes fallbackResId: Int? = null
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Seed with any already-decoded bitmap for this URI (possibly at a different size, e.g. the
    // now-bar's small cover) so opening the full-screen player shows the cover instantly instead
    // of flashing an empty square while the correctly-sized bitmap decodes.
    var bitmap by remember(uri) {
        mutableStateOf(uri?.let { ArtworkLoader.peekAnySize(it) })
    }

    val safeFallbackResId = remember(context, fallbackResId) {
        fallbackResId?.takeIf { isPainterResourceCompatible(context, it) }
    }
    val fallbackPainter: Painter? = safeFallbackResId?.let { painterResource(it) }

    LaunchedEffect(uri, targetSize) {
        if (uri != null) {
            val targetPx = with(density) { targetSize.toPx().toInt() }
            val loaded = ArtworkLoader.load(context.applicationContext, uri, targetPx)
            if (loaded != null) {
                bitmap = loaded
            }
        } else {
            bitmap = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        val loadedBitmap = bitmap
        // Crossfade between fallback/placeholder and the resolved cover so a new cover fades in
        // rather than snapping. Keyed on the bitmap identity so re-feeding the same bitmap
        // (e.g. on a progress tick) does not re-trigger the animation.
        Crossfade(
            targetState = loadedBitmap,
            animationSpec = tween(durationMillis = 220),
            label = "artwork"
        ) { current ->
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (uri == null) {
                EchoArtworkFallback(title, subtitle, Modifier.fillMaxSize(), cornerRadius, fallbackTextSize)
            } else if (fallbackPainter != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = fallbackPainter,
                        contentDescription = null,
                        modifier = Modifier.size((targetSize.value / 2).dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

private fun isPainterResourceCompatible(context: Context, @DrawableRes resId: Int): Boolean {
    val value = TypedValue()
    return runCatching {
        context.resources.getValue(resId, value, true)
        val path = value.string?.toString()?.lowercase() ?: return@runCatching false
        when {
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") -> true
            path.endsWith(".xml") -> context.resources.getXml(resId).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        return@use parser.name == "vector"
                    }
                }
                false
            }
            else -> false
        }
    }.getOrDefault(false)
}
