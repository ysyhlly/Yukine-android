package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.identity.MusicIdentityDiagnostics
import app.yukine.streaming.StreamingProviderCapabilities
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingTrack
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryMultiSourceSyncCoordinatorTest {
    @Test
    fun incrementalSyncStoresMatchesAndNoSourceWithoutCreatingLibraryRows() = runTest {
        val operations = FakeOperations()
        val diagnostics = MusicIdentityDiagnostics()
        val coordinator = LibraryMultiSourceSyncCoordinator(operations, diagnostics)

        val first = coordinator.syncIncremental()
        val second = coordinator.syncIncremental()

        assertEquals(2, first.checkedCount)
        assertEquals(1, first.matchedCount)
        assertEquals(1, first.unavailableCount)
        assertEquals(0, second.checkedCount)
        assertEquals(
            "wy:lx-42",
            StoredStreamingSourceMatchCodec.primaryProviderTrackId(
                operations.matches[operations.track.id to StreamingProviderName.LUOXUE]
            )
        )
        assertEquals(
            true,
            operations.matches[operations.track.id to StreamingProviderName.NETEASE]
                ?.startsWith(STREAMING_NO_SOURCE_MATCH)
        )
        assertEquals(1, coordinator.candidatesFor(operations.track).size)
        assertEquals(
            "streaming:luoxue:wy:lx-42",
            coordinator.candidatesFor(operations.track).first().dataPath.substringBefore('?')
        )
        assertNull(coordinator.persistedMergeIdentityFor(operations.track))
        assertEquals(
            "夜空 歌手 专辑",
            operations.queries.single { it.first == StreamingProviderName.LUOXUE }.second
        )
        assertEquals(
            listOf("wy:lx-42"),
            StreamingPlaybackAdapter.playbackCandidates(
                coordinator.candidatesFor(operations.track).single()
            ).map { it.providerTrackId }
        )
        assertEquals(
            1,
            StoredStreamingSourceMatchCodec.decode(
                operations.matches.getValue(
                    operations.track.id to StreamingProviderName.LUOXUE
                )
            )?.candidates?.size
        )
        assertEquals(
            true,
            StoredStreamingSourceMatchCodec.isCurrentEncoding(
                operations.matches.getValue(
                    operations.track.id to StreamingProviderName.LUOXUE
                )
            )
        )
        assertEquals(
            listOf("wy:lx-42", "tx:lx-live"),
            operations.candidateCatalogs.getValue(
                operations.track.id to StreamingProviderName.LUOXUE
            ).map(StreamingTrack::providerTrackId)
        )
        assertEquals(1, operations.tracks().size)
        assertEquals(2, operations.batchLoadCount)
        assertEquals(0, operations.singleLoadCount)
        val diagnosticSnapshot = diagnostics.snapshot(MusicIdentityDiagnostics.Operation.PLATFORM_SYNC)
        assertEquals(
            2,
            diagnosticSnapshot.stages.getValue(MusicIdentityDiagnostics.Stage.SCORING).sampleCount
        )
        assertEquals(
            2L,
            diagnosticSnapshot.stages.getValue(MusicIdentityDiagnostics.Stage.SCORING).workUnits
        )
        assertEquals(
            2,
            diagnosticSnapshot.stages.getValue(MusicIdentityDiagnostics.Stage.SOURCE_FETCH).sampleCount
        )
        assertEquals(
            2,
            diagnosticSnapshot.stages.getValue(MusicIdentityDiagnostics.Stage.TOTAL).sampleCount
        )
    }

    @Test
    fun refreshUpgradesLegacyCandidateEncodingWithoutSearchingAgain() = runTest {
        val operations = FakeOperations()
        operations.matches[operations.track.id to StreamingProviderName.LUOXUE] =
            "__echo_source_match_v1__:{\"primary\":\"wy:old\",\"candidates\":[{\"id\":\"wy:old\"}]}"
        val coordinator = LibraryMultiSourceSyncCoordinator(operations)

        coordinator.refreshKnownMatches()

        val stored = operations.matches.getValue(operations.track.id to StreamingProviderName.LUOXUE)
        assertEquals(true, stored.startsWith("__echo_source_match_v2__:"))
        assertEquals("wy:old", StoredStreamingSourceMatchCodec.primaryProviderTrackId(stored))
        assertEquals(0, operations.queries.size)
    }

    @Test
    fun incrementalSyncRetriesLegacyLxNoSourceWithFullMetadataStrategy() = runTest {
        val operations = FakeOperations()
        operations.matches[operations.track.id to StreamingProviderName.LUOXUE] =
            "$STREAMING_NO_SOURCE_MATCH:999999"
        operations.matches[operations.track.id to StreamingProviderName.NETEASE] =
            "$STREAMING_NO_SOURCE_MATCH:999999"
        val coordinator = LibraryMultiSourceSyncCoordinator(operations) { 1_000_000L }

        val result = coordinator.syncIncremental()

        assertEquals(1, result.checkedCount)
        assertEquals(1, result.matchedCount)
        assertEquals(
            listOf(StreamingProviderName.LUOXUE to "夜空 歌手 专辑"),
            operations.queries
        )
        assertEquals(
            "wy:lx-42",
            StoredStreamingSourceMatchCodec.primaryProviderTrackId(
                operations.matches.getValue(operations.track.id to StreamingProviderName.LUOXUE)
            )
        )
    }

    @Test
    fun incrementalSyncRefreshesV2LxMatchWithFullMetadataStrategy() = runTest {
        val operations = FakeOperations()
        operations.matches[operations.track.id to StreamingProviderName.LUOXUE] =
            "__echo_source_match_v2__:{\"primary\":\"tx:old\",\"candidates\":[{\"id\":\"tx:old\"}]}"
        operations.matches[operations.track.id to StreamingProviderName.NETEASE] =
            "$STREAMING_NO_SOURCE_MATCH:999999"
        val coordinator = LibraryMultiSourceSyncCoordinator(operations) { 1_000_000L }

        val result = coordinator.syncIncremental()

        assertEquals(1, result.checkedCount)
        assertEquals(
            listOf(StreamingProviderName.LUOXUE to "夜空 歌手 专辑"),
            operations.queries
        )
        assertEquals(
            "wy:lx-42",
            StoredStreamingSourceMatchCodec.primaryProviderTrackId(
                operations.matches.getValue(operations.track.id to StreamingProviderName.LUOXUE)
            )
        )
        assertEquals(
            true,
            StoredStreamingSourceMatchCodec.isCurrentEncoding(
                operations.matches.getValue(operations.track.id to StreamingProviderName.LUOXUE)
            )
        )
    }

    @Test
    fun persistedMergeIdentityUsesOnlyCanonicalRecordingUuid() = runTest {
        val operations = FakeOperations().apply { canonicalId = "123e4567-e89b-12d3-a456-426614174000" }
        val coordinator = LibraryMultiSourceSyncCoordinator(operations)

        coordinator.syncIncremental()

        assertEquals(
            "recording:123e4567-e89b-12d3-a456-426614174000",
            coordinator.persistedMergeIdentityFor(operations.track)
        )
    }

    @Test
    fun incrementalSyncRunsConfirmedSourceBackfillAndPublishesMergedCount() = runTest {
        val operations = FakeOperations().apply { backfillMergeCount = 2 }

        val result = LibraryMultiSourceSyncCoordinator(operations).syncIncremental()

        assertEquals(2, result.mergedRecordingCount)
        assertEquals(1, operations.backfillCount)
        assertEquals(1, operations.identityRefreshCount)
    }

    @Test
    fun incrementalSyncBoundsNetworkConcurrencyAndCommitsInTrackOrder() = runTest {
        val operations = ConcurrentSearchOperations()
        val coordinator = LibraryMultiSourceSyncCoordinator(
            operations = operations,
            maxConcurrentSearches = 2
        )

        val result = coordinator.syncIncremental()

        assertEquals(6, result.checkedCount)
        assertEquals(6, result.matchedCount)
        assertEquals(2, operations.maxConcurrentSearches.get())
        assertEquals((1L..6L).toList(), operations.candidateCommitOrder)
        assertEquals((1L..6L).toList(), operations.matchCommitOrder)
    }

    @Test
    fun incrementalSyncPropagatesCancellationWithoutPersistingNegativeMatches() = runTest {
        val operations = FakeOperations().apply {
            searchCancellation = CancellationException("cancel sync")
        }
        val coordinator = LibraryMultiSourceSyncCoordinator(operations)
        var cancellationObserved = false

        try {
            coordinator.syncIncremental()
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertEquals(true, cancellationObserved)
        assertEquals(emptyMap<Pair<Long, StreamingProviderName>, String>(), operations.matches)
        assertEquals(
            emptyMap<Pair<Long, StreamingProviderName>, List<StreamingTrack>>(),
            operations.candidateCatalogs
        )
    }

    private class FakeOperations : LibraryMultiSourceSyncOperations {
        val track = Track(1L, "夜空", "歌手", "专辑", 180_000L, Uri.EMPTY, "file.flac")
        val matches = linkedMapOf<Pair<Long, StreamingProviderName>, String>()
        val queries = mutableListOf<Pair<StreamingProviderName, String>>()
        val candidateCatalogs = linkedMapOf<Pair<Long, StreamingProviderName>, List<StreamingTrack>>()
        var canonicalId: String? = null
        var searchCancellation: CancellationException? = null
        var batchLoadCount = 0
        var singleLoadCount = 0
        var backfillMergeCount = 0
        var backfillCount = 0
        var identityRefreshCount = 0

        override suspend fun addedProviders(): List<StreamingProviderDescriptor> = listOf(
            descriptor(StreamingProviderName.LUOXUE, "洛雪音源"),
            descriptor(StreamingProviderName.NETEASE, "网易云音乐")
        )

        override fun tracks(): List<Track> = listOf(track)

        override fun canonicalIdentity(track: Track): String? = canonicalId

        override fun ingestConfirmedSources(): Int {
            backfillCount++
            return backfillMergeCount
        }

        override fun refreshIdentitySnapshot(): Long {
            identityRefreshCount++
            return identityRefreshCount.toLong()
        }

        override fun storedMatch(track: Track, provider: StreamingProviderName): String {
            singleLoadCount++
            return matches[track.id to provider].orEmpty()
        }

        override fun storedMatches(
            tracks: List<Track>,
            providers: List<StreamingProviderName>
        ): Map<Long, Map<StreamingProviderName, String>> {
            batchLoadCount++
            return tracks.associate { item ->
                item.id to providers.associateWith { provider ->
                    matches[item.id to provider].orEmpty()
                }
            }
        }

        override fun saveMatch(track: Track, provider: StreamingProviderName, providerTrackId: String) {
            matches[track.id to provider] = providerTrackId
        }

        override fun saveCandidates(
            track: Track,
            provider: StreamingProviderName,
            candidates: List<StreamingTrack>
        ) {
            candidateCatalogs[track.id to provider] = candidates
        }

        override suspend fun search(provider: StreamingProviderName, query: String): List<StreamingTrack> {
            searchCancellation?.let { throw it }
            queries += provider to query
            return if (provider == StreamingProviderName.LUOXUE) {
                listOf(
                    StreamingTrack(
                        provider = provider,
                        providerTrackId = "tx:lx-live",
                        title = "夜空 (Live)",
                        artist = "歌手",
                        album = "巡演现场",
                        durationMs = 196_000L
                    ),
                    StreamingTrack(
                        provider = provider,
                        providerTrackId = "wy:lx-42",
                        title = "夜空",
                        artist = "歌手",
                        durationMs = 180_500L
                    )
                )
            } else {
                emptyList()
            }
        }

        private fun descriptor(provider: StreamingProviderName, name: String) = StreamingProviderDescriptor(
            name = provider,
            displayName = name,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true
            )
        )
    }

    private class ConcurrentSearchOperations : LibraryMultiSourceSyncOperations {
        private val activeSearches = AtomicInteger()
        private val firstPairStarted = CompletableDeferred<Unit>()
        val maxConcurrentSearches = AtomicInteger()
        val candidateCommitOrder = mutableListOf<Long>()
        val matchCommitOrder = mutableListOf<Long>()
        private val tracks = (1L..6L).map { id ->
            Track(id, "Song $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file-$id.flac")
        }

        override suspend fun addedProviders(): List<StreamingProviderDescriptor> = listOf(
            StreamingProviderDescriptor(
                name = StreamingProviderName.NETEASE,
                displayName = "网易云音乐",
                capabilities = StreamingProviderCapabilities(
                    supportsSearch = true,
                    supportsPlayback = true
                )
            )
        )

        override fun tracks(): List<Track> = tracks

        override fun storedMatch(track: Track, provider: StreamingProviderName): String = ""

        override fun storedMatches(
            tracks: List<Track>,
            providers: List<StreamingProviderName>
        ): Map<Long, Map<StreamingProviderName, String>> = tracks.associate { track ->
            track.id to providers.associateWith { "" }
        }

        override suspend fun search(
            provider: StreamingProviderName,
            query: String
        ): List<StreamingTrack> {
            val id = requireNotNull(Regex("Song (\\d+)").find(query)).groupValues[1].toLong()
            val active = activeSearches.incrementAndGet()
            maxConcurrentSearches.updateAndGet { previous -> maxOf(previous, active) }
            if (active >= 2) firstPairStarted.complete(Unit)
            return try {
                firstPairStarted.await()
                delay(7L - id)
                listOf(
                    StreamingTrack(
                        provider = provider,
                        providerTrackId = "netease-$id",
                        title = "Song $id",
                        artist = "Artist",
                        album = "Album",
                        durationMs = 180_000L
                    )
                )
            } finally {
                activeSearches.decrementAndGet()
            }
        }

        override fun saveCandidates(
            track: Track,
            provider: StreamingProviderName,
            candidates: List<StreamingTrack>
        ) {
            candidateCommitOrder += track.id
        }

        override fun saveMatch(
            track: Track,
            provider: StreamingProviderName,
            providerTrackId: String
        ) {
            matchCommitOrder += track.id
        }
    }
}
