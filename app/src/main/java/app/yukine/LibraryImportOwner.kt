package app.yukine

import android.net.Uri
import app.yukine.model.Track

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
    private val audioVerificationScheduler: AudioVerificationScheduler = AudioVerificationScheduler {}
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

    fun loadLibrary(allowCachedFirst: Boolean) {
        val canScan = audioPermissionSource.hasAudioPermission()
        if (!canScan) {
            statusSink.setStatus(text("audio.permission.required"))
        }
        viewModel.loading.loadLibraryJava(
            allowCachedFirst,
            canScan,
            { result ->
                replaceLibrary(result.tracks, result.favorites, result.status) {
                    if (canScan && !allowCachedFirst) {
                        statusSink.setStatus(libraryScanResultStatus(result.tracks.size))
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
            replaceLibrary(result.tracks, result.favorites, result.status)
        }
    }

    fun importAudioFolder(treeUri: Uri) {
        statusSink.setStatus(text("importing.audio.folder"))
        viewModel.loading.importAudioTreeJava(treeUri) { result ->
            replaceLibrary(result.tracks, result.favorites, result.status)
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
        onApplied: Runnable = Runnable {}
    ) {
        applyLibraryReplacement(tracks, favorites) {
            statusSink.setStatus(status)
            viewModel.loading.parseMissingAudioSpecsJava { result ->
                audioVerificationScheduler.schedule()
                applyLibraryReplacement(result.tracks, result.favorites) {
                    if (result.updatedCount > 0) {
                        statusSink.setStatus(text("audio.specs.updated") + " (${result.updatedCount})")
                    }
                }
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

    private fun text(key: String): String = AppLanguage.text(languageModeSource.languageMode(), key)
}
