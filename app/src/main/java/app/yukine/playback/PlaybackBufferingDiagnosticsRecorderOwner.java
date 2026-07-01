package app.yukine.playback;

import app.yukine.model.Track;

final class PlaybackBufferingDiagnosticsRecorderOwner
        implements PlaybackStatePublisher.BufferingRecorder {
    interface StreamingDiagnosticsProvider {
        PlaybackStreamingDiagnostics streamingDiagnostics();
    }

    interface StreamingDiagnosticsOperations {
        void recordBuffering(Track track, long positionMs);
    }

    interface StreamingDiagnosticsOperationsProvider {
        StreamingDiagnosticsOperations streamingDiagnosticsOperations();
    }

    private final StreamingDiagnosticsOperationsProvider streamingDiagnosticsOperationsProvider;

    PlaybackBufferingDiagnosticsRecorderOwner(
            StreamingDiagnosticsOperationsProvider streamingDiagnosticsOperationsProvider
    ) {
        this.streamingDiagnosticsOperationsProvider = streamingDiagnosticsOperationsProvider;
    }

    static PlaybackBufferingDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider
    ) {
        return new PlaybackBufferingDiagnosticsRecorderOwner(
                new PlaybackStreamingDiagnosticsOperationsProvider(streamingDiagnosticsProvider)
        );
    }

    @Override
    public void record(PlaybackStateSnapshot snapshot) {
        StreamingDiagnosticsOperations operations = streamingDiagnosticsOperationsProvider == null
                ? null
                : streamingDiagnosticsOperationsProvider.streamingDiagnosticsOperations();
        if (operations != null && snapshot != null) {
            operations.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        }
    }

    private static final class PlaybackStreamingDiagnosticsOperationsProvider
            implements StreamingDiagnosticsOperationsProvider {
        private final StreamingDiagnosticsProvider streamingDiagnosticsProvider;

        private PlaybackStreamingDiagnosticsOperationsProvider(
                StreamingDiagnosticsProvider streamingDiagnosticsProvider
        ) {
            this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
        }

        @Override
        public StreamingDiagnosticsOperations streamingDiagnosticsOperations() {
            PlaybackStreamingDiagnostics diagnostics = streamingDiagnosticsProvider == null
                    ? null
                    : streamingDiagnosticsProvider.streamingDiagnostics();
            return diagnostics == null ? null : new PlaybackStreamingDiagnosticsOperations(diagnostics);
        }
    }

    private static final class PlaybackStreamingDiagnosticsOperations
            implements StreamingDiagnosticsOperations {
        private final PlaybackStreamingDiagnostics diagnostics;

        private PlaybackStreamingDiagnosticsOperations(PlaybackStreamingDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void recordBuffering(Track track, long positionMs) {
            diagnostics.recordBuffering(track, positionMs);
        }
    }
}
