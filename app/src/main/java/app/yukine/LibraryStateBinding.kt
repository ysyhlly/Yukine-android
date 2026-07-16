package app.yukine

import app.yukine.navigation.LibraryTab
import app.yukine.model.Playlist
import app.yukine.playback.PlaybackReadModel
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.LibraryUiLabels
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun interface LibraryAudioPermissionReader {
    fun hasAudioPermission(): Boolean
}

internal fun interface LibraryStatusSink {
    fun showStatus(message: String)
}

internal fun interface LibraryPlaylistSourcesLoader {
    fun load(playlists: List<Playlist>): Map<Long, StreamingProviderName>
}

internal class LibraryStateBinding @JvmOverloads constructor(
    private val libraryStore: LibraryDataStateOwner,
    private val viewModel: LibraryViewModel,
    private val trackListReducer: TrackListStateReducer,
    private val groupsReducer: LibraryGroupsStateReducer,
    private val playlistsReducer: LibraryPlaylistsStateReducer,
    private val audioPermissionReader: LibraryAudioPermissionReader,
    private val statusSink: LibraryStatusSink,
    private val playlistSourcesLoader: LibraryPlaylistSourcesLoader = LibraryPlaylistSourcesLoader { emptyMap() },
    private val scope: CoroutineScope = MainScope(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var bindingJob: Job? = null
    private var playlistSourcesJob: Job? = null
    private var playbackReadModel: PlaybackReadModel? = null
    private val playlistSources = MutableStateFlow<Map<Long, StreamingProviderName>>(emptyMap())

    fun bindStateSources(
        routeState: StateFlow<NavigationRouteState>?,
        libraryState: StateFlow<LibraryStoreState>?,
        settingsState: StateFlow<SettingsState>?,
        playback: PlaybackReadModel?
    ) {
        bindingJob?.cancel()
        playlistSourcesJob?.cancel()
        bindingJob = null
        playlistSourcesJob = null
        playbackReadModel = playback
        if (routeState == null || libraryState == null || settingsState == null || playback == null) {
            return
        }
        val route = routeState.map(::libraryBindingRoute).distinctUntilChanged()
        bindingJob = scope.launch {
            combine(
                route,
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged(),
                playback.state.map { it.currentTrack }.distinctUntilChanged(),
                playlistSources
            ) { route, library, languageMode, _, sources ->
                LibraryBindingInputs(route, library, languageMode, sources)
            }.collect { inputs ->
                if (inputs.route.active) {
                    publish(inputs)
                }
            }
        }
        playlistSourcesJob = scope.launch {
            combine(route, libraryState) { routeInput, library -> routeInput to library.playlists }
                .collectLatest { (routeInput, playlists) ->
                    if (routeInput.active && routeInput.libraryMode == LibraryGrouping.PLAYLISTS) {
                        playlistSources.value = withContext(ioDispatcher) {
                            playlistSourcesLoader.load(playlists)
                        }
                    }
                }
        }
    }

    fun release() {
        bindingJob?.cancel()
        playlistSourcesJob?.cancel()
        bindingJob = null
        playlistSourcesJob = null
        playbackReadModel = null
        scope.cancel()
    }

    private fun publish(inputs: LibraryBindingInputs) {
        val route = inputs.route
        val languageMode = inputs.languageMode
        viewModel.presentation.updateLibraryLabels(libraryUiLabels(languageMode))
        viewModel.presentation.syncLibraryMode(route.libraryMode)
        viewModel.presentation.syncSearchQuery(route.searchQuery)
        if (!audioPermissionReader.hasAudioPermission()) {
            statusSink.showStatus(
                AppLanguage.text(languageMode, "audio.permission.required") + ": " +
                    AppLanguage.text(languageMode, "audio.permission.description")
            )
            return
        }
        if (inputs.library.visibleTracks.isEmpty() && route.libraryMode != LibraryGrouping.PLAYLISTS) {
            statusSink.showStatus(
                AppLanguage.text(languageMode, "no.music") + ": " +
                    AppLanguage.text(languageMode, "no.music.description")
            )
            return
        }
        val modeActions = libraryModeActions(languageMode, route.libraryMode)
        when (route.libraryMode) {
            LibraryGrouping.PLAYLISTS -> publishPlaylists(inputs, modeActions)
            LibraryGrouping.SONGS -> publishSongs(inputs, modeActions)
            else -> groupsReducer.reduce(
                languageMode,
                inputs.library.visibleTracks,
                route.libraryMode,
                route.selectedLibraryGroupKey,
                route.selectedLibraryGroupTitle,
                modeActions,
                inputs.library.favoriteTrackIds
            )
        }
    }

    private fun publishSongs(inputs: LibraryBindingInputs, modeActions: List<TrackListModeAction>) {
        trackListReducer.reduce(
            AppLanguage.text(inputs.languageMode, "songs"),
            inputs.library.visibleTracks,
            true,
            emptyList(),
            false,
            emptyList<TrackListHeaderMetric>(),
            emptyList<TrackListHeaderAction>(),
            "",
            modeActions,
            trackListLabels(inputs.languageMode),
            playbackReadModel?.state?.value,
            inputs.library.favoriteTrackIds
        )
    }

    private fun publishPlaylists(inputs: LibraryBindingInputs, modeActions: List<TrackListModeAction>) {
        val route = inputs.route
        playlistsReducer.reduce(
            inputs.languageMode,
            inputs.library.playlists,
            route.selectedPlaylistId,
            route.selectedLibraryGroupKey,
            libraryStore.selectedPlaylistName(route.selectedPlaylistId),
            libraryStore.filteredTracks(inputs.library.selectedPlaylistTracks, route.searchQuery),
            inputs.library.favoriteTracks,
            inputs.library.recentRecords,
            modeActions,
            inputs.playlistSources
        )
    }

    private fun libraryModeActions(languageMode: String, selectedMode: String): ArrayList<TrackListModeAction> {
        val modes = ArrayList<TrackListModeAction>()
        addMode(modes, languageMode, "songs", LibraryGrouping.SONGS, selectedMode)
        addMode(modes, languageMode, "albums", LibraryGrouping.ALBUMS, selectedMode)
        addMode(modes, languageMode, "artists", LibraryGrouping.ARTISTS, selectedMode)
        addMode(modes, languageMode, "folders", LibraryGrouping.FOLDERS, selectedMode)
        addMode(modes, languageMode, "playlists", LibraryGrouping.PLAYLISTS, selectedMode)
        return modes
    }

    private fun addMode(
        modes: MutableList<TrackListModeAction>,
        languageMode: String,
        labelKey: String,
        mode: String,
        selectedMode: String
    ) {
        modes += TrackListModeAction(
            AppLanguage.text(languageMode, labelKey),
            mode,
            mode == selectedMode,
            Runnable { viewModel.onEvent(LibraryEvent.ChangeGroupMode(mode)) }
        )
    }
}

private data class LibraryBindingRoute(
    val active: Boolean,
    val libraryMode: String,
    val selectedLibraryGroupKey: String,
    val selectedLibraryGroupTitle: String,
    val selectedPlaylistId: Long,
    val searchQuery: String
)

private data class LibraryBindingInputs(
    val route: LibraryBindingRoute,
    val library: LibraryStoreState,
    val languageMode: String,
    val playlistSources: Map<Long, StreamingProviderName>
)

private fun libraryBindingRoute(state: NavigationRouteState): LibraryBindingRoute =
    LibraryBindingRoute(
        active = state.selectedTab == LibraryTab,
        libraryMode = state.libraryMode,
        selectedLibraryGroupKey = state.selectedLibraryGroupKey,
        selectedLibraryGroupTitle = state.selectedLibraryGroupTitle,
        selectedPlaylistId = state.selectedPlaylistId,
        searchQuery = state.searchQuery
    )

private fun libraryUiLabels(languageMode: String): LibraryUiLabels = LibraryUiLabels(
    search = AppLanguage.text(languageMode, "library.search"),
    sort = AppLanguage.text(languageMode, "library.sort"),
    filter = AppLanguage.text(languageMode, "library.filter"),
    all = AppLanguage.text(languageMode, "library.filter.all"),
    favorites = AppLanguage.text(languageMode, "favorite"),
    local = AppLanguage.text(languageMode, "library.filter.local"),
    network = AppLanguage.text(languageMode, "library.filter.network"),
    selectAll = AppLanguage.text(languageMode, "library.select.all"),
    cancel = AppLanguage.text(languageMode, "cancel"),
    play = AppLanguage.text(languageMode, "play"),
    addToPlaylist = AppLanguage.text(languageMode, "add.to.playlist"),
    favorite = AppLanguage.text(languageMode, "favorite"),
    download = AppLanguage.text(languageMode, "download"),
    delete = AppLanguage.text(languageMode, "delete"),
    more = AppLanguage.text(languageMode, "more"),
    selectedSuffix = AppLanguage.text(languageMode, "library.selected.suffix"),
    sortTitleAscending = AppLanguage.text(languageMode, "library.sort.title.asc"),
    sortTitleDescending = AppLanguage.text(languageMode, "library.sort.title.desc"),
    sortArtist = AppLanguage.text(languageMode, "library.sort.artist"),
    sortAlbum = AppLanguage.text(languageMode, "library.sort.album"),
    sortDurationAscending = AppLanguage.text(languageMode, "library.sort.duration.asc"),
    sortDurationDescending = AppLanguage.text(languageMode, "library.sort.duration.desc"),
    syncLibrary = AppLanguage.text(languageMode, "library.sync.title"),
    syncLibraryDescription = AppLanguage.text(languageMode, "library.sync.description"),
    syncingLibrary = AppLanguage.text(languageMode, "library.sync.in.progress"),
    autoSync = AppLanguage.text(languageMode, "library.auto.sync")
)

private fun trackListLabels(languageMode: String): TrackListLabels = TrackListLabels(
    AppLanguage.text(languageMode, "favorite"),
    AppLanguage.text(languageMode, "remove.favorite"),
    AppLanguage.text(languageMode, "add.to.playlist"),
    AppLanguage.text(languageMode, "edit"),
    AppLanguage.text(languageMode, "delete"),
    AppLanguage.text(languageMode, "download"),
    AppLanguage.text(languageMode, "download.current.list"),
    AppLanguage.text(languageMode, "all.albums"),
    AppLanguage.text(languageMode, "play.all"),
    AppLanguage.text(languageMode, "shuffle"),
    AppLanguage.text(languageMode, "recording.match.manage")
)
