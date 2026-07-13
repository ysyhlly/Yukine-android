package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns account playlists, imports, sync, export presentation and local playlist links. */
class StreamingPlaylistStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val stateOwner: StreamingFeatureStateOwner,
    private val repository: () -> StreamingRepository,
    private val ioDispatcher: () -> CoroutineDispatcher
) {
    private var localPlaylistOperations: StreamingLocalPlaylistOperations? = null
    private val playlistDataCoordinator = StreamingPlaylistDataCoordinator(
        repositoryProvider = repository,
        localOperationsProvider = { localPlaylistOperations },
        ioDispatcherProvider = ioDispatcher
    )

    fun bindLocalPlaylistOperations(operations: StreamingLocalPlaylistOperations?) {
        localPlaylistOperations = operations
    }

    fun streamingPlaylistLoadedDialogTitle(languageMode: String): String =
        text(languageMode, "streaming.playlist.load.success.title")

    fun prepareStreamingPlaylistImportDialogState(languageMode: String): StreamingPlaylistImportDialogState =
        prepareStreamingPlaylistImportDialogState(languageMode, stateOwner.value.selectedProvider)

    fun prepareStreamingPlaylistImportDialogState(
        languageMode: String,
        provider: StreamingProviderName?
    ): StreamingPlaylistImportDialogState {
        val luoxueSelected = provider == StreamingProviderName.LUOXUE
        return StreamingPlaylistImportDialogState(
            title = if (luoxueSelected) {
                text(languageMode, "streaming.lx.import.source")
            } else {
                text(languageMode, "streaming.import.playlist.from")
            },
            hint = if (luoxueSelected) {
                text(languageMode, "streaming.lx.import.hint")
            } else {
                text(languageMode, "streaming.paste.playlist.link")
            }
        )
    }

    fun streamingImportProviderPickerState(
        providers: List<StreamingProviderDescriptor>?,
        requireSearch: Boolean = true
    ): StreamingProviderPickerState {
        val selectable = providers.orEmpty()
            .filterNotNull()
            .filter { !requireSearch || it.capabilities.supportsSearch }
            .filter { it.name != StreamingProviderName.MOCK }
        return StreamingProviderPickerState(
            providers = selectable,
            labels = selectable.map { it.displayName }.toTypedArray()
        )
    }

    fun prepareStreamingImportProviderPickerRequest(
        providers: List<StreamingProviderDescriptor>?,
        requireSearch: Boolean = true,
        languageMode: String
    ): StreamingProviderPickerRequest {
        val pickerState = streamingImportProviderPickerState(providers, requireSearch)
        return StreamingProviderPickerRequest(
            pickerState = pickerState,
            title = text(languageMode, "choose.streaming.provider"),
            emptyStatus = text(languageMode, "streaming.no.providers"),
            valid = pickerState.providers.isNotEmpty()
        )
    }

    fun streamingPlaylistImportStatus(
        summary: StreamingPlaylistImportSummary?
    ): StreamingPlaylistImportStatus {
        if (summary == null) {
            return StreamingPlaylistImportStatus()
        }
        return StreamingPlaylistImportStatus(
            matchedCount = summary.matchedTracks.size,
            totalRequested = summary.totalRequested,
            unresolvedCount = summary.unresolvedTracks.size
        )
    }

    fun prepareStreamingPlaylistExportPresentation(
        importStatus: StreamingPlaylistImportStatus?,
        languageMode: String
    ): StreamingPlaylistExportPresentation {
        if (importStatus == null) {
            return StreamingPlaylistExportPresentation()
        }
        var status = text(languageMode, "streaming.import.matched.prefix") +
            importStatus.matchedCount +
            " / " +
            importStatus.totalRequested
        if (importStatus.unresolvedCount > 0) {
            status += " (" +
                importStatus.unresolvedCount +
                text(languageMode, "streaming.import.unresolved.suffix") +
                ")"
        }
        return StreamingPlaylistExportPresentation(status = status)
    }

    fun prepareStreamingPlaylistExportRequest(
        playlistName: String?,
        tracks: List<Track>?,
        languageMode: String
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (playlistName.isNullOrBlank() || normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text(languageMode, "streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = playlistName,
            tracks = normalizedTracks,
            status = text(languageMode, "streaming.import.matched.prefix") + "...",
            valid = true
        )
    }

    fun prepareStreamingFavoritesExportRequest(
        tracks: List<Track>?,
        languageMode: String
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text(languageMode, "streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = text(languageMode, "favorites"),
            tracks = normalizedTracks,
            status = text(languageMode, "streaming.import.matched.prefix") + "...",
            valid = true
        )
    }

    fun prepareStreamingPlaylistImportTarget(
        linkOrId: String?,
        fallbackProvider: StreamingProviderName?
    ): StreamingPlaylistImportTarget {
        val parsed = StreamingPlaylistLinkParser.parse(
            linkOrId,
            fallbackProvider ?: stateOwner.value.selectedProvider
        )
        return if (parsed == null) {
            StreamingPlaylistImportTarget(invalid = true)
        } else {
            StreamingPlaylistImportTarget(
                provider = parsed.provider,
                providerPlaylistId = parsed.providerPlaylistId
            )
        }
    }

    fun prepareStreamingPlaylistImportStartRequest(
        linkOrId: String?,
        fallbackProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingPlaylistImportStartRequest {
        val target = prepareStreamingPlaylistImportTarget(linkOrId, fallbackProvider)
        val provider = target.provider
        val invalid = target.invalid || provider == null || target.providerPlaylistId.isEmpty()
        if (invalid) {
            return StreamingPlaylistImportStartRequest(
                invalidStatus = text(languageMode, "streaming.playlist.link.invalid")
            )
        }
        return StreamingPlaylistImportStartRequest(
            provider = provider,
            providerPlaylistId = target.providerPlaylistId,
            invalidStatus = text(languageMode, "streaming.playlist.link.invalid"),
            resolvingStatus = text(languageMode, "streaming.resolving"),
            valid = true
        )
    }

    fun prepareStreamingLoginPlaylistRequest(
        provider: StreamingProviderName,
        languageMode: String
    ): StreamingLoginPlaylistRequest {
        val displayName = streamingProviderDisplayName(provider)
        val playlistName =
            text(languageMode, "streaming.my.playlist.prefix") +
                displayName +
                text(languageMode, "streaming.my.playlist.suffix")
        return StreamingLoginPlaylistRequest(
            provider = provider,
            playlistName = playlistName
        )
    }

    fun prepareStreamingLikedPlaylistName(
        provider: StreamingProviderName,
        languageMode: String
    ): String {
        return text(languageMode, "streaming.liked.playlist.prefix") +
            streamingProviderDisplayName(provider) +
            text(languageMode, "streaming.liked.playlist.suffix")
    }

    fun prepareStreamingPlaylistImportPresentation(
        result: StreamingLocalPlaylistImportResult?,
        languageMode: String
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text(languageMode, "streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text(languageMode, "streaming.playlist.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingLikedImportPresentation(
        result: StreamingLocalPlaylistImportResult?,
        languageMode: String
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text(languageMode, "streaming.liked.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text(languageMode, "streaming.liked.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingPlaylistSyncPresentation(
        result: StreamingLocalPlaylistSyncResult?,
        languageMode: String
    ): StreamingLocalPlaylistSyncPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistSyncPresentation(
                empty = true,
                status = text(languageMode, "streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistSyncPresentation(
            status = text(languageMode, "streaming.sync.complete") + " (${result.syncedCount})"
        )
    }

    fun prepareStreamingLoginPlaylistPresentation(
        request: StreamingLoginPlaylistRequest,
        result: StreamingLoginPlaylistResult?,
        languageMode: String
    ): StreamingLoginPlaylistPresentation {
        return StreamingLoginPlaylistPresentation(
            status = text(languageMode, "streaming.playlist.created") + ": " + request.playlistName,
            playlistId = result?.playlistId ?: -1L
        )
    }

    fun prepareStreamingPlaylistSyncTarget(localPlaylistId: Long): StreamingPlaylistSyncTarget? {
        if (localPlaylistId < 0L) {
            return null
        }
        val operations = localPlaylistOperations
        if (operations != null && !operations.playlistExists(localPlaylistId)) {
            return StreamingPlaylistSyncTarget(missingLink = true)
        }
        val link = operations?.linkedPlaylist(localPlaylistId)
        return if (link == null) {
            StreamingPlaylistSyncTarget(missingLink = true)
        } else {
            StreamingPlaylistSyncTarget(link = link)
        }
    }

    fun prepareStreamingPlaylistSyncStartRequest(
        localPlaylistId: Long,
        languageMode: String
    ): StreamingPlaylistSyncStartRequest? {
        val target = prepareStreamingPlaylistSyncTarget(localPlaylistId) ?: return null
        if (target.missingLink || target.link == null) {
            return StreamingPlaylistSyncStartRequest(
                status = text(languageMode, "streaming.not.linked")
            )
        }
        return StreamingPlaylistSyncStartRequest(
            link = target.link,
            status = text(languageMode, "streaming.sync.started"),
            valid = true
        )
    }

    fun loadUserPlaylists(provider: StreamingProviderName): Job {
        stateOwner.value = stateOwner.value.copy(
            userPlaylistsLoading = true,
            errorMessage = null
        )
        return scope.launch {
            runCatching {
                repository().userPlaylists(provider)
            }.onSuccess { playlists ->
                stateOwner.value = stateOwner.value.copy(
                    userPlaylists = playlists,
                    userPlaylistsLoading = false,
                    selectedProvider = provider,
                    diagnostics = repository().diagnostics()
                )
            }.onFailure { error ->
                stateOwner.value = stateOwner.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = repository().diagnostics()
                )
            }
        }
    }

    fun importAllAccountPlaylistsToLocal(
        provider: StreamingProviderName,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ): Job {
        stateOwner.value = stateOwner.value.copy(
            userPlaylistsLoading = true,
            playlistImporting = true,
            errorMessage = null
        )
        return scope.launch {
            val playlists = runCatching {
                repository().userPlaylists(provider)
            }.getOrElse { error ->
                stateOwner.value = stateOwner.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    playlistImporting = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = repository().diagnostics()
                )
                onImported.onResult(StreamingAccountPlaylistImportResult(failedCount = 1))
                return@launch
            }
            importAccountPlaylistsToLocalInternal(provider, playlists, onImported)
        }
    }

    fun fetchAccountPlaylistsForImport(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<StreamingPlaylist>>
    ): Job {
        stateOwner.value = stateOwner.value.copy(
            userPlaylistsLoading = true,
            errorMessage = null
        )
        return scope.launch {
            runCatching {
                repository().userPlaylists(provider)
            }.onSuccess { playlists ->
                stateOwner.value = stateOwner.value.copy(
                    userPlaylists = playlists,
                    userPlaylistsLoading = false,
                    selectedProvider = provider,
                    diagnostics = repository().diagnostics()
                )
                onResolved.onResult(playlists)
            }.onFailure { error ->
                stateOwner.value = stateOwner.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = repository().diagnostics()
                )
                onResolved.onResult(emptyList())
            }
        }
    }

    fun importAccountPlaylistsToLocal(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ): Job {
        stateOwner.value = stateOwner.value.copy(
            userPlaylistsLoading = true,
            playlistImporting = true,
            errorMessage = null
        )
        return scope.launch {
            importAccountPlaylistsToLocalInternal(provider, playlists, onImported)
        }
    }

    private suspend fun importAccountPlaylistsToLocalInternal(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ) {
        var importedPlaylists = 0
        var importedTracks = 0
        var failed = 0
        for (playlist in playlists) {
            if (playlist.providerPlaylistId.isBlank()) {
                failed += 1
                continue
            }
            val result = runCatching {
                val (playlistName, tracks) = loadStreamingPlaylistTracks(
                    playlist.provider,
                    playlist.providerPlaylistId
                )
                if (tracks.isEmpty()) {
                    StreamingLocalPlaylistImportResult(
                        playlistName = playlist.title.ifBlank { playlistName },
                        empty = true
                    )
                } else {
                    val linkedPlaylist = localPlaylistOperations?.linkedPlaylist(
                        playlist.provider,
                        playlist.providerPlaylistId
                    )
                    if (linkedPlaylist != null) {
                        val syncResult = localPlaylistOperations?.syncStreamingPlaylist(
                            linkedPlaylist,
                            tracks
                        ) ?: StreamingLocalPlaylistSyncResult(empty = true)
                        StreamingLocalPlaylistImportResult(
                            playlistName = playlistName.ifBlank { playlist.title },
                            playlistAddedCount = syncResult.syncedCount,
                            empty = syncResult.empty
                        )
                    } else {
                        importStreamingTracksToLocal(
                            playlistName = playlistName.ifBlank { playlist.title },
                            provider = playlist.provider,
                            providerPlaylistId = playlist.providerPlaylistId,
                            tracks = tracks,
                            linkWhenProviderPlaylistIdBlank = false
                        )
                    }
                }
            }.getOrElse {
                failed += 1
                null
            }
            if (result != null && !result.empty) {
                importedPlaylists += 1
                importedTracks += result.playlistAddedCount
            }
        }
        stateOwner.value = stateOwner.value.copy(
            userPlaylists = playlists,
            userPlaylistsLoading = false,
            playlistImporting = false,
            selectedProvider = provider,
            errorMessage = null,
            diagnostics = repository().diagnostics()
        )
        onImported.onResult(
            StreamingAccountPlaylistImportResult(
                playlistCount = playlists.size,
                importedPlaylistCount = importedPlaylists,
                importedTrackCount = importedTracks,
                failedCount = failed
            )
        )
    }

    fun fetchUserLikedTracks(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<StreamingTrack>>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                repository().userLikedTracks(provider)
            }.onSuccess { tracks ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onResolved.onResult(emptyList())
            }
        }
    }

    fun fetchStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        onResolved: StreamingBiCallback<String, List<StreamingTrack>>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                loadStreamingPlaylistTracks(provider, providerPlaylistId)
            }.onSuccess { result ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onResolved.onResult(result.first, result.second)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onResolved.onResult("", emptyList())
            }
        }
    }

    suspend fun loadStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): Pair<String, List<StreamingTrack>> {
        return playlistDataCoordinator.loadPlaylistTracks(provider, providerPlaylistId)
    }

    fun importStreamingPlaylistToLocal(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        onImported: StreamingCallback<StreamingLocalPlaylistImportResult>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                val (playlistName, tracks) = loadStreamingPlaylistTracks(provider, providerPlaylistId)
                if (tracks.isEmpty()) {
                    return@runCatching StreamingLocalPlaylistImportResult(
                        playlistName = playlistName,
                        empty = true
                    )
                }
                importStreamingTracksToLocal(
                    playlistName = playlistName,
                    provider = provider,
                    providerPlaylistId = providerPlaylistId,
                    tracks = tracks,
                    linkWhenProviderPlaylistIdBlank = false
                )
            }.onSuccess { result ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onImported.onResult(result)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onImported.onResult(StreamingLocalPlaylistImportResult(empty = true))
            }
        }
    }

    fun importStreamingLikedTracksToLocal(
        provider: StreamingProviderName,
        playlistName: String,
        onImported: StreamingCallback<StreamingLocalPlaylistImportResult>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                val tracks = repository().userLikedTracks(provider)
                if (tracks.isEmpty()) {
                    return@runCatching StreamingLocalPlaylistImportResult(
                        playlistName = playlistName,
                        empty = true
                    )
                }
                importStreamingTracksToLocal(
                    playlistName = playlistName,
                    provider = provider,
                    providerPlaylistId = "",
                    tracks = tracks,
                    linkWhenProviderPlaylistIdBlank = true
                )
            }.onSuccess { result ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onImported.onResult(result)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onImported.onResult(StreamingLocalPlaylistImportResult(empty = true))
            }
        }
    }

    fun syncStreamingPlaylistToLocal(
        link: app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist,
        onSynced: StreamingCallback<StreamingLocalPlaylistSyncResult>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                playlistDataCoordinator.syncPlaylistToLocal(link)
            }.onSuccess { result ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onSynced.onResult(result)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onSynced.onResult(StreamingLocalPlaylistSyncResult(empty = true))
            }
        }
    }

    fun ensureStreamingLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName,
        onEnsured: StreamingCallback<StreamingLoginPlaylistResult>
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                playlistDataCoordinator.ensureLoginPlaylist(playlistName, provider)
            }.onSuccess { result ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    selectedProvider = provider,
                    diagnostics = repository().diagnostics()
                )
                onEnsured.onResult(result)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onEnsured.onResult(StreamingLoginPlaylistResult(playlistName = playlistName))
            }
        }
    }

    fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>,
        onComplete: ((StreamingPlaylistImportSummary) -> Unit)? = null
    ): Job {
        stateOwner.value = stateOwner.value.copy(
            playlistImporting = true,
            errorMessage = null
        )
        return scope.launch {
            runCatching {
                playlistDataCoordinator.importPlaylistToStreaming(
                    provider,
                    playlistName,
                    localTracks
                )
            }.onSuccess { summary ->
                stateOwner.value = stateOwner.value.copy(
                    playlistImporting = false,
                    playlistImportSummary = summary,
                    selectedProvider = provider,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onComplete?.invoke(summary)
            }.onFailure { error ->
                stateOwner.value = stateOwner.value.copy(
                    playlistImporting = false,
                    errorMessage = error.message ?: "Playlist import failed",
                    diagnostics = repository().diagnostics()
                )
            }
        }
    }

    private suspend fun importStreamingTracksToLocal(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        tracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): StreamingLocalPlaylistImportResult {
        return playlistDataCoordinator.importTracksToLocal(
            playlistName,
            provider,
            providerPlaylistId,
            tracks,
            linkWhenProviderPlaylistIdBlank
        )
    }


    private fun streamingProviderDisplayName(provider: StreamingProviderName): String =
        descriptorFor(provider)?.displayName ?: provider.wireName

    private fun descriptorFor(provider: StreamingProviderName): StreamingProviderDescriptor? =
        stateOwner.value.providers.firstOrNull { it.name == provider }

    private fun text(languageMode: String, key: String): String =
        AppLanguage.text(languageMode, key)

    private fun beginRequest() {
        stateOwner.value = stateOwner.value.copy(
            loading = true,
            loadingMore = false,
            errorMessage = null
        )
    }

    private fun failRequest(message: String?) {
        stateOwner.value = stateOwner.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message ?: "Streaming request failed"
        )
    }

    private fun updateDiagnostics(diagnostics: StreamingGatewayDiagnostics) {
        stateOwner.value = stateOwner.value.copy(diagnostics = diagnostics)
    }
}
