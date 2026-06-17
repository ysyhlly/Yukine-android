package app.echo.next

import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.streaming.StreamingPlaybackAdapter
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import java.util.ArrayList

data class HeartbeatRefillRequest(
    val provider: StreamingProviderName
)

internal class StreamingHeartbeatRecommendationUseCase(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val refillRemainingThreshold: Int = 8,
    private val refillRetryMs: Long = 20_000L
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
        val placeholders = uniquePlaceholders(tracks)
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

    private fun uniquePlaceholders(tracks: List<StreamingTrack>?): ArrayList<Track> {
        val placeholders = ArrayList<Track>()
        tracks.orEmpty().forEach { track ->
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
}
