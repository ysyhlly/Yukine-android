package app.yukine.di

import android.content.Context
import app.yukine.common.StreamingDataPathParser
import app.yukine.StreamingGatewaySettingsStore
import app.yukine.AndroidStreamingWebCookieSessionSource
import app.yukine.StreamingRepositoryProvider
import app.yukine.StreamingRepositorySource
import app.yukine.DefaultRecordingSourceVerificationGateway
import app.yukine.data.RecordingSourceVerificationGateway
import app.yukine.streaming.HeaderBackedStreamingPlaybackTrackAdapter
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.LocalLuoxueTrackMetadataResolver
import app.yukine.streaming.LuoxueSourceStore
import app.yukine.streaming.LuoxueTrackMetadataResolver
import app.yukine.streaming.PersistentStreamingPlaybackHeaders
import app.yukine.streaming.RemoteStreamingGatewayFactory
import app.yukine.streaming.StreamingGatewayFactory
import app.yukine.streaming.StreamingWebCookieSessionSource
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackHeaderStore
import app.yukine.streaming.StreamingPlaybackTrackAdapter
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheDao
import app.yukine.streaming.cache.StreamingCacheDatabase
import app.yukine.streaming.cache.StreamingCacheRepository
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
    fun provideLuoxueSourceStore(
        @ApplicationContext context: Context
    ): LuoxueSourceStore {
        return LuoxueSourceStore(context)
    }

    @Provides
    @Singleton
    fun provideLuoxueTrackMetadataResolver(
        sourceStore: LuoxueSourceStore
    ): LuoxueTrackMetadataResolver {
        return LocalLuoxueTrackMetadataResolver(sourceStore)
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
        localAuthStore: LocalStreamingAuthStore,
        webCookieSessionSource: StreamingWebCookieSessionSource,
        luoxueSourceStore: LuoxueSourceStore
    ): StreamingGatewayFactory {
        return RemoteStreamingGatewayFactory(
            localAuthStore = localAuthStore,
            webCookieSessionSource = webCookieSessionSource,
            luoxueSourceStore = luoxueSourceStore
        )
    }

    @Provides
    @Singleton
    fun provideStreamingWebCookieSessionSource(): StreamingWebCookieSessionSource {
        return AndroidStreamingWebCookieSessionSource()
    }

    @Provides
    @Singleton
    fun provideStreamingPlaybackHeaderStore(
        cacheRepository: StreamingCacheRepository,
        localAuthStore: LocalStreamingAuthStore,
        playbackSourcePolicy: app.yukine.PersistentPlaybackSourcePolicy
    ): StreamingPlaybackHeaderStore {
        return PersistentStreamingPlaybackHeaders(cacheRepository, localAuthStore, playbackSourcePolicy)
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

    @Provides
    fun provideRecordingSourceVerificationGateway(
        gateway: DefaultRecordingSourceVerificationGateway
    ): RecordingSourceVerificationGateway = gateway

    @Provides
    @Singleton
    fun provideStreamingDataPathParser(): StreamingDataPathParser {
        return StreamingPlaybackAdapter
    }
}
