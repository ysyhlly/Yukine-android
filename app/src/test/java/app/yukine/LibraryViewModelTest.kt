package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackRowUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun trackEventsCallGateway() {
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel()
        val tracks = listOf(track(1L), track(2L))
        viewModel.bindGateway(gateway)

        viewModel.onEvent(LibraryEvent.PlayTrackList(tracks, 1))
        viewModel.onEvent(LibraryEvent.ToggleFavorite(tracks[0]))
        viewModel.onEvent(LibraryEvent.AddToPlaylist(tracks[1]))

        assertEquals(listOf("play:2:1", "favorite:1:true", "playlist:2"), gateway.calls)
    }

    /**
     * Regression: opening a library group (folder/album/artist) detail must leave a non-blank
     * track-list title and populated rows. The detail path runs TrackListRenderController.render
     * (updateTrackList) and then publishes chrome; if the chrome publish replaces the whole
     * track-list state with a blank title/rows, the NavHost renders an empty screen
     * (groups.title blank + trackList.title blank). This pins the wiring used by MainActivity.
     */
    @Test
    fun openingGroupDetailKeepsTrackListTitleAndRowsAfterChromePublish() {
        val viewModel = LibraryViewModel()
        // Mirror MainActivity wiring: chrome publish funnels through updateTrackListChrome(state)
        // built with an empty title + empty rows (only chrome fields are meaningful there).
        val listener = object : TrackListRenderController.Listener {
            override fun playTrackList(tracks: List<Track>, index: Int) = Unit
            override fun toggleFavorite(track: Track) = Unit
            override fun showAddToPlaylist(track: Track) = Unit
            override fun downloadTrack(track: Track) = Unit
            override fun downloadTracks(tracks: List<Track>) = Unit
            override fun showEditStream(track: Track) = Unit
            override fun confirmDeleteTrack(track: Track) = Unit
            override fun publishTrackListChrome(
                actions: List<app.yukine.ui.TrackRowActions>,
                headerMetrics: List<app.yukine.ui.TrackListHeaderMetric>,
                headerActions: List<app.yukine.ui.TrackListHeaderAction>,
                emptyText: String,
                modeActions: List<app.yukine.ui.TrackListModeAction>,
                labels: app.yukine.ui.TrackListLabels
            ) {
                viewModel.updateTrackListChrome(
                    MainActivityTrackListUiState(
                        "",
                        emptyList(),
                        emptyList(),
                        actions,
                        headerMetrics,
                        headerActions,
                        emptyText,
                        modeActions,
                        labels
                    )
                )
            }
        }
        val controller = TrackListRenderController(viewModel, listener)

        controller.render(
            "Rock",
            listOf(track(1L), track(2L)),
            true,
            listOf("", ""),
            false,
            emptyList(),
            emptyList(),
            "",
            emptyList(),
            app.yukine.ui.TrackListLabels(),
            null,
            emptySet()
        )

        assertEquals("Rock", viewModel.trackList.value.title)
        assertEquals(2, viewModel.trackList.value.rows.size)
        assertEquals("", viewModel.libraryGroups.value.title)
    }

    @Test
    fun toggleFavoriteAppliesNextStateAndPersistsIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val writes = ArrayList<String>()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindFavoriteWriter { track, favorite ->
            writes.add("${track.id}:$favorite")
            true
        }
        viewModel.bindFavoriteIdsProvider { setOf(1L) }

        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(1L)))
        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(2L)))
        advanceUntilIdle()

        assertEquals(listOf("favorite:1:false", "favorite:2:true"), gateway.calls)
        assertEquals(listOf("1:false", "2:true"), writes)
    }

    @Test
    fun toggleFavoriteUsesBoundFavoriteIdsProviderBeforeComputingNextState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val writes = ArrayList<String>()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindFavoriteWriter { track, favorite ->
            writes.add("${track.id}:$favorite")
            true
        }
        viewModel.bindFavoriteIdsProvider { setOf(2L) }

        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(2L)))
        advanceUntilIdle()

        assertEquals(listOf("favorite:2:false"), gateway.calls)
        assertEquals(listOf("2:false"), writes)
    }

    @Test
    fun playPlaylistLoadsTracksBeforeCallingGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel(dispatcher)
        val tracks = listOf(track(1L), track(2L))
        viewModel.bindGateway(gateway)
        viewModel.bindPlaylistTrackLoader { playlistId ->
            assertEquals(8L, playlistId)
            tracks
        }

        viewModel.onEvent(LibraryEvent.PlayPlaylist(8L))
        advanceUntilIdle()

        assertEquals(listOf("play:2:0"), gateway.calls)
    }

    @Test
    fun playPlaylistShowsEmptyStatusWhenNoTracksLoad() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindPlaylistTrackLoader { emptyList() }

        viewModel.onEvent(LibraryEvent.PlayPlaylist(8L))
        advanceUntilIdle()

        assertEquals(listOf("status:no.tracks.in.playlist"), gateway.calls)
    }

    @Test
    fun navigationAndSearchEventsCallGateway() {
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel()
        viewModel.bindGateway(gateway)

        viewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.ARTISTS))
        viewModel.onEvent(LibraryEvent.OpenGroup("artist:a", "Artist A"))
        viewModel.onEvent(LibraryEvent.OpenPlaylist(9L, "Daily Mix"))
        viewModel.onEvent(LibraryEvent.BackFromGroup)
        viewModel.onEvent(LibraryEvent.Search("kalafina"))

        assertEquals(
            listOf("mode:artists", "group:artist:a:Artist A", "playlist-open:9:Daily Mix", "back", "search:kalafina"),
            gateway.calls
        )
    }

    @Test
    fun importAndScanEventsCallGateway() {
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel()
        viewModel.bindGateway(gateway)

        viewModel.onEvent(LibraryEvent.ImportFiles)
        viewModel.onEvent(LibraryEvent.ScanLibrary)

        assertEquals(listOf("import", "scan"), gateway.calls)
    }

    @Test
    fun trackListAndGroupUiStateAreMutuallyExclusiveForNavHostRouting() {
        val viewModel = LibraryViewModel()
        val trackRows = listOf(
            TrackRowUiState(
                id = 1L,
                title = "Song",
                subtitle = "Artist",
                detail = "",
                duration = "0:01",
                albumArtUri = null,
                current = false,
                favorite = true,
                showPlaylistAction = true
            )
        )
        val groupRows = listOf(LibraryGroupUiState("artist:a", "Artist A", "1 song"))

        viewModel.updateTrackList("Songs", trackRows)
        viewModel.clearTrackList()
        viewModel.updateLibraryGroups("Artists", groupRows)

        assertEquals("", viewModel.trackList.value.title)
        assertEquals(emptyList<TrackRowUiState>(), viewModel.trackList.value.rows)
        assertEquals("Artists", viewModel.libraryGroups.value.title)
        assertEquals(groupRows, viewModel.libraryGroups.value.rows)

        viewModel.clearLibraryGroups()
        viewModel.updateTrackList("Songs", trackRows)

        assertEquals("", viewModel.libraryGroups.value.title)
        assertEquals(emptyList<LibraryGroupUiState>(), viewModel.libraryGroups.value.rows)
        assertEquals("Songs", viewModel.trackList.value.title)
        assertEquals(trackRows, viewModel.trackList.value.rows)
    }

    @Test
    fun albumAndArtistGroupsUseRepresentativeArtwork() {
        val artwork = Uri.parse("content://artwork/album")
        val tracks = listOf(
            track(1L),
            track(2L, artwork)
        )

        assertEquals(artwork, LibraryGrouping.groupArtworkUri(tracks, LibraryGrouping.ALBUMS))
        assertEquals(artwork, LibraryGrouping.groupArtworkUri(tracks, LibraryGrouping.ARTISTS))
        assertEquals(null, LibraryGrouping.groupArtworkUri(tracks, LibraryGrouping.FOLDERS))
    }

    @Test
    fun loadCollectionsDelegatesToBoundGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeCollectionGateway()
        val loaded = mutableListOf<LibraryCollectionsResult>()
        viewModel.bindCollectionGateway(gateway)

        viewModel.loadCollections(12L) { result -> loaded += result }
        advanceUntilIdle()

        assertEquals(listOf("load:12"), gateway.calls)
        assertEquals(12L, loaded.single().selectedPlaylistId)
        assertEquals(setOf(2L), loaded.single().favoriteIds)
    }

    @Test
    fun clearPlayHistoryDelegatesToBoundGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeCollectionGateway()
        val removed = mutableListOf<Int>()
        viewModel.bindCollectionGateway(gateway)

        viewModel.clearPlayHistory { count -> removed += count }
        advanceUntilIdle()

        assertEquals(listOf("clear"), gateway.calls)
        assertEquals(listOf(3), removed)
    }

    @Test
    fun saveLibraryFavoriteDelegatesToBoundGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeCollectionGateway()
        var saved = false
        viewModel.bindCollectionGateway(gateway)

        viewModel.saveLibraryFavorite(44L, true) { saved = true }
        viewModel.saveLibraryFavorite(-1L, false) { saved = false }
        advanceUntilIdle()

        assertEquals(listOf("favorite:44:true"), gateway.calls)
        assertEquals(true, saved)
    }
    @Test
    fun loadLibraryEmitsCachedThenFreshResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val loaded = mutableListOf<LibraryLoadResultUi>()
        viewModel.bindImportGateway(gateway)

        viewModel.loadLibrary(
            allowCachedFirst = true,
            canScan = true,
            onLoaded = { result -> loaded += result }
        )
        advanceUntilIdle()

        assertEquals(listOf("cached", "refresh"), gateway.calls)
        assertEquals(listOf("cached", "fresh"), loaded.map { it.status })
    }

    @Test
    fun loadLibraryReportsSecurityFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        gateway.failRefresh = true
        val failures = mutableListOf<String>()
        viewModel.bindImportGateway(gateway)

        viewModel.loadLibrary(
            allowCachedFirst = false,
            canScan = true,
            onFailed = { status -> failures += status }
        )
        advanceUntilIdle()

        assertEquals(listOf("refresh"), gateway.calls)
        assertEquals(listOf("Status"), failures)
    }

    @Test
    fun importAudioUrisDelegatesToGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val loaded = mutableListOf<String>()
        viewModel.bindImportGateway(gateway)

        viewModel.importAudioUris(emptyList()) { result -> loaded += result.status }
        advanceUntilIdle()

        assertEquals(listOf("uris:0"), gateway.calls)
        assertEquals(listOf("uris"), loaded)
    }

    @Test
    fun parseMissingAudioSpecsSkipsEmptyResultAndSerializesRuns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val parsed = mutableListOf<Int>()
        viewModel.bindImportGateway(gateway)

        gateway.audioSpecsResult = LibraryAudioSpecsResultUi(0)
        viewModel.parseMissingAudioSpecs { result -> parsed += result.updatedCount }
        advanceUntilIdle()
        gateway.audioSpecsResult = LibraryAudioSpecsResultUi(2, listOf(track(2L)), setOf(2L))
        viewModel.parseMissingAudioSpecs { result -> parsed += result.updatedCount }
        advanceUntilIdle()

        assertEquals(listOf("specs", "specs"), gateway.calls)
        assertEquals(listOf(2), parsed)
    }

    @Test
    fun m3uDocumentImportsAndPlaylistExportDelegateToGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeDocumentGateway()
        val streamStatuses = mutableListOf<String>()
        val playlistResults = mutableListOf<LibraryPlaylistImportResultUi>()
        val exportResults = mutableListOf<Boolean>()
        viewModel.bindDocumentGateway(gateway)

        viewModel.importStreamM3u(null) { result -> streamStatuses += result.status }
        viewModel.importPlaylistM3u(null) { result -> playlistResults += result }
        viewModel.exportPlaylist(null, 9L, "Daily Mix") { exported -> exportResults += exported }
        advanceUntilIdle()

        assertEquals(listOf("stream:null", "playlist:null", "export:null:9:Daily Mix"), gateway.calls)
        assertEquals(listOf("stream"), streamStatuses)
        assertEquals(42L, playlistResults.single().playlistId)
        assertEquals("playlist", playlistResults.single().status)
        assertEquals(listOf(true), exportResults)
    }

    @Test
    fun playlistActionsDelegateToGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakePlaylistActionGateway()
        val defaultAdds = mutableListOf<LibraryDefaultPlaylistAddResultUi>()
        val created = mutableListOf<Long>()
        val renamed = mutableListOf<Boolean>()
        val deleted = mutableListOf<Boolean>()
        val removed = mutableListOf<Track>()
        val moved = mutableListOf<Boolean>()
        val added = mutableListOf<Boolean>()
        val track = track(7L)
        viewModel.bindPlaylistActionGateway(gateway)

        viewModel.addToDefaultPlaylist(track) { result -> defaultAdds += result }
        viewModel.createPlaylist("Daily Mix") { playlistId -> created += playlistId }
        viewModel.renamePlaylist(42L, "Renamed") { result -> renamed += result }
        viewModel.deletePlaylist(42L, "Renamed") { result -> deleted += result }
        viewModel.removeSelectedPlaylistTrack(42L, track) { result -> removed += result }
        viewModel.moveSelectedPlaylistTrack(42L, track, 3, -1) { result -> moved += result }
        viewModel.addTrackToPlaylist(42L, 7L) { result -> added += result }
        advanceUntilIdle()

        assertEquals(
            listOf(
                "default:7",
                "create:Daily Mix",
                "rename:42:Renamed",
                "delete:42",
                "remove:42:7",
                "move:42:7:3:-1",
                "add:42:7"
            ),
            gateway.calls
        )
        assertEquals(42L, defaultAdds.single().playlistId)
        assertEquals(true, defaultAdds.single().added)
        assertEquals(listOf(42L), created)
        assertEquals(listOf(true), renamed)
        assertEquals(listOf(true), deleted)
        assertEquals(listOf(track), removed)
        assertEquals(listOf(true), moved)
        assertEquals(listOf(true), added)
    }

    @Test
    fun playlistActionPresentationsBuildLocalizedStatuses() {
        val viewModel = LibraryViewModel()
        val languageMode = AppLanguage.MODE_ENGLISH
        val track = track(7L)

        assertEquals(
            AppLanguage.text(languageMode, "added.to.playlist"),
            viewModel.defaultPlaylistAddPresentation(true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "could.not.add.to.playlist"),
            viewModel.defaultPlaylistAddPresentation(false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.created"),
            viewModel.playlistCreatedPresentation(languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.renamed"),
            viewModel.playlistRenamedPresentation(true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.rename.failed"),
            viewModel.playlistRenamedPresentation(false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "deleted.playlist.prefix") + "Mix",
            viewModel.playlistDeletedPresentation("Mix", true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "could.not.delete.playlist"),
            viewModel.playlistDeletedPresentation("Mix", false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "removed.from.playlist.prefix") + track.title,
            viewModel.selectedPlaylistTrackRemovedPresentation(track, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "moved.up.prefix") + track.title,
            viewModel.selectedPlaylistTrackMovedPresentation(track, -1, true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "moved.down.prefix") + track.title,
            viewModel.selectedPlaylistTrackMovedPresentation(track, 1, true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "move.failed"),
            viewModel.selectedPlaylistTrackMovedPresentation(track, 1, false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "added.to.playlist"),
            viewModel.trackAddedToPlaylistPresentation(true, languageMode).status
        )
    }

    private fun track(id: Long, albumArtUri: Uri? = null): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id", id, albumArtUri)
    }

    private class FakeCollectionGateway : LibraryCollectionGateway {
        val calls = ArrayList<String>()

        override fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult {
            calls.add("load:$selectedPlaylistId")
            return LibraryCollectionsResult(
                selectedPlaylistId = selectedPlaylistId,
                favoriteIds = setOf(2L),
                favoriteTracks = listOf(Track(2L, "Track 2", "Artist", "Album", 1000L, Uri.EMPTY, "file:2"))
            )
        }

        override fun clearPlayHistory(): Int {
            calls.add("clear")
            return 3
        }

        override fun setFavorite(trackId: Long, favorite: Boolean) {
            calls.add("favorite:$trackId:$favorite")
        }
    }
    private class FakeImportGateway : LibraryImportGateway {
        val calls = ArrayList<String>()
        var failRefresh = false
        var audioSpecsResult = LibraryAudioSpecsResultUi(1, listOf(Track(1L, "Track 1", "Artist", "Album", 1000L, Uri.EMPTY, "file:1")), setOf(1L))

        override fun loadCached(): LibraryLoadResultUi {
            calls.add("cached")
            return LibraryLoadResultUi(status = "cached")
        }

        override fun refresh(): LibraryLoadResultUi {
            calls.add("refresh")
            if (failRefresh) {
                throw SecurityException("denied")
            }
            return LibraryLoadResultUi(status = "fresh")
        }

        override fun importAudioUris(uris: List<Uri>): LibraryLoadResultUi {
            calls.add("uris:${uris.size}")
            return LibraryLoadResultUi(status = "uris")
        }

        override fun importAudioTree(treeUri: Uri): LibraryLoadResultUi {
            calls.add("tree:$treeUri")
            return LibraryLoadResultUi(status = "tree")
        }

        override fun parseMissingAudioSpecs(): LibraryAudioSpecsResultUi {
            calls.add("specs")
            return audioSpecsResult
        }
    }

    private class FakeDocumentGateway : LibraryDocumentGateway {
        val calls = ArrayList<String>()

        override fun importStreamM3u(playlistUri: Uri?): LibraryLoadResultUi {
            calls.add("stream:$playlistUri")
            return LibraryLoadResultUi(status = "stream")
        }

        override fun importPlaylistM3u(playlistUri: Uri?): LibraryPlaylistImportResultUi {
            calls.add("playlist:$playlistUri")
            return LibraryPlaylistImportResultUi(playlistId = 42L, status = "playlist")
        }

        override fun exportPlaylist(exportUri: Uri?, playlistId: Long, playlistName: String): Boolean {
            calls.add("export:$exportUri:$playlistId:$playlistName")
            return true
        }
    }

    private class FakePlaylistActionGateway : LibraryPlaylistActionGateway {
        val calls = ArrayList<String>()

        override fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi? {
            calls.add("default:${track?.id}")
            return LibraryDefaultPlaylistAddResultUi(42L, true)
        }

        override fun createPlaylist(name: String): Long {
            calls.add("create:$name")
            return 42L
        }

        override fun renamePlaylist(playlistId: Long, name: String): Boolean {
            calls.add("rename:$playlistId:$name")
            return true
        }

        override fun deletePlaylist(playlistId: Long): Boolean {
            calls.add("delete:$playlistId")
            return true
        }

        override fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean {
            calls.add("remove:$playlistId:${track?.id}")
            return true
        }

        override fun movePlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int): Boolean {
            calls.add("move:$playlistId:${track?.id}:$trackIndex:$direction")
            return true
        }

        override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
            calls.add("add:$playlistId:$trackId")
            return true
        }
    }

    private class FakeGateway : LibraryGateway {
        val calls = ArrayList<String>()

        override fun playTrackList(tracks: List<Track>, index: Int) {
            calls.add("play:${tracks.size}:$index")
        }

        override fun showStatusKey(key: String) {
            calls.add("status:$key")
        }

        override fun applyFavorite(trackId: Long, favorite: Boolean) {
            calls.add("favorite:$trackId:$favorite")
        }

        override fun addToPlaylist(track: Track) {
            calls.add("playlist:${track.id}")
        }

        override fun changeGroupMode(mode: String) {
            calls.add("mode:$mode")
        }

        override fun openGroup(key: String, title: String) {
            calls.add("group:$key:$title")
        }

        override fun openPlaylist(playlistId: Long, title: String) {
            calls.add("playlist-open:$playlistId:$title")
        }

        override fun backFromGroup() {
            calls.add("back")
        }

        override fun search(query: String) {
            calls.add("search:$query")
        }

        override fun importFiles() {
            calls.add("import")
        }

        override fun scanLibrary() {
            calls.add("scan")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryMainDispatcherRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val dispatcher = UnconfinedTestDispatcher()
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }
}
