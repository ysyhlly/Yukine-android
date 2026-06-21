package app.yukine

import app.yukine.model.Track
import app.yukine.queue.QueueIntent

internal class QueueIntentController(
    private val listener: Listener
) {
    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun removeQueueTrack(track: Track)

        fun moveQueueTrack(fromIndex: Int, toIndex: Int)

        fun confirmClearQueue()

        fun back()
    }

    fun handle(intent: QueueIntent) {
        when (intent) {
            is QueueIntent.PlayAt -> listener.playTrackList(intent.tracks, intent.index)
            is QueueIntent.ToggleFavorite -> listener.toggleFavorite(intent.track)
            is QueueIntent.AddToPlaylist -> listener.showAddToPlaylist(intent.track)
            is QueueIntent.Remove -> listener.removeQueueTrack(intent.track)
            is QueueIntent.Move -> listener.moveQueueTrack(intent.fromIndex, intent.toIndex)
            QueueIntent.ClearQueue -> listener.confirmClearQueue()
            QueueIntent.Back -> listener.back()
        }
    }
}
