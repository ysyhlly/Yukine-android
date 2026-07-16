package app.yukine.streaming

enum class StreamingPlaybackTelemetryStage(val wireName: String) {
    TITLE_SEARCH("title_search"),
    CANDIDATE_RANK("candidate_rank"),
    URL_RESOLVE("url_resolve")
}

data class StreamingPlaybackTelemetryEvent(
    val stage: StreamingPlaybackTelemetryStage,
    val provider: StreamingProviderName,
    val providerTrackId: String = "",
    val resolutionPath: StreamingPlaybackResolutionPath? = null,
    val durationMs: Long,
    val success: Boolean,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val cacheHit: Boolean = false,
    val errorCode: StreamingErrorCode? = null,
    val candidateCount: Int = 0
)

fun interface StreamingPlaybackTelemetry {
    fun record(event: StreamingPlaybackTelemetryEvent)
}

object NoOpStreamingPlaybackTelemetry : StreamingPlaybackTelemetry {
    override fun record(event: StreamingPlaybackTelemetryEvent) = Unit
}
