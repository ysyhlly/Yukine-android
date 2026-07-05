package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackRecoveryDiagnosticsRecorderOwnerTest {
    @Test
    public void recordsRecoveryWithStreamingQuality() {
        Track track = track();
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackRecoveryDiagnosticsRecorderOwner owner = new PlaybackRecoveryDiagnosticsRecorderOwner(
                () -> diagnostics,
                requestedTrack -> requestedTrack == track ? "lossless" : ""
        );

        owner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track, 1200L, true));

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.recoveryEvents);
        assertEquals("streaming:test:11", snapshot.recentEvents.get(0).trackKey);
        assertEquals(1200L, snapshot.recentEvents.get(0).positionMs);
        assertEquals("lossless", snapshot.recentEvents.get(0).quality);
    }

    @Test
    public void ignoresMissingDiagnosticsOrRecovery() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackRecoveryDiagnosticsRecorderOwner nullProviderOwner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(null, track -> "high");
        PlaybackRecoveryDiagnosticsRecorderOwner missingDiagnosticsOwner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> null, track -> "high");
        PlaybackRecoveryDiagnosticsRecorderOwner owner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> diagnostics, track -> "high");

        nullProviderOwner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track(), 1L, true));
        missingDiagnosticsOwner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track(), 1L, true));
        owner.record(null);

        assertEquals(0, diagnostics.snapshot().recoveryEvents);
    }

    @Test
    public void recordsEmptyQualityWhenQualityProviderIsMissing() {
        Track track = track();
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackRecoveryDiagnosticsRecorderOwner owner =
                new PlaybackRecoveryDiagnosticsRecorderOwner(() -> diagnostics, null);

        owner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track, 1200L, true));

        assertEquals("", diagnostics.snapshot().recentEvents.get(0).quality);
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

}
