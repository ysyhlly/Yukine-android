package app.yukine

internal class SettingsRenderCoordinator(
    private val renderer: SettingsPageRenderController,
    private val settingsStore: MainSettingsStore,
    private val libraryStore: MainLibraryStore,
    private val permissionController: MainPermissionController,
    private val playbackConnectionController: PlaybackServiceConnectionController,
    private val playbackStore: MainPlaybackStore,
    private val lyricsViewModel: LyricsViewModel?,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore
) {
    fun scrollToTopOnNextRender() {
        renderer.scrollToTopOnNextRender()
    }

    fun render(settingsPage: String) {
        when (settingsPage) {
            MainRoutes.SETTINGS_APPEARANCE_GROUP -> {
                renderer.renderAppearanceGroup(
                    settingsStore.languageMode(),
                    settingsStore.themeMode(),
                    settingsStore.accentMode()
                )
            }
            MainRoutes.SETTINGS_PLAYBACK_GROUP -> {
                renderer.renderPlaybackGroup(
                    settingsStore.languageMode(),
                    settingsStore.playbackSpeed(),
                    settingsStore.appVolume(),
                    settingsStore.concurrentPlaybackEnabled(),
                    settingsStore.audioEffectSettings(),
                    settingsStore.nowPlayingGesturesEnabled(),
                    settingsStore.playbackRestoreEnabled(),
                    settingsStore.replayGainEnabled(),
                    playbackStore.snapshot().sleepTimerRemainingMs
                )
            }
            MainRoutes.SETTINGS_LIBRARY_GROUP -> {
                val allTracks = libraryStore.allTracks()
                renderer.renderLibraryGroup(
                    settingsStore.languageMode(),
                    allTracks.size,
                    LibraryGrouping.uniqueAlbumCount(allTracks),
                    LibraryGrouping.uniqueArtistCount(allTracks),
                    permissionController.hasAudioPermission()
                )
            }
            MainRoutes.SETTINGS_LYRICS_GROUP -> {
                val currentOffsetMs = lyricsViewModel?.offsetMs() ?: 0L
                val currentOnlineLyricsEnabled = lyricsViewModel?.onlineEnabled() == true
                renderer.renderLyricsGroup(
                    settingsStore.languageMode(),
                    currentOffsetMs,
                    currentOnlineLyricsEnabled,
                    settingsStore.statusBarLyricsEnabled(),
                    settingsStore.floatingLyricsEnabled(),
                    permissionController.hasOverlayPermission()
                )
            }
            MainRoutes.SETTINGS_SOURCES_GROUP -> {
                renderer.renderSourcesGroup(
                    settingsStore.languageMode(),
                    settingsStore.streamingAudioQuality(),
                    settingsStore.shareStyle(),
                    streamingGatewaySettingsStore.configured()
                )
            }
            MainRoutes.SETTINGS_ABOUT_GROUP -> {
                renderer.renderAboutGroup(
                    settingsStore.languageMode(),
                    permissionController.hasAudioPermission(),
                    permissionController.hasNotificationPermission(),
                    playbackConnectionController.isBound()
                )
            }
            MainRoutes.SETTINGS_APPEARANCE -> {
                renderer.renderTheme(settingsStore.languageMode(), settingsStore.themeMode())
            }
            MainRoutes.SETTINGS_ADVANCED_THEME -> {
                renderer.renderAdvancedTheme(settingsStore.languageMode(), settingsStore.themeMode())
            }
            MainRoutes.SETTINGS_ACCENT -> {
                renderer.renderAccent(settingsStore.languageMode(), settingsStore.accentMode())
            }
            MainRoutes.SETTINGS_LANGUAGE -> {
                renderer.renderLanguage(settingsStore.languageMode())
            }
            MainRoutes.SETTINGS_PLAYBACK_SPEED -> {
                renderer.renderPlaybackSpeed(settingsStore.languageMode(), settingsStore.playbackSpeed())
            }
            MainRoutes.SETTINGS_APP_VOLUME -> {
                renderer.renderAppVolume(settingsStore.languageMode(), settingsStore.appVolume())
            }
            MainRoutes.SETTINGS_AUDIO_EFFECTS -> {
                renderer.renderAudioEffects(settingsStore.languageMode(), settingsStore.audioEffectSettings())
            }
            MainRoutes.SETTINGS_NOW_PLAYING_GESTURES -> {
                renderer.renderNowPlayingGestures(settingsStore.languageMode(), settingsStore.nowPlayingGesturesEnabled())
            }
            MainRoutes.SETTINGS_PLAYBACK_RESTORE -> {
                renderer.renderPlaybackRestore(settingsStore.languageMode(), settingsStore.playbackRestoreEnabled())
            }
            MainRoutes.SETTINGS_REPLAY_GAIN -> {
                renderer.renderReplayGain(settingsStore.languageMode(), settingsStore.replayGainEnabled())
            }
            MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY -> {
                renderer.renderStreamingAudioQuality(settingsStore.languageMode(), settingsStore.streamingAudioQuality())
            }
            MainRoutes.SETTINGS_SHARE_STYLE -> {
                renderer.renderShareStyle(settingsStore.languageMode(), settingsStore.shareStyle())
            }
            MainRoutes.SETTINGS_CONCURRENT_PLAYBACK -> {
                renderer.renderConcurrentPlayback(settingsStore.languageMode(), settingsStore.concurrentPlaybackEnabled())
            }
            MainRoutes.SETTINGS_SLEEP_TIMER -> {
                renderer.renderSleepTimer(settingsStore.languageMode(), playbackStore.snapshot().sleepTimerRemainingMs)
            }
            MainRoutes.SETTINGS_LYRICS -> {
                val currentOffsetMs = lyricsViewModel?.offsetMs() ?: 0L
                val currentOnlineLyricsEnabled = lyricsViewModel?.onlineEnabled() == true
                renderer.renderLyrics(
                    settingsStore.languageMode(),
                    currentOffsetMs,
                    currentOnlineLyricsEnabled,
                    settingsStore.statusBarLyricsEnabled(),
                    settingsStore.floatingLyricsEnabled(),
                    permissionController.hasOverlayPermission()
                )
            }
            MainRoutes.SETTINGS_STATUS_BAR_LYRICS -> {
                renderer.renderStatusBarLyrics(settingsStore.languageMode(), settingsStore.statusBarLyricsEnabled())
            }
            MainRoutes.SETTINGS_FLOATING_LYRICS -> {
                renderer.renderFloatingLyrics(
                    settingsStore.languageMode(),
                    settingsStore.floatingLyricsEnabled(),
                    permissionController.hasOverlayPermission()
                )
            }
            MainRoutes.SETTINGS_LIBRARY -> {
                val allTracks = libraryStore.allTracks()
                renderer.renderLibrary(
                    settingsStore.languageMode(),
                    allTracks.size,
                    LibraryGrouping.uniqueAlbumCount(allTracks),
                    LibraryGrouping.uniqueArtistCount(allTracks),
                    permissionController.hasAudioPermission()
                )
            }
            MainRoutes.SETTINGS_STREAMING_GATEWAY -> {
                renderer.renderStreamingGateway(
                    settingsStore.languageMode(),
                    streamingGatewaySettingsStore.endpoint(),
                    streamingGatewaySettingsStore.configured()
                )
            }
            else -> {
                renderer.renderHome(
                    settingsStore.themeMode(),
                    settingsStore.accentMode(),
                    settingsStore.languageMode(),
                    permissionController.hasAudioPermission(),
                    permissionController.hasNotificationPermission(),
                    playbackConnectionController.isBound()
                )
            }
        }
    }
}
