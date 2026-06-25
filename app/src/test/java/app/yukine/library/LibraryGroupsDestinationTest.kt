package app.yukine.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.yukine.MainActivityLibraryGroupsUiState
import app.yukine.ui.EchoTheme
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryGroupsDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleAndGroupRows() {
        val state = MutableStateFlow(
            MainActivityLibraryGroupsUiState(
                title = "Artists",
                rows = listOf(
                    LibraryGroupUiState("a1", "Artist One", "10 songs"),
                    LibraryGroupUiState("a2", "Artist Two", "8 songs")
                ),
                actions = listOf(
                    LibraryGroupActions(Runnable {}, Runnable {}, false, null),
                    LibraryGroupActions(Runnable {}, Runnable {}, false, null)
                ),
                emptyText = "No artists",
                modeActions = listOf(TrackListModeAction("Songs", "songs", true, Runnable {}))
            )
        )

        composeRule.setContent {
            EchoTheme.EchoTheme {
                LibraryGroupsDestination(
                    state,
                    audioMotion = YukineOrbAudioMotion.Empty.copy(visualMotionEnabled = false)
                )
            }
        }

        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("Artist One").assertIsDisplayed()
        composeRule.onNodeWithText("Artist Two").assertIsDisplayed()
    }
}
