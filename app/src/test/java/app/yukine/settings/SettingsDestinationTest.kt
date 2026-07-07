package app.yukine.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.SettingsState
import app.yukine.SettingsUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsDestinationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersTitleMetricsAndStateActions() {
        val state = MutableStateFlow(
            SettingsState(
                actions = listOf(
                    SettingsAction("Appearance", Runnable {}),
                    SettingsAction("Playback", Runnable {})
                ),
                ui = SettingsUiState(
                    title = "Settings",
                    metrics = listOf(SettingsMetric("Tracks", "128")),
                    items = emptyList()
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                SettingsDestination(
                    state,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Playback").assertIsDisplayed()
    }

    @Test
    fun systemBackRunsLibrarySettingsBackAction() {
        var backCount = 0
        val state = MutableStateFlow(
            SettingsState(
                actions = listOf(
                    SettingsAction("返回", Runnable { backCount++ }),
                    SettingsAction("扫描曲库", Runnable {})
                ),
                ui = SettingsUiState(
                    title = "曲库",
                    metrics = listOf(SettingsMetric("歌曲", "128")),
                    items = emptyList()
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                SettingsDestination(
                    state,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
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
