package app.yukine.playback;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import java.util.function.LongSupplier;

final class PlaybackPlayerStateOwner implements
        PlaybackCrossfadeStateOwner.PlaybackStateProvider,
        PlaybackRealtimeVisualizationOwner.PlaybackStateProvider,
        PlaybackStateSnapshotOwner.PlaybackPositionProvider,
        PlaybackBufferedProgressOwner.PlaybackPositionProvider {
    interface PlayerProvider {
        Player player();
    }

    private final PlayerProvider playerProvider;
    private final LongSupplier elapsedRealtimeMs;
    private long lastRawPositionMs = Long.MIN_VALUE;
    private long estimatedPositionMs;
    private long lastEstimateTimeMs;
    private boolean lastPlaying;

    PlaybackPlayerStateOwner(PlayerProvider playerProvider) {
        this(playerProvider, SystemClock::elapsedRealtime);
    }

    PlaybackPlayerStateOwner(PlayerProvider playerProvider, LongSupplier elapsedRealtimeMs) {
        this.playerProvider = playerProvider;
        this.elapsedRealtimeMs = elapsedRealtimeMs == null ? SystemClock::elapsedRealtime : elapsedRealtimeMs;
    }

    static PlaybackPlayerStateOwner fromPlayerProvider(PlayerProvider playerProvider) {
        return new PlaybackPlayerStateOwner(playerProvider);
    }

    @Override
    public boolean isPlaying() {
        Player player = player();
        if (player == null) {
            return false;
        }
        try {
            return player.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public synchronized long positionMs() {
        Player player = player();
        if (player == null) {
            resetPositionEstimate();
            return 0L;
        }
        try {
            long rawPositionMs = Math.max(0L, player.getCurrentPosition());
            boolean playing = player.isPlaying();
            long nowMs = Math.max(0L, elapsedRealtimeMs.getAsLong());
            if (!playing) {
                long pausedPositionMs = Math.max(rawPositionMs, estimatedPositionMs);
                seedPositionEstimate(pausedPositionMs, nowMs, false);
                return pausedPositionMs;
            }
            if (!lastPlaying && rawPositionMs < estimatedPositionMs) {
                lastRawPositionMs = rawPositionMs;
                lastEstimateTimeMs = nowMs;
                lastPlaying = true;
                return estimatedPositionMs;
            }
            if (!lastPlaying || rawPositionMs != lastRawPositionMs || rawPositionMs > estimatedPositionMs) {
                seedPositionEstimate(rawPositionMs, nowMs, true);
                return rawPositionMs;
            }
            long elapsedMs = Math.max(0L, nowMs - lastEstimateTimeMs);
            estimatedPositionMs = Math.max(rawPositionMs, estimatedPositionMs + elapsedMs);
            lastEstimateTimeMs = nowMs;
            lastRawPositionMs = rawPositionMs;
            lastPlaying = true;
            return estimatedPositionMs;
        } catch (IllegalStateException ignored) {
            resetPositionEstimate();
            return 0L;
        }
    }

    synchronized long sessionPositionMs() {
        Player player = player();
        if (player == null) {
            return Math.max(0L, estimatedPositionMs);
        }
        try {
            long rawPositionMs = Math.max(0L, player.getCurrentPosition());
            if (rawPositionMs > 0L || estimatedPositionMs <= 0L) {
                return rawPositionMs;
            }
            return estimatedPositionMs;
        } catch (IllegalStateException ignored) {
            return Math.max(0L, estimatedPositionMs);
        }
    }

    @Override
    public long durationMs() {
        Player player = player();
        if (player == null) {
            return 0L;
        }
        try {
            long durationMs = player.getDuration();
            return durationMs == C.TIME_UNSET ? 0L : Math.max(0L, durationMs);
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    private Player player() {
        return playerProvider == null ? null : playerProvider.player();
    }

    synchronized void resetPositionEstimate() {
        lastRawPositionMs = Long.MIN_VALUE;
        estimatedPositionMs = 0L;
        lastEstimateTimeMs = 0L;
        lastPlaying = false;
    }

    synchronized void setPositionEstimate(long positionMs) {
        seedPositionEstimate(Math.max(0L, positionMs), Math.max(0L, elapsedRealtimeMs.getAsLong()), false);
    }

    private void seedPositionEstimate(long positionMs, long nowMs, boolean playing) {
        lastRawPositionMs = positionMs;
        estimatedPositionMs = positionMs;
        lastEstimateTimeMs = nowMs;
        lastPlaying = playing;
    }
}
