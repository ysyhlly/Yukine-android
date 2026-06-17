package app.yukine

import app.yukine.streaming.StreamingGatewayFactory
import app.yukine.streaming.StreamingPlaybackTrackAdapter
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingRepositoryProvider @Inject constructor(
    private val settingsStore: StreamingGatewaySettingsStore,
    private val cacheRepository: StreamingCacheRepository,
    private val gatewayFactory: StreamingGatewayFactory,
    private val playbackTrackAdapter: StreamingPlaybackTrackAdapter,
    private val cachePolicy: StreamingCachePolicy
) : StreamingRepositorySource {
    override fun current(): StreamingRepository {
        return StreamingRepository(
            gatewayFactory.remote(settingsStore.endpoint()),
            cacheRepository,
            playbackTrackAdapter,
            cachePolicy
        )
    }
}
