package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.ui.EchoTheme

/** Appearance page commands and their runtime/persistence effects. */
internal class AppearanceSettingsStateOwner(private val context: SettingsMutationContext) {
    fun applyThemeMode(nextMode: String) {
        val mode = EchoTheme.normalizeMode(nextMode)
        EchoTheme.setMode(mode)
        context.applyRuntime(SettingsRuntimeEffect.ApplyThemeSurface)
        context.updatePreferences { it.copy(themeMode = mode) }
        context.emitStatus(context.currentStatus().themeApplied)
        context.save(SettingsPreferenceKey.ThemeMode, mode)
    }

    fun applyAccentMode(nextAccent: String) {
        val accent = EchoTheme.normalizeAccent(nextAccent)
        EchoTheme.setAccent(accent)
        if (accent == EchoTheme.ACCENT_DYNAMIC_BACKGROUND) {
            context.applyRuntime(
                SettingsRuntimeEffect.RefreshCustomBackgroundAccent(context.state().preferences.pageBackgrounds)
            )
        }
        context.updatePreferences { it.copy(accentMode = accent) }
        context.emitStatus(context.currentStatus().accentApplied)
        context.save(SettingsPreferenceKey.AccentMode, accent)
    }

    fun applyLanguageMode(nextLanguageMode: String) {
        val languageMode = AppLanguage.normalizeMode(nextLanguageMode)
        context.updatePreferences { it.copy(languageMode = languageMode) }
        context.emitStatus(context.currentStatus().languageApplied)
        context.save(SettingsPreferenceKey.LanguageMode, languageMode)
    }

    fun applyShareStyle(style: String) {
        val normalized = TrackShareStyle.normalize(style)
        context.updatePreferences { it.copy(shareStyle = normalized) }
        val language = context.state().preferences.languageMode
        context.emitStatus(
            context.currentStatus().shareStyleApplied + SettingsLabelFormatter.shareStyleLabel(normalized, language)
        )
        context.save(SettingsPreferenceKey.ShareStyle, normalized)
    }

    fun setDebugPromptsEnabled(enabled: Boolean) {
        context.updatePreferences { it.copy(debugPromptsEnabled = enabled) }
        context.emitStatus(
            AppLanguage.text(
                context.state().preferences.languageMode,
                if (enabled) "debug.prompts.enabled" else "debug.prompts.disabled"
            )
        )
        context.save(SettingsPreferenceKey.DebugPromptsEnabled, enabled)
    }

    fun setGlassBlurEnabled(enabled: Boolean) {
        context.updatePreferences { it.copy(glassBlurEnabled = enabled) }
        context.save(SettingsPreferenceKey.GlassBlurEnabled, enabled)
    }

    fun setCustomBackgroundBlurEnabled(enabled: Boolean) {
        context.updatePreferences { it.copy(customBackgroundBlurEnabled = enabled) }
        context.save(SettingsPreferenceKey.CustomBackgroundBlurEnabled, enabled)
    }

    fun setCustomBackgroundBlurRadiusDp(radiusDp: Float) {
        val normalized = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(radiusDp)
        context.updatePreferences { it.copy(customBackgroundBlurRadiusDp = normalized) }
        context.save(SettingsPreferenceKey.CustomBackgroundBlurRadiusDp, normalized)
    }

    fun setGlassBlurRadiusDp(radiusDp: Float) {
        val normalized = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(radiusDp)
        context.updatePreferences { it.copy(glassBlurRadiusDp = normalized) }
        context.save(SettingsPreferenceKey.GlassBlurRadiusDp, normalized)
    }

    fun setGlassSurfaceOpacity(opacity: Float) {
        val normalized = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(opacity)
        context.updatePreferences { it.copy(glassSurfaceOpacity = normalized) }
        context.save(SettingsPreferenceKey.GlassSurfaceOpacity, normalized)
    }

    fun choosePageBackground(page: String) {
        context.emit(SettingsEffect.ChoosePageBackground(page))
    }

    fun applyPageBackgrounds(backgrounds: PageBackgrounds, page: String, cleared: Boolean) {
        context.updatePreferences { it.copy(pageBackgrounds = backgrounds) }
        if (context.state().preferences.accentMode == EchoTheme.ACCENT_DYNAMIC_BACKGROUND) {
            context.applyRuntime(SettingsRuntimeEffect.RefreshCustomBackgroundAccent(backgrounds))
        }
        val language = context.state().preferences.languageMode
        val status = context.currentStatus()
        val prefix = if (cleared) status.pageBackgroundCleared else status.pageBackgroundApplied
        context.emitStatus(prefix + SettingsLabelFormatter.pageBackgroundPageLabel(page, language))
        context.save(SettingsPreferenceKey.PageBackgrounds, backgrounds)
    }

    fun clearPageBackground(page: String) {
        val target = PageBackgrounds.normalizePage(page)
        if (target.isEmpty()) return
        val backgrounds = context.state().preferences.pageBackgrounds.clear(target)
        applyPageBackgrounds(backgrounds, target, true)
    }
}

/** Playback page commands and their runtime/persistence effects. */
internal class PlaybackSettingsStateOwner(private val context: SettingsMutationContext) {
    fun applyPlaybackSpeed(speed: Float) {
        val normalized = normalizePlaybackSpeed(speed)
        context.applyRuntime(SettingsRuntimeEffect.ApplyPlaybackSpeed(normalized))
        context.updatePreferences { it.copy(playbackSpeed = normalized) }
        context.emitStatus(context.currentStatus().playbackSpeedApplied)
        context.save(SettingsPreferenceKey.PlaybackSpeed, normalized)
    }

    fun applyAppVolume(volume: Float) {
        val normalized = normalizeAppVolume(volume)
        context.applyRuntime(SettingsRuntimeEffect.ApplyAppVolume(normalized))
        context.updatePreferences { it.copy(appVolume = normalized) }
        context.emitStatus(context.currentStatus().appVolumeApplied)
        context.save(SettingsPreferenceKey.AppVolume, normalized)
    }

    fun applyStreamingAudioQuality(quality: String) {
        val normalized = StreamingQualityPreference.normalize(quality)
        context.updatePreferences { it.copy(streamingAudioQuality = normalized) }
        val language = context.state().preferences.languageMode
        context.emitStatus(
            AppLanguage.text(language, "streaming.quality.applied") +
                SettingsLabelFormatter.streamingQualityLabel(normalized, language)
        )
        context.save(SettingsPreferenceKey.StreamingAudioQuality, normalized)
    }

    fun setRefuseAutomaticQualityDowngrade(refuse: Boolean) {
        context.updatePreferences { it.copy(refuseAutomaticQualityDowngrade = refuse) }
        context.emitStatus(
            AppLanguage.text(
                context.state().preferences.languageMode,
                if (refuse) "quality.downgrade.refused" else "quality.downgrade.allowed"
            )
        )
        context.save(SettingsPreferenceKey.RefuseAutomaticQualityDowngrade, refuse)
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        val status = context.currentStatus()
        updateConcurrentPlaybackEnabled(
            enabled,
            if (enabled) status.concurrentPlaybackEnabled else status.concurrentPlaybackDisabled
        )
    }

    fun setAudioExclusiveEnabled(enabled: Boolean) {
        updateConcurrentPlaybackEnabled(
            !enabled,
            AppLanguage.text(
                context.state().preferences.languageMode,
                if (enabled) "audio.exclusive.enabled" else "audio.exclusive.disabled"
            )
        )
    }

    private fun updateConcurrentPlaybackEnabled(enabled: Boolean, status: String) {
        context.applyRuntime(SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(enabled))
        context.updatePreferences { it.copy(concurrentPlaybackEnabled = enabled) }
        context.emitStatus(status)
        context.save(SettingsPreferenceKey.ConcurrentPlaybackEnabled, enabled)
    }

    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        context.applyRuntime(SettingsRuntimeEffect.ApplyAudioEffects(settings))
        context.updatePreferences { it.copy(audioEffectSettings = settings) }
        context.emitStatus(context.currentStatus().audioEffectsApplied)
        context.save(SettingsPreferenceKey.AudioEffectSettings, settings)
    }

    fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        context.updatePreferences { it.copy(nowPlayingGesturesEnabled = enabled) }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.nowPlayingGesturesEnabled else status.nowPlayingGesturesDisabled)
        context.save(SettingsPreferenceKey.NowPlayingGesturesEnabled, enabled)
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        context.applyRuntime(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(enabled))
        context.updatePreferences { it.copy(playbackRestoreEnabled = enabled) }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.playbackRestoreEnabled else status.playbackRestoreDisabled)
        context.save(SettingsPreferenceKey.PlaybackRestoreEnabled, enabled)
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        context.applyRuntime(SettingsRuntimeEffect.SetReplayGainEnabled(enabled))
        context.updatePreferences { it.copy(replayGainEnabled = enabled) }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.replayGainEnabled else status.replayGainDisabled)
        context.save(SettingsPreferenceKey.ReplayGainEnabled, enabled)
    }

    fun startSleepTimer(minutes: Int) {
        context.emit(SettingsEffect.StartSleepTimer(minutes))
    }

    fun cancelSleepTimer() {
        context.emit(SettingsEffect.CancelSleepTimer)
    }
}

/** Lyrics page commands and their runtime/persistence effects. */
internal class LyricsSettingsStateOwner(private val context: SettingsMutationContext) {
    fun setOnlineLyricsEnabled(enabled: Boolean) {
        context.applyRuntime(SettingsRuntimeEffect.SetOnlineLyricsEnabled(enabled))
        context.updateRuntime { it.copy(onlineLyricsEnabled = enabled) }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.onlineLyricsEnabled else status.onlineLyricsDisabled)
        context.emit(SettingsEffect.ReloadCurrentLyrics)
        context.save(SettingsPreferenceKey.OnlineLyricsEnabled, enabled)
    }

    fun reloadCurrentLyrics() {
        context.emit(SettingsEffect.ReloadCurrentLyrics)
    }

    fun applyLyricsOffset(offsetMs: Long) {
        val normalized = normalizeLyricsOffsetMs(offsetMs)
        context.applyRuntime(SettingsRuntimeEffect.SetLyricsOffsetMs(normalized))
        context.updateRuntime { it.copy(lyricsOffsetMs = normalized) }
        context.emitStatus(context.currentStatus(normalized).lyricsOffsetApplied)
        context.save(SettingsPreferenceKey.LyricsOffsetMs, normalized)
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        val preferences = context.state().preferences
        val disableFloating = enabled && preferences.floatingLyricsEnabled
        if (disableFloating) context.applyRuntime(SettingsRuntimeEffect.ApplyFloatingLyrics(false))
        context.applyRuntime(SettingsRuntimeEffect.SetStatusBarLyrics(enabled))
        context.updatePreferences {
            it.copy(
                statusBarLyricsEnabled = enabled,
                floatingLyricsEnabled = if (disableFloating) false else it.floatingLyricsEnabled
            )
        }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.statusBarLyricsEnabled else status.statusBarLyricsDisabled)
        if (disableFloating) context.save(SettingsPreferenceKey.FloatingLyricsEnabled, false)
        context.save(SettingsPreferenceKey.StatusBarLyricsEnabled, enabled)
    }

    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        context.applyRuntime(SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled(enabled))
        context.updatePreferences { it.copy(systemMediaLyricsTitleEnabled = enabled) }
        context.emitStatus(
            AppLanguage.text(
                context.state().preferences.languageMode,
                if (enabled) "system.media.lyrics.title.enabled" else "system.media.lyrics.title.disabled"
            )
        )
        context.save(SettingsPreferenceKey.SystemMediaLyricsTitleEnabled, enabled)
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        val applied = context.applyRuntime(SettingsRuntimeEffect.ApplyFloatingLyrics(enabled))
        if (enabled && !applied) {
            context.emitStatus(context.currentStatus().floatingLyricsPermissionRequired)
            return
        }
        val disableStatusBar = enabled && applied && context.state().preferences.statusBarLyricsEnabled
        if (disableStatusBar) context.applyRuntime(SettingsRuntimeEffect.SetStatusBarLyrics(false))
        context.updatePreferences {
            it.copy(
                statusBarLyricsEnabled = if (disableStatusBar) false else it.statusBarLyricsEnabled,
                floatingLyricsEnabled = enabled
            )
        }
        val status = context.currentStatus()
        context.emitStatus(if (enabled) status.floatingLyricsEnabled else status.floatingLyricsDisabled)
        if (disableStatusBar) context.save(SettingsPreferenceKey.StatusBarLyricsEnabled, false)
        context.save(SettingsPreferenceKey.FloatingLyricsEnabled, enabled)
    }

    fun openFloatingLyricsPermission() {
        context.applyRuntime(SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings)
        context.emitStatus(context.currentStatus().floatingLyricsPermissionRequired)
    }
}

internal class LibrarySettingsStateOwner(private val context: SettingsMutationContext) {
    fun loadLibrary() = context.emit(SettingsEffect.LoadLibrary)
    fun openAudioFilePicker() = context.emit(SettingsEffect.OpenAudioFilePicker)
    fun openAudioFolderPicker() = context.emit(SettingsEffect.OpenAudioFolderPicker)
    fun restoreHiddenItem(sourceKey: String) = context.emit(SettingsEffect.RestoreHiddenLibraryItem(sourceKey))
    fun restoreAllHiddenItems() = context.emit(SettingsEffect.RestoreAllHiddenLibraryItems)
}

internal class NetworkSettingsStateOwner(private val context: SettingsMutationContext) {
    fun openPage(page: String) = context.emit(SettingsEffect.OpenNetworkPage(page))
    fun openLuoxueSourceManager() = context.emit(SettingsEffect.OpenLuoxueSourceManager)
    fun importLuoxueSource() = context.emit(SettingsEffect.ImportLuoxueSource)
    fun applyStreamingGatewayEndpoint(endpoint: String) =
        context.emit(SettingsEffect.ApplyStreamingGatewayEndpoint(endpoint))
}

internal class PlatformSettingsStateOwner(private val context: SettingsMutationContext) {
    fun openDownloads() = context.emit(SettingsEffect.OpenDownloads)
    fun requestNeededPermissions() = context.emit(SettingsEffect.RequestNeededPermissions)
    fun openFloatingLyricsPermission() = context.emit(SettingsEffect.OpenFloatingLyricsPermission)
    fun exportBackup() = context.emit(SettingsEffect.ExportBackup)
    fun importBackup() = context.emit(SettingsEffect.ImportBackup)
}

private fun normalizePlaybackSpeed(speed: Float): Float =
    Math.round(speed.coerceIn(0.5f, 2.0f) * 100.0f) / 100.0f

private fun normalizeAppVolume(volume: Float): Float =
    Math.round(volume.coerceIn(0.0f, 1.0f) * 100.0f) / 100.0f

private fun normalizeLyricsOffsetMs(offsetMs: Long): Long =
    Math.round(offsetMs.coerceIn(-5000L, 5000L) / 100.0) * 100L
