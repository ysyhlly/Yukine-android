package app.yukine

import app.yukine.streaming.StreamingProviderName

internal class StreamingManualCookieController(
    private val streamingViewModel: StreamingViewModel,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    fun interface LanguageProvider {
        fun languageMode(): String
    }

    interface Listener {
        fun selectedProvider(): StreamingProviderName?

        fun showManualCookieDialog(dialogState: StreamingManualCookieDialogState)

        fun onStreamingLoginSuccess(provider: StreamingProviderName)

        fun setStatus(status: String)
    }

    fun showStreamingCookieDialog() {
        val dialogState = streamingViewModel.auth.prepareManualCookieDialogState(
            listener.selectedProvider(),
            languageProvider.languageMode()
        )
        if (dialogState.unavailable || dialogState.provider == null) {
            listener.setStatus(dialogState.unavailableStatus)
            return
        }
        listener.showManualCookieDialog(dialogState)
    }

    fun saveStreamingCookie(provider: StreamingProviderName, cookieHeader: String?) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.auth.prepareManualCookieAuthRequest(provider, cookieHeader, languageMode)
        if (request == null) {
            listener.setStatus(streamingViewModel.auth.manualCookieEmptyStatus(languageMode))
            return
        }
        streamingViewModel.auth.completeAuth(request.provider, request.callbackUri, request.cookieHeader) { loggedInProvider ->
            listener.setStatus(request.savedStatus)
            listener.onStreamingLoginSuccess(loggedInProvider)
        }
    }
}
