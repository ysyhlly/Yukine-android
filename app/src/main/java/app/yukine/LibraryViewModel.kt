package app.yukine

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.model.TrackPlayRecord
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibraryMode
import app.yukine.ui.LibraryUiLabels
import app.yukine.ui.LibraryUiState
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowUiState
import app.yukine.ui.TrackRowActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LibraryEvent {
    data class PlayTrackList(val tracks: List<Track>, val index: Int) : LibraryEvent
    data class PlayPlaylist(val playlistId: Long) : LibraryEvent
    data class ToggleFavorite(val track: Track) : LibraryEvent
    data class AddToPlaylist(val track: Track) : LibraryEvent
    data class ChangeGroupMode(val mode: String) : LibraryEvent
    data class OpenGroup(val key: String, val title: String) : LibraryEvent
    data class OpenPlaylist(val playlistId: Long, val title: String) : LibraryEvent
    data object BackFromGroup : LibraryEvent
    data class Search(val query: String) : LibraryEvent
    data object ImportFiles : LibraryEvent
    data object ScanLibrary : LibraryEvent
    data class DeleteTracks(val tracks: List<Track>) : LibraryEvent
}

interface LibraryGateway {
    fun playTrackList(tracks: List<Track>, index: Int)
    fun showStatusKey(key: String)
    fun applyFavorite(trackId: Long, favorite: Boolean)
    fun addToPlaylist(track: Track)
    fun changeGroupMode(mode: String)
    fun openGroup(key: String, title: String)
    fun openPlaylist(playlistId: Long, title: String)
    fun backFromGroup()
    fun search(query: String)
    fun importFiles()
    fun scanLibrary()
    fun requestDeleteTracks(tracks: List<Track>) = Unit
    fun downloadTracks(tracks: List<Track>) = Unit
}

fun interface LibraryPlaylistTrackLoader {
    fun loadPlaylistTracks(playlistId: Long): List<Track>
}

fun interface LibraryFavoriteWriter {
    fun writeFavorite(track: Track, favorite: Boolean): Boolean
}

fun interface LibraryFavoriteIdsProvider {
    fun favoriteIds(): Set<Long>
}

data class LibraryCollectionsResult(
    val selectedPlaylistId: Long = -1L,
    val favoriteIds: Set<Long> = emptySet(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentRecords: List<TrackPlayRecord> = emptyList(),
    val mostPlayedRecords: List<TrackPlayRecord> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val remoteSources: List<RemoteSource> = emptyList(),
    val selectedPlaylistTracks: List<Track> = emptyList()
)

interface LibraryCollectionGateway {
    fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult

    fun clearPlayHistory(): Int
}

interface LibraryExclusionGateway {
    fun restoreLibraryExclusion(sourceKey: String): Boolean

    fun restoreAllLibraryExclusions(): Int
}

fun interface LibraryExclusionRestoreCallback {
    fun onRestored(changed: Boolean)
}

data class LibraryLoadResultUi(
    val tracks: List<Track> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val status: String = "Library updated"
)

data class LibraryAudioSpecsResultUi(
    val updatedCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val favorites: Set<Long> = emptySet()
)

interface LibraryImportGateway {
    fun loadCached(): LibraryLoadResultUi

    fun refresh(): LibraryLoadResultUi

    fun importAudioUris(uris: List<@JvmSuppressWildcards Uri>): LibraryLoadResultUi

    fun importAudioTree(treeUri: Uri): LibraryLoadResultUi

    fun parseMissingAudioSpecs(): LibraryAudioSpecsResultUi
}

/** Optional phase-aware refresh capability implemented by the real import gateway. */
internal interface LibraryRefreshProgressGateway {
    fun refresh(onProgress: (LibraryRefreshProgress) -> Unit): LibraryLoadResultUi
}

data class LibraryPlaylistImportResultUi(
    val playlistId: Long = -1L,
    val tracks: List<Track> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val status: String = ""
)

fun interface LibraryPlaylistImportCallback {
    fun onImported(result: LibraryPlaylistImportResultUi)
}

interface LibraryDocumentGateway {
    fun importStreamM3u(playlistUri: Uri?): LibraryLoadResultUi

    fun importPlaylistM3u(playlistUri: Uri?): LibraryPlaylistImportResultUi

    fun exportPlaylist(exportUri: Uri?, playlistId: Long, playlistName: String): Boolean
}

fun interface LibraryCollectionsCallback {
    fun onLoaded(result: LibraryCollectionsResult)
}

fun interface LibraryLoadCallback {
    fun onLoaded(result: LibraryLoadResultUi)
}

fun interface LibraryLoadFailedCallback {
    fun onFailed(status: String)
}

fun interface LibraryAudioSpecsParsedCallback {
    fun onParsed(result: LibraryAudioSpecsResultUi)
}

fun interface LibraryDefaultPlaylistTrackAddedCallback {
    fun onAdded(playlistId: Long, added: Boolean)
}

fun interface LibraryPlaylistCreatedCallback {
    fun onCreated(playlistId: Long)
}

fun interface LibraryPlaylistRenamedCallback {
    fun onRenamed(playlistId: Long, renamed: Boolean)
}

fun interface LibraryPlaylistDeletedCallback {
    fun onDeleted(playlistId: Long, name: String, deleted: Boolean)
}

fun interface LibrarySelectedPlaylistTrackRemovedCallback {
    fun onRemoved(playlistId: Long, track: Track)
}

fun interface LibrarySelectedPlaylistTrackMovedCallback {
    fun onMoved(playlistId: Long, track: Track, direction: Int, moved: Boolean)
}

fun interface LibraryTrackAddedToPlaylistCallback {
    fun onAdded(playlistId: Long, added: Boolean)
}

class LibraryViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val libraryRefreshDiagnosticTimeoutMs: Long = DEFAULT_LIBRARY_REFRESH_DIAGNOSTIC_TIMEOUT_MS
) : ViewModel() {
    private val trackListState = MutableStateFlow(LibraryTrackListDestinationState())
    val trackList: StateFlow<LibraryTrackListDestinationState> = trackListState.asStateFlow()

    private val libraryGroupsState = MutableStateFlow(LibraryGroupsDestinationState())
    val libraryGroups: StateFlow<LibraryGroupsDestinationState> = libraryGroupsState.asStateFlow()

    private val libraryUiState = MutableStateFlow(LibraryUiState())
    val libraryUi: StateFlow<LibraryUiState> = libraryUiState.asStateFlow()

    private var visibleTrackTargets: LinkedHashMap<String, Track> = LinkedHashMap()
    private var visibleGroupTargets: LinkedHashMap<String, List<Track>> = LinkedHashMap()

    private var gateway: LibraryGateway? = null
    private var playlistTrackLoader: LibraryPlaylistTrackLoader? = null
    private var favoriteWriter: LibraryFavoriteWriter? = null
    private var favoriteIdsProvider: LibraryFavoriteIdsProvider? = null
    private var collectionGateway: LibraryCollectionGateway? = null
    private var importGateway: LibraryImportGateway? = null
    private var documentGateway: LibraryDocumentGateway? = null
    private var playlistActionGateway: LibraryPlaylistActionGateway? = null
    private var exclusionGateway: LibraryExclusionGateway? = null
    private var audioSpecParsingRunning = false
    private var audioSpecParsingPending = false
    private var pendingAudioSpecCallback: ((LibraryAudioSpecsResultUi) -> Unit)? = null
    private var libraryLoadJob: Job? = null
    private var libraryLoadWatchdogJob: Job? = null
    private var collectionLoadJob: Job? = null
    private var nextCollectionLoadId: Long = 0L
    private var activeCollectionLoadId: Long = 0L
    private val libraryMutationMutex = Mutex()
    private var nextLibraryLoadId: Long = 0L
    private var activeLibraryLoadId: Long = 0L

    fun bindGateway(nextGateway: LibraryGateway?) {
        gateway = nextGateway
    }

    fun bindPlaylistTrackLoader(nextLoader: LibraryPlaylistTrackLoader?) {
        playlistTrackLoader = nextLoader
    }

    fun bindFavoriteWriter(nextWriter: LibraryFavoriteWriter?) {
        favoriteWriter = nextWriter
    }

    fun bindFavoriteIdsProvider(nextProvider: LibraryFavoriteIdsProvider?) {
        favoriteIdsProvider = nextProvider
    }

    fun bindCollectionGateway(nextGateway: LibraryCollectionGateway?) {
        collectionGateway = nextGateway
    }

    fun bindImportGateway(nextGateway: LibraryImportGateway?) {
        importGateway = nextGateway
    }

    fun bindDocumentGateway(nextGateway: LibraryDocumentGateway?) {
        documentGateway = nextGateway
    }

    fun bindPlaylistActionGateway(nextGateway: LibraryPlaylistActionGateway?) {
        playlistActionGateway = nextGateway
    }

    fun bindExclusionGateway(nextGateway: LibraryExclusionGateway?) {
        exclusionGateway = nextGateway
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.PlayTrackList -> gateway?.playTrackList(event.tracks, event.index)
            is LibraryEvent.PlayPlaylist -> playPlaylist(event.playlistId)
            is LibraryEvent.ToggleFavorite -> toggleFavorite(event.track)
            is LibraryEvent.AddToPlaylist -> gateway?.addToPlaylist(event.track)
            is LibraryEvent.ChangeGroupMode -> gateway?.changeGroupMode(event.mode)
            is LibraryEvent.OpenGroup -> gateway?.openGroup(event.key, event.title)
            is LibraryEvent.OpenPlaylist -> gateway?.openPlaylist(event.playlistId, event.title)
            LibraryEvent.BackFromGroup -> gateway?.backFromGroup()
            is LibraryEvent.Search -> gateway?.search(event.query)
            LibraryEvent.ImportFiles -> gateway?.importFiles()
            LibraryEvent.ScanLibrary -> gateway?.scanLibrary()
            is LibraryEvent.DeleteTracks -> gateway?.requestDeleteTracks(event.tracks)
        }
    }

    fun onLibraryAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.QueryChanged -> {
                libraryUiState.value = libraryUiState.value.copy(
                    query = action.query,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
                gateway?.search(action.query)
            }
            is LibraryAction.SortChanged -> {
                libraryUiState.value = libraryUiState.value.copy(sort = action.sort, revealedRowKey = null)
                publishInteractionState()
            }
            is LibraryAction.FilterChanged -> {
                libraryUiState.value = libraryUiState.value.copy(
                    filter = action.filter,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
            }
            is LibraryAction.ModeChanged -> {
                libraryUiState.value = libraryUiState.value.copy(
                    mode = action.mode,
                    revealedRowKey = null,
                    selectedTrackKeys = emptySet(),
                    selectedGroupKeys = emptySet()
                )
                publishInteractionState()
                gateway?.changeGroupMode(action.mode.routeKey)
            }
            is LibraryAction.RevealTrack -> {
                libraryUiState.value = libraryUiState.value.copy(revealedRowKey = action.key)
                publishInteractionState()
            }
            is LibraryAction.ToggleTrackSelection -> toggleTrackSelection(action.key)
            is LibraryAction.ToggleGroupSelection -> toggleGroupSelection(action.key)
            LibraryAction.SelectAllVisible -> selectAllVisible()
            LibraryAction.ClearSelection -> clearSelection()
            LibraryAction.PlaySelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway?.playTrackList(it, 0) }
            LibraryAction.FavoriteSelected -> favoriteSelectedTracks()
            LibraryAction.AddSelectedToPlaylist -> addSelectedTracksToDefaultPlaylist()
            LibraryAction.DownloadSelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway?.downloadTracks(it) }
            LibraryAction.DeleteSelected -> selectedTracks().takeIf { it.isNotEmpty() }
                ?.let { gateway?.requestDeleteTracks(it) }
            LibraryAction.ScanLibrary -> gateway?.scanLibrary()
            LibraryAction.ImportFiles -> gateway?.importFiles()
        }
    }

    fun updateVisibleTrackTargets(tracks: List<Track>, keys: List<String>) {
        visibleTrackTargets = LinkedHashMap<String, Track>(tracks.size).apply {
            for (index in tracks.indices) {
                put(keys.getOrElse(index) { tracks[index].id.toString() }, tracks[index])
            }
        }
        val valid = visibleTrackTargets.keys
        val selected = libraryUiState.value.selectedTrackKeys.intersect(valid)
        if (selected != libraryUiState.value.selectedTrackKeys) {
            libraryUiState.value = libraryUiState.value.copy(selectedTrackKeys = selected)
            publishInteractionState()
        }
    }

    fun updateVisibleGroupTargets(groups: Map<String, List<Track>>) {
        visibleGroupTargets = LinkedHashMap(groups)
        val selected = libraryUiState.value.selectedGroupKeys.intersect(visibleGroupTargets.keys)
        if (selected != libraryUiState.value.selectedGroupKeys) {
            libraryUiState.value = libraryUiState.value.copy(selectedGroupKeys = selected)
            publishInteractionState()
        }
    }

    fun syncLibraryMode(routeKey: String) {
        val mode = LibraryMode.fromRouteKey(routeKey)
        if (libraryUiState.value.mode != mode) {
            libraryUiState.value = libraryUiState.value.copy(
                mode = mode,
                revealedRowKey = null,
                selectedTrackKeys = emptySet(),
                selectedGroupKeys = emptySet()
            )
            publishInteractionState()
        }
    }

    fun updateLibraryLabels(labels: LibraryUiLabels) {
        if (libraryUiState.value.labels == labels) return
        libraryUiState.value = libraryUiState.value.copy(labels = labels)
        publishInteractionState()
    }

    private fun toggleTrackSelection(key: String) {
        if (!visibleTrackTargets.containsKey(key)) return
        val selected = libraryUiState.value.selectedTrackKeys.toMutableSet()
        if (!selected.add(key)) selected.remove(key)
        libraryUiState.value = libraryUiState.value.copy(
            selectedTrackKeys = selected,
            selectedGroupKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun toggleGroupSelection(key: String) {
        if (!visibleGroupTargets.containsKey(key)) return
        val selected = libraryUiState.value.selectedGroupKeys.toMutableSet()
        if (!selected.add(key)) selected.remove(key)
        libraryUiState.value = libraryUiState.value.copy(
            selectedGroupKeys = selected,
            selectedTrackKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun selectAllVisible() {
        libraryUiState.value = if (visibleGroupTargets.isNotEmpty() && libraryGroupsState.value.title.isNotBlank()) {
            libraryUiState.value.copy(selectedGroupKeys = visibleGroupTargets.keys, selectedTrackKeys = emptySet(), revealedRowKey = null)
        } else {
            libraryUiState.value.copy(selectedTrackKeys = visibleTrackTargets.keys, selectedGroupKeys = emptySet(), revealedRowKey = null)
        }
        publishInteractionState()
    }

    private fun clearSelection() {
        libraryUiState.value = libraryUiState.value.copy(
            selectedTrackKeys = emptySet(),
            selectedGroupKeys = emptySet(),
            revealedRowKey = null
        )
        publishInteractionState()
    }

    private fun selectedTracks(): List<Track> {
        val direct = libraryUiState.value.selectedTrackKeys.mapNotNull(visibleTrackTargets::get)
        if (direct.isNotEmpty()) return direct.distinctBy { it.id to it.dataPath }
        return libraryUiState.value.selectedGroupKeys
            .flatMap { visibleGroupTargets[it].orEmpty() }
            .distinctBy { it.id to it.dataPath }
    }

    private fun favoriteSelectedTracks() {
        val writer = favoriteWriter ?: return
        val tracks = selectedTracks()
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val succeeded = libraryMutationMutex.withLock {
                withContext(ioDispatcher) {
                    tracks.count { track ->
                        try {
                            writer.writeFavorite(track, true)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }
            if (succeeded < tracks.size) gateway?.showStatusKey("library.favorite.failed")
        }
    }

    private fun addSelectedTracksToDefaultPlaylist() {
        val actionGateway = playlistActionGateway ?: return
        val tracks = selectedTracks()
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val succeeded = libraryMutationMutex.withLock {
                withContext(ioDispatcher) {
                    tracks.count { track ->
                        try {
                            actionGateway.addToDefaultPlaylist(track)?.added == true
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }
            gateway?.showStatusKey(
                if (succeeded == tracks.size) "added.to.playlist" else "could.not.add.to.playlist"
            )
        }
    }

    fun restoreHiddenLibraryItem(sourceKey: String, onRestored: ((Boolean) -> Unit)? = null) {
        val restoreGateway = exclusionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.restore.failed",
            operation = { restoreGateway.restoreLibraryExclusion(sourceKey) },
            onSuccess = onRestored
        )
    }

    fun restoreHiddenLibraryItemJava(sourceKey: String, onRestored: LibraryExclusionRestoreCallback?) {
        restoreHiddenLibraryItem(sourceKey) { changed -> onRestored?.onRestored(changed) }
    }

    fun restoreAllHiddenLibraryItems(onRestored: ((Boolean) -> Unit)? = null) {
        val restoreGateway = exclusionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.restore.failed",
            operation = { restoreGateway.restoreAllLibraryExclusions() > 0 },
            onSuccess = onRestored
        )
    }

    fun restoreAllHiddenLibraryItemsJava(onRestored: LibraryExclusionRestoreCallback?) {
        restoreAllHiddenLibraryItems { changed -> onRestored?.onRestored(changed) }
    }

    private fun publishInteractionState() {
        val ui = libraryUiState.value
        trackListState.value = trackListState.value.copy(libraryUi = ui)
        libraryGroupsState.value = libraryGroupsState.value.copy(libraryUi = ui)
    }

    private fun toggleFavorite(track: Track?) {
        if (track == null || !TrackIdentity.isUsable(track.id)) {
            return
        }
        val writer = favoriteWriter
        if (writer == null) {
            val nextFavorite = track.id !in favoriteIdsProvider?.favoriteIds().orEmpty()
            gateway?.applyFavorite(track.id, nextFavorite)
            return
        }
        viewModelScope.launch {
            try {
                val (nextFavorite, written) = libraryMutationMutex.withLock {
                    withContext(ioDispatcher) {
                        val next = track.id !in favoriteIdsProvider?.favoriteIds().orEmpty()
                        next to writer.writeFavorite(track, next)
                    }
                }
                if (written) {
                    gateway?.applyFavorite(track.id, nextFavorite)
                } else {
                    gateway?.showStatusKey("library.favorite.failed")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway?.showStatusKey("library.favorite.failed")
            }
        }
    }

    private fun playPlaylist(playlistId: Long) {
        val loader = playlistTrackLoader
        if (loader == null) {
            gateway?.showStatusKey("no.tracks.in.playlist")
            return
        }
        viewModelScope.launch {
            try {
                val tracks = runInterruptible(ioDispatcher) {
                    loader.loadPlaylistTracks(playlistId)
                }
                if (tracks.isEmpty()) {
                    gateway?.showStatusKey("no.tracks.in.playlist")
                } else {
                    gateway?.playTrackList(tracks, 0)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway?.showStatusKey("library.playlist.load.failed")
            }
        }
    }

    fun loadCollections(
        selectedPlaylistId: Long,
        onLoaded: ((LibraryCollectionsResult) -> Unit)? = null
    ) {
        val gateway = collectionGateway ?: return
        val loadId = ++nextCollectionLoadId
        activeCollectionLoadId = loadId
        collectionLoadJob?.cancel()
        collectionLoadJob = viewModelScope.launch {
            try {
                val result = libraryMutationMutex.withLock {
                    runInterruptible(ioDispatcher) {
                        gateway.loadCollections(selectedPlaylistId)
                    }
                }
                if (activeCollectionLoadId == loadId) {
                    onLoaded?.invoke(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeCollectionLoadId == loadId) {
                    this@LibraryViewModel.gateway?.showStatusKey("library.collections.failed")
                }
            } finally {
                if (activeCollectionLoadId == loadId) {
                    activeCollectionLoadId = 0L
                    collectionLoadJob = null
                }
            }
        }
    }

    fun loadCollectionsJava(
        selectedPlaylistId: Long,
        onLoaded: LibraryCollectionsCallback?
    ) {
        loadCollections(selectedPlaylistId) { result -> onLoaded?.onLoaded(result) }
    }

    fun clearPlayHistory(onCleared: ((Int) -> Unit)? = null) {
        val gateway = collectionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.history.clear.failed",
            operation = gateway::clearPlayHistory,
            onSuccess = onCleared
        )
    }

    fun loadLibrary(
        allowCachedFirst: Boolean,
        canScan: Boolean,
        onLoaded: ((LibraryLoadResultUi) -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val loadGateway = importGateway ?: return
        val loadId = ++nextLibraryLoadId
        activeLibraryLoadId = loadId
        libraryLoadJob?.cancel()
        libraryLoadWatchdogJob?.cancel()
        libraryLoadJob = viewModelScope.launch {
            val reportProgress: (LibraryRefreshProgress) -> Unit = { progress ->
                viewModelScope.launch {
                    if (activeLibraryLoadId == loadId) {
                        gateway?.showStatusKey(progress.statusKey())
                    }
                }
            }
            try {
                if (allowCachedFirst) {
                    val cached = runInterruptible(ioDispatcher) { loadGateway.loadCached() }
                    if (activeLibraryLoadId == loadId) {
                        onLoaded?.invoke(cached)
                    }
                }
                if (!canScan) {
                    return@launch
                }
                startLibraryRefreshWatchdog(loadId)
                val fresh = libraryMutationMutex.withLock {
                    runInterruptible(ioDispatcher) {
                        (loadGateway as? LibraryRefreshProgressGateway)
                            ?.refresh(reportProgress)
                            ?: loadGateway.refresh()
                    }
                }
                if (activeLibraryLoadId == loadId) {
                    onLoaded?.invoke(fresh)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (activeLibraryLoadId == loadId) {
                    val statusKey = if (error is SecurityException) {
                        "audio.permission.required"
                    } else {
                        "library.scan.failed"
                    }
                    gateway?.showStatusKey(statusKey)
                    onFailed?.invoke(statusKey)
                }
            } finally {
                if (activeLibraryLoadId == loadId) {
                    activeLibraryLoadId = 0L
                    libraryLoadWatchdogJob?.cancel()
                    libraryLoadWatchdogJob = null
                }
            }
        }
    }

    fun cancelLibraryLoad() {
        activeLibraryLoadId = 0L
        libraryLoadWatchdogJob?.cancel()
        libraryLoadWatchdogJob = null
        val cancelledJob = libraryLoadJob
        libraryLoadJob = null
        cancelledJob?.cancel()
    }

    private fun startLibraryRefreshWatchdog(loadId: Long) {
        libraryLoadWatchdogJob?.cancel()
        libraryLoadWatchdogJob = viewModelScope.launch {
            delay(libraryRefreshDiagnosticTimeoutMs)
            if (activeLibraryLoadId == loadId) {
                gateway?.showStatusKey("library.scan.slow")
            }
        }
    }

    fun loadLibraryJava(
        allowCachedFirst: Boolean,
        canScan: Boolean,
        onLoaded: LibraryLoadCallback?,
        onFailed: LibraryLoadFailedCallback?
    ) {
        loadLibrary(
            allowCachedFirst = allowCachedFirst,
            canScan = canScan,
            onLoaded = { result -> onLoaded?.onLoaded(result) },
            onFailed = { status -> onFailed?.onFailed(status) }
        )
    }

    fun importAudioUris(uris: List<@JvmSuppressWildcards Uri>, onLoaded: ((LibraryLoadResultUi) -> Unit)? = null) {
        val gateway = importGateway ?: return
        launchLibraryMutation("library.import.failed", { gateway.importAudioUris(uris) }, onLoaded)
    }

    fun importAudioUrisJava(uris: List<@JvmSuppressWildcards Uri>, onLoaded: LibraryLoadCallback?) {
        importAudioUris(uris) { result -> onLoaded?.onLoaded(result) }
    }

    fun importAudioTree(treeUri: Uri, onLoaded: ((LibraryLoadResultUi) -> Unit)? = null) {
        val gateway = importGateway ?: return
        launchLibraryMutation("library.import.failed", { gateway.importAudioTree(treeUri) }, onLoaded)
    }

    fun importAudioTreeJava(treeUri: Uri, onLoaded: LibraryLoadCallback?) {
        importAudioTree(treeUri) { result -> onLoaded?.onLoaded(result) }
    }

    fun parseMissingAudioSpecs(onParsed: ((LibraryAudioSpecsResultUi) -> Unit)? = null) {
        val gateway = importGateway ?: return
        if (audioSpecParsingRunning) {
            audioSpecParsingPending = true
            pendingAudioSpecCallback = onParsed
            return
        }
        audioSpecParsingRunning = true
        viewModelScope.launch {
            try {
                val result = libraryMutationMutex.withLock {
                    runInterruptible(ioDispatcher) { gateway.parseMissingAudioSpecs() }
                }
                if (result.updatedCount > 0) {
                    onParsed?.invoke(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                this@LibraryViewModel.gateway?.showStatusKey("audio.specs.failed")
            } finally {
                audioSpecParsingRunning = false
                if (audioSpecParsingPending) {
                    val pendingCallback = pendingAudioSpecCallback
                    audioSpecParsingPending = false
                    pendingAudioSpecCallback = null
                    parseMissingAudioSpecs(pendingCallback)
                }
            }
        }
    }

    fun parseMissingAudioSpecsJava(onParsed: LibraryAudioSpecsParsedCallback?) {
        parseMissingAudioSpecs { result -> onParsed?.onParsed(result) }
    }

    fun importStreamM3u(playlistUri: Uri?, onImported: ((LibraryLoadResultUi) -> Unit)? = null) {
        val gateway = documentGateway ?: return
        launchLibraryMutation("local.m3u.import.failed", { gateway.importStreamM3u(playlistUri) }, onImported)
    }

    fun importStreamM3uJava(playlistUri: Uri?, onImported: LibraryLoadCallback?) {
        importStreamM3u(playlistUri) { result -> onImported?.onLoaded(result) }
    }

    fun importPlaylistM3u(
        playlistUri: Uri?,
        onImported: ((LibraryPlaylistImportResultUi) -> Unit)? = null
    ) {
        val gateway = documentGateway ?: return
        launchLibraryMutation("playlist.import.failed", { gateway.importPlaylistM3u(playlistUri) }, onImported)
    }

    fun importPlaylistM3uJava(playlistUri: Uri?, onImported: LibraryPlaylistImportCallback?) {
        importPlaylistM3u(playlistUri) { result -> onImported?.onImported(result) }
    }

    fun exportPlaylist(
        exportUri: Uri?,
        playlistId: Long,
        playlistName: String,
        onExported: ((Boolean) -> Unit)? = null
    ) {
        val gateway = documentGateway ?: return
        launchLibraryMutation(
            "playlist.export.failed",
            { gateway.exportPlaylist(exportUri, playlistId, playlistName) },
            onExported
        )
    }

    fun exportPlaylistJava(
        exportUri: Uri?,
        playlistId: Long,
        playlistName: String
    ) {
        exportPlaylist(exportUri, playlistId, playlistName)
    }

    fun syncSearchQuery(query: String?) {
        val normalized = query.orEmpty()
        if (libraryUiState.value.query == normalized) return
        libraryUiState.value = libraryUiState.value.copy(
            query = normalized,
            revealedRowKey = null,
            selectedTrackKeys = emptySet(),
            selectedGroupKeys = emptySet()
        )
        publishInteractionState()
    }

    private fun <T> launchLibraryMutation(
        failureStatusKey: String,
        operation: () -> T,
        onSuccess: ((T) -> Unit)?
    ) {
        viewModelScope.launch {
            try {
                val result = libraryMutationMutex.withLock {
                    withContext(ioDispatcher) { operation() }
                }
                onSuccess?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway?.showStatusKey(failureStatusKey)
            }
        }
    }

    fun addToDefaultPlaylist(
        track: Track?,
        onAdded: ((LibraryDefaultPlaylistAddResultUi) -> Unit)? = null
    ) {
        if (track == null) {
            return
        }
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.addToDefaultPlaylist(track) },
            onSuccess = { result ->
                if (result == null) {
                    this.gateway?.showStatusKey("library.playlist.action.failed")
                } else {
                    onAdded?.invoke(result)
                }
            }
        )
    }

    fun addToDefaultPlaylistJava(track: Track?, onAdded: LibraryDefaultPlaylistTrackAddedCallback?) {
        addToDefaultPlaylist(track) { result -> onAdded?.onAdded(result.playlistId, result.added) }
    }

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.createPlaylist(name) },
            onSuccess = { playlistId ->
                if (playlistId >= 0L) {
                    onCreated?.invoke(playlistId)
                } else {
                    this.gateway?.showStatusKey("library.playlist.action.failed")
                }
            }
        )
    }

    fun createPlaylistJava(name: String, onCreated: LibraryPlaylistCreatedCallback?) {
        createPlaylist(name) { playlistId -> onCreated?.onCreated(playlistId) }
    }

    fun renamePlaylist(playlistId: Long, name: String, onRenamed: ((Boolean) -> Unit)? = null) {
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.renamePlaylist(playlistId, name) },
            onSuccess = onRenamed
        )
    }

    fun renamePlaylistJava(playlistId: Long, name: String, onRenamed: LibraryPlaylistRenamedCallback?) {
        renamePlaylist(playlistId, name) { renamed -> onRenamed?.onRenamed(playlistId, renamed) }
    }

    fun deletePlaylist(
        playlistId: Long,
        name: String,
        onDeleted: ((Boolean) -> Unit)? = null
    ) {
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.deletePlaylist(playlistId) },
            onSuccess = onDeleted
        )
    }

    fun deletePlaylistJava(playlistId: Long, name: String, onDeleted: LibraryPlaylistDeletedCallback?) {
        deletePlaylist(playlistId, name) { deleted -> onDeleted?.onDeleted(playlistId, name, deleted) }
    }

    fun removeSelectedPlaylistTrack(
        playlistId: Long,
        track: Track?,
        onRemoved: ((Track) -> Unit)? = null
    ) {
        if (playlistId < 0L || track == null) {
            return
        }
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.removeTrackFromPlaylist(playlistId, track) },
            onSuccess = { removed -> if (removed) onRemoved?.invoke(track) }
        )
    }

    fun removeSelectedPlaylistTrackJava(
        playlistId: Long,
        track: Track?,
        onRemoved: LibrarySelectedPlaylistTrackRemovedCallback?
    ) {
        removeSelectedPlaylistTrack(playlistId, track) { removedTrack ->
            onRemoved?.onRemoved(playlistId, removedTrack)
        }
    }

    fun moveSelectedPlaylistTrack(
        playlistId: Long,
        track: Track?,
        trackIndex: Int,
        direction: Int,
        onMoved: ((Boolean) -> Unit)? = null
    ) {
        if (playlistId < 0L || track == null) {
            return
        }
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.movePlaylistTrack(playlistId, track, trackIndex, direction) },
            onSuccess = onMoved
        )
    }

    fun moveSelectedPlaylistTrackJava(
        playlistId: Long,
        track: Track?,
        trackIndex: Int,
        direction: Int,
        onMoved: LibrarySelectedPlaylistTrackMovedCallback?
    ) {
        moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction) { moved ->
            if (track != null) {
                onMoved?.onMoved(playlistId, track, direction, moved)
            }
        }
    }

    fun addTrackToPlaylist(
        playlistId: Long,
        trackId: Long,
        onAdded: ((Boolean) -> Unit)? = null
    ) {
        val gateway = playlistActionGateway ?: return
        launchLibraryMutation(
            failureStatusKey = "library.playlist.action.failed",
            operation = { gateway.addTrackToPlaylist(playlistId, trackId) },
            onSuccess = onAdded
        )
    }

    fun addTrackToPlaylistJava(playlistId: Long, trackId: Long, onAdded: LibraryTrackAddedToPlaylistCallback?) {
        addTrackToPlaylist(playlistId, trackId) { added -> onAdded?.onAdded(playlistId, added) }
    }

    fun defaultPlaylistAddPresentation(
        added: Boolean,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(
            status = text(
                languageMode,
                if (added) "added.to.playlist" else "could.not.add.to.playlist"
            )
        )

    fun playlistCreatedPresentation(languageMode: String): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(status = text(languageMode, "playlist.created"))

    fun playlistRenamedPresentation(
        renamed: Boolean,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(
            status = text(
                languageMode,
                if (renamed) "playlist.renamed" else "playlist.rename.failed"
            )
        )

    fun playlistDeletedPresentation(
        deletedName: String?,
        deleted: Boolean,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(
            status = if (deleted) {
                text(languageMode, "deleted.playlist.prefix") + deletedName.orEmpty()
            } else {
                text(languageMode, "could.not.delete.playlist")
            }
        )

    fun selectedPlaylistTrackRemovedPresentation(
        track: Track?,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(
            status = text(languageMode, "removed.from.playlist.prefix") + (track?.title ?: "")
        )

    fun selectedPlaylistTrackMovedPresentation(
        track: Track?,
        direction: Int,
        moved: Boolean,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        LibraryPlaylistActionPresentation(
            status = if (moved) {
                text(
                    languageMode,
                    if (direction < 0) "moved.up.prefix" else "moved.down.prefix"
                ) + (track?.title ?: "")
            } else {
                text(languageMode, "move.failed")
            }
        )

    fun trackAddedToPlaylistPresentation(
        added: Boolean,
        languageMode: String
    ): LibraryPlaylistActionPresentation =
        defaultPlaylistAddPresentation(added, languageMode)

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
        labels: TrackListLabels
    ) {
        trackListState.value = trackListState.value.copy(
            title = title,
            // TrackListRenderController owns these freshly built lists. Retaining them avoids a
            // second O(n) copy immediately after a large background row build.
            rows = rows,
            footerAlbums = footerAlbums,
            actions = actions,
            headerMetrics = headerMetrics,
            headerActions = headerActions,
            emptyText = emptyText,
            modeActions = modeActions,
            labels = labels,
            libraryUi = libraryUiState.value
        )
    }

    fun clearTrackList() {
        trackListState.value = LibraryTrackListDestinationState()
    }

    fun updateLibraryGroups(title: String, rows: List<LibraryGroupUiState>) {
        libraryGroupsState.value = libraryGroupsState.value.copy(
            title = title,
            rows = rows.toList(),
            libraryUi = libraryUiState.value
        )
    }

    fun clearLibraryGroups() {
        libraryGroupsState.value = LibraryGroupsDestinationState()
    }

    fun updateLibraryGroupsChrome(
        actions: List<LibraryGroupActions>,
        emptyText: String,
        modeActions: List<TrackListModeAction>
    ) {
        libraryGroupsState.value = libraryGroupsState.value.copy(
            actions = actions.toList(),
            emptyText = emptyText,
            modeActions = modeActions.toList()
        )
    }

    fun updateLibraryGroupsChrome(state: LibraryGroupsDestinationState) {
        // Chrome publish carries only chrome fields; title/rows are blank placeholders. Preserve
        // the content set by updateLibraryGroups(...) so the group list does not get wiped.
        libraryGroupsState.value = libraryGroupsState.value.copy(
            actions = state.actions.toList(),
            emptyText = state.emptyText,
            modeActions = state.modeActions.toList()
        )
    }

    private fun text(languageMode: String, key: String): String =
        AppLanguage.text(languageMode, key)

    private fun LibraryRefreshProgress.statusKey(): String = when (phase) {
        LibraryRefreshPhase.CHECKING -> "library.scan.checking"
        LibraryRefreshPhase.SCANNING -> "library.scan.scanning"
        LibraryRefreshPhase.REPLACING -> "library.scan.replacing"
        LibraryRefreshPhase.RELOADING -> "library.scan.reloading"
    }

    private companion object {
        const val DEFAULT_LIBRARY_REFRESH_DIAGNOSTIC_TIMEOUT_MS = 45_000L
    }
}
