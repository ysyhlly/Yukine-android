package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ONE;

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
        if (runtimeStateManager == null) {
            return;
        }
        runtimeStateManager.setShuffleEnabled(modeSettings.loadShuffleEnabled());
        runtimeStateManager.setRepeatMode(modeSettings.loadRepeatMode());
    }

    void setShuffleEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled) {
        if (runtimeStateManager != null) {
            runtimeStateManager.setShuffleEnabled(enabled);
        }
        modeSettings.saveShuffleEnabled(runtimeStateManager != null && runtimeStateManager.shuffleEnabled());
    }

    void setRepeatMode(PlaybackRuntimeStateManager runtimeStateManager, int mode) {
        if (runtimeStateManager != null) {
            runtimeStateManager.setRepeatMode(mode);
        }
        modeSettings.saveRepeatMode(
                runtimeStateManager != null ? runtimeStateManager.repeatMode() : normalizedRepeatFallback(mode)
        );
    }

    void cycleRepeatMode(PlaybackRuntimeStateManager runtimeStateManager) {
        if (runtimeStateManager != null) {
            runtimeStateManager.cycleRepeatMode();
        }
        modeSettings.saveRepeatMode(runtimeStateManager != null ? runtimeStateManager.repeatMode() : REPEAT_ALL);
    }

    private static int normalizedRepeatFallback(int mode) {
        return mode == REPEAT_ALL || mode == REPEAT_ONE || mode == REPEAT_OFF ? mode : REPEAT_ALL;
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
