package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NetworkActionUseCases(
    val testWebDavSourceUseCase: TestWebDavSourceUseCase,
    val syncWebDavSourceUseCase: SyncWebDavSourceUseCase,
    val syncAllWebDavSourcesUseCase: SyncAllWebDavSourcesUseCase,
    val addStreamUrlUseCase: AddStreamUrlUseCase,
    val updateStreamUrlUseCase: UpdateStreamUrlUseCase,
    val importStreamPlaylistUseCase: ImportStreamPlaylistUseCase,
    val deleteAllStreamsUseCase: DeleteAllStreamsUseCase,
    val deleteNetworkTrackUseCase: DeleteNetworkTrackUseCase,
    val deleteNetworkTracksUseCase: DeleteNetworkTracksUseCase,
    val deleteRemoteSourceUseCase: DeleteRemoteSourceUseCase,
    val saveWebDavSourceUseCase: SaveWebDavSourceUseCase
)

internal class NetworkActionsViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val networkDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(), NetworkOperationSink {
    interface Listener {
        fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onStreamUpdated(
            oldTrackId: Long,
            updated: Track?,
            cached: List<Track>,
            favorites: Set<Long>,
            status: String
        )

        fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String)

        fun onRemoteSourceTested(status: String)

        fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String)
    }

    private var useCases: NetworkActionUseCases? = null
    private var listener: Listener? = null

    fun bindUseCases(nextUseCases: NetworkActionUseCases?) {
        useCases = nextUseCases
    }

    fun bindListener(nextListener: Listener?) {
        listener = nextListener
    }

    override fun addStreamUrl(title: String, url: String) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                actions.addStreamUrlUseCase.execute(title, url)
            }
            val status = if (result.track == null) "Could not add stream URL" else "Library updated"
            listener?.onStreamAdded(result.snapshot.cached, result.snapshot.favorites, status)
        }
    }

    override fun updateStreamUrl(oldTrack: Track?, title: String, url: String) {
        if (oldTrack == null) {
            return
        }
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                actions.updateStreamUrlUseCase.execute(oldTrack, title, url)
            } ?: return@launch
            val status = if (result.updated == null) "Could not update stream URL" else "Library updated"
            listener?.onStreamUpdated(
                oldTrack.id,
                result.updated,
                result.snapshot.cached,
                result.snapshot.favorites,
                status
            )
        }
    }

    override fun importM3uPlaylist(url: String) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(networkDispatcher) {
                actions.importStreamPlaylistUseCase.execute(url)
            }
            val status = if (result.importResult == null || result.importResult.isEmpty) {
                "No streams imported"
            } else {
                M3uDocumentHelper.streamImportStatus("Imported streams", result.importResult)
            }
            listener?.onStreamPlaylistImported(result.snapshot.cached, result.snapshot.favorites, status)
        }
    }

    override fun deleteAllStreams() {
        val actions = useCases ?: return
        viewModelScope.launch {
            val snapshot = withContext(ioDispatcher) {
                actions.deleteAllStreamsUseCase.execute()
            }
            listener?.onAllStreamsDeleted(snapshot.cached, snapshot.favorites, "Library updated")
        }
    }

    override fun deleteTrack(trackId: Long, status: String) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val snapshot = withContext(ioDispatcher) {
                actions.deleteNetworkTrackUseCase.execute(trackId)
            }
            listener?.onTrackDeleted(snapshot.cached, snapshot.favorites, status)
        }
    }

    override fun deleteTracks(trackIds: List<Long>, status: String) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val snapshot = withContext(ioDispatcher) {
                actions.deleteNetworkTracksUseCase.execute(trackIds)
            }
            listener?.onTrackDeleted(snapshot.cached, snapshot.favorites, status)
        }
    }

    override fun deleteRemoteSource(sourceId: Long) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val snapshot = withContext(ioDispatcher) {
                actions.deleteRemoteSourceUseCase.execute(sourceId)
            }
            listener?.onRemoteSourceDeleted(snapshot.cached, snapshot.favorites, "Library updated")
        }
    }

    override fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                actions.saveWebDavSourceUseCase.execute(sourceId, name, baseUrl, username, password, rootPath)
            }
            val status = if (result.savedSourceId > 0L) {
                if (sourceId > 0L) "Updated WebDAV source" else "Added WebDAV source"
            } else {
                "Could not save WebDAV source"
            }
            listener?.onWebDavSourceSaved(sourceId, result.snapshot.cached, result.snapshot.favorites, status)
        }
    }

    override fun testRemoteSource(sourceId: Long) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val status = withContext(networkDispatcher) {
                actions.testWebDavSourceUseCase.execute(sourceId)
            }
            listener?.onRemoteSourceTested(status)
        }
    }

    override fun syncRemoteSource(sourceId: Long, sourceName: String) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(networkDispatcher) {
                actions.syncWebDavSourceUseCase.execute(sourceId, sourceName)
            }
            listener?.onRemoteSourceSynced(result.cached, result.favorites, result.status)
        }
    }

    override fun syncAllWebDavSources(sourceIds: List<Long>) {
        val actions = useCases ?: return
        viewModelScope.launch {
            val result = withContext(networkDispatcher) {
                actions.syncAllWebDavSourcesUseCase.execute(sourceIds)
            }
            listener?.onAllWebDavSourcesSynced(result.cached, result.favorites, result.status)
        }
    }
}
