package app.yukine.playback;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import org.junit.Test;

public class PlaybackPrecachePlayerMediaItemOwnerTest {
    @Test
    public void returnsCurrentMediaItemForActivePlayerWithItems() {
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/song.mp3");
        PlaybackPrecachePlayerMediaItemOwner owner = new PlaybackPrecachePlayerMediaItemOwner(
                () -> new FakePlayerOperations(Player.STATE_READY, 1, mediaItem)
        );

        assertSame(mediaItem, owner.currentPlayerMediaItem());
    }

    @Test
    public void returnsNullForMissingIdleOrEmptyPlayer() {
        assertNull(new PlaybackPrecachePlayerMediaItemOwner(null).currentPlayerMediaItem());
        assertNull(new PlaybackPrecachePlayerMediaItemOwner(
                () -> new FakePlayerOperations(Player.STATE_IDLE, 1, MediaItem.fromUri("https://example.test/idle.mp3"))
        ).currentPlayerMediaItem());
        assertNull(new PlaybackPrecachePlayerMediaItemOwner(
                () -> new FakePlayerOperations(Player.STATE_READY, 0, MediaItem.fromUri("https://example.test/empty.mp3"))
        ).currentPlayerMediaItem());
    }

    @Test
    public void returnsNullWhenPlayerStateCannotBeRead() {
        PlaybackPrecachePlayerMediaItemOwner owner = new PlaybackPrecachePlayerMediaItemOwner(
                () -> new ThrowingPlayerOperations()
        );

        assertNull(owner.currentPlayerMediaItem());
    }

    private static class FakePlayerOperations implements PlaybackPrecachePlayerMediaItemOwner.PlayerOperations {
        private final int playbackState;
        private final int mediaItemCount;
        private final MediaItem currentMediaItem;

        private FakePlayerOperations(int playbackState, int mediaItemCount, MediaItem currentMediaItem) {
            this.playbackState = playbackState;
            this.mediaItemCount = mediaItemCount;
            this.currentMediaItem = currentMediaItem;
        }

        @Override
        public int playbackState() {
            return playbackState;
        }

        @Override
        public int mediaItemCount() {
            return mediaItemCount;
        }

        @Override
        public MediaItem currentMediaItem() {
            return currentMediaItem;
        }
    }

    private static final class ThrowingPlayerOperations extends FakePlayerOperations {
        private ThrowingPlayerOperations() {
            super(Player.STATE_READY, 1, MediaItem.fromUri("https://example.test/throw.mp3"));
        }

        @Override
        public int playbackState() {
            throw new IllegalStateException("player released");
        }
    }
}
