package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPageEventControllerTest {
    @Test
    fun forwardsSettingsPageActionsThroughViewModel() {
        val gateway = FakeSettingsGateway()
        val viewModel = SettingsViewModel().apply {
            bindGateway(gateway)
        }
        val controller = SettingsPageEventController(viewModel, FakeContentSink())

        controller.navigateSettingsPage(MainRoutes.SETTINGS_LIBRARY)
        controller.openNetworkSources()
        controller.openDownloads()
        controller.loadLibrary()
        controller.openAudioFilePicker()
        controller.openAudioFolderPicker()
        controller.setOnlineLyricsEnabled(true)
        controller.reloadCurrentLyrics()
        controller.applyLyricsOffset(500L)
        controller.startSleepTimer(30)
        controller.cancelSleepTimer()
        controller.applyPlaybackSpeed(1.25f)
        controller.applyAppVolume(0.7f)
        controller.applyStreamingAudioQuality(StreamingQualityPreference.HIGH)
        controller.applyShareStyle(TrackShareStyle.PLATFORM_CARD)
        controller.setConcurrentPlaybackEnabled(true)
        controller.setStatusBarLyricsEnabled(false)
        controller.setFloatingLyricsEnabled(true)
        controller.openFloatingLyricsPermission()
        controller.setNowPlayingGesturesEnabled(false)
        controller.setPlaybackRestoreEnabled(true)
        controller.applyThemeMode("dark")
        controller.applyAccentMode("teal")
        controller.applyLanguageMode(AppLanguage.MODE_ENGLISH)
        controller.applyStreamingGatewayEndpoint("http://localhost:3301")
        controller.exportBackup()
        controller.importBackup()

        assertEquals(
            listOf(
                "nav:${MainRoutes.SETTINGS_LIBRARY}",
                "network",
                "downloads",
                "load",
                "file",
                "folder",
                "online:true",
                "reload",
                "offset:500",
                "start:30",
                "cancel",
                "speed:1.25",
                "volume:0.7",
                "quality:high",
                "shareStyle:${TrackShareStyle.PLATFORM_CARD}",
                "concurrent:true",
                "statusLyrics:false",
                "floating:true",
                "floatingPermission",
                "gestures:false",
                "restore:true",
                "theme:dark",
                "accent:teal",
                "language:${AppLanguage.MODE_ENGLISH}",
                "endpoint:http://localhost:3301",
                "exportBackup",
                "importBackup"
            ),
            gateway.calls
        )
    }

    private class FakeSettingsGateway : SettingsGateway {
        val calls = ArrayList<String>()

        override fun navigateSettingsPage(page: String) {
            calls.add("nav:$page")
        }

        override fun openNetworkSources() {
            calls.add("network")
        }

        override fun openDownloads() {
            calls.add("downloads")
        }

        override fun loadLibrary() {
            calls.add("load")
        }

        override fun openAudioFilePicker() {
            calls.add("file")
        }

        override fun openAudioFolderPicker() {
            calls.add("folder")
        }

        override fun setOnlineLyricsEnabled(enabled: Boolean) {
            calls.add("online:$enabled")
        }

        override fun reloadCurrentLyrics() {
            calls.add("reload")
        }

        override fun applyLyricsOffset(offsetMs: Long) {
            calls.add("offset:$offsetMs")
        }

        override fun startSleepTimer(minutes: Int) {
            calls.add("start:$minutes")
        }

        override fun cancelSleepTimer() {
            calls.add("cancel")
        }

        override fun applyPlaybackSpeed(speed: Float) {
            calls.add("speed:$speed")
        }

        override fun applyAppVolume(volume: Float) {
            calls.add("volume:$volume")
        }

        override fun applyAudioEffectSettings(settings: app.yukine.playback.AudioEffectSettings) {
            calls.add("audioEffects:${settings.enabled}")
        }

        override fun applyStreamingAudioQuality(quality: String) {
            calls.add("quality:$quality")
        }

        override fun applyShareStyle(style: String) {
            calls.add("shareStyle:$style")
        }

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
            calls.add("concurrent:$enabled")
        }

        override fun setStatusBarLyricsEnabled(enabled: Boolean) {
            calls.add("statusLyrics:$enabled")
        }

        override fun setFloatingLyricsEnabled(enabled: Boolean) {
            calls.add("floating:$enabled")
        }

        override fun openFloatingLyricsPermission() {
            calls.add("floatingPermission")
        }

        override fun setNowPlayingGesturesEnabled(enabled: Boolean) {
            calls.add("gestures:$enabled")
        }

        override fun setPlaybackRestoreEnabled(enabled: Boolean) {
            calls.add("restore:$enabled")
        }

        override fun setReplayGainEnabled(enabled: Boolean) {
            calls.add("replayGain:$enabled")
        }

        override fun exportBackup() {
            calls.add("exportBackup")
        }

        override fun importBackup() {
            calls.add("importBackup")
        }

        override fun applyThemeMode(mode: String) {
            calls.add("theme:$mode")
        }

        override fun applyAccentMode(accent: String) {
            calls.add("accent:$accent")
        }

        override fun applyLanguageMode(languageMode: String) {
            calls.add("language:$languageMode")
        }

        override fun applyStreamingGatewayEndpoint(endpoint: String) {
            calls.add("endpoint:$endpoint")
        }
    }

    private class FakeContentSink : SettingsPageEventController.ContentSink
}
