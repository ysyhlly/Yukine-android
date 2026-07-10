package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface MainPlaybackStoreFactory {
    fun create(viewModel: PlaybackViewModel): MainPlaybackStore
}

internal class MainPlaybackStore(
    private val viewModel: PlaybackViewModel
) {
    fun snapshot(): PlaybackStateSnapshot {
        return viewModel.playback.value.snapshot
    }

    fun replaceSnapshot(snapshot: PlaybackStateSnapshot?): PlaybackStateSnapshot {
        return viewModel.replacePlaybackSnapshot(snapshot)
    }

    fun reset() {
        viewModel.resetPlayback()
    }

    fun lastHistoryRefreshTrackId(): Long {
        return viewModel.lastHistoryRefreshTrackId()
    }

    fun setLastHistoryRefreshTrackId(trackId: Long) {
        viewModel.setLastHistoryRefreshTrackId(trackId)
    }

    fun publish(queue: List<Track>) {
        viewModel.updatePlayback(snapshot(), queue)
    }

    /**
     * Returns the queue already copied for the current playback snapshot when it is still valid.
     * This lets the Queue screen reuse the event-controller snapshot instead of asking the
     * service to copy a large queue a second time during the same UI update.
     */
    fun publishedQueueFor(snapshot: PlaybackStateSnapshot?): List<Track>? {
        val requestedSnapshot = snapshot ?: return null
        val published = viewModel.playback.value
        val publishedSnapshot = published.snapshot
        if (
            publishedSnapshot.queueRevision != requestedSnapshot.queueRevision ||
            publishedSnapshot.queueSize != requestedSnapshot.queueSize ||
            publishedSnapshot.currentIndex != requestedSnapshot.currentIndex ||
            publishedSnapshot.currentTrack?.id != requestedSnapshot.currentTrack?.id ||
            published.queue.size != requestedSnapshot.queueSize
        ) {
            return null
        }
        return published.queue
    }
}
