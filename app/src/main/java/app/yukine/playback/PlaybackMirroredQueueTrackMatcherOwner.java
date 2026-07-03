package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Supplier;

final class PlaybackMirroredQueueTrackMatcherOwner {
    private final IntFunction<MediaItem> playerMediaItemProvider;
    private final BiPredicate<MediaItem, Track> trackMediaItemMatcher;

    PlaybackMirroredQueueTrackMatcherOwner(
            IntFunction<MediaItem> playerMediaItemProvider,
            BiPredicate<MediaItem, Track> trackMediaItemMatcher
    ) {
        this.playerMediaItemProvider = playerMediaItemProvider;
        this.trackMediaItemMatcher = trackMediaItemMatcher;
    }

    PlaybackMirroredQueueTrackMatcherOwner(
            Supplier<Player> playerProvider,
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        this(
                index -> {
                    Player player = playerProvider == null ? null : playerProvider.get();
                    return player == null ? null : player.getMediaItemAt(index);
                },
                (mediaItem, track) -> mediaSourceProvider != null
                        && mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track)
        );
    }

    boolean matches(int index, Track track) {
        if (playerMediaItemProvider == null || trackMediaItemMatcher == null) {
            return false;
        }
        try {
            MediaItem mediaItem = playerMediaItemProvider.apply(index);
            return trackMediaItemMatcher.test(mediaItem, track);
        } catch (IndexOutOfBoundsException | IllegalStateException error) {
            return false;
        }
    }
}
