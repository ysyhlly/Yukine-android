package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackStateSnapshotOwnerTest {
    @Test
    public void buildsSnapshotFromQueueRuntimeVisualizationAndRealtimeState() {
        Track track = track();
        PlaybackWaveformSnapshot waveform = new PlaybackWaveformSnapshot(new float[]{0.2f, 0.8f}, 2, 0.5f);
        PlaybackSpectrumSnapshot spectrum = new PlaybackSpectrumSnapshot(new float[]{0.1f, 0.3f}, 1, 2, 0.6f);
        FakePlaybackPositionProvider playback = new FakePlaybackPositionProvider(321L, 7000L, true);
        FakeVisualizationProvider visualization = new FakeVisualizationProvider(waveform, spectrum, true);
        PlaybackStateSnapshotOwner owner = new PlaybackStateSnapshotOwner(
                () -> new PlaybackQueueManager.QueueStateSnapshot(track, 2, 5, 9L),
                playback,
                new FakeRuntimeStateProvider(true, "buffering", true, 1, 1.25f, 0.75f),
                () -> 9000L,
                visualization,
                () -> 0.4f,
                3
        );

        PlaybackStateSnapshot snapshot = owner.snapshot();

        assertSame(track, snapshot.currentTrack);
        assertEquals(2, snapshot.currentIndex);
        assertEquals(5, snapshot.queueSize);
        assertEquals(9L, snapshot.queueRevision);
        assertEquals(321L, snapshot.positionMs);
        assertEquals(7000L, snapshot.durationMs);
        assertEquals(true, snapshot.playing);
        assertEquals(true, snapshot.preparing);
        assertEquals("buffering", snapshot.errorMessage);
        assertEquals(true, snapshot.shuffleEnabled);
        assertEquals(1, snapshot.repeatMode);
        assertEquals(1.25f, snapshot.playbackSpeed, 0.001f);
        assertEquals(0.75f, snapshot.appVolume, 0.001f);
        assertEquals(9000L, snapshot.sleepTimerRemainingMs);
        assertSame(waveform, snapshot.waveform);
        assertSame(spectrum, snapshot.spectrum);
        assertEquals(0.4f, snapshot.realtimeBeat, 0.001f);
        assertSame(track, visualization.lastTrack);
        assertEquals(7000L, visualization.lastDurationMs);
        assertEquals(true, visualization.lastDeferGeneration);
    }

    @Test
    public void fallsBackToEmptySnapshotPiecesWhenDependenciesAreMissing() {
        PlaybackStateSnapshotOwner owner = new PlaybackStateSnapshotOwner(
                null,
                null,
                null,
                null,
                null,
                null,
                7
        );

        PlaybackStateSnapshot snapshot = owner.snapshot();

        assertSame(null, snapshot.currentTrack);
        assertEquals(-1, snapshot.currentIndex);
        assertEquals(0, snapshot.queueSize);
        assertEquals(0L, snapshot.queueRevision);
        assertEquals(0L, snapshot.positionMs);
        assertEquals(0L, snapshot.durationMs);
        assertEquals(false, snapshot.playing);
        assertEquals(false, snapshot.preparing);
        assertEquals("", snapshot.errorMessage);
        assertEquals(false, snapshot.shuffleEnabled);
        assertEquals(7, snapshot.repeatMode);
        assertEquals(1.0f, snapshot.playbackSpeed, 0.001f);
        assertEquals(1.0f, snapshot.appVolume, 0.001f);
        assertEquals(0L, snapshot.sleepTimerRemainingMs);
        assertEquals(false, snapshot.waveform.hasBars());
        assertEquals(false, snapshot.spectrum.hasBands());
        assertEquals(0f, snapshot.realtimeBeat, 0.001f);
    }

    @Test
    public void doesNotReadRealtimeBeatWhenPlaybackIsPaused() {
        CountingBeatProvider beatProvider = new CountingBeatProvider();
        PlaybackStateSnapshotOwner owner = new PlaybackStateSnapshotOwner(
                () -> PlaybackQueueManager.QueueStateSnapshot.empty(),
                new FakePlaybackPositionProvider(0L, 0L, false),
                null,
                null,
                null,
                beatProvider,
                0
        );

        PlaybackStateSnapshot snapshot = owner.snapshot();

        assertEquals(0f, snapshot.realtimeBeat, 0.001f);
        assertEquals(0, beatProvider.calls);
    }

    private static Track track() {
        return new Track(
                42L,
                "Track",
                "Artist",
                "Album",
                6000L,
                Uri.parse("https://example.test/track.mp3"),
                "streaming:test:42"
        );
    }

    private static final class FakePlaybackPositionProvider
            implements PlaybackStateSnapshotOwner.PlaybackPositionProvider {
        private final long positionMs;
        private final long durationMs;
        private final boolean playing;

        private FakePlaybackPositionProvider(long positionMs, long durationMs, boolean playing) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.playing = playing;
        }

        @Override
        public long positionMs() {
            return positionMs;
        }

        @Override
        public long durationMs() {
            return durationMs;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }
    }

    private static final class FakeRuntimeStateProvider
            implements PlaybackStateSnapshotOwner.RuntimeStateProvider {
        private final boolean preparing;
        private final String errorMessage;
        private final boolean shuffleEnabled;
        private final int repeatMode;
        private final float playbackSpeed;
        private final float appVolume;

        private FakeRuntimeStateProvider(
                boolean preparing,
                String errorMessage,
                boolean shuffleEnabled,
                int repeatMode,
                float playbackSpeed,
                float appVolume
        ) {
            this.preparing = preparing;
            this.errorMessage = errorMessage;
            this.shuffleEnabled = shuffleEnabled;
            this.repeatMode = repeatMode;
            this.playbackSpeed = playbackSpeed;
            this.appVolume = appVolume;
        }

        @Override
        public boolean preparing() {
            return preparing;
        }

        @Override
        public String errorMessage() {
            return errorMessage;
        }

        @Override
        public boolean shuffleEnabled() {
            return shuffleEnabled;
        }

        @Override
        public int repeatMode() {
            return repeatMode;
        }

        @Override
        public float playbackSpeed() {
            return playbackSpeed;
        }

        @Override
        public float appVolume() {
            return appVolume;
        }

        @Override
        public boolean bitPerfectActive() {
            return false;
        }

        @Override
        public int outputSampleRateHz() {
            return 0;
        }

        @Override
        public String bitPerfectFallbackReason() {
            return null;
        }

        @Override
        public boolean audioExclusiveActive() {
            return false;
        }
    }

    private static final class FakeVisualizationProvider
            implements PlaybackStateSnapshotOwner.VisualizationProvider {
        private final PlaybackWaveformSnapshot waveform;
        private final PlaybackSpectrumSnapshot spectrum;
        private final boolean deferGeneration;
        private Track lastTrack;
        private long lastDurationMs;
        private boolean lastDeferGeneration;

        private FakeVisualizationProvider(
                PlaybackWaveformSnapshot waveform,
                PlaybackSpectrumSnapshot spectrum,
                boolean deferGeneration
        ) {
            this.waveform = waveform;
            this.spectrum = spectrum;
            this.deferGeneration = deferGeneration;
        }

        @Override
        public boolean shouldDeferPlaybackVisualization() {
            return deferGeneration;
        }

        @Override
        public PlaybackWaveformSnapshot waveformSnapshot(Track track, long durationMs, boolean deferGeneration) {
            lastTrack = track;
            lastDurationMs = durationMs;
            lastDeferGeneration = deferGeneration;
            return waveform;
        }

        @Override
        public PlaybackSpectrumSnapshot spectrumSnapshot(Track track, long durationMs, boolean deferGeneration) {
            return spectrum;
        }
    }

    private static final class CountingBeatProvider implements PlaybackStateSnapshotOwner.RealtimeBeatProvider {
        private int calls;

        @Override
        public float beat() {
            calls++;
            return 1f;
        }
    }
}
