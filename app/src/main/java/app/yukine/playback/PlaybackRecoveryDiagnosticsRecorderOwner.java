package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackRecoveryDiagnosticsRecorderOwner {
    interface StreamingDiagnosticsProvider {
        PlaybackStreamingDiagnostics streamingDiagnostics();
    }

    interface StreamingQualityProvider {
        String streamingQualityForTrack(Track track);
    }

    private final StreamingDiagnosticsProvider streamingDiagnosticsProvider;
    private final StreamingQualityProvider streamingQualityProvider;

    PlaybackRecoveryDiagnosticsRecorderOwner(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider,
            StreamingQualityProvider streamingQualityProvider
    ) {
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
        this.streamingQualityProvider = streamingQualityProvider;
    }

    static PlaybackRecoveryDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider,
            StreamingQualityProvider streamingQualityProvider
    ) {
        return new PlaybackRecoveryDiagnosticsRecorderOwner(streamingDiagnosticsProvider, streamingQualityProvider);
    }

    void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnosticsProvider == null
                ? null
                : streamingDiagnosticsProvider.streamingDiagnostics();
        if (diagnostics == null || recovery == null) {
            return;
        }
        Track track = recovery.getTrack();
        String quality = streamingQualityProvider == null
                ? ""
                : streamingQualityProvider.streamingQualityForTrack(track);
        diagnostics.recordRecovery(track, recovery.getRestoredPositionMs(), quality);
    }
}
