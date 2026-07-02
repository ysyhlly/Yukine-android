package app.yukine

internal fun interface QueuePlaybackActionResultApplier {
    fun apply(result: PlaybackActionResultUi?)
}
