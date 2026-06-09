package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.QueueScreenFactory
import app.echo.next.ui.QueueScreenLabels
import app.echo.next.ui.QueueTrackActions
import app.echo.next.ui.QueueTrackUiState
import java.util.ArrayList

internal class QueueRenderController(
    private val context: Context,
    private val viewModel: MainActivityViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun removeQueueTrack(track: Track)

        fun confirmClearQueue()

        fun publishQueue(rows: ArrayList<QueueTrackUiState>)

        fun addVirtualContent(view: View)

        fun addStateContent(message: String)
    }

    fun render(queue: List<Track>?, playbackState: PlaybackStateSnapshot?, favoriteIds: Set<Long>, languageMode: String) {
        if (queue.isNullOrEmpty()) {
            listener.addStateContent(AppLanguage.text(languageMode, "queue.empty"))
            return
        }

        val rows = ArrayList<QueueTrackUiState>()
        val actions = ArrayList<QueueTrackActions>()
        val currentTrack = playbackState?.currentTrack
        for (index in queue.indices) {
            val track = queue[index]
            rows.add(
                TrackRowStateFactory.queueRow(
                    TrackRowKeyPolicy.occurrenceKey(queue, index),
                    track,
                    currentTrack,
                    favoriteIds
                )
            )
            actions.add(
                QueueTrackActions(
                    Runnable { listener.playTrackList(queue, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.removeQueueTrack(track) }
                )
            )
        }

        listener.publishQueue(rows)
        listener.addVirtualContent(
            QueueScreenFactory.create(
                context,
                viewModel.queue,
                actions,
                Runnable { listener.confirmClearQueue() },
                QueueScreenLabels(
                    title = AppLanguage.text(languageMode, "tab.queue"),
                    clearQueue = AppLanguage.text(languageMode, "clear.queue.title"),
                    empty = AppLanguage.text(languageMode, "queue.empty"),
                    tracks = AppLanguage.text(languageMode, "tracks"),
                    favorite = AppLanguage.text(languageMode, "favorite"),
                    addToPlaylist = AppLanguage.text(languageMode, "add.to.playlist"),
                    remove = AppLanguage.text(languageMode, "remove")
                )
            )
        )
    }
}
