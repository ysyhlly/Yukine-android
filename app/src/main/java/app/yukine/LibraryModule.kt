package app.yukine

import app.yukine.data.MusicLibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object LibraryModule {
    @Provides
    @ActivityScoped
    fun provideToggleFavoriteUseCase(repository: MusicLibraryRepository): ToggleFavoriteUseCase =
        ToggleFavoriteUseCase(MusicLibraryFavoriteOperations(repository))

    @Provides
    @ActivityScoped
    fun provideLoadPlaylistTracksUseCase(repository: MusicLibraryRepository): LoadPlaylistTracksUseCase =
        LoadPlaylistTracksUseCase(MusicLibraryPlaylistTrackOperations(repository))

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
}
