package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.EchoTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared persistence and runtime boundary used by the focused settings page owners. */
internal class SettingsMutationContext(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentState: () -> SettingsState,
    private val updatePreferencesState: ((SettingsPreferencesSnapshot) -> SettingsPreferencesSnapshot) -> Unit,
    private val updateRuntimeState: ((RuntimeSettingsStatus) -> RuntimeSettingsStatus) -> Unit,
    private val replaceSnapshot: (SettingsPreferencesSnapshot, RuntimeSettingsStatus) -> Unit
) {
    private val pendingEffects = java.util.ArrayDeque<SettingsEffect>()
    private val writeMutex = Mutex()
    private var preferenceGateway: SettingsPreferenceGateway? = null
    private var storeMirror: SettingsStoreMirror? = null
    private var effectListener: SettingsEffectListener? = null
    private var runtimeEffectListener: SettingsRuntimeEffectListener? = null
    private var persistedValues: Map<SettingsPreferenceKey, Any> = emptyMap()

    var baselineReady: Boolean = false
        private set

    fun bindPreferenceGateway(gateway: SettingsPreferenceGateway?) {
        preferenceGateway = gateway
    }

    fun bindStoreMirror(mirror: SettingsStoreMirror?) {
        storeMirror = mirror
    }

    fun bindEffectListener(listener: SettingsEffectListener?) {
        effectListener = listener
    }

    fun bindRuntimeEffectListener(listener: SettingsRuntimeEffectListener?) {
        runtimeEffectListener = listener
    }

    fun establishBaseline(preferences: SettingsPreferencesSnapshot, runtime: RuntimeSettingsStatus) {
        persistedValues = persistentValues(preferences, runtime)
        baselineReady = true
    }

    fun ensureBaseline(preferences: SettingsPreferencesSnapshot, runtime: RuntimeSettingsStatus) {
        if (!baselineReady) {
            establishBaseline(preferences, runtime)
        }
    }

    fun syncStore(preferences: SettingsPreferencesSnapshot) {
        storeMirror?.sync(preferences)
    }

    fun updatePreferences(transform: (SettingsPreferencesSnapshot) -> SettingsPreferencesSnapshot) {
        updatePreferencesState(transform)
    }

    fun updateRuntime(transform: (RuntimeSettingsStatus) -> RuntimeSettingsStatus) {
        updateRuntimeState(transform)
    }

    fun state(): SettingsState = currentState()

    fun emit(effect: SettingsEffect) {
        val listener = effectListener
        if (listener == null) {
            pendingEffects.add(effect)
        } else {
            listener.onEffect(effect)
        }
    }

    fun drainEffects(): List<SettingsEffect> {
        val drained = ArrayList<SettingsEffect>(pendingEffects.size)
        while (pendingEffects.isNotEmpty()) {
            drained.add(pendingEffects.removeFirst())
        }
        return drained
    }

    fun applyRuntime(effect: SettingsRuntimeEffect): Boolean {
        val listener = runtimeEffectListener ?: return true
        val applied = listener.onRuntimeEffect(effect)
        if (!applied) {
            emit(SettingsEffect.ShowStatus(runtimeUnavailableStatus()))
        }
        return applied
    }

    fun currentStatus(offsetMs: Long = 0L): SettingsAppliedStatusText {
        val preferences = currentState().preferences
        return SettingsStatusTextFactory.applied(
            preferences.languageMode,
            preferences.themeMode,
            preferences.accentMode,
            preferences.playbackSpeed,
            preferences.appVolume,
            offsetMs
        )
    }

    fun emitStatus(message: String) {
        if (message.isNotBlank()) {
            emit(SettingsEffect.ShowStatus(message))
        }
    }

    fun save(key: SettingsPreferenceKey, value: Any) {
        val gateway = preferenceGateway ?: return
        scope.launch {
            val result = withContext(ioDispatcher) {
                writeMutex.withLock {
                    runCatching {
                        check(gateway.save(SettingsPreferenceUpdate(key, value)))
                    }
                }
            }
            result.onSuccess {
                persistedValues = persistedValues + (key to value)
            }.onFailure {
                if (baselineReady && currentValue(key) == value) {
                    persistedValues[key]?.let { restore(key, it) }
                }
                emit(SettingsEffect.ShowStatus(preferenceSaveFailedStatus()))
            }
        }
    }

    private fun persistentValues(
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ): Map<SettingsPreferenceKey, Any> = mapOf(
        SettingsPreferenceKey.ThemeMode to preferences.themeMode,
        SettingsPreferenceKey.AccentMode to preferences.accentMode,
        SettingsPreferenceKey.LanguageMode to preferences.languageMode,
        SettingsPreferenceKey.PlaybackSpeed to preferences.playbackSpeed,
        SettingsPreferenceKey.AppVolume to preferences.appVolume,
        SettingsPreferenceKey.StreamingAudioQuality to preferences.streamingAudioQuality,
        SettingsPreferenceKey.RefuseAutomaticQualityDowngrade to preferences.refuseAutomaticQualityDowngrade,
        SettingsPreferenceKey.OnlineLyricsEnabled to runtime.onlineLyricsEnabled,
        SettingsPreferenceKey.LyricsOffsetMs to runtime.lyricsOffsetMs,
        SettingsPreferenceKey.AudioEffectSettings to preferences.audioEffectSettings,
        SettingsPreferenceKey.StatusBarLyricsEnabled to preferences.statusBarLyricsEnabled,
        SettingsPreferenceKey.SystemMediaLyricsTitleEnabled to preferences.systemMediaLyricsTitleEnabled,
        SettingsPreferenceKey.FloatingLyricsEnabled to preferences.floatingLyricsEnabled,
        SettingsPreferenceKey.NowPlayingGesturesEnabled to preferences.nowPlayingGesturesEnabled,
        SettingsPreferenceKey.PlaybackRestoreEnabled to preferences.playbackRestoreEnabled,
        SettingsPreferenceKey.ReplayGainEnabled to preferences.replayGainEnabled,
        SettingsPreferenceKey.AudioExclusiveEnabled to preferences.audioExclusiveEnabled,
        SettingsPreferenceKey.BitPerfectEnabled to preferences.bitPerfectEnabled,
        SettingsPreferenceKey.UsbExclusiveEnabled to preferences.usbExclusiveEnabled,
        SettingsPreferenceKey.DebugPromptsEnabled to preferences.debugPromptsEnabled,
        SettingsPreferenceKey.CheckUpdateEnabled to preferences.checkUpdateEnabled,
        SettingsPreferenceKey.CustomBackgroundBlurEnabled to preferences.customBackgroundBlurEnabled,
        SettingsPreferenceKey.CustomBackgroundBlurRadiusDp to preferences.customBackgroundBlurRadiusDp,
        SettingsPreferenceKey.GlassBlurEnabled to preferences.glassBlurEnabled,
        SettingsPreferenceKey.GlassBlurRadiusDp to preferences.glassBlurRadiusDp,
        SettingsPreferenceKey.GlassSurfaceOpacity to preferences.glassSurfaceOpacity,
        SettingsPreferenceKey.CompactSettingsCards to preferences.compactSettingsCards,
        SettingsPreferenceKey.HomeDashboardLayout to preferences.homeDashboardLayout,
        SettingsPreferenceKey.ShareStyle to preferences.shareStyle,
        SettingsPreferenceKey.PageBackgrounds to preferences.pageBackgrounds
    )

    private fun currentValue(key: SettingsPreferenceKey): Any {
        val state = currentState()
        return when (key) {
            SettingsPreferenceKey.ThemeMode -> state.preferences.themeMode
            SettingsPreferenceKey.AccentMode -> state.preferences.accentMode
            SettingsPreferenceKey.LanguageMode -> state.preferences.languageMode
            SettingsPreferenceKey.PlaybackSpeed -> state.preferences.playbackSpeed
            SettingsPreferenceKey.AppVolume -> state.preferences.appVolume
            SettingsPreferenceKey.StreamingAudioQuality -> state.preferences.streamingAudioQuality
            SettingsPreferenceKey.RefuseAutomaticQualityDowngrade -> state.preferences.refuseAutomaticQualityDowngrade
            SettingsPreferenceKey.OnlineLyricsEnabled -> state.runtime.onlineLyricsEnabled
            SettingsPreferenceKey.LyricsOffsetMs -> state.runtime.lyricsOffsetMs
            SettingsPreferenceKey.AudioEffectSettings -> state.preferences.audioEffectSettings
            SettingsPreferenceKey.StatusBarLyricsEnabled -> state.preferences.statusBarLyricsEnabled
            SettingsPreferenceKey.SystemMediaLyricsTitleEnabled -> state.preferences.systemMediaLyricsTitleEnabled
            SettingsPreferenceKey.FloatingLyricsEnabled -> state.preferences.floatingLyricsEnabled
            SettingsPreferenceKey.NowPlayingGesturesEnabled -> state.preferences.nowPlayingGesturesEnabled
            SettingsPreferenceKey.PlaybackRestoreEnabled -> state.preferences.playbackRestoreEnabled
            SettingsPreferenceKey.ReplayGainEnabled -> state.preferences.replayGainEnabled
            SettingsPreferenceKey.AudioExclusiveEnabled -> state.preferences.audioExclusiveEnabled
            SettingsPreferenceKey.BitPerfectEnabled -> state.preferences.bitPerfectEnabled
            SettingsPreferenceKey.UsbExclusiveEnabled -> state.preferences.usbExclusiveEnabled
            SettingsPreferenceKey.DebugPromptsEnabled -> state.preferences.debugPromptsEnabled
            SettingsPreferenceKey.CheckUpdateEnabled -> state.preferences.checkUpdateEnabled
            SettingsPreferenceKey.CustomBackgroundBlurEnabled -> state.preferences.customBackgroundBlurEnabled
            SettingsPreferenceKey.CustomBackgroundBlurRadiusDp -> state.preferences.customBackgroundBlurRadiusDp
            SettingsPreferenceKey.GlassBlurEnabled -> state.preferences.glassBlurEnabled
            SettingsPreferenceKey.GlassBlurRadiusDp -> state.preferences.glassBlurRadiusDp
            SettingsPreferenceKey.GlassSurfaceOpacity -> state.preferences.glassSurfaceOpacity
            SettingsPreferenceKey.CompactSettingsCards -> state.preferences.compactSettingsCards
            SettingsPreferenceKey.HomeDashboardLayout -> state.preferences.homeDashboardLayout
            SettingsPreferenceKey.ShareStyle -> state.preferences.shareStyle
            SettingsPreferenceKey.PageBackgrounds -> state.preferences.pageBackgrounds
        }
    }

    private fun restore(key: SettingsPreferenceKey, value: Any) {
        val current = currentState()
        var preferences = current.preferences
        var runtime = current.runtime
        when (key) {
            SettingsPreferenceKey.ThemeMode -> {
                val mode = value as String
                EchoTheme.setMode(mode)
                preferences = preferences.copy(themeMode = mode)
            }
            SettingsPreferenceKey.AccentMode -> {
                val accent = value as String
                EchoTheme.setAccent(accent)
                preferences = preferences.copy(accentMode = accent)
            }
            SettingsPreferenceKey.LanguageMode -> preferences = preferences.copy(languageMode = value as String)
            SettingsPreferenceKey.PlaybackSpeed -> preferences = preferences.copy(playbackSpeed = value as Float)
            SettingsPreferenceKey.AppVolume -> preferences = preferences.copy(appVolume = value as Float)
            SettingsPreferenceKey.StreamingAudioQuality -> preferences = preferences.copy(streamingAudioQuality = value as String)
            SettingsPreferenceKey.RefuseAutomaticQualityDowngrade ->
                preferences = preferences.copy(refuseAutomaticQualityDowngrade = value as Boolean)
            SettingsPreferenceKey.OnlineLyricsEnabled -> runtime = runtime.copy(onlineLyricsEnabled = value as Boolean)
            SettingsPreferenceKey.LyricsOffsetMs -> runtime = runtime.copy(lyricsOffsetMs = value as Long)
            SettingsPreferenceKey.AudioEffectSettings ->
                preferences = preferences.copy(audioEffectSettings = value as AudioEffectSettings)
            SettingsPreferenceKey.StatusBarLyricsEnabled ->
                preferences = preferences.copy(statusBarLyricsEnabled = value as Boolean)
            SettingsPreferenceKey.SystemMediaLyricsTitleEnabled ->
                preferences = preferences.copy(systemMediaLyricsTitleEnabled = value as Boolean)
            SettingsPreferenceKey.FloatingLyricsEnabled ->
                preferences = preferences.copy(floatingLyricsEnabled = value as Boolean)
            SettingsPreferenceKey.NowPlayingGesturesEnabled ->
                preferences = preferences.copy(nowPlayingGesturesEnabled = value as Boolean)
            SettingsPreferenceKey.PlaybackRestoreEnabled ->
                preferences = preferences.copy(playbackRestoreEnabled = value as Boolean)
            SettingsPreferenceKey.ReplayGainEnabled ->
                preferences = preferences.copy(replayGainEnabled = value as Boolean)
            SettingsPreferenceKey.AudioExclusiveEnabled ->
                preferences = preferences.copy(audioExclusiveEnabled = value as Boolean)
            SettingsPreferenceKey.BitPerfectEnabled ->
                preferences = preferences.copy(bitPerfectEnabled = value as Boolean)
            SettingsPreferenceKey.UsbExclusiveEnabled ->
                preferences = preferences.copy(usbExclusiveEnabled = value as Boolean)
            SettingsPreferenceKey.DebugPromptsEnabled ->
                preferences = preferences.copy(debugPromptsEnabled = value as Boolean)
            SettingsPreferenceKey.CheckUpdateEnabled ->
                preferences = preferences.copy(checkUpdateEnabled = value as Boolean)
            SettingsPreferenceKey.CustomBackgroundBlurEnabled ->
                preferences = preferences.copy(customBackgroundBlurEnabled = value as Boolean)
            SettingsPreferenceKey.CustomBackgroundBlurRadiusDp ->
                preferences = preferences.copy(customBackgroundBlurRadiusDp = value as Float)
            SettingsPreferenceKey.GlassBlurEnabled -> preferences = preferences.copy(glassBlurEnabled = value as Boolean)
            SettingsPreferenceKey.GlassBlurRadiusDp -> preferences = preferences.copy(glassBlurRadiusDp = value as Float)
            SettingsPreferenceKey.GlassSurfaceOpacity -> preferences = preferences.copy(glassSurfaceOpacity = value as Float)
            SettingsPreferenceKey.CompactSettingsCards ->
                preferences = preferences.copy(compactSettingsCards = value as Boolean)
            SettingsPreferenceKey.HomeDashboardLayout ->
                preferences = preferences.copy(homeDashboardLayout = value as app.yukine.ui.HomeDashboardLayout)
            SettingsPreferenceKey.ShareStyle -> preferences = preferences.copy(shareStyle = value as String)
            SettingsPreferenceKey.PageBackgrounds -> preferences = preferences.copy(pageBackgrounds = value as PageBackgrounds)
        }
        syncStore(preferences)
        replaceSnapshot(preferences, runtime)
        when (key) {
            SettingsPreferenceKey.ThemeMode -> applyRuntime(SettingsRuntimeEffect.ApplyThemeSurface)
            SettingsPreferenceKey.PlaybackSpeed -> applyRuntime(SettingsRuntimeEffect.ApplyPlaybackSpeed(preferences.playbackSpeed))
            SettingsPreferenceKey.AppVolume -> applyRuntime(SettingsRuntimeEffect.ApplyAppVolume(preferences.appVolume))
            SettingsPreferenceKey.OnlineLyricsEnabled -> applyRuntime(SettingsRuntimeEffect.SetOnlineLyricsEnabled(runtime.onlineLyricsEnabled))
            SettingsPreferenceKey.AudioEffectSettings -> applyRuntime(SettingsRuntimeEffect.ApplyAudioEffects(preferences.audioEffectSettings))
            SettingsPreferenceKey.StatusBarLyricsEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetStatusBarLyrics(preferences.statusBarLyricsEnabled))
            SettingsPreferenceKey.SystemMediaLyricsTitleEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled(preferences.systemMediaLyricsTitleEnabled))
            SettingsPreferenceKey.FloatingLyricsEnabled ->
                applyRuntime(SettingsRuntimeEffect.ApplyFloatingLyrics(preferences.floatingLyricsEnabled))
            SettingsPreferenceKey.PlaybackRestoreEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(preferences.playbackRestoreEnabled))
            SettingsPreferenceKey.ReplayGainEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetReplayGainEnabled(preferences.replayGainEnabled))
            SettingsPreferenceKey.AudioExclusiveEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetAudioExclusiveEnabled(preferences.audioExclusiveEnabled))
            SettingsPreferenceKey.BitPerfectEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetBitPerfectEnabled(preferences.bitPerfectEnabled))
            SettingsPreferenceKey.UsbExclusiveEnabled ->
                applyRuntime(SettingsRuntimeEffect.SetUsbExclusiveEnabled(preferences.usbExclusiveEnabled))
            SettingsPreferenceKey.LyricsOffsetMs -> applyRuntime(SettingsRuntimeEffect.SetLyricsOffsetMs(runtime.lyricsOffsetMs))
            else -> Unit
        }
    }

    private fun runtimeUnavailableStatus(): String =
        if (currentState().preferences.languageMode == AppLanguage.MODE_ENGLISH) {
            "Runtime playback is not connected; the setting will apply when playback reconnects."
        } else {
            "播放服务当前未连接，设置将在重新连接后生效。"
        }

    private fun preferenceSaveFailedStatus(): String =
        if (currentState().preferences.languageMode == AppLanguage.MODE_ENGLISH) {
            "Failed to save the setting. Please try again."
        } else {
            "设置保存失败，请重试。"
        }
}
