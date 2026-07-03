package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;

import java.util.function.BooleanSupplier;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    private final BooleanSupplier appVisibilitySupplier;
    private final PlaybackQueueStateOwner queueStateOwner;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackLyricsStateOwner(
            BooleanSupplier appVisibilitySupplier,
            PlaybackQueueStateOwner queueStateOwner,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        this.appVisibilitySupplier = appVisibilitySupplier;
        this.queueStateOwner = queueStateOwner;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilitySupplier.getAsBoolean();
    }

    @Override
    public Track currentTrack() {
        return queueStateOwner == null ? null : queueStateOwner.queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider != null && playingStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }
}
