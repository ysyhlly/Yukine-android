package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsMetric
import app.echo.next.ui.SettingsScreenFactory
import java.util.ArrayList

internal class NetworkMenuRenderController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun navigateNetworkPage(page: String)

        fun showAddStream()

        fun showImportM3u()

        fun openM3uFilePicker()

        fun playAllStreams()

        fun confirmDeleteAllStreams()

        fun showAddWebDav()

        fun syncAllWebDavSources()

        fun playAllWebDavTracks()

        fun addVirtualContent(view: View)
    }

    fun renderHome(languageMode: String, remoteSourceCount: Int, streamTrackCount: Int, webDavSourceCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "remote.sources"), remoteSourceCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "streams"), streamTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "webdav.sources"), webDavSourceCount.toString()))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(text(languageMode, "streaming"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_STREAMING)
        }))
        actions.add(SettingsAction(text(languageMode, "webdav"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_WEBDAV)
        }))
        actions.add(SettingsAction(text(languageMode, "remote.sources"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_SOURCES)
        }))
        listener.addVirtualContent(SettingsScreenFactory.create(context, metrics, actions, title = text(languageMode, "tab.network")))
    }

    fun renderStreaming(languageMode: String, streamTrackCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "streams"), streamTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "source"), text(languageMode, "direct.url.m3u")))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(text(languageMode, "back"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_HOME)
        }))
        actions.add(SettingsAction(text(languageMode, "add.stream.url"), Runnable {
            listener.showAddStream()
        }))
        actions.add(SettingsAction(text(languageMode, "import.m3u.url"), Runnable {
            listener.showImportM3u()
        }))
        actions.add(SettingsAction(text(languageMode, "import.m3u.file"), Runnable {
            listener.openM3uFilePicker()
        }))
        actions.add(SettingsAction(text(languageMode, "play.streams"), Runnable {
            listener.playAllStreams()
        }))
        actions.add(SettingsAction(text(languageMode, "browse.streams"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_STREAM_LIST)
        }))
        actions.add(SettingsAction(text(languageMode, "delete.streams"), Runnable {
            listener.confirmDeleteAllStreams()
        }))
        listener.addVirtualContent(SettingsScreenFactory.create(context, metrics, actions, title = text(languageMode, "streaming")))
    }

    fun renderWebDav(languageMode: String, webDavSourceCount: Int, webDavTrackCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "sources"), webDavSourceCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "tracks"), webDavTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "sync.mode"), text(languageMode, "sync.mode.library")))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(text(languageMode, "back"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_HOME)
        }))
        actions.add(SettingsAction(text(languageMode, "add.webdav"), Runnable {
            listener.showAddWebDav()
        }))
        actions.add(SettingsAction(text(languageMode, "sync.all"), Runnable {
            listener.syncAllWebDavSources()
        }))
        actions.add(SettingsAction(text(languageMode, "play.webdav"), Runnable {
            listener.playAllWebDavTracks()
        }))
        actions.add(SettingsAction(text(languageMode, "browse.tracks"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_WEBDAV_TRACKS)
        }))
        actions.add(SettingsAction(text(languageMode, "manage.sources"), Runnable {
            listener.navigateNetworkPage(MainRoutes.NETWORK_SOURCES)
        }))
        listener.addVirtualContent(SettingsScreenFactory.create(context, metrics, actions, title = text(languageMode, "webdav")))
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
