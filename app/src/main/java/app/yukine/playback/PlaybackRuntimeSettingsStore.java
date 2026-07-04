package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.Objects;

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
        PlaybackRuntimeStateManager runtime = requireRuntimeStateManager(runtimeStateManager);
        runtime.setReplayGainEnabled(runtimeSettings.loadReplayGainEnabled());
        runtime.setConcurrentPlaybackEnabled(runtimeSettings.loadConcurrentPlaybackEnabled());
        runtime.setPlaybackSpeed(runtimeSettings.loadPlaybackSpeed());
        runtime.setAppVolume(runtimeSettings.loadAppVolume());
    }

    void setPlaybackSpeed(PlaybackRuntimeStateManager runtimeStateManager, float speed) {
        requireRuntimeStateManager(runtimeStateManager).setPlaybackSpeed(speed);
    }

    void setAppVolume(PlaybackRuntimeStateManager runtimeStateManager, float volume) {
        requireRuntimeStateManager(runtimeStateManager).setAppVolume(volume);
    }

    void setConcurrentPlaybackEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled) {
        requireRuntimeStateManager(runtimeStateManager).setConcurrentPlaybackEnabled(enabled);
    }

    void setReplayGainEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled) {
        requireRuntimeStateManager(runtimeStateManager).setReplayGainEnabled(enabled);
    }

    float playbackSpeed(PlaybackRuntimeStateManager runtimeStateManager) {
        return requireRuntimeStateManager(runtimeStateManager).playbackSpeed();
    }

    float appVolume(PlaybackRuntimeStateManager runtimeStateManager) {
        return requireRuntimeStateManager(runtimeStateManager).appVolume();
    }

    boolean concurrentPlaybackEnabled(PlaybackRuntimeStateManager runtimeStateManager) {
        return requireRuntimeStateManager(runtimeStateManager).concurrentPlaybackEnabled();
    }

    float currentTrackVolume(PlaybackRuntimeStateManager runtimeStateManager) {
        return requireRuntimeStateManager(runtimeStateManager).currentTrackVolume();
    }

    void applyPlaybackParametersToPlayer(PlaybackRuntimeStateManager runtimeStateManager) {
        requireRuntimeStateManager(runtimeStateManager).applyPlaybackParametersToPlayer();
    }

    void applyCurrentTrackVolumeToPlayer(PlaybackRuntimeStateManager runtimeStateManager) {
        requireRuntimeStateManager(runtimeStateManager).applyCurrentTrackVolumeToPlayer();
    }

    void applyAudioFocusHandling(PlaybackRuntimeStateManager runtimeStateManager) {
        requireRuntimeStateManager(runtimeStateManager).applyAudioFocusHandling();
    }

    private static PlaybackRuntimeStateManager requireRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return Objects.requireNonNull(runtimeStateManager, "runtimeStateManager");
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
