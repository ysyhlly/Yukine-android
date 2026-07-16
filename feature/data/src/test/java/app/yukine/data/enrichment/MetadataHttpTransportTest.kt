package app.yukine.data.enrichment

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataHttpTransportTest {
    @Test
    fun limiterSpacesEveryPermitByAtLeastOneSecond() {
        var clockNanos = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = OneRequestPerSecondRateLimiter(
            nanoTime = { clockNanos },
            sleepMillis = { millis ->
                sleeps += millis
                clockNanos += millis * 1_000_000L
            }
        )

        limiter.awaitPermit()
        limiter.awaitPermit()
        limiter.awaitPermit()

        assertEquals(listOf(1_000L, 1_000L), sleeps)
    }
}
