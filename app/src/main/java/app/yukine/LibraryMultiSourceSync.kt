package app.yukine

import android.util.Log
import app.yukine.data.MusicLibraryRepository
import app.yukine.identity.MusicIdentityDiagnostics
import app.yukine.model.Track
import app.yukine.identity.TrackArtistIdentity
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingAudioCapabilityPolicy
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal const val STREAMING_NO_SOURCE_MATCH = "__echo_no_source__"
private const val LX_NO_SOURCE_MATCH_V2 = "__echo_no_source_lx_v2__"
private const val NO_SOURCE_RECHECK_MS = 7L * 24L * 60L * 60L * 1_000L

internal fun isStreamingNoSourceMatch(value: String): Boolean =
    value == STREAMING_NO_SOURCE_MATCH ||
        value.startsWith("$STREAMING_NO_SOURCE_MATCH:") ||
        value == LX_NO_SOURCE_MATCH_V2 ||
        value.startsWith("$LX_NO_SOURCE_MATCH_V2:")

internal interface LibraryMultiSourceSyncOperations {
    suspend fun addedProviders(): List<StreamingProviderDescriptor>
    fun tracks(): List<Track>
    fun storedMatch(track: Track, provider: StreamingProviderName): String
    fun storedMatches(
        tracks: List<Track>,
        providers: List<StreamingProviderName>
    ): Map<Long, Map<StreamingProviderName, String>> = tracks.associate { track ->
        track.id to providers.associateWith { provider -> storedMatch(track, provider) }
    }
    fun saveMatch(track: Track, provider: StreamingProviderName, providerTrackId: String)
    fun saveCandidates(
        track: Track,
        provider: StreamingProviderName,
        candidates: List<StreamingTrack>
    ) = Unit
    suspend fun search(provider: StreamingProviderName, query: String): List<StreamingTrack>
    fun canonicalIdentity(track: Track): String? = null
    fun artistIdentities(track: Track): List<TrackArtistIdentity> = emptyList()
    fun refreshIdentitySnapshot(): Long = 0L
    fun ingestConfirmedSources(): Int = 0
}

internal class MusicLibraryMultiSourceSyncOperations(
    private val library: MusicLibraryRepository,
    private val repositorySource: StreamingRepositorySource,
    private val trackMatches: StreamingTrackMatchUseCase
) : LibraryMultiSourceSyncOperations {
    @Volatile
    private var mergeIdentitiesByTrack: Map<Long, String> = emptyMap()
    @Volatile
    private var mergeIdentitySnapshotLoaded = false
    @Volatile
    private var artistIdentitiesByTrack: Map<Long, List<TrackArtistIdentity>> = emptyMap()
    @Volatile
    private var artistIdentitySnapshotLoaded = false
    @Volatile
    private var identitySnapshotRevision = 0L
    private val identitySnapshotLock = Any()
    private fun repository(): StreamingRepository = repositorySource.current()

    override suspend fun addedProviders(): List<StreamingProviderDescriptor> = repository()
        .providers()
        .filter(::isAddedPlaybackProvider)

    override fun tracks(): List<Track> {
        ensureIdentitySnapshotLoaded()
        return library.loadCachedTracks()
    }

    override fun canonicalIdentity(track: Track): String? {
        ensureIdentitySnapshotLoaded()
        return mergeIdentitiesByTrack[track.id]?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun artistIdentities(track: Track): List<TrackArtistIdentity> {
        ensureIdentitySnapshotLoaded()
        return artistIdentitiesByTrack[track.id].orEmpty()
    }

    override fun storedMatch(track: Track, provider: StreamingProviderName): String {
        val saved = library.loadStreamingTrackMatch(track, provider.wireName).trim()
        if (provider == StreamingProviderName.LUOXUE && StoredStreamingSourceMatchCodec.isEncoded(saved)) {
            return saved
        }
        val direct = trackMatches.directProviderTrackId(track, provider).trim()
        return direct.ifBlank { saved }
    }

    override fun storedMatches(
        tracks: List<Track>,
        providers: List<StreamingProviderName>
    ): Map<Long, Map<StreamingProviderName, String>> {
        val persisted = library.loadStreamingTrackMatches(tracks, providers.map { it.wireName })
        return tracks.associate { track ->
            track.id to providers.associateWith { provider ->
                val saved = persisted[track.id]?.get(provider.wireName).orEmpty().trim()
                if (provider == StreamingProviderName.LUOXUE &&
                    StoredStreamingSourceMatchCodec.isEncoded(saved)
                ) {
                    saved
                } else {
                    trackMatches.directProviderTrackId(track, provider).trim().ifBlank { saved }
                }
            }
        }
    }

    override fun saveMatch(track: Track, provider: StreamingProviderName, providerTrackId: String) {
        if (StoredStreamingSourceMatchCodec.isEncoded(providerTrackId)) {
            library.saveStructuredStreamingTrackMatch(track, provider.wireName, providerTrackId)
        } else {
            library.saveStreamingTrackMatch(track, provider.wireName, providerTrackId)
        }
    }

    override fun saveCandidates(
        track: Track,
        provider: StreamingProviderName,
        candidates: List<StreamingTrack>
    ) {
        library.saveStreamingTrackCandidates(track, provider.wireName, candidates)
    }

    override suspend fun search(provider: StreamingProviderName, query: String): List<StreamingTrack> =
        repository().search(
            provider = provider,
            query = query,
            mediaTypes = setOf(StreamingMediaType.TRACK),
            pageSize = 12
        ).let { result ->
            result.error?.let { error ->
                throw IllegalStateException(error.message)
            }
            result.tracks
        }

    override fun refreshIdentitySnapshot(): Long = synchronized(identitySnapshotLock) {
        mergeIdentitiesByTrack = library.loadTrackMergeIdentities()
        artistIdentitiesByTrack = library.loadTrackArtistIdentities()
        mergeIdentitySnapshotLoaded = true
        artistIdentitySnapshotLoaded = true
        identitySnapshotRevision += 1L
        identitySnapshotRevision
    }

    override fun ingestConfirmedSources(): Int = library.ingestConfirmedIdentitySources()

    private fun ensureIdentitySnapshotLoaded() {
        if (mergeIdentitySnapshotLoaded && artistIdentitySnapshotLoaded) return
        synchronized(identitySnapshotLock) {
            if (mergeIdentitySnapshotLoaded && artistIdentitySnapshotLoaded) return
            mergeIdentitiesByTrack = library.loadTrackMergeIdentities()
            artistIdentitiesByTrack = library.loadTrackArtistIdentities()
            mergeIdentitySnapshotLoaded = true
            artistIdentitySnapshotLoaded = true
            identitySnapshotRevision += 1L
        }
    }
}

internal data class LibraryMultiSourceSyncResult(
    val providerCount: Int,
    val checkedCount: Int,
    val matchedCount: Int,
    val unavailableCount: Int,
    val mergedRecordingCount: Int = 0
)

/**
 * Keeps cross-platform matches outside the song table. A missing platform is persisted as a
 * match status only, while positive matches are exposed as ephemeral playback candidates.
 */
internal class LibraryMultiSourceSyncCoordinator(
    private val operations: LibraryMultiSourceSyncOperations,
    private val diagnostics: MusicIdentityDiagnostics = MusicIdentityDiagnostics.process(),
    maxConcurrentSearches: Int = DEFAULT_MAX_CONCURRENT_SEARCHES,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private val maxConcurrentSearches = maxConcurrentSearches.coerceAtLeast(1)
    private val providersByName = ConcurrentHashMap<StreamingProviderName, StreamingProviderDescriptor>()
    private val matchesByTrack =
        ConcurrentHashMap<String, Map<StreamingProviderName, StoredStreamingSourceMatch>>()

    suspend fun refreshKnownMatches(): Int {
        val providers = loadProviders()
        val tracks = operations.tracks()
        val storedMatches: MutableMap<Long, MutableMap<StreamingProviderName, String>> =
            operations.storedMatches(tracks, providers.map { it.name })
            .mapValues { (_, matches) -> matches.toMutableMap() }
            .toMutableMap()
        matchesByTrack.clear()
        tracks.forEach { track ->
            providers.forEach { descriptor ->
                val stored = storedMatches[track.id]?.get(descriptor.name).orEmpty()
                StoredStreamingSourceMatchCodec.upgradedEncoding(stored)?.let { upgraded ->
                    operations.saveMatch(track, descriptor.name, upgraded)
                    storedMatches[track.id]?.set(descriptor.name, upgraded)
                }
            }
            refreshTrackCache(track, providers, storedMatches[track.id].orEmpty())
        }
        return providers.size
    }

    suspend fun syncIncremental(): LibraryMultiSourceSyncResult {
        val totalStartedAt = diagnostics.startNanos()
        val snapshotStartedAt = diagnostics.startNanos()
        val providers = loadProviders()
        val tracks = operations.tracks()
        val storedMatches: MutableMap<Long, MutableMap<StreamingProviderName, String>> =
            operations.storedMatches(tracks, providers.map { it.name })
            .mapValues { (_, matches) -> matches.toMutableMap() }
            .toMutableMap()
        diagnostics.recordElapsed(
            DIAGNOSTIC_OPERATION,
            MusicIdentityDiagnostics.Stage.SNAPSHOT_LOAD,
            snapshotStartedAt,
            tracks.size.toLong() + providers.size.toLong()
        )
        matchesByTrack.clear()
        val pending = ArrayList<PendingProviderSearch>()
        var order = 0
        tracks.forEach { track ->
            providers.forEach { descriptor ->
                val provider = descriptor.name
                val stored = storedMatches[track.id]?.get(provider).orEmpty().trim()
                if (!matchNeedsRefresh(provider, stored)) return@forEach
                val normalizationStartedAt = diagnostics.startNanos()
                pending += PendingProviderSearch(
                    order = order++,
                    track = track,
                    provider = provider,
                    query = searchQuery(provider, track)
                )
                diagnostics.recordElapsed(
                    DIAGNOSTIC_OPERATION,
                    MusicIdentityDiagnostics.Stage.NORMALIZATION,
                    normalizationStartedAt,
                    1L
                )
            }
        }
        val resolved = resolvePendingSearches(pending)
        var matched = 0
        var unavailable = 0
        resolved.forEach { item ->
            val rankedCandidates = item.rankedCandidates ?: return@forEach
            val track = item.pending.track
            val provider = item.pending.provider
            val commitStartedAt = diagnostics.startNanos()
            operations.saveCandidates(track, provider, rankedCandidates)
            val storedValue = storedMatchValue(provider, track, rankedCandidates)
            val valueToStore = if (storedValue.isBlank()) {
                unavailable++
                noSourceMatchValue(provider)
            } else {
                matched++
                storedValue
            }
            operations.saveMatch(track, provider, valueToStore)
            storedMatches.getOrPut(track.id) { mutableMapOf() }[provider] = valueToStore
            diagnostics.recordElapsed(
                DIAGNOSTIC_OPERATION,
                MusicIdentityDiagnostics.Stage.DATABASE_COMMIT,
                commitStartedAt,
                rankedCandidates.size.toLong() + 1L
            )
        }
        tracks.forEach { track ->
            val publishStartedAt = diagnostics.startNanos()
            refreshTrackCache(track, providers, storedMatches[track.id].orEmpty())
            diagnostics.recordElapsed(
                DIAGNOSTIC_OPERATION,
                MusicIdentityDiagnostics.Stage.CACHE_PUBLISH,
                publishStartedAt,
                1L
            )
        }
        val mergedRecordingCount = operations.ingestConfirmedSources()
        if (matched > 0 || mergedRecordingCount > 0) {
            val publishStartedAt = diagnostics.startNanos()
            operations.refreshIdentitySnapshot()
            diagnostics.recordElapsed(
                DIAGNOSTIC_OPERATION,
                MusicIdentityDiagnostics.Stage.CACHE_PUBLISH,
                publishStartedAt,
                tracks.size.toLong()
            )
        }
        diagnostics.recordElapsed(
            DIAGNOSTIC_OPERATION,
            MusicIdentityDiagnostics.Stage.TOTAL,
            totalStartedAt,
            pending.size.toLong()
        )
        logDiagnostics()
        return LibraryMultiSourceSyncResult(
            providers.size,
            pending.size,
            matched,
            unavailable,
            mergedRecordingCount
        )
    }

    /**
     * Network search and pure ranking use a small worker pool. Persistence stays outside this
     * method so Room writes remain deterministic and never race each other.
     */
    private suspend fun resolvePendingSearches(
        pending: List<PendingProviderSearch>
    ): List<ResolvedProviderSearch> = coroutineScope {
        if (pending.isEmpty()) return@coroutineScope emptyList()
        val nextIndex = AtomicInteger(0)
        val workerCount = minOf(maxConcurrentSearches, pending.size)
        List(workerCount) {
            async {
                val results = ArrayList<ResolvedProviderSearch>()
                while (true) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= pending.size) break
                    results += resolvePendingSearch(pending[index])
                }
                results
            }
        }.awaitAll()
            .flatten()
            .sortedBy { it.pending.order }
    }

    private suspend fun resolvePendingSearch(pending: PendingProviderSearch): ResolvedProviderSearch {
        val sourceFetchStartedAt = diagnostics.startNanos()
        val result = try {
            operations.search(pending.provider, pending.query)
        } catch (cancellation: CancellationException) {
            diagnostics.recordElapsed(
                DIAGNOSTIC_OPERATION,
                MusicIdentityDiagnostics.Stage.SOURCE_FETCH,
                sourceFetchStartedAt,
                0L
            )
            throw cancellation
        } catch (_: Exception) {
            null
        }
        diagnostics.recordElapsed(
            DIAGNOSTIC_OPERATION,
            MusicIdentityDiagnostics.Stage.SOURCE_FETCH,
            sourceFetchStartedAt,
            result?.size?.toLong() ?: 0L
        )
        result ?: return ResolvedProviderSearch(pending, null)
        val candidateGenerationStartedAt = diagnostics.startNanos()
        val playableCandidates = playableCandidates(result)
        diagnostics.recordElapsed(
            DIAGNOSTIC_OPERATION,
            MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION,
            candidateGenerationStartedAt,
            playableCandidates.size.toLong()
        )
        val scoringStartedAt = diagnostics.startNanos()
        val rankedCandidates = rankedPlayableCandidates(pending.track, playableCandidates)
        diagnostics.recordElapsed(
            DIAGNOSTIC_OPERATION,
            MusicIdentityDiagnostics.Stage.SCORING,
            scoringStartedAt,
            rankedCandidates.size.toLong()
        )
        return ResolvedProviderSearch(pending, rankedCandidates)
    }

    fun candidatesFor(track: Track?): List<Track> {
        val selected = track ?: return emptyList()
        val matches = matchesByTrack[trackKey(selected)].orEmpty()
        if (matches.isEmpty()) return emptyList()
        val choices = matches.flatMap { (provider, match) ->
            match.orderedCandidates().map { candidate -> provider to candidate }
        }
        val playbackCandidates = choices.map { (provider, candidate) ->
            StreamingPlaybackCandidate(
                provider = provider,
                providerTrackId = candidate.providerTrackId,
                label = sourceChoiceLabel(provider, candidate),
                available = true,
                luoxueMusicInfoJson = candidate.luoxueMusicInfoJson
            )
        }
        return choices.map { (provider, candidate) ->
            StreamingPlaybackAdapter.placeholderTrack(
                StreamingTrack(
                    provider = provider,
                    providerTrackId = candidate.providerTrackId,
                    title = candidate.title.ifBlank { selected.title },
                    artist = candidate.artist.ifBlank { selected.artist },
                    album = candidate.album.ifBlank { selected.album },
                    durationMs = candidate.durationMs ?: selected.durationMs,
                    coverUrl = candidate.coverUrl ?: selected.albumArtUri?.toString(),
                    coverThumbUrl = candidate.coverUrl ?: selected.albumArtUri?.toString(),
                    playbackCandidates = playbackCandidates,
                    luoxueMusicInfoJson = candidate.luoxueMusicInfoJson,
                    isrc = candidate.isrc
                )
            )
        }
    }

    /** Persisted recording-only identity for the library display hot path. */
    fun persistedMergeIdentityFor(track: Track?): String? {
        val selected = track ?: return null
        return operations.canonicalIdentity(selected)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "recording:$it" }
    }

    fun artistIdentitiesFor(track: Track?): List<TrackArtistIdentity> =
        track?.let(operations::artistIdentities).orEmpty()

    /** Reloads Room-backed canonical/artist lookup maps without contacting streaming providers. */
    fun refreshIdentitySnapshot() {
        operations.refreshIdentitySnapshot()
    }

    private suspend fun loadProviders(): List<StreamingProviderDescriptor> {
        val providers = runCatching { operations.addedProviders() }
            .getOrDefault(emptyList())
            .filter(::isAddedPlaybackProvider)
            .distinctBy { it.name }
        providersByName.clear()
        providers.forEach { providersByName[it.name] = it }
        return providers
    }

    private fun refreshTrackCache(
        track: Track,
        providers: List<StreamingProviderDescriptor>,
        storedMatches: Map<StreamingProviderName, String>
    ) {
        val trackKey = trackKey(track)
        val matches = matchesByTrack[trackKey].orEmpty().toMutableMap()
        providers.forEach { descriptor ->
            storedMatches[descriptor.name]
                .orEmpty()
                .trim()
                .takeIf { it.isNotBlank() && !isStreamingNoSourceMatch(it) }
                ?.let(StoredStreamingSourceMatchCodec::decode)
                ?.let { decoded ->
                    val existing = matches[descriptor.name]
                    if (existing == null || decoded.candidates.size > existing.candidates.size) {
                        matches[descriptor.name] = decoded
                    }
                }
        }
        if (matches.isEmpty()) {
            matchesByTrack.remove(trackKey)
        } else {
            matchesByTrack[trackKey] = matches
        }
    }

    private fun bestReliableMatch(track: Track, candidates: List<StreamingTrack>): StreamingTrack? {
        val playableCandidates = candidates.filter { it.playable && it.providerTrackId.isNotBlank() }
        return StreamingTrackMatchPolicy.pickReliableCandidate(track, playableCandidates)
    }

    private fun storedMatchValue(
        provider: StreamingProviderName,
        track: Track,
        candidates: List<StreamingTrack>
    ): String {
        val playable = candidates
            .filter { it.playable && it.providerTrackId.isNotBlank() }
            .distinctBy { it.providerTrackId.trim() }
        if (provider == StreamingProviderName.LUOXUE) {
            val primary = playable.firstOrNull() ?: return ""
            return StoredStreamingSourceMatchCodec.encode(primary, playable)
        }
        return bestReliableMatch(track, playable)?.providerTrackId.orEmpty()
    }

    private fun rankedPlayableCandidates(
        track: Track,
        candidates: List<StreamingTrack>
    ): List<StreamingTrack> = StreamingTrackMatchPolicy.rankCandidates(
        StreamingTrackMatchPolicy.reference(track),
        candidates
    ).map { it.track }

    private fun playableCandidates(candidates: List<StreamingTrack>): List<StreamingTrack> = candidates
        .filter { it.playable && it.providerTrackId.isNotBlank() }
        .distinctBy { it.providerTrackId.trim() }

    private fun logDiagnostics() {
        runCatching {
            Log.d(
                DIAGNOSTIC_TAG,
                "$DIAGNOSTIC_OPERATION ${diagnostics.snapshot(DIAGNOSTIC_OPERATION).compactSummary()}"
            )
        }
    }

    private fun searchQuery(provider: StreamingProviderName, track: Track): String =
        if (provider == StreamingProviderName.LUOXUE) {
            StreamingTrackMatchPolicy.titleSearchQuery(track)
        } else {
            StreamingTrackMatchPolicy.searchQuery(track)
        }

    private fun sourceChoiceLabel(
        provider: StreamingProviderName,
        candidate: StoredStreamingSourceCandidate
    ): String {
        val providerLabel = providersByName[provider]?.displayName ?: provider.wireName
        if (provider != StreamingProviderName.LUOXUE) return providerLabel
        val sourceKey = candidate.providerTrackId
            .substringBefore(':', "")
            .trim()
            .uppercase()
            .takeIf { it.isNotBlank() }
        return listOfNotNull(
            providerLabel,
            sourceKey,
            candidate.title.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" · ")
    }

    private fun trackKey(track: Track): String = listOf(
        StreamingTrackMatchPolicy.canonicalTitle(track.title),
        StreamingTrackMatchPolicy.canonicalArtistKey(listOf(track.artist)),
        (track.durationMs / 1_000L).toString()
    ).joinToString("|")

    private fun negativeMatchNeedsRefresh(value: String): Boolean {
        if (!isStreamingNoSourceMatch(value)) return false
        val checkedAt = value.substringAfterLast(':', "").toLongOrNull() ?: return true
        return clockMs() - checkedAt >= NO_SOURCE_RECHECK_MS
    }

    private fun matchNeedsRefresh(provider: StreamingProviderName, stored: String): Boolean {
        if (stored.isBlank()) return true
        if (isStreamingNoSourceMatch(stored)) {
            if (provider == StreamingProviderName.LUOXUE && !stored.startsWith(LX_NO_SOURCE_MATCH_V2)) {
                return true
            }
            return negativeMatchNeedsRefresh(stored)
        }
        return provider == StreamingProviderName.LUOXUE &&
            !StoredStreamingSourceMatchCodec.isEncoded(stored)
    }

    private fun noSourceMatchValue(provider: StreamingProviderName): String {
        val marker = if (provider == StreamingProviderName.LUOXUE) {
            LX_NO_SOURCE_MATCH_V2
        } else {
            STREAMING_NO_SOURCE_MATCH
        }
        return "$marker:${clockMs()}"
    }

    private companion object {
        const val DIAGNOSTIC_TAG = "IdentityDiagnostics"
        const val DEFAULT_MAX_CONCURRENT_SEARCHES = 4
        val DIAGNOSTIC_OPERATION = MusicIdentityDiagnostics.Operation.PLATFORM_SYNC
    }

    private data class PendingProviderSearch(
        val order: Int,
        val track: Track,
        val provider: StreamingProviderName,
        val query: String
    )

    private data class ResolvedProviderSearch(
        val pending: PendingProviderSearch,
        val rankedCandidates: List<StreamingTrack>?
    )

    private fun StoredStreamingSourceMatch.orderedCandidates(): List<StoredStreamingSourceCandidate> {
        val primary = candidates.firstOrNull { it.providerTrackId == primaryProviderTrackId }
            ?: StoredStreamingSourceCandidate(primaryProviderTrackId)
        return (listOf(primary) + candidates)
            .filter { it.providerTrackId.isNotBlank() }
            .distinctBy(StoredStreamingSourceCandidate::providerTrackId)
    }
}

internal fun isAddedPlaybackProvider(descriptor: StreamingProviderDescriptor): Boolean =
    descriptor.enabled &&
        descriptor.status != StreamingProviderStatus.DISABLED &&
        descriptor.capabilities.supportsSearch &&
        StreamingAudioCapabilityPolicy.canResolve(descriptor)
