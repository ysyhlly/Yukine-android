package app.yukine

/** Pure page-to-content mapping; feature mutations stay in focused settings owners. */
internal object SettingsPageContentFactory {
    fun build(
        page: SettingsPage,
        preferences: SettingsPreferencesSnapshot,
        runtime: RuntimeSettingsStatus,
        appearance: AppearanceSettingsStateOwner,
        playback: PlaybackSettingsStateOwner,
        lyrics: LyricsSettingsStateOwner,
        library: LibrarySettingsStateOwner,
        network: NetworkSettingsStateOwner,
        platform: PlatformSettingsStateOwner,
        onNavigate: (SettingsPage) -> Unit
    ): SettingsPageStateContent {
        val languageMode = preferences.languageMode
        fun navigateSettingsPage(nextPage: SettingsPage) = onNavigate(nextPage)
        return when (page) {
            SettingsPage.AppearanceGroup ->
                SettingsPageStateBuilder.appearanceGroup(
                    languageMode,
                    preferences.themeMode,
                    preferences.accentMode,
                    preferences.pageBackgrounds,
                    preferences.customBackgroundBlurEnabled,
                    preferences.customBackgroundBlurRadiusDp,
                    preferences.glassBlurEnabled,
                    preferences.glassBlurRadiusDp,
                    preferences.glassSurfaceOpacity,
                    onCustomBackgroundBlurEnabledChange = { enabled ->
                        appearance.setCustomBackgroundBlurEnabled(enabled)
                    },
                    onCustomBackgroundBlurRadiusChange = { radiusDp ->
                        appearance.setCustomBackgroundBlurRadiusDp(radiusDp)
                    },
                    onGlassBlurEnabledChange = { enabled ->
                        appearance.setGlassBlurEnabled(enabled)
                    },
                    onGlassBlurRadiusChange = { radiusDp ->
                        appearance.setGlassBlurRadiusDp(radiusDp)
                    },
                    onGlassSurfaceOpacityChange = { opacity ->
                        appearance.setGlassSurfaceOpacity(opacity)
                    },
                    onNavigate = { nextPage -> onNavigate(nextPage) }
                )
            SettingsPage.PlaybackGroup ->
                SettingsPageStateBuilder.playbackGroup(
                    languageMode,
                    preferences.playbackSpeed,
                    preferences.appVolume,
                    preferences.concurrentPlaybackEnabled,
                    preferences.audioEffectSettings,
                    preferences.nowPlayingGesturesEnabled,
                    preferences.playbackRestoreEnabled,
                    preferences.replayGainEnabled,
                    runtime.sleepTimerRemainingMs,
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    onReplayGainEnabledChange = { enabled ->
                        playback.setReplayGainEnabled(enabled)
                    },
                    onNowPlayingGesturesEnabledChange = { enabled ->
                        playback.setNowPlayingGesturesEnabled(enabled)
                    },
                    onPlaybackRestoreEnabledChange = { enabled ->
                        playback.setPlaybackRestoreEnabled(enabled)
                    },
                    onAudioExclusiveEnabledChange = { enabled ->
                        playback.setAudioExclusiveEnabled(enabled)
                    }
                )
            SettingsPage.LibraryGroup,
            SettingsPage.Library ->
                SettingsPageStateBuilder.library(
                    languageMode,
                    SettingsBackStack.parent(page),
                    runtime.librarySongCount,
                    runtime.libraryAlbumCount,
                    runtime.libraryArtistCount,
                    runtime.audioPermissionGranted,
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    onLoadLibrary = { library.loadLibrary() },
                    onOpenAudioFilePicker = { library.openAudioFilePicker() },
                    onOpenAudioFolderPicker = { library.openAudioFolderPicker() },
                    hiddenItems = runtime.hiddenLibraryItems,
                    onRestoreHidden = { sourceKey -> library.restoreHiddenItem(sourceKey) },
                    onRestoreAllHidden = { library.restoreAllHiddenItems() }
                )
            SettingsPage.LyricsGroup ->
                SettingsPageStateBuilder.lyricsGroup(
                    languageMode,
                    runtime.lyricsOffsetMs,
                    runtime.onlineLyricsEnabled,
                    preferences.statusBarLyricsEnabled,
                    preferences.systemMediaLyricsTitleEnabled,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOnlineLyricsEnabledChange = { enabled -> lyrics.setOnlineLyricsEnabled(enabled) },
                    onSystemMediaLyricsTitleEnabledChange = { enabled ->
                        lyrics.setSystemMediaLyricsTitleEnabled(enabled)
                    },
                    onStatusBarLyricsEnabledChange = { enabled ->
                        lyrics.setStatusBarLyricsEnabled(enabled)
                    },
                    onReloadLyrics = { lyrics.reloadCurrentLyrics() },
                    onApplyLyricsOffset = { offset -> lyrics.applyLyricsOffset(offset) }
                )
            SettingsPage.SourcesGroup ->
                SettingsPageStateBuilder.sourcesGroup(
                    languageMode,
                    preferences.streamingAudioQuality,
                    preferences.shareStyle,
                    runtime.streamingGatewayConfigured,
                    runtime.luoxueImportedSourceCount,
                    runtime.luoxueEnabledSourceCount,
                    onNavigate = ::navigateSettingsPage,
                    onOpenNetworkPage = { page -> network.openPage(page) },
                    onManageLuoxueSources = { network.openLuoxueSourceManager() },
                    onImportLuoxueSource = { network.importLuoxueSource() }
                )
            SettingsPage.AboutGroup ->
                SettingsPageStateBuilder.aboutGroup(
                    languageMode,
                    runtime.audioPermissionGranted,
                    runtime.notificationPermissionGranted,
                    runtime.playbackServiceConnected,
                    preferences.debugPromptsEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onExportBackup = { platform.exportBackup() },
                    onImportBackup = { platform.importBackup() },
                    onDebugPromptsEnabledChange = { enabled ->
                        appearance.setDebugPromptsEnabled(enabled)
                    }
                )
            SettingsPage.Appearance ->
                SettingsPageStateBuilder.theme(
                    languageMode,
                    preferences.themeMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyTheme = { mode -> appearance.applyThemeMode(mode) }
                )
            SettingsPage.AdvancedTheme ->
                SettingsPageStateBuilder.advancedTheme(
                    languageMode,
                    preferences.themeMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyTheme = { mode -> appearance.applyThemeMode(mode) }
                )
            SettingsPage.Accent ->
                SettingsPageStateBuilder.accent(
                    languageMode,
                    preferences.accentMode,
                    preferences.pageBackgrounds,
                    onNavigate = ::navigateSettingsPage,
                    onApplyAccent = { accent -> appearance.applyAccentMode(accent) }
                )
            SettingsPage.Language ->
                SettingsPageStateBuilder.language(
                    languageMode,
                    onNavigate = ::navigateSettingsPage,
                    onApplyLanguage = { mode -> appearance.applyLanguageMode(mode) }
                )
            SettingsPage.PageBackground ->
                SettingsPageStateBuilder.pageBackgrounds(
                    languageMode,
                    preferences.pageBackgrounds,
                    onNavigate = ::navigateSettingsPage,
                    onChoosePageBackground = { target -> appearance.choosePageBackground(target) },
                    onClearPageBackground = { target -> appearance.clearPageBackground(target) }
                )
            SettingsPage.PlaybackSpeed ->
                SettingsPageStateBuilder.playbackSpeed(
                    languageMode,
                    preferences.playbackSpeed,
                    onNavigate = ::navigateSettingsPage,
                    onApplySpeed = { speed -> playback.applyPlaybackSpeed(speed) }
                )
            SettingsPage.AppVolume ->
                SettingsPageStateBuilder.appVolume(
                    languageMode,
                    preferences.appVolume,
                    onNavigate = ::navigateSettingsPage,
                    onApplyVolume = { volume -> playback.applyAppVolume(volume) }
                )
            SettingsPage.AudioEffects ->
                SettingsPageStateBuilder.audioEffects(
                    languageMode,
                    preferences.audioEffectSettings,
                    onNavigate = ::navigateSettingsPage,
                    onApplyAudioEffects = { effects -> playback.applyAudioEffectSettings(effects) }
                )
            SettingsPage.NowPlayingGestures ->
                SettingsPageStateBuilder.nowPlayingGestures(
                    languageMode,
                    preferences.nowPlayingGesturesEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> playback.setNowPlayingGesturesEnabled(enabled) }
                )
            SettingsPage.PlaybackRestore ->
                SettingsPageStateBuilder.playbackRestore(
                    languageMode,
                    preferences.playbackRestoreEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> playback.setPlaybackRestoreEnabled(enabled) }
                )
            SettingsPage.ReplayGain ->
                SettingsPageStateBuilder.replayGain(
                    languageMode,
                    preferences.replayGainEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> playback.setReplayGainEnabled(enabled) }
                )
            SettingsPage.StreamingAudioQuality ->
                SettingsPageStateBuilder.streamingAudioQuality(
                    languageMode,
                    preferences.streamingAudioQuality,
                    preferences.refuseAutomaticQualityDowngrade,
                    onNavigate = ::navigateSettingsPage,
                    onApplyQuality = { quality -> playback.applyStreamingAudioQuality(quality) },
                    onRefuseAutomaticQualityDowngradeChange = { refuse ->
                        playback.setRefuseAutomaticQualityDowngrade(refuse)
                    }
                )
            SettingsPage.ShareStyle ->
                SettingsPageStateBuilder.shareStyle(
                    languageMode,
                    preferences.shareStyle,
                    onNavigate = ::navigateSettingsPage,
                    onApplyStyle = { style -> appearance.applyShareStyle(style) }
                )
            SettingsPage.ConcurrentPlayback ->
                SettingsPageStateBuilder.audioExclusive(
                    languageMode,
                    !preferences.concurrentPlaybackEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> playback.setAudioExclusiveEnabled(enabled) }
                )
            SettingsPage.SleepTimer ->
                SettingsPageStateBuilder.sleepTimer(
                    languageMode,
                    runtime.sleepTimerRemainingMs,
                    onNavigate = ::navigateSettingsPage,
                    onStartTimer = { minutes -> playback.startSleepTimer(minutes) },
                    onCancelTimer = { playback.cancelSleepTimer() }
                )
            SettingsPage.Lyrics ->
                SettingsPageStateBuilder.lyrics(
                    languageMode,
                    runtime.lyricsOffsetMs,
                    runtime.onlineLyricsEnabled,
                    preferences.statusBarLyricsEnabled,
                    preferences.systemMediaLyricsTitleEnabled,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOnlineLyricsEnabledChange = { enabled -> lyrics.setOnlineLyricsEnabled(enabled) },
                    onSystemMediaLyricsTitleEnabledChange = { enabled ->
                        lyrics.setSystemMediaLyricsTitleEnabled(enabled)
                    },
                    onStatusBarLyricsEnabledChange = { enabled ->
                        lyrics.setStatusBarLyricsEnabled(enabled)
                    },
                    onReloadLyrics = { lyrics.reloadCurrentLyrics() },
                    onApplyLyricsOffset = { offset -> lyrics.applyLyricsOffset(offset) }
                )
            SettingsPage.StatusBarLyrics ->
                SettingsPageStateBuilder.statusBarLyrics(
                    languageMode,
                    preferences.statusBarLyricsEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onToggle = { enabled -> lyrics.setStatusBarLyricsEnabled(enabled) }
                )
            SettingsPage.FloatingLyrics ->
                SettingsPageStateBuilder.floatingLyrics(
                    languageMode,
                    preferences.floatingLyricsEnabled,
                    runtime.overlayPermissionGranted,
                    onNavigate = ::navigateSettingsPage,
                    onOpenPermission = { platform.openFloatingLyricsPermission() },
                    onToggle = { enabled -> lyrics.setFloatingLyricsEnabled(enabled) }
                )
            SettingsPage.StreamingGateway ->
                SettingsPageStateBuilder.streamingGateway(
                    languageMode,
                    runtime.streamingGatewayEndpoint,
                    runtime.streamingGatewayConfigured,
                    onNavigate = ::navigateSettingsPage,
                    onApplyEndpoint = { endpoint -> network.applyStreamingGatewayEndpoint(endpoint) }
                )
            SettingsPage.Home,
            SettingsPage.Downloads ->
                SettingsPageStateBuilder.home(
                    languageMode,
                    runtime.audioPermissionGranted,
                    runtime.notificationPermissionGranted,
                    runtime.playbackServiceConnected,
                    onNavigate = ::navigateSettingsPage,
                    onRequestNeededPermissions = { platform.requestNeededPermissions() },
                    onOpenDownloads = { platform.openDownloads() }
                )
        }
    }

}
