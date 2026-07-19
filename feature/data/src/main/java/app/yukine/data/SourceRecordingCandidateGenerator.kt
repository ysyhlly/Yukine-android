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

internal enum class CandidateRecallSource {
    EXACT_TITLE_ARTIST,
    EXACT_TITLE,
    TRIGRAM,
    BIGRAM,
    EMBEDDING_LSH
}

internal class SourceRecordingCandidateGenerator @JvmOverloads constructor(
    private val topK: Int = DEFAULT_TOP_K
) {
    init {
        require(topK in 1..MAX_TOP_K) { "topK must be between 1 and $MAX_TOP_K" }
    }

    fun generate(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        generatedAt: Long,
        mode: EmbeddingRecallMode = EmbeddingRecallMode.OFF
    ): SourceCandidateGenerationResult {
        val descriptors = sources.mapNotNull { source ->
            descriptor(source, featuresBySourceId[source.sourceId])
        }
        val snapshotSignature = snapshotSignature(sources, featuresBySourceId, mode)
        if (descriptors.isEmpty()) {
            return SourceCandidateGenerationResult(
                candidates = emptyList(),
                shadowCandidates = emptyList(),
                snapshotSignature = snapshotSignature,
                coarseComparisonCount = 0,
                sourceCount = 0
            )
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
        val descriptorsBySourceId = descriptors.associateBy(Descriptor::sourceId)
        val embeddingIndex = EmbeddingPostingIndex(
            descriptors.mapNotNull { descriptor ->
                descriptor.simHash?.let { simHash -> EmbeddingIndexItem(descriptor.sourceId, simHash) }
            }
        )

        var coarseComparisons = 0
        val activeRows = ArrayList<SourceRecordingCandidateEntity>(descriptors.size * topK)
        val shadowRows = ArrayList<SourceRecordingCandidateEntity>()
        descriptors.forEach { query ->
            val baselinePool = linkedMapOf<Descriptor, MutableSet<CandidateRecallSource>>()
            exactTitleArtist[TitleArtistKey(query.coreTitle, query.normalizedArtist)]
                .orEmpty()
                .forEach { candidate ->
                    baselinePool.add(candidate, CandidateRecallSource.EXACT_TITLE_ARTIST)
                }
            exactTitle[query.coreTitle]
                .orEmpty()
                .asSequence()
                .filter { candidate ->
                    query.durationBucket < 0L || candidate.durationBucket < 0L ||
                        abs(query.durationBucket - candidate.durationBucket) <= EXACT_TITLE_DURATION_BUCKETS
                }
                .sortedWith(
                    compareBy<Descriptor> { candidate ->
                        durationDistance(query, candidate)
                    }.thenBy(Descriptor::sourceId)
                )
                .take(MAX_EXACT_TITLE_POSTING)
                .forEach { candidate ->
                    baselinePool.add(candidate, CandidateRecallSource.EXACT_TITLE)
                }
            addRarePostings(
                query.titleTrigrams,
                trigramPostings,
                baselinePool,
                CandidateRecallSource.TRIGRAM
            )
            if (baselinePool.size < MIN_COARSE_POOL) {
                addRarePostings(
                    query.titleBigrams,
                    bigramPostings,
                    baselinePool,
                    CandidateRecallSource.BIGRAM
                )
            }
            baselinePool.removeIneligible(query)

            val baselineBest = HashMap<Long, RankedCandidate>()
            baselinePool.forEach { (candidate, recallSources) ->
                coarseComparisons++
                baselineBest.keepBest(rank(query, candidate, recallSources, bandMatches = 0, fused = false))
            }
            val baselineTop = baselineBest.values.sortedCandidates().take(topK)

            if (mode == EmbeddingRecallMode.OFF || query.simHash == null) {
                baselineTop.forEach { ranked ->
                    activeRows += ranked.toEntity(query.sourceId, generatedAt, STATE_GENERATED)
                }
                return@forEach
            }

            val embeddingHits = embeddingIndex.recall(query.simHash, query.sourceId)
                .asSequence()
                .mapNotNull { hit ->
                    descriptorsBySourceId[hit.sourceId]?.let { descriptor -> descriptor to hit.bandMatches }
                }
                .filter { (candidate, _) -> candidate.recordingId != query.recordingId }
                .sortedWith(
                    compareByDescending<Pair<Descriptor, Int>> { (_, bandMatches) -> bandMatches }
                        .thenByDescending { (candidate, _) ->
                            candidate.normalizedArtist.isNotBlank() &&
                                candidate.normalizedArtist == query.normalizedArtist
                        }
                        .thenBy { (candidate, _) -> durationDistance(query, candidate) }
                        .thenBy { (candidate, _) -> candidate.sourceId }
                )
                .take(MAX_INTERNAL_EMBEDDING_CANDIDATES)
                .toList()
            val bandMatchesBySourceId = embeddingHits.associate { (candidate, count) ->
                candidate.sourceId to count
            }
            val fusedPool = linkedMapOf<Descriptor, MutableSet<CandidateRecallSource>>()
            baselinePool.forEach { (candidate, origins) ->
                fusedPool.getOrPut(candidate) { linkedSetOf() }.addAll(origins)
            }
            embeddingHits.forEach { (candidate, _) ->
                fusedPool.add(candidate, CandidateRecallSource.EMBEDDING_LSH)
            }

            val fusedBest = HashMap<Long, RankedCandidate>()
            fusedPool.forEach { (candidate, recallSources) ->
                coarseComparisons++
                val bandMatches = bandMatchesBySourceId[candidate.sourceId] ?: 0
                val effectiveSources = recallSources.toMutableSet()
                if (bandMatches > 0) effectiveSources += CandidateRecallSource.EMBEDDING_LSH
                fusedBest.keepBest(
                    rank(query, candidate, effectiveSources, bandMatches, fused = true)
                )
            }
            val enhancedTop = selectEnhanced(baselineTop, fusedBest.values)
            when (mode) {
                EmbeddingRecallMode.OFF -> Unit
                EmbeddingRecallMode.SHADOW -> {
                    baselineTop.forEach { ranked ->
                        activeRows += ranked.toEntity(query.sourceId, generatedAt, STATE_GENERATED)
                    }
                    val baselineRecordingIds = baselineTop.mapTo(hashSetOf()) {
                        it.candidateRecordingId
                    }
                    enhancedTop.asSequence()
                        .filter { ranked -> ranked.candidateRecordingId !in baselineRecordingIds }
                        .take(MAX_EMBEDDING_ONLY_TOP_K)
                        .forEach { ranked ->
                            shadowRows += ranked.toEntity(query.sourceId, generatedAt, STATE_SHADOW)
                        }
                }
                EmbeddingRecallMode.ON -> enhancedTop.forEach { ranked ->
                    activeRows += ranked.toEntity(query.sourceId, generatedAt, STATE_GENERATED)
                }
            }
        }
        return SourceCandidateGenerationResult(
            candidates = activeRows,
            shadowCandidates = shadowRows,
            snapshotSignature = snapshotSignature,
            coarseComparisonCount = coarseComparisons,
            sourceCount = descriptors.size
        )
    }

    fun snapshotSignature(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        mode: EmbeddingRecallMode = EmbeddingRecallMode.OFF
    ): String {
        val payload = sources.asSequence()
            .mapNotNull { source ->
                val sourceId = source.sourceId ?: return@mapNotNull null
                val feature = featuresBySourceId[sourceId] ?: return@mapNotNull null
                "$sourceId|${source.recordingId}|${feature.algorithmVersion}|" +
                    "${feature.metadataSignature}|${feature.metadataVectorVersion}|" +
                    "${feature.metadataSimHash ?: 0L}|${mode.name}"
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
        val validEmbedding = current.metadataVector
            ?.takeIf { vector ->
                current.metadataVectorVersion == MetadataHashEmbeddingEncoder.VECTOR_VERSION &&
                    vector.size == MetadataHashEmbeddingEncoder.DIMENSIONS
            }
        return Descriptor(
            sourceId = sourceId,
            recordingId = source.recordingId,
            coreTitle = current.coreTitle,
            normalizedArtist = current.normalizedArtist,
            durationBucket = current.durationBucket,
            versionType = current.versionType.toVersionType(),
            versionSignature = current.versionSignature,
            titleBigrams = decodeSet(current.titleBigrams),
            titleTrigrams = decodeSet(current.titleTrigrams),
            metadataVector = validEmbedding,
            embeddingVersion = if (validEmbedding == null) 0 else current.metadataVectorVersion,
            simHash = if (validEmbedding == null) null else current.metadataSimHash
        )
    }

    private fun addRarePostings(
        grams: Set<String>,
        postings: Map<String, List<Descriptor>>,
        destination: MutableMap<Descriptor, MutableSet<CandidateRecallSource>>,
        origin: CandidateRecallSource
    ) {
        grams.asSequence()
            .mapNotNull { gram -> postings[gram]?.let { gram to it } }
            .filter { (_, rows) -> rows.size <= MAX_NGRAM_POSTING }
            .sortedWith(compareBy<Pair<String, List<Descriptor>>> { it.second.size }.thenBy { it.first })
            .take(MAX_RARE_GRAMS)
            .forEach { (_, rows) ->
                rows.forEach { candidate -> destination.add(candidate, origin) }
            }
    }

    private fun rank(
        query: Descriptor,
        candidate: Descriptor,
        recallSources: Set<CandidateRecallSource>,
        bandMatches: Int,
        fused: Boolean
    ): RankedCandidate {
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
        val baselineScore = (
            titleScore * 0.60 +
                artistScore * 0.25 +
                durationScore * 0.10 +
                versionScore * 0.05
            ).coerceIn(0.0, 1.0)
        val embeddingSimilarity = if (fused) {
            MetadataHashEmbeddingEncoder.cosine(query.metadataVector, candidate.metadataVector)
        } else {
            0.0
        }
        val coarseScore = if (fused) {
            (
                titleScore * 0.50 +
                    artistScore * 0.20 +
                    durationScore * 0.10 +
                    versionScore * 0.05 +
                    embeddingSimilarity * 0.10 +
                    (bandMatches.toDouble() / EmbeddingPostingIndex.BAND_COUNT) * 0.05
                ).coerceIn(0.0, 1.0)
        } else {
            baselineScore
        }
        return RankedCandidate(
            candidateRecordingId = candidate.recordingId,
            candidateSourceId = candidate.sourceId,
            coarseScore = coarseScore,
            baselineScore = baselineScore,
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            hardVersionConflict = hardVersionConflict,
            recallSources = recallSources.toSortedSet(compareBy(CandidateRecallSource::name)),
            embeddingSimilarity = embeddingSimilarity,
            embeddingVersion = minOf(query.embeddingVersion, candidate.embeddingVersion),
            bandMatches = bandMatches
        )
    }

    private fun selectEnhanced(
        baselineTop: List<RankedCandidate>,
        fusedCandidates: Collection<RankedCandidate>
    ): List<RankedCandidate> {
        val fusedByRecording = fusedCandidates.associateBy(RankedCandidate::candidateRecordingId)
        val selected = LinkedHashMap<Long, RankedCandidate>()
        baselineTop.take(BASELINE_RESERVED_TOP_K).forEach { baseline ->
            selected[baseline.candidateRecordingId] = fusedByRecording[baseline.candidateRecordingId] ?: baseline
        }
        val baselineRecordingIds = baselineTop.mapTo(hashSetOf()) { it.candidateRecordingId }
        fusedCandidates.sortedCandidates()
            .asSequence()
            .filter { candidate ->
                candidate.candidateRecordingId !in baselineRecordingIds &&
                    CandidateRecallSource.EMBEDDING_LSH in candidate.recallSources
            }
            .take(MAX_EMBEDDING_ONLY_TOP_K)
            .forEach { candidate -> selected[candidate.candidateRecordingId] = candidate }
        (fusedCandidates.sortedCandidates() + baselineTop)
            .asSequence()
            .filter { candidate -> candidate.candidateRecordingId !in selected }
            .take(topK - selected.size)
            .forEach { candidate -> selected[candidate.candidateRecordingId] = candidate }
        return selected.values.sortedCandidates().take(topK)
    }

    private fun dice(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return 2.0 * left.intersect(right).size / (left.size + right.size).toDouble()
    }

    private fun durationDistance(left: Descriptor, right: Descriptor): Long =
        if (left.durationBucket < 0L || right.durationBucket < 0L) {
            Long.MAX_VALUE
        } else {
            abs(left.durationBucket - right.durationBucket)
        }

    private fun MutableMap<Descriptor, MutableSet<CandidateRecallSource>>.add(
        candidate: Descriptor,
        source: CandidateRecallSource
    ) {
        getOrPut(candidate) { linkedSetOf() } += source
    }

    private fun MutableMap<Descriptor, MutableSet<CandidateRecallSource>>.removeIneligible(
        query: Descriptor
    ) {
        keys.removeAll { candidate ->
            candidate.sourceId == query.sourceId || candidate.recordingId == query.recordingId
        }
    }

    private fun MutableMap<Long, RankedCandidate>.keepBest(candidate: RankedCandidate) {
        val current = this[candidate.candidateRecordingId]
        if (current == null || candidate.isBetterThan(current)) {
            this[candidate.candidateRecordingId] = candidate
        }
    }

    private fun Collection<RankedCandidate>.sortedCandidates(): List<RankedCandidate> =
        sortedWith(
            compareByDescending<RankedCandidate>(RankedCandidate::coarseScore)
                .thenBy(RankedCandidate::candidateRecordingId)
                .thenBy(RankedCandidate::candidateSourceId)
        )

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
        val titleTrigrams: Set<String>,
        val metadataVector: ByteArray?,
        val embeddingVersion: Int,
        val simHash: Long?
    )

    private data class TitleArtistKey(
        val title: String,
        val artist: String
    )

    private data class RankedCandidate(
        val candidateRecordingId: Long,
        val candidateSourceId: Long,
        val coarseScore: Double,
        val baselineScore: Double,
        val titleScore: Double,
        val artistScore: Double,
        val durationScore: Double,
        val hardVersionConflict: Boolean,
        val recallSources: Set<CandidateRecallSource>,
        val embeddingSimilarity: Double,
        val embeddingVersion: Int,
        val bandMatches: Int
    ) {
        fun isBetterThan(other: RankedCandidate): Boolean =
            coarseScore > other.coarseScore ||
                (coarseScore == other.coarseScore && candidateSourceId < other.candidateSourceId)

        fun toEntity(
            sourceId: Long,
            generatedAt: Long,
            state: String
        ): SourceRecordingCandidateEntity = SourceRecordingCandidateEntity(
            sourceId = sourceId,
            candidateRecordingId = candidateRecordingId,
            coarseScore = coarseScore,
            evidenceJson = evidenceJson(),
            state = state,
            algorithmVersion = ALGORITHM_VERSION,
            updatedAt = generatedAt
        )

        private fun evidenceJson(): String {
            val sources = recallSources.joinToString(",") { source -> "\"${source.name}\"" }
            return String.format(
                Locale.ROOT,
                "{\"titleScore\":%.4f,\"artistScore\":%.4f,\"durationScore\":%.4f," +
                    "\"baselineScore\":%.4f,\"hardVersionConflict\":%s," +
                    "\"candidateSourceId\":%d,\"candidateSources\":[%s]," +
                    "\"bandMatches\":%d,\"embeddingSimilarity\":%.4f," +
                    "\"embeddingVersion\":%d,\"embeddingRecallOnly\":true}",
                titleScore,
                artistScore,
                durationScore,
                baselineScore,
                hardVersionConflict,
                candidateSourceId,
                sources,
                bandMatches,
                embeddingSimilarity,
                embeddingVersion
            )
        }
    }

    internal companion object {
        const val ALGORITHM_VERSION = 7
        const val DEFAULT_TOP_K = 20
        const val MAX_TOP_K = 20
        const val MAX_INTERNAL_EMBEDDING_CANDIDATES = 100
        const val BASELINE_RESERVED_TOP_K = 12
        const val MAX_EMBEDDING_ONLY_TOP_K = 8
        const val STATE_GENERATED = "GENERATED"
        const val STATE_SHADOW = "SHADOW"
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
    val shadowCandidates: List<SourceRecordingCandidateEntity>,
    val snapshotSignature: String,
    val coarseComparisonCount: Int,
    val sourceCount: Int
)
