package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
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
    fun updatePagePublishesTitleMetricsAndItems() {
        val viewModel = SettingsViewModel()
        val metrics = listOf(SettingsMetric("Theme", "System"))
        val actions = listOf(
            SettingsAction("Appearance", Runnable { }, "Theme and accent"),
            SettingsAction("Reload", Runnable { })
        )

        viewModel.updatePage("Settings", metrics, actions)

        val state = viewModel.uiState.value
        assertEquals("Settings", state.title)
        assertEquals(metrics, state.metrics)
        assertEquals(3, state.items.size)
        assertTrue(state.items[0] is SettingsItem.Navigation)
        assertTrue(state.items[1] is SettingsItem.Action)
        assertEquals(SettingsItem.Metric("Theme", "System"), state.items[2])
    }

    @Test
    fun onEventDelegatesSettingsActionsToGateway() {
        val gateway = FakeSettingsGateway()
        val viewModel = SettingsViewModel()
        viewModel.bindGateway(gateway)

        viewModel.onEvent(SettingsEvent.NavigateSettingsPage("appearance"))
        viewModel.onEvent(SettingsEvent.OpenNetworkSources)
        viewModel.onEvent(SettingsEvent.LoadLibrary)
        viewModel.onEvent(SettingsEvent.OpenAudioFilePicker)
        viewModel.onEvent(SettingsEvent.OpenAudioFolderPicker)
        viewModel.onEvent(SettingsEvent.SetOnlineLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.ReloadCurrentLyrics)
        viewModel.onEvent(SettingsEvent.ApplyLyricsOffset(500L))
        viewModel.onEvent(SettingsEvent.StartSleepTimer(30))
        viewModel.onEvent(SettingsEvent.CancelSleepTimer)
        viewModel.onEvent(SettingsEvent.ApplyPlaybackSpeed(1.25f))
        viewModel.onEvent(SettingsEvent.ApplyAppVolume(0.85f))
        viewModel.onEvent(SettingsEvent.ApplyStreamingAudioQuality("lossless"))
        viewModel.onEvent(SettingsEvent.SetConcurrentPlaybackEnabled(false))
        viewModel.onEvent(SettingsEvent.SetStatusBarLyricsEnabled(false))
        viewModel.onEvent(SettingsEvent.SetFloatingLyricsEnabled(true))
        viewModel.onEvent(SettingsEvent.OpenFloatingLyricsPermission)
        viewModel.onEvent(SettingsEvent.SetNowPlayingGesturesEnabled(false))
        viewModel.onEvent(SettingsEvent.SetPlaybackRestoreEnabled(true))
        viewModel.onEvent(SettingsEvent.SetReplayGainEnabled(false))
        viewModel.onEvent(SettingsEvent.ExportBackup)
        viewModel.onEvent(SettingsEvent.ImportBackup)
        viewModel.onEvent(SettingsEvent.ApplyThemeMode("dark"))
        viewModel.onEvent(SettingsEvent.ApplyAccentMode("blue"))
        viewModel.onEvent(SettingsEvent.ApplyLanguageMode("zh"))
        viewModel.onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint("http://127.0.0.1:3000"))

        assertEquals(
            listOf(
                "navigate:appearance",
                "network",
                "loadLibrary",
                "audioFile",
                "audioFolder",
                "onlineLyrics:true",
                "reloadLyrics",
                "lyricsOffset:500",
                "sleep:30",
                "cancelSleep",
                "speed:1.25",
                "volume:0.85",
                "quality:lossless",
                "concurrent:false",
                "statusLyrics:false",
                "floatingLyrics:true",
                "floatingPermission",
                "gestures:false",
                "restore:true",
                "replayGain:false",
                "exportBackup",
                "importBackup",
                "theme:dark",
                "accent:blue",
                "language:zh",
                "gateway:http://127.0.0.1:3000"
            ),
            gateway.events
        )
    }

    @Test
    fun onEventWithoutGatewayDoesNotCrash() {
        val viewModel = SettingsViewModel()

        viewModel.onEvent(SettingsEvent.LoadLibrary)

        assertEquals(SettingsUiState(), viewModel.uiState.value)
    }

    @Test
    fun settingActionsNormalizeNotifyAndSavePreferences() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(dispatcher)
        val preferenceGateway = FakePreferenceGateway()
        val listener = FakeAppliedListener()
        viewModel.bindPreferenceGateway(preferenceGateway)
        viewModel.bindAppliedListener(listener)

        viewModel.applyThemeMode("dark")
        viewModel.applyAccentMode("teal")
        viewModel.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        viewModel.applyPlaybackSpeed(2.5f)
        viewModel.applyAppVolume(-0.4f)
        viewModel.applyStreamingAudioQuality("lossless")
        viewModel.setOnlineLyricsEnabled(true)
        viewModel.setConcurrentPlaybackEnabled(false)
        viewModel.setStatusBarLyricsEnabled(false)
        viewModel.setFloatingLyricsEnabled(true)
        viewModel.setNowPlayingGesturesEnabled(false)
        viewModel.setPlaybackRestoreEnabled(true)
        viewModel.setReplayGainEnabled(false)
        viewModel.applyLyricsOffset(5555L)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "theme:dark",
                "accent:teal",
                "language:en",
                "speed:2.0",
                "volume:0.0",
                "quality:lossless",
                "onlineLyrics:true",
                "concurrent:false",
                "statusLyrics:false",
                "floatingLyrics:true",
                "gestures:false",
                "restore:true",
                "replayGain:false",
                "lyricsOffset:5000"
            ),
            listener.events
        )
        assertEquals(listener.events, preferenceGateway.events)
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
                    SettingsPageRenderController.playbackSpeedLabel(1.25f),
            status.playbackSpeedApplied
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "volume.applied") +
                    SettingsPageRenderController.appVolumeLabel(0.8f),
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
                    SettingsPageRenderController.lyricsOffsetLabel(-300L),
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
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "replay.gain.enabled"),
            status.replayGainEnabled
        )
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "replay.gain.disabled"),
            status.replayGainDisabled
        )
    }

    private class FakeSettingsGateway : SettingsGateway {
        val events = mutableListOf<String>()

        override fun navigateSettingsPage(page: String) {
            events += "navigate:$page"
        }

        override fun openNetworkSources() {
            events += "network"
        }

        override fun loadLibrary() {
            events += "loadLibrary"
        }

        override fun openAudioFilePicker() {
            events += "audioFile"
        }

        override fun openAudioFolderPicker() {
            events += "audioFolder"
        }

        override fun setOnlineLyricsEnabled(enabled: Boolean) {
            events += "onlineLyrics:$enabled"
        }

        override fun reloadCurrentLyrics() {
            events += "reloadLyrics"
        }

        override fun applyLyricsOffset(offsetMs: Long) {
            events += "lyricsOffset:$offsetMs"
        }

        override fun startSleepTimer(minutes: Int) {
            events += "sleep:$minutes"
        }

        override fun cancelSleepTimer() {
            events += "cancelSleep"
        }

        override fun applyPlaybackSpeed(speed: Float) {
            events += "speed:$speed"
        }

        override fun applyAppVolume(volume: Float) {
            events += "volume:$volume"
        }

        override fun applyAudioEffectSettings(settings: app.yukine.playback.AudioEffectSettings) {
            events += "audioEffects:${settings.enabled}"
        }

        override fun applyStreamingAudioQuality(quality: String) {
            events += "quality:$quality"
        }

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
            events += "concurrent:$enabled"
        }

        override fun setStatusBarLyricsEnabled(enabled: Boolean) {
            events += "statusLyrics:$enabled"
        }

        override fun setFloatingLyricsEnabled(enabled: Boolean) {
            events += "floatingLyrics:$enabled"
        }

        override fun openFloatingLyricsPermission() {
            events += "floatingPermission"
        }

        override fun setNowPlayingGesturesEnabled(enabled: Boolean) {
            events += "gestures:$enabled"
        }

        override fun setPlaybackRestoreEnabled(enabled: Boolean) {
            events += "restore:$enabled"
        }

        override fun setReplayGainEnabled(enabled: Boolean) {
            events += "replayGain:$enabled"
        }

        override fun exportBackup() {
            events += "exportBackup"
        }

        override fun importBackup() {
            events += "importBackup"
        }

        override fun applyThemeMode(mode: String) {
            events += "theme:$mode"
        }

        override fun applyAccentMode(accent: String) {
            events += "accent:$accent"
        }

        override fun applyLanguageMode(languageMode: String) {
            events += "language:$languageMode"
        }

        override fun applyStreamingGatewayEndpoint(endpoint: String) {
            events += "gateway:$endpoint"
        }
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
                SettingsPreferenceKey.AudioEffectSettings -> "audioEffects:${update.value}"
                SettingsPreferenceKey.StatusBarLyricsEnabled -> "statusLyrics:${update.value}"
                SettingsPreferenceKey.FloatingLyricsEnabled -> "floatingLyrics:${update.value}"
                SettingsPreferenceKey.NowPlayingGesturesEnabled -> "gestures:${update.value}"
                SettingsPreferenceKey.PlaybackRestoreEnabled -> "restore:${update.value}"
                SettingsPreferenceKey.ReplayGainEnabled -> "replayGain:${update.value}"
            }
        }
    }

    private class FakeAppliedListener : SettingsAppliedListener {
        val events = mutableListOf<String>()

        override fun onThemeModeApplied(mode: String) {
            events += "theme:$mode"
        }

        override fun onAccentModeApplied(accent: String) {
            events += "accent:$accent"
        }

        override fun onLanguageModeApplied(languageMode: String) {
            events += "language:$languageMode"
        }

        override fun onPlaybackSpeedApplied(speed: Float) {
            events += "speed:$speed"
        }

        override fun onAppVolumeApplied(volume: Float) {
            events += "volume:$volume"
        }

        override fun onAudioEffectSettingsApplied(settings: app.yukine.playback.AudioEffectSettings) {
            events += "audioEffects:${settings.enabled}"
        }

        override fun onStreamingAudioQualityApplied(quality: String) {
            events += "quality:$quality"
        }

        override fun onConcurrentPlaybackEnabledApplied(enabled: Boolean) {
            events += "concurrent:$enabled"
        }

        override fun onStatusBarLyricsEnabledApplied(enabled: Boolean) {
            events += "statusLyrics:$enabled"
        }

        override fun onFloatingLyricsEnabledApplied(enabled: Boolean) {
            events += "floatingLyrics:$enabled"
        }

        override fun onFloatingLyricsPermissionRequested() {
            events += "floatingPermission"
        }

        override fun onNowPlayingGesturesEnabledApplied(enabled: Boolean) {
            events += "gestures:$enabled"
        }

        override fun onPlaybackRestoreEnabledApplied(enabled: Boolean) {
            events += "restore:$enabled"
        }

        override fun onReplayGainEnabledApplied(enabled: Boolean) {
            events += "replayGain:$enabled"
        }

        override fun onOnlineLyricsEnabledApplied(enabled: Boolean) {
            events += "onlineLyrics:$enabled"
        }

        override fun onLyricsOffsetApplied(offsetMs: Long) {
            events += "lyricsOffset:$offsetMs"
        }
    }
}
