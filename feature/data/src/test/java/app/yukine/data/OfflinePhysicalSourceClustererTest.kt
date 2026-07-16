package app.yukine.data

import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.StreamingTrackMatchPolicy
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePhysicalSourceClustererTest {
    @Test
    fun candidateGenerationReduces300By300MatrixToOneCandidatePerTrack() {
        val local = (1L..300L).map { index ->
            source(index, 10_000L + index, "Song $index", "Artist ${index % 20L}", 180_000L + index)
        }
        val webDav = (1L..300L).map { index ->
            source(
                1_000L + index,
                20_000L + index,
                "Song $index",
                "Artist ${index % 20L}",
                180_000L + index
            )
        }
        val sourcesByRecording = (local + webDav).associateBy(TrackSourceMappingEntity::recordingId)
        var generatedPairs = 0
        var scoredPairs = 0
        val elapsedMs = measureTimeMillis {
            val index = PhysicalSourceCandidateIndex(local + webDav)
            generatedPairs = (1L..300L).sumOf { recordingId ->
                val candidates = index.candidateRecordingIds(recordingId)
                assertEquals(setOf(1_000L + recordingId), candidates)
                candidates.forEach { candidateId ->
                    val evaluation = RecordingMatchEvaluatorV2.evaluate(
                        reference(sourcesByRecording.getValue(recordingId)),
                        reference(sourcesByRecording.getValue(candidateId))
                    )
                    assertTrue(evaluation.hardConflicts.isEmpty())
                    scoredPairs++
                }
                candidates.size
            }
        }

        assertEquals(300, generatedPairs)
        assertEquals(300, scoredPairs)
        assertTrue(
            "300x300 candidate generation and 300 V2 scores took ${elapsedMs}ms",
            elapsedMs < MATCHING_BUDGET_MS
        )
    }

    @Test
    fun candidateIndexUsesNormalizedTitleArtistAndAdjacentDurationBuckets() {
        val reference = source(1L, 101L, "Shared Song", "Same Artist", 199_000L)
        val adjacent = source(2L, 102L, "shared song", "Same Artist", 201_000L)
        val differentArtist = source(3L, 103L, "Shared Song", "Other Artist", 200_000L)
        val distantDuration = source(4L, 104L, "Shared Song", "Same Artist", 221_000L)
        val differentTitle = source(5L, 105L, "Other Song", "Same Artist", 200_000L)
        val index = PhysicalSourceCandidateIndex(
            listOf(reference, adjacent, differentArtist, distantDuration, differentTitle)
        )

        assertEquals(setOf(2L), index.candidateRecordingIds(1L))
        assertEquals(setOf(1L), index.candidateRecordingIds(2L))
        assertTrue(index.candidateRecordingIds(3L).isEmpty())
        assertTrue(index.candidateRecordingIds(4L).isEmpty())
        assertTrue(index.candidateRecordingIds(5L).isEmpty())
    }

    @Test
    fun mergedRecordingReusesUnionOfCandidateBucketsWithoutARescan() {
        val first = source(1L, 101L, "Shared Song", "Same Artist", 200_000L)
        val second = source(2L, 102L, "Shared Song", "Same Artist", 201_000L)
        val third = source(3L, 103L, "Shared Song", "Same Artist", 202_000L)
        val index = PhysicalSourceCandidateIndex(listOf(first, second, third))

        index.merge(sourceRecordingId = 2L, targetRecordingId = 1L)

        assertEquals(setOf(3L), index.candidateRecordingIds(1L))
        assertTrue(index.sources(2L).isEmpty())
        assertEquals(setOf(101L, 102L), index.sources(1L).mapNotNull { it.sourceId }.toSet())
    }

    private fun source(
        recordingId: Long,
        sourceId: Long,
        title: String,
        artist: String,
        durationMs: Long
    ) = TrackSourceMappingEntity(
        sourceId = sourceId,
        recordingId = recordingId,
        provider = if (recordingId % 2L == 0L) "webdav" else "local",
        providerTrackId = "source-$sourceId",
        localTrackId = sourceId,
        dataPath = if (recordingId % 2L == 0L) {
            "webdav:9:/source-$sourceId.flac"
        } else {
            "/music/source-$sourceId.flac"
        },
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        quality = "lossless",
        qualityScore = 400,
        playable = true,
        matchStatus = "UNRESOLVED",
        confidence = 0.0,
        lastSuccessfulAt = 0L,
        lastVerifiedAt = 0L,
        legacyLocalKey = ""
    )

    private fun reference(source: TrackSourceMappingEntity) = StreamingTrackMatchPolicy.Reference(
        title = source.title,
        artist = source.artist,
        album = source.album,
        durationMs = source.durationMs
    )

    private companion object {
        const val MATCHING_BUDGET_MS = 5_000L
    }
}
