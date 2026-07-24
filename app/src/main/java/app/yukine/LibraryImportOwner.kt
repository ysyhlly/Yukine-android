package app.yukine

import android.net.Uri
import app.yukine.model.LocalAudioImportSummary
import app.yukine.model.Track
import java.util.Locale

/** Owns the complete library scan/import-to-state publication pipeline. */
internal class LibraryImportOwner @JvmOverloads constructor(
    private val viewModel: LibraryViewModel,
    private val libraryStore: LibraryDataStateOwner,
    private val routeController: MainRouteController,
    private val audioPermissionSource: AudioPermissionSource,
    private val languageModeSource: LanguageModeSource,
    private val statusSink: StatusSink,
    private val collectionsLoader: CollectionsLoader,
    private val onboardingScanObserver: OnboardingScanObserver,
    private val networkNavigator: NetworkNavigator,
    private val audioVerificationScheduler: AudioVerificationScheduler = AudioVerificationScheduler {},
    private val identityIngestScheduler: IdentityIngestScheduler = IdentityIngestScheduler {}
) {
    fun interface AudioPermissionSource {
        fun hasAudioPermission(): Boolean
    }

    fun interface LanguageModeSource {
        fun languageMode(): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    fun interface CollectionsLoader {
        fun loadCollections()
    }

    fun interface OnboardingScanObserver {
        fun onLibraryScanResult(canScan: Boolean)
    }

    fun interface NetworkNavigator {
        fun openStreaming()
    }

    fun interface AudioVerificationScheduler {
        fun schedule()
    }

    fun interface IdentityIngestScheduler {
        fun schedule()
    }

    fun loadLibrary(allowCachedFirst: Boolean) {
        val canScan = audioPermissionSource.hasAudioPermission()
        if (!canScan) {
            statusSink.setStatus(text("audio.permission.required"))
        }
        viewModel.loading.loadLibraryJava(
            allowCachedFirst,
            canScan,
            { result ->
                val status = importResultStatus(result.importSummary, result.status)
                replaceLibrary(result.tracks, result.favorites, status, result.scanned) {
                    if (canScan && !allowCachedFirst) {
                        statusSink.setStatus(
                            if (result.importSummary.isEmpty) {
                                libraryScanResultStatus(result.tracks.size)
                            } else {
                                status
                            }
                        )
                    }
                    onboardingScanObserver.onLibraryScanResult(canScan)
                }
            },
            { statusKey ->
                statusSink.setStatus(text(statusKey))
                onboardingScanObserver.onLibraryScanResult(false)
            }
        )
    }

    fun loadCachedLibrary() {
        viewModel.loading.loadLibraryJava(
            true,
            false,
            { result ->
                replaceLibrary(
                    result.tracks,
                    result.favorites,
                    importResultStatus(result.importSummary, result.status),
                    false
                )
            },
            { statusKey -> statusSink.setStatus(text(statusKey)) }
        )
    }

    fun cancelLibraryLoad() {
        viewModel.loading.cancelLibraryLoad()
    }

    fun importAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            statusSink.setStatus(text("no.audio.files.selected"))
            return
        }
        statusSink.setStatus(text("importing.audio.files"))
        viewModel.loading.importAudioUrisJava(uris) { result ->
            replaceLibrary(
                result.tracks,
                result.favorites,
                importResultStatus(result.importSummary, result.status)
            )
        }
    }

    @JvmOverloads
    fun importAudioFolder(treeUri: Uri, onComplete: Runnable = Runnable {}) {
        statusSink.setStatus(text("importing.audio.folder"))
        viewModel.loading.importAudioTreeJava(treeUri) { result ->
            replaceLibrary(
                result.tracks,
                result.favorites,
                importResultStatus(result.importSummary, result.status)
            ) { onComplete.run() }
        }
    }

    fun importStreamM3u(playlistUri: Uri) {
        statusSink.setStatus(text("importing.m3u.playlist"))
        viewModel.loading.importStreamM3uJava(playlistUri) { result ->
            replaceLibrary(result.tracks, result.favorites, result.status)
            networkNavigator.openStreaming()
        }
    }

    fun importPlaylistM3u(playlistUri: Uri) {
        statusSink.setStatus(text("import.playlist.m3u"))
        viewModel.loading.importPlaylistM3uJava(playlistUri) { result ->
            if (result.playlistId >= 0L) {
                routeController.setSelectedPlaylistId(result.playlistId)
            }
            replaceLibrary(result.tracks, result.favorites, result.status)
        }
    }

    @JvmOverloads
    fun replaceLibrary(
        tracks: List<Track>,
        favorites: Set<Long>,
        status: String,
        scanned: Boolean = true,
        onApplied: Runnable = Runnable {}
    ) {
        applyLibraryReplacement(tracks, favorites) {
            statusSink.setStatus(status)
            if (scanned) {
                identityIngestScheduler.schedule()
                viewModel.loading.parseMissingAudioSpecsJava { result ->
                    audioVerificationScheduler.schedule()
                    applyLibraryReplacement(result.tracks, result.favorites) {
                        if (result.updatedCount > 0) {
                            statusSink.setStatus(text("audio.specs.updated") + " (${result.updatedCount})")
                        }
                    }
                }
            } else {
                audioVerificationScheduler.schedule()
            }
            onApplied.run()
        }
    }

    /**
     * Republishes a Room snapshot after an offline identity merge. This deliberately skips media
     * scanning, audio-spec parsing, and verification scheduling; only canonical grouping and the
     * collection projections are refreshed.
     */
    fun republishCanonicalLibrary(tracks: List<Track>, favorites: Set<Long>) {
        applyLibraryReplacement(tracks, favorites) {
            collectionsLoader.loadCollections()
        }
    }

    private fun applyLibraryReplacement(
        tracks: List<Track>,
        favorites: Set<Long>,
        onApplied: Runnable
    ) {
        libraryStore.replaceLibraryAsync(tracks, favorites, routeController.searchQuery()) {
            collectionsLoader.loadCollections()
            onApplied.run()
        }
    }

    private fun libraryScanResultStatus(trackCount: Int): String {
        if (trackCount <= 0) {
            return text("no.music")
        }
        return text("library.scan.found.prefix") + trackCount + text("library.scan.found.suffix")
    }

    private fun importResultStatus(summary: LocalAudioImportSummary, fallback: String): String {
        if (summary.isEmpty) {
            return if (fallback == "Library updated") text("library.updated") else fallback
        }
        if (summary.skippedCount() <= 0) {
            return String.format(
                Locale.ROOT,
                text("local.audio.import.success"),
                summary.importedCount()
            )
        }
        val formats = summary.skippedFormatCounts()
            .entries
            .take(3)
            .joinToString(text("local.audio.summary.separator")) { (format, count) ->
                "$format\u00d7$count"
            }
        val base = String.format(
            Locale.ROOT,
            text("local.audio.import.partial"),
            summary.importedCount(),
            summary.skippedCount()
        )
        return if (formats.isEmpty()) {
            base
        } else {
            base + text("local.audio.summary.details.separator") + formats
        }
    }

    private fun text(key: String): String = AppLanguage.text(languageModeSource.languageMode(), key)
}
