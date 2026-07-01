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
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                () -> new FakeRuntimeStateOperations(events)
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
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                () -> new FakeRuntimeStateOperations(events)
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
        FakeRuntimeStateOperations runtimeStateOperations = new FakeRuntimeStateOperations(events);
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                () -> runtimeStateOperations
        );

        owner.beginPreparing();

        assertTrue(owner.preparing());
        owner.setPreparing(false);
        assertFalse(owner.preparing());
    }

    @Test
    public void ignoresMissingRuntimeStateOperations() {
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                () -> null
        );

        owner.setPreparing(false);
        owner.setErrorMessage("ignored");
        owner.beginPreparing();
        owner.markUnableToOpenCurrentTrack();
        assertFalse(owner.preparing());
    }

    @Test
    public void ignoresMissingRuntimeStateOperationsProvider() {
        PlaybackCurrentTrackPreparationRuntimeOwner owner = new PlaybackCurrentTrackPreparationRuntimeOwner(
                null
        );

        owner.setPreparing(false);
        owner.setErrorMessage("ignored");
        owner.beginPreparing();
        owner.markUnableToOpenCurrentTrack();
        assertFalse(owner.preparing());
    }

    private static final class FakeRuntimeStateOperations
            implements PlaybackCurrentTrackPreparationRuntimeOwner.RuntimeStateOperations {
        private final List<String> events;
        private boolean preparing;

        private FakeRuntimeStateOperations(List<String> events) {
            this.events = events;
        }

        @Override
        public void setPreparing(boolean preparing) {
            this.preparing = preparing;
            events.add("preparing:" + preparing);
        }

        @Override
        public void setErrorMessage(String message) {
            events.add("error:" + message);
        }

        @Override
        public boolean preparing() {
            return preparing;
        }
    }
}
