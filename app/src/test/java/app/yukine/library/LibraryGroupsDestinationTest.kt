package app.yukine.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.yukine.LibraryGroupsDestinationState
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryPlaylistFolderEntryUiState
import app.yukine.ui.LibraryPlaylistFolderUiState
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryGroupsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleAndGroupRows() {
        val state = MutableStateFlow(
            LibraryGroupsDestinationState(
                title = "Artists",
                rows = listOf(
                    LibraryGroupUiState("a1", "Artist One", "10 songs"),
                    LibraryGroupUiState("a2", "Artist Two", "8 songs")
                ),
                actions = listOf(
                    LibraryGroupActions(Runnable {}, Runnable {}, false, null),
                    LibraryGroupActions(Runnable {}, Runnable {}, false, null)
                ),
                emptyText = "No artists",
                modeActions = listOf(TrackListModeAction("Songs", "songs", true, Runnable {}))
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(
                    state,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("Artist One").assertIsDisplayed()
        composeRule.onNodeWithText("Artist Two").assertIsDisplayed()
    }

    @Test
    fun keepsBuiltInPlaylistsVisibleAndGroupsOrdinaryPlaylistsBySource() {
        val neteasePlaylist = LibraryGroupUiState("playlist:7", "通勤歌单", "12 首歌曲")
        val state = MutableStateFlow(
            LibraryGroupsDestinationState(
                title = "歌单",
                rows = listOf(
                    LibraryGroupUiState("virtual:favorites", "收藏歌单", "1 首歌曲"),
                    LibraryGroupUiState("virtual:play-history", "播放历史", "28 首歌曲")
                ),
                actions = listOf(
                    LibraryGroupActions(Runnable {}, Runnable {}, true, null),
                    LibraryGroupActions(Runnable {}, Runnable {}, true, null),
                    LibraryGroupActions(Runnable {}, Runnable {}, true, null)
                ),
                playlistFolders = listOf(
                    LibraryPlaylistFolderUiState(
                        key = "netease",
                        title = "网易云音乐",
                        subtitle = "1 个歌单 · 12 首歌曲",
                        entries = listOf(LibraryPlaylistFolderEntryUiState(neteasePlaylist, 2))
                    )
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(
                    state,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("收藏歌单").assertIsDisplayed()
        composeRule.onNodeWithText("播放历史").assertIsDisplayed()
        composeRule.onNodeWithText("网易云音乐").assertIsDisplayed()
        composeRule.onNodeWithText("通勤歌单").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("网易云音乐").performScrollTo().performClick()
        composeRule.onNodeWithText("通勤歌单").assertDoesNotExist()
    }
}
