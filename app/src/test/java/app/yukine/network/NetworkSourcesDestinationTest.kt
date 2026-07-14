package app.yukine.network

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.NetworkSourcesUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.NetworkSourceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkSourcesDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleAndSourceRows() {
        val state = MutableStateFlow(
            NetworkSourcesUiState(
                title = "闊虫簮",
                rows = listOf(
                    NetworkSourceUiState(1L, "家庭 WebDAV", "https://nas.local", "已连接"),
                    NetworkSourceUiState(2L, "浜戠洏", "https://cloud.example", "鏈悓姝?")
                ),
                actions = listOf(
                    NetworkSourceActions(Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {}),
                    NetworkSourceActions(Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {}, Runnable {})
                ),
                headerActions = emptyList(),
                emptyText = "",
                labels = NetworkSourceLabels()
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme { NetworkSourcesDestination(state) }
        }

        composeRule.onNodeWithText("闊虫簮").assertIsDisplayed()
        composeRule.onNodeWithText("家庭 WebDAV").assertIsDisplayed()
        composeRule.onNodeWithText("浜戠洏").assertIsDisplayed()
    }
}
