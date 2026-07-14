package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkLibraryStoreDirectAccessTest {
    @Test
    fun mainActivityCanReadNetworkLibraryStateDirectlyFromStore() {
        val store = populatedStore()

        assertEquals(listOf(1L), store.streamTracks().map { it.id })
        assertEquals(1, store.streamTrackCount())
        assertEquals(listOf(2L, 3L), store.webDavTracks().map { it.id })
        assertEquals(listOf(7L, 8L), store.remoteSources().map { it.id })
        assertEquals("NAS", store.remoteSourceName(7L))
        assertEquals(listOf(2L), store.webDavTracksForSource(7L).map { it.id })
    }

    private fun populatedStore(): LibraryDataStateOwner {
        val store = LibraryDataStateOwner(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
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
