package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal class DefaultStreamingSearchActionHandler(
    private val streamingViewModel: StreamingViewModel,
    private val actionGateway: MainActivityStreamingActionGateway
) : StreamingSearchActionHandler {
    override fun selectProvider(provider: StreamingProviderName) {
        streamingViewModel.auth.selectProvider(provider)
    }

    override fun search(query: String) {
        streamingViewModel.search.searchAllStreaming(
            query = query,
            mediaTypes = setOf(StreamingMediaType.TRACK),
            pageSize = 12
        )
    }

    override fun login(provider: StreamingProviderName) {
        val state = streamingViewModel.state
        val descriptor = state.providers.firstOrNull { it.name == provider }
        val capability = state.providerCapabilities.firstOrNull { it.provider == provider }
        if (descriptor != null && !(capability?.supportsAuth ?: StreamingCapabilityResolver.canAuth(descriptor))) {
            streamingViewModel.failStreamingRequest(
                descriptor.displayName + AppLanguage.text(actionGateway.languageMode(), "streaming.auth.unsupported")
            )
            return
        }
        streamingViewModel.auth.startAuth(
            provider = provider,
            redirectUri = STREAMING_AUTH_REDIRECT_URI + "?provider=${provider.wireName}",
            onLaunchReady = {
                openAuthLaunch()
            }
        )
    }

    override fun signOut(provider: StreamingProviderName) {
        val descriptor = streamingViewModel.state.providers.firstOrNull { it.name == provider }
        if (descriptor != null && !descriptor.capabilities.supportsAuth) {
            return
        }
        streamingViewModel.auth.signOut(provider)
    }

    override fun openAuthLaunch() {
        if (actionGateway.openAuthLaunch(streamingViewModel.state.pendingAuthLaunch)) {
            streamingViewModel.auth.clearAuthLaunch()
        }
    }

    override fun playStreamingTrack(track: StreamingTrack) {
        val descriptor = streamingViewModel.state.providers.firstOrNull { it.name == track.provider }
        val capability = streamingViewModel.state.providerCapabilities.firstOrNull { it.provider == track.provider }
        if (descriptor != null && !(capability?.supportsPlayback ?: StreamingCapabilityResolver.canPlayback(descriptor))) {
            streamingViewModel.failStreamingRequest(sourceMessage(descriptor.displayName, "streaming.playback.unsupported"))
            return
        }
        if (!track.playable) {
            val reason = track.unavailableReason
            streamingViewModel.failStreamingRequest(
                reason?.takeIf { it.isNotBlank() } ?: text("streaming.track.unavailable")
            )
            return
        }
        streamingViewModel.playbackResolution.resolveStreamingPlaybackTrack(
            provider = track.provider,
            providerTrackId = track.providerTrackId,
            quality = actionGateway.streamingPlaybackQuality(),
            metadata = track
        )
    }

    override fun playResolvedTrack(track: Track) {
        actionGateway.playResolvedTrack(track)
    }

    override fun loadNextPage() {
        val provider = streamingViewModel.state.selectedProvider
        val descriptor = streamingViewModel.state.providers.firstOrNull { it.name == provider }
        val capability = streamingViewModel.state.providerCapabilities.firstOrNull { it.provider == provider }
        if (descriptor != null && !(capability?.supportsSearch ?: StreamingCapabilityResolver.canSearch(descriptor))) {
            streamingViewModel.failStreamingRequest(sourceMessage(descriptor.displayName, "streaming.search.unavailable"))
            return
        }
        streamingViewModel.search.searchNextStreamingPage()
    }

    private fun sourceMessage(displayName: String, suffixKey: String): String {
        return displayName + text(suffixKey)
    }

    private fun text(key: String): String {
        return AppLanguage.text(actionGateway.languageMode(), key)
    }
}
