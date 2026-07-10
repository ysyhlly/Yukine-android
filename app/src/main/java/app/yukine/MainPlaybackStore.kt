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
     * Returns the queue already copied for its published queue revision when it is still valid.
     * The latest playback snapshot can advance while the Queue tab is hidden, so its revision
     * alone is not proof that the retained full queue was published for that revision.
     */
    fun publishedQueueFor(snapshot: PlaybackStateSnapshot?): List<Track>? {
        val requestedSnapshot = snapshot ?: return null
        val published = viewModel.playback.value
        if (
            published.publishedQueueRevision != requestedSnapshot.queueRevision ||
            published.queue.size != requestedSnapshot.queueSize
        ) {
            return null
        }
        return published.queue
    }
}
