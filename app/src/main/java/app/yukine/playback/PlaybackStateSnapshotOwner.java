package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

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

    private final PlaybackQueueManager playbackQueueManager;
    private final PlaybackPositionProvider playbackPositionProvider;
    private final RuntimeStateProvider runtimeStateProvider;
    private final LongSupplier sleepTimerProvider;
    private final VisualizationProvider visualizationProvider;
    private final DoubleSupplier realtimeBeatProvider;

    PlaybackStateSnapshotOwner(
            PlaybackQueueManager playbackQueueManager,
            PlaybackPositionProvider playbackPositionProvider,
            RuntimeStateProvider runtimeStateProvider,
            LongSupplier sleepTimerProvider,
            VisualizationProvider visualizationProvider,
            DoubleSupplier realtimeBeatProvider
    ) {
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.playbackPositionProvider = Objects.requireNonNull(
                playbackPositionProvider,
                "playbackPositionProvider"
        );
        this.runtimeStateProvider = Objects.requireNonNull(runtimeStateProvider, "runtimeStateProvider");
        this.sleepTimerProvider = Objects.requireNonNull(sleepTimerProvider, "sleepTimerProvider");
        this.visualizationProvider = Objects.requireNonNull(visualizationProvider, "visualizationProvider");
        this.realtimeBeatProvider = Objects.requireNonNull(realtimeBeatProvider, "realtimeBeatProvider");
    }

    static RuntimeStateProvider fromRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        PlaybackRuntimeStateManager runtimeStateOwner =
                Objects.requireNonNull(runtimeStateManager, "runtimeStateManager");
        return new RuntimeStateProvider() {
            @Override
            public boolean preparing() {
                return runtimeStateOwner.preparing();
            }

            @Override
            public String errorMessage() {
                return runtimeStateOwner.errorMessage();
            }

            @Override
            public boolean shuffleEnabled() {
                return runtimeStateOwner.shuffleEnabled();
            }

            @Override
            public int repeatMode() {
                return runtimeStateOwner.repeatMode();
            }

            @Override
            public float playbackSpeed() {
                return runtimeStateOwner.playbackSpeed();
            }

            @Override
            public float appVolume() {
                return runtimeStateOwner.appVolume();
            }
        };
    }

    static VisualizationProvider fromVisualizationAnalyzer(
            PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer
    ) {
        PlaybackVisualizationAnalyzer visualizationAnalyzer =
                Objects.requireNonNull(playbackVisualizationAnalyzer, "playbackVisualizationAnalyzer");
        return new VisualizationProvider() {
            @Override
            public boolean shouldDeferPlaybackVisualization() {
                return visualizationAnalyzer.shouldDeferPlaybackVisualization();
            }

            @Override
            public PlaybackWaveformSnapshot waveformSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                return visualizationAnalyzer.waveformSnapshot(track, durationMs, deferGeneration);
            }

            @Override
            public PlaybackSpectrumSnapshot spectrumSnapshot(
                    Track track,
                    long durationMs,
                    boolean deferGeneration
            ) {
                return visualizationAnalyzer.spectrumSnapshot(track, durationMs, deferGeneration);
            }
        };
    }

    PlaybackStateSnapshot snapshot() {
        PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateSnapshot();
        Track track = queueSnapshot.getCurrentTrack();
        int currentIndex = queueSnapshot.getCurrentIndex();
        int queueSize = queueSnapshot.getQueueSize();
        long positionMs = playbackPositionProvider.positionMs();
        long playbackDurationMs = playbackPositionProvider.durationMs();
        boolean playing = playbackPositionProvider.isPlaying();
        long durationMs = track == null ? 0L : Math.max(track.durationMs, playbackDurationMs);
        boolean deferVisualGeneration = visualizationProvider.shouldDeferPlaybackVisualization();
        PlaybackWaveformSnapshot waveform =
                visualizationProvider.waveformSnapshot(track, durationMs, deferVisualGeneration);
        PlaybackSpectrumSnapshot spectrum =
                visualizationProvider.spectrumSnapshot(track, durationMs, deferVisualGeneration);
        return new PlaybackStateSnapshot(
                track,
                currentIndex,
                queueSize,
                positionMs,
                durationMs,
                playing,
                runtimeStateProvider.preparing(),
                runtimeStateProvider.errorMessage(),
                runtimeStateProvider.shuffleEnabled(),
                runtimeStateProvider.repeatMode(),
                runtimeStateProvider.playbackSpeed(),
                runtimeStateProvider.appVolume(),
                sleepTimerProvider.getAsLong(),
                waveform,
                spectrum,
                playing ? (float) realtimeBeatProvider.getAsDouble() : 0f
        );
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }
}
