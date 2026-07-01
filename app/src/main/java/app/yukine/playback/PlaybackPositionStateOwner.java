package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackPositionManager;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class PlaybackPositionStateOwner implements PlaybackPositionManager.StateProvider {
    private final Supplier<Track> currentTrackSupplier;
    private final LongSupplier playbackPositionSupplier;

    PlaybackPositionStateOwner(
            Supplier<Track> currentTrackSupplier,
            LongSupplier playbackPositionSupplier
    ) {
        this.currentTrackSupplier = currentTrackSupplier;
        this.playbackPositionSupplier = playbackPositionSupplier;
    }

    @Override
    public Track currentTrack() {
        return currentTrackSupplier.get();
    }

    @Override
    public long positionMs() {
        return playbackPositionSupplier.getAsLong();
    }
}
