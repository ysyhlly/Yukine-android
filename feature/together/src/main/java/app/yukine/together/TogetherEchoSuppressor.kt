package app.yukine.together

import java.util.ArrayDeque

internal class TogetherEchoSuppressor(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val windowMs: Long = 1_500L
) {
    private data class Expected(val key: String, val value: String, val expiresAtMs: Long)

    private val expected = ArrayDeque<Expected>()

    @Synchronized
    fun expect(event: TogetherPlaybackEvent) {
        prune()
        expected.addLast(Expected(key(event), value(event), nowMs() + windowMs))
    }

    @Synchronized
    fun consumeIfExpected(event: TogetherPlaybackEvent): Boolean {
        prune()
        val key = key(event)
        val value = value(event)
        val iterator = expected.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.key == key && next.value == value) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    @Synchronized
    fun clear() {
        expected.clear()
    }

    private fun prune() {
        val current = nowMs()
        while (expected.isNotEmpty() && expected.first().expiresAtMs < current) {
            expected.removeFirst()
        }
    }

    private fun key(event: TogetherPlaybackEvent): String = when (event) {
        is TogetherPlaybackEvent.PauseChanged -> "pause"
        is TogetherPlaybackEvent.Seeked -> "seek"
        is TogetherPlaybackEvent.SpeedChanged -> "speed"
        is TogetherPlaybackEvent.QueueIndexChanged -> "index"
        is TogetherPlaybackEvent.BufferingChanged -> "buffering"
        TogetherPlaybackEvent.PlaybackRestarted -> "restart"
    }

    private fun value(event: TogetherPlaybackEvent): String = when (event) {
        is TogetherPlaybackEvent.PauseChanged -> event.paused.toString()
        is TogetherPlaybackEvent.Seeked -> (event.positionMs / 100L).toString()
        is TogetherPlaybackEvent.SpeedChanged -> "%.3f".format(java.util.Locale.ROOT, event.speed)
        is TogetherPlaybackEvent.QueueIndexChanged -> event.index.toString()
        is TogetherPlaybackEvent.BufferingChanged -> event.buffering.toString()
        TogetherPlaybackEvent.PlaybackRestarted -> ""
    }
}
