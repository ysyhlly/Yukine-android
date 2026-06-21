package app.yukine

internal fun interface LanguageTextProvider {
    fun text(key: String): String
}

internal class NetworkRequestLabels(
    private val textProvider: LanguageTextProvider
) : NetworkRequestController.Labels {
    override fun text(key: String): String = textProvider.text(key)
}

internal class NetworkRequestStatusListener(
    private val statusSink: NetworkRequestStatusSink
) : NetworkRequestController.Listener {
    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}

internal fun interface NetworkRequestStatusSink {
    fun setStatus(status: String)
}
