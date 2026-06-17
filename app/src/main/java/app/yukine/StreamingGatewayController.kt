package app.yukine

internal class StreamingGatewayController(
    private val settingsStore: StreamingGatewayEndpointStore,
    private val viewModelBridge: ViewModelBridge,
    private val listener: Listener
) {
    interface ViewModelBridge {
        fun configureStreamingRepository()

        fun refreshStreamingProviders()
    }

    interface Listener {
        fun onStreamingGatewayApplied(endpoint: String)
    }

    fun configureRepository() {
        viewModelBridge.configureStreamingRepository()
    }

    fun applyEndpoint(endpoint: String?) {
        settingsStore.setEndpoint(endpoint)
        viewModelBridge.configureStreamingRepository()
        viewModelBridge.refreshStreamingProviders()
        listener.onStreamingGatewayApplied(settingsStore.endpoint())
    }
}
