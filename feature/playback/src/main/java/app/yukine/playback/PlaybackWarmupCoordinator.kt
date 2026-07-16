package app.yukine.playback

import app.yukine.model.Track
import java.util.function.Consumer

internal class PlaybackWarmupCoordinator @JvmOverloads constructor(
    private val precacheTrack: Consumer<Track>,
    private val scheduleVisualizationCache: Consumer<Track>,
    private val scheduleCachedFingerprint: Consumer<Track> = Consumer {}
) {
    private var released = false

    fun warmup(track: Track?) {
        if (released || track == null) {
            return
        }
        precacheTrack.accept(track)
        scheduleVisualizationCache.accept(track)
        scheduleCachedFingerprint.accept(track)
    }

    fun release() {
        released = true
    }
}
