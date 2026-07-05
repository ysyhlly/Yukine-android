package app.yukine.playback;

import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    public void setSpeedAndVolumeDelegateToRuntimeStateOwner() {
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        store.setPlaybackSpeed(runtimeStateManager, 1.36f);
        store.setAppVolume(runtimeStateManager, 0.42f);

        assertEquals(1.36f, runtimeStateManager.playbackSpeed(), 0.001f);
        assertEquals(0.42f, runtimeStateManager.appVolume(), 0.001f);
    }

    @Test
    public void setConcurrentPlaybackAndReplayGainDelegateToRuntimeStateOwner() {
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        store.setConcurrentPlaybackEnabled(runtimeStateManager, true);
        store.setReplayGainEnabled(runtimeStateManager, false);

        assertTrue(runtimeStateManager.concurrentPlaybackEnabled());
        assertFalse(runtimeStateManager.replayGainEnabled());
    }

    @Test
    public void readRuntimeSettingsDelegateToRuntimeStateOwner() {
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        store.setPlaybackSpeed(runtimeStateManager, 1.36f);
        store.setAppVolume(runtimeStateManager, 0.42f);
        store.setConcurrentPlaybackEnabled(runtimeStateManager, true);

        assertEquals(1.36f, store.playbackSpeed(runtimeStateManager), 0.001f);
        assertEquals(0.42f, store.appVolume(runtimeStateManager), 0.001f);
        assertEquals(0.42f, store.currentTrackVolume(runtimeStateManager), 0.001f);
        assertTrue(store.concurrentPlaybackEnabled(runtimeStateManager));
    }

    @Test
    public void readRuntimeSettingsFallBackWhenRuntimeStateOwnerMissing() {
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        assertEquals(1.0f, store.playbackSpeed(null), 0.001f);
        assertEquals(1.0f, store.appVolume(null), 0.001f);
        assertEquals(1.0f, store.currentTrackVolume(null), 0.001f);
        assertFalse(store.concurrentPlaybackEnabled(null));
    }

    @Test
    public void applyPlaybackRuntimeSettingsIgnoreMissingRuntimeStateOwner() {
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        store.applyPlaybackParametersToPlayer(null);
        store.applyCurrentTrackVolumeToPlayer(null);
        store.applyAudioFocusHandling(null);
    }

    @Test
    public void applyCurrentTrackVolumeDelegatesToRuntimeStateOwner() {
        RecordingExoPlayer player = new RecordingExoPlayer();
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager(player);
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());
        runtimeStateManager.setAppVolume(0.42f);
        player.events.clear();

        store.applyCurrentTrackVolumeToPlayer(runtimeStateManager);

        assertEquals(java.util.Arrays.asList("setVolume:0.42"), player.events);
    }

    @Test
    public void settersIgnoreMissingRuntimeStateOwner() {
        PlaybackRuntimeSettingsStore store = new PlaybackRuntimeSettingsStore(new FakeRuntimeSettings());

        store.setPlaybackSpeed(null, 1.36f);
        store.setAppVolume(null, 0.42f);
        store.setConcurrentPlaybackEnabled(null, true);
        store.setReplayGainEnabled(null, false);
    }

    private static PlaybackRuntimeStateManager runtimeStateManager() {
        return runtimeStateManager(null);
    }

    private static PlaybackRuntimeStateManager runtimeStateManager(RecordingExoPlayer recordingPlayer) {
        return new PlaybackRuntimeStateManager(new PlaybackRuntimeStateManager.StateProvider() {
            @Override
            public ExoPlayer player() {
                return recordingPlayer == null ? null : recordingPlayer.proxy;
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

    private static final class RecordingExoPlayer {
        private final List<String> events = new ArrayList<>();
        private final ExoPlayer proxy = (ExoPlayer) Proxy.newProxyInstance(
                ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class},
                (target, method, args) -> {
                    if ("setVolume".equals(method.getName())) {
                        events.add("setVolume:" + args[0]);
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        return null;
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
