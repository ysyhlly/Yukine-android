package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.SettingsAction
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsMetric
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsItem {
    data class Navigation(val label: String, val description: String = "") : SettingsItem
    data class Action(val label: String, val description: String = "") : SettingsItem
    data class Metric(val label: String, val value: String) : SettingsItem
}

data class SettingsUiState(
    val title: String = "",
    val metrics: List<SettingsMetric> = emptyList(),
    val items: List<SettingsItem> = emptyList()
)

data class SettingsAppliedStatusText(
    val themeApplied: String = "",
    val accentApplied: String = "",
    val languageApplied: String = "",
    val playbackSpeedApplied: String = "",
    val appVolumeApplied: String = "",
    val onlineLyricsEnabled: String = "",
    val onlineLyricsDisabled: String = "",
    val concurrentPlaybackEnabled: String = "",
    val concurrentPlaybackDisabled: String = "",
    val lyricsOffsetApplied: String = "",
    val audioEffectsApplied: String = "",
    val statusBarLyricsEnabled: String = "",
    val statusBarLyricsDisabled: String = "",
    val floatingLyricsEnabled: String = "",
    val floatingLyricsDisabled: String = "",
    val floatingLyricsPermissionRequired: String = "",
    val nowPlayingGesturesEnabled: String = "",
    val nowPlayingGesturesDisabled: String = "",
    val playbackRestoreEnabled: String = "",
    val playbackRestoreDisabled: String = "",
    val replayGainEnabled: String = "",
    val replayGainDisabled: String = "",
    val shareStyleApplied: String = ""
)

sealed interface SettingsEvent {
    data class NavigateSettingsPage(val page: String) : SettingsEvent
    data object OpenNetworkSources : SettingsEvent
    data object OpenDownloads : SettingsEvent
    data object LoadLibrary : SettingsEvent
    data object OpenAudioFilePicker : SettingsEvent
    data object OpenAudioFolderPicker : SettingsEvent
    data class SetOnlineLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data object ReloadCurrentLyrics : SettingsEvent
    data class ApplyLyricsOffset(val offsetMs: Long) : SettingsEvent
    data class StartSleepTimer(val minutes: Int) : SettingsEvent
    data object CancelSleepTimer : SettingsEvent
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsEvent
    data class ApplyAppVolume(val volume: Float) : SettingsEvent
    data class ApplyStreamingAudioQuality(val quality: String) : SettingsEvent
    data class ApplyShareStyle(val style: String) : SettingsEvent
    data class SetConcurrentPlaybackEnabled(val enabled: Boolean) : SettingsEvent
    data class ApplyAudioEffectSettings(val settings: AudioEffectSettings) : SettingsEvent
    data class SetStatusBarLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetFloatingLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data object OpenFloatingLyricsPermission : SettingsEvent
    data class SetNowPlayingGesturesEnabled(val enabled: Boolean) : SettingsEvent
    data class SetPlaybackRestoreEnabled(val enabled: Boolean) : SettingsEvent
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsEvent
    data object ExportBackup : SettingsEvent
    data object ImportBackup : SettingsEvent
    data class ApplyThemeMode(val mode: String) : SettingsEvent
    data class ApplyAccentMode(val accent: String) : SettingsEvent
    data class ApplyLanguageMode(val languageMode: String) : SettingsEvent
    data class ApplyStreamingGatewayEndpoint(val endpoint: String) : SettingsEvent
}

interface SettingsGateway {
    fun navigateSettingsPage(page: String)
    fun openNetworkSources()
    fun openDownloads() = Unit
    fun loadLibrary()
    fun openAudioFilePicker()
    fun openAudioFolderPicker()
    fun setOnlineLyricsEnabled(enabled: Boolean)
    fun reloadCurrentLyrics()
    fun applyLyricsOffset(offsetMs: Long)
    fun startSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun applyPlaybackSpeed(speed: Float)
    fun applyAppVolume(volume: Float)
    fun applyStreamingAudioQuality(quality: String)
    fun applyShareStyle(style: String)
    fun setConcurrentPlaybackEnabled(enabled: Boolean)
    fun applyAudioEffectSettings(settings: AudioEffectSettings)
    fun setStatusBarLyricsEnabled(enabled: Boolean)
    fun setFloatingLyricsEnabled(enabled: Boolean)
    fun openFloatingLyricsPermission()
    fun setNowPlayingGesturesEnabled(enabled: Boolean)
    fun setPlaybackRestoreEnabled(enabled: Boolean)
    fun setReplayGainEnabled(enabled: Boolean)
    fun exportBackup()
    fun importBackup()
    fun applyThemeMode(mode: String)
    fun applyAccentMode(accent: String)
    fun applyLanguageMode(languageMode: String)
    fun applyStreamingGatewayEndpoint(endpoint: String)
}

fun interface SettingsPreferenceGateway {
    fun save(update: SettingsPreferenceUpdate)
}

interface SettingsAppliedListener {
    fun onThemeModeApplied(mode: String)

    fun onAccentModeApplied(accent: String)

    fun onLanguageModeApplied(languageMode: String)

    fun onPlaybackSpeedApplied(speed: Float)

    fun onAppVolumeApplied(volume: Float)

    fun onStreamingAudioQualityApplied(quality: String)
    fun onShareStyleApplied(style: String)

    fun onConcurrentPlaybackEnabledApplied(enabled: Boolean)

    fun onAudioEffectSettingsApplied(settings: AudioEffectSettings)

    fun onStatusBarLyricsEnabledApplied(enabled: Boolean)

    fun onFloatingLyricsEnabledApplied(enabled: Boolean)

    fun onFloatingLyricsPermissionRequested()

    fun onNowPlayingGesturesEnabledApplied(enabled: Boolean)

    fun onPlaybackRestoreEnabledApplied(enabled: Boolean)

    fun onReplayGainEnabledApplied(enabled: Boolean)

    fun onOnlineLyricsEnabledApplied(enabled: Boolean)

    fun onLyricsOffsetApplied(offsetMs: Long)
}

class SettingsViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var gateway: SettingsGateway? = null
    private var preferenceGateway: SettingsPreferenceGateway? = null
    private var appliedListener: SettingsAppliedListener? = null

    fun bindGateway(nextGateway: SettingsGateway?) {
        gateway = nextGateway
    }

    fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?) {
        preferenceGateway = nextGateway
    }

    fun bindAppliedListener(nextListener: SettingsAppliedListener?) {
        appliedListener = nextListener
    }

    fun updatePage(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        _uiState.value = SettingsUiState(
            title = title,
            metrics = metrics.toList(),
            items = actions.map { action -> action.toSettingsItem() } +
            metrics.map { metric -> SettingsItem.Metric(metric.label, metric.value) }
        )
    }

    fun onEvent(event: SettingsEvent) {
        val currentGateway = gateway ?: return
        when (event) {
            is SettingsEvent.NavigateSettingsPage -> currentGateway.navigateSettingsPage(event.page)
            SettingsEvent.OpenNetworkSources -> currentGateway.openNetworkSources()
            SettingsEvent.OpenDownloads -> currentGateway.openDownloads()
            SettingsEvent.LoadLibrary -> currentGateway.loadLibrary()
            SettingsEvent.OpenAudioFilePicker -> currentGateway.openAudioFilePicker()
            SettingsEvent.OpenAudioFolderPicker -> currentGateway.openAudioFolderPicker()
            is SettingsEvent.SetOnlineLyricsEnabled -> currentGateway.setOnlineLyricsEnabled(event.enabled)
            SettingsEvent.ReloadCurrentLyrics -> currentGateway.reloadCurrentLyrics()
            is SettingsEvent.ApplyLyricsOffset -> currentGateway.applyLyricsOffset(event.offsetMs)
            is SettingsEvent.StartSleepTimer -> currentGateway.startSleepTimer(event.minutes)
            SettingsEvent.CancelSleepTimer -> currentGateway.cancelSleepTimer()
            is SettingsEvent.ApplyPlaybackSpeed -> currentGateway.applyPlaybackSpeed(event.speed)
            is SettingsEvent.ApplyAppVolume -> currentGateway.applyAppVolume(event.volume)
            is SettingsEvent.ApplyStreamingAudioQuality -> currentGateway.applyStreamingAudioQuality(event.quality)
            is SettingsEvent.ApplyShareStyle -> currentGateway.applyShareStyle(event.style)
            is SettingsEvent.SetConcurrentPlaybackEnabled -> currentGateway.setConcurrentPlaybackEnabled(event.enabled)
            is SettingsEvent.ApplyAudioEffectSettings -> currentGateway.applyAudioEffectSettings(event.settings)
            is SettingsEvent.SetStatusBarLyricsEnabled -> currentGateway.setStatusBarLyricsEnabled(event.enabled)
            is SettingsEvent.SetFloatingLyricsEnabled -> currentGateway.setFloatingLyricsEnabled(event.enabled)
            SettingsEvent.OpenFloatingLyricsPermission -> currentGateway.openFloatingLyricsPermission()
            is SettingsEvent.SetNowPlayingGesturesEnabled -> currentGateway.setNowPlayingGesturesEnabled(event.enabled)
            is SettingsEvent.SetPlaybackRestoreEnabled -> currentGateway.setPlaybackRestoreEnabled(event.enabled)
            is SettingsEvent.SetReplayGainEnabled -> currentGateway.setReplayGainEnabled(event.enabled)
            is SettingsEvent.ExportBackup -> currentGateway.exportBackup()
            is SettingsEvent.ImportBackup -> currentGateway.importBackup()
            is SettingsEvent.ApplyThemeMode -> currentGateway.applyThemeMode(event.mode)
            is SettingsEvent.ApplyAccentMode -> currentGateway.applyAccentMode(event.accent)
            is SettingsEvent.ApplyLanguageMode -> currentGateway.applyLanguageMode(event.languageMode)
            is SettingsEvent.ApplyStreamingGatewayEndpoint ->
                currentGateway.applyStreamingGatewayEndpoint(event.endpoint)
        }
    }

    fun applyThemeMode(nextMode: String) {
        val mode = EchoTheme.normalizeMode(nextMode)
        EchoTheme.setMode(mode)
        appliedListener?.onThemeModeApplied(mode)
        savePreference(SettingsPreferenceKey.ThemeMode, mode)
    }

    fun applyAccentMode(nextAccent: String) {
        val accent = EchoTheme.normalizeAccent(nextAccent)
        EchoTheme.setAccent(accent)
        appliedListener?.onAccentModeApplied(accent)
        savePreference(SettingsPreferenceKey.AccentMode, accent)
    }

    fun applyLanguageMode(nextLanguageMode: String) {
        val languageMode = AppLanguage.normalizeMode(nextLanguageMode)
        appliedListener?.onLanguageModeApplied(languageMode)
        savePreference(SettingsPreferenceKey.LanguageMode, languageMode)
    }

    fun applyPlaybackSpeed(speed: Float) {
        val normalizedSpeed = normalizePlaybackSpeed(speed)
        appliedListener?.onPlaybackSpeedApplied(normalizedSpeed)
        savePreference(SettingsPreferenceKey.PlaybackSpeed, normalizedSpeed)
    }

    fun applyAppVolume(volume: Float) {
        val normalizedVolume = normalizeAppVolume(volume)
        appliedListener?.onAppVolumeApplied(normalizedVolume)
        savePreference(SettingsPreferenceKey.AppVolume, normalizedVolume)
    }

    fun applyStreamingAudioQuality(quality: String) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        appliedListener?.onStreamingAudioQualityApplied(normalizedQuality)
        savePreference(SettingsPreferenceKey.StreamingAudioQuality, normalizedQuality)
    }

    fun applyShareStyle(style: String) {
        val normalizedStyle = TrackShareStyle.normalize(style)
        appliedListener?.onShareStyleApplied(normalizedStyle)
        savePreference(SettingsPreferenceKey.ShareStyle, normalizedStyle)
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        appliedListener?.onOnlineLyricsEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.OnlineLyricsEnabled, enabled)
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        appliedListener?.onConcurrentPlaybackEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.ConcurrentPlaybackEnabled, enabled)
    }

    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        appliedListener?.onAudioEffectSettingsApplied(settings)
        savePreference(SettingsPreferenceKey.AudioEffectSettings, settings)
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        appliedListener?.onStatusBarLyricsEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.StatusBarLyricsEnabled, enabled)
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        appliedListener?.onFloatingLyricsEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.FloatingLyricsEnabled, enabled)
    }

    fun openFloatingLyricsPermission() {
        appliedListener?.onFloatingLyricsPermissionRequested()
    }

    fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        appliedListener?.onNowPlayingGesturesEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.NowPlayingGesturesEnabled, enabled)
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        appliedListener?.onPlaybackRestoreEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.PlaybackRestoreEnabled, enabled)
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        appliedListener?.onReplayGainEnabledApplied(enabled)
        savePreference(SettingsPreferenceKey.ReplayGainEnabled, enabled)
    }

    fun applyLyricsOffset(offsetMs: Long) {
        val normalizedOffsetMs = normalizeLyricsOffsetMs(offsetMs)
        appliedListener?.onLyricsOffsetApplied(normalizedOffsetMs)
        savePreference(SettingsPreferenceKey.LyricsOffsetMs, normalizedOffsetMs)
    }

    fun prepareAppliedStatusText(
        languageMode: String,
        themeMode: String,
        accentMode: String,
        playbackSpeed: Float,
        appVolume: Float,
        lyricsOffsetMs: Long
    ): SettingsAppliedStatusText {
        val normalizedLanguageMode = AppLanguage.normalizeMode(languageMode)
        return SettingsAppliedStatusText(
            themeApplied = AppLanguage.text(normalizedLanguageMode, "theme.applied") +
                    AppLanguage.themeLabel(themeMode, normalizedLanguageMode),
            accentApplied = AppLanguage.text(normalizedLanguageMode, "accent.applied") +
                    AppLanguage.accentLabel(accentMode, normalizedLanguageMode),
            languageApplied = AppLanguage.text(normalizedLanguageMode, "language.applied") +
                    AppLanguage.labelFor(normalizedLanguageMode),
            playbackSpeedApplied = AppLanguage.text(normalizedLanguageMode, "speed.applied") +
                    SettingsPageRenderController.playbackSpeedLabel(playbackSpeed),
            appVolumeApplied = AppLanguage.text(normalizedLanguageMode, "volume.applied") +
                    SettingsPageRenderController.appVolumeLabel(appVolume),
            onlineLyricsEnabled = AppLanguage.text(normalizedLanguageMode, "online.lyrics.enabled"),
            onlineLyricsDisabled = AppLanguage.text(normalizedLanguageMode, "online.lyrics.disabled"),
            concurrentPlaybackEnabled = AppLanguage.text(normalizedLanguageMode, "concurrent.playback.enabled"),
            concurrentPlaybackDisabled = AppLanguage.text(normalizedLanguageMode, "concurrent.playback.disabled"),
            lyricsOffsetApplied = AppLanguage.text(normalizedLanguageMode, "lyrics.offset.applied") +
                    SettingsPageRenderController.lyricsOffsetLabel(lyricsOffsetMs),
            audioEffectsApplied = AppLanguage.text(normalizedLanguageMode, "audio.effects.applied"),
            statusBarLyricsEnabled = AppLanguage.text(normalizedLanguageMode, "status.bar.lyrics.enabled"),
            statusBarLyricsDisabled = AppLanguage.text(normalizedLanguageMode, "status.bar.lyrics.disabled"),
            floatingLyricsEnabled = AppLanguage.text(normalizedLanguageMode, "floating.lyrics.enabled"),
            floatingLyricsDisabled = AppLanguage.text(normalizedLanguageMode, "floating.lyrics.disabled"),
            floatingLyricsPermissionRequired = AppLanguage.text(normalizedLanguageMode, "floating.lyrics.permission.required"),
            nowPlayingGesturesEnabled = AppLanguage.text(normalizedLanguageMode, "now.playing.gestures.enabled"),
            nowPlayingGesturesDisabled = AppLanguage.text(normalizedLanguageMode, "now.playing.gestures.disabled"),
            playbackRestoreEnabled = AppLanguage.text(normalizedLanguageMode, "playback.restore.enabled"),
            playbackRestoreDisabled = AppLanguage.text(normalizedLanguageMode, "playback.restore.disabled"),
            replayGainEnabled = AppLanguage.text(normalizedLanguageMode, "replay.gain.enabled"),
            replayGainDisabled = AppLanguage.text(normalizedLanguageMode, "replay.gain.disabled"),
            shareStyleApplied = AppLanguage.text(normalizedLanguageMode, "share.style.applied")
        )
    }

    private fun SettingsAction.toSettingsItem(): SettingsItem {
        return if (description.isBlank()) {
            SettingsItem.Action(label)
        } else {
            SettingsItem.Navigation(label, description)
        }
    }

    private fun savePreference(key: SettingsPreferenceKey, value: Any) {
        val currentGateway = preferenceGateway ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                currentGateway.save(SettingsPreferenceUpdate(key, value))
            }
        }
    }

    private fun normalizePlaybackSpeed(speed: Float): Float {
        if (speed < 0.5f) {
            return 0.5f
        }
        if (speed > 2.0f) {
            return 2.0f
        }
        return Math.round(speed * 100.0f) / 100.0f
    }

    private fun normalizeAppVolume(volume: Float): Float {
        if (volume < 0.0f) {
            return 0.0f
        }
        if (volume > 1.0f) {
            return 1.0f
        }
        return Math.round(volume * 100.0f) / 100.0f
    }

    private fun normalizeLyricsOffsetMs(offsetMs: Long): Long {
        if (offsetMs < -5000L) {
            return -5000L
        }
        if (offsetMs > 5000L) {
            return 5000L
        }
        return Math.round(offsetMs / 100.0) * 100L
    }
}
