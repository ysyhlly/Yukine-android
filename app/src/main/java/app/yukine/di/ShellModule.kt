package app.yukine.di

import app.yukine.MainExecutors
import app.yukine.MainHomeDashboardRenderListener
import app.yukine.MainHomeDashboardRenderListenerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object ShellModule {
    @Provides
    @ActivityScoped
    fun provideMainExecutors(): MainExecutors = MainExecutors()

    @Provides
    @ActivityScoped
    fun provideMainHomeDashboardRenderListenerFactory(): MainHomeDashboardRenderListenerFactory =
        MainHomeDashboardRenderListenerFactory {
                libraryModeOpener,
                playbackContinuer,
                nowPlayingOpener,
                trackListPlayer,
                libraryRefresher,
                queueOpener,
                allTracksSource,
                streamingOpener,
                collectionsOpener,
                searchOpener,
                dailyRecommendationsPlayer,
                heartbeatRecommendationsPlayer,
                actionsPublisher ->
            MainHomeDashboardRenderListener(
                libraryModeOpener,
                playbackContinuer,
                nowPlayingOpener,
                trackListPlayer,
                libraryRefresher,
                queueOpener,
                allTracksSource,
                streamingOpener,
                collectionsOpener,
                searchOpener,
                dailyRecommendationsPlayer,
                heartbeatRecommendationsPlayer,
                actionsPublisher
            )
        }
}
