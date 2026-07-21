package app.yukine

import android.content.Context
import app.yukine.data.CustomLyricsRepository
import app.yukine.data.LyricsRepository
import app.yukine.data.MusicLibraryRepository
import app.yukine.data.RoomProviderResponseCacheRepository
import app.yukine.data.enrichment.MetadataGatewayClient
import app.yukine.data.enrichment.UrlConnectionMetadataHttpTransport
import app.yukine.data.room.YukineDatabase
import app.yukine.streaming.LuoxueTrackMetadataResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object SettingsModule {
    @Provides
    @ActivityScoped
    fun provideMainSettingsStore(): MainSettingsStore = MainSettingsStore()

    @Provides
    @ActivityScoped
    fun provideLoadLyricsSettingsUseCase(
        repository: MusicLibraryRepository
    ): LoadLyricsSettingsUseCase =
        LoadLyricsSettingsUseCase(MusicLibraryLyricsSettingsOperations(repository))

    @Provides
    @ActivityScoped
    fun provideMetadataGatewayClient(
        @ApplicationContext context: Context
    ): MetadataGatewayClient = MetadataGatewayClient(
        cache = RoomProviderResponseCacheRepository(YukineDatabase.getInstance(context)),
        transport = UrlConnectionMetadataHttpTransport(),
        endpoint = IdentityEnhancementSettingsStore(context).effectiveGatewayEndpoint(),
        applicationVersion = BuildConfig.VERSION_NAME,
        requestQuota = PersistentMetadataGatewayRequestQuota(context)
    )

    @Provides
    @ActivityScoped
    fun provideLyricsLoader(
        luoxueTrackMetadataResolver: LuoxueTrackMetadataResolver,
        musicLibraryRepository: MusicLibraryRepository,
        customLyricsRepository: CustomLyricsRepository,
        metadataGatewayClient: MetadataGatewayClient
    ): LyricsLoader {
        val repository = LyricsRepository(
            object : LyricsRepository.BindingStore {
                override fun load(trackId: Long) = musicLibraryRepository.loadLyricBindings(trackId)

                override fun save(trackId: Long, binding: app.yukine.identity.LyricSourceBinding) {
                    musicLibraryRepository.saveLyricBinding(trackId, binding)
                }
            },
            LyricsRepository.GatewaySource { track, neteaseSongId ->
                val result = metadataGatewayClient.searchLyrics(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    neteaseSongId = neteaseSongId
                )
                val lyrics = result.lyrics
                    ?.takeUnless { result.allEndpointsFailed }
                    ?: return@GatewaySource null
                LyricsRepository.ProviderLyrics(
                    lyrics.syncedLyrics.ifBlank { lyrics.plainLyrics },
                    "",
                    lyrics.wordLyrics,
                    lyrics.wordLyricsSource
                )
            }
        )
        return LoadTrackLyricsUseCaseLyricsLoader(
            LoadTrackLyricsUseCase(
                LuoxueFirstTrackLyricsOperations(
                    resolver = luoxueTrackMetadataResolver,
                    repository = repository,
                    fallback = LyricsRepositoryLoadOperations(repository)
                ),
                customLyricsRepository
            )
        )
    }

    @Provides
    @ActivityScoped
    fun provideLoadSettingsPreferencesUseCase(
        repository: MusicLibraryRepository
    ): LoadSettingsPreferencesUseCase =
        LoadSettingsPreferencesUseCase(MusicLibrarySettingsPreferenceLoadOperations(repository))

    @Provides
    @ActivityScoped
    fun provideApplySettingsPreferenceUseCase(
        repository: MusicLibraryRepository
    ): ApplySettingsPreferenceUseCase =
        ApplySettingsPreferenceUseCase(MusicLibrarySettingsPreferenceOperations(repository))
}
