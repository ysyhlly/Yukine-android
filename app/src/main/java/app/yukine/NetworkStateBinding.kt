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

internal class NetworkStateBinding @JvmOverloads constructor(
    private val libraryStore: LibraryDataStateOwner,
    private val menuReducer: NetworkMenuStateReducer,
    private val trackListReducer: NetworkTrackListStateReducer,
    private val sourcesReducer: NetworkSourcesStateReducer,
    private val streamingReducer: StreamingSearchStateReducer,
    private val scope: CoroutineScope = MainScope()
) {
    private var bindingJob: Job? = null

    fun bindStateSources(
        routeState: StateFlow<NavigationRouteState>?,
        libraryState: StateFlow<LibraryStoreState>?,
        settingsState: StateFlow<SettingsState>?
    ) {
        bindingJob?.cancel()
        bindingJob = null
        if (routeState == null || libraryState == null || settingsState == null) {
            return
        }
        bindingJob = scope.launch {
            combine(
                routeState.map(::networkBindingRoute).distinctUntilChanged(),
                libraryState,
                settingsState.map { it.preferences.languageMode }.distinctUntilChanged()
            ) { route, _, languageMode ->
                route.copy(languageMode = languageMode)
            }.collect { inputs ->
                if (inputs.active) {
                    publish(
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
        bindingJob?.cancel()
        bindingJob = null
        scope.cancel()
    }

    fun publish(
        languageMode: String,
        networkPage: NetworkPage,
        selectedRemoteSourceId: Long,
        searchQuery: String
    ) {
        when (networkPage) {
            NetworkPage.Streaming -> publishStreamingNetwork()
            NetworkPage.StreamingHub -> publishStreamingNetwork()
            NetworkPage.StreamList -> publishStreamList(languageMode, searchQuery)
            NetworkPage.WebDav -> publishWebDavNetwork(languageMode)
            NetworkPage.WebDavTracks -> publishWebDavTrackList(languageMode, searchQuery)
            NetworkPage.WebDavSourceTracks -> {
                publishWebDavSourceTrackList(languageMode, selectedRemoteSourceId, searchQuery)
            }
            NetworkPage.Sources -> {
                sourcesReducer.reduce(languageMode, libraryStore.remoteSources(), libraryStore.allTracks())
            }
            NetworkPage.Home -> publishNetworkHome(languageMode)
        }
    }

    private fun publishNetworkHome(languageMode: String) {
        menuReducer.reduceHome(
            languageMode,
            libraryStore.remoteSources().size,
            libraryStore.streamTrackCount(),
            libraryStore.webDavSourceCount()
        )
    }

    private fun publishStreamingNetwork() {
        streamingReducer.reduce()
    }

    private fun publishStreamList(languageMode: String, searchQuery: String) {
        val allStreams = libraryStore.streamTracks()
        val streams = libraryStore.filteredTracks(allStreams, searchQuery)
        trackListReducer.reduceStreamList(
            languageMode,
            allStreams,
            streams,
            libraryStore.streamTrackDetails(streams)
        )
    }

    private fun publishWebDavNetwork(languageMode: String) {
        menuReducer.reduceWebDav(languageMode, libraryStore.webDavSourceCount(), libraryStore.webDavTracks().size)
    }

    private fun publishWebDavTrackList(languageMode: String, searchQuery: String) {
        val allWebDavTracks = libraryStore.webDavTracks()
        val tracks = libraryStore.filteredTracks(allWebDavTracks, searchQuery)
        trackListReducer.reduceWebDavTrackList(
            languageMode,
            allWebDavTracks,
            tracks,
            libraryStore.webDavTrackDetails(tracks, languageMode)
        )
    }

    private fun publishWebDavSourceTrackList(
        languageMode: String,
        selectedRemoteSourceId: Long,
        searchQuery: String
    ) {
        val source = libraryStore.selectedRemoteSource(selectedRemoteSourceId)
        if (source == null) {
            trackListReducer.reduceWebDavSourceTrackList(
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
        trackListReducer.reduceWebDavSourceTrackList(
            languageMode,
            source,
            allSourceTracks,
            tracks,
            libraryStore.webDavTrackDetails(tracks, languageMode)
        )
    }
}

private data class NetworkBindingInputs(
    val active: Boolean,
    val networkPage: NetworkPage,
    val selectedRemoteSourceId: Long,
    val searchQuery: String,
    val languageMode: String = AppLanguage.MODE_SYSTEM
)

private fun networkBindingRoute(state: NavigationRouteState): NetworkBindingInputs =
    NetworkBindingInputs(
        active = state.selectedTab == NetworkTab,
        networkPage = state.networkPage,
        selectedRemoteSourceId = state.selectedRemoteSourceId,
        searchQuery = state.searchQuery
    )
