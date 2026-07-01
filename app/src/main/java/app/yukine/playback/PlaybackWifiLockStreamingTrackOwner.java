package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackWifiLockManager;

final class PlaybackWifiLockStreamingTrackOwner
        implements PlaybackWifiLockManager.StreamingTrackProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    private final CurrentTrackProvider currentTrackProvider;

    PlaybackWifiLockStreamingTrackOwner(CurrentTrackProvider currentTrackProvider) {
        this.currentTrackProvider = currentTrackProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider == null ? null : currentTrackProvider.currentTrack();
    }
}
