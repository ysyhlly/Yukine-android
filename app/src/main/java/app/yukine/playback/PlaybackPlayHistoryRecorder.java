package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class PlaybackPlayHistoryRecorder {
    interface HistorySink {
        void markPlayed(long trackId);
    }

    private final HistorySink historySink;
    private final PlaybackTransitionStateManager transitionStateManager;

    PlaybackPlayHistoryRecorder(
            HistorySink historySink,
            PlaybackTransitionStateManager transitionStateManager
    ) {
        this.historySink = Objects.requireNonNull(historySink, "historySink");
        this.transitionStateManager = Objects.requireNonNull(transitionStateManager, "transitionStateManager");
    }

    static PlaybackPlayHistoryRecorder fromRepository(
            MusicLibraryRepository repository,
            PlaybackTransitionStateManager transitionStateManager
    ) {
        return new PlaybackPlayHistoryRecorder(
                Objects.requireNonNull(repository, "repository")::markPlayed,
                transitionStateManager
        );
    }

    static Runnable recordIfPlaybackStartedAction(
            PlaybackPlayHistoryRecorder recorder,
            BooleanSupplier playWhenReady,
            PlaybackQueueManager playbackQueueManager
    ) {
        PlaybackPlayHistoryRecorder requiredRecorder =
                Objects.requireNonNull(recorder, "recorder");
        BooleanSupplier requiredPlayWhenReady =
                Objects.requireNonNull(playWhenReady, "playWhenReady");
        PlaybackQueueManager requiredQueueManager =
                Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        return () -> {
            requiredRecorder.recordIfPlaybackStarted(
                    requiredPlayWhenReady.getAsBoolean(),
                    currentTrack(requiredQueueManager)
            );
        };
    }

    private static Track currentTrack(PlaybackQueueManager playbackQueueManager) {
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager.queueStateSnapshot();
        return snapshot.getCurrentTrack();
    }

    void recordIfPlaybackStarted(boolean playWhenReady, Track track) {
        if (!playWhenReady || track == null) {
            return;
        }
        Track lastMarked = transitionStateManager.lastMarkedTrack();
        if (lastMarked != null && lastMarked.id == track.id) {
            return;
        }
        historySink.markPlayed(track.id);
        transitionStateManager.setLastMarkedTrack(track);
    }
}
