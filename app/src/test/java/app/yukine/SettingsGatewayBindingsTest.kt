package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGatewayBindingsTest {
    @Test
    fun delegatesSettingsGatewayCallsToBindings() {
        val calls = mutableListOf<String>()
        val gateway = SettingsGatewayBindings(
            navigateSettingsPageAction = SettingsStringAction { page -> calls += "page:$page" },
            openNetworkSourcesAction = Runnable { calls += "network" },
            loadLibraryAction = Runnable { calls += "library" },
            openAudioFilePickerAction = Runnable { calls += "file" },
            openAudioFolderPickerAction = Runnable { calls += "folder" },
            onlineLyricsAction = SettingsBooleanAction { enabled -> calls += "lyrics:$enabled" },
            reloadCurrentLyricsAction = Runnable { calls += "reloadLyrics" },
            lyricsOffsetAction = SettingsLongAction { offsetMs -> calls += "offset:$offsetMs" },
            startSleepTimerAction = SettingsIntAction { minutes -> calls += "timer:$minutes" },
            cancelSleepTimerAction = Runnable { calls += "cancelTimer" },
            playbackSpeedAction = SettingsFloatAction { speed -> calls += "speed:$speed" },
            appVolumeAction = SettingsFloatAction { volume -> calls += "volume:$volume" },
            streamingAudioQualityAction = SettingsStringAction { quality -> calls += "quality:$quality" },
            concurrentPlaybackAction = SettingsBooleanAction { enabled -> calls += "concurrent:$enabled" },
            themeModeAction = SettingsStringAction { mode -> calls += "theme:$mode" },
            accentModeAction = SettingsStringAction { accent -> calls += "accent:$accent" },
            languageModeAction = SettingsStringAction { languageMode -> calls += "language:$languageMode" },
            streamingGatewayEndpointAction = SettingsStringAction { endpoint -> calls += "endpoint:$endpoint" }
        )

        gateway.navigateSettingsPage("playback")
        gateway.openNetworkSources()
        gateway.loadLibrary()
        gateway.openAudioFilePicker()
        gateway.openAudioFolderPicker()
        gateway.setOnlineLyricsEnabled(true)
        gateway.reloadCurrentLyrics()
        gateway.applyLyricsOffset(1200L)
        gateway.startSleepTimer(30)
        gateway.cancelSleepTimer()
        gateway.applyPlaybackSpeed(1.25f)
        gateway.applyAppVolume(0.8f)
        gateway.applyStreamingAudioQuality("lossless")
        gateway.setConcurrentPlaybackEnabled(false)
        gateway.applyThemeMode("dark")
        gateway.applyAccentMode("blue")
        gateway.applyLanguageMode("zh")
        gateway.applyStreamingGatewayEndpoint("http://127.0.0.1:8899")

        assertEquals(
            listOf(
                "page:playback",
                "network",
                "library",
                "file",
                "folder",
                "lyrics:true",
                "reloadLyrics",
                "offset:1200",
                "timer:30",
                "cancelTimer",
                "speed:1.25",
                "volume:0.8",
                "quality:lossless",
                "concurrent:false",
                "theme:dark",
                "accent:blue",
                "language:zh",
                "endpoint:http://127.0.0.1:8899"
            ),
            calls
        )
    }
}
