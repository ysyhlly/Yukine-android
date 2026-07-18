package app.yukine.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.yukine.HomeDashboardDestinationState
import app.yukine.emptyHomeDashboardActions
import app.yukine.ui.EchoTheme
import app.yukine.ui.HomeDashboardLayout
import app.yukine.ui.HomeDashboardUiState
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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

    private val staticMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)

    @Test
    fun rendersHeroTitleFromState() {
        val state = MutableStateFlow(
            HomeDashboardDestinationState(
                content = HomeDashboardUiState(heroTitle = "晚上好，听点什么？")
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("晚上好，听点什么？").assertIsDisplayed()
    }

    @Test
    fun reflectsStateUpdate() {
        val state = MutableStateFlow(
            HomeDashboardDestinationState(content = HomeDashboardUiState(heroTitle = "初始问题"))
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("初始问题").assertIsDisplayed()

        state.value = HomeDashboardDestinationState(
            content = HomeDashboardUiState(heroTitle = "更新问题")
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("更新问题").assertIsDisplayed()
    }

    @Test
    fun viewQueueCardInvokesQueueAction() {
        val openCount = AtomicInteger()
        val state = MutableStateFlow(
            HomeDashboardDestinationState(
                content = HomeDashboardUiState(heroTitle = "队列入口"),
                actions = emptyHomeDashboardActions().copy(
                    onViewQueue = Runnable { openCount.incrementAndGet() }
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("队列").performClick()
        composeRule.waitForIdle()

        assertEquals(1, openCount.get())
    }

    @Test
    fun contentLayoutKeepsContentDashboardActions() {
        val state = MutableStateFlow(
            HomeDashboardDestinationState(
                content = HomeDashboardUiState(heroTitle = "内容主页"),
                actions = emptyHomeDashboardActions()
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(
                    state = state,
                    audioMotion = staticMotion,
                    layout = HomeDashboardLayout.Content
                )
            }
        }

        composeRule.onNodeWithText("查看队列").assertIsDisplayed()
        composeRule.onNodeWithText("随机播放").assertIsDisplayed()
    }

    @Test
    fun classicLayoutShowsTodayListeningAndKeepsRecentPlayback() {
        val state = MutableStateFlow(
            HomeDashboardDestinationState(
                content = HomeDashboardUiState(heroTitle = "经典主页"),
                actions = emptyHomeDashboardActions()
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(
                    state = state,
                    audioMotion = staticMotion,
                    layout = HomeDashboardLayout.Classic
                )
            }
        }

        composeRule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("今日聆听轨迹"))
        composeRule.onNodeWithText("今日聆听轨迹").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("最近播放"))
        composeRule.onNodeWithText("最近播放").assertIsDisplayed()
    }

    @Test
    fun playingDashboardShowsNextTrackCardAndInvokesNextAction() {
        val nextCount = AtomicInteger()
        val state = MutableStateFlow(
            HomeDashboardDestinationState(
                content = HomeDashboardUiState(
                    continueTitle = "Current song",
                    continuePlaying = true,
                    nextTitle = "Next song",
                    nextSubtitle = "Next artist"
                ),
                actions = emptyHomeDashboardActions().copy(
                    onNext = Runnable { nextCount.incrementAndGet() }
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                HomeDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("Next song").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(1, nextCount.get())
    }
}
