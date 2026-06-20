package app.yukine

internal fun interface SettingsStreamingGatewayEndpointApplier {
    fun apply(endpoint: String)
}

internal class SettingsActionBindings(
    private val streamingGatewayEndpointApplier: SettingsStreamingGatewayEndpointApplier,
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier,
    private val lyricsReloader: QueueNoArgAction
) : SettingsActionController.Listener {
    override fun applyStreamingGatewayEndpoint(endpoint: String) {
        streamingGatewayEndpointApplier.apply(endpoint)
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        playbackActionResultApplier.apply(result)
    }

    override fun reloadCurrentLyrics() {
        lyricsReloader.run()
    }
}
