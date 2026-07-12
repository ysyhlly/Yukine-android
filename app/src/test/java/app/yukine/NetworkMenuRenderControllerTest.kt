package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMenuRenderControllerTest {
    @Test
    fun homeUsesMergedSourcesAndNetworkTitleAndKeepsDestinations() {
        val listener = RecordingListener()
        val controller = NetworkMenuRenderController(listener)

        controller.renderHome(AppLanguage.MODE_CHINESE, 1, 2, 3)
        listener.actions.forEach { action -> action.onClick.run() }

        assertEquals(AppLanguage.text(AppLanguage.MODE_CHINESE, "settings.group.sources"), listener.title)
        assertEquals(
            listOf(MainRoutes.NETWORK_STREAMING, MainRoutes.NETWORK_WEBDAV, MainRoutes.NETWORK_SOURCES),
            listener.pages
        )
    }

    @Test
    fun visibleBackActionsUseRouteBackStack() {
        val listener = RecordingListener()
        val controller = NetworkMenuRenderController(listener)

        controller.renderStreaming(AppLanguage.MODE_ENGLISH, 0)
        listener.actions.first().onClick.run()
        controller.renderWebDav(AppLanguage.MODE_ENGLISH, 0, 0)
        listener.actions.first().onClick.run()

        assertEquals(2, listener.backCount)
        assertEquals(emptyList<String>(), listener.pages)
    }

    private class RecordingListener : NetworkMenuRenderController.Listener {
        var title = ""
        var actions = emptyList<SettingsAction>()
        val pages = mutableListOf<String>()
        var backCount = 0

        override fun navigateNetworkPage(page: String) { pages += page }
        override fun backFromNetworkPage() { backCount += 1 }
        override fun showAddStream() = Unit
        override fun showImportM3u() = Unit
        override fun openM3uFilePicker() = Unit
        override fun playAllStreams() = Unit
        override fun confirmDeleteAllStreams() = Unit
        override fun showAddWebDav() = Unit
        override fun syncAllWebDavSources() = Unit
        override fun playAllWebDavTracks() = Unit

        override fun publishNetworkMenu(
            title: String,
            metrics: List<SettingsMetric>,
            actions: List<SettingsAction>
        ) {
            this.title = title
            this.actions = actions
        }
    }
}
