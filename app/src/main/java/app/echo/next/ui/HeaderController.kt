package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class HeaderController(
    context: Context,
    private val title: String
) {
    private val status: MutableState<String> = mutableStateOf("")
    private val expanded: MutableState<Boolean> = mutableStateOf(true)

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                Header(title, status.value, expanded.value)
            }
        }
    }

    fun updateStatus(value: String) {
        status.value = value
    }

    fun setExpanded(value: Boolean) {
        expanded.value = value
    }
}

fun interface SearchAction {
    fun onSearchChanged(query: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    title: String,
    status: String,
    expanded: Boolean
) {
    val p = EchoTheme.colors()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(EchoIconKind.Mark, Modifier.size(34.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = EchoTypography.display,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!expanded) {
            return@Column
        }

        Spacer(Modifier.height(6.dp))
        Text(
            status.ifBlank { " " },
            style = EchoTypography.caption,
            color = p.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
