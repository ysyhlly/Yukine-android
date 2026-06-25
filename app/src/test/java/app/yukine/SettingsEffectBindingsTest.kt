package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEffectBindingsTest {
    @Test
    fun forwardsSettingsEffectsToActivityEdges() {
        val calls = mutableListOf<String>()
        val bindings = SettingsEffectBindings(
            statusDisplayer = SettingsStatusDisplayer { message -> calls += "status:$message" },
            networkSourcesOpener = SettingsNetworkSourcesOpener { calls += "network" },
            downloadsOpener = SettingsDownloadsOpener { calls += "downloads" },
            libraryLoader = SettingsLibraryLoader { calls += "loadLibrary" },
            audioFilePickerOpener = SettingsAudioFilePickerOpener { calls += "file" },
            audioFolderPickerOpener = SettingsAudioFolderPickerOpener { calls += "folder" },
            currentLyricsReloader = SettingsCurrentLyricsReloader { calls += "reloadLyrics" },
            sleepTimerStarter = SettingsSleepTimerStarter { minutes -> calls += "sleep:$minutes" },
            sleepTimerCanceller = Runnable { calls += "cancelSleep" },
            floatingLyricsPermissionOpener = SettingsFloatingLyricsPermissionOpener { calls += "floatingPermission" },
            pageBackgroundChooser = SettingsPageBackgroundChooser { page -> calls += "background:$page" },
            backupExporter = Runnable { calls += "export" },
            backupImporter = Runnable { calls += "import" },
            streamingGatewayEndpointApplier = SettingsStreamingGatewayEndpointApplier { endpoint -> calls += "gateway:$endpoint" }
        )

        listOf(
            SettingsEffect.ShowStatus("saved"),
            SettingsEffect.OpenNetworkSources,
            SettingsEffect.OpenDownloads,
            SettingsEffect.LoadLibrary,
            SettingsEffect.OpenAudioFilePicker,
            SettingsEffect.OpenAudioFolderPicker,
            SettingsEffect.ReloadCurrentLyrics,
            SettingsEffect.StartSleepTimer(20),
            SettingsEffect.CancelSleepTimer,
            SettingsEffect.OpenFloatingLyricsPermission,
            SettingsEffect.ChoosePageBackground(PageBackgrounds.PAGE_SETTINGS),
            SettingsEffect.ExportBackup,
            SettingsEffect.ImportBackup,
            SettingsEffect.ApplyStreamingGatewayEndpoint("http://127.0.0.1:43990")
        ).forEach(bindings::onEffect)

        assertEquals(
            listOf(
                "status:saved",
                "network",
                "downloads",
                "loadLibrary",
                "file",
                "folder",
                "reloadLyrics",
                "sleep:20",
                "cancelSleep",
                "floatingPermission",
                "background:settings",
                "export",
                "import",
                "gateway:http://127.0.0.1:43990"
            ),
            calls
        )
    }
}
