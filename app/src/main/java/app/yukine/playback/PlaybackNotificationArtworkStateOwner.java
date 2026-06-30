package app.yukine.playback;

import app.yukine.model.Track;

final class PlaybackNotificationArtworkStateOwner implements PlaybackNotificationArtworkManager.StateProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    private final CurrentTrackProvider currentTrackProvider;

    PlaybackNotificationArtworkStateOwner(CurrentTrackProvider currentTrackProvider) {
        this.currentTrackProvider = currentTrackProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }
}
