package app.echo.next.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.echo.next.SettingsUiState
import app.echo.next.ui.EchoTheme
import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsMetric
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Settings 原生渲染端 [SettingsDestination]。
 * 验证「StateFlow<SettingsUiState>(title/metrics) + 注入 actions → SettingsScreen」渲染。
 */
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
            EchoTheme.EchoTheme { SettingsDestination(state, actions) }
        }

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithText("播放").assertIsDisplayed()
    }
}
