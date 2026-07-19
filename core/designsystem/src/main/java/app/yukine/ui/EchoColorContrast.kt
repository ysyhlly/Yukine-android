package app.yukine.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

internal const val MIN_TEXT_CONTRAST = 4.5f

/**
 * Calculates the WCAG contrast ratio for opaque foreground and background colors.
 */
internal fun contrastRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = foreground.luminance()
    val backgroundLuminance = background.luminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Resolves a translucent overlay against its real surface so Material color roles stay opaque.
 */
internal fun opaqueComposite(overlay: Color, base: Color): Color =
    overlay.compositeOver(base.copy(alpha = 1f)).copy(alpha = 1f)

/**
 * Preserves semantic colors when they are readable, otherwise falls back to the most legible
 * black/white foreground. This runs only while a theme palette is being created.
 */
internal fun readableContentColor(
    background: Color,
    preferred: Color,
    alternate: Color,
    minRatio: Float = MIN_TEXT_CONTRAST
): Color {
    val opaqueBackground = background.copy(alpha = 1f)
    val opaquePreferred = opaqueComposite(preferred, opaqueBackground)
    if (contrastRatio(opaquePreferred, opaqueBackground) >= minRatio) {
        return opaquePreferred
    }

    val opaqueAlternate = opaqueComposite(alternate, opaqueBackground)
    if (contrastRatio(opaqueAlternate, opaqueBackground) >= minRatio) {
        return opaqueAlternate
    }

    val blackContrast = contrastRatio(Color.Black, opaqueBackground)
    val whiteContrast = contrastRatio(Color.White, opaqueBackground)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

internal fun EchoPalette.withAccessibleContentColors(): EchoPalette = copy(
    onAccent = readableContentColor(
        background = accent,
        preferred = onAccent,
        alternate = text
    )
)
