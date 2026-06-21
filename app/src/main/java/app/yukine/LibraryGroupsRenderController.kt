package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryGroupsRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener,
    private val artistInfoRepository: ArtistInfoRepository = ArtistInfoRepository()
) {
    constructor(viewModel: LibraryViewModel, listener: Listener) : this(
        viewModel,
        listener,
        ArtistInfoRepository()
    )

    interface Listener {
        fun selectLibraryGroup(key: String, title: String)

        fun clearLibraryGroupSelection()

        fun closeLibraryGroup()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun confirmDeleteGroup(title: String, tracks: List<Track>)

        fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        )

        fun renderTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>
        )
    }

    fun render(
        visibleTracks: List<Track>,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        modeActions: List<TrackListModeAction>
    ) {
        val groups = LibraryGrouping.groupTracks(visibleTracks, libraryMode)
        if (selectedLibraryGroupKey.isNotEmpty()) {
            val selectedTracks = groups[selectedLibraryGroupKey]
            if (selectedTracks != null) {
                renderGroupDetail(selectedLibraryGroupTitle, selectedTracks, libraryMode)
                return
            }
        }

        listener.clearLibraryGroupSelection()
        val groupRows = ArrayList<LibraryGroupUiState>()
        val groupActions = ArrayList<LibraryGroupActions>()
        for ((key, tracks) in groups) {
            val title = LibraryGrouping.groupTitle(key, libraryMode)
            val rowId = "$libraryMode:${if (key.isEmpty()) "unknown" else key}"
            groupRows.add(
                LibraryGroupUiState(
                    rowId,
                    title,
                    LibraryGrouping.groupSubtitle(tracks, libraryMode),
                    LibraryGrouping.groupArtworkUri(tracks, libraryMode)
                )
            )
            groupActions.add(
                LibraryGroupActions(
                    Runnable { listener.selectLibraryGroup(key, title) },
                    Runnable { listener.playTrackList(tracks, 0) },
                    true,
                    Runnable { listener.confirmDeleteGroup(title, tracks) }
                )
            )
        }

        val title = LibraryGrouping.modeTitle(libraryMode)
        val emptyText = "No $title groups"
        viewModel.clearTrackList()
        viewModel.updateLibraryGroups(title, groupRows)
        listener.publishLibraryGroupsChrome(groupActions, emptyText, modeActions)
    }

    private fun renderGroupDetail(selectedLibraryGroupTitle: String, tracks: ArrayList<Track>, libraryMode: String) {
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        if (libraryMode == LibraryGrouping.ARTISTS) {
            headerMetrics.add(TrackListHeaderMetric("歌手介绍", artistIntro(selectedLibraryGroupTitle, tracks, null)))
            headerMetrics.add(TrackListHeaderMetric("专辑", LibraryGrouping.albumCount(tracks).toString()))
            headerMetrics.add(TrackListHeaderMetric("歌曲", tracks.size.toString()))
        } else {
            headerMetrics.add(TrackListHeaderMetric("Metric", tracks.size.toString()))
        }
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction("Back", Runnable {
                listener.closeLibraryGroup()
            })
        )
        headerActions.add(
            TrackListHeaderAction(if (libraryMode == LibraryGrouping.ARTISTS) "播放该歌手" else "Play group", Runnable {
                listener.playTrackList(tracks, 0)
            })
        )
        listener.renderTrackList(selectedLibraryGroupTitle, tracks, headerMetrics, headerActions)
        if (libraryMode == LibraryGrouping.ARTISTS) {
            loadOnlineArtistInfo(selectedLibraryGroupTitle, tracks, headerActions)
        }
    }

    private fun loadOnlineArtistInfo(
        artist: String,
        tracks: ArrayList<Track>,
        headerActions: ArrayList<TrackListHeaderAction>
    ) {
        Thread {
            val info = runCatching { artistInfoRepository.loadArtistInfo(artist, tracks) }.getOrNull()
            val headerMetrics = ArrayList<TrackListHeaderMetric>()
            headerMetrics.add(TrackListHeaderMetric("歌手介绍", artistIntro(artist, tracks, info)))
            headerMetrics.add(TrackListHeaderMetric("资料来源", info?.source ?: "未找到在线资料"))
            headerMetrics.add(TrackListHeaderMetric("专辑", LibraryGrouping.albumCount(tracks).toString()))
            headerMetrics.add(TrackListHeaderMetric("歌曲", tracks.size.toString()))
            listener.renderTrackList(artist, tracks, headerMetrics, headerActions)
        }.apply {
            name = "ArtistInfo-$artist"
            isDaemon = true
            start()
        }
    }

    private fun artistIntro(artist: String, tracks: List<Track>, onlineInfo: ArtistInfo?): String {
        if (onlineInfo != null && onlineInfo.summary.isNotBlank()) {
            return onlineInfo.summary
        }
        val albums = tracks.map { it.album }.filter { it.isNotBlank() }.distinct()
        val latestAlbum = albums.firstOrNull().orEmpty()
        val spec = tracks.map { it.audioSpecSummary() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return buildString {
            append(artist.ifBlank { "未知艺人" })
            append(" 收录在你的本地曲库中。")
            append("当前共有 ")
            append(tracks.size)
            append(" 首歌曲")
            if (albums.isNotEmpty()) {
                append("、")
                append(albums.size)
                append(" 张专辑")
            }
            if (latestAlbum.isNotBlank()) {
                append("。代表专辑：")
                append(latestAlbum)
            }
            if (spec.isNotBlank()) {
                append("。曲库中包含 ")
                append(spec)
                append(" 等音频规格")
            }
            append("。暂未从在线资料源找到可靠简介。")
        }
    }
}
