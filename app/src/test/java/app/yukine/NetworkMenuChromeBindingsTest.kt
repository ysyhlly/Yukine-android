package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkMenuChromeBindingsTest {
    @Test
    fun publishesNetworkMenuChromeState() {
        val metrics = listOf(SettingsMetric("Tracks", "4"))
        val actions = listOf(SettingsAction("Open", Runnable { }))
        var state: NetworkMenuChromeState? = null
        val bindings = NetworkMenuChromeBindings(
            NetworkMenuChromeSink { state = it }
        )

        bindings.publishNetworkMenu("Network", metrics, actions)

        assertEquals("Network", state?.title)
        assertSame(metrics, state?.metrics)
        assertSame(actions, state?.actions)
    }
}
