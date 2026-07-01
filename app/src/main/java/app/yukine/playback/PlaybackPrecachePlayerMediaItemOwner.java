package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

final class PlaybackPrecachePlayerMediaItemOwner
        implements PlaybackPrecacheStateOwner.PlayerMediaItemProvider {
    interface PlayerProvider {
        Player player();
    }

    interface PlayerOperations {
        int playbackState();

        int mediaItemCount();

        MediaItem currentMediaItem();
    }

    interface PlayerOperationsProvider {
        PlayerOperations playerOperations();
    }

    private final PlayerOperationsProvider playerOperationsProvider;

    static PlaybackPrecachePlayerMediaItemOwner fromPlayerProvider(PlayerProvider playerProvider) {
        return new PlaybackPrecachePlayerMediaItemOwner(
                () -> {
                    Player player = playerProvider == null ? null : playerProvider.player();
                    return player == null ? null : new Media3PlayerOperations(player);
                }
        );
    }

    PlaybackPrecachePlayerMediaItemOwner(PlayerOperationsProvider playerOperationsProvider) {
        this.playerOperationsProvider = playerOperationsProvider;
    }

    @Override
    public MediaItem currentPlayerMediaItem() {
        PlayerOperations playerOperations = playerOperations();
        if (playerOperations == null) {
            return null;
        }
        try {
            if (playerOperations.playbackState() == Player.STATE_IDLE
                    || playerOperations.mediaItemCount() <= 0) {
                return null;
            }
            return playerOperations.currentMediaItem();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private PlayerOperations playerOperations() {
        return playerOperationsProvider == null ? null : playerOperationsProvider.playerOperations();
    }

    private static final class Media3PlayerOperations implements PlayerOperations {
        private final Player player;

        private Media3PlayerOperations(Player player) {
            this.player = player;
        }

        @Override
        public int playbackState() {
            return player.getPlaybackState();
        }

        @Override
        public int mediaItemCount() {
            return player.getMediaItemCount();
        }

        @Override
        public MediaItem currentMediaItem() {
            return player.getCurrentMediaItem();
        }
    }
}
