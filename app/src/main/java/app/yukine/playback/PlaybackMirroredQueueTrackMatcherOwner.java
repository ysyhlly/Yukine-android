package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackMirroredQueueTrackMatcherOwner
        implements PlaybackQueueManager.QueueTrackMatcher {
    interface PlayerProvider {
        Player player();
    }

    interface PlayerMediaItemProvider {
        MediaItem mediaItemAt(int index);
    }

    interface TrackMediaItemMatcher {
        boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, Track track);
    }

    private final PlayerMediaItemProvider playerMediaItemProvider;
    private final TrackMediaItemMatcher trackMediaItemMatcher;

    PlaybackMirroredQueueTrackMatcherOwner(
            PlayerMediaItemProvider playerMediaItemProvider,
            TrackMediaItemMatcher trackMediaItemMatcher
    ) {
        this.playerMediaItemProvider = playerMediaItemProvider;
        this.trackMediaItemMatcher = trackMediaItemMatcher;
    }

    static PlaybackMirroredQueueTrackMatcherOwner fromPlayerProvider(
            PlayerProvider playerProvider,
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        return new PlaybackMirroredQueueTrackMatcherOwner(
                index -> {
                    Player player = playerProvider == null ? null : playerProvider.player();
                    return player == null ? null : player.getMediaItemAt(index);
                },
                mediaSourceProvider == null
                        ? null
                        : mediaSourceProvider::mediaItemMatchesTrackForReuse
        );
    }

    @Override
    public boolean matches(int index, Track track) {
        if (playerMediaItemProvider == null || trackMediaItemMatcher == null) {
            return false;
        }
        try {
            MediaItem mediaItem = playerMediaItemProvider.mediaItemAt(index);
            return trackMediaItemMatcher.mediaItemMatchesTrackForReuse(mediaItem, track);
        } catch (IndexOutOfBoundsException | IllegalStateException error) {
            return false;
        }
    }
}
