package app.yukine.playback.manager

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaybackHeaderStore
import java.io.File
import java.nio.charset.StandardCharsets

@UnstableApi
internal class PlaybackMediaSourceProvider(
    private val context: Context,
    private val repository: MusicLibraryRepository,
    private val streamingPlaybackHeaderStore: StreamingPlaybackHeaderStore
) {
    private var audioCache: SimpleCache? = null

    fun mediaSourceFactory(track: Track): DefaultMediaSourceFactory {
        val headers = headersForTrack(track)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }
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
        val headers = headersForTrack(track)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }
        val upstream = DefaultDataSource(context, httpFactory.createDataSource())
        return CacheDataSource(
            audioCache(),
            upstream,
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        )
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

    fun restoreHeadersForTrack(track: Track?): Boolean {
        return restoreHeadersForDataPath(track?.dataPath)
    }

    fun restoreHeadersForDataPath(dataPath: String?): Boolean {
        return streamingPlaybackHeaderStore.restoreForDataPath(dataPath)
    }

    private fun restorePlaybackHeadersForMediaSource(track: Track?) {
        if (StreamingDataPathMetadata.isStreamingTrack(track?.dataPath)) {
            restoreHeadersForTrack(track)
        }
    }

    fun audioCache(): SimpleCache {
        audioCache?.let { return it }
        val cacheDir = File(context.cacheDir, "streaming-audio-cache")
        val cache = try {
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES))
        } catch (error: RuntimeException) {
            Log.w(TAG, "Audio cache corrupted; clearing and rebuilding", error)
            deleteRecursively(cacheDir)
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES))
        }
        audioCache = cache
        return cache
    }

    fun releaseAudioCache() {
        val cache = audioCache ?: return
        try {
            cache.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to release audio cache", error)
        }
        audioCache = null
    }

    fun headersForTrack(track: Track): Map<String, String> {
        val headers = HashMap<String, String>()
        headers.putAll(streamingPlaybackHeaderStore.forDataPath(track.dataPath))
        if (!track.dataPath.startsWith("webdav:")) {
            return headers
        }
        val sourceId = webDavSourceId(track.dataPath)
        if (sourceId <= 0L) return headers
        val source = repository.loadRemoteSource(sourceId) ?: return headers
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

        @JvmStatic
        fun hasPlayableMediaUri(track: Track?): Boolean {
            val uri = track?.contentUri ?: return false
            return uri != Uri.EMPTY
        }

        @JvmStatic
        fun isRestorableQueueTrack(track: Track?): Boolean {
            if (track == null || track.id < 0L) {
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
                return if (uri.isNullOrEmpty()) dataPath else "$dataPath|url=$uri"
            }
            if (dataPath.startsWith("webdav:")) return dataPath
            return null
        }

        @JvmStatic
        fun playbackMediaItemForTrack(track: Track, metadata: MediaMetadata?): MediaItem {
            return MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(track.id.toString())
                .setCustomCacheKey(mediaCacheKey(track))
                .setMediaMetadata(metadata ?: MediaMetadata.Builder().build())
                .build()
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
            return left == right
        }
    }
}
