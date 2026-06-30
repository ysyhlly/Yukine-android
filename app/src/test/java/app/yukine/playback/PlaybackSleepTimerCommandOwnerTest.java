package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackSleepTimerCommandOwnerTest {
    @Test
    public void delegatesSleepTimerActionsToPlaybackCommands() {
        List<String> events = new ArrayList<>();
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                new FakePlaybackCommands(events),
                () -> events.add("publish")
        );

        owner.pausePlayback();
        owner.publishState();

        assertEquals(
                java.util.Arrays.asList(
                        "pause",
                        "publish"
                ),
                events
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
