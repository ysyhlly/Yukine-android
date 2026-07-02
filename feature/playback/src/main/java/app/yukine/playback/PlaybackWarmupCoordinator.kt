package app.yukine.playback

import app.yukine.model.Track
import java.util.function.Consumer

internal class PlaybackWarmupCoordinator(
    private val precacheTrack: Consumer<Track>,
    private val scheduleVisualizationCache: Consumer<Track>
) {
    private var released = false

    fun warmup(track: Track?) {
        if (released || track == null) {
            return
        }
        precacheTrack.accept(track)
        scheduleVisualizationCache.accept(track)
    }

    fun release() {
        released = true
    }
}
