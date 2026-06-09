package app.echo.next

import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.ui.TrackListHeaderAction
import app.echo.next.ui.TrackListHeaderMetric
import app.echo.next.ui.TrackListLabels
import java.util.ArrayList

internal class NetworkTrackListRenderController(private val listener: Listener) {
    interface Listener {
        fun navigateNetworkPage(page: String)

        fun clearRemoteSourceAndNavigateNetworkPage(page: String)

        fun syncRemoteSource(sourceId: Long)

        fun playRemoteSourceTracks(source: RemoteSource)

        fun playTrackList(tracks: List<Track>, index: Int)

        fun renderTrackList(
            title: String,
            tracks: List<Track>,
            showPlaylistAction: Boolean,
            details: List<String>,
            showStreamActions: Boolean,
            headerMetrics: List<TrackListHeaderMetric>,
            headerActions: List<TrackListHeaderAction>,
            emptyText: String,
            labels: TrackListLabels
        )
    }

    fun renderStreamList(
        languageMode: String,
        allStreams: ArrayList<Track>,
        streams: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back")) {
                listener.navigateNetworkPage(MainRoutes.NETWORK_STREAMING)
            }
        )
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "streams"),
                "${streams.size} / ${allStreams.size}"
            )
        )
        listener.renderTrackList(
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
    }

    fun renderRecommendationStreamList(
        languageMode: String,
        title: String,
        allRecommendations: ArrayList<Track>,
        recommendations: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back")) {
                listener.navigateNetworkPage(MainRoutes.NETWORK_STREAMING)
            }
        )
        if (recommendations.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.playlist")) {
                    listener.playTrackList(recommendations, 0)
                }
            )
        }
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "tracks"),
                "${recommendations.size} / ${allRecommendations.size}"
            )
        )
        listener.renderTrackList(
            title,
            recommendations,
            false,
            details,
            false,
            headerMetrics,
            headerActions,
            if (allRecommendations.isEmpty()) {
                AppLanguage.text(languageMode, "streaming.recommend.daily.empty")
            } else {
                AppLanguage.text(languageMode, "no.matching.streams")
            },
            trackListLabels(languageMode)
        )
    }

    fun renderWebDavTrackList(
        languageMode: String,
        allWebDavTracks: ArrayList<Track>,
        tracks: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.webdav")) {
                listener.navigateNetworkPage(MainRoutes.NETWORK_WEBDAV)
            }
        )
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(
            TrackListHeaderMetric(
                AppLanguage.text(languageMode, "tracks"),
                "${tracks.size} / ${allWebDavTracks.size}"
            )
        )
        listener.renderTrackList(
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
    }

    fun renderWebDavSourceTrackList(
        languageMode: String,
        source: RemoteSource?,
        allSourceTracks: ArrayList<Track>,
        tracks: ArrayList<Track>,
        details: ArrayList<String>
    ) {
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.sources")) {
                listener.clearRemoteSourceAndNavigateNetworkPage(MainRoutes.NETWORK_SOURCES)
            }
        )
        if (source == null) {
            listener.renderTrackList(
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
            TrackListHeaderAction(AppLanguage.text(languageMode, "sync.source")) {
                listener.syncRemoteSource(source.id)
            }
        )
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "play.source")) {
                listener.playRemoteSourceTracks(source)
            }
        )
        listener.renderTrackList(
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
    }

    private fun trackListLabels(languageMode: String): TrackListLabels = TrackListLabels(
        AppLanguage.text(languageMode, "favorite"),
        AppLanguage.text(languageMode, "remove.favorite"),
        AppLanguage.text(languageMode, "add.to.playlist"),
        AppLanguage.text(languageMode, "edit"),
        AppLanguage.text(languageMode, "delete")
    )
}
