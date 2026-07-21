package app.yukine.ui

enum class LibraryMode(val routeKey: String) {
    Songs("songs"), Albums("albums"), Artists("artists"), Folders("folders"), Playlists("playlists");

    companion object {
        fun fromRouteKey(value: String?): LibraryMode = entries.firstOrNull { it.routeKey == value } ?: Songs
    }
}

enum class LibrarySort {
    TitleAscending, TitleDescending, Artist, Album, DurationAscending, DurationDescending,
    DateAddedDescending, DateAddedAscending, PlayCountDescending
}

enum class LibraryGroupSort {
    TitleAscending, TitleDescending, TrackCountDescending, TrackCountAscending
}

enum class LibraryFilter { All, Favorites, Local, Network }

enum class LibrarySourceKind { MediaStore, Document, Stream, WebDav }

enum class LibraryDeleteMode { RemoveFromPlaylist, HideFromLibrary, DeleteFile, DeleteNetworkRecord }

data class LibraryUiState(
    val mode: LibraryMode = LibraryMode.Songs,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.TitleAscending,
    val groupSort: LibraryGroupSort = LibraryGroupSort.TitleAscending,
    val filter: LibraryFilter = LibraryFilter.All,
    val revealedRowKey: String? = null,
    val selectedTrackKeys: Set<String> = emptySet(),
    val selectedGroupKeys: Set<String> = emptySet(),
    val operationInProgress: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val dedupCandidateCount: Int = 0,
    val message: String = "",
    val labels: LibraryUiLabels = LibraryUiLabels()
) {
    val selectionActive: Boolean
        get() = selectedTrackKeys.isNotEmpty() || selectedGroupKeys.isNotEmpty()
}

data class LibraryUiLabels(
    val search: String = "搜索曲库",
    val sort: String = "排序",
    val filter: String = "筛选",
    val all: String = "全部",
    val favorites: String = "收藏",
    val local: String = "本地",
    val network: String = "网络",
    val selectAll: String = "全选",
    val cancel: String = "取消",
    val play: String = "播放",
    val addToPlaylist: String = "加入歌单",
    val favorite: String = "收藏",
    val download: String = "下载",
    val delete: String = "删除",
    val more: String = "更多",
    val selectedSuffix: String = " 项已选择",
    val sortTitleAscending: String = "标题 A-Z",
    val sortTitleDescending: String = "标题 Z-A",
    val sortTrackCountDescending: String = "歌曲数从多到少",
    val sortTrackCountAscending: String = "歌曲数从少到多",
    val sortArtist: String = "歌手",
    val sortAlbum: String = "专辑",
    val sortDurationAscending: String = "时长升序",
    val sortDurationDescending: String = "时长降序",
    val sortDateAddedDescending: String = "最近入库",
    val sortDateAddedAscending: String = "最早入库",
    val sortPlayCount: String = "播放次数",
    val syncLibrary: String = "同步曲库",
    val syncLibraryDescription: String = "更新 WebDAV 歌曲、标签和封面",
    val syncingLibrary: String = "正在同步曲库",
    val autoSync: String = "启动时自动同步",
    val scanLibrary: String = "扫描曲库",
    val importFiles: String = "导入文件",
    val clearSearch: String = "清除搜索",
    val resetFilter: String = "重置筛选",
    val emptySearch: String = "没有匹配的歌曲",
    val emptyFilter: String = "当前筛选没有歌曲",
    val emptyGroupSearch: String = "没有匹配的分组",
    val emptyGroupFilter: String = "当前筛选没有分组",
    val emptyLibrary: String = "曲库中还没有歌曲",
    val groupCountSuffix: String = "个结果",
    val back: String = "返回",
    val overviewShelf: String = "为你推荐",
    val overviewBrowse: String = "按方式浏览",
    val overviewSaved: String = "收藏与离线",
    val overviewSourcesSync: String = "音乐来源与同步",
    val overviewFavorites: String = "喜欢的音乐",
    val overviewDownloaded: String = "已下载",
    val overviewSources: String = "音乐来源",
    val overviewSearchHint: String = "搜索歌曲、专辑、艺人或歌单",
    val overviewSongUnit: String = " 首",
    val overviewLocalSource: String = "本机",
    val overviewAllSongs: String = "全部歌曲",
    val overviewAlbums: String = "专辑",
    val overviewArtists: String = "艺人",
    val overviewPlaylists: String = "歌单",
    val overviewFolders: String = "文件夹",
    val smartRecentAdded: String = "最近添加",
    val smartRecentPlayed: String = "最近播放",
    val smartWeekFavorites: String = "一周最爱",
    val smartLongUnplayed: String = "很久没听",
    val smartRecentAddedEmpty: String = "扫描或导入歌曲后，会出现在这里",
    val smartRecentPlayedEmpty: String = "暂无最近播放",
    val smartWeekFavoritesEmpty: String = "本周播放喜欢的歌曲后，会出现在这里",
    val smartLongUnplayedEmpty: String = "最近所有歌都听过啦，真棒",
    val dedup: String = "去重"
)

sealed interface LibraryAction {
    data class QueryChanged(val query: String) : LibraryAction
    data class SortChanged(val sort: LibrarySort) : LibraryAction
    data class GroupSortChanged(val sort: LibraryGroupSort) : LibraryAction
    data class FilterChanged(val filter: LibraryFilter) : LibraryAction
    data class ModeChanged(val mode: LibraryMode) : LibraryAction
    data class RevealTrack(val key: String?) : LibraryAction
    data class ToggleTrackSelection(val key: String) : LibraryAction
    data class ToggleGroupSelection(val key: String) : LibraryAction
    data object SelectAllVisible : LibraryAction
    data object ClearSelection : LibraryAction
    data object PlaySelected : LibraryAction
    data object FavoriteSelected : LibraryAction
    data object AddSelectedToPlaylist : LibraryAction
    data object DownloadSelected : LibraryAction
    data object DeleteSelected : LibraryAction
    data object ScanLibrary : LibraryAction
    data object ImportFiles : LibraryAction
    data object SyncLibrary : LibraryAction
    data class SetAutoSyncEnabled(val enabled: Boolean) : LibraryAction
    data object OpenDedupCenter : LibraryAction
}

fun interface LibraryActionHandler {
    fun onAction(action: LibraryAction)
}
