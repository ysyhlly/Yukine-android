package app.yukine.playback;

import android.media.session.MediaSession;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider {
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;
    private final Predicate<Track> favoriteStateProvider;
    private final Supplier<MediaSession.Token> sessionTokenSupplier;

    PlaybackNotificationStateOwner(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider,
            Predicate<Track> favoriteStateProvider,
            Supplier<MediaSession.Token> sessionTokenSupplier
    ) {
        this.queueStateSnapshotSupplier = queueStateSnapshotSupplier;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenSupplier = sessionTokenSupplier;
    }

    @Override
    public boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
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
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
