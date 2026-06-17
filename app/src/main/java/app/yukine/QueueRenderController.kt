package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackActions
import app.yukine.ui.QueueTrackUiState
import java.util.ArrayList

internal class QueueRenderController(
    private val viewModel: MainActivityViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun removeQueueTrack(track: Track)

        fun confirmClearQueue()

        fun requestBack()

        fun publishQueue(rows: ArrayList<QueueTrackUiState>)

        fun publishQueueChrome(
            actions: List<QueueTrackActions>,
            onClearQueue: Runnable,
            labels: QueueScreenLabels,
            onBack: Runnable
        )

        fun addStateContent(message: String)
    }

    fun render(queue: List<Track>?, playbackState: PlaybackStateSnapshot?, favoriteIds: Set<Long>, languageMode: String) {
        val queueTracks = queue ?: emptyList()
        val rows = ArrayList<QueueTrackUiState>()
        val actions = ArrayList<QueueTrackActions>()
        val currentTrack = playbackState?.currentTrack
        for (index in queueTracks.indices) {
            val track = queueTracks[index]
            rows.add(
                TrackRowStateFactory.queueRow(
                    TrackRowKeyPolicy.occurrenceKey(queueTracks, index),
                    track,
                    currentTrack,
                    favoriteIds
                )
            )
            actions.add(
                QueueTrackActions(
                    Runnable { listener.playTrackList(queueTracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.removeQueueTrack(track) }
                )
            )
        }

        listener.publishQueue(rows)
        listener.publishQueueChrome(
            actions,
            Runnable { listener.confirmClearQueue() },
            QueueScreenLabels(
                title = AppLanguage.text(languageMode, "tab.queue"),
                back = AppLanguage.text(languageMode, "back"),
                clearQueue = AppLanguage.text(languageMode, "clear.queue.title"),
                empty = AppLanguage.text(languageMode, "queue.empty"),
                emptyDescription = AppLanguage.text(languageMode, "queue.empty.description"),
                tracks = AppLanguage.text(languageMode, "tracks"),
                favorite = AppLanguage.text(languageMode, "favorite"),
                addToPlaylist = AppLanguage.text(languageMode, "add.to.playlist"),
                remove = AppLanguage.text(languageMode, "remove")
            ),
            Runnable { listener.requestBack() }
        )
    }
}
