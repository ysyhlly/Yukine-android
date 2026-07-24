package app.yukine.playback.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.AudioFallbackReason;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public final class StreamingAudioFormatPreflightTest {
    @Test
    public void decodedPcmUsesTheDecoderEncodingInsteadOfCompressedSourceDepth() {
        assertEquals(
                16,
                StreamingAudioFormatPreflight.bitDepthForPcmEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                )
        );
        assertEquals(
                32,
                StreamingAudioFormatPreflight.bitDepthForPcmEncoding(
                        AudioFormat.ENCODING_PCM_FLOAT
                )
        );
    }

    @Test
    public void completeStereoPcmIsEligibleForFinalUsbSinkValidation() {
        StreamingAudioFormatPreflight.PcmFormat pcm =
                new StreamingAudioFormatPreflight.PcmFormat(
                        96_000,
                        24,
                        2,
                        AudioFormat.ENCODING_PCM_24BIT_PACKED
                );

        StreamingAudioFormatPreflight.Result result =
                StreamingAudioFormatPreflight.Result.decoded(pcm, 37L, 8192L);

        assertEquals(
                StreamingAudioFormatPreflight.Eligibility.VERIFIED_COMPATIBLE,
                result.eligibility
        );
        assertTrue(result.formatVerified());
        assertEquals(8192L, result.bytesRead);
    }

    @Test
    public void incompleteAndTimedOutFormatsStayOnDirectPcm() {
        StreamingAudioFormatPreflight.Result incomplete =
                StreamingAudioFormatPreflight.Result.incomplete(
                        new StreamingAudioFormatPreflight.PcmFormat(48_000, 0, 2, 0),
                        18L,
                        4096L,
                        "missing encoding"
                );
        StreamingAudioFormatPreflight.Result timedOut =
                StreamingAudioFormatPreflight.Result.timedOut(2_000L, 4L * 1024L * 1024L);

        assertFalse(incomplete.formatVerified());
        assertEquals(
                AudioFallbackReason.FORMAT_METADATA_INCOMPLETE,
                incomplete.fallbackReason
        );
        assertEquals(StreamingAudioFormatPreflight.Eligibility.TIMED_OUT, timedOut.eligibility);
        assertEquals(AudioFallbackReason.FORMAT_PREFLIGHT_TIMEOUT, timedOut.fallbackReason);
    }

    @Test
    public void multichannelPcmIsRejectedBeforeUsbNegotiation() {
        StreamingAudioFormatPreflight.Result result =
                StreamingAudioFormatPreflight.Result.decoded(
                        new StreamingAudioFormatPreflight.PcmFormat(
                                48_000,
                                24,
                                6,
                                AudioFormat.ENCODING_PCM_24BIT_PACKED
                        ),
                        10L,
                        1024L
                );

        assertEquals(
                StreamingAudioFormatPreflight.Eligibility.VERIFIED_INCOMPATIBLE,
                result.eligibility
        );
        assertEquals(AudioFallbackReason.FORMAT_UNSUPPORTED, result.fallbackReason);
    }

    @Test
    public void hardTimeoutReturnsTimedOutAndCancelsTheDecoder() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch completed = new CountDownLatch(1);
        StreamingAudioFormatPreflight preflight = preflight((track, cancellation) -> {
            try {
                while (!cancellation.cancelled()) {
                    Thread.sleep(10L);
                }
            } catch (InterruptedException interrupted) {
                cancelled.set(true);
                throw interrupted;
            }
            cancelled.set(true);
            throw new InterruptedException("cancelled");
        });

        preflight.requestCurrent(track(1L), result -> {
            assertEquals(StreamingAudioFormatPreflight.Eligibility.TIMED_OUT, result.eligibility);
            completed.countDown();
        });

        assertTrue(completed.await(5L, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 100 && !cancelled.get(); attempt++) {
            Thread.sleep(10L);
        }
        assertTrue(cancelled.get());
        preflight.release();
    }

    @Test
    public void changingCurrentTrackCancelsOldGenerationWithoutOldCallback() throws Exception {
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch oldCancelled = new CountDownLatch(1);
        CountDownLatch newCompleted = new CountDownLatch(1);
        AtomicBoolean oldCallback = new AtomicBoolean();
        StreamingAudioFormatPreflight preflight = preflight((track, cancellation) -> {
            if (track.id == 1L) {
                oldStarted.countDown();
                try {
                    while (!cancellation.cancelled()) {
                        Thread.sleep(10L);
                    }
                } catch (InterruptedException interrupted) {
                    oldCancelled.countDown();
                    throw interrupted;
                }
                oldCancelled.countDown();
                throw new InterruptedException("superseded");
            }
            return StreamingAudioFormatPreflight.Result.decoded(
                    new StreamingAudioFormatPreflight.PcmFormat(
                            48_000,
                            16,
                            2,
                            AudioFormat.ENCODING_PCM_16BIT
                    ),
                    8L,
                    2048L
            );
        });

        preflight.requestCurrent(track(1L), ignored -> oldCallback.set(true));
        assertTrue(oldStarted.await(2L, TimeUnit.SECONDS));
        preflight.requestCurrent(track(2L), result -> newCompleted.countDown());

        assertTrue(oldCancelled.await(2L, TimeUnit.SECONDS));
        assertTrue(newCompleted.await(2L, TimeUnit.SECONDS));
        assertFalse(oldCallback.get());
        preflight.release();
    }

    private static StreamingAudioFormatPreflight preflight(
            StreamingAudioFormatPreflight.ProbeEngine engine
    ) {
        PlaybackMediaSourceProvider provider = new PlaybackMediaSourceProvider(
                RuntimeEnvironment.getApplication(),
                ignored -> null,
                new FakeHeaderStore()
        );
        return new StreamingAudioFormatPreflight(
                provider,
                engine,
                Runnable::run,
                Executors.newFixedThreadPool(2),
                Executors.newCachedThreadPool()
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180_000L,
                Uri.parse("https://example.test/" + id + ".mp3"),
                "streaming:test:" + id
        );
    }

    private static final class FakeHeaderStore implements StreamingPlaybackHeaderStore {
        @Override
        public void register(String dataPath, Map<String, String> headers) {
        }

        @Override
        public Map<String, String> forDataPath(String dataPath) {
            return Collections.emptyMap();
        }

        @Override
        public boolean restoreForDataPath(String dataPath) {
            return true;
        }

        @Override
        public Track restoredTrackFor(Track track) {
            return null;
        }
    }
}
