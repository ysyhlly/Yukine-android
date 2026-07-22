package app.yukine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.yukine.data.MusicLibraryRepository
import app.yukine.data.RecordingSourceVerification
import app.yukine.data.RecordingSourceVerificationGateway
import app.yukine.identity.TrackSourceMapping
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Performs explicit, user-triggered source verification without exposing source credentials. */
@Singleton
class DefaultRecordingSourceVerificationGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val streamingRepositorySource: StreamingRepositorySource,
    private val musicLibraryRepository: MusicLibraryRepository
) : RecordingSourceVerificationGateway {
    override suspend fun verify(source: TrackSourceMapping): RecordingSourceVerification =
        withContext(Dispatchers.IO) {
            try {
                when (source.provider.trim().lowercase()) {
                    "local", "document" -> verifyLocal(source)
                    "webdav" -> verifyWebDav(source)
                    else -> verifyStreaming(source)
                }
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RecordingSourceVerification(
                    success = false,
                    failureReason = error.safeFailureReason()
                )
            }
        }

    private fun verifyLocal(source: TrackSourceMapping): RecordingSourceVerification {
        val dataPath = source.dataPath.trim()
        require(dataPath.isNotBlank()) { "Missing local path" }
        val uri = if (dataPath.startsWith(DOCUMENT_PREFIX)) {
            Uri.parse(dataPath.removePrefix(DOCUMENT_PREFIX))
        } else {
            Uri.fromFile(File(dataPath))
        }
        if (uri.scheme == "content") {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Document unavailable" }.use { input ->
                check(input.read() >= 0) { "Empty document" }
            }
        } else {
            val file = File(requireNotNull(uri.path) { "Missing local path" })
            check(file.isFile && file.canRead()) { "Local file unavailable" }
            file.inputStream().use { input -> check(input.read() >= 0) { "Empty local file" } }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "content") retriever.setDataSource(context, uri)
            else retriever.setDataSource(requireNotNull(uri.path))
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.let(::toKbps)
                ?: 0
            RecordingSourceVerification(
                success = true,
                codec = mime.substringAfter('/', "").ifBlank { codecFromPath(dataPath) },
                bitrateKbps = bitrate
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun verifyWebDav(source: TrackSourceMapping): RecordingSourceVerification {
        val payload = source.dataPath.removePrefix(WEBDAV_PREFIX)
        val separator = payload.indexOf(':')
        require(separator > 0) { "Invalid WebDAV source" }
        val remoteSourceId = payload.substring(0, separator).toLongOrNull()
            ?: error("Invalid WebDAV source")
        val url = payload.substring(separator + 1).trim()
        require(url.startsWith("http://") || url.startsWith("https://")) { "Invalid WebDAV URL" }
        val remote = requireNotNull(musicLibraryRepository.loadRemoteSource(remoteSourceId)) {
            "WebDAV account unavailable"
        }
        val headers = webDavAuthorizationHeaders(url, remote.username, remote.password)
        val probe = RecordingHttpProbe.probe(url, headers)
        return RecordingSourceVerification(
            success = true,
            codec = probe.codec.ifBlank { codecFromPath(url) },
            bitrateKbps = source.bitrateKbps
        )
    }

    private suspend fun verifyStreaming(source: TrackSourceMapping): RecordingSourceVerification {
        val provider = requireNotNull(StreamingProviderName.fromWireName(source.provider)) {
            "Unsupported provider"
        }
        val quality = StreamingAudioQuality.fromWireName(source.quality) ?: StreamingAudioQuality.LOSSLESS
        val resolved = streamingRepositorySource.current().resolvePlayback(
            provider = provider,
            providerTrackId = source.providerTrackId,
            quality = quality,
            forceRefresh = true
        )
        val probe = RecordingHttpProbe.probe(resolved.url, resolved.headers)
        return RecordingSourceVerification(
            success = true,
            codec = resolved.codec.orEmpty()
                .ifBlank { resolved.mimeType.orEmpty().substringAfter('/', "") }
                .ifBlank { probe.codec },
            bitrateKbps = resolved.bitrate?.toLong()?.let(::toKbps) ?: source.bitrateKbps
        )
    }

    private fun Throwable.safeFailureReason(): String {
        val message = message.orEmpty().trim()
        return when {
            message.matches(Regex("HTTP \\d{3}")) -> message
            message in SAFE_FAILURE_MESSAGES -> message
            else -> javaClass.simpleName.ifBlank { "Verification failed" }
        }
    }

    private fun codecFromPath(value: String): String = value
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "")
        .lowercase()

    private fun toKbps(value: Long): Int = when {
        value <= 0L -> 0
        value >= 10_000L -> (value / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val DOCUMENT_PREFIX = "document:"
        const val WEBDAV_PREFIX = "webdav:"
        val SAFE_FAILURE_MESSAGES = setOf(
            "Missing local path",
            "Document unavailable",
            "Empty document",
            "Local file unavailable",
            "Empty local file",
            "Invalid WebDAV source",
            "Invalid WebDAV URL",
            "WebDAV account unavailable",
            "WebDAV authentication requires HTTPS",
            "Unsupported provider",
            "Empty response"
        )
    }
}

internal fun webDavAuthorizationHeaders(
    url: String,
    username: String,
    password: String
): Map<String, String> {
    if (username.isBlank()) return emptyMap()
    require(URL(url).protocol.equals("https", ignoreCase = true)) {
        "WebDAV authentication requires HTTPS"
    }
    val credential = Base64.getEncoder().encodeToString(
        "$username:$password".toByteArray(Charsets.UTF_8)
    )
    return mapOf("Authorization" to "Basic $credential")
}

internal data class RecordingProbeResult(val codec: String)

/** Bounded redirect handling that never forwards dynamic source headers across origins. */
internal object RecordingHttpProbe {
    fun probe(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int = NETWORK_TIMEOUT_MS
    ): RecordingProbeResult {
        var current = URL(url)
        require(current.protocol == "http" || current.protocol == "https") { "Unsupported URL protocol" }
        var currentHeaders = headers
        val deadlineNanos = System.nanoTime() + timeoutMs.coerceAtLeast(1) * 1_000_000L
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val remainingMs = ((deadlineNanos - System.nanoTime()) / 1_000_000L)
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
            check(System.nanoTime() < deadlineNanos) { "Verification timed out" }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = remainingMs
                readTimeout = remainingMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("Accept-Encoding", "identity")
                currentHeaders.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value)
                }
            }
            try {
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    check(redirectCount < MAX_REDIRECTS) { "Too many redirects" }
                    val location = connection.getHeaderField("Location").orEmpty()
                    check(location.isNotBlank()) { "Redirect without location" }
                    val next = URL(current, location)
                    require(next.protocol == "http" || next.protocol == "https") {
                        "Unsupported redirect protocol"
                    }
                    if (!current.sameOrigin(next)) currentHeaders = emptyMap()
                    current = next
                    return@repeat
                }
                check(code in 200..299) { "HTTP $code" }
                connection.inputStream.use { input -> check(input.read() >= 0) { "Empty response" } }
                return RecordingProbeResult(
                    connection.contentType.orEmpty().substringBefore(';').substringAfter('/', "")
                )
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }

    private fun URL.sameOrigin(other: URL): Boolean =
        protocol.equals(other.protocol, ignoreCase = true) &&
            host.equals(other.host, ignoreCase = true) &&
            effectivePort() == other.effectivePort()

    private fun URL.effectivePort(): Int = if (port >= 0) port else defaultPort

    private const val NETWORK_TIMEOUT_MS = 5_000
    private const val MAX_REDIRECTS = 3
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
