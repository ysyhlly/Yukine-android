package app.echo.next

import android.content.Intent

internal class ActivityIntentController(
    private val streamingAuthCallbackController: StreamingAuthCallbackController
) {
    fun handleInitialIntent(intent: Intent?): Boolean =
        streamingAuthCallbackController.handleInitialIntent(intent)

    fun handleNewIntent(intent: Intent?): Boolean =
        streamingAuthCallbackController.handleNewIntent(intent)
}
