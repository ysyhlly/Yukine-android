package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryWebDavSyncOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startupPreferenceRunsOneSyncAndPublishesResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val operations = FakeOperations(autoSyncEnabled = true)
        val viewModel = LibraryViewModel(dispatcher, dispatcher)
        val snapshots = mutableListOf<WebDavSourceSnapshot>()
        val statuses = mutableListOf<String>()
        val owner = LibraryWebDavSyncOwner(
            operations,
            viewModel.presentationOwner(),
            snapshots::add,
            { AppLanguage.MODE_ENGLISH },
            statuses::add,
            CoroutineScope(dispatcher + Job()),
            dispatcher
        )

        owner.initialize()
        advanceUntilIdle()

        assertTrue(viewModel.libraryUi.value.autoSyncEnabled)
        assertFalse(viewModel.libraryUi.value.operationInProgress)
        assertEquals(1, operations.syncCount)
        assertEquals(1, snapshots.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "library.sync.complete"), statuses.last())
        owner.release()
    }

    @Test
    fun enablingAutomaticSyncPersistsAndSyncsImmediately() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val operations = FakeOperations(autoSyncEnabled = false)
        val viewModel = LibraryViewModel(dispatcher, dispatcher)
        val owner = LibraryWebDavSyncOwner(
            operations,
            viewModel.presentationOwner(),
            { },
            { AppLanguage.MODE_ENGLISH },
            { },
            CoroutineScope(dispatcher + Job()),
            dispatcher
        )

        owner.setAutoSyncEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf(true), operations.savedValues)
        assertTrue(viewModel.libraryUi.value.autoSyncEnabled)
        assertEquals(1, operations.syncCount)
        owner.release()
    }

    @Test
    fun initializationDoesNotScanKnownMatchesWhenAutomaticSyncIsDisabled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val operations = FakeOperations(autoSyncEnabled = false)
        val matchOperations = KnownMatchOperations()
        val viewModel = LibraryViewModel(dispatcher, dispatcher)
        var invalidations = 0
        val owner = LibraryWebDavSyncOwner(
            operations,
            viewModel.presentationOwner(),
            { },
            { AppLanguage.MODE_ENGLISH },
            { },
            CoroutineScope(dispatcher + Job()),
            dispatcher,
            LibraryMultiSourceSyncCoordinator(matchOperations),
            Runnable { invalidations++ }
        )

        owner.initialize()
        advanceUntilIdle()

        assertEquals(0, matchOperations.batchLoadCount)
        assertEquals(0, invalidations)
        assertEquals(0, operations.syncCount)
        owner.release()
    }

    private class FakeOperations(
        private val autoSyncEnabled: Boolean
    ) : LibraryWebDavSyncOperations {
        val savedValues = mutableListOf<Boolean>()
        var syncCount = 0

        override fun sourceIds(): List<Long> = listOf(7L)

        override fun syncAll(sourceIds: List<Long>): WebDavSourceSnapshot {
            syncCount++
            return WebDavSourceSnapshot(
                listOf(Track(7L, "Track", "Artist", "Album", 1_000L, Uri.EMPTY, "webdav:7:file.flac")),
                emptySet(),
                "sync"
            )
        }

        override fun loadAutoSyncEnabled(): Boolean = autoSyncEnabled

        override fun saveAutoSyncEnabled(enabled: Boolean) {
            savedValues += enabled
        }
    }

    private class KnownMatchOperations : LibraryMultiSourceSyncOperations {
        var batchLoadCount = 0

        override suspend fun addedProviders(): List<StreamingProviderDescriptor> = emptyList()

        override fun tracks(): List<Track> = emptyList()

        override fun storedMatch(track: Track, provider: StreamingProviderName): String = ""

        override fun storedMatches(
            tracks: List<Track>,
            providers: List<StreamingProviderName>
        ): Map<Long, Map<StreamingProviderName, String>> {
            batchLoadCount++
            return emptyMap()
        }

        override fun saveMatch(
            track: Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) = Unit

        override suspend fun search(
            provider: StreamingProviderName,
            query: String
        ): List<StreamingTrack> = emptyList()
    }
}
