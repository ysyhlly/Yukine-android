package app.yukine.ui

import androidx.compose.ui.unit.dp
import app.yukine.SettingsEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun contentActionsRemoveTheActualBackActionWithoutDroppingEarlierContent() {
        val first = SettingsAction("First", Runnable {})
        val back = SettingsAction("Back", Runnable {}, isBack = true)
        val last = SettingsAction("Last", Runnable {})

        assertEquals(listOf(first, last), settingsContentActions(listOf(first, back, last)))
    }

    @Test
    fun contentActionsReuseTheOriginalListWhenThereIsNoBackAction() {
        val actions = listOf(SettingsAction("Action", Runnable {}))

        assertSame(actions, settingsContentActions(actions))
    }

    @Test
    fun actionSectionsKeepAdjacentRowsInOneSection() {
        val playback = SettingsAction("Speed", Runnable {}, section = "Audio")
        val volume = SettingsAction("Volume", Runnable {}, section = "Audio")
        val restore = SettingsAction("Restore", Runnable {}, section = "Behavior")

        val sections = settingsActionSections(listOf(playback, volume, restore))

        assertEquals(listOf("Audio", "Behavior"), sections.map { it.title })
        assertEquals(listOf(playback, volume), sections[0].actions)
        assertEquals(listOf(restore), sections[1].actions)
    }

    @Test
    fun actionCardsAreSeparateByDefaultAndGroupedOnlyInCompactMode() {
        val first = SettingsAction("First", Runnable {})
        val second = SettingsAction("Second", Runnable {})

        assertEquals(
            listOf(listOf(first), listOf(second)),
            settingsActionCardGroups(listOf(first, second), compact = false)
        )
        assertEquals(
            listOf(listOf(first, second)),
            settingsActionCardGroups(listOf(first, second), compact = true)
        )
    }

    @Test
    fun actionSectionsDoNotMergeRepeatedNonAdjacentSections() {
        val first = SettingsAction("First", Runnable {}, section = "A")
        val second = SettingsAction("Second", Runnable {}, section = "B")
        val third = SettingsAction("Third", Runnable {}, section = "A")

        assertEquals(
            listOf("A", "B", "A"),
            settingsActionSections(listOf(first, second, third)).map { it.title }
        )
    }

    @Test
    fun highlightedEntryResolvesItsLazyListSectionAfterHeaderCards() {
        val target = SettingsAction(
            "Theme",
            Runnable {},
            section = "Appearance",
            entryId = SettingsEntryId.Theme
        )
        val sections = settingsActionSections(
            listOf(
                SettingsAction("Back", Runnable {}, section = "Navigation"),
                target
            )
        )

        assertEquals(
            4,
            settingsHighlightedSectionItemIndex(
                sections = sections,
                highlightedEntryId = SettingsEntryId.Theme,
                hasSearch = false,
                hasIssues = true,
                hasMetrics = true
            )
        )
        assertEquals(
            null,
            settingsHighlightedSectionItemIndex(
                sections = sections,
                highlightedEntryId = SettingsEntryId.StreamingGateway,
                hasSearch = false,
                hasIssues = false,
                hasMetrics = false
            )
        )
    }

    @Test
    fun actionAccessibilityDescriptionIncludesCurrentValueAndExplanation() {
        val action = SettingsAction(
            label = "Playback speed",
            onClick = Runnable {},
            description = "Default playback rate",
            value = "1.25x"
        )

        assertEquals(
            "Playback speed. 1.25x. Default playback rate",
            settingsActionContentDescription(action)
        )
    }

    @Test
    fun cardDensityMatchesLibraryRowsByDefaultAndCompactsWhenEnabled() {
        val libraryStyle = settingsCardDensityTokens(compact = false)
        val compact = settingsCardDensityTokens(compact = true)

        assertEquals(14.dp, libraryStyle.horizontalPadding)
        assertEquals(12.dp, libraryStyle.verticalPadding)
        assertEquals(10.dp, libraryStyle.sectionSpacing)
        assertEquals(12.dp, compact.horizontalPadding)
        assertEquals(10.dp, compact.verticalPadding)
        assertEquals(6.dp, compact.sectionSpacing)
    }
}
