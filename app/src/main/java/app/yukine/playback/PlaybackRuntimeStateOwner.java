package app.yukine.playback;

import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackRuntimeStateOwner implements PlaybackRuntimeStateManager.StateProvider {
    private final Supplier<ExoPlayer> playerSupplier;
    private final BooleanSupplier mirroredQueueSupplier;
    private final Supplier<Track> currentTrackSupplier;

    PlaybackRuntimeStateOwner(
            Supplier<ExoPlayer> playerSupplier,
            BooleanSupplier mirroredQueueSupplier,
            Supplier<Track> currentTrackSupplier
    ) {
        this.playerSupplier = playerSupplier;
        this.mirroredQueueSupplier = mirroredQueueSupplier;
        this.currentTrackSupplier = currentTrackSupplier;
    }

    @Override
    public ExoPlayer player() {
        return playerSupplier.get();
    }

    @Override
    public boolean playerMirrorsQueue() {
        return mirroredQueueSupplier.getAsBoolean();
    }

    @Override
    public Track currentTrack() {
        return currentTrackSupplier.get();
    }
}
