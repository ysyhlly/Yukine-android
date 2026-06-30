package app.yukine.playback;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public final class PlaybackAudioEffectSettingsStoreTest {
    @Test
    public void restoreLoadsCurrentSettingsFromPersistence() {
        AudioEffectSettings settings = settings(true);
        FakePersistence persistence = new FakePersistence();
        persistence.loadedSettings = settings;
        PlaybackAudioEffectSettingsStore store = new PlaybackAudioEffectSettingsStore(persistence);

        AudioEffectSettings restored = store.restore();

        assertSame(settings, restored);
        assertSame(settings, store.current());
    }

    @Test
    public void restoreNormalizesMissingSettingsToDefault() {
        FakePersistence persistence = new FakePersistence();
        persistence.loadedSettings = null;
        PlaybackAudioEffectSettingsStore store = new PlaybackAudioEffectSettingsStore(persistence);

        AudioEffectSettings restored = store.restore();

        assertSame(AudioEffectSettings.DEFAULT, restored);
        assertSame(AudioEffectSettings.DEFAULT, store.current());
    }

    @Test
    public void applySavesNormalizedSettingsAndUpdatesCurrent() {
        FakePersistence persistence = new FakePersistence();
        PlaybackAudioEffectSettingsStore store = new PlaybackAudioEffectSettingsStore(persistence);
        AudioEffectSettings settings = settings(true);

        AudioEffectSettings applied = store.apply(settings);

        assertSame(settings, applied);
        assertSame(settings, store.current());
        assertSame(settings, persistence.savedSettings);
    }

    @Test
    public void applyNormalizesNullSettingsToDefaultBeforeSaving() {
        FakePersistence persistence = new FakePersistence();
        PlaybackAudioEffectSettingsStore store = new PlaybackAudioEffectSettingsStore(persistence);

        AudioEffectSettings applied = store.apply(null);

        assertSame(AudioEffectSettings.DEFAULT, applied);
        assertSame(AudioEffectSettings.DEFAULT, store.current());
        assertSame(AudioEffectSettings.DEFAULT, persistence.savedSettings);
    }

    private static AudioEffectSettings settings(boolean enabled) {
        return new AudioEffectSettings(enabled, AudioEffectSettings.PRESET_CUSTOM, new short[]{100}, (short) 200, (short) 300, 400);
    }

    private static final class FakePersistence implements PlaybackAudioEffectSettingsStore.AudioEffectSettingsPersistence {
        AudioEffectSettings loadedSettings = AudioEffectSettings.DEFAULT;
        AudioEffectSettings savedSettings;

        @Override
        public AudioEffectSettings loadAudioEffectSettings() {
            return loadedSettings;
        }

        @Override
        public void saveAudioEffectSettings(AudioEffectSettings settings) {
            savedSettings = settings;
        }
    }
}
