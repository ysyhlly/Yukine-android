package app.yukine

import android.net.Uri
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDataStateOwnerTest {
    private val owner = LibraryDataStateOwner(
        CoroutineScope(Dispatchers.Unconfined),
        Dispatchers.Unconfined
    )

    @Test
    fun favoriteMutationsUpdateTheOwnedLibrarySnapshot() {
        val first = track(1L)
        val second = track(2L)
        owner.replaceLibrary(listOf(first, second), emptySet(), null)

        owner.setFavorite(second.id, true)
        assertEquals(setOf(second.id), owner.state.value.favoriteTrackIds)
        assertEquals(listOf(second), owner.state.value.favoriteTracks)

        assertEquals(false, owner.toggleFavorite(second.id))
        assertEquals(emptySet<Long>(), owner.state.value.favoriteTrackIds)
        assertEquals(emptyList<Track>(), owner.state.value.favoriteTracks)
    }

    @Test
    fun replacementPublishesFavoriteIdsAndTracksTogether() {
        val first = track(1L)
        val second = track(2L)

        owner.replaceLibrary(listOf(first, second), setOf(second.id), null)

        assertEquals(setOf(second.id), owner.state.value.favoriteTrackIds)
        assertEquals(listOf(second), owner.state.value.favoriteTracks)
        assertEquals(setOf(second.id), owner.favoriteTrackIds.value)
    }

    @Test
    fun favoritePendingSurvivesReplacementAndCollectionsPublication() {
        val first = track(1L)
        val second = track(2L)
        owner.replaceLibrary(listOf(first), emptySet(), null)
        assertTrue(owner.beginFavoriteMutation(first.id))

        owner.replaceLibrary(listOf(first, second), setOf(second.id), null)
        assertEquals(setOf(first.id), owner.favoritePendingTrackIds.value)

        owner.applyCollections(
            LibraryCollectionsResult(
                favoriteIds = setOf(second.id),
                favoriteTracks = listOf(second)
            )
        )

        assertEquals(setOf(first.id), owner.favoritePendingTrackIds.value)
    }

    @Test
    fun batchFavoritesPublishOneLibrarySnapshotAndPendingDoesNotPublishLibraryState() = runTest {
        val batchOwner = LibraryDataStateOwner(this, Dispatchers.Unconfined)
        val tracks = (1L..120L).map(::track)
        batchOwner.replaceLibrary(tracks, emptySet(), null)
        var publications = 0
        batchOwner.state
            .drop(1)
            .onEach { publications++ }
            .launchIn(backgroundScope)
        runCurrent()

        val ids = tracks.mapTo(linkedSetOf()) { it.id }
        assertEquals(ids, batchOwner.beginFavoriteMutations(ids))
        batchOwner.endFavoriteMutations(ids)
        runCurrent()
        assertEquals(0, publications)

        batchOwner.setFavorites(ids, true)
        runCurrent()

        assertEquals(1, publications)
        assertEquals(120, batchOwner.state.value.favoriteTrackIds.size)
        assertEquals(120, batchOwner.state.value.favoriteTracks.size)
    }

    @Test
    fun searchCombinesLibraryAndSelectedPlaylistWithoutDuplicateTracks() {
        val shared = track(2L, "Shared Echo")
        owner.replaceLibrary(listOf(track(1L, "Alpha"), shared), emptySet(), null)
        owner.applyCollections(
            LibraryCollectionsResult(
                selectedPlaylistTracks = listOf(shared, track(3L, "Playlist Echo"))
            )
        )

        owner.applySearch("echo")

        assertEquals(listOf(2L, 3L), owner.visibleTracks().map { it.id })
    }

    @Test
    fun searchKeepsCanonicalAliasesMergedWithTheMainLibrarySnapshot() {
        val original = track(11L, "Echo original title")
        val alias = track(12L, "Echo translated alias")
        owner.bindMergeIdentityProvider { "recording:shared" }
        owner.replaceLibrary(listOf(original, alias), emptySet(), null)

        owner.applySearch("echo")

        assertEquals(1, owner.state.value.allTracks.size)
        assertEquals(1, owner.visibleTracks().size)
        assertEquals(
            setOf(original.id, alias.id),
            owner.sourceCandidatesFor(owner.visibleTracks().single()).map { it.id }.toSet()
        )
    }

    @Test
    fun playlistAliasSearchReusesPreparedCanonicalRepresentative() {
        val original = track(21L, "Original title")
        val alias = track(22L, "Echo translated alias")
        owner.bindMergeIdentityProvider { "recording:shared" }
        owner.replaceLibrary(listOf(original, alias), emptySet(), null)
        owner.applyCollections(
            LibraryCollectionsResult(selectedPlaylistTracks = listOf(alias))
        )

        owner.applySearch("echo")

        assertEquals(listOf(original.id), owner.visibleTracks().map { it.id })
        assertEquals(
            setOf(original.id, alias.id),
            owner.sourceCandidatesFor(owner.visibleTracks().single()).map { it.id }.toSet()
        )
    }

    @Test
    fun asyncReplacementReadsFreshPersistedIdentitySnapshotOncePerPublication() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val asyncOwner = LibraryDataStateOwner(this, dispatcher)
        val original = track(23L, "Original")
        val webDav = track(24L, "WebDAV")
        var snapshotLoads = 0
        var identities = emptyMap<Long, Long>()
        asyncOwner.bindMergeIdentityProvider { "recording:stale-process-cache" }
        asyncOwner.bindRecordingIdentitySnapshotProvider {
            snapshotLoads++
            identities
        }

        asyncOwner.replaceLibraryAsync(
            listOf(original, webDav),
            emptySet(),
            null,
            Runnable {}
        )
        runCurrent()
        assertEquals(listOf(23L, 24L), asyncOwner.allTracks().map { it.id })

        identities = mapOf(23L to 901L, 24L to 901L)
        asyncOwner.replaceLibraryAsync(
            listOf(original, webDav),
            emptySet(),
            null,
            Runnable {}
        )
        runCurrent()

        assertEquals(2, snapshotLoads)
        assertEquals(listOf(23L), asyncOwner.allTracks().map { it.id })
        assertEquals(
            setOf(23L, 24L),
            asyncOwner.sourceCandidatesFor(asyncOwner.allTracks().single()).map { it.id }.toSet()
        )
    }

    @Test
    fun asyncSearchDebouncesAndPublishesOnlyTheLatestQuery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val asyncOwner = LibraryDataStateOwner(this, dispatcher)
        asyncOwner.replaceLibrary(
            listOf(track(31L, "Alpha"), track(32L, "Echo")),
            emptySet(),
            null
        )
        var appliedCount = 0

        asyncOwner.applySearchAsync("alpha", Runnable { appliedCount++ })
        asyncOwner.applySearchAsync("echo", Runnable { appliedCount++ })
        runCurrent()
        advanceTimeBy(199L)
        runCurrent()
        assertEquals(0, appliedCount)
        assertEquals(listOf(31L, 32L), asyncOwner.visibleTracks().map { it.id })

        advanceTimeBy(1L)
        runCurrent()

        assertEquals(1, appliedCount)
        assertEquals(listOf(32L), asyncOwner.visibleTracks().map { it.id })
    }

    @Test
    fun staleAsyncSearchDoesNotPublishAfterLibraryReplacement() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val asyncOwner = LibraryDataStateOwner(this, dispatcher)
        asyncOwner.replaceLibrary(
            listOf(track(31L, "Alpha"), track(32L, "Echo")),
            emptySet(),
            null
        )
        var appliedCount = 0

        asyncOwner.applySearchAsync("alpha", Runnable { appliedCount++ })
        runCurrent()
        advanceTimeBy(200L)
        asyncOwner.replaceLibrary(listOf(track(33L, "Beta")), emptySet(), null)
        runCurrent()

        assertEquals(0, appliedCount)
        assertEquals(listOf(33L), asyncOwner.visibleTracks().map { it.id })
    }

    private fun track(id: Long, title: String = "Track $id"): Track =
        Track(id, title, "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
