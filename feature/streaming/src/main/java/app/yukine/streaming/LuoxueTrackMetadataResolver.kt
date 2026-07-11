package app.yukine.streaming

import app.yukine.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Independent LX metadata capability for an already queued streaming [Track]. It deliberately
 * does not participate in search or playback URL resolution, so lyric/artwork work can stay off
 * the ExoPlayer-critical path and still recover the original LX `musicInfo` after a queue restore.
 */
interface LuoxueTrackMetadataResolver {
    suspend fun resolveLyrics(track: Track?): LuoxueScriptLyrics?

    suspend fun resolveCoverUrl(track: Track?): String?
}

class LocalLuoxueTrackMetadataResolver(
    private val sourceStore: LuoxueSourceStore,
    private val client: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient()
) : LuoxueTrackMetadataResolver {
    override suspend fun resolveLyrics(track: Track?): LuoxueScriptLyrics? = withContext(Dispatchers.IO) {
        val request = requestFor(track) ?: return@withContext null
        try {
            client.resolveLyrics(
                providerTrackId = request.providerTrackId,
                luoxueMusicInfoJson = request.musicInfoJson,
                importedSources = sourceStore.load()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun resolveCoverUrl(track: Track?): String? = withContext(Dispatchers.IO) {
        val request = requestFor(track) ?: return@withContext null
        try {
            client.resolveCoverUrl(
                providerTrackId = request.providerTrackId,
                luoxueMusicInfoJson = request.musicInfoJson,
                importedSources = sourceStore.load()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private fun requestFor(track: Track?): MetadataRequest? {
        val dataPath = track?.dataPath.orEmpty()
        if (StreamingPlaybackAdapter.streamingProviderName(dataPath) != StreamingProviderName.LUOXUE) {
            return null
        }
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(dataPath).trim()
        if (providerTrackId.isBlank()) {
            return null
        }
        return MetadataRequest(
            providerTrackId = providerTrackId,
            musicInfoJson = StreamingPlaybackAdapter.luoxueMusicInfoJson(dataPath)
        )
    }

    private data class MetadataRequest(
        val providerTrackId: String,
        val musicInfoJson: String?
    )
}
