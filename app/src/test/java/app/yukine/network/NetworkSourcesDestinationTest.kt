package app.yukine.network

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.MainActivityNetworkSourcesUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：Network sources 原生渲染端 [NetworkSourcesDestination]。
 * 验证「StateFlow<MainActivityNetworkSourcesUiState> → NetworkSourcesScreen」渲染。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkSourcesDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleAndSourceRows() {
        val state = MutableStateFlow(
            MainActivityNetworkSourcesUiState(
                title = "音源",
                rows = listOf(
                    NetworkSourceUiState(1L, "家庭 WebDAV", "https://nas.local", "已连接"),
                    NetworkSourceUiState(2L, "云盘", "https://cloud.example", "未同步")
                )
            )
        )
        val actions = state.value.rows.map {
            NetworkSourceActions(Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {})
        }

        composeRule.setContent {
            EchoTheme.EchoTheme { NetworkSourcesDestination(state, actions) }
        }

        composeRule.onNodeWithText("音源").assertIsDisplayed()
        composeRule.onNodeWithText("家庭 WebDAV").assertIsDisplayed()
        composeRule.onNodeWithText("云盘").assertIsDisplayed()
    }
}
