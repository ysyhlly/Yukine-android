package app.echo.next.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R

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

// ── Typography scale ────────────────────────────────────────────────────────

object EchoTypography {
    private val outfit = FontFamily(
        Font(R.font.outfit, FontWeight.Normal),
        Font(R.font.outfit, FontWeight.Medium),
        Font(R.font.outfit, FontWeight.SemiBold),
        Font(R.font.outfit, FontWeight.Bold)
    )

    val display = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    )
    val headline = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )
    val title = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    )
    val body = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val caption = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val small = TextStyle(
        fontFamily = outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

// ── Shape system ────────────────────────────────────────────────────────────

object EchoShapes {
    val small = RoundedCornerShape(6.dp)
    val medium = RoundedCornerShape(8.dp)
    val large = RoundedCornerShape(12.dp)
    val full = RoundedCornerShape(16.dp)
}

// ── Main theme object (Java-facing state holder) ────────────────────────────

object EchoTheme {
    const val MODE_SYSTEM   = "system"
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

    @JvmStatic fun modeOptions(): Array<String> = arrayOf(
        MODE_SYSTEM,
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

    @JvmStatic fun normalizeMode(mode: String?): String {
        val clean = mode?.trim()
        return when (clean) {
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

    @Composable
    fun colors(): EchoPalette {
        val mode   = modeState.value
        val accent = accentState.value
        val dark = when (mode) {
            MODE_DARK -> true; MODE_LIGHT -> false
            MODE_AMOLED -> true; MODE_CONTRAST -> true
            MODE_GRAPHITE -> true; MODE_MIST -> false
            MODE_MIDNIGHT -> true; MODE_FOREST -> true
            MODE_OCEAN -> true; MODE_DAYLIGHT -> false
            else -> isSystemInDarkTheme()
        }
        return paletteForMode(mode, dark, accent)
    }

    /**
     * Wrap every ComposeView's setContent with this to get consistent theming.
     * Usage: EchoTheme { YourScreen(...) }
     */
    @Composable
    fun EchoTheme(content: @Composable () -> Unit) {
        val p = colors()
        val scheme = colorSchemeFrom(p)
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                displayLarge = EchoTypography.display,
                headlineLarge = EchoTypography.headline,
                titleLarge = EchoTypography.title,
                bodyLarge = EchoTypography.body,
                bodyMedium = EchoTypography.bodyMedium,
                labelLarge = EchoTypography.label,
                bodySmall = EchoTypography.caption,
                labelSmall = EchoTypography.small
            ),
            shapes = Shapes(
                extraSmall = EchoShapes.small,
                small = EchoShapes.small,
                medium = EchoShapes.medium,
                large = EchoShapes.large,
                extraLarge = EchoShapes.full
            )
        ) {
            content()
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

    private fun paletteForContext(context: Context): EchoPalette =
        paletteForMode(modeState.value, isDark(context), accentState.value)

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
            background = Color(0xFF0E1117),
            surface = Color(0xFF161A22),
            surfaceVariant = Color(0xFF1C212C),
            panel = Color(0xFF212836),
            accent = a, accentSoft = a.copy(alpha = 0.18f),
            text = Color(0xFFE4E8F0), muted = Color(0xFF9AA3B4),
            highlight = a.copy(alpha = 0.13f), border = Color(0xFF2A3040), onAccent = Color.White
        ) else EchoPalette(
            background = Color(0xFFF5F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEEF0F4),
            panel = Color(0xFFE6E9F0),
            accent = a, accentSoft = a.copy(alpha = 0.12f),
            text = Color(0xFF191C22), muted = Color(0xFF6B7180),
            highlight = a.copy(alpha = 0.08f), border = Color(0xFFDFE2E8), onAccent = Color.White
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
        EchoTheme.ACCENT_TEAL -> Color(0xFF0D8D80); EchoTheme.ACCENT_ROSE -> Color(0xFFD2386C)
        EchoTheme.ACCENT_VIOLET -> Color(0xFF6D4AFF); EchoTheme.ACCENT_AMBER -> Color(0xFFC57800)
        EchoTheme.ACCENT_EMERALD -> Color(0xFF189A58); EchoTheme.ACCENT_CYAN -> Color(0xFF0588A8)
        EchoTheme.ACCENT_LIME -> Color(0xFF6B9400); EchoTheme.ACCENT_RED -> Color(0xFFD94038)
        EchoTheme.ACCENT_INDIGO -> Color(0xFF3A66E8); EchoTheme.ACCENT_PINE -> Color(0xFF128266)
        EchoTheme.ACCENT_PEACH -> Color(0xFFD96040)
        else -> Color(0xFF256CFF)
    }
}
