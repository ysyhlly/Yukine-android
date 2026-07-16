package app.yukine.playback;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

@UnstableApi
final class YukineRealtimeBassAudioProcessor extends BaseAudioProcessor {
    private final RealtimeBassDetector detector;
    private final Runnable firstPcmOutputListener;
    private volatile boolean firstPcmOutputPending;
    private boolean analyze16BitPcm;

    YukineRealtimeBassAudioProcessor(RealtimeBassDetector detector) {
        this(detector, null);
    }

    YukineRealtimeBassAudioProcessor(RealtimeBassDetector detector, Runnable firstPcmOutputListener) {
        this.detector = detector;
        this.firstPcmOutputListener = firstPcmOutputListener;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        analyze16BitPcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;
        if (!analyze16BitPcm) {
            detector.reset();
        } else {
            detector.configure(
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.encoding
            );
        }
        firstPcmOutputPending = true;
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }
        if (analyze16BitPcm) {
            detector.analyze16BitPcm(inputBuffer, inputAudioFormat.bytesPerFrame);
        }
        int length = inputBuffer.remaining();
        ByteBuffer output = replaceOutputBuffer(length);
        output.put(inputBuffer);
        output.flip();
        if (firstPcmOutputPending) {
            firstPcmOutputPending = false;
            if (firstPcmOutputListener != null) {
                try {
                    firstPcmOutputListener.run();
                } catch (RuntimeException ignored) {
                    // Diagnostics and persistence feedback must never interrupt the audio chain.
                }
            }
        }
    }

    @Override
    protected void onFlush() {
        detector.reset();
        firstPcmOutputPending = true;
    }

    @Override
    protected void onReset() {
        detector.reset();
        firstPcmOutputPending = false;
        analyze16BitPcm = false;
    }
}
