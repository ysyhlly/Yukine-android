package app.echo.next

import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsListScrollState

internal class SettingsPageEventController(
    private val viewModel: SettingsViewModel,
    private val contentSink: ContentSink
) : SettingsPageRenderController.Listener {
    interface ContentSink {
        fun publishSettingsChrome(
            actions: List<SettingsAction>,
            scrollState: SettingsListScrollState
        ) {}
    }

    override fun navigateSettingsPage(page: String) {
        viewModel.onEvent(SettingsEvent.NavigateSettingsPage(page))
    }

    override fun openNetworkSources() {
        viewModel.onEvent(SettingsEvent.OpenNetworkSources)
    }

    override fun loadLibrary() {
        viewModel.onEvent(SettingsEvent.LoadLibrary)
    }

    override fun openAudioFilePicker() {
        viewModel.onEvent(SettingsEvent.OpenAudioFilePicker)
    }

    override fun openAudioFolderPicker() {
        viewModel.onEvent(SettingsEvent.OpenAudioFolderPicker)
    }

    override fun setOnlineLyricsEnabled(enabled: Boolean) {
        viewModel.onEvent(SettingsEvent.SetOnlineLyricsEnabled(enabled))
    }

    override fun reloadCurrentLyrics() {
        viewModel.onEvent(SettingsEvent.ReloadCurrentLyrics)
    }

    override fun applyLyricsOffset(offsetMs: Long) {
        viewModel.onEvent(SettingsEvent.ApplyLyricsOffset(offsetMs))
    }

    override fun startSleepTimer(minutes: Int) {
        viewModel.onEvent(SettingsEvent.StartSleepTimer(minutes))
    }

    override fun cancelSleepTimer() {
        viewModel.onEvent(SettingsEvent.CancelSleepTimer)
    }

    override fun applyPlaybackSpeed(speed: Float) {
        viewModel.onEvent(SettingsEvent.ApplyPlaybackSpeed(speed))
    }

    override fun applyAppVolume(volume: Float) {
        viewModel.onEvent(SettingsEvent.ApplyAppVolume(volume))
    }

    override fun applyStreamingAudioQuality(quality: String) {
        viewModel.onEvent(SettingsEvent.ApplyStreamingAudioQuality(quality))
    }

    override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        viewModel.onEvent(SettingsEvent.SetConcurrentPlaybackEnabled(enabled))
    }

    override fun applyThemeMode(mode: String) {
        viewModel.onEvent(SettingsEvent.ApplyThemeMode(mode))
    }

    override fun applyAccentMode(accent: String) {
        viewModel.onEvent(SettingsEvent.ApplyAccentMode(accent))
    }

    override fun applyLanguageMode(languageMode: String) {
        viewModel.onEvent(SettingsEvent.ApplyLanguageMode(languageMode))
    }

    override fun applyStreamingGatewayEndpoint(endpoint: String) {
        viewModel.onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint(endpoint))
    }

    override fun publishSettingsChrome(
        actions: List<SettingsAction>,
        scrollState: SettingsListScrollState
    ) {
        contentSink.publishSettingsChrome(actions, scrollState)
    }
}
