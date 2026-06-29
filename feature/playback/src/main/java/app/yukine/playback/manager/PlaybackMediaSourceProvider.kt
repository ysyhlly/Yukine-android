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
        if (!isHttpUri(track.contentUri)) {
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

    fun isHttpUri(uri: Uri?): Boolean {
        if (uri == null) return false
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
            if (left == null || right == null) {
                return true
            }
            return left == right
        }
    }
}
