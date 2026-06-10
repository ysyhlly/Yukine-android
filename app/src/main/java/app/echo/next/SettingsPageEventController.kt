package app.echo.next

import android.view.View

internal class SettingsPageEventController(
    private val navigator: Navigator,
    private val networkSourcesNavigator: NetworkSourcesNavigator,
    private val libraryLoader: LibraryLoader,
    private val audioPicker: AudioPicker,
    private val lyricsActions: LyricsActions,
    private val playbackTimer: PlaybackTimer,
    private val settingsActions: SettingsActions,
    private val streamingGatewayActions: StreamingGatewayActions,
    private val contentSink: ContentSink
) : SettingsPageRenderController.Listener {
    fun interface Navigator {
        fun navigateSettingsPage(page: String)
    }

    fun interface NetworkSourcesNavigator {
        fun openNetworkSources()
    }

    fun interface LibraryLoader {
        fun loadLibrary()
    }

    interface AudioPicker {
        fun openAudioFilePicker()

        fun openAudioFolderPicker()
    }

    interface LyricsActions {
        fun setOnlineLyricsEnabled(enabled: Boolean)

        fun reloadCurrentLyrics()

        fun applyLyricsOffset(offsetMs: Long)
    }

    interface PlaybackTimer {
        fun startSleepTimer(minutes: Int)

        fun cancelSleepTimer()
    }

    interface SettingsActions {
        fun applyPlaybackSpeed(speed: Float)

        fun applyAppVolume(volume: Float)

        fun applyStreamingAudioQuality(quality: String)

        fun setConcurrentPlaybackEnabled(enabled: Boolean)

        fun applyThemeMode(mode: String)

        fun applyAccentMode(accent: String)

        fun applyLanguageMode(languageMode: String)
    }

    fun interface StreamingGatewayActions {
        fun applyEndpoint(endpoint: String)
    }

    fun interface ContentSink {
        fun addVirtualContent(view: View)
    }

    override fun navigateSettingsPage(page: String) {
        navigator.navigateSettingsPage(page)
    }

    override fun openNetworkSources() {
        networkSourcesNavigator.openNetworkSources()
    }

    override fun loadLibrary() {
        libraryLoader.loadLibrary()
    }

    override fun openAudioFilePicker() {
        audioPicker.openAudioFilePicker()
    }

    override fun openAudioFolderPicker() {
        audioPicker.openAudioFolderPicker()
    }

    override fun setOnlineLyricsEnabled(enabled: Boolean) {
        lyricsActions.setOnlineLyricsEnabled(enabled)
    }

    override fun reloadCurrentLyrics() {
        lyricsActions.reloadCurrentLyrics()
    }

    override fun applyLyricsOffset(offsetMs: Long) {
        lyricsActions.applyLyricsOffset(offsetMs)
    }

    override fun startSleepTimer(minutes: Int) {
        playbackTimer.startSleepTimer(minutes)
    }

    override fun cancelSleepTimer() {
        playbackTimer.cancelSleepTimer()
    }

    override fun applyPlaybackSpeed(speed: Float) {
        settingsActions.applyPlaybackSpeed(speed)
    }

    override fun applyAppVolume(volume: Float) {
        settingsActions.applyAppVolume(volume)
    }

    override fun applyStreamingAudioQuality(quality: String) {
        settingsActions.applyStreamingAudioQuality(quality)
    }

    override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        settingsActions.setConcurrentPlaybackEnabled(enabled)
    }

    override fun applyThemeMode(mode: String) {
        settingsActions.applyThemeMode(mode)
    }

    override fun applyAccentMode(accent: String) {
        settingsActions.applyAccentMode(accent)
    }

    override fun applyLanguageMode(languageMode: String) {
        settingsActions.applyLanguageMode(languageMode)
    }

    override fun applyStreamingGatewayEndpoint(endpoint: String) {
        streamingGatewayActions.applyEndpoint(endpoint)
    }

    override fun addVirtualContent(view: View) {
        contentSink.addVirtualContent(view)
    }
}
