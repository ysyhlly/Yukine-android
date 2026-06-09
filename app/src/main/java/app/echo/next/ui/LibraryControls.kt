package app.echo.next.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class LibraryModeUiState(val label: String, val mode: String, val selected: Boolean)
fun interface LibraryModeSelectAction { fun onModeSelected(mode: String) }

object LibraryControlsFactory {
    @JvmStatic
    fun modeSelector(
        context: Context, modes: List<LibraryModeUiState>, action: LibraryModeSelectAction
    ): ComposeView = ComposeView(context).apply {
        layoutParams = bottomSpacedParams(context)
        setContent {
            EchoTheme.EchoTheme { ModeSelector(modes, action) }
        }
    }

    private fun bottomSpacedParams(context: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.setMargins(0, 0, 0, context.dp(8)) }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}

@Composable
private fun ModeSelector(modes: List<LibraryModeUiState>, action: LibraryModeSelectAction) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { mode ->
            Surface(
                onClick = { action.onModeSelected(mode.mode) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .echoGlassLayer(p, EchoShapes.medium)
                    .semantics { contentDescription = mode.label },
                shape = EchoShapes.medium,
                color = if (mode.selected) p.accentSoft else Color.Transparent
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    EchoIcon(
                        kind = iconForMode(mode.mode),
                        modifier = Modifier.size(20.dp),
                        color = if (mode.selected) p.accent else p.muted
                    )
                }
            }
        }
    }
}

private fun iconForMode(mode: String): EchoIconKind = when (mode) {
    "albums" -> EchoIconKind.Collections
    "artists" -> EchoIconKind.Artist
    "folders" -> EchoIconKind.Folder
    else -> EchoIconKind.Library
}
