package app.yukine

import app.yukine.identity.LibraryDedupMode
import app.yukine.feature.settingsui.R
import app.yukine.streaming.StreamingQualityPreference

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsActionProgress
import app.yukine.ui.SettingsActionStyle
import app.yukine.ui.SettingsImageDialog
import app.yukine.ui.SettingsMetric
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoIconKind
import app.yukine.ui.HomeDashboardLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

data class SettingsPageStateContent(
    val uiState: SettingsUiState,
    val actions: List<SettingsAction>
)

object SettingsPageStateBuilder {
    private val homeCategoryOrder = listOf(
        SettingsCategoryId.PlaybackAudio,
        SettingsCategoryId.LyricsSystemDisplay,
        SettingsCategoryId.AppearanceInteraction,
        SettingsCategoryId.LibraryMetadata,
        SettingsCategoryId.SourcesAccountsSync,
        SettingsCategoryId.DownloadsStorageBackup,
        SettingsCategoryId.SystemPrivacyHelp
    )

    fun build(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ): SettingsUiState = buildContent(title, metrics, actions).uiState

    fun buildContent(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>,
        issues: List<SettingsIssue> = emptyList(),
        issuesTitle: String = "",
        searchEntries: List<SettingsSearchEntry> = emptyList(),
        searchPlaceholder: String = "",
        searchResultsTitle: String = "",
        searchEmptyMessage: String = ""
    ): SettingsPageStateContent {
        val stableMetrics = metrics.toList()
        val stableActions = actions.toList()
        return SettingsPageStateContent(
            uiState = SettingsUiState(
                title = title,
                metrics = stableMetrics,
                items = stableActions.map { action -> action.toSettingsItem() } +
                        stableMetrics.map { metric -> SettingsItem.Metric(metric.label, metric.value) },
                issues = issues.toList(),
                issuesTitle = issuesTitle,
                searchEntries = searchEntries.toList(),
                searchPlaceholder = searchPlaceholder,
                searchResultsTitle = searchResultsTitle,
                searchEmptyMessage = searchEmptyMessage
            ),
            actions = stableActions
        )
    }

    fun home(
        languageMode: String,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus,
        onNavigate: (SettingsPage) -> Unit,
        onOpenSearchEntry: (SettingsEntryId, SettingsPage) -> Unit,
        onRequestNeededPermissions: () -> Unit,
        onOpenOverlayPermission: () -> Unit
    ): SettingsPageStateContent {
        val actions = SettingsInformationArchitecture.categories
            .sortedBy { category -> homeCategoryOrder.indexOf(category.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .map { category ->
            SettingsAction(
                label = groupTitle(languageMode, category.groupKey),
                onClick = Runnable { onNavigate(category.page) },
                description = groupDescription(languageMode, category.groupKey),
                value = homeCategorySummary(category.id, languageMode, preferences, runtime),
                style = SettingsActionStyle.Navigation,
                icon = category.icon,
                section = text(languageMode, homeCategorySectionKey(category.id)),
                categoryId = category.id
            )
        }
        val issues = buildList {
            if (!runtime.audioPermissionGranted) {
                add(SettingsIssue(
                    SettingsIssueId.AudioPermission,
                    text(languageMode, "settings.issue.audio.title"),
                    text(languageMode, "settings.issue.audio.description"),
                    EchoIconKind.Permission,
                    text(languageMode, "settings.issue.review"),
                    Runnable(onRequestNeededPermissions)
                ))
            }
            if (!runtime.notificationPermissionGranted) {
                add(SettingsIssue(
                    SettingsIssueId.NotificationPermission,
                    text(languageMode, "settings.issue.notification.title"),
                    text(languageMode, "settings.issue.notification.description"),
                    EchoIconKind.Permission,
                    text(languageMode, "settings.issue.review"),
                    Runnable(onRequestNeededPermissions)
                ))
            }
            if (preferences.floatingLyricsEnabled && !runtime.overlayPermissionGranted) {
                add(SettingsIssue(
                    SettingsIssueId.OverlayPermission,
                    text(languageMode, "settings.issue.overlay.title"),
                    text(languageMode, "settings.issue.overlay.description"),
                    EchoIconKind.Permission,
                    text(languageMode, "settings.issue.review"),
                    Runnable(onOpenOverlayPermission)
                ))
            }
            if (!runtime.playbackServiceConnected) {
                add(SettingsIssue(
                    SettingsIssueId.PlaybackService,
                    text(languageMode, "settings.issue.playback.title"),
                    text(languageMode, "settings.issue.playback.description"),
                    EchoIconKind.Info,
                    text(languageMode, "settings.issue.open.playback"),
                    Runnable { onNavigate(SettingsPage.PlaybackGroup) }
                ))
            }
        }
        return buildContent(
            title = text(languageMode, "tab.settings"),
            metrics = emptyList(),
            actions = actions,
            issues = issues,
            issuesTitle = text(languageMode, "settings.issues"),
            searchEntries = SettingsInformationArchitecture.searchEntries(languageMode, onOpenSearchEntry),
            searchPlaceholder = text(languageMode, "settings.search.placeholder"),
            searchResultsTitle = text(languageMode, "settings.search.results"),
            searchEmptyMessage = text(languageMode, "settings.search.no.results")
        )
    }

    private fun homeCategorySectionKey(categoryId: SettingsCategoryId): String = when (categoryId) {
        SettingsCategoryId.PlaybackAudio,
        SettingsCategoryId.LyricsSystemDisplay,
        SettingsCategoryId.AppearanceInteraction -> "settings.section.listening"

        SettingsCategoryId.LibraryMetadata,
        SettingsCategoryId.SourcesAccountsSync,
        SettingsCategoryId.DownloadsStorageBackup -> "settings.section.library.connections"

        SettingsCategoryId.SystemPrivacyHelp -> "settings.section.system.support"
    }

    fun aboutGroup(
        languageMode: String,
        appVersionName: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        debugPromptsEnabled: Boolean,
        checkUpdateEnabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onRequestNeededPermissions: () -> Unit,
        onDebugPromptsEnabledChange: (Boolean) -> Unit,
        onExportDiagnostics: () -> Unit,
        onCheckUpdateEnabledChange: (Boolean) -> Unit,
        onCheckUpdateNow: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "version"), appVersionName),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "notification.permission"), permissionLabel(notificationPermissionGranted, languageMode))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate))
            if (!audioPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "audio.permission"),
                        onClick = Runnable(onRequestNeededPermissions),
                        description = text(languageMode, "settings.permissions.review.description"),
                        style = SettingsActionStyle.Navigation,
                        icon = EchoIconKind.Permission,
                        section = text(languageMode, "settings.section.permissions"),
                        entryId = SettingsEntryId.AudioPermission
                    )
                )
            }
            if (!notificationPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "notification.permission"),
                        onClick = Runnable(onRequestNeededPermissions),
                        description = text(languageMode, "settings.permissions.review.description"),
                        style = SettingsActionStyle.Navigation,
                        icon = EchoIconKind.Permission,
                        section = text(languageMode, "settings.section.permissions"),
                        entryId = SettingsEntryId.NotificationPermission
                    )
                )
            }
            add(
                SettingsAction(
                    label = text(languageMode, "qq.group"),
                    onClick = Runnable { },
                    description = text(languageMode, "qq.group.hint"),
                    value = "1013122077",
                    style = SettingsActionStyle.Navigation,
                    icon = EchoIconKind.Network,
                    section = text(languageMode, "settings.section.help"),
                    imageDialog = SettingsImageDialog(
                        title = text(languageMode, "qq.group"),
                        message = text(languageMode, "qq.group.number"),
                        imageResId = R.drawable.qq_group_qr,
                        imageContentDescription = text(languageMode, "qq.group.qr.description"),
                        dismissLabel = text(languageMode, "close")
                    ),
                    entryId = SettingsEntryId.Community
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "debug.prompts"),
                    onClick = Runnable { onDebugPromptsEnabledChange(!debugPromptsEnabled) },
                    description = text(languageMode, "debug.prompts.hint"),
                    style = SettingsActionStyle.Toggle,
                    checked = debugPromptsEnabled,
                    section = text(languageMode, "settings.section.diagnostics"),
                    entryId = SettingsEntryId.DebugPrompts
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "diagnostics.export"),
                    onClick = Runnable(onExportDiagnostics),
                    description = text(languageMode, "diagnostics.export.hint"),
                    style = SettingsActionStyle.Navigation,
                    icon = EchoIconKind.Upload,
                    section = text(languageMode, "settings.section.diagnostics"),
                    entryId = SettingsEntryId.DiagnosticsExport
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "check.update"),
                    onClick = Runnable { onCheckUpdateEnabledChange(!checkUpdateEnabled) },
                    description = text(languageMode, "check.update.hint"),
                    style = SettingsActionStyle.Toggle,
                    checked = checkUpdateEnabled,
                    section = text(languageMode, "settings.section.update"),
                    entryId = SettingsEntryId.CheckUpdate
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "check.update.now"),
                    onClick = Runnable(onCheckUpdateNow),
                    description = text(languageMode, "check.update.now.hint"),
                    style = SettingsActionStyle.Navigation,
                    icon = EchoIconKind.Sync,
                    section = text(languageMode, "settings.section.update"),
                    entryId = SettingsEntryId.CheckUpdate
                )
            )
        }
        return buildContent(groupTitle(languageMode, "about"), metrics, actions)
    }

    fun storageGroup(
        languageMode: String,
        onNavigate: (SettingsPage) -> Unit,
        onOpenDownloads: () -> Unit,
        onExportBackup: () -> Unit,
        onImportBackup: () -> Unit
    ): SettingsPageStateContent {
        val actions = listOf(
            backNavigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            SettingsAction(
                label = text(languageMode, "download.manager"),
                onClick = Runnable(onOpenDownloads),
                description = text(languageMode, "download.manager.hint"),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Download,
                section = text(languageMode, "settings.section.downloads"),
                entryId = SettingsEntryId.DownloadManager
            ),
            SettingsAction(
                label = text(languageMode, "backup.export"),
                onClick = Runnable(onExportBackup),
                icon = EchoIconKind.Upload,
                section = text(languageMode, "settings.section.backup"),
                entryId = SettingsEntryId.BackupExport
            ),
            SettingsAction(
                label = text(languageMode, "backup.import"),
                onClick = Runnable(onImportBackup),
                description = text(languageMode, "backup.import.description"),
                style = SettingsActionStyle.Destructive,
                icon = EchoIconKind.Import,
                section = text(languageMode, "settings.section.backup"),
                entryId = SettingsEntryId.BackupImport
            )
        )
        return buildContent(
            groupTitle(languageMode, "storage"),
            emptyList(),
            actions
        )
    }

    fun appearanceGroup(
        languageMode: String,
        themeMode: String,
        accentMode: String,
        pageBackgrounds: PageBackgrounds,
        customBackgroundBlurEnabled: Boolean,
        customBackgroundBlurRadiusDp: Float,
        glassBlurEnabled: Boolean,
        glassBlurRadiusDp: Float,
        glassSurfaceOpacity: Float,
        onCustomBackgroundBlurEnabledChange: (Boolean) -> Unit,
        onCustomBackgroundBlurRadiusChange: (Float) -> Unit,
        onGlassBlurEnabledChange: (Boolean) -> Unit,
        onGlassBlurRadiusChange: (Float) -> Unit,
        onGlassSurfaceOpacityChange: (Float) -> Unit,
        onNavigate: (SettingsPage) -> Unit,
        nowPlayingGesturesEnabled: Boolean = true,
        shareStyle: String = TrackShareStyle.defaultValue(),
        onNowPlayingGesturesEnabledChange: (Boolean) -> Unit = {},
        compactSettingsCards: Boolean = false,
        onCompactSettingsCardsChange: (Boolean) -> Unit = {},
        homeDashboardLayout: HomeDashboardLayout = HomeDashboardLayout.Classic,
        onHomeDashboardLayoutChange: (HomeDashboardLayout) -> Unit = {}
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)),
            SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)),
            SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)),
            SettingsMetric(text(languageMode, "page.background"), pageBackgroundSummary(pageBackgrounds, languageMode)),
            SettingsMetric(text(languageMode, "description"), groupDescription(languageMode, "appearance"))
        )
        val actions = listOf(
            backNavigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            navigationAction(
                text(languageMode, "appearance"),
                SettingsPage.Appearance,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.themeLabel(themeMode, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.appearance"),
                entryId = SettingsEntryId.Theme
            ),
            navigationAction(
                text(languageMode, "accent"),
                SettingsPage.Accent,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.accentLabel(accentMode, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.appearance"),
                entryId = SettingsEntryId.Accent
            ),
            navigationAction(
                text(languageMode, "language"),
                SettingsPage.Language,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.labelFor(languageMode)
            ).copy(
                section = text(languageMode, "settings.section.appearance"),
                entryId = SettingsEntryId.Language
            ),
            navigationAction(
                text(languageMode, "page.background"),
                SettingsPage.PageBackground,
                onNavigate,
                text(languageMode, "page.background.hint"),
                pageBackgroundSummary(pageBackgrounds, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.appearance"),
                entryId = SettingsEntryId.PageBackground
            ),
            SettingsAction(
                label = text(languageMode, "home.layout"),
                onClick = Runnable {
                    onHomeDashboardLayoutChange(
                        if (homeDashboardLayout == HomeDashboardLayout.Classic) {
                            HomeDashboardLayout.Content
                        } else {
                            HomeDashboardLayout.Classic
                        }
                    )
                },
                description = text(languageMode, "home.layout.hint"),
                value = text(
                    languageMode,
                    if (homeDashboardLayout == HomeDashboardLayout.Classic) {
                        "home.layout.classic"
                    } else {
                        "home.layout.content"
                    }
                ),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Library,
                checked = homeDashboardLayout == HomeDashboardLayout.Content,
                section = text(languageMode, "settings.section.layout"),
                entryId = SettingsEntryId.HomeDashboardLayout
            ),
            SettingsAction(
                label = text(languageMode, "settings.compact.cards"),
                onClick = Runnable { onCompactSettingsCardsChange(!compactSettingsCards) },
                description = text(languageMode, "settings.compact.cards.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Settings,
                checked = compactSettingsCards,
                section = text(languageMode, "settings.section.layout"),
                entryId = SettingsEntryId.CompactSettingsCards
            ),
            SettingsAction(
                label = text(languageMode, "background.blur"),
                onClick = Runnable {
                    onCustomBackgroundBlurEnabledChange(!customBackgroundBlurEnabled)
                },
                description = text(languageMode, "background.blur.hint"),
                style = SettingsActionStyle.Toggle,
                checked = customBackgroundBlurEnabled,
                section = text(languageMode, "settings.section.effects"),
                entryId = SettingsEntryId.BackgroundBlur
            ),
            SettingsAction(
                label = text(languageMode, "background.blur.intensity"),
                onClick = Runnable { },
                description = text(languageMode, "background.blur.intensity.hint"),
                value = "${customBackgroundBlurRadiusDp.roundToInt()} dp",
                style = SettingsActionStyle.Slider,
                enabled = customBackgroundBlurEnabled,
                sliderValue = app.yukine.ui.EchoBackgroundBlurDefaults.normalizeRadius(
                    customBackgroundBlurRadiusDp
                ),
                sliderRangeStart = app.yukine.ui.EchoBackgroundBlurDefaults.MIN_RADIUS_DP,
                sliderRangeEnd = app.yukine.ui.EchoBackgroundBlurDefaults.MAX_RADIUS_DP,
                onSliderValueChange = onCustomBackgroundBlurRadiusChange,
                section = text(languageMode, "settings.section.effects"),
                entryId = SettingsEntryId.BackgroundBlurIntensity,
                sliderDefaultLabel = text(languageMode, "settings.default.value") + ": " +
                    "${app.yukine.ui.EchoBackgroundBlurDefaults.DEFAULT_RADIUS_DP.roundToInt()} dp",
                sliderResetLabel = text(languageMode, "settings.restore.default"),
                onSliderReset = Runnable {
                    onCustomBackgroundBlurRadiusChange(
                        app.yukine.ui.EchoBackgroundBlurDefaults.DEFAULT_RADIUS_DP
                    )
                }
            ),
            SettingsAction(
                label = text(languageMode, "glass.blur"),
                onClick = Runnable { onGlassBlurEnabledChange(!glassBlurEnabled) },
                description = text(languageMode, "glass.blur.hint"),
                style = SettingsActionStyle.Toggle,
                checked = glassBlurEnabled,
                section = text(languageMode, "settings.section.effects"),
                entryId = SettingsEntryId.GlassBlur
            ),
            SettingsAction(
                label = text(languageMode, "glass.blur.intensity"),
                onClick = Runnable { },
                description = text(languageMode, "glass.blur.intensity.hint"),
                value = "${glassBlurRadiusDp.roundToInt()} dp",
                style = SettingsActionStyle.Slider,
                enabled = glassBlurEnabled,
                sliderValue = app.yukine.ui.EchoGlassDefaults.normalizeBlurRadius(glassBlurRadiusDp),
                sliderRangeStart = app.yukine.ui.EchoGlassDefaults.MIN_BLUR_RADIUS_DP,
                sliderRangeEnd = app.yukine.ui.EchoGlassDefaults.MAX_BLUR_RADIUS_DP,
                onSliderValueChange = onGlassBlurRadiusChange,
                section = text(languageMode, "settings.section.effects"),
                entryId = SettingsEntryId.GlassBlurIntensity,
                sliderDefaultLabel = text(languageMode, "settings.default.value") + ": " +
                    "${app.yukine.ui.EchoGlassDefaults.BLUR_RADIUS_DP.roundToInt()} dp",
                sliderResetLabel = text(languageMode, "settings.restore.default"),
                onSliderReset = Runnable {
                    onGlassBlurRadiusChange(app.yukine.ui.EchoGlassDefaults.BLUR_RADIUS_DP)
                }
            ),
            SettingsAction(
                label = text(languageMode, "glass.card.opacity"),
                onClick = Runnable { },
                description = text(languageMode, "glass.card.opacity.hint"),
                value = "${(app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(glassSurfaceOpacity) * 100f).roundToInt()}%",
                style = SettingsActionStyle.Slider,
                enabled = glassBlurEnabled,
                sliderValue = app.yukine.ui.EchoGlassDefaults.normalizeSurfaceOpacity(glassSurfaceOpacity) * 100f,
                sliderRangeStart = app.yukine.ui.EchoGlassDefaults.MIN_SURFACE_OPACITY * 100f,
                sliderRangeEnd = app.yukine.ui.EchoGlassDefaults.MAX_SURFACE_OPACITY * 100f,
                onSliderValueChange = { value -> onGlassSurfaceOpacityChange(value / 100f) },
                section = text(languageMode, "settings.section.effects"),
                entryId = SettingsEntryId.GlassSurfaceOpacity,
                sliderDefaultLabel = text(languageMode, "settings.default.value") + ": " +
                    "${(app.yukine.ui.EchoGlassDefaults.SURFACE_OPACITY * 100f).roundToInt()}%",
                sliderResetLabel = text(languageMode, "settings.restore.default"),
                onSliderReset = Runnable {
                    onGlassSurfaceOpacityChange(app.yukine.ui.EchoGlassDefaults.SURFACE_OPACITY)
                }
            ),
            SettingsAction(
                label = text(languageMode, "now.playing.gestures"),
                onClick = Runnable { onNowPlayingGesturesEnabledChange(!nowPlayingGesturesEnabled) },
                description = text(languageMode, "now.playing.gestures.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.More,
                checked = nowPlayingGesturesEnabled,
                section = text(languageMode, "settings.section.interaction"),
                entryId = SettingsEntryId.NowPlayingGestures
            ),
            navigationAction(
                text(languageMode, "share.style"),
                SettingsPage.ShareStyle,
                onNavigate,
                text(languageMode, "share.style.hint"),
                shareStyleLabel(shareStyle, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.interaction"),
                entryId = SettingsEntryId.ShareStyle
            )
        )
        return buildContent(groupTitle(languageMode, "appearance"), metrics, actions)
    }

    fun sourcesGroup(
        languageMode: String,
        quality: String,
        gatewayConfigured: Boolean,
        luoxueImportedSourceCount: Int,
        luoxueEnabledSourceCount: Int,
        onNavigate: (SettingsPage) -> Unit,
        onOpenNetworkPage: (NetworkPage) -> Unit,
        onManageLuoxueSources: () -> Unit,
        onImportLuoxueSource: () -> Unit,
        kugouExperimentalSyncEnabled: Boolean = false,
        kugouAccountConnected: Boolean = false,
        kugouAccountDisplayName: String = "",
        kugouSyncLastResult: String = "",
        kugouSyncDegradationReason: String = "",
        onKugouExperimentalSyncEnabledChange: (Boolean) -> Unit = {}
    ): SettingsPageStateContent {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val lxSummary = if (languageMode == AppLanguage.MODE_ENGLISH) {
            "$luoxueEnabledSourceCount of $luoxueImportedSourceCount enabled"
        } else {
            "已启用 $luoxueEnabledSourceCount/$luoxueImportedSourceCount"
        }
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.lx.source.manager"), lxSummary),
            SettingsMetric(
                if (languageMode == AppLanguage.MODE_ENGLISH) "Kugou account" else "酷狗账号",
                when {
                    !kugouAccountConnected ->
                        if (languageMode == AppLanguage.MODE_ENGLISH) "Not verified" else "未验证"
                    kugouAccountDisplayName.isNotBlank() -> kugouAccountDisplayName
                    else -> if (languageMode == AppLanguage.MODE_ENGLISH) "Verified" else "已验证"
                }
            ),
            SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)),
            SettingsMetric(text(languageMode, "streaming.gateway"), if (gatewayConfigured) text(languageMode, "connected") else text(languageMode, "missing"))
        )
        val actions = listOf(
            backNavigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            SettingsAction(
                label = text(languageMode, "streaming.providers.manage"),
                onClick = Runnable { onOpenNetworkPage(NetworkPage.Streaming) },
                description = text(languageMode, "streaming.providers.manage.hint"),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Network,
                section = text(languageMode, "settings.section.accounts"),
                entryId = SettingsEntryId.StreamingProviders
            ),
            SettingsAction(
                label = if (languageMode == AppLanguage.MODE_ENGLISH) {
                    "Kugou experimental sync"
                } else {
                    "酷狗实验同步"
                },
                onClick = Runnable {
                    onKugouExperimentalSyncEnabledChange(!kugouExperimentalSyncEnabled)
                },
                description = buildString {
                    append(
                        if (languageMode == AppLanguage.MODE_ENGLISH) {
                            "Private account writes are experimental and stay read-only until the contract gate passes."
                        } else {
                            "私有账号写入属于实验能力；接口契约验证通过前始终保持只读。"
                        }
                    )
                    kugouSyncDegradationReason.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                },
                value = kugouSyncLastResult,
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Network,
                checked = kugouExperimentalSyncEnabled,
                section = text(languageMode, "settings.section.accounts")
            ),
            SettingsAction(
                label = text(languageMode, "streaming.lx.source.manager"),
                onClick = Runnable { onManageLuoxueSources() },
                description = text(languageMode, "streaming.lx.source.manager.hint") + " · " + lxSummary,
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Network,
                section = text(languageMode, "settings.section.accounts"),
                entryId = SettingsEntryId.LuoxueSourceManager
            ),
            SettingsAction(
                label = text(languageMode, "streaming.lx.import.source"),
                onClick = Runnable { onImportLuoxueSource() },
                description = text(languageMode, "streaming.lx.import.hint"),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Network,
                section = text(languageMode, "settings.section.accounts"),
                entryId = SettingsEntryId.LuoxueSourceImport
            ),
            SettingsAction(
                label = text(languageMode, "remote.music.sources"),
                onClick = Runnable { onOpenNetworkPage(NetworkPage.Sources) },
                description = text(languageMode, "remote.music.sources.hint"),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Folder,
                section = text(languageMode, "settings.section.sources"),
                entryId = SettingsEntryId.RemoteMusicSources
            ),
            SettingsAction(
                label = text(languageMode, "webdav"),
                onClick = Runnable { onOpenNetworkPage(NetworkPage.WebDav) },
                description = text(languageMode, "settings.sources.webdav.hint"),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Folder,
                section = text(languageMode, "settings.section.sources"),
                entryId = SettingsEntryId.WebDav
            ),
            navigationAction(
                text(languageMode, "streaming.audio.quality"),
                SettingsPage.StreamingAudioQuality,
                onNavigate,
                text(languageMode, "streaming.audio.quality.hint"),
                streamingQualityLabel(normalizedQuality, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.playback"),
                entryId = SettingsEntryId.StreamingAudioQuality
            ),
            navigationAction(
                text(languageMode, "advanced") + " · " + text(languageMode, "streaming.gateway"),
                SettingsPage.StreamingGateway,
                onNavigate,
                text(languageMode, "streaming.gateway.hint"),
                if (gatewayConfigured) text(languageMode, "connected") else text(languageMode, "missing")
            ).copy(
                section = text(languageMode, "settings.section.advanced"),
                entryId = SettingsEntryId.StreamingGateway
            )
        )
        return buildContent(text(languageMode, "streaming.settings"), metrics, actions)
    }

    fun playbackGroup(
        languageMode: String,
        playbackSpeed: Float,
        appVolume: Float,
        audioEffects: AudioEffectSettings,
        playbackRestoreEnabled: Boolean,
        replayGainEnabled: Boolean,
        audioExclusiveEnabled: Boolean,
        bitPerfectEnabled: Boolean,
        usbExclusiveEnabled: Boolean = false,
        remainingMs: Long,
        onNavigate: (SettingsPage) -> Unit,
        onReplayGainEnabledChange: (Boolean) -> Unit = {},
        onPlaybackRestoreEnabledChange: (Boolean) -> Unit = {},
        onAudioExclusiveEnabledChange: (Boolean) -> Unit = {},
        onBitPerfectEnabledChange: (Boolean) -> Unit = {},
        onUsbExclusiveEnabledChange: (Boolean) -> Unit = {},
        audioExclusiveStatusDescription: String? = null,
        bitPerfectStatusDescription: String? = null,
        usbExclusiveStatusDescription: String? = null,
        usbClockMismatchCompatibilityEnabled: Boolean = false,
        onUsbClockMismatchCompatibilityEnabledChange: (Boolean) -> Unit = {}
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "playback.speed"), playbackSpeedLabel(playbackSpeed)),
            SettingsMetric(text(languageMode, "app.volume"), appVolumeLabel(appVolume)),
            SettingsMetric(text(languageMode, "audio.effects"), audioEffectsLabel(audioEffects, languageMode)),
            SettingsMetric(text(languageMode, "replay.gain"), enabledLabel(replayGainEnabled, languageMode)),
            SettingsMetric(text(languageMode, "playback.restore"), enabledLabel(playbackRestoreEnabled, languageMode)),
            SettingsMetric(text(languageMode, "audio.exclusive"), enabledLabel(audioExclusiveEnabled, languageMode)),
            SettingsMetric(text(languageMode, "bit.perfect"), enabledLabel(bitPerfectEnabled, languageMode)),
            SettingsMetric(text(languageMode, "usb.exclusive"), enabledLabel(usbExclusiveEnabled, languageMode)),
            SettingsMetric(text(languageMode, "sleep.timer"), sleepTimerLabel(remainingMs, languageMode))
        )
        val actions = listOf(
            backNavigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            navigationAction(
                text(languageMode, "playback.speed"),
                SettingsPage.PlaybackSpeed,
                onNavigate,
                text(languageMode, "speed.description"),
                playbackSpeedLabel(playbackSpeed)
            ).copy(
                section = text(languageMode, "settings.section.audio"),
                entryId = SettingsEntryId.PlaybackSpeed
            ),
            navigationAction(
                text(languageMode, "app.volume"),
                SettingsPage.AppVolume,
                onNavigate,
                text(languageMode, "volume.description"),
                appVolumeLabel(appVolume)
            ).copy(
                section = text(languageMode, "settings.section.audio"),
                entryId = SettingsEntryId.AppVolume
            ),
            navigationAction(
                text(languageMode, "audio.effects"),
                SettingsPage.AudioEffects,
                onNavigate,
                text(languageMode, "audio.effects.hint"),
                audioEffectsLabel(audioEffects, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.audio"),
                entryId = SettingsEntryId.AudioEffects
            ),
            SettingsAction(
                label = text(languageMode, "replay.gain"),
                onClick = Runnable { onReplayGainEnabledChange(!replayGainEnabled) },
                description = text(languageMode, "replay.gain.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Gauge,
                checked = replayGainEnabled,
                section = text(languageMode, "settings.section.audio"),
                entryId = SettingsEntryId.ReplayGain
            ),
            SettingsAction(
                label = text(languageMode, "playback.restore"),
                onClick = Runnable { onPlaybackRestoreEnabledChange(!playbackRestoreEnabled) },
                description = text(languageMode, "playback.restore.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Refresh,
                checked = playbackRestoreEnabled,
                section = text(languageMode, "settings.section.behavior"),
                entryId = SettingsEntryId.PlaybackRestore
            ),
            SettingsAction(
                label = text(languageMode, "audio.exclusive"),
                onClick = Runnable { onAudioExclusiveEnabledChange(!audioExclusiveEnabled) },
                description = audioExclusiveStatusDescription ?: if (audioExclusiveEnabled)
                        text(languageMode, "audio.exclusive.active.description")
                    else
                        text(languageMode, "audio.exclusive.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Gauge,
                checked = audioExclusiveEnabled,
                section = text(languageMode, "settings.section.behavior"),
                entryId = SettingsEntryId.AudioExclusive
            ),
            SettingsAction(
                label = text(languageMode, "bit.perfect"),
                onClick = Runnable { onBitPerfectEnabledChange(!bitPerfectEnabled) },
                description = bitPerfectStatusDescription ?: text(languageMode, "bit.perfect.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Gauge,
                checked = bitPerfectEnabled,
                section = text(languageMode, "settings.section.behavior"),
                entryId = SettingsEntryId.BitPerfect
            ),
            SettingsAction(
                label = text(languageMode, "usb.exclusive"),
                onClick = Runnable { onUsbExclusiveEnabledChange(!usbExclusiveEnabled) },
                description = usbExclusiveStatusDescription ?: text(languageMode, "usb.exclusive.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Gauge,
                checked = usbExclusiveEnabled,
                section = text(languageMode, "settings.section.behavior"),
                entryId = SettingsEntryId.UsbExclusive
            ),
            SettingsAction(
                label = text(languageMode, "usb.clock.compatibility"),
                onClick = Runnable {
                    onUsbClockMismatchCompatibilityEnabledChange(
                        !usbClockMismatchCompatibilityEnabled
                    )
                },
                description = text(languageMode, "usb.clock.compatibility.hint"),
                style = SettingsActionStyle.Toggle,
                icon = EchoIconKind.Gauge,
                checked = usbClockMismatchCompatibilityEnabled,
                enabled = usbExclusiveEnabled,
                section = text(languageMode, "settings.section.behavior"),
                entryId = SettingsEntryId.UsbClockMismatchCompatibility
            ),
            navigationAction(
                text(languageMode, "sleep.timer"),
                SettingsPage.SleepTimer,
                onNavigate,
                text(languageMode, "sleep.timer.description"),
                sleepTimerLabel(remainingMs, languageMode)
            ).copy(
                section = text(languageMode, "settings.section.tools"),
                entryId = SettingsEntryId.SleepTimer
            )
        )
        return buildContent(groupTitle(languageMode, "playback"), metrics, actions)
    }

    fun theme(
        languageMode: String,
        themeMode: String,
        onNavigate: (SettingsPage) -> Unit,
        onApplyTheme: (String) -> Unit
    ): SettingsPageStateContent {
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Appearance), onNavigate))
            EchoTheme.primaryModeOptions().forEach { mode ->
                add(themeOption(languageMode, themeMode, mode, onApplyTheme))
            }
            if (EchoTheme.advancedModeOptions().isNotEmpty()) {
                add(
                    navigationAction(
                        text(languageMode, "advanced.themes"),
                        SettingsPage.AdvancedTheme,
                        onNavigate,
                        text(languageMode, "advanced.themes.description")
                    ).copy(
                        section = text(languageMode, "settings.section.advanced"),
                        entryId = SettingsEntryId.AdvancedTheme
                    )
                )
            }
        }
        return buildContent(
            text(languageMode, "appearance"),
            listOf(
                SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)),
                SettingsMetric(text(languageMode, "options"), text(languageMode, "theme.options"))
            ),
            actions
        )
    }

    fun advancedTheme(
        languageMode: String,
        themeMode: String,
        onNavigate: (SettingsPage) -> Unit,
        onApplyTheme: (String) -> Unit
    ): SettingsPageStateContent {
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AdvancedTheme), onNavigate))
            EchoTheme.advancedModeOptions().forEach { mode ->
                add(themeOption(languageMode, themeMode, mode, onApplyTheme))
            }
        }
        return buildContent(
            text(languageMode, "advanced.themes"),
            listOf(
                SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)),
                SettingsMetric(text(languageMode, "description"), text(languageMode, "advanced.themes.description"))
            ),
            actions
        )
    }

    fun accent(
        languageMode: String,
        accentMode: String,
        pageBackgrounds: PageBackgrounds,
        onNavigate: (SettingsPage) -> Unit,
        onApplyAccent: (String) -> Unit
    ): SettingsPageStateContent {
        val accentOptions = listOf(
            EchoTheme.ACCENT_BLUE,
            EchoTheme.ACCENT_TEAL,
            EchoTheme.ACCENT_ROSE,
            EchoTheme.ACCENT_VIOLET,
            EchoTheme.ACCENT_AMBER,
            EchoTheme.ACCENT_EMERALD,
            EchoTheme.ACCENT_CYAN,
            EchoTheme.ACCENT_LIME,
            EchoTheme.ACCENT_RED,
            EchoTheme.ACCENT_INDIGO,
            EchoTheme.ACCENT_PINE,
            EchoTheme.ACCENT_PEACH
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Accent), onNavigate))
            if (EchoTheme.dynamicColorAvailable()) {
                add(dynamicAccentOption(
                    languageMode,
                    accentMode,
                    EchoTheme.ACCENT_DYNAMIC_SYSTEM,
                    "accent.dynamic.system.description",
                    true,
                    onApplyAccent
                ))
            }
            val hasCustomBackground = pageBackgrounds.accentSourceUri().isNotBlank()
            add(dynamicAccentOption(
                languageMode,
                accentMode,
                EchoTheme.ACCENT_DYNAMIC_BACKGROUND,
                if (hasCustomBackground) "accent.dynamic.background.description" else "accent.dynamic.background.missing",
                hasCustomBackground,
                onApplyAccent
            ))
            accentOptions.forEach { accent ->
                add(accentOption(languageMode, accentMode, accent, onApplyAccent))
            }
        }
        return buildContent(
            text(languageMode, "accent"),
            listOf(
                SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)),
                SettingsMetric(text(languageMode, "options"), text(languageMode, "accent.options"))
            ),
            actions
        )
    }

    fun language(
        languageMode: String,
        onNavigate: (SettingsPage) -> Unit,
        onApplyLanguage: (String) -> Unit
    ): SettingsPageStateContent {
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Language), onNavigate))
            listOf(AppLanguage.MODE_SYSTEM, AppLanguage.MODE_CHINESE, AppLanguage.MODE_ENGLISH).forEach { option ->
                add(languageOption(languageMode, option, onApplyLanguage))
            }
        }
        return buildContent(
            text(languageMode, "language"),
            listOf(
                SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)),
                SettingsMetric(text(languageMode, "options"), text(languageMode, "language.options"))
            ),
            actions
        )
    }

    fun pageBackgrounds(
        languageMode: String,
        pageBackgrounds: PageBackgrounds,
        onNavigate: (SettingsPage) -> Unit,
        onChoosePageBackground: (String) -> Unit,
        onClearPageBackground: (String) -> Unit
    ): SettingsPageStateContent {
        val backgroundPages = listOf(
            PageBackgrounds.PAGE_ALL to pageBackgrounds.sharedUri,
            PageBackgrounds.PAGE_HOME to pageBackgrounds.homeUri,
            PageBackgrounds.PAGE_LIBRARY to pageBackgrounds.libraryUri,
            PageBackgrounds.PAGE_PLAYER to pageBackgrounds.playerUri,
            PageBackgrounds.PAGE_SETTINGS to pageBackgrounds.settingsUri
        )
        val metrics = backgroundPages.map { (page, uri) ->
            SettingsMetric(pageBackgroundPageLabel(page, languageMode), backgroundStateLabel(uri, languageMode))
        } + SettingsMetric(text(languageMode, "description"), text(languageMode, "page.background.description"))
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.PageBackground), onNavigate))
            backgroundPages.forEach { (page, uri) ->
                addPageBackgroundActions(languageMode, page, uri, onChoosePageBackground, onClearPageBackground)
            }
        }
        return buildContent(text(languageMode, "page.background"), metrics, actions)
    }

    fun nowPlayingGestures(
        languageMode: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent = booleanLeafPage(
        languageMode = languageMode,
        currentPage = SettingsPage.NowPlayingGestures,
        titleKey = "now.playing.gestures",
        descriptionKey = "now.playing.gestures.description",
        enableKey = "enable.now.playing.gestures",
        disableKey = "disable.now.playing.gestures",
        enabled = enabled,
        onNavigate = onNavigate,
        onToggle = onToggle
    )

    fun playbackRestore(
        languageMode: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent = booleanLeafPage(
        languageMode = languageMode,
        currentPage = SettingsPage.PlaybackRestore,
        titleKey = "playback.restore",
        descriptionKey = "playback.restore.description",
        enableKey = "enable.playback.restore",
        disableKey = "disable.playback.restore",
        enabled = enabled,
        onNavigate = onNavigate,
        onToggle = onToggle
    )

    fun replayGain(
        languageMode: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent = booleanLeafPage(
        languageMode = languageMode,
        currentPage = SettingsPage.ReplayGain,
        titleKey = "replay.gain",
        descriptionKey = "replay.gain.description",
        enableKey = "enable.replay.gain",
        disableKey = "disable.replay.gain",
        enabled = enabled,
        onNavigate = onNavigate,
        onToggle = onToggle
    )

    fun audioEffects(
        languageMode: String,
        settings: AudioEffectSettings,
        onNavigate: (SettingsPage) -> Unit,
        onApplyAudioEffects: (AudioEffectSettings) -> Unit
    ): SettingsPageStateContent {
        val effects = settings
        val metrics = listOf(
            SettingsMetric(text(languageMode, "audio.effects"), audioEffectsLabel(effects, languageMode)),
            SettingsMetric(text(languageMode, "equalizer.preset"), equalizerPresetLabel(effects.preset, languageMode)),
            SettingsMetric(text(languageMode, "bass.boost"), strengthLabel(effects.bassBoostStrength)),
            SettingsMetric(text(languageMode, "virtualizer"), strengthLabel(effects.virtualizerStrength)),
            SettingsMetric(text(languageMode, "loudness"), loudnessLabel(effects.loudnessGainMb)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "audio.effects.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AudioEffects), onNavigate))
            add(
                SettingsAction(
                    label = text(languageMode, "audio.effects"),
                    onClick = Runnable { onApplyAudioEffects(effects.withEnabled(!effects.enabled)) },
                    description = text(languageMode, "audio.effects.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Gauge,
                    checked = effects.enabled
                )
            )
            listOf(AudioEffectSettings.PRESET_CUSTOM, 0, 1, 2).forEach { preset ->
                add(equalizerPresetOption(languageMode, effects, preset, onApplyAudioEffects))
            }
            listOf(0, 500, 1000).forEach { strength ->
                add(strengthOption(languageMode, effects, "bass.boost", "bass", strength, onApplyAudioEffects))
            }
            listOf(0, 500, 1000).forEach { strength ->
                add(strengthOption(languageMode, effects, "virtualizer", "virtualizer", strength, onApplyAudioEffects))
            }
            listOf(0, 300, 600).forEach { gainMb ->
                add(loudnessOption(languageMode, effects, gainMb, onApplyAudioEffects))
            }
        }
        return buildContent(text(languageMode, "audio.effects"), metrics, actions)
    }

    fun statusBarLyrics(
        languageMode: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent = booleanLeafPage(
        languageMode = languageMode,
        currentPage = SettingsPage.StatusBarLyrics,
        titleKey = "status.bar.lyrics",
        descriptionKey = "status.bar.lyrics.description",
        enableKey = "enable.status.bar.lyrics",
        disableKey = "disable.status.bar.lyrics",
        enabled = enabled,
        onNavigate = onNavigate,
        onToggle = onToggle
    )

    fun floatingLyrics(
        languageMode: String,
        enabled: Boolean,
        overlayPermissionGranted: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOpenPermission: () -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(enabled, languageMode)),
            SettingsMetric(
                text(languageMode, "overlay.permission"),
                permissionLabel(overlayPermissionGranted, languageMode)
            ),
            SettingsMetric(
                text(languageMode, "description"),
                text(languageMode, "floating.lyrics.description")
            )
        )
        val actions = buildList {
            add(
                backNavigationAction(
                    text(languageMode, "back"),
                    SettingsBackStack.parent(SettingsPage.FloatingLyrics),
                    onNavigate
                )
            )
            if (!overlayPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "grant.overlay.permission"),
                        onClick = Runnable(onOpenPermission),
                        description = text(languageMode, "floating.lyrics.description"),
                        style = SettingsActionStyle.Navigation,
                        icon = EchoIconKind.Permission
                    )
                )
            }
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics"),
                    onClick = Runnable { onToggle(!enabled) },
                    description = text(languageMode, "floating.lyrics.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Lyrics,
                    checked = enabled,
                    enabled = overlayPermissionGranted || enabled
                )
            )
        }
        return buildContent(text(languageMode, "floating.lyrics"), metrics, actions)
    }

    fun floatingLyrics(
        languageMode: String,
        enabled: Boolean,
        overlayPermissionGranted: Boolean,
        runtimeStatus: String,
        textSizeSp: Int,
        widthPercent: Int,
        backgroundOpacityPercent: Int,
        transparentBackground: Boolean,
        textColorArgb: Int,
        onNavigate: (SettingsPage) -> Unit,
        onOpenPermission: () -> Unit,
        onToggle: (Boolean) -> Unit,
        onTextSizeChange: (Int) -> Unit,
        onWidthChange: (Int) -> Unit,
        onBackgroundOpacityChange: (Int) -> Unit,
        onTransparentBackgroundChange: (Boolean) -> Unit,
        onTextColorChange: (Int) -> Unit,
        onShow: () -> Unit,
        onUnlock: () -> Unit,
        onReset: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(enabled, languageMode)),
            SettingsMetric(text(languageMode, "overlay.permission"), permissionLabel(overlayPermissionGranted, languageMode)),
            SettingsMetric(
                text(languageMode, "floating.lyrics.runtime.status"),
                text(
                    languageMode,
                    when (runtimeStatus) {
                        "PermissionRequired" -> "floating.lyrics.status.permission"
                        "Waiting" -> "floating.lyrics.status.waiting"
                        "Visible" -> "floating.lyrics.status.visible"
                        "Hidden" -> "floating.lyrics.status.hidden"
                        "Failed" -> "floating.lyrics.status.failed"
                        else -> "floating.lyrics.status.disabled"
                    }
                )
            ),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "floating.lyrics.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.FloatingLyrics), onNavigate))
            if (!overlayPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "grant.overlay.permission"),
                        onClick = Runnable { onOpenPermission() },
                        description = text(languageMode, "floating.lyrics.description"),
                        style = SettingsActionStyle.Navigation,
                        icon = EchoIconKind.Permission,
                        section = text(languageMode, "floating.lyrics.section.runtime")
                    )
                )
            }
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics"),
                    onClick = Runnable { onToggle(!enabled) },
                    description = text(languageMode, "floating.lyrics.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Lyrics,
                    checked = enabled,
                    enabled = true,
                    section = text(languageMode, "floating.lyrics.section.runtime")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.text.size"),
                    onClick = Runnable { },
                    description = text(languageMode, "floating.lyrics.text.size.description"),
                    value = "$textSizeSp sp",
                    style = SettingsActionStyle.Slider,
                    icon = EchoIconKind.Lyrics,
                    sliderValue = textSizeSp.coerceIn(12, 30).toFloat(),
                    sliderRangeStart = 12f,
                    sliderRangeEnd = 30f,
                    sliderSteps = 17,
                    onSliderValueChange = { value -> onTextSizeChange(value.toInt()) },
                    section = text(languageMode, "floating.lyrics.section.appearance")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.width"),
                    onClick = Runnable { },
                    description = text(languageMode, "floating.lyrics.width.description"),
                    value = "${widthPercent.coerceIn(40, 100)}%",
                    style = SettingsActionStyle.Slider,
                    icon = EchoIconKind.Gauge,
                    sliderValue = widthPercent.coerceIn(40, 100).toFloat(),
                    sliderRangeStart = 40f,
                    sliderRangeEnd = 100f,
                    sliderSteps = 59,
                    onSliderValueChange = { value -> onWidthChange(value.toInt()) },
                    section = text(languageMode, "floating.lyrics.section.appearance")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.opacity"),
                    onClick = Runnable { },
                    description = text(languageMode, "floating.lyrics.opacity.description"),
                    value = "${backgroundOpacityPercent.coerceIn(0, 100)}%",
                    style = SettingsActionStyle.Slider,
                    icon = EchoIconKind.Gauge,
                    enabled = !transparentBackground,
                    sliderValue = backgroundOpacityPercent.coerceIn(0, 100).toFloat(),
                    sliderRangeStart = 0f,
                    sliderRangeEnd = 100f,
                    sliderSteps = 99,
                    onSliderValueChange = { value ->
                        onBackgroundOpacityChange(value.toInt())
                    },
                    section = text(languageMode, "floating.lyrics.section.appearance")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.transparent"),
                    onClick = Runnable {
                        onTransparentBackgroundChange(!transparentBackground)
                    },
                    description = text(languageMode, "floating.lyrics.transparent.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Palette,
                    checked = transparentBackground,
                    section = text(languageMode, "floating.lyrics.section.appearance")
                )
            )
            val colorPresets = listOf(
                0xFFF8FBFF.toInt() to "floating.lyrics.color.white",
                0xFF7FDBFF.toInt() to "floating.lyrics.color.cyan",
                0xFFFF9EC4.toInt() to "floating.lyrics.color.pink",
                0xFFFFD700.toInt() to "floating.lyrics.color.gold",
                0xFF98FB98.toInt() to "floating.lyrics.color.green"
            )
            for ((color, labelKey) in colorPresets) {
                add(
                    SettingsAction(
                        label = text(languageMode, labelKey),
                        onClick = Runnable { onTextColorChange(color) },
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Palette,
                        checked = textColorArgb == color,
                        section = text(languageMode, "floating.lyrics.section.text.color")
                    )
                )
            }
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.show"),
                    onClick = Runnable(onShow),
                    description = text(languageMode, "floating.lyrics.show.description"),
                    icon = EchoIconKind.Lyrics,
                    enabled = enabled && overlayPermissionGranted,
                    section = text(languageMode, "floating.lyrics.section.actions")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.unlock"),
                    onClick = Runnable(onUnlock),
                    description = text(languageMode, "floating.lyrics.unlock.description"),
                    icon = EchoIconKind.Permission,
                    enabled = enabled && overlayPermissionGranted,
                    section = text(languageMode, "floating.lyrics.section.actions")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.reset"),
                    onClick = Runnable(onReset),
                    description = text(languageMode, "floating.lyrics.reset.description"),
                    icon = EchoIconKind.Settings,
                    section = text(languageMode, "floating.lyrics.section.actions")
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics.behavior"),
                    onClick = Runnable { },
                    description = text(languageMode, "floating.lyrics.behavior.description"),
                    icon = EchoIconKind.Info,
                    enabled = false,
                    section = text(languageMode, "floating.lyrics.section.notes")
                )
            )
        }
        return buildContent(text(languageMode, "floating.lyrics"), metrics, actions)
    }

    fun sleepTimer(
        languageMode: String,
        remainingMs: Long,
        onNavigate: (SettingsPage) -> Unit,
        onStartTimer: (Int) -> Unit,
        onCancelTimer: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "sleep.timer"), sleepTimerLabel(remainingMs, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "sleep.timer.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.SleepTimer), onNavigate))
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                add(
                    SettingsAction(
                        label = minutes.toString() + text(languageMode, "min"),
                        onClick = Runnable { onStartTimer(minutes) },
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Timer,
                        checked = remainingMs > 0L && abs(remainingMs - minutes * 60_000L) < 60_000L
                    )
                )
            }
            if (remainingMs > 0L) {
                add(
                    SettingsAction(
                        label = text(languageMode, "cancel.sleep.timer"),
                        onClick = Runnable { onCancelTimer() },
                        style = SettingsActionStyle.Destructive,
                        icon = EchoIconKind.Delete
                    )
                )
            }
        }
        return buildContent(text(languageMode, "sleep.timer"), metrics, actions)
    }

    fun playbackSpeed(
        languageMode: String,
        playbackSpeed: Float,
        onNavigate: (SettingsPage) -> Unit,
        onApplySpeed: (Float) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "playback.speed"), playbackSpeedLabel(playbackSpeed)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "speed.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.PlaybackSpeed), onNavigate))
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                add(
                    SettingsAction(
                        label = playbackSpeedLabel(speed),
                        onClick = Runnable { onApplySpeed(speed) },
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Gauge,
                        checked = abs(normalizePlaybackSpeed(playbackSpeed) - normalizePlaybackSpeed(speed)) < 0.01f
                    )
                )
            }
        }
        return buildContent(text(languageMode, "playback.speed"), metrics, actions)
    }

    fun appVolume(
        languageMode: String,
        appVolume: Float,
        onNavigate: (SettingsPage) -> Unit,
        onApplyVolume: (Float) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "app.volume"), appVolumeLabel(appVolume)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "volume.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AppVolume), onNavigate))
            listOf(0.5f, 0.7f, 0.85f, 1.0f).forEach { volume ->
                add(
                    SettingsAction(
                        label = appVolumeLabel(volume),
                        onClick = Runnable { onApplyVolume(volume) },
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Volume,
                        checked = abs(normalizeAppVolume(appVolume) - normalizeAppVolume(volume)) < 0.01f
                    )
                )
            }
        }
        return buildContent(text(languageMode, "app.volume"), metrics, actions)
    }

    fun streamingAudioQuality(
        languageMode: String,
        quality: String,
        refuseAutomaticQualityDowngrade: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onApplyQuality: (String) -> Unit,
        onRefuseAutomaticQualityDowngradeChange: (Boolean) -> Unit
    ): SettingsPageStateContent {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.quality.description"), compact = true),
            SettingsMetric(text(languageMode, "quality.platform.mapping"), text(languageMode, "quality.platform.mapping.summary"), compact = true)
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.StreamingAudioQuality), onNavigate))
            add(
                SettingsAction(
                    label = text(languageMode, "quality.downgrade.refuse"),
                    onClick = Runnable {
                        onRefuseAutomaticQualityDowngradeChange(!refuseAutomaticQualityDowngrade)
                    },
                    description = text(languageMode, "quality.downgrade.refuse.hint"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Gauge,
                    checked = refuseAutomaticQualityDowngrade
                )
            )
            StreamingQualityPreference.options().forEach { option ->
                val normalizedOption = StreamingQualityPreference.normalize(option)
                val audioQuality = StreamingAudioQuality.fromWireName(normalizedOption)
                add(
                    SettingsAction(
                        label = streamingQualityLabel(normalizedOption, languageMode),
                        onClick = Runnable { onApplyQuality(normalizedOption) },
                        description = audioQuality?.let { StreamingQualityPlatformMapping.explanation(it, languageMode) }.orEmpty(),
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Gauge,
                        checked = StreamingQualityPreference.normalize(normalizedQuality) ==
                            StreamingQualityPreference.normalize(normalizedOption)
                    )
                )
            }
        }
        return buildContent(text(languageMode, "streaming.audio.quality"), metrics, actions)
    }

    fun shareStyle(
        languageMode: String,
        shareStyle: String,
        onNavigate: (SettingsPage) -> Unit,
        onApplyStyle: (String) -> Unit
    ): SettingsPageStateContent {
        val normalizedStyle = TrackShareStyle.normalize(shareStyle)
        val metrics = listOf(
            SettingsMetric(text(languageMode, "share.style"), shareStyleLabel(normalizedStyle, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "share.style.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.ShareStyle), onNavigate))
            TrackShareStyle.options().forEach { option ->
                val normalizedOption = TrackShareStyle.normalize(option)
                add(
                    SettingsAction(
                        label = shareStyleLabel(normalizedOption, languageMode),
                        onClick = Runnable { onApplyStyle(normalizedOption) },
                        style = SettingsActionStyle.Choice,
                        icon = EchoIconKind.Upload,
                        checked = TrackShareStyle.normalize(normalizedStyle) == TrackShareStyle.normalize(normalizedOption)
                    )
                )
            }
        }
        return buildContent(text(languageMode, "share.style"), metrics, actions)
    }

    fun streamingGateway(
        languageMode: String,
        endpoint: String,
        configured: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onApplyEndpoint: (String) -> Unit,
        onEditMusicBrainzProxy: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.gateway"), if (configured) text(languageMode, "connected") else text(languageMode, "missing")),
            SettingsMetric(text(languageMode, "endpoint"), endpoint),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.gateway.description"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.StreamingGateway), onNavigate))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewayEndpoint.EMULATOR_HOST, "streaming.gateway.emulator", onApplyEndpoint))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewayEndpoint.LOCALHOST, "streaming.gateway.localhost", onApplyEndpoint))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewayEndpoint.UNCONFIGURED, "disable", onApplyEndpoint))
            add(SettingsAction(
                label = text(languageMode, "metadata.gateway"),
                description = text(languageMode, "metadata.gateway.description"),
                onClick = Runnable(onEditMusicBrainzProxy),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Network
            ))
        }
        return buildContent(text(languageMode, "streaming.gateway"), metrics, actions)
    }

    fun library(
        languageMode: String,
        backPage: SettingsPage,
        songCount: Int,
        albumCount: Int,
        artistCount: Int,
        audioPermissionGranted: Boolean,
        identityBackfill: IdentityBackfillStatusUi = IdentityBackfillStatusUi(),
        dedupMode: LibraryDedupMode = LibraryDedupMode.SAFE,
        duplicateCandidateCount: Int = 0,
        onNavigate: (SettingsPage) -> Unit,
        onLoadLibrary: () -> Unit,
        onOpenAudioFilePicker: () -> Unit,
        onOpenAudioFolderPicker: () -> Unit,
        onRebuildSongIdentity: () -> Unit = {},
        onCancelIdentityBackfill: () -> Unit = {},
        onDedupModeChange: (LibraryDedupMode) -> Unit = {},
        hiddenItems: List<HiddenLibraryItemUi> = emptyList(),
        onRestoreHidden: (String) -> Unit = {},
        onRestoreAllHidden: () -> Unit = {}
    ): SettingsPageStateContent {
        val metrics = buildList {
            addAll(listOf(
            SettingsMetric(text(languageMode, "songs"), songCount.toString()),
            SettingsMetric(text(languageMode, "albums"), albumCount.toString()),
            SettingsMetric(text(languageMode, "artists"), artistCount.toString()),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode))
            ))
            if (identityBackfill.total > 0 || identityBackfill.lxDeleted > 0) {
                add(SettingsMetric(
                    text(languageMode, "identity.backfill.progress"),
                    "${identityBackfill.processed}/${identityBackfill.total} · " +
                        "${text(languageMode, "identity.backfill.merged")} ${identityBackfill.merged} · " +
                        "${text(languageMode, "identity.backfill.pending")} ${identityBackfill.pending}"
                ))
                add(SettingsMetric(
                    text(languageMode, "identity.backfill.lx"),
                    "${identityBackfill.lxMigrated}/${identityBackfill.lxDeleted}"
                ))
            }
        }
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), backPage, onNavigate))
            add(SettingsAction(
                label = text(languageMode, "scan.library"),
                onClick = Runnable { onLoadLibrary() },
                icon = EchoIconKind.Sync,
                section = text(languageMode, "settings.section.library"),
                entryId = SettingsEntryId.LibraryScan
            ))
            add(SettingsAction(
                label = text(languageMode, "import.audio.files"),
                onClick = Runnable { onOpenAudioFilePicker() },
                icon = EchoIconKind.Import,
                section = text(languageMode, "settings.section.library"),
                entryId = SettingsEntryId.ImportAudioFiles
            ))
            add(SettingsAction(
                label = text(languageMode, "import.audio.folder"),
                onClick = Runnable { onOpenAudioFolderPicker() },
                icon = EchoIconKind.Folder,
                section = text(languageMode, "settings.section.library"),
                entryId = SettingsEntryId.ImportAudioFolder
            ))
            add(SettingsAction(
                label = text(languageMode, "identity.backfill.rebuild"),
                onClick = Runnable { onRebuildSongIdentity() },
                description = identityBackfillDescription(languageMode, identityBackfill),
                enabled = !identityBackfill.active,
                progress = identityBackfillProgress(languageMode, identityBackfill),
                icon = EchoIconKind.Refresh,
                section = text(languageMode, "settings.section.metadata"),
                entryId = SettingsEntryId.IdentityRebuild
            ))
            add(SettingsAction(
                label = text(languageMode, "library.dedup.safe"),
                description = text(languageMode, "library.dedup.safe.description"),
                value = if (dedupMode == LibraryDedupMode.SAFE) {
                    text(languageMode, "selected")
                } else "",
                style = SettingsActionStyle.Choice,
                checked = dedupMode == LibraryDedupMode.SAFE,
                onClick = Runnable { onDedupModeChange(LibraryDedupMode.SAFE) },
                icon = EchoIconKind.Library,
                section = text(languageMode, "library.dedup.mode"),
                entryId = SettingsEntryId.LibraryDedupSafe
            ))
            add(SettingsAction(
                label = text(languageMode, "library.dedup.aggressive"),
                description = text(languageMode, "library.dedup.aggressive.description"),
                value = if (dedupMode == LibraryDedupMode.AGGRESSIVE) {
                    text(languageMode, "selected")
                } else "",
                style = SettingsActionStyle.Choice,
                checked = dedupMode == LibraryDedupMode.AGGRESSIVE,
                onClick = Runnable { onDedupModeChange(LibraryDedupMode.AGGRESSIVE) },
                icon = EchoIconKind.Library,
                section = text(languageMode, "library.dedup.mode"),
                entryId = SettingsEntryId.LibraryDedupAggressive
            ))
            add(SettingsAction(
                label = text(languageMode, "library.dedup.candidates"),
                description = text(languageMode, "library.dedup.candidates.description"),
                value = duplicateCandidateCount.toString(),
                style = SettingsActionStyle.Navigation,
                onClick = Runnable { onNavigate(SettingsPage.DuplicateCandidates) },
                icon = EchoIconKind.Library,
                section = text(languageMode, "library.dedup.mode"),
                entryId = SettingsEntryId.DuplicateCandidateCenter
            ))
            if (identityBackfill.running) {
                add(SettingsAction(
                    label = text(languageMode, "identity.backfill.cancel"),
                    onClick = Runnable { onCancelIdentityBackfill() },
                    icon = EchoIconKind.Remove,
                    section = text(languageMode, "settings.section.metadata"),
                    entryId = SettingsEntryId.IdentityRebuild
                ))
            }
            if (hiddenItems.isNotEmpty()) {
                add(SettingsAction(
                    text(languageMode, "library.hidden.restore.all") + " (${hiddenItems.size})",
                    Runnable { onRestoreAllHidden() },
                    icon = EchoIconKind.Refresh,
                    section = text(languageMode, "settings.section.hidden"),
                    entryId = SettingsEntryId.RestoreHiddenLibraryItems
                ))
                hiddenItems.forEach { item ->
                    add(SettingsAction(
                        text(languageMode, "library.hidden.restore") + ": " + item.label,
                        Runnable { onRestoreHidden(item.sourceKey) },
                        icon = EchoIconKind.Refresh,
                        section = text(languageMode, "settings.section.hidden"),
                        entryId = SettingsEntryId.RestoreHiddenLibraryItems
                    ))
                }
            }
        }
        return buildContent(text(languageMode, "library"), metrics, actions)
    }

    private fun identityBackfillDescription(
        languageMode: String,
        status: IdentityBackfillStatusUi
    ): String {
        if (status.state == IdentityBackfillStateUi.IDLE) return ""
        val parts = mutableListOf(
            text(
                languageMode,
                when (status.state) {
                    IdentityBackfillStateUi.IDLE -> "identity.backfill.state.idle"
                    IdentityBackfillStateUi.QUEUED -> "identity.backfill.state.queued"
                    IdentityBackfillStateUi.RUNNING -> "identity.backfill.state.running"
                    IdentityBackfillStateUi.COMPLETED -> "identity.backfill.state.completed"
                    IdentityBackfillStateUi.FAILED -> "identity.backfill.state.failed"
                    IdentityBackfillStateUi.CANCELLED -> "identity.backfill.state.cancelled"
                }
            )
        )
        if (status.state == IdentityBackfillStateUi.RUNNING && status.stage.isNotBlank()) {
            parts += text(
                languageMode,
                when (status.stage.uppercase(Locale.ROOT)) {
                    "NORMALIZE" -> "identity.backfill.stage.normalize"
                    "CLASSIFY" -> "identity.backfill.stage.classify"
                    "INGEST" -> "identity.backfill.stage.ingest"
                    "LX_CLEANUP" -> "identity.backfill.stage.cleanup"
                    "VALIDATE" -> "identity.backfill.stage.validate"
                    else -> "identity.backfill.stage.processing"
                }
            )
        }
        if (status.total > 0) {
            parts += "${status.processed}/${status.total}"
        }
        if (status.state != IdentityBackfillStateUi.QUEUED) {
            parts += "${text(languageMode, "identity.backfill.merged")} ${status.merged}"
            parts += "${text(languageMode, "identity.backfill.pending")} ${status.pending}"
        }
        if (status.state == IdentityBackfillStateUi.FAILED && status.errorMessage.isNotBlank()) {
            parts += status.errorMessage
        }
        return parts.joinToString(" · ")
    }

    private fun identityBackfillProgress(
        languageMode: String,
        status: IdentityBackfillStatusUi
    ): SettingsActionProgress? {
        val fraction = when (status.state) {
            IdentityBackfillStateUi.IDLE -> return null
            IdentityBackfillStateUi.QUEUED -> null
            IdentityBackfillStateUi.RUNNING -> status.progressFraction
            IdentityBackfillStateUi.COMPLETED -> 1f
            IdentityBackfillStateUi.FAILED,
            IdentityBackfillStateUi.CANCELLED -> status.progressFraction ?: return null
        }
        return SettingsActionProgress(
            fraction = fraction,
            contentDescription = identityBackfillDescription(languageMode, status)
        )
    }

    fun duplicateCandidates(
        languageMode: String,
        backPage: SettingsPage,
        center: DuplicateCandidateCenterUi,
        onNavigate: (SettingsPage) -> Unit,
        onConfirm: (Long, Long) -> Unit,
        onConfirmBatch: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "library.dedup.pending"), center.total.toString()),
            SettingsMetric(
                text(languageMode, "library.dedup.review.required"),
                center.reviewRequired.toString()
            ),
            SettingsMetric(
                text(languageMode, "library.dedup.high.confidence"),
                center.highConfidenceCount.toString()
            )
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), backPage, onNavigate))
            if (center.highConfidenceCount > 0) {
                add(SettingsAction(
                    label = text(languageMode, "library.dedup.confirm.batch") +
                        " (${center.highConfidenceCount})",
                    description = text(languageMode, "library.dedup.confirm.batch.description"),
                    onClick = Runnable { onConfirmBatch() },
                    icon = EchoIconKind.Sync,
                    section = text(languageMode, "library.dedup.candidates"),
                    entryId = SettingsEntryId.DuplicateCandidateCenter
                ))
            }
            center.items.forEach { candidate ->
                add(SettingsAction(
                    label = "${candidate.leftLabel} ↔ ${candidate.rightLabel}",
                    description = buildString {
                        append(text(languageMode, "library.dedup.score"))
                        append(" ")
                        append("%.1f%%".format(candidate.score * 100.0))
                        append(" · ")
                        append(text(languageMode, "library.dedup.margin"))
                        append(" ")
                        append("%.1f%%".format(candidate.margin * 100.0))
                        append(" · ")
                        append(candidate.relationType)
                    },
                    value = if (candidate.batchEligible) {
                        text(languageMode, "library.dedup.high.confidence")
                    } else {
                        text(languageMode, "library.dedup.needs.review")
                    },
                    onClick = Runnable {
                        onConfirm(candidate.leftRecordingId, candidate.rightRecordingId)
                    },
                    enabled = candidate.batchEligible,
                    icon = EchoIconKind.Library,
                    section = text(languageMode, "library.dedup.candidates"),
                    entryId = SettingsEntryId.DuplicateCandidateCenter
                ))
            }
        }
        return buildContent(text(languageMode, "library.dedup.candidates"), metrics, actions)
    }

    fun lyricsGroup(
        languageMode: String,
        offsetMs: Long,
        onlineLyricsEnabled: Boolean,
        statusBarLyricsEnabled: Boolean,
        systemMediaLyricsTitleEnabled: Boolean,
        floatingLyricsEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOnlineLyricsEnabledChange: (Boolean) -> Unit,
        onSystemMediaLyricsTitleEnabledChange: (Boolean) -> Unit,
        onStatusBarLyricsEnabledChange: (Boolean) -> Unit = {},
        onReloadLyrics: () -> Unit,
        onImportCurrentLyrics: (() -> Unit)? = null,
        onImportLyricsDirectory: (() -> Unit)? = null,
        onViewLyricsImportReport: (() -> Unit)? = null,
        onApplyLyricsOffset: (Long) -> Unit
    ): SettingsPageStateContent = lyricsContent(
        title = groupTitle(languageMode, "lyrics"),
        backPage = SettingsPage.Home,
        languageMode = languageMode,
        offsetMs = offsetMs,
        onlineLyricsEnabled = onlineLyricsEnabled,
        statusBarLyricsEnabled = statusBarLyricsEnabled,
        systemMediaLyricsTitleEnabled = systemMediaLyricsTitleEnabled,
        floatingLyricsEnabled = floatingLyricsEnabled,
        overlayPermissionGranted = overlayPermissionGranted,
        onNavigate = onNavigate,
        onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
        onSystemMediaLyricsTitleEnabledChange = onSystemMediaLyricsTitleEnabledChange,
        onStatusBarLyricsEnabledChange = onStatusBarLyricsEnabledChange,
        onReloadLyrics = onReloadLyrics,
        onImportCurrentLyrics = onImportCurrentLyrics,
        onImportLyricsDirectory = onImportLyricsDirectory,
        onViewLyricsImportReport = onViewLyricsImportReport,
        onApplyLyricsOffset = onApplyLyricsOffset
    )

    fun lyrics(
        languageMode: String,
        offsetMs: Long,
        onlineLyricsEnabled: Boolean,
        statusBarLyricsEnabled: Boolean,
        systemMediaLyricsTitleEnabled: Boolean,
        floatingLyricsEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOnlineLyricsEnabledChange: (Boolean) -> Unit,
        onSystemMediaLyricsTitleEnabledChange: (Boolean) -> Unit,
        onStatusBarLyricsEnabledChange: (Boolean) -> Unit = {},
        onReloadLyrics: () -> Unit,
        onImportCurrentLyrics: (() -> Unit)? = null,
        onImportLyricsDirectory: (() -> Unit)? = null,
        onViewLyricsImportReport: (() -> Unit)? = null,
        onApplyLyricsOffset: (Long) -> Unit
    ): SettingsPageStateContent = lyricsContent(
        title = text(languageMode, "lyrics"),
        backPage = SettingsBackStack.parent(SettingsPage.Lyrics),
        languageMode = languageMode,
        offsetMs = offsetMs,
        onlineLyricsEnabled = onlineLyricsEnabled,
        statusBarLyricsEnabled = statusBarLyricsEnabled,
        systemMediaLyricsTitleEnabled = systemMediaLyricsTitleEnabled,
        floatingLyricsEnabled = floatingLyricsEnabled,
        overlayPermissionGranted = overlayPermissionGranted,
        onNavigate = onNavigate,
        onOnlineLyricsEnabledChange = onOnlineLyricsEnabledChange,
        onSystemMediaLyricsTitleEnabledChange = onSystemMediaLyricsTitleEnabledChange,
        onStatusBarLyricsEnabledChange = onStatusBarLyricsEnabledChange,
        onReloadLyrics = onReloadLyrics,
        onImportCurrentLyrics = onImportCurrentLyrics,
        onImportLyricsDirectory = onImportLyricsDirectory,
        onViewLyricsImportReport = onViewLyricsImportReport,
        onApplyLyricsOffset = onApplyLyricsOffset
    )

    private fun lyricsContent(
        title: String,
        backPage: SettingsPage,
        languageMode: String,
        offsetMs: Long,
        onlineLyricsEnabled: Boolean,
        statusBarLyricsEnabled: Boolean,
        systemMediaLyricsTitleEnabled: Boolean,
        floatingLyricsEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOnlineLyricsEnabledChange: (Boolean) -> Unit,
        onSystemMediaLyricsTitleEnabledChange: (Boolean) -> Unit,
        onStatusBarLyricsEnabledChange: (Boolean) -> Unit,
        onReloadLyrics: () -> Unit,
        onImportCurrentLyrics: (() -> Unit)?,
        onImportLyricsDirectory: (() -> Unit)?,
        onViewLyricsImportReport: (() -> Unit)?,
        onApplyLyricsOffset: (Long) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "offset"), lyricsOffsetLabel(offsetMs)),
            SettingsMetric(text(languageMode, "online.lyrics"), enabledLabel(onlineLyricsEnabled, languageMode)),
            SettingsMetric(text(languageMode, "status.bar.lyrics"), enabledLabel(statusBarLyricsEnabled, languageMode)),
            SettingsMetric(text(languageMode, "system.media.lyrics.title"), enabledLabel(systemMediaLyricsTitleEnabled, languageMode)),
            SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(floatingLyricsEnabled, languageMode)),
            SettingsMetric(text(languageMode, "overlay.permission"), permissionLabel(overlayPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "provider"), "LRCLIB"),
            SettingsMetric(text(languageMode, "local.lyrics"), text(languageMode, "same.name.lrc"))
        )
        val actions = buildList {
            add(backNavigationAction(text(languageMode, "back"), backPage, onNavigate))
            add(
                SettingsAction(
                    label = text(languageMode, "online.lyrics"),
                    onClick = Runnable { onOnlineLyricsEnabledChange(!onlineLyricsEnabled) },
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Lyrics,
                    checked = onlineLyricsEnabled,
                    section = text(languageMode, "settings.section.lyrics"),
                    entryId = SettingsEntryId.OnlineLyrics
                )
            )
            add(SettingsAction(
                label = text(languageMode, "reload.lyrics"),
                onClick = Runnable { onReloadLyrics() },
                icon = EchoIconKind.Sync,
                section = text(languageMode, "settings.section.lyrics"),
                entryId = SettingsEntryId.ReloadLyrics
            ))
            if (onImportCurrentLyrics != null) add(
                SettingsAction(
                    label = "导入/替换当前歌曲歌词",
                    description = "支持 LRC、TTML、XML 与 TXT",
                    onClick = Runnable(onImportCurrentLyrics),
                    icon = EchoIconKind.Import,
                    section = text(languageMode, "settings.section.lyrics"),
                    entryId = SettingsEntryId.ImportCurrentLyrics
                )
            )
            if (onImportLyricsDirectory != null) add(
                SettingsAction(
                    label = "批量导入歌词目录",
                    description = "递归扫描并仅写入安全的唯一匹配",
                    onClick = Runnable(onImportLyricsDirectory),
                    icon = EchoIconKind.Folder,
                    section = text(languageMode, "settings.section.lyrics"),
                    entryId = SettingsEntryId.ImportLyricsDirectory
                )
            )
            if (onViewLyricsImportReport != null) add(
                SettingsAction(
                    label = "查看最近批量报告",
                    onClick = Runnable(onViewLyricsImportReport),
                    icon = EchoIconKind.Info,
                    section = text(languageMode, "settings.section.lyrics"),
                    entryId = SettingsEntryId.LyricsImportReport
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "status.bar.lyrics"),
                    onClick = Runnable { onStatusBarLyricsEnabledChange(!statusBarLyricsEnabled) },
                    description = text(languageMode, "status.bar.lyrics.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Lyrics,
                    checked = statusBarLyricsEnabled,
                    section = text(languageMode, "settings.section.system.display"),
                    entryId = SettingsEntryId.StatusBarLyrics
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "system.media.lyrics.title"),
                    onClick = Runnable { onSystemMediaLyricsTitleEnabledChange(!systemMediaLyricsTitleEnabled) },
                    description = text(languageMode, "system.media.lyrics.title.description"),
                    style = SettingsActionStyle.Toggle,
                    icon = EchoIconKind.Lyrics,
                    checked = systemMediaLyricsTitleEnabled,
                    section = text(languageMode, "settings.section.system.display"),
                    entryId = SettingsEntryId.SystemMediaLyricsTitle
                )
            )
            add(
                navigationAction(
                    text(languageMode, "floating.lyrics"),
                    SettingsPage.FloatingLyrics,
                    onNavigate,
                    text(languageMode, "floating.lyrics.description"),
                    enabledLabel(floatingLyricsEnabled, languageMode)
                ).copy(
                    section = text(languageMode, "settings.section.system.display"),
                    entryId = SettingsEntryId.FloatingLyrics
                )
            )
            listOf(-1000L, -500L, 0L, 500L, 1000L).forEach { offset ->
                add(
                    lyricsOffsetAction(languageMode, offsetMs, offset, onApplyLyricsOffset).copy(
                        section = text(languageMode, "settings.section.timing"),
                        entryId = SettingsEntryId.LyricsOffset
                    )
                )
            }
        }
        return buildContent(title, metrics, actions)
    }

    private fun homeCategorySummary(
        categoryId: SettingsCategoryId,
        languageMode: String,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus
    ): String = when (categoryId) {
        SettingsCategoryId.PlaybackAudio ->
            playbackSpeedLabel(preferences.playbackSpeed) + " · " + appVolumeLabel(preferences.appVolume)
        SettingsCategoryId.LibraryMetadata ->
            runtime.librarySongCount.toString() + " " + text(languageMode, "songs")
        SettingsCategoryId.SourcesAccountsSync ->
            streamingQualityLabel(preferences.streamingAudioQuality, languageMode) + " · " +
                if (runtime.streamingGatewayConfigured) text(languageMode, "connected") else text(languageMode, "settings.summary.local.first")
        SettingsCategoryId.LyricsSystemDisplay -> when {
            preferences.floatingLyricsEnabled -> text(languageMode, "floating.lyrics")
            preferences.statusBarLyricsEnabled -> text(languageMode, "status.bar.lyrics")
            runtime.onlineLyricsEnabled -> text(languageMode, "online.lyrics")
            else -> text(languageMode, "off")
        }
        SettingsCategoryId.DownloadsStorageBackup -> text(languageMode, "settings.summary.downloads.backup")
        SettingsCategoryId.AppearanceInteraction ->
            AppLanguage.themeLabel(preferences.themeMode, languageMode) + " · " +
                AppLanguage.accentLabel(preferences.accentMode, languageMode)
        SettingsCategoryId.SystemPrivacyHelp -> runtime.appVersionName.ifBlank {
            permissionLabel(runtime.audioPermissionGranted, languageMode)
        }
    }

    private fun groupNavigationAction(
        languageMode: String,
        key: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit,
        section: String = ""
    ): SettingsAction = SettingsAction(
        label = groupTitle(languageMode, key),
        onClick = Runnable { onNavigate(page) },
        description = groupDescription(languageMode, key),
        style = SettingsActionStyle.Navigation,
        icon = settingsIconForPage(page),
        section = section
    )

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit,
        isBack: Boolean = false
    ): SettingsAction = SettingsAction(
        label = label,
        onClick = Runnable { onNavigate(page) },
        style = SettingsActionStyle.Navigation,
        icon = if (isBack) EchoIconKind.Back else settingsIconForPage(page),
        isBack = isBack
    )

    private fun backNavigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit
    ): SettingsAction = navigationAction(label, page, onNavigate, isBack = true)

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit,
        description: String,
        value: String = "",
        isBack: Boolean = false
    ): SettingsAction = SettingsAction(
        label = label,
        onClick = Runnable { onNavigate(page) },
        description = description,
        value = value,
        style = SettingsActionStyle.Navigation,
        icon = if (isBack) EchoIconKind.Back else settingsIconForPage(page),
        isBack = isBack
    )

    private fun settingsIconForPage(page: SettingsPage): EchoIconKind = when (page) {
        SettingsPage.AppearanceGroup, SettingsPage.Appearance, SettingsPage.PageBackground -> EchoIconKind.Palette
        SettingsPage.AdvancedTheme -> EchoIconKind.Sparkle
        SettingsPage.Accent -> EchoIconKind.Swatch
        SettingsPage.Language -> EchoIconKind.Language
        SettingsPage.PlaybackGroup, SettingsPage.AudioEffects,
        SettingsPage.PlaybackSpeed -> EchoIconKind.Gauge
        SettingsPage.AppVolume -> EchoIconKind.Volume
        SettingsPage.LyricsGroup, SettingsPage.Lyrics, SettingsPage.StatusBarLyrics -> EchoIconKind.Lyrics
        SettingsPage.FloatingLyrics -> EchoIconKind.Permission
        SettingsPage.SourcesGroup, SettingsPage.StreamingGateway -> EchoIconKind.Network
        SettingsPage.StreamingAudioQuality -> EchoIconKind.Gauge
        SettingsPage.LibraryGroup, SettingsPage.Library,
        SettingsPage.DuplicateCandidates -> EchoIconKind.Library
        SettingsPage.AboutGroup -> EchoIconKind.Info
        SettingsPage.Downloads -> EchoIconKind.Download
        SettingsPage.SleepTimer -> EchoIconKind.Timer
        SettingsPage.NowPlayingGestures -> EchoIconKind.More
        SettingsPage.PlaybackRestore -> EchoIconKind.Refresh
        SettingsPage.ReplayGain -> EchoIconKind.Gauge
        SettingsPage.ShareStyle -> EchoIconKind.Upload
        SettingsPage.Home -> EchoIconKind.Back
    }

    private fun permissionLabel(granted: Boolean, languageMode: String): String =
        if (granted) text(languageMode, "granted") else text(languageMode, "missing")

    private fun booleanLeafPage(
        languageMode: String,
        currentPage: SettingsPage,
        titleKey: String,
        descriptionKey: String,
        enableKey: String,
        disableKey: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, titleKey), enabledLabel(enabled, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, descriptionKey))
        )
        val actions = listOf(
            backNavigationAction(text(languageMode, "back"), SettingsBackStack.parent(currentPage), onNavigate),
            SettingsAction(
                label = text(languageMode, titleKey),
                onClick = Runnable { onToggle(!enabled) },
                description = text(languageMode, descriptionKey),
                style = SettingsActionStyle.Toggle,
                icon = settingsIconForPage(currentPage),
                checked = enabled
            )
        )
        return buildContent(text(languageMode, titleKey), metrics, actions)
    }

    private fun enabledLabel(enabled: Boolean, languageMode: String): String =
        if (enabled) text(languageMode, "enabled") else text(languageMode, "disabled")

    private fun sleepTimerLabel(remainingMs: Long, languageMode: String): String {
        if (remainingMs <= 0L) {
            return text(languageMode, "off")
        }
        val minutes = maxOf(1L, (remainingMs + 59999L) / 60000L)
        return minutes.toString() + text(languageMode, "min.left")
    }

    private fun selectedEndpointLabel(label: String, currentEndpoint: String, optionEndpoint: String, languageMode: String): String {
        return if (StreamingGatewayEndpoint.normalize(currentEndpoint) == StreamingGatewayEndpoint.normalize(optionEndpoint)) {
            label + text(languageMode, "selected")
        } else {
            label
        }
    }

    private fun streamingGatewayOption(
        languageMode: String,
        currentEndpoint: String,
        endpoint: String,
        labelKey: String,
        onApplyEndpoint: (String) -> Unit
    ): SettingsAction = SettingsAction(
        label = selectedEndpointLabel(text(languageMode, labelKey), currentEndpoint, endpoint, languageMode),
        onClick = Runnable { onApplyEndpoint(endpoint) },
        style = SettingsActionStyle.Choice,
        icon = EchoIconKind.Network,
        checked = StreamingGatewayEndpoint.normalize(currentEndpoint) ==
            StreamingGatewayEndpoint.normalize(endpoint)
    )

    private fun themeOption(
        languageMode: String,
        currentMode: String,
        mode: String,
        onApplyTheme: (String) -> Unit
    ): SettingsAction {
        val label = if (EchoTheme.normalizeMode(currentMode) == EchoTheme.normalizeMode(mode)) {
            AppLanguage.themeLabel(mode, languageMode) + text(languageMode, "selected")
        } else {
            AppLanguage.themeLabel(mode, languageMode)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyTheme(mode) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Palette,
            checked = EchoTheme.normalizeMode(currentMode) == EchoTheme.normalizeMode(mode)
        )
    }

    private fun accentOption(
        languageMode: String,
        currentAccent: String,
        accent: String,
        onApplyAccent: (String) -> Unit
    ): SettingsAction {
        val label = if (EchoTheme.normalizeAccent(currentAccent) == EchoTheme.normalizeAccent(accent)) {
            AppLanguage.accentLabel(accent, languageMode) + text(languageMode, "selected")
        } else {
            AppLanguage.accentLabel(accent, languageMode)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyAccent(accent) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Swatch,
            checked = EchoTheme.normalizeAccent(currentAccent) == EchoTheme.normalizeAccent(accent)
        )
    }

    private fun dynamicAccentOption(
        languageMode: String,
        currentAccent: String,
        accent: String,
        descriptionKey: String,
        enabled: Boolean,
        onApplyAccent: (String) -> Unit
    ): SettingsAction {
        val selected = EchoTheme.normalizeAccent(currentAccent) == EchoTheme.normalizeAccent(accent)
        val label = AppLanguage.accentLabel(accent, languageMode) +
            if (selected) text(languageMode, "selected") else ""
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyAccent(accent) },
            description = text(languageMode, descriptionKey),
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Palette,
            checked = selected,
            enabled = enabled
        )
    }

    private fun languageOption(
        languageMode: String,
        optionMode: String,
        onApplyLanguage: (String) -> Unit
    ): SettingsAction {
        val label = if (AppLanguage.normalizeMode(languageMode) == AppLanguage.normalizeMode(optionMode)) {
            languageOptionLabel(languageMode, optionMode) + text(languageMode, "selected")
        } else {
            languageOptionLabel(languageMode, optionMode)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyLanguage(optionMode) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Language,
            checked = AppLanguage.normalizeMode(languageMode) == AppLanguage.normalizeMode(optionMode)
        )
    }

    private fun languageOptionLabel(languageMode: String, optionMode: String): String {
        return when (AppLanguage.normalizeMode(optionMode)) {
            AppLanguage.MODE_CHINESE -> text(languageMode, "language.chinese")
            AppLanguage.MODE_ENGLISH -> text(languageMode, "language.english")
            else -> text(languageMode, "language.system")
        }
    }

    private fun MutableList<SettingsAction>.addPageBackgroundActions(
        languageMode: String,
        page: String,
        uri: String,
        onChoosePageBackground: (String) -> Unit,
        onClearPageBackground: (String) -> Unit
    ) {
        val pageLabel = pageBackgroundPageLabel(page, languageMode)
        add(
            SettingsAction(
                label = pageBackgroundActionLabel(languageMode, "choose.page.background", pageLabel),
                onClick = Runnable { onChoosePageBackground(page) },
                description = backgroundActionDescription(page, languageMode),
                style = SettingsActionStyle.Navigation,
                icon = EchoIconKind.Palette,
                value = backgroundStateLabel(uri, languageMode)
            )
        )
        if (uri.isNotBlank()) {
            add(
                SettingsAction(
                    label = pageBackgroundActionLabel(languageMode, "clear.page.background", pageLabel),
                    onClick = Runnable { onClearPageBackground(page) },
                    style = SettingsActionStyle.Destructive,
                    icon = EchoIconKind.Delete
                )
            )
        }
    }

    private fun pageBackgroundActionLabel(languageMode: String, actionKey: String, pageLabel: String): String {
        val separator = if (AppLanguage.isChinese(languageMode)) "：" else ": "
        return text(languageMode, actionKey) + separator + pageLabel
    }

    private fun pageBackgroundPageLabel(page: String, languageMode: String): String {
        return when (PageBackgrounds.normalizePage(page)) {
            PageBackgrounds.PAGE_ALL -> text(languageMode, "page.background.all")
            PageBackgrounds.PAGE_HOME -> text(languageMode, "tab.home")
            PageBackgrounds.PAGE_LIBRARY -> text(languageMode, "tab.library")
            PageBackgrounds.PAGE_PLAYER -> text(languageMode, "tab.playing")
            PageBackgrounds.PAGE_SETTINGS -> text(languageMode, "tab.settings")
            else -> text(languageMode, "page.background")
        }
    }

    private fun backgroundStateLabel(uri: String, languageMode: String): String =
        if (uri.isBlank()) text(languageMode, "off") else text(languageMode, "enabled")

    private fun backgroundActionDescription(page: String, languageMode: String): String =
        if (PageBackgrounds.normalizePage(page) == PageBackgrounds.PAGE_ALL) {
            text(languageMode, "page.background.all.description")
        } else {
            text(languageMode, "page.background.single.description")
        }

    private fun equalizerPresetOption(
        languageMode: String,
        settings: AudioEffectSettings,
        preset: Int,
        onApplyAudioEffects: (AudioEffectSettings) -> Unit
    ): SettingsAction {
        val label = if (settings.preset == preset) {
            equalizerPresetLabel(preset, languageMode) + text(languageMode, "selected")
        } else {
            equalizerPresetLabel(preset, languageMode)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyAudioEffects(settings.withEnabled(true).withPreset(preset)) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Gauge,
            checked = settings.preset == preset
        )
    }

    private fun strengthOption(
        languageMode: String,
        settings: AudioEffectSettings,
        labelKey: String,
        target: String,
        strength: Int,
        onApplyAudioEffects: (AudioEffectSettings) -> Unit
    ): SettingsAction {
        val selected = if (target == "bass") {
            settings.bassBoostStrength.toInt() == strength
        } else {
            settings.virtualizerStrength.toInt() == strength
        }
        val label = if (selected) {
            text(languageMode, labelKey) + " " + strengthLabel(strength.toShort()) + text(languageMode, "selected")
        } else {
            text(languageMode, labelKey) + " " + strengthLabel(strength.toShort())
        }
        return SettingsAction(
            label = label,
            onClick = Runnable {
                val next = if (target == "bass") {
                    settings.withEnabled(true).withBassBoostStrength(strength.toShort())
                } else {
                    settings.withEnabled(true).withVirtualizerStrength(strength.toShort())
                }
                onApplyAudioEffects(next)
            },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Gauge,
            checked = selected
        )
    }

    private fun loudnessOption(
        languageMode: String,
        settings: AudioEffectSettings,
        gainMb: Int,
        onApplyAudioEffects: (AudioEffectSettings) -> Unit
    ): SettingsAction {
        val label = if (settings.loudnessGainMb == gainMb) {
            text(languageMode, "loudness") + " " + loudnessLabel(gainMb) + text(languageMode, "selected")
        } else {
            text(languageMode, "loudness") + " " + loudnessLabel(gainMb)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyAudioEffects(settings.withEnabled(true).withLoudnessGainMb(gainMb)) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Gauge,
            checked = settings.loudnessGainMb == gainMb
        )
    }

    private fun lyricsOffsetAction(
        languageMode: String,
        currentOffsetMs: Long,
        offsetMs: Long,
        onApplyLyricsOffset: (Long) -> Unit
    ): SettingsAction {
        val label = if (normalizeLyricsOffsetMs(currentOffsetMs) == normalizeLyricsOffsetMs(offsetMs)) {
            lyricsOffsetLabel(offsetMs) + text(languageMode, "selected")
        } else {
            lyricsOffsetLabel(offsetMs)
        }
        return SettingsAction(
            label = label,
            onClick = Runnable { onApplyLyricsOffset(offsetMs) },
            style = SettingsActionStyle.Choice,
            icon = EchoIconKind.Gauge,
            checked = normalizeLyricsOffsetMs(currentOffsetMs) == normalizeLyricsOffsetMs(offsetMs)
        )
    }

    private fun playbackSpeedLabel(speed: Float): String {
        val normalized = normalizePlaybackSpeed(speed)
        if (abs(normalized - round(normalized)) < 0.01f) {
            return round(normalized).toInt().toString() + "x"
        }
        return String.format(Locale.ROOT, "%.2fx", normalized).replace(Regex("0x$"), "x")
    }

    private fun appVolumeLabel(volume: Float): String =
        (normalizeAppVolume(volume) * 100.0f).roundToInt().toString() + "%"

    private fun audioEffectsLabel(settings: AudioEffectSettings?, languageMode: String): String {
        val effects = settings ?: AudioEffectSettings.DEFAULT
        if (!effects.enabled) {
            return text(languageMode, "off")
        }
        return text(languageMode, "enabled") + " / " + equalizerPresetLabel(effects.preset, languageMode)
    }

    private fun equalizerPresetLabel(preset: Int, languageMode: String): String {
        return when (preset) {
            AudioEffectSettings.PRESET_CUSTOM -> text(languageMode, "eq.custom")
            0 -> text(languageMode, "eq.normal")
            1 -> text(languageMode, "eq.classical")
            2 -> text(languageMode, "eq.dance")
            else -> text(languageMode, "eq.preset") + " " + preset
        }
    }

    private fun strengthLabel(strength: Short): String =
        (strength.coerceIn(0, 1000).toInt() / 10).toString() + "%"

    private fun loudnessLabel(gainMb: Int): String {
        if (gainMb == 0) {
            return "0 dB"
        }
        return String.format(Locale.ROOT, "%+.1f dB", gainMb / 100.0f)
    }

    private fun normalizePlaybackSpeed(speed: Float): Float {
        if (speed < 0.5f) {
            return 0.5f
        }
        if (speed > 2.0f) {
            return 2.0f
        }
        return round(speed * 100.0f) / 100.0f
    }

    private fun normalizeAppVolume(volume: Float): Float {
        if (volume < 0.0f) {
            return 0.0f
        }
        if (volume > 1.0f) {
            return 1.0f
        }
        return round(volume * 100.0f) / 100.0f
    }

    private fun lyricsOffsetLabel(offsetMs: Long): String {
        val normalized = normalizeLyricsOffsetMs(offsetMs)
        if (normalized == 0L) {
            return "0 ms"
        }
        val sign = if (normalized > 0L) "+" else "-"
        val absolute = abs(normalized)
        if (absolute % 1000L == 0L) {
            return sign + (absolute / 1000L) + " s"
        }
        return sign + String.format(Locale.ROOT, "%.1f", absolute / 1000.0) + " s"
    }

    private fun normalizeLyricsOffsetMs(offsetMs: Long): Long =
        offsetMs.coerceIn(-5000L, 5000L)

    private fun groupTitle(languageMode: String, key: String): String =
        text(languageMode, "settings.group.$key")

    private fun groupDescription(languageMode: String, key: String): String =
        text(languageMode, "settings.group.$key.description")

    private fun text(languageMode: String, key: String): String =
        AppLanguage.text(languageMode, key)

    private fun streamingQualityLabel(quality: String, languageMode: String): String {
        return when (StreamingQualityPreference.normalize(quality)) {
            StreamingQualityPreference.AUTO -> text(languageMode, "quality.auto")
            StreamingQualityPreference.STANDARD -> text(languageMode, "quality.standard")
            StreamingQualityPreference.HIGH -> text(languageMode, "quality.high")
            StreamingQualityPreference.LOSSLESS -> text(languageMode, "quality.lossless")
            StreamingQualityPreference.HIRES -> text(languageMode, "quality.hires")
            else -> text(languageMode, "quality.high")
        }
    }

    private fun shareStyleLabel(style: String, languageMode: String): String {
        return when (TrackShareStyle.normalize(style)) {
            TrackShareStyle.PLATFORM_CARD -> text(languageMode, "share.style.platform.card")
            TrackShareStyle.CARD -> text(languageMode, "share.style.card")
            else -> text(languageMode, "share.style.text")
        }
    }

    private fun pageBackgroundSummary(pageBackgrounds: PageBackgrounds, languageMode: String): String {
        val customCount = listOf(
            pageBackgrounds.homeUri,
            pageBackgrounds.libraryUri,
            pageBackgrounds.playerUri,
            pageBackgrounds.settingsUri
        ).count { it.isNotBlank() }
        if (pageBackgrounds.sharedUri.isBlank() && customCount == 0) {
            return text(languageMode, "off")
        }
        if (customCount == 0) {
            return text(languageMode, "page.background.all")
        }
        return customCount.toString() + text(languageMode, "page.background.custom.count")
    }

    private fun SettingsAction.toSettingsItem(): SettingsItem {
        return if (description.isBlank()) {
            SettingsItem.Action(label)
        } else {
            SettingsItem.Navigation(label, description)
        }
    }
}
