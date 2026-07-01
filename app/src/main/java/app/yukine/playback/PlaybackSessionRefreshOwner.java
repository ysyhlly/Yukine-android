package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackSessionManager;

import java.util.function.Supplier;

final class PlaybackSessionRefreshOwner
        implements PlaybackNotificationArtworkBridgeOwner.SessionRefresher,
        PlaybackNotificationManager.SessionRefresher {
    interface SessionOperations {
        void refreshPlayer();
    }

    private final Supplier<SessionOperations> sessionOperationsSupplier;

    PlaybackSessionRefreshOwner(Supplier<SessionOperations> sessionOperationsSupplier) {
        this.sessionOperationsSupplier = sessionOperationsSupplier;
    }

    static PlaybackSessionRefreshOwner fromPlaybackSessionManager(
            Supplier<PlaybackSessionManager> playbackSessionManagerSupplier
    ) {
        return new PlaybackSessionRefreshOwner(
                () -> {
                    PlaybackSessionManager manager = playbackSessionManagerSupplier == null
                            ? null
                            : playbackSessionManagerSupplier.get();
                    return manager == null ? null : manager::refreshPlayer;
                }
        );
    }

    @Override
    public void refreshPlaybackSession() {
        SessionOperations sessionOperations = sessionOperationsSupplier == null
                ? null
                : sessionOperationsSupplier.get();
        if (sessionOperations != null) {
            sessionOperations.refreshPlayer();
        }
    }
}
