package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

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

    interface RealtimeBeatProvider {
        float beat();
    }

    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider;
    private final PlaybackPositionProvider playbackPositionProvider;
    private final RuntimeStateProvider runtimeStateProvider;
    private final LongSupplier sleepTimerProvider;
    private final VisualizationProvider visualizationProvider;
    private final RealtimeBeatProvider realtimeBeatProvider;
    private final int defaultRepeatMode;

    PlaybackStateSnapshotOwner(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider,
            PlaybackPositionProvider playbackPositionProvider,
            RuntimeStateProvider runtimeStateProvider,
            LongSupplier sleepTimerProvider,
            VisualizationProvider visualizationProvider,
            RealtimeBeatProvider realtimeBeatProvider,
            int defaultRepeatMode
    ) {
        this.queueStateProvider = queueStateProvider;
        this.playbackPositionProvider = playbackPositionProvider;
        this.runtimeStateProvider = runtimeStateProvider;
        this.sleepTimerProvider = sleepTimerProvider;
        this.visualizationProvider = visualizationProvider;
        this.realtimeBeatProvider = realtimeBeatProvider;
        this.defaultRepeatMode = defaultRepeatMode;
    }

    static RuntimeStateProvider fromRuntimeStateManagerProvider(
            Supplier<PlaybackRuntimeStateManager> runtimeStateManagerProvider
    ) {
        return new RuntimeStateProvider() {
            @Override
            public boolean preparing() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager != null && manager.preparing();
            }

            @Override
            public String errorMessage() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager == null ? "" : manager.errorMessage();
            }

            @Override
            public boolean shuffleEnabled() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager != null && manager.shuffleEnabled();
            }

            @Override
            public int repeatMode() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager == null ? 0 : manager.repeatMode();
            }

            @Override
            public float playbackSpeed() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager == null ? 1.0f : manager.playbackSpeed();
            }

            @Override
            public float appVolume() {
                PlaybackRuntimeStateManager manager = runtimeStateManager();
                return manager == null ? 1.0f : manager.appVolume();
            }

            private PlaybackRuntimeStateManager runtimeStateManager() {
                return runtimeStateManagerProvider == null ? null : runtimeStateManagerProvider.get();
            }
        };
    }

    static VisualizationProvider fromVisualizationAnalyzerProvider(
            Supplier<PlaybackVisualizationAnalyzer> visualizationAnalyzerProvider
    ) {
        return new VisualizationProvider() {
            @Override
            public boolean shouldDeferPlaybackVisualization() {
                PlaybackVisualizationAnalyzer analyzer = playbackVisualizationAnalyzer();
                return analyzer != null && analyzer.shouldDeferPlaybackVisualization();
            }

            @Override
            public PlaybackWaveformSnapshot waveformSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                PlaybackVisualizationAnalyzer analyzer = playbackVisualizationAnalyzer();
                return analyzer == null
                        ? PlaybackWaveformSnapshot.empty()
                        : analyzer.waveformSnapshot(track, durationMs, deferGeneration);
            }

            @Override
            public PlaybackSpectrumSnapshot spectrumSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                PlaybackVisualizationAnalyzer analyzer = playbackVisualizationAnalyzer();
                return analyzer == null
                        ? PlaybackSpectrumSnapshot.empty()
                        : analyzer.spectrumSnapshot(track, durationMs, deferGeneration);
            }

            private PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer() {
                return visualizationAnalyzerProvider == null
                        ? null
                        : visualizationAnalyzerProvider.get();
            }
        };
    }

    PlaybackStateSnapshot snapshot() {
        PlaybackQueueManager.QueueStateSnapshot queueState = queueStateProvider == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateProvider.get();
        if (queueState == null) {
            queueState = PlaybackQueueManager.QueueStateSnapshot.empty();
        }
        Track track = queueState.getCurrentTrack();
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
                queueState.getCurrentIndex(),
                queueState.getQueueSize(),
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
                playing && realtimeBeatProvider != null ? realtimeBeatProvider.beat() : 0f
        );
    }
}
