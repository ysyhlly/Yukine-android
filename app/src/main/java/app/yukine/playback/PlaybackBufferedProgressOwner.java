package app.yukine.playback;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;

final class PlaybackBufferedProgressOwner
        implements LongToDoubleFunction {
    private final LongSupplier playbackPositionProvider;
    private final LongSupplier bufferedPositionProvider;

    PlaybackBufferedProgressOwner(
            LongSupplier playbackPositionProvider,
            LongSupplier bufferedPositionProvider
    ) {
        this.playbackPositionProvider = Objects.requireNonNull(
                playbackPositionProvider,
                "playbackPositionProvider"
        );
        this.bufferedPositionProvider = Objects.requireNonNull(
                bufferedPositionProvider,
                "bufferedPositionProvider"
        );
    }

    float bufferedProgress(long durationMs) {
        return (float) applyAsDouble(durationMs);
    }

    @Override
    public double applyAsDouble(long durationMs) {
        if (durationMs <= 0L) {
            return 0.0;
        }
        try {
            long positionMs = playbackPositionProvider.getAsLong();
            long bufferedPositionMs = bufferedPositionProvider.getAsLong();
            long bufferedMs = Math.max(positionMs, bufferedPositionMs);
            return Math.max(0.0, Math.min(1.0, bufferedMs / (double) durationMs));
        } catch (IllegalStateException ignored) {
            return 0.0;
        }
    }
}
