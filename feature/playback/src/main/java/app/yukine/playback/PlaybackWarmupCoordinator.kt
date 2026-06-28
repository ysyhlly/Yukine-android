package app.yukine.playback

import app.yukine.model.Track
import java.util.function.Consumer

internal class PlaybackWarmupCoordinator(
    private val precacheTrack: Consumer<Track>,
    private val scheduleVisualizationCache: Consumer<Track>
) {
    fun warmup(track: Track?) {
        if (track == null) {
            return
        }
        precacheTrack.accept(track)
        scheduleVisualizationCache.accept(track)
    }
}
