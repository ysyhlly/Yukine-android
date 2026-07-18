package app.yukine.playback.manager

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.common.ApplicationNetworkClient
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.model.RemoteSource
import app.yukine.playback.PlaybackCachedMediaReader
import app.yukine.streaming.StreamingPlaybackHeaderStore
import app.yukine.streaming.StreamingProviderName
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.function.LongFunction
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@UnstableApi
internal class PlaybackMediaSourceProvider(
    private val context: Context,
    private val remoteSourceLookup: LongFunction<RemoteSource?>,
    private val streamingPlaybackHeaderStore: StreamingPlaybackHeaderStore
) : PlaybackQueueManager.StreamingRestoreProvider, PlaybackCachedMediaReader {
    constructor(
        context: Context,
        repository: MusicLibraryRepository,
        streamingPlaybackHeaderStore: StreamingPlaybackHeaderStore
    ) : this(context, LongFunction(repository::loadRemoteSource), streamingPlaybackHeaderStore)

    private var audioCache: SimpleCache? = null
    private var audioCacheKey: String? = null

    fun mediaSourceFactory(track: Track): DefaultMediaSourceFactory {
        val httpFactory = httpDataSourceFactory(headersForTrack(track))
        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)
        if (!isHttpTrack(track)) {
            return DefaultMediaSourceFactory(upstreamFactory)
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(audioCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(cacheFactory)
    }

    fun cacheDataSourceForTrack(track: Track): CacheDataSource {
        val httpFactory = httpDataSourceFactory(headersForTrack(track))
        val upstream = DefaultDataSource(context, httpFactory.createDataSource())
        return CacheDataSource(
            audioCache(),
            upstream,
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        )
    }

    private fun httpDataSourceFactory(headers: Map<String, String>): HttpDataSource.Factory {
        // Factory/headers remain track-scoped, while every playback, precache and retry request
        // shares the same OkHttp DNS/TLS/HTTP2 connection pool.
        return OkHttpDataSource.Factory(PlaybackNetworkClient.httpClient).apply {
            if (headers.isNotEmpty()) {
                setDefaultRequestProperties(headers)
            }
        }
    }

    internal fun connectionPoolForTest(): ConnectionPool = PlaybackNetworkClient.httpClient.connectionPool

    internal fun rangeProbeConnectionPoolForTest(): ConnectionPool =
        PlaybackNetworkClient.rangeProbeClient.connectionPool

    fun contentLengthFromRange(track: Track, start: Long, endInclusive: Long): Long {
        if (!isHttpTrack(track) || start < 0L || endInclusive < start) return -1L
        val request = Request.Builder()
            .url(track.contentUri.toString())
            .header("Range", "bytes=$start-$endInclusive")
            .apply {
                headersForTrack(track).forEach { (name, value) ->
                    if (name.isNotBlank()) header(name, value)
                }
            }
            .build()
        return runCatching {
            PlaybackNetworkClient.rangeProbeClient.newCall(request).execute().use { response ->
                if (response.code != 206) return@use -1L
                contentLengthFromContentRange(response.header("Content-Range"))
            }
        }.getOrDefault(-1L)
    }

    fun mediaItemForTrack(track: Track, metadataProvider: ((Track) -> MediaMetadata)?): MediaItem {
        return playbackMediaItemForTrack(track, metadataProvider?.invoke(track))
    }

    fun mediaSourceForTrack(track: Track, metadataProvider: ((Track) -> MediaMetadata)?): MediaSource {
        restorePlaybackHeadersForMediaSource(track)
        return mediaSourceFactory(track).createMediaSource(mediaItemForTrack(track, metadataProvider))
    }

    fun mediaSourcesForTracks(
        tracks: List<Track>,
        metadataProvider: ((Track) -> MediaMetadata)?
    ): List<MediaSource> {
        return tracks.map { track -> mediaSourceForTrack(track, metadataProvider) }
    }

    fun mediaItemMatchesTrackForReuse(mediaItem: MediaItem?, track: Track?): Boolean {
        if (track == null) {
            return false
        }
        return mediaItemMatchesTrackForReuse(
            mediaItem,
            track.id,
            track.contentUri,
            cacheKeyForTrack(track)
        )
    }

    fun tracksShareResolvedUriForReuse(current: Track?, candidate: Track?): Boolean {
        val currentUri = current?.contentUri ?: return false
        val candidateUri = candidate?.contentUri ?: return false
        return currentUri == candidateUri
    }

    fun tracksShareMediaIdentityForReuse(current: Track?, candidate: Track?): Boolean {
        if (current == null || candidate == null || current.id != candidate.id) {
            return false
        }
        return tracksShareResolvedUriForReuse(current, candidate)
    }

    fun streamingQualityForTrack(track: Track?): String {
        return StreamingDataPathMetadata.quality(track?.dataPath)
    }

    data class PlaybackPreparation(
        val track: Track,
        val restoredTrack: Track?,
        val playable: Boolean,
        val unplayableMessage: String?
    )

    fun prepareTrackForPlayback(track: Track): PlaybackPreparation {
        val restoredTrack = restoredTrackForPreparation(track)
        val preparedTrack = restoredTrack ?: track
        val unplayableMessage = unplayableMessageForTrack(preparedTrack)
        return PlaybackPreparation(
            track = preparedTrack,
            restoredTrack = restoredTrack,
            playable = unplayableMessage == null,
            unplayableMessage = unplayableMessage
        )
    }

    fun restoredTrackForPreparation(track: Track?): Track? {
        return streamingPlaybackHeaderStore.restoredTrackFor(track)
    }

    override fun restoredTrackFor(track: Track): Track? {
        return restoredTrackForPreparation(track)
    }

    fun restoreHeadersForTrack(track: Track?): Boolean {
        return restoreHeadersForDataPath(track?.dataPath)
    }

    fun restoreHeadersForDataPath(dataPath: String?): Boolean {
        return streamingPlaybackHeaderStore.restoreForDataPath(dataPath)
    }

    override fun restoreForDataPath(dataPath: String?) {
        restoreHeadersForDataPath(dataPath)
    }

    private fun restorePlaybackHeadersForMediaSource(track: Track?) {
        if (StreamingDataPathMetadata.isStreamingTrack(track?.dataPath)) {
            restoreHeadersForTrack(track)
        }
    }

    fun audioCache(): SimpleCache = synchronized(AUDIO_CACHE_LOCK) {
        audioCache?.let { return@synchronized it }
        val cacheDir = File(context.cacheDir, "streaming-audio-cache")
        val cacheKey = cacheDir.absolutePath
        val shared = sharedAudioCaches[cacheKey] ?: SharedAudioCache(
            cache = createAudioCache(cacheDir),
            ownerCount = 0
        ).also { sharedAudioCaches[cacheKey] = it }
        shared.ownerCount += 1
        audioCache = shared.cache
        audioCacheKey = cacheKey
        shared.cache
    }

    fun releaseAudioCache() = synchronized(AUDIO_CACHE_LOCK) {
        val cache = audioCache ?: return@synchronized
        val cacheKey = audioCacheKey
        audioCache = null
        audioCacheKey = null
        val shared = cacheKey?.let(sharedAudioCaches::get)
        if (shared == null || shared.cache !== cache) {
            return@synchronized
        }
        shared.ownerCount -= 1
        if (shared.ownerCount > 0) {
            return@synchronized
        }
        sharedAudioCaches.remove(cacheKey)
        try {
            shared.cache.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to release audio cache", error)
        }
    }

    private fun createAudioCache(cacheDir: File): SimpleCache {
        return try {
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES))
        } catch (error: RuntimeException) {
            Log.w(TAG, "Audio cache corrupted; clearing and rebuilding", error)
            deleteRecursively(cacheDir)
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES))
        }
    }

    fun headersForTrack(track: Track): Map<String, String> {
        val headers = HashMap<String, String>()
        headers.putAll(streamingPlaybackHeaderStore.forDataPath(track.dataPath))
        if (!track.dataPath.startsWith("webdav:")) {
            return headers
        }
        val sourceId = webDavSourceId(track.dataPath)
        if (sourceId <= 0L) return headers
        val source = remoteSourceLookup.apply(sourceId) ?: return headers
        if (!source.hasAuth()) return headers
        val auth = "${source.username}:${source.password}"
        val encoded = Base64.encodeToString(
            auth.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        headers["Authorization"] = "Basic $encoded"
        return headers
    }

    fun cacheKeyForTrack(track: Track?): String? = mediaCacheKey(track)

    fun mediaCacheKeyForTrack(track: Track?): String {
        val cacheKey = cacheKeyForTrack(track)
        if (!cacheKey.isNullOrEmpty()) return cacheKey
        return if (track?.contentUri == null) "" else track.contentUri.toString()
    }

    fun continuousCachedBytes(cacheKey: String): Long {
        return try {
            val length = audioCache().getCachedLength(cacheKey, 0L, Long.MAX_VALUE)
            if (length > 0L) length else 0L
        } catch (_: RuntimeException) {
            0L
        }
    }

    fun contentLengthForCacheKey(cacheKey: String?): Long {
        if (cacheKey.isNullOrEmpty()) return -1L
        return try {
            ContentMetadata.getContentLength(audioCache().getContentMetadata(cacheKey))
        } catch (_: RuntimeException) {
            -1L
        }
    }

    /**
     * Copies a continuous cached prefix without constructing an upstream DataSource. Missing
     * bytes therefore return zero instead of contacting WebDAV. The caller owns [target].
     */
    override fun copyCachedPrefix(
        track: Track?,
        target: File,
        minimumBytes: Long,
        maximumBytes: Long
    ): Long {
        val cacheKey = cacheKeyForTrack(track) ?: return 0L
        if (minimumBytes <= 0L || maximumBytes < minimumBytes) return 0L
        val available = continuousCachedBytes(cacheKey)
        if (available < minimumBytes) return 0L
        val targetBytes = minOf(available, maximumBytes)
        val spans = runCatching { audioCache().getCachedSpans(cacheKey).toList() }
            .getOrDefault(emptyList())
            .sortedBy { it.position }
        target.parentFile?.mkdirs()
        var copied = 0L
        return try {
            FileOutputStream(target, false).use { output ->
                val buffer = ByteArray(CACHED_PREFIX_COPY_BUFFER_BYTES)
                spans.forEach { span ->
                    if (copied >= targetBytes || span.position > copied) return@forEach
                    val spanEnd = span.position + span.length
                    if (spanEnd <= copied) return@forEach
                    val spanFile = span.file ?: return@forEach
                    val offset = copied - span.position
                    var remaining = minOf(spanEnd - copied, targetBytes - copied)
                    RandomAccessFile(spanFile, "r").use { input ->
                        input.seek(offset)
                        while (remaining > 0L) {
                            val requested = minOf(buffer.size.toLong(), remaining).toInt()
                            val count = input.read(buffer, 0, requested)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            remaining -= count
                        }
                    }
                }
            }
            if (copied >= minimumBytes) copied else {
                target.delete()
                0L
            }
        } catch (_: Exception) {
            target.delete()
            0L
        }
    }

    fun isHttpTrack(track: Track?): Boolean {
        val uri = track?.contentUri ?: return false
        val scheme = uri.scheme
        return "http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)
    }

    private fun webDavSourceId(dataPath: String): Long {
        val parts = dataPath.split(":", limit = 3)
        if (parts.size < 3) return -1L
        return parts[1].toLongOrNull() ?: -1L
    }

    private fun deleteRecursively(file: File?) {
        if (file == null || !file.exists()) return
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                deleteRecursively(child)
            }
        }
        file.delete()
    }

    companion object {
        private const val TAG = "PlaybackMediaSource"
        private const val AUDIO_CACHE_MAX_BYTES = 1024L * 1024L * 1024L
        private const val CACHED_PREFIX_COPY_BUFFER_BYTES = 64 * 1024
        private val AUDIO_CACHE_LOCK = Any()
        private val sharedAudioCaches = HashMap<String, SharedAudioCache>()

        private data class SharedAudioCache(
            val cache: SimpleCache,
            var ownerCount: Int
        )

        @JvmStatic
        fun hasPlayableMediaUri(track: Track?): Boolean {
            val uri = track?.contentUri ?: return false
            return uri != Uri.EMPTY
        }

        @JvmStatic
        fun isRestorableQueueTrack(track: Track?): Boolean {
            if (track == null || !TrackIdentity.isUsable(track.id)) {
                return false
            }
            if (track.dataPath.isNullOrBlank()) {
                return false
            }
            val contentUri = track.contentUri
            if (contentUri == null || Uri.EMPTY == contentUri) {
                return isStreamingPlaceholder(track)
            }
            val scheme = contentUri.scheme
            if (scheme.equals("file", ignoreCase = true)) {
                val path = contentUri.path
                return path != null && File(path).exists()
            }
            if (scheme.isNullOrBlank()) {
                return contentUri.toString().isNotBlank()
            }
            return true
        }

        @JvmStatic
        fun isStreamingPlaceholder(track: Track?): Boolean {
            return StreamingDataPathMetadata.isStreamingTrack(track?.dataPath)
        }

        @JvmStatic
        fun unplayableMessageForTrack(track: Track?): String? {
            if (track == null || hasPlayableMediaUri(track)) {
                return null
            }
            return if (StreamingDataPathMetadata.isStreamingTrack(track.dataPath)) {
                "Streaming track is not resolved yet. Tap the track again to play."
            } else {
                "Unable to open this track."
            }
        }

        @JvmStatic
        fun mediaCacheKey(track: Track?): String? {
            if (track?.dataPath.isNullOrEmpty()) return null
            val uri = track!!.contentUri?.toString() ?: ""
            return mediaCacheKey(track.dataPath, uri)
        }

        @JvmStatic
        fun mediaCacheKey(dataPath: String?, uri: String?): String? {
            if (dataPath.isNullOrEmpty()) return null
            if (dataPath.startsWith("streaming:")) {
                val identity = StreamingDataPathMetadata.cacheIdentity(dataPath) ?: dataPath
                return if (uri.isNullOrEmpty()) identity else "$identity|url=$uri"
            }
            if (dataPath.startsWith("webdav:")) {
                return if (uri.isNullOrEmpty()) dataPath else "$dataPath|url=$uri"
            }
            return null
        }

        @JvmStatic
        fun playbackMediaItemForTrack(track: Track, metadata: MediaMetadata?): MediaItem {
            val builder = MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(track.id.toString())
                .setCustomCacheKey(mediaCacheKey(track))
                .setMediaMetadata(metadata ?: MediaMetadata.Builder().build())
            val playbackMimeType = StreamingDataPathMetadata.playbackMimeType(track.dataPath)
                .ifBlank {
                    if (StreamingDataPathMetadata.provider(track.dataPath) == StreamingProviderName.BILIBILI) {
                        "audio/mp4"
                    } else {
                        ""
                    }
                }
            if (playbackMimeType.isNotBlank()) {
                builder.setMimeType(playbackMimeType)
            }
            return builder.build()
        }

        @JvmStatic
        fun mediaItemMatchesTrackForReuse(
            mediaItem: MediaItem?,
            trackId: Long,
            contentUri: Uri?,
            cacheKey: String?
        ): Boolean {
            val localConfiguration = mediaItem?.localConfiguration ?: return false
            return mediaItemIdentityMatchesForReuse(
                mediaItem.mediaId,
                localConfiguration.uri?.toString(),
                localConfiguration.customCacheKey,
                trackId,
                contentUri?.toString(),
                cacheKey
            )
        }

        @JvmStatic
        fun mediaItemIdentityMatchesForReuse(
            mediaId: String?,
            mediaUri: String?,
            mediaCacheKey: String?,
            trackId: Long,
            trackUri: String?,
            trackCacheKey: String?
        ): Boolean {
            if (trackId.toString() != mediaId) {
                return false
            }
            return mediaUri == trackUri && cacheKeyMatchesForReuse(mediaCacheKey, trackCacheKey)
        }

        private fun cacheKeyMatchesForReuse(left: String?, right: String?): Boolean {
            if (left == null || right == null) {
                return true
            }
            return left == right
        }

        @JvmStatic
        fun contentLengthFromContentRange(contentRange: String?): Long {
            if (contentRange.isNullOrBlank()) return -1L
            val total = contentRange.substringAfterLast('/', "").trim()
            return total.toLongOrNull()?.takeIf { it > 0L } ?: -1L
        }
    }
}

private object PlaybackNetworkClient {
    val httpClient: OkHttpClient = ApplicationNetworkClient.httpClient

    val rangeProbeClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
}
