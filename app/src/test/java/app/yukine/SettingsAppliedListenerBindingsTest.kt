package app.yukine

import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.EchoTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAppliedListenerBindingsTest {
    @Test
    fun appliedSettingsUpdateStoreControlsAndUiCallbacks() {
        val settingsStore = MainSettingsStore()
        val calls = mutableListOf<String>()
        var selectedTab = MainRoutes.TAB_LIBRARY
        val playbackControls = object : SettingsPlaybackServiceControls {
            override fun setPlaybackSpeed(speed: Float) {
                calls += "speed:$speed"
            }

            override fun setAppVolume(volume: Float) {
                calls += "volume:$volume"
            }

            override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
                calls += "concurrentControl:$enabled"
            }

            override fun applyAudioEffectSettings(settings: AudioEffectSettings) {
                calls += "effectsControl:${settings.enabled}"
            }

            override fun setStatusBarLyricsEnabled(enabled: Boolean) {
                calls += "statusLyricsControl:$enabled"
            }

            override fun setPlaybackRestoreEnabled(enabled: Boolean) {
                calls += "restoreControl:$enabled"
            }

            override fun setReplayGainEnabled(enabled: Boolean) {
                calls += "replayGainControl:$enabled"
            }
        }
        val lyricsControls = object : SettingsLyricsControls {
            override fun setOnlineEnabled(enabled: Boolean) {
                calls += "lyricsOnline:$enabled"
            }

            override fun setOffsetMs(offsetMs: Long) {
                calls += "lyricsOffsetControl:$offsetMs"
            }
        }
        val floatingControls = object : SettingsFloatingLyricsControls {
            var allow = true

            override fun apply(enabled: Boolean): Boolean {
                calls += "floatingApply:$enabled"
                return allow
            }

            override fun openPermissionSettings() {
                calls += "floatingPermission"
            }
        }
        val listener = SettingsAppliedListenerBindings(
            settingsStore = settingsStore,
            applyThemeSurfaceAction = Runnable { calls += "themeSurface" },
            updateLanguageAction = SettingsLanguageUpdateAction { calls += "languageSurface:$it" },
            playbackServiceControlsProvider = SettingsPlaybackServiceControlsProvider { playbackControls },
            lyricsControlsProvider = SettingsLyricsControlsProvider { lyricsControls },
            floatingLyricsControlsProvider = SettingsFloatingLyricsControlsProvider { floatingControls },
            appliedStatusTextProvider = SettingsAppliedStatusTextProvider { offsetMs ->
                SettingsAppliedStatusText(
                    themeApplied = "themeStatus:$offsetMs",
                    accentApplied = "accentStatus:$offsetMs",
                    languageApplied = "languageStatus:$offsetMs",
                    playbackSpeedApplied = "speedStatus:$offsetMs",
                    appVolumeApplied = "volumeStatus:$offsetMs",
                    onlineLyricsEnabled = "online:on",
                    onlineLyricsDisabled = "online:off",
                    concurrentPlaybackEnabled = "concurrent:on",
                    concurrentPlaybackDisabled = "concurrent:off",
                    lyricsOffsetApplied = "lyricsStatus:$offsetMs",
                    audioEffectsApplied = "effectsStatus:$offsetMs",
                    statusBarLyricsEnabled = "statusLyrics:on",
                    statusBarLyricsDisabled = "statusLyrics:off",
                    floatingLyricsEnabled = "floating:on",
                    floatingLyricsDisabled = "floating:off",
                    floatingLyricsPermissionRequired = "floating:permission",
                    nowPlayingGesturesEnabled = "gestures:on",
                    nowPlayingGesturesDisabled = "gestures:off",
                    playbackRestoreEnabled = "restore:on",
                    playbackRestoreDisabled = "restore:off",
                    replayGainEnabled = "replayGain:on",
                    replayGainDisabled = "replayGain:off"
                )
            },
            streamingQualityAppliedStatusProvider = StreamingQualityAppliedStatusProvider { quality ->
                "qualityStatus:$quality"
            },
            statusSink = SettingsStatusSink { calls += "status:$it" },
            renderSelectedTabAction = Runnable { calls += "render" },
            renderNowBarAction = Runnable { calls += "nowbar" },
            reloadCurrentLyricsAction = Runnable { calls += "reloadLyrics" },
            selectedTabProvider = SettingsSelectedTabProvider { selectedTab }
        )

        listener.onThemeModeApplied(EchoTheme.MODE_DARK)
        listener.onAccentModeApplied(EchoTheme.ACCENT_TEAL)
        listener.onLanguageModeApplied(AppLanguage.MODE_ENGLISH)
        listener.onPlaybackSpeedApplied(1.5f)
        listener.onAppVolumeApplied(0.7f)
        listener.onStreamingAudioQualityApplied(StreamingQualityPreference.HIRES)
        listener.onOnlineLyricsEnabledApplied(true)
        listener.onOnlineLyricsEnabledApplied(false)
        listener.onConcurrentPlaybackEnabledApplied(true)
        listener.onConcurrentPlaybackEnabledApplied(false)
        listener.onAudioEffectSettingsApplied(AudioEffectSettings.DEFAULT.withEnabled(true))
        listener.onStatusBarLyricsEnabledApplied(false)
        listener.onFloatingLyricsEnabledApplied(true)
        floatingControls.allow = false
        listener.onFloatingLyricsEnabledApplied(true)
        listener.onFloatingLyricsPermissionRequested()
        listener.onFloatingLyricsEnabledApplied(false)
        listener.onNowPlayingGesturesEnabledApplied(false)
        listener.onPlaybackRestoreEnabledApplied(true)
        listener.onReplayGainEnabledApplied(false)
        listener.onLyricsOffsetApplied(250L)
        selectedTab = MainRoutes.TAB_NOW
        listener.onLyricsOffsetApplied(500L)

        assertEquals(EchoTheme.MODE_DARK, settingsStore.themeMode())
        assertEquals(EchoTheme.ACCENT_TEAL, settingsStore.accentMode())
        assertEquals(AppLanguage.MODE_ENGLISH, settingsStore.languageMode())
        assertEquals(1.5f, settingsStore.playbackSpeed(), 0.0f)
        assertEquals(0.7f, settingsStore.appVolume(), 0.0f)
        assertEquals(StreamingQualityPreference.HIRES, settingsStore.streamingAudioQuality())
        assertEquals(false, settingsStore.concurrentPlaybackEnabled())
        assertEquals(true, settingsStore.audioEffectSettings().enabled)
        assertEquals(false, settingsStore.statusBarLyricsEnabled())
        assertEquals(false, settingsStore.floatingLyricsEnabled())
        assertEquals(false, settingsStore.nowPlayingGesturesEnabled())
        assertEquals(true, settingsStore.playbackRestoreEnabled())
        assertEquals(false, settingsStore.replayGainEnabled())
        assertEquals(
            listOf(
                "themeSurface",
                "render",
                "nowbar",
                "status:themeStatus:0",
                "render",
                "nowbar",
                "status:accentStatus:0",
                "languageSurface:en",
                "render",
                "nowbar",
                "status:languageStatus:0",
                "speed:1.5",
                "render",
                "nowbar",
                "status:speedStatus:0",
                "volume:0.7",
                "render",
                "nowbar",
                "status:volumeStatus:0",
                "render",
                "status:qualityStatus:hires",
                "lyricsOnline:true",
                "status:online:on",
                "reloadLyrics",
                "render",
                "lyricsOnline:false",
                "status:online:off",
                "reloadLyrics",
                "render",
                "concurrentControl:true",
                "status:concurrent:on",
                "render",
                "concurrentControl:false",
                "status:concurrent:off",
                "render",
                "effectsControl:true",
                "status:effectsStatus:0",
                "render",
                "nowbar",
                "statusLyricsControl:false",
                "status:statusLyrics:off",
                "render",
                "nowbar",
                "floatingApply:true",
                "status:floating:on",
                "render",
                "nowbar",
                "floatingApply:true",
                "status:floating:permission",
                "render",
                "nowbar",
                "floatingPermission",
                "status:floating:permission",
                "floatingApply:false",
                "status:floating:off",
                "render",
                "nowbar",
                "status:gestures:off",
                "render",
                "nowbar",
                "restoreControl:true",
                "status:restore:on",
                "render",
                "replayGainControl:false",
                "status:replayGain:off",
                "render",
                "nowbar",
                "lyricsOffsetControl:250",
                "status:lyricsStatus:250",
                "render",
                "lyricsOffsetControl:500",
                "status:lyricsStatus:500",
                "render",
                "nowbar"
            ),
            calls
        )
    }
}
