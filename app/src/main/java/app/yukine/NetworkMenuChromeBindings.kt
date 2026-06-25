package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

internal class NetworkMenuChromeBindings(
    private val viewModel: NetworkMenuViewModel
) : NetworkMenuEventController.ContentSink {
    override fun publishNetworkMenu(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        viewModel.updateMenu(title, metrics, actions)
    }
}
