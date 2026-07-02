package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Function;
import java.util.function.Supplier;

final class PlaybackStreamingDiagnosticsRecorderOwner
        implements PlaybackStatePublisher.BufferingRecorder {
    private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider;
    private final Function<Track, String> streamingQualityProvider;

    PlaybackStreamingDiagnosticsRecorderOwner(
            Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider,
            Function<Track, String> streamingQualityProvider
    ) {
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
        this.streamingQualityProvider = streamingQualityProvider;
    }

    @Override
    public void record(PlaybackStateSnapshot snapshot) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnostics();
        if (diagnostics != null && snapshot != null) {
            diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        }
    }

    void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnostics();
        if (diagnostics == null || recovery == null) {
            return;
        }
        Track track = recovery.getTrack();
        String quality = streamingQualityProvider == null
                ? ""
                : streamingQualityProvider.apply(track);
        diagnostics.recordRecovery(track, recovery.getRestoredPositionMs(), quality);
    }

    private PlaybackStreamingDiagnostics streamingDiagnostics() {
        return streamingDiagnosticsProvider == null
                ? null
                : streamingDiagnosticsProvider.get();
    }
}
