package app.yukine.playback;

import java.util.function.LongSupplier;

final class PlaybackBufferedProgressOwner
        implements PlaybackVisualizationStateOwner.BufferedProgressProvider {
    private final LongSupplier playbackPositionProvider;
    private final LongSupplier bufferedPositionProvider;

    PlaybackBufferedProgressOwner(
            LongSupplier playbackPositionProvider,
            LongSupplier bufferedPositionProvider
    ) {
        this.playbackPositionProvider = playbackPositionProvider;
        this.bufferedPositionProvider = bufferedPositionProvider;
    }

    @Override
    public float bufferedProgress(long durationMs) {
        if (durationMs <= 0L) {
            return 0.0f;
        }
        try {
            long positionMs = playbackPositionProvider == null ? 0L : playbackPositionProvider.getAsLong();
            long bufferedPositionMs = bufferedPositionProvider == null ? 0L : bufferedPositionProvider.getAsLong();
            long bufferedMs = Math.max(positionMs, bufferedPositionMs);
            return Math.max(0.0f, Math.min(1.0f, bufferedMs / (float) durationMs));
        } catch (IllegalStateException ignored) {
            return 0.0f;
        }
    }
}
