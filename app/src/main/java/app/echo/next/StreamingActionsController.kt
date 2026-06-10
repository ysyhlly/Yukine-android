package app.echo.next

import android.content.Context
import android.content.Intent
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingCapabilityResolver
import app.echo.next.streaming.StreamingMediaType
import app.echo.next.streaming.StreamingProviderCapability
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack

internal interface StreamingSearchActionHandler {
    fun selectProvider(provider: StreamingProviderName)

    fun search(query: String)

    fun login(provider: StreamingProviderName)

    fun signOut(provider: StreamingProviderName)

    fun openAuthLaunch()

    fun playStreamingTrack(track: StreamingTrack)

    fun playResolvedTrack(track: Track)

    fun loadNextPage()
}

internal interface StreamingAuthCallbackHandler {
    fun handleAuthCallback(intent: Intent?): Boolean
}

internal class StreamingActionsController(
    private val context: Context,
    private val viewModel: MainActivityViewModel,
    private val redirectUriBase: String,
    private val qualitySource: QualitySource,
    private val languageSource: LanguageSource,
    private val listener: Listener
) : StreamingSearchActionHandler, StreamingAuthCallbackHandler {
    fun interface QualitySource {
        fun streamingAudioQuality(): String
    }

    fun interface LanguageSource {
        fun languageMode(): String
    }

    interface Listener {
        fun playResolvedTrack(track: Track)

        fun onStreamingLoginSuccess(provider: StreamingProviderName)
    }

    override fun selectProvider(provider: StreamingProviderName) {
        viewModel.selectStreamingProvider(provider)
    }

    override fun search(query: String) {
        val provider = viewModel.streaming.value.selectedProvider
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsSearch ?: StreamingCapabilityResolver.canSearch(descriptor))) {
            viewModel.failStreamingRequest(sourceMessage(descriptor, "streaming.search.unavailable"))
            return
        }
        val mediaTypes = capability?.supportedSearchMediaTypes ?: StreamingCapabilityResolver.supportedSearchMediaTypes(descriptor)
        if (descriptor != null && mediaTypes.isEmpty()) {
            viewModel.failStreamingRequest(sourceMessage(descriptor, "streaming.search.types.unavailable"))
            return
        }
        viewModel.searchStreaming(
            provider = provider,
            query = query,
            mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
            page = 1,
            pageSize = 20
        )
    }

    override fun login(provider: StreamingProviderName) {
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsAuth ?: StreamingCapabilityResolver.canAuth(descriptor))) {
            viewModel.failStreamingRequest(sourceMessage(descriptor, "streaming.auth.unsupported"))
            return
        }
        viewModel.startStreamingAuth(
            provider = provider,
            redirectUri = "$redirectUriBase?provider=${provider.wireName}",
            onLaunchReady = {
                openAuthLaunch()
            }
        )
    }

    override fun signOut(provider: StreamingProviderName) {
        val descriptor = descriptorFor(provider)
        if (descriptor != null && !descriptor.capabilities.supportsAuth) {
            return
        }
        viewModel.signOutStreaming(provider)
    }

    override fun openAuthLaunch() {
        val launch = viewModel.streaming.value.pendingAuthLaunch
        if (StreamingAuthLauncher.launch(context, launch)) {
            viewModel.clearStreamingAuthLaunch()
        }
    }

    override fun playStreamingTrack(track: StreamingTrack) {
        val descriptor = descriptorFor(track.provider)
        val capability = capabilityFor(track.provider)
        if (descriptor != null && !(capability?.supportsPlayback ?: StreamingCapabilityResolver.canPlayback(descriptor))) {
            viewModel.failStreamingRequest(sourceMessage(descriptor, "streaming.playback.unsupported"))
            return
        }
        if (!track.playable) {
            val reason = track.unavailableReason
            viewModel.failStreamingRequest(reason?.takeIf { it.isNotBlank() } ?: text("streaming.track.unavailable"))
            return
        }
        viewModel.resolveStreamingPlaybackTrack(
            provider = track.provider,
            providerTrackId = track.providerTrackId,
            quality = StreamingQualityPreference.playbackQuality(context, qualitySource.streamingAudioQuality()),
            metadata = track
        )
    }

    override fun playResolvedTrack(track: Track) {
        listener.playResolvedTrack(track)
    }

    override fun loadNextPage() {
        val provider = viewModel.streaming.value.selectedProvider
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsSearch ?: StreamingCapabilityResolver.canSearch(descriptor))) {
            viewModel.failStreamingRequest(sourceMessage(descriptor, "streaming.search.unavailable"))
            return
        }
        viewModel.searchNextStreamingPage()
    }

    override fun handleAuthCallback(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "echo-next" || uri.host != "streaming-auth") {
            return false
        }
        val providerValue = uri.getQueryParameter("provider")
        val selectedProvider = viewModel.streaming.value.selectedProvider
        val parsedProvider = providerValue
            ?.takeIf { it.isNotBlank() }
            ?.let(StreamingProviderName::fromWireName)
        val provider = parsedProvider ?: selectedProvider
        viewModel.completeStreamingAuth(
            provider,
            uri.toString(),
            intent.getStringExtra(StreamingWebAuthActivity.EXTRA_COOKIE_HEADER)
        ) { loggedInProvider ->
            listener.onStreamingLoginSuccess(loggedInProvider)
        }
        viewModel.clearStreamingAuthLaunch()
        return true
    }

    private fun descriptorFor(provider: StreamingProviderName): StreamingProviderDescriptor? {
        return viewModel.streaming.value.providers.firstOrNull { it.name == provider }
    }

    private fun capabilityFor(provider: StreamingProviderName): StreamingProviderCapability? {
        return viewModel.streaming.value.providerCapabilities.firstOrNull { it.provider == provider }
    }

    private fun sourceMessage(descriptor: StreamingProviderDescriptor, suffixKey: String): String {
        return descriptor.displayName + text(suffixKey)
    }

    private fun text(key: String): String = AppLanguage.text(languageSource.languageMode(), key)
}
