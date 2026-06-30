package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

final class PlaybackRuntimeSettingsStore {
    interface RuntimeSettings {
        boolean loadReplayGainEnabled();

        boolean loadConcurrentPlaybackEnabled();

        float loadPlaybackSpeed();

        float loadAppVolume();
    }

    private final RuntimeSettings runtimeSettings;

    PlaybackRuntimeSettingsStore(RuntimeSettings runtimeSettings) {
        this.runtimeSettings = runtimeSettings;
    }

    static PlaybackRuntimeSettingsStore fromRepository(MusicLibraryRepository repository) {
        return new PlaybackRuntimeSettingsStore(new RepositoryRuntimeSettings(repository));
    }

    void restoreInto(PlaybackRuntimeStateManager runtimeStateManager) {
        if (runtimeStateManager == null) {
            return;
        }
        runtimeStateManager.setReplayGainEnabled(runtimeSettings.loadReplayGainEnabled());
        runtimeStateManager.setConcurrentPlaybackEnabled(runtimeSettings.loadConcurrentPlaybackEnabled());
        runtimeStateManager.setPlaybackSpeed(runtimeSettings.loadPlaybackSpeed());
        runtimeStateManager.setAppVolume(runtimeSettings.loadAppVolume());
    }

    private static final class RepositoryRuntimeSettings implements RuntimeSettings {
        private final MusicLibraryRepository repository;

        private RepositoryRuntimeSettings(MusicLibraryRepository repository) {
            this.repository = repository;
        }

        @Override
        public boolean loadReplayGainEnabled() {
            return repository.loadReplayGainEnabled();
        }

        @Override
        public boolean loadConcurrentPlaybackEnabled() {
            return repository.loadConcurrentPlaybackEnabled();
        }

        @Override
        public float loadPlaybackSpeed() {
            return repository.loadPlaybackSpeed();
        }

        @Override
        public float loadAppVolume() {
            return repository.loadAppVolume();
        }
    }
}
