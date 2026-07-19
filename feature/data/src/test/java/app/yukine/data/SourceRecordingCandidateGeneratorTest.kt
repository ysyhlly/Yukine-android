package app.yukine.data

import app.yukine.data.room.SourceMatchFeatureEntity
import app.yukine.data.room.TrackSourceMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRecordingCandidateGeneratorTest {
    @Test
    fun paired300By300FixtureHasPerfectRecallAt20WithoutFullHeavyMatrix() {
        val local = (1L..300L).map { index ->
            source(
                sourceId = 1_000L + index,
                recordingId = index,
                title = "Song $index",
                artist = "Artist ${index % 20L}",
                durationMs = 180_000L + index
            )
        }
        val webDav = (1L..300L).map { index ->
            source(
                sourceId = 2_000L + index,
                recordingId = 10_000L + index,
                title = "ｓｏｎｇ $index",
                artist = "Artist ${index % 20L}",
                durationMs = 180_000L + index,
                provider = "webdav"
            )
        }
        val sources = local + webDav
        val features = features(sources)

        val result = SourceRecordingCandidateGenerator().generate(sources, features, 100L)
        val rowsBySource = result.candidates.groupBy { it.sourceId }
        val recalled = local.count { source ->
            rowsBySource[source.sourceId].orEmpty().any { candidate ->
                candidate.candidateRecordingId == 10_000L + source.recordingId
            }
        }

        assertEquals(300, recalled)
        assertTrue(rowsBySource.values.all { it.size <= SourceRecordingCandidateGenerator.DEFAULT_TOP_K })
        assertTrue(result.candidates.size <= sources.size * SourceRecordingCandidateGenerator.DEFAULT_TOP_K)
        assertTrue(
            "coarse comparisons=${result.coarseComparisonCount}",
            result.coarseComparisonCount < 90_000
        )
    }

    @Test
    fun rareTrigramCandidateRecoversMinorCjkMetadataVariation() {
        val first = source(11L, 1L, "星空物语", "测试歌手", 200_000L)
        val second = source(12L, 2L, "星空物語", "测试歌手", 200_100L, "webdav")

        val result = SourceRecordingCandidateGenerator().generate(
            listOf(first, second),
            features(listOf(first, second)),
            200L
        )

        assertTrue(
            result.candidates.any { row ->
                row.sourceId == first.sourceId && row.candidateRecordingId == second.recordingId
            }
        )
    }

    @Test
    fun differentVersionRemainsVisibleForRelationshipUiButCarriesHardConflictEvidence() {
        val original = source(21L, 3L, "Version Song", "Same Artist", 210_000L)
        val live = source(22L, 4L, "Version Song (Live)", "Same Artist", 224_000L, "webdav")

        val result = SourceRecordingCandidateGenerator().generate(
            listOf(original, live),
            features(listOf(original, live)),
            300L
        )

        val row = result.candidates.first { candidate ->
            candidate.sourceId == original.sourceId && candidate.candidateRecordingId == live.recordingId
        }
        assertTrue(row.evidenceJson.contains("\"hardVersionConflict\":true"))
    }

    @Test
    fun snapshotSignatureChangesWhenSourceMovesToAnotherRecording() {
        val source = source(31L, 5L, "Stable Song", "Artist", 180_000L)
        val features = features(listOf(source))
        val generator = SourceRecordingCandidateGenerator()

        val before = generator.snapshotSignature(listOf(source), features)
        val after = generator.snapshotSignature(listOf(source.copy(recordingId = 99L)), features)

        assertNotEquals(before, after)
        assertEquals(64, before.length)
        assertEquals(64, after.length)
    }

    @Test
    fun shadowKeepsEmbeddingOnlyCandidatesOutOfTheActiveTopK() {
        val first = source(41L, 6L, "甲", "First", 180_000L)
        val second = source(42L, 7L, "乙", "Second", 180_100L, "webdav")
        val vector = ByteArray(64).also { it[0] = 1 }
        val features = features(listOf(first, second)).mapValues { (_, feature) ->
            feature.copy(
                metadataVector = vector.copyOf(),
                metadataVectorVersion = MetadataHashEmbeddingEncoder.VECTOR_VERSION,
                metadataSimHash = 1L
            )
        }

        val result = SourceRecordingCandidateGenerator().generate(
            listOf(first, second),
            features,
            400L,
            EmbeddingRecallMode.SHADOW
        )

        assertTrue(result.candidates.isEmpty())
        assertTrue(result.shadowCandidates.any { row ->
            row.sourceId == first.sourceId &&
                row.candidateRecordingId == second.recordingId &&
                row.state == SourceRecordingCandidateGenerator.STATE_SHADOW &&
                row.evidenceJson.contains("\"EMBEDDING_LSH\"")
        })
    }

    @Test
    fun onPromotesEmbeddingOnlyCandidatesButOffDoesNot() {
        val first = source(51L, 8L, "Alpha", "One", 180_000L)
        val second = source(52L, 9L, "Omega", "Two", 180_100L, "webdav")
        val vector = ByteArray(64).also { it[3] = 2 }
        val features = features(listOf(first, second)).mapValues { (_, feature) ->
            feature.copy(
                metadataVector = vector.copyOf(),
                metadataVectorVersion = MetadataHashEmbeddingEncoder.VECTOR_VERSION,
                metadataSimHash = 8L
            )
        }
        val generator = SourceRecordingCandidateGenerator()

        val off = generator.generate(
            listOf(first, second),
            features,
            500L,
            EmbeddingRecallMode.OFF
        )
        val on = generator.generate(
            listOf(first, second),
            features,
            500L,
            EmbeddingRecallMode.ON
        )

        assertTrue(off.candidates.isEmpty())
        assertTrue(on.candidates.any { row ->
            row.sourceId == first.sourceId && row.candidateRecordingId == second.recordingId
        })
        assertNotEquals(
            generator.snapshotSignature(listOf(first, second), features, EmbeddingRecallMode.OFF),
            generator.snapshotSignature(listOf(first, second), features, EmbeddingRecallMode.ON)
        )
    }

    private fun features(
        sources: List<TrackSourceMappingEntity>
    ): Map<Long, SourceMatchFeatureEntity> = sources.associate { source ->
        val feature = checkNotNull(SourceMatchFeaturePolicy.build(source, updatedAt = 1L))
        feature.sourceId to feature
    }

    private fun source(
        sourceId: Long,
        recordingId: Long,
        title: String,
        artist: String,
        durationMs: Long,
        provider: String = "local"
    ) = TrackSourceMappingEntity(
        sourceId = sourceId,
        recordingId = recordingId,
        provider = provider,
        providerTrackId = "$provider-$sourceId",
        localTrackId = sourceId,
        dataPath = if (provider == "webdav") {
            "webdav:1:/$sourceId.flac"
        } else {
            "/music/$sourceId.flac"
        },
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        quality = "lossless",
        qualityScore = 400,
        playable = true,
        matchStatus = "CONFIRMED",
        confidence = 1.0,
        lastSuccessfulAt = 0L,
        lastVerifiedAt = 0L,
        legacyLocalKey = ""
    )
}
