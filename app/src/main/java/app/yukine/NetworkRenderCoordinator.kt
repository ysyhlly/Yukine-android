package app.yukine

import app.yukine.model.Track
import app.yukine.navigation.NetworkTab
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class NetworkRenderCoordinator @JvmOverloads constructor(
    private val libraryStore: LibraryDataStateOwner,
    private val menuRenderer: NetworkMenuRenderController,
    private val trackListRenderer: NetworkTrackListRenderController,
    private val sourcesRenderer: NetworkSourcesRenderController,
    private val streamingRenderer: StreamingSearchRenderController,
    private val scope: CoroutineScope = MainScope()
) {
    private var renderJob: Job? = null

    fun bindStateSources(
        routeState: StateFlow<NavigationRouteState>?,
        libraryState: StateFlow<LibraryStoreState>?,
        settingsState: StateFlow<SettingsState>?
    ) {
        renderJob?.cancel()
        renderJob = null
        if (routeState == null || libraryState == null || settingsState == null) {
            return
        }
        renderJob = scope.launch {
            combine(
                routeState.map(::networkRenderRoute).distinctUntilChanged(),
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged()
            ) { route, _, languageMode ->
                route.copy(languageMode = languageMode)
            }.collect { inputs ->
                if (inputs.active) {
                    render(
                        inputs.languageMode,
                        inputs.networkPage,
                        inputs.selectedRemoteSourceId,
                        inputs.searchQuery
                    )
                }
            }
        }
    }

    fun release() {
        renderJob?.cancel()
        renderJob = null
        scope.cancel()
    }

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

private data class NetworkRenderInputs(
    val active: Boolean,
    val networkPage: String,
    val selectedRemoteSourceId: Long,
    val searchQuery: String,
    val languageMode: String = AppLanguage.MODE_SYSTEM
)

private fun networkRenderRoute(state: NavigationRouteState): NetworkRenderInputs =
    NetworkRenderInputs(
        active = state.selectedTab == NetworkTab,
        networkPage = state.networkPage,
        selectedRemoteSourceId = state.selectedRemoteSourceId,
        searchQuery = state.searchQuery
    )
