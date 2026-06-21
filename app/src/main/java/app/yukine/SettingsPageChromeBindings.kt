package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsListScrollState

internal data class SettingsPageChromeState(
    val actions: List<SettingsAction>,
    val scrollState: SettingsListScrollState
)

internal fun interface SettingsPageChromeSink {
    fun publish(state: SettingsPageChromeState)
}

internal class SettingsPageChromeBindings(
    private val sink: SettingsPageChromeSink
) : SettingsPageEventController.ContentSink {
    override fun publishSettingsChrome(
        actions: List<SettingsAction>,
        scrollState: SettingsListScrollState
    ) {
        sink.publish(SettingsPageChromeState(actions, scrollState))
    }
}
