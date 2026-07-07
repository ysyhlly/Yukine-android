package app.yukine.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.yukine.LibraryTrackListDestinationState
import app.yukine.ui.EchoTheme
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
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
class LibraryTrackListDestinationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val staticMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)

    private fun row(id: Long, title: String, key: String = id.toString()) = TrackRowUiState(
        id = id,
        title = title,
        subtitle = "Artist",
        detail = "",
        duration = "3:00",
        albumArtUri = null,
        current = false,
        favorite = false,
        showPlaylistAction = false,
        key = key
    )

    private fun actions(count: Int) = List(count) {
        TrackRowActions(Runnable {}, Runnable {}, Runnable {}, Runnable {}, null, null)
    }

    @Test
    fun rendersTitleAndRows() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(1L, "First"), row(2L, "Second")),
                actions = actions(2)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("First").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun rendersRowsWithDuplicateTrackIds() {
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Songs",
                rows = listOf(row(7L, "Echo", "7:1"), row(7L, "Echo", "7:2")),
                actions = actions(2)
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
            }
        }

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onAllNodesWithText("Echo").assertCountEquals(2)
    }

    @Test
    fun systemBackRunsHeaderBackAction() {
        var backCount = 0
        val state = MutableStateFlow(
            LibraryTrackListDestinationState(
                title = "Album",
                rows = listOf(row(1L, "First")),
                actions = actions(1),
                headerActions = listOf(
                    TrackListHeaderAction("返回", Runnable { backCount++ }),
                    TrackListHeaderAction("播放分组", Runnable {})
                )
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryTrackListDestination(state, audioMotion = staticMotion)
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
