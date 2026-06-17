package app.yukine

internal class StreamingPlaybackTaskQueueAdapter(
    private val scheduler: StreamingPlaybackTaskScheduler
) : StreamingPlaybackTaskQueue {
    override fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask) {
        schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, task)
    }

    override fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask) {
        schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, task)
    }

    override fun scheduleNextUrlResolve(task: StreamingPlaybackTask) {
        schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, task)
    }

    private fun schedule(
        priority: StreamingPlaybackTaskScheduler.Priority,
        task: StreamingPlaybackTask
    ) {
        scheduler.schedule(priority) { completion ->
            task.run { completion.complete() }
        }
    }
}
