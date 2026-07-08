package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueCommandOwnerTest {
    @Test
    public void delegatesQueueActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                playWhenReady -> events.add("prepare:" + playWhenReady),
                () -> events.add("publish"),
                new FakePlaybackCommands(events),
                (tracks, currentIndex) -> {
                    events.add("persist:" + tracks.size() + ":" + currentIndex);
                    return true;
                }
        );

        owner.prepareCurrent(true);
        owner.publishState();
        owner.stopAndClear();
        boolean persisted = owner.persistQueueAsync(
                java.util.Collections.singletonList(track(1L)),
                0
        );

        assertTrue(persisted);
        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "publish",
                        "stopAndClear",
                        "persist:1:0"
                ),
                events
        );
    }

    private static app.yukine.model.Track track(long id) {
        return new app.yukine.model.Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                android.net.Uri.EMPTY,
                "file:" + id
        );
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
