package app.yukine.playback;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCrossfadeCommandOwnerTest {
    @Test
    public void delegatesCrossfadeActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeCommandOwner owner = new PlaybackCrossfadeCommandOwner(
                enabled -> events.add("fade:" + enabled),
                volume -> events.add("volume:" + volume),
                () -> events.add("nextImmediately"),
                () -> events.add("applyAppVolume")
        );

        owner.setFadeOutAdvancing(true);
        owner.setPlayerVolume(0.5f);
        owner.skipToNextImmediately();
        owner.applyAppVolume();
        owner.setFadeOutAdvancing(false);

        assertEquals(
                java.util.Arrays.asList(
                        "fade:true",
                        "volume:0.5",
                        "nextImmediately",
                        "applyAppVolume",
                        "fade:false"
                ),
                events
        );
    }

    @Test
    public void delegatesCrossfadeCancelToManager() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeAdvanceManager manager = new PlaybackCrossfadeAdvanceManager(
                new FakeCrossfadeScheduler(events),
                new FakeCrossfadeState(),
                new FakeCrossfadeActions(events),
                () -> 0L,
                700L,
                70L
        );
        PlaybackCrossfadeCommandOwner owner = new PlaybackCrossfadeCommandOwner(
                enabled -> events.add("ownerFade:" + enabled),
                volume -> events.add("ownerVolume:" + volume),
                () -> events.add("ownerNextImmediately"),
                () -> events.add("ownerApplyAppVolume"),
                () -> manager
        );

        owner.cancelCrossfadeAdvance();

        assertEquals(java.util.Arrays.asList("managerFade:false"), events);
    }

    @Test
    public void delegatesCrossfadeStartToManager() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeAdvanceManager manager = new PlaybackCrossfadeAdvanceManager(
                new FakeCrossfadeScheduler(events),
                new FakeCrossfadeState(),
                new FakeCrossfadeActions(events),
                () -> 0L,
                700L,
                70L
        );
        PlaybackCrossfadeCommandOwner owner = new PlaybackCrossfadeCommandOwner(
                enabled -> events.add("ownerFade:" + enabled),
                volume -> events.add("ownerVolume:" + volume),
                () -> events.add("ownerNextImmediately"),
                () -> events.add("ownerApplyAppVolume"),
                () -> manager
        );

        assertTrue(owner.startFadeOutThenNext());

        assertEquals(
                java.util.Arrays.asList(
                        "managerFade:true",
                        "post"
                ),
                events
        );
    }

    @Test
    public void toleratesMissingCrossfadeAdvanceManagerProvider() {
        PlaybackCrossfadeCommandOwner owner = new PlaybackCrossfadeCommandOwner(
                enabled -> {
                },
                volume -> {
                },
                () -> {
                },
                () -> {
                }
        );

        owner.cancelCrossfadeAdvance();
        assertFalse(owner.startFadeOutThenNext());
    }

    private static final class FakeCrossfadeScheduler
            implements PlaybackCrossfadeAdvanceManager.CallbackScheduler {
        private final List<String> events;

        FakeCrossfadeScheduler(List<String> events) {
            this.events = events;
        }

        @Override
        public void post(Runnable runnable) {
            events.add("post");
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            events.add("postDelayed:" + delayMs);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            events.add("remove");
        }
    }

    private static final class FakeCrossfadeState
            implements PlaybackCrossfadeAdvanceManager.StateProvider {
        @Override
        public boolean fadeOutAdvancing() {
            return false;
        }

        @Override
        public boolean playerAvailable() {
            return true;
        }

        @Override
        public boolean isPlaying() {
            return true;
        }

        @Override
        public boolean canCrossfadeAdvance() {
            return true;
        }

        @Override
        public float baseVolume() {
            return 1.0f;
        }
    }

    private static final class FakeCrossfadeActions
            implements PlaybackCrossfadeAdvanceManager.Actions {
        private final List<String> events;

        FakeCrossfadeActions(List<String> events) {
            this.events = events;
        }

        @Override
        public void setFadeOutAdvancing(boolean enabled) {
            events.add("managerFade:" + enabled);
        }

        @Override
        public void setPlayerVolume(float volume) {
            events.add("managerVolume:" + volume);
        }

        @Override
        public void skipToNextImmediately() {
            events.add("managerNextImmediately");
        }

        @Override
        public void applyAppVolume() {
            events.add("managerApplyAppVolume");
        }
    }

}
