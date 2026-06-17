package app.yukine.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.CollectionsViewModel
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.EchoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Collections tab 的原生渲染端 [CollectionsDestination]。
 *
 * 验证「ViewModel.screen StateFlow → CollectionsScreen 渲染」闭环。actions 由调用方注入
 * （此处用空 actions），与 QueueDestination 同形态：destination 是纯渲染端，可独立测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CollectionsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun emptyActions() = CollectionsActions(
        onBack = null,
        topActions = emptyList(),
        trackSections = emptyList(),
        playlistActions = emptyList(),
        selectedPlaylistTopActions = emptyList(),
        selectedPlaylistTrackActions = emptyList()
    )

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
        selectedPlaylistTracks = emptyList()
    )

    @Test
    fun rendersScreenTitleFromViewModelState() {
        val vm = CollectionsViewModel()
        vm.updateScreen(screenState("我的收藏"))

        composeRule.setContent {
            EchoTheme.EchoTheme { CollectionsDestination(vm, emptyActions()) }
        }

        composeRule.onNodeWithText("我的收藏").assertIsDisplayed()
    }

    @Test
    fun reflectsUpdatedStateFromViewModel() {
        val vm = CollectionsViewModel()
        vm.updateScreen(screenState("初始标题"))

        composeRule.setContent {
            EchoTheme.EchoTheme { CollectionsDestination(vm, emptyActions()) }
        }

        composeRule.onNodeWithText("初始标题").assertIsDisplayed()

        vm.updateScreen(screenState("更新标题"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("更新标题").assertIsDisplayed()
    }
}
