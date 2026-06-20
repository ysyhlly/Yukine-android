package app.yukine

import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsAppliedStatusTextProvider {
    fun text(offsetMs: Long): SettingsAppliedStatusText
}

internal fun interface StreamingQualityAppliedStatusProvider {
    fun text(quality: String): String
}

internal fun interface SettingsStatusSink {
    fun set(message: String)
}

internal fun interface SettingsLanguageUpdateAction {
    fun update(languageMode: String)
}

internal interface SettingsPlaybackServiceControls {
    fun setPlaybackSpeed(speed: Float)

    fun setAppVolume(volume: Float)

    fun setConcurrentPlaybackEnabled(enabled: Boolean)

    fun applyAudioEffectSettings(settings: AudioEffectSettings)

    fun setStatusBarLyricsEnabled(enabled: Boolean)

    fun setPlaybackRestoreEnabled(enabled: Boolean)

    fun setReplayGainEnabled(enabled: Boolean)
}

internal fun interface SettingsPlaybackServiceControlsProvider {
    fun controls(): SettingsPlaybackServiceControls?
}

internal interface SettingsLyricsControls {
    fun setOnlineEnabled(enabled: Boolean)

    fun setOffsetMs(offsetMs: Long)
}

internal fun interface SettingsLyricsControlsProvider {
    fun controls(): SettingsLyricsControls?
}

internal interface SettingsFloatingLyricsControls {
    fun apply(enabled: Boolean): Boolean

    fun openPermissionSettings()
}

internal fun interface SettingsFloatingLyricsControlsProvider {
    fun controls(): SettingsFloatingLyricsControls?
}

internal fun interface SettingsSelectedTabProvider {
    fun selectedTab(): String
}

internal class SettingsAppliedListenerBindings(
    private val settingsStore: MainSettingsStore,
    private val applyThemeSurfaceAction: Runnable,
    private val updateLanguageAction: SettingsLanguageUpdateAction,
    private val playbackServiceControlsProvider: SettingsPlaybackServiceControlsProvider,
    private val lyricsControlsProvider: SettingsLyricsControlsProvider,
    private val floatingLyricsControlsProvider: SettingsFloatingLyricsControlsProvider,
    private val appliedStatusTextProvider: SettingsAppliedStatusTextProvider,
    private val streamingQualityAppliedStatusProvider: StreamingQualityAppliedStatusProvider,
    private val statusSink: SettingsStatusSink,
    private val renderSelectedTabAction: Runnable,
    private val renderNowBarAction: Runnable,
    private val reloadCurrentLyricsAction: Runnable,
    private val selectedTabProvider: SettingsSelectedTabProvider
) : SettingsAppliedListener {
    override fun onThemeModeApplied(mode: String) {
        settingsStore.setThemeMode(mode)
        applyThemeSurfaceAction.run()
        renderSelectedTabAction.run()
        renderNowBarAction.run()
        statusSink.set(appliedStatusTextProvider.text(0L).themeApplied)
    }

    override fun onAccentModeApplied(accent: String) {
        settingsStore.setAccentMode(accent)
        renderSelectedTabAction.run()
        renderNowBarAction.run()
        statusSink.set(appliedStatusTextProvider.text(0L).accentApplied)
    }

    override fun onLanguageModeApplied(languageMode: String) {
        settingsStore.setLanguageMode(languageMode)
        updateLanguageAction.update(settingsStore.languageMode())
        renderSelectedTabAction.run()
        renderNowBarAction.run()
        statusSink.set(appliedStatusTextProvider.text(0L).languageApplied)
    }

    override fun onPlaybackSpeedApplied(speed: Float) {
        settingsStore.setPlaybackSpeed(speed)
        playbackServiceControlsProvider.controls()?.setPlaybackSpeed(settingsStore.playbackSpeed())
        renderSelectedTabAction.run()
        renderNowBarAction.run()
        statusSink.set(appliedStatusTextProvider.text(0L).playbackSpeedApplied)
    }

    override fun onAppVolumeApplied(volume: Float) {
        settingsStore.setAppVolume(volume)
        playbackServiceControlsProvider.controls()?.setAppVolume(settingsStore.appVolume())
        renderSelectedTabAction.run()
        renderNowBarAction.run()
        statusSink.set(appliedStatusTextProvider.text(0L).appVolumeApplied)
    }

    override fun onStreamingAudioQualityApplied(quality: String) {
        settingsStore.setStreamingAudioQuality(quality)
        renderSelectedTabAction.run()
        statusSink.set(streamingQualityAppliedStatusProvider.text(settingsStore.streamingAudioQuality()))
    }

    override fun onConcurrentPlaybackEnabledApplied(enabled: Boolean) {
        settingsStore.setConcurrentPlaybackEnabled(enabled)
        playbackServiceControlsProvider.controls()
            ?.setConcurrentPlaybackEnabled(settingsStore.concurrentPlaybackEnabled())
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.concurrentPlaybackEnabled
            } else {
                statusText.concurrentPlaybackDisabled
            }
        )
        renderSelectedTabAction.run()
    }

    override fun onAudioEffectSettingsApplied(settings: AudioEffectSettings) {
        settingsStore.setAudioEffectSettings(settings)
        playbackServiceControlsProvider.controls()?.applyAudioEffectSettings(settingsStore.audioEffectSettings())
        statusSink.set(appliedStatusTextProvider.text(0L).audioEffectsApplied)
        renderSelectedTabAction.run()
        renderNowBarAction.run()
    }

    override fun onStatusBarLyricsEnabledApplied(enabled: Boolean) {
        settingsStore.setStatusBarLyricsEnabled(enabled)
        playbackServiceControlsProvider.controls()
            ?.setStatusBarLyricsEnabled(settingsStore.statusBarLyricsEnabled())
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.statusBarLyricsEnabled
            } else {
                statusText.statusBarLyricsDisabled
            }
        )
        renderSelectedTabAction.run()
        renderNowBarAction.run()
    }

    override fun onFloatingLyricsEnabledApplied(enabled: Boolean) {
        settingsStore.setFloatingLyricsEnabled(enabled)
        val applied = floatingLyricsControlsProvider.controls()?.apply(enabled) != false
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (!applied && enabled) {
                statusText.floatingLyricsPermissionRequired
            } else if (enabled) {
                statusText.floatingLyricsEnabled
            } else {
                statusText.floatingLyricsDisabled
            }
        )
        renderSelectedTabAction.run()
        renderNowBarAction.run()
    }

    override fun onFloatingLyricsPermissionRequested() {
        floatingLyricsControlsProvider.controls()?.openPermissionSettings()
        statusSink.set(appliedStatusTextProvider.text(0L).floatingLyricsPermissionRequired)
    }

    override fun onNowPlayingGesturesEnabledApplied(enabled: Boolean) {
        settingsStore.setNowPlayingGesturesEnabled(enabled)
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.nowPlayingGesturesEnabled
            } else {
                statusText.nowPlayingGesturesDisabled
            }
        )
        renderSelectedTabAction.run()
        renderNowBarAction.run()
    }

    override fun onPlaybackRestoreEnabledApplied(enabled: Boolean) {
        settingsStore.setPlaybackRestoreEnabled(enabled)
        playbackServiceControlsProvider.controls()
            ?.setPlaybackRestoreEnabled(settingsStore.playbackRestoreEnabled())
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.playbackRestoreEnabled
            } else {
                statusText.playbackRestoreDisabled
            }
        )
        renderSelectedTabAction.run()
    }

    override fun onReplayGainEnabledApplied(enabled: Boolean) {
        settingsStore.setReplayGainEnabled(enabled)
        playbackServiceControlsProvider.controls()
            ?.setReplayGainEnabled(settingsStore.replayGainEnabled())
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.replayGainEnabled
            } else {
                statusText.replayGainDisabled
            }
        )
        renderSelectedTabAction.run()
        renderNowBarAction.run()
    }

    override fun onOnlineLyricsEnabledApplied(enabled: Boolean) {
        lyricsControlsProvider.controls()?.setOnlineEnabled(enabled)
        val statusText = appliedStatusTextProvider.text(0L)
        statusSink.set(
            if (enabled) {
                statusText.onlineLyricsEnabled
            } else {
                statusText.onlineLyricsDisabled
            }
        )
        reloadCurrentLyricsAction.run()
        renderSelectedTabAction.run()
    }

    override fun onLyricsOffsetApplied(offsetMs: Long) {
        lyricsControlsProvider.controls()?.setOffsetMs(offsetMs)
        statusSink.set(appliedStatusTextProvider.text(offsetMs).lyricsOffsetApplied)
        renderSelectedTabAction.run()
        if (MainRoutes.TAB_NOW == selectedTabProvider.selectedTab()) {
            renderNowBarAction.run()
        }
    }
}
