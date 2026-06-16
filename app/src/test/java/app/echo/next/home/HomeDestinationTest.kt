package app.echo.next.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.echo.next.MainActivityHomeDashboardUiState
import app.echo.next.ui.EchoTheme
import app.echo.next.ui.HomeDashboardActions
import app.echo.next.ui.HomeDashboardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Home tab 原生渲染端 [HomeDestination]。
 * 验证「StateFlow<MainActivityHomeDashboardUiState> → HomeDashboardScreen」渲染闭环。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun emptyActions() = HomeDashboardActions(
        onOpenStat = emptyList(),
        onContinue = Runnable {},
        onOpenNowPlaying = Runnable {},
        onPlayRecent = emptyList(),
        onRefresh = Runnable {},
        onViewQueue = Runnable {},
        onShuffleAll = Runnable {},
        onRecentTabChanged = {}
    )

    @Test
    fun rendersHeroTitleFromState() {
        val state = MutableStateFlow(
            MainActivityHomeDashboardUiState(
                content = HomeDashboardUiState(heroTitle = "晚上好，听点什么")
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme { HomeDestination(state, emptyActions()) }
        }

        composeRule.onNodeWithText("晚上好，听点什么").assertIsDisplayed()
    }

    @Test
    fun reflectsStateUpdate() {
        val state = MutableStateFlow(
            MainActivityHomeDashboardUiState(content = HomeDashboardUiState(heroTitle = "初始问候"))
        )

        composeRule.setContent {
            EchoTheme.EchoTheme { HomeDestination(state, emptyActions()) }
        }

        composeRule.onNodeWithText("初始问候").assertIsDisplayed()

        state.value = MainActivityHomeDashboardUiState(
            content = HomeDashboardUiState(heroTitle = "更新问候")
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("更新问候").assertIsDisplayed()
    }
}
