package app.yukine.ui

import androidx.compose.ui.unit.dp

object EchoMobileLayoutMetrics {
    val nowBarHeight = 148.dp
    val nowBarExpandedHeight = nowBarHeight
    /** Single-row height while Expanded is height-compacted (scroll or swipe-down lock). */
    val nowBarCompactHeight = 64.dp
    val nowBarArtworkSize = 48.dp
    val nowBarArtworkCornerRadius = 6.dp
    val nowBarProgressHeight = 18.dp
    /** Progress bar + time labels block used when layout-collapsing the Expanded NowBar. */
    val nowBarProgressBlockHeight = 36.dp
    /** Mode chip row under the track line. */
    val nowBarModeControlsHeight = 26.dp
    val nowBarScrollTranslation = 1.5.dp
    const val nowBarScrollScale = 0.985f
    val nowBarScrollStretchTranslation = 0.25.dp
    const val nowBarScrollStretchScale = 1.008f
    val bottomTabScrollTranslation = 0.75.dp
    const val bottomTabScrollScale = 0.99f
    val bottomTabScrollStretchTranslation = 0.25.dp
    const val bottomTabScrollStretchScale = 1.006f
    const val nowBarScrollRestoreDelayMs = 250L
    /** Scroll-driven compact commits a sticky lock once progress reaches this threshold. */
    const val nowBarHeightCompactLockThreshold = 0.92f
    const val nowBarCompactShadowFactor = 0.90f
    const val nowBarCompactLyricsAlpha = 0.94f
    val nowBarDockedWidth = 168.dp
    val nowBarDockedHeight = 40.dp
    val nowBarTopCloudWidth = 144.dp
    val nowBarTopCloudHeight = 26.dp
    val nowBarTopCloudControlSize = 24.dp
    val nowBarTopCloudControlIconSize = 14.dp
    val nowBarTopCloudExpandedWidth = 216.dp
    val nowBarTopCloudExpandedHeight = 36.dp
    val nowBarTopCloudExpandedArtworkSize = 28.dp
    val nowBarTopCloudExpandedControlSize = 34.dp
    val nowBarTopCloudExpandedControlIconSize = 17.dp
    val nowBarTopCloudOffset = 8.dp
    val nowBarTopCloudContentClearance = 40.dp
    val nowBarTopCloudExpandedContentClearance = 50.dp
    val nowBarDockedBottomPadding = 1.dp
    val nowBarDockSwipeDistance = 24.dp
    val nowBarTopCloudEnterDistance = 24.dp
    val nowBarTopCloudRestoreDistance = 32.dp
    const val nowBarDockSwipeVelocityDpPerSecond = 500f
    const val nowBarTopCloudSwipeVelocityDpPerSecond = 500f
    const val nowBarDockHorizontalRatio = 1.2f
    const val nowBarDockVerticalRatio = 1.5f
    const val nowBarTopCloudDiagonalDockRatio = 0.35f
    const val nowBarDockMoveDurationMs = 420
    const val nowBarDockSizeDurationMs = 420
    val bottomTabIconSize = 22.dp
    val bottomTabVerticalPadding = 8.dp
    val floatingChromeHorizontalPadding = 12.dp
    val floatingChromeGap = 3.dp
    val floatingChromeBottomPadding = 10.dp
    val floatingChromeElevation = EchoElevations.chrome
    val nowPlayingArtworkSize = 220.dp
    val nowPlayingArtworkCornerRadius = 12.dp
    val lyricsPanelMinHeight = 300.dp
    val lyricsPanelMaxHeight = 380.dp
    val lyricsListHeight = 292.dp
}
