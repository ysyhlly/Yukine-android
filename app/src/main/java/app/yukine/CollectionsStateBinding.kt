package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.LocalAudioFormatPolicy
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.navigation.CollectionsTab
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.CollectionActionUiState
import app.yukine.ui.CollectionMetricUiState
import app.yukine.ui.CollectionPlaylistFolderUiState
import app.yukine.ui.CollectionTrackSectionActions
import app.yukine.ui.CollectionTrackSectionUiState
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.emptyCollectionsActions
import app.yukine.ui.EchoIconKind
import app.yukine.ui.FavoriteSyncActions
import app.yukine.ui.FavoriteSyncSourceUiState
import app.yukine.ui.FavoriteSyncUiState
import app.yukine.ui.PlaylistRowActions
import app.yukine.ui.PlaylistRowUiState
import app.yukine.ui.PlaylistTrackActions
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import java.text.DateFormat
import java.util.ArrayList
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CollectionsInsightSnapshot(
    val recentlyAdded: List<Track> = emptyList(),
    val longUnplayed: List<Track> = emptyList(),
    val playlistSources: Map<Long, StreamingProviderName> = emptyMap()
)

internal fun interface CollectionsInsightsLoader {
    fun load(playlists: List<Playlist>): CollectionsInsightSnapshot
}

internal class CollectionsStateBinding @JvmOverloads constructor(
    private val viewModel: CollectionsViewModel,
    private val listener: Listener,
    private val scope: CoroutineScope = MainScope(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val insights = MutableStateFlow(CollectionsInsightSnapshot())
    private val favoriteDashboard = MutableStateFlow(FavoriteSyncDashboard())
    private var bindingJob: Job? = null
    private var insightsJob: Job? = null
    private var favoriteJob: Job? = null
    private var playbackReadModel: PlaybackReadModel? = null
    private var favoriteSyncViewModel: FavoriteSyncViewModel? = null

    interface Listener {
        fun showCreatePlaylist()

        fun openPlaylistM3uFilePicker()

        fun confirmClearPlayHistory()

        fun requestBack()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun downloadTrack(track: Track)

        fun downloadTracks(tracks: List<Track>)

        fun selectPlaylist(playlistId: Long)

        fun showRenamePlaylist(playlist: Playlist)

        fun confirmDeletePlaylist(playlist: Playlist)

        fun openSelectedPlaylistExportDocument()

        fun importSelectedPlaylistToStreaming()

        fun importFavoritesToStreaming()

        fun importStreamingFavorites()

        fun syncSelectedPlaylistFromStreaming()

        fun moveSelectedPlaylistTrack(playlistId: Long, track: Track, trackIndex: Int, direction: Int)

        fun removeSelectedPlaylistTrack(playlistId: Long, track: Track)
    }

    fun bindFavoriteSync(viewModel: FavoriteSyncViewModel?) {
        favoriteJob?.cancel()
        favoriteSyncViewModel = viewModel
        favoriteJob = viewModel?.let { target ->
            scope.launch { target.dashboard.collect { favoriteDashboard.value = it } }
        }
    }

    fun bindStateSources(
        routeState: StateFlow<NavigationRouteState>?,
        libraryState: StateFlow<LibraryStoreState>?,
        settingsState: StateFlow<SettingsState>?,
        playback: PlaybackReadModel?,
        insightsLoader: CollectionsInsightsLoader?
    ) {
        bindingJob?.cancel()
        insightsJob?.cancel()
        bindingJob = null
        insightsJob = null
        playbackReadModel = playback
        if (
            routeState == null || libraryState == null || settingsState == null ||
            playback == null || insightsLoader == null
        ) {
            return
        }
        val route = routeState.map(::collectionsBindingRoute).distinctUntilChanged()
        bindingJob = scope.launch {
            combine(
                route,
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged(),
                playback.state.map { it.currentTrack }.distinctUntilChanged(),
                insights
            ) { routeInput, library, languageMode, _, insightSnapshot ->
                CollectionsBindingInputs(routeInput, library, languageMode, insightSnapshot)
            }.combine(favoriteDashboard) { input, dashboard ->
                input.copy(favoriteSync = dashboard)
            }.collect { input ->
                if (input.route.active) {
                    publishFromState(input)
                }
            }
        }
        insightsJob = scope.launch {
            combine(
                route.map { it.active }.distinctUntilChanged(),
                libraryState
            ) { active, library -> active to library }
                .collectLatest { (active, library) ->
                    if (active) {
                        insights.value = withContext(ioDispatcher) {
                            insightsLoader.load(library.playlists)
                        }
                    }
                }
        }
    }

    fun release() {
        bindingJob?.cancel()
        insightsJob?.cancel()
        favoriteJob?.cancel()
        bindingJob = null
        insightsJob = null
        favoriteJob = null
        favoriteSyncViewModel = null
        playbackReadModel = null
        scope.cancel()
    }

    private fun publishFromState(input: CollectionsBindingInputs) {
        reduceAndPublish(
            input.languageMode,
            input.library.favoriteTracks,
            input.library.recentRecords,
            input.library.mostPlayedRecords,
            input.library.playlists,
            input.library.selectedPlaylistTracks,
            input.route.selectedPlaylistId,
            playbackReadModel?.state?.value,
            input.library.favoriteTrackIds,
            input.insights.recentlyAdded,
            input.insights.longUnplayed,
            input.insights.playlistSources,
            input.favoriteSync
        )
    }

    fun reduceAndPublish(
        languageMode: String,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        mostPlayedRecords: List<TrackPlayRecord>,
        playlists: List<Playlist>,
        selectedPlaylistTracks: List<Track>,
        selectedPlaylistId: Long,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        recentlyAdded: List<Track> = emptyList(),
        longUnplayed: List<Track> = emptyList(),
        playlistSources: Map<Long, StreamingProviderName> = emptyMap(),
        favoriteSync: FavoriteSyncDashboard = FavoriteSyncDashboard()
    ) {
        val fallbackPlaylistTitle = text(languageMode, "playlist")
        viewModel.updateCollections(
            favoriteTracks,
            recentRecords,
            mostPlayedRecords,
            playlists,
            selectedPlaylistTracks,
            selectedPlaylistId,
            fallbackPlaylistTitle
        )
        val metrics = ArrayList<CollectionMetricUiState>()
        metrics.add(CollectionMetricUiState(text(languageMode, "favorites"), favoriteTracks.size.toString()))
        metrics.add(CollectionMetricUiState(text(languageMode, "recent"), recentRecords.size.toString()))
        metrics.add(CollectionMetricUiState(text(languageMode, "playlists"), playlists.size.toString()))

        val topActionRows = ArrayList<CollectionActionUiState>()
        val topActions = ArrayList<Runnable>()
        addCollectionAction(topActionRows, topActions, text(languageMode, "new.playlist"), EchoIconKind.Action, Runnable {
            listener.showCreatePlaylist()
        })
        addCollectionAction(topActionRows, topActions, text(languageMode, "import.playlist.m3u"), EchoIconKind.Import, Runnable {
            listener.openPlaylistM3uFilePicker()
        })
        if (favoriteTracks.isNotEmpty()) {
            addCollectionAction(topActionRows, topActions, text(languageMode, "import.favorites.to.streaming"), EchoIconKind.Import, Runnable {
                listener.importFavoritesToStreaming()
            })
        }
        addCollectionAction(topActionRows, topActions, text(languageMode, "streaming.import.liked"), EchoIconKind.Import, Runnable {
            listener.importStreamingFavorites()
        })
        if (recentRecords.isNotEmpty() || mostPlayedRecords.isNotEmpty()) {
            addCollectionAction(topActionRows, topActions, text(languageMode, "clear.play.history"), EchoIconKind.Delete, Runnable {
                listener.confirmClearPlayHistory()
            })
        }

        val currentTrack = playbackState?.currentTrack
        val trackSections = ArrayList<CollectionTrackSectionUiState>()
        val trackSectionActions = ArrayList<CollectionTrackSectionActions>()
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "favorites",
            text(languageMode, "favorites"),
            favoriteTracks,
            text(languageMode, "no.favorites"),
            text(languageMode, "no.favorites.description"),
            text(languageMode, "play.favorites"),
            null,
            currentTrack,
            favoriteIds,
            languageMode
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "recent",
            text(languageMode, "recent"),
            tracksFromRecords(recentRecords),
            text(languageMode, "no.recent.tracks"),
            text(languageMode, "no.recent.tracks.description"),
            text(languageMode, "play.recent"),
            recordDetails(recentRecords, showPlayCount = false, languageMode),
            currentTrack,
            favoriteIds,
            languageMode
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "most-played",
            text(languageMode, "most.played"),
            tracksFromRecords(mostPlayedRecords),
            text(languageMode, "no.play.history"),
            text(languageMode, "no.play.history.description"),
            text(languageMode, "play.most.played"),
            recordDetails(mostPlayedRecords, showPlayCount = true, languageMode),
            currentTrack,
            favoriteIds,
            languageMode
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "recently-added",
            text(languageMode, "recently.added"),
            recentlyAdded,
            text(languageMode, "no.recently.added"),
            text(languageMode, "no.recently.added.description"),
            text(languageMode, "play.recently.added"),
            null,
            currentTrack,
            favoriteIds,
            languageMode
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "long-unplayed",
            text(languageMode, "long.unplayed"),
            longUnplayed,
            text(languageMode, "no.long.unplayed"),
            text(languageMode, "no.long.unplayed.description"),
            text(languageMode, "play.long.unplayed"),
            null,
            currentTrack,
            favoriteIds,
            languageMode
        )

        val playlistRows = ArrayList<PlaylistRowUiState>()
        val playlistActions = ArrayList<PlaylistRowActions>()
        buildPlaylistRows(playlistRows, playlistActions, playlists, selectedPlaylistId, languageMode)
        val playlistFolders = buildPlaylistFolders(playlists, playlistRows, playlistSources, languageMode)

        val selectedPlaylistActionRows = ArrayList<CollectionActionUiState>()
        val selectedPlaylistActions = ArrayList<Runnable>()
        val selectedPlaylistRows = ArrayList<PlaylistTrackUiState>()
        val selectedPlaylistTrackActions = ArrayList<PlaylistTrackActions>()
        if (selectedPlaylistId >= 0L && selectedPlaylistTracks.isNotEmpty()) {
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "play.playlist"), EchoIconKind.Play, Runnable {
                playFirstSupported(selectedPlaylistTracks)
            })
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "download.playlist"), EchoIconKind.Download, Runnable {
                listener.downloadTracks(selectedPlaylistTracks)
            })
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "export.playlist"), EchoIconKind.Upload, Runnable {
                listener.openSelectedPlaylistExportDocument()
            })
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "import.playlist.to.streaming"), EchoIconKind.Import, Runnable {
                listener.importSelectedPlaylistToStreaming()
            })
            buildSelectedPlaylistRows(
                selectedPlaylistRows,
                selectedPlaylistTrackActions,
                selectedPlaylistId,
                selectedPlaylistTracks,
                currentTrack,
                favoriteIds,
                languageMode
            )
        }
        // Sync button for streaming-linked playlists (even if empty)
        if (selectedPlaylistId >= 0L) {
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "sync.streaming.playlist"), EchoIconKind.Sync, Runnable {
                listener.syncSelectedPlaylistFromStreaming()
            })
        }

        val state = CollectionsUiState(
            title = text(languageMode, "tab.collections"),
            backLabel = text(languageMode, "back"),
            metrics = metrics,
            topActions = topActionRows,
            trackSections = trackSections,
            playlistTitle = text(languageMode, "playlists"),
            playlistEmptyText = text(languageMode, "no.playlists"),
            playlistEmptyDescription = text(languageMode, "no.playlists.description"),
            playlists = playlistRows,
            selectedPlaylistVisible = selectedPlaylistId >= 0L,
            selectedPlaylistTitle = selectedPlaylistName(playlists, selectedPlaylistId, fallbackPlaylistTitle),
            selectedPlaylistEmptyText = text(languageMode, "no.tracks.in.playlist"),
            selectedPlaylistEmptyDescription = text(languageMode, "no.tracks.in.playlist.description"),
            selectedPlaylistTopActions = selectedPlaylistActionRows,
            selectedPlaylistTracks = selectedPlaylistRows,
            actions = emptyCollectionsActions(),
            favoriteLabel = text(languageMode, "favorite"),
            removeFavoriteLabel = text(languageMode, "remove.favorite"),
            addToPlaylistLabel = text(languageMode, "add.to.playlist"),
            renameLabel = text(languageMode, "rename"),
            deleteLabel = text(languageMode, "delete"),
            upLabel = text(languageMode, "up"),
            downLabel = text(languageMode, "down"),
            removeLabel = text(languageMode, "remove"),
            playlistFolders = playlistFolders,
            favoriteSync = FavoriteSyncUiState(
                lastSyncText = if (favoriteSync.lastSyncAtMs <= 0L) "尚未同步"
                    else "最近同步 ${formatDateTime(favoriteSync.lastSyncAtMs)}",
                pendingText = "待同步 ${favoriteSync.pendingCount}",
                failureText = "失败 ${favoriteSync.failureCount}",
                running = favoriteSync.running,
                autoSync = favoriteSync.preferences.autoSyncEnabled,
                syncOnForeground = favoriteSync.preferences.syncOnForeground,
                periodicSync = favoriteSync.preferences.periodicSyncEnabled,
                wifiOnly = favoriteSync.preferences.wifiOnly,
                propagateRemovals = favoriteSync.preferences.propagateRemovals,
                confirmLowConfidence = favoriteSync.preferences.confirmLowConfidence,
                sources = favoriteSync.sources.map { source ->
                    FavoriteSyncSourceUiState(
                        sourceKey = source.sourceKey,
                        providerName = source.providerName,
                        sourceName = source.sourceName,
                        selected = source.selected,
                        supported = source.supported,
                        loggedIn = source.loggedIn,
                        statusText = source.statusText
                    )
                }
            )
        )
        val actions = CollectionsActions(
            Runnable { listener.requestBack() },
            topActions,
            trackSectionActions,
            playlistActions,
            selectedPlaylistActions,
            selectedPlaylistTrackActions,
            FavoriteSyncActions(
                onSyncNow = Runnable { favoriteSyncViewModel?.syncNow() },
                onAutoSyncChanged = { favoriteSyncViewModel?.setAutoSync(it) },
                onForegroundChanged = { favoriteSyncViewModel?.setSyncOnForeground(it) },
                onPeriodicChanged = { favoriteSyncViewModel?.setPeriodicSync(it) },
                onWifiOnlyChanged = { favoriteSyncViewModel?.setWifiOnly(it) },
                onPropagateRemovalsChanged = { favoriteSyncViewModel?.setPropagateRemovals(it) },
                onConfirmLowConfidenceChanged = { favoriteSyncViewModel?.setConfirmLowConfidence(it) },
                onSourceChanged = { sourceKey, enabled ->
                    favoriteSyncViewModel?.setSourceEnabled(sourceKey, enabled)
                },
                onClearSource = { sourceKey -> favoriteSyncViewModel?.clearSource(sourceKey) }
            )
        )
        viewModel.updateScreenWithActions(state, actions)
    }

    private fun addCollectionAction(
        rows: ArrayList<CollectionActionUiState>,
        actions: ArrayList<Runnable>,
        label: String,
        icon: EchoIconKind,
        action: Runnable
    ) {
        rows.add(CollectionActionUiState(label, icon))
        actions.add(action)
    }

    private fun addCollectionTrackSection(
        sections: ArrayList<CollectionTrackSectionUiState>,
        sectionActions: ArrayList<CollectionTrackSectionActions>,
        key: String,
        title: String,
        tracks: List<Track>,
        emptyText: String,
        emptyDescription: String,
        playActionLabel: String,
        details: List<String>?,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        languageMode: String
    ) {
        val rows = ArrayList<TrackRowUiState>()
        val rowActions = ArrayList<TrackRowActions>()
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    currentTrack,
                    favoriteIds,
                    if (details != null && index < details.size) details[index] else "",
                    true,
                    rowKeys[index],
                    unsupportedFormatLabel = text(languageMode, "local.audio.unsupported")
                )
            )
            rowActions.add(
                TrackRowActions(
                    Runnable { playSupportedAt(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.downloadTrack(track) }
                )
            )
        }
        sections.add(CollectionTrackSectionUiState(key, title, emptyText, emptyDescription, playActionLabel, rows))
        sectionActions.add(CollectionTrackSectionActions(Runnable {
            playFirstSupported(tracks)
        }, rowActions))
    }

    private fun recordDetails(records: List<TrackPlayRecord>, showPlayCount: Boolean, languageMode: String): ArrayList<String> {
        val details = ArrayList<String>()
        for (record in records) {
            details.add(
                if (showPlayCount) {
                    playCountLabel(record.playCount, languageMode)
                } else {
                    text(languageMode, "played.at") + formatDateTime(record.playedAt)
                }
            )
        }
        return details
    }

    private fun buildPlaylistRows(
        rows: ArrayList<PlaylistRowUiState>,
        actions: ArrayList<PlaylistRowActions>,
        playlistsToRender: List<Playlist>,
        selectedPlaylistId: Long,
        languageMode: String
    ) {
        for (playlist in playlistsToRender) {
            val actionIndex = actions.size
            rows.add(
                CollectionRowStateFactory.playlistRow(playlist, selectedPlaylistId, languageMode)
                    .copy(actionIndex = actionIndex)
            )
            actions.add(
                PlaylistRowActions(
                    Runnable { listener.selectPlaylist(playlist.id) },
                    Runnable { listener.showRenamePlaylist(playlist) },
                    Runnable { listener.confirmDeletePlaylist(playlist) }
                )
            )
        }
    }

    private fun buildPlaylistFolders(
        playlists: List<Playlist>,
        rows: List<PlaylistRowUiState>,
        playlistSources: Map<Long, StreamingProviderName>,
        languageMode: String
    ): List<CollectionPlaylistFolderUiState> {
        val grouped = linkedMapOf<StreamingProviderName?, MutableList<Pair<Playlist, PlaylistRowUiState>>>()
        for (index in playlists.indices) {
            val playlist = playlists[index]
            val row = rows.getOrNull(index) ?: continue
            grouped.getOrPut(playlistSources[playlist.id]) { ArrayList() }.add(playlist to row)
        }
        return grouped.entries
            .sortedBy { playlistSourceOrder(it.key) }
            .map { (provider, entries) ->
                val trackCount = entries.sumOf { it.first.trackCount }
                CollectionPlaylistFolderUiState(
                    key = provider?.wireName ?: "local",
                    title = playlistSourceTitle(provider, languageMode),
                    subtitle = playlistFolderSummary(entries.size, trackCount, languageMode),
                    playlists = entries.map { it.second }
                )
            }
    }

    private fun playlistSourceOrder(provider: StreamingProviderName?): Int = when (provider) {
        null -> 0
        StreamingProviderName.NETEASE -> 10
        StreamingProviderName.QQ_MUSIC -> 20
        StreamingProviderName.LUOXUE -> 30
        StreamingProviderName.KUGOU -> 40
        StreamingProviderName.BILIBILI -> 50
        StreamingProviderName.YOUTUBE -> 60
        StreamingProviderName.SOUNDCLOUD -> 70
        StreamingProviderName.SPOTIFY -> 80
        StreamingProviderName.TIDAL -> 90
        StreamingProviderName.M3U8 -> 100
        StreamingProviderName.PLUGIN -> 110
        StreamingProviderName.MOCK -> 120
    }

    private fun playlistSourceTitle(provider: StreamingProviderName?, languageMode: String): String {
        if (provider == null) {
            return text(languageMode, "playlist.source.local")
        }
        val chinese = AppLanguage.isChinese(languageMode)
        return when (provider) {
            StreamingProviderName.NETEASE -> if (chinese) "\u7f51\u6613\u4e91\u97f3\u4e50" else "NetEase Cloud Music"
            StreamingProviderName.QQ_MUSIC -> if (chinese) "QQ \u97f3\u4e50" else "QQ Music"
            StreamingProviderName.KUGOU -> if (chinese) "\u9177\u72d7\u97f3\u4e50" else "Kugou Music"
            StreamingProviderName.BILIBILI -> "bilibili"
            StreamingProviderName.YOUTUBE -> "YouTube"
            StreamingProviderName.SOUNDCLOUD -> "SoundCloud"
            StreamingProviderName.SPOTIFY -> "Spotify"
            StreamingProviderName.TIDAL -> "TIDAL"
            StreamingProviderName.M3U8 -> "M3U8"
            StreamingProviderName.LUOXUE -> if (chinese) "\u6d1b\u96ea\u97f3\u6e90" else "LX Music Source"
            StreamingProviderName.PLUGIN -> if (chinese) "\u81ea\u5b9a\u4e49\u63d2\u4ef6" else "Custom plugins"
            StreamingProviderName.MOCK -> "Mock"
        }
    }

    private fun playlistFolderSummary(playlistCount: Int, trackCount: Int, languageMode: String): String {
        val playlistLabel = if (playlistCount == 1) {
            text(languageMode, "playlist.folder.count.one")
        } else {
            text(languageMode, "playlist.folder.count.prefix") + playlistCount +
                text(languageMode, "playlist.folder.count.suffix")
        }
        return playlistLabel + " \u00b7 " + CollectionRowStateFactory.trackCountLabel(trackCount, languageMode)
    }

    private fun buildSelectedPlaylistRows(
        rows: ArrayList<PlaylistTrackUiState>,
        actions: ArrayList<PlaylistTrackActions>,
        playlistIdForRows: Long,
        selectedPlaylistTracks: List<Track>,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        languageMode: String
    ) {
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(selectedPlaylistTracks)
        for (index in selectedPlaylistTracks.indices) {
            val track = selectedPlaylistTracks[index]
            rows.add(
                TrackRowStateFactory.playlistRow(
                    rowKeys[index],
                    track,
                    currentTrack,
                    favoriteIds,
                    index > 0,
                    index < selectedPlaylistTracks.size - 1,
                    text(languageMode, "local.audio.unsupported")
                )
            )
            actions.add(
                PlaylistTrackActions(
                    Runnable { playSupportedAt(selectedPlaylistTracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.downloadTrack(track) },
                    Runnable { listener.moveSelectedPlaylistTrack(playlistIdForRows, track, index, -1) },
                    Runnable { listener.moveSelectedPlaylistTrack(playlistIdForRows, track, index, 1) },
                    Runnable { listener.removeSelectedPlaylistTrack(playlistIdForRows, track) }
                )
            )
        }
    }

    private fun playFirstSupported(tracks: List<Track>) {
        val playable = tracks.filter(LocalAudioFormatPolicy::isPlaybackAllowed)
        if (playable.isNotEmpty()) listener.playTrackList(playable, 0)
    }

    private fun playSupportedAt(tracks: List<Track>, sourceIndex: Int) {
        if (sourceIndex !in tracks.indices || !LocalAudioFormatPolicy.isPlaybackAllowed(tracks[sourceIndex])) return
        val playableIndices = tracks.indices.filter { LocalAudioFormatPolicy.isPlaybackAllowed(tracks[it]) }
        val targetIndex = playableIndices.indexOf(sourceIndex)
        if (targetIndex >= 0) listener.playTrackList(playableIndices.map(tracks::get), targetIndex)
    }

    private fun tracksFromRecords(records: List<TrackPlayRecord>): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        for (record in records) {
            tracks.add(record.track)
        }
        return tracks
    }

    private fun selectedPlaylistName(playlists: List<Playlist>, selectedPlaylistId: Long, fallbackPlaylistTitle: String): String {
        for (playlist in playlists) {
            if (playlist.id == selectedPlaylistId) {
                return playlist.name
            }
        }
        return fallbackPlaylistTitle
    }

    private fun playCountLabel(count: Int, languageMode: String): String =
        if (count == 1) {
            text(languageMode, "played.once")
        } else {
            text(languageMode, "played.times.prefix") + count + text(languageMode, "played.times.suffix")
        }

    private fun formatDateTime(timestampMs: Long): String {
        if (timestampMs <= 0L) {
            return ""
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestampMs))
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}

private data class CollectionsBindingRoute(
    val active: Boolean,
    val selectedPlaylistId: Long
)

private data class CollectionsBindingInputs(
    val route: CollectionsBindingRoute,
    val library: LibraryStoreState,
    val languageMode: String,
    val insights: CollectionsInsightSnapshot,
    val favoriteSync: FavoriteSyncDashboard = FavoriteSyncDashboard()
)

private fun collectionsBindingRoute(state: NavigationRouteState): CollectionsBindingRoute =
    CollectionsBindingRoute(
        active = state.selectedTab == CollectionsTab,
        selectedPlaylistId = state.selectedPlaylistId
    )
