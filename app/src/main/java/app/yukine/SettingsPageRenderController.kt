package app.yukine

import app.yukine.ui.EchoTheme
import app.yukine.playback.AudioEffectSettings
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsMetric
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

internal class SettingsPageRenderController(
    private val viewModel: SettingsViewModel,
    private val listener: Listener
) {
    private val scrollState = SettingsListScrollState()

    interface Listener {
        fun navigateSettingsPage(page: String)

        fun openNetworkSources()

        fun loadLibrary()

        fun openAudioFilePicker()

        fun openAudioFolderPicker()

        fun setOnlineLyricsEnabled(enabled: Boolean)

        fun reloadCurrentLyrics()

        fun applyLyricsOffset(offsetMs: Long)

        fun startSleepTimer(minutes: Int)

        fun cancelSleepTimer()

        fun applyPlaybackSpeed(speed: Float)

        fun applyAppVolume(volume: Float)

        fun applyStreamingAudioQuality(quality: String)

        fun setConcurrentPlaybackEnabled(enabled: Boolean)

        fun applyAudioEffectSettings(settings: AudioEffectSettings)

        fun setStatusBarLyricsEnabled(enabled: Boolean)

        fun setFloatingLyricsEnabled(enabled: Boolean)

        fun openFloatingLyricsPermission()

        fun setNowPlayingGesturesEnabled(enabled: Boolean)

        fun setPlaybackRestoreEnabled(enabled: Boolean)

        fun setReplayGainEnabled(enabled: Boolean)

        fun applyThemeMode(mode: String)

        fun applyAccentMode(accent: String)

        fun applyLanguageMode(languageMode: String)

        fun applyStreamingGatewayEndpoint(endpoint: String)

        fun publishSettingsChrome(
            actions: List<SettingsAction>,
            scrollState: SettingsListScrollState
        )
    }

    fun scrollToTopOnNextRender() {
        scrollState.scrollToTop()
    }

    private fun renderSettingsScreen(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        viewModel.updatePage(title, metrics, actions)
        listener.publishSettingsChrome(actions, scrollState)
    }

    fun renderHome(
        themeMode: String,
        accentMode: String,
        languageMode: String,
        audioPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        playbackServiceConnected: Boolean
    ) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "audio.permission"), if (audioPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "notification.permission"), if (notificationPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected")))
        val actions = ArrayList<SettingsAction>()
        addGroupNavigationAction(actions, languageMode, "appearance", MainRoutes.SETTINGS_APPEARANCE_GROUP)
        addGroupNavigationAction(actions, languageMode, "playback", MainRoutes.SETTINGS_PLAYBACK_GROUP)
        addGroupNavigationAction(actions, languageMode, "library", MainRoutes.SETTINGS_LIBRARY_GROUP)
        addGroupNavigationAction(actions, languageMode, "lyrics", MainRoutes.SETTINGS_LYRICS_GROUP)
        addGroupNavigationAction(actions, languageMode, "sources", MainRoutes.SETTINGS_SOURCES_GROUP)
        addGroupNavigationAction(actions, languageMode, "about", MainRoutes.SETTINGS_ABOUT_GROUP)
        renderSettingsScreen(text(languageMode, "tab.settings"), metrics, actions)
    }

    fun renderAppearanceGroup(languageMode: String, themeMode: String, accentMode: String) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), groupDescription(languageMode, "appearance")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode)
        addNavigationAction(actions, text(languageMode, "appearance"), MainRoutes.SETTINGS_APPEARANCE)
        addNavigationAction(actions, text(languageMode, "accent"), MainRoutes.SETTINGS_ACCENT)
        addNavigationAction(actions, text(languageMode, "language"), MainRoutes.SETTINGS_LANGUAGE)
        renderSettingsScreen(groupTitle(languageMode, "appearance"), metrics, actions)
    }

    fun renderPlaybackGroup(
        languageMode: String,
        playbackSpeed: Float,
        appVolume: Float,
        concurrentPlaybackEnabled: Boolean,
        audioEffects: AudioEffectSettings,
        nowPlayingGesturesEnabled: Boolean,
        playbackRestoreEnabled: Boolean,
        replayGainEnabled: Boolean,
        remainingMs: Long
    ) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "playback.speed"), playbackSpeedLabel(playbackSpeed)))
        metrics.add(SettingsMetric(text(languageMode, "app.volume"), appVolumeLabel(appVolume)))
        metrics.add(SettingsMetric(text(languageMode, "audio.effects"), audioEffectsLabel(audioEffects, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "replay.gain"), enabledLabel(replayGainEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "now.playing.gestures"), enabledLabel(nowPlayingGesturesEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "playback.restore"), enabledLabel(playbackRestoreEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "concurrent.playback"), if (concurrentPlaybackEnabled) text(languageMode, "enabled") else text(languageMode, "disabled")))
        metrics.add(SettingsMetric(text(languageMode, "sleep.timer"), sleepTimerLabel(remainingMs, languageMode)))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode)
        addNavigationAction(actions, text(languageMode, "playback.speed"), MainRoutes.SETTINGS_PLAYBACK_SPEED)
        addNavigationAction(actions, text(languageMode, "app.volume"), MainRoutes.SETTINGS_APP_VOLUME)
        addNavigationAction(actions, text(languageMode, "audio.effects"), MainRoutes.SETTINGS_AUDIO_EFFECTS)
        addNavigationAction(actions, text(languageMode, "replay.gain"), MainRoutes.SETTINGS_REPLAY_GAIN)
        addNavigationAction(actions, text(languageMode, "now.playing.gestures"), MainRoutes.SETTINGS_NOW_PLAYING_GESTURES)
        addNavigationAction(actions, text(languageMode, "playback.restore"), MainRoutes.SETTINGS_PLAYBACK_RESTORE)
        addNavigationAction(actions, text(languageMode, "concurrent.playback"), MainRoutes.SETTINGS_CONCURRENT_PLAYBACK)
        addNavigationAction(actions, text(languageMode, "sleep.timer"), MainRoutes.SETTINGS_SLEEP_TIMER)
        renderSettingsScreen(groupTitle(languageMode, "playback"), metrics, actions)
    }

    fun renderLibraryGroup(languageMode: String, songCount: Int, albumCount: Int, artistCount: Int, audioPermissionGranted: Boolean) {
        renderLibrary(languageMode, songCount, albumCount, artistCount, audioPermissionGranted)
    }

    fun renderLyricsGroup(
        languageMode: String,
        offsetMs: Long,
        onlineLyricsEnabled: Boolean,
        statusBarLyricsEnabled: Boolean,
        floatingLyricsEnabled: Boolean,
        overlayPermissionGranted: Boolean
    ) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "offset"), lyricsOffsetLabel(offsetMs)))
        metrics.add(SettingsMetric(text(languageMode, "online.lyrics"), if (onlineLyricsEnabled) text(languageMode, "enabled") else text(languageMode, "disabled")))
        metrics.add(SettingsMetric(text(languageMode, "status.bar.lyrics"), enabledLabel(statusBarLyricsEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(floatingLyricsEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "overlay.permission"), if (overlayPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "provider"), "LRCLIB"))
        metrics.add(SettingsMetric(text(languageMode, "local.lyrics"), text(languageMode, "same.name.lrc")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode)
        actions.add(
            SettingsAction(
                if (onlineLyricsEnabled) text(languageMode, "disable.online.lyrics") else text(languageMode, "enable.online.lyrics"),
                Runnable { listener.setOnlineLyricsEnabled(!onlineLyricsEnabled) }
            )
        )
        actions.add(SettingsAction(text(languageMode, "reload.lyrics"), Runnable { listener.reloadCurrentLyrics() }))
        addNavigationAction(actions, text(languageMode, "status.bar.lyrics"), MainRoutes.SETTINGS_STATUS_BAR_LYRICS)
        addNavigationAction(actions, text(languageMode, "floating.lyrics"), MainRoutes.SETTINGS_FLOATING_LYRICS)
        addLyricsOffsetOption(actions, languageMode, offsetMs, -1000L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, -500L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 0L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 500L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 1000L)
        renderSettingsScreen(groupTitle(languageMode, "lyrics"), metrics, actions)
    }

    fun renderSourcesGroup(languageMode: String, quality: String, gatewayConfigured: Boolean) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "streaming.gateway"), if (gatewayConfigured) text(languageMode, "connected") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "description"), groupDescription(languageMode, "sources")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode)
        actions.add(SettingsAction(text(languageMode, "remote.music.sources"), Runnable { listener.openNetworkSources() }))
        addNavigationAction(actions, text(languageMode, "streaming.audio.quality"), MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY)
        addNavigationAction(actions, text(languageMode, "advanced") + " · " + text(languageMode, "streaming.gateway"), MainRoutes.SETTINGS_STREAMING_GATEWAY)
        renderSettingsScreen(groupTitle(languageMode, "sources"), metrics, actions)
    }

    fun renderAboutGroup(languageMode: String, audioPermissionGranted: Boolean, notificationPermissionGranted: Boolean, playbackServiceConnected: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "version"), "0.1.0"))
        metrics.add(SettingsMetric(text(languageMode, "audio.permission"), if (audioPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "notification.permission"), if (notificationPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "playback.service"), if (playbackServiceConnected) text(languageMode, "connected") else text(languageMode, "disconnected")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode)
        renderSettingsScreen(groupTitle(languageMode, "about"), metrics, actions)
    }

    fun renderStreamingGateway(languageMode: String, endpoint: String, configured: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "streaming.gateway"), if (configured) text(languageMode, "connected") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "endpoint"), endpoint))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.gateway.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_STREAMING_GATEWAY)
        addStreamingGatewayOption(actions, languageMode, endpoint, StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT, "streaming.gateway.emulator")
        addStreamingGatewayOption(actions, languageMode, endpoint, StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT, "streaming.gateway.localhost")
        addStreamingGatewayOption(actions, languageMode, endpoint, StreamingGatewaySettingsStore.UNCONFIGURED_ENDPOINT, "disable")
        renderSettingsScreen(text(languageMode, "streaming.gateway"), metrics, actions)
    }

    fun renderLibrary(languageMode: String, songCount: Int, albumCount: Int, artistCount: Int, audioPermissionGranted: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "songs"), songCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "albums"), albumCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "artists"), artistCount.toString()))
        metrics.add(SettingsMetric(text(languageMode, "audio.permission"), if (audioPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_LIBRARY)
        actions.add(SettingsAction(text(languageMode, "scan.library"), Runnable { listener.loadLibrary() }))
        actions.add(SettingsAction(text(languageMode, "import.audio.files"), Runnable { listener.openAudioFilePicker() }))
        actions.add(SettingsAction(text(languageMode, "import.audio.folder"), Runnable { listener.openAudioFolderPicker() }))
        renderSettingsScreen(text(languageMode, "library"), metrics, actions)
    }

    fun renderLyrics(
        languageMode: String,
        offsetMs: Long,
        onlineLyricsEnabled: Boolean,
        statusBarLyricsEnabled: Boolean,
        floatingLyricsEnabled: Boolean,
        overlayPermissionGranted: Boolean
    ) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "offset"), lyricsOffsetLabel(offsetMs)))
        metrics.add(SettingsMetric(text(languageMode, "online.lyrics"), if (onlineLyricsEnabled) text(languageMode, "enabled") else text(languageMode, "disabled")))
        metrics.add(SettingsMetric(text(languageMode, "status.bar.lyrics"), enabledLabel(statusBarLyricsEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(floatingLyricsEnabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "overlay.permission"), if (overlayPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "provider"), "LRCLIB"))
        metrics.add(SettingsMetric(text(languageMode, "local.lyrics"), text(languageMode, "same.name.lrc")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_LYRICS)
        actions.add(
            SettingsAction(
                if (onlineLyricsEnabled) text(languageMode, "disable.online.lyrics") else text(languageMode, "enable.online.lyrics"),
                Runnable { listener.setOnlineLyricsEnabled(!onlineLyricsEnabled) }
            )
        )
        actions.add(SettingsAction(text(languageMode, "reload.lyrics"), Runnable { listener.reloadCurrentLyrics() }))
        addNavigationAction(actions, text(languageMode, "status.bar.lyrics"), MainRoutes.SETTINGS_STATUS_BAR_LYRICS)
        addNavigationAction(actions, text(languageMode, "floating.lyrics"), MainRoutes.SETTINGS_FLOATING_LYRICS)
        addLyricsOffsetOption(actions, languageMode, offsetMs, -1000L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, -500L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 0L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 500L)
        addLyricsOffsetOption(actions, languageMode, offsetMs, 1000L)
        renderSettingsScreen(text(languageMode, "lyrics"), metrics, actions)
    }

    fun renderSleepTimer(languageMode: String, remainingMs: Long) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "sleep.timer"), sleepTimerLabel(remainingMs, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "sleep.timer.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_SLEEP_TIMER)
        addSleepTimerOption(actions, languageMode, 15)
        addSleepTimerOption(actions, languageMode, 30)
        addSleepTimerOption(actions, languageMode, 45)
        addSleepTimerOption(actions, languageMode, 60)
        addSleepTimerOption(actions, languageMode, 90)
        actions.add(SettingsAction(text(languageMode, "cancel.sleep.timer"), Runnable { listener.cancelSleepTimer() }))
        renderSettingsScreen(text(languageMode, "sleep.timer"), metrics, actions)
    }

    fun renderPlaybackSpeed(languageMode: String, playbackSpeed: Float) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "playback.speed"), playbackSpeedLabel(playbackSpeed)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "speed.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_PLAYBACK_SPEED)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 0.5f)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 0.75f)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 1.0f)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 1.25f)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 1.5f)
        addPlaybackSpeedOption(actions, languageMode, playbackSpeed, 2.0f)
        renderSettingsScreen(text(languageMode, "playback.speed"), metrics, actions)
    }

    fun renderAppVolume(languageMode: String, appVolume: Float) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "app.volume"), appVolumeLabel(appVolume)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "volume.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_APP_VOLUME)
        addAppVolumeOption(actions, languageMode, appVolume, 0.5f)
        addAppVolumeOption(actions, languageMode, appVolume, 0.7f)
        addAppVolumeOption(actions, languageMode, appVolume, 0.85f)
        addAppVolumeOption(actions, languageMode, appVolume, 1.0f)
        renderSettingsScreen(text(languageMode, "app.volume"), metrics, actions)
    }

    fun renderStreamingAudioQuality(languageMode: String, quality: String) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "streaming.audio.quality"), streamingQualityLabel(normalizedQuality, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "streaming.quality.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY)
        StreamingQualityPreference.options().forEach { option ->
            addStreamingQualityOption(actions, languageMode, normalizedQuality, option)
        }
        renderSettingsScreen(text(languageMode, "streaming.audio.quality"), metrics, actions)
    }

    fun renderConcurrentPlayback(languageMode: String, concurrentPlaybackEnabled: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "concurrent.playback"), if (concurrentPlaybackEnabled) text(languageMode, "enabled") else text(languageMode, "disabled")))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "concurrent.playback.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_CONCURRENT_PLAYBACK)
        actions.add(
            SettingsAction(
                if (concurrentPlaybackEnabled) text(languageMode, "disable.concurrent.playback") else text(languageMode, "enable.concurrent.playback"),
                Runnable { listener.setConcurrentPlaybackEnabled(!concurrentPlaybackEnabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "concurrent.playback"), metrics, actions)
    }

    fun renderAudioEffects(languageMode: String, settings: AudioEffectSettings) {
        val effects = settings
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "audio.effects"), audioEffectsLabel(effects, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "equalizer.preset"), equalizerPresetLabel(effects.preset, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "bass.boost"), strengthLabel(effects.bassBoostStrength)))
        metrics.add(SettingsMetric(text(languageMode, "virtualizer"), strengthLabel(effects.virtualizerStrength)))
        metrics.add(SettingsMetric(text(languageMode, "loudness"), loudnessLabel(effects.loudnessGainMb)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "audio.effects.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_AUDIO_EFFECTS)
        actions.add(SettingsAction(
            if (effects.enabled) text(languageMode, "disable.audio.effects") else text(languageMode, "enable.audio.effects"),
            Runnable { listener.applyAudioEffectSettings(effects.withEnabled(!effects.enabled)) }
        ))
        addEqualizerPresetOption(actions, languageMode, effects, AudioEffectSettings.PRESET_CUSTOM)
        addEqualizerPresetOption(actions, languageMode, effects, 0)
        addEqualizerPresetOption(actions, languageMode, effects, 1)
        addEqualizerPresetOption(actions, languageMode, effects, 2)
        addStrengthOption(actions, languageMode, effects, "bass.boost", "bass", 0)
        addStrengthOption(actions, languageMode, effects, "bass.boost", "bass", 500)
        addStrengthOption(actions, languageMode, effects, "bass.boost", "bass", 1000)
        addStrengthOption(actions, languageMode, effects, "virtualizer", "virtualizer", 0)
        addStrengthOption(actions, languageMode, effects, "virtualizer", "virtualizer", 500)
        addStrengthOption(actions, languageMode, effects, "virtualizer", "virtualizer", 1000)
        addLoudnessOption(actions, languageMode, effects, 0)
        addLoudnessOption(actions, languageMode, effects, 300)
        addLoudnessOption(actions, languageMode, effects, 600)
        renderSettingsScreen(text(languageMode, "audio.effects"), metrics, actions)
    }

    fun renderStatusBarLyrics(languageMode: String, enabled: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "status.bar.lyrics"), enabledLabel(enabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "status.bar.lyrics.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_STATUS_BAR_LYRICS)
        actions.add(
            SettingsAction(
                if (enabled) text(languageMode, "disable.status.bar.lyrics") else text(languageMode, "enable.status.bar.lyrics"),
                Runnable { listener.setStatusBarLyricsEnabled(!enabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "status.bar.lyrics"), metrics, actions)
    }

    fun renderFloatingLyrics(languageMode: String, enabled: Boolean, overlayPermissionGranted: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "floating.lyrics"), enabledLabel(enabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "overlay.permission"), if (overlayPermissionGranted) text(languageMode, "granted") else text(languageMode, "missing")))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "floating.lyrics.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_FLOATING_LYRICS)
        if (!overlayPermissionGranted) {
            actions.add(SettingsAction(text(languageMode, "grant.overlay.permission"), Runnable { listener.openFloatingLyricsPermission() }))
        }
        actions.add(
            SettingsAction(
                if (enabled) text(languageMode, "disable.floating.lyrics") else text(languageMode, "enable.floating.lyrics"),
                Runnable { listener.setFloatingLyricsEnabled(!enabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "floating.lyrics"), metrics, actions)
    }

    fun renderNowPlayingGestures(languageMode: String, enabled: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "now.playing.gestures"), enabledLabel(enabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "now.playing.gestures.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_NOW_PLAYING_GESTURES)
        actions.add(
            SettingsAction(
                if (enabled) text(languageMode, "disable.now.playing.gestures") else text(languageMode, "enable.now.playing.gestures"),
                Runnable { listener.setNowPlayingGesturesEnabled(!enabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "now.playing.gestures"), metrics, actions)
    }

    fun renderPlaybackRestore(languageMode: String, enabled: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "playback.restore"), enabledLabel(enabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "playback.restore.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_PLAYBACK_RESTORE)
        actions.add(
            SettingsAction(
                if (enabled) text(languageMode, "disable.playback.restore") else text(languageMode, "enable.playback.restore"),
                Runnable { listener.setPlaybackRestoreEnabled(!enabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "playback.restore"), metrics, actions)
    }

    fun renderReplayGain(languageMode: String, enabled: Boolean) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "replay.gain"), enabledLabel(enabled, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "replay.gain.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_REPLAY_GAIN)
        actions.add(
            SettingsAction(
                if (enabled) text(languageMode, "disable.replay.gain") else text(languageMode, "enable.replay.gain"),
                Runnable { listener.setReplayGainEnabled(!enabled) }
            )
        )
        renderSettingsScreen(text(languageMode, "replay.gain"), metrics, actions)
    }

    fun renderTheme(languageMode: String, themeMode: String) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "options"), text(languageMode, "theme.options")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_APPEARANCE)
        EchoTheme.primaryModeOptions().forEach { mode ->
            addThemeOption(actions, languageMode, themeMode, mode)
        }
        if (EchoTheme.advancedModeOptions().isNotEmpty()) {
            actions.add(
                SettingsAction(
                    text(languageMode, "advanced.themes"),
                    Runnable { listener.navigateSettingsPage(MainRoutes.SETTINGS_ADVANCED_THEME) },
                    text(languageMode, "advanced.themes.description")
                )
            )
        }
        renderSettingsScreen(text(languageMode, "appearance"), metrics, actions)
    }

    fun renderAdvancedTheme(languageMode: String, themeMode: String) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "theme"), AppLanguage.themeLabel(themeMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "description"), text(languageMode, "advanced.themes.description")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_ADVANCED_THEME)
        EchoTheme.advancedModeOptions().forEach { mode ->
            addThemeOption(actions, languageMode, themeMode, mode)
        }
        renderSettingsScreen(text(languageMode, "advanced.themes"), metrics, actions)
    }

    fun renderAccent(languageMode: String, accentMode: String) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "accent"), AppLanguage.accentLabel(accentMode, languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "options"), text(languageMode, "accent.options")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_ACCENT)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_BLUE)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_TEAL)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_ROSE)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_VIOLET)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_AMBER)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_EMERALD)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_CYAN)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_LIME)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_RED)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_INDIGO)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_PINE)
        addAccentOption(actions, languageMode, accentMode, EchoTheme.ACCENT_PEACH)
        renderSettingsScreen(text(languageMode, "accent"), metrics, actions)
    }

    fun renderLanguage(languageMode: String) {
        val metrics = ArrayList<SettingsMetric>()
        metrics.add(SettingsMetric(text(languageMode, "language"), AppLanguage.labelFor(languageMode)))
        metrics.add(SettingsMetric(text(languageMode, "options"), text(languageMode, "language.options")))
        val actions = ArrayList<SettingsAction>()
        addBackAction(actions, languageMode, MainRoutes.SETTINGS_LANGUAGE)
        addLanguageOption(actions, languageMode, AppLanguage.MODE_SYSTEM)
        addLanguageOption(actions, languageMode, AppLanguage.MODE_CHINESE)
        addLanguageOption(actions, languageMode, AppLanguage.MODE_ENGLISH)
        renderSettingsScreen(text(languageMode, "language"), metrics, actions)
    }

    private fun addNavigationAction(actions: ArrayList<SettingsAction>, label: String, page: String) {
        actions.add(SettingsAction(label, Runnable { listener.navigateSettingsPage(page) }))
    }

    private fun addGroupNavigationAction(actions: ArrayList<SettingsAction>, languageMode: String, key: String, page: String) {
        actions.add(SettingsAction(groupTitle(languageMode, key), Runnable { listener.navigateSettingsPage(page) }, groupDescription(languageMode, key)))
    }

    private fun groupTitle(languageMode: String, key: String): String =
        text(languageMode, "settings.group.$key")

    private fun groupDescription(languageMode: String, key: String): String =
        text(languageMode, "settings.group.$key.description")

    private fun addBackAction(actions: ArrayList<SettingsAction>, languageMode: String) {
        addNavigationAction(actions, text(languageMode, "back"), MainRoutes.SETTINGS_HOME)
    }

    private fun addBackAction(actions: ArrayList<SettingsAction>, languageMode: String, currentPage: String) {
        addNavigationAction(actions, text(languageMode, "back"), SettingsBackStack.parentPage(currentPage))
    }

    private fun addLyricsOffsetOption(actions: ArrayList<SettingsAction>, languageMode: String, currentOffsetMs: Long, offsetMs: Long) {
        var label = lyricsOffsetLabel(offsetMs)
        if (normalizeLyricsOffsetMs(currentOffsetMs) == normalizeLyricsOffsetMs(offsetMs)) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyLyricsOffset(offsetMs) }))
    }

    private fun addSleepTimerOption(actions: ArrayList<SettingsAction>, languageMode: String, minutes: Int) {
        actions.add(SettingsAction(minutes.toString() + text(languageMode, "min"), Runnable { listener.startSleepTimer(minutes) }))
    }

    private fun addPlaybackSpeedOption(actions: ArrayList<SettingsAction>, languageMode: String, currentSpeed: Float, speed: Float) {
        var label = playbackSpeedLabel(speed)
        if (abs(normalizePlaybackSpeed(currentSpeed) - normalizePlaybackSpeed(speed)) < 0.01f) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyPlaybackSpeed(speed) }))
    }

    private fun addAppVolumeOption(actions: ArrayList<SettingsAction>, languageMode: String, currentVolume: Float, volume: Float) {
        var label = appVolumeLabel(volume)
        if (abs(normalizeAppVolume(currentVolume) - normalizeAppVolume(volume)) < 0.01f) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyAppVolume(volume) }))
    }

    private fun addStreamingQualityOption(actions: ArrayList<SettingsAction>, languageMode: String, currentQuality: String, quality: String) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        var label = streamingQualityLabel(normalizedQuality, languageMode)
        if (StreamingQualityPreference.normalize(currentQuality) == normalizedQuality) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyStreamingAudioQuality(normalizedQuality) }))
    }

    private fun addEqualizerPresetOption(actions: ArrayList<SettingsAction>, languageMode: String, settings: AudioEffectSettings, preset: Int) {
        var label = equalizerPresetLabel(preset, languageMode)
        if (settings.preset == preset) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyAudioEffectSettings(settings.withEnabled(true).withPreset(preset)) }))
    }

    private fun addStrengthOption(actions: ArrayList<SettingsAction>, languageMode: String, settings: AudioEffectSettings, labelKey: String, target: String, strength: Int) {
        val selected = if (target == "bass") settings.bassBoostStrength.toInt() == strength else settings.virtualizerStrength.toInt() == strength
        var label = text(languageMode, labelKey) + " " + strengthLabel(strength.toShort())
        if (selected) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable {
            val next = if (target == "bass") {
                settings.withEnabled(true).withBassBoostStrength(strength.toShort())
            } else {
                settings.withEnabled(true).withVirtualizerStrength(strength.toShort())
            }
            listener.applyAudioEffectSettings(next)
        }))
    }

    private fun addLoudnessOption(actions: ArrayList<SettingsAction>, languageMode: String, settings: AudioEffectSettings, gainMb: Int) {
        var label = text(languageMode, "loudness") + " " + loudnessLabel(gainMb)
        if (settings.loudnessGainMb == gainMb) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyAudioEffectSettings(settings.withEnabled(true).withLoudnessGainMb(gainMb)) }))
    }

    private fun addThemeOption(actions: ArrayList<SettingsAction>, languageMode: String, currentMode: String, mode: String) {
        var label = AppLanguage.themeLabel(mode, languageMode)
        if (EchoTheme.normalizeMode(currentMode) == EchoTheme.normalizeMode(mode)) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyThemeMode(mode) }))
    }

    private fun addAccentOption(actions: ArrayList<SettingsAction>, languageMode: String, currentAccent: String, accent: String) {
        var label = AppLanguage.accentLabel(accent, languageMode)
        if (EchoTheme.normalizeAccent(currentAccent) == EchoTheme.normalizeAccent(accent)) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyAccentMode(accent) }))
    }

    private fun addLanguageOption(actions: ArrayList<SettingsAction>, languageMode: String, optionMode: String) {
        var label = languageOptionLabel(languageMode, optionMode)
        if (AppLanguage.normalizeMode(languageMode) == AppLanguage.normalizeMode(optionMode)) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyLanguageMode(optionMode) }))
    }

    private fun addStreamingGatewayOption(actions: ArrayList<SettingsAction>, languageMode: String, currentEndpoint: String, endpoint: String, labelKey: String) {
        var label = text(languageMode, labelKey)
        if (StreamingGatewaySettingsStore.normalize(currentEndpoint) == StreamingGatewaySettingsStore.normalize(endpoint)) {
            label += text(languageMode, "selected")
        }
        actions.add(SettingsAction(label, Runnable { listener.applyStreamingGatewayEndpoint(endpoint) }))
    }

    companion object {
        @JvmStatic
        fun playbackSpeedLabel(speed: Float): String {
            val normalized = normalizePlaybackSpeed(speed)
            if (abs(normalized - round(normalized)) < 0.01f) {
                return round(normalized).toInt().toString() + "x"
            }
            return String.format(Locale.ROOT, "%.2fx", normalized).replace(Regex("0x$"), "x")
        }

        @JvmStatic
        fun appVolumeLabel(volume: Float): String =
            (normalizeAppVolume(volume) * 100.0f).roundToInt().toString() + "%"

        @JvmStatic
        fun streamingQualityLabel(quality: String, languageMode: String): String {
            return when (StreamingQualityPreference.normalize(quality)) {
                StreamingQualityPreference.AUTO -> text(languageMode, "quality.auto")
                StreamingQualityPreference.STANDARD -> text(languageMode, "quality.standard")
                StreamingQualityPreference.HIGH -> text(languageMode, "quality.high")
                StreamingQualityPreference.LOSSLESS -> text(languageMode, "quality.lossless")
                StreamingQualityPreference.HIRES -> text(languageMode, "quality.hires")
                else -> text(languageMode, "quality.high")
            }
        }

        @JvmStatic
        fun audioEffectsLabel(settings: AudioEffectSettings?, languageMode: String): String {
            val effects = settings ?: AudioEffectSettings.DEFAULT
            if (!effects.enabled) {
                return text(languageMode, "off")
            }
            return text(languageMode, "enabled") + " / " + equalizerPresetLabel(effects.preset, languageMode)
        }

        @JvmStatic
        fun equalizerPresetLabel(preset: Int, languageMode: String): String {
            return when (preset) {
                AudioEffectSettings.PRESET_CUSTOM -> text(languageMode, "eq.custom")
                0 -> text(languageMode, "eq.normal")
                1 -> text(languageMode, "eq.classical")
                2 -> text(languageMode, "eq.dance")
                else -> text(languageMode, "eq.preset") + " " + preset
            }
        }

        private fun strengthLabel(strength: Short): String =
            (strength.coerceIn(0, 1000).toInt() / 10).toString() + "%"

        private fun loudnessLabel(gainMb: Int): String {
            if (gainMb == 0) {
                return "0 dB"
            }
            return String.format(Locale.ROOT, "%+.1f dB", gainMb / 100.0f)
        }

        private fun enabledLabel(enabled: Boolean, languageMode: String): String =
            if (enabled) text(languageMode, "enabled") else text(languageMode, "disabled")

        @JvmStatic
        fun lyricsOffsetLabel(offsetMs: Long): String {
            val normalized = normalizeLyricsOffsetMs(offsetMs)
            if (normalized == 0L) {
                return "0 ms"
            }
            val sign = if (normalized > 0L) "+" else "-"
            val absolute = abs(normalized)
            if (absolute % 1000L == 0L) {
                return sign + (absolute / 1000L) + " s"
            }
            return sign + String.format(Locale.ROOT, "%.1f", absolute / 1000.0) + " s"
        }

        private fun sleepTimerLabel(remainingMs: Long, languageMode: String): String {
            if (remainingMs <= 0L) {
                return text(languageMode, "off")
            }
            val minutes = max(1L, (remainingMs + 59999L) / 60000L)
            return minutes.toString() + text(languageMode, "min.left")
        }

        private fun languageOptionLabel(languageMode: String, optionMode: String): String {
            return when (AppLanguage.normalizeMode(optionMode)) {
                AppLanguage.MODE_CHINESE -> text(languageMode, "language.chinese")
                AppLanguage.MODE_ENGLISH -> text(languageMode, "language.english")
                else -> text(languageMode, "language.system")
            }
        }

        private fun text(languageMode: String, key: String): String =
            AppLanguage.text(languageMode, key)

        private fun normalizePlaybackSpeed(speed: Float): Float {
            if (speed < 0.5f) {
                return 0.5f
            }
            if (speed > 2.0f) {
                return 2.0f
            }
            return round(speed * 100.0f) / 100.0f
        }

        private fun normalizeAppVolume(volume: Float): Float {
            if (volume < 0.0f) {
                return 0.0f
            }
            if (volume > 1.0f) {
                return 1.0f
            }
            return round(volume * 100.0f) / 100.0f
        }

        private fun normalizeLyricsOffsetMs(offsetMs: Long): Long {
            if (offsetMs < -5000L) {
                return -5000L
            }
            if (offsetMs > 5000L) {
                return 5000L
            }
            return round(offsetMs / 100.0).toLong() * 100L
        }
    }
}
