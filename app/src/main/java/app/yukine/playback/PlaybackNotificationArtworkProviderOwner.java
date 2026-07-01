package app.yukine.playback;

import android.graphics.Bitmap;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.Supplier;

final class PlaybackNotificationArtworkProviderOwner
        implements PlaybackNotificationManager.ArtworkProvider, PlaybackStatePublisher.ArtworkProvider {
    private final Supplier<PlaybackNotificationArtworkSource> artworkSourceProvider;

    PlaybackNotificationArtworkProviderOwner(Supplier<PlaybackNotificationArtworkSource> artworkSourceProvider) {
        this.artworkSourceProvider = artworkSourceProvider;
    }

    @Override
    public Bitmap notificationArtworkFor(Track track) {
        PlaybackNotificationArtworkSource source = artworkSource();
        return source == null ? null : source.notificationArtworkFor(track);
    }

    @Override
    public byte[] notificationArtworkDataFor(Track track) {
        PlaybackNotificationArtworkSource source = artworkSource();
        return source == null ? null : source.notificationArtworkDataFor(track);
    }

    private PlaybackNotificationArtworkSource artworkSource() {
        return artworkSourceProvider == null ? null : artworkSourceProvider.get();
    }
}
