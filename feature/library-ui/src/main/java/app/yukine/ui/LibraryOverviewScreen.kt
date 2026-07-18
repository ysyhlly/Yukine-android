package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.DownloadsUiState
import app.yukine.LibraryGrouping
import app.yukine.LibraryStoreState
import app.yukine.TrackDownloadItem
import app.yukine.core.designsystem.R

private data class LibraryOverviewUiState(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val playlistCount: Int,
    val folderCount: Int,
    val favoriteCount: Int,
    val recentCount: Int,
    val downloadedCount: Int,
    val sourceCount: Int,
    val sourceSummary: String,
    val artworkUris: List<Uri>
)

private data class LibraryOverviewLabels(
    val title: String,
    val browse: String,
    val allSongs: String,
    val albums: String,
    val artists: String,
    val playlists: String,
    val folders: String,
    val myMusic: String,
    val favorites: String,
    val recent: String,
    val downloaded: String,
    val sources: String,
    val search: String,
    val manage: String,
    val songUnit: String
)

@Composable
fun LibraryOverviewScreen(
    library: LibraryStoreState,
    downloads: DownloadsUiState,
    modeActions: List<TrackListModeAction>,
    onOpenMode: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSources: () -> Unit,
    onManageLibrary: () -> Unit,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    compactCards: Boolean = true
) {
    val overview = remember(library, downloads) { buildOverviewState(library, downloads) }
    val labels = remember(modeActions) { overviewLabels(modeActions) }
    val modeLabels = remember(modeActions) { modeActions.associate { it.mode to it.label } }
    val density = libraryCardDensityTokens(compactCards)

    LazyColumn(
        contentPadding = PaddingValues(
            start = density.pageHorizontalPadding,
            top = 8.dp,
            end = density.pageHorizontalPadding,
            bottom = echoPageBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(density.sectionSpacing)
    ) {
        item("overview-search") {
            YukineSearchBar(
                label = labels.search,
                activeDownload = activeDownload,
                playbackQuality = playbackQuality,
                audioMotion = audioMotion
            ) { onSearch.run() }
        }

        item("overview-browse") {
            LibraryOverviewSectionTitle(labels.browse)
            Spacer(Modifier.height(8.dp))
            BrowseLibraryCard(
                overview = overview,
                labels = labels,
                modeLabels = modeLabels,
                onOpenMode = onOpenMode,
                density = density
            )
        }

        item("overview-personal") {
            LibraryOverviewSectionTitle(labels.myMusic)
            Spacer(Modifier.height(8.dp))
            PersonalMusicCard(
                overview = overview,
                labels = labels,
                onOpenFavorites = onOpenFavorites,
                onOpenRecent = onOpenRecent,
                onOpenDownloads = onOpenDownloads,
                density = density
            )
        }

        item("overview-sources") {
            MusicSourcesCard(overview, labels, onOpenSources, density)
        }
    }
}

@Composable
private fun LibraryOverviewSectionTitle(title: String) {
    val p = EchoTheme.colors()
    Text(title, style = EchoTypography.title, color = p.text)
}

@Composable
private fun BrowseLibraryCard(
    overview: LibraryOverviewUiState,
    labels: LibraryOverviewLabels,
    modeLabels: Map<String, String>,
    onOpenMode: (String) -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (density.independentCards) {
                    Modifier
                } else {
                    Modifier
                        .echoFloatingLayer(p, EchoShapes.large)
                        .echoGlassLayer(p, EchoShapes.large)
                }
            ),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Column(
            verticalArrangement = if (density.independentCards) {
                Arrangement.spacedBy(8.dp)
            } else {
                Arrangement.Top
            }
        ) {
            Surface(
                onClick = { onOpenMode(LibraryGrouping.SONGS) },
                modifier = Modifier.then(
                    if (density.independentCards) {
                        Modifier
                            .echoFloatingLayer(p, EchoShapes.large)
                            .echoGlassLayer(p, EchoShapes.large)
                    } else {
                        Modifier
                    }
                ),
                shape = EchoShapes.large,
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(density.allSongsHeight)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryCoverStack(overview.artworkUris, density.coverSize)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            labels.allSongs,
                            style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                            color = p.text
                        )
                        Text(
                            songCount(overview.songCount, labels),
                            style = EchoTypography.body,
                            color = p.muted
                        )
                    }
                    EchoIcon(EchoIconKind.ChevronRight, Modifier.size(18.dp), p.muted)
                }
            }
            if (!density.independentCards) LibraryOverviewDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (density.independentCards) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Start
                }
            ) {
                BrowseModeCell(
                    modifier = Modifier.weight(1f),
                    mode = LibraryGrouping.ALBUMS,
                    label = modeLabels[LibraryGrouping.ALBUMS] ?: labels.albums,
                    count = overview.albumCount,
                    icon = EchoIconKind.Collections,
                    onOpenMode = onOpenMode,
                    density = density
                )
                if (!density.independentCards) {
                    LibraryOverviewVerticalDivider(density.browseCellHeight)
                }
                BrowseModeCell(
                    modifier = Modifier.weight(1f),
                    mode = LibraryGrouping.ARTISTS,
                    label = modeLabels[LibraryGrouping.ARTISTS] ?: labels.artists,
                    count = overview.artistCount,
                    icon = EchoIconKind.Artist,
                    onOpenMode = onOpenMode,
                    density = density
                )
            }
            if (!density.independentCards) LibraryOverviewDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (density.independentCards) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Start
                }
            ) {
                BrowseModeCell(
                    modifier = Modifier.weight(1f),
                    mode = LibraryGrouping.PLAYLISTS,
                    label = modeLabels[LibraryGrouping.PLAYLISTS] ?: labels.playlists,
                    count = overview.playlistCount,
                    icon = EchoIconKind.PlaylistAdd,
                    onOpenMode = onOpenMode,
                    density = density
                )
                if (!density.independentCards) {
                    LibraryOverviewVerticalDivider(density.browseCellHeight)
                }
                BrowseModeCell(
                    modifier = Modifier.weight(1f),
                    mode = LibraryGrouping.FOLDERS,
                    label = modeLabels[LibraryGrouping.FOLDERS] ?: labels.folders,
                    count = overview.folderCount,
                    icon = EchoIconKind.Folder,
                    onOpenMode = onOpenMode,
                    density = density
                )
            }
        }
    }
}

@Composable
private fun BrowseModeCell(
    modifier: Modifier,
    mode: String,
    label: String,
    count: Int,
    icon: EchoIconKind,
    onOpenMode: (String) -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { onOpenMode(mode) },
        modifier = modifier
            .height(density.browseCellHeight)
            .then(
                if (density.independentCards) {
                    Modifier
                        .echoFloatingLayer(p, EchoShapes.medium)
                        .echoGlassLayer(p, EchoShapes.medium)
                } else {
                    Modifier
                }
            ),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = EchoShapes.medium,
                color = p.accentSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(icon, Modifier.size(22.dp), p.accent)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(count.toString(), style = EchoTypography.caption, color = p.muted)
            }
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(15.dp), p.muted)
        }
    }
}

@Composable
private fun LibraryCoverStack(artworkUris: List<Uri>, coverSize: androidx.compose.ui.unit.Dp) {
    val p = EchoTheme.colors()
    val coverOffset = coverSize * 0.375f
    Box(modifier = Modifier.size(width = coverSize + coverOffset * 2f, height = coverSize)) {
        for (index in 2 downTo 0) {
            AsyncArtwork(
                uri = artworkUris.getOrNull(index),
                title = "Library ${index + 1}",
                subtitle = "",
                modifier = Modifier
                    .offset(x = coverOffset * index)
                    .size(coverSize),
                cornerRadius = 12.dp,
                fallbackTextSize = 14.sp,
                targetSize = coverSize,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
        }
    }
}

@Composable
private fun PersonalMusicCard(
    overview: LibraryOverviewUiState,
    labels: LibraryOverviewLabels,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDownloads: () -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (density.independentCards) {
                    Modifier
                } else {
                    Modifier
                        .echoFloatingLayer(p, EchoShapes.large)
                        .echoGlassLayer(p, EchoShapes.large)
                }
            ),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Column(
            verticalArrangement = if (density.independentCards) {
                Arrangement.spacedBy(8.dp)
            } else {
                Arrangement.Top
            }
        ) {
            PersonalMusicRow(
                label = labels.favorites,
                count = songCount(overview.favoriteCount, labels),
                icon = EchoIconKind.Heart,
                onClick = onOpenFavorites,
                rowHeight = density.personalRowHeight,
                independentCard = density.independentCards
            )
            if (!density.independentCards) LibraryOverviewDivider(horizontalPadding = 14.dp)
            PersonalMusicRow(
                label = labels.recent,
                count = songCount(overview.recentCount, labels),
                icon = EchoIconKind.Timer,
                onClick = onOpenRecent,
                rowHeight = density.personalRowHeight,
                independentCard = density.independentCards
            )
            if (!density.independentCards) LibraryOverviewDivider(horizontalPadding = 14.dp)
            PersonalMusicRow(
                label = labels.downloaded,
                count = songCount(overview.downloadedCount, labels),
                icon = EchoIconKind.Download,
                onClick = onOpenDownloads,
                rowHeight = density.personalRowHeight,
                independentCard = density.independentCards
            )
        }
    }
}

@Composable
private fun PersonalMusicRow(
    label: String,
    count: String,
    icon: EchoIconKind,
    onClick: () -> Unit,
    rowHeight: androidx.compose.ui.unit.Dp,
    independentCard: Boolean
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier.then(
            if (independentCard) {
                Modifier
                    .echoFloatingLayer(p, EchoShapes.medium)
                    .echoGlassLayer(p, EchoShapes.medium)
            } else {
                Modifier
            }
        ),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(25.dp), p.accent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text
                )
                Text(count, style = EchoTypography.caption, color = p.muted)
            }
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(17.dp), p.muted)
        }
    }
}

@Composable
private fun MusicSourcesCard(
    overview: LibraryOverviewUiState,
    labels: LibraryOverviewLabels,
    onOpenSources: () -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onOpenSources,
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(density.sourceRowHeight)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = EchoShapes.medium,
                color = p.accentSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.Network, Modifier.size(23.dp), p.accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    labels.sources,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text
                )
                Text(
                    overview.sourceSummary,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(overview.sourceCount.toString(), style = EchoTypography.caption, color = p.muted)
            Spacer(Modifier.width(8.dp))
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(17.dp), p.muted)
        }
    }
}

@Composable
private fun LibraryOverviewDivider(horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(1.dp)
            .background(p.border.copy(alpha = 0.72f))
    )
}

@Composable
private fun LibraryOverviewVerticalDivider(height: androidx.compose.ui.unit.Dp) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(p.border.copy(alpha = 0.72f))
    )
}

private fun buildOverviewState(
    library: LibraryStoreState,
    downloads: DownloadsUiState
): LibraryOverviewUiState {
    val tracks = library.visibleTracks
    val artworkUris = tracks
        .mapNotNull { it.albumArtUri }
        .distinctBy(Uri::toString)
        .take(3)
    val recentCount = library.recentRecords
        .map { it.track.id }
        .distinct()
        .size
    val sourceNames = buildList {
        add("本机")
        library.remoteSources.mapTo(this) { it.name.ifBlank { it.type } }
    }
    return LibraryOverviewUiState(
        songCount = tracks.size,
        albumCount = LibraryGrouping.uniqueAlbumCount(tracks),
        artistCount = LibraryGrouping.uniqueArtistCount(tracks),
        playlistCount = library.playlists.size,
        folderCount = LibraryGrouping.uniqueFolderCount(tracks),
        favoriteCount = library.favoriteTrackIds.size,
        recentCount = recentCount,
        downloadedCount = downloads.finished.size,
        sourceCount = sourceNames.size,
        sourceSummary = sourceNames.joinToString(" · "),
        artworkUris = artworkUris
    )
}

private fun overviewLabels(modeActions: List<TrackListModeAction>): LibraryOverviewLabels {
    val english = modeActions.firstOrNull { it.mode == LibraryGrouping.SONGS }
        ?.label
        ?.equals("Songs", ignoreCase = true) == true
    return if (english) {
        LibraryOverviewLabels(
            title = "Library",
            browse = "Browse library",
            allSongs = "All songs",
            albums = "Albums",
            artists = "Artists",
            playlists = "Playlists",
            folders = "Folders",
            myMusic = "My music",
            favorites = "Favorites",
            recent = "Recently played",
            downloaded = "Downloaded",
            sources = "Music sources",
            search = "Search songs, albums, artists, or playlists",
            manage = "Sync library",
            songUnit = " songs"
        )
    } else {
        LibraryOverviewLabels(
            title = "曲库",
            browse = "浏览曲库",
            allSongs = "全部歌曲",
            albums = "专辑",
            artists = "艺人",
            playlists = "歌单",
            folders = "文件夹",
            myMusic = "我的音乐",
            favorites = "喜欢的音乐",
            recent = "最近播放",
            downloaded = "已下载",
            sources = "音乐来源",
            search = "搜索歌曲、专辑、艺人或歌单",
            manage = "同步曲库",
            songUnit = " 首"
        )
    }
}

private fun songCount(count: Int, labels: LibraryOverviewLabels): String =
    count.toString() + labels.songUnit
