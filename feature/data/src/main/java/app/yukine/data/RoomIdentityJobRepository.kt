package app.yukine.data

import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.MusicIdentityDao
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.IdentityJobRepository
import app.yukine.identity.IdentityJobStatus
import app.yukine.identity.IdentityResolutionJob
import java.util.concurrent.Callable

class RoomIdentityJobRepository(
    private val database: YukineDatabase
) : IdentityJobRepository {
    private val dao: MusicIdentityDao
        get() = database.musicIdentityDao()

    override fun readyJobs(now: Long, limit: Int): List<IdentityResolutionJob> =
        database.runInTransaction(Callable {
            val safeNow = now.coerceAtLeast(0L)
            val staleBefore = (safeNow - STALE_RUNNING_TIMEOUT_MS).coerceAtLeast(0L)
            dao.deleteRetryPeersForStaleRunningJobs(staleBefore)
            dao.recoverStaleRunningJobs(staleBefore, safeNow)
            dao.readyJobs(safeNow, limit.coerceIn(1, 100)).map { it.toModel() }
        })

    override fun claim(jobId: String, now: Long): IdentityResolutionJob? =
        database.runInTransaction(Callable {
            if (dao.claimJob(jobId.trim(), now.coerceAtLeast(0L)) != 1) {
                return@Callable null
            }
            requireJob(jobId).toModel()
        })

    override fun markSucceeded(jobId: String, now: Long) {
        update(jobId, IdentityJobStatus.SUCCEEDED, 0L, "", now)
    }

    override fun markRetry(jobId: String, nextAttemptAt: Long, error: String, now: Long) {
        require(nextAttemptAt >= now) { "Retry time cannot be in the past" }
        update(jobId, IdentityJobStatus.RETRY, nextAttemptAt, error, now)
    }

    override fun markFailed(jobId: String, error: String, now: Long) {
        update(jobId, IdentityJobStatus.FAILED, 0L, error, now)
    }

    private fun update(
        jobId: String,
        status: IdentityJobStatus,
        nextAttemptAt: Long,
        error: String,
        now: Long
    ) {
        database.runInTransaction {
            val current = requireJob(jobId)
            val attempts = if (status == IdentityJobStatus.RETRY || status == IdentityJobStatus.FAILED) {
                current.attemptCount + 1
            } else {
                current.attemptCount
            }
            // The schema intentionally keeps at most one row per target and status. A newer job
            // supersedes an older terminal/retry row before its own atomic state transition.
            dao.deleteJobStatusPeers(
                targetType = current.targetType,
                targetId = current.targetId,
                status = status.name,
                jobId = current.jobId
            )
            check(
                dao.updateJobStatus(
                    jobId = current.jobId,
                    status = status.name,
                    attemptCount = attempts,
                    nextAttemptAt = nextAttemptAt,
                    lastError = error.take(2_000),
                    updatedAt = now.coerceAtLeast(0L)
                ) == 1
            ) { "Job disappeared while updating status" }
        }
    }

    private fun requireJob(jobId: String): IdentityResolutionJobEntity =
        requireNotNull(dao.job(jobId.trim())) { "Unknown identity job $jobId" }

    internal companion object {
        const val STALE_RUNNING_TIMEOUT_MS = 15L * 60L * 1_000L
    }
}
