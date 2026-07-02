package app.yukine.playback;

import android.media.session.MediaSession;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider {
    interface PlaybackStateProvider {
        boolean isPlaying();
        boolean isPreparing();
    }

    static PlaybackStateProvider playbackStateProviderFromPlaybackState(
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        return new PlaybackStateProvider() {
            @Override
            public boolean isPlaying() {
                return playingStateProvider != null && playingStateProvider.getAsBoolean();
            }

            @Override
            public boolean isPreparing() {
                return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
            }
        };
    }

    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;
    private final PlaybackStateProvider playbackStateProvider;
    private final Predicate<Track> favoriteStateProvider;
    private final Supplier<MediaSession.Token> sessionTokenSupplier;

    PlaybackNotificationStateOwner(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier,
            PlaybackStateProvider playbackStateProvider,
            Predicate<Track> favoriteStateProvider,
            Supplier<MediaSession.Token> sessionTokenSupplier
    ) {
        this.queueStateSupplier = queueStateSupplier;
        this.playbackStateProvider = playbackStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenSupplier = sessionTokenSupplier;
    }

    @Override
    public boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return playbackStateProvider.isPreparing();
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public boolean isFavorite(Track track) {
        return favoriteStateProvider.test(track);
    }

    @Override
    public MediaSession.Token playbackSessionPlatformToken() {
        return sessionTokenSupplier.get();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSupplier == null
                ? null
                : queueStateSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
