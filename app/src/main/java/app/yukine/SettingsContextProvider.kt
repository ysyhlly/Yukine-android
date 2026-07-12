package app.yukine

internal class SettingsContextProvider(
    private val settingsStore: MainSettingsStore,
    private val libraryStore: MainLibraryStore,
    private val permissionController: MainPermissionController,
    private val playbackConnectionController: PlaybackServiceConnectionController,
    private val playbackStore: MainPlaybackStore,
    private val lyricsViewModel: LyricsViewModel?,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore,
    private val repository: app.yukine.data.MusicLibraryRepository
) {
    fun preferencesSnapshot(): SettingsPreferencesSnapshot = settingsStore.preferencesSnapshot()

    fun runtimeStatus(): RuntimeSettingsStatus {
        val allTracks = libraryStore.allTracks()
        return RuntimeSettingsStatus(
            audioPermissionGranted = permissionController.hasAudioPermission(),
            notificationPermissionGranted = permissionController.hasNotificationPermission(),
            overlayPermissionGranted = permissionController.hasOverlayPermission(),
            playbackServiceConnected = playbackConnectionController.isBound(),
            sleepTimerRemainingMs = playbackStore.snapshot().sleepTimerRemainingMs,
            lyricsOffsetMs = lyricsViewModel?.offsetMs() ?: 0L,
            onlineLyricsEnabled = lyricsViewModel?.onlineEnabled() == true,
            librarySongCount = allTracks.size,
            libraryAlbumCount = LibraryGrouping.uniqueAlbumCount(allTracks),
            libraryArtistCount = LibraryGrouping.uniqueArtistCount(allTracks),
            streamingGatewayEndpoint = streamingGatewaySettingsStore.endpoint(),
            streamingGatewayConfigured = streamingGatewaySettingsStore.configured(),
            hiddenLibraryItems = repository.loadLibraryExclusions().map { exclusion ->
                HiddenLibraryItemUi(exclusion.sourceKey, exclusion.displayName())
            }
        )
    }
}
