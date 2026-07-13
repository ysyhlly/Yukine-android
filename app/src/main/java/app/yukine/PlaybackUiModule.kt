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
    fun provideNowPlayingPlaybackServiceStarter(
        @ActivityContext context: Context
    ): NowPlayingPlaybackServiceStarter =
        NowPlayingPlaybackServiceStarter(context)

}
