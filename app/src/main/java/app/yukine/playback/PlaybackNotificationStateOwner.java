package app.yukine.playback;

import android.media.session.MediaSession;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider {
    private final PlaybackQueueStateOwner queueStateOwner;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;
    private final Predicate<Track> favoriteStateProvider;
    private final Supplier<MediaSession.Token> sessionTokenSupplier;

    PlaybackNotificationStateOwner(
            PlaybackQueueStateOwner queueStateOwner,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider,
            Predicate<Track> favoriteStateProvider,
            Supplier<MediaSession.Token> sessionTokenSupplier
    ) {
        this.queueStateOwner = queueStateOwner;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenSupplier = sessionTokenSupplier;
    }

    @Override
    public boolean isQueueEmpty() {
        PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateOwner.queueStateSnapshot();
        return queueSnapshot.isQueueEmpty();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider != null && playingStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }

    @Override
    public Track currentTrack() {
        return queueStateOwner == null ? null : queueStateOwner.currentTrack();
    }

    @Override
    public boolean isFavorite(Track track) {
        return favoriteStateProvider.test(track);
    }

    @Override
    public MediaSession.Token playbackSessionPlatformToken() {
        return sessionTokenSupplier.get();
    }
}
