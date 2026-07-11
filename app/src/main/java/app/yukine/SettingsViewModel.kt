package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.SettingsAction
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsListScrollState
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

data class SettingsPreferencesSnapshot(
    val themeMode: String = EchoTheme.MODE_SYSTEM,
    val accentMode: String = EchoTheme.ACCENT_BLUE,
    val languageMode: String = AppLanguage.MODE_SYSTEM,
    val playbackSpeed: Float = 1.0f,
    val appVolume: Float = 1.0f,
    val streamingAudioQuality: String = StreamingQualityPreference.defaultValue(),
    val concurrentPlaybackEnabled: Boolean = false,
    val audioEffectSettings: AudioEffectSettings = AudioEffectSettings.DEFAULT,
    val statusBarLyricsEnabled: Boolean = true,
    val systemMediaLyricsTitleEnabled: Boolean = false,
    val floatingLyricsEnabled: Boolean = false,
    val nowPlayingGesturesEnabled: Boolean = true,
    val playbackRestoreEnabled: Boolean = true,
    val replayGainEnabled: Boolean = true,
    val debugPromptsEnabled: Boolean = false,
    val shareStyle: String = TrackShareStyle.defaultValue(),
    val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty()
)

data class RuntimeSettingsStatus(
    val audioPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val playbackServiceConnected: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val lyricsOffsetMs: Long = 0L,
    val onlineLyricsEnabled: Boolean = false,
    val librarySongCount: Int = 0,
    val libraryAlbumCount: Int = 0,
    val libraryArtistCount: Int = 0,
    val streamingGatewayEndpoint: String = StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT,
    val streamingGatewayConfigured: Boolean = false,
    val hiddenLibraryItems: List<HiddenLibraryItemUi> = emptyList()
)

data class HiddenLibraryItemUi(val sourceKey: String, val label: String)

data class SettingsState(
    val page: SettingsPage = SettingsPage.Home,
    val preferences: SettingsPreferencesSnapshot = SettingsPreferencesSnapshot(),
    val runtime: RuntimeSettingsStatus = RuntimeSettingsStatus(),
    val actions: List<SettingsAction> = emptyList(),
    val ui: SettingsUiState = SettingsUiState()
) : SettingsDestinationState {
    override val destinationTitle: String
        get() = ui.title
    override val destinationMetrics: List<SettingsMetric>
        get() = ui.metrics
    override val destinationActions: List<SettingsAction>
        get() = actions
}

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
    val shareStyleApplied: String = "",
    val pageBackgroundApplied: String = "",
    val pageBackgroundCleared: String = ""
)

sealed interface SettingsEffect {
    data class ShowStatus(val message: String) : SettingsEffect
    data class NavigatePage(val page: SettingsPage) : SettingsEffect
    data object OpenNetworkSources : SettingsEffect
    data object OpenDownloads : SettingsEffect
    data object RequestNeededPermissions : SettingsEffect
    data object LoadLibrary : SettingsEffect
    data object OpenAudioFilePicker : SettingsEffect
    data object OpenAudioFolderPicker : SettingsEffect
    data object ReloadCurrentLyrics : SettingsEffect
    data class StartSleepTimer(val minutes: Int) : SettingsEffect
    data object CancelSleepTimer : SettingsEffect
    data object OpenFloatingLyricsPermission : SettingsEffect
    data class ChoosePageBackground(val page: String) : SettingsEffect
    data object ExportBackup : SettingsEffect
    data object ImportBackup : SettingsEffect
    data class ApplyStreamingGatewayEndpoint(val endpoint: String) : SettingsEffect
    data class RestoreHiddenLibraryItem(val sourceKey: String) : SettingsEffect
    data object RestoreAllHiddenLibraryItems : SettingsEffect
}

sealed interface SettingsEvent {
    data class NavigateSettingsPage(val page: SettingsPage) : SettingsEvent
    data object OpenNetworkSources : SettingsEvent
    data object OpenDownloads : SettingsEvent
    data object RequestNeededPermissions : SettingsEvent
    data object LoadLibrary : SettingsEvent
    data object OpenAudioFilePicker : SettingsEvent
    data object OpenAudioFolderPicker : SettingsEvent
    data class RestoreHiddenLibraryItem(val sourceKey: String) : SettingsEvent
    data object RestoreAllHiddenLibraryItems : SettingsEvent
    data class SetOnlineLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data object ReloadCurrentLyrics : SettingsEvent
    data class ApplyLyricsOffset(val offsetMs: Long) : SettingsEvent
    data class StartSleepTimer(val minutes: Int) : SettingsEvent
    data object CancelSleepTimer : SettingsEvent
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsEvent
    data class ApplyAppVolume(val volume: Float) : SettingsEvent
    data class ApplyStreamingAudioQuality(val quality: String) : SettingsEvent
    data class ApplyShareStyle(val style: String) : SettingsEvent
    data class SetAudioExclusiveEnabled(val enabled: Boolean) : SettingsEvent
    data class SetConcurrentPlaybackEnabled(val enabled: Boolean) : SettingsEvent
    data class ApplyAudioEffectSettings(val settings: AudioEffectSettings) : SettingsEvent
    data class SetStatusBarLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetSystemMediaLyricsTitleEnabled(val enabled: Boolean) : SettingsEvent
    data class SetFloatingLyricsEnabled(val enabled: Boolean) : SettingsEvent
    data object OpenFloatingLyricsPermission : SettingsEvent
    data class SetNowPlayingGesturesEnabled(val enabled: Boolean) : SettingsEvent
    data class SetPlaybackRestoreEnabled(val enabled: Boolean) : SettingsEvent
    data class SetReplayGainEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDebugPromptsEnabled(val enabled: Boolean) : SettingsEvent
    data class ChoosePageBackground(val page: String) : SettingsEvent
    data class ClearPageBackground(val page: String) : SettingsEvent
    data object ExportBackup : SettingsEvent
    data object ImportBackup : SettingsEvent
    data class ApplyThemeMode(val mode: String) : SettingsEvent
    data class ApplyAccentMode(val accent: String) : SettingsEvent
    data class ApplyLanguageMode(val languageMode: String) : SettingsEvent
    data class ApplyStreamingGatewayEndpoint(val endpoint: String) : SettingsEvent
}

fun interface SettingsEffectListener {
    fun onEffect(effect: SettingsEffect)
}

fun interface SettingsRuntimeEffectListener {
    fun onRuntimeEffect(effect: SettingsRuntimeEffect): Boolean
}

fun interface SettingsStoreMirror {
    fun sync(preferences: SettingsPreferencesSnapshot)
}

fun interface SettingsPreferenceGateway {
    fun save(update: SettingsPreferenceUpdate)
}

class SettingsViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    val scrollState = SettingsListScrollState()
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    private val _chromeState = MutableStateFlow(SettingsChromeState())
    val chromeState: StateFlow<SettingsChromeState> = _chromeState.asStateFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val pendingEffects = java.util.ArrayDeque<SettingsEffect>()
    private var preferenceGateway: SettingsPreferenceGateway? = null
    private var storeMirror: SettingsStoreMirror? = null
    private var effectListener: SettingsEffectListener? = null
    private var runtimeEffectListener: SettingsRuntimeEffectListener? = null

    fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?) {
        preferenceGateway = nextGateway
    }

    fun bindStoreMirror(nextMirror: SettingsStoreMirror?) {
        storeMirror = nextMirror
    }

    fun bindEffectListener(nextListener: SettingsEffectListener?) {
        effectListener = nextListener
    }

    fun bindRuntimeEffectListener(nextListener: SettingsRuntimeEffectListener?) {
        runtimeEffectListener = nextListener
    }

    fun scrollToTopOnNextRender() {
        scrollState.scrollToTop()
    }

    fun updateSettingsContext(
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ) {
        storeMirror?.sync(preferences)
        _state.value = _state.value.copy(
            preferences = preferences,
            runtime = runtime
        )
        syncChromeState(preferences)
    }

    internal fun renderCurrentPage(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ): SettingsPageStateContent {
        val content = buildPageContent(page, preferences, runtime)
        _state.value = _state.value.copy(
            page = page,
            preferences = preferences,
            runtime = runtime,
            actions = content.actions,
            ui = content.uiState
        )
        syncChromeState(preferences)
        _uiState.value = content.uiState
        return content
    }

    internal fun renderCurrentPage(): SettingsPageStateContent {
        val current = _state.value
        val content = buildPageContent(current.page, current.preferences, current.runtime)
        _state.value = current.copy(
            actions = content.actions,
            ui = content.uiState
        )
        _uiState.value = content.uiState
        return content
    }

    fun renderPageFromHost(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ) {
        renderCurrentPage(page, preferences, runtime)
    }

    fun onEvent(event: SettingsEvent) {
        if (event is SettingsEvent.NavigateSettingsPage) {
            val current = _state.value
            renderCurrentPage(event.page, current.preferences, current.runtime)
            emitEffect(SettingsEffect.NavigatePage(event.page))
            return
        }
        when (event) {
            is SettingsEvent.NavigateSettingsPage -> Unit
            SettingsEvent.OpenNetworkSources -> emitEffect(SettingsEffect.OpenNetworkSources)
            SettingsEvent.OpenDownloads -> emitEffect(SettingsEffect.OpenDownloads)
            SettingsEvent.RequestNeededPermissions -> emitEffect(SettingsEffect.RequestNeededPermissions)
            SettingsEvent.LoadLibrary -> emitEffect(SettingsEffect.LoadLibrary)
            SettingsEvent.OpenAudioFilePicker -> emitEffect(SettingsEffect.OpenAudioFilePicker)
            SettingsEvent.OpenAudioFolderPicker -> emitEffect(SettingsEffect.OpenAudioFolderPicker)
            is SettingsEvent.RestoreHiddenLibraryItem ->
                emitEffect(SettingsEffect.RestoreHiddenLibraryItem(event.sourceKey))
            SettingsEvent.RestoreAllHiddenLibraryItems -> emitEffect(SettingsEffect.RestoreAllHiddenLibraryItems)
            is SettingsEvent.SetOnlineLyricsEnabled -> setOnlineLyricsEnabled(event.enabled)
            SettingsEvent.ReloadCurrentLyrics -> emitEffect(SettingsEffect.ReloadCurrentLyrics)
            is SettingsEvent.ApplyLyricsOffset -> applyLyricsOffset(event.offsetMs)
            is SettingsEvent.StartSleepTimer -> emitEffect(SettingsEffect.StartSleepTimer(event.minutes))
            SettingsEvent.CancelSleepTimer -> emitEffect(SettingsEffect.CancelSleepTimer)
            is SettingsEvent.ApplyPlaybackSpeed -> applyPlaybackSpeed(event.speed)
            is SettingsEvent.ApplyAppVolume -> applyAppVolume(event.volume)
            is SettingsEvent.ApplyStreamingAudioQuality -> applyStreamingAudioQuality(event.quality)
            is SettingsEvent.ApplyShareStyle -> applyShareStyle(event.style)
            is SettingsEvent.SetAudioExclusiveEnabled -> setAudioExclusiveEnabled(event.enabled)
            is SettingsEvent.SetConcurrentPlaybackEnabled -> setConcurrentPlaybackEnabled(event.enabled)
            is SettingsEvent.ApplyAudioEffectSettings -> applyAudioEffectSettings(event.settings)
            is SettingsEvent.SetStatusBarLyricsEnabled -> setStatusBarLyricsEnabled(event.enabled)
            is SettingsEvent.SetSystemMediaLyricsTitleEnabled -> setSystemMediaLyricsTitleEnabled(event.enabled)
            is SettingsEvent.SetFloatingLyricsEnabled -> setFloatingLyricsEnabled(event.enabled)
            SettingsEvent.OpenFloatingLyricsPermission -> emitEffect(SettingsEffect.OpenFloatingLyricsPermission)
            is SettingsEvent.SetNowPlayingGesturesEnabled -> setNowPlayingGesturesEnabled(event.enabled)
            is SettingsEvent.SetPlaybackRestoreEnabled -> setPlaybackRestoreEnabled(event.enabled)
            is SettingsEvent.SetReplayGainEnabled -> setReplayGainEnabled(event.enabled)
            is SettingsEvent.SetDebugPromptsEnabled -> setDebugPromptsEnabled(event.enabled)
            is SettingsEvent.ChoosePageBackground -> emitEffect(SettingsEffect.ChoosePageBackground(event.page))
            is SettingsEvent.ClearPageBackground -> clearPageBackground(event.page)
            is SettingsEvent.ExportBackup -> emitEffect(SettingsEffect.ExportBackup)
            is SettingsEvent.ImportBackup -> emitEffect(SettingsEffect.ImportBackup)
            is SettingsEvent.ApplyThemeMode -> applyThemeMode(event.mode)
            is SettingsEvent.ApplyAccentMode -> applyAccentMode(event.accent)
            is SettingsEvent.ApplyLanguageMode -> applyLanguageMode(event.languageMode)
            is SettingsEvent.ApplyStreamingGatewayEndpoint ->
                emitEffect(SettingsEffect.ApplyStreamingGatewayEndpoint(event.endpoint))
        }
    }

    fun drainEffects(): List<SettingsEffect> {
        val drained = ArrayList<SettingsEffect>(pendingEffects.size)
        while (pendingEffects.isNotEmpty()) {
            drained.add(pendingEffects.removeFirst())
        }
        return drained
    }

    private fun buildPageContent(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ): SettingsPageStateContent {
        val languageMode = preferences.languageMode
        return when (page) {
            SettingsPage.AppearanceGroup ->
                SettingsPageStateBuilder.appearanceGroup(
                    languageMode,
                    preferences.themeMode,
                    preferences.accentMode,
                    preferences.pageBackgrounds,
                    onNavigate = ::navigateSettingsPage
                )
            SettingsPage.PlaybackGroup ->
                SettingsPageStateBuilder.playbackGroup(
                    languageMode,
                    preferences.playbackSpeed,
                    preferences.appVolume,
                    preferences.concurrentPlaybackEnabled,
                    preferences.audioEffectSettings,
                    preferences.nowPlayingGesturesEnabled,
                    preferences.playbackRestoreEnabled,
                    preferences.replayGainEnabled,
                    runtime.sleepTimerRemainingMs,
                    onNavigate = ::navigateSettingsPage,
                    onReplayGainEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetReplayGainEnabled(enabled))
                    },
                    onNowPlayingGesturesEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetNowPlayingGesturesEnabled(enabled))
                    },
                    onPlaybackRestoreEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetPlaybackRestoreEnabled(enabled))
                    },
                    onAudioExclusiveEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetAudioExclusiveEnabled(enabled))
                    }
                )
            SettingsPage.LibraryGroup,
            SettingsPage.Library ->
                SettingsPageStateBuilder.library(
                    languageMode,
                    SettingsBackStack.parent(page),
                    runtime.librarySongCount,
                    runtime.libraryAlbumCount,
                    runtime.libraryArtistCount,
                    runtime.audioPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onLoadLibrary = { onEvent(SettingsEvent.LoadLibrary) },
                    onOpenAudioFilePicker = { onEvent(SettingsEvent.OpenAudioFilePicker) },
                    onOpenAudioFolderPicker = { onEvent(SettingsEvent.OpenAudioFolderPicker) },
                    hiddenItems = runtime.hiddenLibraryItems,
                    onRestoreHidden = { sourceKey -> onEvent(SettingsEvent.RestoreHiddenLibraryItem(sourceKey)) },
                    onRestoreAllHidden = { onEvent(SettingsEvent.RestoreAllHiddenLibraryItems) }
                )
            SettingsPage.LyricsGroup ->
                SettingsPageStateBuilder.lyricsGroup(
                    languageMode,
                    runtime.lyricsOffsetMs,
                    runtime.onlineLyricsEnabled,
                    preferences.statusBarLyricsEnabled,
                    preferences.systemMediaLyricsTitleEnabled,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOnlineLyricsEnabledChange = { enabled -> onEvent(SettingsEvent.SetOnlineLyricsEnabled(enabled)) },
                    onSystemMediaLyricsTitleEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetSystemMediaLyricsTitleEnabled(enabled))
                    },
                    onStatusBarLyricsEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetStatusBarLyricsEnabled(enabled))
                    },
                    onReloadLyrics = { onEvent(SettingsEvent.ReloadCurrentLyrics) },
                    onApplyLyricsOffset = { offset -> onEvent(SettingsEvent.ApplyLyricsOffset(offset)) }
                )
            SettingsPage.SourcesGroup ->
                SettingsPageStateBuilder.sourcesGroup(
                    languageMode,
                    preferences.streamingAudioQuality,
                    preferences.shareStyle,
                    runtime.streamingGatewayConfigured,
                    onNavigate = ::navigateSettingsPage,
                    onOpenNetworkSources = { onEvent(SettingsEvent.OpenNetworkSources) }
                )
            SettingsPage.AboutGroup ->
                SettingsPageStateBuilder.aboutGroup(
                    languageMode,
                    runtime.audioPermissionGranted,
                    runtime.notificationPermissionGranted,
                    runtime.playbackServiceConnected,
                    preferences.debugPromptsEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onExportBackup = { onEvent(SettingsEvent.ExportBackup) },
                    onImportBackup = { onEvent(SettingsEvent.ImportBackup) },
                    onDebugPromptsEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetDebugPromptsEnabled(enabled))
                    }
                )
            SettingsPage.Appearance ->
                SettingsPageStateBuilder.theme(
                    languageMode,
                    preferences.themeMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyTheme = { mode -> onEvent(SettingsEvent.ApplyThemeMode(mode)) }
                )
            SettingsPage.AdvancedTheme ->
                SettingsPageStateBuilder.advancedTheme(
                    languageMode,
                    preferences.themeMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyTheme = { mode -> onEvent(SettingsEvent.ApplyThemeMode(mode)) }
                )
            SettingsPage.Accent ->
                SettingsPageStateBuilder.accent(
                    languageMode,
                    preferences.accentMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyAccent = { accent -> onEvent(SettingsEvent.ApplyAccentMode(accent)) }
                )
            SettingsPage.Language ->
                SettingsPageStateBuilder.language(
                    languageMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyLanguage = { mode -> onEvent(SettingsEvent.ApplyLanguageMode(mode)) }
                )
            SettingsPage.PageBackground ->
                SettingsPageStateBuilder.pageBackgrounds(
                    languageMode,
                    preferences.pageBackgrounds,
                    onNavigate = ::navigateSettingsPage,
                    onChoosePageBackground = { target -> onEvent(SettingsEvent.ChoosePageBackground(target)) },
                    onClearPageBackground = { target -> onEvent(SettingsEvent.ClearPageBackground(target)) }
                )
            SettingsPage.PlaybackSpeed ->
                SettingsPageStateBuilder.playbackSpeed(
                    languageMode,
                    preferences.playbackSpeed,
                    onNavigate = ::navigateSettingsPage,
                    onApplySpeed = { speed -> onEvent(SettingsEvent.ApplyPlaybackSpeed(speed)) }
                )
            SettingsPage.AppVolume ->
                SettingsPageStateBuilder.appVolume(
                    languageMode,
                    preferences.appVolume,
                    onNavigate = ::navigateSettingsPage,
                    onApplyVolume = { volume -> onEvent(SettingsEvent.ApplyAppVolume(volume)) }
                )
            SettingsPage.AudioEffects ->
                SettingsPageStateBuilder.audioEffects(
                    languageMode,
                    preferences.audioEffectSettings,
                    onNavigate = ::navigateSettingsPage,
                    onApplyAudioEffects = { effects -> onEvent(SettingsEvent.ApplyAudioEffectSettings(effects)) }
                )
            SettingsPage.NowPlayingGestures ->
                SettingsPageStateBuilder.nowPlayingGestures(
                    languageMode,
                    preferences.nowPlayingGesturesEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> onEvent(SettingsEvent.SetNowPlayingGesturesEnabled(enabled)) }
                )
            SettingsPage.PlaybackRestore ->
                SettingsPageStateBuilder.playbackRestore(
                    languageMode,
                    preferences.playbackRestoreEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> onEvent(SettingsEvent.SetPlaybackRestoreEnabled(enabled)) }
                )
            SettingsPage.ReplayGain ->
                SettingsPageStateBuilder.replayGain(
                    languageMode,
                    preferences.replayGainEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> onEvent(SettingsEvent.SetReplayGainEnabled(enabled)) }
                )
            SettingsPage.StreamingAudioQuality ->
                SettingsPageStateBuilder.streamingAudioQuality(
                    languageMode,
                    preferences.streamingAudioQuality,
                    onNavigate = ::navigateSettingsPage,
                    onApplyQuality = { quality -> onEvent(SettingsEvent.ApplyStreamingAudioQuality(quality)) }
                )
            SettingsPage.ShareStyle ->
                SettingsPageStateBuilder.shareStyle(
                    languageMode,
                    preferences.shareStyle,
                    onNavigate = ::navigateSettingsPage,
                    onApplyStyle = { style -> onEvent(SettingsEvent.ApplyShareStyle(style)) }
                )
            SettingsPage.ConcurrentPlayback ->
                SettingsPageStateBuilder.audioExclusive(
                    languageMode,
                    !preferences.concurrentPlaybackEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> onEvent(SettingsEvent.SetAudioExclusiveEnabled(enabled)) }
                )
            SettingsPage.SleepTimer ->
                SettingsPageStateBuilder.sleepTimer(
                    languageMode,
                    runtime.sleepTimerRemainingMs,
                    onNavigate = ::navigateSettingsPage,
                    onStartTimer = { minutes -> onEvent(SettingsEvent.StartSleepTimer(minutes)) },
                    onCancelTimer = { onEvent(SettingsEvent.CancelSleepTimer) }
                )
            SettingsPage.Lyrics ->
                SettingsPageStateBuilder.lyrics(
                    languageMode,
                    runtime.lyricsOffsetMs,
                    runtime.onlineLyricsEnabled,
                    preferences.statusBarLyricsEnabled,
                    preferences.systemMediaLyricsTitleEnabled,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOnlineLyricsEnabledChange = { enabled -> onEvent(SettingsEvent.SetOnlineLyricsEnabled(enabled)) },
                    onSystemMediaLyricsTitleEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetSystemMediaLyricsTitleEnabled(enabled))
                    },
                    onStatusBarLyricsEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetStatusBarLyricsEnabled(enabled))
                    },
                    onReloadLyrics = { onEvent(SettingsEvent.ReloadCurrentLyrics) },
                    onApplyLyricsOffset = { offset -> onEvent(SettingsEvent.ApplyLyricsOffset(offset)) }
                )
            SettingsPage.StatusBarLyrics ->
                SettingsPageStateBuilder.statusBarLyrics(
                    languageMode,
                    preferences.statusBarLyricsEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> onEvent(SettingsEvent.SetStatusBarLyricsEnabled(enabled)) }
                )
            SettingsPage.FloatingLyrics ->
                SettingsPageStateBuilder.floatingLyrics(
                    languageMode,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOpenPermission = { onEvent(SettingsEvent.OpenFloatingLyricsPermission) },
                    onToggle = { enabled -> onEvent(SettingsEvent.SetFloatingLyricsEnabled(enabled)) }
                )
            SettingsPage.StreamingGateway ->
                SettingsPageStateBuilder.streamingGateway(
                    languageMode,
                    runtime.streamingGatewayEndpoint,
                    runtime.streamingGatewayConfigured,
                    onNavigate = ::navigateSettingsPage,
                    onApplyEndpoint = { endpoint -> onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint(endpoint)) }
                )
            SettingsPage.Home,
            SettingsPage.Downloads ->
                SettingsPageStateBuilder.home(
                    languageMode,
                    runtime.audioPermissionGranted,
                    runtime.notificationPermissionGranted,
                    runtime.playbackServiceConnected,
                    onNavigate = ::navigateSettingsPage,
                    onRequestNeededPermissions = { onEvent(SettingsEvent.RequestNeededPermissions) },
                    onOpenDownloads = { onEvent(SettingsEvent.OpenDownloads) }
                )
        }
    }

    private fun navigateSettingsPage(page: SettingsPage) {
        onEvent(SettingsEvent.NavigateSettingsPage(page))
    }

    private fun emitEffect(effect: SettingsEffect) {
        pendingEffects.add(effect)
        effectListener?.onEffect(effect)
    }

    private fun applyRuntimeEffect(effect: SettingsRuntimeEffect): Boolean {
        return runtimeEffectListener?.onRuntimeEffect(effect) != false
    }

    private fun currentAppliedStatusText(offsetMs: Long = 0L): SettingsAppliedStatusText {
        val current = _state.value
        return prepareAppliedStatusText(
            current.preferences.languageMode,
            current.preferences.themeMode,
            current.preferences.accentMode,
            current.preferences.playbackSpeed,
            current.preferences.appVolume,
            offsetMs
        )
    }

    private fun emitAppliedStatus(message: String) {
        if (message.isNotBlank()) {
            emitEffect(SettingsEffect.ShowStatus(message))
        }
    }

    private fun streamingQualityAppliedStatus(quality: String): String {
        val languageMode = _state.value.preferences.languageMode
        return AppLanguage.text(languageMode, "streaming.quality.applied") +
            SettingsLabelFormatter.streamingQualityLabel(quality, languageMode)
    }

    private fun shareStyleAppliedStatus(style: String): String {
        val languageMode = _state.value.preferences.languageMode
        return currentAppliedStatusText().shareStyleApplied +
            SettingsLabelFormatter.shareStyleLabel(style, languageMode)
    }

    private fun pageBackgroundAppliedStatus(page: String, cleared: Boolean): String {
        val languageMode = _state.value.preferences.languageMode
        val statusText = currentAppliedStatusText()
        val prefix = if (cleared) {
            statusText.pageBackgroundCleared
        } else {
            statusText.pageBackgroundApplied
        }
        return prefix + SettingsLabelFormatter.pageBackgroundPageLabel(page, languageMode)
    }

    private fun updatePreferences(transform: (SettingsPreferencesSnapshot) -> SettingsPreferencesSnapshot) {
        val current = _state.value
        val nextPreferences = transform(current.preferences)
        storeMirror?.sync(nextPreferences)
        renderCurrentPage(current.page, nextPreferences, current.runtime)
    }

    private fun syncChromeState(preferences: SettingsPreferencesSnapshot) {
        _chromeState.value = SettingsChromeState(
            pageBackgrounds = preferences.pageBackgrounds,
            nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled
        )
    }

    private fun updateRuntime(transform: (RuntimeSettingsStatus) -> RuntimeSettingsStatus) {
        val current = _state.value
        renderCurrentPage(current.page, current.preferences, transform(current.runtime))
    }

    fun applyThemeMode(nextMode: String) {
        val mode = EchoTheme.normalizeMode(nextMode)
        EchoTheme.setMode(mode)
        applyRuntimeEffect(SettingsRuntimeEffect.ApplyThemeSurface)
        updatePreferences { it.copy(themeMode = mode) }
        emitAppliedStatus(currentAppliedStatusText().themeApplied)
        savePreference(SettingsPreferenceKey.ThemeMode, mode)
    }

    fun applyAccentMode(nextAccent: String) {
        val accent = EchoTheme.normalizeAccent(nextAccent)
        EchoTheme.setAccent(accent)
        updatePreferences { it.copy(accentMode = accent) }
        emitAppliedStatus(currentAppliedStatusText().accentApplied)
        savePreference(SettingsPreferenceKey.AccentMode, accent)
    }

    fun applyLanguageMode(nextLanguageMode: String) {
        val languageMode = AppLanguage.normalizeMode(nextLanguageMode)
        updatePreferences { it.copy(languageMode = languageMode) }
        emitAppliedStatus(currentAppliedStatusText().languageApplied)
        savePreference(SettingsPreferenceKey.LanguageMode, languageMode)
    }

    fun applyPlaybackSpeed(speed: Float) {
        val normalizedSpeed = normalizePlaybackSpeed(speed)
        applyRuntimeEffect(SettingsRuntimeEffect.ApplyPlaybackSpeed(normalizedSpeed))
        updatePreferences { it.copy(playbackSpeed = normalizedSpeed) }
        emitAppliedStatus(currentAppliedStatusText().playbackSpeedApplied)
        savePreference(SettingsPreferenceKey.PlaybackSpeed, normalizedSpeed)
    }

    fun applyAppVolume(volume: Float) {
        val normalizedVolume = normalizeAppVolume(volume)
        applyRuntimeEffect(SettingsRuntimeEffect.ApplyAppVolume(normalizedVolume))
        updatePreferences { it.copy(appVolume = normalizedVolume) }
        emitAppliedStatus(currentAppliedStatusText().appVolumeApplied)
        savePreference(SettingsPreferenceKey.AppVolume, normalizedVolume)
    }

    fun applyStreamingAudioQuality(quality: String) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        updatePreferences { it.copy(streamingAudioQuality = normalizedQuality) }
        emitAppliedStatus(streamingQualityAppliedStatus(normalizedQuality))
        savePreference(SettingsPreferenceKey.StreamingAudioQuality, normalizedQuality)
    }

    fun applyShareStyle(style: String) {
        val normalizedStyle = TrackShareStyle.normalize(style)
        updatePreferences { it.copy(shareStyle = normalizedStyle) }
        emitAppliedStatus(shareStyleAppliedStatus(normalizedStyle))
        savePreference(SettingsPreferenceKey.ShareStyle, normalizedStyle)
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        applyRuntimeEffect(SettingsRuntimeEffect.SetOnlineLyricsEnabled(enabled))
        updateRuntime { it.copy(onlineLyricsEnabled = enabled) }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(if (enabled) statusText.onlineLyricsEnabled else statusText.onlineLyricsDisabled)
        emitEffect(SettingsEffect.ReloadCurrentLyrics)
        savePreference(SettingsPreferenceKey.OnlineLyricsEnabled, enabled)
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        val statusText = currentAppliedStatusText()
        updateConcurrentPlaybackEnabled(
            enabled,
            if (enabled) statusText.concurrentPlaybackEnabled else statusText.concurrentPlaybackDisabled
        )
    }

    /**
     * User-facing audio exclusivity maps to the existing persisted mixing flag. Keeping one
     * source of truth avoids competing audio-focus requests while remaining compatible with
     * existing preferences: exclusive on means concurrent playback off.
     */
    fun setAudioExclusiveEnabled(enabled: Boolean) {
        val languageMode = _state.value.preferences.languageMode
        updateConcurrentPlaybackEnabled(
            !enabled,
            AppLanguage.text(
                languageMode,
                if (enabled) "audio.exclusive.enabled" else "audio.exclusive.disabled"
            )
        )
    }

    private fun updateConcurrentPlaybackEnabled(enabled: Boolean, status: String) {
        applyRuntimeEffect(SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(enabled))
        updatePreferences { it.copy(concurrentPlaybackEnabled = enabled) }
        emitAppliedStatus(status)
        savePreference(SettingsPreferenceKey.ConcurrentPlaybackEnabled, enabled)
    }

    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        applyRuntimeEffect(SettingsRuntimeEffect.ApplyAudioEffects(settings))
        updatePreferences { it.copy(audioEffectSettings = settings) }
        emitAppliedStatus(currentAppliedStatusText().audioEffectsApplied)
        savePreference(SettingsPreferenceKey.AudioEffectSettings, settings)
    }

    fun setStatusBarLyricsEnabled(enabled: Boolean) {
        val currentPreferences = _state.value.preferences
        val disableFloatingLyrics = enabled && currentPreferences.floatingLyricsEnabled
        if (disableFloatingLyrics) {
            applyRuntimeEffect(SettingsRuntimeEffect.ApplyFloatingLyrics(false))
        }
        applyRuntimeEffect(SettingsRuntimeEffect.SetStatusBarLyrics(enabled))
        updatePreferences {
            it.copy(
                statusBarLyricsEnabled = enabled,
                floatingLyricsEnabled = if (disableFloatingLyrics) false else it.floatingLyricsEnabled
            )
        }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(if (enabled) statusText.statusBarLyricsEnabled else statusText.statusBarLyricsDisabled)
        if (disableFloatingLyrics) {
            savePreference(SettingsPreferenceKey.FloatingLyricsEnabled, false)
        }
        savePreference(SettingsPreferenceKey.StatusBarLyricsEnabled, enabled)
    }

    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) {
        applyRuntimeEffect(SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled(enabled))
        updatePreferences { it.copy(systemMediaLyricsTitleEnabled = enabled) }
        val languageMode = _state.value.preferences.languageMode
        emitAppliedStatus(
            AppLanguage.text(
                languageMode,
                if (enabled) "system.media.lyrics.title.enabled" else "system.media.lyrics.title.disabled"
            )
        )
        savePreference(SettingsPreferenceKey.SystemMediaLyricsTitleEnabled, enabled)
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        val applied = applyRuntimeEffect(SettingsRuntimeEffect.ApplyFloatingLyrics(enabled))
        val disableStatusBarLyrics = enabled && applied && _state.value.preferences.statusBarLyricsEnabled
        if (disableStatusBarLyrics) {
            applyRuntimeEffect(SettingsRuntimeEffect.SetStatusBarLyrics(false))
        }
        updatePreferences {
            it.copy(
                statusBarLyricsEnabled = if (disableStatusBarLyrics) false else it.statusBarLyricsEnabled,
                floatingLyricsEnabled = enabled
            )
        }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(
            if (!applied && enabled) {
                statusText.floatingLyricsPermissionRequired
            } else if (enabled) {
                statusText.floatingLyricsEnabled
            } else {
                statusText.floatingLyricsDisabled
            }
        )
        if (disableStatusBarLyrics) {
            savePreference(SettingsPreferenceKey.StatusBarLyricsEnabled, false)
        }
        savePreference(SettingsPreferenceKey.FloatingLyricsEnabled, enabled)
    }

    fun openFloatingLyricsPermission() {
        applyRuntimeEffect(SettingsRuntimeEffect.OpenFloatingLyricsPermissionSettings)
        emitAppliedStatus(currentAppliedStatusText().floatingLyricsPermissionRequired)
    }

    fun setNowPlayingGesturesEnabled(enabled: Boolean) {
        updatePreferences { it.copy(nowPlayingGesturesEnabled = enabled) }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(if (enabled) statusText.nowPlayingGesturesEnabled else statusText.nowPlayingGesturesDisabled)
        savePreference(SettingsPreferenceKey.NowPlayingGesturesEnabled, enabled)
    }

    fun setPlaybackRestoreEnabled(enabled: Boolean) {
        applyRuntimeEffect(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(enabled))
        updatePreferences { it.copy(playbackRestoreEnabled = enabled) }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(if (enabled) statusText.playbackRestoreEnabled else statusText.playbackRestoreDisabled)
        savePreference(SettingsPreferenceKey.PlaybackRestoreEnabled, enabled)
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        applyRuntimeEffect(SettingsRuntimeEffect.SetReplayGainEnabled(enabled))
        updatePreferences { it.copy(replayGainEnabled = enabled) }
        val statusText = currentAppliedStatusText()
        emitAppliedStatus(if (enabled) statusText.replayGainEnabled else statusText.replayGainDisabled)
        savePreference(SettingsPreferenceKey.ReplayGainEnabled, enabled)
    }

    fun setDebugPromptsEnabled(enabled: Boolean) {
        updatePreferences { it.copy(debugPromptsEnabled = enabled) }
        val languageMode = _state.value.preferences.languageMode
        emitAppliedStatus(
            AppLanguage.text(
                languageMode,
                if (enabled) "debug.prompts.enabled" else "debug.prompts.disabled"
            )
        )
        savePreference(SettingsPreferenceKey.DebugPromptsEnabled, enabled)
    }

    fun applyPageBackgrounds(backgrounds: PageBackgrounds, page: String, cleared: Boolean) {
        updatePreferences { it.copy(pageBackgrounds = backgrounds) }
        emitAppliedStatus(pageBackgroundAppliedStatus(page, cleared))
        savePreference(SettingsPreferenceKey.PageBackgrounds, backgrounds)
    }

    fun clearPageBackground(page: String) {
        val target = PageBackgrounds.normalizePage(page)
        if (target.isEmpty()) {
            return
        }
        val current = _state.value
        val backgrounds = current.preferences.pageBackgrounds.clear(target)
        applyPageBackgrounds(backgrounds, target, true)
    }

    fun applyLyricsOffset(offsetMs: Long) {
        val normalizedOffsetMs = normalizeLyricsOffsetMs(offsetMs)
        applyRuntimeEffect(SettingsRuntimeEffect.SetLyricsOffsetMs(normalizedOffsetMs))
        updateRuntime { it.copy(lyricsOffsetMs = normalizedOffsetMs) }
        emitAppliedStatus(currentAppliedStatusText(normalizedOffsetMs).lyricsOffsetApplied)
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
                    SettingsLabelFormatter.playbackSpeedLabel(playbackSpeed),
            appVolumeApplied = AppLanguage.text(normalizedLanguageMode, "volume.applied") +
                    SettingsLabelFormatter.appVolumeLabel(appVolume),
            onlineLyricsEnabled = AppLanguage.text(normalizedLanguageMode, "online.lyrics.enabled"),
            onlineLyricsDisabled = AppLanguage.text(normalizedLanguageMode, "online.lyrics.disabled"),
            concurrentPlaybackEnabled = AppLanguage.text(normalizedLanguageMode, "concurrent.playback.enabled"),
            concurrentPlaybackDisabled = AppLanguage.text(normalizedLanguageMode, "concurrent.playback.disabled"),
            lyricsOffsetApplied = AppLanguage.text(normalizedLanguageMode, "lyrics.offset.applied") +
                    SettingsLabelFormatter.lyricsOffsetLabel(lyricsOffsetMs),
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
            shareStyleApplied = AppLanguage.text(normalizedLanguageMode, "share.style.applied"),
            pageBackgroundApplied = AppLanguage.text(normalizedLanguageMode, "page.background.applied"),
            pageBackgroundCleared = AppLanguage.text(normalizedLanguageMode, "page.background.cleared")
        )
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
