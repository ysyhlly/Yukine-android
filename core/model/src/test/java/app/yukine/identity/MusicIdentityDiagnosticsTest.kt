package app.yukine.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicIdentityDiagnosticsTest {
    @Test
    fun reportsBoundedP50P95AndWorkUnitsWithoutMetadata() {
        val diagnostics = MusicIdentityDiagnostics()
        repeat(150) { index ->
            diagnostics.record(
                MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER,
                MusicIdentityDiagnostics.Stage.SCORING,
                durationMs = index.toLong() + 1L,
                workUnits = 2L
            )
        }

        val scoring = diagnostics.snapshot(MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER)
            .stages.getValue(MusicIdentityDiagnostics.Stage.SCORING)

        assertEquals(128, scoring.sampleCount)
        assertEquals(256L, scoring.workUnits)
        assertEquals(23L, scoring.minimumMs)
        assertEquals(150L, scoring.maximumMs)
        assertEquals(86L, scoring.p50Ms)
        assertEquals(144L, scoring.p95Ms)
        assertTrue(
            diagnostics.snapshot(MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER)
                .compactSummary()
                .contains("scoring=86/144ms#128@256")
        )
    }

    @Test
    fun elapsedTimingUsesMonotonicNanosecondsAndRoundsSubMillisecondWorkUp() {
        var nanos = 10_000_000L
        val diagnostics = MusicIdentityDiagnostics { nanos }
        val started = diagnostics.startNanos()
        nanos += 250_000L

        diagnostics.recordElapsed(
            MusicIdentityDiagnostics.Operation.PLATFORM_SYNC,
            MusicIdentityDiagnostics.Stage.NORMALIZATION,
            started,
            workUnits = 3L
        )

        val stage = diagnostics.snapshot(MusicIdentityDiagnostics.Operation.PLATFORM_SYNC)
            .stages.getValue(MusicIdentityDiagnostics.Stage.NORMALIZATION)
        assertEquals(1L, stage.p50Ms)
        assertEquals(3L, stage.workUnits)
    }
}
