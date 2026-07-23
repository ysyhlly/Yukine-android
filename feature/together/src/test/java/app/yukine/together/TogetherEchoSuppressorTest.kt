package app.yukine.together

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherEchoSuppressorTest {
    @Test
    fun consumesExpectedRemoteEchoExactlyOnce() {
        var now = 1_000L
        val suppressor = TogetherEchoSuppressor(nowMs = { now }, windowMs = 500L)
        val event = TogetherPlaybackEvent.Seeked(12_345L)

        suppressor.expect(event)

        assertTrue(suppressor.consumeIfExpected(TogetherPlaybackEvent.Seeked(12_399L)))
        assertFalse(suppressor.consumeIfExpected(event))
    }

    @Test
    fun expiredExpectationDoesNotHideLocalAction() {
        var now = 1_000L
        val suppressor = TogetherEchoSuppressor(nowMs = { now }, windowMs = 500L)
        val event = TogetherPlaybackEvent.PauseChanged(true)
        suppressor.expect(event)
        now = 1_501L

        assertFalse(suppressor.consumeIfExpected(event))
    }
}
