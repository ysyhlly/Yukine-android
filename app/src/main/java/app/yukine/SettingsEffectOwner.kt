package app.yukine

import java.util.function.Consumer
import java.util.function.IntConsumer

internal data class SettingsNavigationEffectActions(
    val showStatus: Consumer<String>,
    val navigatePage: Consumer<SettingsPage>,
    val openNetworkPage: Consumer<String>,
    val openDownloads: Runnable
)

internal data class SettingsLibraryEffectActions(
    val requestNeededPermissions: Runnable,
    val loadLibrary: Runnable,
    val openAudioFilePicker: Runnable,
    val openAudioFolderPicker: Runnable,
    val openLuoxueSourceManager: Runnable,
    val importLuoxueSource: Runnable,
    val restoreHiddenLibraryItem: Consumer<String>,
    val restoreAllHiddenLibraryItems: Runnable
)

internal data class SettingsPlaybackEffectActions(
    val reloadCurrentLyrics: Runnable,
    val startSleepTimer: IntConsumer,
    val cancelSleepTimer: Runnable,
    val openFloatingLyricsPermission: Runnable
)

internal data class SettingsFileEffectActions(
    val choosePageBackground: Consumer<String>,
    val exportBackup: Runnable,
    val importBackup: Runnable
)

internal fun interface SettingsStreamingEffectActions {
    fun applyGatewayEndpoint(endpoint: String)
}

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
            SettingsEffect.OpenLuoxueSourceManager -> library.openLuoxueSourceManager.run()
            SettingsEffect.ImportLuoxueSource -> library.importLuoxueSource.run()
            is SettingsEffect.RestoreHiddenLibraryItem ->
                library.restoreHiddenLibraryItem.accept(effect.sourceKey)
            SettingsEffect.RestoreAllHiddenLibraryItems -> library.restoreAllHiddenLibraryItems.run()
            SettingsEffect.ReloadCurrentLyrics -> playback.reloadCurrentLyrics.run()
            is SettingsEffect.StartSleepTimer -> playback.startSleepTimer.accept(effect.minutes)
            SettingsEffect.CancelSleepTimer -> playback.cancelSleepTimer.run()
            SettingsEffect.OpenFloatingLyricsPermission -> playback.openFloatingLyricsPermission.run()
            is SettingsEffect.ChoosePageBackground -> files.choosePageBackground.accept(effect.page)
            SettingsEffect.ExportBackup -> files.exportBackup.run()
            SettingsEffect.ImportBackup -> files.importBackup.run()
            is SettingsEffect.ApplyStreamingGatewayEndpoint ->
                streaming.applyGatewayEndpoint(effect.endpoint)
        }
    }
}
