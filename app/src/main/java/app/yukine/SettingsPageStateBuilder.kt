package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.ui.SettingsAction
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
        themeMode: String,
        accentMode: String,
        languageMode: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        playbackServiceConnected: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onOpenDownloads: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)),
            SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)),
            SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "notification.permission"), permissionLabel(notificationPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected"))
        )
        val actions = listOf(
            groupNavigationAction(languageMode, "appearance", SettingsPage.AppearanceGroup, onNavigate),
            groupNavigationAction(languageMode, "playback", SettingsPage.PlaybackGroup, onNavigate),
            groupNavigationAction(languageMode, "library", SettingsPage.LibraryGroup, onNavigate),
            groupNavigationAction(languageMode, "lyrics", SettingsPage.LyricsGroup, onNavigate),
            groupNavigationAction(languageMode, "sources", SettingsPage.SourcesGroup, onNavigate),
            SettingsAction(text(languageMode, "download.manager"), Runnable { onOpenDownloads() }, text(languageMode, "download.manager.hint")),
            groupNavigationAction(languageMode, "about", SettingsPage.AboutGroup, onNavigate)
        )
        return buildContent(text(languageMode, "tab.settings"), metrics, actions)
    }

    fun aboutGroup(
        languageMode: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        playbackServiceConnected: Boolean,
        onNavigate: (SettingsPage) -> Unit,
        onExportBackup: () -> Unit,
        onImportBackup: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "version"), "0.1.0"),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "notification.permission"), permissionLabel(notificationPermissionGranted, languageMode)),
            SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected"))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), SettingsPage.Home, onNavigate),
            SettingsAction(text(languageMode, "backup.export"), Runnable { onExportBackup() }),
            SettingsAction(text(languageMode, "backup.import"), Runnable { onImportBackup() })
        )
        return buildContent(groupTitle(languageMode, "about"), metrics, actions)
    }

    fun appearanceGroup(
        languageMode: String,
        themeMode: String,
        accentMode: String,
        pageBackgrounds: PageBackgrounds,
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
            navigationAction(text(languageMode, "appearance"), SettingsPage.Appearance, onNavigate),
            navigationAction(text(languageMode, "accent"), SettingsPage.Accent, onNavigate),
            navigationAction(text(languageMode, "language"), SettingsPage.Language, onNavigate),
            navigationAction(
                text(languageMode, "page.background"),
                SettingsPage.PageBackground,
                onNavigate,
                text(languageMode, "page.background.hint")
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
            SettingsAction(text(languageMode, "remote.music.sources"), Runnable { onOpenNetworkSources() }, text(languageMode, "remote.music.sources.hint")),
            navigationAction(text(languageMode, "streaming.audio.quality"), SettingsPage.StreamingAudioQuality, onNavigate, text(languageMode, "streaming.audio.quality.hint")),
            navigationAction(text(languageMode, "share.style"), SettingsPage.ShareStyle, onNavigate, text(languageMode, "share.style.hint")),
            navigationAction(
                text(languageMode, "advanced") + " · " + text(languageMode, "streaming.gateway"),
                SettingsPage.StreamingGateway,
                onNavigate,
                text(languageMode, "streaming.gateway.hint")
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
        onNavigate: (SettingsPage) -> Unit
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
            navigationAction(text(languageMode, "playback.speed"), SettingsPage.PlaybackSpeed, onNavigate),
            navigationAction(text(languageMode, "app.volume"), SettingsPage.AppVolume, onNavigate),
            navigationAction(text(languageMode, "audio.effects"), SettingsPage.AudioEffects, onNavigate, text(languageMode, "audio.effects.hint")),
            navigationAction(text(languageMode, "replay.gain"), SettingsPage.ReplayGain, onNavigate, text(languageMode, "replay.gain.hint")),
            navigationAction(text(languageMode, "now.playing.gestures"), SettingsPage.NowPlayingGestures, onNavigate, text(languageMode, "now.playing.gestures.hint")),
            navigationAction(text(languageMode, "playback.restore"), SettingsPage.PlaybackRestore, onNavigate, text(languageMode, "playback.restore.hint")),
            navigationAction(text(languageMode, "audio.exclusive"), SettingsPage.ConcurrentPlayback, onNavigate, text(languageMode, "audio.exclusive.hint")),
            navigationAction(text(languageMode, "sleep.timer"), SettingsPage.SleepTimer, onNavigate)
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
                    if (effects.enabled) text(languageMode, "disable.audio.effects") else text(languageMode, "enable.audio.effects"),
                    Runnable { onApplyAudioEffects(effects.withEnabled(!effects.enabled)) }
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
                add(SettingsAction(text(languageMode, "grant.overlay.permission"), Runnable { onOpenPermission() }))
            }
            add(
                SettingsAction(
                    if (enabled) text(languageMode, "disable.floating.lyrics") else text(languageMode, "enable.floating.lyrics"),
                    Runnable { onToggle(!enabled) }
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
                add(SettingsAction(minutes.toString() + text(languageMode, "min"), Runnable { onStartTimer(minutes) }))
            }
            add(SettingsAction(text(languageMode, "cancel.sleep.timer"), Runnable { onCancelTimer() }))
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
                add(SettingsAction(selectedFloatLabel(playbackSpeedLabel(speed), normalizePlaybackSpeed(playbackSpeed), normalizePlaybackSpeed(speed), languageMode), Runnable { onApplySpeed(speed) }))
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
                add(SettingsAction(selectedFloatLabel(appVolumeLabel(volume), normalizeAppVolume(appVolume), normalizeAppVolume(volume), languageMode), Runnable { onApplyVolume(volume) }))
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
                        selectedStringLabel(streamingQualityLabel(normalizedOption, languageMode), normalizedQuality, normalizedOption, languageMode),
                        Runnable { onApplyQuality(normalizedOption) },
                        audioQuality?.let { StreamingQualityPlatformMapping.explanation(it, languageMode) }.orEmpty()
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
                        selectedStyleLabel(shareStyleLabel(normalizedOption, languageMode), normalizedStyle, normalizedOption, languageMode),
                        Runnable { onApplyStyle(normalizedOption) }
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
        onOpenAudioFolderPicker: () -> Unit
    ): SettingsPageStateContent {
        val metrics = listOf(
            SettingsMetric(text(languageMode, "songs"), songCount.toString()),
            SettingsMetric(text(languageMode, "albums"), albumCount.toString()),
            SettingsMetric(text(languageMode, "artists"), artistCount.toString()),
            SettingsMetric(text(languageMode, "audio.permission"), permissionLabel(audioPermissionGranted, languageMode))
        )
        val actions = listOf(
            navigationAction(text(languageMode, "back"), backPage, onNavigate),
            SettingsAction(text(languageMode, "scan.library"), Runnable { onLoadLibrary() }),
            SettingsAction(text(languageMode, "import.audio.files"), Runnable { onOpenAudioFilePicker() }),
            SettingsAction(text(languageMode, "import.audio.folder"), Runnable { onOpenAudioFolderPicker() })
        )
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
                    if (onlineLyricsEnabled) text(languageMode, "disable.online.lyrics") else text(languageMode, "enable.online.lyrics"),
                    Runnable { onOnlineLyricsEnabledChange(!onlineLyricsEnabled) }
                )
            )
            add(SettingsAction(text(languageMode, "reload.lyrics"), Runnable { onReloadLyrics() }))
            add(navigationAction(text(languageMode, "status.bar.lyrics"), SettingsPage.StatusBarLyrics, onNavigate))
            add(
                SettingsAction(
                    if (systemMediaLyricsTitleEnabled) {
                        text(languageMode, "disable.system.media.lyrics.title")
                    } else {
                        text(languageMode, "enable.system.media.lyrics.title")
                    },
                    Runnable { onSystemMediaLyricsTitleEnabledChange(!systemMediaLyricsTitleEnabled) },
                    text(languageMode, "system.media.lyrics.title.description")
                )
            )
            add(navigationAction(text(languageMode, "floating.lyrics"), SettingsPage.FloatingLyrics, onNavigate))
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
        onNavigate: (SettingsPage) -> Unit
    ): SettingsAction = SettingsAction(
        groupTitle(languageMode, key),
        Runnable { onNavigate(page) },
        groupDescription(languageMode, key)
    )

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit
    ): SettingsAction = SettingsAction(label, Runnable { onNavigate(page) })

    private fun navigationAction(
        label: String,
        page: SettingsPage,
        onNavigate: (SettingsPage) -> Unit,
        description: String
    ): SettingsAction = SettingsAction(label, Runnable { onNavigate(page) }, description)

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
                if (enabled) text(languageMode, disableKey) else text(languageMode, enableKey),
                Runnable { onToggle(!enabled) }
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

    private fun selectedFloatLabel(label: String, currentValue: Float, optionValue: Float, languageMode: String): String {
        return if (abs(currentValue - optionValue) < 0.01f) {
            label + text(languageMode, "selected")
        } else {
            label
        }
    }

    private fun selectedStringLabel(label: String, currentValue: String, optionValue: String, languageMode: String): String {
        return if (StreamingQualityPreference.normalize(currentValue) == StreamingQualityPreference.normalize(optionValue)) {
            label + text(languageMode, "selected")
        } else {
            label
        }
    }

    private fun selectedStyleLabel(label: String, currentValue: String, optionValue: String, languageMode: String): String {
        return if (TrackShareStyle.normalize(currentValue) == TrackShareStyle.normalize(optionValue)) {
            label + text(languageMode, "selected")
        } else {
            label
        }
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
        selectedEndpointLabel(text(languageMode, labelKey), currentEndpoint, endpoint, languageMode),
        Runnable { onApplyEndpoint(endpoint) }
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
        return SettingsAction(label, Runnable { onApplyTheme(mode) })
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
        return SettingsAction(label, Runnable { onApplyAccent(accent) })
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
        return SettingsAction(label, Runnable { onApplyLanguage(optionMode) })
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
                text(languageMode, "choose.page.background") + " 路 " + pageLabel,
                Runnable { onChoosePageBackground(page) },
                backgroundActionDescription(page, languageMode)
            )
        )
        if (uri.isNotBlank()) {
            add(
                SettingsAction(
                    text(languageMode, "clear.page.background") + " 路 " + pageLabel,
                    Runnable { onClearPageBackground(page) }
                )
            )
        }
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
        return SettingsAction(label, Runnable { onApplyAudioEffects(settings.withEnabled(true).withPreset(preset)) })
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
        return SettingsAction(label, Runnable {
            val next = if (target == "bass") {
                settings.withEnabled(true).withBassBoostStrength(strength.toShort())
            } else {
                settings.withEnabled(true).withVirtualizerStrength(strength.toShort())
            }
            onApplyAudioEffects(next)
        })
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
        return SettingsAction(label, Runnable { onApplyAudioEffects(settings.withEnabled(true).withLoudnessGainMb(gainMb)) })
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
        return SettingsAction(label, Runnable { onApplyLyricsOffset(offsetMs) })
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
