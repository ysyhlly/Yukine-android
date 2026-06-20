package app.yukine

import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingManualCookieDialogPresenter {
    fun show(dialogState: StreamingManualCookieDialogState)
}

internal fun interface StreamingManualCookieLoginSuccessAction {
    fun onSuccess(provider: StreamingProviderName)
}

internal class StreamingManualCookieBindings(
    private val selectedProviderProvider: StreamingSelectedProviderProvider,
    private val dialogPresenter: StreamingManualCookieDialogPresenter,
    private val loginSuccessAction: StreamingManualCookieLoginSuccessAction,
    private val statusSink: QueueStatusSink
) : StreamingManualCookieController.Listener {
    override fun selectedProvider(): StreamingProviderName? {
        return selectedProviderProvider.provider()
    }

    override fun showManualCookieDialog(dialogState: StreamingManualCookieDialogState) {
        dialogPresenter.show(dialogState)
    }

    override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        loginSuccessAction.onSuccess(provider)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
