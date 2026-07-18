package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInformationArchitectureTest {
    @Test
    fun registryExposesSevenOrderedCategoriesAndUniqueEntryIds() {
        assertEquals(
            listOf(
                SettingsCategoryId.PlaybackAudio,
                SettingsCategoryId.LibraryMetadata,
                SettingsCategoryId.SourcesAccountsSync,
                SettingsCategoryId.LyricsSystemDisplay,
                SettingsCategoryId.DownloadsStorageBackup,
                SettingsCategoryId.AppearanceInteraction,
                SettingsCategoryId.SystemPrivacyHelp
            ),
            SettingsInformationArchitecture.categories.map { it.id }
        )

        val ids = SettingsInformationArchitecture.entryIds()
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun searchMatchesEnglishChineseAliasesAndCategoryKeywords() {
        val opened = mutableListOf<Pair<SettingsEntryId, SettingsPage>>()
        val entries = SettingsInformationArchitecture.searchEntries(
            languageMode = AppLanguage.MODE_ENGLISH,
            onOpen = { id, page -> opened += id to page }
        )

        assertTrue(
            filterSettingsSearchEntries(entries, "主题")
                .any { it.id == SettingsEntryId.Theme }
        )
        assertTrue(
            filterSettingsSearchEntries(entries, "gateway")
                .any { it.id == SettingsEntryId.StreamingGateway }
        )
        assertTrue(
            filterSettingsSearchEntries(entries, "appearance interaction")
                .any { it.categoryId == SettingsCategoryId.AppearanceInteraction }
        )
        assertTrue(
            filterSettingsSearchEntries(entries, "外观 交互")
                .any { it.categoryId == SettingsCategoryId.AppearanceInteraction }
        )
        assertTrue(
            filterSettingsSearchEntries(entries, "\u5bbd\u677e")
                .any { it.id == SettingsEntryId.CompactSettingsCards }
        )
        assertTrue(filterSettingsSearchEntries(entries, "no-such-setting").isEmpty())

        entries.first { it.id == SettingsEntryId.AdvancedTheme }.onClick.run()

        assertEquals(listOf(SettingsEntryId.AdvancedTheme to SettingsPage.Appearance), opened)
    }
}
