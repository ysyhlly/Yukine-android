package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;

final class PlaybackAudioEffectSettingsStore {
    interface AudioEffectSettingsPersistence {
        AudioEffectSettings loadAudioEffectSettings();

        void saveAudioEffectSettings(AudioEffectSettings settings);
    }

    private final AudioEffectSettingsPersistence persistence;
    private AudioEffectSettings current = AudioEffectSettings.DEFAULT;

    PlaybackAudioEffectSettingsStore(AudioEffectSettingsPersistence persistence) {
        this.persistence = persistence;
    }

    static PlaybackAudioEffectSettingsStore fromRepository(MusicLibraryRepository repository) {
        return new PlaybackAudioEffectSettingsStore(new RepositoryPersistence(repository));
    }

    AudioEffectSettings restore() {
        current = normalize(persistence.loadAudioEffectSettings());
        return current;
    }

    AudioEffectSettings apply(AudioEffectSettings settings) {
        current = normalize(settings);
        persistence.saveAudioEffectSettings(current);
        return current;
    }

    AudioEffectSettings current() {
        return current;
    }

    private static AudioEffectSettings normalize(AudioEffectSettings settings) {
        return settings == null ? AudioEffectSettings.DEFAULT : settings;
    }

    private static final class RepositoryPersistence implements AudioEffectSettingsPersistence {
        private final MusicLibraryRepository repository;

        private RepositoryPersistence(MusicLibraryRepository repository) {
            this.repository = repository;
        }

        @Override
        public AudioEffectSettings loadAudioEffectSettings() {
            return repository.loadAudioEffectSettings();
        }

        @Override
        public void saveAudioEffectSettings(AudioEffectSettings settings) {
            repository.saveAudioEffectSettings(settings);
        }
    }
}
