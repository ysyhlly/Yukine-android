package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable statePublisher;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final QueuePersistence queuePersistence;

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands
    ) {
        this(playbackPreparer, statePublisher, playbackCommands, null);
    }

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            QueuePersistence queuePersistence
    ) {
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.playbackCommands = playbackCommands;
        this.queuePersistence = queuePersistence;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.accept(playWhenReady);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void stopAndClear() {
        playbackCommands.stopAndClear();
    }

    @Override
    public boolean persistQueueAsync(List<Track> tracks, int currentIndex) {
        return queuePersistence != null && queuePersistence.persist(tracks, currentIndex);
    }

    @Override
    public boolean persistQueueNow(List<Track> tracks, int currentIndex) {
        return queuePersistence != null && queuePersistence.persistNow(tracks, currentIndex);
    }

    static QueuePersistence conflatingQueuePersistence(
            QueuePersistenceScheduler scheduler,
            QueueSaver queueSaver
    ) {
        if (scheduler == null || queueSaver == null) {
            return null;
        }
        return new ConflatingQueuePersistence(scheduler, queueSaver);
    }

    interface QueuePersistence {
        boolean persist(List<Track> tracks, int currentIndex);

        default boolean persistNow(List<Track> tracks, int currentIndex) {
            return false;
        }
    }

    interface QueuePersistenceScheduler {
        boolean schedule(Runnable command);
    }

    interface QueueSaver {
        void save(List<Track> tracks, int currentIndex);
    }

    private static final class ConflatingQueuePersistence implements QueuePersistence {
        private final Object lock = new Object();
        private final QueuePersistenceScheduler scheduler;
        private final QueueSaver queueSaver;
        private PendingQueueSave pendingSave;
        private boolean drainScheduled;

        private ConflatingQueuePersistence(
                QueuePersistenceScheduler scheduler,
                QueueSaver queueSaver
        ) {
            this.scheduler = scheduler;
            this.queueSaver = queueSaver;
        }

        @Override
        public boolean persist(List<Track> tracks, int currentIndex) {
            if (tracks == null) {
                return false;
            }
            synchronized (lock) {
                PendingQueueSave requestedSave =
                        new PendingQueueSave(new ArrayList<>(tracks), currentIndex);
                pendingSave = requestedSave;
                if (drainScheduled) {
                    return true;
                }
                drainScheduled = true;
                if (scheduleDrainLocked()) {
                    return true;
                }
                if (pendingSave == requestedSave) {
                    pendingSave = null;
                }
                return false;
            }
        }

        @Override
        public boolean persistNow(List<Track> tracks, int currentIndex) {
            if (tracks == null) {
                return false;
            }
            synchronized (lock) {
                pendingSave = null;
                queueSaver.save(new ArrayList<>(tracks), currentIndex);
                return true;
            }
        }

        private void drainLatest() {
            synchronized (lock) {
                PendingQueueSave next = pendingSave;
                pendingSave = null;
                if (next == null) {
                    drainScheduled = false;
                    return;
                }
                try {
                    queueSaver.save(next.tracks, next.currentIndex);
                } catch (RuntimeException error) {
                    if (pendingSave == null) {
                        drainScheduled = false;
                    } else {
                        scheduleDrainLocked();
                    }
                    throw error;
                }
                if (pendingSave == null) {
                    drainScheduled = false;
                    return;
                }
                scheduleDrainLocked();
            }
        }

        private boolean scheduleDrainLocked() {
            try {
                if (scheduler.schedule(this::drainLatest)) {
                    return true;
                }
            } catch (RuntimeException error) {
                // Fall through so a future persistence request can retry scheduling.
            }
            drainScheduled = false;
            return false;
        }
    }

    private static final class PendingQueueSave {
        private final List<Track> tracks;
        private final int currentIndex;

        private PendingQueueSave(List<Track> tracks, int currentIndex) {
            this.tracks = tracks;
            this.currentIndex = currentIndex;
        }
    }
}
