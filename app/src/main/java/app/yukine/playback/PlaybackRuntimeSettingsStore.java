package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

final class PlaybackRuntimeSettingsStore {
    interface RuntimeSettings {
        boolean loadReplayGainEnabled();

        float loadPlaybackSpeed();

        float loadAppVolume();

        boolean loadBitPerfectEnabled();
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
        runtimeStateManager.setPlaybackSpeed(runtimeSettings.loadPlaybackSpeed());
        runtimeStateManager.setAppVolume(runtimeSettings.loadAppVolume());
        runtimeStateManager.setBitPerfectActive(runtimeSettings.loadBitPerfectEnabled());
    }

    void setPlaybackSpeed(PlaybackRuntimeStateManager runtimeStateManager, float speed) {
        if (runtimeStateManager != null) {
            runtimeStateManager.setPlaybackSpeed(speed);
        }
    }

    void setAppVolume(PlaybackRuntimeStateManager runtimeStateManager, float volume) {
        if (runtimeStateManager != null) {
            runtimeStateManager.setAppVolume(volume);
        }
    }

    void setReplayGainEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled) {
        if (runtimeStateManager != null) {
            runtimeStateManager.setReplayGainEnabled(enabled);
        }
    }

    float playbackSpeed(PlaybackRuntimeStateManager runtimeStateManager) {
        return runtimeStateManager == null ? 1.0f : runtimeStateManager.playbackSpeed();
    }

    float appVolume(PlaybackRuntimeStateManager runtimeStateManager) {
        return runtimeStateManager == null ? 1.0f : runtimeStateManager.appVolume();
    }

    boolean concurrentPlaybackEnabled(PlaybackRuntimeStateManager runtimeStateManager) {
        return false;
    }

    float currentTrackVolume(PlaybackRuntimeStateManager runtimeStateManager) {
        return runtimeStateManager == null ? 1.0f : runtimeStateManager.currentTrackVolume();
    }

    void applyPlaybackParametersToPlayer(PlaybackRuntimeStateManager runtimeStateManager) {
        if (runtimeStateManager != null) {
            runtimeStateManager.applyPlaybackParametersToPlayer();
        }
    }

    void applyCurrentTrackVolumeToPlayer(PlaybackRuntimeStateManager runtimeStateManager) {
        if (runtimeStateManager != null) {
            runtimeStateManager.applyCurrentTrackVolumeToPlayer();
        }
    }

    void applyAudioAttributes(PlaybackRuntimeStateManager runtimeStateManager) {
        if (runtimeStateManager != null) {
            runtimeStateManager.applyAudioAttributes();
        }
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
        public float loadPlaybackSpeed() {
            return repository.loadPlaybackSpeed();
        }

        @Override
        public float loadAppVolume() {
            return repository.loadAppVolume();
        }

        @Override
        public boolean loadBitPerfectEnabled() {
            return repository.loadBitPerfectEnabled();
        }
    }
}
