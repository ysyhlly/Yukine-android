package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingPlaybackSource
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingProviderHealth
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import app.echo.next.ui.StreamingHubActions
import app.echo.next.ui.StreamingHubScreenFactory
import app.echo.next.ui.StreamingHubUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller for the Streaming Hub screen - manages provider selection,
 * authentication, search, and playlist import.
 */
class StreamingHubController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onProviderSelected(provider: StreamingProviderName)
        fun onLoginRequested(provider: StreamingProviderName)
        fun onLogoutRequested(provider: StreamingProviderName)
        fun onSearchRequested(provider: StreamingProviderName, query: String)
        fun onPlayStreamingTrack(track: StreamingTrack)
        fun onImportPlaylist(playlist: StreamingPlaylist)
        fun onOpenPlaylistDetail(playlist: StreamingPlaylist)
        fun onBack()
        fun addVirtualContent(view: View)
    }

    private val _state = MutableStateFlow(StreamingHubUiState())
    val state: StateFlow<StreamingHubUiState> = _state.asStateFlow()

    fun updateProviders(providers: List<StreamingProviderDescriptor>) {
        _state.value = _state.value.copy(providers = providers)
    }

    fun updateProviderHealth(health: List<StreamingProviderHealth>) {
        val healthMap = health.associateBy { it.provider }
        _state.value = _state.value.copy(providerHealth = healthMap)
    }

    fun updateAuthState(provider: StreamingProviderName, authState: StreamingAuthState) {
        val updated = _state.value.authStates.toMutableMap()
        updated[provider] = authState
        _state.value = _state.value.copy(authStates = updated)
    }

    fun updateAuthStates(authStates: Map<StreamingProviderName, StreamingAuthState>) {
        _state.value = _state.value.copy(authStates = authStates)
    }

    fun selectProvider(provider: StreamingProviderName) {
        _state.value = _state.value.copy(
            selectedProvider = provider,
            searchQuery = "",
            searchResults = emptyList(),
            playlistResults = emptyList(),
            errorMessage = null
        )
    }

    fun setLoading(loading: Boolean) {
        _state.value = _state.value.copy(isLoading = loading)
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(errorMessage = message, isLoading = false)
    }

    fun updateSearchResults(tracks: List<StreamingTrack>, playlists: List<StreamingPlaylist>, query: String) {
        _state.value = _state.value.copy(
            searchQuery = query,
            searchResults = tracks,
            playlistResults = playlists,
            isLoading = false,
            errorMessage = null
        )
    }

    fun clearResults() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            playlistResults = emptyList(),
            errorMessage = null
        )
    }

    fun render() {
        val actions = StreamingHubActions(
            onSelectProvider = { provider ->
                selectProvider(provider)
                listener.onProviderSelected(provider)
            },
            onLogin = { provider ->
                listener.onLoginRequested(provider)
            },
            onLogout = { provider ->
                listener.onLogoutRequested(provider)
            },
            onSearch = { query ->
                val provider = _state.value.selectedProvider
                if (provider != null && query.isNotBlank()) {
                    setLoading(true)
                    listener.onSearchRequested(provider, query)
                }
            },
            onPlayTrack = { track ->
                listener.onPlayStreamingTrack(track)
            },
            onImportPlaylist = { playlist ->
                listener.onImportPlaylist(playlist)
            },
            onOpenPlaylist = { playlist ->
                listener.onOpenPlaylistDetail(playlist)
            },
            onBack = {
                listener.onBack()
            }
        )

        listener.addVirtualContent(
            StreamingHubScreenFactory.create(context, state, actions)
        )
    }
}
