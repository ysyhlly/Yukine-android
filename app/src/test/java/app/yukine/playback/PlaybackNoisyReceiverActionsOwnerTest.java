package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackNoisyReceiverActionsOwnerTest {
    @Test
    public void pausesOnlyWhenPlaybackIsActive() {
        FakePlaybackControls playingControls = new FakePlaybackControls();
        new PlaybackNoisyReceiverActionsOwner(() -> true, playingControls).pauseIfPlaying();

        FakePlaybackControls stoppedControls = new FakePlaybackControls();
        new PlaybackNoisyReceiverActionsOwner(() -> false, stoppedControls).pauseIfPlaying();

        assertEquals(1, playingControls.pauseCalls);
        assertEquals(0, stoppedControls.pauseCalls);
    }

    @Test
    public void ignoresMissingDependencies() {
        FakePlaybackControls controls = new FakePlaybackControls();
        new PlaybackNoisyReceiverActionsOwner(null, controls).pauseIfPlaying();
        new PlaybackNoisyReceiverActionsOwner(() -> true, null).pauseIfPlaying();

        assertEquals(0, controls.pauseCalls);
    }

    private static final class FakePlaybackControls
            implements PlaybackNoisyReceiverActionsOwner.PlaybackControls {
        private int pauseCalls;

        @Override
        public void pause() {
            pauseCalls++;
        }
    }
}
