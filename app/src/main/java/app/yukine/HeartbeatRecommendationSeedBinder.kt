package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName

internal class HeartbeatRecommendationSeedBinder(
    private val serviceSnapshotProvider: () -> PlaybackStateSnapshot?,
    private val serviceQueueProvider: () -> List<Track>,
    private val storeSnapshotProvider: () -> PlaybackStateSnapshot?,
    private val viewModelQueueProvider: () -> List<Track>,
    private val libraryContextProvider: () -> List<Track>
) : HeartbeatSeedRequestProvider {
    private val lateBoundProvider = LateBoundHeartbeatSeedRequestProvider()

    fun bind(trackMatchStore: StreamingTrackMatchStore?) {
        lateBoundProvider.bind(
            trackMatchStore?.let {
                HeartbeatRecommendationSeedResolver(
                    it,
                    serviceSnapshotProvider,
                    serviceQueueProvider,
                    storeSnapshotProvider,
                    viewModelQueueProvider,
                    libraryContextProvider
                )
            }
        )
    }

    override fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
        return lateBoundProvider.request(provider)
    }
}
