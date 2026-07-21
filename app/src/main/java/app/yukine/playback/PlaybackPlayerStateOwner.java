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
    // A mirrored queue changes its logical current track before ExoPlayer necessarily reports
    // the target media item. Keep the old item's position out of the new track's snapshot
    // during that short hand-off window.
    private int pendingMediaItemIndex = C.INDEX_UNSET;
    private long pendingMediaItemPositionMs;
    // After a media-item transition, ExoPlayer may still report the previous item's position
    // for a brief window even though getCurrentMediaItemIndex() already reflects the new item.
    // Track the transition timestamp and expected start position to suppress stale readings.
    private long transitionStartElapsedMs = Long.MIN_VALUE;
    private long transitionExpectedPositionMs;
    private static final long TRANSITION_GUARD_WINDOW_MS = 800L;
    private static final long TRANSITION_STALE_THRESHOLD_MS = 500L;

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
            if (shouldReportPendingMediaItemPosition(player)) {
                return pendingMediaItemPositionMs;
            }
            clearPendingMediaItemPosition();
            long rawFromPlayer = Math.max(0L, player.getCurrentPosition());
            long nowMs = Math.max(0L, elapsedRealtimeMs.getAsLong());
            long rawPositionMs = guardStalePositionAfterTransition(rawFromPlayer, nowMs);
            if (rawPositionMs != rawFromPlayer) {
                // Guard suppressed a stale reading; anchor the estimate at the expected
                // position so interpolation does not add elapsed time from the old clock.
                seedPositionEstimate(rawPositionMs, nowMs, false);
                return rawPositionMs;
            }
            boolean playing = player.isPlaying();
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
            if (shouldReportPendingMediaItemPosition(player)) {
                return pendingMediaItemPositionMs;
            }
            clearPendingMediaItemPosition();
            long rawPositionMs = Math.max(0L, player.getCurrentPosition());
            long nowMs = Math.max(0L, elapsedRealtimeMs.getAsLong());
            rawPositionMs = guardStalePositionAfterTransition(rawPositionMs, nowMs);
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
        clearPendingMediaItemPosition();
        resetPositionEstimateInternal();
    }

    /**
     * Starts a media-item hand-off without allowing the previous item's position estimate to
     * leak into the target item. The target start position remains visible until ExoPlayer has
     * actually selected the requested media-item index.
     */
    synchronized void beginMediaItemPositionTransition(int mediaItemIndex, long startPositionMs) {
        resetPositionEstimateInternal();
        pendingMediaItemIndex = mediaItemIndex < 0 ? C.INDEX_UNSET : mediaItemIndex;
        pendingMediaItemPositionMs = Math.max(0L, startPositionMs);
        transitionStartElapsedMs = Math.max(0L, elapsedRealtimeMs.getAsLong());
        transitionExpectedPositionMs = Math.max(0L, startPositionMs);
    }

    private boolean shouldReportPendingMediaItemPosition(Player player) {
        return pendingMediaItemIndex != C.INDEX_UNSET
                && player.getCurrentMediaItemIndex() != pendingMediaItemIndex;
    }

    private void clearPendingMediaItemPosition() {
        pendingMediaItemIndex = C.INDEX_UNSET;
        pendingMediaItemPositionMs = 0L;
    }

    private void resetPositionEstimateInternal() {
        lastRawPositionMs = Long.MIN_VALUE;
        estimatedPositionMs = 0L;
        lastEstimateTimeMs = 0L;
        lastPlaying = false;
    }

    /**
     * Suppresses a stale position reading from ExoPlayer during the brief window after a
     * media-item transition. Even though {@code getCurrentMediaItemIndex()} already reports
     * the new item, {@code getCurrentPosition()} can momentarily return the previous item's
     * position. If the raw position is far above the expected start position within the guard
     * window, it is clamped to the expected value.
     */
    private long guardStalePositionAfterTransition(long rawPositionMs, long nowMs) {
        if (transitionStartElapsedMs == Long.MIN_VALUE) {
            return rawPositionMs;
        }
        long sinceTransitionMs = nowMs - transitionStartElapsedMs;
        if (sinceTransitionMs < 0L || sinceTransitionMs > TRANSITION_GUARD_WINDOW_MS) {
            transitionStartElapsedMs = Long.MIN_VALUE;
            return rawPositionMs;
        }
        if (rawPositionMs > transitionExpectedPositionMs + TRANSITION_STALE_THRESHOLD_MS) {
            return transitionExpectedPositionMs;
        }
        // Position is consistent with the new track; disarm the guard early.
        transitionStartElapsedMs = Long.MIN_VALUE;
        return rawPositionMs;
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
