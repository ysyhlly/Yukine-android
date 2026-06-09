package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp

/**
 * Standalone "search music" bar that lives directly beneath the tab bar.
 *
 * The bar can collapse on downward scroll and reappear on upward scroll. The
 * visibility change is intentionally instant: animating layout height leaves a
 * half-collapsed state that can jitter while the user is holding or dragging.
 */
class SearchBarController(
    context: Context,
    placeholderText: String,
    private val onSearchChanged: SearchAction
) {
    private val query: MutableState<String> = mutableStateOf("")
    private val placeholder: MutableState<String> = mutableStateOf(placeholderText)
    // visible  -> whether the bar is allowed to show at all (e.g. hidden on
    //             screens without search such as Settings)
    // collapsed -> driven by scroll direction; true hides the field
    private val visible: MutableState<Boolean> = mutableStateOf(true)
    private val collapsed: MutableState<Boolean> = mutableStateOf(false)

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                SearchBar(
                    query = query.value,
                    placeholder = placeholder.value,
                    expanded = visible.value && !collapsed.value
                ) { value ->
                    query.value = value
                    onSearchChanged.onSearchChanged(value)
                }
            }
        }
    }

    /** Show or hide the search bar entirely (used per-screen). */
    fun setVisible(value: Boolean) {
        visible.value = value
        if (value) {
            collapsed.value = false
        }
    }

    /** Collapse (true) or reveal (false) the bar in response to scrolling. */
    fun setCollapsed(value: Boolean) {
        if (collapsed.value == value) {
            return
        }
        collapsed.value = value
    }

    fun updatePlaceholder(value: String) {
        placeholder.value = value
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun currentQuery(): String = query.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    placeholder: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit
) {
    val p = EchoTheme.colors()
    if (!expanded) {
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .echoGlassLayer(p, EchoShapes.medium)
            .padding(horizontal = 2.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = EchoShapes.medium,
            placeholder = {
                Text(placeholder, style = EchoTypography.body, color = p.muted)
            },
            leadingIcon = {
                EchoIcon(EchoIconKind.Search, Modifier.size(20.dp), p.muted)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                cursorColor = p.accent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = p.accent,
                unfocusedBorderColor = Color.Transparent,
                focusedPlaceholderColor = p.muted,
                unfocusedPlaceholderColor = p.muted
            )
        )
    }
}
