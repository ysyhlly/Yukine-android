package app.yukine

internal fun interface QueuePlaybackActionResultApplier {
    fun apply(result: PlaybackActionResultUi?)
}

internal fun interface QueuePlaybackServiceAvailability {
    fun hasService(): Boolean
}

internal fun interface QueueMoveAction {
    fun move(fromIndex: Int, toIndex: Int)
}

internal fun interface QueueStatusProvider {
    fun status(): String
}

internal fun interface QueueStatusSink {
    fun set(status: String)
}

internal class QueueActionBindings(
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier,
    private val playbackServiceAvailability: QueuePlaybackServiceAvailability,
    private val moveQueueTrackAction: QueueMoveAction,
    private val renderNowBarAction: QueueNoArgAction,
    private val renderSelectedTabAction: QueueNoArgAction,
    private val clearQueueConfirmer: QueueNoArgAction,
    private val queueEmptyStatusProvider: QueueStatusProvider,
    private val statusSink: QueueStatusSink
) : QueueActionController.Listener {
    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        playbackActionResultApplier.apply(result)
    }

    override fun hasPlaybackService(): Boolean = playbackServiceAvailability.hasService()

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        moveQueueTrackAction.move(fromIndex, toIndex)
    }

    override fun renderNowBar() {
        renderNowBarAction.run()
    }

    override fun renderSelectedTab() {
        renderSelectedTabAction.run()
    }

    override fun confirmClearQueue() {
        clearQueueConfirmer.run()
    }

    override fun queueEmptyStatus(): String = queueEmptyStatusProvider.status()

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
