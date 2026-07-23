package app.yukine.playback.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsbPcmSessionOwnerTest {
    @Test
    fun transitionStopsTransportBeforeClosingAndroidConnection() {
        val events = mutableListOf<String>()
        val owner = UsbPcmSessionOwner { events += "connection.close" }
        val firstWriter = writer(events, "first")
        val firstGeneration = owner.beginTransition()
        assertTrue(owner.install(firstGeneration, key(44_100, 16), firstWriter))

        owner.beginTransition()

        assertTrue(events.indexOf("first.cancel") >= 0)
        assertTrue(events.indexOf("first.close") > events.indexOf("first.cancel"))
        assertTrue(events.lastIndexOf("connection.close") > events.indexOf("first.close"))
    }

    @Test
    fun staleWriterCannotDetachReplacementSession() {
        val owner = UsbPcmSessionOwner { }
        val first = writer(mutableListOf(), "first")
        val firstGeneration = owner.beginTransition()
        assertTrue(owner.install(firstGeneration, key(44_100, 16), first))

        val secondGeneration = owner.beginTransition()
        val second = writer(mutableListOf(), "second")
        assertTrue(owner.install(secondGeneration, key(96_000, 24), second))

        assertFalse(owner.detachIfCurrent(firstGeneration, first))
        assertSame(second, owner.writer())
        owner.closeCurrent()
    }

    @Test
    fun requestMatchIncludesRateChannelsDepthAndDevice() {
        val owner = UsbPcmSessionOwner { }
        val generation = owner.beginTransition()
        val writer = writer(mutableListOf(), "active")
        assertTrue(owner.install(generation, key(48_000, 24), writer))

        assertTrue(owner.matchesRequest(7, 48_000, 2, 24))
        assertFalse(owner.matchesRequest(7, 44_100, 2, 24))
        assertFalse(owner.matchesRequest(8, 48_000, 2, 24))
        owner.closeCurrent()
    }

    @Test
    fun consecutivePcmFormatsAlwaysClosePreviousWriterFirst() {
        val events = mutableListOf<String>()
        val owner = UsbPcmSessionOwner { events += "connection.close" }
        val formats = listOf(
            44_100 to 16,
            48_000 to 24,
            96_000 to 32,
            192_000 to 24,
            44_100 to 16
        )

        formats.forEachIndexed { index, (rate, depth) ->
            val generation = owner.beginTransition()
            events += "session.$index.create"
            assertTrue(owner.install(generation, key(rate, depth), writer(events, "session.$index")))
            if (index > 0) {
                assertTrue(
                    events.indexOf("session.${index - 1}.close") <
                        events.indexOf("session.$index.create")
                )
            }
        }
        owner.closeCurrent()
    }

    private fun key(rate: Int, depth: Int) = UsbPcmSessionKey(
        deviceId = 7,
        sampleRateHz = rate,
        channelCount = 2,
        sourceBitDepth = depth,
        usbSubslotBytes = if (depth == 16) 2 else 4,
        interfaceNumber = 1,
        alternateSetting = if (rate > 48_000) 2 else 1,
        clockSourceEntityId = 5
    )

    private fun writer(events: MutableList<String>, name: String) = UsbPcmWriter(
        UsbAudioStreamConfig.FALLBACK,
        object : UsbPcmTransport {
            override fun write(pcmData: ByteArray): Int = pcmData.size
            override fun reset() = Unit
            override fun cancel() { events += "$name.cancel" }
            override fun close() { events += "$name.close" }
        },
        { throw AssertionError(it) }
    )
}
