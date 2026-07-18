package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackStackTest {
    @Test
    fun groupsReturnToHome() {
        val groups = listOf(
            SettingsPage.AppearanceGroup,
            SettingsPage.PlaybackGroup,
            SettingsPage.LibraryGroup,
            SettingsPage.LyricsGroup,
            SettingsPage.SourcesGroup,
            SettingsPage.Downloads,
            SettingsPage.AboutGroup
        )

        groups.forEach { page ->
            assertEquals(SettingsPage.Home, SettingsBackStack.parent(page))
        }
    }

    @Test
    fun pagesReturnToTheirGroups() {
        assertEquals(SettingsPage.AppearanceGroup, SettingsBackStack.parent(SettingsPage.PageBackground))
        assertEquals(SettingsPage.AppearanceGroup, SettingsBackStack.parent(SettingsPage.NowPlayingGestures))
        assertEquals(SettingsPage.AppearanceGroup, SettingsBackStack.parent(SettingsPage.ShareStyle))
        assertEquals(SettingsPage.Appearance, SettingsBackStack.parent(SettingsPage.AdvancedTheme))
        assertEquals(SettingsPage.PlaybackGroup, SettingsBackStack.parent(SettingsPage.PlaybackSpeed))
        assertEquals(SettingsPage.LyricsGroup, SettingsBackStack.parent(SettingsPage.FloatingLyrics))
        assertEquals(SettingsPage.SourcesGroup, SettingsBackStack.parent(SettingsPage.StreamingAudioQuality))
        assertEquals(SettingsPage.LibraryGroup, SettingsBackStack.parent(SettingsPage.Library))
    }

}
