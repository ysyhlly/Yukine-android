package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMenuChromeBindingsTest {
    @Test
    fun publishesNetworkMenuUiState() {
        val metrics = listOf(SettingsMetric("Tracks", "4"))
        val actions = listOf(SettingsAction("Open", Runnable { }))
        val viewModel = NetworkMenuViewModel()
        val bindings = NetworkMenuChromeBindings(viewModel)

        bindings.publishNetworkMenu("Network", metrics, actions)

        val state = viewModel.uiState.value
        assertEquals("Network", state.title)
        assertEquals(metrics, state.metrics)
        assertEquals(actions, state.actions)
    }
}
