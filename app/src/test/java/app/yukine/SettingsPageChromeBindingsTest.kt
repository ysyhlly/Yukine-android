package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsListScrollState
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsPageChromeBindingsTest {
    @Test
    fun publishesSettingsPageChromeState() {
        val actions = listOf(SettingsAction("Action", Runnable { }))
        val scrollState = SettingsListScrollState(2, 8)
        var state: SettingsPageChromeState? = null
        val bindings = SettingsPageChromeBindings(
            SettingsPageChromeSink { state = it }
        )

        bindings.publishSettingsChrome(actions, scrollState)

        assertSame(actions, state?.actions)
        assertSame(scrollState, state?.scrollState)
    }
}
