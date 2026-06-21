package app.yukine

import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels

internal data class StreamingSearchChromeState(
    val labels: StreamingSearchLabels,
    val actions: StreamingSearchActions
)

internal fun interface StreamingSearchChromeSink {
    fun publish(state: StreamingSearchChromeState)
}

internal class StreamingSearchChromeBindings(
    private val sink: StreamingSearchChromeSink
) : StreamingSearchEventController.ContentSink {
    override fun publishStreamingSearchChrome(
        labels: StreamingSearchLabels,
        actions: StreamingSearchActions
    ) {
        sink.publish(StreamingSearchChromeState(labels, actions))
    }
}
