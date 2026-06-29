package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName

internal fun interface MainStreamingActionGatewayFactory {
    fun create(
        qualityProvider: MainStreamingActionGateway.QualityProvider,
        languageModeProvider: MainStreamingActionGateway.LanguageModeProvider,
        authLauncher: MainStreamingActionGateway.AuthLauncher,
        trackPlayer: MainStreamingActionGateway.TrackPlayer,
        loginSuccessHandler: MainStreamingActionGateway.LoginSuccessHandler,
        providerSelector: MainStreamingActionGateway.ProviderSelector,
        manualCookiePresenter: MainStreamingActionGateway.ManualCookiePresenter
    ): MainActivityStreamingActionGateway
}

internal class MainStreamingActionGateway(
    private val qualityProvider: QualityProvider,
    private val languageModeProvider: LanguageModeProvider,
    private val authLauncher: AuthLauncher,
    private val trackPlayer: TrackPlayer,
    private val loginSuccessHandler: LoginSuccessHandler,
    private val providerSelector: ProviderSelector,
    private val manualCookiePresenter: ManualCookiePresenter
) : MainActivityStreamingActionGateway {
    fun interface QualityProvider {
        fun streamingPlaybackQuality(): StreamingAudioQuality
    }

    fun interface LanguageModeProvider {
        fun languageMode(): String
    }

    fun interface AuthLauncher {
        fun openAuthLaunch(launch: StreamingSearchAuthLaunch?): Boolean
    }

    fun interface TrackPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface LoginSuccessHandler {
        fun onStreamingLoginSuccess(provider: StreamingProviderName)
    }

    fun interface ProviderSelector {
        fun selectStreamingProvider(provider: StreamingProviderName)
    }

    fun interface ManualCookiePresenter {
        fun showStreamingCookieDialog()
    }

    override fun streamingPlaybackQuality(): StreamingAudioQuality {
        return qualityProvider.streamingPlaybackQuality()
    }

    override fun languageMode(): String {
        return languageModeProvider.languageMode()
    }

    override fun openAuthLaunch(launch: StreamingSearchAuthLaunch?): Boolean {
        return authLauncher.openAuthLaunch(launch)
    }

    override fun playResolvedTrack(track: Track) {
        trackPlayer.playTrackList(listOf(track), 0)
    }

    override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        loginSuccessHandler.onStreamingLoginSuccess(provider)
    }

    override fun openManualCookieImport(provider: StreamingProviderName) {
        providerSelector.selectStreamingProvider(provider)
        manualCookiePresenter.showStreamingCookieDialog()
    }
}
