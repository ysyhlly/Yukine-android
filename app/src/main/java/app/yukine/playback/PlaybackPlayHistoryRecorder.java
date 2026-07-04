package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
        this.historySink = historySink;
        this.transitionStateManager = transitionStateManager;
    }

    static PlaybackPlayHistoryRecorder fromRepository(
            MusicLibraryRepository repository,
            PlaybackTransitionStateManager transitionStateManager
    ) {
        return new PlaybackPlayHistoryRecorder(repository::markPlayed, transitionStateManager);
    }

    static Runnable recordIfPlaybackStartedAction(
            PlaybackPlayHistoryRecorder recorder,
            BooleanSupplier playWhenReady,
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return () -> {
            if (recorder == null) {
                return;
            }
            recorder.recordIfPlaybackStarted(
                    playWhenReady != null && playWhenReady.getAsBoolean(),
                    currentTrack(playbackQueueManagerSupplier)
            );
        };
    }

    private static Track currentTrack(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                ? null
                : playbackQueueManagerSupplier.get();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? null : snapshot.getCurrentTrack();
    }

    void recordIfPlaybackStarted(boolean playWhenReady, Track track) {
        if (!playWhenReady || track == null || transitionStateManager == null) {
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
