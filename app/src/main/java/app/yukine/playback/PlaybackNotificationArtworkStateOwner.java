package app.yukine.playback;

import app.yukine.model.Track;

import java.util.function.Supplier;

final class PlaybackNotificationArtworkStateOwner implements PlaybackNotificationArtworkManager.StateProvider {
    private final Supplier<Track> currentTrackProvider;

    PlaybackNotificationArtworkStateOwner(Supplier<Track> currentTrackProvider) {
        this.currentTrackProvider = currentTrackProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider == null ? null : currentTrackProvider.get();
    }
}
