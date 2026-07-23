package app.yukine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.annotation.SuppressLint
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.common.EmbeddedArtwork
import app.yukine.common.ApplicationNetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.Request

object ArtworkLoader {
    private const val BYTES_PER_KIB = 1024
    private const val FALLBACK_CACHE_KIB = 16 * 1024
    private const val MAX_CACHE_KIB = 24 * 1024
    private const val NETWORK_ARTWORK_CACHE_BYTES = 96L * 1024L * 1024L
    private const val MAX_NETWORK_ARTWORK_BYTES = 16L * 1024L * 1024L
    private const val NETWORK_ARTWORK_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L

    /** Hard ceiling on decoded edge length so a huge source image can never blow the heap. */
    const val MAX_TARGET_PX = 1536
    /** Crop previews keep extra detail without ever decoding an unbounded ARGB bitmap. */
    internal const val MAX_PREVIEW_TARGET_PX = 2048

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKib()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / BYTES_PER_KIB).coerceAtLeast(1)
        }
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()
    private val networkFileInFlight = ConcurrentHashMap<String, CompletableFuture<File?>>()
    private val artworkHttpClient = ApplicationNetworkClient.httpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun load(context: Context, albumArtUri: Uri, targetPx: Int): Bitmap? {
        val safeTargetPx = targetPx.coerceIn(1, MAX_TARGET_PX)
        val key = albumArtUri.toString() + "#" + safeTargetPx
        return loadDeduplicated(key) {
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(context, albumArtUri, safeTargetPx)
            }
        }
    }

    suspend fun loadOriginal(context: Context, albumArtUri: Uri): Bitmap? {
        val key = albumArtUri.toString() + "#preview-$MAX_PREVIEW_TARGET_PX"
        return loadDeduplicated(key) {
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(
                    context = context,
                    uri = albumArtUri,
                    targetPx = MAX_PREVIEW_TARGET_PX,
                    preferredConfig = Bitmap.Config.ARGB_8888
                )
            }
        }
    }

    private suspend fun loadDeduplicated(
        key: String,
        decode: suspend () -> Bitmap?
    ): Bitmap? {
        cache.get(key)?.let { return it }
        val pending = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        try {
            cache.get(key)?.let {
                pending.complete(it)
                return it
            }
            val bitmap = decode()
            bitmap?.let { cache.put(key, it) }
            pending.complete(bitmap)
            return bitmap
        } catch (cancelled: CancellationException) {
            pending.cancel(cancelled)
            throw cancelled
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
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

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        targetPx: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            return decodeSampledEmbeddedArtwork(context, uri, targetPx, preferredConfig)
        }
        return artworkCandidates(uri).firstNotNullOfOrNull { candidate ->
            decodeSampledBitmapCandidate(context, candidate, targetPx, preferredConfig)
        }
    }

    private fun decodeSampledBitmapCandidate(
        context: Context,
        uri: Uri,
        targetPx: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
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
            inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = preferredConfig
        }
        return runCatching {
            openArtworkStream(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }

    private fun artworkCandidates(uri: Uri): List<Uri> {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return listOf(uri)
        }
        val raw = uri.toString()
        val candidates = linkedSetOf(raw)
        val noQuery = raw.substringBefore('?')
        if (noQuery != raw) {
            candidates += noQuery
        }
        if ("music.126.net" in raw || "p1.music.126.net" in raw || "p2.music.126.net" in raw) {
            candidates += "$noQuery?param=512y512"
            candidates += "$noQuery?param=300y300"
        }
        return candidates.map(Uri::parse)
    }

    private fun decodeSampledEmbeddedArtwork(
        context: Context,
        uri: Uri,
        targetPx: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
        val bytes = EmbeddedArtwork.read(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            inPreferredConfig = preferredConfig
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun openArtworkStream(context: Context, uri: Uri): InputStream? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return context.contentResolver.openInputStream(uri)
        }
        return cachedNetworkArtwork(context.applicationContext, uri)?.inputStream()
    }

    private fun cachedNetworkArtwork(context: Context, uri: Uri): File? {
        val rawUrl = uri.toString()
        val cacheDir = File(context.cacheDir, "artwork-originals").apply { mkdirs() }
        val cacheFile = File(cacheDir, ArtworkDiskCachePolicy.fileName(rawUrl))
        val now = System.currentTimeMillis()
        if (ArtworkDiskCachePolicy.isFresh(cacheFile, now, NETWORK_ARTWORK_MAX_AGE_MS)) {
            cacheFile.setLastModified(now)
            return cacheFile
        }

        val pending = CompletableFuture<File?>()
        val existing = networkFileInFlight.putIfAbsent(rawUrl, pending)
        if (existing != null) {
            return runCatching { existing.get() }.getOrNull()
        }
        try {
            val downloaded = downloadArtwork(rawUrl, cacheDir, cacheFile)
            val result = downloaded ?: cacheFile.takeIf { it.isFile && it.length() > 0L }
            pending.complete(result)
            return result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            return cacheFile.takeIf { it.isFile && it.length() > 0L }
        } finally {
            networkFileInFlight.remove(rawUrl, pending)
        }
    }

    private fun downloadArtwork(rawUrl: String, cacheDir: File, destination: File): File? {
        val request = Request.Builder()
            .url(rawUrl)
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .header("User-Agent", "Mozilla/5.0 Yukine-Android")
            .header("Referer", "https://music.163.com/")
            .build()
        return artworkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_NETWORK_ARTWORK_BYTES) return@use null
            val temporary = File.createTempFile("artwork-", ".part", cacheDir)
            try {
                if (!copyArtworkBody(body.byteStream(), temporary)) return@use null
                if (temporary.length() <= 0L) return@use null
                if (destination.exists() && !destination.delete()) return@use null
                if (!temporary.renameTo(destination)) return@use null
                destination.setLastModified(System.currentTimeMillis())
                ArtworkDiskCachePolicy.trim(cacheDir, NETWORK_ARTWORK_CACHE_BYTES, destination)
                destination
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun copyArtworkBody(input: InputStream, destination: File): Boolean {
        return input.use { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_NETWORK_ARTWORK_BYTES) return false
                    output.write(buffer, 0, count)
                }
                true
            }
        }
    }

    private fun cacheSizeKib(): Int {
        val runtimeKib = (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB).toInt()
        return (runtimeKib / 8).coerceIn(FALLBACK_CACHE_KIB, MAX_CACHE_KIB)
    }
}

internal fun artworkSampleSize(width: Int, height: Int, targetPx: Int): Int {
    if (width <= 0 || height <= 0 || targetPx <= 0) return 1
    var sample = 1
    val maxEdge = maxOf(width, height)
    while (maxEdge / sample > targetPx) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
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
    @DrawableRes fallbackResId: Int? = null,
    crossfadeEnabled: Boolean = true
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
        if (crossfadeEnabled) {
            Crossfade(
                targetState = loadedBitmap,
                animationSpec = tween(durationMillis = 220),
                label = "artwork"
            ) { current ->
                ArtworkFrame(
                    current,
                    uri,
                    title,
                    subtitle,
                    cornerRadius,
                    fallbackTextSize,
                    targetSize,
                    fallbackPainter
                )
            }
        } else {
            ArtworkFrame(
                loadedBitmap,
                uri,
                title,
                subtitle,
                cornerRadius,
                fallbackTextSize,
                targetSize,
                fallbackPainter
            )
        }
    }

}

internal object ArtworkDiskCachePolicy {
    fun fileName(rawUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawUrl.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) } + ".img"
    }

    fun isFresh(file: File, now: Long, maxAgeMs: Long): Boolean {
        return file.isFile && file.length() > 0L && now - file.lastModified() in 0..maxAgeMs
    }

    fun trim(directory: File, maxBytes: Long, protectedFile: File? = null) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "img" }
            ?.sortedBy(File::lastModified)
            .orEmpty()
        var total = files.sumOf(File::length)
        if (total <= maxBytes) return
        for (file in files) {
            if (file == protectedFile) continue
            val length = file.length()
            if (file.delete()) total -= length
            if (total <= maxBytes) break
        }
    }
}

@Composable
private fun ArtworkFrame(
    bitmap: Bitmap?,
    uri: Uri?,
    title: String,
    subtitle: String,
    cornerRadius: Dp,
    fallbackTextSize: TextUnit,
    targetSize: Dp,
    fallbackPainter: Painter?
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else if (uri == null) {
        InlineArtworkFallback(title, subtitle, Modifier.fillMaxSize(), cornerRadius, fallbackTextSize)
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

@Composable
private fun InlineArtworkFallback(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    textSize: TextUnit = 22.sp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(inlineFallbackColor(title, subtitle)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = inlineFallbackInitials(title, subtitle),
            color = Color.White,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun inlineFallbackColor(title: String, subtitle: String): Color {
    val seed = inlineFallbackSeed(title, subtitle)
    val colors = listOf(
        Color(0xFF246BFE),
        Color(0xFF087D70),
        Color(0xFFD2386C),
        Color(0xFF6D4AFF),
        Color(0xFF138A4F),
        Color(0xFF047C9C),
        Color(0xFF5F7500),
        Color(0xFF596273)
    )
    return colors[(seed.hashCode() and Int.MAX_VALUE) % colors.size]
}

private fun inlineFallbackInitials(title: String, subtitle: String): String {
    val seed = inlineFallbackSeed(title, subtitle)
    val clean = seed.filter { it.isLetterOrDigit() }.take(2)
    return if (clean.isEmpty()) {
        "E"
    } else {
        clean.uppercase(Locale.ROOT)
    }
}

private fun inlineFallbackSeed(title: String, subtitle: String): String {
    val cleanTitle = title.trim()
    if (cleanTitle.isNotEmpty() && cleanTitle != "未选择歌曲") {
        return cleanTitle
    }
    val cleanSubtitle = subtitle.trim()
    return if (cleanSubtitle.isEmpty()) "Yukine" else cleanSubtitle
}

@SuppressLint("ResourceType")
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
