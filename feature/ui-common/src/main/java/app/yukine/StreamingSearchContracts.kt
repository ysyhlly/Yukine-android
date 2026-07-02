package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistImportSummary
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchResult
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels

data class StreamingSearchState(
    val providers: List<StreamingProviderDescriptor> = emptyList(),
    val providerCapabilities: List<StreamingProviderCapability> = emptyList(),
    val providerHealth: List<StreamingProviderHealth> = emptyList(),
    val diagnostics: StreamingGatewayDiagnostics = StreamingGatewayDiagnostics(),
    val selectedProvider: StreamingProviderName = StreamingProviderName.MOCK,
    val searchQuery: String = "",
    val searchMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val searchResult: StreamingSearchResult? = null,
    val resolvedPlaybackSource: StreamingPlaybackSource? = null,
    val resolvedPlaybackTrack: Track? = null,
    val authStates: Map<StreamingProviderName, StreamingAuthState> = emptyMap(),
    val pendingAuthLaunch: StreamingSearchAuthLaunch? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
    val playlistImportSummary: StreamingPlaylistImportSummary? = null,
    val playlistImporting: Boolean = false,
    val userPlaylists: List<StreamingPlaylist> = emptyList(),
    val userPlaylistsLoading: Boolean = false,
    val searchChromeLabels: StreamingSearchLabels = StreamingSearchLabels.empty(),
    val searchChromeActions: StreamingSearchActions = StreamingSearchActions.empty()
)

data class StreamingSearchAuthLaunch(
    val provider: StreamingProviderName,
    val launchUrl: String,
    val kind: StreamingAuthKind
)
