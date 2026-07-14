package app.yukine

import app.yukine.model.Track
import app.yukine.queue.QueueIntent
import app.yukine.queue.QueueViewModel

/** Owns the typed queue-screen intent boundary. */
internal class QueueIntentOwner(
    private val dispatchLibraryEvent: (LibraryEvent) -> Unit,
    private val showAddToPlaylist: (Track) -> Unit,
    private val removeTrack: (Track) -> Unit,
    private val moveTrack: (Int, Int) -> Unit,
    private val confirmClear: () -> Unit,
    private val navigateBack: () -> Unit
) : QueueViewModel.IntentListener {
    override fun onIntent(intent: QueueIntent) {
        when (intent) {
            is QueueIntent.PlayAt -> dispatchLibraryEvent(
                LibraryEvent.PlayTrackList(intent.tracks, intent.index)
            )
            is QueueIntent.ToggleFavorite -> dispatchLibraryEvent(
                LibraryEvent.ToggleFavorite(intent.track)
            )
            is QueueIntent.AddToPlaylist -> showAddToPlaylist(intent.track)
            is QueueIntent.Remove -> removeTrack(intent.track)
            is QueueIntent.Move -> moveTrack(intent.fromIndex, intent.toIndex)
            QueueIntent.ClearQueue -> confirmClear()
            QueueIntent.Back -> navigateBack()
        }
    }
}
