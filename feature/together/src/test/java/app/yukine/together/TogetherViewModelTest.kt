package app.yukine.together

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherViewModelTest {
    @Test
    fun openCreateFromPlaylistLoadsCatalogTracksForGivenRef() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val ref = TogetherPlaylistRef.Streaming("qqmusic", "playlist-1")
            val current = listOf(localItem("local"), streamingItem("cloud", "track-1"))
            val catalog = FakeCatalog(
                loads = mapOf(
                    ref to TogetherPlaylistLoadResult(
                        "Playlist",
                        listOf(localItem("from-playlist"), streamingItem("pl-cloud", "pl-1"))
                    )
                )
            )
            val viewModel = TogetherViewModel(
                session = FakeSession(),
                currentQueue = { current },
                preferences = FakeSettings(),
                playlistCatalog = catalog
            )

            viewModel.openCreateFromPlaylist(ref)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TogetherPage.Create, state.page)
            assertEquals(listOf("from-playlist", "pl-cloud"), state.draftQueue.map(TogetherQueueItem::title))
            assertEquals(listOf(ref), catalog.loaded)
            assertTrue(state.skippedItems.isEmpty())
            assertNull(state.message)
            assertFalse(state.playlistLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectingPlaylistConfirmsBeforeReplacingNonEmptyDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val ref = TogetherPlaylistRef.Local(7)
            val catalog = FakeCatalog(
                loads = mapOf(
                    ref to TogetherPlaylistLoadResult("Local", listOf(localItem("replacement")))
                )
            )
            val viewModel = TogetherViewModel(
                session = FakeSession(),
                currentQueue = { listOf(localItem("existing")) },
                preferences = FakeSettings(),
                playlistCatalog = catalog
            )
            viewModel.openCreate()
            viewModel.choosePlaylist(ref)

            assertEquals("replace_queue_confirmation_required", viewModel.uiState.value.message)
            assertEquals(ref, viewModel.uiState.value.pendingPlaylistRef)
            assertTrue(catalog.loaded.isEmpty())

            viewModel.confirmReplacePlaylist()
            advanceUntilIdle()

            assertEquals(listOf("replacement"), viewModel.uiState.value.draftQueue.map(TogetherQueueItem::title))
            assertEquals(listOf(ref), catalog.loaded)
            assertNull(viewModel.uiState.value.pendingPlaylistRef)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun openCreateFromPlaylistClearsDraftAndBlocksCreateWhileLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val ref = TogetherPlaylistRef.Local(3)
            val catalog = FakeCatalog(
                loads = mapOf(
                    ref to TogetherPlaylistLoadResult("Local", listOf(localItem("loaded")))
                )
            )
            val session = FakeSession()
            val viewModel = TogetherViewModel(
                session = session,
                currentQueue = { listOf(localItem("stale")) },
                preferences = FakeSettings(),
                playlistCatalog = catalog
            )
            viewModel.openCreate()
            assertEquals(listOf("stale"), viewModel.uiState.value.draftQueue.map { it.title })

            viewModel.openCreateFromPlaylist(ref)
            // Loading starts with empty draft so Create cannot commit the stale queue.
            assertTrue(viewModel.uiState.value.playlistLoading)
            assertTrue(viewModel.uiState.value.draftQueue.isEmpty())
            viewModel.create()
            advanceUntilIdle()
            assertEquals(0, session.createCalls)

            assertEquals(listOf("loaded"), viewModel.uiState.value.draftQueue.map { it.title })
            assertFalse(viewModel.uiState.value.playlistLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyCurrentQueueCannotCreateRoom() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val session = FakeSession()
            val viewModel = TogetherViewModel(
                session = session,
                currentQueue = { emptyList() },
                preferences = FakeSettings(),
                playlistCatalog = FakeCatalog()
            )

            viewModel.openCreate()
            advanceUntilIdle()
            viewModel.create()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.draftQueue.isEmpty())
            assertEquals(0, session.createCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sameTrackIdentityQueueEmissionsDoNotWipeDraftOrMessages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val first = localItem("one")
            val second = streamingItem("cloud", "track-2")
            val updates = MutableStateFlow(listOf(first, second))
            val viewModel = TogetherViewModel(
                session = FakeSession(),
                currentQueue = { updates.value },
                preferences = FakeSettings(),
                currentQueueUpdates = updates
            )
            viewModel.openCreate()
            advanceUntilIdle()
            viewModel.choosePlaylist(TogetherPlaylistRef.Local(1))
            assertEquals("replace_queue_confirmation_required", viewModel.uiState.value.message)

            // Re-emit same identity/order (e.g. currentIndex-only publish with new list instances).
            updates.value = listOf(
                first.copy(title = "one-renamed-by-metadata"),
                second.copy(artist = "updated")
            )
            advanceUntilIdle()

            assertEquals("replace_queue_confirmation_required", viewModel.uiState.value.message)
            assertEquals(listOf("one", "cloud"), viewModel.uiState.value.draftQueue.map { it.title })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun liveQueueIdentityChangesStillUpdateDraftWhileFollowingPlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val updates = MutableStateFlow(listOf(localItem("one"), streamingItem("cloud", "track-2")))
            val edits = FakeQueueEdits()
            val viewModel = TogetherViewModel(
                session = FakeSession(),
                currentQueue = { updates.value },
                preferences = FakeSettings(),
                currentQueueUpdates = updates,
                queueEditPort = edits
            )
            viewModel.openCreate()
            advanceUntilIdle()

            updates.value = updates.value + localItem("three")
            advanceUntilIdle()
            assertEquals(listOf("one", "cloud", "three"), viewModel.uiState.value.draftQueue.map { it.title })

            viewModel.moveDraft(2, 1)
            viewModel.removeDraft(0)
            assertEquals(listOf("three" to "track-2"), edits.moves)
            assertEquals(listOf("one"), edits.removed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun afterPlaylistLoadDraftEditsDoNotHitQueueEditPort() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val ref = TogetherPlaylistRef.Local(9)
            val catalog = FakeCatalog(
                loads = mapOf(
                    ref to TogetherPlaylistLoadResult(
                        "Local",
                        listOf(localItem("a"), localItem("b"), localItem("c"))
                    )
                )
            )
            val edits = FakeQueueEdits()
            val updates = MutableStateFlow(listOf(localItem("playback")))
            val viewModel = TogetherViewModel(
                session = FakeSession(),
                currentQueue = { updates.value },
                preferences = FakeSettings(),
                playlistCatalog = catalog,
                currentQueueUpdates = updates,
                queueEditPort = edits
            )

            viewModel.openCreateFromPlaylist(ref)
            advanceUntilIdle()
            assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.draftQueue.map { it.title })

            // Live playback ticks must not overwrite catalog draft.
            updates.value = listOf(localItem("should-not-appear"))
            advanceUntilIdle()
            assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.draftQueue.map { it.title })

            viewModel.moveDraft(2, 0)
            viewModel.removeDraft(1)
            assertTrue(edits.moves.isEmpty())
            assertTrue(edits.removed.isEmpty())
            assertEquals(listOf("c", "b"), viewModel.uiState.value.draftQueue.map { it.title })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeCatalog(
        private val playlists: List<TogetherPlaylistSummary> = emptyList(),
        private val loads: Map<TogetherPlaylistRef, TogetherPlaylistLoadResult> = emptyMap()
    ) : TogetherPlaylistCatalogPort {
        val loaded = mutableListOf<TogetherPlaylistRef>()

        override suspend fun listPlaylists(): TogetherPlaylistCatalogResult =
            TogetherPlaylistCatalogResult(playlists)

        override suspend fun loadPlaylist(ref: TogetherPlaylistRef): TogetherPlaylistLoadResult {
            loaded += ref
            return requireNotNull(loads[ref])
        }
    }

    private class FakeSettings : TogetherSettingsPort {
        override fun load(): TogetherSavedSettings = TogetherSavedSettings(nickname = "tester")
        override fun save(settings: TogetherSavedSettings) = Unit
    }

    private class FakeQueueEdits : TogetherQueueEditPort {
        val removed = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, String>>()

        override fun remove(stableId: String) {
            removed += stableId
        }

        override fun move(fromStableId: String, toStableId: String) {
            moves += fromStableId to toStableId
        }
    }

    private class FakeSession : TogetherSessionPort {
        override val state = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
        var createCalls = 0

        override suspend fun testConnection(options: TogetherConnectOptions) = Result.success("ok")
        override suspend fun previewJoin(roomCode: String, options: TogetherConnectOptions) =
            Result.success(TogetherJoinPreview(emptyList(), Long.MAX_VALUE))

        override suspend fun create(
            editableQueue: List<TogetherQueueItem>,
            options: TogetherConnectOptions
        ): Result<Unit> {
            createCalls += 1
            return Result.success(Unit)
        }

        override suspend fun join(
            roomCode: String,
            localMatches: List<TogetherQueueItem>,
            options: TogetherConnectOptions
        ) = Result.success(Unit)

        override suspend fun leave(reason: String) = Unit
        override suspend fun saveReceived(fileId: String) = Result.success("")
    }

    private fun localItem(id: String) = TogetherQueueItem(
        stableId = id,
        title = id,
        artist = "",
        sourceUri = "file:///$id.mp3"
    )

    private fun streamingItem(title: String, trackId: String) = TogetherQueueItem(
        stableId = trackId,
        title = title,
        artist = "",
        sourceUri = "https://example.invalid/$trackId",
        sizeBytes = 1024,
        source = TogetherQueueSource.Streaming(
            provider = "qqmusic",
            providerTrackId = trackId,
            quality = "lossless",
            resolvedUrl = "https://example.invalid/$trackId"
        )
    )
}
