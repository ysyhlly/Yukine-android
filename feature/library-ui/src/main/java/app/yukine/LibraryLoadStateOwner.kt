package app.yukine

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Focused owner for library refresh, imports, documents and hidden-item restoration. */
class LibraryLoadStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val mutations: LibraryMutationContext,
    private val gateway: () -> LibraryGateway?,
    private val refreshDiagnosticTimeoutMs: Long
) {
    private var importGateway: LibraryImportGateway? = null
    private var documentGateway: LibraryDocumentGateway? = null
    private var exclusionGateway: LibraryExclusionGateway? = null
    private var audioSpecParsingRunning = false
    private var audioSpecParsingPending = false
    private var pendingAudioSpecCallback: ((LibraryAudioSpecsResultUi) -> Unit)? = null
    private var loadJob: Job? = null
    private var watchdogJob: Job? = null
    private var nextLoadId = 0L
    private var activeLoadId = 0L

    fun bindImportGateway(next: LibraryImportGateway?) {
        importGateway = next
    }

    fun bindDocumentGateway(next: LibraryDocumentGateway?) {
        documentGateway = next
    }

    fun bindExclusionGateway(next: LibraryExclusionGateway?) {
        exclusionGateway = next
    }

    fun restoreHiddenItem(sourceKey: String, onRestored: ((Boolean) -> Unit)? = null) {
        val source = exclusionGateway ?: return
        mutations.launch("library.restore.failed", { source.restoreLibraryExclusion(sourceKey) }, onRestored)
    }

    fun restoreHiddenItemJava(sourceKey: String, onRestored: LibraryExclusionRestoreCallback?) {
        restoreHiddenItem(sourceKey) { changed -> onRestored?.onRestored(changed) }
    }

    fun restoreAllHiddenItems(onRestored: ((Boolean) -> Unit)? = null) {
        val source = exclusionGateway ?: return
        mutations.launch("library.restore.failed", { source.restoreAllLibraryExclusions() > 0 }, onRestored)
    }

    fun restoreAllHiddenItemsJava(onRestored: LibraryExclusionRestoreCallback?) {
        restoreAllHiddenItems { changed -> onRestored?.onRestored(changed) }
    }

    fun loadLibrary(
        allowCachedFirst: Boolean,
        canScan: Boolean,
        onLoaded: ((LibraryLoadResultUi) -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val source = importGateway ?: return
        val loadId = ++nextLoadId
        activeLoadId = loadId
        loadJob?.cancel()
        watchdogJob?.cancel()
        loadJob = scope.launch {
            val reportProgress: (LibraryRefreshProgress) -> Unit = { progress ->
                scope.launch {
                    if (activeLoadId == loadId) gateway()?.showStatusKey(progress.statusKey())
                }
            }
            try {
                if (allowCachedFirst) {
                    val cached = mutations.runLocked { source.loadCached() }
                    if (activeLoadId == loadId) onLoaded?.invoke(cached)
                }
                if (!canScan) return@launch
                startWatchdog(loadId)
                val fresh = mutations.runLocked {
                    (source as? LibraryRefreshProgressGateway)?.refresh(reportProgress) ?: source.refresh()
                }
                if (activeLoadId == loadId) onLoaded?.invoke(fresh)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (activeLoadId == loadId) {
                    val status = if (error is SecurityException) "audio.permission.required" else "library.scan.failed"
                    gateway()?.showStatusKey(status)
                    onFailed?.invoke(status)
                }
            } finally {
                if (activeLoadId == loadId) {
                    activeLoadId = 0L
                    watchdogJob?.cancel()
                    watchdogJob = null
                }
            }
        }
    }

    fun cancelLibraryLoad() {
        activeLoadId = 0L
        watchdogJob?.cancel()
        watchdogJob = null
        val cancelled = loadJob
        loadJob = null
        cancelled?.cancel()
    }

    private fun startWatchdog(loadId: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(refreshDiagnosticTimeoutMs)
            if (activeLoadId == loadId) gateway()?.showStatusKey("library.scan.slow")
        }
    }

    fun loadLibraryJava(
        allowCachedFirst: Boolean,
        canScan: Boolean,
        onLoaded: LibraryLoadCallback?,
        onFailed: LibraryLoadFailedCallback?
    ) {
        loadLibrary(
            allowCachedFirst,
            canScan,
            { result -> onLoaded?.onLoaded(result) },
            { status -> onFailed?.onFailed(status) }
        )
    }

    fun importAudioUris(uris: List<@JvmSuppressWildcards Uri>, onLoaded: ((LibraryLoadResultUi) -> Unit)? = null) {
        val source = importGateway ?: return
        mutations.launch("library.import.failed", { source.importAudioUris(uris) }, onLoaded)
    }

    fun importAudioUrisJava(uris: List<@JvmSuppressWildcards Uri>, onLoaded: LibraryLoadCallback?) {
        importAudioUris(uris) { result -> onLoaded?.onLoaded(result) }
    }

    fun importAudioTree(treeUri: Uri, onLoaded: ((LibraryLoadResultUi) -> Unit)? = null) {
        val source = importGateway ?: return
        mutations.launch("library.import.failed", { source.importAudioTree(treeUri) }, onLoaded)
    }

    fun importAudioTreeJava(treeUri: Uri, onLoaded: LibraryLoadCallback?) {
        importAudioTree(treeUri) { result -> onLoaded?.onLoaded(result) }
    }

    fun parseMissingAudioSpecs(onParsed: ((LibraryAudioSpecsResultUi) -> Unit)? = null) {
        val source = importGateway ?: return
        if (audioSpecParsingRunning) {
            audioSpecParsingPending = true
            pendingAudioSpecCallback = onParsed
            return
        }
        audioSpecParsingRunning = true
        scope.launch {
            try {
                val result = mutations.runLocked { source.parseMissingAudioSpecs() }
                if (result.updatedCount > 0) onParsed?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway()?.showStatusKey("audio.specs.failed")
            } finally {
                audioSpecParsingRunning = false
                if (audioSpecParsingPending) {
                    val callback = pendingAudioSpecCallback
                    audioSpecParsingPending = false
                    pendingAudioSpecCallback = null
                    parseMissingAudioSpecs(callback)
                }
            }
        }
    }

    fun parseMissingAudioSpecsJava(onParsed: LibraryAudioSpecsParsedCallback?) {
        parseMissingAudioSpecs { result -> onParsed?.onParsed(result) }
    }

    fun importStreamM3u(uri: Uri?, onImported: ((LibraryLoadResultUi) -> Unit)? = null) {
        val source = documentGateway ?: return
        mutations.launch("local.m3u.import.failed", { source.importStreamM3u(uri) }, onImported)
    }

    fun importStreamM3uJava(uri: Uri?, onImported: LibraryLoadCallback?) {
        importStreamM3u(uri) { result -> onImported?.onLoaded(result) }
    }

    fun importPlaylistM3u(uri: Uri?, onImported: ((LibraryPlaylistImportResultUi) -> Unit)? = null) {
        val source = documentGateway ?: return
        mutations.launch("playlist.import.failed", { source.importPlaylistM3u(uri) }, onImported)
    }

    fun importPlaylistM3uJava(uri: Uri?, onImported: LibraryPlaylistImportCallback?) {
        importPlaylistM3u(uri) { result -> onImported?.onImported(result) }
    }

    fun exportPlaylist(uri: Uri?, playlistId: Long, name: String, onExported: ((Boolean) -> Unit)? = null) {
        val source = documentGateway ?: return
        mutations.launch("playlist.export.failed", { source.exportPlaylist(uri, playlistId, name) }, onExported)
    }

    fun exportPlaylistJava(uri: Uri?, playlistId: Long, name: String) {
        exportPlaylist(uri, playlistId, name)
    }

    private fun LibraryRefreshProgress.statusKey(): String = when (phase) {
        LibraryRefreshPhase.CHECKING -> "library.scan.checking"
        LibraryRefreshPhase.SCANNING -> "library.scan.scanning"
        LibraryRefreshPhase.REPLACING -> "library.scan.replacing"
        LibraryRefreshPhase.RELOADING -> "library.scan.reloading"
    }
}
