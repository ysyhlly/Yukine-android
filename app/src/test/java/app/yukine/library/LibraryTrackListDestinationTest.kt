package app.yukine.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.MainActivityTrackListUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.TrackRowUiState
import app.yukine.ui.TrackRowActions
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI 测试：track-list 原生渲染端 [LibraryTrackListDestination]。
 * 验证「StateFlow<MainActivityTrackListUiState> → TrackListScreen」渲染（title + rows）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryTrackListDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(id: Long, title: String) = TrackRowUiState(
        id = id,
        title = title,
        subtitle = "Artist",
        detail = "",
        duration = "3:00",
        albumArtUri = null,
        current = false,
        favorite = false,
        showPlaylistAction = false
    )

    @Test
    fun rendersTitleAndRows() {
        val state = MutableStateFlow(
            MainActivityTrackListUiState(
                title = "歌曲",
                rows = listOf(row(1L, "第一首"), row(2L, "第二首"))
            )
        )
        val actions = state.value.rows.map { TrackRowActions(Runnable {}, Runnable {}, Runnable {}, null, null) }

        composeRule.setContent {
            EchoTheme.EchoTheme { LibraryTrackListDestination(state, actions) }
        }

        composeRule.onNodeWithText("歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("第一首").assertIsDisplayed()
        composeRule.onNodeWithText("第二首").assertIsDisplayed()
    }
}
