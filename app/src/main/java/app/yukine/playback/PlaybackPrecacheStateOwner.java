package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class PlaybackPrecacheStateOwner implements PlaybackPrecacheManager.StateProvider {
    private final Supplier<MediaItem> playerMediaItemSupplier;

    PlaybackPrecacheStateOwner(
            Supplier<MediaItem> playerMediaItemSupplier
    ) {
        this.playerMediaItemSupplier = playerMediaItemSupplier;
    }

    static Supplier<MediaItem> playerMediaItemSupplierFromPlayerSupplier(Supplier<Player> playerSupplier) {
        return () -> {
            Player player = playerSupplier == null ? null : playerSupplier.get();
            if (player == null) {
                return null;
            }
            return currentMediaItemForPlayerState(
                    player::getPlaybackState,
                    player::getMediaItemCount,
                    player::getCurrentMediaItem
            );
        };
    }

    static Supplier<MediaItem> playerMediaItemSupplierFromStateSuppliers(
            IntSupplier playbackStateSupplier,
            IntSupplier mediaItemCountSupplier,
            Supplier<MediaItem> currentMediaItemSupplier
    ) {
        return () -> currentMediaItemForPlayerState(
                playbackStateSupplier,
                mediaItemCountSupplier,
                currentMediaItemSupplier
        );
    }

    private static MediaItem currentMediaItemForPlayerState(
            IntSupplier playbackStateSupplier,
            IntSupplier mediaItemCountSupplier,
            Supplier<MediaItem> currentMediaItemSupplier
    ) {
        if (playbackStateSupplier == null
                || mediaItemCountSupplier == null
                || currentMediaItemSupplier == null) {
            return null;
        }
        try {
            if (playbackStateSupplier.getAsInt() == Player.STATE_IDLE
                    || mediaItemCountSupplier.getAsInt() <= 0) {
                return null;
            }
            return currentMediaItemSupplier.get();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Override
    public MediaItem currentPlayerMediaItem() {
        return playerMediaItemSupplier == null ? null : playerMediaItemSupplier.get();
    }

}
