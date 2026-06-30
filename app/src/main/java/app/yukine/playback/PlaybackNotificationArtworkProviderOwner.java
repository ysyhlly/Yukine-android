package app.yukine.playback;

import android.graphics.Bitmap;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;

final class PlaybackNotificationArtworkProviderOwner implements PlaybackNotificationManager.ArtworkProvider {
    interface ArtworkManagerProvider {
        PlaybackNotificationArtworkSource artworkSource();
    }

    private final ArtworkManagerProvider artworkManagerProvider;

    PlaybackNotificationArtworkProviderOwner(ArtworkManagerProvider artworkManagerProvider) {
        this.artworkManagerProvider = artworkManagerProvider;
    }

    @Override
    public Bitmap notificationArtworkFor(Track track) {
        PlaybackNotificationArtworkSource source = artworkManagerProvider.artworkSource();
        return source == null ? null : source.notificationArtworkFor(track);
    }

    @Override
    public byte[] notificationArtworkDataFor(Track track) {
        PlaybackNotificationArtworkSource source = artworkManagerProvider.artworkSource();
        return source == null ? null : source.notificationArtworkDataFor(track);
    }
}
