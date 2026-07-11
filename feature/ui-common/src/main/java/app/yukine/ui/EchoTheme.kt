package app.yukine.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.feature.uicommon.R

// ── Public palette (used by Java interop and Compose surfaces) ──────────────

object EchoGlassDefaults {
    const val RENDER_EFFECT_MIN_API = Build.VERSION_CODES.S
    const val BLUR_RADIUS_DP = 18f
    const val SATURATION = 1.08f
    const val ALPHA = 0.72f
}

data class EchoGlassSpec(
    val blurRadiusDp: Float = EchoGlassDefaults.BLUR_RADIUS_DP,
    val saturation: Float = EchoGlassDefaults.SATURATION,
    val alpha: Float = EchoGlassDefaults.ALPHA
)

data class EchoPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val panel: Color,
    val accent: Color,
    val accentSoft: Color,
    val text: Color,
    val muted: Color,
    val highlight: Color,
    val border: Color,
    val onAccent: Color,
    val backgroundAlt: Color = background,
    val backgroundDeep: Color = background,
    val heading: Color = text,
    val subtle: Color = muted,
    val accentStrong: Color = accent,
    val secondary: Color = accent,
    val success: Color = secondary,
    val shadow: Color = Color.Black,
    val glass: EchoGlassSpec = EchoGlassSpec()
) {
    val backgroundArgb: Int get() = background.toArgb()
    val surfaceArgb: Int get() = surface.toArgb()
    val textArgb: Int get() = text.toArgb()
    val mutedArgb: Int get() = muted.toArgb()
    val accentArgb: Int get() = accent.toArgb()
}

/**
 * Shares the active [EchoPalette] across the whole composition so `EchoTheme.colors()` resolves
 * to a single cached instance instead of recomputing per call site. `null` means no provider is
 * present (callers fall back to computing directly).
 */
val LocalEchoPalette = staticCompositionLocalOf<EchoPalette?> { null }

/**
 * True when a user-chosen background image is active behind the whole page. Card surfaces read
 * this to switch from an opaque fill to a frosted, semi-transparent fill so the wallpaper shows
 * through. Defaults to false (no custom background).
 */
val LocalEchoCustomBackground = staticCompositionLocalOf { false }

// ── Typography scale ────────────────────────────────────────────────────────

object EchoTypography {
    private val yukineCjk = FontFamily(
        Font(R.font.noto_sans_cjk_sc_regular, FontWeight.Normal),
        Font(R.font.noto_sans_cjk_sc_regular, FontWeight.Medium),
        Font(R.font.noto_sans_cjk_sc_regular, FontWeight.SemiBold),
        Font(R.font.noto_sans_cjk_sc_regular, FontWeight.Bold)
    )

    val display = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    )
    val headline = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )
    val title = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    )
    val body = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val caption = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val small = TextStyle(
        fontFamily = yukineCjk,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

// ── Shape system ────────────────────────────────────────────────────────────

object EchoShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val full = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

object EchoElevations {
    val card = 6.dp
    val chrome = 10.dp
}

// ── Main theme object (Java-facing state holder) ────────────────────────────

object EchoTheme {
    const val MODE_SYSTEM   = "system"
    const val MODE_DYNAMIC  = "dynamic"
    const val MODE_DARK     = "dark"
    const val MODE_LIGHT    = "light"
    const val MODE_AMOLED   = "amoled"
    const val MODE_CONTRAST = "contrast"
    const val MODE_GRAPHITE = "graphite"
    const val MODE_MIST     = "mist"
    const val MODE_MIDNIGHT = "midnight"
    const val MODE_FOREST   = "forest"
    const val MODE_OCEAN    = "ocean"
    const val MODE_DAYLIGHT = "daylight"

    const val ACCENT_BLUE    = "blue"
    const val ACCENT_TEAL    = "teal"
    const val ACCENT_ROSE    = "rose"
    const val ACCENT_VIOLET  = "violet"
    const val ACCENT_AMBER   = "amber"
    const val ACCENT_EMERALD = "emerald"
    const val ACCENT_CYAN    = "cyan"
    const val ACCENT_LIME    = "lime"
    const val ACCENT_RED     = "red"
    const val ACCENT_INDIGO  = "indigo"
    const val ACCENT_PINE    = "pine"
    const val ACCENT_PEACH   = "peach"

    private val modeState   = mutableStateOf(MODE_SYSTEM)
    private val accentState = mutableStateOf(ACCENT_BLUE)

    @JvmStatic fun setMode(mode: String?)    { modeState.value   = normalizeMode(mode) }
    @JvmStatic fun currentMode(): String      { return modeState.value }
    @JvmStatic fun setAccent(accent: String?) { accentState.value = normalizeAccent(accent) }
    @JvmStatic fun currentAccent(): String    { return accentState.value }

    @JvmStatic fun dynamicColorAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun dynamicOption(): Array<String> =
        if (dynamicColorAvailable()) arrayOf(MODE_DYNAMIC) else emptyArray()

    @JvmStatic fun modeOptions(): Array<String> = arrayOf(
        MODE_SYSTEM,
        *dynamicOption(),
        MODE_DARK,
        MODE_LIGHT,
        MODE_AMOLED,
        MODE_CONTRAST,
        MODE_GRAPHITE,
        MODE_MIST,
        MODE_MIDNIGHT,
        MODE_FOREST,
        MODE_OCEAN,
        MODE_DAYLIGHT,
        *EchoThemePresets.ids
    )

    @JvmStatic fun primaryModeOptions(): Array<String> = arrayOf(
        MODE_SYSTEM,
        *dynamicOption(),
        MODE_LIGHT,
        MODE_DARK,
        MODE_AMOLED
    )

    @JvmStatic fun advancedModeOptions(): Array<String> {
        val primary = primaryModeOptions().toSet()
        return modeOptions().filterNot { primary.contains(it) }.toTypedArray()
    }

    @JvmStatic fun normalizeMode(mode: String?): String {
        val clean = mode?.trim()
        return when (clean) {
            MODE_DYNAMIC -> if (dynamicColorAvailable()) MODE_DYNAMIC else MODE_SYSTEM
            MODE_DARK -> MODE_DARK; MODE_LIGHT -> MODE_LIGHT
            MODE_AMOLED -> MODE_AMOLED; MODE_CONTRAST -> MODE_CONTRAST
            MODE_GRAPHITE -> MODE_GRAPHITE; MODE_MIST -> MODE_MIST
            MODE_MIDNIGHT -> MODE_MIDNIGHT; MODE_FOREST -> MODE_FOREST
            MODE_OCEAN -> MODE_OCEAN; MODE_DAYLIGHT -> MODE_DAYLIGHT
            else -> if (EchoThemePresets.contains(clean)) clean!! else MODE_SYSTEM
        }
    }

    @JvmStatic fun nextMode(mode: String?): String {
        val options = modeOptions()
        val index = options.indexOf(normalizeMode(mode))
        return options[(if (index < 0) 0 else index + 1) % options.size]
    }

    @JvmStatic fun labelFor(mode: String?): String = when (normalizeMode(mode)) {
        MODE_DYNAMIC -> "Material You"
        MODE_DARK -> "Dark"; MODE_LIGHT -> "Light"
        MODE_AMOLED -> "AMOLED"; MODE_CONTRAST -> "Contrast"
        MODE_GRAPHITE -> "Graphite"; MODE_MIST -> "Mist"
        MODE_MIDNIGHT -> "Midnight"; MODE_FOREST -> "Forest"
        MODE_OCEAN -> "Ocean"; MODE_DAYLIGHT -> "Daylight"
        else -> EchoThemePresets.labelFor(normalizeMode(mode)) ?: "Follow system"
    }

    @JvmStatic fun presetLabelFor(mode: String?): String? =
        EchoThemePresets.labelFor(normalizeMode(mode))

    @JvmStatic fun normalizeAccent(accent: String?): String = when (accent) {
        ACCENT_TEAL -> ACCENT_TEAL; ACCENT_ROSE -> ACCENT_ROSE
        ACCENT_VIOLET -> ACCENT_VIOLET; ACCENT_AMBER -> ACCENT_AMBER
        ACCENT_EMERALD -> ACCENT_EMERALD; ACCENT_CYAN -> ACCENT_CYAN
        ACCENT_LIME -> ACCENT_LIME; ACCENT_RED -> ACCENT_RED
        ACCENT_INDIGO -> ACCENT_INDIGO; ACCENT_PINE -> ACCENT_PINE
        ACCENT_PEACH -> ACCENT_PEACH
        else -> ACCENT_BLUE
    }

    @JvmStatic fun nextAccent(accent: String?): String = when (normalizeAccent(accent)) {
        ACCENT_BLUE -> ACCENT_TEAL; ACCENT_TEAL -> ACCENT_ROSE
        ACCENT_ROSE -> ACCENT_VIOLET; ACCENT_VIOLET -> ACCENT_AMBER
        ACCENT_AMBER -> ACCENT_EMERALD; ACCENT_EMERALD -> ACCENT_CYAN
        ACCENT_CYAN -> ACCENT_LIME; ACCENT_LIME -> ACCENT_RED
        ACCENT_RED -> ACCENT_INDIGO; ACCENT_INDIGO -> ACCENT_PINE
        ACCENT_PINE -> ACCENT_PEACH
        else -> ACCENT_BLUE
    }

    @JvmStatic fun labelForAccent(accent: String?): String = when (normalizeAccent(accent)) {
        ACCENT_TEAL -> "Teal"; ACCENT_ROSE -> "Rose"
        ACCENT_VIOLET -> "Violet"; ACCENT_AMBER -> "Amber"
        ACCENT_EMERALD -> "Emerald"; ACCENT_CYAN -> "Cyan"
        ACCENT_LIME -> "Lime"; ACCENT_RED -> "Red"
        ACCENT_INDIGO -> "Indigo"; ACCENT_PINE -> "Pine"
        ACCENT_PEACH -> "Peach"
        else -> "Blue"
    }

    // Java interop helpers
    @JvmStatic fun isLight(context: Context): Boolean = !isDark(context)
    @JvmStatic fun isDarkMode(context: Context): Boolean = isDark(context)
    @JvmStatic fun backgroundArgb(context: Context): Int = paletteForContext(context).backgroundArgb
    @JvmStatic fun surfaceArgb(context: Context): Int = paletteForContext(context).surfaceArgb
    @JvmStatic fun textArgb(context: Context): Int = paletteForContext(context).textArgb
    @JvmStatic fun mutedArgb(context: Context): Int = paletteForContext(context).mutedArgb
    @JvmStatic fun panelArgb(context: Context): Int = paletteForContext(context).panel.toArgb()
    @JvmStatic fun surfaceVariantArgb(context: Context): Int = paletteForContext(context).surfaceVariant.toArgb()
    @JvmStatic fun accentArgb(context: Context): Int = paletteForContext(context).accentArgb
    @JvmStatic fun accentSoftArgb(context: Context): Int = paletteForContext(context).accentSoft.toArgb()
    @JvmStatic fun accentStrongArgb(context: Context): Int = paletteForContext(context).accentStrong.toArgb()
    @JvmStatic fun secondaryArgb(context: Context): Int = paletteForContext(context).secondary.toArgb()
    @JvmStatic fun backgroundAltArgb(context: Context): Int = paletteForContext(context).backgroundAlt.toArgb()
    @JvmStatic fun onAccentArgb(context: Context): Int = paletteForContext(context).onAccent.toArgb()
    @JvmStatic fun borderArgb(context: Context): Int = paletteForContext(context).border.toArgb()

    // ── The single Compose entry point ──────────────────────────────────────

    /**
     * Compute the active palette for the current mode/accent/dark state. Heavy: builds a full
     * [EchoPalette] with many [Color] objects and blend math. Callers should prefer [colors],
     * which caches this behind a [remember] + [CompositionLocal] so the whole tree shares one
     * instance instead of recomputing on every recomposition (14+ call sites per screen).
     */
    @Composable
    private fun computeColors(): EchoPalette {
        val mode   = normalizeMode(modeState.value)
        val accent = accentState.value
        val context = LocalContext.current
        val systemDark = isSystemInDarkTheme()
        val dark = when (mode) {
            MODE_DARK -> true; MODE_LIGHT -> false
            MODE_AMOLED -> true; MODE_CONTRAST -> true
            MODE_GRAPHITE -> true; MODE_MIST -> false
            MODE_MIDNIGHT -> true; MODE_FOREST -> true
            MODE_OCEAN -> true; MODE_DAYLIGHT -> false
            else -> systemDark
        }
        return remember(mode, accent, dark) {
            if (mode == MODE_DYNAMIC) {
                dynamicPalette(context, dark)?.let { return@remember it }
            }
            paletteForMode(mode, dark, accent)
        }
    }

    @Composable
    fun colors(): EchoPalette {
        return LocalEchoPalette.current ?: computeColors()
    }

    /**
     * Wrap every ComposeView's setContent with this to get consistent theming.
     * Usage: EchoTheme { YourScreen(...) }
     */
    @Composable
    fun EchoTheme(content: @Composable () -> Unit) {
        val p = computeColors()
        val scheme = remember(p) { colorSchemeFrom(p) }
        val typography = remember {
            Typography(
                displayLarge = EchoTypography.display,
                headlineLarge = EchoTypography.headline,
                titleLarge = EchoTypography.title,
                bodyLarge = EchoTypography.body,
                bodyMedium = EchoTypography.bodyMedium,
                labelLarge = EchoTypography.label,
                bodySmall = EchoTypography.caption,
                labelSmall = EchoTypography.small
            )
        }
        val shapes = remember {
            Shapes(
                extraSmall = EchoShapes.small,
                small = EchoShapes.small,
                medium = EchoShapes.medium,
                large = EchoShapes.large,
                extraLarge = EchoShapes.full
            )
        }
        val accentRipple = remember(p.accent) { ripple(color = p.accent) }
        val accentRippleConfig = remember(p.accent) {
            androidx.compose.material3.RippleConfiguration(color = p.accent)
        }
        CompositionLocalProvider(
            LocalEchoPalette provides p
        ) {
            MaterialTheme(
                colorScheme = scheme,
                typography = typography,
                shapes = shapes
            ) {
                CompositionLocalProvider(
                    LocalIndication provides accentRipple,
                    androidx.compose.material3.LocalRippleConfiguration provides accentRippleConfig
                ) {
                    content()
                }
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun isDark(context: Context): Boolean = when (normalizeMode(modeState.value)) {
        MODE_DARK -> true; MODE_LIGHT -> false
        MODE_AMOLED -> true; MODE_CONTRAST -> true
        MODE_GRAPHITE -> true; MODE_MIST -> false
        MODE_MIDNIGHT -> true; MODE_FOREST -> true
        MODE_OCEAN -> true; MODE_DAYLIGHT -> false
        else -> {
            val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun paletteForContext(context: Context): EchoPalette {
        val mode = normalizeMode(modeState.value)
        if (mode == MODE_DYNAMIC) {
            dynamicPalette(context, isDark(context))?.let { return it }
        }
        return paletteForMode(mode, isDark(context), accentState.value)
    }

    /**
     * Material You: derive a full [EchoPalette] from the device's wallpaper-based dynamic color
     * scheme (Android 12 / API 31+). Returns null on older devices so callers fall back to the
     * curated palettes.
     */
    private fun dynamicPalette(context: Context, dark: Boolean): EchoPalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        val scheme = if (dark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        return EchoPalette(
            background = scheme.background,
            surface = scheme.surface,
            surfaceVariant = scheme.surfaceVariant,
            panel = scheme.surfaceVariant,
            accent = scheme.primary,
            accentSoft = scheme.primary.copy(alpha = if (dark) 0.20f else 0.14f),
            text = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            highlight = scheme.primary.copy(alpha = 0.12f),
            border = scheme.outlineVariant,
            onAccent = scheme.onPrimary,
            backgroundAlt = scheme.surface,
            backgroundDeep = scheme.background,
            heading = scheme.onSurface,
            subtle = scheme.onSurfaceVariant,
            accentStrong = scheme.primary,
            secondary = scheme.secondary,
            success = scheme.tertiary,
            shadow = Color.Black
        )
    }

    private fun paletteForMode(mode: String, dark: Boolean, accent: String): EchoPalette {
        val normalized = normalizeMode(mode)
        EchoThemePresets.paletteFor(normalized, dark)?.let { return it }
        return when (normalized) {
            MODE_AMOLED   -> amoledPalette(accent)
            MODE_CONTRAST -> contrastPalette(accent)
            MODE_GRAPHITE -> graphitePalette(accent)
            MODE_MIST     -> mistPalette(accent)
            MODE_MIDNIGHT -> midnightPalette(accent)
            MODE_FOREST   -> forestPalette(accent)
            MODE_OCEAN    -> oceanPalette(accent)
            MODE_DAYLIGHT -> daylightPalette(accent)
            else          -> defaultPalette(dark, accent)
        }
    }

    private fun colorSchemeFrom(p: EchoPalette): ColorScheme = when {
        // dark scheme
        p.background.red < 0.3f && p.background.green < 0.3f && p.background.blue < 0.3f ->
            darkColorScheme(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.accent,
                secondary = p.muted,
                onSecondary = p.onAccent,
                secondaryContainer = p.surfaceVariant,
                onSecondaryContainer = p.text,
                background = p.background,
                onBackground = p.text,
                surface = p.surface,
                onSurface = p.text,
                surfaceVariant = p.surfaceVariant,
                onSurfaceVariant = p.muted,
                outline = p.border,
                outlineVariant = p.border,
                error = AccentPalettes.darkAccent(ACCENT_RED),
                onError = Color.White
            )
        else ->
            lightColorScheme(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.accent,
                secondary = p.muted,
                onSecondary = p.onAccent,
                secondaryContainer = p.surfaceVariant,
                onSecondaryContainer = p.text,
                background = p.background,
                onBackground = p.text,
                surface = p.surface,
                onSurface = p.text,
                surfaceVariant = p.surfaceVariant,
                onSurfaceVariant = p.muted,
                outline = p.border,
                outlineVariant = p.border,
                error = AccentPalettes.lightAccent(ACCENT_RED),
                onError = Color.White
            )
    }

    // ── Palette definitions ─────────────────────────────────────────────────

    private fun defaultPalette(dark: Boolean, accent: String): EchoPalette {
        val a = AccentPalettes.accent(dark, accent)
        return if (dark) EchoPalette(
            background = blendWithAccent(Color(0xFF0C1018), a, 0.16f),
            surface = blendWithAccent(Color(0xFF141820), a, 0.14f),
            surfaceVariant = blendWithAccent(Color(0xFF1A2028), a, 0.15f),
            panel = blendWithAccent(Color(0xFF1E2530), a, 0.14f),
            accent = a, accentSoft = a.copy(alpha = 0.18f),
            text = Color(0xFFE4E8F0), muted = Color(0xFF9AA3B4),
            highlight = a.copy(alpha = 0.13f),
            border = blendWithAccent(Color(0xFF283040), a, 0.14f),
            onAccent = Color.White,
            backgroundAlt = blendWithAccent(Color(0xFF101620), a, 0.18f)
        ) else EchoPalette(
            background = blendWithAccent(Color(0xFFFBFCFF), a, 0.03f),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = blendWithAccent(Color(0xFFF4F7FF), a, 0.05f),
            panel = blendWithAccent(Color(0xFFEDF1FA), a, 0.05f),
            accent = a, accentSoft = a.copy(alpha = 0.10f),
            text = Color(0xFF2A3040), muted = Color(0xFF8892A8),
            highlight = a.copy(alpha = 0.08f),
            border = blendWithAccent(Color(0xFFE6EAF4), a, 0.06f),
            onAccent = Color.White,
            backgroundAlt = Color(0xFFFDFEFF)
        )
    }

    /** Blend a base color toward the accent to tint backgrounds with the theme color. */
    private fun blendWithAccent(base: Color, accent: Color, ratio: Float): Color {
        val inv = 1f - ratio
        return Color(
            red = base.red * inv + accent.red * ratio,
            green = base.green * inv + accent.green * ratio,
            blue = base.blue * inv + accent.blue * ratio,
            alpha = 1f
        )
    }

    private fun amoledPalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF000000), surface = Color(0xFF08090C),
            surfaceVariant = Color(0xFF101318), panel = Color(0xFF151820), accent = a,
            accentSoft = a.copy(alpha = 0.18f), text = Color(0xFFF5F7FA),
            muted = Color(0xFFB2BDCC), highlight = Color(0xFF1E2330),
            border = Color(0xFF262A36), onAccent = Color.Black)
    }

    private fun contrastPalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF050607), surface = Color(0xFF11131A),
            surfaceVariant = Color(0xFF191D27), panel = Color(0xFF242B3A), accent = a,
            accentSoft = a.copy(alpha = 0.2f), text = Color(0xFFFFFFFF),
            muted = Color(0xFFD0DAE8), highlight = Color(0xFF30394D),
            border = Color(0xFF353D50), onAccent = Color.Black)
    }

    private fun graphitePalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF0E0F10), surface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFF222528), panel = Color(0xFF292D31), accent = a,
            accentSoft = a.copy(alpha = 0.18f), text = Color(0xFFF1F3F5),
            muted = Color(0xFFAAB1B8), highlight = Color(0xFF30363B),
            border = Color(0xFF353A3F), onAccent = Color.Black)
    }

    private fun mistPalette(accent: String): EchoPalette {
        val a = AccentPalettes.lightAccent(accent)
        return EchoPalette(background = Color(0xFFF2F5F4), surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EDEB), panel = Color(0xFFE1E8E5), accent = a,
            accentSoft = a.copy(alpha = 0.12f), text = Color(0xFF17201D),
            muted = Color(0xFF5F6D68), highlight = Color(0xFFDDE7E3),
            border = Color(0xFFD5DCD9), onAccent = Color.White)
    }

    private fun midnightPalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF080A12), surface = Color(0xFF111827),
            surfaceVariant = Color(0xFF1A2238), panel = Color(0xFF1E293B), accent = a,
            accentSoft = a.copy(alpha = 0.18f), text = Color(0xFFEAF0FF),
            muted = Color(0xFF9AA8C7), highlight = Color(0xFF263449),
            border = Color(0xFF2D3B54), onAccent = Color.Black)
    }

    private fun forestPalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF07110D), surface = Color(0xFF102019),
            surfaceVariant = Color(0xFF182B22), panel = Color(0xFF1C3328), accent = a,
            accentSoft = a.copy(alpha = 0.18f), text = Color(0xFFE8F3ED),
            muted = Color(0xFFA1B7AA), highlight = Color(0xFF274537),
            border = Color(0xFF2C4D3E), onAccent = Color.Black)
    }

    private fun oceanPalette(accent: String): EchoPalette {
        val a = AccentPalettes.darkAccent(accent)
        return EchoPalette(background = Color(0xFF061017), surface = Color(0xFF0E2230),
            surfaceVariant = Color(0xFF152E40), panel = Color(0xFF17384B), accent = a,
            accentSoft = a.copy(alpha = 0.18f), text = Color(0xFFE6F5FB),
            muted = Color(0xFF9BC0CD), highlight = Color(0xFF204A61),
            border = Color(0xFF254F68), onAccent = Color.Black)
    }

    private fun daylightPalette(accent: String): EchoPalette {
        val a = AccentPalettes.lightAccent(accent)
        return EchoPalette(background = Color(0xFFFBFCF8), surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F3ED), panel = Color(0xFFEAF0E7), accent = a,
            accentSoft = a.copy(alpha = 0.12f), text = Color(0xFF18201A),
            muted = Color(0xFF667268), highlight = Color(0xFFE1E9DC),
            border = Color(0xFFD8DFD4), onAccent = Color.White)
    }
}

// ── Accent color resolver ───────────────────────────────────────────────────

private object AccentPalettes {
    fun accent(dark: Boolean, a: String): Color = if (dark) darkAccent(a) else lightAccent(a)

    fun darkAccent(a: String): Color = when (EchoTheme.normalizeAccent(a)) {
        EchoTheme.ACCENT_TEAL -> Color(0xFF5CC9BA); EchoTheme.ACCENT_ROSE -> Color(0xFFFF7093)
        EchoTheme.ACCENT_VIOLET -> Color(0xFFA78BFA); EchoTheme.ACCENT_AMBER -> Color(0xFFF5C263)
        EchoTheme.ACCENT_EMERALD -> Color(0xFF5DD490); EchoTheme.ACCENT_CYAN -> Color(0xFF55DEF6)
        EchoTheme.ACCENT_LIME -> Color(0xFFB2E05A); EchoTheme.ACCENT_RED -> Color(0xFFFF7A72)
        EchoTheme.ACCENT_INDIGO -> Color(0xFF82A5FF); EchoTheme.ACCENT_PINE -> Color(0xFF72CFB0)
        EchoTheme.ACCENT_PEACH -> Color(0xFFFFA58A)
        else -> Color(0xFF4B8EFF)
    }

    fun lightAccent(a: String): Color = when (EchoTheme.normalizeAccent(a)) {
        EchoTheme.ACCENT_TEAL -> Color(0xFF18A898); EchoTheme.ACCENT_ROSE -> Color(0xFFE84878)
        EchoTheme.ACCENT_VIOLET -> Color(0xFF7C5AFF); EchoTheme.ACCENT_AMBER -> Color(0xFFD68800)
        EchoTheme.ACCENT_EMERALD -> Color(0xFF24B068); EchoTheme.ACCENT_CYAN -> Color(0xFF10A0C0)
        EchoTheme.ACCENT_LIME -> Color(0xFF7AA800); EchoTheme.ACCENT_RED -> Color(0xFFE04848)
        EchoTheme.ACCENT_INDIGO -> Color(0xFF4A78F0); EchoTheme.ACCENT_PINE -> Color(0xFF1A9878)
        EchoTheme.ACCENT_PEACH -> Color(0xFFE07050)
        else -> Color(0xFF4D8EFF)
    }
}
