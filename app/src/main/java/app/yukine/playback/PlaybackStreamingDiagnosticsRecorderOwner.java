package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Function;

final class PlaybackStreamingDiagnosticsRecorderOwner
        implements PlaybackStatePublisher.BufferingRecorder {
    private final PlaybackStreamingDiagnostics streamingDiagnostics;
    private final Function<Track, String> streamingQualityProvider;

    PlaybackStreamingDiagnosticsRecorderOwner(
            PlaybackStreamingDiagnostics streamingDiagnostics,
            Function<Track, String> streamingQualityProvider
    ) {
        this.streamingDiagnostics = streamingDiagnostics;
        this.streamingQualityProvider = streamingQualityProvider;
    }

    @Override
    public void record(PlaybackStateSnapshot snapshot) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnostics;
        if (diagnostics != null && snapshot != null) {
            diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        }
    }

    void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnostics;
        if (diagnostics == null || recovery == null) {
            return;
        }
        Track track = recovery.getTrack();
        String quality = streamingQualityProvider == null
                ? ""
                : streamingQualityProvider.apply(track);
        diagnostics.recordRecovery(track, recovery.getRestoredPositionMs(), quality);
    }
}
