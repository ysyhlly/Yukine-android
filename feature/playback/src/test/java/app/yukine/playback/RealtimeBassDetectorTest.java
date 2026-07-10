package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

public final class RealtimeBassDetectorTest {
    @Test
    public void publishesAudibleBandsFromPcmWithoutMutatingTheAudioBuffer() {
        AtomicLong nowMs = new AtomicLong(1_000L);
        RealtimeBassDetector detector = new RealtimeBassDetector(nowMs::get);
        detector.configure(44_100, 2, 2);

        detector.analyze16BitPcm(pulsePcm(), 4);

        float[] bands = detector.bands();
        assertEquals(RealtimeBassDetector.REALTIME_BAND_COUNT, bands.length);
        assertTrue("a loud PCM pulse should produce visible spectrum bands", hasSignal(bands));
    }

    @Test
    public void repeatedUiReadsDoNotDecayThePublishedBeat() {
        AtomicLong nowMs = new AtomicLong(1_000L);
        RealtimeBassDetector detector = new RealtimeBassDetector(nowMs::get);
        detector.configure(44_100, 2, 2);
        detector.analyze16BitPcm(pulsePcm(), 4);

        nowMs.set(1_050L);
        float firstRead = detector.beat();
        float secondRead = detector.beat();

        assertTrue("the pulse must still be visible after the beat hold window", firstRead > 0.015f);
        assertEquals("UI polling must not mutate the audio-thread beat state", firstRead, secondRead, 0.0001f);
    }

    @Test
    public void resetImmediatelyHidesThePublishedSnapshot() {
        AtomicLong nowMs = new AtomicLong(1_000L);
        RealtimeBassDetector detector = new RealtimeBassDetector(nowMs::get);
        detector.configure(44_100, 2, 2);
        detector.analyze16BitPcm(pulsePcm(), 4);

        detector.reset();

        assertEquals(0f, detector.beat(), 0f);
        assertTrue("reset snapshot should have no visible bands", !hasSignal(detector.bands()));
    }

    private static ByteBuffer pulsePcm() {
        ByteBuffer buffer = ByteBuffer.allocate(96 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < 96; frame++) {
            short sample = frame < 80 ? 0 : (short) 30_000;
            buffer.putShort(sample);
            buffer.putShort(sample);
        }
        buffer.flip();
        return buffer;
    }

    private static boolean hasSignal(float[] values) {
        for (float value : values) {
            if (value > 0.02f) {
                return true;
            }
        }
        return false;
    }
}
