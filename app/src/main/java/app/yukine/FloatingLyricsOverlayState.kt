package app.yukine

internal enum class FloatingLyricsMode {
    Compact,
    Expanded
}

internal enum class FloatingLyricsInteraction {
    Interactive,
    ClickThrough
}

internal sealed interface FloatingLyricsPresentation {
    data object WaitingForLyrics : FloatingLyricsPresentation
    data object HiddenByUser : FloatingLyricsPresentation
    data class Visible(
        val mode: FloatingLyricsMode,
        val interaction: FloatingLyricsInteraction
    ) : FloatingLyricsPresentation
}

enum class FloatingLyricsRuntimeStatus {
    Disabled,
    PermissionRequired,
    Waiting,
    Visible,
    Hidden,
    Failed
}

internal sealed interface FloatingLyricsOverlayAction {
    data object ToggleExpanded : FloatingLyricsOverlayAction
    data class DragBy(val deltaX: Int, val deltaY: Int) : FloatingLyricsOverlayAction
    data object DragFinished : FloatingLyricsOverlayAction
    data object PlayPause : FloatingLyricsOverlayAction
    data object Previous : FloatingLyricsOverlayAction
    data object Next : FloatingLyricsOverlayAction
    data object RequestClickThrough : FloatingLyricsOverlayAction
    data object ConfirmClickThrough : FloatingLyricsOverlayAction
    data object CancelClickThrough : FloatingLyricsOverlayAction
    data object Unlock : FloatingLyricsOverlayAction
    data object HideSession : FloatingLyricsOverlayAction
    data object Show : FloatingLyricsOverlayAction
    data object OpenSettings : FloatingLyricsOverlayAction
    data class UpdateBackgroundOpacity(val percent: Int) : FloatingLyricsOverlayAction
}

internal object FloatingLyricsOverlayReducer {
    fun onLyricsAvailability(
        current: FloatingLyricsPresentation,
        lyricsAvailable: Boolean
    ): FloatingLyricsPresentation {
        if (!lyricsAvailable) {
            return if (current is FloatingLyricsPresentation.HiddenByUser) {
                current
            } else {
                FloatingLyricsPresentation.WaitingForLyrics
            }
        }
        return when (current) {
            FloatingLyricsPresentation.WaitingForLyrics ->
                FloatingLyricsPresentation.Visible(
                    FloatingLyricsMode.Compact,
                    FloatingLyricsInteraction.Interactive
                )
            FloatingLyricsPresentation.HiddenByUser -> current
            is FloatingLyricsPresentation.Visible -> current
        }
    }

    fun reduce(
        current: FloatingLyricsPresentation,
        action: FloatingLyricsOverlayAction
    ): FloatingLyricsPresentation = when (action) {
        FloatingLyricsOverlayAction.ToggleExpanded -> {
            val visible = current as? FloatingLyricsPresentation.Visible ?: return current
            if (visible.interaction == FloatingLyricsInteraction.ClickThrough) {
                visible
            } else {
                visible.copy(
                    mode = if (visible.mode == FloatingLyricsMode.Compact) {
                        FloatingLyricsMode.Expanded
                    } else {
                        FloatingLyricsMode.Compact
                    }
                )
            }
        }
        FloatingLyricsOverlayAction.ConfirmClickThrough -> {
            val visible = current as? FloatingLyricsPresentation.Visible ?: return current
            visible.copy(
                mode = FloatingLyricsMode.Compact,
                interaction = FloatingLyricsInteraction.ClickThrough
            )
        }
        FloatingLyricsOverlayAction.Unlock -> {
            val visible = current as? FloatingLyricsPresentation.Visible ?: return current
            visible.copy(interaction = FloatingLyricsInteraction.Interactive)
        }
        FloatingLyricsOverlayAction.HideSession -> FloatingLyricsPresentation.HiddenByUser
        FloatingLyricsOverlayAction.Show -> {
            if (current is FloatingLyricsPresentation.HiddenByUser) {
                FloatingLyricsPresentation.WaitingForLyrics
            } else {
                current
            }
        }
        else -> current
    }

    fun status(
        enabled: Boolean,
        permissionGranted: Boolean,
        presentation: FloatingLyricsPresentation?
    ): FloatingLyricsRuntimeStatus {
        if (!enabled) return FloatingLyricsRuntimeStatus.Disabled
        if (!permissionGranted) return FloatingLyricsRuntimeStatus.PermissionRequired
        return when (presentation) {
            null,
            FloatingLyricsPresentation.WaitingForLyrics -> FloatingLyricsRuntimeStatus.Waiting
            FloatingLyricsPresentation.HiddenByUser -> FloatingLyricsRuntimeStatus.Hidden
            is FloatingLyricsPresentation.Visible -> FloatingLyricsRuntimeStatus.Visible
        }
    }
}
