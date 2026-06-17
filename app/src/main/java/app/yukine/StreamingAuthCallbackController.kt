package app.yukine

import android.content.Intent

internal class StreamingAuthCallbackController(
    private val actionsController: StreamingAuthCallbackHandler
) {
    fun handleInitialIntent(intent: Intent?): Boolean = handle(intent)

    fun handleNewIntent(intent: Intent?): Boolean = handle(intent)

    private fun handle(intent: Intent?): Boolean {
        return actionsController.handleAuthCallback(
            intent?.data?.toString(),
            intent?.getStringExtra(StreamingWebAuthActivity.EXTRA_COOKIE_HEADER)
        )
    }
}
