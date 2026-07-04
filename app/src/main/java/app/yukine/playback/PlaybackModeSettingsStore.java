package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.Objects;

final class PlaybackModeSettingsStore {
    interface ModeSettings {
        boolean loadShuffleEnabled();

        int loadRepeatMode();

        void saveShuffleEnabled(boolean enabled);

        void saveRepeatMode(int repeatMode);
    }

    private final ModeSettings modeSettings;

    PlaybackModeSettingsStore(ModeSettings modeSettings) {
        this.modeSettings = modeSettings;
    }

    static PlaybackModeSettingsStore fromRepository(MusicLibraryRepository repository) {
        return new PlaybackModeSettingsStore(new RepositoryModeSettings(repository));
    }

    void restoreInto(PlaybackRuntimeStateManager runtimeStateManager) {
        PlaybackRuntimeStateManager runtime = requireRuntimeStateManager(runtimeStateManager);
        runtime.setShuffleEnabled(modeSettings.loadShuffleEnabled());
        runtime.setRepeatMode(modeSettings.loadRepeatMode());
    }

    void setShuffleEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled) {
        PlaybackRuntimeStateManager runtime = requireRuntimeStateManager(runtimeStateManager);
        runtime.setShuffleEnabled(enabled);
        runtime.applyPlaybackModeToPlayer();
        modeSettings.saveShuffleEnabled(runtime.shuffleEnabled());
    }

    void setRepeatMode(PlaybackRuntimeStateManager runtimeStateManager, int mode) {
        PlaybackRuntimeStateManager runtime = requireRuntimeStateManager(runtimeStateManager);
        runtime.setRepeatMode(mode);
        runtime.applyPlaybackModeToPlayer();
        modeSettings.saveRepeatMode(runtime.repeatMode());
    }

    void cycleRepeatMode(PlaybackRuntimeStateManager runtimeStateManager) {
        PlaybackRuntimeStateManager runtime = requireRuntimeStateManager(runtimeStateManager);
        runtime.cycleRepeatMode();
        runtime.applyPlaybackModeToPlayer();
        modeSettings.saveRepeatMode(runtime.repeatMode());
    }

    void applyPlaybackModeToPlayer(PlaybackRuntimeStateManager runtimeStateManager) {
        requireRuntimeStateManager(runtimeStateManager).applyPlaybackModeToPlayer();
    }

    int repeatMode(PlaybackRuntimeStateManager runtimeStateManager) {
        return requireRuntimeStateManager(runtimeStateManager).repeatMode();
    }

    private static PlaybackRuntimeStateManager requireRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return Objects.requireNonNull(runtimeStateManager, "runtimeStateManager");
    }

    private static final class RepositoryModeSettings implements ModeSettings {
        private final MusicLibraryRepository repository;

        private RepositoryModeSettings(MusicLibraryRepository repository) {
            this.repository = repository;
        }

        @Override
        public boolean loadShuffleEnabled() {
            return repository.loadShuffleEnabled();
        }

        @Override
        public int loadRepeatMode() {
            return repository.loadRepeatMode();
        }

        @Override
        public void saveShuffleEnabled(boolean enabled) {
            repository.saveShuffleEnabled(enabled);
        }

        @Override
        public void saveRepeatMode(int repeatMode) {
            repository.saveRepeatMode(repeatMode);
        }
    }
}
