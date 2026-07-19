package app.yukine.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoThemeContrastTest {
    @Test
    fun everyPresetProvidesReadableMaterialTextRoles() {
        EchoThemePresets.all.forEach { preset ->
            listOf(
                "light" to preset.light.toPalette(),
                "dark" to preset.dark.toPalette()
            ).forEach { (tone, rawPalette) ->
                val palette = rawPalette.withAccessibleContentColors()
                val scheme = EchoTheme.colorSchemeFrom(palette)
                assertReadableRoles("${preset.id}/$tone", scheme)
                assertOpaqueContainers("${preset.id}/$tone", scheme)
                assertReadable("$tone palette onAccent", palette.onAccent, palette.accent)
            }
        }
    }

    @Test
    fun dynamicAccentExtremesProvideReadableMaterialTextRoles() {
        val accents = listOf(
            Color.Black,
            Color.White,
            Color(0xFF777777),
            Color(0xFFFFEB3B),
            Color(0xFF4D8EFF),
            Color(0xFFE04848)
        )

        accents.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val palette = samplePalette(accent, dark).withAccessibleContentColors()
                val scheme = EchoTheme.colorSchemeFrom(palette)
                assertReadableRoles("accent=$accent dark=$dark", scheme)
                assertOpaqueContainers("accent=$accent dark=$dark", scheme)
            }
        }
    }

    @Test
    fun softAccentIsCompositedBeforeChoosingContainerText() {
        val accent = Color(0xFF4D8EFF)
        val palette = samplePalette(accent, dark = false)
        val scheme = EchoTheme.colorSchemeFrom(palette)

        assertEquals(1f, scheme.primaryContainer.alpha, COLOR_EPSILON)
        assertTrue(contrastRatio(accent, scheme.primaryContainer) < MIN_TEXT_CONTRAST)
        assertReadable(
            "default blue primary container",
            scheme.onPrimaryContainer,
            scheme.primaryContainer
        )
    }

    @Test
    fun errorForegroundsFlipWhenWhiteIsNotReadable() {
        val lightScheme = EchoTheme.colorSchemeFrom(samplePalette(Color(0xFF4D8EFF), dark = false))
        val darkScheme = EchoTheme.colorSchemeFrom(samplePalette(Color(0xFF4B8EFF), dark = true))

        assertEquals(Color.Black, lightScheme.onError)
        assertEquals(Color.Black, darkScheme.onError)
        assertReadable("light error", lightScheme.onError, lightScheme.error)
        assertReadable("dark error", darkScheme.onError, darkScheme.error)
    }

    @Test
    fun readablePreferredColorIsPreserved() {
        val preferred = Color.White

        assertEquals(
            preferred,
            readableContentColor(
                background = Color(0xFF1A1A1A),
                preferred = preferred,
                alternate = Color.Black
            )
        )
    }

    @Test
    fun translucentOverlayUsesItsRenderedColorForContrast() {
        val accent = Color(0xFF4D8EFF)
        val renderedContainer = opaqueComposite(accent.copy(alpha = 0.10f), Color.White)
        val content = readableContentColor(
            background = renderedContainer,
            preferred = accent,
            alternate = Color(0xFF1F2735)
        )

        assertEquals(1f, renderedContainer.alpha, COLOR_EPSILON)
        assertTrue(contrastRatio(accent, renderedContainer) < MIN_TEXT_CONTRAST)
        assertReadable("translucent overlay", content, renderedContainer)
    }

    private fun assertReadableRoles(label: String, scheme: ColorScheme) {
        assertReadable("$label primary", scheme.onPrimary, scheme.primary)
        assertReadable("$label primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertReadable("$label secondary", scheme.onSecondary, scheme.secondary)
        assertReadable("$label secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
        assertReadable("$label background", scheme.onBackground, scheme.background)
        assertReadable("$label surface", scheme.onSurface, scheme.surface)
        assertReadable("$label surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant)
        assertReadable("$label error", scheme.onError, scheme.error)
        assertReadable("$label errorContainer", scheme.onErrorContainer, scheme.errorContainer)
    }

    private fun assertOpaqueContainers(label: String, scheme: ColorScheme) {
        mapOf(
            "primary" to scheme.primary,
            "primaryContainer" to scheme.primaryContainer,
            "secondary" to scheme.secondary,
            "secondaryContainer" to scheme.secondaryContainer,
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "error" to scheme.error,
            "errorContainer" to scheme.errorContainer
        ).forEach { (role, color) ->
            assertEquals("$label $role must be opaque", 1f, color.alpha, COLOR_EPSILON)
        }
    }

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label contrast was $ratio, expected at least $MIN_TEXT_CONTRAST",
            ratio + CONTRAST_EPSILON >= MIN_TEXT_CONTRAST
        )
    }

    private fun samplePalette(accent: Color, dark: Boolean): EchoPalette {
        val background = if (dark) Color(0xFF080C13) else Color(0xFFF2F5FA)
        val surface = if (dark) Color(0xFF181E29) else Color.White
        val surfaceVariant = if (dark) Color(0xFF222A36) else Color(0xFFEAF0F8)
        val text = if (dark) Color(0xFFF2F5FA) else Color(0xFF1F2735)
        val muted = if (dark) Color(0xFFB5BECD) else Color(0xFF606B7D)
        return EchoPalette(
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            panel = surfaceVariant,
            accent = accent,
            accentSoft = accent.copy(alpha = if (dark) 0.18f else 0.10f),
            text = text,
            muted = muted,
            highlight = accent.copy(alpha = if (dark) 0.13f else 0.08f),
            border = muted,
            onAccent = Color.White,
            heading = text,
            accentStrong = accent,
            secondary = accent
        )
    }

    private companion object {
        const val COLOR_EPSILON = 0.0001f
        const val CONTRAST_EPSILON = 0.001f
    }
}
