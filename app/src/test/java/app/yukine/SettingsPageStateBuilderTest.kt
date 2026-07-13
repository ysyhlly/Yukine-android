package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.ui.EchoTheme
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsActionStyle
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageStateBuilderTest {
    @Test
    fun buildMapsActionsBeforeMetrics() {
        val metrics = listOf(SettingsMetric("Theme", "Dark"))
        val actions = listOf(
            SettingsAction("Appearance", Runnable { }, "Theme and accent"),
            SettingsAction("Reload", Runnable { })
        )

        val state = SettingsPageStateBuilder.build("Settings", metrics, actions)

        assertEquals("Settings", state.title)
        assertEquals(metrics, state.metrics)
        assertEquals(3, state.items.size)
        assertTrue(state.items[0] is SettingsItem.Navigation)
        assertTrue(state.items[1] is SettingsItem.Action)
        assertEquals(SettingsItem.Metric("Theme", "Dark"), state.items[2])
    }

    @Test
    fun homeBuildsSettingsOverviewAndRoutesActions() {
        val navigated = mutableListOf<SettingsPage>()
        var downloadsOpened = false

        val content = SettingsPageStateBuilder.home(
            languageMode = AppLanguage.MODE_ENGLISH,
            audioPermissionGranted = true,
            notificationPermissionGranted = false,
            playbackServiceConnected = true,
            onNavigate = { page -> navigated += page },
            onRequestNeededPermissions = {},
            onOpenDownloads = { downloadsOpened = true }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "tab.settings"), content.uiState.title)
        assertEquals(4, content.uiState.metrics.size)
        assertEquals(7, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.start"), content.uiState.metrics[0].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.section.start"), content.actions[0].section)
        assertEquals(SettingsActionStyle.Navigation, content.actions[0].style)

        content.actions[0].onClick.run()
        content.actions[5].onClick.run()

        assertEquals(listOf(SettingsPage.LibraryGroup), navigated)
        assertEquals(true, downloadsOpened)
    }

    @Test
    fun homePrioritizesMusicPermissionWhenItIsMissing() {
        var permissionRequested = false

        val content = SettingsPageStateBuilder.home(
            languageMode = AppLanguage.MODE_ENGLISH,
            audioPermissionGranted = false,
            notificationPermissionGranted = false,
            playbackServiceConnected = false,
            onNavigate = {},
            onRequestNeededPermissions = { permissionRequested = true },
            onOpenDownloads = {}
        )

        assertEquals(8, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.grant.music.access"), content.actions[0].label)
        assertEquals(SettingsActionStyle.Navigation, content.actions[0].style)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.section.start"), content.actions[0].section)

        content.actions[0].onClick.run()

        assertEquals(true, permissionRequested)
    }

    @Test
    fun aboutGroupBuildsBackupActions() {
        val navigated = mutableListOf<SettingsPage>()
        var exported = false
        var imported = false
        var debugPromptsEnabled = false

        val content = SettingsPageStateBuilder.aboutGroup(
            languageMode = AppLanguage.MODE_ENGLISH,
            audioPermissionGranted = true,
            notificationPermissionGranted = true,
            playbackServiceConnected = false,
            debugPromptsEnabled = false,
            onNavigate = { page -> navigated += page },
            onExportBackup = { exported = true },
            onImportBackup = { imported = true },
            onDebugPromptsEnabledChange = { debugPromptsEnabled = it }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.group.about"), content.uiState.title)
        assertEquals(4, content.uiState.metrics.size)
        assertEquals(5, content.actions.size)
        assertTrue(content.actions[0].isBack)
        assertEquals("1013122077", content.actions[1].value)
        assertEquals(R.drawable.qq_group_qr, content.actions[1].imageDialog?.imageResId)
        assertEquals(SettingsActionStyle.Toggle, content.actions[2].style)
        assertEquals(false, content.actions[2].checked)
        assertEquals(SettingsActionStyle.Destructive, content.actions[4].style)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "backup.import.description"), content.actions[4].description)

        content.actions[0].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()
        content.actions[4].onClick.run()

        assertEquals(listOf(SettingsPage.Home), navigated)
        assertEquals(true, exported)
        assertEquals(true, imported)
        assertEquals(true, debugPromptsEnabled)
    }

    @Test
    fun appearanceGroupBuildsNavigationActions() {
        val navigated = mutableListOf<SettingsPage>()
        var blurEnabled = true
        var blurRadius = 0f
        var backgroundBlurEnabled = true
        var backgroundBlurRadius = 0f
        val backgrounds = PageBackgrounds(
            sharedUri = "",
            homeUri = "content://home",
            libraryUri = "",
            playerUri = "content://player",
            settingsUri = ""
        )

        val content = SettingsPageStateBuilder.appearanceGroup(
            languageMode = AppLanguage.MODE_ENGLISH,
            themeMode = "dark",
            accentMode = "green",
            pageBackgrounds = backgrounds,
            customBackgroundBlurEnabled = true,
            customBackgroundBlurRadiusDp = 24f,
            glassBlurEnabled = true,
            glassBlurRadiusDp = 24f,
            glassSurfaceOpacity = 0.62f,
            onCustomBackgroundBlurEnabledChange = { backgroundBlurEnabled = it },
            onCustomBackgroundBlurRadiusChange = { backgroundBlurRadius = it },
            onGlassBlurEnabledChange = { blurEnabled = it },
            onGlassBlurRadiusChange = { blurRadius = it },
            onGlassSurfaceOpacityChange = { },
            onNavigate = { page -> navigated += page }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.group.appearance"), content.uiState.title)
        assertEquals(5, content.uiState.metrics.size)
        assertEquals("2" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "page.background.custom.count"), content.uiState.metrics[3].value)
        assertEquals(10, content.actions.size)

        content.actions[0].onClick.run()
        content.actions[4].onClick.run()
        content.actions[5].onClick.run()
        content.actions[6].onSliderValueChange?.invoke(36f)
        content.actions[7].onClick.run()
        content.actions[8].onSliderValueChange?.invoke(28f)

        assertEquals(listOf(SettingsPage.Home, SettingsPage.PageBackground), navigated)
        assertEquals(false, backgroundBlurEnabled)
        assertEquals(36f, backgroundBlurRadius)
        assertEquals(false, blurEnabled)
        assertEquals(28f, blurRadius)
    }

    @Test
    fun themeBuildsPrimaryThemeActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.theme(
            languageMode = AppLanguage.MODE_ENGLISH,
            themeMode = EchoTheme.MODE_DARK,
            onNavigate = { page -> navigated += page },
            onApplyTheme = { mode -> applied += mode }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "appearance"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(AppLanguage.themeLabel(EchoTheme.MODE_DARK, AppLanguage.MODE_ENGLISH), content.uiState.metrics[0].value)
        assertTrue(content.actions.any { action -> action.label == AppLanguage.themeLabel(EchoTheme.MODE_DARK, AppLanguage.MODE_ENGLISH) + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected") })

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions.last().onClick.run()

        assertEquals(SettingsPage.AppearanceGroup, navigated.first())
        assertEquals(EchoTheme.primaryModeOptions()[0], applied.first())
        assertEquals(SettingsPage.AdvancedTheme, navigated.last())
    }

    @Test
    fun advancedThemeBuildsAdvancedThemeActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.advancedTheme(
            languageMode = AppLanguage.MODE_ENGLISH,
            themeMode = EchoTheme.MODE_CONTRAST,
            onNavigate = { page -> navigated += page },
            onApplyTheme = { mode -> applied += mode }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "advanced.themes"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(EchoTheme.advancedModeOptions().size + 1, content.actions.size)
        assertTrue(content.actions.any { action -> action.label == AppLanguage.themeLabel(EchoTheme.MODE_CONTRAST, AppLanguage.MODE_ENGLISH) + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected") })

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.Appearance), navigated)
        assertEquals(listOf(EchoTheme.advancedModeOptions()[0]), applied)
    }

    @Test
    fun accentBuildsAccentActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.accent(
            languageMode = AppLanguage.MODE_ENGLISH,
            accentMode = EchoTheme.ACCENT_ROSE,
            pageBackgrounds = PageBackgrounds(sharedUri = "content://background/all"),
            onNavigate = { page -> navigated += page },
            onApplyAccent = { accent -> applied += accent }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "accent"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(AppLanguage.accentLabel(EchoTheme.ACCENT_ROSE, AppLanguage.MODE_ENGLISH), content.uiState.metrics[0].value)
        val fixedAccentOffset = if (EchoTheme.dynamicColorAvailable()) 3 else 2
        assertEquals(13 + fixedAccentOffset - 1, content.actions.size)
        assertEquals(AppLanguage.accentLabel(EchoTheme.ACCENT_ROSE, AppLanguage.MODE_ENGLISH) + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[fixedAccentOffset + 2].label)

        content.actions[0].onClick.run()
        content.actions[fixedAccentOffset].onClick.run()
        content.actions[fixedAccentOffset + 2].onClick.run()

        assertEquals(listOf(SettingsPage.AppearanceGroup), navigated)
        assertEquals(listOf(EchoTheme.ACCENT_BLUE, EchoTheme.ACCENT_ROSE), applied)
    }

    @Test
    fun languageBuildsLanguageActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.language(
            languageMode = AppLanguage.MODE_ENGLISH,
            onNavigate = { page -> navigated += page },
            onApplyLanguage = { mode -> applied += mode }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "language"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(AppLanguage.labelFor(AppLanguage.MODE_ENGLISH), content.uiState.metrics[0].value)
        assertEquals(4, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "language.english") + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[3].label)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[3].onClick.run()

        assertEquals(listOf(SettingsPage.AppearanceGroup), navigated)
        assertEquals(listOf(AppLanguage.MODE_SYSTEM, AppLanguage.MODE_ENGLISH), applied)
    }

    @Test
    fun pageBackgroundsBuildsChooseAndClearActions() {
        val navigated = mutableListOf<SettingsPage>()
        val chosen = mutableListOf<String>()
        val cleared = mutableListOf<String>()
        val backgrounds = PageBackgrounds(
            sharedUri = "content://all",
            homeUri = "",
            libraryUri = "content://library",
            playerUri = "",
            settingsUri = "content://settings"
        )

        val content = SettingsPageStateBuilder.pageBackgrounds(
            languageMode = AppLanguage.MODE_ENGLISH,
            pageBackgrounds = backgrounds,
            onNavigate = { page -> navigated += page },
            onChoosePageBackground = { page -> chosen += page },
            onClearPageBackground = { page -> cleared += page }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "page.background"), content.uiState.title)
        assertEquals(6, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled"), content.uiState.metrics[0].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "off"), content.uiState.metrics[1].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled"), content.uiState.metrics[2].value)
        assertEquals(9, content.actions.size)
        assertEquals("Choose background: All pages", content.actions[1].label)
        assertEquals("Clear background: All pages", content.actions[2].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "page.background.all.description"), content.actions[1].description)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "page.background.single.description"), content.actions[3].description)

        val chineseContent = SettingsPageStateBuilder.pageBackgrounds(
            languageMode = AppLanguage.MODE_CHINESE,
            pageBackgrounds = backgrounds,
            onNavigate = { },
            onChoosePageBackground = { },
            onClearPageBackground = { }
        )
        assertEquals("选择背景：全部页面", chineseContent.actions[1].label)
        assertEquals("清除背景：全部页面", chineseContent.actions[2].label)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()
        content.actions[4].onClick.run()
        content.actions[8].onClick.run()

        assertEquals(listOf(SettingsPage.AppearanceGroup), navigated)
        assertEquals(listOf(PageBackgrounds.PAGE_ALL, PageBackgrounds.PAGE_HOME, PageBackgrounds.PAGE_LIBRARY), chosen)
        assertEquals(listOf(PageBackgrounds.PAGE_ALL, PageBackgrounds.PAGE_SETTINGS), cleared)
    }

    @Test
    fun sourcesGroupBuildsNetworkAndProviderActions() {
        val navigated = mutableListOf<SettingsPage>()
        val openedNetworkPages = mutableListOf<String>()
        val lxActions = mutableListOf<String>()

        val content = SettingsPageStateBuilder.sourcesGroup(
            languageMode = AppLanguage.MODE_ENGLISH,
            quality = StreamingQualityPreference.LOSSLESS,
            shareStyle = TrackShareStyle.CARD,
            gatewayConfigured = false,
            luoxueImportedSourceCount = 4,
            luoxueEnabledSourceCount = 3,
            onNavigate = { page -> navigated += page },
            onOpenNetworkPage = { page -> openedNetworkPages += page },
            onManageLuoxueSources = { lxActions += "manage" },
            onImportLuoxueSource = { lxActions += "import" }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.settings"), content.uiState.title)
        assertEquals(3, content.uiState.metrics.size)
        assertEquals("3 of 4 enabled", content.uiState.metrics[0].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "quality.lossless"), content.uiState.metrics[1].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "missing"), content.uiState.metrics[2].value)
        assertEquals(9, content.actions.size)

        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()
        content.actions[5].onClick.run()
        content.actions[6].onClick.run()
        content.actions[7].onClick.run()
        content.actions[8].onClick.run()

        assertEquals(
            listOf(
                MainRoutes.NETWORK_STREAMING,
                MainRoutes.NETWORK_SOURCES,
                MainRoutes.NETWORK_WEBDAV
            ),
            openedNetworkPages
        )
        assertEquals(listOf("manage", "import"), lxActions)
        assertEquals(listOf(SettingsPage.StreamingGateway, SettingsPage.ShareStyle), navigated)
    }

    @Test
    fun playbackGroupKeepsAdjustmentsAsNavigationAndExposesCommonTogglesInline() {
        val navigated = mutableListOf<SettingsPage>()
        val toggled = mutableListOf<String>()
        val audioEffects = AudioEffectSettings.DEFAULT
            .withEnabled(true)
            .withPreset(1)

        val content = SettingsPageStateBuilder.playbackGroup(
            languageMode = AppLanguage.MODE_ENGLISH,
            playbackSpeed = 1.25f,
            appVolume = 0.7f,
            concurrentPlaybackEnabled = false,
            audioEffects = audioEffects,
            nowPlayingGesturesEnabled = false,
            playbackRestoreEnabled = true,
            replayGainEnabled = false,
            remainingMs = 61000L,
            onNavigate = { page -> navigated += page },
            onReplayGainEnabledChange = { enabled -> toggled += "replay:$enabled" },
            onNowPlayingGesturesEnabledChange = { enabled -> toggled += "gestures:$enabled" },
            onPlaybackRestoreEnabledChange = { enabled -> toggled += "restore:$enabled" },
            onAudioExclusiveEnabledChange = { enabled -> toggled += "exclusive:$enabled" }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.group.playback"), content.uiState.title)
        assertEquals(8, content.uiState.metrics.size)
        assertEquals("1.25x", content.uiState.metrics[0].value)
        assertEquals("70%", content.uiState.metrics[1].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "audio.exclusive"), content.uiState.metrics[6].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled"), content.uiState.metrics[6].value)
        assertEquals(
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled") + " / " + AppLanguage.text(AppLanguage.MODE_ENGLISH, "eq.classical"),
            content.uiState.metrics[2].value
        )
        assertEquals("2" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "min.left"), content.uiState.metrics[7].value)
        assertEquals(9, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "audio.effects.hint"), content.actions[3].description)
        assertEquals("1.25x", content.actions[1].value)
        assertEquals(SettingsActionStyle.Toggle, content.actions[4].style)
        assertEquals(false, content.actions[4].checked)
        assertEquals(SettingsActionStyle.Toggle, content.actions[7].style)
        assertEquals(true, content.actions[7].checked)

        content.actions.forEach { action -> action.onClick.run() }

        assertEquals(
            listOf(
                SettingsPage.Home,
                SettingsPage.PlaybackSpeed,
                SettingsPage.AppVolume,
                SettingsPage.AudioEffects,
                SettingsPage.SleepTimer
            ),
            navigated
        )
        assertEquals(
            listOf("replay:true", "gestures:true", "restore:false", "exclusive:false"),
            toggled
        )
    }

    @Test
    fun audioEffectsBuildsEffectActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<AudioEffectSettings>()
        val settings = AudioEffectSettings.DEFAULT
            .withEnabled(true)
            .withPreset(1)
            .withBassBoostStrength(500)
            .withVirtualizerStrength(1000)
            .withLoudnessGainMb(300)

        val content = SettingsPageStateBuilder.audioEffects(
            languageMode = AppLanguage.MODE_ENGLISH,
            settings = settings,
            onNavigate = { page -> navigated += page },
            onApplyAudioEffects = { next -> applied += next }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "audio.effects"), content.uiState.title)
        assertEquals(6, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled") + " / " + AppLanguage.text(AppLanguage.MODE_ENGLISH, "eq.classical"), content.uiState.metrics[0].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "eq.classical"), content.uiState.metrics[1].value)
        assertEquals("50%", content.uiState.metrics[2].value)
        assertEquals("100%", content.uiState.metrics[3].value)
        assertEquals("+3.0 dB", content.uiState.metrics[4].value)
        assertEquals(15, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "eq.classical") + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[4].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "bass.boost") + " 50%" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[7].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "virtualizer") + " 100%" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[11].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "loudness") + " +3.0 dB" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[13].label)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[4].onClick.run()
        content.actions[8].onClick.run()
        content.actions[11].onClick.run()
        content.actions[14].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup), navigated)
        assertEquals(false, applied[0].enabled)
        assertEquals(true, applied[1].enabled)
        assertEquals(1, applied[1].preset)
        assertEquals(1000, applied[2].bassBoostStrength.toInt())
        assertEquals(1000, applied[3].virtualizerStrength.toInt())
        assertEquals(600, applied[4].loudnessGainMb)
    }

    @Test
    fun audioExclusiveBuildsBooleanLeafPage() {
        val navigated = mutableListOf<SettingsPage>()
        val toggles = mutableListOf<Boolean>()

        val content = SettingsPageStateBuilder.audioExclusive(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = true,
            onNavigate = { page -> navigated += page },
            onToggle = { enabled -> toggles += enabled }
        )

        assertBooleanLeafPage(
            content = content,
            titleKey = "audio.exclusive",
            enabled = true
        )

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup), navigated)
        assertEquals(listOf(false), toggles)
    }

    @Test
    fun playbackBooleanLeafPagesBuildToggleActions() {
        val navigated = mutableListOf<SettingsPage>()
        val toggles = mutableListOf<String>()

        val nowPlaying = SettingsPageStateBuilder.nowPlayingGestures(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = false,
            onNavigate = { page -> navigated += page },
            onToggle = { enabled -> toggles += "gestures:$enabled" }
        )
        val restore = SettingsPageStateBuilder.playbackRestore(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = false,
            onNavigate = { page -> navigated += page },
            onToggle = { enabled -> toggles += "restore:$enabled" }
        )
        val replayGain = SettingsPageStateBuilder.replayGain(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = true,
            onNavigate = { page -> navigated += page },
            onToggle = { enabled -> toggles += "replay:$enabled" }
        )

        assertBooleanLeafPage(nowPlaying, "now.playing.gestures", false)
        assertBooleanLeafPage(restore, "playback.restore", false)
        assertBooleanLeafPage(replayGain, "replay.gain", true)

        nowPlaying.actions[0].onClick.run()
        restore.actions[0].onClick.run()
        replayGain.actions[0].onClick.run()
        nowPlaying.actions[1].onClick.run()
        restore.actions[1].onClick.run()
        replayGain.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup, SettingsPage.PlaybackGroup, SettingsPage.PlaybackGroup), navigated)
        assertEquals(listOf("gestures:true", "restore:true", "replay:false"), toggles)
    }

    @Test
    fun statusBarLyricsBuildsLyricsBooleanLeafPage() {
        val navigated = mutableListOf<SettingsPage>()
        val toggles = mutableListOf<Boolean>()

        val content = SettingsPageStateBuilder.statusBarLyrics(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = false,
            onNavigate = { page -> navigated += page },
            onToggle = { enabled -> toggles += enabled }
        )

        assertBooleanLeafPage(content, "status.bar.lyrics", false)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.LyricsGroup), navigated)
        assertEquals(listOf(true), toggles)
    }

    @Test
    fun floatingLyricsAddsPermissionActionWhenMissing() {
        val navigated = mutableListOf<SettingsPage>()
        var permissionOpened = false
        val toggles = mutableListOf<Boolean>()

        val content = SettingsPageStateBuilder.floatingLyrics(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = false,
            overlayPermissionGranted = false,
            onNavigate = { page -> navigated += page },
            onOpenPermission = { permissionOpened = true },
            onToggle = { enabled -> toggles += enabled }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics"), content.uiState.title)
        assertEquals(3, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "missing"), content.uiState.metrics[1].value)
        assertEquals(3, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "grant.overlay.permission"), content.actions[1].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics"), content.actions[2].label)
        assertEquals(SettingsActionStyle.Navigation, content.actions[1].style)
        assertEquals(SettingsActionStyle.Toggle, content.actions[2].style)
        assertEquals(false, content.actions[2].checked)
        assertEquals(false, content.actions[2].enabled)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.LyricsGroup), navigated)
        assertEquals(true, permissionOpened)
        assertEquals(emptyList<Boolean>(), toggles)
    }

    @Test
    fun floatingLyricsOmitsPermissionActionWhenGranted() {
        val navigated = mutableListOf<SettingsPage>()
        var permissionOpened = false
        val toggles = mutableListOf<Boolean>()

        val content = SettingsPageStateBuilder.floatingLyrics(
            languageMode = AppLanguage.MODE_ENGLISH,
            enabled = true,
            overlayPermissionGranted = true,
            onNavigate = { page -> navigated += page },
            onOpenPermission = { permissionOpened = true },
            onToggle = { enabled -> toggles += enabled }
        )

        assertEquals(3, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "granted"), content.uiState.metrics[1].value)
        assertEquals(2, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "floating.lyrics"), content.actions[1].label)
        assertEquals(SettingsActionStyle.Toggle, content.actions[1].style)
        assertEquals(true, content.actions[1].checked)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()

        assertEquals(listOf(SettingsPage.LyricsGroup), navigated)
        assertEquals(false, permissionOpened)
        assertEquals(listOf(false), toggles)
    }

    @Test
    fun sleepTimerBuildsPresetAndCancelActions() {
        val navigated = mutableListOf<SettingsPage>()
        val started = mutableListOf<Int>()
        var canceled = false

        val content = SettingsPageStateBuilder.sleepTimer(
            languageMode = AppLanguage.MODE_ENGLISH,
            remainingMs = 61_000L,
            onNavigate = { page -> navigated += page },
            onStartTimer = { minutes -> started += minutes },
            onCancelTimer = { canceled = true }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "sleep.timer"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals("2" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "min.left"), content.uiState.metrics[0].value)
        assertEquals(7, content.actions.size)
        assertEquals("15" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "min"), content.actions[1].label)
        assertEquals("90" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "min"), content.actions[5].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "cancel.sleep.timer"), content.actions[6].label)
        assertEquals(SettingsActionStyle.Choice, content.actions[1].style)
        assertEquals(SettingsActionStyle.Destructive, content.actions[6].style)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[5].onClick.run()
        content.actions[6].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup), navigated)
        assertEquals(listOf(15, 90), started)
        assertEquals(true, canceled)
    }

    @Test
    fun sleepTimerShowsOffWhenInactive() {
        val content = SettingsPageStateBuilder.sleepTimer(
            languageMode = AppLanguage.MODE_ENGLISH,
            remainingMs = 0L,
            onNavigate = {},
            onStartTimer = {},
            onCancelTimer = {}
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "off"), content.uiState.metrics[0].value)
        assertEquals(6, content.actions.size)
    }

    @Test
    fun playbackSpeedBuildsNormalizedPresetActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<Float>()

        val content = SettingsPageStateBuilder.playbackSpeed(
            languageMode = AppLanguage.MODE_ENGLISH,
            playbackSpeed = 1.249f,
            onNavigate = { page -> navigated += page },
            onApplySpeed = { speed -> applied += speed }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "playback.speed"), content.uiState.title)
        assertEquals("1.25x", content.uiState.metrics[0].value)
        assertEquals(7, content.actions.size)
        assertEquals("1.25x", content.actions[4].label)
        assertEquals(SettingsActionStyle.Choice, content.actions[4].style)
        assertEquals(true, content.actions[4].checked)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[4].onClick.run()
        content.actions[6].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup), navigated)
        assertEquals(listOf(0.5f, 1.25f, 2.0f), applied)
    }

    @Test
    fun appVolumeBuildsNormalizedPresetActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<Float>()

        val content = SettingsPageStateBuilder.appVolume(
            languageMode = AppLanguage.MODE_ENGLISH,
            appVolume = 0.851f,
            onNavigate = { page -> navigated += page },
            onApplyVolume = { volume -> applied += volume }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "app.volume"), content.uiState.title)
        assertEquals("85%", content.uiState.metrics[0].value)
        assertEquals(5, content.actions.size)
        assertEquals("85%", content.actions[3].label)
        assertEquals(SettingsActionStyle.Choice, content.actions[3].style)
        assertEquals(true, content.actions[3].checked)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[3].onClick.run()
        content.actions[4].onClick.run()

        assertEquals(listOf(SettingsPage.PlaybackGroup), navigated)
        assertEquals(listOf(0.5f, 0.85f, 1.0f), applied)
    }

    @Test
    fun streamingAudioQualityBuildsPlatformMappingActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()
        val downgradePreferences = mutableListOf<Boolean>()

        val content = SettingsPageStateBuilder.streamingAudioQuality(
            languageMode = AppLanguage.MODE_ENGLISH,
            quality = StreamingQualityPreference.LOSSLESS,
            refuseAutomaticQualityDowngrade = false,
            onNavigate = { page -> navigated += page },
            onApplyQuality = { quality -> applied += quality },
            onRefuseAutomaticQualityDowngradeChange = { refuse -> downgradePreferences += refuse }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.audio.quality"), content.uiState.title)
        assertEquals(3, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "quality.lossless"), content.uiState.metrics[0].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "quality.platform.mapping.summary"), content.uiState.metrics[2].value)
        assertTrue(content.uiState.metrics[1].compact)
        assertTrue(content.uiState.metrics[2].compact)
        assertEquals(7, content.actions.size)
        assertEquals(SettingsActionStyle.Toggle, content.actions[1].style)
        assertEquals(false, content.actions[1].checked)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "quality.lossless"), content.actions[5].label)
        assertEquals(
            StreamingQualityPlatformMapping.explanation(app.yukine.streaming.StreamingAudioQuality.LOSSLESS, AppLanguage.MODE_ENGLISH),
            content.actions[5].description
        )
        assertEquals(SettingsActionStyle.Choice, content.actions[5].style)
        assertEquals(true, content.actions[5].checked)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[5].onClick.run()
        assertEquals(listOf(true), downgradePreferences)
        content.actions[6].onClick.run()

        assertEquals(listOf(SettingsPage.SourcesGroup), navigated)
        assertEquals(
            listOf(
                StreamingQualityPreference.AUTO,
                StreamingQualityPreference.LOSSLESS,
                StreamingQualityPreference.HIRES
            ),
            applied
        )
    }

    @Test
    fun shareStyleBuildsNormalizedOptionActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.shareStyle(
            languageMode = AppLanguage.MODE_ENGLISH,
            shareStyle = "music_card",
            onNavigate = { page -> navigated += page },
            onApplyStyle = { style -> applied += style }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "share.style"), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "share.style.platform.card"), content.uiState.metrics[0].value)
        assertEquals(4, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "share.style.platform.card"), content.actions[1].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "share.style.text"), content.actions[2].label)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "share.style.card"), content.actions[3].label)
        assertEquals(SettingsActionStyle.Choice, content.actions[1].style)
        assertEquals(true, content.actions[1].checked)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()

        assertEquals(listOf(SettingsPage.Home), navigated)
        assertEquals(
            listOf(
                TrackShareStyle.PLATFORM_CARD,
                TrackShareStyle.TEXT,
                TrackShareStyle.CARD
            ),
            applied
        )
    }

    @Test
    fun streamingGatewayBuildsEndpointActions() {
        val navigated = mutableListOf<SettingsPage>()
        val applied = mutableListOf<String>()

        val content = SettingsPageStateBuilder.streamingGateway(
            languageMode = AppLanguage.MODE_ENGLISH,
            endpoint = StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT,
            configured = true,
            onNavigate = { page -> navigated += page },
            onApplyEndpoint = { endpoint -> applied += endpoint }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.gateway"), content.uiState.title)
        assertEquals(3, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "connected"), content.uiState.metrics[0].value)
        assertEquals(StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT, content.uiState.metrics[1].value)
        assertEquals(4, content.actions.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.gateway.localhost") + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[2].label)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()

        assertEquals(listOf(SettingsPage.SourcesGroup), navigated)
        assertEquals(
            listOf(
                StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT,
                StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT,
                StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT
            ),
            applied
        )
    }

    @Test
    fun libraryBuildsImportActions() {
        val navigated = mutableListOf<SettingsPage>()
        val calls = mutableListOf<String>()

        val content = SettingsPageStateBuilder.library(
            languageMode = AppLanguage.MODE_ENGLISH,
            backPage = SettingsPage.LibraryGroup,
            songCount = 12,
            albumCount = 3,
            artistCount = 5,
            audioPermissionGranted = false,
            onNavigate = { page -> navigated += page },
            onLoadLibrary = { calls += "load" },
            onOpenAudioFilePicker = { calls += "files" },
            onOpenAudioFolderPicker = { calls += "folder" }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "library"), content.uiState.title)
        assertEquals(4, content.uiState.metrics.size)
        assertEquals("12", content.uiState.metrics[0].value)
        assertEquals("3", content.uiState.metrics[1].value)
        assertEquals("5", content.uiState.metrics[2].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "missing"), content.uiState.metrics[3].value)
        assertEquals(4, content.actions.size)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()

        assertEquals(listOf(SettingsPage.LibraryGroup), navigated)
        assertEquals(listOf("load", "files", "folder"), calls)
    }

    @Test
    fun libraryGroupBackCanReturnToSettingsHome() {
        val navigated = mutableListOf<SettingsPage>()

        val content = SettingsPageStateBuilder.library(
            languageMode = AppLanguage.MODE_ENGLISH,
            backPage = SettingsPage.Home,
            songCount = 12,
            albumCount = 3,
            artistCount = 5,
            audioPermissionGranted = true,
            onNavigate = { page -> navigated += page },
            onLoadLibrary = {},
            onOpenAudioFilePicker = {},
            onOpenAudioFolderPicker = {}
        )

        content.actions[0].onClick.run()

        assertEquals(listOf(SettingsPage.Home), navigated)
    }

    @Test
    fun lyricsBuildsLyricsActions() {
        val navigated = mutableListOf<SettingsPage>()
        val onlineChanges = mutableListOf<Boolean>()
        val systemMediaTitleChanges = mutableListOf<Boolean>()
        val statusBarChanges = mutableListOf<Boolean>()
        var reloadCount = 0
        val offsets = mutableListOf<Long>()

        val content = SettingsPageStateBuilder.lyrics(
            languageMode = AppLanguage.MODE_ENGLISH,
            offsetMs = 500L,
            onlineLyricsEnabled = true,
            statusBarLyricsEnabled = false,
            systemMediaLyricsTitleEnabled = false,
            floatingLyricsEnabled = true,
            overlayPermissionGranted = false,
            onNavigate = { page -> navigated += page },
            onOnlineLyricsEnabledChange = { enabled -> onlineChanges += enabled },
            onSystemMediaLyricsTitleEnabledChange = { enabled -> systemMediaTitleChanges += enabled },
            onStatusBarLyricsEnabledChange = { enabled -> statusBarChanges += enabled },
            onReloadLyrics = { reloadCount += 1 },
            onApplyLyricsOffset = { offset -> offsets += offset }
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "lyrics"), content.uiState.title)
        assertEquals(8, content.uiState.metrics.size)
        assertEquals("+0.5 s", content.uiState.metrics[0].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled"), content.uiState.metrics[1].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "disabled"), content.uiState.metrics[2].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "disabled"), content.uiState.metrics[3].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "enabled"), content.uiState.metrics[4].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "missing"), content.uiState.metrics[5].value)
        assertEquals("LRCLIB", content.uiState.metrics[6].value)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "same.name.lrc"), content.uiState.metrics[7].value)
        assertEquals(11, content.actions.size)
        assertEquals(SettingsActionStyle.Toggle, content.actions[3].style)
        assertEquals(false, content.actions[3].checked)
        assertEquals("+0.5 s" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "selected"), content.actions[9].label)

        content.actions[0].onClick.run()
        content.actions[1].onClick.run()
        content.actions[2].onClick.run()
        content.actions[3].onClick.run()
        content.actions[4].onClick.run()
        content.actions[5].onClick.run()
        content.actions[6].onClick.run()
        content.actions[7].onClick.run()
        content.actions[8].onClick.run()
        content.actions[9].onClick.run()
        content.actions[10].onClick.run()

        assertEquals(listOf(SettingsPage.LyricsGroup, SettingsPage.FloatingLyrics), navigated)
        assertEquals(listOf(false), onlineChanges)
        assertEquals(listOf(true), systemMediaTitleChanges)
        assertEquals(listOf(true), statusBarChanges)
        assertEquals(1, reloadCount)
        assertEquals(listOf(-1000L, -500L, 0L, 500L, 1000L), offsets)
    }

    @Test
    fun lyricsGroupUsesGroupTitleAndHomeBackStack() {
        val navigated = mutableListOf<SettingsPage>()

        val content = SettingsPageStateBuilder.lyricsGroup(
            languageMode = AppLanguage.MODE_ENGLISH,
            offsetMs = 0L,
            onlineLyricsEnabled = false,
            statusBarLyricsEnabled = false,
            systemMediaLyricsTitleEnabled = false,
            floatingLyricsEnabled = false,
            overlayPermissionGranted = true,
            onNavigate = { page -> navigated += page },
            onOnlineLyricsEnabledChange = {},
            onSystemMediaLyricsTitleEnabledChange = {},
            onReloadLyrics = {},
            onApplyLyricsOffset = {}
        )

        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "settings.group.lyrics"), content.uiState.title)
        assertEquals(8, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "granted"), content.uiState.metrics[5].value)
        assertEquals(11, content.actions.size)

        content.actions[0].onClick.run()

        assertEquals(listOf(SettingsPage.Home), navigated)
    }

    private fun assertBooleanLeafPage(
        content: SettingsPageStateContent,
        titleKey: String,
        enabled: Boolean
    ) {
        val languageMode = AppLanguage.MODE_ENGLISH
        assertEquals(AppLanguage.text(languageMode, titleKey), content.uiState.title)
        assertEquals(2, content.uiState.metrics.size)
        assertEquals(AppLanguage.text(languageMode, if (enabled) "enabled" else "disabled"), content.uiState.metrics[0].value)
        assertEquals(2, content.actions.size)
        assertEquals(AppLanguage.text(languageMode, titleKey), content.actions[1].label)
        assertEquals(SettingsActionStyle.Toggle, content.actions[1].style)
        assertEquals(enabled, content.actions[1].checked)
        assertEquals(4, content.uiState.items.size)
        assertTrue(content.uiState.items[0] is SettingsItem.Action)
        assertTrue(content.uiState.items[1] is SettingsItem.Navigation)
        assertEquals(
            SettingsItem.Metric(content.uiState.metrics[0].label, content.uiState.metrics[0].value),
            content.uiState.items[2]
        )
        assertEquals(
            SettingsItem.Metric(content.uiState.metrics[1].label, content.uiState.metrics[1].value),
            content.uiState.items[3]
        )
    }
}
