package app.yukine.playback;

final class PlaybackVisualizationStateOwner implements PlaybackVisualizationAnalyzer.StateProvider {
    interface AppVisibilityProvider {
        boolean isAppVisible();
    }

    interface BufferedProgressProvider {
        float bufferedProgress(long durationMs);
    }

    interface StatePublisher {
        void publishState();
    }

    private final AppVisibilityProvider appVisibilityProvider;
    private final BufferedProgressProvider bufferedProgressProvider;
    private final StatePublisher statePublisher;

    PlaybackVisualizationStateOwner(
            AppVisibilityProvider appVisibilityProvider,
            BufferedProgressProvider bufferedProgressProvider,
            StatePublisher statePublisher
    ) {
        this.appVisibilityProvider = appVisibilityProvider;
        this.bufferedProgressProvider = bufferedProgressProvider;
        this.statePublisher = statePublisher;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilityProvider.isAppVisible();
    }

    @Override
    public float bufferedProgress(long durationMs) {
        return bufferedProgressProvider.bufferedProgress(durationMs);
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }
}
