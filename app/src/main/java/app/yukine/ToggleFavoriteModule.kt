package app.yukine

import app.yukine.data.MusicLibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ServiceComponent

@Module
@InstallIn(ActivityComponent::class, ServiceComponent::class)
internal object ToggleFavoriteModule {
    @Provides
    fun provideToggleFavoriteUseCase(repository: MusicLibraryRepository): ToggleFavoriteUseCase =
        ToggleFavoriteUseCase(MusicLibraryFavoriteOperations(repository))
}
