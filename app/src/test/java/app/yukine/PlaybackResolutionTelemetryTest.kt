package app.yukine

import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics
import app.yukine.streaming.StreamingErrorCode
import app.yukine.streaming.StreamingPlaybackResolutionPath
import app.yukine.streaming.StreamingPlaybackTelemetryEvent
import app.yukine.streaming.StreamingPlaybackTelemetryStage
import app.yukine.streaming.StreamingProviderName
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResolutionTelemetryTest {
    @Test
    fun failurePersistenceIsAsynchronousDeduplicatedAndCancellationSafe() {
        val queued = mutableListOf<Runnable>()
        val persisted = mutableListOf<StreamingPlaybackTelemetryEvent>()
        val diagnostics = PlaybackStreamingDiagnostics()
        val telemetry = PlaybackResolutionTelemetry(
            diagnostics,
            Executor(queued::add),
            PlaybackResolutionFailureRecorder(persisted::add)
        )
        val failure = StreamingPlaybackTelemetryEvent(
            stage = StreamingPlaybackTelemetryStage.URL_RESOLVE,
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            resolutionPath = StreamingPlaybackResolutionPath.KNOWN_PROVIDER_ID,
            durationMs = 250L,
            success = false,
            errorCode = StreamingErrorCode.SOURCE_UNAVAILABLE
        )

        telemetry.record(failure)
        telemetry.record(failure)
        telemetry.record(failure.copy(cancelled = true))

        assertEquals(1, queued.size)
        assertTrue(persisted.isEmpty())
        queued.removeAt(0).run()
        assertEquals(listOf(failure), persisted)
        assertEquals(2, diagnostics.snapshot().providerStageFailures)
        assertEquals(1, diagnostics.snapshot().providerStageCancellations)

        telemetry.record(failure)
        assertEquals(1, queued.size)
    }
}
