package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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

    interface PersistenceFlusher {
        void flushPendingPersistence();
    }

    private final PlaybackPositionPersister playbackPositionPersister;
    private final PlaybackQueueLifecycleStore playbackQueueLifecycleStore;
    private final PlaybackStateProvider playbackStateProvider;
    private final NotificationStateProvider notificationStateProvider;
    private final NotificationPublisher notificationPublisher;
    private final Runnable notificationCleaner;
    private final PersistenceFlusher persistenceFlusher;

    PlaybackShutdownLifecycleResourcesOwner(
            PlaybackPositionPersister playbackPositionPersister,
            PlaybackQueueLifecycleStore playbackQueueLifecycleStore,
            PlaybackStateProvider playbackStateProvider,
            NotificationStateProvider notificationStateProvider,
            NotificationPublisher notificationPublisher
    ) {
        this(
                playbackPositionPersister,
                playbackQueueLifecycleStore,
                playbackStateProvider,
                notificationStateProvider,
                notificationPublisher,
                null,
                null
        );
    }

    PlaybackShutdownLifecycleResourcesOwner(
            PlaybackPositionPersister playbackPositionPersister,
            PlaybackQueueLifecycleStore playbackQueueLifecycleStore,
            PlaybackStateProvider playbackStateProvider,
            NotificationStateProvider notificationStateProvider,
            NotificationPublisher notificationPublisher,
            Runnable notificationCleaner
    ) {
        this(
                playbackPositionPersister,
                playbackQueueLifecycleStore,
                playbackStateProvider,
                notificationStateProvider,
                notificationPublisher,
                notificationCleaner,
                null
        );
    }

    PlaybackShutdownLifecycleResourcesOwner(
            PlaybackPositionPersister playbackPositionPersister,
            PlaybackQueueLifecycleStore playbackQueueLifecycleStore,
            PlaybackStateProvider playbackStateProvider,
            NotificationStateProvider notificationStateProvider,
            NotificationPublisher notificationPublisher,
            Runnable notificationCleaner,
            PersistenceFlusher persistenceFlusher
    ) {
        this.playbackPositionPersister = playbackPositionPersister;
        this.playbackQueueLifecycleStore = playbackQueueLifecycleStore;
        this.playbackStateProvider = playbackStateProvider;
        this.notificationStateProvider = notificationStateProvider;
        this.notificationPublisher = notificationPublisher;
        this.notificationCleaner = notificationCleaner;
        this.persistenceFlusher = persistenceFlusher;
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

    static PlaybackQueueLifecycleStore playbackQueueLifecycleStoreFromQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueLifecycleStore() {
            @Override
            public void persistQueueState() {
                PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                if (playbackQueueManager != null) {
                    playbackQueueManager.persistQueueState();
                }
            }

            @Override
            public void savePlaybackResumeRequested(boolean requested) {
                PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                if (playbackQueueManager != null) {
                    playbackQueueManager.savePlaybackResumeRequested(requested);
                }
            }
        };
    }

    private static PlaybackQueueManager playbackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
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

    @Override
    public void flushPendingPersistence() {
        if (persistenceFlusher != null) {
            persistenceFlusher.flushPendingPersistence();
        }
    }

    @Override
    public void clearPlaybackNotification() {
        if (notificationCleaner != null) {
            notificationCleaner.run();
        }
    }
}
