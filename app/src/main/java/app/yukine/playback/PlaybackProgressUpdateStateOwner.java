package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

import java.util.function.BooleanSupplier;

final class PlaybackProgressUpdateStateOwner implements PlaybackProgressUpdateManager.StateProvider {
    private final BooleanSupplier playbackStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackProgressUpdateStateOwner(
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
