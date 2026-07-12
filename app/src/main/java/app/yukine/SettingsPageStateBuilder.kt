package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsActionStyle
import app.yukine.ui.SettingsImageDialog
import app.yukine.ui.SettingsMetric
import app.yukine.ui.EchoTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

internal data class SettingsPageStateContent(
    val uiState: SettingsUiState,
    val actions: List<SettingsAction>
)

internal object SettingsPageStateBuilder {
    fun build(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ): SettingsUiState = buildContent(title, metrics, actions).uiState

    fun buildContent(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ): SettingsPageStateContent {
        val stableMetrics = metrics.toList()
        val stableActions = actions.toList()
        return SettingsPageStateContent(
            uiState = SettingsUiState(
                title = title,
                metrics = stableMetrics,
                items = stableActions.map { action -> action.toSettingsItem() } +
                        stableMetrics.map { metric -> SettingsItem.Metric(metric.label, metric.value) }
            ),
            actions = stableActions
        )
    }

    fun home(
        languageMode: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        playbackServiceConnected: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onRequestNeededPermissions: () -> Unit,
        onOpenDownloads: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(
                text(languageMode, "settings.start"),
                text(languageMode, "settings.start.hint"),
                compact = true
            ),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "notification.permission"), permissionLabel(notificationPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected"))
        )
        val actions = buildList {
            if (!audioPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "settings.grant.music.access"),
                        onClick = Runnable { onRequestNeededPermissions() },
                        description = text(languageMode, "settings.grant.music.access.hint"),
                        style = SettingsActionStyle.Navigation,
                        section = text(languageMode, "settings.section.start")
                    )
                )
            }
            add(groupNavigationAction(
                languageMode,
                "library",
                SettingsPage.LibraryGroup,
                onNavigate,
                text(languageMode, "settings.section.start")
            ))
            add(groupNavigationAction(
                languageMode,
                "playback",
                SettingsPage.PlaybackGroup,
                onNavigate,
                text(languageMode, "settings.section.start")
            ))
            add(groupNavigationAction(
                languageMode,
                "lyrics",
                SettingsPage.LyricsGroup,
                onNavigate,
                text(languageMode, "settings.section.start")
            ))
            add(groupNavigationAction(
                languageMode,
                "appearance",
                SettingsPage.AppearanceGroup,
                onNavigate,
                text(languageMode, "settings.section.start")
            ))
            add(groupNavigationAction(
                languageMode,
                "sources",
                SettingsPage.SourcesGroup,
                onNavigate,
                text(languageMode, "settings.section.more")
            ))
            add(SettingsAction(
                label = text(languageMode, "download.manager"),
                onClick = Runnable { onOpenDownloads() },
                description = text(languageMode, "download.manager.hint"),
                style = SettingsActionStyle.Navigation,
                section = text(languageMode, "settings.section.more")
            ))
            add(groupNavigationAction(
                languageMode,
                "about",
                SettingsPage.AboutGroup,
                onNavigate,
                text(languageMode, "settings.section.more")
            ))
        }
        return buildContent(text(languageMode, "tab.settings"), metrics, actions)
    }

    fun aboutGroup(
        languageMode: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        playbackServiceConnected: Boolean,
        debugPromptsEnabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onExportBackup: () -> Unit,
        onImportBackup: () -> Unit,
        onDebugPromptsEnabledChange: (Boolean) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "version"), BuildConfig.VERSION_NAME),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "notification.permission"), permissionLabel(notificationPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected"))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            SettingsAction(
                label = text(languageMode, "qq.group"),
                onClick = Runnable { },
                description = text(languageMode, "qq.group.hint"),
                value = "1013122077",
                style = SettingsActionStyle.Navigation,
                imageDialog = SettingsImageDialog(
                    title = text(languageMode, "qq.group"),
                    message = text(languageMode, "qq.group.number"),
                    imageResId = R.drawable.qq_group_qr,
                    imageContentDescription = text(languageMode, "qq.group.qr.description"),
                    dismissLabel = text(languageMode, "close")
                )
            ),
            SettingsAction(
                label = text(languageMode, "debug.prompts"),
                onClick = Runnable { onDebugPromptsEnabledChange(!debugPromptsEnabled) },
                description = text(languageMode, "debug.prompts.hint"),
                style = SettingsActionStyle.Toggle,
                checked = debugPromptsEnabled
            ),
            SettingsAction(text(languageMode, "backup.export"), Runnable { onExportBackup() }),
            SettingsAction(
                label = text(languageMode, "backup.import"),
                onClick = Runnable { onImportBackup() },
                description = text(languageMode, "backup.import.description"),
                style = SettingsActionStyle.Destructive
            )
        )
        return buildContent(groupTitle(languageMode, "about"), metrics, actions)
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
        onNavigate: (SettingsPage) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)),
            SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)),
            SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)),
            SettingsMetric(text(languageMode, "page.background"), pageBackgroundSummary(pageBackgrounds, languageMode)),
            SettingsMetric(text(languageMode, "description"), groupDescription(languageMode, "appearance"))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            navigationAction(
                text(languageMode, "appearance"),
                SettingsPage.Appearance,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.themeLabel(themeMode, languageMode)
            ),
            navigationAction(
                text(languageMode, "accent"),
                SettingsPage.Accent,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.accentLabel(accentMode, languageMode)
            ),
            navigationAction(
                text(languageMode, "language"),
                SettingsPage.Language,
                onNavigate,
                text(languageMode, "settings.choose.hint"),
                AppLanguage.labelFor(languageMode)
            ),
            navigationAction(
                text(languageMode, "page.background"),
                SettingsPage.PageBackground,
                onNavigate,
                text(languageMode, "page.background.hint"),
                pageBackgroundSummary(pageBackgrounds, languageMode)
            ),
            SettingsAction(
                label = text(languageMode, "background.blur"),
                onClick = Runnable {
                    onCustomBackgroundBlurEnabledChange(!customBackgroundBlurEnabled)
                },
                description = text(languageMode, "background.blur.hint"),
                style = SettingsActionStyle.Toggle,
                checked = customBackgroundBlurEnabled
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
                onSliderValueChange = onCustomBackgroundBlurRadiusChange
            ),
            SettingsAction(
                label = text(languageMode, "glass.blur"),
                onClick = Runnable { onGlassBlurEnabledChange(!glassBlurEnabled) },
                description = text(languageMode, "glass.blur.hint"),
                style = SettingsActionStyle.Toggle,
                checked = glassBlurEnabled
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
                onSliderValueChange = onGlassBlurRadiusChange
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
                onSliderValueChange = { value -> onGlassSurfaceOpacityChange(value / 100f) }
            )
        )
        return buildContent(groupTitle(languageMode, "appearance"), metrics, actions)
    }

    fun sourcesGroup(
        languageMode: String,
        quality: String,
        shareStyle: String,
        gatewayConfigured: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOpenNetworkSources: () -> Unit
    ): SettingsPageStateContent {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)),
            SettingsMetric(text(languageMode, "share.style"), shareStyleLabel(shareStyle, languageMode)),
            SettingsMetric(text(languageMode, "streaming.gateway"), if (gatewayConfigured) text(languageMode, "connected") else text(languageMode, "missing")),
            SettingsMetric(text(languageMode, "description"), groupDescription(languageMode, "sources"))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            SettingsAction(
                label = text(languageMode, "remote.music.sources"),
                onClick = Runnable { onOpenNetworkSources() },
                description = text(languageMode, "remote.music.sources.hint"),
                style = SettingsActionStyle.Navigation
            ),
            navigationAction(
                text(languageMode, "streaming.audio.quality"),
                SettingsPage.StreamingAudioQuality,
                onNavigate,
                text(languageMode, "streaming.audio.quality.hint"),
                streamingQualityLabel(normalizedQuality, languageMode)
            ),
            navigationAction(
                text(languageMode, "share.style"),
                SettingsPage.ShareStyle,
                onNavigate,
                text(languageMode, "share.style.hint"),
                shareStyleLabel(shareStyle, languageMode)
            ),
            navigationAction(
                text(languageMode, "advanced") + " · " + text(languageMode, "streaming.gateway"),
                SettingsPage.StreamingGateway,
                onNavigate,
                text(languageMode, "streaming.gateway.hint"),
                if (gatewayConfigured) text(languageMode, "connected") else text(languageMode, "missing")
            )
        )
        return buildContent(groupTitle(languageMode, "sources"), metrics, actions)
    }

    fun playbackGroup(
        languageMode: String,
        playbackSpeed: Float,
        appVolume: Float,
        concurrentPlaybackEnabled: Boolean,
        audioEffects: AudioEffectSettings,
        nowPlayingGesturesEnabled: Boolean,
        playbackRestoreEnabled: Boolean,
        replayGainEnabled: Boolean,
        remainingMs: Long,
        onNavigate: (SettingsPage) -> Unit,
        onReplayGainEnabledChange: (Boolean) -> Unit = {},
        onNowPlayingGesturesEnabledChange: (Boolean) -> Unit = {},
        onPlaybackRestoreEnabledChange: (Boolean) -> Unit = {},
        onAudioExclusiveEnabledChange: (Boolean) -> Unit = {}
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "playback.speed"), playbackSpeedLabel(playbackSpeed)),
            SettingsMetric(text(languageMode, "app.volume"), appVolumeLabel(appVolume)),
            SettingsMetric(text(languageMode, "audio.effects"), audioEffectsLabel(audioEffects, languageMode)),
            SettingsMetric(text(languageMode, "replay.gain"), enabledLabel(replayGainEnabled, languageMode)),
            SettingsMetric(text(languageMode, "now.playing.gestures"), enabledLabel(nowPlayingGesturesEnabled, languageMode)),
            SettingsMetric(text(languageMode, "playback.restore"), enabledLabel(playbackRestoreEnabled, languageMode)),
            SettingsMetric(text(languageMode, "audio.exclusive"), enabledLabel(!concurrentPlaybackEnabled, languageMode)),
            SettingsMetric(text(languageMode, "sleep.timer"), sleepTimerLabel(remainingMs, languageMode))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            navigationAction(
                text(languageMode, "playback.speed"),
                SettingsPage.PlaybackSpeed,
                onNavigate,
                text(languageMode, "speed.description"),
                playbackSpeedLabel(playbackSpeed)
            ),
            navigationAction(
                text(languageMode, "app.volume"),
                SettingsPage.AppVolume,
                onNavigate,
                text(languageMode, "volume.description"),
                appVolumeLabel(appVolume)
            ),
            navigationAction(
                text(languageMode, "audio.effects"),
                SettingsPage.AudioEffects,
                onNavigate,
                text(languageMode, "audio.effects.hint"),
                audioEffectsLabel(audioEffects, languageMode)
            ),
            SettingsAction(
                label = text(languageMode, "replay.gain"),
                onClick = Runnable { onReplayGainEnabledChange(!replayGainEnabled) },
                description = text(languageMode, "replay.gain.hint"),
                style = SettingsActionStyle.Toggle,
                checked = replayGainEnabled
            ),
            SettingsAction(
                label = text(languageMode, "now.playing.gestures"),
                onClick = Runnable { onNowPlayingGesturesEnabledChange(!nowPlayingGesturesEnabled) },
                description = text(languageMode, "now.playing.gestures.hint"),
                style = SettingsActionStyle.Toggle,
                checked = nowPlayingGesturesEnabled
            ),
            SettingsAction(
                label = text(languageMode, "playback.restore"),
                onClick = Runnable { onPlaybackRestoreEnabledChange(!playbackRestoreEnabled) },
                description = text(languageMode, "playback.restore.hint"),
                style = SettingsActionStyle.Toggle,
                checked = playbackRestoreEnabled
            ),
            SettingsAction(
                label = text(languageMode, "audio.exclusive"),
                onClick = Runnable { onAudioExclusiveEnabledChange(concurrentPlaybackEnabled) },
                description = text(languageMode, "audio.exclusive.hint"),
                style = SettingsActionStyle.Toggle,
                checked = !concurrentPlaybackEnabled
            ),
            navigationAction(
                text(languageMode, "sleep.timer"),
                SettingsPage.SleepTimer,
                onNavigate,
                text(languageMode, "sleep.timer.description"),
                sleepTimerLabel(remainingMs, languageMode)
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Appearance), onNavigate))
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AdvancedTheme), onNavigate))
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Accent), onNavigate))
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.Language), onNavigate))
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.PageBackground), onNavigate))
            backgroundPages.forEach { (page, uri) ->
                addPageBackgroundActions(languageMode, page, uri, onChoosePageBackground, onClearPageBackground)
            }
        }
        return buildContent(text(languageMode, "page.background"), metrics, actions)
    }

    fun audioExclusive(
        languageMode: String,
        enabled: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onToggle: (Boolean) -> Unit
    ): SettingsPageStateContent = booleanLeafPage(
        languageMode = languageMode,
        currentPage = SettingsPage.ConcurrentPlayback,
        titleKey = "audio.exclusive",
        descriptionKey = "audio.exclusive.description",
        enableKey = "enable.audio.exclusive",
        disableKey = "disable.audio.exclusive",
        enabled = enabled,
        onNavigate = onNavigate,
        onToggle = onToggle
    )

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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AudioEffects), onNavigate))
            add(
                SettingsAction(
                    label = text(languageMode, "audio.effects"),
                    onClick = Runnable { onApplyAudioEffects(effects.withEnabled(!effects.enabled)) },
                    description = text(languageMode, "audio.effects.description"),
                    style = SettingsActionStyle.Toggle,
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
            SettingsMetric(text(languageMode, "overlay.permission"), permissionLabel(overlayPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "floating.lyrics.description"))
        )
        val actions = buildList {
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.FloatingLyrics), onNavigate))
            if (!overlayPermissionGranted) {
                add(
                    SettingsAction(
                        label = text(languageMode, "grant.overlay.permission"),
                        onClick = Runnable { onOpenPermission() },
                        description = text(languageMode, "floating.lyrics.description"),
                        style = SettingsActionStyle.Navigation
                    )
                )
            }
            add(
                SettingsAction(
                    label = text(languageMode, "floating.lyrics"),
                    onClick = Runnable { onToggle(!enabled) },
                    description = text(languageMode, "floating.lyrics.description"),
                    style = SettingsActionStyle.Toggle,
                    checked = enabled
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.SleepTimer), onNavigate))
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                add(
                    SettingsAction(
                        label = minutes.toString() + text(languageMode, "min"),
                        onClick = Runnable { onStartTimer(minutes) },
                        style = SettingsActionStyle.Choice,
                        checked = remainingMs > 0L && abs(remainingMs - minutes * 60_000L) < 60_000L
                    )
                )
            }
            if (remainingMs > 0L) {
                add(
                    SettingsAction(
                        label = text(languageMode, "cancel.sleep.timer"),
                        onClick = Runnable { onCancelTimer() },
                        style = SettingsActionStyle.Destructive
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.PlaybackSpeed), onNavigate))
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                add(
                    SettingsAction(
                        label = playbackSpeedLabel(speed),
                        onClick = Runnable { onApplySpeed(speed) },
                        style = SettingsActionStyle.Choice,
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.AppVolume), onNavigate))
            listOf(0.5f, 0.7f, 0.85f, 1.0f).forEach { volume ->
                add(
                    SettingsAction(
                        label = appVolumeLabel(volume),
                        onClick = Runnable { onApplyVolume(volume) },
                        style = SettingsActionStyle.Choice,
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
        onNavigate: (SettingsPage) -> Unit,
        onApplyQuality: (String) -> Unit
    ): SettingsPageStateContent {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.quality.description"), compact = true),
            SettingsMetric(text(languageMode, "quality.platform.mapping"), text(languageMode, "quality.platform.mapping.summary"), compact = true)
        )
        val actions = buildList {
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.StreamingAudioQuality), onNavigate))
            StreamingQualityPreference.options().forEach { option ->
                val normalizedOption = StreamingQualityPreference.normalize(option)
                val audioQuality = StreamingAudioQuality.fromWireName(normalizedOption)
                add(
                    SettingsAction(
                        label = streamingQualityLabel(normalizedOption, languageMode),
                        onClick = Runnable { onApplyQuality(normalizedOption) },
                        description = audioQuality?.let { StreamingQualityPlatformMapping.explanation(it, languageMode) }.orEmpty(),
                        style = SettingsActionStyle.Choice,
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
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.ShareStyle), onNavigate))
            TrackShareStyle.options().forEach { option ->
                val normalizedOption = TrackShareStyle.normalize(option)
                add(
                    SettingsAction(
                        label = shareStyleLabel(normalizedOption, languageMode),
                        onClick = Runnable { onApplyStyle(normalizedOption) },
                        style = SettingsActionStyle.Choice,
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
        onApplyEndpoint: (String) -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "streaming.gateway"), if (configured) text(languageMode, "connected") else text(languageMode, "missing")),
            SettingsMetric(text(languageMode, "endpoint"), endpoint),
            SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.gateway.description"))
        )
        val actions = buildList {
            add(navigationAction(text(languageMode, "back"), SettingsBackStack.parent(SettingsPage.StreamingGateway), onNavigate))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT, "streaming.gateway.emulator", onApplyEndpoint))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT, "streaming.gateway.localhost", onApplyEndpoint))
            add(streamingGatewayOption(languageMode, endpoint, StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT, "disable", onApplyEndpoint))
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
        onNavigate: (SettingsPage) -> Unit,
        onLoadLibrary: () -> Unit,
        onOpenAudioFilePicker: () -> Unit,
        onOpenAudioFolderPicker: () -> Unit,
        hiddenItems: List<HiddenLibraryItemUi> = emptyList(),
        onRestoreHidden: (String) -> Unit = {},
        onRestoreAllHidden: () -> Unit = {}
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "songs"), songCount.toString()),
            SettingsMetric(text(languageMode, "albums"), albumCount.toString()),
            SettingsMetric(text(languageMode, "artists"), artistCount.toString()),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode))
        )
        val actions = buildList {
            add(navigationAction(text(languageMode, "back"), backPage, onNavigate))
            add(SettingsAction(text(languageMode, "scan.library"), Runnable { onLoadLibrary() }))
            add(SettingsAction(text(languageMode, "import.audio.files"), Runnable { onOpenAudioFilePicker() }))
            add(SettingsAction(text(languageMode, "import.audio.folder"), Runnable { onOpenAudioFolderPicker() }))
            if (hiddenItems.isNotEmpty()) {
                add(SettingsAction(
                    text(languageMode, "library.hidden.restore.all") + " (${hiddenItems.size})",
                    Runnable { onRestoreAllHidden() }
                ))
                hiddenItems.forEach { item ->
                    add(SettingsAction(
                        text(languageMode, "library.hidden.restore") + ": " + item.label,
                        Runnable { onRestoreHidden(item.sourceKey) }
                    ))
                }
            }
        }
        return buildContent(text(languageMode, "library"), metrics, actions)
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
            add(navigationAction(text(languageMode, "back"), backPage, onNavigate))
            add(
                SettingsAction(
                    label = text(languageMode, "online.lyrics"),
                    onClick = Runnable { onOnlineLyricsEnabledChange(!onlineLyricsEnabled) },
                    style = SettingsActionStyle.Toggle,
                    checked = onlineLyricsEnabled
                )
            )
            add(SettingsAction(text(languageMode, "reload.lyrics"), Runnable { onReloadLyrics() }))
            add(
                SettingsAction(
                    label = text(languageMode, "status.bar.lyrics"),
                    onClick = Runnable { onStatusBarLyricsEnabledChange(!statusBarLyricsEnabled) },
                    description = text(languageMode, "status.bar.lyrics.description"),
                    style = SettingsActionStyle.Toggle,
                    checked = statusBarLyricsEnabled
                )
            )
            add(
                SettingsAction(
                    label = text(languageMode, "system.media.lyrics.title"),
                    onClick = Runnable { onSystemMediaLyricsTitleEnabledChange(!systemMediaLyricsTitleEnabled) },
                    description = text(languageMode, "system.media.lyrics.title.description"),
                    style = SettingsActionStyle.Toggle,
                    checked = systemMediaLyricsTitleEnabled
                )
            )
            add(
                navigationAction(
                    text(languageMode, "floating.lyrics"),
                    SettingsPage.FloatingLyrics,
                    onNavigate,
                    text(languageMode, "floating.lyrics.description"),
                    enabledLabel(floatingLyricsEnabled, languageMode)
                )
            )
            listOf(-1000L, -500L, 0L, 500L, 1000L).forEach { offset ->
                add(lyricsOffsetAction(languageMode, offsetMs, offset, onApplyLyricsOffset))
            }
        }
        return buildContent(title, metrics, actions)
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
        section = section
    )

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit
    ): SettingsAction = SettingsAction(
        label = label,
        onClick = Runnable { onNavigate(page) },
        style = SettingsActionStyle.Navigation,
        isBack = isBackLabel(label)
    )

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit,
        description: String,
        value: String = ""
    ): SettingsAction = SettingsAction(
        label = label,
        onClick = Runnable { onNavigate(page) },
        description = description,
        value = value,
        style = SettingsActionStyle.Navigation,
        isBack = isBackLabel(label)
    )

    private fun isBackLabel(label: String): Boolean =
        label.startsWith("Back", ignoreCase = true) || label.contains("返回")

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
            navigationAction(text(languageMode, "back"), SettingsBackStack.parent(currentPage), onNavigate),
            SettingsAction(
                label = text(languageMode, titleKey),
                onClick = Runnable { onToggle(!enabled) },
                description = text(languageMode, descriptionKey),
                style = SettingsActionStyle.Toggle,
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
        return if (StreamingGatewaySettingsStore.normalize(currentEndpoint) == StreamingGatewaySettingsStore.normalize(optionEndpoint)) {
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
        checked = StreamingGatewaySettingsStore.normalize(currentEndpoint) ==
            StreamingGatewaySettingsStore.normalize(endpoint)
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
            checked = EchoTheme.normalizeAccent(currentAccent) == EchoTheme.normalizeAccent(accent)
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
                value = backgroundStateLabel(uri, languageMode)
            )
        )
        if (uri.isNotBlank()) {
            add(
                SettingsAction(
                    label = pageBackgroundActionLabel(languageMode, "clear.page.background", pageLabel),
                    onClick = Runnable { onClearPageBackground(page) },
                    style = SettingsActionStyle.Destructive
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
