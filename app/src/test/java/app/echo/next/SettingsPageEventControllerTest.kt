package app.echo.next

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPageEventControllerTest {
    @Test
    fun forwardsSettingsPageActionsToInjectedHandlers() {
        val navigator = FakeNavigator()
        val networkSourcesNavigator = FakeNetworkSourcesNavigator()
        val libraryLoader = FakeLibraryLoader()
        val audioPicker = FakeAudioPicker()
        val lyricsActions = FakeLyricsActions()
        val playbackTimer = FakePlaybackTimer()
        val settingsActions = FakeSettingsActions()
        val streamingGatewayActions = FakeStreamingGatewayActions()
        val controller = SettingsPageEventController(
            navigator,
            networkSourcesNavigator,
            libraryLoader,
            audioPicker,
            lyricsActions,
            playbackTimer,
            settingsActions,
            streamingGatewayActions,
            FakeContentSink()
        )

        controller.navigateSettingsPage(MainRoutes.SETTINGS_LIBRARY)
        controller.openNetworkSources()
        controller.loadLibrary()
        controller.openAudioFilePicker()
        controller.openAudioFolderPicker()
        controller.setOnlineLyricsEnabled(true)
        controller.reloadCurrentLyrics()
        controller.applyLyricsOffset(500L)
        controller.startSleepTimer(30)
        controller.cancelSleepTimer()
        controller.applyPlaybackSpeed(1.25f)
        controller.applyAppVolume(0.7f)
        controller.applyStreamingAudioQuality(StreamingQualityPreference.HIGH)
        controller.setConcurrentPlaybackEnabled(true)
        controller.applyThemeMode("dark")
        controller.applyAccentMode("teal")
        controller.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        controller.applyStreamingGatewayEndpoint("http://localhost:3301")

        assertEquals(listOf("nav:${MainRoutes.SETTINGS_LIBRARY}"), navigator.calls)
        assertEquals(listOf("network"), networkSourcesNavigator.calls)
        assertEquals(listOf("load"), libraryLoader.calls)
        assertEquals(listOf("file", "folder"), audioPicker.calls)
        assertEquals(listOf("online:true", "reload", "offset:500"), lyricsActions.calls)
        assertEquals(listOf("start:30", "cancel"), playbackTimer.calls)
        assertEquals(
            listOf("speed:1.25", "volume:0.7", "quality:high", "concurrent:true", "theme:dark", "accent:teal", "language:${AppLanguage.MODE_ENGLISH}"),
            settingsActions.calls
        )
        assertEquals(listOf("endpoint:http://localhost:3301"), streamingGatewayActions.calls)
    }

    private class FakeNavigator : SettingsPageEventController.Navigator {
        val calls = ArrayList<String>()

        override fun navigateSettingsPage(page: String) {
            calls.add("nav:$page")
        }
    }

    private class FakeNetworkSourcesNavigator : SettingsPageEventController.NetworkSourcesNavigator {
        val calls = ArrayList<String>()

        override fun openNetworkSources() {
            calls.add("network")
        }
    }

    private class FakeLibraryLoader : SettingsPageEventController.LibraryLoader {
        val calls = ArrayList<String>()

        override fun loadLibrary() {
            calls.add("load")
        }
    }

    private class FakeAudioPicker : SettingsPageEventController.AudioPicker {
        val calls = ArrayList<String>()

        override fun openAudioFilePicker() {
            calls.add("file")
        }

        override fun openAudioFolderPicker() {
            calls.add("folder")
        }
    }

    private class FakeLyricsActions : SettingsPageEventController.LyricsActions {
        val calls = ArrayList<String>()

        override fun setOnlineLyricsEnabled(enabled: Boolean) {
            calls.add("online:$enabled")
        }

        override fun reloadCurrentLyrics() {
            calls.add("reload")
        }

        override fun applyLyricsOffset(offsetMs: Long) {
            calls.add("offset:$offsetMs")
        }
    }

    private class FakePlaybackTimer : SettingsPageEventController.PlaybackTimer {
        val calls = ArrayList<String>()

        override fun startSleepTimer(minutes: Int) {
            calls.add("start:$minutes")
        }

        override fun cancelSleepTimer() {
            calls.add("cancel")
        }
    }

    private class FakeSettingsActions : SettingsPageEventController.SettingsActions {
        val calls = ArrayList<String>()

        override fun applyPlaybackSpeed(speed: Float) {
            calls.add("speed:$speed")
        }

        override fun applyAppVolume(volume: Float) {
            calls.add("volume:$volume")
        }

        override fun applyStreamingAudioQuality(quality: String) {
            calls.add("quality:$quality")
        }

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
            calls.add("concurrent:$enabled")
        }

        override fun applyThemeMode(mode: String) {
            calls.add("theme:$mode")
        }

        override fun applyAccentMode(accent: String) {
            calls.add("accent:$accent")
        }

        override fun applyLanguageMode(languageMode: String) {
            calls.add("language:$languageMode")
        }
    }

    private class FakeStreamingGatewayActions : SettingsPageEventController.StreamingGatewayActions {
        val calls = ArrayList<String>()

        override fun applyEndpoint(endpoint: String) {
            calls.add("endpoint:$endpoint")
        }
    }

    private class FakeContentSink : SettingsPageEventController.ContentSink {
        override fun addVirtualContent(view: View) = Unit
    }
}
