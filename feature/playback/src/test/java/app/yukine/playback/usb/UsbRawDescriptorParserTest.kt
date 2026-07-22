package app.yukine.playback.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class UsbRawDescriptorParserTest {
    @Test
    fun parsesUac2ClockAndHighBandwidthAsyncEndpoint() {
        val descriptor = byteArrayOf(
            9, 2, 51, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 2, 0, 0, 0, 0,
            8, 0x24, 0x0A, 0x10, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            7, 5, 1, 5, 0, 0x12, 1
        )

        val topology = UsbRawDescriptorParser.parseTopology(descriptor)
        val endpoint = UsbRawDescriptorParser.parseEndpoints(descriptor).single()

        assertEquals(2, topology.audioClassVersion)
        assertEquals(0, topology.controlInterfaceNumber)
        assertEquals(0x10, topology.clockSourceEntityId)
        assertEquals(3, endpoint.transactionsPerServiceInterval)
        assertEquals(1, endpoint.synchronizationType)
        assertEquals(1, endpoint.alternateSetting)
    }

    @Test
    fun resolvesClockSelectorFromStreamingTerminal() {
        val descriptor = byteArrayOf(
            9, 2, 101, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 2, 0, 0, 0, 0,
            8, 0x24, 0x0A, 0x10, 0, 1, 0, 0,
            8, 0x24, 0x0A, 0x11, 0, 3, 0, 0,
            9, 0x24, 0x0B, 0x20, 2, 0x10, 0x11, 1, 0,
            17, 0x24, 2, 0x30, 1, 1, 0, 0x20, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 1, 0x30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            7, 5, 1, 5, 0, 2, 1
        )

        val endpoint = UsbRawDescriptorParser.parseEndpoints(descriptor).single()

        assertEquals(0x20, endpoint.clockSelectorEntityId)
        assertEquals(1, endpoint.clockSelectorControl)
        assertArrayEquals(intArrayOf(0x10, 0x11), endpoint.clockSourceEntityIds)
        assertArrayEquals(intArrayOf(1, 3), endpoint.clockSourceFrequencyControls)
    }

    @Test
    fun terminalLinkWinsOverUnrelatedLaterClockSource() {
        val descriptor = byteArrayOf(
            9, 2, 84, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 2, 0, 0, 0, 0,
            8, 0x24, 0x0A, 0x10, 0, 3, 0, 0,
            8, 0x24, 0x0A, 0x11, 0, 3, 0, 0,
            17, 0x24, 2, 0x30, 1, 1, 0, 0x10, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 1, 0x30, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            7, 5, 1, 5, 0, 2, 1
        )

        val endpoint = UsbRawDescriptorParser.parseEndpoints(descriptor).single()

        assertEquals(0x10, endpoint.clockSourceEntityId)
        assertArrayEquals(intArrayOf(0x10), endpoint.clockSourceEntityIds)
    }

    @Test
    fun parsesUac1EndpointSamplingFrequencyCapability() {
        val withoutControl = byteArrayOf(
            9, 2, 48, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0, 0,
            9, 0x24, 1, 0, 1, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            7, 5, 1, 1, 192.toByte(), 0, 1,
            7, 0x25, 1, 0, 0, 0, 0
        )
        val withControl = withoutControl.copyOf().also { it[it.size - 4] = 1 }

        assertEquals(0, UsbRawDescriptorParser.parseEndpoints(withoutControl).single().sampleFrequencyControl)
        assertEquals(3, UsbRawDescriptorParser.parseEndpoints(withControl).single().sampleFrequencyControl)
    }

    @Test
    fun selectsUac2AlternateMatchingPcmContainerWidth() {
        val descriptor = byteArrayOf(
            9, 2, 0, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            9, 0x24, 1, 0, 2, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 1, 0x30, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0,
            6, 0x24, 2, 1, 4, 32,
            7, 5, 1, 9, 0xff.toByte(), 3, 2,
            9, 4, 1, 2, 1, 1, 2, 0x20, 0,
            16, 0x24, 1, 0x30, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0,
            6, 0x24, 2, 1, 3, 24,
            7, 5, 1, 9, 0xff.toByte(), 3, 2
        )

        val endpoints = UsbRawDescriptorParser.parseEndpoints(descriptor)
        val pcm24 = UsbRawDescriptorParser.selectAudioStreamingEndpoint(endpoints, 192_000, 2, 24)
        val pcm32 = UsbRawDescriptorParser.selectAudioStreamingEndpoint(endpoints, 192_000, 2, 32)

        assertEquals(2, pcm24?.alternateSetting)
        assertEquals(3, pcm24?.subslotSizeBytes)
        assertEquals(24, pcm24?.bitResolution)
        assertEquals(1, pcm32?.alternateSetting)
        assertEquals(4, pcm32?.subslotSizeBytes)
        assertEquals(32, pcm32?.bitResolution)
    }

    @Test
    fun rejectsDescribedAlternateWithIncompatibleBitDepth() {
        val endpoints = listOf(
            UsbRawDescriptorParser.RawEndpointInfo(
                address = 1,
                attributes = 9,
                maxPacketSize = 1023,
                interval = 2,
                interfaceNumber = 1,
                alternateSetting = 1,
                synchronizationType = 2,
                transactionsPerServiceInterval = 1,
                audioClassVersion = 2,
                controlInterfaceNumber = 0,
                clockSourceEntityId = 3,
                channelCount = 2,
                subslotSizeBytes = 3,
                bitResolution = 24
            )
        )

        val selected = UsbRawDescriptorParser.selectAudioStreamingEndpoint(endpoints, 48_000, 2, 32)

        assertEquals(null, selected)
    }

    @Test
    fun parsesUac1DiscreteSampleRatesAndRejectsUnsupportedRate() {
        val descriptor = byteArrayOf(
            9, 2, 0, 0, 2, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 1, 1, 0, 0,
            9, 0x24, 1, 0, 1, 0, 0, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            7, 0x24, 1, 1, 1, 1, 0,
            14, 0x24, 2, 1, 2, 2, 16, 2,
            0x44, 0xac.toByte(), 0,
            0x80.toByte(), 0xbb.toByte(), 0,
            7, 5, 1, 9, 192.toByte(), 0, 1
        )

        val endpoints = UsbRawDescriptorParser.parseEndpoints(descriptor)
        val endpoint = endpoints.single()

        assertArrayEquals(intArrayOf(44_100, 48_000), endpoint.discreteSampleRatesHz)
        assertEquals(1, UsbRawDescriptorParser.selectAudioStreamingEndpoint(
            endpoints, 44_100, 2, 16
        )?.alternateSetting)
        assertEquals(null, UsbRawDescriptorParser.selectAudioStreamingEndpoint(
            endpoints, 96_000, 2, 16
        ))
    }
}
