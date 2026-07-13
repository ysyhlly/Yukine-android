package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibrarySort
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
    fun libraryInteractionOwnsRevealSelectionAndBatchDelete() {
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel()
        val tracks = listOf(track(1L), track(2L))
        viewModel.bindGateway(gateway)
        viewModel.presentation.updateVisibleTrackTargets(tracks, listOf("one", "two"))

        viewModel.presentation.onAction(LibraryAction.RevealTrack("one"))
        assertEquals("one", viewModel.libraryUi.value.revealedRowKey)

        viewModel.presentation.onAction(LibraryAction.ToggleTrackSelection("one"))
        assertTrue(viewModel.libraryUi.value.selectionActive)
        assertEquals(null, viewModel.libraryUi.value.revealedRowKey)

        viewModel.presentation.onAction(LibraryAction.SelectAllVisible)
        assertEquals(setOf("one", "two"), viewModel.libraryUi.value.selectedTrackKeys)
        viewModel.presentation.onAction(LibraryAction.DeleteSelected)

        assertEquals("delete:1,2", gateway.calls.last())
        viewModel.presentation.onAction(LibraryAction.ClearSelection)
        assertFalse(viewModel.libraryUi.value.selectionActive)
    }

    @Test
    fun sortAndFilterUpdateTheObservedUiStateDirectly() {
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel()
        viewModel.bindGateway(gateway)

        viewModel.presentation.onAction(LibraryAction.SortChanged(LibrarySort.DurationDescending))
        viewModel.presentation.onAction(LibraryAction.FilterChanged(LibraryFilter.Local))

        assertEquals(LibrarySort.DurationDescending, viewModel.libraryUi.value.sort)
        assertEquals(LibraryFilter.Local, viewModel.libraryUi.value.filter)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun batchFavoriteAndPlaylistActionsIsolateIndividualFailures() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val libraryGateway = FakeGateway()
        val playlistGateway = FakePlaylistActionGateway().apply {
            failedDefaultIds += 2L
        }
        val favoriteWrites = mutableListOf<Long>()
        val viewModel = LibraryViewModel(dispatcher)
        val tracks = listOf(track(1L), track(2L), track(3L))
        viewModel.bindGateway(libraryGateway)
        viewModel.bindPlaylistActionGateway(playlistGateway)
        viewModel.bindFavoriteWriter { track, _ ->
            favoriteWrites += track.id
            track.id != 2L
        }
        viewModel.presentation.updateVisibleTrackTargets(tracks, listOf("1", "2", "3"))
        viewModel.presentation.onAction(LibraryAction.SelectAllVisible)

        viewModel.presentation.onAction(LibraryAction.FavoriteSelected)
        viewModel.presentation.onAction(LibraryAction.AddSelectedToPlaylist)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), favoriteWrites)
        assertTrue(libraryGateway.calls.contains("status:library.favorite.failed"))
        assertTrue(libraryGateway.calls.contains("status:could.not.add.to.playlist"))
        assertEquals(listOf("default:1", "default:2", "default:3"), playlistGateway.calls)
    }

    @Test
    fun hiddenLibraryRestoreRunsThroughMutationGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val calls = mutableListOf<String>()
        val results = mutableListOf<Boolean>()
        viewModel.bindExclusionGateway(object : LibraryExclusionGateway {
            override fun restoreLibraryExclusion(sourceKey: String): Boolean {
                calls += "one:$sourceKey"
                return true
            }

            override fun restoreAllLibraryExclusions(): Int {
                calls += "all"
                return 2
            }
        })

        viewModel.loading.restoreHiddenItem("document:1") { results += it }
        advanceUntilIdle()
        viewModel.loading.restoreAllHiddenItems { results += it }
        advanceUntilIdle()

        assertEquals(listOf("one:document:1", "all"), calls)
        assertEquals(listOf(true, true), results)
    }

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

    /** Opening a group detail publishes its content and chrome in one destination state. */
    @Test
    fun openingGroupDetailPublishesCompleteTrackListState() {
        val viewModel = LibraryViewModel()
        val listener = object : TrackListRenderController.Listener {
            override fun playTrackList(tracks: List<Track>, index: Int) = Unit
            override fun toggleFavorite(track: Track) = Unit
            override fun showAddToPlaylist(track: Track) = Unit
            override fun downloadTrack(track: Track) = Unit
            override fun downloadTracks(tracks: List<Track>) = Unit
            override fun showEditStream(track: Track) = Unit
            override fun confirmDeleteTrack(track: Track) = Unit
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
        assertEquals(2, viewModel.trackList.value.actions.size)
        assertEquals("收藏", viewModel.trackList.value.labels.favoriteLabel)
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
    fun toggleFavoriteDoesNotUpdateUiWhenPersistenceFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindFavoriteWriter { _, _ -> false }

        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(2L)))
        advanceUntilIdle()

        assertEquals(listOf("status:library.favorite.failed"), gateway.calls)
    }

    @Test
    fun rapidFavoriteTogglesReadTheLatestPersistedStateSerially() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val favorites = mutableSetOf<Long>()
        val writes = mutableListOf<Boolean>()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindFavoriteIdsProvider { favorites.toSet() }
        viewModel.bindFavoriteWriter { track, favorite ->
            writes += favorite
            if (favorite) favorites += track.id else favorites -= track.id
            true
        }

        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(2L)))
        viewModel.onEvent(LibraryEvent.ToggleFavorite(track(2L)))
        advanceUntilIdle()

        assertEquals(listOf(true, false), writes)
        assertEquals(listOf("favorite:2:true", "favorite:2:false"), gateway.calls)
        assertTrue(favorites.isEmpty())
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
    fun playPlaylistReportsLoaderFailureWithoutEscapingScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindGateway(gateway)
        viewModel.bindPlaylistTrackLoader { throw IllegalStateException("database unavailable") }

        viewModel.onEvent(LibraryEvent.PlayPlaylist(8L))
        advanceUntilIdle()

        assertEquals(listOf("status:library.playlist.load.failed"), gateway.calls)
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

        viewModel.presentation.updateTrackList("Songs", trackRows)
        viewModel.presentation.clearTrackList()
        viewModel.presentation.updateLibraryGroups("Artists", groupRows)

        assertEquals("", viewModel.trackList.value.title)
        assertEquals(emptyList<TrackRowUiState>(), viewModel.trackList.value.rows)
        assertEquals("Artists", viewModel.libraryGroups.value.title)
        assertEquals(groupRows, viewModel.libraryGroups.value.rows)

        viewModel.presentation.clearLibraryGroups()
        viewModel.presentation.updateTrackList("Songs", trackRows)

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

        viewModel.playlists.loadCollections(12L) { result -> loaded += result }
        advanceUntilIdle()

        assertEquals(listOf("load:12"), gateway.calls)
        assertEquals(12L, loaded.single().selectedPlaylistId)
        assertEquals(setOf(2L), loaded.single().favoriteIds)
    }

    @Test
    fun newerCollectionLoadCancelsTheStaleRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeCollectionGateway()
        val loaded = mutableListOf<Long>()
        viewModel.bindCollectionGateway(gateway)

        viewModel.playlists.loadCollections(11L) { loaded += it.selectedPlaylistId }
        viewModel.playlists.loadCollections(12L) { loaded += it.selectedPlaylistId }
        advanceUntilIdle()

        assertEquals(listOf(12L), loaded)
        assertEquals(listOf("load:12"), gateway.calls)
    }

    @Test
    fun clearPlayHistoryDelegatesToBoundGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeCollectionGateway()
        val removed = mutableListOf<Int>()
        viewModel.bindCollectionGateway(gateway)

        viewModel.playlists.clearPlayHistory { count -> removed += count }
        advanceUntilIdle()

        assertEquals(listOf("clear"), gateway.calls)
        assertEquals(listOf(3), removed)
    }

    @Test
    fun clearPlayHistoryReportsFailureWithoutCallingSuccessCallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collectionGateway = FakeCollectionGateway().apply { failClear = true }
        val libraryGateway = FakeGateway()
        val removed = mutableListOf<Int>()
        val viewModel = LibraryViewModel(dispatcher)
        viewModel.bindCollectionGateway(collectionGateway)
        viewModel.bindGateway(libraryGateway)

        viewModel.playlists.clearPlayHistory { removed += it }
        advanceUntilIdle()

        assertTrue(removed.isEmpty())
        assertEquals(listOf("status:library.history.clear.failed"), libraryGateway.calls)
    }

    @Test
    fun loadLibraryEmitsCachedThenFreshResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val loaded = mutableListOf<LibraryLoadResultUi>()
        viewModel.bindImportGateway(gateway)

        viewModel.loading.loadLibrary(
            allowCachedFirst = true,
            canScan = true,
            onLoaded = { result -> loaded += result }
        )
        advanceUntilIdle()

        assertEquals(listOf("cached", "refreshWithProgress"), gateway.calls)
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

        viewModel.loading.loadLibrary(
            allowCachedFirst = false,
            canScan = true,
            onFailed = { status -> failures += status }
        )
        advanceUntilIdle()

        assertEquals(listOf("refreshWithProgress"), gateway.calls)
        assertEquals(listOf("audio.permission.required"), failures)
    }

    @Test
    fun phaseAwareRefreshPublishesLocalizedStatusKeysInOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val importGateway = FakeImportGateway().apply {
            refreshProgress = listOf(
                LibraryRefreshProgress(LibraryRefreshPhase.CHECKING),
                LibraryRefreshProgress(LibraryRefreshPhase.SCANNING),
                LibraryRefreshProgress(LibraryRefreshPhase.REPLACING, trackCount = 2),
                LibraryRefreshProgress(LibraryRefreshPhase.RELOADING, trackCount = 2)
            )
        }
        val statusGateway = FakeGateway()
        viewModel.bindGateway(statusGateway)
        viewModel.bindImportGateway(importGateway)

        viewModel.loading.loadLibrary(allowCachedFirst = false, canScan = true)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "status:library.scan.checking",
                "status:library.scan.scanning",
                "status:library.scan.replacing",
                "status:library.scan.reloading"
            ),
            statusGateway.calls
        )
    }

    @Test
    fun cancelLibraryLoadSuppressesQueuedRefreshAndStaleCallbacks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val loaded = mutableListOf<LibraryLoadResultUi>()
        val failures = mutableListOf<String>()
        viewModel.bindImportGateway(gateway)

        viewModel.loading.loadLibrary(
            allowCachedFirst = false,
            canScan = true,
            onLoaded = { result -> loaded += result },
            onFailed = { status -> failures += status }
        )
        viewModel.loading.cancelLibraryLoad()
        advanceUntilIdle()

        assertTrue(gateway.calls.isEmpty())
        assertTrue(loaded.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun importAudioUrisDelegatesToGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val loaded = mutableListOf<String>()
        viewModel.bindImportGateway(gateway)

        viewModel.loading.importAudioUris(emptyList()) { result -> loaded += result.status }
        advanceUntilIdle()

        assertEquals(listOf("uris:0"), gateway.calls)
        assertEquals(listOf("uris"), loaded)
    }

    @Test
    fun importFailureIsReportedWithoutEscapingTheViewModelScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val importGateway = FakeImportGateway().apply { failImport = true }
        val libraryGateway = FakeGateway()
        viewModel.bindImportGateway(importGateway)
        viewModel.bindGateway(libraryGateway)

        viewModel.loading.importAudioUris(emptyList())
        advanceUntilIdle()

        assertTrue(libraryGateway.calls.contains("status:library.import.failed"))
    }

    @Test
    fun parseMissingAudioSpecsSkipsEmptyResultAndSerializesRuns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = FakeImportGateway()
        val parsed = mutableListOf<Int>()
        viewModel.bindImportGateway(gateway)

        gateway.audioSpecsResult = LibraryAudioSpecsResultUi(0)
        viewModel.loading.parseMissingAudioSpecs { result -> parsed += result.updatedCount }
        advanceUntilIdle()
        gateway.audioSpecsResult = LibraryAudioSpecsResultUi(2, listOf(track(2L)), setOf(2L))
        viewModel.loading.parseMissingAudioSpecs { result -> parsed += result.updatedCount }
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

        viewModel.loading.importStreamM3u(null) { result -> streamStatuses += result.status }
        viewModel.loading.importPlaylistM3u(null) { result -> playlistResults += result }
        viewModel.loading.exportPlaylist(null, 9L, "Daily Mix") { exported -> exportResults += exported }
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

        viewModel.playlists.addToDefaultPlaylist(track) { result -> defaultAdds += result }
        viewModel.playlists.createPlaylist("Daily Mix") { playlistId -> created += playlistId }
        viewModel.playlists.renamePlaylist(42L, "Renamed") { result -> renamed += result }
        viewModel.playlists.deletePlaylist(42L, "Renamed") { result -> deleted += result }
        viewModel.playlists.removeSelectedPlaylistTrack(42L, track) { result -> removed += result }
        viewModel.playlists.moveSelectedPlaylistTrack(42L, track, 3, -1) { result -> moved += result }
        viewModel.playlists.addTrackToPlaylist(42L, 7L) { result -> added += result }
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
    fun playlistActionFailuresAreContainedAndDoNotPublishSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val actionGateway = FakePlaylistActionGateway().apply { failAll = true }
        val libraryGateway = FakeGateway()
        val callbacks = mutableListOf<String>()
        val viewModel = LibraryViewModel(dispatcher)
        val track = track(7L)
        viewModel.bindPlaylistActionGateway(actionGateway)
        viewModel.bindGateway(libraryGateway)

        viewModel.playlists.addToDefaultPlaylist(track) { callbacks += "default" }
        viewModel.playlists.createPlaylist("Daily Mix") { callbacks += "create" }
        viewModel.playlists.renamePlaylist(42L, "Renamed") { callbacks += "rename" }
        viewModel.playlists.deletePlaylist(42L, "Renamed") { callbacks += "delete" }
        viewModel.playlists.removeSelectedPlaylistTrack(42L, track) { callbacks += "remove" }
        viewModel.playlists.moveSelectedPlaylistTrack(42L, track, 3, -1) { callbacks += "move" }
        viewModel.playlists.addTrackToPlaylist(42L, 7L) { callbacks += "add" }
        advanceUntilIdle()

        assertTrue(callbacks.isEmpty())
        assertEquals(
            7,
            libraryGateway.calls.count { it == "status:library.playlist.action.failed" }
        )
    }

    @Test
    fun playlistActionPresentationsBuildLocalizedStatuses() {
        val languageMode = AppLanguage.MODE_ENGLISH
        val track = track(7L)

        assertEquals(
            AppLanguage.text(languageMode, "added.to.playlist"),
            LibraryPlaylistStatusFactory.defaultAdd(true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "could.not.add.to.playlist"),
            LibraryPlaylistStatusFactory.defaultAdd(false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.created"),
            LibraryPlaylistStatusFactory.created(languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.renamed"),
            LibraryPlaylistStatusFactory.renamed(true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "playlist.rename.failed"),
            LibraryPlaylistStatusFactory.renamed(false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "deleted.playlist.prefix") + "Mix",
            LibraryPlaylistStatusFactory.deleted("Mix", true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "could.not.delete.playlist"),
            LibraryPlaylistStatusFactory.deleted("Mix", false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "removed.from.playlist.prefix") + track.title,
            LibraryPlaylistStatusFactory.removed(track, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "moved.up.prefix") + track.title,
            LibraryPlaylistStatusFactory.moved(track, -1, true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "moved.down.prefix") + track.title,
            LibraryPlaylistStatusFactory.moved(track, 1, true, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "move.failed"),
            LibraryPlaylistStatusFactory.moved(track, 1, false, languageMode).status
        )
        assertEquals(
            AppLanguage.text(languageMode, "added.to.playlist"),
            LibraryPlaylistStatusFactory.defaultAdd(true, languageMode).status
        )
    }

    private fun track(id: Long, albumArtUri: Uri? = null): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id", id, albumArtUri)
    }

    private class FakeCollectionGateway : LibraryCollectionGateway {
        val calls = ArrayList<String>()
        var failClear = false

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
            if (failClear) throw IllegalStateException("database unavailable")
            return 3
        }

    }
    private class FakeImportGateway : LibraryImportGateway, LibraryRefreshProgressGateway {
        val calls = ArrayList<String>()
        var failRefresh = false
        var failImport = false
        var refreshProgress: List<LibraryRefreshProgress> = emptyList()
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

        override fun refresh(onProgress: (LibraryRefreshProgress) -> Unit): LibraryLoadResultUi {
            calls.add("refreshWithProgress")
            refreshProgress.forEach(onProgress)
            if (failRefresh) {
                throw SecurityException("denied")
            }
            return LibraryLoadResultUi(status = "fresh")
        }

        override fun importAudioUris(uris: List<Uri>): LibraryLoadResultUi {
            calls.add("uris:${uris.size}")
            if (failImport) throw SecurityException("revoked")
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
        val failedDefaultIds = mutableSetOf<Long>()
        var failAll = false

        override fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi? {
            calls.add("default:${track?.id}")
            if (failAll) throw IllegalStateException("database unavailable")
            return LibraryDefaultPlaylistAddResultUi(42L, track?.id !in failedDefaultIds)
        }

        override fun createPlaylist(name: String): Long {
            calls.add("create:$name")
            if (failAll) throw IllegalStateException("database unavailable")
            return 42L
        }

        override fun renamePlaylist(playlistId: Long, name: String): Boolean {
            calls.add("rename:$playlistId:$name")
            if (failAll) throw IllegalStateException("database unavailable")
            return true
        }

        override fun deletePlaylist(playlistId: Long): Boolean {
            calls.add("delete:$playlistId")
            if (failAll) throw IllegalStateException("database unavailable")
            return true
        }

        override fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean {
            calls.add("remove:$playlistId:${track?.id}")
            if (failAll) throw IllegalStateException("database unavailable")
            return true
        }

        override fun movePlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int): Boolean {
            calls.add("move:$playlistId:${track?.id}:$trackIndex:$direction")
            if (failAll) throw IllegalStateException("database unavailable")
            return true
        }

        override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
            calls.add("add:$playlistId:$trackId")
            if (failAll) throw IllegalStateException("database unavailable")
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

        override fun requestDeleteTracks(tracks: List<Track>) {
            calls.add("delete:" + tracks.joinToString(",") { it.id.toString() })
        }

        override fun downloadTracks(tracks: List<Track>) {
            calls.add("download:" + tracks.size)
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
