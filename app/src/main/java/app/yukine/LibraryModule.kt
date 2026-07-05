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
internal object LibraryModule {
    @Provides
    @ActivityScoped
    fun provideLoadPlaylistTracksUseCase(repository: MusicLibraryRepository): LoadPlaylistTracksUseCase =
        LoadPlaylistTracksUseCase(MusicLibraryPlaylistTrackOperations(repository))

    @Provides
    @ActivityScoped
    fun provideLibrarySearchUseCase(repository: MusicLibraryRepository): LibrarySearchUseCase =
        LibrarySearchUseCase(MusicLibrarySearchOperations(repository))

    @Provides
    @ActivityScoped
    fun provideArtistInfoRepository(): ArtistInfoRepository = ArtistInfoRepository()

    @Provides
    @ActivityScoped
    fun provideNetworkActionUseCases(repository: MusicLibraryRepository): NetworkActionUseCases {
        val webDavSourceOperations = MusicLibraryWebDavSourceOperations(repository)
        val networkLibraryOperations = MusicLibraryNetworkLibraryOperations(repository)
        return NetworkActionUseCases(
            TestWebDavSourceUseCase(webDavSourceOperations),
            SyncWebDavSourceUseCase(webDavSourceOperations),
            SyncAllWebDavSourcesUseCase(webDavSourceOperations),
            AddStreamUrlUseCase(networkLibraryOperations),
            UpdateStreamUrlUseCase(networkLibraryOperations),
            ImportStreamPlaylistUseCase(networkLibraryOperations),
            DeleteAllStreamsUseCase(networkLibraryOperations),
            DeleteNetworkTrackUseCase(networkLibraryOperations),
            DeleteNetworkTracksUseCase(networkLibraryOperations),
            DeleteRemoteSourceUseCase(networkLibraryOperations),
            SaveWebDavSourceUseCase(networkLibraryOperations)
        )
    }

    @Provides
    @ActivityScoped
    fun provideMainLibraryStoreFactory(searchUseCase: LibrarySearchUseCase): MainLibraryStoreFactory =
        MainLibraryStoreFactory { viewModel -> MainLibraryStore(searchUseCase, viewModel) }

    @Provides
    @ActivityScoped
    fun provideMainPlayHistoryActionControllerFactory(): MainPlayHistoryActionControllerFactory =
        MainPlayHistoryActionControllerFactory {
                viewModel,
                languageModeProvider,
                libraryStateStore,
                statusSink,
                collectionsReloadAction ->
            PlayHistoryActionController(
                viewModel,
                languageModeProvider,
                libraryStateStore,
                statusSink,
                collectionsReloadAction
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainNetworkActionsListenerFactory(): MainNetworkActionsListenerFactory =
        MainNetworkActionsListenerFactory {
                nowPlayingViewModel,
                libraryReplacementSink,
                networkNavigator,
                collectionsReloader,
                statusSink ->
            MainNetworkActionsListener(
                nowPlayingViewModel,
                libraryReplacementSink,
                networkNavigator,
                collectionsReloader,
                statusSink
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainLibraryGatewayFactory(): MainLibraryGatewayFactory =
        MainLibraryGatewayFactory {
                trackListPlayer,
                languageModeProvider,
                statusSink,
                favoriteApplier,
                nowBarRenderer,
                selectedTabRenderer,
                collectionsLoader,
                playlistAdder,
                routeActionsProvider,
                searchApplier,
                audioImporter,
                libraryScanner ->
            MainLibraryGateway(
                trackListPlayer,
                languageModeProvider,
                statusSink,
                favoriteApplier,
                nowBarRenderer,
                selectedTabRenderer,
                collectionsLoader,
                playlistAdder,
                routeActionsProvider,
                searchApplier,
                audioImporter,
                libraryScanner
            )
        }

    @Provides
    @ActivityScoped
    fun provideLibraryCollectionGateway(repository: MusicLibraryRepository): LibraryCollectionGateway =
        MainLibraryCollectionGateway(MusicLibraryCollectionOperations(repository))

    @Provides
    @ActivityScoped
    fun provideLibraryImportGateway(repository: MusicLibraryRepository): LibraryImportGateway =
        MainLibraryImportGateway(MusicLibraryImportOperations(repository))

    @Provides
    @ActivityScoped
    fun provideLibraryDocumentGateway(
        @ApplicationContext context: Context,
        repository: MusicLibraryRepository
    ): LibraryDocumentGateway =
        ContentResolverLibraryDocumentGateway(
            context.contentResolver,
            MusicLibraryImportOperations(repository)
        )

    @Provides
    @ActivityScoped
    fun provideLibraryPlaylistActionGateway(
        repository: MusicLibraryRepository,
        syncStore: StreamingPlaylistSyncStore
    ): LibraryPlaylistActionGateway =
        MainLibraryPlaylistActionGateway(MusicLibraryPlaylistActionOperations(repository, syncStore))

    @Provides
    @ActivityScoped
    fun provideMainTrackListRenderListenerFactory(): MainTrackListRenderListenerFactory =
        MainTrackListRenderListenerFactory {
                trackListPlayer,
                favoriteToggler,
                playlistAdder,
                trackDownloader,
                tracksDownloader,
                streamEditor,
                trackDeleteConfirmer,
                chromePublisher ->
            MainTrackListRenderListener(
                trackListPlayer,
                favoriteToggler,
                playlistAdder,
                trackDownloader,
                tracksDownloader,
                streamEditor,
                trackDeleteConfirmer,
                chromePublisher
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainLibraryGroupsRenderListenerFactory(): MainLibraryGroupsRenderListenerFactory =
        MainLibraryGroupsRenderListenerFactory {
                groupOpener,
                groupSelectionClearer,
                groupCloser,
                languageModeProvider,
                trackListPlayer,
                groupDeleteConfirmer,
                chromePublisher,
                trackListRenderer ->
            MainLibraryGroupsRenderListener(
                groupOpener,
                groupSelectionClearer,
                groupCloser,
                languageModeProvider,
                trackListPlayer,
                groupDeleteConfirmer,
                chromePublisher,
                trackListRenderer
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainCollectionsRenderListenerFactory(): MainCollectionsRenderListenerFactory =
        MainCollectionsRenderListenerFactory {
                playlistCreator,
                playlistM3uPicker,
                playHistoryClearConfirmer,
                backRequester,
                trackListPlayer,
                favoriteToggler,
                playlistAdder,
                trackDownloader,
                tracksDownloader,
                playlistSelector,
                playlistRenamer,
                playlistDeleteConfirmer,
                selectedPlaylistIdSource,
                selectedPlaylistTracksSource,
                selectedPlaylistNameSource,
                statusKeySink,
                playlistExportDocumentOpener,
                selectedPlaylistStreamingImporter,
                favoritesStreamingImporter,
                streamingFavoritesImporter,
                selectedPlaylistStreamingSyncer,
                selectedPlaylistTrackMover,
                selectedPlaylistTrackRemover ->
            MainCollectionsRenderListener(
                playlistCreator,
                playlistM3uPicker,
                playHistoryClearConfirmer,
                backRequester,
                trackListPlayer,
                favoriteToggler,
                playlistAdder,
                trackDownloader,
                tracksDownloader,
                playlistSelector,
                playlistRenamer,
                playlistDeleteConfirmer,
                selectedPlaylistIdSource,
                selectedPlaylistTracksSource,
                selectedPlaylistNameSource,
                statusKeySink,
                playlistExportDocumentOpener,
                selectedPlaylistStreamingImporter,
                favoritesStreamingImporter,
                streamingFavoritesImporter,
                selectedPlaylistStreamingSyncer,
                selectedPlaylistTrackMover,
                selectedPlaylistTrackRemover
            )
        }
}
