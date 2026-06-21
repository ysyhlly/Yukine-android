package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName

internal class StreamingTrackMatchStoreBindings(
    private val useCase: StreamingTrackMatchUseCase
) : StreamingTrackMatchStore {
    override fun directProviderTrackId(track: Track, provider: StreamingProviderName): String =
        useCase.directProviderTrackId(track, provider)

    override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String =
        useCase.providerTrackIdFor(track, provider)

    override fun saveProviderTrackId(
        track: Track,
        provider: StreamingProviderName,
        providerTrackId: String
    ) {
        useCase.saveProviderTrackId(track, provider, providerTrackId)
    }

    override fun providerTrackIdFromCandidates(
        candidates: List<Track?>?,
        provider: StreamingProviderName?
    ): String =
        useCase.providerTrackIdFromCandidates(candidates, provider)

    override fun heartbeatSeedCandidates(
        serviceSnapshot: PlaybackStateSnapshot?,
        serviceQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?,
        viewModelQueue: List<Track?>?
    ): List<Track> =
        useCase.heartbeatSeedCandidates(serviceSnapshot, serviceQueue, storeSnapshot, viewModelQueue)

    override fun snapshotQueueForHeartbeat(
        serviceQueue: List<Track?>?,
        viewModelQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?
    ): List<Track> =
        useCase.snapshotQueueForHeartbeat(serviceQueue, viewModelQueue, storeSnapshot)

    override fun heartbeatSeedMissMessage(
        provider: StreamingProviderName?,
        snapshot: PlaybackStateSnapshot?,
        storeSnapshot: PlaybackStateSnapshot?,
        queue: List<Track?>?
    ): String =
        useCase.heartbeatSeedMissMessage(provider, snapshot, storeSnapshot, queue)
}
