package app.yukine.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.SettingsUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleMetricsAndInjectedActions() {
        val state = MutableStateFlow(
            SettingsUiState(
                title = "设置",
                metrics = listOf(SettingsMetric("曲目", "128")),
                items = emptyList()
            )
        )
        val actions = listOf(
            SettingsAction("外观", Runnable {}),
            SettingsAction("播放", Runnable {})
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                SettingsDestination(
                    state,
                    actions,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithText("播放").assertIsDisplayed()
    }
}
