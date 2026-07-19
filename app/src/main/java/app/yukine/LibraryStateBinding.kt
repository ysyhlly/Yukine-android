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
    private var favoritePendingJob: Job? = null
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
        favoritePendingJob?.cancel()
        bindingJob = null
        playlistSourcesJob = null
        favoritePendingJob = null
        playbackReadModel = playback
        if (routeState == null || libraryState == null || settingsState == null || playback == null) {
            return
        }
        val route = routeState.map(::libraryBindingRoute).distinctUntilChanged()
        val presentation = viewModel.presentation.ui
            .map { LibraryPresentationProjection(it.sort, it.groupSort, it.filter) }
            .distinctUntilChanged()
        bindingJob = scope.launch {
            val content = combine(
                route,
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged(),
                playback.state.map { it.currentTrack }.distinctUntilChanged(),
                playlistSources
            ) { route, library, languageMode, _, sources ->
                LibraryBindingInputs(route, library, languageMode, sources)
            }
            combine(content, presentation) { inputs, _ -> inputs }.collect { inputs ->
                if (inputs.route.active) {
                    publish(inputs)
                }
            }
        }
        playlistSourcesJob = scope.launch {
            combine(
                route,
                libraryState.map { it.playlists }.distinctUntilChanged()
            ) { routeInput, playlists -> routeInput to playlists }
                .collectLatest { (routeInput, playlists) ->
                    if (routeInput.active && routeInput.libraryMode == LibraryGrouping.PLAYLISTS) {
                        playlistSources.value = withContext(ioDispatcher) {
                            playlistSourcesLoader.load(playlists)
                        }
                    }
                }
        }
        favoritePendingJob = scope.launch {
            combine(route, libraryStore.favoritePendingTrackIds) { routeInput, pendingIds ->
                routeInput to pendingIds
            }.collect { (routeInput, pendingIds) ->
                if (routeInput.active) {
                    trackListReducer.updateFavoritePendingIds(pendingIds)
                }
            }
        }
    }

    fun release() {
        bindingJob?.cancel()
        playlistSourcesJob?.cancel()
        favoritePendingJob?.cancel()
        bindingJob = null
        playlistSourcesJob = null
        favoritePendingJob = null
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
            AppLanguage.text(inputs.languageMode, "library.allSongs"),
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
            inputs.library.favoriteTrackIds,
            context = LibraryListContext.Songs,
            favoritePendingIds = libraryStore.favoritePendingTrackIds.value
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
            inputs.playlistSources,
            recentlyAddedTracks = inputs.library.recentlyAddedTracks,
            longUnplayedTracks = inputs.library.longUnplayedTracks
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
    sortTrackCountDescending = AppLanguage.text(languageMode, "library.sort.track.count.desc"),
    sortTrackCountAscending = AppLanguage.text(languageMode, "library.sort.track.count.asc"),
    sortArtist = AppLanguage.text(languageMode, "library.sort.artist"),
    sortAlbum = AppLanguage.text(languageMode, "library.sort.album"),
    sortDurationAscending = AppLanguage.text(languageMode, "library.sort.duration.asc"),
    sortDurationDescending = AppLanguage.text(languageMode, "library.sort.duration.desc"),
    syncLibrary = AppLanguage.text(languageMode, "library.sync.title"),
    syncLibraryDescription = AppLanguage.text(languageMode, "library.sync.description"),
    syncingLibrary = AppLanguage.text(languageMode, "library.sync.in.progress"),
    autoSync = AppLanguage.text(languageMode, "library.auto.sync"),
    scanLibrary = AppLanguage.text(languageMode, "scan.library"),
    importFiles = AppLanguage.text(languageMode, "import.audio.files"),
    clearSearch = AppLanguage.text(languageMode, "library.search.clear"),
    resetFilter = AppLanguage.text(languageMode, "library.filter.reset"),
    emptySearch = AppLanguage.text(languageMode, "library.empty.search"),
    emptyFilter = AppLanguage.text(languageMode, "library.empty.filter"),
    emptyGroupSearch = AppLanguage.text(languageMode, "library.empty.group.search"),
    emptyGroupFilter = AppLanguage.text(languageMode, "library.empty.group.filter"),
    emptyLibrary = AppLanguage.text(languageMode, "no.music"),
    groupCountSuffix = AppLanguage.text(languageMode, "library.group.count.suffix"),
    back = AppLanguage.text(languageMode, "back"),
    overviewShelf = AppLanguage.text(languageMode, "library.overview.shelf"),
    overviewBrowse = AppLanguage.text(languageMode, "library.overview.browse"),
    overviewSaved = AppLanguage.text(languageMode, "library.overview.saved"),
    overviewSourcesSync = AppLanguage.text(languageMode, "library.overview.sources.sync"),
    overviewFavorites = AppLanguage.text(languageMode, "library.overview.favorites"),
    overviewDownloaded = AppLanguage.text(languageMode, "library.overview.downloaded"),
    overviewSources = AppLanguage.text(languageMode, "library.overview.sources"),
    overviewSearchHint = AppLanguage.text(languageMode, "library.overview.search.hint"),
    overviewSongUnit = AppLanguage.text(languageMode, "library.overview.song.unit"),
    overviewLocalSource = AppLanguage.text(languageMode, "library.overview.local.source"),
    overviewAllSongs = AppLanguage.text(languageMode, "library.allSongs"),
    overviewAlbums = AppLanguage.text(languageMode, "albums"),
    overviewArtists = AppLanguage.text(languageMode, "artists"),
    overviewPlaylists = AppLanguage.text(languageMode, "playlists"),
    overviewFolders = AppLanguage.text(languageMode, "folders"),
    smartRecentAdded = AppLanguage.text(languageMode, "library.smart.recent.added"),
    smartRecentPlayed = AppLanguage.text(languageMode, "library.smart.recent.played"),
    smartWeekFavorites = AppLanguage.text(languageMode, "library.smart.week.favorites"),
    smartLongUnplayed = AppLanguage.text(languageMode, "library.smart.long.unplayed"),
    smartRecentAddedEmpty = AppLanguage.text(languageMode, "library.empty.smart.recent.added"),
    smartRecentPlayedEmpty = AppLanguage.text(languageMode, "library.empty.smart.recent.played"),
    smartWeekFavoritesEmpty = AppLanguage.text(languageMode, "library.empty.smart.week.favorites"),
    smartLongUnplayedEmpty = AppLanguage.text(languageMode, "library.empty.smart.long.unplayed")
)

private data class LibraryPresentationProjection(
    val sort: app.yukine.ui.LibrarySort,
    val groupSort: app.yukine.ui.LibraryGroupSort,
    val filter: app.yukine.ui.LibraryFilter
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
    AppLanguage.text(languageMode, "recording.match.manage"),
    AppLanguage.text(languageMode, "songs"),
    AppLanguage.text(languageMode, "more"),
    AppLanguage.text(languageMode, "library.favorite.updating")
)
