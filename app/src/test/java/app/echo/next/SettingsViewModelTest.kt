package app.echo.next

import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsMetric
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
                "lyricsOffset:5000"
            ),
            listener.events
        )
        assertEquals(listener.events, preferenceGateway.events)
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

        override fun applyStreamingAudioQuality(quality: String) {
            events += "quality:$quality"
        }

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
            events += "concurrent:$enabled"
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

        override fun onStreamingAudioQualityApplied(quality: String) {
            events += "quality:$quality"
        }

        override fun onConcurrentPlaybackEnabledApplied(enabled: Boolean) {
            events += "concurrent:$enabled"
        }

        override fun onOnlineLyricsEnabledApplied(enabled: Boolean) {
            events += "onlineLyrics:$enabled"
        }

        override fun onLyricsOffsetApplied(offsetMs: Long) {
            events += "lyricsOffset:$offsetMs"
        }
    }
}
