package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackNotificationCommandOwnerTest {
    @Test
    public void delegatesNotificationActionsToPlaybackCommands() {
        List<String> events = new ArrayList<>();
        PlaybackNotificationCommandOwner owner = new PlaybackNotificationCommandOwner(
                force -> events.add("notify:" + force),
                () -> true,
                new FakePlaybackCommands(events),
                () -> events.add("stopForegroundAndSelf")
        );

        owner.publishPlaybackNotification(true);
        owner.publishPlaybackNotificationIfWorthy();
        owner.play();
        owner.pause();
        owner.skipToPrevious();
        owner.skipToNext();
        owner.toggleCurrentFavorite();
        owner.restoreLastPlayback(false);
        owner.restoreLastPlayback(true);
        owner.stopAndClear();
        owner.stopForegroundAndSelf();

        assertEquals(
                java.util.Arrays.asList(
                        "notify:true",
                        "notify:true",
                        "play",
                        "pause",
                        "previous",
                        "next",
                        "favorite",
                        "restore:false",
                        "restore:true",
                        "stopAndClear",
                        "stopForegroundAndSelf"
                ),
                events
        );
    }

    @Test
    public void skipsConditionalPublishWhenNotificationStateIsNotWorthy() {
        List<String> events = new ArrayList<>();
        PlaybackNotificationCommandOwner owner = new PlaybackNotificationCommandOwner(
                force -> events.add("notify:" + force),
                () -> false,
                new FakePlaybackCommands(events),
                () -> events.add("stopForegroundAndSelf")
        );

        owner.publishPlaybackNotificationIfWorthy();

        assertEquals(java.util.Collections.emptyList(), events);
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
