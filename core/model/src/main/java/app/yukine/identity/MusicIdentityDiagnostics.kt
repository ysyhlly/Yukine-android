package app.yukine.identity

import java.util.ArrayDeque
import kotlin.math.ceil

/** Bounded, metadata-free phase timings for canonical identity work. */
class MusicIdentityDiagnostics(
    private val nanoTime: () -> Long = System::nanoTime
) {
    enum class Operation {
        PHYSICAL_CLUSTER,
        PLATFORM_SYNC
    }

    enum class Stage {
        SNAPSHOT_LOAD,
        SOURCE_FETCH,
        NORMALIZATION,
        CANDIDATE_GENERATION,
        SCORING,
        DATABASE_COMMIT,
        CACHE_PUBLISH,
        TOTAL
    }

    data class StageLatency(
        val sampleCount: Int = 0,
        val workUnits: Long = 0L,
        val minimumMs: Long = 0L,
        val maximumMs: Long = 0L,
        val p50Ms: Long = 0L,
        val p95Ms: Long = 0L
    )

    data class OperationSnapshot(
        val operation: Operation,
        val stages: Map<Stage, StageLatency>
    ) {
        fun compactSummary(): String = stages.entries.joinToString(separator = ",") { (stage, value) ->
            "${stage.name.lowercase()}=${value.p50Ms}/${value.p95Ms}ms" +
                "#${value.sampleCount}@${value.workUnits}"
        }
    }

    private data class Sample(val durationMs: Long, val workUnits: Long)

    private val samples = linkedMapOf<Pair<Operation, Stage>, ArrayDeque<Sample>>()

    fun startNanos(): Long = nanoTime()

    @Synchronized
    fun recordElapsed(
        operation: Operation,
        stage: Stage,
        startedAtNanos: Long,
        workUnits: Long = 0L
    ) {
        val elapsedNanos = (nanoTime() - startedAtNanos).coerceAtLeast(0L)
        val elapsedMs = if (elapsedNanos == 0L) 0L else (elapsedNanos + NANOS_PER_MS - 1L) / NANOS_PER_MS
        record(operation, stage, elapsedMs, workUnits)
    }

    @Synchronized
    fun record(
        operation: Operation,
        stage: Stage,
        durationMs: Long,
        workUnits: Long = 0L
    ) {
        val values = samples.getOrPut(operation to stage, ::ArrayDeque)
        values.addLast(Sample(durationMs.coerceAtLeast(0L), workUnits.coerceAtLeast(0L)))
        while (values.size > MAX_SAMPLES_PER_STAGE) values.removeFirst()
    }

    @Synchronized
    fun snapshot(operation: Operation): OperationSnapshot = OperationSnapshot(
        operation = operation,
        stages = Stage.entries.associateWith { stage ->
            summarize(samples[operation to stage].orEmpty())
        }
    )

    @Synchronized
    fun reset() {
        samples.clear()
    }

    private fun summarize(values: Collection<Sample>): StageLatency {
        if (values.isEmpty()) return StageLatency()
        val sorted = values.map(Sample::durationMs).sorted()
        return StageLatency(
            sampleCount = sorted.size,
            workUnits = values.sumOf(Sample::workUnits),
            minimumMs = sorted.first(),
            maximumMs = sorted.last(),
            p50Ms = percentile(sorted, 0.50),
            p95Ms = percentile(sorted, 0.95)
        )
    }

    private fun percentile(sorted: List<Long>, percentile: Double): Long {
        val index = (ceil(percentile * sorted.size).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    companion object {
        private const val MAX_SAMPLES_PER_STAGE = 128
        private const val NANOS_PER_MS = 1_000_000L
        private val PROCESS = MusicIdentityDiagnostics()

        @JvmStatic
        fun process(): MusicIdentityDiagnostics = PROCESS
    }
}
