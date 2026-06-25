package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;

import app.yukine.model.Track;

public class PlaybackStreamingDiagnosticsTest {
    @Test
    public void recordsBufferingRecoveryAndPrecacheEvents() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        Track track = new Track(
                1L,
                "Echo",
                "Tester",
                "Album",
                0L,
                Uri.EMPTY,
                "streaming:netease:track-1"
        );

        diagnostics.recordBuffering(track, 1200L);
        diagnostics.recordRecovery(track, 1200L, "high");
        diagnostics.recordPrecacheQueued(track);
        diagnostics.recordPrecacheComplete(track, 131072L);
        diagnostics.recordPrecacheSegmentComplete(track, 524288L, 262144L);

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.bufferingEvents);
        assertEquals(1, snapshot.recoveryEvents);
        assertEquals(1, snapshot.precacheAttempts);
        assertEquals(1, snapshot.precacheSuccesses);
        assertEquals(0, snapshot.precacheFailures);
        assertEquals(1, snapshot.precacheSegmentSuccesses);
        assertEquals(0, snapshot.precacheSegmentFailures);
        assertEquals(5, snapshot.recentEvents.size());
        assertEquals("precache_segment_complete", snapshot.recentEvents.get(0).type);
        assertEquals(524288L, snapshot.recentEvents.get(0).positionMs);
        assertEquals("buffering", snapshot.recentEvents.get(4).type);
    }
}
