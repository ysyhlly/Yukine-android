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

        fun openFavoritesCollection()

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
        render(
            AppLanguage.MODE_CHINESE,
            visibleTracks,
            libraryMode,
            selectedLibraryGroupKey,
            selectedLibraryGroupTitle,
            modeActions
        )
    }

    fun render(
        languageMode: String,
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
                renderGroupDetail(languageMode, selectedLibraryGroupTitle, selectedTracks, libraryMode)
                return
            }
        }

        listener.clearLibraryGroupSelection()
        val groupRows = ArrayList<LibraryGroupUiState>()
        val groupActions = ArrayList<LibraryGroupActions>()
        if (LibraryGrouping.PLAYLISTS == libraryMode) {
            groupRows.add(
                LibraryGroupUiState(
                    "virtual:favorites",
                    AppLanguage.text(languageMode, "library.favorites.playlist.title"),
                    AppLanguage.text(languageMode, "library.favorites.playlist.description")
                )
            )
            groupActions.add(
                LibraryGroupActions(
                    Runnable { listener.openFavoritesCollection() },
                    Runnable { listener.openFavoritesCollection() },
                    false,
                    null
                )
            )
        }
        for ((key, tracks) in groups) {
            val title = LibraryGrouping.groupTitle(key, libraryMode, languageMode)
            val rowId = "$libraryMode:${if (key.isEmpty()) "unknown" else key}"
            groupRows.add(
                LibraryGroupUiState(
                    rowId,
                    title,
                    LibraryGrouping.groupSubtitle(tracks, libraryMode, languageMode),
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

        val title = LibraryGrouping.modeTitle(libraryMode, languageMode)
        val emptyText = AppLanguage.text(languageMode, "no.library.groups").replace("%s", title)
        viewModel.clearTrackList()
        viewModel.updateLibraryGroups(title, groupRows)
        listener.publishLibraryGroupsChrome(groupActions, emptyText, modeActions)
    }

    private fun renderGroupDetail(
        languageMode: String,
        selectedLibraryGroupTitle: String,
        tracks: ArrayList<Track>,
        libraryMode: String
    ) {
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        if (libraryMode == LibraryGrouping.ARTISTS) {
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "artist.info"), artistIntro(languageMode, selectedLibraryGroupTitle, tracks, null)))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "albums"), LibraryGrouping.albumCount(tracks).toString()))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString()))
        } else {
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "tracks"), tracks.size.toString()))
        }
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back"), Runnable {
                listener.closeLibraryGroup()
            })
        )
        headerActions.add(
            TrackListHeaderAction(if (libraryMode == LibraryGrouping.ARTISTS) AppLanguage.text(languageMode, "play.artist") else AppLanguage.text(languageMode, "play.group"), Runnable {
                listener.playTrackList(tracks, 0)
            })
        )
        listener.renderTrackList(selectedLibraryGroupTitle, tracks, headerMetrics, headerActions)
        if (libraryMode == LibraryGrouping.ARTISTS) {
            loadOnlineArtistInfo(languageMode, selectedLibraryGroupTitle, tracks, headerActions)
        }
    }

    private fun loadOnlineArtistInfo(
        languageMode: String,
        artist: String,
        tracks: ArrayList<Track>,
        headerActions: ArrayList<TrackListHeaderAction>
    ) {
        Thread {
            val info = runCatching { artistInfoRepository.loadArtistInfo(artist, tracks) }.getOrNull()
            val headerMetrics = ArrayList<TrackListHeaderMetric>()
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "artist.info"), artistIntro(languageMode, artist, tracks, info)))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "data.source"), info?.source ?: AppLanguage.text(languageMode, "online.info.not.found")))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "albums"), LibraryGrouping.albumCount(tracks).toString()))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString()))
            listener.renderTrackList(artist, tracks, headerMetrics, headerActions)
        }.apply {
            name = "ArtistInfo-$artist"
            isDaemon = true
            start()
        }
    }

    private fun artistIntro(languageMode: String, artist: String, tracks: List<Track>, onlineInfo: ArtistInfo?): String {
        if (onlineInfo != null && onlineInfo.summary.isNotBlank()) {
            return onlineInfo.summary
        }
        val albums = tracks.map { it.album }.filter { it.isNotBlank() }.distinct()
        val latestAlbum = albums.firstOrNull().orEmpty()
        val spec = tracks.map { it.audioSpecSummary() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return buildString {
            val displayArtist = artist.ifBlank { AppLanguage.text(languageMode, "unknown.artist") }
            if (AppLanguage.MODE_ENGLISH == languageMode) {
                append(displayArtist)
                append(" is in your local library. Current library has ")
                append(tracks.size)
                append(if (tracks.size == 1) " track" else " tracks")
                if (albums.isNotEmpty()) {
                    append(" and ")
                    append(albums.size)
                    append(if (albums.size == 1) " album" else " albums")
                }
                if (latestAlbum.isNotBlank()) {
                    append(". Representative album: ")
                    append(latestAlbum)
                }
                if (spec.isNotBlank()) {
                    append(". Library includes ")
                    append(spec)
                    append(" audio specs")
                }
                append(". No reliable online bio was found yet.")
            } else {
                append(displayArtist)
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
}
