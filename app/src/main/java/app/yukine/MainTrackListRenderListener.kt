package app.yukine

import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions

internal fun interface MainTrackListRenderListenerFactory {
    fun create(
        trackListPlayer: MainTrackListRenderListener.TrackListPlayer,
        favoriteToggler: MainTrackListRenderListener.FavoriteToggler,
        playlistAdder: MainTrackListRenderListener.PlaylistAdder,
        trackDownloader: MainTrackListRenderListener.TrackDownloader,
        tracksDownloader: MainTrackListRenderListener.TracksDownloader,
        streamEditor: MainTrackListRenderListener.StreamEditor,
        trackDeleteConfirmer: MainTrackListRenderListener.TrackDeleteConfirmer,
        chromePublisher: MainTrackListRenderListener.ChromePublisher
    ): TrackListRenderController.Listener
}

internal class MainTrackListRenderListener(
    private val trackListPlayer: TrackListPlayer,
    private val favoriteToggler: FavoriteToggler,
    private val playlistAdder: PlaylistAdder,
    private val trackDownloader: TrackDownloader,
    private val tracksDownloader: TracksDownloader,
    private val streamEditor: StreamEditor,
    private val trackDeleteConfirmer: TrackDeleteConfirmer,
    private val chromePublisher: ChromePublisher
) : TrackListRenderController.Listener {
    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface FavoriteToggler {
        fun toggleFavorite(track: Track)
    }

    fun interface PlaylistAdder {
        fun showAddToPlaylist(track: Track)
    }

    fun interface TrackDownloader {
        fun downloadTrack(track: Track)
    }

    fun interface TracksDownloader {
        fun downloadTracks(tracks: List<Track>)
    }

    fun interface StreamEditor {
        fun showEditStream(track: Track)
    }

    fun interface TrackDeleteConfirmer {
        fun confirmDeleteTrack(track: Track)
    }

    fun interface ChromePublisher {
        fun publishTrackListChromeState(state: TrackListChromeState)
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

    override fun downloadTrack(track: Track) {
        trackDownloader.downloadTrack(track)
    }

    override fun downloadTracks(tracks: List<Track>) {
        tracksDownloader.downloadTracks(tracks)
    }

    override fun showEditStream(track: Track) {
        streamEditor.showEditStream(track)
    }

    override fun confirmDeleteTrack(track: Track) {
        trackDeleteConfirmer.confirmDeleteTrack(track)
    }

    override fun publishTrackListChrome(
        actions: List<TrackRowActions>,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels
    ) {
        chromePublisher.publishTrackListChromeState(
            TrackListChromeState(
                actions = ArrayList(actions),
                headerMetrics = ArrayList(headerMetrics),
                headerActions = ArrayList(headerActions),
                emptyText = emptyText,
                modeActions = ArrayList(modeActions),
                labels = labels
            )
        )
    }
}
