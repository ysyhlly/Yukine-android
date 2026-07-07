package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import java.util.ArrayList

data class HeartbeatRefillRequest(
    val provider: StreamingProviderName
)

internal class StreamingHeartbeatRecommendationUseCase(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val refillRemainingThreshold: Int = 18,
    private val refillRetryMs: Long = 8_000L,
    private val initialPlaybackLimit: Int = 30
) {
    private var active = false
    private var loading = false
    private var provider: StreamingProviderName? = null
    private var lastRefillAtMs = 0L
    private val seenKeys = HashSet<String>()

    fun startLoading(provider: StreamingProviderName) {
        this.provider = provider
        loading = true
    }

    fun startMode() {
        active = true
        loading = false
        seenKeys.clear()
    }

    fun stop() {
        active = false
        loading = false
        provider = null
        seenKeys.clear()
    }

    fun markLoadingFinished() {
        loading = false
    }

    fun markLoadingFinished(provider: StreamingProviderName) {
        if (this.provider == provider) {
            loading = false
        }
    }

    fun accepts(provider: StreamingProviderName): Boolean {
        return active && this.provider == provider
    }

    fun canContinueLoading(provider: StreamingProviderName): Boolean {
        return loading && this.provider == provider
    }

    fun prepareRefill(snapshot: PlaybackStateSnapshot?): HeartbeatRefillRequest? {
        if (!active || loading || provider == null || snapshot == null || !snapshot.playing) {
            return null
        }
        val remaining = snapshot.queueSize - snapshot.currentIndex - 1
        if (remaining > refillRemainingThreshold) {
            return null
        }
        val now = clockMs()
        if (now - lastRefillAtMs < refillRetryMs) {
            return null
        }
        lastRefillAtMs = now
        loading = true
        return HeartbeatRefillRequest(provider!!)
    }

    fun playlistPlaceholders(tracks: List<StreamingTrack>?): ArrayList<Track> {
        loading = false
        seenKeys.clear()
        val placeholders = uniquePlaceholders(tracks, initialPlaybackLimit)
        if (placeholders.isEmpty()) {
            stop()
            return placeholders
        }
        active = true
        return placeholders
    }

    fun appendPlaceholders(tracks: List<StreamingTrack>?): ArrayList<Track> {
        loading = false
        return uniquePlaceholders(tracks)
    }

    private fun uniquePlaceholders(tracks: List<StreamingTrack>?, limit: Int = Int.MAX_VALUE): ArrayList<Track> {
        val placeholders = ArrayList<Track>()
        tracks.orEmpty().forEach { track ->
            if (placeholders.size >= limit) {
                return@forEach
            }
            if (!isPlayableRecommendation(track)) {
                return@forEach
            }
            val key = streamingTrackKey(track)
            if (key.isEmpty() || !seenKeys.add(key)) {
                return@forEach
            }
            placeholders.add(StreamingPlaybackAdapter.placeholderTrack(track))
        }
        return placeholders
    }

    private fun streamingTrackKey(track: StreamingTrack?): String {
        if (track?.provider == null) {
            return ""
        }
        val providerTrackId = track.providerTrackId.trim()
        if (providerTrackId.isEmpty()) {
            return ""
        }
        return "${track.provider.wireName}:$providerTrackId"
    }

    private fun isPlayableRecommendation(track: StreamingTrack?): Boolean {
        if (track == null || !track.playable) {
            return false
        }
        if (track.providerTrackId.trim().isEmpty()) {
            return false
        }
        return track.title.isNotBlank() || track.artist.isNotBlank()
    }
}
