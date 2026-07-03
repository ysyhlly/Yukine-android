package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class PlaybackStateSnapshotOwner {
    interface PlaybackPositionProvider {
        long positionMs();

        long durationMs();

        boolean isPlaying();
    }

    interface RuntimeStateProvider {
        boolean preparing();

        String errorMessage();

        boolean shuffleEnabled();

        int repeatMode();

        float playbackSpeed();

        float appVolume();
    }

    interface VisualizationProvider {
        boolean shouldDeferPlaybackVisualization();

        PlaybackWaveformSnapshot waveformSnapshot(Track track, long durationMs, boolean deferGeneration);

        PlaybackSpectrumSnapshot spectrumSnapshot(Track track, long durationMs, boolean deferGeneration);
    }

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final PlaybackPositionProvider playbackPositionProvider;
    private final RuntimeStateProvider runtimeStateProvider;
    private final LongSupplier sleepTimerProvider;
    private final VisualizationProvider visualizationProvider;
    private final DoubleSupplier realtimeBeatProvider;
    private final int defaultRepeatMode;

    PlaybackStateSnapshotOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            PlaybackPositionProvider playbackPositionProvider,
            RuntimeStateProvider runtimeStateProvider,
            LongSupplier sleepTimerProvider,
            VisualizationProvider visualizationProvider,
            DoubleSupplier realtimeBeatProvider,
            int defaultRepeatMode
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.playbackPositionProvider = playbackPositionProvider;
        this.runtimeStateProvider = runtimeStateProvider;
        this.sleepTimerProvider = sleepTimerProvider;
        this.visualizationProvider = visualizationProvider;
        this.realtimeBeatProvider = realtimeBeatProvider;
        this.defaultRepeatMode = defaultRepeatMode;
    }

    static RuntimeStateProvider fromRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return new RuntimeStateProvider() {
            @Override
            public boolean preparing() {
                return runtimeStateManager != null && runtimeStateManager.preparing();
            }

            @Override
            public String errorMessage() {
                return runtimeStateManager == null ? "" : runtimeStateManager.errorMessage();
            }

            @Override
            public boolean shuffleEnabled() {
                return runtimeStateManager != null && runtimeStateManager.shuffleEnabled();
            }

            @Override
            public int repeatMode() {
                return runtimeStateManager == null ? 0 : runtimeStateManager.repeatMode();
            }

            @Override
            public float playbackSpeed() {
                return runtimeStateManager == null ? 1.0f : runtimeStateManager.playbackSpeed();
            }

            @Override
            public float appVolume() {
                return runtimeStateManager == null ? 1.0f : runtimeStateManager.appVolume();
            }
        };
    }

    static VisualizationProvider fromVisualizationAnalyzer(
            PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer
    ) {
        return new VisualizationProvider() {
            @Override
            public boolean shouldDeferPlaybackVisualization() {
                return playbackVisualizationAnalyzer != null
                        && playbackVisualizationAnalyzer.shouldDeferPlaybackVisualization();
            }

            @Override
            public PlaybackWaveformSnapshot waveformSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                return playbackVisualizationAnalyzer == null
                        ? PlaybackWaveformSnapshot.empty()
                        : playbackVisualizationAnalyzer.waveformSnapshot(track, durationMs, deferGeneration);
            }

            @Override
            public PlaybackSpectrumSnapshot spectrumSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                return playbackVisualizationAnalyzer == null
                        ? PlaybackSpectrumSnapshot.empty()
                        : playbackVisualizationAnalyzer.spectrumSnapshot(track, durationMs, deferGeneration);
            }
        };
    }

    PlaybackStateSnapshot snapshot() {
        PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateSnapshot();
        Track track = queueSnapshot.getCurrentTrack();
        int currentIndex = queueSnapshot.getCurrentIndex();
        int queueSize = queueSnapshot.getQueueSize();
        long positionMs = playbackPositionProvider == null ? 0L : playbackPositionProvider.positionMs();
        long playbackDurationMs = playbackPositionProvider == null ? 0L : playbackPositionProvider.durationMs();
        boolean playing = playbackPositionProvider != null && playbackPositionProvider.isPlaying();
        long durationMs = track == null ? 0L : Math.max(track.durationMs, playbackDurationMs);
        boolean deferVisualGeneration = visualizationProvider != null
                && visualizationProvider.shouldDeferPlaybackVisualization();
        PlaybackWaveformSnapshot waveform = visualizationProvider == null
                ? PlaybackWaveformSnapshot.empty()
                : visualizationProvider.waveformSnapshot(track, durationMs, deferVisualGeneration);
        PlaybackSpectrumSnapshot spectrum = visualizationProvider == null
                ? PlaybackSpectrumSnapshot.empty()
                : visualizationProvider.spectrumSnapshot(track, durationMs, deferVisualGeneration);
        return new PlaybackStateSnapshot(
                track,
                currentIndex,
                queueSize,
                positionMs,
                durationMs,
                playing,
                runtimeStateProvider != null && runtimeStateProvider.preparing(),
                runtimeStateProvider == null ? "" : runtimeStateProvider.errorMessage(),
                runtimeStateProvider != null && runtimeStateProvider.shuffleEnabled(),
                runtimeStateProvider == null ? defaultRepeatMode : runtimeStateProvider.repeatMode(),
                runtimeStateProvider == null ? 1.0f : runtimeStateProvider.playbackSpeed(),
                runtimeStateProvider == null ? 1.0f : runtimeStateProvider.appVolume(),
                sleepTimerProvider == null ? 0L : sleepTimerProvider.getAsLong(),
                waveform,
                spectrum,
                playing && realtimeBeatProvider != null ? (float) realtimeBeatProvider.getAsDouble() : 0f
        );
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
