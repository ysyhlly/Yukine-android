package app.yukine

import android.net.Uri
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.yukine.model.Track
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryOverviewScreen
import app.yukine.ui.LibraryUiLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryOverviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chineseOverviewRoutesShelfBrowseSavedAndSourcesSync() {
        val calls = mutableListOf<String>()
        val tracks = listOf(track(1L, "First"), track(2L, "Second"))

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryOverviewScreen(
                    library = LibraryStoreState(
                        allTracks = tracks,
                        visibleTracks = tracks,
                        favoriteTrackIds = setOf(1L)
                    ),
                    downloads = DownloadsUiState(),
                    labels = LibraryUiLabels(),
                    onOpenMode = { calls += "mode:$it" },
                    onOpenFavorites = { calls += "favorites" },
                    onOpenRecent = { calls += "recent" },
                    onOpenDownloads = { calls += "downloads" },
                    onOpenSources = { calls += "sources" },
                    onScanLibrary = { calls += "scan" },
                    onSyncLibrary = { calls += "sync" },
                    onOpenSmartCollection = { key, _ -> calls += "smart:$key" },
                    onSearch = Runnable { calls += "search" }
                )
            }
        }

        val labels = LibraryUiLabels()
        composeRule.onNodeWithText(labels.overviewShelf).assertIsDisplayed()
        composeRule.onNodeWithText(labels.smartRecentAdded).assertIsDisplayed()
        composeRule.onNodeWithText(labels.smartRecentPlayed).assertIsDisplayed()
        composeRule.onNodeWithText(labels.overviewAllSongs).assertIsDisplayed()
        composeRule.onNodeWithText("2" + labels.overviewSongUnit).assertIsDisplayed()

        composeRule.onNode(hasText(labels.overviewSearchHint) and hasClickAction()).performClick()
        composeRule.onNode(hasContentDescription(labels.scanLibrary) and hasClickAction()).performClick()
        composeRule.onNode(hasContentDescription(labels.smartRecentAdded) and hasClickAction()).performClick()
        composeRule.onNode(hasContentDescription(labels.smartRecentPlayed) and hasClickAction()).performClick()

        composeRule.onNodeWithTag("library_overview_list").performScrollToNode(hasContentDescription(labels.syncLibrary))
        composeRule.onNode(hasContentDescription(labels.overviewSources) and hasClickAction()).performClick()
        composeRule.onNode(hasContentDescription(labels.syncLibrary) and hasClickAction()).performClick()

        assertEquals(
            listOf(
                "search",
                "scan",
                "smart:virtual:recent-added",
                "recent",
                "sources",
                "sync"
            ),
            calls
        )
    }

    @Test
    fun englishOverviewUsesEnglishLabelsAndNoChineseLeakage() {
        val calls = mutableListOf<String>()
        val labels = englishLabels()

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryOverviewScreen(
                    library = LibraryStoreState(),
                    downloads = DownloadsUiState(),
                    labels = labels,
                    onOpenMode = {},
                    onOpenFavorites = {},
                    onOpenRecent = {},
                    onOpenDownloads = {},
                    onOpenSources = { calls += "sources" },
                    onScanLibrary = { calls += "scan" },
                    onSyncLibrary = { calls += "sync" },
                    onOpenSmartCollection = { _, _ -> },
                    onSearch = Runnable { calls += "search" }
                )
            }
        }

        composeRule.onNodeWithText(labels.overviewShelf).assertIsDisplayed()
        composeRule.onNodeWithText(labels.overviewAllSongs).assertIsDisplayed()
        composeRule.onNodeWithText("0" + labels.overviewSongUnit).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(labels.scanLibrary) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("library_overview_list").performScrollToNode(hasContentDescription(labels.syncLibrary))
        composeRule.onNode(hasContentDescription(labels.overviewSources) and hasClickAction()).performClick()
        composeRule.onNode(hasContentDescription(labels.syncLibrary) and hasClickAction()).performClick()

        assertTrue(composeRule.onAllNodesWithText("全部歌曲").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("同步曲库").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("音乐来源与同步").fetchSemanticsNodes().isEmpty())

        assertEquals(listOf("scan", "sources", "sync"), calls)
    }

    private fun englishLabels(): LibraryUiLabels = LibraryUiLabels(
        scanLibrary = "Scan library",
        syncLibrary = "Sync library",
        overviewShelf = "For you",
        overviewBrowse = "Browse by type",
        overviewSaved = "Saved & offline",
        overviewSourcesSync = "Sources & sync",
        overviewFavorites = "Favorites",
        overviewDownloaded = "Downloaded",
        overviewSources = "Music sources",
        overviewSearchHint = "Search songs, albums, artists, or playlists",
        overviewSongUnit = " songs",
        overviewLocalSource = "Local device",
        overviewAllSongs = "All songs",
        overviewAlbums = "Albums",
        overviewArtists = "Artists",
        overviewPlaylists = "Playlists",
        overviewFolders = "Folders",
        smartRecentAdded = "Recently added",
        smartRecentPlayed = "Recently played",
        smartWeekFavorites = "Weekly favorites",
        smartLongUnplayed = "Long unplayed",
        smartRecentAddedEmpty = "Songs you scan or import will show up here",
        smartRecentPlayedEmpty = "No recently played songs yet",
        smartWeekFavoritesEmpty = "Play a few favorites this week and they will show up here",
        smartLongUnplayedEmpty = "You have listened to everything recently. Nice!"
    )

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
}
