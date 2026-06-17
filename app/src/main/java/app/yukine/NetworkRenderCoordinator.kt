package app.yukine

import app.yukine.model.Track
import java.util.ArrayList

internal class NetworkRenderCoordinator(
    private val libraryStore: MainLibraryStore,
    private val menuRenderer: NetworkMenuRenderController,
    private val trackListRenderer: NetworkTrackListRenderController,
    private val sourcesRenderer: NetworkSourcesRenderController,
    private val streamingRenderer: StreamingSearchRenderController
) {
    fun render(
        languageMode: String,
        networkPage: String,
        selectedRemoteSourceId: Long,
        searchQuery: String
    ) {
        when (networkPage) {
            MainRoutes.NETWORK_STREAMING -> renderStreamingNetwork()
            MainRoutes.NETWORK_STREAMING_HUB -> renderStreamingNetwork()
            MainRoutes.NETWORK_STREAM_LIST -> renderStreamList(languageMode, searchQuery)
            MainRoutes.NETWORK_WEBDAV -> renderWebDavNetwork(languageMode)
            MainRoutes.NETWORK_WEBDAV_TRACKS -> renderWebDavTrackList(languageMode, searchQuery)
            MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS -> {
                renderWebDavSourceTrackList(languageMode, selectedRemoteSourceId, searchQuery)
            }
            MainRoutes.NETWORK_SOURCES -> {
                sourcesRenderer.render(languageMode, libraryStore.remoteSources(), libraryStore.allTracks())
            }
            else -> renderNetworkHome(languageMode)
        }
    }

    private fun renderNetworkHome(languageMode: String) {
        menuRenderer.renderHome(
            languageMode,
            libraryStore.remoteSources().size,
            libraryStore.streamTrackCount(),
            libraryStore.webDavSourceCount()
        )
    }

    private fun renderStreamingNetwork() {
        streamingRenderer.render()
    }

    private fun renderStreamList(languageMode: String, searchQuery: String) {
        if (libraryStore.hasRecommendationStreamList()) {
            val allRecommendations = libraryStore.recommendationStreamTracks()
            val recommendations = libraryStore.filteredTracks(allRecommendations, searchQuery)
            trackListRenderer.renderRecommendationStreamList(
                languageMode,
                libraryStore.recommendationStreamTitle(),
                allRecommendations,
                recommendations,
                ArrayList()
            )
            return
        }
        val allStreams = libraryStore.streamTracks()
        val streams = libraryStore.filteredTracks(allStreams, searchQuery)
        trackListRenderer.renderStreamList(
            languageMode,
            allStreams,
            streams,
            libraryStore.streamTrackDetails(streams)
        )
    }

    private fun renderWebDavNetwork(languageMode: String) {
        menuRenderer.renderWebDav(languageMode, libraryStore.webDavSourceCount(), libraryStore.webDavTracks().size)
    }

    private fun renderWebDavTrackList(languageMode: String, searchQuery: String) {
        val allWebDavTracks = libraryStore.webDavTracks()
        val tracks = libraryStore.filteredTracks(allWebDavTracks, searchQuery)
        trackListRenderer.renderWebDavTrackList(
            languageMode,
            allWebDavTracks,
            tracks,
            libraryStore.webDavTrackDetails(tracks, languageMode)
        )
    }

    private fun renderWebDavSourceTrackList(
        languageMode: String,
        selectedRemoteSourceId: Long,
        searchQuery: String
    ) {
        val source = libraryStore.selectedRemoteSource(selectedRemoteSourceId)
        if (source == null) {
            trackListRenderer.renderWebDavSourceTrackList(
                languageMode,
                null,
                ArrayList<Track>(),
                ArrayList<Track>(),
                ArrayList<String>()
            )
            return
        }
        val allSourceTracks = libraryStore.webDavTracksForSource(source.id)
        val tracks = libraryStore.filteredTracks(allSourceTracks, searchQuery)
        trackListRenderer.renderWebDavSourceTrackList(
            languageMode,
            source,
            allSourceTracks,
            tracks,
            libraryStore.webDavTrackDetails(tracks, languageMode)
        )
    }
}
