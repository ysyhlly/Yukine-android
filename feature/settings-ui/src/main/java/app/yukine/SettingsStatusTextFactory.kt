package app.yukine

/** Stateless localization for settings command feedback. */
object SettingsStatusTextFactory {
    fun applied(
        languageMode: String,
        themeMode: String,
        accentMode: String,
        playbackSpeed: Float,
        appVolume: Float,
        lyricsOffsetMs: Long
    ): SettingsAppliedStatusText {
        val language = AppLanguage.normalizeMode(languageMode)
        return SettingsAppliedStatusText(
            themeApplied = AppLanguage.text(language, "theme.applied") +
                AppLanguage.themeLabel(themeMode, language),
            accentApplied = AppLanguage.text(language, "accent.applied") +
                AppLanguage.accentLabel(accentMode, language),
            languageApplied = AppLanguage.text(language, "language.applied") +
                AppLanguage.labelFor(language),
            playbackSpeedApplied = AppLanguage.text(language, "speed.applied") +
                SettingsLabelFormatter.playbackSpeedLabel(playbackSpeed),
            appVolumeApplied = AppLanguage.text(language, "volume.applied") +
                SettingsLabelFormatter.appVolumeLabel(appVolume),
            onlineLyricsEnabled = AppLanguage.text(language, "online.lyrics.enabled"),
            onlineLyricsDisabled = AppLanguage.text(language, "online.lyrics.disabled"),
            concurrentPlaybackEnabled = AppLanguage.text(language, "concurrent.playback.enabled"),
            concurrentPlaybackDisabled = AppLanguage.text(language, "concurrent.playback.disabled"),
            lyricsOffsetApplied = AppLanguage.text(language, "lyrics.offset.applied") +
                SettingsLabelFormatter.lyricsOffsetLabel(lyricsOffsetMs),
            audioEffectsApplied = AppLanguage.text(language, "audio.effects.applied"),
            statusBarLyricsEnabled = AppLanguage.text(language, "status.bar.lyrics.enabled"),
            statusBarLyricsDisabled = AppLanguage.text(language, "status.bar.lyrics.disabled"),
            floatingLyricsEnabled = AppLanguage.text(language, "floating.lyrics.enabled"),
            floatingLyricsDisabled = AppLanguage.text(language, "floating.lyrics.disabled"),
            floatingLyricsPermissionRequired = AppLanguage.text(language, "floating.lyrics.permission.required"),
            nowPlayingGesturesEnabled = AppLanguage.text(language, "now.playing.gestures.enabled"),
            nowPlayingGesturesDisabled = AppLanguage.text(language, "now.playing.gestures.disabled"),
            playbackRestoreEnabled = AppLanguage.text(language, "playback.restore.enabled"),
            playbackRestoreDisabled = AppLanguage.text(language, "playback.restore.disabled"),
            replayGainEnabled = AppLanguage.text(language, "replay.gain.enabled"),
            replayGainDisabled = AppLanguage.text(language, "replay.gain.disabled"),
            shareStyleApplied = AppLanguage.text(language, "share.style.applied"),
            pageBackgroundApplied = AppLanguage.text(language, "page.background.applied"),
            pageBackgroundCleared = AppLanguage.text(language, "page.background.cleared")
        )
    }
}
