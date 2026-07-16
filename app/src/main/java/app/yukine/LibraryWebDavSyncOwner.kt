package app.yukine

import app.yukine.data.MusicLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface LibraryWebDavSyncOperations {
    fun sourceIds(): List<Long>
    fun syncAll(sourceIds: List<Long>): WebDavSourceSnapshot
    fun loadAutoSyncEnabled(): Boolean
    fun saveAutoSyncEnabled(enabled: Boolean)
}

internal class MusicLibraryWebDavSyncOperations(
    private val repository: MusicLibraryRepository
) : LibraryWebDavSyncOperations {
    private val syncAll = SyncAllWebDavSourcesUseCase(MusicLibraryWebDavSourceOperations(repository))

    override fun sourceIds(): List<Long> = repository.loadRemoteSources()
        .map { it.id }
        .filter { it > 0L }

    override fun syncAll(sourceIds: List<Long>): WebDavSourceSnapshot = syncAll.execute(sourceIds)

    override fun loadAutoSyncEnabled(): Boolean = repository.loadLibraryAutoSyncEnabled()

    override fun saveAutoSyncEnabled(enabled: Boolean) {
        repository.saveLibraryAutoSyncEnabled(enabled)
    }
}

internal fun interface LibrarySyncResultListener {
    fun onSynced(snapshot: WebDavSourceSnapshot)
}

internal fun interface LibrarySyncLanguageProvider {
    fun languageMode(): String
}

internal fun interface LibrarySyncStatusSink {
    fun show(message: String)
}

/** Activity-scoped owner for user-triggered and startup WebDAV library synchronization. */
internal class LibraryWebDavSyncOwner @JvmOverloads constructor(
    private val operations: LibraryWebDavSyncOperations,
    private val presentation: LibraryPresentationStateOwner,
    private val resultListener: LibrarySyncResultListener,
    private val languageProvider: LibrarySyncLanguageProvider,
    private val statusSink: LibrarySyncStatusSink,
    private val scope: CoroutineScope = MainScope(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val multiSourceSync: LibraryMultiSourceSyncCoordinator? = null,
    private val matchesChanged: Runnable = Runnable {}
) {
    constructor(
        operations: LibraryWebDavSyncOperations,
        presentation: LibraryPresentationStateOwner,
        resultListener: LibrarySyncResultListener,
        languageProvider: LibrarySyncLanguageProvider,
        statusSink: LibrarySyncStatusSink,
        multiSourceSync: LibraryMultiSourceSyncCoordinator
    ) : this(
        operations,
        presentation,
        resultListener,
        languageProvider,
        statusSink,
        MainScope(),
        Dispatchers.IO,
        multiSourceSync
    )

    constructor(
        operations: LibraryWebDavSyncOperations,
        presentation: LibraryPresentationStateOwner,
        resultListener: LibrarySyncResultListener,
        languageProvider: LibrarySyncLanguageProvider,
        statusSink: LibrarySyncStatusSink,
        multiSourceSync: LibraryMultiSourceSyncCoordinator,
        matchesChanged: Runnable
    ) : this(
        operations,
        presentation,
        resultListener,
        languageProvider,
        statusSink,
        MainScope(),
        Dispatchers.IO,
        multiSourceSync,
        matchesChanged
    )

    private var syncJob: Job? = null
    private var released = false

    fun initialize() {
        if (released) return
        scope.launch {
            val backfilled = runCatching {
                withContext(ioDispatcher) {
                    multiSourceSync?.backfillPendingConfirmedSources() ?: 0
                }
            }.getOrDefault(0)
            if (backfilled > 0 && !released) {
                matchesChanged.run()
            }
            val enabled = runCatching {
                withContext(ioDispatcher) { operations.loadAutoSyncEnabled() }
            }.getOrDefault(false)
            presentation.updateAutoSyncEnabled(enabled)
            if (enabled) syncNow(false)
        }
    }

    @JvmOverloads
    fun syncNow(showBusyMessage: Boolean = true) {
        if (released) return
        if (syncJob?.isActive == true) {
            if (showBusyMessage) statusSink.show(text("library.sync.busy"))
            return
        }
        syncJob = scope.launch {
            presentation.updateSyncInProgress(true)
            try {
                val sourceIds = withContext(ioDispatcher) { operations.sourceIds() }
                var synchronizedAnySource = false
                if (sourceIds.isNotEmpty()) {
                    val snapshot = withContext(ioDispatcher) { operations.syncAll(sourceIds) }
                    val localized = snapshot.copy(status = text("library.sync.complete"))
                    resultListener.onSynced(localized)
                    synchronizedAnySource = true
                    withContext(ioDispatcher) { multiSourceSync?.refreshIdentitySnapshot() }
                }
                val multiSourceResult = withContext(ioDispatcher) {
                    multiSourceSync?.syncIncremental()
                }
                if (multiSourceResult != null && multiSourceResult.providerCount > 0) {
                    synchronizedAnySource = true
                    matchesChanged.run()
                }
                if (!synchronizedAnySource) {
                    statusSink.show(text("library.sync.no.sources"))
                    return@launch
                }
                statusSink.show(text("library.sync.complete"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                statusSink.show(text("library.sync.failed"))
            } finally {
                presentation.updateSyncInProgress(false)
            }
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        if (released) return
        scope.launch {
            val saved = runCatching {
                withContext(ioDispatcher) { operations.saveAutoSyncEnabled(enabled) }
            }.isSuccess
            if (!saved) {
                statusSink.show(text("library.sync.failed"))
                return@launch
            }
            presentation.updateAutoSyncEnabled(enabled)
            statusSink.show(text(if (enabled) "library.auto.sync.enabled" else "library.auto.sync.disabled"))
            if (enabled) syncNow(false)
        }
    }

    fun release() {
        released = true
        syncJob?.cancel()
        scope.cancel()
    }

    fun sourceCandidatesFor(track: app.yukine.model.Track?): List<app.yukine.model.Track> =
        multiSourceSync?.candidatesFor(track).orEmpty()

    private fun text(key: String): String = AppLanguage.text(languageProvider.languageMode(), key)
}
