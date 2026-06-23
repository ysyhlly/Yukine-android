package app.yukine.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoThemePresetContractTest {
    @Test
    fun presetIdsMatchDesktopThemePreferencesOrder() {
        val desktopPresetOrder = arrayOf(
            "classic",
            "echoTwilight",
            "sakuraMilk",
            "peachSoda",
            "mintCandy",
            "berryDream",
            "matchaCream",
            "lemonMochi",
            "cottonCloud",
            "melonCream",
            "seaSaltJelly",
            "caramelPudding",
            "neonCandy",
            "nyanCat",
            "childrenDoodle",
            "wisteriaBubble",
            "strawberryCookie",
            "graphiteAurora",
            "amberNoir",
            "oceanStudio",
            "rosewoodVinyl",
            "darkSideMoon",
            "shibuyaNight",
            "kyotoKurenai",
            "ukiyoIndigo",
            "fujiSnow",
            "matsuriLantern",
            "ginzaNoir",
            "frostJazz",
            "FINAL"
        )

        assertArrayEquals(desktopPresetOrder, EchoThemePresets.ids)
        desktopPresetOrder.forEach { preset ->
            assertTrue("Missing preset: $preset", EchoThemePresets.contains(preset))
        }
    }

    @Test
    fun finalPresetCarriesDesktopPaletteValues() {
        val light = EchoThemePresets.paletteFor("FINAL", dark = false)
        val dark = EchoThemePresets.paletteFor("FINAL", dark = true)

        checkNotNull(light)
        checkNotNull(dark)
        assertEquals(0xFFEFEDE7.toInt(), light.background.toArgb())
        assertEquals(0xFFFFFDF7.toInt(), light.surface.toArgb())
        assertEquals(0xFF171511.toInt(), light.heading.toArgb())
        assertEquals(0xFFB78943.toInt(), light.accent.toArgb())
        assertEquals(0xFF0F0E0C.toInt(), dark.background.toArgb())
        assertEquals(0xFF23201C.toInt(), dark.surface.toArgb())
        assertEquals(0xFFFFF7E8.toInt(), dark.heading.toArgb())
        assertEquals(0xFFD0A05A.toInt(), dark.accent.toArgb())
    }

    @Test
    fun themeModeOptionsExposeLegacyModesAndDesktopPresets() {
        val options = EchoTheme.modeOptions().toList()

        assertTrue(options.contains(EchoTheme.MODE_SYSTEM))
        assertTrue(options.contains(EchoTheme.MODE_DARK))
        assertTrue(options.contains(EchoTheme.MODE_LIGHT))
        assertTrue(options.contains(EchoTheme.MODE_AMOLED))
        EchoThemePresets.ids.forEach { preset ->
            assertTrue("Mode options missing preset: $preset", options.contains(preset))
        }
    }

    @Test
    fun primaryThemeOptionsStayFocusedWhileAdvancedOptionsKeepLegacyModes() {
        val primary = EchoTheme.primaryModeOptions().toList()
        val advanced = EchoTheme.advancedModeOptions().toList()

        assertEquals(
            listOf(
                EchoTheme.MODE_SYSTEM,
                EchoTheme.MODE_LIGHT,
                EchoTheme.MODE_DARK,
                EchoTheme.MODE_AMOLED
            ),
            primary
        )
        assertTrue(advanced.contains(EchoTheme.MODE_CONTRAST))
        assertTrue(advanced.contains(EchoTheme.MODE_GRAPHITE))
        EchoThemePresets.ids.forEach { preset ->
            assertTrue("Advanced mode options missing preset: $preset", advanced.contains(preset))
        }
        primary.forEach { mode ->
            assertTrue("Advanced options should not duplicate primary mode: $mode", !advanced.contains(mode))
        }
    }

    @Test
    fun cjkFontResourceIsBundledForGlobalTypography() {
        assertEquals(0f, EchoTypography.display.letterSpacing.value)
        assertEquals(EchoTypography.display.fontFamily, EchoTypography.body.fontFamily)
        assertNotEquals(null, EchoTypography.display.fontFamily)
    }
}
