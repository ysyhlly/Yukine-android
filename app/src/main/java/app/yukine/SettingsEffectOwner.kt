package app.yukine

import app.yukine.identity.LibraryDedupMode
import java.util.function.Consumer
import java.util.function.BiConsumer
import java.util.function.IntConsumer

internal data class SettingsNavigationEffectActions(
    val showStatus: Consumer<String>,
    val navigatePage: Consumer<SettingsPage>,
    val openNetworkPage: Consumer<NetworkPage>,
    val openDownloads: Runnable,
    val checkGitHubUpdate: Runnable = Runnable {}
)

internal data class SettingsLibraryEffectActions @JvmOverloads constructor(
    val requestNeededPermissions: Runnable,
    val loadLibrary: Runnable,
    val openAudioFilePicker: Runnable,
    val openAudioFolderPicker: Runnable,
    val rebuildSongIdentity: Runnable,
    val cancelIdentityBackfill: Runnable,
    val openLuoxueSourceManager: Runnable,
    val importLuoxueSource: Runnable,
    val restoreHiddenLibraryItem: Consumer<String>,
    val restoreAllHiddenLibraryItems: Runnable,
    val setLibraryDedupMode: Consumer<LibraryDedupMode> = Consumer {},
    val confirmDuplicateCandidate: BiConsumer<Long, Long> = BiConsumer { _, _ -> },
    val confirmHighConfidenceDuplicates: Runnable = Runnable {}
)

internal data class SettingsPlaybackEffectActions @JvmOverloads constructor(
    val reloadCurrentLyrics: Runnable,
    val startSleepTimer: IntConsumer,
    val cancelSleepTimer: Runnable,
    val openFloatingLyricsPermission: Runnable,
    val importCurrentLyrics: Runnable = Runnable {},
    val importLyricsDirectory: Runnable = Runnable {},
    val viewLyricsImportReport: Runnable = Runnable {}
)

internal data class SettingsFileEffectActions(
    val choosePageBackground: Consumer<String>,
    val exportBackup: Runnable,
    val importBackup: Runnable,
    val exportDiagnostics: Runnable
)

internal data class SettingsStreamingEffectActions(
    val applyGatewayEndpoint: Consumer<String>,
    val editMusicBrainzProxy: Runnable,
    val setKugouExperimentalSyncEnabled: Consumer<Boolean> = Consumer {}
)

/** Exhaustively consumes one-time settings effects at focused platform boundaries. */
internal class SettingsEffectOwner(
    private val navigation: SettingsNavigationEffectActions,
    private val library: SettingsLibraryEffectActions,
    private val playback: SettingsPlaybackEffectActions,
    private val files: SettingsFileEffectActions,
    private val streaming: SettingsStreamingEffectActions
) : SettingsEffectListener {
    override fun onEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.ShowStatus -> navigation.showStatus.accept(effect.message)
            is SettingsEffect.NavigatePage -> navigation.navigatePage.accept(effect.page)
            is SettingsEffect.OpenNetworkPage -> navigation.openNetworkPage.accept(effect.page)
            SettingsEffect.OpenDownloads -> navigation.openDownloads.run()
            SettingsEffect.RequestNeededPermissions -> library.requestNeededPermissions.run()
            SettingsEffect.LoadLibrary -> library.loadLibrary.run()
            SettingsEffect.OpenAudioFilePicker -> library.openAudioFilePicker.run()
            SettingsEffect.OpenAudioFolderPicker -> library.openAudioFolderPicker.run()
            SettingsEffect.RebuildSongIdentity -> library.rebuildSongIdentity.run()
            SettingsEffect.CancelIdentityBackfill -> library.cancelIdentityBackfill.run()
            is SettingsEffect.SetLibraryDedupMode -> library.setLibraryDedupMode.accept(effect.mode)
            is SettingsEffect.ConfirmDuplicateCandidate ->
                library.confirmDuplicateCandidate.accept(
                    effect.leftRecordingId,
                    effect.rightRecordingId
                )
            SettingsEffect.ConfirmHighConfidenceDuplicates ->
                library.confirmHighConfidenceDuplicates.run()
            SettingsEffect.OpenLuoxueSourceManager -> library.openLuoxueSourceManager.run()
            SettingsEffect.ImportLuoxueSource -> library.importLuoxueSource.run()
            is SettingsEffect.RestoreHiddenLibraryItem ->
                library.restoreHiddenLibraryItem.accept(effect.sourceKey)
            SettingsEffect.RestoreAllHiddenLibraryItems -> library.restoreAllHiddenLibraryItems.run()
            SettingsEffect.ReloadCurrentLyrics -> playback.reloadCurrentLyrics.run()
            SettingsEffect.ImportCurrentLyrics -> playback.importCurrentLyrics.run()
            SettingsEffect.ImportLyricsDirectory -> playback.importLyricsDirectory.run()
            SettingsEffect.ViewLyricsImportReport -> playback.viewLyricsImportReport.run()
            is SettingsEffect.StartSleepTimer -> playback.startSleepTimer.accept(effect.minutes)
            SettingsEffect.CancelSleepTimer -> playback.cancelSleepTimer.run()
            SettingsEffect.OpenFloatingLyricsPermission -> playback.openFloatingLyricsPermission.run()
            is SettingsEffect.ChoosePageBackground -> files.choosePageBackground.accept(effect.page)
            SettingsEffect.ExportBackup -> files.exportBackup.run()
            SettingsEffect.ImportBackup -> files.importBackup.run()
            SettingsEffect.ExportDiagnostics -> files.exportDiagnostics.run()
            is SettingsEffect.ApplyStreamingGatewayEndpoint ->
                streaming.applyGatewayEndpoint.accept(effect.endpoint)
            is SettingsEffect.SetKugouExperimentalSyncEnabled ->
                streaming.setKugouExperimentalSyncEnabled.accept(effect.enabled)
            SettingsEffect.EditMusicBrainzProxy -> streaming.editMusicBrainzProxy.run()
            SettingsEffect.CheckGitHubUpdate -> navigation.checkGitHubUpdate.run()
        }
    }
}
