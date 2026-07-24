package app.yukine.playback;

import androidx.annotation.Nullable;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.StreamingAudioFormatPreflight;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Owns the lifecycle boundary between streaming PCM probing and current-track preparation.
 * Results may warm in the background, but only the still-current track can resume a blocked
 * foreground preparation.
 */
final class PlaybackStreamingAudioPreflightOwner {
    private final StreamingAudioFormatPreflight preflight;
    private final Supplier<Track> currentTrackProvider;
    private final Runnable beginPreparingAction;

    PlaybackStreamingAudioPreflightOwner(
            PlaybackMediaSourceProvider mediaSourceProvider,
            Executor callbackExecutor,
            Supplier<Track> currentTrackProvider,
            Runnable beginPreparingAction
    ) {
        preflight = new StreamingAudioFormatPreflight(mediaSourceProvider, callbackExecutor);
        this.currentTrackProvider = currentTrackProvider;
        this.beginPreparingAction = beginPreparingAction;
    }

    boolean appliesTo(@Nullable Track track) {
        return preflight.appliesTo(track);
    }

    StreamingAudioFormatPreflight.Result resultFor(Track track) {
        return preflight.resultFor(track);
    }

    /**
     * @return true when preparation must pause until the callback is invoked.
     */
    boolean waitForCurrentResult(
            Track track,
            boolean usbExclusiveEnabled,
            Runnable resumePreparation
    ) {
        if (!usbExclusiveEnabled || !preflight.appliesTo(track)) {
            return false;
        }
        StreamingAudioFormatPreflight.Result existing = preflight.resultFor(track);
        if (existing.terminal()) {
            return false;
        }
        beginPreparingAction.run();
        final long expectedTrackId = track.id;
        final String expectedDataPath = track.dataPath;
        final String expectedUri = track.contentUri.toString();
        preflight.requestCurrent(track, completed -> {
            Track current = currentTrackProvider.get();
            if (sameResolvedTrack(current, expectedTrackId, expectedDataPath, expectedUri)) {
                resumePreparation.run();
            }
        });
        return true;
    }

    void requestUpcoming(@Nullable Track track) {
        preflight.requestUpcoming(track);
    }

    void release() {
        preflight.release();
    }

    private static boolean sameResolvedTrack(
            @Nullable Track current,
            long expectedTrackId,
            String expectedDataPath,
            String expectedUri
    ) {
        return current != null
                && current.id == expectedTrackId
                && expectedDataPath.equals(current.dataPath)
                && expectedUri.equals(current.contentUri.toString());
    }
}
