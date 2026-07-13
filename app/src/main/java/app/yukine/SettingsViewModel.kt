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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val refuseAutomaticQualityDowngrade: Boolean = false,
    val concurrentPlaybackEnabled: Boolean = false,
    val audioEffectSettings: AudioEffectSettings = AudioEffectSettings.DEFAULT,
    val statusBarLyricsEnabled: Boolean = true,
    val systemMediaLyricsTitleEnabled: Boolean = false,
    val floatingLyricsEnabled: Boolean = false,
    val nowPlayingGesturesEnabled: Boolean = true,
    val playbackRestoreEnabled: Boolean = true,
    val replayGainEnabled: Boolean = true,
    val debugPromptsEnabled: Boolean = false,
    val customBackgroundBlurEnabled: Boolean = false,
    val customBackgroundBlurRadiusDp: Float = app.yukine.ui.EchoBackgroundBlurDefaults.DEFAULT_RADIUS_DP,
    val glassBlurEnabled: Boolean = false,
    val glassBlurRadiusDp: Float = app.yukine.ui.EchoGlassDefaults.BLUR_RADIUS_DP,
    val glassSurfaceOpacity: Float = app.yukine.ui.EchoGlassDefaults.SURFACE_OPACITY,
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
    val luoxueImportedSourceCount: Int = 0,
    val luoxueEnabledSourceCount: Int = 0,
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
    data class OpenNetworkPage(val page: String) : SettingsEffect
    data object OpenDownloads : SettingsEffect
    data object RequestNeededPermissions : SettingsEffect
    data object LoadLibrary : SettingsEffect
    data object OpenAudioFilePicker : SettingsEffect
    data object OpenAudioFolderPicker : SettingsEffect
    data object OpenLuoxueSourceManager : SettingsEffect
    data object ImportLuoxueSource : SettingsEffect
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
    data class OpenNetworkPage(val page: String) : SettingsEvent
    data object OpenDownloads : SettingsEvent
    data object RequestNeededPermissions : SettingsEvent
    data object LoadLibrary : SettingsEvent
    data object OpenAudioFilePicker : SettingsEvent
    data object OpenAudioFolderPicker : SettingsEvent
    data object OpenLuoxueSourceManager : SettingsEvent
    data object ImportLuoxueSource : SettingsEvent
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
    data class SetRefuseAutomaticQualityDowngrade(val refuse: Boolean) : SettingsEvent
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
    data class SetCustomBackgroundBlurEnabled(val enabled: Boolean) : SettingsEvent
    data class SetCustomBackgroundBlurRadiusDp(val radiusDp: Float) : SettingsEvent
    data class SetGlassBlurEnabled(val enabled: Boolean) : SettingsEvent
    data class SetGlassBlurRadiusDp(val radiusDp: Float) : SettingsEvent
    data class SetGlassSurfaceOpacity(val opacity: Float) : SettingsEvent
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
    fun save(update: SettingsPreferenceUpdate): Boolean
}

data class SettingsContextSnapshot(
    val preferences: SettingsPreferencesSnapshot,
    val runtime: RuntimeSettingsStatus
)

fun interface SettingsContextLoader {
    fun load(): SettingsContextSnapshot
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
    private var contextLoader: SettingsContextLoader? = null
    private var contextLoadJob: Job? = null
    private var nextContextLoadId = 0L
    private val preferenceWriteMutex = Mutex()
    /**
     * Last values confirmed by the persistence gateway. UI state is optimistic so controls remain
     * responsive, but a failed write must not leave the in-memory mirrors claiming a value that
     * will disappear after the next process restart.
     */
    private var persistedValues: Map<SettingsPreferenceKey, Any> = emptyMap()
    private var persistenceBaselineReady = false

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

    fun bindContextLoader(nextLoader: SettingsContextLoader?) {
        contextLoader = nextLoader
    }

    fun refreshSettingsContext() {
        val loader = contextLoader ?: return
        val loadId = ++nextContextLoadId
        contextLoadJob?.cancel()
        contextLoadJob = viewModelScope.launch {
            try {
                val snapshot = runInterruptible(ioDispatcher) { loader.load() }
                if (loadId == nextContextLoadId) {
                    val renderedPage = _state.value.page
                    val refreshRenderedPage = _state.value.ui != SettingsUiState()
                    updateSettingsContext(snapshot.preferences, snapshot.runtime)
                    if (refreshRenderedPage) {
                        renderCurrentPage(renderedPage, snapshot.preferences, snapshot.runtime)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last valid snapshot; a later render retries the refresh.
            } finally {
                if (loadId == nextContextLoadId) {
                    contextLoadJob = null
                }
            }
        }
    }

    fun scrollToTopOnNextRender() {
        scrollState.scrollToTop()
    }

    fun updateSettingsContext(
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ) {
        persistedValues = persistentValues(preferences, runtime)
        persistenceBaselineReady = true
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
        if (!persistenceBaselineReady) {
            persistedValues = persistentValues(preferences, runtime)
            persistenceBaselineReady = true
        }
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
            is SettingsEvent.OpenNetworkPage -> emitEffect(SettingsEffect.OpenNetworkPage(event.page))
            SettingsEvent.OpenDownloads -> emitEffect(SettingsEffect.OpenDownloads)
            SettingsEvent.RequestNeededPermissions -> emitEffect(SettingsEffect.RequestNeededPermissions)
            SettingsEvent.LoadLibrary -> emitEffect(SettingsEffect.LoadLibrary)
            SettingsEvent.OpenAudioFilePicker -> emitEffect(SettingsEffect.OpenAudioFilePicker)
            SettingsEvent.OpenAudioFolderPicker -> emitEffect(SettingsEffect.OpenAudioFolderPicker)
            SettingsEvent.OpenLuoxueSourceManager -> emitEffect(SettingsEffect.OpenLuoxueSourceManager)
            SettingsEvent.ImportLuoxueSource -> emitEffect(SettingsEffect.ImportLuoxueSource)
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
            is SettingsEvent.SetRefuseAutomaticQualityDowngrade ->
                setRefuseAutomaticQualityDowngrade(event.refuse)
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
            is SettingsEvent.SetCustomBackgroundBlurEnabled ->
                setCustomBackgroundBlurEnabled(event.enabled)
            is SettingsEvent.SetCustomBackgroundBlurRadiusDp ->
                setCustomBackgroundBlurRadiusDp(event.radiusDp)
            is SettingsEvent.SetGlassBlurEnabled -> setGlassBlurEnabled(event.enabled)
            is SettingsEvent.SetGlassBlurRadiusDp -> setGlassBlurRadiusDp(event.radiusDp)
            is SettingsEvent.SetGlassSurfaceOpacity -> setGlassSurfaceOpacity(event.opacity)
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
                    preferences.customBackgroundBlurEnabled,
                    preferences.customBackgroundBlurRadiusDp,
                    preferences.glassBlurEnabled,
                    preferences.glassBlurRadiusDp,
                    preferences.glassSurfaceOpacity,
                    onCustomBackgroundBlurEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetCustomBackgroundBlurEnabled(enabled))
                    },
                    onCustomBackgroundBlurRadiusChange = { radiusDp ->
                        onEvent(SettingsEvent.SetCustomBackgroundBlurRadiusDp(radiusDp))
                    },
                    onGlassBlurEnabledChange = { enabled ->
                        onEvent(SettingsEvent.SetGlassBlurEnabled(enabled))
                    },
                    onGlassBlurRadiusChange = { radiusDp ->
                        onEvent(SettingsEvent.SetGlassBlurRadiusDp(radiusDp))
                    },
                    onGlassSurfaceOpacityChange = { opacity ->
                        onEvent(SettingsEvent.SetGlassSurfaceOpacity(opacity))
                    },
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
                    runtime.luoxueImportedSourceCount,
                    runtime.luoxueEnabledSourceCount,
                    onNavigate = ::navigateSettingsPage,
                    onOpenNetworkPage = { page -> onEvent(SettingsEvent.OpenNetworkPage(page)) },
                    onManageLuoxueSources = { onEvent(SettingsEvent.OpenLuoxueSourceManager) },
                    onImportLuoxueSource = { onEvent(SettingsEvent.ImportLuoxueSource) }
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
                    preferences.pageBackgrounds,
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
                    preferences.refuseAutomaticQualityDowngrade,
                    onNavigate = ::navigateSettingsPage,
                    onApplyQuality = { quality -> onEvent(SettingsEvent.ApplyStreamingAudioQuality(quality)) },
                    onRefuseAutomaticQualityDowngradeChange = { refuse ->
                        onEvent(SettingsEvent.SetRefuseAutomaticQualityDowngrade(refuse))
                    }
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
        val listener = effectListener
        // The Activity listener is the production delivery path. Keep the pull queue only for
        // callers/tests that have not installed a listener yet; otherwise every effect would be
        // delivered twice and the queue would grow for the entire ViewModel lifetime.
        if (listener == null) {
            pendingEffects.add(effect)
        } else {
            listener.onEffect(effect)
        }
    }

    private fun applyRuntimeEffect(effect: SettingsRuntimeEffect): Boolean {
        val listener = runtimeEffectListener ?: return true
        val applied = listener.onRuntimeEffect(effect)
        if (!applied) {
            emitEffect(SettingsEffect.ShowStatus(runtimeUnavailableStatus()))
        }
        return applied
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
            nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled,
            customBackgroundBlurEnabled = preferences.customBackgroundBlurEnabled,
            customBackgroundBlurRadiusDp = preferences.customBackgroundBlurRadiusDp,
            glassBlurEnabled = preferences.glassBlurEnabled,
            glassBlurRadiusDp = preferences.glassBlurRadiusDp,
            glassSurfaceOpacity = preferences.glassSurfaceOpacity
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
        if (accent == EchoTheme.ACCENT_DYNAMIC_BACKGROUND) {
            applyRuntimeEffect(SettingsRuntimeEffect.RefreshCustomBackgroundAccent(_state.value.preferences.pageBackgrounds))
        }
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

    fun setRefuseAutomaticQualityDowngrade(refuse: Boolean) {
        updatePreferences { it.copy(refuseAutomaticQualityDowngrade = refuse) }
        emitAppliedStatus(
            AppLanguage.text(
                _state.value.preferences.languageMode,
                if (refuse) "quality.downgrade.refused" else "quality.downgrade.allowed"
            )
        )
        savePreference(SettingsPreferenceKey.RefuseAutomaticQualityDowngrade, refuse)
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
        if (enabled && !applied) {
            emitAppliedStatus(currentAppliedStatusText().floatingLyricsPermissionRequired)
            return
        }
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
            if (enabled) {
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

    fun setGlassBlurEnabled(enabled: Boolean) {
        updatePreferences { it.copy(glassBlurEnabled = enabled) }
        savePreference(SettingsPreferenceKey.GlassBlurEnabled, enabled)
    }

    fun setCustomBackgroundBlurEnabled(enabled: Boolean) {
        updatePreferences { it.copy(customBackgroundBlurEnabled = enabled) }
        savePreference(SettingsPreferenceKey.CustomBackgroundBlurEnabled, enabled)
    }

    fun setCustomBackgroundBlurRadiusDp(radiusDp: Float) {
        val normalized = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(radiusDp)
        updatePreferences { it.copy(customBackgroundBlurRadiusDp = normalized) }
        savePreference(SettingsPreferenceKey.CustomBackgroundBlurRadiusDp, normalized)
    }

    fun setGlassBlurRadiusDp(radiusDp: Float) {
        val normalized = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(radiusDp)
        updatePreferences { it.copy(glassBlurRadiusDp = normalized) }
        savePreference(SettingsPreferenceKey.GlassBlurRadiusDp, normalized)
    }

    fun setGlassSurfaceOpacity(opacity: Float) {
        val normalized = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(opacity)
        updatePreferences { it.copy(glassSurfaceOpacity = normalized) }
        savePreference(SettingsPreferenceKey.GlassSurfaceOpacity, normalized)
    }

    fun applyPageBackgrounds(backgrounds: PageBackgrounds, page: String, cleared: Boolean) {
        updatePreferences { it.copy(pageBackgrounds = backgrounds) }
        if (_state.value.preferences.accentMode == EchoTheme.ACCENT_DYNAMIC_BACKGROUND) {
            applyRuntimeEffect(SettingsRuntimeEffect.RefreshCustomBackgroundAccent(backgrounds))
        }
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
            val result = withContext(ioDispatcher) {
                preferenceWriteMutex.withLock {
                    runCatching {
                        check(currentGateway.save(SettingsPreferenceUpdate(key, value)))
                    }
                }
            }
            result.onSuccess {
                persistedValues = persistedValues + (key to value)
            }.onFailure {
                if (persistenceBaselineReady && currentPersistedValue(key) == value) {
                    persistedValues[key]?.let { restorePersistedValue(key, it) }
                }
                emitEffect(SettingsEffect.ShowStatus(preferenceSaveFailedStatus()))
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
        SettingsPreferenceKey.ConcurrentPlaybackEnabled to preferences.concurrentPlaybackEnabled,
        SettingsPreferenceKey.LyricsOffsetMs to runtime.lyricsOffsetMs,
        SettingsPreferenceKey.AudioEffectSettings to preferences.audioEffectSettings,
        SettingsPreferenceKey.StatusBarLyricsEnabled to preferences.statusBarLyricsEnabled,
        SettingsPreferenceKey.SystemMediaLyricsTitleEnabled to preferences.systemMediaLyricsTitleEnabled,
        SettingsPreferenceKey.FloatingLyricsEnabled to preferences.floatingLyricsEnabled,
        SettingsPreferenceKey.NowPlayingGesturesEnabled to preferences.nowPlayingGesturesEnabled,
        SettingsPreferenceKey.PlaybackRestoreEnabled to preferences.playbackRestoreEnabled,
        SettingsPreferenceKey.ReplayGainEnabled to preferences.replayGainEnabled,
        SettingsPreferenceKey.DebugPromptsEnabled to preferences.debugPromptsEnabled,
        SettingsPreferenceKey.CustomBackgroundBlurEnabled to preferences.customBackgroundBlurEnabled,
        SettingsPreferenceKey.CustomBackgroundBlurRadiusDp to preferences.customBackgroundBlurRadiusDp,
        SettingsPreferenceKey.GlassBlurEnabled to preferences.glassBlurEnabled,
        SettingsPreferenceKey.GlassBlurRadiusDp to preferences.glassBlurRadiusDp,
        SettingsPreferenceKey.GlassSurfaceOpacity to preferences.glassSurfaceOpacity,
        SettingsPreferenceKey.ShareStyle to preferences.shareStyle,
        SettingsPreferenceKey.PageBackgrounds to preferences.pageBackgrounds
    )

    private fun currentPersistedValue(key: SettingsPreferenceKey): Any = when (key) {
        SettingsPreferenceKey.ThemeMode -> _state.value.preferences.themeMode
        SettingsPreferenceKey.AccentMode -> _state.value.preferences.accentMode
        SettingsPreferenceKey.LanguageMode -> _state.value.preferences.languageMode
        SettingsPreferenceKey.PlaybackSpeed -> _state.value.preferences.playbackSpeed
        SettingsPreferenceKey.AppVolume -> _state.value.preferences.appVolume
        SettingsPreferenceKey.StreamingAudioQuality -> _state.value.preferences.streamingAudioQuality
        SettingsPreferenceKey.RefuseAutomaticQualityDowngrade ->
            _state.value.preferences.refuseAutomaticQualityDowngrade
        SettingsPreferenceKey.OnlineLyricsEnabled -> _state.value.runtime.onlineLyricsEnabled
        SettingsPreferenceKey.ConcurrentPlaybackEnabled -> _state.value.preferences.concurrentPlaybackEnabled
        SettingsPreferenceKey.LyricsOffsetMs -> _state.value.runtime.lyricsOffsetMs
        SettingsPreferenceKey.AudioEffectSettings -> _state.value.preferences.audioEffectSettings
        SettingsPreferenceKey.StatusBarLyricsEnabled -> _state.value.preferences.statusBarLyricsEnabled
        SettingsPreferenceKey.SystemMediaLyricsTitleEnabled -> _state.value.preferences.systemMediaLyricsTitleEnabled
        SettingsPreferenceKey.FloatingLyricsEnabled -> _state.value.preferences.floatingLyricsEnabled
        SettingsPreferenceKey.NowPlayingGesturesEnabled -> _state.value.preferences.nowPlayingGesturesEnabled
        SettingsPreferenceKey.PlaybackRestoreEnabled -> _state.value.preferences.playbackRestoreEnabled
        SettingsPreferenceKey.ReplayGainEnabled -> _state.value.preferences.replayGainEnabled
        SettingsPreferenceKey.DebugPromptsEnabled -> _state.value.preferences.debugPromptsEnabled
        SettingsPreferenceKey.CustomBackgroundBlurEnabled -> _state.value.preferences.customBackgroundBlurEnabled
        SettingsPreferenceKey.CustomBackgroundBlurRadiusDp -> _state.value.preferences.customBackgroundBlurRadiusDp
        SettingsPreferenceKey.GlassBlurEnabled -> _state.value.preferences.glassBlurEnabled
        SettingsPreferenceKey.GlassBlurRadiusDp -> _state.value.preferences.glassBlurRadiusDp
        SettingsPreferenceKey.GlassSurfaceOpacity -> _state.value.preferences.glassSurfaceOpacity
        SettingsPreferenceKey.ShareStyle -> _state.value.preferences.shareStyle
        SettingsPreferenceKey.PageBackgrounds -> _state.value.preferences.pageBackgrounds
    }

    private fun restorePersistedValue(key: SettingsPreferenceKey, value: Any) {
        val current = _state.value
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
            SettingsPreferenceKey.StreamingAudioQuality ->
                preferences = preferences.copy(streamingAudioQuality = value as String)
            SettingsPreferenceKey.RefuseAutomaticQualityDowngrade ->
                preferences = preferences.copy(refuseAutomaticQualityDowngrade = value as Boolean)
            SettingsPreferenceKey.OnlineLyricsEnabled ->
                runtime = runtime.copy(onlineLyricsEnabled = value as Boolean)
            SettingsPreferenceKey.ConcurrentPlaybackEnabled ->
                preferences = preferences.copy(concurrentPlaybackEnabled = value as Boolean)
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
            SettingsPreferenceKey.DebugPromptsEnabled ->
                preferences = preferences.copy(debugPromptsEnabled = value as Boolean)
            SettingsPreferenceKey.CustomBackgroundBlurEnabled ->
                preferences = preferences.copy(customBackgroundBlurEnabled = value as Boolean)
            SettingsPreferenceKey.CustomBackgroundBlurRadiusDp ->
                preferences = preferences.copy(customBackgroundBlurRadiusDp = value as Float)
            SettingsPreferenceKey.GlassBlurEnabled ->
                preferences = preferences.copy(glassBlurEnabled = value as Boolean)
            SettingsPreferenceKey.GlassBlurRadiusDp ->
                preferences = preferences.copy(glassBlurRadiusDp = value as Float)
            SettingsPreferenceKey.GlassSurfaceOpacity ->
                preferences = preferences.copy(glassSurfaceOpacity = value as Float)
            SettingsPreferenceKey.ShareStyle -> preferences = preferences.copy(shareStyle = value as String)
            SettingsPreferenceKey.PageBackgrounds ->
                preferences = preferences.copy(pageBackgrounds = value as PageBackgrounds)
        }
        storeMirror?.sync(preferences)
        renderCurrentPage(current.page, preferences, runtime)
        when (key) {
            SettingsPreferenceKey.ThemeMode -> applyRuntimeEffect(SettingsRuntimeEffect.ApplyThemeSurface)
            SettingsPreferenceKey.PlaybackSpeed ->
                applyRuntimeEffect(SettingsRuntimeEffect.ApplyPlaybackSpeed(preferences.playbackSpeed))
            SettingsPreferenceKey.AppVolume ->
                applyRuntimeEffect(SettingsRuntimeEffect.ApplyAppVolume(preferences.appVolume))
            SettingsPreferenceKey.RefuseAutomaticQualityDowngrade,
            SettingsPreferenceKey.StreamingAudioQuality -> Unit
            SettingsPreferenceKey.OnlineLyricsEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetOnlineLyricsEnabled(runtime.onlineLyricsEnabled))
            SettingsPreferenceKey.ConcurrentPlaybackEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetConcurrentPlaybackEnabled(preferences.concurrentPlaybackEnabled))
            SettingsPreferenceKey.AudioEffectSettings ->
                applyRuntimeEffect(SettingsRuntimeEffect.ApplyAudioEffects(preferences.audioEffectSettings))
            SettingsPreferenceKey.StatusBarLyricsEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetStatusBarLyrics(preferences.statusBarLyricsEnabled))
            SettingsPreferenceKey.SystemMediaLyricsTitleEnabled ->
                applyRuntimeEffect(
                    SettingsRuntimeEffect.SetSystemMediaLyricsTitleEnabled(
                        preferences.systemMediaLyricsTitleEnabled
                    )
                )
            SettingsPreferenceKey.FloatingLyricsEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.ApplyFloatingLyrics(preferences.floatingLyricsEnabled))
            SettingsPreferenceKey.PlaybackRestoreEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetPlaybackRestoreEnabled(preferences.playbackRestoreEnabled))
            SettingsPreferenceKey.ReplayGainEnabled ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetReplayGainEnabled(preferences.replayGainEnabled))
            SettingsPreferenceKey.LyricsOffsetMs ->
                applyRuntimeEffect(SettingsRuntimeEffect.SetLyricsOffsetMs(runtime.lyricsOffsetMs))
            else -> Unit
        }
    }

    private fun runtimeUnavailableStatus(): String {
        return if (_state.value.preferences.languageMode == AppLanguage.MODE_ENGLISH) {
            "Runtime playback is not connected; the setting will apply when playback reconnects."
        } else {
            "播放服务当前未连接，设置将在重新连接后生效。"
        }
    }

    private fun preferenceSaveFailedStatus(): String {
        return if (_state.value.preferences.languageMode == AppLanguage.MODE_ENGLISH) {
            "Failed to save the setting. Please try again."
        } else {
            "设置保存失败，请重试。"
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
