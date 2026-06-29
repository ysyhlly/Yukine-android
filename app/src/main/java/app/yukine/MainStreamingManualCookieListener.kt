package app.yukine

import app.yukine.streaming.StreamingProviderName

internal fun interface ManualCookieSelectedProviderSource {
    fun selectedProvider(): StreamingProviderName?
}

internal fun interface ManualCookieDialogPresenter {
    fun showManualCookieDialog(dialogState: StreamingManualCookieDialogState)
}

internal fun interface ManualCookieLoginSuccessHandler {
    fun onStreamingLoginSuccess(provider: StreamingProviderName)
}

internal fun interface ManualCookieStatusSink {
    fun setStatus(status: String)
}

internal fun interface MainStreamingManualCookieListenerFactory {
    fun create(
        selectedProviderSource: ManualCookieSelectedProviderSource,
        dialogPresenter: ManualCookieDialogPresenter,
        loginSuccessHandler: ManualCookieLoginSuccessHandler,
        statusSink: ManualCookieStatusSink
    ): StreamingManualCookieController.Listener
}

internal class MainStreamingManualCookieListener(
    private val selectedProviderSource: ManualCookieSelectedProviderSource,
    private val dialogPresenter: ManualCookieDialogPresenter,
    private val loginSuccessHandler: ManualCookieLoginSuccessHandler,
    private val statusSink: ManualCookieStatusSink
) : StreamingManualCookieController.Listener {
    override fun selectedProvider(): StreamingProviderName? =
        selectedProviderSource.selectedProvider()

    override fun showManualCookieDialog(dialogState: StreamingManualCookieDialogState) {
        dialogPresenter.showManualCookieDialog(dialogState)
    }

    override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        loginSuccessHandler.onStreamingLoginSuccess(provider)
    }

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
