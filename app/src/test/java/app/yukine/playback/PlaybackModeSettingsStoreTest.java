package app.yukine.playback;

import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlaybackModeSettingsStoreTest {
    @Test
    public void restoreAppliesSavedShuffleAndRepeatToRuntimeState() {
        FakeModeSettings settings = new FakeModeSettings();
        settings.shuffle = true;
        settings.repeatMode = REPEAT_ONE;
        PlaybackRuntimeStateManager runtime = runtimeStateManager();

        new PlaybackModeSettingsStore(settings).restoreInto(runtime);

        assertTrue(runtime.shuffleEnabled());
        assertEquals(REPEAT_ONE, runtime.repeatMode());
    }

    @Test
    public void setShuffleSavesRuntimeStateValue() {
        FakeModeSettings settings = new FakeModeSettings();
        PlaybackRuntimeStateManager runtime = runtimeStateManager();

        new PlaybackModeSettingsStore(settings).setShuffleEnabled(runtime, true);

        assertTrue(runtime.shuffleEnabled());
        assertTrue(settings.savedShuffle);
    }

    @Test
    public void setRepeatSavesNormalizedRuntimeStateValue() {
        FakeModeSettings settings = new FakeModeSettings();
        PlaybackRuntimeStateManager runtime = runtimeStateManager();

        new PlaybackModeSettingsStore(settings).setRepeatMode(runtime, 99);

        assertEquals(REPEAT_ALL, runtime.repeatMode());
        assertEquals(REPEAT_ALL, settings.savedRepeatMode);
    }

    @Test
    public void nullRuntimeFallsBackToPersistedDefaults() {
        FakeModeSettings settings = new FakeModeSettings();
        PlaybackModeSettingsStore store = new PlaybackModeSettingsStore(settings);

        store.setShuffleEnabled(null, true);
        store.setRepeatMode(null, REPEAT_OFF);
        store.cycleRepeatMode(null);

        assertFalse(settings.savedShuffle);
        assertEquals(REPEAT_ALL, settings.savedRepeatMode);
    }

    @Test
    public void cycleRepeatModeSavesCycledRuntimeState() {
        FakeModeSettings settings = new FakeModeSettings();
        PlaybackRuntimeStateManager runtime = runtimeStateManager();
        runtime.setRepeatMode(REPEAT_ALL);

        new PlaybackModeSettingsStore(settings).cycleRepeatMode(runtime);

        assertEquals(REPEAT_ONE, runtime.repeatMode());
        assertEquals(REPEAT_ONE, settings.savedRepeatMode);
    }

    private static PlaybackRuntimeStateManager runtimeStateManager() {
        return new PlaybackRuntimeStateManager(new PlaybackRuntimeStateManager.StateProvider() {
            @Override
            public ExoPlayer player() {
                return null;
            }

            @Override
            public boolean playerMirrorsQueue() {
                return true;
            }

            @Override
            public Track currentTrack() {
                return null;
            }
        });
    }

    private static final class FakeModeSettings implements PlaybackModeSettingsStore.ModeSettings {
        boolean shuffle;
        int repeatMode = REPEAT_ALL;
        boolean savedShuffle;
        int savedRepeatMode = -1;

        @Override
        public boolean loadShuffleEnabled() {
            return shuffle;
        }

        @Override
        public int loadRepeatMode() {
            return repeatMode;
        }

        @Override
        public void saveShuffleEnabled(boolean enabled) {
            savedShuffle = enabled;
        }

        @Override
        public void saveRepeatMode(int repeatMode) {
            savedRepeatMode = repeatMode;
        }
    }
}
