package app.yukine

import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsPlaybackSpeedSetter {
    fun set(speed: Float)
}

internal fun interface SettingsAppVolumeSetter {
    fun set(volume: Float)
}

internal fun interface SettingsConcurrentPlaybackSetter {
    fun set(enabled: Boolean)
}

internal fun interface SettingsAudioEffectsSetter {
    fun apply(settings: AudioEffectSettings)
}

internal fun interface SettingsStatusBarLyricsSetter {
    fun set(enabled: Boolean)
}

internal fun interface SettingsPlaybackRestoreSetter {
    fun set(enabled: Boolean)
}

internal fun interface SettingsReplayGainSetter {
    fun set(enabled: Boolean)
}

internal class SettingsPlaybackServiceControlsBindings(
    private val speedSetter: SettingsPlaybackSpeedSetter,
    private val volumeSetter: SettingsAppVolumeSetter,
    private val concurrentPlaybackSetter: SettingsConcurrentPlaybackSetter,
    private val audioEffectsSetter: SettingsAudioEffectsSetter,
    private val statusBarLyricsSetter: SettingsStatusBarLyricsSetter,
    private val playbackRestoreSetter: SettingsPlaybackRestoreSetter,
    private val replayGainSetter: SettingsReplayGainSetter
) : SettingsPlaybackServiceControls {
    override fun setPlaybackSpeed(speed: Float) {
        speedSetter.set(speed)
    }

    override fun setAppVolume(volume: Float) {
        volumeSetter.set(volume)
    }

    override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        concurrentPlaybackSetter.set(enabled)
    }

    override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        audioEffectsSetter.apply(settings)
    }

    override fun setStatusBarLyricsEnabled(enabled: Boolean) {
        statusBarLyricsSetter.set(enabled)
    }

    override fun setPlaybackRestoreEnabled(enabled: Boolean) {
        playbackRestoreSetter.set(enabled)
    }

    override fun setReplayGainEnabled(enabled: Boolean) {
        replayGainSetter.set(enabled)
    }
}

internal fun interface SettingsOnlineLyricsSetter {
    fun set(enabled: Boolean)
}

internal fun interface SettingsLyricsOffsetSetter {
    fun set(offsetMs: Long)
}

internal class SettingsLyricsControlsBindings(
    private val onlineLyricsSetter: SettingsOnlineLyricsSetter,
    private val lyricsOffsetSetter: SettingsLyricsOffsetSetter
) : SettingsLyricsControls {
    override fun setOnlineEnabled(enabled: Boolean) {
        onlineLyricsSetter.set(enabled)
    }

    override fun setOffsetMs(offsetMs: Long) {
        lyricsOffsetSetter.set(offsetMs)
    }
}
