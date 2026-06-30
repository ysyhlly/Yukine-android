package app.yukine.playback;

import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlaybackRuntimeSettingsStoreTest {
    @Test
    public void restoreAppliesRuntimeSettingsToStateOwner() {
        FakeRuntimeSettings settings = new FakeRuntimeSettings();
        settings.replayGainEnabled = false;
        settings.concurrentPlaybackEnabled = true;
        settings.playbackSpeed = 1.25f;
        settings.appVolume = 0.55f;
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();

        new PlaybackRuntimeSettingsStore(settings).restoreInto(runtimeStateManager);

        assertFalse(runtimeStateManager.replayGainEnabled());
        assertTrue(runtimeStateManager.concurrentPlaybackEnabled());
        assertEquals(1.25f, runtimeStateManager.playbackSpeed(), 0.001f);
        assertEquals(0.55f, runtimeStateManager.appVolume(), 0.001f);
    }

    @Test
    public void restoreIgnoresMissingRuntimeStateOwner() {
        FakeRuntimeSettings settings = new FakeRuntimeSettings();

        new PlaybackRuntimeSettingsStore(settings).restoreInto(null);

        assertEquals(0, settings.loadCalls);
    }

    private static PlaybackRuntimeStateManager runtimeStateManager() {
        return new PlaybackRuntimeStateManager(new PlaybackRuntimeStateManager.StateProvider() {
            @Override
            public ExoPlayer player() {
                return null;
            }

            @Override
            public boolean playerMirrorsQueue() {
                return false;
            }

            @Override
            public Track currentTrack() {
                return null;
            }
        });
    }

    private static final class FakeRuntimeSettings implements PlaybackRuntimeSettingsStore.RuntimeSettings {
        boolean replayGainEnabled = true;
        boolean concurrentPlaybackEnabled = false;
        float playbackSpeed = 1.0f;
        float appVolume = 1.0f;
        int loadCalls;

        @Override
        public boolean loadReplayGainEnabled() {
            loadCalls++;
            return replayGainEnabled;
        }

        @Override
        public boolean loadConcurrentPlaybackEnabled() {
            loadCalls++;
            return concurrentPlaybackEnabled;
        }

        @Override
        public float loadPlaybackSpeed() {
            loadCalls++;
            return playbackSpeed;
        }

        @Override
        public float loadAppVolume() {
            loadCalls++;
            return appVolume;
        }
    }
}
