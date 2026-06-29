package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackServiceActionsTest {
    @Test
    public void serviceConstantsRemainCompatibilityAliasesForSharedActions() {
        assertEquals(PlaybackServiceActions.PREVIOUS, EchoPlaybackService.ACTION_PREVIOUS);
        assertEquals(PlaybackServiceActions.PAUSE, EchoPlaybackService.ACTION_PAUSE);
        assertEquals(PlaybackServiceActions.RESTORE_AND_PLAY, EchoPlaybackService.ACTION_RESTORE_AND_PLAY);
        assertEquals(PlaybackServiceActions.NEXT, EchoPlaybackService.ACTION_NEXT);
        assertEquals(PlaybackServiceActions.STOP, EchoPlaybackService.ACTION_STOP);
    }

    @Test
    public void actionOwnerRecognizesPlaybackServiceActions() {
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.PLAY));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.PAUSE));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.PREVIOUS));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.NEXT));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.TOGGLE_FAVORITE));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.RESTORE));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.RESTORE_AND_PLAY));
        assertTrue(PlaybackServiceActions.isPlaybackServiceAction(PlaybackServiceActions.STOP));
        assertFalse(PlaybackServiceActions.isPlaybackServiceAction(""));
        assertFalse(PlaybackServiceActions.isPlaybackServiceAction(null));
    }
}
