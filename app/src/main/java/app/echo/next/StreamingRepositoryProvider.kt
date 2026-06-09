package app.echo.next

import app.echo.next.streaming.StreamingGatewayFactory
import app.echo.next.streaming.StreamingPlaybackTrackAdapter
import app.echo.next.streaming.StreamingRepository
import app.echo.next.streaming.cache.StreamingCachePolicy
import app.echo.next.streaming.cache.StreamingCacheRepository
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
