package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import app.yukine.model.Track;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackNotificationArtworkSourceTest {
    @Test
    public void returnsNullUntilArtworkSourceIsAvailable() {
        MutableArtworkSourceProvider provider = new MutableArtworkSourceProvider();
        PlaybackNotificationArtworkSource source =
                PlaybackNotificationArtworkSource.fromSupplier(provider::artworkSource);

        assertNull(source.notificationArtworkFor(track()));
        assertNull(source.notificationArtworkDataFor(track()));
    }

    @Test
    public void delegatesArtworkRequestsToCurrentSource() {
        MutableArtworkSourceProvider provider = new MutableArtworkSourceProvider();
        PlaybackNotificationArtworkSource source =
                PlaybackNotificationArtworkSource.fromSupplier(provider::artworkSource);
        byte[] artworkData = new byte[] {1, 2, 3};
        Track track = track();
        provider.source = new FakeArtworkSource(track, artworkData);

        assertNull(source.notificationArtworkFor(track));
        assertSame(artworkData, source.notificationArtworkDataFor(track));
    }

    private static Track track() {
        return new Track(
                11L,
                "Track",
                "Artist",
                "Album",
                1000L,
                Uri.parse("content://track/11"),
                "file:11",
                0L,
                Uri.parse("content://art/11")
        );
    }

    private static final class MutableArtworkSourceProvider {
        private PlaybackNotificationArtworkSource source;

        public PlaybackNotificationArtworkSource artworkSource() {
            return source;
        }
    }

    private static final class FakeArtworkSource implements PlaybackNotificationArtworkSource {
        private final Track expectedTrack;
        private final byte[] artworkData;

        FakeArtworkSource(Track expectedTrack, byte[] artworkData) {
            this.expectedTrack = expectedTrack;
            this.artworkData = artworkData;
        }

        @Override
        public android.graphics.Bitmap notificationArtworkFor(Track track) {
            if (track != expectedTrack) {
                throw new AssertionError("Unexpected artwork track");
            }
            return null;
        }

        @Override
        public byte[] notificationArtworkDataFor(Track track) {
            if (track != expectedTrack) {
                throw new AssertionError("Unexpected artwork data track");
            }
            return artworkData;
        }
    }
}
