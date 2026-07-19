package app.yukine

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.yukine.model.Track
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryOverviewScreen
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
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
    fun chineseOverviewSeparatesSourcesSyncAndSearchActions() {
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
                    modeActions = listOf(
                        TrackListModeAction("歌曲", LibraryGrouping.SONGS, true, Runnable {})
                    ),
                    onOpenMode = { calls += "mode:$it" },
                    onOpenFavorites = { calls += "favorites" },
                    onOpenRecent = { calls += "recent" },
                    onOpenDownloads = { calls += "downloads" },
                    onOpenSources = { calls += "sources" },
                    onManageLibrary = { calls += "sync" },
                    onSearch = Runnable { calls += "search" }
                )
            }
        }

        composeRule.onNodeWithText("全部歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("2 首").assertIsDisplayed()
        composeRule.onNode(hasText("搜索歌曲、专辑、艺人或歌单") and hasClickAction()).performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("音乐来源"))
        composeRule.onNodeWithText("音乐来源").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("同步曲库"))
        composeRule.onNodeWithContentDescription("同步曲库").performClick()

        assertEquals(listOf("search", "sources", "sync"), calls)
    }

    @Test
    fun englishOverviewUsesEnglishSourceAndSyncLabels() {
        val calls = mutableListOf<String>()

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryOverviewScreen(
                    library = LibraryStoreState(),
                    downloads = DownloadsUiState(),
                    modeActions = listOf(
                        TrackListModeAction("Songs", LibraryGrouping.SONGS, true, Runnable {})
                    ),
                    onOpenMode = {},
                    onOpenFavorites = {},
                    onOpenRecent = {},
                    onOpenDownloads = {},
                    onOpenSources = { calls += "sources" },
                    onManageLibrary = { calls += "sync" }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Music sources"))
        composeRule.onNodeWithText("Music sources").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Sync library"))
        composeRule.onNodeWithContentDescription("Sync library").performClick()

        assertEquals(listOf("sources", "sync"), calls)
    }

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
}
