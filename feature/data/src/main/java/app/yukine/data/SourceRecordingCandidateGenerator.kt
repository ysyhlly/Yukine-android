package app.yukine.data

import app.yukine.data.room.SourceMatchFeatureEntity
import app.yukine.data.room.SourceRecordingCandidateEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.streaming.RecordingVersionClassifier
import app.yukine.streaming.RecordingVersionType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * Bounded, metadata-only candidate generation for the offline/background ingestion path.
 *
 * Exact title/artist buckets and rare title n-gram postings provide recall without running the
 * expensive V2 evaluator against the whole library. The result is capped per source and persisted;
 * hard version conflicts remain visible as candidates but are rejected later by complete-link V2.
 */
internal class SourceRecordingCandidateGenerator(
    private val topK: Int = DEFAULT_TOP_K
) {
    init {
        require(topK in 1..MAX_TOP_K) { "topK must be between 1 and $MAX_TOP_K" }
    }

    fun generate(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        generatedAt: Long
    ): SourceCandidateGenerationResult {
        val descriptors = sources.mapNotNull { source ->
            descriptor(source, featuresBySourceId[source.sourceId])
        }
        val snapshotSignature = snapshotSignature(sources, featuresBySourceId)
        if (descriptors.isEmpty()) {
            return SourceCandidateGenerationResult(emptyList(), snapshotSignature, 0, 0)
        }

        val exactTitleArtist = HashMap<TitleArtistKey, MutableList<Descriptor>>()
        val exactTitle = HashMap<String, MutableList<Descriptor>>()
        val trigramPostings = HashMap<String, MutableList<Descriptor>>()
        val bigramPostings = HashMap<String, MutableList<Descriptor>>()
        descriptors.forEach { descriptor ->
            exactTitleArtist.getOrPut(
                TitleArtistKey(descriptor.coreTitle, descriptor.normalizedArtist)
            ) { ArrayList() } += descriptor
            exactTitle.getOrPut(descriptor.coreTitle) { ArrayList() } += descriptor
            descriptor.titleTrigrams.forEach { gram ->
                trigramPostings.getOrPut(gram) { ArrayList() } += descriptor
            }
            descriptor.titleBigrams.forEach { gram ->
                bigramPostings.getOrPut(gram) { ArrayList() } += descriptor
            }
        }

        var coarseComparisons = 0
        val rows = ArrayList<SourceRecordingCandidateEntity>(descriptors.size * topK)
        descriptors.forEach { query ->
            val pool = linkedSetOf<Descriptor>()
            exactTitleArtist[TitleArtistKey(query.coreTitle, query.normalizedArtist)]
                .orEmpty()
                .forEach(pool::add)
            exactTitle[query.coreTitle]
                .orEmpty()
                .asSequence()
                .filter { candidate ->
                    query.durationBucket < 0L || candidate.durationBucket < 0L ||
                        abs(query.durationBucket - candidate.durationBucket) <= EXACT_TITLE_DURATION_BUCKETS
                }
                .sortedWith(
                    compareBy<Descriptor> { candidate ->
                        if (query.durationBucket < 0L || candidate.durationBucket < 0L) {
                            Long.MAX_VALUE
                        } else {
                            abs(query.durationBucket - candidate.durationBucket)
                        }
                    }.thenBy(Descriptor::sourceId)
                )
                .take(MAX_EXACT_TITLE_POSTING)
                .forEach(pool::add)
            addRarePostings(query.titleTrigrams, trigramPostings, pool)
            if (pool.size < MIN_COARSE_POOL) {
                addRarePostings(query.titleBigrams, bigramPostings, pool)
            }

            val bestByRecording = HashMap<Long, RankedCandidate>()
            pool.forEach { candidate ->
                if (candidate.sourceId == query.sourceId || candidate.recordingId == query.recordingId) {
                    return@forEach
                }
                coarseComparisons++
                val ranked = rank(query, candidate)
                val current = bestByRecording[candidate.recordingId]
                if (current == null || ranked.isBetterThan(current)) {
                    bestByRecording[candidate.recordingId] = ranked
                }
            }
            bestByRecording.values
                .sortedWith(
                    compareByDescending<RankedCandidate>(RankedCandidate::coarseScore)
                        .thenBy(RankedCandidate::candidateRecordingId)
                        .thenBy(RankedCandidate::candidateSourceId)
                )
                .take(topK)
                .forEach { ranked ->
                    rows += SourceRecordingCandidateEntity(
                        sourceId = query.sourceId,
                        candidateRecordingId = ranked.candidateRecordingId,
                        coarseScore = ranked.coarseScore,
                        evidenceJson = ranked.evidenceJson(),
                        algorithmVersion = ALGORITHM_VERSION,
                        updatedAt = generatedAt
                    )
                }
        }
        return SourceCandidateGenerationResult(
            candidates = rows,
            snapshotSignature = snapshotSignature,
            coarseComparisonCount = coarseComparisons,
            sourceCount = descriptors.size
        )
    }

    fun snapshotSignature(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>
    ): String {
        val payload = sources.asSequence()
            .mapNotNull { source ->
                val sourceId = source.sourceId ?: return@mapNotNull null
                val feature = featuresBySourceId[sourceId] ?: return@mapNotNull null
                "$sourceId|${source.recordingId}|${feature.algorithmVersion}|${feature.metadataSignature}"
            }
            .sorted()
            .joinToString("\u001E")
        return sha256(payload)
    }

    private fun descriptor(
        source: TrackSourceMappingEntity,
        feature: SourceMatchFeatureEntity?
    ): Descriptor? {
        val sourceId = source.sourceId ?: return null
        val current = feature
            ?.takeIf { it.algorithmVersion == SourceMatchFeaturePolicy.ALGORITHM_VERSION }
            ?: return null
        if (current.coreTitle.isBlank()) return null
        return Descriptor(
            sourceId = sourceId,
            recordingId = source.recordingId,
            coreTitle = current.coreTitle,
            normalizedArtist = current.normalizedArtist,
            durationBucket = current.durationBucket,
            versionType = current.versionType.toVersionType(),
            versionSignature = current.versionSignature,
            titleBigrams = decodeSet(current.titleBigrams),
            titleTrigrams = decodeSet(current.titleTrigrams)
        )
    }

    private fun addRarePostings(
        grams: Set<String>,
        postings: Map<String, List<Descriptor>>,
        destination: MutableSet<Descriptor>
    ) {
        grams.asSequence()
            .mapNotNull { gram -> postings[gram]?.let { gram to it } }
            .filter { (_, rows) -> rows.size <= MAX_NGRAM_POSTING }
            .sortedWith(compareBy<Pair<String, List<Descriptor>>> { it.second.size }.thenBy { it.first })
            .take(MAX_RARE_GRAMS)
            .forEach { (_, rows) -> rows.forEach(destination::add) }
    }

    private fun rank(query: Descriptor, candidate: Descriptor): RankedCandidate {
        val titleScore = if (query.coreTitle == candidate.coreTitle) {
            1.0
        } else {
            maxOf(
                dice(query.titleTrigrams, candidate.titleTrigrams),
                dice(query.titleBigrams, candidate.titleBigrams)
            )
        }
        val artistScore = when {
            query.normalizedArtist.isBlank() || candidate.normalizedArtist.isBlank() -> 0.0
            query.normalizedArtist == candidate.normalizedArtist -> 1.0
            else -> 0.0
        }
        val durationScore = if (query.durationBucket < 0L || candidate.durationBucket < 0L) {
            0.5
        } else {
            (1.0 - abs(query.durationBucket - candidate.durationBucket) / 6.0).coerceIn(0.0, 1.0)
        }
        val hardVersionConflict = RecordingVersionClassifier.hardConflict(
            query.versionType,
            candidate.versionType,
            query.versionSignature,
            candidate.versionSignature
        )
        val versionScore = if (hardVersionConflict) 0.0 else 1.0
        val coarseScore = (
            titleScore * 0.60 +
                artistScore * 0.25 +
                durationScore * 0.10 +
                versionScore * 0.05
            ).coerceIn(0.0, 1.0)
        return RankedCandidate(
            candidateRecordingId = candidate.recordingId,
            candidateSourceId = candidate.sourceId,
            coarseScore = coarseScore,
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            hardVersionConflict = hardVersionConflict
        )
    }

    private fun dice(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return 2.0 * left.intersect(right).size / (left.size + right.size).toDouble()
    }

    private fun decodeSet(value: String): Set<String> = value
        .split(FEATURE_SEPARATOR)
        .filter(String::isNotBlank)
        .toSet()

    private fun String.toVersionType(): RecordingVersionType = runCatching {
        RecordingVersionType.valueOf(this)
    }.getOrDefault(RecordingVersionType.UNKNOWN)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class Descriptor(
        val sourceId: Long,
        val recordingId: Long,
        val coreTitle: String,
        val normalizedArtist: String,
        val durationBucket: Long,
        val versionType: RecordingVersionType,
        val versionSignature: String,
        val titleBigrams: Set<String>,
        val titleTrigrams: Set<String>
    )

    private data class TitleArtistKey(
        val title: String,
        val artist: String
    )

    private data class RankedCandidate(
        val candidateRecordingId: Long,
        val candidateSourceId: Long,
        val coarseScore: Double,
        val titleScore: Double,
        val artistScore: Double,
        val durationScore: Double,
        val hardVersionConflict: Boolean
    ) {
        fun isBetterThan(other: RankedCandidate): Boolean =
            coarseScore > other.coarseScore ||
                (coarseScore == other.coarseScore && candidateSourceId < other.candidateSourceId)

        fun evidenceJson(): String = String.format(
            Locale.ROOT,
            "{\"titleScore\":%.4f,\"artistScore\":%.4f," +
                "\"durationScore\":%.4f,\"hardVersionConflict\":%s," +
                "\"candidateSourceId\":%d}",
            titleScore,
            artistScore,
            durationScore,
            hardVersionConflict,
            candidateSourceId
        )
    }

    internal companion object {
        const val ALGORITHM_VERSION = 1
        const val DEFAULT_TOP_K = 20
        const val MAX_TOP_K = 20
        private const val FEATURE_SEPARATOR = '\u001F'
        private const val MAX_RARE_GRAMS = 6
        private const val MAX_NGRAM_POSTING = 128
        private const val MAX_EXACT_TITLE_POSTING = 128
        private const val EXACT_TITLE_DURATION_BUCKETS = 2L
        private const val MIN_COARSE_POOL = 8
    }
}

internal data class SourceCandidateGenerationResult(
    val candidates: List<SourceRecordingCandidateEntity>,
    val snapshotSignature: String,
    val coarseComparisonCount: Int,
    val sourceCount: Int
)
