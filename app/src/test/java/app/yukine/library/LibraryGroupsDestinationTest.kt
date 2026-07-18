package app.yukine.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.yukine.LibraryGroupsDestinationState
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryActionHandler
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.LibraryMode
import app.yukine.ui.LibraryPlaylistFolderEntryUiState
import app.yukine.ui.LibraryPlaylistFolderUiState
import app.yukine.ui.LibraryUiState
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                modeActions = listOf(TrackListModeAction("Songs", "songs", true, Runnable {})),
                libraryUi = LibraryUiState(mode = LibraryMode.Artists)
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
        composeRule.onNodeWithText("2 个结果").assertIsDisplayed()
        composeRule.onNodeWithText("搜索曲库").assertIsDisplayed()
        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onNodeWithText("Artist One").assertIsDisplayed()
        composeRule.onNodeWithText("Artist Two").assertIsDisplayed()
    }

    @Test
    fun visibleBackButtonUsesTheDestinationNavigateUpCallback() {
        var backCalls = 0
        val state = MutableStateFlow(
            LibraryGroupsDestinationState(
                title = "Albums",
                libraryUi = LibraryUiState(mode = LibraryMode.Albums)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(
                    state = state,
                    onNavigateUp = Runnable { backCalls++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun longPressEntersGroupSelectionAndBulkDeleteUsesTheSharedActionPath() {
        val events = mutableListOf<LibraryAction>()
        val group = LibraryGroupUiState("artists:a", "Artist One", "1 album · 2 songs")
        val state = MutableStateFlow(
            LibraryGroupsDestinationState(
                title = "Artists",
                rows = listOf(group),
                actions = listOf(LibraryGroupActions(Runnable {}, Runnable {})),
                libraryUi = LibraryUiState(mode = LibraryMode.Artists)
            )
        )
        val handler = LibraryActionHandler { action ->
            events += action
            if (action is LibraryAction.ToggleGroupSelection) {
                state.value = state.value.copy(
                    libraryUi = state.value.libraryUi.copy(selectedGroupKeys = setOf(action.key))
                )
            }
        }

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(state = state, actionHandler = handler)
            }
        }

        composeRule.onNodeWithText("Artist One").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 项已选择").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()

        assertTrue(events.first() is LibraryAction.ToggleGroupSelection)
        assertTrue(events.last() is LibraryAction.DeleteSelected)
    }

    @Test
    fun foldersShowStorageRootsWithoutInventingParentRows() {
        val state = MutableStateFlow(
            LibraryGroupsDestinationState(
                title = "文件夹",
                rows = listOf(
                    LibraryGroupUiState(
                        id = "folders:echo",
                        title = "Echo",
                        subtitle = "2 首歌曲",
                        trackCount = 2,
                        groupKey = "/storage/emulated/0/Music/Echo"
                    )
                ),
                actions = listOf(LibraryGroupActions(Runnable {}, Runnable {})),
                libraryUi = LibraryUiState(mode = LibraryMode.Folders)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(state)
            }
        }

        composeRule.onNodeWithText("/storage/emulated/0").assertIsDisplayed()
        composeRule.onNodeWithText("Echo").assertIsDisplayed()
        composeRule.onNodeWithText("Music/Echo").assertIsDisplayed()
        composeRule.onNodeWithText("Music").assertDoesNotExist()
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
                libraryUi = LibraryUiState(mode = LibraryMode.Playlists),
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
