package app.yukine.data;

import java.util.Arrays;

import app.yukine.PageBackgrounds;
import app.yukine.TrackShareStyle;
import app.yukine.data.room.SettingEntity;
import app.yukine.data.room.SettingsDao;
import app.yukine.playback.AudioEffectSettings;

/** Typed settings-table repository; serialization defaults remain compatible with v1-v14. */
public final class SettingsRepository {
    private static final String THEME_MODE = "theme_mode";
    private static final String ACCENT_MODE = "accent_mode";
    private static final String LANGUAGE_MODE = "language_mode";
    private static final String PLAYBACK_SPEED = "playback_speed";
    private static final String APP_VOLUME = "app_volume";
    private static final String STREAMING_AUDIO_QUALITY = "streaming_audio_quality";
    private static final String REFUSE_AUTOMATIC_QUALITY_DOWNGRADE =
            "refuse_automatic_quality_downgrade";
    private static final String ONLINE_LYRICS = "online_lyrics";
    private static final String CONCURRENT_PLAYBACK = "concurrent_playback";
    private static final String LYRICS_OFFSET_MS = "lyrics_offset_ms";
    private static final String SHUFFLE_ENABLED = "shuffle_enabled";
    private static final String REPEAT_MODE = "repeat_mode";
    private static final String PLAYBACK_RESUME_REQUESTED = "playback_resume_requested";
    private static final String AUDIO_EFFECTS = "audio_effects";
    private static final String ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String STATUS_BAR_LYRICS = "status_bar_lyrics";
    private static final String SYSTEM_MEDIA_LYRICS_TITLE = "system_media_lyrics_title";
    private static final String FLOATING_LYRICS = "floating_lyrics";
    private static final String NOW_PLAYING_GESTURES = "now_playing_gestures";
    private static final String PLAYBACK_RESTORE_ENABLED = "playback_restore_enabled";
    private static final String REPLAY_GAIN_ENABLED = "replay_gain_enabled";
    private static final String DEBUG_PROMPTS_ENABLED = "debug_prompts_enabled";
    private static final String CUSTOM_BACKGROUND_BLUR_ENABLED = "custom_background_blur_enabled";
    private static final String CUSTOM_BACKGROUND_BLUR_RADIUS_DP = "custom_background_blur_radius_dp";
    private static final String GLASS_BLUR_ENABLED = "glass_blur_enabled";
    private static final String GLASS_BLUR_RADIUS_DP = "glass_blur_radius_dp";
    private static final String GLASS_SURFACE_OPACITY = "glass_surface_opacity";
    private static final String SHARE_STYLE = "share_style";
    private static final String PAGE_BACKGROUND_SHARED = "page_background_shared";
    private static final String PAGE_BACKGROUND_HOME = "page_background_home";
    private static final String PAGE_BACKGROUND_LIBRARY = "page_background_library";
    private static final String PAGE_BACKGROUND_PLAYER = "page_background_player";
    private static final String PAGE_BACKGROUND_SETTINGS = "page_background_settings";
    private static final String PAGE_BACKGROUND_SHARED_TRANSFORM = "page_background_shared_transform";
    private static final String PAGE_BACKGROUND_HOME_TRANSFORM = "page_background_home_transform";
    private static final String PAGE_BACKGROUND_LIBRARY_TRANSFORM = "page_background_library_transform";
    private static final String PAGE_BACKGROUND_PLAYER_TRANSFORM = "page_background_player_transform";
    private static final String PAGE_BACKGROUND_SETTINGS_TRANSFORM = "page_background_settings_transform";
    private static final String MEDIA_STORE_GENERATION = "media_store_generation";
    private static final String LIBRARY_AUTO_SYNC_ENABLED = "library_auto_sync_enabled";

    private final SettingsDao dao;

    public SettingsRepository(SettingsDao dao) {
        this.dao = dao;
    }

    String loadThemeMode() { return load(THEME_MODE, "system"); }
    void saveThemeMode(String value) { save(THEME_MODE, value == null ? "system" : value); }
    String loadAccentMode() { return load(ACCENT_MODE, "blue"); }
    void saveAccentMode(String value) { save(ACCENT_MODE, value == null ? "blue" : value); }
    String loadLanguageMode() { return load(LANGUAGE_MODE, "system"); }
    void saveLanguageMode(String value) { save(LANGUAGE_MODE, value == null ? "system" : value); }
    float loadPlaybackSpeed() { return floatValue(PLAYBACK_SPEED, 1.0f); }
    void savePlaybackSpeed(float value) { save(PLAYBACK_SPEED, String.valueOf(value)); }
    float loadAppVolume() { return floatValue(APP_VOLUME, 1.0f); }
    void saveAppVolume(float value) { save(APP_VOLUME, String.valueOf(value)); }
    String loadStreamingAudioQuality() { return load(STREAMING_AUDIO_QUALITY, "high"); }
    void saveStreamingAudioQuality(String value) {
        save(STREAMING_AUDIO_QUALITY, value == null ? "high" : value);
    }
    boolean loadRefuseAutomaticQualityDowngrade() {
        return bool(REFUSE_AUTOMATIC_QUALITY_DOWNGRADE, false);
    }
    void saveRefuseAutomaticQualityDowngrade(boolean value) {
        saveBool(REFUSE_AUTOMATIC_QUALITY_DOWNGRADE, value);
    }
    boolean loadOnlineLyricsEnabled() { return bool(ONLINE_LYRICS, true); }
    void saveOnlineLyricsEnabled(boolean value) { saveBool(ONLINE_LYRICS, value); }
    boolean loadConcurrentPlaybackEnabled() { return bool(CONCURRENT_PLAYBACK, false); }
    void saveConcurrentPlaybackEnabled(boolean value) { saveBool(CONCURRENT_PLAYBACK, value); }
    boolean loadLibraryAutoSyncEnabled() { return bool(LIBRARY_AUTO_SYNC_ENABLED, false); }
    void saveLibraryAutoSyncEnabled(boolean value) { saveBool(LIBRARY_AUTO_SYNC_ENABLED, value); }
    String loadWebDavSyncManifest(long sourceId) {
        return load("webdav_sync_manifest_" + sourceId, "");
    }
    void saveWebDavSyncManifest(long sourceId, String value) {
        save("webdav_sync_manifest_" + sourceId, value == null ? "" : value);
    }
    long loadLyricsOffsetMs() { return longValue(LYRICS_OFFSET_MS, 0L); }
    void saveLyricsOffsetMs(long value) { save(LYRICS_OFFSET_MS, String.valueOf(value)); }
    boolean loadShuffleEnabled() { return bool(SHUFFLE_ENABLED, false); }
    void saveShuffleEnabled(boolean value) { saveBool(SHUFFLE_ENABLED, value); }
    int loadRepeatMode() { return intValue(REPEAT_MODE, 0); }
    void saveRepeatMode(int value) { save(REPEAT_MODE, String.valueOf(value)); }
    boolean loadPlaybackResumeRequested() { return bool(PLAYBACK_RESUME_REQUESTED, false); }
    void savePlaybackResumeRequested(boolean value) { saveBool(PLAYBACK_RESUME_REQUESTED, value); }
    AudioEffectSettings loadAudioEffectSettings() {
        return AudioEffectSettings.decode(load(AUDIO_EFFECTS, ""));
    }
    void saveAudioEffectSettings(AudioEffectSettings value) {
        save(AUDIO_EFFECTS, (value == null ? AudioEffectSettings.DEFAULT : value).encode());
    }
    boolean loadStatusBarLyricsEnabled() { return bool(STATUS_BAR_LYRICS, true); }
    void saveStatusBarLyricsEnabled(boolean value) { saveBool(STATUS_BAR_LYRICS, value); }
    boolean loadSystemMediaLyricsTitleEnabled() { return bool(SYSTEM_MEDIA_LYRICS_TITLE, false); }
    void saveSystemMediaLyricsTitleEnabled(boolean value) { saveBool(SYSTEM_MEDIA_LYRICS_TITLE, value); }
    boolean loadFloatingLyricsEnabled() { return bool(FLOATING_LYRICS, false); }
    void saveFloatingLyricsEnabled(boolean value) { saveBool(FLOATING_LYRICS, value); }
    boolean loadNowPlayingGesturesEnabled() { return bool(NOW_PLAYING_GESTURES, true); }
    void saveNowPlayingGesturesEnabled(boolean value) { saveBool(NOW_PLAYING_GESTURES, value); }
    boolean loadPlaybackRestoreEnabled() { return bool(PLAYBACK_RESTORE_ENABLED, true); }
    void savePlaybackRestoreEnabled(boolean value) { saveBool(PLAYBACK_RESTORE_ENABLED, value); }
    boolean loadReplayGainEnabled() { return bool(REPLAY_GAIN_ENABLED, true); }
    void saveReplayGainEnabled(boolean value) { saveBool(REPLAY_GAIN_ENABLED, value); }
    boolean loadDebugPromptsEnabled() { return bool(DEBUG_PROMPTS_ENABLED, false); }
    void saveDebugPromptsEnabled(boolean value) { saveBool(DEBUG_PROMPTS_ENABLED, value); }
    boolean loadCustomBackgroundBlurEnabled() { return bool(CUSTOM_BACKGROUND_BLUR_ENABLED, false); }
    float loadCustomBackgroundBlurRadiusDp() {
        return normalizeCustomBackgroundBlurRadius(floatValue(CUSTOM_BACKGROUND_BLUR_RADIUS_DP, 24f));
    }
    void saveCustomBackgroundBlurEnabled(boolean value) { saveBool(CUSTOM_BACKGROUND_BLUR_ENABLED, value); }
    void saveCustomBackgroundBlurRadiusDp(float value) {
        save(CUSTOM_BACKGROUND_BLUR_RADIUS_DP, String.valueOf(normalizeCustomBackgroundBlurRadius(value)));
    }
    boolean loadGlassBlurEnabled() { return bool(GLASS_BLUR_ENABLED, false); }
    float loadGlassBlurRadiusDp() {
        return normalizeGlassBlurRadius(floatValue(GLASS_BLUR_RADIUS_DP, 18f));
    }
    void saveGlassBlurEnabled(boolean value) { saveBool(GLASS_BLUR_ENABLED, value); }
    void saveGlassBlurRadiusDp(float value) {
        save(GLASS_BLUR_RADIUS_DP, String.valueOf(normalizeGlassBlurRadius(value)));
    }
    float loadGlassSurfaceOpacity() {
        return normalizeGlassSurfaceOpacity(floatValue(GLASS_SURFACE_OPACITY, 0.62f));
    }
    void saveGlassSurfaceOpacity(float value) {
        save(GLASS_SURFACE_OPACITY, String.valueOf(normalizeGlassSurfaceOpacity(value)));
    }
    String loadShareStyle() { return TrackShareStyle.normalize(load(SHARE_STYLE, "platform_card")); }
    void saveShareStyle(String value) { save(SHARE_STYLE, TrackShareStyle.normalize(value)); }

    PageBackgrounds loadPageBackgrounds() {
        return new PageBackgrounds(
                load(PAGE_BACKGROUND_SHARED, ""),
                load(PAGE_BACKGROUND_HOME, ""),
                load(PAGE_BACKGROUND_LIBRARY, ""),
                load(PAGE_BACKGROUND_PLAYER, ""),
                load(PAGE_BACKGROUND_SETTINGS, ""),
                load(PAGE_BACKGROUND_SHARED_TRANSFORM, ""),
                load(PAGE_BACKGROUND_HOME_TRANSFORM, ""),
                load(PAGE_BACKGROUND_LIBRARY_TRANSFORM, ""),
                load(PAGE_BACKGROUND_PLAYER_TRANSFORM, ""),
                load(PAGE_BACKGROUND_SETTINGS_TRANSFORM, "")
        );
    }

    void savePageBackgrounds(PageBackgrounds value) {
        PageBackgrounds safe = value == null ? PageBackgrounds.empty() : value;
        dao.putAll(Arrays.asList(
                setting(PAGE_BACKGROUND_SHARED, safe.getSharedUri()),
                setting(PAGE_BACKGROUND_HOME, safe.getHomeUri()),
                setting(PAGE_BACKGROUND_LIBRARY, safe.getLibraryUri()),
                setting(PAGE_BACKGROUND_PLAYER, safe.getPlayerUri()),
                setting(PAGE_BACKGROUND_SETTINGS, safe.getSettingsUri()),
                setting(PAGE_BACKGROUND_SHARED_TRANSFORM, safe.getSharedTransform()),
                setting(PAGE_BACKGROUND_HOME_TRANSFORM, safe.getHomeTransform()),
                setting(PAGE_BACKGROUND_LIBRARY_TRANSFORM, safe.getLibraryTransform()),
                setting(PAGE_BACKGROUND_PLAYER_TRANSFORM, safe.getPlayerTransform()),
                setting(PAGE_BACKGROUND_SETTINGS_TRANSFORM, safe.getSettingsTransform())
        ));
    }

    boolean loadOnboardingCompleted() { return bool(ONBOARDING_COMPLETED, false); }
    void saveOnboardingCompleted(boolean value) { saveBool(ONBOARDING_COMPLETED, value); }
    long loadMediaStoreGeneration() { return Math.max(-1L, longValue(MEDIA_STORE_GENERATION, -1L)); }
    void saveMediaStoreGeneration(long value) {
        if (value >= 0L) save(MEDIA_STORE_GENERATION, String.valueOf(value));
    }

    private String load(String key, String fallback) {
        String value = dao.value(key);
        return value == null ? fallback : value;
    }

    private void save(String key, String value) {
        dao.put(setting(key, value));
    }

    private void saveBool(String key, boolean value) {
        save(key, value ? "true" : "false");
    }

    private boolean bool(String key, boolean fallback) {
        return "true".equals(load(key, fallback ? "true" : "false"));
    }

    private int intValue(String key, int fallback) {
        try {
            return Integer.parseInt(load(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long longValue(String key, long fallback) {
        try {
            return Long.parseLong(load(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private float floatValue(String key, float fallback) {
        try {
            return Float.parseFloat(load(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static SettingEntity setting(String key, String value) {
        return new SettingEntity(key, value == null ? "" : value);
    }

    private static float normalizeGlassBlurRadius(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 18f;
        return Math.max(4f, Math.min(36f, value));
    }

    private static float normalizeCustomBackgroundBlurRadius(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 24f;
        return Math.max(4f, Math.min(64f, value));
    }

    private static float normalizeGlassSurfaceOpacity(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.62f;
        return Math.max(0.4f, Math.min(1f, value));
    }
}
