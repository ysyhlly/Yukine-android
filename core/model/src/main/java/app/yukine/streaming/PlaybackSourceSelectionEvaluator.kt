package app.yukine.streaming

import java.util.Locale
import kotlin.math.exp

/** Playback-source quality is intentionally independent from recording/work identity scores. */
data class PlaybackSourceSelectionFeatures(
    val sourceId: Long,
    val provider: String,
    val playable: Boolean,
    val confirmed: Boolean,
    val qualityScore: Int,
    val lastSuccessfulAt: Long,
    val lastVerifiedAt: Long,
    val failureCount: Int
)

data class PlaybackSourceSelectionEvaluation(
    val source: PlaybackSourceSelectionFeatures,
    val sourceSelectionScore: Double,
    val eligible: Boolean,
    val reasons: Set<String>
)

object PlaybackSourceSelectionEvaluator {
    private const val RECENCY_WINDOW_MS = 30L * 24L * 60L * 60L * 1_000L

    fun evaluate(
        source: PlaybackSourceSelectionFeatures,
        now: Long = System.currentTimeMillis()
    ): PlaybackSourceSelectionEvaluation {
        val provider = ProviderRolePolicy.normalize(source.provider)
        val physical = ProviderRolePolicy.isPhysical(provider)
        val verified = source.lastVerifiedAt > 0L || source.lastSuccessfulAt > 0L
        val reasons = linkedSetOf<String>()
        if (!source.playable) reasons += "unplayable"
        if (!source.confirmed) reasons += "unconfirmed"
        if (!physical && !verified) reasons += "remote_unverified"
        if (!ProviderRolePolicy.canEverBecomeActive(provider)) reasons += "provider_not_active"
        val eligible = source.playable && source.confirmed && (physical || verified) &&
            ProviderRolePolicy.canEverBecomeActive(provider)
        if (!eligible) {
            return PlaybackSourceSelectionEvaluation(source, 0.0, false, reasons)
        }

        val providerScore = providerPreference(provider)
        val quality = (source.qualityScore.coerceIn(0, 1_000) / 1_000.0)
        val recency = recencyScore(source, now.coerceAtLeast(0L), physical)
        val health = (1.0 - source.failureCount.coerceAtLeast(0) * 0.12).coerceIn(0.0, 1.0)
        reasons += if (physical) "physical" else "verified_remote"
        if (quality > 0.0) reasons += "quality"
        if (source.lastSuccessfulAt > 0L) reasons += "recent_success"
        if (source.failureCount > 0) reasons += "failure_penalty"
        val score = providerScore * 0.35 + quality * 0.35 + recency * 0.20 + health * 0.10
        return PlaybackSourceSelectionEvaluation(
            source = source,
            sourceSelectionScore = score.coerceIn(0.0, 1.0),
            eligible = true,
            reasons = reasons
        )
    }

    fun rank(
        sources: List<PlaybackSourceSelectionFeatures>,
        now: Long = System.currentTimeMillis(),
        neteasePlaybackEnabled: Boolean = true
    ): List<PlaybackSourceSelectionEvaluation> {
        val evaluated = sources.asSequence()
            .map { source -> evaluate(source, now) }
            .filter(PlaybackSourceSelectionEvaluation::eligible)
            .toList()
        val hasPhysical = evaluated.any { ProviderRolePolicy.isPhysical(it.source.provider) }
        return evaluated.asSequence()
            .filter { evaluation ->
                ProviderRolePolicy.canBecomeActive(
                    evaluation.source.provider,
                    neteasePlaybackEnabled,
                    hasPhysical
                )
            }
        .sortedWith(
            compareByDescending<PlaybackSourceSelectionEvaluation> { it.sourceSelectionScore }
                .thenByDescending { it.source.lastSuccessfulAt }
                .thenByDescending { it.source.qualityScore }
                .thenByDescending { providerPreference(it.source.provider.trim().lowercase(Locale.ROOT)) }
                .thenBy { it.source.sourceId }
        )
        .toList()
    }

    private fun recencyScore(
        source: PlaybackSourceSelectionFeatures,
        now: Long,
        physical: Boolean
    ): Double {
        val lastHealthyAt = maxOf(source.lastSuccessfulAt, source.lastVerifiedAt)
        if (lastHealthyAt <= 0L) return if (physical) 0.65 else 0.0
        val age = (now - lastHealthyAt).coerceAtLeast(0L)
        return exp(-age.toDouble() / RECENCY_WINDOW_MS)
    }

    private fun providerPreference(provider: String): Double = when (provider) {
        "local" -> 1.00
        "webdav" -> 0.90
        "document" -> 0.85
        "stream" -> 0.75
        "netease" -> 0.65
        "qqmusic" -> 0.55
        "luoxue" -> 0.50
        else -> 0.40
    }
}
