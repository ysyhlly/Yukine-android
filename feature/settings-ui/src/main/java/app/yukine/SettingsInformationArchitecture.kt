package app.yukine

import app.yukine.ui.EchoIconKind
import java.util.Locale

enum class SettingsCategoryId {
    PlaybackAudio,
    LibraryMetadata,
    SourcesAccountsSync,
    LyricsSystemDisplay,
    DownloadsStorageBackup,
    AppearanceInteraction,
    SystemPrivacyHelp
}

enum class SettingsEntryId {
    PlaybackSpeed,
    AppVolume,
    AudioEffects,
    ReplayGain,
    PlaybackRestore,
    AudioExclusive,
    SleepTimer,
    LibraryScan,
    ImportAudioFiles,
    ImportAudioFolder,
    IdentityRebuild,
    RestoreHiddenLibraryItems,
    StreamingProviders,
    LuoxueSourceManager,
    LuoxueSourceImport,
    StreamingAudioQuality,
    RemoteMusicSources,
    WebDav,
    StreamingGateway,
    OnlineLyrics,
    ReloadLyrics,
    ImportCurrentLyrics,
    ImportLyricsDirectory,
    LyricsImportReport,
    StatusBarLyrics,
    SystemMediaLyricsTitle,
    FloatingLyrics,
    LyricsOffset,
    DownloadManager,
    BackupExport,
    BackupImport,
    Theme,
    AdvancedTheme,
    Accent,
    Language,
    PageBackground,
    HomeDashboardLayout,
    CompactSettingsCards,
    BackgroundBlur,
    BackgroundBlurIntensity,
    GlassBlur,
    GlassBlurIntensity,
    GlassSurfaceOpacity,
    NowPlayingGestures,
    ShareStyle,
    AudioPermission,
    NotificationPermission,
    AppVersion,
    Community,
    DebugPrompts
}

enum class SettingsIssueId {
    AudioPermission,
    NotificationPermission,
    OverlayPermission,
    PlaybackService
}

data class SettingsCategorySpec(
    val id: SettingsCategoryId,
    val page: SettingsPage,
    val groupKey: String,
    val icon: EchoIconKind
)

data class SettingsSearchEntry(
    val id: SettingsEntryId,
    val categoryId: SettingsCategoryId,
    val title: String,
    val description: String,
    val categoryLabel: String,
    val keywords: List<String>,
    val icon: EchoIconKind,
    val onClick: Runnable
)

data class SettingsIssue(
    val id: SettingsIssueId,
    val title: String,
    val description: String,
    val icon: EchoIconKind,
    val actionLabel: String = "",
    val onClick: Runnable? = null
)

private data class SettingsEntrySpec(
    val id: SettingsEntryId,
    val categoryId: SettingsCategoryId,
    val titleKey: String,
    val descriptionKey: String? = null,
    val keywords: List<String> = emptyList(),
    val icon: EchoIconKind,
    val page: SettingsPage? = null
)

object SettingsInformationArchitecture {
    val categories: List<SettingsCategorySpec> = listOf(
        SettingsCategorySpec(
            SettingsCategoryId.PlaybackAudio,
            SettingsPage.PlaybackGroup,
            "playback",
            EchoIconKind.Gauge
        ),
        SettingsCategorySpec(
            SettingsCategoryId.LibraryMetadata,
            SettingsPage.LibraryGroup,
            "library",
            EchoIconKind.Library
        ),
        SettingsCategorySpec(
            SettingsCategoryId.SourcesAccountsSync,
            SettingsPage.SourcesGroup,
            "sources",
            EchoIconKind.Network
        ),
        SettingsCategorySpec(
            SettingsCategoryId.LyricsSystemDisplay,
            SettingsPage.LyricsGroup,
            "lyrics",
            EchoIconKind.Lyrics
        ),
        SettingsCategorySpec(
            SettingsCategoryId.DownloadsStorageBackup,
            SettingsPage.Downloads,
            "storage",
            EchoIconKind.Download
        ),
        SettingsCategorySpec(
            SettingsCategoryId.AppearanceInteraction,
            SettingsPage.AppearanceGroup,
            "appearance",
            EchoIconKind.Palette
        ),
        SettingsCategorySpec(
            SettingsCategoryId.SystemPrivacyHelp,
            SettingsPage.AboutGroup,
            "about",
            EchoIconKind.Info
        )
    )

    private val entries: List<SettingsEntrySpec> = listOf(
        entry(SettingsEntryId.PlaybackSpeed, SettingsCategoryId.PlaybackAudio, "playback.speed", "speed.description", EchoIconKind.Gauge, "speed", "\u901f\u5ea6"),
        entry(SettingsEntryId.AppVolume, SettingsCategoryId.PlaybackAudio, "app.volume", "volume.description", EchoIconKind.Volume, "volume", "\u97f3\u91cf"),
        entry(SettingsEntryId.AudioEffects, SettingsCategoryId.PlaybackAudio, "audio.effects", "audio.effects.hint", EchoIconKind.Gauge, "equalizer", "eq", "\u5747\u8861\u5668", "\u97f3\u6548"),
        entry(SettingsEntryId.ReplayGain, SettingsCategoryId.PlaybackAudio, "replay.gain", "replay.gain.hint", EchoIconKind.Gauge, "replaygain", "normalization", "\u97f3\u91cf\u5747\u8861"),
        entry(SettingsEntryId.PlaybackRestore, SettingsCategoryId.PlaybackAudio, "playback.restore", "playback.restore.hint", EchoIconKind.Refresh, "queue restore", "\u6062\u590d\u961f\u5217"),
        entry(SettingsEntryId.AudioExclusive, SettingsCategoryId.PlaybackAudio, "audio.exclusive", "audio.exclusive.hint", EchoIconKind.Gauge, "audio focus", "\u97f3\u9891\u7126\u70b9"),
        entry(SettingsEntryId.SleepTimer, SettingsCategoryId.PlaybackAudio, "sleep.timer", "sleep.timer.description", EchoIconKind.Timer, "timer", "\u5b9a\u65f6"),

        entryWithKeywords(SettingsEntryId.LibraryScan, SettingsCategoryId.LibraryMetadata, "scan.library", icon = EchoIconKind.Sync, keywords = arrayOf("rescan", "\u626b\u63cf")),
        entryWithKeywords(SettingsEntryId.ImportAudioFiles, SettingsCategoryId.LibraryMetadata, "import.audio.files", icon = EchoIconKind.Import, keywords = arrayOf("file", "\u6587\u4ef6")),
        entryWithKeywords(SettingsEntryId.ImportAudioFolder, SettingsCategoryId.LibraryMetadata, "import.audio.folder", icon = EchoIconKind.Folder, keywords = arrayOf("folder", "directory", "\u6587\u4ef6\u5939", "\u76ee\u5f55")),
        entryWithKeywords(SettingsEntryId.IdentityRebuild, SettingsCategoryId.LibraryMetadata, "identity.backfill.rebuild", icon = EchoIconKind.Refresh, keywords = arrayOf("metadata", "identity", "match", "\u5143\u6570\u636e", "\u5339\u914d")),
        entryWithKeywords(SettingsEntryId.RestoreHiddenLibraryItems, SettingsCategoryId.LibraryMetadata, "library.hidden.restore.all", icon = EchoIconKind.Refresh, keywords = arrayOf("hidden", "\u9690\u85cf", "\u6062\u590d")),

        entry(SettingsEntryId.StreamingProviders, SettingsCategoryId.SourcesAccountsSync, "streaming.providers.manage", "streaming.providers.manage.hint", EchoIconKind.Network, "account", "provider", "\u8d26\u53f7", "\u5e73\u53f0"),
        entry(SettingsEntryId.LuoxueSourceManager, SettingsCategoryId.SourcesAccountsSync, "streaming.lx.source.manager", "streaming.lx.source.manager.hint", EchoIconKind.Network, "lx", "luoxue", "\u6d1b\u96ea"),
        entry(SettingsEntryId.LuoxueSourceImport, SettingsCategoryId.SourcesAccountsSync, "streaming.lx.import.source", "streaming.lx.import.hint", EchoIconKind.Import, "script", "js", "\u811a\u672c"),
        entry(SettingsEntryId.StreamingAudioQuality, SettingsCategoryId.SourcesAccountsSync, "streaming.audio.quality", "streaming.audio.quality.hint", EchoIconKind.Gauge, "quality", "lossless", "\u97f3\u8d28", "\u65e0\u635f"),
        entry(SettingsEntryId.RemoteMusicSources, SettingsCategoryId.SourcesAccountsSync, "remote.music.sources", "remote.music.sources.hint", EchoIconKind.Folder, "m3u", "stream", "\u8fdc\u7a0b\u97f3\u6e90"),
        entry(SettingsEntryId.WebDav, SettingsCategoryId.SourcesAccountsSync, "webdav", "settings.sources.webdav.hint", EchoIconKind.Folder, "cloud", "nas", "\u7f51\u76d8"),
        entry(SettingsEntryId.StreamingGateway, SettingsCategoryId.SourcesAccountsSync, "streaming.gateway", "streaming.gateway.hint", EchoIconKind.Network, "gateway", "proxy", "\u7f51\u5173", "\u4ee3\u7406"),

        entryWithKeywords(SettingsEntryId.OnlineLyrics, SettingsCategoryId.LyricsSystemDisplay, "online.lyrics", icon = EchoIconKind.Lyrics, keywords = arrayOf("lyrics", "\u5728\u7ebf\u6b4c\u8bcd")),
        entryWithKeywords(SettingsEntryId.ReloadLyrics, SettingsCategoryId.LyricsSystemDisplay, "reload.lyrics", icon = EchoIconKind.Sync, keywords = arrayOf("refresh", "\u5237\u65b0\u6b4c\u8bcd")),
        entry(SettingsEntryId.StatusBarLyrics, SettingsCategoryId.LyricsSystemDisplay, "status.bar.lyrics", "status.bar.lyrics.description", EchoIconKind.Lyrics, "notification lyrics", "\u901a\u77e5\u6b4c\u8bcd"),
        entry(SettingsEntryId.SystemMediaLyricsTitle, SettingsCategoryId.LyricsSystemDisplay, "system.media.lyrics.title", "system.media.lyrics.title.description", EchoIconKind.Lyrics, "car", "media title", "\u8f66\u673a"),
        entry(SettingsEntryId.FloatingLyrics, SettingsCategoryId.LyricsSystemDisplay, "floating.lyrics", "floating.lyrics.description", EchoIconKind.Permission, "overlay", "\u60ac\u6d6e\u6b4c\u8bcd"),
        entryWithKeywords(SettingsEntryId.LyricsOffset, SettingsCategoryId.LyricsSystemDisplay, "offset", icon = EchoIconKind.Gauge, keywords = arrayOf("timing", "delay", "\u504f\u79fb", "\u5ef6\u8fdf")),

        entry(SettingsEntryId.DownloadManager, SettingsCategoryId.DownloadsStorageBackup, "download.manager", "download.manager.hint", EchoIconKind.Download, "download", "\u4e0b\u8f7d"),
        entryWithKeywords(SettingsEntryId.BackupExport, SettingsCategoryId.DownloadsStorageBackup, "backup.export", icon = EchoIconKind.Upload, keywords = arrayOf("backup", "export", "\u5907\u4efd", "\u5bfc\u51fa")),
        entry(SettingsEntryId.BackupImport, SettingsCategoryId.DownloadsStorageBackup, "backup.import", "backup.import.description", EchoIconKind.Import, "restore", "import", "\u6062\u590d", "\u5bfc\u5165"),

        entryWithKeywords(SettingsEntryId.Theme, SettingsCategoryId.AppearanceInteraction, "appearance", icon = EchoIconKind.Palette, keywords = arrayOf("theme", "dark", "amoled", "\u4e3b\u9898", "\u6df1\u8272")),
        entry(SettingsEntryId.AdvancedTheme, SettingsCategoryId.AppearanceInteraction, "advanced.themes", "advanced.themes.description", EchoIconKind.Sparkle, "contrast", "preset", "\u9ad8\u7ea7\u4e3b\u9898")
            .copy(page = SettingsPage.Appearance),
        entryWithKeywords(SettingsEntryId.Accent, SettingsCategoryId.AppearanceInteraction, "accent", icon = EchoIconKind.Swatch, keywords = arrayOf("color", "dynamic color", "\u5f3a\u8c03\u8272", "\u989c\u8272")),
        entryWithKeywords(SettingsEntryId.Language, SettingsCategoryId.AppearanceInteraction, "language", icon = EchoIconKind.Language, keywords = arrayOf("english", "chinese", "\u8bed\u8a00", "\u4e2d\u6587")),
        entry(SettingsEntryId.PageBackground, SettingsCategoryId.AppearanceInteraction, "page.background", "page.background.hint", EchoIconKind.Palette, "wallpaper", "\u80cc\u666f\u56fe"),
        entry(SettingsEntryId.HomeDashboardLayout, SettingsCategoryId.AppearanceInteraction, "home.layout", "home.layout.hint", EchoIconKind.Library, "home", "dashboard", "layout", "classic", "content", "\u4e3b\u9875", "\u6392\u7248", "\u7ecf\u5178", "\u5185\u5bb9"),
        entry(SettingsEntryId.CompactSettingsCards, SettingsCategoryId.AppearanceInteraction, "settings.compact.cards", "settings.compact.cards.hint", EchoIconKind.Settings, "density", "spacing", "compact", "grouped", "separate", "spacious", "\u5361\u7247\u5bc6\u5ea6", "\u95f4\u8ddd", "\u7d27\u51d1", "\u5206\u7ec4", "\u5206\u6563", "\u5bbd\u677e"),
        entry(SettingsEntryId.BackgroundBlur, SettingsCategoryId.AppearanceInteraction, "background.blur", "background.blur.hint", EchoIconKind.Palette, "blur", "\u6a21\u7cca"),
        entry(SettingsEntryId.BackgroundBlurIntensity, SettingsCategoryId.AppearanceInteraction, "background.blur.intensity", "background.blur.intensity.hint", EchoIconKind.Gauge, "radius", "\u6a21\u7cca\u5f3a\u5ea6"),
        entry(SettingsEntryId.GlassBlur, SettingsCategoryId.AppearanceInteraction, "glass.blur", "glass.blur.hint", EchoIconKind.Palette, "glass", "\u73bb\u7483"),
        entry(SettingsEntryId.GlassBlurIntensity, SettingsCategoryId.AppearanceInteraction, "glass.blur.intensity", "glass.blur.intensity.hint", EchoIconKind.Gauge, "glass radius", "\u73bb\u7483\u6a21\u7cca"),
        entry(SettingsEntryId.GlassSurfaceOpacity, SettingsCategoryId.AppearanceInteraction, "glass.card.opacity", "glass.card.opacity.hint", EchoIconKind.Gauge, "opacity", "\u900f\u660e\u5ea6"),
        entry(SettingsEntryId.NowPlayingGestures, SettingsCategoryId.AppearanceInteraction, "now.playing.gestures", "now.playing.gestures.hint", EchoIconKind.More, "gesture", "swipe", "\u624b\u52bf"),
        entry(SettingsEntryId.ShareStyle, SettingsCategoryId.AppearanceInteraction, "share.style", "share.style.hint", EchoIconKind.Upload, "share card", "\u5206\u4eab\u6837\u5f0f"),

        entryWithKeywords(SettingsEntryId.AudioPermission, SettingsCategoryId.SystemPrivacyHelp, "audio.permission", icon = EchoIconKind.Permission, keywords = arrayOf("permission", "\u6743\u9650")),
        entryWithKeywords(SettingsEntryId.NotificationPermission, SettingsCategoryId.SystemPrivacyHelp, "notification.permission", icon = EchoIconKind.Permission, keywords = arrayOf("notification", "permission", "\u901a\u77e5", "\u6743\u9650")),
        entryWithKeywords(SettingsEntryId.AppVersion, SettingsCategoryId.SystemPrivacyHelp, "version", icon = EchoIconKind.Info, keywords = arrayOf("about", "build", "\u7248\u672c")),
        entry(SettingsEntryId.Community, SettingsCategoryId.SystemPrivacyHelp, "qq.group", "qq.group.hint", EchoIconKind.Network, "community", "feedback", "qq", "\u793e\u533a", "\u53cd\u9988"),
        entry(SettingsEntryId.DebugPrompts, SettingsCategoryId.SystemPrivacyHelp, "debug.prompts", "debug.prompts.hint", EchoIconKind.Settings, "diagnostics", "developer", "\u8c03\u8bd5", "\u8bca\u65ad")
    )

    fun searchEntries(
        languageMode: String,
        onOpen: (SettingsEntryId, SettingsPage) -> Unit
    ): List<SettingsSearchEntry> {
        val categoriesById = categories.associateBy(SettingsCategorySpec::id)
        return entries.map { spec ->
            val category = requireNotNull(categoriesById[spec.categoryId])
            val bilingualKeywords = buildList {
                addAll(spec.keywords)
                listOf(AppLanguage.MODE_ENGLISH, AppLanguage.MODE_CHINESE).forEach { mode ->
                    add(AppLanguage.text(mode, spec.titleKey))
                    spec.descriptionKey?.let { add(AppLanguage.text(mode, it)) }
                    add(AppLanguage.text(mode, "settings.group.${category.groupKey}"))
                }
            }.filter(String::isNotBlank).distinct()
            SettingsSearchEntry(
                id = spec.id,
                categoryId = spec.categoryId,
                title = AppLanguage.text(languageMode, spec.titleKey),
                description = spec.descriptionKey?.let { AppLanguage.text(languageMode, it) }.orEmpty(),
                categoryLabel = AppLanguage.text(languageMode, "settings.group.${category.groupKey}"),
                keywords = bilingualKeywords,
                icon = spec.icon,
                onClick = Runnable { onOpen(spec.id, spec.page ?: category.page) }
            )
        }
    }

    internal fun entryIds(): List<SettingsEntryId> = entries.map(SettingsEntrySpec::id)

    private fun entry(
        id: SettingsEntryId,
        categoryId: SettingsCategoryId,
        titleKey: String,
        descriptionKey: String? = null,
        icon: EchoIconKind,
        vararg keywords: String
    ): SettingsEntrySpec = SettingsEntrySpec(
        id = id,
        categoryId = categoryId,
        titleKey = titleKey,
        descriptionKey = descriptionKey,
        keywords = keywords.toList(),
        icon = icon
    )

    private fun entryWithKeywords(
        id: SettingsEntryId,
        categoryId: SettingsCategoryId,
        titleKey: String,
        descriptionKey: String? = null,
        icon: EchoIconKind,
        keywords: Array<String>
    ): SettingsEntrySpec = entry(id, categoryId, titleKey, descriptionKey, icon, *keywords)

}

internal fun filterSettingsSearchEntries(
    entries: List<SettingsSearchEntry>,
    query: String
): List<SettingsSearchEntry> {
    val terms = query
        .trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return entries
    return entries.filter { entry ->
        val haystack = buildString {
            append(entry.title)
            append(' ')
            append(entry.description)
            append(' ')
            append(entry.categoryLabel)
            entry.keywords.forEach { keyword ->
                append(' ')
                append(keyword)
            }
        }.lowercase(Locale.ROOT)
        terms.all(haystack::contains)
    }
}
