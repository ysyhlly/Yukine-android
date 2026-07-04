package app.yukine.playback;

import java.util.function.BooleanSupplier;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

final class PlaybackShutdownLifecycleResourcesOwner implements PlaybackShutdownCoordinator.LifecycleResources {
    interface PlaybackPositionPersister {
        void persistPlaybackPosition();
    }

    interface PlaybackQueueLifecycleStore {
        void persistQueueState();
        void savePlaybackResumeRequested(boolean requested);
    }

    interface PlaybackStateProvider {
        boolean isPlaying();
        boolean isPreparing();
    }

    interface NotificationStateProvider {
        boolean hasNotificationWorthyState();
    }

    interface NotificationPublisher {
        void publishPlaybackNotification();
    }

    private final PlaybackPositionPersister playbackPositionPersister;
    private final PlaybackQueueLifecycleStore playbackQueueLifecycleStore;
    private final PlaybackStateProvider playbackStateProvider;
    private final NotificationStateProvider notificationStateProvider;
    private final NotificationPublisher notificationPublisher;

    PlaybackShutdownLifecycleResourcesOwner(
            PlaybackPositionPersister playbackPositionPersister,
            PlaybackQueueLifecycleStore playbackQueueLifecycleStore,
            PlaybackStateProvider playbackStateProvider,
            NotificationStateProvider notificationStateProvider,
            NotificationPublisher notificationPublisher
    ) {
        this.playbackPositionPersister = playbackPositionPersister;
        this.playbackQueueLifecycleStore = playbackQueueLifecycleStore;
        this.playbackStateProvider = playbackStateProvider;
        this.notificationStateProvider = notificationStateProvider;
        this.notificationPublisher = notificationPublisher;
    }

    static PlaybackStateProvider playbackStateProviderFromPlaybackState(
            BooleanSupplier playbackStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        return new PlaybackStateProvider() {
            @Override
            public boolean isPlaying() {
                return playbackStateProvider != null && playbackStateProvider.getAsBoolean();
            }

            @Override
            public boolean isPreparing() {
                return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
            }
        };
    }

    static PlaybackQueueLifecycleStore playbackQueueLifecycleStore(
            PlaybackQueueManager playbackQueueManager,
            PlaybackQueueStore queueStore
    ) {
        return new PlaybackQueueLifecycleStore() {
            @Override
            public void persistQueueState() {
                PlaybackShutdownLifecycleResourcesOwner.persistQueueState(playbackQueueManager);
            }

            @Override
            public void savePlaybackResumeRequested(boolean requested) {
                if (queueStore != null) {
                    queueStore.saveResumeRequested(requested);
                }
            }
        };
    }

    static void persistQueueState(PlaybackQueueManager playbackQueueManager) {
        if (playbackQueueManager != null) {
            playbackQueueManager.persistQueueState();
        }
    }

    @Override
    public void persistPlaybackPosition() {
        if (playbackPositionPersister != null) {
            playbackPositionPersister.persistPlaybackPosition();
        }
    }

    @Override
    public void persistPlaybackQueue() {
        if (playbackQueueLifecycleStore != null) {
            playbackQueueLifecycleStore.persistQueueState();
        }
    }

    @Override
    public void savePlaybackResumeRequested(boolean requested) {
        if (playbackQueueLifecycleStore != null) {
            playbackQueueLifecycleStore.savePlaybackResumeRequested(requested);
        }
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider != null && playbackStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return playbackStateProvider != null && playbackStateProvider.isPreparing();
    }

    @Override
    public boolean hasNotificationWorthyState() {
        return notificationStateProvider != null && notificationStateProvider.hasNotificationWorthyState();
    }

    @Override
    public void publishPlaybackNotification() {
        if (notificationPublisher != null) {
            notificationPublisher.publishPlaybackNotification();
        }
    }
}
