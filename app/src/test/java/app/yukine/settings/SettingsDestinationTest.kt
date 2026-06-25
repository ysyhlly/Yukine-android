package app.yukine.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.SettingsState
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
}
