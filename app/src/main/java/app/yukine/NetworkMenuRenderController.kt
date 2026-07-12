package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsActionStyle
import app.yukine.ui.SettingsMetric
import app.yukine.ui.EchoIconKind
import java.util.ArrayList

internal class NetworkMenuRenderController(
    private val listener: Listener
) {
    interface Listener {
        fun navigateNetworkPage(page: String)

        fun backFromNetworkPage()

        fun showAddStream()

        fun showImportM3u()

        fun openM3uFilePicker()

        fun playAllStreams()

        fun confirmDeleteAllStreams()

        fun showAddWebDav()

        fun syncAllWebDavSources()

        fun playAllWebDavTracks()

        fun publishNetworkMenu(title: String, metrics: List<SettingsMetric>, actions: List<SettingsAction>)
    }

    fun renderHome(languageMode: String, remoteSourceCount: Int, streamTrackCount: Int, webDavSourceCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "remote.sources"), remoteSourceCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "streams"), streamTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "webdav.sources"), webDavSourceCount.toString()))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(
            label = text(languageMode, "streaming"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_STREAMING) },
            icon = EchoIconKind.Network
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "webdav"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_WEBDAV) },
            icon = EchoIconKind.Folder
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "remote.sources"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_SOURCES) },
            icon = EchoIconKind.Network
        ))
        val title = text(languageMode, "settings.group.sources")
        listener.publishNetworkMenu(title, metrics, actions)
    }

    fun renderStreaming(languageMode: String, streamTrackCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "streams"), streamTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "source"), text(languageMode, "direct.url.m3u")))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(
            label = text(languageMode, "back"),
            onClick = Runnable { listener.backFromNetworkPage() },
            icon = EchoIconKind.Back,
            isBack = true
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "add.stream.url"),
            onClick = Runnable { listener.showAddStream() },
            icon = EchoIconKind.Action
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "import.m3u.url"),
            onClick = Runnable { listener.showImportM3u() },
            icon = EchoIconKind.Import
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "import.m3u.file"),
            onClick = Runnable { listener.openM3uFilePicker() },
            icon = EchoIconKind.Folder
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "play.streams"),
            onClick = Runnable { listener.playAllStreams() },
            icon = EchoIconKind.Play
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "browse.streams"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_STREAM_LIST) },
            icon = EchoIconKind.Collections
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "delete.streams"),
            onClick = Runnable { listener.confirmDeleteAllStreams() },
            icon = EchoIconKind.Delete,
            style = SettingsActionStyle.Destructive
        ))
        val title = text(languageMode, "streaming")
        listener.publishNetworkMenu(title, metrics, actions)
    }

    fun renderWebDav(languageMode: String, webDavSourceCount: Int, webDavTrackCount: Int) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "sources"), webDavSourceCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "tracks"), webDavTrackCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "sync.mode"), text(languageMode, "sync.mode.library")))

        val actions = ArrayList<SettingsAction>()
        actions.add(SettingsAction(
            label = text(languageMode, "back"),
            onClick = Runnable { listener.backFromNetworkPage() },
            icon = EchoIconKind.Back,
            isBack = true
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "add.webdav"),
            onClick = Runnable { listener.showAddWebDav() },
            icon = EchoIconKind.Action
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "sync.all"),
            onClick = Runnable { listener.syncAllWebDavSources() },
            icon = EchoIconKind.Sync
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "play.webdav"),
            onClick = Runnable { listener.playAllWebDavTracks() },
            icon = EchoIconKind.Play
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "browse.tracks"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_WEBDAV_TRACKS) },
            icon = EchoIconKind.Collections
        ))
        actions.add(SettingsAction(
            label = text(languageMode, "manage.sources"),
            onClick = Runnable { listener.navigateNetworkPage(MainRoutes.NETWORK_SOURCES) },
            icon = EchoIconKind.Edit
        ))
        val title = text(languageMode, "webdav")
        listener.publishNetworkMenu(title, metrics, actions)
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
