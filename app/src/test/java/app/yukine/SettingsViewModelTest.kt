package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsMetric
import app.yukine.streaming.StreamingQualityPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun renderPageFromHostPublishesTypedPageUi() {
        val viewModel = SettingsViewModel()

        viewModel.renderPageFromHost(
            SettingsPage.Home,
            SettingsPreferencesSnapshot(),
            RuntimeSettingsStatus()
        )

        val state = viewModel.uiState.value
        assertEquals(AppLanguage.text(AppLanguage.MODE_SYSTEM, "tab.settings"), state.title)
        assertEquals(state, viewModel.state.value.ui)
        assertEquals(SettingsPage.Home, viewModel.state.value.page)
    }

    @Test
    fun onEventEmitsLibraryAndExternalActionEffects() {
        val viewModel = SettingsViewModel()

        viewModel.onEvent(SettingsEvent.OpenNetworkSources)
        viewModel.onEvent(SettingsEvent.RequestNeededPermissions)
        viewModel.onEvent(SettingsEvent.LoadLibrary)
        viewModel.onEvent(SettingsEvent.OpenAudioFilePicker)
        viewModel.onEvent(SettingsEvent.OpenAudioFolderPicker)
        viewModel.onEvent(SettingsEvent.SetOnlineLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.ReloadCurrentLyrics)
        viewModel.onEvent(SettingsEvent.ApplyLyricsOffset(500L))
        viewModel.onEvent(SettingsEvent.StartSleepTimer(30))
        viewModel.onEvent(SettingsEvent.CancelSleepTimer)
        viewModel.onEvent(SettingsEvent.SetStatusBarLyricsEnabled(false))
        viewModel.onEvent(SettingsEvent.SetSystemMediaLyricsTitleEnabled(true))
        viewModel.onEvent(SettingsEvent.SetFloatingLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.OpenFloatingLyricsPermission)
        viewModel.onEvent(SettingsEvent.ChoosePageBackground(PageBackgrounds.PAGE_HOME))
        viewModel.onEvent(SettingsEvent.ClearPageBackground(PageBackgrounds.PAGE_SETTINGS))
        viewModel.onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint("http://127.0.0.1:3000"))
        viewModel.onEvent(SettingsEvent.ExportBackup)
        viewModel.onEvent(SettingsEvent.ImportBackup)

        val effects = viewModel.drainEffects()
        assertEquals(
            listOf(
                SettingsEffect.OpenNetworkSources,
                SettingsEffect.RequestNeededPermissions,
                SettingsEffect.LoadLibrary,
                SettingsEffect.OpenAudioFilePicker,
                SettingsEffect.OpenAudioFolderPicker,
                SettingsEffect.ShowStatus(AppLanguage.text(AppLanguage.MODE_SYSTEM, "online.lyrics.enabled")),
                SettingsEffect.ReloadCurrentLyrics,
                SettingsEffect.ReloadCurrentLyrics,
                SettingsEffect.ShowStatus(
                    AppLanguage.text(AppLanguage.MODE_SYSTEM, "lyrics.offset.applied") +
                        SettingsLabelFormatter.lyricsOffsetLabel(500L)
                ),
                SettingsEffect.StartSleepTimer(30),
                SettingsEffect.CancelSleepTimer,
                SettingsEffect.ShowStatus(AppLanguage.text(AppLanguage.MODE_SYSTEM, "status.bar.lyrics.disabled")),
                SettingsEffect.ShowStatus(AppLanguage.text(AppLanguage.MODE_SYSTEM, "system.media.lyrics.title.enabled")),
                SettingsEffect.ShowStatus(AppLanguage.text(AppLanguage.MODE_SYSTEM, "floating.lyrics.enabled")),
                SettingsEffect.OpenFloatingLyricsPermission,
                SettingsEffect.ChoosePageBackground(PageBackgrounds.PAGE_HOME),
                SettingsEffect.ShowStatus(
                    AppLanguage.text(AppLanguage.MODE_SYSTEM, "page.background.cleared") +
                        SettingsLabelFormatter.pageBackgroundPageLabel(PageBackgrounds.PAGE_SETTINGS, AppLanguage.MODE_SYSTEM)
                ),
                SettingsEffect.ApplyStreamingGatewayEndpoint("http://127.0.0.1:3000"),
                SettingsEffect.ExportBackup,
                SettingsEffect.ImportBackup
            ),
            effects
        )
        assertEquals(SettingsPage.Home, viewModel.state.value.page)
    }

    @Test
    fun onEventWithoutGatewayDoesNotCrash() {
        val viewModel = SettingsViewModel()

        viewModel.onEvent(SettingsEvent.LoadLibrary)

        assertEquals(SettingsUiState(), viewModel.uiState.value)
        assertEquals(SettingsState(), viewModel.state.value)
        assertEquals(listOf(SettingsEffect.LoadLibrary), viewModel.drainEffects())
    }

    @Test
    fun platformNavigationEventsEmitEffectsWithoutGateway() {
        val effects = mutableListOf<SettingsEffect>()
        val viewModel = SettingsViewModel()
        viewModel.bindEffectListener { effect -> effects += effect }

        viewModel.onEvent(SettingsEvent.OpenNetworkSources)
        viewModel.onEvent(SettingsEvent.OpenDownloads)
        viewModel.onEvent(SettingsEvent.RequestNeededPermissions)
        viewModel.onEvent(SettingsEvent.LoadLibrary)
        viewModel.onEvent(SettingsEvent.OpenAudioFilePicker)
        viewModel.onEvent(SettingsEvent.OpenAudioFolderPicker)
        viewModel.onEvent(SettingsEvent.ReloadCurrentLyrics)
        viewModel.onEvent(SettingsEvent.StartSleepTimer(15))
        viewModel.onEvent(SettingsEvent.CancelSleepTimer)
        viewModel.onEvent(SettingsEvent.OpenFloatingLyricsPermission)
        viewModel.onEvent(SettingsEvent.ChoosePageBackground(PageBackgrounds.PAGE_SETTINGS))
        viewModel.onEvent(SettingsEvent.ExportBackup)
        viewModel.onEvent(SettingsEvent.ImportBackup)
        viewModel.onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint("http://127.0.0.1:43990"))

        val expected = listOf(
            SettingsEffect.OpenNetworkSources,
            SettingsEffect.OpenDownloads,
            SettingsEffect.RequestNeededPermissions,
            SettingsEffect.LoadLibrary,
            SettingsEffect.OpenAudioFilePicker,
            SettingsEffect.OpenAudioFolderPicker,
            SettingsEffect.ReloadCurrentLyrics,
            SettingsEffect.StartSleepTimer(15),
            SettingsEffect.CancelSleepTimer,
            SettingsEffect.OpenFloatingLyricsPermission,
            SettingsEffect.ChoosePageBackground(PageBackgrounds.PAGE_SETTINGS),
            SettingsEffect.ExportBackup,
            SettingsEffect.ImportBackup,
            SettingsEffect.ApplyStreamingGatewayEndpoint("http://127.0.0.1:43990")
        )
        assertEquals(expected, effects)
        assertEquals(expected, viewModel.drainEffects())
        assertEquals(emptyList<SettingsEffect>(), viewModel.drainEffects())
    }

    @Test
    fun navigatePageUpdatesStateWithoutGateway() {
        val viewModel = SettingsViewModel()

        viewModel.onEvent(SettingsEvent.NavigateSettingsPage(SettingsPage.PageBackground))

        assertEquals(SettingsPage.PageBackground, viewModel.state.value.page)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_SYSTEM, "page.background"),
            viewModel.state.value.ui.title
        )
        assertEquals(listOf(SettingsEffect.NavigatePage(SettingsPage.PageBackground)), viewModel.drainEffects())
    }

    @Test
    fun navigateLibrarySettingsPageEmitsRouteSyncEffect() {
        val effects = mutableListOf<SettingsEffect>()
        val viewModel = SettingsViewModel()
        viewModel.bindEffectListener { effect -> effects += effect }

        viewModel.onEvent(SettingsEvent.NavigateSettingsPage(SettingsPage.LibraryGroup))
        viewModel.onEvent(SettingsEvent.NavigateSettingsPage(SettingsPage.Library))

        assertEquals(SettingsPage.Library, viewModel.state.value.page)
        assertEquals(
            listOf(
                SettingsEffect.NavigatePage(SettingsPage.LibraryGroup),
                SettingsEffect.NavigatePage(SettingsPage.Library)
            ),
            effects
        )
        assertEquals(effects, viewModel.drainEffects())
    }

    @Test
    fun librarySettingsBackActionsNavigateThroughExpectedParents() {
        val viewModel = SettingsViewModel()
        viewModel.renderCurrentPage(
            SettingsPage.Library,
            SettingsPreferencesSnapshot(languageMode = AppLanguage.MODE_CHINESE),
            RuntimeSettingsStatus(librarySongCount = 128)
        )

        viewModel.state.value.actions.first().onClick.run()

        assertEquals(SettingsPage.LibraryGroup, viewModel.state.value.page)
        assertEquals(
            listOf(SettingsEffect.NavigatePage(SettingsPage.LibraryGroup)),
            viewModel.drainEffects()
        )

        viewModel.state.value.actions.first().onClick.run()

        assertEquals(SettingsPage.Home, viewModel.state.value.page)
        assertEquals(
            listOf(SettingsEffect.NavigatePage(SettingsPage.Home)),
            viewModel.drainEffects()
        )
    }

    @Test
    fun renderPageFromHostUpdatesCurrentPage() {
        val viewModel = SettingsViewModel()

        viewModel.renderPageFromHost(
            SettingsPage.PlaybackGroup,
            SettingsPreferencesSnapshot(),
            RuntimeSettingsStatus()
        )

        assertEquals(SettingsPage.PlaybackGroup, viewModel.state.value.page)
        assertEquals(viewModel.state.value.ui, viewModel.uiState.value)
    }

    @Test
    fun updateSettingsContextPublishesPreferencesAndRuntimeStatus() {
        val viewModel = SettingsViewModel()
        val preferences = SettingsPreferencesSnapshot(
            themeMode = "dark",
            accentMode = "teal",
            languageMode = AppLanguage.MODE_ENGLISH,
            playbackSpeed = 1.25f,
            appVolume = 0.75f,
            streamingAudioQuality = "lossless",
            concurrentPlaybackEnabled = true,
            statusBarLyricsEnabled = false,
            floatingLyricsEnabled = true,
            nowPlayingGesturesEnabled = false,
            playbackRestoreEnabled = false,
            replayGainEnabled = false,
            shareStyle = TrackShareStyle.PLATFORM_CARD,
            pageBackgrounds = PageBackgrounds(sharedUri = "content://all")
        )
        val runtime = RuntimeSettingsStatus(
            audioPermissionGranted = true,
            notificationPermissionGranted = true,
            overlayPermissionGranted = true,
            playbackServiceConnected = true,
            sleepTimerRemainingMs = 60_000L,
            lyricsOffsetMs = -300L,
            onlineLyricsEnabled = true,
            librarySongCount = 12,
            libraryAlbumCount = 3,
            libraryArtistCount = 4,
            streamingGatewayEndpoint = "http://127.0.0.1:43990",
            streamingGatewayConfigured = true
        )

        viewModel.updateSettingsContext(preferences, runtime)

        assertEquals(preferences, viewModel.state.value.preferences)
        assertEquals(runtime, viewModel.state.value.runtime)
        assertEquals(preferences.pageBackgrounds, viewModel.chromeState.value.pageBackgrounds)
        assertEquals(false, viewModel.chromeState.value.nowPlayingGesturesEnabled)
        assertEquals(SettingsUiState(), viewModel.uiState.value)
    }

    @Test
    fun onEventAppliesPurePreferencesWithoutGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val runtimeEffects = mutableListOf<SettingsRuntimeEffect>()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindRuntimeEffectListener { effect ->
            runtimeEffects += effect
            true
        }

        viewModel.onEvent(SettingsEvent.ApplyThemeMode("dark"))
        viewModel.onEvent(SettingsEvent.ApplyAccentMode("teal"))
        viewModel.onEvent(SettingsEvent.ApplyLanguageMode(AppLanguage.MODE_ENGLISH))
        viewModel.onEvent(SettingsEvent.ApplyPlaybackSpeed(2.5f))
        viewModel.onEvent(SettingsEvent.ApplyAppVolume(-0.4f))
        viewModel.onEvent(SettingsEvent.ApplyStreamingAudioQuality("lossless"))
        viewModel.onEvent(SettingsEvent.ApplyShareStyle(TrackShareStyle.CARD))
        viewModel.onEvent(SettingsEvent.SetConcurrentPlaybackEnabled(false))
        viewModel.onEvent(SettingsEvent.SetOnlineLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.ApplyLyricsOffset(5555L))
        viewModel.onEvent(SettingsEvent.ApplyAudioEffectSettings(app.yukine.playback.AudioEffectSettings.DEFAULT.withEnabled(true)))
        viewModel.onEvent(SettingsEvent.SetStatusBarLyricsEnabled(false))
        viewModel.onEvent(SettingsEvent.SetSystemMediaLyricsTitleEnabled(true))
        viewModel.onEvent(SettingsEvent.SetFloatingLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.SetNowPlayingGesturesEnabled(false))
        viewModel.onEvent(SettingsEvent.SetPlaybackRestoreEnabled(true))
        viewModel.onEvent(SettingsEvent.SetReplayGainEnabled(false))
        viewModel.onEvent(SettingsEvent.SetDebugPromptsEnabled(true))
        viewModel.onEvent(SettingsEvent.SetCustomBackgroundBlurEnabled(true))
        viewModel.onEvent(SettingsEvent.SetCustomBackgroundBlurRadiusDp(80f))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "theme",
                "speed:2.0",
                "volume:0.0",
                "concurrent:false",
                "onlineLyrics:true",
                "lyricsOffset:5000",
                "audioEffects:true",
                "statusLyrics:false",
                "systemMediaTitle:true",
                "floatingLyrics:true",
                "restore:true",
                "replayGain:false"
            ),
            runtimeEffects.map { effect ->
                when (effect) {
                    SettingsRuntimeEffect.ApplyThemeSurface -> "theme"
                    is SettingsRuntimeEffect.ApplyPlaybackSpeed -> "speed:${effect.speed}"
                    is SettingsRuntimeEffect.ApplyAppVolume -> "volume:${effect.volume}"
                    is SettingsRuntimeEffect.SetConcurrentPlaybackEnabled -> "concurrent:${effect.enabled}"
                    is SettingsRuntimeEffect.SetOnlineLyricsEnabled -> "onlineLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetLyricsOffsetMs -> "lyricsOffset:${effect.offsetMs}"
                    is SettingsRuntimeEffect.ApplyAudioEffects -> "audioEffects:${effect.settings.enabled}"
                    is SettingsRuntimeEffect.SetStatusBarLyrics -> "statusLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled -> "systemMediaTitle:${effect.enabled}"
                    is SettingsRuntimeEffect.ApplyFloatingLyrics -> "floatingLyrics:${effect.enabled}"
                    SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings -> "floatingPermission"
                    is SettingsRuntimeEffect.SetPlaybackRestoreEnabled -> "restore:${effect.enabled}"
                    is SettingsRuntimeEffect.SetReplayGainEnabled -> "replayGain:${effect.enabled}"
                }
            }
        )
        assertTrue(viewModel.drainEffects().filterIsInstance<SettingsEffect.ShowStatus>().isNotEmpty())
        assertEquals(
            listOf(
                "theme:dark",
                "accent:teal",
                "language:en",
                "speed:2.0",
                "volume:0.0",
                "quality:lossless",
                "shareStyle:${TrackShareStyle.CARD}",
                "concurrent:false",
                "onlineLyrics:true",
                "lyricsOffset:5000",
                "audioEffects:enabled=true;preset=-1;bands=;bass=0;virtualizer=0;loudness=0",
                "statusLyrics:false",
                "systemMediaTitle:true",
                "floatingLyrics:true",
                "gestures:false",
                "restore:true",
                "replayGain:false",
                "debugPrompts:true",
                "customBackgroundBlurEnabled:true",
                "customBackgroundBlurRadius:64.0"
            ),
            preferenceGateway.events
        )
        val state = viewModel.state.value
        assertEquals(EchoTheme.MODE_DARK, state.preferences.themeMode)
        assertEquals(EchoTheme.ACCENT_TEAL, state.preferences.accentMode)
        assertEquals(AppLanguage.MODE_ENGLISH, state.preferences.languageMode)
        assertEquals(2.0f, state.preferences.playbackSpeed, 0.0f)
        assertEquals(0.0f, state.preferences.appVolume, 0.0f)
        assertEquals(StreamingQualityPreference.LOSSLESS, state.preferences.streamingAudioQuality)
        assertEquals(TrackShareStyle.CARD, state.preferences.shareStyle)
        assertEquals(true, state.runtime.onlineLyricsEnabled)
        assertEquals(false, state.preferences.concurrentPlaybackEnabled)
        assertEquals(false, state.preferences.statusBarLyricsEnabled)
        assertEquals(true, state.preferences.systemMediaLyricsTitleEnabled)
        assertEquals(true, state.preferences.floatingLyricsEnabled)
        assertEquals(false, state.preferences.nowPlayingGesturesEnabled)
        assertEquals(true, state.preferences.playbackRestoreEnabled)
        assertEquals(true, state.preferences.debugPromptsEnabled)
        assertEquals(true, state.preferences.customBackgroundBlurEnabled)
        assertEquals(64f, state.preferences.customBackgroundBlurRadiusDp)
        assertEquals(true, viewModel.chromeState.value.customBackgroundBlurEnabled)
        assertEquals(64f, viewModel.chromeState.value.customBackgroundBlurRadiusDp)
        assertEquals(5000L, state.runtime.lyricsOffsetMs)
        assertEquals(state.ui, viewModel.uiState.value)
    }

    @Test
    fun audioExclusiveMapsToTheInverseConcurrentPlaybackRuntimeSetting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val runtimeEffects = mutableListOf<SettingsRuntimeEffect>()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindRuntimeEffectListener { effect ->
            runtimeEffects += effect
            true
        }

        viewModel.onEvent(SettingsEvent.SetAudioExclusiveEnabled(true))
        viewModel.onEvent(SettingsEvent.SetAudioExclusiveEnabled(false))
        advanceUntilIdle()

        assertEquals(
            listOf(
                SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(false),
                SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(true)
            ),
            runtimeEffects
        )
        assertEquals(listOf("concurrent:false", "concurrent:true"), preferenceGateway.events)
        assertEquals(true, viewModel.state.value.preferences.concurrentPlaybackEnabled)
        assertEquals(
            listOf(
                AppLanguage.text(AppLanguage.MODE_SYSTEM, "audio.exclusive.enabled"),
                AppLanguage.text(AppLanguage.MODE_SYSTEM, "audio.exclusive.disabled")
            ),
            viewModel.drainEffects().filterIsInstance<SettingsEffect.ShowStatus>().map { it.message }
        )
    }

    @Test
    fun clearPageBackgroundAppliesCurrentSnapshotWithoutGateway() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        viewModel.bindPreferenceGateway(preferenceGateway)
        val preferences = SettingsPreferencesSnapshot(
            pageBackgrounds = PageBackgrounds(
                homeUri = "content://home",
                settingsUri = "content://settings"
            )
        )
        viewModel.renderCurrentPage(SettingsPage.PageBackground, preferences, RuntimeSettingsStatus())

        viewModel.onEvent(SettingsEvent.ClearPageBackground(PageBackgrounds.PAGE_SETTINGS))
        advanceUntilIdle()

        val backgrounds = viewModel.state.value.preferences.pageBackgrounds
        assertEquals("content://home", backgrounds.homeUri)
        assertEquals("", backgrounds.settingsUri)
        assertEquals(SettingsPage.PageBackground, viewModel.state.value.page)
        assertEquals(
            listOf(
                SettingsEffect.ShowStatus(
                    AppLanguage.text(AppLanguage.MODE_SYSTEM, "page.background.cleared") +
                        SettingsLabelFormatter.pageBackgroundPageLabel(PageBackgrounds.PAGE_SETTINGS, AppLanguage.MODE_SYSTEM)
                )
            ),
            viewModel.drainEffects()
        )
        assertEquals(listOf("background:"), preferenceGateway.events)
    }

    @Test
    fun renderCurrentPageBuildsUiFromSnapshotsAndRoutesActionsThroughViewModel() {
        val viewModel = SettingsViewModel()
        val preferences = SettingsPreferencesSnapshot(
            themeMode = "dark",
            accentMode = "teal",
            languageMode = AppLanguage.MODE_ENGLISH,
            playbackSpeed = 1.25f,
            appVolume = 0.75f,
            streamingAudioQuality = StreamingQualityPreference.LOSSLESS,
            shareStyle = TrackShareStyle.CARD
        )
        val runtime = RuntimeSettingsStatus(
            streamingGatewayEndpoint = StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT,
            streamingGatewayConfigured = true
        )

        val content = viewModel.renderCurrentPage(SettingsPage.StreamingGateway, preferences, runtime)

        assertEquals(SettingsPage.StreamingGateway, viewModel.state.value.page)
        assertEquals(preferences, viewModel.state.value.preferences)
        assertEquals(runtime, viewModel.state.value.runtime)
        assertEquals(content.uiState, viewModel.state.value.ui)
        assertEquals(content.uiState, viewModel.uiState.value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.gateway"), content.uiState.title)
        assertEquals(4, content.actions.size)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(
            listOf(
                SettingsEffect.NavigatePage(SettingsPage.SourcesGroup),
                SettingsEffect.ApplyStreamingGatewayEndpoint(StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT)
            ),
            viewModel.drainEffects()
        )
        assertEquals(SettingsPage.SourcesGroup, viewModel.state.value.page)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.group.sources"), viewModel.state.value.ui.title)
    }

    @Test
    fun settingActionsNormalizeNotifyAndSavePreferences() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val mirror = FakeSettingsStoreMirror()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindStoreMirror(mirror)

        viewModel.applyThemeMode("dark")
        viewModel.applyAccentMode("teal")
        viewModel.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        viewModel.applyPlaybackSpeed(2.5f)
        viewModel.applyAppVolume(-0.4f)
        viewModel.applyStreamingAudioQuality("lossless")
        viewModel.applyShareStyle(TrackShareStyle.CARD)
        viewModel.setOnlineLyricsEnabled(true)
        viewModel.setConcurrentPlaybackEnabled(false)
        viewModel.setStatusBarLyricsEnabled(false)
        viewModel.setFloatingLyricsEnabled(true)
        viewModel.setNowPlayingGesturesEnabled(false)
        viewModel.setPlaybackRestoreEnabled(true)
        viewModel.applyPageBackgrounds(PageBackgrounds(sharedUri = "content://bg"), PageBackgrounds.PAGE_ALL, false)
        viewModel.applyLyricsOffset(5555L)
        advanceUntilIdle()

        val emittedEffects = viewModel.drainEffects()
        assertTrue(emittedEffects.filterIsInstance<SettingsEffect.ShowStatus>().size >= 14)
        assertTrue(emittedEffects.contains(SettingsEffect.ReloadCurrentLyrics))
        assertEquals(
            listOf(
                "theme:dark",
                "accent:teal",
                "language:en",
                "speed:2.0",
                "volume:0.0",
                "quality:lossless",
                "shareStyle:${TrackShareStyle.CARD}",
                "onlineLyrics:true",
                "concurrent:false",
                "statusLyrics:false",
                "floatingLyrics:true",
                "gestures:false",
                "restore:true",
                "background:content://bg",
                "lyricsOffset:5000"
            ),
            preferenceGateway.events
        )
        assertTrue(mirror.snapshots.isNotEmpty())
        assertEquals(
            "dark|teal|en|2.0|0.0|lossless|false|true|false|true|${TrackShareStyle.CARD}|content://bg",
            mirror.snapshots.last()
        )
    }

    @Test
    fun enablingFloatingLyricsDisablesStatusBarLyricsWhenOverlayStarts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val runtimeEffects = mutableListOf<SettingsRuntimeEffect>()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindRuntimeEffectListener { effect ->
            runtimeEffects += effect
            true
        }
        viewModel.renderCurrentPage(
            SettingsPage.Lyrics,
            SettingsPreferencesSnapshot(statusBarLyricsEnabled = true, floatingLyricsEnabled = false),
            RuntimeSettingsStatus()
        )

        viewModel.setFloatingLyricsEnabled(true)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.preferences.statusBarLyricsEnabled)
        assertEquals(true, viewModel.state.value.preferences.floatingLyricsEnabled)
        assertEquals(
            listOf("floatingLyrics:true", "statusLyrics:false"),
            runtimeEffects.map { effect ->
                when (effect) {
                    is SettingsRuntimeEffect.ApplyFloatingLyrics -> "floatingLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetStatusBarLyrics -> "statusLyrics:${effect.enabled}"
                    else -> error("Unexpected runtime effect $effect")
                }
            }
        )
        assertEquals(
            listOf("statusLyrics:false", "floatingLyrics:true"),
            preferenceGateway.events
        )
    }

    @Test
    fun enablingStatusBarLyricsDisablesFloatingLyrics() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val runtimeEffects = mutableListOf<SettingsRuntimeEffect>()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindRuntimeEffectListener { effect ->
            runtimeEffects += effect
            true
        }
        viewModel.renderCurrentPage(
            SettingsPage.Lyrics,
            SettingsPreferencesSnapshot(statusBarLyricsEnabled = false, floatingLyricsEnabled = true),
            RuntimeSettingsStatus()
        )

        viewModel.setStatusBarLyricsEnabled(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.preferences.statusBarLyricsEnabled)
        assertEquals(false, viewModel.state.value.preferences.floatingLyricsEnabled)
        assertEquals(
            listOf("floatingLyrics:false", "statusLyrics:true"),
            runtimeEffects.map { effect ->
                when (effect) {
                    is SettingsRuntimeEffect.ApplyFloatingLyrics -> "floatingLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetStatusBarLyrics -> "statusLyrics:${effect.enabled}"
                    else -> error("Unexpected runtime effect $effect")
                }
            }
        )
        assertEquals(
            listOf("floatingLyrics:false", "statusLyrics:true"),
            preferenceGateway.events
        )
    }

    @Test
    fun prepareAppliedStatusTextBuildsLocalizedSettingsMessages() {
        val viewModel = SettingsViewModel()

        val status = viewModel.prepareAppliedStatusText(
            languageMode = AppLanguage.MODE_ENGLISH,
            themeMode = "dark",
            accentMode = "teal",
            playbackSpeed = 1.25f,
            appVolume = 0.8f,
            lyricsOffsetMs = -300L
        )

        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "theme.applied") +
                    AppLanguage.themeLabel("dark", AppLanguage.MODE_ENGLISH),
            status.themeApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "accent.applied") +
                    AppLanguage.accentLabel("teal", AppLanguage.MODE_ENGLISH),
            status.accentApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "language.applied") +
                    AppLanguage.labelFor(AppLanguage.MODE_ENGLISH),
            status.languageApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "speed.applied") +
                    SettingsLabelFormatter.playbackSpeedLabel(1.25f),
            status.playbackSpeedApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "volume.applied") +
                    SettingsLabelFormatter.appVolumeLabel(0.8f),
            status.appVolumeApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "online.lyrics.enabled"),
            status.onlineLyricsEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "online.lyrics.disabled"),
            status.onlineLyricsDisabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "concurrent.playback.enabled"),
            status.concurrentPlaybackEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "concurrent.playback.disabled"),
            status.concurrentPlaybackDisabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "lyrics.offset.applied") +
                    SettingsLabelFormatter.lyricsOffsetLabel(-300L),
            status.lyricsOffsetApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "status.bar.lyrics.enabled"),
            status.statusBarLyricsEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "status.bar.lyrics.disabled"),
            status.statusBarLyricsDisabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics.enabled"),
            status.floatingLyricsEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics.disabled"),
            status.floatingLyricsDisabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics.permission.required"),
            status.floatingLyricsPermissionRequired
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "now.playing.gestures.enabled"),
            status.nowPlayingGesturesEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "now.playing.gestures.disabled"),
            status.nowPlayingGesturesDisabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "playback.restore.enabled"),
            status.playbackRestoreEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "playback.restore.disabled"),
            status.playbackRestoreDisabled
        )
    }

    private class FakePreferenceGateway : SettingsPreferenceGateway {
        val events = mutableListOf<String>()

        override fun save(update: SettingsPreferenceUpdate) {
            events += when (update.key) {
                SettingsPreferenceKey.ThemeMode -> "theme:${update.value}"
                SettingsPreferenceKey.AccentMode -> "accent:${update.value}"
                SettingsPreferenceKey.LanguageMode -> "language:${update.value}"
                SettingsPreferenceKey.PlaybackSpeed -> "speed:${update.value}"
                SettingsPreferenceKey.AppVolume -> "volume:${update.value}"
                SettingsPreferenceKey.StreamingAudioQuality -> "quality:${update.value}"
                SettingsPreferenceKey.OnlineLyricsEnabled -> "onlineLyrics:${update.value}"
                SettingsPreferenceKey.ConcurrentPlaybackEnabled -> "concurrent:${update.value}"
                SettingsPreferenceKey.LyricsOffsetMs -> "lyricsOffset:${update.value}"
                SettingsPreferenceKey.AudioEffectSettings -> {
                    val settings = update.value as app.yukine.playback.AudioEffectSettings
                    "audioEffects:${settings.encode()}"
                }
                SettingsPreferenceKey.StatusBarLyricsEnabled -> "statusLyrics:${update.value}"
                SettingsPreferenceKey.SystemMediaLyricsTitleEnabled -> "systemMediaTitle:${update.value}"
                SettingsPreferenceKey.FloatingLyricsEnabled -> "floatingLyrics:${update.value}"
                SettingsPreferenceKey.NowPlayingGesturesEnabled -> "gestures:${update.value}"
                SettingsPreferenceKey.PlaybackRestoreEnabled -> "restore:${update.value}"
                SettingsPreferenceKey.ReplayGainEnabled -> "replayGain:${update.value}"
                SettingsPreferenceKey.DebugPromptsEnabled -> "debugPrompts:${update.value}"
                SettingsPreferenceKey.CustomBackgroundBlurEnabled ->
                    "customBackgroundBlurEnabled:${update.value}"
                SettingsPreferenceKey.CustomBackgroundBlurRadiusDp ->
                    "customBackgroundBlurRadius:${update.value}"
                SettingsPreferenceKey.GlassBlurEnabled -> "glassBlurEnabled:${update.value}"
                SettingsPreferenceKey.GlassBlurRadiusDp -> "glassBlurRadius:${update.value}"
                SettingsPreferenceKey.GlassSurfaceOpacity -> "glassSurfaceOpacity:${update.value}"
                SettingsPreferenceKey.ShareStyle -> "shareStyle:${update.value}"
                SettingsPreferenceKey.PageBackgrounds -> {
                    val backgrounds = update.value as PageBackgrounds
                    "background:${backgrounds.sharedUri}"
                }
            }
        }
    }

    private class FakeSettingsStoreMirror : SettingsStoreMirror {
        val snapshots = mutableListOf<String>()

        override fun sync(preferences: SettingsPreferencesSnapshot) {
            snapshots += listOf(
                preferences.themeMode,
                preferences.accentMode,
                preferences.languageMode,
                preferences.playbackSpeed.toString(),
                preferences.appVolume.toString(),
                preferences.streamingAudioQuality,
                preferences.concurrentPlaybackEnabled.toString(),
                preferences.floatingLyricsEnabled.toString(),
                preferences.nowPlayingGesturesEnabled.toString(),
                preferences.playbackRestoreEnabled.toString(),
                preferences.shareStyle,
                preferences.pageBackgrounds.sharedUri
            ).joinToString("|")
        }
    }

}
