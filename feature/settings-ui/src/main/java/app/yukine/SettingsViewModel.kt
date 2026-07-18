package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.SettingsAction
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardLayout
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsMetric
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

sealed interface SettingsItem {
    data class Navigation(val label: String, val description: String = "") : SettingsItem
    data class Action(val label: String, val description: String = "") : SettingsItem
    data class Metric(val label: String, val value: String) : SettingsItem
}

data class SettingsUiState(
    val title: String = "",
    val metrics: List<SettingsMetric> = emptyList(),
    val items: List<SettingsItem> = emptyList(),
    val issues: List<SettingsIssue> = emptyList(),
    val issuesTitle: String = "",
    val searchEntries: List<SettingsSearchEntry> = emptyList(),
    val searchPlaceholder: String = "",
    val searchResultsTitle: String = "",
    val searchEmptyMessage: String = ""
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
    val compactSettingsCards: Boolean = false,
    val homeDashboardLayout: HomeDashboardLayout = HomeDashboardLayout.Classic,
    val shareStyle: String = TrackShareStyle.defaultValue(),
    val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty()
)

data class RuntimeSettingsStatus(
    val appVersionName: String = "",
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
    val streamingGatewayEndpoint: String = "gateway://unconfigured",
    val streamingGatewayConfigured: Boolean = false,
    val luoxueImportedSourceCount: Int = 0,
    val luoxueEnabledSourceCount: Int = 0,
    val identityBackfill: IdentityBackfillStatusUi = IdentityBackfillStatusUi(),
    val hiddenLibraryItems: List<HiddenLibraryItemUi> = emptyList()
)

data class IdentityBackfillStatusUi(
    val total: Int = 0,
    val processed: Int = 0,
    val merged: Int = 0,
    val pending: Int = 0,
    val lxMigrated: Int = 0,
    val lxDeleted: Int = 0
) {
    val running: Boolean get() = total > 0 && processed < total
}

data class HiddenLibraryItemUi(val sourceKey: String, val label: String)

/** Narrow route projection supplied by the navigation feature. */
data class SettingsRouteState(
    val active: Boolean = false,
    val page: SettingsPage = SettingsPage.Home
)

data class SettingsState(
    val page: SettingsPage = SettingsPage.Home,
    val preferences: SettingsPreferencesSnapshot = SettingsPreferencesSnapshot(),
    val runtime: RuntimeSettingsStatus = RuntimeSettingsStatus(),
    val actions: List<SettingsAction> = emptyList(),
    val ui: SettingsUiState = SettingsUiState(),
    val highlightedEntryId: SettingsEntryId? = null
) : SettingsDestinationState {
    override val destinationTitle: String
        get() = ui.title
    override val destinationMetrics: List<SettingsMetric>
        get() = ui.metrics
    override val destinationActions: List<SettingsAction>
        get() = actions
    override val destinationIssues: List<SettingsIssue>
        get() = ui.issues
    override val destinationIssuesTitle: String
        get() = ui.issuesTitle
    override val destinationSearchEntries: List<SettingsSearchEntry>
        get() = ui.searchEntries
    override val destinationSearchPlaceholder: String
        get() = ui.searchPlaceholder
    override val destinationSearchResultsTitle: String
        get() = ui.searchResultsTitle
    override val destinationSearchEmptyMessage: String
        get() = ui.searchEmptyMessage
    override val destinationHighlightedEntryId: SettingsEntryId?
        get() = highlightedEntryId
    override val destinationCompactSettingsCards: Boolean
        get() = preferences.compactSettingsCards
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
    val languageMode: StateFlow<String> = state
        .map { it.preferences.languageMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.preferences.languageMode)
    private val _chromeState = MutableStateFlow(SettingsChromeState())
    val chromeState: StateFlow<SettingsChromeState> = _chromeState.asStateFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val mutations = SettingsMutationContext(
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        currentState = { _state.value },
        updatePreferencesState = ::updatePreferences,
        updateRuntimeState = ::updateRuntime,
        replaceSnapshot = ::replaceSnapshot
    )
    val appearance = AppearanceSettingsStateOwner(mutations)
    val playback = PlaybackSettingsStateOwner(mutations)
    val lyrics = LyricsSettingsStateOwner(mutations)
    val library = LibrarySettingsStateOwner(mutations)
    val network = NetworkSettingsStateOwner(mutations)
    val platform = PlatformSettingsStateOwner(mutations)
    @JvmName("lyricsOwner")
    fun lyricsOwner(): LyricsSettingsStateOwner = lyrics
    private var contextLoader: SettingsContextLoader? = null
    private var contextLoadJob: Job? = null
    private var routeStateJob: Job? = null
    private var nextContextLoadId = 0L

    fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?) {
        mutations.bindPreferenceGateway(nextGateway)
    }

    fun bindStoreMirror(nextMirror: SettingsStoreMirror?) {
        mutations.bindStoreMirror(nextMirror)
    }

    fun bindEffectListener(nextListener: SettingsEffectListener?) {
        mutations.bindEffectListener(nextListener)
    }

    fun bindRuntimeEffectListener(nextListener: SettingsRuntimeEffectListener?) {
        mutations.bindRuntimeEffectListener(nextListener)
    }

    fun bindContextLoader(nextLoader: SettingsContextLoader?) {
        contextLoader = nextLoader
    }

    fun bindRouteState(nextState: StateFlow<SettingsRouteState>?) {
        routeStateJob?.cancel()
        routeStateJob = nextState?.let { routeState ->
            viewModelScope.launch {
                routeState
                    .map { it.active to it.page }
                    .distinctUntilChanged()
                    .collect { (active, page) ->
                        val current = _state.value
                        publishCurrentPage(
                            page,
                            current.preferences,
                            current.runtime,
                            current.highlightedEntryId.takeIf { current.page == page }
                        )
                        if (active) {
                            refreshSettingsContext()
                        }
                    }
            }
        }
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
                        publishCurrentPage(renderedPage, snapshot.preferences, snapshot.runtime)
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
        mutations.establishBaseline(preferences, runtime)
        mutations.syncStore(preferences)
        _state.value = _state.value.copy(
            preferences = preferences,
            runtime = runtime
        )
        syncChromeState(preferences)
    }

    fun publishCurrentPage(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus,
        highlightedEntryId: SettingsEntryId? = null
    ): SettingsPageStateContent {
        mutations.ensureBaseline(preferences, runtime)
        val content = buildPageContent(page, preferences, runtime)
        _state.value = _state.value.copy(
            page = page,
            preferences = preferences,
            runtime = runtime,
            actions = content.actions,
            ui = content.uiState,
            highlightedEntryId = highlightedEntryId
        )
        syncChromeState(preferences)
        _uiState.value = content.uiState
        return content
    }

    fun publishCurrentPage(): SettingsPageStateContent {
        val current = _state.value
        val content = buildPageContent(current.page, current.preferences, current.runtime)
        _state.value = current.copy(
            actions = content.actions,
            ui = content.uiState,
            highlightedEntryId = current.highlightedEntryId
        )
        _uiState.value = content.uiState
        return content
    }

    fun drainEffects(): List<SettingsEffect> {
        return mutations.drainEffects()
    }

    private fun buildPageContent(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ): SettingsPageStateContent = SettingsPageContentFactory.build(
        page,
        preferences,
        runtime,
        appearance,
        playback,
        lyrics,
        library,
        network,
        platform,
        ::navigateSettingsPage,
        ::navigateSettingsSearchResult
    )

    fun navigateSettingsPage(page: SettingsPage) {
        val current = _state.value
        publishCurrentPage(page, current.preferences, current.runtime, highlightedEntryId = null)
        mutations.emit(SettingsEffect.NavigatePage(page))
    }

    private fun navigateSettingsSearchResult(entryId: SettingsEntryId, page: SettingsPage) {
        val current = _state.value
        publishCurrentPage(page, current.preferences, current.runtime, highlightedEntryId = entryId)
        mutations.emit(SettingsEffect.NavigatePage(page))
    }

    private fun updatePreferences(transform: (SettingsPreferencesSnapshot) -> SettingsPreferencesSnapshot) {
        val current = _state.value
        val nextPreferences = transform(current.preferences)
        mutations.syncStore(nextPreferences)
        publishCurrentPage(current.page, nextPreferences, current.runtime)
    }

    private fun syncChromeState(preferences: SettingsPreferencesSnapshot) {
        _chromeState.value = SettingsChromeState(
            pageBackgrounds = preferences.pageBackgrounds,
            homeDashboardLayout = preferences.homeDashboardLayout,
            nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled,
            customBackgroundBlurEnabled = preferences.customBackgroundBlurEnabled,
            customBackgroundBlurRadiusDp = preferences.customBackgroundBlurRadiusDp,
            glassBlurEnabled = preferences.glassBlurEnabled,
            glassBlurRadiusDp = preferences.glassBlurRadiusDp,
            glassSurfaceOpacity = preferences.glassSurfaceOpacity,
            compactSettingsCards = preferences.compactSettingsCards
        )
    }

    private fun updateRuntime(transform: (RuntimeSettingsStatus) -> RuntimeSettingsStatus) {
        val current = _state.value
        publishCurrentPage(
            current.page,
            current.preferences,
            transform(current.runtime),
            current.highlightedEntryId
        )
    }

    private fun replaceSnapshot(
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ) {
        val current = _state.value
        publishCurrentPage(current.page, preferences, runtime, current.highlightedEntryId)
    }

}
