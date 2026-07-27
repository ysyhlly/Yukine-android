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
    // Last media-item index observed while reading position. An unannounced index change
    // (auto-advance path that skipped beginMediaItemPositionTransition) still must not inherit
    // the previous item's progress.
    private int lastObservedMediaItemIndex = C.INDEX_UNSET;
    // Stop validating against wall-clock after this; eventually trust ExoPlayer again.
    private static final long TRANSITION_GUARD_HARD_TIMEOUT_MS = 8_000L;
    // Allow a little jitter above (expected + elapsed) before treating a reading as stale.
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
            noteMediaItemIndex(player.getCurrentMediaItemIndex());
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
            noteMediaItemIndex(player.getCurrentMediaItemIndex());
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
        // Replacing the playable source without a known start should still suppress a brief
        // stale ExoPlayer position reading from the previous item.
        armTransitionGuard(0L);
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
        if (mediaItemIndex >= 0) {
            lastObservedMediaItemIndex = mediaItemIndex;
        }
        armTransitionGuard(Math.max(0L, startPositionMs));
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

    private void armTransitionGuard(long expectedPositionMs) {
        transitionStartElapsedMs = Math.max(0L, elapsedRealtimeMs.getAsLong());
        transitionExpectedPositionMs = Math.max(0L, expectedPositionMs);
    }

    private void clearTransitionGuard() {
        transitionStartElapsedMs = Long.MIN_VALUE;
        transitionExpectedPositionMs = 0L;
    }

    /**
     * When ExoPlayer advances to a new media item without an explicit hand-off call, treat that
     * as a start-at-zero transition so the previous item's progress cannot stick to the next song.
     */
    private void noteMediaItemIndex(int mediaItemIndex) {
        if (mediaItemIndex < 0) {
            return;
        }
        // During an explicit hand-off the player may still report the previous index. Ignore it
        // so we do not treat "still on old item" as another transition back to that item.
        if (pendingMediaItemIndex != C.INDEX_UNSET && mediaItemIndex != pendingMediaItemIndex) {
            return;
        }
        if (lastObservedMediaItemIndex == C.INDEX_UNSET) {
            lastObservedMediaItemIndex = mediaItemIndex;
            return;
        }
        if (mediaItemIndex == lastObservedMediaItemIndex) {
            return;
        }
        lastObservedMediaItemIndex = mediaItemIndex;
        // Unannounced index change: drop the old estimate and clamp to the new item start.
        resetPositionEstimateInternal();
        clearPendingMediaItemPosition();
        armTransitionGuard(0L);
    }

    /**
     * Suppresses a stale position reading from ExoPlayer during the window after a media-item
     * transition. Even though {@code getCurrentMediaItemIndex()} already reports the new item,
     * {@code getCurrentPosition()} can momentarily (or for several seconds while buffering)
     * return the previous item's position.
     * <p>
     * A reading is only accepted when it could have been reached from the expected start by
     * wall-clock elapsed time since the transition (plus a small jitter threshold). That keeps
     * a 3-minute leftover from the previous song from sticking onto the next one, without
     * blocking real seeks that update {@link #transitionExpectedPositionMs}.
     */
    private long guardStalePositionAfterTransition(long rawPositionMs, long nowMs) {
        if (transitionStartElapsedMs == Long.MIN_VALUE) {
            return rawPositionMs;
        }
        long sinceTransitionMs = nowMs - transitionStartElapsedMs;
        if (sinceTransitionMs < 0L) {
            return transitionExpectedPositionMs;
        }
        if (sinceTransitionMs > TRANSITION_GUARD_HARD_TIMEOUT_MS) {
            clearTransitionGuard();
            return rawPositionMs;
        }
        long maxPlausibleMs = transitionExpectedPositionMs
                + sinceTransitionMs
                + TRANSITION_STALE_THRESHOLD_MS;
        if (rawPositionMs > maxPlausibleMs) {
            return transitionExpectedPositionMs;
        }
        // First plausible reading for the new item — hand control back to the player clock.
        clearTransitionGuard();
        return rawPositionMs;
    }

    synchronized void setPositionEstimate(long positionMs) {
        long clamped = Math.max(0L, positionMs);
        seedPositionEstimate(clamped, Math.max(0L, elapsedRealtimeMs.getAsLong()), false);
        // Explicit seek / start position is intentional. If a transition guard is still armed,
        // move its expected anchor so we do not clamp a real seek back to the pre-seek start.
        if (pendingMediaItemIndex != C.INDEX_UNSET) {
            pendingMediaItemPositionMs = clamped;
        }
        if (transitionStartElapsedMs != Long.MIN_VALUE) {
            transitionExpectedPositionMs = clamped;
        }
    }

    private void seedPositionEstimate(long positionMs, long nowMs, boolean playing) {
        lastRawPositionMs = positionMs;
        estimatedPositionMs = positionMs;
        lastEstimateTimeMs = nowMs;
        lastPlaying = playing;
    }
}
