package app.echo.next.di

import android.content.Context
import app.echo.next.StreamingGatewaySettingsStore
import app.echo.next.StreamingRepositoryProvider
import app.echo.next.StreamingRepositorySource
import app.echo.next.streaming.HeaderBackedStreamingPlaybackTrackAdapter
import app.echo.next.streaming.LocalStreamingAuthStore
import app.echo.next.streaming.PersistentStreamingPlaybackHeaders
import app.echo.next.streaming.RemoteStreamingGatewayFactory
import app.echo.next.streaming.StreamingGatewayFactory
import app.echo.next.streaming.StreamingPlaybackHeaderStore
import app.echo.next.streaming.StreamingPlaybackTrackAdapter
import app.echo.next.streaming.cache.StreamingCachePolicy
import app.echo.next.streaming.cache.StreamingCacheDao
import app.echo.next.streaming.cache.StreamingCacheDatabase
import app.echo.next.streaming.cache.StreamingCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamingDataModule {
    @Provides
    @Singleton
    fun provideStreamingGatewaySettingsStore(
        @ApplicationContext context: Context
    ): StreamingGatewaySettingsStore {
        return StreamingGatewaySettingsStore(context)
    }

    @Provides
    @Singleton
    fun provideLocalStreamingAuthStore(
        @ApplicationContext context: Context
    ): LocalStreamingAuthStore {
        return LocalStreamingAuthStore(context)
    }

    @Provides
    @Singleton
    fun provideStreamingCacheDatabase(
        @ApplicationContext context: Context
    ): StreamingCacheDatabase {
        return StreamingCacheDatabase.getInstance(context)
    }

    @Provides
    fun provideStreamingCacheDao(database: StreamingCacheDatabase): StreamingCacheDao {
        return database.streamingCacheDao()
    }

    @Provides
    @Singleton
    fun provideStreamingCacheRepository(dao: StreamingCacheDao): StreamingCacheRepository {
        return StreamingCacheRepository(dao)
    }

    @Provides
    @Singleton
    fun provideStreamingCachePolicy(): StreamingCachePolicy {
        return StreamingCachePolicy()
    }

    @Provides
    @Singleton
    fun provideStreamingGatewayFactory(
        localAuthStore: LocalStreamingAuthStore
    ): StreamingGatewayFactory {
        return RemoteStreamingGatewayFactory(localAuthStore)
    }

    @Provides
    @Singleton
    fun provideStreamingPlaybackHeaderStore(
        cacheRepository: StreamingCacheRepository
    ): StreamingPlaybackHeaderStore {
        return PersistentStreamingPlaybackHeaders(cacheRepository)
    }

    @Provides
    @Singleton
    fun provideStreamingPlaybackTrackAdapter(
        headers: StreamingPlaybackHeaderStore
    ): StreamingPlaybackTrackAdapter {
        return HeaderBackedStreamingPlaybackTrackAdapter(headers)
    }

    @Provides
    fun provideStreamingRepositorySource(provider: StreamingRepositoryProvider): StreamingRepositorySource {
        return provider
    }
}
