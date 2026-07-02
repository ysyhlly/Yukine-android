package app.yukine.playback;

import java.util.function.BooleanSupplier;
import java.util.function.LongToDoubleFunction;

final class PlaybackVisualizationStateOwner implements PlaybackVisualizationAnalyzer.StateProvider {
    private final BooleanSupplier appVisibilityProvider;
    private final LongToDoubleFunction bufferedProgressProvider;
    private final Runnable statePublisher;

    PlaybackVisualizationStateOwner(
            BooleanSupplier appVisibilityProvider,
            LongToDoubleFunction bufferedProgressProvider,
            Runnable statePublisher
    ) {
        this.appVisibilityProvider = appVisibilityProvider;
        this.bufferedProgressProvider = bufferedProgressProvider;
        this.statePublisher = statePublisher;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilityProvider.getAsBoolean();
    }

    @Override
    public float bufferedProgress(long durationMs) {
        return bufferedProgressProvider == null ? 0f : (float) bufferedProgressProvider.applyAsDouble(durationMs);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }
}
