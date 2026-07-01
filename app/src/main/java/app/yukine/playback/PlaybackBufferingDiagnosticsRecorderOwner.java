package app.yukine.playback;

import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

final class PlaybackBufferingDiagnosticsRecorderOwner
        implements PlaybackStatePublisher.BufferingRecorder {
    interface StreamingDiagnosticsProvider {
        PlaybackStreamingDiagnostics streamingDiagnostics();
    }

    private final StreamingDiagnosticsProvider streamingDiagnosticsProvider;

    PlaybackBufferingDiagnosticsRecorderOwner(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider
    ) {
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
    }

    static PlaybackBufferingDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider
    ) {
        return new PlaybackBufferingDiagnosticsRecorderOwner(streamingDiagnosticsProvider);
    }

    @Override
    public void record(PlaybackStateSnapshot snapshot) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnosticsProvider == null
                ? null
                : streamingDiagnosticsProvider.streamingDiagnostics();
        if (diagnostics != null && snapshot != null) {
            diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        }
    }
}
