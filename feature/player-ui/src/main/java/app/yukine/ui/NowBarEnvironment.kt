package app.yukine.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalEchoNowBarCompactProgress = staticCompositionLocalOf { 0f }
val LocalEchoNowBarScrollProgress = staticCompositionLocalOf { 0f }
val LocalEchoNowBarPageScrollEvent = staticCompositionLocalOf { 0 }
val LocalEchoNowBarBottomInset = staticCompositionLocalOf { 0.dp }
val LocalEchoNowBarTopCloudClearanceChanged = staticCompositionLocalOf<(Dp) -> Unit> { {} }

/** Reports the vertical space the expanded/bottom NowBar occupies above the bottom navigation. */
val LocalEchoNowBarOccupiedHeightChanged = staticCompositionLocalOf<(Dp) -> Unit> { {} }
