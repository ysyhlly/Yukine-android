package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackStreamingDiagnosticsRecorderOwnerTest {
    @Test
    public void recordsBufferingFromSnapshot() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackStreamingDiagnosticsRecorderOwner owner =
                new PlaybackStreamingDiagnosticsRecorderOwner(diagnostics, track -> "");

        owner.record(PlaybackStateSnapshot.empty());

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.bufferingEvents);
        assertEquals(0L, snapshot.recentEvents.get(0).positionMs);
    }

    @Test
    public void recordsRecoveryWithStreamingQuality() {
        Track track = track();
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackStreamingDiagnosticsRecorderOwner owner = new PlaybackStreamingDiagnosticsRecorderOwner(
                diagnostics,
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
        PlaybackStreamingDiagnosticsRecorderOwner missingDiagnosticsOwner =
                new PlaybackStreamingDiagnosticsRecorderOwner(null, track -> "high");
        PlaybackStreamingDiagnosticsRecorderOwner owner =
                new PlaybackStreamingDiagnosticsRecorderOwner(diagnostics, track -> "high");

        missingDiagnosticsOwner.record(new PlaybackQueueManager.CurrentTrackReplacementRecovery(track(), 1L, true));
        missingDiagnosticsOwner.record(PlaybackStateSnapshot.empty());
        owner.record((PlaybackQueueManager.CurrentTrackReplacementRecovery) null);
        owner.record((PlaybackStateSnapshot) null);

        assertEquals(0, diagnostics.snapshot().recoveryEvents);
        assertEquals(0, diagnostics.snapshot().bufferingEvents);
    }

    @Test
    public void recordsEmptyQualityWhenQualityProviderIsMissing() {
        Track track = track();
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackStreamingDiagnosticsRecorderOwner owner =
                new PlaybackStreamingDiagnosticsRecorderOwner(diagnostics, null);

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
