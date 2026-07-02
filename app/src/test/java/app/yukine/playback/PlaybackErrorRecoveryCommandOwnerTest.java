package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackErrorRecoveryCommandOwnerTest {
    @Test
    public void delegatesErrorRecoveryActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        PlaybackErrorRecoveryCommandOwner owner = new PlaybackErrorRecoveryCommandOwner(
                () -> track,
                failed -> {
                    events.add("canSkip:" + (failed == track));
                    return true;
                },
                playWhenReady -> events.add("prepare:" + playWhenReady),
                new FakePlaybackCommands(events),
                message -> events.add("error:" + message),
                () -> events.add("publish"),
                (message, error) -> events.add("warn:" + message + ":" + error.getMessage())
        );

        assertSame(track, owner.currentTrack());
        assertTrue(owner.canSkipFailedTrack(track));
        assertEquals("trackId=7, title=Track 7, dataPath=file:7, uri=null", owner.debugTrack(track));
        assertEquals("track=<null>", owner.debugTrack(null));
        owner.prepareCurrent(true);
        owner.skipToNext();
        owner.setErrorMessage("Unable");
        owner.publishState();
        owner.logWarning("Playback failed", new Exception("boom"));

        assertEquals(
                java.util.Arrays.asList(
                        "canSkip:true",
                        "prepare:true",
                        "next",
                        "error:Unable",
                        "publish",
                        "warn:Playback failed:boom"
                ),
                events
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakePlaybackCommands implements PlaybackNotificationCommandOwner.PlaybackCommands {
        private final List<String> events;

        FakePlaybackCommands(List<String> events) {
            this.events = events;
        }

        @Override
        public void play() {
            events.add("play");
        }

        @Override
        public void pause() {
            events.add("pause");
        }

        @Override
        public void skipToPrevious() {
            events.add("previous");
        }

        @Override
        public void skipToNext() {
            events.add("next");
        }

        @Override
        public void toggleCurrentFavorite() {
            events.add("favorite");
        }

        @Override
        public void restoreLastPlayback(boolean playWhenReady) {
            events.add("restore:" + playWhenReady);
        }

        @Override
        public void stopAndClear() {
            events.add("stopAndClear");
        }
    }
}
