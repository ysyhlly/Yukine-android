package app.yukine.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

private val SearchHeaderHeight = 68.dp

@Composable
fun CollapsibleSearchHeader(
    header: @Composable () -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val headerOffset by animateDpAsState(
        targetValue = if (expanded) 0.dp else -SearchHeaderHeight,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "searchHeaderOffset"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "searchHeaderAlpha"
    )
    val contentOffset by animateDpAsState(
        targetValue = if (expanded) SearchHeaderHeight else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "searchContentOffset"
    )
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    when {
                        available.y < -8f -> expanded = false
                        available.y > 8f -> expanded = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection)
    ) {
        content(
            Modifier
                .fillMaxSize()
                .padding(top = contentOffset),
            expanded
        )
        if (headerAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SearchHeaderHeight)
                    .offset(y = headerOffset)
                    .alpha(headerAlpha)
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp)
            ) {
                header()
            }
        }
    }
}
