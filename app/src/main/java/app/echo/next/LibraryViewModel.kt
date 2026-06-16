package app.echo.next

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.echo.next.model.Playlist
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.model.TrackPlayRecord
import app.echo.next.ui.LibraryGroupUiState
import app.echo.next.ui.TrackRowUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
}

data class LibraryUiState(
    val mode: String = LibraryGrouping.SONGS,
    val selectedGroupKey: String = "",
    val selectedGroupTitle: String = "",
    val searchQuery: String = "",
    val totalTracks: Int = 0,
    val visibleTracks: Int = 0,
    val favorites: Int = 0,
    val playlists: Int = 0,
    val selectedPlaylistId: Long = -1L
)

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
}

fun interface LibraryPlaylistTrackLoader {
    fun loadPlaylistTracks(playlistId: Long): List<Track>
}

fun interface LibraryFavoriteWriter {
    fun writeFavorite(track: Track, favorite: Boolean): Boolean
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

    fun setFavorite(trackId: Long, favorite: Boolean)
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

data class LibraryPlaylistImportResultUi(
    val playlistId: Long = -1L,
    val tracks: List<Track> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val status: String = ""
)

fun interface LibraryPlaylistImportCallback {
    fun onImported(result: LibraryPlaylistImportResultUi)
}

fun interface LibraryPlaylistExportCallback {
    fun onExported(exported: Boolean)
}

interface LibraryDocumentGateway {
    fun importStreamM3u(playlistUri: Uri?): LibraryLoadResultUi

    fun importPlaylistM3u(playlistUri: Uri?): LibraryPlaylistImportResultUi

    fun exportPlaylist(exportUri: Uri?, playlistId: Long, playlistName: String): Boolean
}

data class LibraryDefaultPlaylistAddResultUi(
    val playlistId: Long = -1L,
    val added: Boolean = false
)

data class LibraryPlaylistActionPresentation(
    val status: String = ""
)

interface LibraryPlaylistActionGateway {
    fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi?

    fun createPlaylist(name: String): Long

    fun renamePlaylist(playlistId: Long, name: String): Boolean

    fun deletePlaylist(playlistId: Long): Boolean

    fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean

    fun movePlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int): Boolean

    fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean
}

fun interface LibraryCollectionsCallback {
    fun onLoaded(result: LibraryCollectionsResult)
}

fun interface LibraryPlayHistoryClearedCallback {
    fun onCleared(removed: Int)
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val trackListState = MutableStateFlow(MainActivityTrackListUiState())
    val trackList: StateFlow<MainActivityTrackListUiState> = trackListState.asStateFlow()

    private val libraryGroupsState = MutableStateFlow(MainActivityLibraryGroupsUiState())
    val libraryGroups: StateFlow<MainActivityLibraryGroupsUiState> = libraryGroupsState.asStateFlow()

    private var gateway: LibraryGateway? = null
    private var playlistTrackLoader: LibraryPlaylistTrackLoader? = null
    private var favoriteWriter: LibraryFavoriteWriter? = null
    private var collectionGateway: LibraryCollectionGateway? = null
    private var importGateway: LibraryImportGateway? = null
    private var documentGateway: LibraryDocumentGateway? = null
    private var playlistActionGateway: LibraryPlaylistActionGateway? = null
    private var audioSpecParsingRunning = false
    private var favoriteTrackIds: Set<Long> = emptySet()

    fun bindGateway(nextGateway: LibraryGateway?) {
        gateway = nextGateway
    }

    fun bindPlaylistTrackLoader(nextLoader: LibraryPlaylistTrackLoader?) {
        playlistTrackLoader = nextLoader
    }

    fun bindFavoriteWriter(nextWriter: LibraryFavoriteWriter?) {
        favoriteWriter = nextWriter
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

    fun updateState(
        routeState: MainActivityRouteState?,
        libraryState: MainActivityLibraryState?
    ) {
        val route = routeState ?: MainActivityRouteState()
        val library = libraryState ?: MainActivityLibraryState()
        favoriteTrackIds = library.favoriteTrackIds.toSet()
        _uiState.value = LibraryUiState(
            mode = route.libraryMode,
            selectedGroupKey = route.selectedLibraryGroupKey,
            selectedGroupTitle = route.selectedLibraryGroupTitle,
            searchQuery = route.searchQuery,
            totalTracks = library.allTracks.size,
            visibleTracks = library.visibleTracks.size,
            favorites = library.favoriteTracks.size,
            playlists = library.playlists.size,
            selectedPlaylistId = route.selectedPlaylistId
        )
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
            is LibraryEvent.Search -> {
                _uiState.value = _uiState.value.copy(searchQuery = event.query)
                gateway?.search(event.query)
            }
            LibraryEvent.ImportFiles -> gateway?.importFiles()
            LibraryEvent.ScanLibrary -> gateway?.scanLibrary()
        }
    }

    private fun toggleFavorite(track: Track?) {
        if (track == null || track.id < 0L) {
            return
        }
        val nextFavorite = !favoriteTrackIds.contains(track.id)
        favoriteTrackIds = if (nextFavorite) {
            favoriteTrackIds + track.id
        } else {
            favoriteTrackIds - track.id
        }
        gateway?.applyFavorite(track.id, nextFavorite)
        val writer = favoriteWriter ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                writer.writeFavorite(track, nextFavorite)
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
            val tracks = withContext(ioDispatcher) {
                loader.loadPlaylistTracks(playlistId)
            }
            if (tracks.isEmpty()) {
                gateway?.showStatusKey("no.tracks.in.playlist")
            } else {
                gateway?.playTrackList(tracks, 0)
            }
        }
    }

    fun loadCollections(
        selectedPlaylistId: Long,
        onLoaded: ((LibraryCollectionsResult) -> Unit)? = null
    ) {
        val gateway = collectionGateway ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                gateway.loadCollections(selectedPlaylistId)
            }
            onLoaded?.invoke(result)
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
        viewModelScope.launch {
            val removed = withContext(ioDispatcher) {
                gateway.clearPlayHistory()
            }
            onCleared?.invoke(removed)
        }
    }

    fun clearPlayHistoryJava(onCleared: LibraryPlayHistoryClearedCallback?) {
        clearPlayHistory { removed -> onCleared?.onCleared(removed) }
    }

    fun saveLibraryFavorite(trackId: Long, favorite: Boolean, onSaved: (() -> Unit)? = null) {
        if (trackId < 0L) {
            return
        }
        val gateway = collectionGateway ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                gateway.setFavorite(trackId, favorite)
            }
            onSaved?.invoke()
        }
    }

    fun saveLibraryFavoriteJava(trackId: Long, favorite: Boolean, onSaved: Runnable?) {
        saveLibraryFavorite(trackId, favorite) { onSaved?.run() }
    }
    fun loadLibrary(
        allowCachedFirst: Boolean,
        canScan: Boolean,
        onLoaded: ((LibraryLoadResultUi) -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val gateway = importGateway ?: return
        viewModelScope.launch {
            if (allowCachedFirst) {
                val cached = withContext(ioDispatcher) { gateway.loadCached() }
                onLoaded?.invoke(cached)
            }
            if (!canScan) {
                return@launch
            }
            runCatching {
                withContext(ioDispatcher) { gateway.refresh() }
            }.onSuccess { fresh ->
                onLoaded?.invoke(fresh)
            }.onFailure { error ->
                if (error is SecurityException) {
                    onFailed?.invoke("Status")
                } else {
                    onFailed?.invoke(error.message ?: "Status")
                }
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
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { gateway.importAudioUris(uris) }
            onLoaded?.invoke(result)
        }
    }

    fun importAudioUrisJava(uris: List<@JvmSuppressWildcards Uri>, onLoaded: LibraryLoadCallback?) {
        importAudioUris(uris) { result -> onLoaded?.onLoaded(result) }
    }

    fun importAudioTree(treeUri: Uri, onLoaded: ((LibraryLoadResultUi) -> Unit)? = null) {
        val gateway = importGateway ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { gateway.importAudioTree(treeUri) }
            onLoaded?.invoke(result)
        }
    }

    fun importAudioTreeJava(treeUri: Uri, onLoaded: LibraryLoadCallback?) {
        importAudioTree(treeUri) { result -> onLoaded?.onLoaded(result) }
    }

    fun parseMissingAudioSpecs(onParsed: ((LibraryAudioSpecsResultUi) -> Unit)? = null) {
        val gateway = importGateway ?: return
        if (audioSpecParsingRunning) {
            return
        }
        audioSpecParsingRunning = true
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { gateway.parseMissingAudioSpecs() }
                if (result.updatedCount > 0) {
                    onParsed?.invoke(result)
                }
            } finally {
                audioSpecParsingRunning = false
            }
        }
    }

    fun parseMissingAudioSpecsJava(onParsed: LibraryAudioSpecsParsedCallback?) {
        parseMissingAudioSpecs { result -> onParsed?.onParsed(result) }
    }

    fun importStreamM3u(playlistUri: Uri?, onImported: ((LibraryLoadResultUi) -> Unit)? = null) {
        val gateway = documentGateway ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { gateway.importStreamM3u(playlistUri) }
            onImported?.invoke(result)
        }
    }

    fun importStreamM3uJava(playlistUri: Uri?, onImported: LibraryLoadCallback?) {
        importStreamM3u(playlistUri) { result -> onImported?.onLoaded(result) }
    }

    fun importPlaylistM3u(
        playlistUri: Uri?,
        onImported: ((LibraryPlaylistImportResultUi) -> Unit)? = null
    ) {
        val gateway = documentGateway ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { gateway.importPlaylistM3u(playlistUri) }
            onImported?.invoke(result)
        }
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
        viewModelScope.launch {
            val exported = withContext(ioDispatcher) {
                gateway.exportPlaylist(exportUri, playlistId, playlistName)
            }
            onExported?.invoke(exported)
        }
    }

    fun exportPlaylistJava(
        exportUri: Uri?,
        playlistId: Long,
        playlistName: String,
        onExported: LibraryPlaylistExportCallback?
    ) {
        exportPlaylist(exportUri, playlistId, playlistName) { exported -> onExported?.onExported(exported) }
    }

    fun addToDefaultPlaylist(
        track: Track?,
        onAdded: ((LibraryDefaultPlaylistAddResultUi) -> Unit)? = null
    ) {
        if (track == null) {
            return
        }
        val gateway = playlistActionGateway ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                gateway.addToDefaultPlaylist(track)
            } ?: return@launch
            onAdded?.invoke(result)
        }
    }

    fun addToDefaultPlaylistJava(track: Track?, onAdded: LibraryDefaultPlaylistTrackAddedCallback?) {
        addToDefaultPlaylist(track) { result -> onAdded?.onAdded(result.playlistId, result.added) }
    }

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        val gateway = playlistActionGateway ?: return
        viewModelScope.launch {
            val playlistId = withContext(ioDispatcher) {
                gateway.createPlaylist(name)
            }
            onCreated?.invoke(playlistId)
        }
    }

    fun createPlaylistJava(name: String, onCreated: LibraryPlaylistCreatedCallback?) {
        createPlaylist(name) { playlistId -> onCreated?.onCreated(playlistId) }
    }

    fun renamePlaylist(playlistId: Long, name: String, onRenamed: ((Boolean) -> Unit)? = null) {
        val gateway = playlistActionGateway ?: return
        viewModelScope.launch {
            val renamed = withContext(ioDispatcher) {
                gateway.renamePlaylist(playlistId, name)
            }
            onRenamed?.invoke(renamed)
        }
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
        viewModelScope.launch {
            val deleted = withContext(ioDispatcher) {
                gateway.deletePlaylist(playlistId)
            }
            onDeleted?.invoke(deleted)
        }
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
        viewModelScope.launch {
            val removed = withContext(ioDispatcher) {
                gateway.removeTrackFromPlaylist(playlistId, track)
            }
            if (removed) {
                onRemoved?.invoke(track)
            }
        }
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
        viewModelScope.launch {
            val moved = withContext(ioDispatcher) {
                gateway.movePlaylistTrack(playlistId, track, trackIndex, direction)
            }
            onMoved?.invoke(moved)
        }
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
        viewModelScope.launch {
            val added = withContext(ioDispatcher) {
                gateway.addTrackToPlaylist(playlistId, trackId)
            }
            onAdded?.invoke(added)
        }
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

    fun updateTrackList(title: String, rows: List<TrackRowUiState>) {
        trackListState.value = MainActivityTrackListUiState(title, rows.toList())
    }

    fun updateLibraryGroups(title: String, rows: List<LibraryGroupUiState>) {
        libraryGroupsState.value = MainActivityLibraryGroupsUiState(title, rows.toList())
    }

    private fun text(languageMode: String, key: String): String =
        AppLanguage.text(languageMode, key)
}
