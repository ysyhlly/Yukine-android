package app.yukine.playback;

import android.graphics.Bitmap;

import app.yukine.model.Track;

import java.util.function.Supplier;

public interface PlaybackNotificationArtworkSource extends PlaybackStatePublisher.ArtworkProvider {
    static PlaybackNotificationArtworkSource fromSupplier(
            Supplier<PlaybackNotificationArtworkSource> artworkSourceProvider
    ) {
        return new PlaybackNotificationArtworkSource() {
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
        };
    }

    @Override
    Bitmap notificationArtworkFor(Track track);

    byte[] notificationArtworkDataFor(Track track);
}
