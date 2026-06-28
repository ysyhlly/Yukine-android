package app.yukine

import androidx.activity.ComponentActivity
import app.yukine.playback.AudioEffectSettings
import app.yukine.playback.EchoPlaybackService

internal class SettingsCoordinator(
    private val activity: ComponentActivity,
    private val settingsViewModel: SettingsViewModel,
    private val settingsStore: MainSettingsStore,
    private val libraryStore: MainLibraryStore,
    private val permissionController: MainPermissionController,
    private val playbackServiceConnectionController: PlaybackServiceConnectionController,
    private val playbackStore: MainPlaybackStore,
    private val lyricsViewModel: LyricsViewModel,
    private val streamingGatewaySettingsStore: StreamingGatewaySettingsStore,
    private val uiShellController: MainUiShellController,
    private val listener: Listener
) {

    interface Listener {
        fun currentPlaybackService(): EchoPlaybackService?
    }

    val settingsContextProvider: SettingsContextProvider = SettingsContextProvider(
        settingsStore,
        libraryStore,
        permissionController,
        playbackServiceConnectionController,
        playbackStore,
        lyricsViewModel,
        streamingGatewaySettingsStore
    )

    fun bindRuntimeApplier() {
        val settingsRuntimeApplier = SettingsRuntimeApplier(
            { uiShellController.applyThemeSurface() },
            {
                val svc = listener.currentPlaybackService() ?: return@SettingsRuntimeApplier null
                object : SettingsPlaybackServiceControls {
                    override fun setPlaybackSpeed(speed: Float) { svc.setPlaybackSpeed(speed) }
                    override fun setAppVolume(volume: Float) { svc.setAppVolume(volume) }
                    override fun setConcurrentPlaybackEnabled(enabled: Boolean) { svc.setConcurrentPlaybackEnabled(enabled) }
                    override fun applyAudioEffectSettings(settings: AudioEffectSettings) { svc.applyAudioEffectSettings(settings) }
                    override fun setStatusBarLyricsEnabled(enabled: Boolean) { svc.setStatusBarLyricsEnabled(enabled) }
                    override fun setPlaybackRestoreEnabled(enabled: Boolean) { svc.setPlaybackRestoreEnabled(enabled) }
                    override fun setReplayGainEnabled(enabled: Boolean) { svc.setReplayGainEnabled(enabled) }
                }
            },
            {
                val lvm = lyricsViewModel
                object : SettingsLyricsControls {
                    override fun setOnlineEnabled(enabled: Boolean) { lvm.setOnlineEnabled(enabled) }
                    override fun setOffsetMs(offsetMs: Long) { lvm.setOffsetMs(offsetMs) }
                }
            },
            {
                object : SettingsFloatingLyricsControls {
                    override fun apply(enabled: Boolean): Boolean {
                        if (!enabled) {
                            FloatingLyricsService.stop(activity)
                            return true
                        }
                        if (!permissionController.hasOverlayPermission()) {
                            FloatingLyricsService.stop(activity)
                            permissionController.openOverlayPermissionSettings()
                            return false
                        }
                        FloatingLyricsService.start(activity)
                        return true
                    }

                    override fun openPermissionSettings() {
                        permissionController.openOverlayPermissionSettings()
                    }
                }
            }
        )
        settingsViewModel.bindRuntimeEffectListener { update -> settingsRuntimeApplier.apply(update) }
    }

    fun renderSettings(settingsPage: String) {
        settingsViewModel.renderPageFromHost(
            SettingsPage.fromRoute(settingsPage),
            settingsContextProvider.preferencesSnapshot(),
            settingsContextProvider.runtimeStatus()
        )
    }
}
