package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import app.yukine.model.Track;
import org.junit.Test;

public class PlaybackBufferingDiagnosticsRecorderOwnerTest {
    @Test
    public void recordsBufferingFromSnapshot() {
        FakeStreamingDiagnosticsOperations operations = new FakeStreamingDiagnosticsOperations();
        PlaybackBufferingDiagnosticsRecorderOwner owner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> operations);

        owner.record(PlaybackStateSnapshot.empty());

        assertEquals(1, operations.bufferingCalls);
        assertEquals(0L, operations.lastPositionMs);
    }

    @Test
    public void ignoresMissingDiagnosticsOperationsOrSnapshot() {
        FakeStreamingDiagnosticsOperations operations = new FakeStreamingDiagnosticsOperations();
        PlaybackBufferingDiagnosticsRecorderOwner nullProviderOwner =
                new PlaybackBufferingDiagnosticsRecorderOwner(null);
        PlaybackBufferingDiagnosticsRecorderOwner missingOperationsOwner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> null);
        PlaybackBufferingDiagnosticsRecorderOwner owner =
                new PlaybackBufferingDiagnosticsRecorderOwner(() -> operations);

        nullProviderOwner.record(PlaybackStateSnapshot.empty());
        missingOperationsOwner.record(PlaybackStateSnapshot.empty());
        owner.record(null);

        assertEquals(0, operations.bufferingCalls);
    }

    private static final class FakeStreamingDiagnosticsOperations
            implements PlaybackBufferingDiagnosticsRecorderOwner.StreamingDiagnosticsOperations {
        private int bufferingCalls;
        private long lastPositionMs = -1L;

        @Override
        public void recordBuffering(Track track, long positionMs) {
            bufferingCalls++;
            lastPositionMs = positionMs;
        }
    }
}
