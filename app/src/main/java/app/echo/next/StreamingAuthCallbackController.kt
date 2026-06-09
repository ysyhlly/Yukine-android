package app.echo.next

import android.content.Intent

internal class StreamingAuthCallbackController(
    private val actionsController: StreamingAuthCallbackHandler
) {
    fun handleInitialIntent(intent: Intent?): Boolean = actionsController.handleAuthCallback(intent)

    fun handleNewIntent(intent: Intent?): Boolean = actionsController.handleAuthCallback(intent)
}
