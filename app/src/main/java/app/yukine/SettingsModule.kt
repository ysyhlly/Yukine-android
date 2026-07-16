package app.yukine

import app.yukine.data.LyricsRepository
import app.yukine.data.MusicLibraryRepository
import app.yukine.streaming.LuoxueTrackMetadataResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
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
    fun provideLyricsLoader(
        luoxueTrackMetadataResolver: LuoxueTrackMetadataResolver,
        musicLibraryRepository: MusicLibraryRepository
    ): LyricsLoader {
        val repository = LyricsRepository(object : LyricsRepository.BindingStore {
            override fun load(trackId: Long) = musicLibraryRepository.loadLyricBindings(trackId)

            override fun save(trackId: Long, binding: app.yukine.identity.LyricSourceBinding) {
                musicLibraryRepository.saveLyricBinding(trackId, binding)
            }
        })
        return LoadTrackLyricsUseCaseLyricsLoader(
            LoadTrackLyricsUseCase(
                LuoxueFirstTrackLyricsOperations(
                    resolver = luoxueTrackMetadataResolver,
                    repository = repository,
                    fallback = LyricsRepositoryLoadOperations(repository)
                )
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
