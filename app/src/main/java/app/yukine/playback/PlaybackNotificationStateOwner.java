package app.yukine.playback;

import android.media.session.MediaSession;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider {
    interface PlaybackStateProvider {
        boolean isPlaying();
        boolean isPreparing();
        Track currentTrack();
    }

    private final BooleanSupplier queueEmptySupplier;
    private final PlaybackStateProvider playbackStateProvider;
    private final Predicate<Track> favoriteStateProvider;
    private final Supplier<MediaSession.Token> sessionTokenSupplier;

    PlaybackNotificationStateOwner(
            BooleanSupplier queueEmptySupplier,
            PlaybackStateProvider playbackStateProvider,
            Predicate<Track> favoriteStateProvider,
            Supplier<MediaSession.Token> sessionTokenSupplier
    ) {
        this.queueEmptySupplier = queueEmptySupplier;
        this.playbackStateProvider = playbackStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenSupplier = sessionTokenSupplier;
    }

    @Override
    public boolean isQueueEmpty() {
        return queueEmptySupplier.getAsBoolean();
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
        return playbackStateProvider.currentTrack();
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
