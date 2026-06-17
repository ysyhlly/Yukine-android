package app.yukine.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.MainActivityLibraryGroupsUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Library 分组原生渲染端 [LibraryGroupsDestination]。
 * 验证「StateFlow<MainActivityLibraryGroupsUiState> → LibraryGroupsScreen」渲染。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryGroupsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleAndGroupRows() {
        val state = MutableStateFlow(
            MainActivityLibraryGroupsUiState(
                title = "专辑",
                rows = listOf(
                    LibraryGroupUiState("a1", "专辑甲", "歌手 · 10 首"),
                    LibraryGroupUiState("a2", "专辑乙", "歌手 · 8 首")
                )
            )
        )
        val actions = state.value.rows.map { LibraryGroupActions(Runnable {}, Runnable {}) }

        composeRule.setContent {
            EchoTheme.EchoTheme { LibraryGroupsDestination(state, actions) }
        }

        composeRule.onNodeWithText("专辑").assertIsDisplayed()
        composeRule.onNodeWithText("专辑甲").assertIsDisplayed()
        composeRule.onNodeWithText("专辑乙").assertIsDisplayed()
    }
}
