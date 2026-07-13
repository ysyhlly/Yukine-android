package app.yukine

import app.yukine.navigation.LibraryTab
import app.yukine.playback.PlaybackReadModel
import app.yukine.ui.LibraryUiLabels
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun interface LibraryAudioPermissionReader {
    fun hasAudioPermission(): Boolean
}

internal fun interface LibraryStatusSink {
    fun showStatus(message: String)
}

internal class LibraryRenderOwner @JvmOverloads constructor(
    private val libraryStore: MainLibraryStore,
    private val viewModel: LibraryViewModel,
    private val trackListRenderer: TrackListRenderController,
    private val groupsRenderer: LibraryGroupsRenderController,
    private val playlistsRenderer: LibraryPlaylistsRenderController,
    private val audioPermissionReader: LibraryAudioPermissionReader,
    private val statusSink: LibraryStatusSink,
    private val scope: CoroutineScope = MainScope()
) {
    private var renderJob: Job? = null
    private var playbackReadModel: PlaybackReadModel? = null

    fun bindStateSources(
        routeState: StateFlow<NavigationRouteState>?,
        libraryState: StateFlow<LibraryStoreState>?,
        settingsState: StateFlow<SettingsState>?,
        playback: PlaybackReadModel?
    ) {
        renderJob?.cancel()
        renderJob = null
        playbackReadModel = playback
        if (routeState == null || libraryState == null || settingsState == null || playback == null) {
            return
        }
        renderJob = scope.launch {
            combine(
                routeState.map(::libraryRenderRoute).distinctUntilChanged(),
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged(),
                playback.state.map { it.currentTrack }.distinctUntilChanged()
            ) { route, library, languageMode, _ ->
                LibraryRenderInputs(route, library, languageMode)
            }.collect { inputs ->
                if (inputs.route.active) {
                    render(inputs)
                }
            }
        }
    }

    fun release() {
        renderJob?.cancel()
        renderJob = null
        playbackReadModel = null
        scope.cancel()
    }

    private fun render(inputs: LibraryRenderInputs) {
        val route = inputs.route
        val languageMode = inputs.languageMode
        viewModel.updateLibraryLabels(libraryUiLabels(languageMode))
        viewModel.syncLibraryMode(route.libraryMode)
        viewModel.syncSearchQuery(route.searchQuery)
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
            LibraryGrouping.PLAYLISTS -> renderPlaylists(inputs, modeActions)
            LibraryGrouping.SONGS -> renderSongs(inputs, modeActions)
            else -> groupsRenderer.render(
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

    private fun renderSongs(inputs: LibraryRenderInputs, modeActions: List<TrackListModeAction>) {
        trackListRenderer.render(
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

    private fun renderPlaylists(inputs: LibraryRenderInputs, modeActions: List<TrackListModeAction>) {
        val route = inputs.route
        playlistsRenderer.render(
            inputs.languageMode,
            inputs.library.playlists,
            route.selectedPlaylistId,
            route.selectedLibraryGroupKey,
            libraryStore.selectedPlaylistName(route.selectedPlaylistId),
            libraryStore.filteredTracks(inputs.library.selectedPlaylistTracks, route.searchQuery),
            inputs.library.favoriteTracks,
            inputs.library.recentRecords,
            modeActions
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

private data class LibraryRenderRoute(
    val active: Boolean,
    val libraryMode: String,
    val selectedLibraryGroupKey: String,
    val selectedLibraryGroupTitle: String,
    val selectedPlaylistId: Long,
    val searchQuery: String
)

private data class LibraryRenderInputs(
    val route: LibraryRenderRoute,
    val library: LibraryStoreState,
    val languageMode: String
)

private fun libraryRenderRoute(state: NavigationRouteState): LibraryRenderRoute =
    LibraryRenderRoute(
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
    sortDurationDescending = AppLanguage.text(languageMode, "library.sort.duration.desc")
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
    AppLanguage.text(languageMode, "shuffle")
)
