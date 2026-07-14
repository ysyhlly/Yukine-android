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

}
