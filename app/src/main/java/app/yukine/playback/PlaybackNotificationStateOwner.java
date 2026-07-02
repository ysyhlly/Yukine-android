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

    private final BooleanSupplier queueEmptySupplier;
    private final Supplier<Track> currentTrackSupplier;
    private final PlaybackStateProvider playbackStateProvider;
    private final Predicate<Track> favoriteStateProvider;
    private final Supplier<MediaSession.Token> sessionTokenSupplier;

    PlaybackNotificationStateOwner(
            BooleanSupplier queueEmptySupplier,
            Supplier<Track> currentTrackSupplier,
            PlaybackStateProvider playbackStateProvider,
            Predicate<Track> favoriteStateProvider,
            Supplier<MediaSession.Token> sessionTokenSupplier
    ) {
        this.queueEmptySupplier = queueEmptySupplier;
        this.currentTrackSupplier = currentTrackSupplier;
        this.playbackStateProvider = playbackStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenSupplier = sessionTokenSupplier;
    }

    @Override
    public boolean isQueueEmpty() {
        return queueEmptySupplier == null || queueEmptySupplier.getAsBoolean();
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
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
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
