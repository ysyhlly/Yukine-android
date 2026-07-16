package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryAction
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.EchoIconKind
import app.yukine.streaming.StreamingPlaybackAdapter
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Locale

fun interface LibraryGroupsUiDispatcher {
    fun dispatch(action: Runnable)
}

fun interface ArtistLocalInfoSource {
    fun load(languageMode: String, artistId: String, tracks: List<Track>): ArtistInfo?
}

data class LibraryGroupsChromeState(
    val actions: List<LibraryGroupActions>,
    val emptyText: String,
    val modeActions: List<TrackListModeAction>
)

data class LibraryGroupTrackListRequest(
    val title: String,
    val tracks: ArrayList<Track>,
    val headerMetrics: ArrayList<TrackListHeaderMetric>,
    val headerActions: ArrayList<TrackListHeaderAction>,
    val footerAlbums: ArrayList<TrackListAlbumCardUiState> = ArrayList()
)

class LibraryGroupsStateReducer @JvmOverloads constructor(
    private val viewModel: LibraryViewModel,
    private val listener: Listener,
    private val uiDispatcher: LibraryGroupsUiDispatcher = LibraryGroupsUiDispatcher { action -> action.run() },
    private val artistLocalInfoSource: ArtistLocalInfoSource? = null
) {
    private val artistInfoCache = object : LinkedHashMap<String, ArtistInfo>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistInfo>?): Boolean = size > 24
    }
    private val artistInfoRequests = HashSet<String>()
    private var activeArtistInfoKey: String = ""
    private var artistInfoRequestSerial = 0

    interface Listener {
        fun selectLibraryGroup(key: String, title: String)

        fun clearLibraryGroupSelection()

        fun closeLibraryGroup()

        fun openFavoritesCollection()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun confirmDeleteGroup(title: String, tracks: List<Track>)

        fun manageArtistIdentity(artistId: String, title: String) = Unit

        fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        )

        fun publishTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>,
            footerAlbums: ArrayList<TrackListAlbumCardUiState> = ArrayList()
        )
    }

    fun reduce(
        visibleTracks: List<Track>,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        modeActions: List<TrackListModeAction>,
        favoriteIds: Set<Long> = emptySet()
    ) {
        reduce(
            AppLanguage.MODE_CHINESE,
            visibleTracks,
            libraryMode,
            selectedLibraryGroupKey,
            selectedLibraryGroupTitle,
            modeActions,
            favoriteIds
        )
    }

    fun reduce(
        languageMode: String,
        visibleTracks: List<Track>,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        modeActions: List<TrackListModeAction>,
        favoriteIds: Set<Long> = emptySet()
    ) {
        val filteredTracks = LibraryTrackPresentationPolicy.present(
            visibleTracks,
            emptyList(),
            viewModel.libraryUi.value,
            favoriteIds
        ).map { it.track }
        val groups = LibraryGrouping.groupTracks(
            filteredTracks,
            libraryMode,
            viewModel.dataOwner()::artistIdentitiesFor
        )
        if (selectedLibraryGroupKey.isNotEmpty()) {
            val selectedTracks = groups[selectedLibraryGroupKey]
            if (selectedTracks != null) {
                reduceGroupDetail(
                    languageMode,
                    selectedLibraryGroupTitle,
                    selectedTracks,
                    libraryMode,
                    selectedLibraryGroupKey
                )
                return
            }
        }

        activeArtistInfoKey = ""
        artistInfoRequestSerial++
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
                    Runnable { viewModel.presentation.onAction(LibraryAction.ToggleGroupSelection(rowId)) }
                )
            )
        }

        viewModel.presentation.updateVisibleGroupTargets(groups.mapKeys { (key, _) ->
            "$libraryMode:${if (key.isEmpty()) "unknown" else key}"
        })

        val title = LibraryGrouping.modeTitle(libraryMode, languageMode)
        val emptyText = AppLanguage.text(languageMode, "no.library.groups").replace("%s", title)
        viewModel.presentation.clearTrackList()
        viewModel.presentation.updateLibraryGroups(title, groupRows)
        listener.publishLibraryGroupsChrome(groupActions, emptyText, modeActions)
    }

    private fun reduceGroupDetail(
        languageMode: String,
        selectedLibraryGroupTitle: String,
        tracks: ArrayList<Track>,
        libraryMode: String,
        selectedLibraryGroupKey: String
    ) {
        val artistId = LibraryGrouping.artistIdFromGroupKey(selectedLibraryGroupKey)
        val cachedInfo = if (libraryMode == LibraryGrouping.ARTISTS) {
            val lookupKey = artistInfoLookupKey(artistId ?: selectedLibraryGroupTitle)
            activeArtistInfoKey = lookupKey
            artistInfoCache[lookupKey]
        } else {
            activeArtistInfoKey = ""
            null
        }
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        if (libraryMode == LibraryGrouping.ARTISTS) {
            headerMetrics.add(
                TrackListHeaderMetric(
                    AppLanguage.text(languageMode, "artist.info"),
                    artistIntro(languageMode, selectedLibraryGroupTitle, tracks, cachedInfo)
                )
            )
            if (cachedInfo != null) {
                headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "data.source"), cachedInfo.source))
            }
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "albums"), LibraryGrouping.albumCount(tracks).toString()))
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString()))
        } else {
            headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "tracks"), tracks.size.toString()))
        }
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back"), Runnable {
                listener.closeLibraryGroup()
            }, icon = EchoIconKind.Back, isBack = true)
        )
        headerActions.add(
            TrackListHeaderAction(if (libraryMode == LibraryGrouping.ARTISTS) AppLanguage.text(languageMode, "play.artist") else AppLanguage.text(languageMode, "play.group"), Runnable {
                listener.playTrackList(tracks, 0)
            }, icon = EchoIconKind.Play)
        )
        if (libraryMode == LibraryGrouping.ARTISTS && artistId != null) {
            headerActions.add(
                TrackListHeaderAction(
                    AppLanguage.text(languageMode, "artist.identity.manage"),
                    Runnable { listener.manageArtistIdentity(artistId, selectedLibraryGroupTitle) },
                    icon = EchoIconKind.Edit
                )
            )
        }
        listener.publishTrackList(
            selectedLibraryGroupTitle,
            tracks,
            headerMetrics,
            headerActions,
            artistAlbumCards(languageMode, cachedInfo)
        )
        if (libraryMode == LibraryGrouping.ARTISTS) {
            loadLocalArtistInfo(
                languageMode,
                artistId,
                selectedLibraryGroupTitle,
                tracks,
                headerActions,
                cachedInfo
            )
        }
    }

    private fun loadLocalArtistInfo(
        languageMode: String,
        artistId: String?,
        artist: String,
        tracks: ArrayList<Track>,
        headerActions: ArrayList<TrackListHeaderAction>,
        cachedInfo: ArtistInfo?
    ) {
        val stableArtistId = artistId?.takeIf { it.isNotBlank() } ?: return
        if (cachedInfo != null) return
        val source = artistLocalInfoSource ?: return
        val lookupKey = artistInfoLookupKey(stableArtistId)
        if (!artistInfoRequests.add(lookupKey)) return
        val requestSerial = ++artistInfoRequestSerial
        Thread {
            val info = runCatching { source.load(languageMode, stableArtistId, tracks) }.getOrNull()
            uiDispatcher.dispatch(Runnable {
                artistInfoRequests.remove(lookupKey)
                if (info != null) artistInfoCache[lookupKey] = info
                if (info == null || activeArtistInfoKey != lookupKey || requestSerial != artistInfoRequestSerial) {
                    return@Runnable
                }
                publishArtistTrackList(languageMode, artist, tracks, headerActions, info)
            })
        }.apply {
            name = "LocalArtistInfo-$stableArtistId"
            isDaemon = true
            start()
        }
    }

    private fun publishArtistTrackList(
        languageMode: String,
        artist: String,
        tracks: ArrayList<Track>,
        headerActions: ArrayList<TrackListHeaderAction>,
        info: ArtistInfo?
    ) {
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "artist.info"), artistIntro(languageMode, artist, tracks, info)))
        headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "data.source"), info?.source ?: AppLanguage.text(languageMode, "local.identity.pending")))
        headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "albums"), LibraryGrouping.albumCount(tracks).toString()))
        headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString()))
        listener.publishTrackList(artist, tracks, headerMetrics, headerActions, artistAlbumCards(languageMode, info))
    }

    private fun artistInfoLookupKey(artist: String): String =
        artist.trim().lowercase(Locale.ROOT)

    private fun artistAlbumCards(languageMode: String, info: ArtistInfo?): ArrayList<TrackListAlbumCardUiState> {
        val cards = ArrayList<TrackListAlbumCardUiState>()
        info?.albums.orEmpty().forEach { album ->
            cards.add(
                TrackListAlbumCardUiState(
                    title = album.title,
                    subtitle = albumSubtitle(languageMode, album),
                    coverUri = album.coverUrl?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse),
                    onClick = Runnable {
                        playOnlineArtistAlbum(album)
                    }
                )
            )
        }
        return cards
    }

    private fun playOnlineArtistAlbum(album: ArtistAlbumInfo) {
        val immediateTracks = album.tracks.map(StreamingPlaybackAdapter::placeholderTrack)
        if (immediateTracks.isNotEmpty()) {
            listener.playTrackList(immediateTracks, 0)
        }
    }

    private fun albumSubtitle(languageMode: String, album: ArtistAlbumInfo): String {
        val count = album.trackCount
        return if (AppLanguage.MODE_ENGLISH == languageMode) {
            listOf(
                album.artist,
                count?.let { "$it tracks" }.orEmpty(),
                providerLabel(album.provider)
            ).filter { it.isNotBlank() }.joinToString(" - ")
        } else {
            listOf(
                album.artist,
                count?.let { "$it 首" }.orEmpty(),
                providerLabel(album.provider)
            ).filter { it.isNotBlank() }.joinToString(" - ")
        }
    }

    private fun providerLabel(provider: app.yukine.streaming.StreamingProviderName): String = when (provider) {
        app.yukine.streaming.StreamingProviderName.NETEASE -> "网易云音乐"
        app.yukine.streaming.StreamingProviderName.QQ_MUSIC -> "QQ音乐"
        app.yukine.streaming.StreamingProviderName.LUOXUE -> "洛雪音源"
        else -> provider.wireName
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
                append(". Verified metadata can be added later by background enrichment.")
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
                append("。后台增强可在后续补充已验证资料。")
            }
        }
    }
}
