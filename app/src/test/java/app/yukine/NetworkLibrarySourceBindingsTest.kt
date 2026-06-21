package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkLibrarySourceBindingsTest {
    @Test
    fun menuAndSourceBindingsReadNetworkLibraryStateFromStore() {
        val store = populatedStore()
        val menuSource = NetworkMenuLibrarySourceBindings(store)
        val sourceBindings = NetworkSourcesLibrarySourceBindings(store)

        assertEquals(listOf(1L), menuSource.streamTracks().map { it.id })
        assertEquals(1, menuSource.streamTrackCount())
        assertEquals(listOf(2L, 3L), menuSource.webDavTracks().map { it.id })
        assertEquals(listOf(7L, 8L), menuSource.remoteSources().map { it.id })
        assertEquals("NAS", sourceBindings.remoteSourceName(7L))
        assertEquals(listOf(2L), sourceBindings.webDavTracksForSource(7L).map { it.id })
    }

    @Test
    fun networkSourcesPlayerForwardsPlayback() {
        val calls = mutableListOf<String>()
        val player = NetworkSourcesPlayerBindings(
            TrackListPlaybackAction { tracks, index ->
                calls += "play:${tracks.size}:$index:${tracks.first().id}"
            }
        )

        player.playTrackList(listOf(track(9L, "file:/tmp/song.mp3")), 0)

        assertEquals(listOf("play:1:0:9"), calls)
    }

    private fun populatedStore(): MainLibraryStore {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val store = MainLibraryStore(
            LibrarySearchUseCase(
                object : LibrarySearchOperations {
                    override fun search(source: List<Track>, query: String?): List<Track> = source
                }
            ),
            viewModel
        )
        store.replaceLibrary(
            listOf(
                track(1L, "stream:https://example.test/stream.mp3"),
                track(2L, "webdav:7:music/a.mp3"),
                track(3L, "webdav:8:music/b.mp3")
            ),
            emptySet(),
            null
        )
        store.applyCollections(
            LibraryCollectionsResult(
                remoteSources = listOf(
                    RemoteSource(7L, RemoteSource.TYPE_WEBDAV, "NAS", "https://nas.test", "", "", "/", "", 0L),
                    RemoteSource(8L, RemoteSource.TYPE_WEBDAV, "Backup", "https://backup.test", "", "", "/", "", 0L)
                )
            )
        )
        return store
    }

    private fun track(id: Long, dataPath: String): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, dataPath)
}
