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

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.bufferingEvents);
        assertEquals(1, snapshot.recoveryEvents);
        assertEquals(1, snapshot.precacheAttempts);
        assertEquals(1, snapshot.precacheSuccesses);
        assertEquals(0, snapshot.precacheFailures);
        assertEquals(4, snapshot.recentEvents.size());
        assertEquals("precache_complete", snapshot.recentEvents.get(0).type);
        assertEquals("buffering", snapshot.recentEvents.get(3).type);
    }
}
