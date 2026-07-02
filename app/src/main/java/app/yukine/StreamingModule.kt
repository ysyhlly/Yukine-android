package app.yukine

import android.content.Context
import app.yukine.data.MusicLibraryRepository
import app.yukine.streaming.StreamingPlaylistSyncStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object StreamingModule {
    @Provides
    @ActivityScoped
    fun provideStreamingPlaybackTaskScheduler(): StreamingPlaybackTaskScheduler =
        StreamingPlaybackTaskScheduler()

    @Provides
    @ActivityScoped
    fun provideResolveStreamingPlaybackUseCase(): ResolveStreamingPlaybackUseCase =
        ResolveStreamingPlaybackUseCase()

    @Provides
    @ActivityScoped
    fun provideStreamingTrackMatchUseCase(repository: MusicLibraryRepository): StreamingTrackMatchUseCase =
        StreamingTrackMatchUseCase(MusicLibraryStreamingTrackMatchOperations(repository))

    @Provides
    @ActivityScoped
    fun provideStreamingPlaylistSyncStore(@ApplicationContext context: Context): StreamingPlaylistSyncStore =
        StreamingPlaylistSyncStore(context)

    @Provides
    @ActivityScoped
    fun provideImportStreamingPlaylistUseCase(
        repository: MusicLibraryRepository,
        syncStore: StreamingPlaylistSyncStore
    ): ImportStreamingPlaylistUseCase =
        ImportStreamingPlaylistUseCase(MusicLibraryStreamingPlaylistImportOperations(repository, syncStore))

    @Provides
    @ActivityScoped
    fun provideSyncStreamingPlaylistUseCase(
        repository: MusicLibraryRepository,
        syncStore: StreamingPlaylistSyncStore
    ): SyncStreamingPlaylistUseCase =
        SyncStreamingPlaylistUseCase(MusicLibraryStreamingPlaylistSyncOperations(repository, syncStore))

    @Provides
    @ActivityScoped
    fun provideEnsureStreamingLoginPlaylistUseCase(
        repository: MusicLibraryRepository,
        syncStore: StreamingPlaylistSyncStore
    ): EnsureStreamingLoginPlaylistUseCase =
        EnsureStreamingLoginPlaylistUseCase(MusicLibraryStreamingLoginPlaylistOperations(repository, syncStore))

    @Provides
    @ActivityScoped
    fun provideGetStreamingPlaylistLinkUseCase(
        syncStore: StreamingPlaylistSyncStore
    ): GetStreamingPlaylistLinkUseCase =
        GetStreamingPlaylistLinkUseCase(StreamingPlaylistSyncStoreLinkOperations(syncStore))

    @Provides
    @ActivityScoped
    fun provideStreamingLocalPlaylistOperations(
        importStreamingPlaylistUseCase: ImportStreamingPlaylistUseCase,
        syncStreamingPlaylistUseCase: SyncStreamingPlaylistUseCase,
        ensureStreamingLoginPlaylistUseCase: EnsureStreamingLoginPlaylistUseCase,
        streamingPlaylistLinkUseCase: GetStreamingPlaylistLinkUseCase
    ): StreamingLocalPlaylistOperations =
        MainStreamingLocalPlaylistOperations(
            importStreamingPlaylistUseCase,
            syncStreamingPlaylistUseCase,
            ensureStreamingLoginPlaylistUseCase,
            streamingPlaylistLinkUseCase
        )

    @Provides
    @ActivityScoped
    fun provideMainHeartbeatRecommendationListenerFactory(): MainHeartbeatRecommendationListenerFactory =
        MainHeartbeatRecommendationListenerFactory {
                serviceAvailability,
                seedRequestProvider,
                modeStopper,
                queueAppender,
                playerSink,
                seedMissLogger,
                statusSink ->
            MainHeartbeatRecommendationListener(
                serviceAvailability,
                seedRequestProvider,
                modeStopper,
                queueAppender,
                playerSink,
                seedMissLogger,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainRecommendationActionCallbacksFactory(): MainRecommendationActionCallbacksFactory =
        MainRecommendationActionCallbacksFactory {
                statusSink,
                dailyPlayerSink,
                seedRequestProvider,
                heartbeatPlayerSink,
                seedMissLogger ->
            MainRecommendationActionCallbacks(
                statusSink,
                dailyPlayerSink,
                seedRequestProvider,
                heartbeatPlayerSink,
                seedMissLogger
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingPlaylistDialogListenerFactory(): MainStreamingPlaylistDialogListenerFactory =
        MainStreamingPlaylistDialogListenerFactory {
                statusSink,
                playlistImportRunner,
                accountPlaylistImportSink,
                likedTracksImportSink ->
            MainStreamingPlaylistDialogListener(
                statusSink,
                playlistImportRunner,
                accountPlaylistImportSink,
                likedTracksImportSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingPlaylistListenerFactory(): MainStreamingPlaylistListenerFactory =
        MainStreamingPlaylistListenerFactory {
                playlistIdSource,
                playlistIdSink,
                collectionsLoader,
                libraryRefreshSink,
                playlistNameSource,
                playlistTracksSource,
                favoriteTracksSource,
                selectedProviderSource,
                providerPickerPresenter,
                navigationSink,
                loadedDialogPresenter,
                accountPlaylistPickerPresenter,
                statusSink,
                selectedTabRenderer ->
            MainStreamingPlaylistListener(
                playlistIdSource,
                playlistIdSink,
                collectionsLoader,
                libraryRefreshSink,
                playlistNameSource,
                playlistTracksSource,
                favoriteTracksSource,
                selectedProviderSource,
                providerPickerPresenter,
                navigationSink,
                loadedDialogPresenter,
                accountPlaylistPickerPresenter,
                statusSink,
                selectedTabRenderer
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingPlaylistImportDialogListenerFactory(): MainStreamingPlaylistImportDialogListenerFactory =
        MainStreamingPlaylistImportDialogListenerFactory {
                selectedProviderSource,
                luoxueDialogPresenter,
                playlistLinkImportSink ->
            MainStreamingPlaylistImportDialogListener(
                selectedProviderSource,
                luoxueDialogPresenter,
                playlistLinkImportSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingManualCookieListenerFactory(): MainStreamingManualCookieListenerFactory =
        MainStreamingManualCookieListenerFactory {
                selectedProviderSource,
                dialogPresenter,
                loginSuccessHandler,
                statusSink ->
            MainStreamingManualCookieListener(
                selectedProviderSource,
                dialogPresenter,
                loginSuccessHandler,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingSearchActionHandlerFactory(): MainStreamingSearchActionHandlerFactory =
        MainStreamingSearchActionHandlerFactory { streamingViewModel, actionGateway ->
            DefaultStreamingSearchActionHandler(streamingViewModel, actionGateway)
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingActionGatewayFactory(): MainStreamingActionGatewayFactory =
        MainStreamingActionGatewayFactory {
                qualityProvider,
                languageModeProvider,
                authLauncher,
                trackPlayer,
                loginSuccessHandler,
                providerSelector,
                manualCookiePresenter ->
            MainStreamingActionGateway(
                qualityProvider,
                languageModeProvider,
                authLauncher,
                trackPlayer,
                loginSuccessHandler,
                providerSelector,
                manualCookiePresenter
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainStreamingSearchRenderListenerFactory(): MainStreamingSearchRenderListenerFactory =
        MainStreamingSearchRenderListenerFactory {
                networkNavigator,
                actionHandler,
                selectedProviderSource,
                playlistImporter,
                accountPlaylistSyncPicker,
                likedTracksImporter,
                recommendationActionRunner,
                playlistImportDialogPresenter,
                manualCookiePresenter,
                chromePublisher ->
            MainStreamingSearchRenderListener(
                networkNavigator,
                actionHandler,
                selectedProviderSource,
                playlistImporter,
                accountPlaylistSyncPicker,
                likedTracksImporter,
                recommendationActionRunner,
                playlistImportDialogPresenter,
                manualCookiePresenter,
                chromePublisher
            )
        }
}
