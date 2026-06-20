package app.yukine

import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsStringAction {
    fun run(value: String)
}

internal fun interface SettingsBooleanAction {
    fun set(enabled: Boolean)
}

internal fun interface SettingsLongAction {
    fun apply(value: Long)
}

internal fun interface SettingsIntAction {
    fun apply(value: Int)
}

internal fun interface SettingsFloatAction {
    fun apply(value: Float)
}

internal fun interface SettingsAudioEffectsAction {
    fun apply(value: AudioEffectSettings)
}

internal class SettingsGatewayBindings(
    private val navigateSettingsPageAction: SettingsStringAction,
    private val openNetworkSourcesAction: Runnable,
    private val loadLibraryAction: Runnable,
    private val openAudioFilePickerAction: Runnable,
    private val openAudioFolderPickerAction: Runnable,
    private val onlineLyricsAction: SettingsBooleanAction,
    private val reloadCurrentLyricsAction: Runnable,
    private val lyricsOffsetAction: SettingsLongAction,
    private val startSleepTimerAction: SettingsIntAction,
    private val cancelSleepTimerAction: Runnable,
    private val playbackSpeedAction: SettingsFloatAction,
    private val appVolumeAction: SettingsFloatAction,
    private val streamingAudioQualityAction: SettingsStringAction,
    private val concurrentPlaybackAction: SettingsBooleanAction,
    private val audioEffectsAction: SettingsAudioEffectsAction,
    private val statusBarLyricsAction: SettingsBooleanAction,
    private val floatingLyricsAction: SettingsBooleanAction,
    private val floatingLyricsPermissionAction: Runnable,
    private val nowPlayingGesturesAction: SettingsBooleanAction,
    private val playbackRestoreAction: SettingsBooleanAction,
    private val replayGainAction: SettingsBooleanAction,
    private val themeModeAction: SettingsStringAction,
    private val accentModeAction: SettingsStringAction,
    private val languageModeAction: SettingsStringAction,
    private val streamingGatewayEndpointAction: SettingsStringAction
) : SettingsGateway {
    override fun navigateSettingsPage(page: String) {
        navigateSettingsPageAction.run(page)
    }

    override fun openNetworkSources() {
        openNetworkSourcesAction.run()
    }

    override fun loadLibrary() {
        loadLibraryAction.run()
    }

    override fun openAudioFilePicker() {
        openAudioFilePickerAction.run()
    }

    override fun openAudioFolderPicker() {
        openAudioFolderPickerAction.run()
    }

    override fun setOnlineLyricsEnabled(enabled: Boolean) {
        onlineLyricsAction.set(enabled)
    }

    override fun reloadCurrentLyrics() {
        reloadCurrentLyricsAction.run()
    }

    override fun applyLyricsOffset(offsetMs: Long) {
        lyricsOffsetAction.apply(offsetMs)
    }

    override fun startSleepTimer(minutes: Int) {
        startSleepTimerAction.apply(minutes)
    }

    override fun cancelSleepTimer() {
        cancelSleepTimerAction.run()
    }

    override fun applyPlaybackSpeed(speed: Float) {
        playbackSpeedAction.apply(speed)
    }

    override fun applyAppVolume(volume: Float) {
        appVolumeAction.apply(volume)
    }

    override fun applyStreamingAudioQuality(quality: String) {
        streamingAudioQualityAction.run(quality)
    }

    override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        concurrentPlaybackAction.set(enabled)
    }

    override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        audioEffectsAction.apply(settings)
    }

    override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        statusBarLyricsAction.set(enabled)
    }

    override fun setFloatingLyricsEnabled(enabled: Boolean) {
        floatingLyricsAction.set(enabled)
    }

    override fun openFloatingLyricsPermission() {
        floatingLyricsPermissionAction.run()
    }

    override fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        nowPlayingGesturesAction.set(enabled)
    }

    override fun setPlaybackRestoreEnabled(enabled: Boolean) {
        playbackRestoreAction.set(enabled)
    }

    override fun setReplayGainEnabled(enabled: Boolean) {
        replayGainAction.set(enabled)
    }

    override fun applyThemeMode(mode: String) {
        themeModeAction.run(mode)
    }

    override fun applyAccentMode(accent: String) {
        accentModeAction.run(accent)
    }

    override fun applyLanguageMode(languageMode: String) {
        languageModeAction.run(languageMode)
    }

    override fun applyStreamingGatewayEndpoint(endpoint: String) {
        streamingGatewayEndpointAction.run(endpoint)
    }
}
