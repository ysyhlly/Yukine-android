package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
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
            Supplier<Track> currentTrackSupplier
    ) {
        return () -> {
            if (recorder == null) {
                return;
            }
            recorder.recordIfPlaybackStarted(
                    playWhenReady != null && playWhenReady.getAsBoolean(),
                    currentTrack(currentTrackSupplier)
            );
        };
    }

    private static Track currentTrack(
            Supplier<Track> currentTrackSupplier
    ) {
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
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
