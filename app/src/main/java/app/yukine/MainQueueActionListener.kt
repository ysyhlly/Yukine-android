package app.yukine

internal fun interface QueuePlaybackServiceAvailability {
    fun hasService(): Boolean
}

internal fun interface QueueTrackMoveSink {
    fun move(fromIndex: Int, toIndex: Int)
}

internal fun interface QueueNowBarRenderer {
    fun renderNowBar()
}

internal fun interface QueueSelectedTabRenderer {
    fun renderSelectedTab()
}

internal fun interface QueueClearQueueConfirmer {
    fun confirmClearQueue()
}

internal fun interface QueueEmptyStatusProvider {
    fun queueEmptyStatus(): String
}

internal fun interface QueueStatusSink {
    fun setStatus(status: String)
}

internal fun interface MainQueueActionListenerFactory {
    fun create(
        resultApplier: QueuePlaybackActionResultApplier,
        serviceAvailability: QueuePlaybackServiceAvailability,
        trackMoveSink: QueueTrackMoveSink,
        nowBarRenderer: QueueNowBarRenderer,
        selectedTabRenderer: QueueSelectedTabRenderer,
        clearQueueConfirmer: QueueClearQueueConfirmer,
        emptyStatusProvider: QueueEmptyStatusProvider,
        statusSink: QueueStatusSink
    ): QueueActionController.Listener
}

internal class MainQueueActionListener(
    private val resultApplier: QueuePlaybackActionResultApplier,
    private val serviceAvailability: QueuePlaybackServiceAvailability,
    private val trackMoveSink: QueueTrackMoveSink,
    private val nowBarRenderer: QueueNowBarRenderer,
    private val selectedTabRenderer: QueueSelectedTabRenderer,
    private val clearQueueConfirmer: QueueClearQueueConfirmer,
    private val emptyStatusProvider: QueueEmptyStatusProvider,
    private val statusSink: QueueStatusSink
) : QueueActionController.Listener {
    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        resultApplier.apply(result)
    }

    override fun hasPlaybackService(): Boolean =
        serviceAvailability.hasService()

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        trackMoveSink.move(fromIndex, toIndex)
    }

    override fun renderNowBar() {
        nowBarRenderer.renderNowBar()
    }

    override fun renderSelectedTab() {
        selectedTabRenderer.renderSelectedTab()
    }

    override fun confirmClearQueue() {
        clearQueueConfirmer.confirmClearQueue()
    }

    override fun queueEmptyStatus(): String =
        emptyStatusProvider.queueEmptyStatus()

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
