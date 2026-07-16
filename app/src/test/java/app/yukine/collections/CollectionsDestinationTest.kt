package app.yukine.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.yukine.CollectionsViewModel
import app.yukine.ui.CollectionPlaylistFolderUiState
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.PlaylistRowActions
import app.yukine.ui.PlaylistRowUiState
import app.yukine.ui.emptyCollectionsActions
import app.yukine.ui.EchoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Collections tab 的原生渲染端 [CollectionsDestination]。
 *
 * 验证「ViewModel.screen StateFlow → CollectionsScreen 渲染」闭环。actions 现在也由
 * ViewModel.screen 一起承载，destination 只做纯渲染。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CollectionsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun screenState(title: String) = CollectionsUiState(
        title = title,
        metrics = emptyList(),
        topActions = emptyList(),
        trackSections = emptyList(),
        playlistTitle = "歌单",
        playlistEmptyText = "",
        playlistEmptyDescription = "",
        playlists = emptyList(),
        selectedPlaylistVisible = false,
        selectedPlaylistTitle = "",
        selectedPlaylistEmptyText = "",
        selectedPlaylistEmptyDescription = "",
        selectedPlaylistTopActions = emptyList(),
        selectedPlaylistTracks = emptyList(),
        actions = emptyCollectionsActions()
    )

    @Test
    fun rendersScreenTitleFromViewModelState() {
        val vm = CollectionsViewModel()
        vm.updateScreen(screenState("我的收藏"))

        composeRule.setContent {
            EchoTheme.EchoTheme { CollectionsDestination(vm) }
        }

        composeRule.onNodeWithText("我的收藏").assertIsDisplayed()
    }

    @Test
    fun reflectsUpdatedStateFromViewModel() {
        val vm = CollectionsViewModel()
        vm.updateScreen(screenState("初始标题"))

        composeRule.setContent {
            EchoTheme.EchoTheme { CollectionsDestination(vm) }
        }

        composeRule.onNodeWithText("初始标题").assertIsDisplayed()

        vm.updateScreen(screenState("更新标题"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("更新标题").assertIsDisplayed()
    }

    @Test
    fun rendersSourceFolderWithChildPlaylistsAndCanCollapseIt() {
        val vm = CollectionsViewModel()
        val playlist = PlaylistRowUiState("通勤歌单", "12 首歌曲", false, 7L, 0)
        val actions = emptyCollectionsActions().copy(
            playlistActions = listOf(PlaylistRowActions(Runnable {}, Runnable {}, Runnable {}))
        )
        vm.updateScreen(
            screenState("收藏").copy(
                playlists = listOf(playlist),
                playlistFolders = listOf(
                    CollectionPlaylistFolderUiState(
                        key = "netease",
                        title = "网易云音乐",
                        subtitle = "1 个歌单 · 12 首歌曲",
                        playlists = listOf(playlist)
                    )
                ),
                actions = actions
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme { CollectionsDestination(vm) }
        }

        composeRule.onNodeWithText("网易云音乐").assertIsDisplayed()
        composeRule.onNodeWithText("通勤歌单").assertIsDisplayed()
        composeRule.onNodeWithText("网易云音乐").performClick()
        composeRule.onNodeWithText("通勤歌单").assertDoesNotExist()
    }
}
