package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    private final BooleanSupplier appVisibilitySupplier;
    private final PlaybackQueueManager playbackQueueManager;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackLyricsStateOwner(
            BooleanSupplier appVisibilitySupplier,
            PlaybackQueueManager playbackQueueManager,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        this.appVisibilitySupplier = appVisibilitySupplier;
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilitySupplier.getAsBoolean();
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider != null && playingStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }
}
