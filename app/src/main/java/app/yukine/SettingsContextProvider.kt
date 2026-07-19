package app.yukine

import android.content.Context

internal class SettingsContextProvider(
    context: Context,
    private val settingsStore: MainSettingsStore,
    private val libraryStore: LibraryDataStateOwner,
    private val permissionController: MainPermissionController,
    private val playbackConnectionController: PlaybackServiceConnectionController,
    private val playbackSnapshotProvider: PlaybackSnapshotProvider,
    private val lyricsViewModel: LyricsViewModel?,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore,
    private val luoxueSourceStore: app.yukine.streaming.LuoxueSourceStoreManager,
    private val repository: app.yukine.data.MusicLibraryRepository
) : SettingsContextLoader {
    private val identityBackfillStore = IdentityBackfillCheckpointStore(context)
    private val floatingLyricsSettingsStore = FloatingLyricsOverlaySettingsStore(context)
    private val kugouExperimentalSyncStore =
        app.yukine.streaming.KugouExperimentalSyncStore(context)
    private val localStreamingAuthStore =
        app.yukine.streaming.LocalStreamingAuthStore(context)
    override fun load(): SettingsContextSnapshot = SettingsContextSnapshot(
        preferences = preferencesSnapshot(),
        runtime = runtimeStatus()
    )

    fun preferencesSnapshot(): SettingsPreferencesSnapshot = settingsStore.preferencesSnapshot()

    fun runtimeStatus(): RuntimeSettingsStatus {
        val allTracks = libraryStore.allTracks()
        val luoxueSources = luoxueSourceStore.load()
        val identityBackfill = identityBackfillStore.load().progress
        val floatingLyricsSettings = floatingLyricsSettingsStore.load()
        val kugouAuth = localStreamingAuthStore.authState(
            app.yukine.streaming.StreamingProviderName.KUGOU
        )
        val kugouSync = kugouExperimentalSyncStore.status(kugouAuth.connected)
        return RuntimeSettingsStatus(
            appVersionName = BuildConfig.VERSION_NAME,
            audioPermissionGranted = permissionController.hasAudioPermission(),
            notificationPermissionGranted = permissionController.hasNotificationPermission(),
            overlayPermissionGranted = permissionController.hasOverlayPermission(),
            floatingLyricsRuntimeStatus = FloatingLyricsService.runtimeStatus().name,
            floatingLyricsTextSizeSp = floatingLyricsSettings.textSizeSp,
            floatingLyricsWidthPercent = floatingLyricsSettings.widthPercent,
            floatingLyricsBackgroundOpacityPercent =
                floatingLyricsSettings.backgroundOpacityPercent,
            floatingLyricsTransparentBackground =
                floatingLyricsSettings.transparentBackground,
            playbackServiceConnected = playbackConnectionController.isBound(),
            sleepTimerRemainingMs = playbackSnapshotProvider.playbackSnapshot.value.sleepTimerRemainingMs,
            lyricsOffsetMs = lyricsViewModel?.offsetMs() ?: 0L,
            onlineLyricsEnabled = lyricsViewModel?.onlineEnabled() == true,
            librarySongCount = allTracks.size,
            libraryAlbumCount = LibraryGrouping.uniqueAlbumCount(allTracks),
            libraryArtistCount = LibraryGrouping.uniqueArtistCount(allTracks),
            streamingGatewayEndpoint = streamingGatewaySettingsStore.endpoint(),
            streamingGatewayConfigured = streamingGatewaySettingsStore.configured(),
            luoxueImportedSourceCount = luoxueSources.size,
            luoxueEnabledSourceCount = luoxueSources.count { it.enabled && it.script.isNotBlank() },
            kugouExperimentalSyncEnabled = kugouSync.userEnabled,
            kugouAccountConnected = kugouAuth.connected,
            kugouAccountDisplayName = kugouAuth.accountDisplayName.orEmpty(),
            kugouSyncLastResult = kugouSync.lastResult.orEmpty(),
            kugouSyncDegradationReason = kugouSync.degradationReason.orEmpty(),
            identityBackfill = IdentityBackfillStatusUi(
                total = identityBackfill.total,
                processed = identityBackfill.processed,
                merged = identityBackfill.merged,
                pending = identityBackfill.pending,
                lxMigrated = identityBackfill.lxMigrated,
                lxDeleted = identityBackfill.lxDeleted
            ),
            hiddenLibraryItems = repository.loadLibraryExclusions().map { exclusion ->
                HiddenLibraryItemUi(exclusion.sourceKey, exclusion.displayName())
            }
        )
    }
}
