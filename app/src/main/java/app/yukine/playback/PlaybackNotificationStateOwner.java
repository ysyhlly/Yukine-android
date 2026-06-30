package app.yukine.playback;

import android.media.session.MediaSession;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackNotificationManager;

final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider {
    interface QueueStateProvider {
        boolean isQueueEmpty();
    }

    interface PlaybackStateProvider {
        boolean isPlaying();
        boolean isPreparing();
        Track currentTrack();
    }

    interface FavoriteStateProvider {
        boolean isFavorite(Track track);
    }

    interface SessionTokenProvider {
        MediaSession.Token playbackSessionPlatformToken();
    }

    private final QueueStateProvider queueStateProvider;
    private final PlaybackStateProvider playbackStateProvider;
    private final FavoriteStateProvider favoriteStateProvider;
    private final SessionTokenProvider sessionTokenProvider;

    PlaybackNotificationStateOwner(
            QueueStateProvider queueStateProvider,
            PlaybackStateProvider playbackStateProvider,
            FavoriteStateProvider favoriteStateProvider,
            SessionTokenProvider sessionTokenProvider
    ) {
        this.queueStateProvider = queueStateProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.favoriteStateProvider = favoriteStateProvider;
        this.sessionTokenProvider = sessionTokenProvider;
    }

    @Override
    public boolean isQueueEmpty() {
        return queueStateProvider.isQueueEmpty();
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
        return favoriteStateProvider.isFavorite(track);
    }

    @Override
    public MediaSession.Token playbackSessionPlatformToken() {
        return sessionTokenProvider.playbackSessionPlatformToken();
    }
}
