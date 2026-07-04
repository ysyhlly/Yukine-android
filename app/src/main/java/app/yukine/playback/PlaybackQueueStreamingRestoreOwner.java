package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlaybackQueueStreamingRestoreOwner implements PlaybackQueueManager.StreamingRestoreProvider {
    private final Function<Track, Track> restoredTrackForPreparation;
    private final Consumer<String> restoreHeadersForDataPath;

    PlaybackQueueStreamingRestoreOwner(
            Function<Track, Track> restoredTrackForPreparation,
            Consumer<String> restoreHeadersForDataPath
    ) {
        this.restoredTrackForPreparation = restoredTrackForPreparation;
        this.restoreHeadersForDataPath = restoreHeadersForDataPath;
    }

    PlaybackQueueStreamingRestoreOwner(PlaybackMediaSourceProvider mediaSourceProvider) {
        this(
                restoredTrackForPreparation(mediaSourceProvider),
                restoreHeadersForDataPath(mediaSourceProvider)
        );
    }

    @Override
    public Track restoreTrackForPlayback(Track track) {
        if (!PlaybackMediaSourceProvider.isRestorableQueueTrack(track)) {
            return null;
        }
        Track restoredTrack = restoredTrackForPreparation == null
                ? null
                : restoredTrackForPreparation.apply(track);
        Track playbackTrack = restoredTrack == null ? track : restoredTrack;
        if (restoreHeadersForDataPath != null) {
            restoreHeadersForDataPath.accept(playbackTrack.dataPath);
        }
        return playbackTrack;
    }

    private static Function<Track, Track> restoredTrackForPreparation(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        PlaybackMediaSourceProvider mediaSourceOwner =
                Objects.requireNonNull(mediaSourceProvider, "mediaSourceProvider");
        return mediaSourceOwner::restoredTrackForPreparation;
    }

    private static Consumer<String> restoreHeadersForDataPath(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        PlaybackMediaSourceProvider mediaSourceOwner =
                Objects.requireNonNull(mediaSourceProvider, "mediaSourceProvider");
        return mediaSourceOwner::restoreHeadersForDataPath;
    }
}
