package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.streaming.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val STREAMING_QUEUE_PRE_RESOLVE_LIMIT = 3
// Kept as source-compatible aliases for existing callers/tests; the coordinator owns the limits.
internal const val STREAMING_PLAYLIST_PAGE_SIZE =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_PAGE_SIZE
internal const val STREAMING_PLAYLIST_MAX_PAGES =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_MAX_PAGES

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamingRepositorySource: StreamingRepositorySource
) : ViewModel() {
    constructor() : this(EmptyStreamingRepositorySource)

    private val streamingState = StreamingFeatureStateOwner()
    private var streamingRepository: StreamingRepository = streamingRepositorySource.current()
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal val auth = StreamingAuthStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    internal val search = StreamingSearchStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    internal val playbackResolution = StreamingPlaybackResolutionStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository },
        ioDispatcher = { ioDispatcher }
    )
    internal val playlists = StreamingPlaylistStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository },
        ioDispatcher = { ioDispatcher }
    )
    @JvmName("playbackResolutionOwner")
    internal fun playbackResolutionOwner(): StreamingPlaybackResolutionStateOwner = playbackResolution
    @JvmName("playlistOwner")
    internal fun playlistOwner(): StreamingPlaylistStateOwner = playlists
    @JvmName("searchOwner")
    internal fun searchOwner(): StreamingSearchStateOwner = search
    @JvmName("authOwner")
    internal fun authOwner(): StreamingAuthStateOwner = auth
    val streaming: StateFlow<StreamingSearchState> = streamingState.state
    val state: StreamingSearchState
        get() = streamingState.value

    fun bindStreamingRepository(repository: StreamingRepository) {
        streamingRepository = repository
    }

    /**
     * Test-only seam: lets unit tests inject a deterministic dispatcher so background resolves run
     * on the test scheduler instead of the real IO thread pool. Production keeps [Dispatchers.IO].
     */
    internal fun bindIoDispatcherForTest(dispatcher: CoroutineDispatcher) {
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

}
