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

}
