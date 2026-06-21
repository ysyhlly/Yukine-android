package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
import java.util.ArrayList

internal class NetworkMenuLibrarySourceBindings(
    private val libraryStore: MainLibraryStore
) : NetworkMenuEventController.LibrarySource {
    override fun streamTracks(): ArrayList<Track> = libraryStore.streamTracks()

    override fun streamTrackCount(): Int = libraryStore.streamTrackCount()

    override fun webDavTracks(): ArrayList<Track> = libraryStore.webDavTracks()

    override fun remoteSources(): List<RemoteSource> = libraryStore.remoteSources()
}

internal class NetworkSourcesLibrarySourceBindings(
    private val libraryStore: MainLibraryStore
) : NetworkSourcesEventController.LibrarySource {
    override fun remoteSourceName(sourceId: Long): String = libraryStore.remoteSourceName(sourceId)

    override fun webDavTracksForSource(sourceId: Long): ArrayList<Track> =
        libraryStore.webDavTracksForSource(sourceId)
}

internal class NetworkSourcesPlayerBindings(
    private val playTrackListAction: TrackListPlaybackAction
) : NetworkSourcesEventController.Player {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.play(tracks, index)
    }
}
