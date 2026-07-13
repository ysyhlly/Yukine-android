package app.yukine.data;

import app.yukine.PageBackgrounds;
import app.yukine.TrackShareStyle;
import app.yukine.playback.AudioEffectSettings;

/** Temporary compatibility shell removed when MusicLibraryRepository switches to Room. */
final class EchoSettingsStore {
    private final EchoDatabaseHelper database;

    EchoSettingsStore(EchoDatabaseHelper database) { this.database = database; }

    String loadThemeMode() { return database.loadThemeMode(); }
    void saveThemeMode(String value) { database.saveThemeMode(value); }
    String loadAccentMode() { return database.loadAccentMode(); }
    void saveAccentMode(String value) { database.saveAccentMode(value); }
    String loadLanguageMode() { return database.loadLanguageMode(); }
    void saveLanguageMode(String value) { database.saveLanguageMode(value); }
    float loadPlaybackSpeed() { return database.loadPlaybackSpeed(); }
    void savePlaybackSpeed(float value) { database.savePlaybackSpeed(value); }
    float loadAppVolume() { return database.loadAppVolume(); }
    void saveAppVolume(float value) { database.saveAppVolume(value); }
    String loadStreamingAudioQuality() { return database.loadStreamingAudioQuality(); }
    void saveStreamingAudioQuality(String value) { database.saveStreamingAudioQuality(value); }
    boolean loadRefuseAutomaticQualityDowngrade() { return database.loadRefuseAutomaticQualityDowngrade(); }
    void saveRefuseAutomaticQualityDowngrade(boolean value) { database.saveRefuseAutomaticQualityDowngrade(value); }
    boolean loadOnlineLyricsEnabled() { return database.loadOnlineLyricsEnabled(); }
    void saveOnlineLyricsEnabled(boolean value) { database.saveOnlineLyricsEnabled(value); }
    boolean loadConcurrentPlaybackEnabled() { return database.loadConcurrentPlaybackEnabled(); }
    void saveConcurrentPlaybackEnabled(boolean value) { database.saveConcurrentPlaybackEnabled(value); }
    long loadLyricsOffsetMs() { return database.loadLyricsOffsetMs(); }
    void saveLyricsOffsetMs(long value) { database.saveLyricsOffsetMs(value); }
    boolean loadPlaybackResumeRequested() { return database.loadPlaybackResumeRequested(); }
    void savePlaybackResumeRequested(boolean value) { database.savePlaybackResumeRequested(value); }
    AudioEffectSettings loadAudioEffectSettings() { return database.loadAudioEffectSettings(); }
    void saveAudioEffectSettings(AudioEffectSettings value) { database.saveAudioEffectSettings(value); }
    boolean loadStatusBarLyricsEnabled() { return database.loadStatusBarLyricsEnabled(); }
    void saveStatusBarLyricsEnabled(boolean value) { database.saveStatusBarLyricsEnabled(value); }
    boolean loadSystemMediaLyricsTitleEnabled() { return database.loadSystemMediaLyricsTitleEnabled(); }
    void saveSystemMediaLyricsTitleEnabled(boolean value) { database.saveSystemMediaLyricsTitleEnabled(value); }
    boolean loadFloatingLyricsEnabled() { return database.loadFloatingLyricsEnabled(); }
    void saveFloatingLyricsEnabled(boolean value) { database.saveFloatingLyricsEnabled(value); }
    boolean loadNowPlayingGesturesEnabled() { return database.loadNowPlayingGesturesEnabled(); }
    void saveNowPlayingGesturesEnabled(boolean value) { database.saveNowPlayingGesturesEnabled(value); }
    boolean loadPlaybackRestoreEnabled() { return database.loadPlaybackRestoreEnabled(); }
    void savePlaybackRestoreEnabled(boolean value) { database.savePlaybackRestoreEnabled(value); }
    boolean loadReplayGainEnabled() { return database.loadReplayGainEnabled(); }
    void saveReplayGainEnabled(boolean value) { database.saveReplayGainEnabled(value); }
    boolean loadDebugPromptsEnabled() { return database.loadDebugPromptsEnabled(); }
    void saveDebugPromptsEnabled(boolean value) { database.saveDebugPromptsEnabled(value); }
    boolean loadCustomBackgroundBlurEnabled() { return database.loadCustomBackgroundBlurEnabled(); }
    float loadCustomBackgroundBlurRadiusDp() { return database.loadCustomBackgroundBlurRadiusDp(); }
    void saveCustomBackgroundBlurEnabled(boolean value) { database.saveCustomBackgroundBlurEnabled(value); }
    void saveCustomBackgroundBlurRadiusDp(float value) { database.saveCustomBackgroundBlurRadiusDp(value); }
    boolean loadGlassBlurEnabled() { return database.loadGlassBlurEnabled(); }
    float loadGlassBlurRadiusDp() { return database.loadGlassBlurRadiusDp(); }
    void saveGlassBlurEnabled(boolean value) { database.saveGlassBlurEnabled(value); }
    void saveGlassBlurRadiusDp(float value) { database.saveGlassBlurRadiusDp(value); }
    float loadGlassSurfaceOpacity() { return database.loadGlassSurfaceOpacity(); }
    void saveGlassSurfaceOpacity(float value) { database.saveGlassSurfaceOpacity(value); }
    String loadShareStyle() { return TrackShareStyle.normalize(database.loadShareStyle()); }
    void saveShareStyle(String value) { database.saveShareStyle(TrackShareStyle.normalize(value)); }
    PageBackgrounds loadPageBackgrounds() { return database.loadPageBackgrounds(); }
    void savePageBackgrounds(PageBackgrounds value) {
        database.savePageBackgrounds(value == null ? PageBackgrounds.empty() : value);
    }
    boolean loadOnboardingCompleted() { return database.loadOnboardingCompleted(); }
    void saveOnboardingCompleted(boolean value) { database.saveOnboardingCompleted(value); }
}
