package app.yukine

import app.yukine.model.Track
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackActions

internal fun interface MainQueueRenderListenerFactory {
    fun create(
        trackListPlayer: MainQueueRenderListener.TrackListPlayer,
        favoriteToggler: MainQueueRenderListener.FavoriteToggler,
        playlistAdder: MainQueueRenderListener.PlaylistAdder,
        queueTrackRemover: MainQueueRenderListener.QueueTrackRemover,
        clearQueueConfirmer: MainQueueRenderListener.ClearQueueConfirmer,
        backRequester: MainQueueRenderListener.BackRequester
    ): QueueRenderController.Listener
}

internal class MainQueueRenderListener(
    private val trackListPlayer: TrackListPlayer,
    private val favoriteToggler: FavoriteToggler,
    private val playlistAdder: PlaylistAdder,
    private val queueTrackRemover: QueueTrackRemover,
    private val clearQueueConfirmer: ClearQueueConfirmer,
    private val backRequester: BackRequester
) : QueueRenderController.Listener {
    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface FavoriteToggler {
        fun toggleFavorite(track: Track)
    }

    fun interface PlaylistAdder {
        fun showAddToPlaylist(track: Track)
    }

    fun interface QueueTrackRemover {
        fun removeQueueTrack(track: Track)
    }

    fun interface ClearQueueConfirmer {
        fun confirmClearQueue()
    }

    fun interface BackRequester {
        fun requestBack()
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        trackListPlayer.playTrackList(tracks, index)
    }

    override fun toggleFavorite(track: Track) {
        favoriteToggler.toggleFavorite(track)
    }

    override fun showAddToPlaylist(track: Track) {
        playlistAdder.showAddToPlaylist(track)
    }

    override fun removeQueueTrack(track: Track) {
        queueTrackRemover.removeQueueTrack(track)
    }

    override fun confirmClearQueue() {
        clearQueueConfirmer.confirmClearQueue()
    }

    override fun requestBack() {
        backRequester.requestBack()
    }

    override fun publishQueueChrome(
        actions: List<QueueTrackActions>,
        onClearQueue: Runnable,
        labels: QueueScreenLabels,
        onBack: Runnable
    ) = Unit
}
