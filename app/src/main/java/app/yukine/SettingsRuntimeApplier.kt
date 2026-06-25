package app.yukine

import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsThemeSurfaceApplier {
    fun apply()
}

internal fun interface SettingsRuntimeLanguageUpdater {
    fun update(languageMode: String)
}

sealed interface SettingsRuntimeEffect {
    data object ApplyThemeSurface : SettingsRuntimeEffect
    data class UpdateLanguage(val languageMode: String) : SettingsRuntimeEffect
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsRuntimeEffect
    data class ApplyAppVolume(val volume: Float) : SettingsRuntimeEffect
    data class SetConcurrentPlaybackEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyAudioEffects(val settings: AudioEffectSettings) : SettingsRuntimeEffect
    data class SetStatusBarLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data class ApplyFloatingLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data object OpenFloatingLyricsPermissionSettings : SettingsRuntimeEffect
    data class SetPlaybackRestoreEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetOnlineLyricsEnabled(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetLyricsOffsetMs(val offsetMs: Long) : SettingsRuntimeEffect
}

internal class SettingsRuntimeApplier(
    private val applyThemeSurfaceAction: SettingsThemeSurfaceApplier,
    private val updateLanguageAction: SettingsRuntimeLanguageUpdater,
    private val playbackServiceControlsProvider: SettingsPlaybackServiceControlsProvider,
    private val lyricsControlsProvider: SettingsLyricsControlsProvider,
    private val floatingLyricsControlsProvider: SettingsFloatingLyricsControlsProvider
) {
    fun apply(effect: SettingsRuntimeEffect): Boolean {
        return when (effect) {
            SettingsRuntimeEffect.ApplyThemeSurface -> {
                applyThemeSurfaceAction.apply()
                true
            }
            is SettingsRuntimeEffect.UpdateLanguage -> {
                updateLanguageAction.update(effect.languageMode)
                true
            }
            is SettingsRuntimeEffect.ApplyPlaybackSpeed -> {
                playbackServiceControlsProvider.controls()?.setPlaybackSpeed(effect.speed)
                true
            }
            is SettingsRuntimeEffect.ApplyAppVolume -> {
                playbackServiceControlsProvider.controls()?.setAppVolume(effect.volume)
                true
            }
            is SettingsRuntimeEffect.SetConcurrentPlaybackEnabled -> {
                playbackServiceControlsProvider.controls()?.setConcurrentPlaybackEnabled(effect.enabled)
                true
            }
            is SettingsRuntimeEffect.ApplyAudioEffects -> {
                playbackServiceControlsProvider.controls()?.applyAudioEffectSettings(effect.settings)
                true
            }
            is SettingsRuntimeEffect.SetStatusBarLyrics -> {
                playbackServiceControlsProvider.controls()?.setStatusBarLyricsEnabled(effect.enabled)
                true
            }
            is SettingsRuntimeEffect.ApplyFloatingLyrics -> {
                floatingLyricsControlsProvider.controls()?.apply(effect.enabled) != false
            }
            SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings -> {
                floatingLyricsControlsProvider.controls()?.openPermissionSettings()
                true
            }
            is SettingsRuntimeEffect.SetPlaybackRestoreEnabled -> {
                playbackServiceControlsProvider.controls()?.setPlaybackRestoreEnabled(effect.enabled)
                true
            }
            is SettingsRuntimeEffect.SetReplayGainEnabled -> {
                playbackServiceControlsProvider.controls()?.setReplayGainEnabled(effect.enabled)
                true
            }
            is SettingsRuntimeEffect.SetOnlineLyricsEnabled -> {
                lyricsControlsProvider.controls()?.setOnlineEnabled(effect.enabled)
                true
            }
            is SettingsRuntimeEffect.SetLyricsOffsetMs -> {
                lyricsControlsProvider.controls()?.setOffsetMs(effect.offsetMs)
                true
            }
        }
    }

    fun applyThemeSurface() {
        apply(SettingsRuntimeEffect.ApplyThemeSurface)
    }

    fun updateLanguage(languageMode: String) {
        apply(SettingsRuntimeEffect.UpdateLanguage(languageMode))
    }

    fun applyPlaybackSpeed(speed: Float) {
        apply(SettingsRuntimeEffect.ApplyPlaybackSpeed(speed))
    }

    fun applyAppVolume(volume: Float) {
        apply(SettingsRuntimeEffect.ApplyAppVolume(volume))
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(enabled))
    }

    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        apply(SettingsRuntimeEffect.ApplyAudioEffects(settings))
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetStatusBarLyrics(enabled))
    }

    fun applyFloatingLyrics(enabled: Boolean): Boolean {
        return apply(SettingsRuntimeEffect.ApplyFloatingLyrics(enabled))
    }

    fun openFloatingLyricsPermissionSettings() {
        apply(SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings)
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(enabled))
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetReplayGainEnabled(enabled))
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetOnlineLyricsEnabled(enabled))
    }

    fun setLyricsOffsetMs(offsetMs: Long) {
        apply(SettingsRuntimeEffect.SetLyricsOffsetMs(offsetMs))
    }
}
