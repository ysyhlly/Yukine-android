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
    fun applyFavorites(trackIds: Set<Long>, favorite: Boolean) {
        trackIds.forEach { trackId -> applyFavorite(trackId, favorite) }
    }
    fun addToPlaylist(track: Track)
    fun changeGroupMode(mode: String)
    fun openGroup(key: String, title: String)
    fun openPlaylist(playlistId: Long, title: String)
    fun backFromGroup()
    fun search(query: String)
    fun importFiles()
    fun scanLibrary()
    fun syncWebDavLibrary() = Unit
    fun setAutomaticSyncEnabled(enabled: Boolean) = Unit
    fun requestDeleteTracks(tracks: List<Track>) = Unit
    fun downloadTracks(tracks: List<Track>) = Unit
    fun openDedupCenter() = Unit
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
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val longUnplayedTracks: List<Track> = emptyList()
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
    val status: String = "Library updated",
    val scanned: Boolean = true
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
interface LibraryRefreshProgressGateway {
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
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    preparationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    libraryRefreshDiagnosticTimeoutMs: Long = DEFAULT_LIBRARY_REFRESH_DIAGNOSTIC_TIMEOUT_MS
) : ViewModel() {
    private var gateway: LibraryGateway? = null
    private val mutations = LibraryMutationContext(viewModelScope, ioDispatcher) { gateway }
    val data = LibraryDataStateOwner(viewModelScope, preparationDispatcher)
    internal val favorites = LibraryFavoriteStateOwner(viewModelScope, mutations, data) { gateway }
    val playlists = LibraryPlaylistStateOwner(viewModelScope, mutations) { gateway }
    val loading = LibraryLoadStateOwner(
        viewModelScope,
        mutations,
        { gateway },
        libraryRefreshDiagnosticTimeoutMs
    )
    val presentation = LibraryPresentationStateOwner({ gateway }, favorites, playlists)

    val trackList: StateFlow<LibraryTrackListDestinationState> = presentation.trackList
    val libraryGroups: StateFlow<LibraryGroupsDestinationState> = presentation.groups
    val libraryUi: StateFlow<LibraryUiState> = presentation.ui
    val library: StateFlow<LibraryStoreState> = data.state
    val favoritePendingTrackIds: StateFlow<Set<Long>> = data.favoritePendingTrackIds

    @JvmName("dataOwner")
    fun dataOwner(): LibraryDataStateOwner = data

    @JvmName("playlistOwner")
    fun playlistOwner(): LibraryPlaylistStateOwner = playlists

    @JvmName("loadOwner")
    fun loadOwner(): LibraryLoadStateOwner = loading

    @JvmName("presentationOwner")
    fun presentationOwner(): LibraryPresentationStateOwner = presentation

    fun bindGateway(nextGateway: LibraryGateway?) {
        gateway = nextGateway
    }

    fun bindPlaylistTrackLoader(nextLoader: LibraryPlaylistTrackLoader?) {
        playlists.bindTrackLoader(nextLoader)
    }

    fun bindFavoriteWriter(nextWriter: LibraryFavoriteWriter?) {
        favorites.bindWriter(nextWriter)
    }

    fun bindFavoriteIdsProvider(nextProvider: LibraryFavoriteIdsProvider?) {
        favorites.bindIdsProvider(nextProvider)
    }

    fun bindCollectionGateway(nextGateway: LibraryCollectionGateway?) {
        playlists.bindCollectionGateway(nextGateway)
    }

    fun bindImportGateway(nextGateway: LibraryImportGateway?) {
        loading.bindImportGateway(nextGateway)
    }

    fun bindDocumentGateway(nextGateway: LibraryDocumentGateway?) {
        loading.bindDocumentGateway(nextGateway)
    }

    fun bindPlaylistActionGateway(nextGateway: LibraryPlaylistActionGateway?) {
        playlists.bindActionGateway(nextGateway)
    }

    fun bindExclusionGateway(nextGateway: LibraryExclusionGateway?) {
        loading.bindExclusionGateway(nextGateway)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.PlayTrackList -> gateway?.playTrackList(event.tracks, event.index)
            is LibraryEvent.PlayPlaylist -> playlists.play(event.playlistId)
            is LibraryEvent.ToggleFavorite -> favorites.toggle(event.track)
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

    private companion object {
        const val DEFAULT_LIBRARY_REFRESH_DIAGNOSTIC_TIMEOUT_MS = 45_000L
    }
}
