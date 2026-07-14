package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryImportOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun audioImportPublishesOneLibrarySnapshotAndRefreshesCollections() = runTest {
        val track = Track(8L, "Imported", "Artist", "Album", 1_000L, Uri.EMPTY, "file:8")
        val gateway = FakeImportGateway(
            imported = LibraryLoadResultUi(listOf(track), setOf(track.id), "Imported")
        )
        val fixture = fixture(gateway, StandardTestDispatcher(testScheduler))

        fixture.owner.importAudioUris(listOf(Uri.EMPTY))
        advanceUntilIdle()

        assertEquals(listOf(track), fixture.libraryViewModel.library.value.allTracks)
        assertEquals(setOf(track.id), fixture.libraryViewModel.library.value.favoriteTrackIds)
        assertEquals(1, fixture.collectionsLoads)
        assertEquals(
            listOf(
                AppLanguage.text(AppLanguage.MODE_ENGLISH, "importing.audio.files"),
                "Imported"
            ),
            fixture.statuses
        )
    }

    @Test
    fun deviceScanPublishesCompletionOnlyAfterReplacementIsApplied() = runTest {
        val track = Track(9L, "Scanned", "Artist", "Album", 1_000L, Uri.EMPTY, "file:9")
        val gateway = FakeImportGateway(
            refreshed = LibraryLoadResultUi(listOf(track), emptySet(), "Library updated")
        )
        val fixture = fixture(gateway, StandardTestDispatcher(testScheduler))

        fixture.owner.loadLibrary(allowCachedFirst = false)
        advanceUntilIdle()

        assertEquals(listOf(track), fixture.libraryViewModel.library.value.allTracks)
        assertEquals(listOf(true), fixture.scanResults)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "library.scan.found.prefix") +
                "1" +
                AppLanguage.text(AppLanguage.MODE_ENGLISH, "library.scan.found.suffix"),
            fixture.statuses.last()
        )
    }

    private fun fixture(
        gateway: FakeImportGateway,
        dispatcher: CoroutineDispatcher
    ): Fixture {
        val libraryViewModel = LibraryViewModel(dispatcher, dispatcher)
        libraryViewModel.bindImportGateway(gateway)
        val store = libraryViewModel.dataOwner()
        val routeController = MainRouteController(NavigationViewModel(SavedStateHandle()))
        val statuses = mutableListOf<String>()
        val scanResults = mutableListOf<Boolean>()
        var collectionsLoads = 0
        val owner = LibraryImportOwner(
            libraryViewModel,
            store,
            routeController,
            LibraryImportOwner.AudioPermissionSource { true },
            LibraryImportOwner.LanguageModeSource { AppLanguage.MODE_ENGLISH },
            LibraryImportOwner.StatusSink(statuses::add),
            LibraryImportOwner.CollectionsLoader { collectionsLoads++ },
            LibraryImportOwner.OnboardingScanObserver(scanResults::add),
            LibraryImportOwner.NetworkNavigator {}
        )
        return Fixture(owner, libraryViewModel, statuses, scanResults) { collectionsLoads }
    }

    private class FakeImportGateway(
        private val imported: LibraryLoadResultUi = LibraryLoadResultUi(),
        private val refreshed: LibraryLoadResultUi = LibraryLoadResultUi()
    ) : LibraryImportGateway {
        override fun loadCached(): LibraryLoadResultUi = refreshed
        override fun refresh(): LibraryLoadResultUi = refreshed
        override fun importAudioUris(uris: List<Uri>): LibraryLoadResultUi = imported
        override fun importAudioTree(treeUri: Uri): LibraryLoadResultUi = imported
        override fun parseMissingAudioSpecs(): LibraryAudioSpecsResultUi = LibraryAudioSpecsResultUi()
    }

    private class Fixture(
        val owner: LibraryImportOwner,
        val libraryViewModel: LibraryViewModel,
        val statuses: List<String>,
        val scanResults: List<Boolean>,
        private val collectionsLoadCount: () -> Int
    ) {
        val collectionsLoads: Int
            get() = collectionsLoadCount()
    }
}
