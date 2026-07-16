package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchGoldenFixturesTest {
    @Test
    fun recordingLabelsRemainStableAndExplainFailuresByFixtureName() {
        RecordingMatchGoldenFixtures.recordingPairs.forEach { fixture ->
            val evaluation = RecordingMatchEvaluatorV2.evaluate(fixture.left, fixture.right)
            when (fixture.expected) {
                GoldenRecordingLabel.SAME_RECORDING -> {
                    assertFalse(fixture.name, evaluation.hasHardConflict)
                    assertTrue(fixture.name, evaluation.similarityScore >= 0.92)
                }
                GoldenRecordingLabel.DIFFERENT_RECORDING ->
                    assertTrue(fixture.name, evaluation.hasHardConflict)
                GoldenRecordingLabel.ALTERNATE_VERSION ->
                    assertTrue(
                        fixture.name,
                        evaluation.hasHardConflict || evaluation.similarityScore < 0.92
                    )
                GoldenRecordingLabel.UNCERTAIN -> {
                    assertFalse(fixture.name, evaluation.hasHardConflict)
                    assertTrue(fixture.name, evaluation.similarityScore < 0.92)
                }
            }
        }
    }

    @Test
    fun playbackGoldenTop1HasNoMinimumThreshold() {
        RecordingMatchGoldenFixtures.playbackCases.forEach { fixture ->
            val ranked = fixture.candidatesInProviderOrder.withIndex().sortedWith(
                compareByDescending<IndexedValue<GoldenPlaybackCandidate>> { indexed ->
                    RecordingMatchEvaluatorV2.evaluate(fixture.target, indexed.value.reference).similarityScore
                }.thenBy { it.index }
            )
            assertEquals(fixture.name, fixture.expectedTop1Id, ranked.first().value.id)
        }
    }

    @Test
    fun fiftyCandidateSortP95HasNoPathologicalRegressionAfterWarmup() {
        val target = RecordingMatchGoldenFixtures.reference()
        val candidates = List(50) { index ->
            RecordingMatchGoldenFixtures.reference(
                title = if (index == 37) "Echo" else "Echo candidate $index",
                durationMs = 180_000L + index * 137L
            )
        }
        repeat(20) { rank(target, candidates) }
        val samples = LongArray(100) {
            val started = System.nanoTime()
            rank(target, candidates)
            System.nanoTime() - started
        }.sorted()
        val p95Nanos = samples[94]

        // Host unit-test scheduling is noisy and is not the device acceptance benchmark.
        assertTrue("50-candidate p95=${p95Nanos / 1_000_000.0}ms", p95Nanos <= 25_000_000L)
    }

    @Test
    fun threeHundredByThreeHundredMatchingBaselineReportsLatencyAndTop1Accuracy() {
        val candidates = List(300) { index ->
            RecordingMatchGoldenFixtures.reference(
                title = "Baseline Song $index",
                artist = "Baseline Artist ${index % 23}",
                album = "Baseline Album ${index % 17}",
                durationMs = 150_000L + index * 211L
            )
        }
        val candidateFeatures = candidates.map(RecordingMatchFeatureExtractor::extract)

        repeat(10) { index -> bestCandidateIndex(candidates[index], candidateFeatures) }
        var correctTop1 = 0
        val samples = LongArray(candidates.size) { index ->
            val started = System.nanoTime()
            val bestIndex = bestCandidateIndex(candidates[index], candidateFeatures)
            val elapsed = System.nanoTime() - started
            if (bestIndex == index) correctTop1++
            elapsed
        }.sorted()
        val p50Nanos = samples[149]
        val p95Nanos = samples[284]
        val totalNanos = samples.sum()

        println(
            "RECORDING_MATCH_BASELINE_300X300 " +
                "comparisons=90000 correctTop1=$correctTop1 " +
                "p50Ms=${p50Nanos / 1_000_000.0} " +
                "p95Ms=${p95Nanos / 1_000_000.0} " +
                "totalMs=${totalNanos / 1_000_000.0}"
        )
        assertEquals(300, correctTop1)
        // This is a broad pathological-regression guard. Device acceptance limits are tracked
        // separately because host JVM timing is not representative of an Android playback device.
        assertTrue("300-candidate p95=${p95Nanos / 1_000_000.0}ms", p95Nanos <= 2_000_000_000L)
    }

    private fun rank(
        target: StreamingTrackMatchPolicy.Reference,
        candidates: List<StreamingTrackMatchPolicy.Reference>
    ): List<RecordingMatchFeatures> {
        val targetFeatures = RecordingMatchFeatureExtractor.extract(target)
        return candidates.asSequence()
            .map(RecordingMatchFeatureExtractor::extract)
            .map { candidate ->
                candidate to RecordingMatchEvaluatorV2.evaluate(
                    targetFeatures,
                    candidate,
                    includeExplanation = false
                )
            }
            .sortedByDescending { (_, evaluation) -> evaluation.similarityScore }
            .map { it.first }
            .toList()
    }

    private fun bestCandidateIndex(
        target: StreamingTrackMatchPolicy.Reference,
        candidates: List<RecordingMatchFeatures>
    ): Int {
        val targetFeatures = RecordingMatchFeatureExtractor.extract(target)
        var bestIndex = -1
        var bestScore = Double.NEGATIVE_INFINITY
        candidates.forEachIndexed { index, candidate ->
            val score = RecordingMatchEvaluatorV2.evaluate(
                targetFeatures,
                candidate,
                includeExplanation = false
            ).similarityScore
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return bestIndex
    }
}
