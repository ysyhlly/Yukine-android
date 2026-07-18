package app.yukine.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.yukine.UnifiedSearchUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.StreamingTrackAction
import app.yukine.ui.UnifiedLocalTrackAction
import app.yukine.ui.UnifiedSearchActions
import app.yukine.ui.UnifiedSearchQueryAction
import app.yukine.ui.UnifiedSearchStreamingState
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
class SearchDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchInputAcceptsTextAndSubmitsLatestQuery() {
        lateinit var state: MutableStateFlow<UnifiedSearchUiState>
        var latestInput = ""
        var submittedQuery = ""
        val actions = UnifiedSearchActions(
            onQueryChange = UnifiedSearchQueryAction { query ->
                latestInput = query
                state.value = state.value.copy(query = query)
            },
            onSearch = UnifiedSearchQueryAction { query -> submittedQuery = query },
            onPlayLocalTrack = UnifiedLocalTrackAction { },
            onPlayStreamingTrack = StreamingTrackAction { },
            onLoadMoreStreaming = Runnable { },
            onExit = Runnable { }
        )
        state = MutableStateFlow(UnifiedSearchUiState(actions = actions))

        composeRule.setContent {
            EchoTheme.EchoTheme {
                SearchDestination(
                    searchState = state,
                    streamingState = UnifiedSearchStreamingState(),
                    activeDownload = null,
                    playbackQuality = "",
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("想听什么，就从这里开始。").assertIsDisplayed()
        composeRule.onNodeWithText("一次搜索，两处回声").assertIsDisplayed()
        composeRule.onNodeWithTag("unified-search-input")
            .assertHeightIsAtLeast(56.dp)
            .performTextInput("周杰伦")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("周杰伦").assertIsDisplayed()
        composeRule.onNodeWithText("关于「周杰伦」").assertIsDisplayed()
        composeRule.onNodeWithText("本地曲库").assertIsDisplayed()
        composeRule.onNodeWithText("在线音乐").assertIsDisplayed()
        composeRule.onNodeWithTag("unified-search-input").performImeAction()

        assertEquals("周杰伦", latestInput)
        assertEquals("周杰伦", submittedQuery)
    }
}
