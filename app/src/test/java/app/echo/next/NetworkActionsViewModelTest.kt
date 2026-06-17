package app.echo.next

import app.echo.next.model.StreamImportResult
import app.echo.next.model.Track
import app.echo.next.model.WebDavSyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkActionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun streamActionsDelegateThroughUseCasesAndReportResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val operations = FakeNetworkLibraryOperations()
        val webDavOperations = FakeWebDavSourceOperations()
        val listener = FakeListener()
        val viewModel = NetworkActionsViewModel(dispatcher, dispatcher)
        operations.addedTrack = track(1L)
        operations.updatedTrack = track(2L)
        operations.importResult = StreamImportResult(listOf(track(3L)), 1, 1, 0)
        operations.cached = listOf(track(1L), track(2L))
        operations.favorites = setOf(2L)
        viewModel.bindUseCases(useCases(webDavOperations, operations))
        viewModel.bindListener(listener)

        viewModel.addStreamUrl("Radio", "https://example.test/radio.mp3")
        viewModel.updateStreamUrl(track(1L), "Radio 2", "https://example.test/radio2.mp3")
        viewModel.importM3uPlaylist("https://example.test/list.m3u")
        advanceUntilIdle()

        assertEquals(
            listOf(
                "add:Radio:https://example.test/radio.mp3",
                "cached",
                "favorites",
                "update:1:Radio 2:https://example.test/radio2.mp3",
                "cached",
                "favorites",
                "import:https://example.test/list.m3u",
                "cached",
                "favorites"
            ),
            operations.events
        )
        assertEquals(
            listOf(
                "added:2:1:Library updated",
                "updated:1:2:2:1:Library updated",
                "imported:2:1:Imported streams: added 1, skipped 0"
            ),
            listener.events
        )
    }

    @Test
    fun deleteAndWebDavActionsDelegateThroughUseCasesAndReportResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val operations = FakeNetworkLibraryOperations()
        val webDavOperations = FakeWebDavSourceOperations()
        val listener = FakeListener()
        val viewModel = NetworkActionsViewModel(dispatcher, dispatcher)
        operations.cached = listOf(track(4L))
        operations.favorites = setOf(4L)
        operations.savedSourceId = 8L
        webDavOperations.testStatus = "OK"
        webDavOperations.syncResult = WebDavSyncResult(listOf(track(5L)), 1, 0, 0)
        viewModel.bindUseCases(useCases(webDavOperations, operations))
        viewModel.bindListener(listener)

        viewModel.deleteAllStreams()
        viewModel.deleteTrack(4L, "Deleted")
        viewModel.deleteTracks(listOf(4L, 5L), "Removed tracks: 2")
        viewModel.deleteRemoteSource(8L)
        viewModel.saveWebDavSource(-1L, "NAS", "https://example.test", "u", "p", "music")
        viewModel.testRemoteSource(8L)
        viewModel.syncRemoteSource(8L, "NAS")
        viewModel.syncAllWebDavSources(listOf(8L, 9L))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "deleteAll",
                "cached",
                "favorites",
                "deleteTrack:4",
                "cached",
                "favorites",
                "deleteTracks:4,5",
                "cached",
                "favorites",
                "deleteSource:8",
                "cached",
                "favorites",
                "saveSource:-1:NAS:https://example.test:u:p:music",
                "cached",
                "favorites"
            ),
            operations.events
        )
        assertEquals(
            listOf(
                "test:8",
                "sync:8",
                "sync:8",
                "sync:9"
            ),
            webDavOperations.events
        )
        assertEquals(
            listOf(
                "deleteAll:1:1:Library updated",
                "deleteTrack:1:1:Deleted",
                "deleteTrack:1:1:Removed tracks: 2",
                "deleteSource:1:1:Library updated",
                "saveSource:-1:1:1:Added WebDAV source",
                "tested:OK",
                "synced:1:1:added 1, removed 0, kept 0",
                "syncedAll:1:1:WebDAV sync: added 2, removed 0, kept 0, tracks 2, ok 2"
            ),
            listener.events
        )
    }

    private fun useCases(
        webDavOperations: WebDavSourceOperations,
        networkOperations: NetworkLibraryOperations
    ): NetworkActionUseCases =
        NetworkActionUseCases(
            TestWebDavSourceUseCase(webDavOperations),
            SyncWebDavSourceUseCase(webDavOperations),
            SyncAllWebDavSourcesUseCase(webDavOperations),
            AddStreamUrlUseCase(networkOperations),
            UpdateStreamUrlUseCase(networkOperations),
            ImportStreamPlaylistUseCase(networkOperations),
            DeleteAllStreamsUseCase(networkOperations),
            DeleteNetworkTrackUseCase(networkOperations),
            DeleteNetworkTracksUseCase(networkOperations),
            DeleteRemoteSourceUseCase(networkOperations),
            SaveWebDavSourceUseCase(networkOperations)
        )

    private class FakeListener : NetworkActionsViewModel.Listener {
        val events = mutableListOf<String>()

        override fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("added:${cached.size}:${favorites.size}:$status")
        }

        override fun onStreamUpdated(oldTrackId: Long, updated: Track?, cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("updated:$oldTrackId:${updated?.id}:${cached.size}:${favorites.size}:$status")
        }

        override fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("imported:${cached.size}:${favorites.size}:$status")
        }

        override fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("deleteAll:${cached.size}:${favorites.size}:$status")
        }

        override fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("deleteTrack:${cached.size}:${favorites.size}:$status")
        }

        override fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("deleteSource:${cached.size}:${favorites.size}:$status")
        }

        override fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("saveSource:$sourceId:${cached.size}:${favorites.size}:$status")
        }

        override fun onRemoteSourceTested(status: String) {
            events.add("tested:$status")
        }

        override fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("synced:${cached.size}:${favorites.size}:$status")
        }

        override fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
            events.add("syncedAll:${cached.size}:${favorites.size}:$status")
        }
    }

    private class FakeNetworkLibraryOperations : NetworkLibraryOperations {
        val events = mutableListOf<String>()
        var cached: List<Track> = emptyList()
        var favorites: Set<Long> = emptySet()
        var addedTrack: Track? = null
        var updatedTrack: Track? = null
        var importResult: StreamImportResult? = null
        var savedSourceId: Long = -1L

        override fun addStreamUrl(title: String, url: String): Track? {
            events.add("add:$title:$url")
            return addedTrack
        }

        override fun updateStreamUrl(trackId: Long, title: String, url: String): Track? {
            events.add("update:$trackId:$title:$url")
            return updatedTrack
        }

        override fun importM3uPlaylistWithResult(url: String): StreamImportResult? {
            events.add("import:$url")
            return importResult
        }

        override fun deleteAllStreams() {
            events.add("deleteAll")
        }

        override fun deleteTrack(trackId: Long) {
            events.add("deleteTrack:$trackId")
        }

        override fun deleteTracks(trackIds: List<Long>) {
            events.add("deleteTracks:${trackIds.joinToString(",")}")
        }

        override fun deleteRemoteSource(sourceId: Long) {
            events.add("deleteSource:$sourceId")
        }

        override fun saveWebDavSource(
            sourceId: Long,
            name: String,
            baseUrl: String,
            username: String,
            password: String,
            rootPath: String
        ): Long {
            events.add("saveSource:$sourceId:$name:$baseUrl:$username:$password:$rootPath")
            return savedSourceId
        }

        override fun loadCachedTracks(): List<Track> {
            events.add("cached")
            return cached
        }

        override fun loadFavoriteIds(): Set<Long> {
            events.add("favorites")
            return favorites
        }
    }

    private class FakeWebDavSourceOperations : WebDavSourceOperations {
        val events = mutableListOf<String>()
        var testStatus = ""
        var cached: List<Track> = listOf(Track(5L, "Song 5", "Artist", "Album", 120_000L, null, "file:5.mp3"))
        var favorites: Set<Long> = setOf(5L)
        var syncResult: WebDavSyncResult? = null

        override fun testRemoteSource(sourceId: Long): String {
            events.add("test:$sourceId")
            return testStatus
        }

        override fun syncRemoteSource(sourceId: Long): WebDavSyncResult? {
            events.add("sync:$sourceId")
            return syncResult
        }

        override fun loadCachedTracks(): List<Track> {
            return cached
        }

        override fun loadFavoriteIds(): Set<Long> {
            return favorites
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
