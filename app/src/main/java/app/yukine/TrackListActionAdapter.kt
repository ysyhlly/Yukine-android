package app.yukine

import app.yukine.model.Track

internal class TrackListActionAdapter(
    private val trackListPlayer: TrackListPlayer,
    private val favoriteToggler: FavoriteToggler,
    private val playlistAdder: PlaylistAdder,
    private val trackDownloader: TrackDownloader,
    private val tracksDownloader: TracksDownloader,
    private val streamEditor: StreamEditor,
    private val trackDeleteConfirmer: TrackDeleteConfirmer
) : TrackListStateReducer.Listener {
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
}
