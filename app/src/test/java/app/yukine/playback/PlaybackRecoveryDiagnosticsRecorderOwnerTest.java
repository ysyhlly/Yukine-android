package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackRecoveryDiagnosticsRecorderOwnerTest {
    @Test
    public void recordsRecoveryWithStreamingQuality() {
        Track track = track();
        FakeStreamingDiagnosticsOperations operations = new FakeStreamingDiagnosticsOperations();
        PlaybackRecoveryDiagnosticsRecorderOwner owner = new PlaybackRecoveryDiagnosticsRecorderOwner(
                () -> operations,
                requestedTrack -> requestedTrack == track ? "lossless" : ""
        );

        owner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track, 1200L, true));

        assertEquals(1, operations.recoveryCalls);
        assertSame(track, operations.lastTrack);
        assertEquals(1200L, operations.lastPositionMs);
        assertEquals("lossless", operations.lastQuality);
    }

    @Test
    public void ignoresMissingDiagnosticsOperationsOrRecovery() {
        FakeStreamingDiagnosticsOperations operations = new FakeStreamingDiagnosticsOperations();
        PlaybackRecoveryDiagnosticsRecorderOwner nullProviderOwner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(null, track -> "high");
        PlaybackRecoveryDiagnosticsRecorderOwner missingOperationsOwner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> null, track -> "high");
        PlaybackRecoveryDiagnosticsRecorderOwner owner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> operations, track -> "high");

        nullProviderOwner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track(), 1L, true));
        missingOperationsOwner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track(), 1L, true));
        owner.record(null);

        assertEquals(0, operations.recoveryCalls);
    }

    @Test
    public void recordsEmptyQualityWhenQualityProviderIsMissing() {
        Track track = track();
        FakeStreamingDiagnosticsOperations operations = new FakeStreamingDiagnosticsOperations();
        PlaybackRecoveryDiagnosticsRecorderOwner owner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> operations, null);

        owner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track, 1200L, true));

        assertEquals("", operations.lastQuality);
    }

    private static Track track() {
        return new Track(
                11L,
                "Track",
                "Artist",
                "Album",
                1000L,
                Uri.parse("https://example.test/track.mp3"),
                "streaming:test:11"
        );
    }

    private static final class FakeStreamingDiagnosticsOperations
            implements PlaybackRecoveryDiagnosticsRecorderOwner.StreamingDiagnosticsOperations {
        private int recoveryCalls;
        private Track lastTrack;
        private long lastPositionMs = -1L;
        private String lastQuality = "<unset>";

        @Override
        public void recordRecovery(Track track, long positionMs, String quality) {
            recoveryCalls++;
            lastTrack = track;
            lastPositionMs = positionMs;
            lastQuality = quality;
        }
    }
}
