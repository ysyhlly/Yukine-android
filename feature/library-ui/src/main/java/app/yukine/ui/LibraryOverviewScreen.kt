package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import app.yukine.SMART_LONG_UNPLAYED_KEY
import app.yukine.SMART_RECENT_ADDED_KEY
import app.yukine.SMART_WEEK_FAVORITES_KEY
import app.yukine.TrackDownloadItem
import app.yukine.weekFavoriteRecords
import app.yukine.core.designsystem.R

private data class LibraryOverviewUiState(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val playlistCount: Int,
    val folderCount: Int,
    val favoriteCount: Int,
    val recentPlayedCount: Int,
    val weekFavoritesCount: Int,
    val recentAddedCount: Int,
    val longUnplayedCount: Int,
    val downloadedCount: Int,
    val sourceCount: Int,
    val sourceSummary: String,
    val artworkUris: List<Uri>
)

@Composable
fun LibraryOverviewScreen(
    library: LibraryStoreState,
    downloads: DownloadsUiState,
    labels: LibraryUiLabels,
    onOpenMode: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSources: () -> Unit,
    onScanLibrary: () -> Unit,
    onSyncLibrary: () -> Unit,
    onOpenSmartCollection: (key: String, title: String) -> Unit,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    compactCards: Boolean = true
) {
    val overview = remember(library, downloads, labels) {
        buildOverviewState(library, downloads, labels.overviewLocalSource)
    }
    val density = libraryCardDensityTokens(compactCards)
    val shelfCardWidth = 150.dp

    LazyColumn(
        modifier = Modifier.testTag("library_overview_list"),
        contentPadding = PaddingValues(
            start = density.pageHorizontalPadding,
            top = 8.dp,
            end = density.pageHorizontalPadding,
            bottom = echoPageBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(density.sectionSpacing)
    ) {
        item("overview-search") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YukineSearchBar(
                    modifier = Modifier.weight(1f),
                    label = labels.overviewSearchHint,
                    activeDownload = activeDownload,
                    playbackQuality = playbackQuality,
                    audioMotion = audioMotion
                ) { onSearch.run() }
                LibraryScanChip(labels.scanLibrary, onScanLibrary)
            }
        }

        item("overview-shelf") {
            LibraryOverviewSectionTitle(labels.overviewShelf)
            Spacer(Modifier.height(8.dp))
            RecommendShelf(
                overview = overview,
                labels = labels,
                cardWidth = shelfCardWidth,
                onOpenRecent = onOpenRecent,
                onOpenSmartCollection = onOpenSmartCollection
            )
        }

        item("overview-browse") {
            LibraryOverviewSectionTitle(labels.overviewBrowse)
            Spacer(Modifier.height(8.dp))
            BrowseLibraryCard(
                overview = overview,
                labels = labels,
                onOpenMode = onOpenMode,
                density = density
            )
        }

        item("overview-saved") {
            LibraryOverviewSectionTitle(labels.overviewSaved)
            Spacer(Modifier.height(8.dp))
            SavedCard(
                overview = overview,
                labels = labels,
                onOpenFavorites = onOpenFavorites,
                onOpenDownloads = onOpenDownloads,
                density = density
            )
        }

        item("overview-sources-sync") {
            LibraryOverviewSectionTitle(labels.overviewSourcesSync)
            Spacer(Modifier.height(8.dp))
            MusicSourcesCard(overview, labels, onOpenSources, onSyncLibrary, density)
        }
    }
}

@Composable
private fun LibraryScanChip(label: String, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(EchoIconKind.Refresh, Modifier.size(16.dp), p.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = EchoTypography.label,
                color = p.accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecommendShelf(
    overview: LibraryOverviewUiState,
    labels: LibraryUiLabels,
    cardWidth: androidx.compose.ui.unit.Dp,
    onOpenRecent: () -> Unit,
    onOpenSmartCollection: (key: String, title: String) -> Unit
) {
    val unit = labels.overviewSongUnit
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SmartShelfCard(
                label = labels.smartRecentAdded,
                count = overview.recentAddedCount,
                unit = unit,
                emptyHint = labels.smartRecentAddedEmpty,
                icon = EchoIconKind.Sparkle,
                cardWidth = cardWidth,
                onClick = { onOpenSmartCollection(SMART_RECENT_ADDED_KEY, labels.smartRecentAdded) }
            )
        }
        item {
            SmartShelfCard(
                label = labels.smartRecentPlayed,
                count = overview.recentPlayedCount,
                unit = unit,
                emptyHint = labels.smartRecentPlayedEmpty,
                icon = EchoIconKind.Timer,
                cardWidth = cardWidth,
                onClick = onOpenRecent
            )
        }
        item {
            SmartShelfCard(
                label = labels.smartWeekFavorites,
                count = overview.weekFavoritesCount,
                unit = unit,
                emptyHint = labels.smartWeekFavoritesEmpty,
                icon = EchoIconKind.Heart,
                cardWidth = cardWidth,
                onClick = { onOpenSmartCollection(SMART_WEEK_FAVORITES_KEY, labels.smartWeekFavorites) }
            )
        }
        item {
            SmartShelfCard(
                label = labels.smartLongUnplayed,
                count = overview.longUnplayedCount,
                unit = unit,
                emptyHint = labels.smartLongUnplayedEmpty,
                icon = EchoIconKind.Refresh,
                cardWidth = cardWidth,
                onClick = { onOpenSmartCollection(SMART_LONG_UNPLAYED_KEY, labels.smartLongUnplayed) }
            )
        }
    }
}

@Composable
private fun SmartShelfCard(
    label: String,
    count: Int,
    unit: String,
    emptyHint: String,
    icon: EchoIconKind,
    cardWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large)
            .semantics { contentDescription = label },
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .height(96.dp)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = EchoShapes.medium,
                    color = p.accentSoft
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(icon, Modifier.size(17.dp), p.accent)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            if (count > 0) {
                Text(
                    count.toString() + unit,
                    style = EchoTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent
                )
            } else {
                Text(
                    emptyHint,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    labels: LibraryUiLabels,
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
                            labels.overviewAllSongs,
                            style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                            color = p.text
                        )
                        Text(
                            songCount(overview.songCount, labels.overviewSongUnit),
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
                    label = labels.overviewAlbums,
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
                    label = labels.overviewArtists,
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
                    label = labels.overviewPlaylists,
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
                    label = labels.overviewFolders,
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
private fun SavedCard(
    overview: LibraryOverviewUiState,
    labels: LibraryUiLabels,
    onOpenFavorites: () -> Unit,
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
                label = labels.overviewFavorites,
                count = songCount(overview.favoriteCount, labels.overviewSongUnit),
                icon = EchoIconKind.Heart,
                onClick = onOpenFavorites,
                rowHeight = density.personalRowHeight,
                independentCard = density.independentCards
            )
            if (!density.independentCards) LibraryOverviewDivider(horizontalPadding = 14.dp)
            PersonalMusicRow(
                label = labels.overviewDownloaded,
                count = songCount(overview.downloadedCount, labels.overviewSongUnit),
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
    labels: LibraryUiLabels,
    onOpenSources: () -> Unit,
    onSyncLibrary: () -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Column {
            Surface(
                onClick = onOpenSources,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = labels.overviewSources },
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
                    Text(
                        overview.sourceSummary,
                        modifier = Modifier.weight(1f),
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        overview.sourceCount.toString(),
                        style = EchoTypography.caption,
                        color = p.muted
                    )
                    Spacer(Modifier.width(8.dp))
                    EchoIcon(EchoIconKind.ChevronRight, Modifier.size(17.dp), p.muted)
                }
            }
            LibraryOverviewDivider(horizontalPadding = 14.dp)
            Surface(
                onClick = onSyncLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = labels.syncLibrary },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoIcon(EchoIconKind.Sync, Modifier.size(18.dp), p.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        labels.syncLibrary,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.accent
                    )
                }
            }
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
    downloads: DownloadsUiState,
    localSourceName: String
): LibraryOverviewUiState {
    val tracks = library.visibleTracks
    val artworkUris = tracks
        .mapNotNull { it.albumArtUri }
        .distinctBy(Uri::toString)
        .take(3)
    val now = System.currentTimeMillis()
    val recentPlayedCount = library.recentRecords
        .filter { it.track != null }
        .map { it.track.id }
        .distinct()
        .size
    val weekFavoritesCount = weekFavoriteRecords(library.recentRecords, now)
        .filter { it.track != null }
        .map { it.track.id }
        .distinct()
        .size
    val sourceNames = buildList {
        add(localSourceName)
        library.remoteSources.mapTo(this) { it.name.ifBlank { it.type } }
    }
    return LibraryOverviewUiState(
        songCount = tracks.size,
        albumCount = LibraryGrouping.uniqueAlbumCount(tracks),
        artistCount = LibraryGrouping.uniqueArtistCount(tracks),
        playlistCount = library.playlists.size,
        folderCount = LibraryGrouping.uniqueFolderCount(tracks),
        favoriteCount = library.favoriteTrackIds.size,
        recentPlayedCount = recentPlayedCount,
        weekFavoritesCount = weekFavoritesCount,
        recentAddedCount = library.recentlyAddedTracks.size,
        longUnplayedCount = library.longUnplayedTracks.size,
        downloadedCount = downloads.finished.size,
        sourceCount = sourceNames.size,
        sourceSummary = sourceNames.joinToString(" · "),
        artworkUris = artworkUris
    )
}

private fun songCount(count: Int, unit: String): String = count.toString() + unit
