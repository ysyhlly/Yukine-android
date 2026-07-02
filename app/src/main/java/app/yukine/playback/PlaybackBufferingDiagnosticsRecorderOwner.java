package app.yukine.playback;

import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

import java.util.function.Supplier;

final class PlaybackBufferingDiagnosticsRecorderOwner
        implements PlaybackStatePublisher.BufferingRecorder {
    private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider;

    PlaybackBufferingDiagnosticsRecorderOwner(
            Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider
    ) {
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
    }

    static PlaybackBufferingDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider(
            Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider
    ) {
        return new PlaybackBufferingDiagnosticsRecorderOwner(streamingDiagnosticsProvider);
    }

    @Override
    public void record(PlaybackStateSnapshot snapshot) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnosticsProvider == null
                ? null
                : streamingDiagnosticsProvider.get();
        if (diagnostics != null && snapshot != null) {
            diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        }
    }
}
