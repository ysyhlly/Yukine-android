package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import org.junit.Test;

public class PlaybackBufferingDiagnosticsRecorderOwnerTest {
    @Test
    public void recordsBufferingFromSnapshot() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackBufferingDiagnosticsRecorderOwner owner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> diagnostics);

        owner.record(PlaybackStateSnapshot.empty());

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.bufferingEvents);
        assertEquals(0L, snapshot.recentEvents.get(0).positionMs);
    }

    @Test
    public void ignoresMissingDiagnosticsOrSnapshot() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackBufferingDiagnosticsRecorderOwner nullProviderOwner =
                new PlaybackBufferingDiagnosticsRecorderOwner(null);
        PlaybackBufferingDiagnosticsRecorderOwner missingDiagnosticsOwner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> null);
        PlaybackBufferingDiagnosticsRecorderOwner owner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> diagnostics);

        nullProviderOwner.record(PlaybackStateSnapshot.empty());
        missingDiagnosticsOwner.record(PlaybackStateSnapshot.empty());
        owner.record(null);

        assertEquals(0, diagnostics.snapshot().bufferingEvents);
    }
}
