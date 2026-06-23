package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingPlaybackQualityProvider {
    fun quality(): StreamingAudioQuality
}

internal fun interface StreamingLanguageModeProvider {
    fun languageMode(): String
}

internal fun interface StreamingAuthLaunchAction {
    fun launch(launch: MainActivityStreamingAuthLaunch?): Boolean
}

internal fun interface StreamingTrackListPlayAction {
    fun play(tracks: List<Track>, index: Int)
}

internal fun interface StreamingLoginSuccessAction {
    fun onSuccess(provider: StreamingProviderName)
}

internal fun interface StreamingManualCookieImportAction {
    fun open(provider: StreamingProviderName)
}

internal class StreamingActionGatewayBindings(
    private val qualityProvider: StreamingPlaybackQualityProvider,
    private val languageModeProvider: StreamingLanguageModeProvider,
    private val authLaunchAction: StreamingAuthLaunchAction,
    private val trackListPlayAction: StreamingTrackListPlayAction,
    private val loginSuccessAction: StreamingLoginSuccessAction,
    private val manualCookieImportAction: StreamingManualCookieImportAction
) : MainActivityStreamingActionGateway {
    override fun streamingPlaybackQuality(): StreamingAudioQuality {
        return qualityProvider.quality()
    }

    override fun languageMode(): String {
        return languageModeProvider.languageMode()
    }

    override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean {
        return authLaunchAction.launch(launch)
    }

    override fun playResolvedTrack(track: Track) {
        trackListPlayAction.play(listOf(track), 0)
    }

    override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        loginSuccessAction.onSuccess(provider)
    }

    override fun openManualCookieImport(provider: StreamingProviderName) {
        manualCookieImportAction.open(provider)
    }
}
