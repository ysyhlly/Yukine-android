package app.yukine.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import app.yukine.RecordingMatchDestinationStateProvider
import app.yukine.RecordingMatchUiState
import app.yukine.ui.RecordingMatchScreen

@Composable
fun RecordingMatchDestination(
    state: RecordingMatchUiState,
    provider: RecordingMatchDestinationStateProvider
) {
    BackHandler(enabled = state.visible) { provider.close() }
    RecordingMatchScreen(state = state, provider = provider)
}
