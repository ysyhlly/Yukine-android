package app.yukine

import app.yukine.model.LocalAudioFormatPolicy
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderActionKind
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.EchoIconKind
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.LibraryMode
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibraryUiState
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

class TrackListStateReducer(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    private var rowBuildJob: Job? = null
    private val buildGeneration = AtomicLong()
    private var lastSearchQuery = ""
    private var fastPatchTracks: List<Track>? = null
    private var fastPatchSignature: SongRowsSignature? = null
    private var latestCurrentTrackId: Long? = null
    private var latestFavoriteIds: Set<Long> = emptySet()
    private var latestFavoritePendingIds: Set<Long> = emptySet()
    private var publishedCurrentTrackId: Long? = null
    private var publishedFavoriteIds: Set<Long> = emptySet()
    private var publishedFavoritePendingIds: Set<Long> = emptySet()
    private var rowIndicesByTrackId: Map<Long, List<Int>> = emptyMap()

    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun showRecordingMatch(track: Track) = Unit

        fun downloadTrack(track: Track)

        fun downloadTracks(tracks: List<Track>)

        fun showEditStream(track: Track)

        fun confirmDeleteTrack(track: Track)
    }

    fun reduce(
        title: String,
        tracks: List<Track>,
        showPlaylistAction: Boolean,
        details: List<String>,
        showStreamActions: Boolean,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        footerAlbums: List<TrackListAlbumCardUiState> = emptyList(),
        context: LibraryListContext = LibraryListContext.Songs,
        favoritePendingIds: Set<Long> = emptySet(),
        playCountByTrackId: Map<Long, Int> = emptyMap()
    ) {
        val libraryUi = viewModel.libraryUi.value
        latestCurrentTrackId = playbackState?.currentTrack?.id
        latestFavoriteIds = favoriteIds
        latestFavoritePendingIds = favoritePendingIds
        val fastSignature = songRowsSignature(
            title,
            details,
            showPlaylistAction,
            showStreamActions,
            headerMetrics,
            headerActions,
            emptyText,
            modeActions,
            labels,
            footerAlbums,
            libraryUi,
            favoriteIds,
            context
        )
        if (fastSignature != null && tracks === fastPatchTracks && fastSignature == fastPatchSignature) {
            patchPublishedRowFlags()
            return
        }
        fastPatchTracks = if (fastSignature == null) null else tracks
        fastPatchSignature = fastSignature
        val searchQueryChanged = lastSearchQuery != libraryUi.query
        lastSearchQuery = libraryUi.query
        publishTrackList(title, tracks.size, searchQueryChanged) {
            val presented = LibraryTrackPresentationPolicy.present(
                tracks,
                details,
                libraryUi,
                favoriteIds,
                playCountByTrackId
            )
            val visibleTracks = presented.map { it.track }
            val visibleDetails = presented.map { it.detail }
            buildTrackListContent(
                visibleTracks,
                showPlaylistAction,
                visibleDetails,
                showStreamActions,
                headerMetrics,
                headerActions,
                emptyText,
                modeActions,
                labels,
                playbackState,
                favoriteIds,
                favoritePendingIds,
                footerAlbums,
                libraryUi.mode,
                context
            )
        }
    }

    fun updateFavoritePendingIds(favoritePendingIds: Set<Long>) {
        latestFavoritePendingIds = favoritePendingIds
        patchPublishedRowFlags()
    }

    fun reduceRecommendation(title: String, tracks: List<Track>) {
        reduceRecommendation(title, tracks, AppLanguage.MODE_CHINESE)
    }

    fun reduceRecommendation(title: String, tracks: List<Track>, languageMode: String) {
        fastPatchTracks = null
        fastPatchSignature = null
        val headerMetrics = listOf(TrackListHeaderMetric(AppLanguage.text(languageMode, "tracks"), "${tracks.size}"))
        val headerActions = listOf(
            TrackListHeaderAction(
                AppLanguage.text(languageMode, "download.current.list"),
                Runnable { listener.downloadTracks(tracks) },
                icon = EchoIconKind.Download,
                kind = TrackListHeaderActionKind.DownloadCurrentList
            )
        )
        val labels = TrackListLabels(
            AppLanguage.text(languageMode, "favorite"),
            AppLanguage.text(languageMode, "remove.favorite"),
            AppLanguage.text(languageMode, "add.to.playlist"),
            AppLanguage.text(languageMode, "edit"),
            AppLanguage.text(languageMode, "delete"),
            AppLanguage.text(languageMode, "download"),
            AppLanguage.text(languageMode, "download.current.list"),
            AppLanguage.text(languageMode, "all.albums"),
            AppLanguage.text(languageMode, "play.all"),
            AppLanguage.text(languageMode, "shuffle"),
            AppLanguage.text(languageMode, "recording.match.manage"),
            AppLanguage.text(languageMode, "songs"),
            AppLanguage.text(languageMode, "more"),
            AppLanguage.text(languageMode, "library.favorite.updating"),
            AppLanguage.text(languageMode, "local.audio.unsupported")
        )
        publishTrackList(title, tracks.size, false) {
            buildRecommendationContent(tracks, headerMetrics, headerActions, labels)
        }
    }

    private fun publishTrackList(
        title: String,
        trackCount: Int,
        debounceSearch: Boolean,
        buildContent: () -> BuiltTrackListContent
    ) {
        val generation = buildGeneration.incrementAndGet()
        rowBuildJob?.cancel()
        viewModel.presentation.clearLibraryGroups()
        if (trackCount < BACKGROUND_ROW_BUILD_THRESHOLD && !debounceSearch) {
            publishTrackListContent(title, buildContent())
            return
        }
        rowBuildJob = viewModel.viewModelScope.launch {
            if (debounceSearch) delay(SEARCH_DEBOUNCE_MS)
            val content = withContext(Dispatchers.Default) { buildContent() }
            if (generation != buildGeneration.get()) return@launch
            publishTrackListContent(title, content)
        }
    }

    private fun publishTrackListContent(title: String, content: BuiltTrackListContent) {
        val rows = patchRowsForLatestState(content)
        rowIndicesByTrackId = content.rowIndicesByTrackId
        publishedCurrentTrackId = latestCurrentTrackId
        publishedFavoriteIds = latestFavoriteIds
        publishedFavoritePendingIds = latestFavoritePendingIds
        viewModel.presentation.updateVisibleTrackTargets(content.tracks, rows.map { it.key })
        viewModel.presentation.updateTrackListContentAndChrome(
            title,
            rows,
            content.footerAlbums,
            content.actions,
            content.headerMetrics,
            content.headerActions,
            content.emptyText,
            content.modeActions,
            content.labels,
            content.context
        )
    }

    /**
     * Row building already captured playback/favorite state off the main thread. Only patch the
     * handful of rows whose flags changed while that build was running instead of mapping the
     * complete library again on publication.
     */
    private fun patchRowsForLatestState(content: BuiltTrackListContent): List<TrackRowUiState> {
        val affectedTrackIds = HashSet<Long>()
        if (content.currentTrackId != latestCurrentTrackId) {
            content.currentTrackId?.let(affectedTrackIds::add)
            latestCurrentTrackId?.let(affectedTrackIds::add)
        }
        if (content.favoriteIds !== latestFavoriteIds) {
            content.favoriteIds.forEach { id ->
                if (id !in latestFavoriteIds) affectedTrackIds += id
            }
            latestFavoriteIds.forEach { id ->
                if (id !in content.favoriteIds) affectedTrackIds += id
            }
        }
        if (content.favoritePendingIds !== latestFavoritePendingIds) {
            content.favoritePendingIds.forEach { id ->
                if (id !in latestFavoritePendingIds) affectedTrackIds += id
            }
            latestFavoritePendingIds.forEach { id ->
                if (id !in content.favoritePendingIds) affectedTrackIds += id
            }
        }
        if (affectedTrackIds.isEmpty()) return content.rows

        val rows = content.rows.toMutableList()
        var changed = false
        affectedTrackIds.forEach { trackId ->
            content.rowIndicesByTrackId[trackId].orEmpty().forEach { index ->
                if (index !in rows.indices) return@forEach
                val row = rows[index]
                val current = row.id == latestCurrentTrackId
                val favorite = row.id in latestFavoriteIds
                val favoritePending = row.id in latestFavoritePendingIds
                if (row.current != current || row.favorite != favorite ||
                    row.favoritePending != favoritePending
                ) {
                    rows[index] = row.copy(
                        current = current,
                        favorite = favorite,
                        favoritePending = favoritePending
                    )
                    changed = true
                }
            }
        }
        return if (changed) rows else content.rows
    }

    private fun patchPublishedRowFlags() {
        if (rowBuildJob?.isActive == true) return
        val affectedTrackIds = HashSet<Long>()
        publishedCurrentTrackId?.let(affectedTrackIds::add)
        latestCurrentTrackId?.let(affectedTrackIds::add)
        publishedFavoriteIds.forEach { id ->
            if (id !in latestFavoriteIds) affectedTrackIds += id
        }
        latestFavoriteIds.forEach { id ->
            if (id !in publishedFavoriteIds) affectedTrackIds += id
        }
        publishedFavoritePendingIds.forEach { id ->
            if (id !in latestFavoritePendingIds) affectedTrackIds += id
        }
        latestFavoritePendingIds.forEach { id ->
            if (id !in publishedFavoritePendingIds) affectedTrackIds += id
        }
        if (affectedTrackIds.isEmpty()) return
        val state = viewModel.trackList.value
        val rows = state.rows.toMutableList()
        var changed = false
        affectedTrackIds.forEach { trackId ->
            rowIndicesByTrackId[trackId].orEmpty().forEach { index ->
                if (index !in rows.indices) return@forEach
                val row = rows[index]
                val current = row.id == latestCurrentTrackId
                val favorite = row.id in latestFavoriteIds
                val favoritePending = row.id in latestFavoritePendingIds
                if (row.current != current || row.favorite != favorite ||
                    row.favoritePending != favoritePending
                ) {
                    rows[index] = row.copy(
                        current = current,
                        favorite = favorite,
                        favoritePending = favoritePending
                    )
                    changed = true
                }
            }
        }
        publishedCurrentTrackId = latestCurrentTrackId
        publishedFavoriteIds = latestFavoriteIds
        publishedFavoritePendingIds = latestFavoritePendingIds
        if (changed) {
            viewModel.presentation.updateTrackList(state.title, rows, state.footerAlbums)
        }
    }

    private fun songRowsSignature(
        title: String,
        details: List<String>,
        showPlaylistAction: Boolean,
        showStreamActions: Boolean,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels,
        footerAlbums: List<TrackListAlbumCardUiState>,
        libraryUi: LibraryUiState,
        favoriteIds: Set<Long>,
        context: LibraryListContext
    ): SongRowsSignature? {
        if (libraryUi.mode != LibraryMode.Songs || modeActions.isEmpty() || details.isNotEmpty() ||
            headerMetrics.isNotEmpty() || headerActions.isNotEmpty() || footerAlbums.isNotEmpty()
        ) {
            return null
        }
        return SongRowsSignature(
            title,
            showPlaylistAction,
            showStreamActions,
            emptyText,
            labels,
            libraryUi,
            if (libraryUi.filter == LibraryFilter.Favorites) favoriteIds else null,
            context
        )
    }

    private fun buildTrackListContent(
        tracks: List<Track>,
        showPlaylistAction: Boolean,
        details: List<String>,
        showStreamActions: Boolean,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        favoritePendingIds: Set<Long>,
        footerAlbums: List<TrackListAlbumCardUiState>,
        libraryMode: LibraryMode,
        context: LibraryListContext
    ): BuiltTrackListContent {
        val rows = ArrayList<TrackRowUiState>(tracks.size)
        val actions = ArrayList<TrackRowActions>(tracks.size)
        val effectiveHeaderActions = ArrayList(headerActions)
        val isSongsRoot = libraryMode == LibraryMode.Songs && modeActions.isNotEmpty()
        if (tracks.isNotEmpty() && isSongsRoot) {
            if (effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.PlayAll }) {
                effectiveHeaderActions.add(
                    TrackListHeaderAction(
                        labels.playAllLabel,
                        Runnable { playFirstSupported(tracks) },
                        icon = EchoIconKind.Play,
                        kind = TrackListHeaderActionKind.PlayAll
                    )
                )
            }
            if (effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.Shuffle }) {
                effectiveHeaderActions.add(
                    TrackListHeaderAction(
                        labels.shuffleLabel,
                        Runnable { playFirstSupported(tracks.filter(LocalAudioFormatPolicy::isPlaybackAllowed).shuffled()) },
                        icon = EchoIconKind.Shuffle,
                        kind = TrackListHeaderActionKind.Shuffle
                    )
                )
            }
        }
        if (tracks.isNotEmpty() && !isSongsRoot &&
            effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.DownloadCurrentList }
        ) {
            effectiveHeaderActions.add(
                TrackListHeaderAction(
                    labels.downloadCurrentListLabel,
                    Runnable { listener.downloadTracks(tracks) },
                    icon = EchoIconKind.Download,
                    kind = TrackListHeaderActionKind.DownloadCurrentList
                )
            )
        }
        val currentTrack = playbackState?.currentTrack
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    currentTrack,
                    favoriteIds,
                    if (index < details.size) details[index] else "",
                    showPlaylistAction,
                    rowKeys[index],
                    favoritePendingIds,
                    labels.unsupportedFormatLabel
                )
            )
            actions.add(
                TrackRowActions(
                    onPlay = Runnable { playSupportedAt(tracks, index) },
                    onFavorite = Runnable { listener.toggleFavorite(track) },
                    onAddToPlaylist = Runnable { listener.showAddToPlaylist(track) },
                    onDownload = Runnable { listener.downloadTrack(track) },
                    onEdit = if (showStreamActions) Runnable { listener.showEditStream(track) } else null,
                    onDelete = Runnable { listener.confirmDeleteTrack(track) },
                    onLongPress = Runnable { listener.confirmDeleteTrack(track) },
                    onMatchManagement = Runnable { listener.showRecordingMatch(track) }
                )
            )
        }
        return BuiltTrackListContent(
            tracks,
            rows,
            rowIndices(rows),
            currentTrack?.id,
            favoriteIds,
            favoritePendingIds,
            footerAlbums,
            actions,
            headerMetrics,
            effectiveHeaderActions,
            emptyText,
            modeActions,
            labels,
            context
        )
    }

    private fun buildRecommendationContent(
        tracks: List<Track>,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        labels: TrackListLabels
    ): BuiltTrackListContent {
        val rows = ArrayList<TrackRowUiState>(tracks.size)
        val actions = ArrayList<TrackRowActions>(tracks.size)
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    null,
                    emptySet(),
                    "",
                    true,
                    rowKeys[index],
                    unsupportedFormatLabel = labels.unsupportedFormatLabel
                )
            )
            actions.add(
                TrackRowActions(
                    onPlay = Runnable { playSupportedAt(tracks, index) },
                    onFavorite = Runnable { listener.toggleFavorite(track) },
                    onAddToPlaylist = Runnable { listener.showAddToPlaylist(track) },
                    onDownload = Runnable { listener.downloadTrack(track) },
                    onEdit = null,
                    onDelete = null,
                    onLongPress = null,
                    onMatchManagement = Runnable { listener.showRecordingMatch(track) }
                )
            )
        }
        return BuiltTrackListContent(
            tracks,
            rows,
            rowIndices(rows),
            null,
            emptySet(),
            emptySet(),
            emptyList(),
            actions,
            headerMetrics,
            headerActions,
            "",
            emptyList(),
            labels,
            LibraryListContext.Songs
        )
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

    private data class BuiltTrackListContent(
        val tracks: List<Track>,
        val rows: List<TrackRowUiState>,
        val rowIndicesByTrackId: Map<Long, List<Int>>,
        val currentTrackId: Long?,
        val favoriteIds: Set<Long>,
        val favoritePendingIds: Set<Long>,
        val footerAlbums: List<TrackListAlbumCardUiState>,
        val actions: List<TrackRowActions>,
        val headerMetrics: List<TrackListHeaderMetric>,
        val headerActions: List<TrackListHeaderAction>,
        val emptyText: String,
        val modeActions: List<TrackListModeAction>,
        val labels: TrackListLabels,
        val context: LibraryListContext
    )

    private fun rowIndices(rows: List<TrackRowUiState>): Map<Long, List<Int>> {
        val indices = LinkedHashMap<Long, MutableList<Int>>()
        rows.forEachIndexed { index, row ->
            indices.getOrPut(row.id) { ArrayList(1) }.add(index)
        }
        return indices
    }

    private data class SongRowsSignature(
        val title: String,
        val showPlaylistAction: Boolean,
        val showStreamActions: Boolean,
        val emptyText: String,
        val labels: TrackListLabels,
        val libraryUi: LibraryUiState,
        val favoriteFilterIds: Set<Long>?,
        val context: LibraryListContext
    )

    private companion object {
        const val BACKGROUND_ROW_BUILD_THRESHOLD = 200
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
