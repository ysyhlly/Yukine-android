package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCurrentTrackPreparationRuntimeOwnerTest {
    @Test
    public void delegatesRuntimeStatePreparationUpdates() {
        List<String> events = new ArrayList<>();
        FakeRuntimeStateActions actions = new FakeRuntimeStateActions(events);
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                actions::setPreparing,
                actions::setErrorMessage,
                actions::preparing
        );

        owner.setPreparing(false);
        owner.setErrorMessage("Unable to play this track.");

        assertEquals(
                Arrays.asList(
                        "preparing:false",
                        "error:Unable to play this track."
                ),
                events
        );
    }

    @Test
    public void beginPreparingAndOpenFailureUsePreparationRuntimeState() {
        List<String> events = new ArrayList<>();
        FakeRuntimeStateActions actions = new FakeRuntimeStateActions(events);
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                actions::setPreparing,
                actions::setErrorMessage,
                actions::preparing
        );

        owner.beginPreparing();
        owner.markUnableToOpenCurrentTrack();

        assertEquals(
                Arrays.asList(
                        "preparing:true",
                        "preparing:false",
                        "error:Unable to open this track."
                ),
                events
        );
    }

    @Test
    public void readsPreparingRuntimeState() {
        List<String> events = new ArrayList<>();
        FakeRuntimeStateActions actions = new FakeRuntimeStateActions(events);
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                actions::setPreparing,
                actions::setErrorMessage,
                actions::preparing
        );

        owner.beginPreparing();

        assertTrue(owner.preparing());
        owner.setPreparing(false);
        assertFalse(owner.preparing());
    }

    @Test
    public void ignoresMissingRuntimeStateActions() {
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                null,
                null,
                null
        );

        owner.setPreparing(false);
        owner.setErrorMessage("ignored");
        owner.beginPreparing();
        owner.markUnableToOpenCurrentTrack();
        assertFalse(owner.preparing());
    }

    private static final class FakeRuntimeStateActions {
        private final List<String> events;
        private boolean preparing;

        private FakeRuntimeStateActions(List<String> events) {
            this.events = events;
        }

        public void setPreparing(boolean preparing) {
            this.preparing = preparing;
            events.add("preparing:" + preparing);
        }

        public void setErrorMessage(String message) {
            events.add("error:" + message);
        }

        public boolean preparing() {
            return preparing;
        }
    }
}
