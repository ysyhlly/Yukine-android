package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

internal data class NetworkMenuChromeState(
    val title: String,
    val metrics: List<SettingsMetric>,
    val actions: List<SettingsAction>
)

internal fun interface NetworkMenuChromeSink {
    fun publish(state: NetworkMenuChromeState)
}

internal class NetworkMenuChromeBindings(
    private val sink: NetworkMenuChromeSink
) : NetworkMenuEventController.ContentSink {
    override fun publishNetworkMenu(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        sink.publish(NetworkMenuChromeState(title, metrics, actions))
    }
}
