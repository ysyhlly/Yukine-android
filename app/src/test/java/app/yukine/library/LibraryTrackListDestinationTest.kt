package app.yukine.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.yukine.LibraryTrackListDestinationState
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoIconKind
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderActionKind
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import app.yukine.ui.YukineOrbAudioMotion
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryActionHandler
import app.yukine.ui.LibraryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryTrackListDestinationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val staticMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)

    private fun row(id: Long, title: String, key: String = id.toString()) = TrackRowUiState(
        id = id,
        title = title,
        subtitle = "Artist",
        detail = "",
        duration = "3:00",
        albumArtUri = null,
        current = false,
        favorite = false,
        showPlaylistAction = false,
        key = key
    )

    private fun actions(count: Int) = List(count) {
        TrackRowActions(
            onPlay = Runnable {},
            onFavorite = Runnable {},
            onAddToPlaylist = Runnable {},
            onDownload = Runnable {},
            onEdit = null,
            onDelete = Runnable {},
            onLongPress = Runnable {}
        )
    }

    @Test
    fun rendersTitleAndRows() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "First"), row(2L, "Second")),
                actions = actions(2)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("First").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun rendersRowsWithDuplicateTrackIds() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(7L, "Echo", "7:1"), row(7L, "Echo", "7:2")),
                actions = actions(2)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onAllNodesWithText("Echo").assertCountEquals(2)
    }

    @Test
    fun modeSelectorDispatchesOnceFromA48DpSemanticTarget() {
        var modeClicks = 0
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                modeActions = listOf(
                    TrackListModeAction("Albums", "albums", false, Runnable { modeClicks++ })
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithContentDescription("Albums")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, modeClicks)
    }

    @Test
    fun modeSelectorScrollsTheLastModeFullyIntoView() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                modeActions = listOf(
                    TrackListModeAction("Songs", "songs", true, Runnable {}),
                    TrackListModeAction("Albums", "albums", false, Runnable {}),
                    TrackListModeAction("Artists", "artists", false, Runnable {}),
                    TrackListModeAction("Folders", "folders", false, Runnable {}),
                    TrackListModeAction("Playlists", "playlists", false, Runnable {})
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithContentDescription("Playlists")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(104.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun unavailableDeleteAndDownloadActionsAreNotRendered() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "No capabilities")),
                actions = listOf(
                    TrackRowActions(
                        onPlay = Runnable {},
                        onFavorite = Runnable {},
                        onAddToPlaylist = Runnable {},
                        onDownload = null,
                        onEdit = null,
                        onDelete = null,
                        onLongPress = null
                    )
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithContentDescription("更多操作")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("下载").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertDoesNotExist()
    }

    @Test
    fun openActionSheetTracksLatestFavoriteStateAndClosesWhenRowDisappears() {
        val initialRow = row(1L, "Live favorite")
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(initialRow),
                actions = actions(1)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()

        state.value = state.value.copy(
            rows = listOf(initialRow.copy(favoritePending = true))
        )
        composeRule.onNodeWithText("正在更新收藏").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertDoesNotExist()

        state.value = state.value.copy(
            rows = listOf(initialRow.copy(favorite = true, favoritePending = false))
        )
        composeRule.onNodeWithText("取消收藏").assertIsDisplayed()
        composeRule.onNodeWithText("正在更新收藏").assertDoesNotExist()

        state.value = state.value.copy(rows = emptyList(), actions = emptyList())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消收藏").assertDoesNotExist()
    }

    @Test
    fun libraryControlsExposeInlineSearchAndTypedQueryAction() {
        val calls = ArrayList<LibraryAction>()
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "First")),
                actions = actions(1)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    actionHandler = LibraryActionHandler { calls.add(it) },
                    libraryControlsEnabled = true
                )
            }
        }

        composeRule.onNodeWithText("搜索曲库").performTextInput("rock")
        composeRule.runOnIdle {
            assertEquals(LibraryAction.QueryChanged("rock"), calls.last())
        }
    }

    @Test
    fun libraryRootShowsBackAndUsesFallbackForButtonAndSystemBack() {
        var backCount = 0
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "First")),
                actions = actions(1)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    libraryControlsEnabled = true,
                    onNavigateUp = Runnable { backCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.runOnIdle {
            assertEquals(2, backCount)
        }
    }

    @Test
    fun rootActionsFiltersSortAndTrackGesturesDispatchTypedActions() {
        val calls = ArrayList<LibraryAction>()
        var playCount = 0
        val trackActions = TrackRowActions(
            onPlay = Runnable { playCount++ },
            onFavorite = Runnable { },
            onAddToPlaylist = Runnable { },
            onDownload = Runnable { },
            onEdit = null,
            onDelete = Runnable { },
            onLongPress = Runnable { }
        )
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "First")),
                actions = listOf(trackActions),
                headerActions = listOf(
                    TrackListHeaderAction(
                        "播放全部",
                        Runnable { },
                        icon = EchoIconKind.Play,
                        kind = TrackListHeaderActionKind.PlayAll
                    ),
                    TrackListHeaderAction(
                        "随机播放",
                        Runnable { },
                        icon = EchoIconKind.Shuffle,
                        kind = TrackListHeaderActionKind.Shuffle
                    )
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    actionHandler = LibraryActionHandler { action ->
                        calls.add(action)
                        if (action is LibraryAction.RevealTrack) {
                            state.value = state.value.copy(
                                libraryUi = state.value.libraryUi.copy(revealedRowKey = action.key)
                            )
                        }
                    },
                    libraryControlsEnabled = true,
                    onNavigateUp = Runnable { }
                )
            }
        }

        composeRule.onNodeWithContentDescription("播放全部").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("随机播放").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").performClick()
        composeRule.runOnIdle {
            assertEquals(LibraryAction.FilterChanged(app.yukine.ui.LibraryFilter.Favorites), calls.last())
        }

        composeRule.onNodeWithText("First").performClick()
        composeRule.runOnIdle {
            assertEquals(1, playCount)
        }
        composeRule.onNodeWithText("First").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(LibraryAction.ToggleTrackSelection("1"), calls.last())
        }

        composeRule.onNodeWithContentDescription("标题 A-Z").performClick()
        composeRule.onAllNodesWithText("标题 A-Z").assertCountEquals(2)
        composeRule.onAllNodesWithText("标题 Z-A").assertCountEquals(1)
        composeRule.onAllNodesWithText("歌手").assertCountEquals(1)
        composeRule.onAllNodesWithText("专辑").assertCountEquals(1)
        composeRule.onAllNodesWithText("时长升序").assertCountEquals(1)
        composeRule.onAllNodesWithText("时长降序").assertCountEquals(1)
    }

    @Test
    fun emptyLibraryOffersScanAndImportAndSwipeStopsAtActions() {
        val calls = ArrayList<LibraryAction>()
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = emptyList(),
                actions = emptyList()
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    actionHandler = LibraryActionHandler { action ->
                        calls.add(action)
                        if (action is LibraryAction.RevealTrack) {
                            state.value = state.value.copy(
                                libraryUi = state.value.libraryUi.copy(revealedRowKey = action.key)
                            )
                        }
                    },
                    libraryControlsEnabled = true,
                    onNavigateUp = Runnable { }
                )
            }
        }

        composeRule.onNodeWithText("扫描曲库").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("导入文件").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(LibraryAction.ScanLibrary, LibraryAction.ImportFiles), calls.takeLast(2))
        }

        state.value = state.value.copy(
            rows = listOf(row(2L, "Swipe me")),
            actions = actions(1)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Swipe me").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("更多").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun activeSelectionReplacesNormalControlsWithBulkToolbar() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "Selected")),
                actions = actions(1),
                libraryUi = LibraryUiState(selectedTrackKeys = setOf("1"))
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    libraryControlsEnabled = true,
                    onNavigateUp = Runnable { }
                )
            }
        }

        composeRule.onNodeWithText("1 项已选择").assertIsDisplayed()
        composeRule.onNodeWithText("全选").assertIsDisplayed()
        composeRule.onNodeWithText("播放").assertIsDisplayed()
        composeRule.onNodeWithText("加入歌单").assertIsDisplayed()
        composeRule.onNodeWithText("下载").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").assertCountEquals(1)
        composeRule.onAllNodesWithText("搜索曲库").assertCountEquals(0)
    }

    @Test
    fun searchAndFilterEmptyStatesOfferTheRightRecoveryAction() {
        val calls = ArrayList<LibraryAction>()
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                libraryUi = LibraryUiState(query = "missing")
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(
                    state = state,
                    audioMotion = staticMotion,
                    actionHandler = LibraryActionHandler { calls.add(it) },
                    libraryControlsEnabled = true,
                    onNavigateUp = Runnable { }
                )
            }
        }

        composeRule.onNodeWithText("没有匹配的歌曲").assertIsDisplayed()
        composeRule.onNode(hasText("清除搜索") and hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(LibraryAction.QueryChanged(""), calls.last())
        }
    }

    @Test
    fun systemBackRunsHeaderBackAction() {
        var backCount = 0
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Album",
                rows = listOf(row(1L, "First")),
                actions = actions(1),
                headerActions = listOf(
                    TrackListHeaderAction("返回", Runnable { backCount++ }, isBack = true, icon = EchoIconKind.Back),
                    TrackListHeaderAction("播放分组", Runnable {})
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }
}
