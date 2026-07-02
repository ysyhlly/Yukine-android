package app.yukine.playback;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

@UnstableApi
final class YukineRealtimeBassAudioProcessor extends BaseAudioProcessor {
    private final RealtimeBassDetector detector;

    YukineRealtimeBassAudioProcessor(RealtimeBassDetector detector) {
        this.detector = detector;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            detector.reset();
            return AudioFormat.NOT_SET;
        }
        detector.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.encoding);
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }
        detector.analyze16BitPcm(inputBuffer, inputAudioFormat.bytesPerFrame);
        int length = inputBuffer.remaining();
        ByteBuffer output = replaceOutputBuffer(length);
        output.put(inputBuffer);
        output.flip();
    }

    @Override
    protected void onFlush() {
        detector.reset();
    }

    @Override
    protected void onReset() {
        detector.reset();
    }
}
