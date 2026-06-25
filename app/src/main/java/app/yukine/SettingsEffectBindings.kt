package app.yukine

internal fun interface SettingsNetworkSourcesOpener {
    fun openNetworkSources()
}

internal fun interface SettingsStatusDisplayer {
    fun showStatus(message: String)
}

internal fun interface SettingsDownloadsOpener {
    fun openDownloads()
}

internal fun interface SettingsLibraryLoader {
    fun loadLibrary()
}

internal fun interface SettingsAudioFilePickerOpener {
    fun openAudioFilePicker()
}

internal fun interface SettingsAudioFolderPickerOpener {
    fun openAudioFolderPicker()
}

internal fun interface SettingsCurrentLyricsReloader {
    fun reloadCurrentLyrics()
}

internal fun interface SettingsSleepTimerStarter {
    fun startSleepTimer(minutes: Int)
}

internal fun interface SettingsFloatingLyricsPermissionOpener {
    fun openFloatingLyricsPermission()
}

internal fun interface SettingsPageBackgroundChooser {
    fun choosePageBackground(page: String)
}

internal fun interface SettingsStreamingGatewayEndpointApplier {
    fun apply(endpoint: String)
}

internal class SettingsEffectBindings(
    private val statusDisplayer: SettingsStatusDisplayer,
    private val networkSourcesOpener: SettingsNetworkSourcesOpener,
    private val downloadsOpener: SettingsDownloadsOpener,
    private val libraryLoader: SettingsLibraryLoader,
    private val audioFilePickerOpener: SettingsAudioFilePickerOpener,
    private val audioFolderPickerOpener: SettingsAudioFolderPickerOpener,
    private val currentLyricsReloader: SettingsCurrentLyricsReloader,
    private val sleepTimerStarter: SettingsSleepTimerStarter,
    private val sleepTimerCanceller: Runnable,
    private val floatingLyricsPermissionOpener: SettingsFloatingLyricsPermissionOpener,
    private val pageBackgroundChooser: SettingsPageBackgroundChooser,
    private val backupExporter: Runnable,
    private val backupImporter: Runnable,
    private val streamingGatewayEndpointApplier: SettingsStreamingGatewayEndpointApplier
) : SettingsEffectListener {
    override fun onEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.ShowStatus -> statusDisplayer.showStatus(effect.message)
            SettingsEffect.OpenNetworkSources -> networkSourcesOpener.openNetworkSources()
            SettingsEffect.OpenDownloads -> downloadsOpener.openDownloads()
            SettingsEffect.LoadLibrary -> libraryLoader.loadLibrary()
            SettingsEffect.OpenAudioFilePicker -> audioFilePickerOpener.openAudioFilePicker()
            SettingsEffect.OpenAudioFolderPicker -> audioFolderPickerOpener.openAudioFolderPicker()
            SettingsEffect.ReloadCurrentLyrics -> currentLyricsReloader.reloadCurrentLyrics()
            is SettingsEffect.StartSleepTimer -> sleepTimerStarter.startSleepTimer(effect.minutes)
            SettingsEffect.CancelSleepTimer -> sleepTimerCanceller.run()
            SettingsEffect.OpenFloatingLyricsPermission ->
                floatingLyricsPermissionOpener.openFloatingLyricsPermission()
            is SettingsEffect.ChoosePageBackground -> pageBackgroundChooser.choosePageBackground(effect.page)
            SettingsEffect.ExportBackup -> backupExporter.run()
            SettingsEffect.ImportBackup -> backupImporter.run()
            is SettingsEffect.ApplyStreamingGatewayEndpoint ->
                streamingGatewayEndpointApplier.apply(effect.endpoint)
        }
    }
}
