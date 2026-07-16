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

    fun setPlaybackProviderEnabled(provider: StreamingProviderName, enabled: Boolean) = Unit

    fun movePlaybackProvider(provider: StreamingProviderName, direction: Int) = Unit
}

interface MainActivityStreamingActionGateway {
    fun streamingPlaybackQuality(): StreamingAudioQuality

    fun languageMode(): String

    fun openAuthLaunch(launch: StreamingSearchAuthLaunch?): Boolean

    fun playResolvedTrack(track: Track)

    fun onStreamingLoginSuccess(provider: StreamingProviderName)

    fun openManualCookieImport(provider: StreamingProviderName)
}
