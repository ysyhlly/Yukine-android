package app.yukine.playback;

import java.util.function.BooleanSupplier;

final class PlaybackShutdownPlaybackStateOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider {
    private final BooleanSupplier playbackStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackShutdownPlaybackStateOwner(
            BooleanSupplier playbackStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider != null && playbackStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }
}
