package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.streaming.StreamingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal const val STREAMING_QUEUE_PRE_RESOLVE_LIMIT = 3
// Kept as source-compatible aliases for existing callers/tests; the coordinator owns the limits.
const val STREAMING_PLAYLIST_PAGE_SIZE =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_PAGE_SIZE
const val STREAMING_PLAYLIST_MAX_PAGES =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_MAX_PAGES

class StreamingViewModel @JvmOverloads constructor(
    private var streamingRepositorySource: StreamingRepositorySource = EmptyStreamingRepositorySource
) : ViewModel() {
    private val streamingState = StreamingFeatureStateOwner()
    private var streamingRepository: StreamingRepository = streamingRepositorySource.current()
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    val auth = StreamingAuthStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    val search = StreamingSearchStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    val playbackResolution = StreamingPlaybackResolutionStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository },
        ioDispatcher = { ioDispatcher }
    )
    val playlists = StreamingPlaylistStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository },
        ioDispatcher = { ioDispatcher }
    )
    @JvmName("playbackResolutionOwner")
    fun playbackResolutionOwner(): StreamingPlaybackResolutionStateOwner = playbackResolution
    @JvmName("playlistOwner")
    fun playlistOwner(): StreamingPlaylistStateOwner = playlists
    @JvmName("searchOwner")
    fun searchOwner(): StreamingSearchStateOwner = search
    @JvmName("authOwner")
    fun authOwner(): StreamingAuthStateOwner = auth
    val streaming: StateFlow<StreamingSearchState> = streamingState.state
    val state: StreamingSearchState
        get() = streamingState.value

    fun bindRepositorySource(source: StreamingRepositorySource?) {
        streamingRepositorySource = source ?: EmptyStreamingRepositorySource
        streamingRepository = streamingRepositorySource.current()
    }

    fun bindStreamingRepository(repository: StreamingRepository) {
        streamingRepository = repository
    }

    /**
     * Test-only seam: lets unit tests inject a deterministic dispatcher so background resolves run
     * on the test scheduler instead of the real IO thread pool. Production keeps [Dispatchers.IO].
     */
    fun bindIoDispatcherForTest(dispatcher: CoroutineDispatcher) {
        ioDispatcher = dispatcher
    }

    fun configureStreamingRepository(): Job {
        streamingRepository = streamingRepositorySource.current()
        return clearExpiredStreamingCache()
    }

    fun clearExpiredStreamingCache(): Job {
        return viewModelScope.launch {
            runCatching {
                streamingRepository.clearExpiredCache()
            }.onFailure { error ->
                search.failRequest(error.message)
            }
        }
    }

    fun setPlaybackProviderEnabled(provider: app.yukine.streaming.StreamingProviderName, enabled: Boolean) {
        streamingRepository.setPlaybackProviderEnabled(provider, enabled)
        auth.refreshProviders()
    }

    fun setPlaybackProviderPriority(providers: List<app.yukine.streaming.StreamingProviderName>) {
        streamingRepository.setPlaybackProviderPriority(providers)
        auth.refreshProviders()
    }

}
