package app.yukine.data

import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.StreamingTrackMatchPolicy
import kotlin.math.max
import org.json.JSONObject

internal data class RecordingIdentityGoldSide(
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val releaseType: String = "",
    val year: Int = 0,
    val durationMs: Long = 0L,
    val versionSignature: String = ""
)

internal data class RecordingIdentityGoldPair(
    val pairId: String,
    val label: ManualMatchLabel,
    val left: RecordingIdentityGoldSide,
    val right: RecordingIdentityGoldSide,
    val note: String = ""
)

internal data class RecordingIdentityBenchmarkMetrics(
    val recallAt20: Double,
    val candidateCount: Int,
    val evaluationCount: Int,
    val falseMergeCount: Int,
    val elapsedMs: Long,
    val peakIncrementalMemoryBytes: Long
)

internal data class RecordingIdentityBenchmarkComparison(
    val schemaVersion: Int,
    val pairCount: Int,
    val baselineV6: RecordingIdentityBenchmarkMetrics,
    val enhancedV7: RecordingIdentityBenchmarkMetrics
)

/** Parses schemaVersion=1 gold JSONL and compares the frozen V6-equivalent OFF path with V7 ON. */
internal class RecordingIdentityBenchmarkRunner {
    fun run(jsonl: String): RecordingIdentityBenchmarkComparison = run(parse(jsonl))

    fun run(pairs: List<RecordingIdentityGoldPair>): RecordingIdentityBenchmarkComparison {
        require(pairs.isNotEmpty()) { "At least one gold pair is required" }
        require(pairs.map(RecordingIdentityGoldPair::pairId).distinct().size == pairs.size) {
            "Gold pair IDs must be unique"
        }
        val fixture = buildFixture(pairs)
        return RecordingIdentityBenchmarkComparison(
            schemaVersion = SCHEMA_VERSION,
            pairCount = pairs.size,
            baselineV6 = measure(fixture, EmbeddingRecallMode.OFF),
            enhancedV7 = measure(fixture, EmbeddingRecallMode.ON)
        )
    }

    private fun measure(
        fixture: BenchmarkFixture,
        mode: EmbeddingRecallMode
    ): RecordingIdentityBenchmarkMetrics {
        val runtime = Runtime.getRuntime()
        val beforeMemory = usedMemory(runtime)
        val startedAt = System.nanoTime()
        val generated = SourceRecordingCandidateGenerator().generate(
            sources = fixture.sources,
            featuresBySourceId = fixture.features,
            generatedAt = 1L,
            mode = mode
        )
        val falseMergePairIds = hashSetOf<String>()
        generated.candidates.forEach { candidate ->
            val left = fixture.sideBySourceId[candidate.sourceId] ?: return@forEach
            val right = fixture.sideByRecordingId[candidate.candidateRecordingId] ?: return@forEach
            val evaluation = RecordingMatchEvaluatorV2.evaluate(left.toReference(), right.toReference())
            val gold = fixture.labelByCandidate[
                candidate.sourceId to candidate.candidateRecordingId
            ]
            if (gold != null &&
                gold.label != ManualMatchLabel.SAME &&
                !evaluation.hasHardConflict &&
                evaluation.sameRecordingProbability >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE
            ) {
                falseMergePairIds += gold.pairId
            }
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        val afterMemory = usedMemory(runtime)
        val candidateKeys = generated.candidates
            .mapTo(hashSetOf()) { it.sourceId to it.candidateRecordingId }
        val samePairs = fixture.pairs.filter { it.gold.label == ManualMatchLabel.SAME }
        val recalled = samePairs.count { pair ->
            (pair.leftSourceId to pair.rightRecordingId) in candidateKeys ||
                (pair.rightSourceId to pair.leftRecordingId) in candidateKeys
        }
        return RecordingIdentityBenchmarkMetrics(
            recallAt20 = if (samePairs.isEmpty()) 1.0 else recalled.toDouble() / samePairs.size,
            candidateCount = generated.candidates.size,
            evaluationCount = generated.candidates.size,
            falseMergeCount = falseMergePairIds.size,
            elapsedMs = elapsedMs,
            peakIncrementalMemoryBytes = max(0L, afterMemory - beforeMemory)
        )
    }

    private fun buildFixture(pairs: List<RecordingIdentityGoldPair>): BenchmarkFixture {
        val sources = ArrayList<TrackSourceMappingEntity>(pairs.size * 2)
        val featureMap = linkedMapOf<Long, app.yukine.data.room.SourceMatchFeatureEntity>()
        val pairFixtures = ArrayList<BenchmarkPair>(pairs.size)
        val sideBySourceId = linkedMapOf<Long, RecordingIdentityGoldSide>()
        val sideByRecordingId = linkedMapOf<Long, RecordingIdentityGoldSide>()
        val labelByCandidate = linkedMapOf<Pair<Long, Long>, LabeledCandidate>()
        pairs.forEachIndexed { index, gold ->
            val leftRecordingId = index * 2L + 1L
            val rightRecordingId = leftRecordingId + 1L
            val leftSourceId = leftRecordingId
            val rightSourceId = rightRecordingId
            val left = gold.left.toSource(leftSourceId, leftRecordingId)
            val right = gold.right.toSource(rightSourceId, rightRecordingId)
            sources += left
            sources += right
            SourceMatchFeaturePolicy.build(left, updatedAt = 1L)?.let { featureMap[leftSourceId] = it }
            SourceMatchFeaturePolicy.build(right, updatedAt = 1L)?.let { featureMap[rightSourceId] = it }
            sideBySourceId[leftSourceId] = gold.left
            sideBySourceId[rightSourceId] = gold.right
            sideByRecordingId[leftRecordingId] = gold.left
            sideByRecordingId[rightRecordingId] = gold.right
            val labeled = LabeledCandidate(gold.pairId, gold.label)
            labelByCandidate[leftSourceId to rightRecordingId] = labeled
            labelByCandidate[rightSourceId to leftRecordingId] = labeled
            pairFixtures += BenchmarkPair(
                gold = gold,
                leftSourceId = leftSourceId,
                rightSourceId = rightSourceId,
                leftRecordingId = leftRecordingId,
                rightRecordingId = rightRecordingId
            )
        }
        return BenchmarkFixture(
            sources,
            featureMap,
            pairFixtures,
            sideBySourceId,
            sideByRecordingId,
            labelByCandidate
        )
    }

    private fun parse(jsonl: String): List<RecordingIdentityGoldPair> =
        jsonl.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapIndexed { index, line ->
                val json = runCatching { JSONObject(line) }.getOrElse {
                    throw IllegalArgumentException("Invalid JSONL at line ${index + 1}", it)
                }
                require(json.optInt("schemaVersion") == SCHEMA_VERSION) {
                    "Unsupported schemaVersion at line ${index + 1}"
                }
                val label = runCatching { ManualMatchLabel.valueOf(json.getString("label")) }
                    .getOrElse { throw IllegalArgumentException("Invalid label at line ${index + 1}", it) }
                RecordingIdentityGoldPair(
                    pairId = json.getString("pairId").trim().also {
                        require(it.isNotEmpty()) { "Missing pairId at line ${index + 1}" }
                    },
                    label = label,
                    left = json.getJSONObject("left").toGoldSide(),
                    right = json.getJSONObject("right").toGoldSide(),
                    note = json.optString("note").trim()
                )
            }
            .toList()

    private fun JSONObject.toGoldSide(): RecordingIdentityGoldSide = RecordingIdentityGoldSide(
        title = optString("title").trim(),
        artist = optString("artist").trim(),
        album = optString("album").trim(),
        albumArtist = optString("albumArtist").trim(),
        composer = optString("composer").trim(),
        releaseType = optString("releaseType").trim(),
        year = optInt("year").takeIf { it in 1000..9999 } ?: 0,
        durationMs = optLong("durationMs").coerceAtLeast(0L),
        versionSignature = optString("versionSignature").trim()
    )

    private fun RecordingIdentityGoldSide.toReference(): StreamingTrackMatchPolicy.Reference =
        StreamingTrackMatchPolicy.Reference(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs.takeIf { it > 0L }
        )

    private fun RecordingIdentityGoldSide.toSource(
        sourceId: Long,
        recordingId: Long
    ): TrackSourceMappingEntity = TrackSourceMappingEntity(
        sourceId = sourceId,
        recordingId = recordingId,
        provider = "benchmark",
        providerTrackId = "",
        localTrackId = null,
        dataPath = "",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        quality = "",
        qualityScore = 0,
        playable = true,
        matchStatus = "CONFIRMED",
        confidence = 1.0,
        lastSuccessfulAt = 0L,
        lastVerifiedAt = 0L,
        legacyLocalKey = "",
        albumArtist = albumArtist,
        composer = composer,
        releaseType = releaseType,
        year = year
    )

    private fun usedMemory(runtime: Runtime): Long = runtime.totalMemory() - runtime.freeMemory()

    private data class BenchmarkFixture(
        val sources: List<TrackSourceMappingEntity>,
        val features: Map<Long, app.yukine.data.room.SourceMatchFeatureEntity>,
        val pairs: List<BenchmarkPair>,
        val sideBySourceId: Map<Long, RecordingIdentityGoldSide>,
        val sideByRecordingId: Map<Long, RecordingIdentityGoldSide>,
        val labelByCandidate: Map<Pair<Long, Long>, LabeledCandidate>
    )

    private data class LabeledCandidate(
        val pairId: String,
        val label: ManualMatchLabel
    )

    private data class BenchmarkPair(
        val gold: RecordingIdentityGoldPair,
        val leftSourceId: Long,
        val rightSourceId: Long,
        val leftRecordingId: Long,
        val rightRecordingId: Long
    )

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
