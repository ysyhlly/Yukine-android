package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.EchoIconKind
import java.util.ArrayList

data class NetworkTrackListRequest(
    val title: String,
    val tracks: List<Track>,
    val showPlaylistAction: Boolean,
    val details: List<String>,
    val showStreamActions: Boolean,
    val headerMetrics: List<TrackListHeaderMetric>,
    val headerActions: List<TrackListHeaderAction>,
    val emptyText: String,
    val labels: TrackListLabels
)

class NetworkTrackListStateReducer(private val listener: Listener) {
    interface Listener {
        fun navigateNetworkPage(page: NetworkPage)

        fun clearRemoteSourceAndNavigateNetworkPage(page: NetworkPage)

        fun syncRemoteSource(sourceId: Long)

        fun playRemoteSourceTracks(source: RemoteSource)

        fun publish(request: NetworkTrackListRequest)
    }

    fun reduceStreamList(
        languageMode: String,
        allStreams: ArrayList<Track>,
        streams: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back"), Runnable {
                listener.navigateNetworkPage(NetworkPage.Streaming)
            }, icon = EchoIconKind.Back, isBack = true)
        )
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "streams"),
                "${streams.size} / ${allStreams.size}"
            )
        )
        listener.publish(
            NetworkTrackListRequest(
                AppLanguage.text(languageMode, "songs"),
                streams,
                false,
                details,
                true,
                headerMetrics,
                headerActions,
                if (allStreams.isEmpty()) {
                    AppLanguage.text(languageMode, "no.streams")
                } else {
                    AppLanguage.text(languageMode, "no.matching.streams")
                },
                trackListLabels(languageMode)
            )
        )
    }

    fun reduceWebDavTrackList(
        languageMode: String,
        allWebDavTracks: ArrayList<Track>,
        tracks: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.webdav"), Runnable {
                listener.navigateNetworkPage(NetworkPage.WebDav)
            }, icon = EchoIconKind.Back, isBack = true)
        )
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "tracks"),
                "${tracks.size} / ${allWebDavTracks.size}"
            )
        )
        listener.publish(
            NetworkTrackListRequest(
                AppLanguage.text(languageMode, "songs"),
                tracks,
                true,
                details,
                false,
                headerMetrics,
                headerActions,
                if (allWebDavTracks.isEmpty()) {
                    AppLanguage.text(languageMode, "no.webdav.tracks")
                } else {
                    AppLanguage.text(languageMode, "no.matching.webdav.tracks")
                },
                trackListLabels(languageMode)
            )
        )
    }

    fun reduceWebDavSourceTrackList(
        languageMode: String,
        source: RemoteSource?,
        allSourceTracks: ArrayList<Track>,
        tracks: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.sources"), Runnable {
                listener.clearRemoteSourceAndNavigateNetworkPage(NetworkPage.Sources)
            }, icon = EchoIconKind.Back, isBack = true)
        )
        if (source == null) {
            listener.publish(
                NetworkTrackListRequest(
                    AppLanguage.text(languageMode, "songs"),
                    ArrayList(),
                    true,
                    ArrayList(),
                    false,
                    ArrayList(),
                    headerActions,
                    AppLanguage.text(languageMode, "source.not.found"),
                    trackListLabels(languageMode)
                )
            )
            return
        }

        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(TrackListHeaderMetric(AppLanguage.text(languageMode, "source"), source.name))
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "tracks"),
                "${tracks.size} / ${allSourceTracks.size}"
            )
        )
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "sync.source"), Runnable {
                listener.syncRemoteSource(source.id)
            }, icon = EchoIconKind.Sync)
        )
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "play.source"), Runnable {
                listener.playRemoteSourceTracks(source)
            }, icon = EchoIconKind.Play)
        )
        listener.publish(
            NetworkTrackListRequest(
                AppLanguage.text(languageMode, "songs"),
                tracks,
                true,
                details,
                false,
                headerMetrics,
                headerActions,
                if (allSourceTracks.isEmpty()) {
                    AppLanguage.text(languageMode, "no.tracks.from.source")
                } else {
                    AppLanguage.text(languageMode, "no.matching.source.tracks")
                },
                trackListLabels(languageMode)
            )
        )
    }

    private fun trackListLabels(languageMode: String): TrackListLabels = TrackListLabels(
        AppLanguage.text(languageMode, "favorite"),
        AppLanguage.text(languageMode, "remove.favorite"),
        AppLanguage.text(languageMode, "add.to.playlist"),
        AppLanguage.text(languageMode, "edit"),
        AppLanguage.text(languageMode, "delete"),
        AppLanguage.text(languageMode, "download")
    )
}
