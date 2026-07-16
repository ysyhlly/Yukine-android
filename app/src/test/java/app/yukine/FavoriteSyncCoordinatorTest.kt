package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteSyncCoordinatorTest {
    @Test
    fun localFavoriteUsesCanonicalRecordingUuidAsUnifiedIdentity() = runTest {
        val canonicalId = "123e4567-e89b-12d3-a456-426614174000"
        val fixture = fixture(capabilities = emptyList(), canonicalId = canonicalId)

        fixture.coordinator.onLocalFavoriteChanged(fixture.library.track, true)

        assertEquals("recording:$canonicalId", fixture.repository.state.value.favorites.single().unifiedId)
    }

    @Test
    fun neteaseIncrementPropagatesToLocalAndQq() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.library.isFavorite(1L))
        assertEquals(listOf(QQ to "qq-1"), fixture.providers.added)
        assertEquals(FavoriteSyncStatus.SYNCED, fixture.mapping(QQ)?.status)
    }

    @Test
    fun noCorrespondingSourceKeepsLocalFavoriteAndRecordsNoSource() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        )

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.library.isFavorite(1L))
        assertEquals(FavoriteSyncStatus.NO_SOURCE, fixture.mapping(QQ)?.status)
        assertTrue(fixture.providers.added.isEmpty())
    }

    @Test
    fun loggedOutTargetIsAuthRequiredWithoutBlockingLocalMerge() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ).copy(loggedIn = false, authorized = false)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1")))
        )

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.library.isFavorite(1L))
        assertEquals(FavoriteSyncStatus.AUTH_REQUIRED, fixture.mapping(QQ)?.status)
        assertTrue(fixture.providers.added.isEmpty())
    }

    @Test
    fun readOnlyTargetIsRecordedSeparately() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ).copy(canAddFavorite = false)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        )

        fixture.coordinator.syncIncremental()

        assertEquals(FavoriteSyncStatus.READ_ONLY, fixture.mapping(QQ)?.status)
        assertTrue(fixture.providers.added.isEmpty())
    }

    @Test
    fun repeatedIncrementIsIdempotent() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )

        fixture.coordinator.syncIncremental()
        fixture.coordinator.syncIncremental()

        assertEquals(1, fixture.library.importCount)
        assertEquals(1, fixture.providers.added.count { it.first == QQ })
        assertEquals(1, fixture.repository.state.value.favorites.size)
    }

    @Test
    fun largeRemoteLibraryIsMergedCompletelyInOneSync() = runTest {
        val tracks = (1..40).map { index -> remoteTrack(NETEASE, "netease-$index") }.toMutableList()
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to tracks)
        )

        fixture.coordinator.syncIncremental()

        assertEquals(40, fixture.library.importCount)
        assertEquals(40, fixture.repository.state.value.cursors.single().seenProviderTrackIds.size)
    }

    @Test
    fun oneSyncWritesEveryMatchedFavoriteWithoutProviderBudgetDeferral() = runTest {
        val neteaseTracks = (1..20).map { index ->
            remoteTrack(NETEASE, "netease-$index").copy(
                title = "歌曲 $index",
                isrc = "TEST-ISRC-$index"
            )
        }.toMutableList()
        val qqTracks = (1..20).map { index ->
            remoteTrack(QQ, "qq-$index").copy(
                title = "歌曲 $index",
                isrc = "TEST-ISRC-$index"
            )
        }
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to neteaseTracks),
            search = mutableMapOf(QQ to qqTracks),
            confirmedProviderMatchesByRecording = (1..20).associate { index ->
                (100L + index to QQ) to "qq-$index"
            },
            uniqueImports = true
        )

        fixture.coordinator.syncIncremental()

        assertEquals(20, fixture.library.importCount)
        assertEquals(20, fixture.providers.added.size)
        assertTrue(fixture.repository.state.value.operations.all { it.status == FavoriteSyncStatus.SYNCED })
    }

    @Test
    fun confirmedFavoriteBatchUsesBoundedParallelProviderWrites() = runTest {
        val count = 8
        val neteaseTracks = (1..count).map { index ->
            remoteTrack(NETEASE, "netease-$index").copy(
                title = "歌曲 $index",
                isrc = "BATCH-ISRC-$index"
            )
        }.toMutableList()
        val qqTracks = (1..count).map { index ->
            remoteTrack(QQ, "qq-$index").copy(
                title = "歌曲 $index",
                isrc = "BATCH-ISRC-$index"
            )
        }
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to neteaseTracks),
            search = mutableMapOf(QQ to qqTracks),
            confirmedProviderMatchesByRecording = (1..count).associate { index ->
                (100L + index to QQ) to "qq-$index"
            },
            uniqueImports = true,
            addDelayMs = 100L
        )

        fixture.coordinator.syncIncremental()

        assertEquals(count, fixture.providers.added.size)
        assertTrue(fixture.providers.maxConcurrentAdds.get() > 1)
        assertTrue(fixture.providers.maxConcurrentAdds.get() <= 4)
    }

    @Test
    fun confirmedCanonicalProviderSourceSkipsNetworkSearch() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-search"))),
            confirmedProviderMatches = mapOf(QQ to "qq-confirmed")
        )

        fixture.coordinator.syncIncremental()

        assertEquals(listOf(QQ to "qq-confirmed"), fixture.providers.added)
        assertEquals(0, fixture.providers.searchCalls[QQ] ?: 0)
    }

    @Test
    fun previouslySyncedLegacyMappingIsRevalidatedAgainstCanonicalSource() = runTest {
        val fixture = fixture(
            capabilities = listOf(target(QQ)),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-search")))
        )
        fixture.repository.update { state ->
            state.copy(
                mappings = listOf(
                    ProviderFavoriteMapping(
                        unifiedId = "legacy-favorite",
                        provider = QQ,
                        providerTrackId = "qq-legacy",
                        status = FavoriteSyncStatus.SYNCED,
                        confidence = 0.99f,
                        recordingId = 1L
                    )
                )
            )
        }

        fixture.coordinator.onLocalFavoriteChanged(fixture.library.track, true)

        assertTrue(fixture.providers.added.isEmpty())
        assertEquals(0, fixture.providers.searchCalls[QQ] ?: 0)
        assertEquals(FavoriteSyncStatus.NEEDS_CONFIRMATION, fixture.mapping(QQ)?.status)
        assertEquals("qq-legacy", fixture.repository.state.value.conflicts.single().candidateProviderTrackId)
        assertEquals(FavoriteRecordSyncState.NEEDS_CONFIRMATION, fixture.library.syncState(1L))
    }

    @Test
    fun searchAndLegacyMatchCannotBypassCanonicalConfirmation() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-isrc"))),
            storedMatches = mapOf(QQ to "qq-stale")
        )

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.providers.added.isEmpty())
        assertEquals(0, fixture.providers.searchCalls[QQ] ?: 0)
        assertEquals(FavoriteSyncStatus.NEEDS_CONFIRMATION, fixture.mapping(QQ)?.status)
        assertEquals("qq-stale", fixture.repository.state.value.conflicts.single().candidateProviderTrackId)
    }

    @Test
    fun targetAcknowledgementPreventsProviderLoop() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), source(QQ).copy(canAddFavorite = true, canRemoveFavorite = true)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )

        fixture.coordinator.syncIncremental()

        assertEquals(1, fixture.library.importCount)
        assertEquals(listOf(QQ to "qq-1"), fixture.providers.added)
        assertFalse(fixture.providers.added.any { it.first == NETEASE })
    }

    @Test
    fun explicitLocalRemovalPropagatesWhenEnabled() = runTest {
        val fixture = fixture(
            capabilities = listOf(target(QQ)),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )
        val local = fixture.library.track

        fixture.coordinator.onLocalFavoriteChanged(local, true)
        fixture.repository.update {
            it.copy(preferences = it.preferences.copy(propagateRemovals = true))
        }
        fixture.coordinator.onLocalFavoriteChanged(local, false)

        assertEquals(listOf(QQ to "qq-1"), fixture.providers.removed)
        assertEquals(false, fixture.repository.state.value.favorites.single().active)
        assertEquals(false, fixture.mapping(QQ)?.active)
    }

    @Test
    fun remoteRemovalPropagatesToLocalAndOtherProvider() = runTest {
        val remote = mutableMapOf(
            NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")),
            QQ to mutableListOf<StreamingTrack>()
        )
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), source(QQ).copy(canAddFavorite = true, canRemoveFavorite = true)),
            remote = remote,
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )
        fixture.coordinator.syncIncremental()
        remote.getValue(NETEASE).clear()

        fixture.coordinator.syncIncremental()

        assertFalse(fixture.library.isFavorite(1L))
        assertEquals(listOf(QQ to "qq-1"), fixture.providers.removed)
        assertEquals(false, fixture.mapping(NETEASE)?.active)
        assertEquals(false, fixture.mapping(QQ)?.active)
    }

    @Test
    fun providerFailurePreservesFavoritesAndUnsupportedTargetIsSkipped() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ), target(KUGOU)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(
                QQ to listOf(remoteTrack(QQ, "qq-1")),
                KUGOU to listOf(remoteTrack(KUGOU, "kugou-1"))
            ),
            confirmedProviderMatches = mapOf(
                QQ to "qq-1",
                KUGOU to "kugou-1"
            ),
            failingAdds = setOf(QQ)
        )
        fixture.library.addExistingFavorite()

        fixture.coordinator.syncIncremental()

        assertEquals(FavoriteSyncStatus.RETRYABLE_ERROR, fixture.mapping(QQ)?.status)
        assertNull(fixture.mapping(KUGOU))
        assertTrue(fixture.library.isFavorite(99L))
        assertTrue(fixture.library.isFavorite(1L))
        assertNotNull(fixture.repository.state.value.favorites.singleOrNull { it.localTrackId == 1L })
        assertEquals(FavoriteRecordSyncState.RETRY, fixture.library.syncState(1L))
    }

    @Test
    fun legacyCandidateNeverWritesEvenWhenConfirmationPreferenceIsDisabled() = runTest {
        val lowConfidence = remoteTrack(QQ, "qq-low").copy(
            artist = "Different Artist",
            album = "Different Album",
            durationMs = 240_000L,
            isrc = null
        )
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1"))),
            search = mutableMapOf(QQ to listOf(lowConfidence)),
            storedMatches = mapOf(QQ to "qq-low")
        )
        fixture.repository.update {
            it.copy(preferences = it.preferences.copy(confirmLowConfidence = false))
        }

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.providers.added.isEmpty())
        assertEquals(0, fixture.providers.searchCalls[QQ] ?: 0)
        assertEquals(FavoriteSyncStatus.NEEDS_CONFIRMATION, fixture.mapping(QQ)?.status)
        assertEquals(1, fixture.repository.state.value.conflicts.size)
        assertEquals(FavoriteRecordSyncState.NEEDS_CONFIRMATION, fixture.library.syncState(1L))
    }

    @Test
    fun unmatchedProviderFavoriteRemainsInPendingImportQueue() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "pending-1"))),
            failImportAttempts = 1
        )

        fixture.coordinator.syncIncremental()

        val pending = fixture.repository.state.value.pendingImports.single()
        assertEquals(NETEASE, pending.provider)
        assertEquals("pending-1", pending.providerTrackId)
        assertEquals(1, pending.attemptCount)
        assertTrue(fixture.coordinator.dashboard.value.pendingCount > 0)
    }

    @Test
    fun pendingProviderFavoriteRetriesBeforeCursorAdvances() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "retry-1"))),
            failImportAttempts = 1
        )

        fixture.coordinator.syncIncremental()
        assertEquals(1, fixture.repository.state.value.pendingImports.size)
        assertTrue(fixture.repository.state.value.cursors.isEmpty())

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.repository.state.value.pendingImports.isEmpty())
        assertEquals(1, fixture.library.importCount)
        assertTrue("retry-1" in fixture.repository.state.value.cursors.single().seenProviderTrackIds)
    }

    @Test
    fun localLuoxueFavoriteMapsToCanonicalRecordingAndPropagatesToWritableAccountProvider() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(LUOXUE), target(QQ)),
            search = mutableMapOf(QQ to listOf(remoteTrack(QQ, "qq-1"))),
            confirmedProviderMatches = mapOf(QQ to "qq-1")
        )
        val lxTrack = Track(
            1L,
            "夜空",
            "歌手",
            "专辑",
            180_000L,
            Uri.EMPTY,
            "streaming:luoxue:kw:lx-1"
        )

        fixture.coordinator.onLocalFavoriteChanged(lxTrack, true)

        assertEquals(1L, fixture.mapping(LUOXUE)?.recordingId)
        assertEquals("kw:lx-1", fixture.mapping(LUOXUE)?.providerTrackId)
        assertEquals(listOf(QQ to "qq-1"), fixture.providers.added)
    }

    @Test
    fun canonicalRekeyRewritesPendingOperationIdWithoutDuplication() = runTest {
        val fixture = fixture(capabilities = emptyList(), canonicalId = "stable-uuid")
        fixture.repository.update { state ->
            state.copy(
                favorites = listOf(
                    UnifiedFavorite(
                        unifiedId = "meta:legacy",
                        localTrackId = 1L,
                        title = "夜空",
                        artist = "歌手",
                        album = "专辑",
                        durationMs = 180_000L
                    )
                ),
                operations = listOf(
                    FavoriteSyncOperation(
                        operationId = "ADD:meta:legacy:qqmusic",
                        unifiedId = "meta:legacy",
                        action = FavoriteSyncAction.ADD,
                        targetProvider = QQ,
                        batchId = "legacy"
                    )
                )
            )
        }

        fixture.coordinator.onLocalFavoriteChanged(fixture.library.track, true)

        val operation = fixture.repository.state.value.operations.single()
        assertEquals(1L, operation.recordingId)
        assertEquals("ADD:recording-id:1:qqmusic", operation.operationId)
    }

    private fun fixture(
        capabilities: List<ProviderCapability>,
        remote: MutableMap<StreamingProviderName, MutableList<StreamingTrack>> = mutableMapOf(),
        search: MutableMap<StreamingProviderName, List<StreamingTrack>> = mutableMapOf(),
        failingAdds: Set<StreamingProviderName> = emptySet(),
        storedMatches: Map<StreamingProviderName, String> = emptyMap(),
        confirmedProviderMatches: Map<StreamingProviderName, String> = emptyMap(),
        confirmedProviderMatchesByRecording: Map<Pair<Long, StreamingProviderName>, String> = emptyMap(),
        canonicalId: String? = null,
        failImportAttempts: Int = 0,
        uniqueImports: Boolean = false,
        addDelayMs: Long = 0L
    ): Fixture {
        val repository = InMemoryFavoriteSyncRepository()
        val providers = FakeProviderAdapter(capabilities, remote, search, failingAdds, addDelayMs)
        val library = FakeLibrary(
            canonicalId,
            failImportAttempts,
            confirmedProviderMatches,
            confirmedProviderMatchesByRecording,
            uniqueImports
        )
        val matchOperations = FakeMatchOperations(storedMatches)
        val coordinator = FavoriteSyncCoordinator(
            repository = repository,
            providers = providers,
            library = library,
            trackMatches = StreamingTrackMatchUseCase(matchOperations),
            eventBus = FavoriteSyncEventBus(),
            clockMs = { 1_000L }
        )
        return Fixture(repository, providers, library, coordinator)
    }

    private data class Fixture(
        val repository: InMemoryFavoriteSyncRepository,
        val providers: FakeProviderAdapter,
        val library: FakeLibrary,
        val coordinator: FavoriteSyncCoordinator
    ) {
        fun mapping(provider: StreamingProviderName): ProviderFavoriteMapping? =
            repository.state.value.mappings.firstOrNull { it.provider == provider }
    }

    private class FakeProviderAdapter(
        private val providerCapabilities: List<ProviderCapability>,
        private val remote: MutableMap<StreamingProviderName, MutableList<StreamingTrack>>,
        private val searchResults: MutableMap<StreamingProviderName, List<StreamingTrack>>,
        private val failingAdds: Set<StreamingProviderName>,
        private val addDelayMs: Long
    ) : FavoriteProviderAdapter {
        val added: MutableList<Pair<StreamingProviderName, String>> =
            Collections.synchronizedList(mutableListOf())
        val removed: MutableList<Pair<StreamingProviderName, String>> =
            Collections.synchronizedList(mutableListOf())
        val searchCalls = mutableMapOf<StreamingProviderName, Int>()
        val maxConcurrentAdds = AtomicInteger()
        private val activeAdds = AtomicInteger()

        override suspend fun capabilities(): List<ProviderCapability> = providerCapabilities

        override suspend fun pullFavoriteDelta(
            provider: StreamingProviderName,
            cursor: FavoriteSyncCursor?
        ): FavoritePullDelta {
            val all = remote[provider].orEmpty()
            val unseen = all.filterNot { it.providerTrackId in cursor?.seenProviderTrackIds.orEmpty() }
            val observed = all.map { it.providerTrackId }.toSet()
            return FavoritePullDelta(
                unseen,
                "cursor-${all.size}",
                observed,
                cursor?.seenProviderTrackIds.orEmpty() - observed
            )
        }

        override suspend fun addFavorite(provider: StreamingProviderName, providerTrackId: String) {
            val concurrent = activeAdds.incrementAndGet()
            maxConcurrentAdds.updateAndGet { current -> maxOf(current, concurrent) }
            try {
                if (addDelayMs > 0L) delay(addDelayMs)
                if (provider in failingAdds) throw IOException("temporary $provider failure")
                added += provider to providerTrackId
                val matched = searchResults[provider].orEmpty().firstOrNull { it.providerTrackId == providerTrackId }
                if (matched != null) synchronized(remote) {
                    if (remote.getOrPut(provider) { mutableListOf() }.none { it.providerTrackId == providerTrackId }) {
                        remote.getValue(provider) += matched
                    }
                }
            } finally {
                activeAdds.decrementAndGet()
            }
        }

        override suspend fun removeFavorite(provider: StreamingProviderName, providerTrackId: String) {
            removed += provider to providerTrackId
            remote[provider]?.removeAll { it.providerTrackId == providerTrackId }
        }

        override suspend fun search(provider: StreamingProviderName, track: UnifiedFavorite): List<StreamingTrack> {
            searchCalls[provider] = (searchCalls[provider] ?: 0) + 1
            return searchResults[provider].orEmpty()
        }
    }

    private class FakeLibrary(
        private val canonicalId: String? = null,
        failImportAttempts: Int = 0,
        private val confirmedProviderMatches: Map<StreamingProviderName, String> = emptyMap(),
        private val confirmedProviderMatchesByRecording: Map<Pair<Long, StreamingProviderName>, String> = emptyMap(),
        private val uniqueImports: Boolean = false
    ) : UnifiedFavoriteLibrary {
        val track = Track(1L, "夜空", "歌手", "专辑", 180_000L, Uri.EMPTY, "source.flac")
        private val tracks = linkedMapOf<Long, Track>(track.id to track)
        private val favorites = linkedSetOf<Long>()
        private val syncStates = mutableMapOf<Long, String>()
        var importCount = 0
            private set
        private var remainingImportFailures = failImportAttempts

        @Synchronized
        override fun importExternalFavorite(track: StreamingTrack): Track {
            if (remainingImportFailures > 0) {
                remainingImportFailures--
                throw IOException("temporary import failure")
            }
            importCount++
            if (!uniqueImports) {
                favorites += this.track.id
                return this.track
            }
            val imported = Track(
                100L + importCount,
                track.title,
                track.artist,
                track.album.orEmpty(),
                track.durationMs ?: 0L,
                Uri.EMPTY,
                "streaming:${track.provider.wireName}:${track.providerTrackId}"
            )
            tracks[imported.id] = imported
            favorites += imported.id
            return imported
        }

        @Synchronized
        override fun localTrack(localTrackId: Long): Track? = tracks[localTrackId]

        @Synchronized
        override fun setFavorite(track: Track, favorite: Boolean) {
            if (favorite) favorites += track.id else favorites -= track.id
        }

        @Synchronized
        override fun isFavorite(localTrackId: Long): Boolean = localTrackId in favorites

        override fun canonicalId(localTrackId: Long): String? = canonicalId

        @Synchronized
        override fun recordingId(localTrackId: Long): Long = if (localTrackId in tracks) localTrackId else 0L

        override fun confirmedProviderTrackId(recordingId: Long, provider: StreamingProviderName): String =
            confirmedProviderMatchesByRecording[recordingId to provider]
                ?: confirmedProviderMatches[provider].orEmpty()

        override fun confirmDirectProviderSource(
            localTrackId: Long,
            provider: StreamingProviderName,
            providerTrackId: String
        ): Boolean = localTrackId in tracks && providerTrackId.isNotBlank()

        @Synchronized
        override fun favoriteTracks(): List<Track> = tracks.values.filter { it.id in favorites }

        @Synchronized
        override fun updateFavoriteSyncState(recordingId: Long, syncState: String) {
            syncStates[recordingId] = syncState
        }

        fun syncState(recordingId: Long): String? = syncStates[recordingId]

        fun addExistingFavorite() {
            val existing = Track(99L, "已收藏", "歌手", "专辑", 200_000L, Uri.EMPTY, "existing.flac")
            tracks[existing.id] = existing
            favorites += existing.id
        }
    }

    private class FakeMatchOperations(storedMatches: Map<StreamingProviderName, String>) : StreamingTrackMatchOperations {
        private val matches = storedMatches.mapKeys { (provider, _) -> "1:${provider.wireName}" }.toMutableMap()

        override fun loadStreamingTrackMatch(track: Track, provider: String): String =
            matches["${track.id}:$provider"].orEmpty()

        override fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String) {
            matches["${track.id}:$provider"] = providerTrackId
        }
    }

    private fun source(provider: StreamingProviderName) = ProviderCapability(
        provider = provider,
        displayName = provider.wireName,
        enabled = true,
        loggedIn = true,
        canPullFavorites = true,
        canAddFavorite = false,
        canRemoveFavorite = false
    )

    private fun target(provider: StreamingProviderName) = ProviderCapability(
        provider = provider,
        displayName = provider.wireName,
        enabled = true,
        loggedIn = true,
        canPullFavorites = false,
        canAddFavorite = true,
        canRemoveFavorite = true
    )

    private fun remoteTrack(provider: StreamingProviderName, id: String) = StreamingTrack(
        provider = provider,
        providerTrackId = id,
        title = "夜空",
        artist = "歌手",
        album = "专辑",
        durationMs = 180_500L,
        isrc = "JPABC1234567"
    )

    private companion object {
        val NETEASE = StreamingProviderName.NETEASE
        val QQ = StreamingProviderName.QQ_MUSIC
        val KUGOU = StreamingProviderName.KUGOU
        val LUOXUE = StreamingProviderName.LUOXUE
    }
}
