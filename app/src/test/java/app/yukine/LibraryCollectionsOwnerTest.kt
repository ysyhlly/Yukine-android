package app.yukine

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCollectionsOwnerTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun loadsCurrentSelectionAndPublishesNormalizedResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val navigationViewModel = NavigationViewModel(SavedStateHandle())
        val routeController = MainRouteController(navigationViewModel)
        val activityViewModel = MainActivityViewModel(SavedStateHandle())
        val libraryStore = MainLibraryStore(
            LibrarySearchUseCase(object : LibrarySearchOperations {
                override fun search(source: List<app.yukine.model.Track>, query: String?) = source
            }),
            activityViewModel,
            dispatcher
        )
        val libraryViewModel = LibraryViewModel(dispatcher)
        val requestedIds = mutableListOf<Long>()
        libraryViewModel.bindCollectionGateway(object : LibraryCollectionGateway {
            override fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult {
                requestedIds += selectedPlaylistId
                return LibraryCollectionsResult(
                    selectedPlaylistId = 12L,
                    favoriteIds = setOf(4L)
                )
            }

            override fun clearPlayHistory(): Int = 0
        })
        val owner = LibraryCollectionsOwner(libraryViewModel, routeController, libraryStore)
        routeController.setSelectedPlaylistId(9L)

        owner.load()
        advanceUntilIdle()

        assertEquals(listOf(9L), requestedIds)
        assertEquals(12L, routeController.selectedPlaylistId())
        assertEquals(setOf(4L), libraryStore.favoriteIds())
    }

    @Test
    fun selectAndLoadUpdatesRouteBeforeReadingGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val routeController = MainRouteController(NavigationViewModel(SavedStateHandle()))
        val activityViewModel = MainActivityViewModel(SavedStateHandle())
        val libraryStore = MainLibraryStore(
            LibrarySearchUseCase(object : LibrarySearchOperations {
                override fun search(source: List<app.yukine.model.Track>, query: String?) = source
            }),
            activityViewModel,
            dispatcher
        )
        val requestedIds = mutableListOf<Long>()
        val libraryViewModel = LibraryViewModel(dispatcher).apply {
            bindCollectionGateway(object : LibraryCollectionGateway {
                override fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult {
                    requestedIds += selectedPlaylistId
                    return LibraryCollectionsResult(selectedPlaylistId = selectedPlaylistId)
                }

                override fun clearPlayHistory(): Int = 0
            })
        }
        val owner = LibraryCollectionsOwner(libraryViewModel, routeController, libraryStore)

        owner.selectAndLoad(21L)
        advanceUntilIdle()

        assertEquals(listOf(21L), requestedIds)
        assertEquals(21L, routeController.selectedPlaylistId())
    }
}
