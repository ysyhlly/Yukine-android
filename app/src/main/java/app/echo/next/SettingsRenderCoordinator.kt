package app.echo.next

internal class SettingsRenderCoordinator(
    private val renderer: SettingsPageRenderController,
    private val settingsStore: MainSettingsStore,
    private val libraryStore: MainLibraryStore,
    private val permissionController: MainPermissionController,
    private val playbackConnectionController: PlaybackServiceConnectionController,
    private val playbackStore: MainPlaybackStore,
    private val lyricsController: LyricsController?,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore
) {
    fun scrollToTopOnNextRender() {
        renderer.scrollToTopOnNextRender()
    }

    fun render(settingsPage: String) {
        when (settingsPage) {
            MainRoutes.SETTINGS_APPEARANCE -> {
                renderer.renderTheme(settingsStore.languageMode(), settingsStore.themeMode())
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
            MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY -> {
                renderer.renderStreamingAudioQuality(settingsStore.languageMode(), settingsStore.streamingAudioQuality())
            }
            MainRoutes.SETTINGS_CONCURRENT_PLAYBACK -> {
                renderer.renderConcurrentPlayback(settingsStore.languageMode(), settingsStore.concurrentPlaybackEnabled())
            }
            MainRoutes.SETTINGS_SLEEP_TIMER -> {
                renderer.renderSleepTimer(settingsStore.languageMode(), playbackStore.snapshot().sleepTimerRemainingMs)
            }
            MainRoutes.SETTINGS_LYRICS -> {
                val currentOffsetMs = lyricsController?.offsetMs() ?: 0L
                val currentOnlineLyricsEnabled = lyricsController?.onlineEnabled() == true
                renderer.renderLyrics(settingsStore.languageMode(), currentOffsetMs, currentOnlineLyricsEnabled)
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
