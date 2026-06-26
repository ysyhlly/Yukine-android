package app.yukine

internal fun interface QueuePlaybackActionResultApplier {
    fun apply(result: PlaybackActionResultUi?)
}

internal fun interface QueuePlaybackServiceAvailability {
    fun hasService(): Boolean
}

internal fun interface QueueStatusProvider {
    fun status(): String
}

internal fun interface QueueStatusSink {
    fun set(status: String)
}

internal fun interface QueueNoArgAction {
    fun run()
}
