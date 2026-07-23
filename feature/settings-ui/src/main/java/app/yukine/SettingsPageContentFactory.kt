package app.yukine

import app.yukine.playback.AudioFallbackReason
import app.yukine.playback.AudioOutputPhase
import app.yukine.playback.AudioOutputSnapshot

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
        onNavigate: (SettingsPage) -> Unit,
        onSearchNavigate: (SettingsEntryId, SettingsPage) -> Unit
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
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    nowPlayingGesturesEnabled = preferences.nowPlayingGesturesEnabled,
                    shareStyle = preferences.shareStyle,
                    onNowPlayingGesturesEnabledChange = { enabled ->
                        playback.setNowPlayingGesturesEnabled(enabled)
                    },
                    compactSettingsCards = preferences.compactSettingsCards,
                    onCompactSettingsCardsChange = { enabled ->
                        appearance.setCompactSettingsCards(enabled)
                    },
                    homeDashboardLayout = preferences.homeDashboardLayout,
                    onHomeDashboardLayoutChange = { layout ->
                        appearance.setHomeDashboardLayout(layout)
                    }
                )
            SettingsPage.PlaybackGroup ->
                SettingsPageStateBuilder.playbackGroup(
                    languageMode,
                    preferences.playbackSpeed,
                    preferences.appVolume,
                    preferences.audioEffectSettings,
                    preferences.playbackRestoreEnabled,
                    preferences.replayGainEnabled,
                    audioExclusiveEnabled = preferences.audioExclusiveEnabled,
                    bitPerfectEnabled = preferences.bitPerfectEnabled,
                    usbExclusiveEnabled = preferences.usbExclusiveEnabled,
                    usbClockMismatchCompatibilityEnabled =
                        preferences.usbClockMismatchCompatibilityEnabled,
                    remainingMs = runtime.sleepTimerRemainingMs,
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    onReplayGainEnabledChange = { enabled ->
                        playback.setReplayGainEnabled(enabled)
                    },
                    onPlaybackRestoreEnabledChange = { enabled ->
                        playback.setPlaybackRestoreEnabled(enabled)
                    },
                    onAudioExclusiveEnabledChange = { enabled ->
                        playback.setAudioExclusiveEnabled(enabled)
                    },
                    onBitPerfectEnabledChange = { enabled ->
                        playback.setBitPerfectEnabled(enabled)
                    },
                    onUsbExclusiveEnabledChange = { enabled ->
                        playback.setUsbExclusiveEnabled(enabled)
                    },
                    onUsbClockMismatchCompatibilityEnabledChange = { enabled ->
                        playback.setUsbClockMismatchCompatibilityEnabled(enabled)
                    },
                    audioExclusiveStatusDescription = focusStatusDescription(
                        languageMode,
                        preferences.audioExclusiveEnabled,
                        runtime.audioExclusiveActive
                    ),
                    bitPerfectStatusDescription = outputStatusDescription(
                        languageMode,
                        preferences.bitPerfectEnabled,
                        runtime.audioOutput
                    ),
                    usbExclusiveStatusDescription = outputStatusDescription(
                        languageMode,
                        preferences.usbExclusiveEnabled,
                        runtime.audioOutput
                    )
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
                    identityBackfill = runtime.identityBackfill,
                    dedupMode = runtime.libraryDedupMode,
                    duplicateCandidateCount = runtime.duplicateCandidateCenter.total,
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    onLoadLibrary = { library.loadLibrary() },
                    onOpenAudioFilePicker = { library.openAudioFilePicker() },
                    onOpenAudioFolderPicker = { library.openAudioFolderPicker() },
                    onRebuildSongIdentity = { library.rebuildSongIdentity() },
                    onCancelIdentityBackfill = { library.cancelIdentityBackfill() },
                    onDedupModeChange = { mode -> library.setDedupMode(mode) },
                    hiddenItems = runtime.hiddenLibraryItems,
                    onRestoreHidden = { sourceKey -> library.restoreHiddenItem(sourceKey) },
                    onRestoreAllHidden = { library.restoreAllHiddenItems() }
                )
            SettingsPage.DuplicateCandidates ->
                SettingsPageStateBuilder.duplicateCandidates(
                    languageMode = languageMode,
                    backPage = SettingsBackStack.parent(page),
                    center = runtime.duplicateCandidateCenter,
                    onNavigate = { nextPage -> onNavigate(nextPage) },
                    onConfirm = { leftRecordingId, rightRecordingId ->
                        library.confirmDuplicateCandidate(leftRecordingId, rightRecordingId)
                    },
                    onConfirmBatch = { library.confirmHighConfidenceDuplicates() }
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
                    onImportCurrentLyrics = { lyrics.importCurrentLyrics() },
                    onImportLyricsDirectory = { lyrics.importLyricsDirectory() },
                    onViewLyricsImportReport = { lyrics.viewLyricsImportReport() },
                    onApplyLyricsOffset = { offset -> lyrics.applyLyricsOffset(offset) }
                )
            SettingsPage.SourcesGroup ->
                SettingsPageStateBuilder.sourcesGroup(
                    languageMode,
                    preferences.streamingAudioQuality,
                    runtime.streamingGatewayConfigured,
                    runtime.luoxueImportedSourceCount,
                    runtime.luoxueEnabledSourceCount,
                    onNavigate = ::navigateSettingsPage,
                    onOpenNetworkPage = { page -> network.openPage(page) },
                    onManageLuoxueSources = { network.openLuoxueSourceManager() },
                    onImportLuoxueSource = { network.importLuoxueSource() },
                    kugouExperimentalSyncEnabled = runtime.kugouExperimentalSyncEnabled,
                    kugouAccountConnected = runtime.kugouAccountConnected,
                    kugouAccountDisplayName = runtime.kugouAccountDisplayName,
                    kugouSyncLastResult = runtime.kugouSyncLastResult,
                    kugouSyncDegradationReason = runtime.kugouSyncDegradationReason,
                    onKugouExperimentalSyncEnabledChange = { enabled ->
                        network.setKugouExperimentalSyncEnabled(enabled)
                    }
                )
            SettingsPage.AboutGroup ->
                SettingsPageStateBuilder.aboutGroup(
                    languageMode,
                    runtime.appVersionName,
                    runtime.audioPermissionGranted,
                    runtime.notificationPermissionGranted,
                    preferences.debugPromptsEnabled,
                    preferences.checkUpdateEnabled,
                    onNavigate = ::navigateSettingsPage,
                    onRequestNeededPermissions = { platform.requestNeededPermissions() },
                    onDebugPromptsEnabledChange = { enabled ->
                        appearance.setDebugPromptsEnabled(enabled)
                    },
                    onExportDiagnostics = { platform.exportDiagnostics() },
                    onCheckUpdateEnabledChange = { enabled ->
                        appearance.setCheckUpdateEnabled(enabled)
                    },
                    onCheckUpdateNow = { platform.checkGitHubUpdate() }
                )
            SettingsPage.Downloads ->
                SettingsPageStateBuilder.storageGroup(
                    languageMode = languageMode,
                    onNavigate = ::navigateSettingsPage,
                    onOpenDownloads = { platform.openDownloads() },
                    onExportBackup = { platform.exportBackup() },
                    onImportBackup = { platform.importBackup() }
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
                    onImportCurrentLyrics = { lyrics.importCurrentLyrics() },
                    onImportLyricsDirectory = { lyrics.importLyricsDirectory() },
                    onViewLyricsImportReport = { lyrics.viewLyricsImportReport() },
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
                    runtime.floatingLyricsRuntimeStatus,
                    runtime.floatingLyricsTextSizeSp,
                    runtime.floatingLyricsWidthPercent,
                    runtime.floatingLyricsBackgroundOpacityPercent,
                    runtime.floatingLyricsTransparentBackground,
                    runtime.floatingLyricsTextColorArgb,
                    onNavigate = ::navigateSettingsPage,
                    onOpenPermission = { platform.openFloatingLyricsPermission() },
                    onToggle = { enabled -> lyrics.setFloatingLyricsEnabled(enabled) },
                    onTextSizeChange = { value -> lyrics.setFloatingLyricsTextSize(value) },
                    onWidthChange = { value -> lyrics.setFloatingLyricsWidth(value) },
                    onBackgroundOpacityChange = { value ->
                        lyrics.setFloatingLyricsBackgroundOpacity(value)
                    },
                    onTransparentBackgroundChange = { value ->
                        lyrics.setFloatingLyricsTransparentBackground(value)
                    },
                    onTextColorChange = { color -> lyrics.setFloatingLyricsTextColor(color) },
                    onShow = { lyrics.showFloatingLyrics() },
                    onUnlock = { lyrics.unlockFloatingLyrics() },
                    onReset = { lyrics.resetFloatingLyricsLayout() }
                )
            SettingsPage.StreamingGateway ->
                SettingsPageStateBuilder.streamingGateway(
                    languageMode,
                    runtime.streamingGatewayEndpoint,
                    runtime.streamingGatewayConfigured,
                    onNavigate = ::navigateSettingsPage,
                    onApplyEndpoint = { endpoint -> network.applyStreamingGatewayEndpoint(endpoint) },
                    onEditMusicBrainzProxy = { network.editMusicBrainzProxy() }
                )
            SettingsPage.Home ->
                SettingsPageStateBuilder.home(
                    languageMode = languageMode,
                    preferences = preferences,
                    runtime = runtime,
                    onNavigate = ::navigateSettingsPage,
                    onOpenSearchEntry = onSearchNavigate,
                    onRequestNeededPermissions = { platform.requestNeededPermissions() },
                    onOpenOverlayPermission = { platform.openFloatingLyricsPermission() }
                )
    }
    }

    private fun focusStatusDescription(languageMode: String, requested: Boolean, active: Boolean): String {
        return if (AppLanguage.isChinese(languageMode)) {
            "请求：${if (requested) "开启" else "关闭"}；实际：${if (active) "已取得独占音频焦点" else "协作音频焦点"}"
        } else {
            "Requested: ${if (requested) "on" else "off"}; actual: ${if (active) "exclusive audio focus" else "cooperative audio focus"}"
        }
    }

    private fun outputStatusDescription(
        languageMode: String,
        requested: Boolean,
        snapshot: AudioOutputSnapshot
    ): String {
        val reason = localizedFallbackReason(languageMode, snapshot.fallbackReason)
        val format = when {
            snapshot.dsdRate > 0 -> "DSD${snapshot.dsdRate}"
            snapshot.phase == app.yukine.playback.AudioOutputPhase.NEGOTIATING &&
                snapshot.requestedSampleRateHz > 0 &&
                snapshot.requestedSampleRateHz != snapshot.previousSampleRateHz ->
                "${snapshot.previousSampleRateHz} Hz → ${snapshot.requestedSampleRateHz} Hz"
            snapshot.sampleRateHz > 0 -> "${snapshot.sampleRateHz} Hz"
            else -> ""
        }
        val actual = listOf(snapshot.transport.name, snapshot.phase.name, format)
            .filter(String::isNotBlank)
            .joinToString(" · ")
        return if (AppLanguage.isChinese(languageMode)) {
            "请求：${if (requested) "开启" else "关闭"}；实际：$actual" +
                if (reason.isBlank()) "" else "；原因：$reason"
        } else {
            "Requested: ${if (requested) "on" else "off"}; actual: $actual" +
                if (reason.isBlank()) "" else "; reason: $reason"
        }
    }

    private fun localizedFallbackReason(languageMode: String, reason: AudioFallbackReason): String {
        if (reason == AudioFallbackReason.NONE) return ""
        if (!AppLanguage.isChinese(languageMode)) return reason.name.lowercase().replace('_', ' ')
        return when (reason) {
            AudioFallbackReason.NO_USB_DEVICE -> "未连接 USB 音频设备"
            AudioFallbackReason.USB_PERMISSION_DENIED -> "USB 权限被拒绝"
            AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE -> "原生 USB 传输库不可用"
            AudioFallbackReason.NO_COMPATIBLE_ENDPOINT -> "无兼容音频端点"
            AudioFallbackReason.CLOCK_NEGOTIATION_FAILED -> "时钟或采样率协商失败"
            AudioFallbackReason.SESSION_RECONFIGURE_FAILED -> "USB 音频会话切换失败"
            AudioFallbackReason.TRANSFER_FAILED -> "USB 传输失败"
            AudioFallbackReason.DEVICE_DETACHED -> "设备已断开"
            AudioFallbackReason.NATIVE_DSD_PROFILE_MISSING -> "缺少 Native DSD 设备配置"
            AudioFallbackReason.DOP_UNSUPPORTED -> "设备不支持 DoP"
            AudioFallbackReason.FORMAT_UNSUPPORTED -> "格式不受支持"
            AudioFallbackReason.REMOTE_DSD_NOT_CACHED -> "远程 DSD 需先完整下载"
            AudioFallbackReason.DST_UNSUPPORTED -> "首版不支持 DST 压缩"
            AudioFallbackReason.OFFLOAD_UNAVAILABLE -> "硬件 Offload 不可用"
            AudioFallbackReason.OFFLOAD_FAILED -> "硬件 Offload 失败"
            AudioFallbackReason.NONE -> ""
        }
    }
}
