package app.yukine.data

import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.streaming.PlaybackSourcePolicy
import app.yukine.streaming.PlaybackSourceSelectionEvaluator
import app.yukine.streaming.PlaybackSourceSelectionFeatures
import app.yukine.streaming.StreamingProviderName

/** The only persisted active-source selector used by library playback and queue restoration. */
internal class PolicyAwarePlaybackSourceSelector(
    database: YukineDatabase,
    private val policy: PlaybackSourcePolicy
) {
    private val dao = database.musicIdentityDao()

    fun select(recordingId: Long): TrackSourceMappingEntity? {
        val sources = dao.sources(recordingId)
        if (sources.isEmpty()) {
            dao.updateActiveSource(recordingId, null)
            return null
        }
        val byId = sources.mapNotNull { source -> source.sourceId?.let { it to source } }.toMap()
        val ranked = PlaybackSourceSelectionEvaluator.rank(
            sources = sources.mapNotNull { source ->
                source.sourceId?.let { sourceId ->
                    PlaybackSourceSelectionFeatures(
                        sourceId = sourceId,
                        provider = source.provider,
                        playable = source.playable,
                        confirmed = source.matchStatus == "CONFIRMED",
                        qualityScore = source.qualityScore,
                        lastSuccessfulAt = source.lastSuccessfulAt,
                        lastVerifiedAt = source.lastVerifiedAt,
                        failureCount = source.failureCount
                    )
                }
            },
            now = System.currentTimeMillis(),
            neteasePlaybackEnabled = policy.isEnabled(StreamingProviderName.NETEASE)
        )
        val winnerId = ranked.firstOrNull()?.source?.sourceId
        dao.updateActiveSource(recordingId, winnerId)
        return winnerId?.let(byId::get)
    }
}
