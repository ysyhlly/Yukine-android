package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryPlaylistFolderEntryUiState
import app.yukine.ui.LibraryPlaylistFolderUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.EchoIconKind
import app.yukine.ui.LibraryFilter
import app.yukine.streaming.StreamingProviderName
import java.util.ArrayList
import java.util.LinkedHashMap

class LibraryPlaylistsStateReducer(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    private val favoritesKey = "virtual:favorites"
    private val historyKey = HISTORY_GROUP_KEY

    interface Listener {
        fun openFavoritePlaylist(title: String)

        fun openPlayHistory(title: String)

        fun openPlaylist(playlistId: Long, title: String)

        fun playPlaylist(playlistId: Long)

        fun confirmDeletePlaylist(playlist: Playlist)

        fun backFromPlaylist()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState)

        fun publishPlaylistTracks(request: LibraryPlaylistTrackListRequest)
    }

    fun reduce(
        languageMode: String,
        playlists: List<Playlist>,
        selectedPlaylistId: Long,
        selectedLibraryGroupKey: String,
        selectedPlaylistName: String,
        selectedPlaylistTracks: List<Track>,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>,
        playlistSources: Map<Long, StreamingProviderName> = emptyMap(),
        recentlyAddedTracks: List<Track> = emptyList(),
        longUnplayedTracks: List<Track> = emptyList()
    ) {
        if (selectedLibraryGroupKey == favoritesKey) {
            reduceFavoriteTracks(languageMode, favoriteTracks, modeActions)
            return
        }
        if (selectedLibraryGroupKey == historyKey) {
            reducePlayHistory(languageMode, recentRecords, modeActions)
            return
        }
        if (selectedLibraryGroupKey == SMART_RECENT_ADDED_KEY) {
            reduceSmartCollection(
                languageMode,
                "library.smart.recent.added",
                "library.empty.smart.recent.added",
                recentlyAddedTracks,
                modeActions
            )
            return
        }
        if (selectedLibraryGroupKey == SMART_WEEK_FAVORITES_KEY) {
            reduceSmartCollection(
                languageMode,
                "library.smart.week.favorites",
                "library.empty.smart.week.favorites",
                tracksFromRecords(weekFavoriteRecords(recentRecords, System.currentTimeMillis())),
                modeActions
            )
            return
        }
        if (selectedLibraryGroupKey == SMART_LONG_UNPLAYED_KEY) {
            reduceSmartCollection(
                languageMode,
                "library.smart.long.unplayed",
                "library.empty.smart.long.unplayed",
                longUnplayedTracks,
                modeActions
            )
            return
        }
        if (selectedPlaylistId >= 0L && selectedLibraryGroupKey.startsWith("playlist:")) {
            publishPlaylistTracks(languageMode, selectedPlaylistName, selectedPlaylistTracks, modeActions)
            return
        }
        reducePlaylists(languageMode, playlists, favoriteTracks, recentRecords, modeActions, playlistSources)
    }

    private fun reducePlaylists(
        languageMode: String,
        playlists: List<Playlist>,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>,
        playlistSources: Map<Long, StreamingProviderName>
    ) {
        val rows = ArrayList<LibraryGroupUiState>()
        val actions = ArrayList<LibraryGroupActions>()
        val favoriteTitle = AppLanguage.text(languageMode, "favorite.playlist")
        rows.add(
            LibraryGroupUiState(
                favoritesKey,
                favoriteTitle,
                CollectionRowStateFactory.trackCountLabel(favoriteTracks.size, languageMode),
                trackCount = favoriteTracks.size,
                groupKey = favoritesKey
            )
        )
        actions.add(
            LibraryGroupActions(
                Runnable { listener.openFavoritePlaylist(favoriteTitle) },
                Runnable { listener.playTrackList(favoriteTracks, 0) },
                favoriteTracks.isNotEmpty(),
                null
            )
        )
        val historyTracks = tracksFromRecords(recentRecords)
        val historyTitle = AppLanguage.text(languageMode, "play.history.playlist")
        rows.add(
            LibraryGroupUiState(
                historyKey,
                historyTitle,
                CollectionRowStateFactory.trackCountLabel(historyTracks.size, languageMode),
                trackCount = historyTracks.size,
                groupKey = historyKey
            )
        )
        actions.add(
            LibraryGroupActions(
                Runnable { listener.openPlayHistory(historyTitle) },
                Runnable { listener.playTrackList(historyTracks, 0) },
                historyTracks.isNotEmpty(),
                null
            )
        )
        val groupedPlaylists = LinkedHashMap<
            StreamingProviderName?,
            MutableList<Pair<LibraryPlaylistFolderEntryUiState, Int>>
        >()
        val uiState = viewModel.libraryUi.value
        val visiblePlaylists = playlists.filter { playlist ->
            val queryMatches = uiState.query.isBlank() ||
                playlist.name.contains(uiState.query, ignoreCase = true)
            val provider = playlistSources[playlist.id]
            val filterMatches = when (uiState.filter) {
                LibraryFilter.All -> true
                LibraryFilter.Favorites -> false
                LibraryFilter.Local -> provider == null
                LibraryFilter.Network -> provider != null
            }
            queryMatches && filterMatches
        }
        val sortedPlaylists = LibraryGroupSortPolicy.sort(
            items = visiblePlaylists,
            sort = uiState.groupSort,
            languageMode = languageMode,
            stableId = { playlist -> playlist.id.toString() },
            title = { playlist -> playlist.name },
            trackCount = { playlist -> playlist.trackCount }
        )
        for (playlist in sortedPlaylists) {
            val row = LibraryGroupUiState(
                "playlist:${playlist.id}",
                playlist.name,
                CollectionRowStateFactory.trackCountLabel(playlist.trackCount, languageMode),
                trackCount = playlist.trackCount,
                groupKey = playlist.id.toString()
            )
            val actionIndex = actions.size
            actions.add(
                LibraryGroupActions(
                    Runnable { listener.openPlaylist(playlist.id, playlist.name) },
                    Runnable { listener.playPlaylist(playlist.id) },
                    playlist.trackCount > 0,
                    Runnable { listener.confirmDeletePlaylist(playlist) }
                )
            )
            groupedPlaylists.getOrPut(playlistSources[playlist.id]) { ArrayList() }
                .add(LibraryPlaylistFolderEntryUiState(row, actionIndex) to playlist.trackCount)
        }
        val playlistFolders = groupedPlaylists.entries
            .sortedBy { playlistSourceOrder(it.key) }
            .map { (provider, entries) ->
                LibraryPlaylistFolderUiState(
                    key = provider?.wireName ?: "local",
                    title = playlistSourceTitle(provider, languageMode),
                    subtitle = playlistFolderSubtitle(entries.size, entries.sumOf { it.second }, languageMode),
                    entries = entries.map { it.first }
                )
            }

        val title = AppLanguage.text(languageMode, "playlists")
        viewModel.presentation.clearTrackList()
        viewModel.presentation.updateLibraryGroups(title, rows, playlistFolders)
        listener.publishLibraryGroupsChrome(
            LibraryGroupsChromeState(
                actions = actions,
                emptyText = AppLanguage.text(languageMode, "no.playlists"),
                modeActions = ArrayList(modeActions)
            )
        )
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
            return if (AppLanguage.MODE_ENGLISH == languageMode) "Local source" else "本地音源"
        }
        val english = AppLanguage.MODE_ENGLISH == languageMode
        return when (provider) {
            StreamingProviderName.NETEASE -> if (english) "NetEase Cloud Music" else "网易云音乐"
            StreamingProviderName.QQ_MUSIC -> if (english) "QQ Music" else "QQ 音乐"
            StreamingProviderName.KUGOU -> if (english) "Kugou Music" else "酷狗音乐"
            StreamingProviderName.BILIBILI -> "bilibili"
            StreamingProviderName.YOUTUBE -> "YouTube"
            StreamingProviderName.SOUNDCLOUD -> "SoundCloud"
            StreamingProviderName.SPOTIFY -> "Spotify"
            StreamingProviderName.TIDAL -> "TIDAL"
            StreamingProviderName.M3U8 -> "M3U8"
            StreamingProviderName.LUOXUE -> if (english) "LX Music Source" else "洛雪音源"
            StreamingProviderName.PLUGIN -> if (english) "Custom plugins" else "自定义插件"
            StreamingProviderName.MOCK -> "Mock"
        }
    }

    private fun playlistFolderSubtitle(playlistCount: Int, trackCount: Int, languageMode: String): String {
        return if (AppLanguage.MODE_ENGLISH == languageMode) {
            "$playlistCount ${if (playlistCount == 1) "playlist" else "playlists"} · " +
                CollectionRowStateFactory.trackCountLabel(trackCount, languageMode)
        } else {
            "$playlistCount 个歌单 · " + CollectionRowStateFactory.trackCountLabel(trackCount, languageMode)
        }
    }

    private fun reducePlayHistory(
        languageMode: String,
        records: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>
    ) {
        val tracks = tracksFromRecords(records)
        val title = AppLanguage.text(languageMode, "play.history.playlist")
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            }, icon = EchoIconKind.Back, isBack = true)
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.recent"), Runnable {
                    listener.playTrackList(tracks, 0)
                }, icon = EchoIconKind.Play)
            )
        }
            listener.publishPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = title,
                tracks = tracks,
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.recent.tracks"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun reduceFavoriteTracks(
        languageMode: String,
        tracks: List<Track>,
        modeActions: List<TrackListModeAction>
    ) {
        val title = AppLanguage.text(languageMode, "favorite.playlist")
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            }, icon = EchoIconKind.Back, isBack = true)
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.favorites"), Runnable {
                    listener.playTrackList(tracks, 0)
                }, icon = EchoIconKind.Play)
            )
        }
        listener.publishPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = title,
                tracks = ArrayList(tracks),
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.favorites"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun publishPlaylistTracks(
        languageMode: String,
        selectedPlaylistName: String,
        tracks: List<Track>,
        modeActions: List<TrackListModeAction>
    ) {
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            }, icon = EchoIconKind.Back, isBack = true)
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.playlist"), Runnable {
                    listener.playTrackList(tracks, 0)
                }, icon = EchoIconKind.Play)
            )
        }
        listener.publishPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = selectedPlaylistName,
                tracks = ArrayList(tracks),
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.tracks.in.playlist"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun reduceSmartCollection(
        languageMode: String,
        titleKey: String,
        emptyKey: String,
        tracks: List<Track>,
        modeActions: List<TrackListModeAction>
    ) {
        val title = AppLanguage.text(languageMode, titleKey)
        val list = ArrayList(tracks)
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), list.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            }, icon = EchoIconKind.Back, isBack = true)
        )
        if (list.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.all"), Runnable {
                    listener.playTrackList(list, 0)
                }, icon = EchoIconKind.Play)
            )
        }
        listener.publishPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = title,
                tracks = list,
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, emptyKey),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun tracksFromRecords(records: List<TrackPlayRecord>): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        for (record in records) {
            if (record.track != null) tracks.add(record.track)
        }
        return tracks
    }

    companion object {
        const val HISTORY_GROUP_KEY = "virtual:play-history"
    }
}

data class LibraryPlaylistTrackListRequest(
    val title: String,
    val tracks: ArrayList<Track>,
    val headerMetrics: ArrayList<TrackListHeaderMetric>,
    val headerActions: ArrayList<TrackListHeaderAction>,
    val emptyText: String,
    val modeActions: ArrayList<TrackListModeAction>,
    val context: LibraryListContext = LibraryListContext.Playlist
)
