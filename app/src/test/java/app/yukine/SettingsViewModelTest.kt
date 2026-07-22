package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsMetric
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.navigation.SettingsTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun publishCurrentPagePublishesTypedPageUi() {
        val viewModel = SettingsViewModel()

        viewModel.publishCurrentPage(
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
    fun focusedPageOwnersEmitLibraryAndExternalActionEffects() {
        val viewModel = SettingsViewModel()

        viewModel.network.openPage(NetworkPage.Sources)
        viewModel.platform.requestNeededPermissions()
        viewModel.library.loadLibrary()
        viewModel.library.openAudioFilePicker()
        viewModel.library.openAudioFolderPicker()
        viewModel.lyrics.setOnlineLyricsEnabled(true)
        viewModel.lyrics.reloadCurrentLyrics()
        viewModel.lyrics.applyLyricsOffset(500L)
        viewModel.playback.startSleepTimer(30)
        viewModel.playback.cancelSleepTimer()
        viewModel.lyrics.setStatusBarLyricsEnabled(false)
        viewModel.lyrics.setSystemMediaLyricsTitleEnabled(true)
        viewModel.lyrics.setFloatingLyricsEnabled(true)
        viewModel.platform.openFloatingLyricsPermission()
        viewModel.appearance.choosePageBackground(PageBackgrounds.PAGE_HOME)
        viewModel.appearance.clearPageBackground(PageBackgrounds.PAGE_SETTINGS)
        viewModel.network.applyStreamingGatewayEndpoint("http://127.0.0.1:3000")
        viewModel.platform.exportBackup()
        viewModel.platform.importBackup()

        val effects = viewModel.drainEffects()
        assertEquals(
            listOf(
                SettingsEffect.OpenNetworkPage(NetworkPage.Sources),
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

        viewModel.library.loadLibrary()

        assertEquals(SettingsUiState(), viewModel.uiState.value)
        assertEquals(SettingsPage.Home, viewModel.state.value.page)
        assertEquals(SettingsPreferencesSnapshot(), viewModel.state.value.preferences)
        assertEquals(emptyList<SettingsAction>(), viewModel.state.value.actions)
        assertEquals(listOf(SettingsEffect.LoadLibrary), viewModel.drainEffects())
    }

    @Test
    fun platformNavigationEventsEmitEffectsWithoutGateway() {
        val effects = mutableListOf<SettingsEffect>()
        val viewModel = SettingsViewModel()
        viewModel.bindEffectListener { effect -> effects += effect }

        viewModel.network.openPage(NetworkPage.WebDav)
        viewModel.platform.openDownloads()
        viewModel.platform.requestNeededPermissions()
        viewModel.library.loadLibrary()
        viewModel.library.openAudioFilePicker()
        viewModel.library.openAudioFolderPicker()
        viewModel.lyrics.reloadCurrentLyrics()
        viewModel.playback.startSleepTimer(15)
        viewModel.playback.cancelSleepTimer()
        viewModel.platform.openFloatingLyricsPermission()
        viewModel.appearance.choosePageBackground(PageBackgrounds.PAGE_SETTINGS)
        viewModel.platform.exportBackup()
        viewModel.platform.importBackup()
        viewModel.network.applyStreamingGatewayEndpoint("http://127.0.0.1:43990")

        val expected = listOf(
            SettingsEffect.OpenNetworkPage(NetworkPage.WebDav),
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
        assertEquals(emptyList<SettingsEffect>(), viewModel.drainEffects())
    }

    @Test
    fun navigatePageUpdatesStateWithoutGateway() {
        val viewModel = SettingsViewModel()

        viewModel.navigateSettingsPage(SettingsPage.PageBackground)

        assertEquals(SettingsPage.PageBackground, viewModel.state.value.page)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_SYSTEM, "page.background"),
            viewModel.state.value.ui.title
        )
        assertEquals(listOf(SettingsEffect.NavigatePage(SettingsPage.PageBackground)), viewModel.drainEffects())
    }

    @Test
    fun searchResultNavigatesToCategoryAndHighlightsWithoutExecutingTheSetting() {
        val viewModel = SettingsViewModel()
        viewModel.publishCurrentPage(
            SettingsPage.Home,
            SettingsPreferencesSnapshot(themeMode = EchoTheme.MODE_DARK),
            RuntimeSettingsStatus(
                audioPermissionGranted = true,
                notificationPermissionGranted = true,
                playbackServiceConnected = true
            )
        )

        viewModel.uiState.value.searchEntries
            .first { it.id == SettingsEntryId.Theme }
            .onClick.run()

        assertEquals(SettingsPage.AppearanceGroup, viewModel.state.value.page)
        assertEquals(SettingsEntryId.Theme, viewModel.state.value.highlightedEntryId)
        assertEquals(EchoTheme.MODE_DARK, viewModel.state.value.preferences.themeMode)
        assertTrue(viewModel.state.value.actions.any { it.entryId == SettingsEntryId.Theme })
        assertEquals(
            listOf(SettingsEffect.NavigatePage(SettingsPage.AppearanceGroup)),
            viewModel.drainEffects()
        )

        viewModel.navigateSettingsPage(SettingsPage.Home)

        assertEquals(null, viewModel.state.value.highlightedEntryId)
    }

    @Test
    fun navigateLibrarySettingsPageEmitsRouteSyncEffect() {
        val effects = mutableListOf<SettingsEffect>()
        val viewModel = SettingsViewModel()
        viewModel.bindEffectListener { effect -> effects += effect }

        viewModel.navigateSettingsPage(SettingsPage.LibraryGroup)
        viewModel.navigateSettingsPage(SettingsPage.Library)

        assertEquals(SettingsPage.Library, viewModel.state.value.page)
        assertEquals(
            listOf(
                SettingsEffect.NavigatePage(SettingsPage.LibraryGroup),
                SettingsEffect.NavigatePage(SettingsPage.Library)
            ),
            effects
        )
        assertEquals(emptyList<SettingsEffect>(), viewModel.drainEffects())
    }

    @Test
    fun librarySettingsBackActionsNavigateThroughExpectedParents() {
        val viewModel = SettingsViewModel()
        viewModel.publishCurrentPage(
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
    fun routeStateDrivesCurrentPageWithoutHostRendering() = runTest {
        val viewModel = SettingsViewModel()
        val routes = MutableStateFlow(
            SettingsRouteState(active = true, page = SettingsPage.PlaybackGroup)
        )

        viewModel.bindRouteState(routes)
        advanceUntilIdle()

        assertEquals(SettingsPage.PlaybackGroup, viewModel.state.value.page)
        assertEquals(viewModel.state.value.ui, viewModel.uiState.value)

        routes.value = SettingsRouteState(active = true, page = SettingsPage.Lyrics)
        advanceUntilIdle()

        assertEquals(SettingsPage.Lyrics, viewModel.state.value.page)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_SYSTEM, "lyrics"),
            viewModel.uiState.value.title
        )
    }

    @Test
    fun enteringSettingsRefreshesRuntimeContextEvenWhenPageIsUnchanged() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val routes = MutableStateFlow(SettingsRouteState(page = SettingsPage.Home))
        var loads = 0
        viewModel.bindContextLoader {
            loads += 1
            SettingsContextSnapshot(SettingsPreferencesSnapshot(), RuntimeSettingsStatus())
        }
        viewModel.bindRouteState(routes)
        advanceUntilIdle()

        routes.value = SettingsRouteState(active = true, page = SettingsPage.Home)
        advanceUntilIdle()
        routes.value = SettingsRouteState(page = SettingsPage.Home)
        advanceUntilIdle()
        routes.value = SettingsRouteState(active = true, page = SettingsPage.Home)
        advanceUntilIdle()

        assertEquals(2, loads)
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
    fun refreshSettingsContextLoadsOffTheCallerPathAndRebuildsRenderedPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferences = SettingsPreferencesSnapshot(languageMode = AppLanguage.MODE_ENGLISH)
        val runtime = RuntimeSettingsStatus(librarySongCount = 12)
        var loads = 0
        viewModel.bindContextLoader {
            loads += 1
            SettingsContextSnapshot(preferences, runtime)
        }
        viewModel.publishCurrentPage(
            SettingsPage.Library,
            SettingsPreferencesSnapshot(),
            RuntimeSettingsStatus()
        )

        viewModel.refreshSettingsContext()
        assertEquals(0, loads)
        advanceUntilIdle()

        assertEquals(1, loads)
        assertEquals(preferences, viewModel.state.value.preferences)
        assertEquals(runtime, viewModel.state.value.runtime)
        assertEquals(SettingsPage.Library, viewModel.state.value.page)
        assertTrue(viewModel.uiState.value.metrics.any { it.value == "12" })
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

        viewModel.appearance.applyThemeMode("dark")
        viewModel.appearance.applyAccentMode("teal")
        viewModel.appearance.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        viewModel.playback.applyPlaybackSpeed(2.5f)
        viewModel.playback.applyAppVolume(-0.4f)
        viewModel.playback.applyStreamingAudioQuality("lossless")
        viewModel.appearance.applyShareStyle(TrackShareStyle.CARD)
        viewModel.lyrics.setOnlineLyricsEnabled(true)
        viewModel.lyrics.applyLyricsOffset(5555L)
        viewModel.playback.applyAudioEffectSettings(app.yukine.playback.AudioEffectSettings.DEFAULT.withEnabled(true))
        viewModel.lyrics.setStatusBarLyricsEnabled(false)
        viewModel.lyrics.setSystemMediaLyricsTitleEnabled(true)
        viewModel.lyrics.setFloatingLyricsEnabled(true)
        viewModel.playback.setNowPlayingGesturesEnabled(false)
        viewModel.playback.setPlaybackRestoreEnabled(true)
        viewModel.playback.setReplayGainEnabled(false)
        viewModel.appearance.setDebugPromptsEnabled(true)
        viewModel.appearance.setCustomBackgroundBlurEnabled(true)
        viewModel.appearance.setCustomBackgroundBlurRadiusDp(80f)
        viewModel.appearance.setCompactSettingsCards(true)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "theme",
                "speed:2.0",
                "volume:0.0",
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
                    is SettingsRuntimeEffect.RefreshCustomBackgroundAccent -> "accentRefresh"
                    is SettingsRuntimeEffect.ApplyPlaybackSpeed -> "speed:${effect.speed}"
                    is SettingsRuntimeEffect.ApplyAppVolume -> "volume:${effect.volume}"
                    is SettingsRuntimeEffect.SetOnlineLyricsEnabled -> "onlineLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetLyricsOffsetMs -> "lyricsOffset:${effect.offsetMs}"
                    is SettingsRuntimeEffect.ApplyAudioEffects -> "audioEffects:${effect.settings.enabled}"
                    is SettingsRuntimeEffect.SetStatusBarLyrics -> "statusLyrics:${effect.enabled}"
                    is SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled -> "systemMediaTitle:${effect.enabled}"
                    is SettingsRuntimeEffect.ApplyFloatingLyrics -> "floatingLyrics:${effect.enabled}"
                    SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings -> "floatingPermission"
                    is SettingsRuntimeEffect.UpdateFloatingLyricsTextSize ->
                        "floatingTextSize:${effect.textSizeSp}"
                    is SettingsRuntimeEffect.UpdateFloatingLyricsWidth ->
                        "floatingWidth:${effect.widthPercent}"
                    is SettingsRuntimeEffect.UpdateFloatingLyricsBackgroundOpacity ->
                        "floatingOpacity:${effect.opacityPercent}"
                    is SettingsRuntimeEffect.UpdateFloatingLyricsTransparentBackground ->
                        "floatingTransparent:${effect.enabled}"
                    SettingsRuntimeEffect.ShowFloatingLyrics -> "floatingShow"
                    SettingsRuntimeEffect.UnlockFloatingLyrics -> "floatingUnlock"
                    SettingsRuntimeEffect.ResetFloatingLyricsLayout -> "floatingReset"
                    is SettingsRuntimeEffect.SetPlaybackRestoreEnabled -> "restore:${effect.enabled}"
                    is SettingsRuntimeEffect.SetReplayGainEnabled -> "replayGain:${effect.enabled}"
                    is SettingsRuntimeEffect.SetAudioExclusiveEnabled -> "audioExclusive:${effect.enabled}"
                    is SettingsRuntimeEffect.SetBitPerfectEnabled -> "bitPerfect:${effect.enabled}"
                    is SettingsRuntimeEffect.SetUsbExclusiveEnabled -> "usbExclusive:${effect.enabled}"
                    is SettingsRuntimeEffect.UpdateFloatingLyricsTextColor ->
                        "floatingTextColor:${effect.colorArgb}"
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
                "customBackgroundBlurRadius:64.0",
                "compactSettingsCards:true"
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
        assertEquals(false, state.preferences.statusBarLyricsEnabled)
        assertEquals(true, state.preferences.systemMediaLyricsTitleEnabled)
        assertEquals(true, state.preferences.floatingLyricsEnabled)
        assertEquals(false, state.preferences.nowPlayingGesturesEnabled)
        assertEquals(true, state.preferences.playbackRestoreEnabled)
        assertEquals(true, state.preferences.debugPromptsEnabled)
        assertEquals(true, state.preferences.customBackgroundBlurEnabled)
        assertEquals(64f, state.preferences.customBackgroundBlurRadiusDp)
        assertEquals(true, state.preferences.compactSettingsCards)
        assertEquals(true, viewModel.chromeState.value.customBackgroundBlurEnabled)
        assertEquals(64f, viewModel.chromeState.value.customBackgroundBlurRadiusDp)
        assertEquals(true, viewModel.chromeState.value.compactSettingsCards)
        assertEquals(5000L, state.runtime.lyricsOffsetMs)
        assertEquals(state.ui, viewModel.uiState.value)
    }

    @Test
    fun failedPreferenceWriteEmitsFailureStatus() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway().also { it.failWrites = true }
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.publishCurrentPage(
            SettingsPage.Appearance,
            SettingsPreferencesSnapshot(languageMode = AppLanguage.MODE_ENGLISH),
            RuntimeSettingsStatus()
        )

        viewModel.appearance.applyThemeMode(EchoTheme.MODE_DARK)
        advanceUntilIdle()

        assertEquals(EchoTheme.MODE_SYSTEM, viewModel.state.value.preferences.themeMode)
        val statuses = viewModel.drainEffects()
            .filterIsInstance<SettingsEffect.ShowStatus>()
            .map { it.message }
        assertTrue(statuses.last().contains("Failed to save"))
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
        viewModel.publishCurrentPage(SettingsPage.PageBackground, preferences, RuntimeSettingsStatus())

        viewModel.appearance.clearPageBackground(PageBackgrounds.PAGE_SETTINGS)
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
    fun publishCurrentPageBuildsUiFromSnapshotsAndRoutesActionsThroughViewModel() {
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

        val content = viewModel.publishCurrentPage(SettingsPage.StreamingGateway, preferences, runtime)

        assertEquals(SettingsPage.StreamingGateway, viewModel.state.value.page)
        assertEquals(preferences, viewModel.state.value.preferences)
        assertEquals(runtime, viewModel.state.value.runtime)
        assertEquals(content.uiState, viewModel.state.value.ui)
        assertEquals(content.uiState, viewModel.uiState.value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.gateway"), content.uiState.title)
        assertEquals(5, content.actions.size)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[4].onClick.run()

        assertEquals(
            listOf(
                SettingsEffect.NavigatePage(SettingsPage.SourcesGroup),
                SettingsEffect.ApplyStreamingGatewayEndpoint(StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT),
                SettingsEffect.EditMusicBrainzProxy
            ),
            viewModel.drainEffects()
        )
        assertEquals(SettingsPage.SourcesGroup, viewModel.state.value.page)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.settings"), viewModel.state.value.ui.title)
    }

    @Test
    fun luoxueSettingsActionsEmitDirectManagerAndImportEffects() {
        val viewModel = SettingsViewModel()

        viewModel.network.openLuoxueSourceManager()
        viewModel.network.importLuoxueSource()

        assertEquals(
            listOf(
                SettingsEffect.OpenLuoxueSourceManager,
                SettingsEffect.ImportLuoxueSource
            ),
            viewModel.drainEffects()
        )
    }

    @Test
    fun settingActionsNormalizeNotifyAndSavePreferences() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val mirror = FakeSettingsStoreMirror()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindStoreMirror(mirror)
        viewModel.bindRuntimeEffectListener { true }

        viewModel.appearance.applyThemeMode("dark")
        viewModel.appearance.applyAccentMode("teal")
        viewModel.appearance.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        viewModel.playback.applyPlaybackSpeed(2.5f)
        viewModel.playback.applyAppVolume(-0.4f)
        viewModel.playback.applyStreamingAudioQuality("lossless")
        viewModel.appearance.applyShareStyle(TrackShareStyle.CARD)
        viewModel.lyrics.setOnlineLyricsEnabled(true)
        viewModel.lyrics.setStatusBarLyricsEnabled(false)
        viewModel.lyrics.setFloatingLyricsEnabled(true)
        viewModel.playback.setNowPlayingGesturesEnabled(false)
        viewModel.playback.setPlaybackRestoreEnabled(true)
        viewModel.appearance.applyPageBackgrounds(PageBackgrounds(sharedUri = "content://bg"), PageBackgrounds.PAGE_ALL, false)
        viewModel.lyrics.applyLyricsOffset(5555L)
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
            "dark|teal|en|2.0|0.0|lossless|true|false|true|${TrackShareStyle.CARD}|content://bg",
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
        viewModel.publishCurrentPage(
            SettingsPage.Lyrics,
            SettingsPreferencesSnapshot(statusBarLyricsEnabled = true, floatingLyricsEnabled = false),
            RuntimeSettingsStatus()
        )

        viewModel.lyrics.setFloatingLyricsEnabled(true)
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
    fun rejectedFloatingLyricsEnableDoesNotPersistAnInactiveSetting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindRuntimeEffectListener { effect ->
            effect !is SettingsRuntimeEffect.ApplyFloatingLyrics || !effect.enabled
        }
        viewModel.publishCurrentPage(
            SettingsPage.FloatingLyrics,
            SettingsPreferencesSnapshot(statusBarLyricsEnabled = true, floatingLyricsEnabled = false),
            RuntimeSettingsStatus(overlayPermissionGranted = false)
        )

        viewModel.lyrics.setFloatingLyricsEnabled(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.preferences.statusBarLyricsEnabled)
        assertEquals(false, viewModel.state.value.preferences.floatingLyricsEnabled)
        assertEquals(emptyList<String>(), preferenceGateway.events)
        assertTrue(
            viewModel.drainEffects()
                .filterIsInstance<SettingsEffect.ShowStatus>()
                .any { it.message == AppLanguage.text(AppLanguage.MODE_SYSTEM, "floating.lyrics.permission.required") }
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
        viewModel.publishCurrentPage(
            SettingsPage.Lyrics,
            SettingsPreferencesSnapshot(statusBarLyricsEnabled = false, floatingLyricsEnabled = true),
            RuntimeSettingsStatus()
        )

        viewModel.lyrics.setStatusBarLyricsEnabled(true)
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

        val status = SettingsStatusTextFactory.applied(
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
        var failWrites: Boolean = false

        override fun save(update: SettingsPreferenceUpdate): Boolean {
            events += when (update.key) {
                SettingsPreferenceKey.ThemeMode -> "theme:${update.value}"
                SettingsPreferenceKey.AccentMode -> "accent:${update.value}"
                SettingsPreferenceKey.LanguageMode -> "language:${update.value}"
                SettingsPreferenceKey.PlaybackSpeed -> "speed:${update.value}"
                SettingsPreferenceKey.AppVolume -> "volume:${update.value}"
                SettingsPreferenceKey.StreamingAudioQuality -> "quality:${update.value}"
                SettingsPreferenceKey.RefuseAutomaticQualityDowngrade ->
                    "refuseQualityDowngrade:${update.value}"
                SettingsPreferenceKey.OnlineLyricsEnabled -> "onlineLyrics:${update.value}"
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
                SettingsPreferenceKey.AudioExclusiveEnabled -> "audioExclusive:${update.value}"
                SettingsPreferenceKey.BitPerfectEnabled -> "bitPerfect:${update.value}"
                SettingsPreferenceKey.UsbExclusiveEnabled -> "usbExclusive:${update.value}"
                SettingsPreferenceKey.DebugPromptsEnabled -> "debugPrompts:${update.value}"
                SettingsPreferenceKey.CheckUpdateEnabled -> "checkUpdate:${update.value}"
                SettingsPreferenceKey.CustomBackgroundBlurEnabled ->
                    "customBackgroundBlurEnabled:${update.value}"
                SettingsPreferenceKey.CustomBackgroundBlurRadiusDp ->
                    "customBackgroundBlurRadius:${update.value}"
                SettingsPreferenceKey.GlassBlurEnabled -> "glassBlurEnabled:${update.value}"
                SettingsPreferenceKey.GlassBlurRadiusDp -> "glassBlurRadius:${update.value}"
                SettingsPreferenceKey.GlassSurfaceOpacity -> "glassSurfaceOpacity:${update.value}"
                SettingsPreferenceKey.CompactSettingsCards -> "compactSettingsCards:${update.value}"
                SettingsPreferenceKey.HomeDashboardLayout -> "homeDashboardLayout:${update.value}"
                SettingsPreferenceKey.ShareStyle -> "shareStyle:${update.value}"
                SettingsPreferenceKey.PageBackgrounds -> {
                    val backgrounds = update.value as PageBackgrounds
                    "background:${backgrounds.sharedUri}"
                }
            }
            return !failWrites
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
                preferences.floatingLyricsEnabled.toString(),
                preferences.nowPlayingGesturesEnabled.toString(),
                preferences.playbackRestoreEnabled.toString(),
                preferences.shareStyle,
                preferences.pageBackgrounds.sharedUri
            ).joinToString("|")
        }
    }

}
