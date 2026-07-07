package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import kotlin.random.Random

private const val HEARTBEAT_SEED_SAMPLE_SIZE = 12
private const val HEARTBEAT_QUEUE_CONTEXT_LIMIT = 48
private const val HEARTBEAT_LIBRARY_CONTEXT_LIMIT = 16
private const val HEARTBEAT_CURRENT_WINDOW_RADIUS = 6

internal fun interface HeartbeatSeedRequestProvider {
    fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest
}

internal class HeartbeatRecommendationSeedResolver(
    private val trackMatchStore: StreamingTrackMatchStore,
    private val serviceSnapshotProvider: (() -> PlaybackStateSnapshot?)? = null,
    private val serviceQueueProvider: (() -> List<Track>)? = null,
    private val storeSnapshotProvider: (() -> PlaybackStateSnapshot?)? = null,
    private val viewModelQueueProvider: (() -> List<Track>)? = null,
    private val libraryContextProvider: (() -> List<Track>)? = null
) : HeartbeatSeedRequestProvider {
    private var heartbeatSeedCursor = 0

    override fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
        return request(
            provider,
            serviceSnapshotProvider?.invoke(),
            serviceQueueProvider?.invoke(),
            storeSnapshotProvider?.invoke(),
            viewModelQueueProvider?.invoke(),
            libraryContextProvider?.invoke()
        )
    }

    fun request(
        provider: StreamingProviderName,
        serviceSnapshot: PlaybackStateSnapshot?,
        serviceQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?,
        viewModelQueue: List<Track?>?,
        libraryContextTracks: List<Track?>? = null
    ): HeartbeatRecommendationSeedRequest {
        val boundedServiceQueue = boundedSeedQueue(serviceQueue, serviceSnapshot)
        val boundedViewModelQueue = boundedSeedQueue(viewModelQueue, storeSnapshot)
        val boundedLibraryContext = libraryContextTracks?.take(HEARTBEAT_LIBRARY_CONTEXT_LIMIT)
        val mergedServiceQueue = mergeQueues(boundedServiceQueue, boundedLibraryContext)
        val candidates = randomSeedCandidates(
            trackMatchStore.heartbeatSeedCandidates(
                serviceSnapshot,
                mergedServiceQueue,
                storeSnapshot,
                boundedViewModelQueue
            )
        )
        val seedTrackId = trackMatchStore.providerTrackIdFromCandidates(candidates, provider).trim()
        val queue = trackMatchStore.snapshotQueueForHeartbeat(
            mergedServiceQueue,
            boundedViewModelQueue,
            storeSnapshot
        )
        val diagnosticSnapshot = serviceSnapshot ?: storeSnapshot
        val seedMissingMessage = trackMatchStore.heartbeatSeedMissMessage(
            provider,
            diagnosticSnapshot,
            storeSnapshot,
            queue
        )
        return HeartbeatRecommendationSeedRequest(
            candidates = candidates,
            seedTrackId = seedTrackId,
            playlistId = seedTrackId,
            seedMissingMessage = seedMissingMessage
        )
    }

    private fun mergeQueues(
        primaryQueue: List<Track?>?,
        contextTracks: List<Track?>?
    ): List<Track?>? {
        if (contextTracks.isNullOrEmpty()) {
            return primaryQueue
        }
        if (primaryQueue.isNullOrEmpty()) {
            return contextTracks
        }
        return contextTracks + primaryQueue
    }

    private fun boundedSeedQueue(
        queue: List<Track?>?,
        snapshot: PlaybackStateSnapshot?
    ): List<Track?>? {
        if (queue.isNullOrEmpty() || queue.size <= HEARTBEAT_QUEUE_CONTEXT_LIMIT) {
            return queue
        }
        val bounded = ArrayList<Track?>(HEARTBEAT_QUEUE_CONTEXT_LIMIT)
        val seenIndexes = HashSet<Int>()
        fun addIndex(index: Int) {
            if (index in queue.indices &&
                bounded.size < HEARTBEAT_QUEUE_CONTEXT_LIMIT &&
                seenIndexes.add(index)
            ) {
                bounded += queue[index]
            }
        }
        val currentIndex = snapshot?.currentIndex ?: -1
        if (currentIndex in queue.indices) {
            val start = (currentIndex - HEARTBEAT_CURRENT_WINDOW_RADIUS).coerceAtLeast(0)
            val end = (currentIndex + HEARTBEAT_CURRENT_WINDOW_RADIUS).coerceAtMost(queue.lastIndex)
            for (index in start..end) {
                addIndex(index)
            }
        }
        var index = 0
        while (bounded.size < HEARTBEAT_QUEUE_CONTEXT_LIMIT && index < queue.size) {
            addIndex(index)
            index += 1
        }
        return bounded
    }

    private fun randomSeedCandidates(candidates: List<Track>): List<Track> {
        if (candidates.size <= 1) {
            return candidates
        }
        val cursor = heartbeatSeedCursor
        heartbeatSeedCursor = if (heartbeatSeedCursor == Int.MAX_VALUE) 0 else heartbeatSeedCursor + 1
        val entropy = System.nanoTime() xor System.currentTimeMillis() xor cursor.toLong()
        val random = Random(entropy)
        val anchorIndex = random.nextInt(candidates.size)
        val anchor = candidates[anchorIndex]
        val rest = candidates
            .filterIndexed { index, _ -> index != anchorIndex }
            .shuffled(random)
            .take((HEARTBEAT_SEED_SAMPLE_SIZE - 1).coerceAtLeast(0))
        return listOf(anchor) + rest
    }
}

internal class LateBoundHeartbeatSeedRequestProvider : HeartbeatSeedRequestProvider {
    private var delegate: HeartbeatSeedRequestProvider? = null

    fun bind(provider: HeartbeatSeedRequestProvider?) {
        delegate = provider
    }

    override fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
        return delegate?.request(provider) ?: HeartbeatRecommendationSeedRequest()
    }
}
