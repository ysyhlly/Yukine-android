package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics
import app.yukine.streaming.StreamingPlaybackTelemetry
import app.yukine.streaming.StreamingPlaybackTelemetryEvent
import app.yukine.streaming.StreamingPlaybackTelemetryStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface PlaybackResolutionFailureRecorder {
    fun record(event: StreamingPlaybackTelemetryEvent)
}

/** Bridges typed streaming telemetry to diagnostics and asynchronous Room health persistence. */
@Singleton
class PlaybackResolutionTelemetry internal constructor(
    private val diagnostics: PlaybackStreamingDiagnostics,
    private val persistenceExecutor: Executor,
    private val failureRecorder: PlaybackResolutionFailureRecorder
) : StreamingPlaybackTelemetry {
    private val pendingFailureKeys = ConcurrentHashMap.newKeySet<String>()

    @Inject
    constructor(repository: MusicLibraryRepository) : this(
        PlaybackStreamingDiagnostics.process(),
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "EchoPlaybackTelemetry").apply {
                isDaemon = true
                priority = (Thread.NORM_PRIORITY - 1).coerceAtLeast(Thread.MIN_PRIORITY)
            }
        },
        PlaybackResolutionFailureRecorder { event ->
            repository.recordPlaybackResolutionFailure(
                event.provider.wireName,
                event.providerTrackId,
                event.errorCode?.wireName ?: "UNKNOWN",
                event.timedOut
            )
        }
    )

    override fun record(event: StreamingPlaybackTelemetryEvent) {
        diagnostics.recordProviderStage(
            event.stage.wireName,
            event.provider.wireName,
            event.durationMs,
            event.success,
            event.timedOut,
            event.cancelled,
            event.cacheHit,
            event.errorCode?.wireName.orEmpty(),
            event.resolutionPath?.wireName.orEmpty(),
            event.candidateCount
        )
        if (event.stage != StreamingPlaybackTelemetryStage.URL_RESOLVE ||
            event.success || event.cancelled || event.providerTrackId.isBlank()
        ) {
            return
        }
        val key = "${event.provider.wireName}:${event.providerTrackId}"
        if (!pendingFailureKeys.add(key)) {
            return
        }
        try {
            persistenceExecutor.execute {
                try {
                    failureRecorder.record(event)
                } finally {
                    pendingFailureKeys.remove(key)
                }
            }
        } catch (_: RuntimeException) {
            pendingFailureKeys.remove(key)
        }
    }
}
