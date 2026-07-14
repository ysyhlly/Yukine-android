package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import app.yukine.playback.service.PlaybackServiceActions;

public class PlaybackServiceActionBufferTest {
    @Test
    public void defersColdStartActionsAndReplaysThemInOrder() {
        PlaybackServiceActionBuffer buffer = new PlaybackServiceActionBuffer();

        assertTrue(buffer.requiresBootstrapForeground(PlaybackServiceActions.PLAY));
        assertTrue(buffer.accept(PlaybackServiceActions.PLAY).isEmpty());
        assertTrue(buffer.accept(PlaybackServiceActions.NEXT).isEmpty());

        assertEquals(
                Arrays.asList(PlaybackServiceActions.PLAY, PlaybackServiceActions.NEXT),
                buffer.markReadyAndDrain()
        );
        assertEquals(
                Collections.singletonList(PlaybackServiceActions.PREVIOUS),
                buffer.accept(PlaybackServiceActions.PREVIOUS)
        );
        assertFalse(buffer.requiresBootstrapForeground(PlaybackServiceActions.PREVIOUS));
    }

    @Test
    public void stopClearsDeferredActionsAndDispatchesImmediately() {
        PlaybackServiceActionBuffer buffer = new PlaybackServiceActionBuffer();
        buffer.accept(PlaybackServiceActions.NEXT);

        assertEquals(
                Collections.singletonList(PlaybackServiceActions.STOP),
                buffer.accept(PlaybackServiceActions.STOP)
        );
        assertFalse(buffer.requiresBootstrapForeground(PlaybackServiceActions.STOP));
        assertTrue(buffer.markReadyAndDrain().isEmpty());
    }

    @Test
    public void ignoresUnknownActions() {
        PlaybackServiceActionBuffer buffer = new PlaybackServiceActionBuffer();

        assertTrue(buffer.accept("app.yukine.UNKNOWN").isEmpty());
        assertTrue(buffer.markReadyAndDrain().isEmpty());
    }
}
