package app.echo.next.dashboard

/**
 * Gateway interface for Dashboard API endpoints.
 * Implementations: RemoteDashboardGateway (HTTP), FallbackDashboardGateway (local)
 */
interface DashboardGateway {
    /**
     * GET /dashboard/home
     * Fetch all homepage data in one call.
     */
    suspend fun home(): DashboardHomeResponse

    /**
     * GET /playback/state
     * Get current playback state.
     */
    suspend fun playbackState(): PlaybackStateResponse

    /**
     * POST /playback/play
     * Start or resume playback.
     */
    suspend fun play(): PlaybackActionResponse

    /**
     * POST /playback/pause
     * Pause playback.
     */
    suspend fun pause(): PlaybackActionResponse

    /**
     * POST /playback/toggle
     * Toggle play/pause state.
     */
    suspend fun toggle(): PlaybackActionResponse

    /**
     * POST /playback/next
     * Skip to next track.
     */
    suspend fun next(): PlaybackActionResponse

    /**
     * POST /playback/previous
     * Skip to previous track.
     */
    suspend fun previous(): PlaybackActionResponse

    /**
     * POST /playback/seek
     * Seek to position.
     */
    suspend fun seek(positionMs: Long): PlaybackActionResponse

    /**
     * GET /activity/recent?limit=N
     * Fetch recent activity.
     */
    suspend fun recentActivity(limit: Int = 20): RecentActivityResponse

    /**
     * GET /recap/weekly
     * Fetch weekly recap with heatmap.
     */
    suspend fun weeklyRecap(): WeeklyRecapResponse
}

/**
 * Exception thrown when gateway operations fail.
 */
class DashboardGatewayException(
    message: String,
    cause: Throwable? = null,
    val httpCode: Int = 0
) : RuntimeException(message, cause)
