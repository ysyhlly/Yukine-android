package app.yukine

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.yukine.data.room.YukineDatabase
import java.util.concurrent.ConcurrentHashMap

/**
 * On-demand enrichment trigger invoked when a user opens an artist page that lacks
 * an avatar or description. Applies in-memory debouncing and checks for active jobs
 * before scheduling a lightweight Worker run.
 */
class ArtistEnrichmentTriggerImpl(context: Context) : ArtistEnrichmentTrigger {
    private val appContext = context.applicationContext
    private val lastTriggered = ConcurrentHashMap<String, Long>()

    override fun requestIfMissing(artistId: String) {
        val now = System.currentTimeMillis()
        val last = lastTriggered[artistId] ?: 0L
        if (now - last < TRIGGER_COOLDOWN_MS) return
        lastTriggered[artistId] = now
        if (hasActiveJob(artistId)) return
        val workManager = WorkManager.getInstance(appContext)
        workManager.enqueueUniqueWork(
            "artist_enrichment_$artistId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IdentityEnhancementWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf(KEY_ON_DEMAND to true))
                .build()
        )
    }

    private fun hasActiveJob(artistId: String): Boolean = runCatching {
        val database = YukineDatabase.getInstance(appContext)
        val dao = database.musicIdentityDao()
        val artist = dao.canonicalArtist(artistId)
        val artistKey = artist?.id ?: return@runCatching false
        dao.jobs("ARTIST", artistKey).any {
            it.status in setOf("PENDING", "RETRY", "RUNNING")
        }
    }.getOrDefault(false)

    private companion object {
        const val TRIGGER_COOLDOWN_MS = 60L * 60L * 1_000L
        const val KEY_ON_DEMAND = "on_demand"
    }
}
