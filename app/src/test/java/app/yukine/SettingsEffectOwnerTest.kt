package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.function.Consumer
import java.util.function.IntConsumer

class SettingsEffectOwnerTest {
    @Test
    fun everyTypedEffectReachesItsFocusedPlatformAction() {
        val calls = mutableListOf<String>()
        val owner = SettingsEffectOwner(
            SettingsNavigationEffectActions(
                Consumer { calls += "status:$it" },
                Consumer { calls += "page:${it.route}" },
                Consumer { calls += "network:$it" },
                Runnable { calls += "downloads" },
                Runnable { calls += "check-update" }
            ),
            SettingsLibraryEffectActions(
                Runnable { calls += "permissions" },
                Runnable { calls += "library" },
                Runnable { calls += "audio-file" },
                Runnable { calls += "audio-folder" },
                Runnable { calls += "identity-rebuild" },
                Runnable { calls += "identity-cancel" },
                Runnable { calls += "luoxue-manager" },
                Runnable { calls += "luoxue-import" },
                Consumer { calls += "restore:$it" },
                Runnable { calls += "restore-all" }
            ),
            SettingsPlaybackEffectActions(
                Runnable { calls += "lyrics" },
                IntConsumer { calls += "sleep:$it" },
                Runnable { calls += "sleep-cancel" },
                Runnable { calls += "floating-permission" }
            ),
            SettingsFileEffectActions(
                Consumer { calls += "background:$it" },
                Runnable { calls += "backup-export" },
                Runnable { calls += "backup-import" },
                Runnable { calls += "diagnostics-export" }
            ),
            SettingsStreamingEffectActions(
                Consumer { calls += "gateway:$it" },
                Runnable { calls += "musicbrainz-proxy" }
            )
        )

        listOf(
            SettingsEffect.ShowStatus("ready"),
            SettingsEffect.NavigatePage(SettingsPage.Appearance),
            SettingsEffect.OpenNetworkPage(NetworkPage.WebDav),
            SettingsEffect.OpenDownloads,
            SettingsEffect.RequestNeededPermissions,
            SettingsEffect.LoadLibrary,
            SettingsEffect.OpenAudioFilePicker,
            SettingsEffect.OpenAudioFolderPicker,
            SettingsEffect.RebuildSongIdentity,
            SettingsEffect.CancelIdentityBackfill,
            SettingsEffect.OpenLuoxueSourceManager,
            SettingsEffect.ImportLuoxueSource,
            SettingsEffect.RestoreHiddenLibraryItem("hidden-key"),
            SettingsEffect.RestoreAllHiddenLibraryItems,
            SettingsEffect.ReloadCurrentLyrics,
            SettingsEffect.StartSleepTimer(15),
            SettingsEffect.CancelSleepTimer,
            SettingsEffect.OpenFloatingLyricsPermission,
            SettingsEffect.ChoosePageBackground(PageBackgrounds.PAGE_SETTINGS),
            SettingsEffect.ExportBackup,
            SettingsEffect.ImportBackup,
            SettingsEffect.ExportDiagnostics,
            SettingsEffect.ApplyStreamingGatewayEndpoint("http://127.0.0.1:43990"),
            SettingsEffect.EditMusicBrainzProxy,
            SettingsEffect.CheckGitHubUpdate
        ).forEach(owner::onEffect)

        assertEquals(
            listOf(
                "status:ready",
                "page:${SettingsPage.Appearance.route}",
                "network:${NetworkPage.WebDav}",
                "downloads",
                "permissions",
                "library",
                "audio-file",
                "audio-folder",
                "identity-rebuild",
                "identity-cancel",
                "luoxue-manager",
                "luoxue-import",
                "restore:hidden-key",
                "restore-all",
                "lyrics",
                "sleep:15",
                "sleep-cancel",
                "floating-permission",
                "background:${PageBackgrounds.PAGE_SETTINGS}",
                "backup-export",
                "backup-import",
                "diagnostics-export",
                "gateway:http://127.0.0.1:43990",
                "musicbrainz-proxy",
                "check-update"
            ),
            calls
        )
    }
}
