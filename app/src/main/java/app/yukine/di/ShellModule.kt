package app.yukine.di

import app.yukine.MainExecutors
import app.yukine.NativeMusicShareManager
import app.yukine.TrackShareManager
import app.yukine.TrackShareManagerOperations
import app.yukine.TrackShareOperations
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
    fun provideTrackShareOperations(
        trackShareManager: TrackShareManager,
        nativeMusicShareManager: NativeMusicShareManager
    ): TrackShareOperations =
        TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)
}
