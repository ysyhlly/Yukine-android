package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction

internal class LibraryCoordinator(
    private val libraryViewModel: LibraryViewModel,
    private val collectionsViewModel: CollectionsViewModel,
    private val libraryStore: MainLibraryStore,
    private val settingsStore: MainSettingsStore,
    private val playbackStore: MainPlaybackStore,
    private val repository: MusicLibraryRepository,
    private val permissionController: MainPermissionController,
    private val statusMessageController: StatusMessageController,
    val trackListRenderController: TrackListRenderController,
    val libraryGroupsRenderController: LibraryGroupsRenderController,
    val libraryPlaylistsRenderController: LibraryPlaylistsRenderController,
    val collectionsRenderController: CollectionsRenderController,
    val playHistoryActionController: PlayHistoryActionController,
    private val listener: Listener
) {

    interface Listener {
        fun renderSelectedTab()
        fun renderAndPersistSelectedTab()
        fun selectedPlaylistId(): Long
        fun selectedLibraryGroupKey(): String
        fun selectedLibraryGroupTitle(): String
        fun libraryMode(): String
        fun searchQuery(): String
        fun setSelectedPlaylistId(playlistId: Long)
        fun loadCollections()
    }

    fun loadLibrary(allowCachedFirst: Boolean) {
        val canScan = permissionController.hasAudioPermission()
        if (!canScan) {
            statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "audio.permission.required")
            )
        }
        libraryViewModel.loadLibraryJava(
            allowCachedFirst,
            canScan,
            { result ->
                replaceLibrary(result.tracks, result.favorites, result.status)
                if (canScan && !allowCachedFirst) {
                    statusMessageController.setStatus(libraryScanResultStatus(result.tracks.size))
                }
            },
            { status ->
                statusMessageController.setStatus(status)
                listener.renderSelectedTab()
            }
        )
    }

    fun replaceLibrary(tracks: List<Track>, favorites: Set<Long>, status: String) {
        libraryStore.replaceLibrary(tracks, favorites, listener.searchQuery())
        listener.renderAndPersistSelectedTab()
        statusMessageController.setStatus(status)
        listener.loadCollections()
        libraryViewModel.parseMissingAudioSpecsJava { result ->
            applyBackgroundAudioSpecs(result.tracks, result.favorites, result.updatedCount)
        }
    }

    fun loadCollections(selectedPlaylistId: Long) {
        libraryViewModel.loadCollectionsJava(selectedPlaylistId) { result ->
            listener.setSelectedPlaylistId(result.selectedPlaylistId)
            libraryStore.applyCollections(result)
            listener.renderSelectedTab()
        }
    }

    fun renderLibrary() {
        if (!permissionController.hasAudioPermission()) {
            statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "audio.permission.required") + ": "
                        + AppLanguage.text(settingsStore.languageMode(), "audio.permission.description")
            )
            return
        }
        val mode = listener.libraryMode()
        if (libraryStore.visibleTracks().isEmpty() && LibraryGrouping.PLAYLISTS != mode) {
            statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "no.music") + ": "
                        + AppLanguage.text(settingsStore.languageMode(), "no.music.description")
            )
            return
        }
        when (mode) {
            LibraryGrouping.PLAYLISTS -> renderLibraryPlaylists()
            LibraryGrouping.SONGS -> renderLibrarySongs()
            else -> renderLibraryGroups()
        }
    }

    fun renderCollections() {
        collectionsRenderController.render(
            settingsStore.languageMode(),
            libraryStore.favoriteTracks(),
            libraryStore.recentRecords(),
            libraryStore.mostPlayedRecords(),
            libraryStore.playlists(),
            libraryStore.selectedPlaylistTracks(),
            listener.selectedPlaylistId(),
            playbackStore.snapshot(),
            libraryStore.favoriteIds(),
            repository.loadRecentlyAdded(30),
            repository.loadLongUnplayed(30)
        )
    }

    fun renderHome(homeDashboardRenderController: HomeDashboardRenderController, anyStreamingConnected: Boolean) {
        homeDashboardRenderController.render(
            settingsStore.languageMode(),
            libraryStore.allTracks(),
            libraryStore.allTracks(),
            libraryStore.recentRecords(),
            playbackStore.snapshot(),
            anyStreamingConnected
        )
    }

    private fun renderLibrarySongs() {
        trackListRenderController.render(
            AppLanguage.text(settingsStore.languageMode(), "songs"),
            libraryStore.visibleTracks(),
            true,
            ArrayList<String>(),
            false,
            ArrayList(),
            ArrayList(),
            "",
            libraryModeActions(),
            TrackListLabels(),
            playbackStore.snapshot(),
            libraryStore.favoriteIds(),
            ArrayList()
        )
    }

    private fun renderLibraryPlaylists() {
        libraryPlaylistsRenderController.render(
            settingsStore.languageMode(),
            libraryStore.playlists(),
            listener.selectedPlaylistId(),
            listener.selectedLibraryGroupKey(),
            libraryStore.selectedPlaylistName(listener.selectedPlaylistId()),
            libraryStore.filteredSelectedPlaylistTracks(listener.searchQuery()),
            libraryStore.favoriteTracks(),
            libraryStore.recentRecords(),
            libraryModeActions()
        )
    }

    private fun renderLibraryGroups() {
        libraryGroupsRenderController.render(
            settingsStore.languageMode(),
            libraryStore.visibleTracks(),
            listener.libraryMode(),
            listener.selectedLibraryGroupKey(),
            listener.selectedLibraryGroupTitle(),
            libraryModeActions()
        )
    }

    fun libraryModeActions(): ArrayList<TrackListModeAction> {
        val languageMode = settingsStore.languageMode()
        val modes = ArrayList<TrackListModeAction>()
        val currentMode = listener.libraryMode()
        modes.add(TrackListModeAction(AppLanguage.text(languageMode, "songs"), LibraryGrouping.SONGS, LibraryGrouping.SONGS == currentMode) { libraryViewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.SONGS)) })
        modes.add(TrackListModeAction(AppLanguage.text(languageMode, "albums"), LibraryGrouping.ALBUMS, LibraryGrouping.ALBUMS == currentMode) { libraryViewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.ALBUMS)) })
        modes.add(TrackListModeAction(AppLanguage.text(languageMode, "artists"), LibraryGrouping.ARTISTS, LibraryGrouping.ARTISTS == currentMode) { libraryViewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.ARTISTS)) })
        modes.add(TrackListModeAction(AppLanguage.text(languageMode, "folders"), LibraryGrouping.FOLDERS, LibraryGrouping.FOLDERS == currentMode) { libraryViewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.FOLDERS)) })
        modes.add(TrackListModeAction(AppLanguage.text(languageMode, "playlists"), LibraryGrouping.PLAYLISTS, LibraryGrouping.PLAYLISTS == currentMode) { libraryViewModel.onEvent(LibraryEvent.ChangeGroupMode(LibraryGrouping.PLAYLISTS)) })
        return modes
    }

    private fun applyBackgroundAudioSpecs(tracks: List<Track>, favorites: Set<Long>, updatedCount: Int) {
        libraryStore.replaceLibrary(tracks, favorites, listener.searchQuery())
        listener.renderAndPersistSelectedTab()
        listener.loadCollections()
        if (updatedCount > 0) {
            statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "audio.specs.updated") + " ($updatedCount)"
            )
        }
    }

    private fun libraryScanResultStatus(trackCount: Int): String {
        if (trackCount <= 0) {
            return AppLanguage.text(settingsStore.languageMode(), "no.music")
        }
        val languageMode = settingsStore.languageMode()
        return AppLanguage.text(languageMode, "library.scan.found.prefix") +
                trackCount +
                AppLanguage.text(languageMode, "library.scan.found.suffix")
    }
}
