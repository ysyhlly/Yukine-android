package app.yukine

import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels

internal fun interface StreamingSearchChromeSink {
    fun publish(labels: StreamingSearchLabels, actions: StreamingSearchActions)
}

internal class StreamingSearchChromeBindings(
    private val sink: StreamingSearchChromeSink
) : StreamingSearchEventController.ContentSink {
    override fun publishStreamingSearchChrome(
        labels: StreamingSearchLabels,
        actions: StreamingSearchActions
    ) {
        sink.publish(labels, actions)
    }
}
