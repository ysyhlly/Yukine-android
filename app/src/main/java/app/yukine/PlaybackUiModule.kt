package app.yukine

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object PlaybackUiModule {
    @Provides
    @ActivityScoped
    fun provideMainPlaybackStoreFactory(): MainPlaybackStoreFactory =
        MainPlaybackStoreFactory { viewModel -> MainPlaybackStore(viewModel) }

    @Provides
    @ActivityScoped
    fun provideMainNowPlayingGatewayFactory(): MainNowPlayingGatewayFactory =
        MainNowPlayingGatewayFactory { playbackActions, playbackStore, favoriteToggler, seekHandler, statusText ->
            MainNowPlayingGateway(playbackActions, playbackStore, favoriteToggler, seekHandler, statusText)
        }

    @Provides
    @ActivityScoped
    fun provideNowPlayingPlaybackServiceStarter(
        @ActivityContext context: Context
    ): NowPlayingPlaybackServiceStarter =
        NowPlayingPlaybackServiceStarter(context)

    @Provides
    @ActivityScoped
    fun provideMainNowPlayingPlaybackGatewayFactory(
        serviceStarter: NowPlayingPlaybackServiceStarter
    ): MainNowPlayingPlaybackGatewayFactory {
        return MainNowPlayingPlaybackGatewayFactory(serviceStarter::startPlaybackService)
    }

    @Provides
    @ActivityScoped
    fun provideMainNowPlayingStateListenerFactory(): MainNowPlayingStateListenerFactory =
        MainNowPlayingStateListenerFactory {
                storesReadySource,
                playbackSnapshotSource,
                favoriteIdsSource,
                lyricsStateSource,
                languageModeSource,
                queueVisibilitySource,
                floatingLyricsSink,
                queueInputsSyncer ->
            MainNowPlayingStateListener(
                storesReadySource,
                playbackSnapshotSource,
                favoriteIdsSource,
                lyricsStateSource,
                languageModeSource,
                queueVisibilitySource,
                floatingLyricsSink,
                queueInputsSyncer
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainPlaybackActionListenerFactory(): MainPlaybackActionListenerFactory =
        MainPlaybackActionListenerFactory { streamingResolver, snapshotSource, fallbackTracksSource, resultSink ->
            MainPlaybackActionListener(streamingResolver, snapshotSource, fallbackTracksSource, resultSink)
        }

    @Provides
    @ActivityScoped
    fun provideMainQueueActionListenerFactory(): MainQueueActionListenerFactory =
        MainQueueActionListenerFactory {
                resultApplier,
                serviceAvailability,
                trackMoveSink,
                clearQueueConfirmer,
                emptyStatusProvider,
                statusSink ->
            MainQueueActionListener(
                resultApplier,
                serviceAvailability,
                trackMoveSink,
                clearQueueConfirmer,
                emptyStatusProvider,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingPlaybackListenerFactory(): MainStreamingPlaybackListenerFactory =
        MainStreamingPlaybackListenerFactory {
                languageProvider,
                adaptiveQualityProvider,
                selectedQualityProvider,
                queueSnapshotSource,
                heartbeatAppendHandler,
                resultSink,
                statusSink ->
            MainStreamingPlaybackListener(
                languageProvider,
                adaptiveQualityProvider,
                selectedQualityProvider,
                queueSnapshotSource,
                heartbeatAppendHandler,
                resultSink,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainPlaybackStartListenerFactory(): MainPlaybackStartListenerFactory =
        MainPlaybackStartListenerFactory {
                heartbeatStopper,
                serviceStarter,
                serviceAvailability,
                resolvingStatusProvider,
                statusSink,
                queueOpener ->
            MainPlaybackStartListener(
                heartbeatStopper,
                serviceStarter,
                serviceAvailability,
                resolvingStatusProvider,
                statusSink,
                queueOpener
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainPlaybackStateEventListenerFactory(): MainPlaybackStateEventListenerFactory =
        MainPlaybackStateEventListenerFactory {
                selectedTabSource,
                queueVisibilitySource,
                currentLyricsTrackIdSource,
                playbackSettingsSaver,
                lyricsLoader,
                collectionsLoader,
                nowBarRenderer,
                homeDashboardPlaybackUpdater,
                selectedTabRenderer,
                nowPlayingContentUpdater,
                nextStreamingTrackPreResolver,
                streamingBufferingRecoveryHandler,
                currentStreamingTrackResolver,
                statusSink ->
            MainPlaybackStateEventListener(
                selectedTabSource,
                queueVisibilitySource,
                currentLyricsTrackIdSource,
                playbackSettingsSaver,
                lyricsLoader,
                collectionsLoader,
                nowBarRenderer,
                homeDashboardPlaybackUpdater,
                selectedTabRenderer,
                nowPlayingContentUpdater,
                nextStreamingTrackPreResolver,
                streamingBufferingRecoveryHandler,
                currentStreamingTrackResolver,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainPlaybackServiceHostFactory(): MainPlaybackServiceHostFactory =
        MainPlaybackServiceHostFactory {
                playbackSpeedSource,
                appVolumeSource,
                concurrentPlaybackSource,
                statusBarLyricsSource,
                systemMediaLyricsTitleSource,
                playbackRestoreSource,
                replayGainSource,
                playbackServiceAttacher,
                playbackServiceClearer,
                playbackStoreResetter,
                pendingTracksPlayer,
                selectedTabRenderer,
                nowBarRenderer ->
            MainPlaybackServiceHost(
                playbackSpeedSource,
                appVolumeSource,
                concurrentPlaybackSource,
                statusBarLyricsSource,
                systemMediaLyricsTitleSource,
                playbackRestoreSource,
                replayGainSource,
                playbackServiceAttacher,
                playbackServiceClearer,
                playbackStoreResetter,
                pendingTracksPlayer,
                selectedTabRenderer,
                nowBarRenderer
            )
        }
}
