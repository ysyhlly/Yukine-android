package app.yukine

import android.content.Context
import app.yukine.playback.AudioEffectSettings

internal fun interface SettingsThemeSurfaceApplier {
    fun apply()
}

internal fun interface SettingsCustomBackgroundAccentRefresher {
    fun refresh(backgrounds: PageBackgrounds)
}

internal class MainSettingsLyricsControls(
    private val viewModel: LyricsViewModel
) : SettingsLyricsControls {
    override fun setOnlineEnabled(enabled: Boolean) {
        viewModel.setOnlineEnabled(enabled)
    }

    override fun setOffsetMs(offsetMs: Long) {
        viewModel.setOffsetMs(offsetMs)
    }
}

internal class MainSettingsFloatingLyricsControls(
    private val context: Context,
    private val permissionControllerProvider: () -> MainPermissionController?
) : SettingsFloatingLyricsControls {
    override fun apply(enabled: Boolean): Boolean {
        if (!enabled) {
            FloatingLyricsService.stop(context)
            return true
        }
        val permissionController = permissionControllerProvider() ?: return false
        if (!permissionController.hasOverlayPermission()) {
            FloatingLyricsService.stop(context)
            permissionController.openOverlayPermissionSettings()
            return false
        }
        FloatingLyricsService.start(context)
        return true
    }

    override fun openPermissionSettings(): Boolean {
        val permissionController = permissionControllerProvider() ?: return false
        permissionController.openOverlayPermissionSettings()
        return true
    }
}

internal class SettingsRuntimeApplier(
    private val applyThemeSurfaceAction: SettingsThemeSurfaceApplier,
    private val customBackgroundAccentRefresher: SettingsCustomBackgroundAccentRefresher,
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
            is SettingsRuntimeEffect.RefreshCustomBackgroundAccent -> {
                customBackgroundAccentRefresher.refresh(effect.backgrounds)
                true
            }
            is SettingsRuntimeEffect.ApplyPlaybackSpeed -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setPlaybackSpeed(effect.speed)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.ApplyAppVolume -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setAppVolume(effect.volume)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetConcurrentPlaybackEnabled -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setConcurrentPlaybackEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.ApplyAudioEffects -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.applyAudioEffectSettings(effect.settings)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetStatusBarLyrics -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setStatusBarLyricsEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setSystemMediaLyricsTitleEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.ApplyFloatingLyrics -> {
                floatingLyricsControlsProvider.controls()?.apply(effect.enabled) != false
            }
            SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings -> {
                floatingLyricsControlsProvider.controls()?.let {
                    it.openPermissionSettings()
                } ?: false
            }
            is SettingsRuntimeEffect.SetPlaybackRestoreEnabled -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setPlaybackRestoreEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetReplayGainEnabled -> {
                playbackServiceControlsProvider.controls()?.let {
                    it.setReplayGainEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetOnlineLyricsEnabled -> {
                lyricsControlsProvider.controls()?.let {
                    it.setOnlineEnabled(effect.enabled)
                    true
                } ?: false
            }
            is SettingsRuntimeEffect.SetLyricsOffsetMs -> {
                lyricsControlsProvider.controls()?.let {
                    it.setOffsetMs(effect.offsetMs)
                    true
                } ?: false
            }
        }
    }

    fun applyThemeSurface() {
        apply(SettingsRuntimeEffect.ApplyThemeSurface)
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

    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        apply(SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled(enabled))
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
