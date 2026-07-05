package app.yukine.playback;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import org.junit.Test;

import java.util.function.Supplier;

public class PlaybackPrecacheStateOwnerPlayerMediaItemTest {
    @Test
    public void returnsCurrentMediaItemForActivePlayerWithItems() {
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/song.mp3");
        Supplier<MediaItem> supplier =
                supplierFromState(new FakePlayerState(Player.STATE_READY, 1, mediaItem));

        assertSame(mediaItem, supplier.get());
    }

    @Test
    public void returnsNullForMissingIdleOrEmptyPlayer() {
        assertNull(PlaybackPrecacheStateOwner.playerMediaItemSupplierFromStateSuppliers(null, null, null)
                .get());
        assertNull(supplierFromState(new FakePlayerState(
                Player.STATE_IDLE,
                1,
                MediaItem.fromUri("https://example.test/idle.mp3")
        )).get());
        assertNull(supplierFromState(new FakePlayerState(
                Player.STATE_READY,
                0,
                MediaItem.fromUri("https://example.test/empty.mp3")
        )).get());
    }

    @Test
    public void returnsNullWhenPlayerStateCannotBeRead() {
        Supplier<MediaItem> supplier =
                supplierFromState(new ThrowingPlayerState());

        assertNull(supplier.get());
    }

    private static Supplier<MediaItem> supplierFromState(FakePlayerState state) {
        return PlaybackPrecacheStateOwner.playerMediaItemSupplierFromStateSuppliers(
                state::playbackState,
                state::mediaItemCount,
                state::currentMediaItem
        );
    }

    private static class FakePlayerState {
        private final int playbackState;
        private final int mediaItemCount;
        private final MediaItem currentMediaItem;

        private FakePlayerState(int playbackState, int mediaItemCount, MediaItem currentMediaItem) {
            this.playbackState = playbackState;
            this.mediaItemCount = mediaItemCount;
            this.currentMediaItem = currentMediaItem;
        }

        public int playbackState() {
            return playbackState;
        }

        public int mediaItemCount() {
            return mediaItemCount;
        }

        public MediaItem currentMediaItem() {
            return currentMediaItem;
        }
    }

    private static final class ThrowingPlayerState extends FakePlayerState {
        private ThrowingPlayerState() {
            super(Player.STATE_READY, 1, MediaItem.fromUri("https://example.test/throw.mp3"));
        }

        public int playbackState() {
            throw new IllegalStateException("player released");
        }
    }
}
