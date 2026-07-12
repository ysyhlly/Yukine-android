package app.yukine.data;

import app.yukine.PageBackgrounds;
import app.yukine.TrackShareStyle;
import app.yukine.playback.AudioEffectSettings;
import app.yukine.streaming.StreamingQualityPreference;

/**
 * Owns the settings-table boundary. EchoDatabaseHelper remains responsible for schema creation
 * and transactional playback-queue updates, while settings callers do not need to know how the
 * generic settings table is encoded.
 */
final class EchoSettingsStore {
    private final EchoDatabaseHelper database;

    EchoSettingsStore(EchoDatabaseHelper database) {
        this.database = database;
    }

    String loadThemeMode() { return database.loadThemeMode(); }
    void saveThemeMode(String mode) { database.saveThemeMode(mode); }
    String loadAccentMode() { return database.loadAccentMode(); }
    void saveAccentMode(String accent) { database.saveAccentMode(accent); }
    String loadLanguageMode() { return database.loadLanguageMode(); }
    void saveLanguageMode(String languageMode) { database.saveLanguageMode(languageMode); }
    float loadPlaybackSpeed() { return database.loadPlaybackSpeed(); }
    void savePlaybackSpeed(float speed) { database.savePlaybackSpeed(speed); }
    float loadAppVolume() { return database.loadAppVolume(); }
    void saveAppVolume(float volume) { database.saveAppVolume(volume); }
    String loadStreamingAudioQuality() { return database.loadStreamingAudioQuality(); }
    void saveStreamingAudioQuality(String quality) { database.saveStreamingAudioQuality(quality); }
    boolean loadRefuseAutomaticQualityDowngrade() { return database.loadRefuseAutomaticQualityDowngrade(); }
    void saveRefuseAutomaticQualityDowngrade(boolean refuse) {
        database.saveRefuseAutomaticQualityDowngrade(refuse);
    }
    boolean loadOnlineLyricsEnabled() { return database.loadOnlineLyricsEnabled(); }
    void saveOnlineLyricsEnabled(boolean enabled) { database.saveOnlineLyricsEnabled(enabled); }
    boolean loadConcurrentPlaybackEnabled() { return database.loadConcurrentPlaybackEnabled(); }
    void saveConcurrentPlaybackEnabled(boolean enabled) { database.saveConcurrentPlaybackEnabled(enabled); }
    long loadLyricsOffsetMs() { return database.loadLyricsOffsetMs(); }
    void saveLyricsOffsetMs(long offsetMs) { database.saveLyricsOffsetMs(offsetMs); }
    boolean loadPlaybackResumeRequested() { return database.loadPlaybackResumeRequested(); }
    void savePlaybackResumeRequested(boolean requested) { database.savePlaybackResumeRequested(requested); }
    AudioEffectSettings loadAudioEffectSettings() { return database.loadAudioEffectSettings(); }
    void saveAudioEffectSettings(AudioEffectSettings settings) { database.saveAudioEffectSettings(settings); }
    boolean loadStatusBarLyricsEnabled() { return database.loadStatusBarLyricsEnabled(); }
    void saveStatusBarLyricsEnabled(boolean enabled) { database.saveStatusBarLyricsEnabled(enabled); }
    boolean loadSystemMediaLyricsTitleEnabled() { return database.loadSystemMediaLyricsTitleEnabled(); }
    void saveSystemMediaLyricsTitleEnabled(boolean enabled) { database.saveSystemMediaLyricsTitleEnabled(enabled); }
    boolean loadFloatingLyricsEnabled() { return database.loadFloatingLyricsEnabled(); }
    void saveFloatingLyricsEnabled(boolean enabled) { database.saveFloatingLyricsEnabled(enabled); }
    boolean loadNowPlayingGesturesEnabled() { return database.loadNowPlayingGesturesEnabled(); }
    void saveNowPlayingGesturesEnabled(boolean enabled) { database.saveNowPlayingGesturesEnabled(enabled); }
    boolean loadPlaybackRestoreEnabled() { return database.loadPlaybackRestoreEnabled(); }
    void savePlaybackRestoreEnabled(boolean enabled) { database.savePlaybackRestoreEnabled(enabled); }
    boolean loadReplayGainEnabled() { return database.loadReplayGainEnabled(); }
    void saveReplayGainEnabled(boolean enabled) { database.saveReplayGainEnabled(enabled); }
    boolean loadDebugPromptsEnabled() { return database.loadDebugPromptsEnabled(); }
    void saveDebugPromptsEnabled(boolean enabled) { database.saveDebugPromptsEnabled(enabled); }
    boolean loadCustomBackgroundBlurEnabled() { return database.loadCustomBackgroundBlurEnabled(); }
    float loadCustomBackgroundBlurRadiusDp() { return database.loadCustomBackgroundBlurRadiusDp(); }
    void saveCustomBackgroundBlurEnabled(boolean enabled) { database.saveCustomBackgroundBlurEnabled(enabled); }
    void saveCustomBackgroundBlurRadiusDp(float radiusDp) { database.saveCustomBackgroundBlurRadiusDp(radiusDp); }
    boolean loadGlassBlurEnabled() { return database.loadGlassBlurEnabled(); }
    float loadGlassBlurRadiusDp() { return database.loadGlassBlurRadiusDp(); }
    void saveGlassBlurEnabled(boolean enabled) { database.saveGlassBlurEnabled(enabled); }
    void saveGlassBlurRadiusDp(float radiusDp) { database.saveGlassBlurRadiusDp(radiusDp); }
    float loadGlassSurfaceOpacity() { return database.loadGlassSurfaceOpacity(); }
    void saveGlassSurfaceOpacity(float opacity) { database.saveGlassSurfaceOpacity(opacity); }
    String loadShareStyle() { return TrackShareStyle.normalize(database.loadShareStyle()); }
    void saveShareStyle(String style) { database.saveShareStyle(TrackShareStyle.normalize(style)); }
    PageBackgrounds loadPageBackgrounds() { return database.loadPageBackgrounds(); }
    void savePageBackgrounds(PageBackgrounds backgrounds) {
        database.savePageBackgrounds(backgrounds == null ? PageBackgrounds.empty() : backgrounds);
    }
    boolean loadOnboardingCompleted() { return database.loadOnboardingCompleted(); }
    void saveOnboardingCompleted(boolean completed) { database.saveOnboardingCompleted(completed); }
}
