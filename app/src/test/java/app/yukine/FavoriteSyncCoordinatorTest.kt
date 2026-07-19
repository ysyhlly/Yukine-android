package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun remoteFavoritesMergeLocallyWithoutAnyProviderWrite() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), target(QQ)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        )

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.library.isFavorite(1L))
        assertEquals(1, fixture.library.importCount)
        assertEquals(NETEASE, fixture.repository.state.value.mappings.single().provider)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun loginTriggeredSyncPullsOnlyTheAuthenticatedProvider() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), source(QQ)),
            remote = mutableMapOf(
                NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")),
                QQ to mutableListOf(remoteTrack(QQ, "qq-1"))
            ),
            uniqueImports = true
        )

        fixture.coordinator.syncIncremental(QQ)

        assertEquals(setOf(QQ), fixture.repository.state.value.mappings.map { it.provider }.toSet())
        assertEquals(1, fixture.library.importCount)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun repeatedSnapshotIsIdempotent() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        )

        fixture.coordinator.syncIncremental()
        fixture.coordinator.syncIncremental()

        assertEquals(1, fixture.library.importCount)
        assertEquals(1, fixture.repository.state.value.favorites.size)
        assertEquals(1, fixture.repository.state.value.mappings.size)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun largeRemoteLibraryIsMergedCompletelyInOneSync() = runTest {
        val tracks = (1..40).map { remoteTrack(NETEASE, "netease-$it") }.toMutableList()
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to tracks),
            uniqueImports = true
        )

        fixture.coordinator.syncIncremental()

        assertEquals(40, fixture.library.importCount)
        assertEquals(40, fixture.repository.state.value.cursors.single().seenProviderTrackIds.size)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun unionRemovesLocalOnlyAfterEverySourceIsMissingTwice() = runTest {
        val remote = mutableMapOf(
            NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")),
            QQ to mutableListOf(remoteTrack(QQ, "qq-1"))
        )
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), source(QQ)),
            remote = remote
        )
        fixture.coordinator.syncIncremental()
        assertEquals(2, fixture.repository.state.value.mappings.size)

        remote.getValue(NETEASE).clear()
        fixture.coordinator.syncIncremental()
        fixture.coordinator.syncIncremental()

        assertFalse(fixture.mapping(NETEASE)!!.active)
        assertTrue(fixture.mapping(QQ)!!.active)
        assertTrue(fixture.library.isFavorite(1L))

        remote.getValue(QQ).clear()
        fixture.coordinator.syncIncremental()
        assertTrue(fixture.library.isFavorite(1L))
        fixture.coordinator.syncIncremental()

        assertFalse(fixture.mapping(QQ)!!.active)
        assertFalse(fixture.library.isFavorite(1L))
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun localOwnedFavoriteSurvivesAllRemoteSourcesDisappearing() = runTest {
        val remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        val fixture = fixture(listOf(source(NETEASE)), remote)
        fixture.coordinator.syncIncremental()
        fixture.coordinator.onLocalFavoriteChanged(fixture.library.track, true)

        remote.getValue(NETEASE).clear()
        fixture.coordinator.syncIncremental()
        fixture.coordinator.syncIncremental()

        assertFalse(fixture.mapping(NETEASE)!!.active)
        assertTrue(fixture.repository.state.value.favorites.single().localOwned)
        assertTrue(fixture.library.isFavorite(1L))
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun localRemovalIsRestoredWhenRemoteSourceStillContainsTrack() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE)),
            remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        )
        fixture.coordinator.syncIncremental()
        fixture.library.setFavorite(fixture.library.track, false)
        fixture.coordinator.onLocalFavoriteChanged(fixture.library.track, false)
        assertFalse(fixture.library.isFavorite(1L))

        fixture.coordinator.syncIncremental()

        assertTrue(fixture.library.isFavorite(1L))
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun firstSnapshotIsOnlyABaselineAndCannotDeleteLegacyMapping() = runTest {
        val capability = source(NETEASE)
        val fixture = fixture(listOf(capability))
        fixture.library.setFavorite(fixture.library.track, true)
        fixture.repository.update {
            it.copy(
                favorites = listOf(unified(localOwned = false)),
                mappings = listOf(mapping(capability, "netease-legacy"))
            )
        }

        fixture.coordinator.syncIncremental()

        val mapping = fixture.mapping(NETEASE)!!
        assertTrue(mapping.active)
        assertEquals(0, mapping.consecutiveMissing)
        assertTrue(fixture.library.isFavorite(1L))
    }

    @Test
    fun missingStableAccountIdAllowsAdditionsButNeverDeletion() = runTest {
        val capability = source(NETEASE, accountId = "")
        val remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        val fixture = fixture(listOf(capability), remote)
        fixture.coordinator.syncIncremental()

        remote.getValue(NETEASE).clear()
        repeat(3) { fixture.coordinator.syncIncremental() }

        assertTrue(fixture.mapping(NETEASE)!!.active)
        assertTrue(fixture.library.isFavorite(1L))
    }

    @Test
    fun accountSwitchFreezesPreviousAccountMembership() = runTest {
        val remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        val fixture = fixture(listOf(source(NETEASE, "account-a")), remote)
        fixture.coordinator.syncIncremental()

        fixture.providers.providerCapabilities = listOf(source(NETEASE, "account-b"))
        remote.getValue(NETEASE).clear()
        repeat(3) { fixture.coordinator.syncIncremental() }

        val oldMapping = fixture.repository.state.value.mappings.single { it.accountId == "account-a" }
        assertTrue(oldMapping.active)
        assertTrue(fixture.library.isFavorite(1L))
    }

    @Test
    fun incompleteSnapshotCannotIncreaseMissingCount() = runTest {
        val remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        val fixture = fixture(listOf(source(NETEASE)), remote)
        fixture.coordinator.syncIncremental()
        remote.getValue(NETEASE).clear()
        fixture.providers.completeSnapshots[NETEASE] = false

        repeat(3) { fixture.coordinator.syncIncremental() }

        assertEquals(0, fixture.mapping(NETEASE)!!.consecutiveMissing)
        assertTrue(fixture.library.isFavorite(1L))
    }

    @Test
    fun providerFailureDoesNotBecomeAnEmptySnapshot() = runTest {
        val remote = mutableMapOf(NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1")))
        val fixture = fixture(listOf(source(NETEASE)), remote)
        fixture.coordinator.syncIncremental()
        remote.getValue(NETEASE).clear()
        fixture.providers.failingPulls += NETEASE

        repeat(3) { fixture.coordinator.syncIncremental() }

        assertEquals(0, fixture.mapping(NETEASE)!!.consecutiveMissing)
        assertTrue(fixture.library.isFavorite(1L))
        assertTrue(fixture.coordinator.dashboard.value.failureCount > 0)
    }

    @Test
    fun uncertainCrossProviderTracksRemainSeparateFavorites() = runTest {
        val fixture = fixture(
            capabilities = listOf(source(NETEASE), source(QQ)),
            remote = mutableMapOf(
                NETEASE to mutableListOf(remoteTrack(NETEASE, "netease-1").copy(isrc = null)),
                QQ to mutableListOf(
                    remoteTrack(QQ, "qq-1").copy(
                        artist = "另一位歌手",
                        durationMs = 230_000L,
                        isrc = null
                    )
                )
            ),
            uniqueImports = true
        )

        fixture.coordinator.syncIncremental()

        assertEquals(2, fixture.repository.state.value.favorites.size)
        assertEquals(2, fixture.repository.state.value.mappings.size)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun pendingRemoteImportRetriesBeforeCursorAdvances() = runTest {
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
    fun legacyPendingWriteOperationIsPreservedButNeverRetried() = runTest {
        val fixture = fixture(listOf(target(QQ)))
        fixture.repository.update {
            it.copy(
                favorites = listOf(unified(localOwned = true)),
                operations = listOf(
                    FavoriteSyncOperation(
                        operationId = "ADD:recording-id:1:qqmusic",
                        unifiedId = "recording-id:1",
                        action = FavoriteSyncAction.ADD,
                        targetProvider = QQ,
                        batchId = "legacy",
                        recordingId = 1L
                    )
                )
            )
        }

        fixture.coordinator.syncIncremental()

        assertEquals(FavoriteSyncStatus.PENDING, fixture.repository.state.value.operations.single().status)
        fixture.assertNoRemoteWrites()
    }

    @Test
    fun headlessRunnerCloseDoesNotUnbindForegroundFavoriteEvents() {
        val eventBus = FavoriteSyncEventBus()
        var received = 0
        eventBus.bind { _, _ -> received++ }
        val fixture = fixture(capabilities = emptyList(), eventBus = eventBus)

        fixture.coordinator.close()
        eventBus.publish(fixture.library.track, true)

        assertEquals(1, received)
    }

    @Test
    fun canonicalReconcilerKeepsMembershipsFromDifferentSources() {
        val fixture = fixture(capabilities = emptyList(), canonicalId = "current-uuid")
        fixture.repository.update { state ->
            state.copy(
                favorites = listOf(unified().copy(unifiedId = "recording:stale", recordingId = 99L)),
                mappings = listOf(
                    mapping(source(NETEASE, "account-a"), "netease-1")
                        .copy(unifiedId = "recording:stale", recordingId = 99L),
                    mapping(source(NETEASE, "account-b"), "netease-1")
                        .copy(unifiedId = "recording:stale", recordingId = 99L)
                )
            )
        }

        val reconciler = FavoriteSyncCanonicalReconciler(fixture.repository, fixture.library)

        assertEquals(1, reconciler.reconcile())
        assertEquals(2, fixture.repository.state.value.mappings.size)
        assertEquals(setOf("account-a", "account-b"), fixture.repository.state.value.mappings.map { it.accountId }.toSet())
    }

    private fun fixture(
        capabilities: List<ProviderCapability>,
        remote: MutableMap<StreamingProviderName, MutableList<StreamingTrack>> = mutableMapOf(),
        canonicalId: String? = null,
        failImportAttempts: Int = 0,
        uniqueImports: Boolean = false,
        eventBus: FavoriteSyncEventBus = FavoriteSyncEventBus()
    ): Fixture {
        val repository = InMemoryFavoriteSyncRepository()
        val providers = FakeProviderAdapter(capabilities, remote)
        val library = FakeLibrary(canonicalId, failImportAttempts, uniqueImports)
        val coordinator = FavoriteSyncCoordinator(
            repository = repository,
            providers = providers,
            library = library,
            trackMatches = StreamingTrackMatchUseCase(FakeMatchOperations()),
            eventBus = eventBus,
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

        fun assertNoRemoteWrites() {
            assertTrue(providers.added.isEmpty())
            assertTrue(providers.removed.isEmpty())
        }
    }

    private class FakeProviderAdapter(
        var providerCapabilities: List<ProviderCapability>,
        private val remote: MutableMap<StreamingProviderName, MutableList<StreamingTrack>>
    ) : FavoriteProviderAdapter {
        val added = mutableListOf<Pair<StreamingProviderName, String>>()
        val removed = mutableListOf<Pair<StreamingProviderName, String>>()
        val completeSnapshots = mutableMapOf<StreamingProviderName, Boolean>()
        val failingPulls = mutableSetOf<StreamingProviderName>()

        override suspend fun capabilities(): List<ProviderCapability> = providerCapabilities

        override suspend fun pullFavoriteDelta(
            provider: StreamingProviderName,
            cursor: FavoriteSyncCursor?
        ): FavoritePullDelta {
            if (provider in failingPulls) throw IOException("temporary $provider failure")
            val all = remote[provider].orEmpty()
            val observed = all.map { it.providerTrackId }.toSet()
            return FavoritePullDelta(
                added = all.filterNot { it.providerTrackId in cursor?.seenProviderTrackIds.orEmpty() },
                cursor = "cursor-${all.size}",
                observedProviderTrackIds = observed,
                removedProviderTrackIds = cursor?.seenProviderTrackIds.orEmpty() - observed,
                completeSnapshot = completeSnapshots[provider] ?: true
            )
        }

        override suspend fun addFavorite(provider: StreamingProviderName, providerTrackId: String) {
            added += provider to providerTrackId
        }

        override suspend fun removeFavorite(provider: StreamingProviderName, providerTrackId: String) {
            removed += provider to providerTrackId
        }

        override suspend fun search(
            provider: StreamingProviderName,
            track: UnifiedFavorite
        ): List<StreamingTrack> = emptyList()
    }

    private class FakeLibrary(
        private val canonicalId: String? = null,
        failImportAttempts: Int = 0,
        private val uniqueImports: Boolean = false
    ) : UnifiedFavoriteLibrary {
        val track = Track(1L, "夜空", "歌手", "专辑", 180_000L, Uri.EMPTY, "source.flac")
        private val tracks = linkedMapOf<Long, Track>(track.id to track)
        private val favorites = linkedSetOf<Long>()
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

        override fun confirmDirectProviderSource(
            localTrackId: Long,
            provider: StreamingProviderName,
            providerTrackId: String
        ): Boolean = localTrackId in tracks && providerTrackId.isNotBlank()

        @Synchronized
        override fun favoriteTracks(): List<Track> = tracks.values.filter { it.id in favorites }
    }

    private class FakeMatchOperations : StreamingTrackMatchOperations {
        override fun loadStreamingTrackMatch(track: Track, provider: String): String = ""
        override fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String) = Unit
    }

    private fun source(
        provider: StreamingProviderName,
        accountId: String = "${provider.wireName}-account"
    ) = ProviderCapability(
        provider = provider,
        displayName = provider.wireName,
        enabled = true,
        loggedIn = true,
        canPullFavorites = true,
        canAddFavorite = false,
        canRemoveFavorite = false,
        accountId = accountId
    )

    private fun target(provider: StreamingProviderName) = ProviderCapability(
        provider = provider,
        displayName = provider.wireName,
        enabled = true,
        loggedIn = true,
        canPullFavorites = false,
        canAddFavorite = true,
        canRemoveFavorite = true,
        accountId = "${provider.wireName}-account"
    )

    private fun unified(localOwned: Boolean = false) = UnifiedFavorite(
        unifiedId = "recording-id:1",
        localTrackId = 1L,
        title = "夜空",
        artist = "歌手",
        album = "专辑",
        durationMs = 180_000L,
        recordingId = 1L,
        localOwned = localOwned
    )

    private fun mapping(
        capability: ProviderCapability,
        providerTrackId: String
    ) = ProviderFavoriteMapping(
        unifiedId = "recording-id:1",
        provider = capability.provider,
        providerTrackId = providerTrackId,
        status = FavoriteSyncStatus.SYNCED,
        confidence = 1f,
        recordingId = 1L,
        sourceKey = favoriteSourceKey(capability.provider, capability.accountId, "liked"),
        accountId = capability.accountId
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
    }
}
