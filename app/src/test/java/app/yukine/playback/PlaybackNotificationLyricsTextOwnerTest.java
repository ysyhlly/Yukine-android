package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import app.yukine.model.Track;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.playback.manager.LyricsPublisher;

import static org.junit.Assert.assertEquals;

public class PlaybackNotificationLyricsTextOwnerTest {
    @Test
    public void returnsEmptyTextUntilLyricsPublisherIsAvailable() {
        MutableLyricsPublisherSource provider = new MutableLyricsPublisherSource();
        PlaybackNotificationLyricsTextOwner owner =
                new PlaybackNotificationLyricsTextOwner(provider::lyricsPublisher);

        assertEquals("", owner.currentNotificationLyric(track()));
        assertEquals("", owner.sanitizeNotificationLyric("line"));
    }

    @Test
    public void delegatesNotificationLyricsToCurrentPublisher() {
        MutableLyricsPublisherSource provider = new MutableLyricsPublisherSource();
        PlaybackNotificationLyricsTextOwner owner =
                new PlaybackNotificationLyricsTextOwner(provider::lyricsPublisher);
        provider.publisher = new FakeLyricsPublisher();
        Track track = track();

        assertEquals("lyric:9", owner.currentNotificationLyric(track));
        assertEquals("clean:line", owner.sanitizeNotificationLyric("line"));
    }

    private static Track track() {
        return new Track(9L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:9");
    }

    private static final class MutableLyricsPublisherSource {
        private LyricsPublisher publisher;

        public LyricsPublisher lyricsPublisher() {
            return publisher;
        }
    }

    private static final class FakeLyricsPublisher implements LyricsPublisher {
        @Override
        public void bind() {
        }

        @Override
        public void release() {
        }

        @Override
        public void setStatusBarLyricsEnabled(boolean enabled) {
        }

        @Override
        public void syncFloatingLyricsPlaybackState(PlaybackStateSnapshot snapshot) {
        }

        @Override
        public String notificationLyricText(Track track) {
            return "lyric:" + track.id;
        }

        @Override
        public String sanitizeNotificationLyric(String value) {
            return "clean:" + value;
        }
    }
}
