package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

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
    fun handleAuthCallback(callbackUri: String?, cookieHeader: String?): Boolean
}

interface MainActivityStreamingActionGateway {
    fun streamingPlaybackQuality(): StreamingAudioQuality

    fun languageMode(): String

    fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean

    fun playResolvedTrack(track: Track)

    fun onStreamingLoginSuccess(provider: StreamingProviderName)
}
