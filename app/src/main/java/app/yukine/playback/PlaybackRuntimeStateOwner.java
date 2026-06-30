package app.yukine.playback;

import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

final class PlaybackRuntimeStateOwner implements PlaybackRuntimeStateManager.StateProvider {
    interface PlayerProvider {
        ExoPlayer player();
    }

    interface MirroredQueueProvider {
        boolean playerMirrorsQueue();
    }

    interface CurrentTrackProvider {
        Track currentTrack();
    }

    private final PlayerProvider playerProvider;
    private final MirroredQueueProvider mirroredQueueProvider;
    private final CurrentTrackProvider currentTrackProvider;

    PlaybackRuntimeStateOwner(
            PlayerProvider playerProvider,
            MirroredQueueProvider mirroredQueueProvider,
            CurrentTrackProvider currentTrackProvider
    ) {
        this.playerProvider = playerProvider;
        this.mirroredQueueProvider = mirroredQueueProvider;
        this.currentTrackProvider = currentTrackProvider;
    }

    @Override
    public ExoPlayer player() {
        return playerProvider.player();
    }

    @Override
    public boolean playerMirrorsQueue() {
        return mirroredQueueProvider.playerMirrorsQueue();
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }
}
