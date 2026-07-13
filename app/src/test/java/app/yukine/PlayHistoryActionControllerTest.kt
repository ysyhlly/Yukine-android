package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayHistoryActionControllerTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun clearPlayHistoryPublishesStatusUpdatesStoreAndReloadsCollections() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher, dispatcher)
        val store = viewModel.dataOwner()
        val statuses = mutableListOf<String>()
        var reloads = 0
        viewModel.bindCollectionGateway(FakeCollectionGateway(removed = 4))
        val controller = PlayHistoryActionController(
            viewModel,
            { AppLanguage.MODE_ENGLISH },
            PlayHistoryStateStore { store.clearPlayHistory() },
            { statuses += it },
            Runnable { reloads += 1 }
        )

        store.applyCollections(
            LibraryCollectionsResult(
                favoriteIds = emptySet(),
                favoriteTracks = listOf(),
                recentRecords = listOf(record(1L), record(2L)),
                mostPlayedRecords = listOf(record(3L))
            )
        )

        controller.clearPlayHistory()
        advanceUntilIdle()

        assertEquals(listOf("Clearing play history", "Cleared play history: 4"), statuses)
        assertEquals(1, reloads)
        assertEquals(0, store.recentRecords().size)
        assertEquals(0, store.mostPlayedRecords().size)
    }

    private fun record(trackId: Long): TrackPlayRecord =
        TrackPlayRecord(
            Track(trackId, "Track $trackId", "", "", 1000L, Uri.EMPTY, "file:$trackId"),
            1L,
            1
        )

    private class FakeCollectionGateway(private val removed: Int) : LibraryCollectionGateway {
        override fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult = LibraryCollectionsResult()

        override fun clearPlayHistory(): Int = removed

    }
}
