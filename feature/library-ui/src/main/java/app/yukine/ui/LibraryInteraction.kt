package app.yukine.ui

enum class LibraryMode(val routeKey: String) {
    Songs("songs"), Albums("albums"), Artists("artists"), Folders("folders"), Playlists("playlists");

    companion object {
        fun fromRouteKey(value: String?): LibraryMode = entries.firstOrNull { it.routeKey == value } ?: Songs
    }
}

enum class LibrarySort {
    TitleAscending, TitleDescending, Artist, Album, DurationAscending, DurationDescending
}

enum class LibraryFilter { All, Favorites, Local, Network }

enum class LibrarySourceKind { MediaStore, Document, Stream, WebDav }

enum class LibraryDeleteMode { RemoveFromPlaylist, HideFromLibrary, DeleteFile, DeleteNetworkRecord }

data class LibraryUiState(
    val mode: LibraryMode = LibraryMode.Songs,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.TitleAscending,
    val filter: LibraryFilter = LibraryFilter.All,
    val revealedRowKey: String? = null,
    val selectedTrackKeys: Set<String> = emptySet(),
    val selectedGroupKeys: Set<String> = emptySet(),
    val operationInProgress: Boolean = false,
    val autoSyncEnabled: Boolean = false,
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
    val sortArtist: String = "歌手",
    val sortAlbum: String = "专辑",
    val sortDurationAscending: String = "时长升序",
    val sortDurationDescending: String = "时长降序",
    val syncLibrary: String = "同步曲库",
    val syncLibraryDescription: String = "更新 WebDAV 歌曲、标签和封面",
    val syncingLibrary: String = "正在同步曲库",
    val autoSync: String = "启动时自动同步"
)

sealed interface LibraryAction {
    data class QueryChanged(val query: String) : LibraryAction
    data class SortChanged(val sort: LibrarySort) : LibraryAction
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
}

fun interface LibraryActionHandler {
    fun onAction(action: LibraryAction)
}
