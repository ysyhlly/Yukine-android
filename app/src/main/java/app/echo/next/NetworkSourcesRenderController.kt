package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.ui.NetworkSourceActions
import app.echo.next.ui.NetworkSourceLabels
import app.echo.next.ui.NetworkSourceUiState
import app.echo.next.ui.NetworkSourcesScreenFactory
import app.echo.next.ui.TrackListHeaderAction
import java.util.ArrayList

internal class NetworkSourcesRenderController(
    private val context: Context,
    private val viewModel: NetworkSourcesViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun backToNetwork()

        fun testRemoteSource(sourceId: Long)

        fun syncRemoteSource(sourceId: Long)

        fun playRemoteSourceTracks(source: RemoteSource)

        fun openRemoteSourceTracks(sourceId: Long)

        fun showEditWebDav(source: RemoteSource)

        fun confirmDeleteRemoteSource(source: RemoteSource)

        fun publishNetworkSources(title: String, rows: ArrayList<NetworkSourceUiState>)

        fun addVirtualContent(view: View)
    }

    fun render(languageMode: String, remoteSources: List<RemoteSource>, allTracks: List<Track>) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(
                AppLanguage.text(languageMode, "back.to.network"),
                Runnable { listener.backToNetwork() }
            )
        )

        val rows = ArrayList<NetworkSourceUiState>()
        val actions = ArrayList<NetworkSourceActions>()
        for (source in remoteSources) {
            rows.add(CollectionRowStateFactory.networkSourceRow(source, allTracks, languageMode))
            actions.add(
                NetworkSourceActions(
                    Runnable { listener.testRemoteSource(source.id) },
                    Runnable { listener.syncRemoteSource(source.id) },
                    Runnable { listener.playRemoteSourceTracks(source) },
                    Runnable { listener.openRemoteSourceTracks(source.id) },
                    Runnable { listener.showEditWebDav(source) },
                    Runnable { listener.confirmDeleteRemoteSource(source) }
                )
            )
        }

        val title = AppLanguage.text(languageMode, "remote.music.sources")
        viewModel.updateSources(title, remoteSources, rows)
        listener.addVirtualContent(
            NetworkSourcesScreenFactory.create(
                context,
                viewModel.screen,
                actions,
                headerActions,
                AppLanguage.text(languageMode, "no.remote.sources"),
                NetworkSourceLabels(
                    AppLanguage.text(languageMode, "test"),
                    AppLanguage.text(languageMode, "sync"),
                    AppLanguage.text(languageMode, "play"),
                    AppLanguage.text(languageMode, "tracks"),
                    AppLanguage.text(languageMode, "edit"),
                    AppLanguage.text(languageMode, "delete")
                )
            )
        )
    }
}
