package app.yukine.data

import android.net.Uri
import app.yukine.model.LocalAudioFormat
import app.yukine.model.LocalAudioSkipReason
import app.yukine.model.LocalAudioSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 27, 33])
class LocalAudioCandidateProbeTest {
    @Test
    fun safProbeAcceptsAacM4aWhenDeviceHasDecoder() {
        val probe = probe("audio/mp4a-latm", hasVideo = false, hasDecoder = true)

        val decision = probe.probe(uri(), "song.m4a", "audio/mp4")

        assertEquals(LocalAudioFormat.M4A_AAC, decision.format())
        assertEquals(LocalAudioSupport.SUPPORTED, decision.support())
    }

    @Test
    fun containerProbeRejectsAlacVideoMp4AndNonPcmWav() {
        val alac = probe("audio/alac", false, true)
            .probe(uri(), "song.m4a", "audio/mp4")
        val video = probe("audio/mp4a-latm", true, true)
            .probe(uri(), "clip.mp4", "video/mp4")
        val nonPcm = probe("audio/mpeg", false, true)
            .probe(uri(), "song.wav", "audio/wav")

        assertEquals(LocalAudioFormat.ALAC, alac.format())
        assertEquals(LocalAudioSkipReason.VIDEO_CONTAINER, video.skipReason())
        assertFalse(nonPcm.shouldImport())
    }

    @Test
    fun missingDeviceDecoderIsReportedSeparately() {
        val decision = probe("audio/opus", false, false)
            .probe(uri(), "song.opus", "audio/opus")

        assertEquals(LocalAudioSkipReason.DEVICE_DECODER_MISSING, decision.skipReason())
        assertFalse(decision.shouldImport())
    }

    @Test
    fun mediaStoreDoesNotOpenUnambiguousMp3Container() {
        var reads = 0
        val probe = LocalAudioCandidateProbe(
            {
                reads++
                LocalAudioCandidateProbe.ContainerInfo("audio/mpeg", false)
            },
            { true }
        )

        val decision = probe.probeForMediaStore(uri(), "song.mp3", "audio/mpeg")

        assertTrue(decision.shouldImport())
        assertEquals(0, reads)
    }

    @Test
    fun unreadableSafCandidateIsSkippedButDsfBypassesSystemExtractor() {
        var reads = 0
        val probe = LocalAudioCandidateProbe(
            {
                reads++
                throw IOException("unreadable")
            },
            { true }
        )

        val mp3 = probe.probe(uri(), "song.mp3", "audio/mpeg")
        val dsf = probe.probe(uri(), "album.dsf", "audio/x-dsf")

        assertEquals(LocalAudioSkipReason.UNREADABLE, mp3.skipReason())
        assertEquals(LocalAudioSupport.USB_ONLY, dsf.support())
        assertEquals(1, reads)
    }

    private fun probe(
        sampleMime: String,
        hasVideo: Boolean,
        hasDecoder: Boolean
    ) = LocalAudioCandidateProbe(
        { LocalAudioCandidateProbe.ContainerInfo(sampleMime, hasVideo) },
        { hasDecoder }
    )

    private fun uri(): Uri = Uri.parse("content://test/audio/1")
}
