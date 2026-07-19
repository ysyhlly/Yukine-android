package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingLyricsOverlayStateTest {
    @Test
    fun lyricsAvailabilityCreatesCompactInteractiveOverlay() {
        assertEquals(
            FloatingLyricsPresentation.Visible(
                FloatingLyricsMode.Compact,
                FloatingLyricsInteraction.Interactive
            ),
            FloatingLyricsOverlayReducer.onLyricsAvailability(
                FloatingLyricsPresentation.WaitingForLyrics,
                lyricsAvailable = true
            )
        )
    }

    @Test
    fun hiddenOverlayIsNotRestoredByLyricsUpdates() {
        assertEquals(
            FloatingLyricsPresentation.HiddenByUser,
            FloatingLyricsOverlayReducer.onLyricsAvailability(
                FloatingLyricsPresentation.HiddenByUser,
                lyricsAvailable = true
            )
        )
    }

    @Test
    fun clickThroughCollapsesAndIgnoresExpansion() {
        val expanded = FloatingLyricsPresentation.Visible(
            FloatingLyricsMode.Expanded,
            FloatingLyricsInteraction.Interactive
        )
        val locked = FloatingLyricsOverlayReducer.reduce(
            expanded,
            FloatingLyricsOverlayAction.ConfirmClickThrough
        )

        assertEquals(
            FloatingLyricsPresentation.Visible(
                FloatingLyricsMode.Compact,
                FloatingLyricsInteraction.ClickThrough
            ),
            locked
        )
        assertEquals(
            locked,
            FloatingLyricsOverlayReducer.reduce(
                locked,
                FloatingLyricsOverlayAction.ToggleExpanded
            )
        )
    }

    @Test
    fun showOnlyClearsTemporaryHiddenState() {
        assertEquals(
            FloatingLyricsPresentation.WaitingForLyrics,
            FloatingLyricsOverlayReducer.reduce(
                FloatingLyricsPresentation.HiddenByUser,
                FloatingLyricsOverlayAction.Show
            )
        )
    }

    @Test
    fun runtimeStatusPrioritizesDisabledAndPermissionRequired() {
        assertEquals(
            FloatingLyricsRuntimeStatus.Disabled,
            FloatingLyricsOverlayReducer.status(
                enabled = false,
                permissionGranted = false,
                presentation = FloatingLyricsPresentation.HiddenByUser
            )
        )
        assertEquals(
            FloatingLyricsRuntimeStatus.PermissionRequired,
            FloatingLyricsOverlayReducer.status(
                enabled = true,
                permissionGranted = false,
                presentation = FloatingLyricsPresentation.WaitingForLyrics
            )
        )
    }
}
