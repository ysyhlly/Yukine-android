package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Function;
import java.util.function.Supplier;

final class PlaybackRecoveryDiagnosticsRecorderOwner {
    private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider;
    private final Function<Track, String> streamingQualityProvider;

    PlaybackRecoveryDiagnosticsRecorderOwner(
            Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider,
            Function<Track, String> streamingQualityProvider
    ) {
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
        this.streamingQualityProvider = streamingQualityProvider;
    }

    void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery) {
        PlaybackStreamingDiagnostics diagnostics = streamingDiagnosticsProvider == null
                ? null
                : streamingDiagnosticsProvider.get();
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
