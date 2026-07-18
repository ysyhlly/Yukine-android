package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryMode
import app.yukine.ui.LibraryPlaylistFolderUiState
import app.yukine.ui.LibraryUiLabels
import app.yukine.ui.LibraryUiState
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The only mutable owner of library browse, selection and destination presentation state. */
class LibraryPresentationStateOwner internal constructor(
    private val gateway: () -> LibraryGateway?,
    private val favorites: LibraryFavoriteStateOwner,
    private val playlists: LibraryPlaylistStateOwner
) {
    private val trackListState = MutableStateFlow(LibraryTrackListDestinationState())
    val trackList: StateFlow<LibraryTrackListDestinationState> = trackListState.asStateFlow()
    private val groupsState = MutableStateFlow(LibraryGroupsDestinationState())
    val groups: StateFlow<LibraryGroupsDestinationState> = groupsState.asStateFlow()
    private val uiState = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = uiState.asStateFlow()
    private var visibleTracks: LinkedHashMap<String, Track> = LinkedHashMap()
    private var visibleGroups: LinkedHashMap<String, List<Track>> = LinkedHashMap()

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.QueryChanged -> {
                uiState.value = uiState.value.copy(
                    query = action.query,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
                gateway()?.search(action.query)
            }
            is LibraryAction.SortChanged -> {
                uiState.value = uiState.value.copy(sort = action.sort, revealedRowKey = null)
                publishInteractionState()
            }
            is LibraryAction.GroupSortChanged -> {
                uiState.value = uiState.value.copy(groupSort = action.sort, revealedRowKey = null)
                publishInteractionState()
            }
            is LibraryAction.FilterChanged -> {
                uiState.value = uiState.value.copy(
                    filter = action.filter,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
            }
            is LibraryAction.ModeChanged -> {
                uiState.value = uiState.value.copy(
                    mode = action.mode,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
                gateway()?.changeGroupMode(action.mode.routeKey)
            }
            is LibraryAction.RevealTrack -> {
                uiState.value = uiState.value.copy(revealedRowKey = action.key)
                publishInteractionState()
            }
            is LibraryAction.ToggleTrackSelection -> toggleTrackSelection(action.key)
            is LibraryAction.ToggleGroupSelection -> toggleGroupSelection(action.key)
            LibraryAction.SelectAllVisible -> selectAllVisible()
            LibraryAction.ClearSelection -> clearSelection()
            LibraryAction.PlaySelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway()?.playTrackList(it, 0) }
            LibraryAction.FavoriteSelected -> favorites.favoriteAll(selectedTracks())
            LibraryAction.AddSelectedToPlaylist -> playlists.addAllToDefault(selectedTracks())
            LibraryAction.DownloadSelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway()?.downloadTracks(it) }
            LibraryAction.DeleteSelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway()?.requestDeleteTracks(it) }
            LibraryAction.ScanLibrary -> gateway()?.scanLibrary()
            LibraryAction.ImportFiles -> gateway()?.importFiles()
            LibraryAction.SyncLibrary -> gateway()?.syncWebDavLibrary()
            is LibraryAction.SetAutoSyncEnabled -> gateway()?.setAutomaticSyncEnabled(action.enabled)
        }
    }

    fun updateSyncInProgress(inProgress: Boolean) {
        if (uiState.value.operationInProgress == inProgress) return
        uiState.value = uiState.value.copy(operationInProgress = inProgress)
        publishInteractionState()
    }

    fun updateAutoSyncEnabled(enabled: Boolean) {
        if (uiState.value.autoSyncEnabled == enabled) return
        uiState.value = uiState.value.copy(autoSyncEnabled = enabled)
        publishInteractionState()
    }

    fun updateVisibleTrackTargets(tracks: List<Track>, keys: List<String>) {
        visibleTracks = LinkedHashMap<String, Track>(tracks.size).apply {
            for (index in tracks.indices) {
                put(keys.getOrElse(index) { tracks[index].id.toString() }, tracks[index])
            }
        }
        val selected = uiState.value.selectedTrackKeys.intersect(visibleTracks.keys)
        if (selected != uiState.value.selectedTrackKeys) {
            uiState.value = uiState.value.copy(selectedTrackKeys = selected)
            publishInteractionState()
        }
    }

    fun updateVisibleGroupTargets(groups: Map<String, List<Track>>) {
        visibleGroups = LinkedHashMap(groups)
        val selected = uiState.value.selectedGroupKeys.intersect(visibleGroups.keys)
        if (selected != uiState.value.selectedGroupKeys) {
            uiState.value = uiState.value.copy(selectedGroupKeys = selected)
            publishInteractionState()
        }
    }

    fun syncLibraryMode(routeKey: String) {
        val mode = LibraryMode.fromRouteKey(routeKey)
        if (uiState.value.mode == mode) return
        uiState.value = uiState.value.copy(
            mode = mode,
            revealedRowKey = null,
            selectedTrackKeys = emptySet(),
            selectedGroupKeys = emptySet()
        )
        publishInteractionState()
    }

    fun updateLibraryLabels(labels: LibraryUiLabels) {
        if (uiState.value.labels == labels) return
        uiState.value = uiState.value.copy(labels = labels)
        publishInteractionState()
    }

    fun syncSearchQuery(query: String?) {
        val normalized = query.orEmpty()
        if (uiState.value.query == normalized) return
        uiState.value = uiState.value.copy(
            query = normalized,
            revealedRowKey = null,
            selectedTrackKeys = emptySet(),
            selectedGroupKeys = emptySet()
        )
        publishInteractionState()
    }

    private fun toggleTrackSelection(key: String) {
        if (!visibleTracks.containsKey(key)) return
        val selected = uiState.value.selectedTrackKeys.toMutableSet()
        if (!selected.add(key)) selected.remove(key)
        uiState.value = uiState.value.copy(
            selectedTrackKeys = selected,
            selectedGroupKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun toggleGroupSelection(key: String) {
        if (!visibleGroups.containsKey(key)) return
        val selected = uiState.value.selectedGroupKeys.toMutableSet()
        if (!selected.add(key)) selected.remove(key)
        uiState.value = uiState.value.copy(
            selectedGroupKeys = selected,
            selectedTrackKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun selectAllVisible() {
        uiState.value = if (visibleGroups.isNotEmpty() && groupsState.value.title.isNotBlank()) {
            uiState.value.copy(
                selectedGroupKeys = visibleGroups.keys,
                selectedTrackKeys = emptySet(),
                revealedRowKey = null
            )
        } else {
            uiState.value.copy(
                selectedTrackKeys = visibleTracks.keys,
                selectedGroupKeys = emptySet(),
                revealedRowKey = null
            )
        }
        publishInteractionState()
    }

    private fun clearSelection() {
        uiState.value = uiState.value.copy(
            selectedTrackKeys = emptySet(),
            selectedGroupKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun selectedTracks(): List<Track> {
        val direct = uiState.value.selectedTrackKeys.mapNotNull(visibleTracks::get)
        if (direct.isNotEmpty()) return direct.distinctBy { it.id to it.dataPath }
        return uiState.value.selectedGroupKeys
            .flatMap { visibleGroups[it].orEmpty() }
            .distinctBy { it.id to it.dataPath }
    }

    private fun publishInteractionState() {
        val state = uiState.value
        trackListState.value = trackListState.value.copy(libraryUi = state)
        groupsState.value = groupsState.value.copy(libraryUi = state)
    }

    fun updateTrackList(
        title: String,
        rows: List<TrackRowUiState>,
        footerAlbums: List<TrackListAlbumCardUiState> = emptyList()
    ) {
        trackListState.value = trackListState.value.copy(
            title = title,
            rows = rows.toList(),
            footerAlbums = footerAlbums.toList()
        )
    }

    fun updateTrackListContentAndChrome(
        title: String,
        rows: List<TrackRowUiState>,
        footerAlbums: List<TrackListAlbumCardUiState>,
        actions: List<TrackRowActions>,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels,
        context: LibraryListContext = LibraryListContext.Songs
    ) {
        trackListState.value = trackListState.value.copy(
            title = title,
            rows = rows,
            footerAlbums = footerAlbums,
            actions = actions,
            headerMetrics = headerMetrics,
            headerActions = headerActions,
            emptyText = emptyText,
            modeActions = modeActions,
            labels = labels,
            libraryUi = uiState.value,
            context = context
        )
    }

    fun clearTrackList() {
        trackListState.value = LibraryTrackListDestinationState()
    }

    @JvmOverloads
    fun updateLibraryGroups(
        title: String,
        rows: List<LibraryGroupUiState>,
        playlistFolders: List<LibraryPlaylistFolderUiState> = emptyList()
    ) {
        groupsState.value = groupsState.value.copy(
            title = title,
            rows = rows.toList(),
            libraryUi = uiState.value,
            playlistFolders = playlistFolders.toList()
        )
    }

    fun clearLibraryGroups() {
        groupsState.value = LibraryGroupsDestinationState()
    }

    fun updateLibraryGroupsChrome(
        actions: List<LibraryGroupActions>,
        emptyText: String,
        modeActions: List<TrackListModeAction>
    ) {
        groupsState.value = groupsState.value.copy(
            actions = actions.toList(),
            emptyText = emptyText,
            modeActions = modeActions.toList()
        )
    }

    fun updateLibraryGroupsChrome(state: LibraryGroupsDestinationState) {
        groupsState.value = groupsState.value.copy(
            actions = state.actions.toList(),
            emptyText = state.emptyText,
            modeActions = state.modeActions.toList()
        )
    }
}
