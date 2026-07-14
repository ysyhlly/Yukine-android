package app.yukine.di

import android.os.Handler
import android.os.Looper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object PlatformModule {
    @Provides
    @ActivityScoped
    fun provideMainHandler(): Handler = Handler(Looper.getMainLooper())

}
