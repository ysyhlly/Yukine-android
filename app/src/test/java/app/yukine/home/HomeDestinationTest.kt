package app.yukine.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.MainActivityHomeDashboardUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardActions
import app.yukine.ui.HomeDashboardUiState
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private val staticMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)

    @Test
    fun rendersHeroTitleFromState() {
        val state = MutableStateFlow(
            MainActivityHomeDashboardUiState(
                content = HomeDashboardUiState(heroTitle = "晚上好，听点什么？")
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, emptyActions(), audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("晚上好，听点什么？").assertIsDisplayed()
    }

    @Test
    fun reflectsStateUpdate() {
        val state = MutableStateFlow(
            MainActivityHomeDashboardUiState(content = HomeDashboardUiState(heroTitle = "初始问题"))
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, emptyActions(), audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("初始问题").assertIsDisplayed()

        state.value = MainActivityHomeDashboardUiState(
            content = HomeDashboardUiState(heroTitle = "更新问题")
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("更新问题").assertIsDisplayed()
    }
}
