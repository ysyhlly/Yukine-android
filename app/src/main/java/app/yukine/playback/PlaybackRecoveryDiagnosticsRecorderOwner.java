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

    interface StreamingDiagnosticsOperations {
        void recordRecovery(Track track, long positionMs, String quality);
    }

    interface StreamingDiagnosticsOperationsProvider {
        StreamingDiagnosticsOperations streamingDiagnosticsOperations();
    }

    private final StreamingDiagnosticsOperationsProvider streamingDiagnosticsOperationsProvider;
    private final StreamingQualityProvider streamingQualityProvider;

    PlaybackRecoveryDiagnosticsRecorderOwner(
            StreamingDiagnosticsOperationsProvider streamingDiagnosticsOperationsProvider,
            StreamingQualityProvider streamingQualityProvider
    ) {
        this.streamingDiagnosticsOperationsProvider = streamingDiagnosticsOperationsProvider;
        this.streamingQualityProvider = streamingQualityProvider;
    }

    static PlaybackRecoveryDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider(
            StreamingDiagnosticsProvider streamingDiagnosticsProvider,
            StreamingQualityProvider streamingQualityProvider
    ) {
        return new PlaybackRecoveryDiagnosticsRecorderOwner(
                new PlaybackStreamingDiagnosticsOperationsProvider(streamingDiagnosticsProvider),
                streamingQualityProvider
        );
    }

    void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery) {
        StreamingDiagnosticsOperations operations = streamingDiagnosticsOperationsProvider == null
                ? null
                : streamingDiagnosticsOperationsProvider.streamingDiagnosticsOperations();
        if (operations == null || recovery == null) {
            return;
        }
        Track track = recovery.getTrack();
        String quality = streamingQualityProvider == null
                ? ""
                : streamingQualityProvider.streamingQualityForTrack(track);
        operations.recordRecovery(track, recovery.getRestoredPositionMs(), quality);
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
        public void recordRecovery(Track track, long positionMs, String quality) {
            diagnostics.recordRecovery(track, positionMs, quality);
        }
    }
}
