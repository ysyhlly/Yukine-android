package app.yukine.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared sizing for every native library surface. Both modes keep the same content density;
 * the non-compact mode changes the hierarchy by rendering every entry as an independent card.
 */
internal data class LibraryCardDensityTokens(
    val independentCards: Boolean,
    val pageHorizontalPadding: Dp,
    val sectionSpacing: Dp,
    val gridHorizontalSpacing: Dp,
    val gridVerticalSpacing: Dp,
    val allSongsHeight: Dp,
    val browseCellHeight: Dp,
    val coverSize: Dp,
    val personalRowHeight: Dp,
    val sourceRowHeight: Dp,
    val groupRowMinHeight: Dp,
    val groupRowHorizontalPadding: Dp,
    val groupRowVerticalPadding: Dp,
    val artistArtworkSize: Dp,
    val playlistArtworkSize: Dp,
    val folderArtworkSize: Dp,
    val albumTextHorizontalPadding: Dp,
    val albumTextVerticalPadding: Dp,
    val trackRowHeight: Dp,
    val trackArtworkSize: Dp,
    val trackHorizontalPadding: Dp,
    val trackVerticalPadding: Dp
)

internal fun libraryCardDensityTokens(compact: Boolean): LibraryCardDensityTokens =
    if (compact) {
        LibraryCardDensityTokens(
            independentCards = false,
            pageHorizontalPadding = 18.dp,
            sectionSpacing = 14.dp,
            gridHorizontalSpacing = 8.dp,
            gridVerticalSpacing = 6.dp,
            allSongsHeight = 104.dp,
            browseCellHeight = 82.dp,
            coverSize = 64.dp,
            personalRowHeight = 54.dp,
            sourceRowHeight = 62.dp,
            groupRowMinHeight = 64.dp,
            groupRowHorizontalPadding = 10.dp,
            groupRowVerticalPadding = 8.dp,
            artistArtworkSize = 52.dp,
            playlistArtworkSize = 48.dp,
            folderArtworkSize = 46.dp,
            albumTextHorizontalPadding = 8.dp,
            albumTextVerticalPadding = 9.dp,
            trackRowHeight = 88.dp,
            trackArtworkSize = 58.dp,
            trackHorizontalPadding = 10.dp,
            trackVerticalPadding = 10.dp
        )
    } else {
        LibraryCardDensityTokens(
            independentCards = true,
            pageHorizontalPadding = 18.dp,
            sectionSpacing = 14.dp,
            gridHorizontalSpacing = 8.dp,
            gridVerticalSpacing = 8.dp,
            allSongsHeight = 104.dp,
            browseCellHeight = 82.dp,
            coverSize = 64.dp,
            personalRowHeight = 54.dp,
            sourceRowHeight = 62.dp,
            groupRowMinHeight = 64.dp,
            groupRowHorizontalPadding = 10.dp,
            groupRowVerticalPadding = 8.dp,
            artistArtworkSize = 52.dp,
            playlistArtworkSize = 48.dp,
            folderArtworkSize = 46.dp,
            albumTextHorizontalPadding = 8.dp,
            albumTextVerticalPadding = 9.dp,
            trackRowHeight = 88.dp,
            trackArtworkSize = 58.dp,
            trackHorizontalPadding = 10.dp,
            trackVerticalPadding = 10.dp
        )
    }
