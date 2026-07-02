package app.yukine.playback;

import java.util.function.BooleanSupplier;

final class PlaybackVisualizationStateOwner implements PlaybackVisualizationAnalyzer.StateProvider {
    interface BufferedProgressProvider {
        float bufferedProgress(long durationMs);
    }

    private final BooleanSupplier appVisibilityProvider;
    private final BufferedProgressProvider bufferedProgressProvider;
    private final Runnable statePublisher;

    PlaybackVisualizationStateOwner(
            BooleanSupplier appVisibilityProvider,
            BufferedProgressProvider bufferedProgressProvider,
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
        return bufferedProgressProvider.bufferedProgress(durationMs);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }
}
