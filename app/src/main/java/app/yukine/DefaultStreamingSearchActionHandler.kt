package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAudioCapabilityPolicy
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal class DefaultStreamingSearchActionHandler(
    private val streamingViewModel: StreamingViewModel,
    private val actionGateway: MainActivityStreamingActionGateway,
    private val onPlaybackPolicyChanged: () -> Unit = {}
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
            streamingViewModel.search.failRequest(
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
        // Source-provider playback may be disabled while metadata/search stays enabled. Always
        // hand online tracks to the central policy, which tries LX first and only then enabled
        // providers. This also keeps QQ metadata playable without ever resolving a QQ URL.
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
            streamingViewModel.search.failRequest(sourceMessage(descriptor.displayName, "streaming.search.unavailable"))
            return
        }
        streamingViewModel.search.searchNextStreamingPage()
    }

    override fun setPlaybackProviderEnabled(provider: StreamingProviderName, enabled: Boolean) {
        if (provider == StreamingProviderName.QQ_MUSIC) return
        streamingViewModel.setPlaybackProviderEnabled(provider, enabled)
        onPlaybackPolicyChanged()
    }

    override fun movePlaybackProvider(provider: StreamingProviderName, direction: Int) {
        if (provider == StreamingProviderName.QQ_MUSIC || provider == StreamingProviderName.LUOXUE) return
        val order = streamingViewModel.state.playbackSourcePolicy.remotePriority.toMutableList()
        val from = order.indexOf(provider)
        if (from < 0) return
        val to = (from + direction).coerceIn(1, order.lastIndex)
        if (from == to) return
        order.removeAt(from)
        order.add(to, provider)
        streamingViewModel.setPlaybackProviderPriority(order)
    }

    private fun sourceMessage(displayName: String, suffixKey: String): String {
        return displayName + text(suffixKey)
    }

    private fun text(key: String): String {
        return AppLanguage.text(actionGateway.languageMode(), key)
    }
}
