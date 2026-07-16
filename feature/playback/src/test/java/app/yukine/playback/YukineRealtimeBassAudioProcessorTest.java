package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class YukineRealtimeBassAudioProcessorTest {
    @Test
    public void reportsOnlyTheFirstPcmBufferUntilTheProcessorIsFlushed() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        YukineRealtimeBassAudioProcessor processor = new YukineRealtimeBassAudioProcessor(
                new RealtimeBassDetector(),
                callbacks::incrementAndGet
        );
        processor.configure(new AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();

        processor.queueInput(pcmFrame());
        processor.queueInput(pcmFrame());
        assertEquals(1, callbacks.get());

        processor.flush();
        processor.queueInput(pcmFrame());
        assertEquals(2, callbacks.get());
    }

    @Test
    public void reportsFloatPcmEvenWhenBassAnalysisIsNotApplicable() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        YukineRealtimeBassAudioProcessor processor = new YukineRealtimeBassAudioProcessor(
                new RealtimeBassDetector(),
                callbacks::incrementAndGet
        );
        processor.configure(new AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_FLOAT));
        processor.flush();

        processor.queueInput(pcmFrame());

        assertEquals(1, callbacks.get());
    }

    private static ByteBuffer pcmFrame() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8);
        buffer.putLong(0L);
        buffer.flip();
        return buffer;
    }
}
