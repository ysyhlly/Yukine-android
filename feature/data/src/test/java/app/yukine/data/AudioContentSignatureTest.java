package app.yukine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import app.yukine.data.room.AudioFeatureEntity;
import org.junit.Test;

public final class AudioContentSignatureTest {
    @Test
    public void signatureIsStableAndChangesWithPhysicalRevision() {
        String first = AudioContentSignature.create("content://audio/7", "/music/a.flac", 180_000L, 10_000L, 20L);
        String same = AudioContentSignature.create("content://audio/7", "/music/a.flac", 180_000L, 10_000L, 20L);
        String changed = AudioContentSignature.create("content://audio/7", "/music/a.flac", 180_000L, 10_001L, 21L);

        assertEquals(first, same);
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first, changed);
    }

    @Test
    public void unchangedFailedFeatureSkipsExtractorButChangedContentRetries() {
        AudioFeatureEntity failed = feature("signature", "FAILED", 1);

        assertTrue(MusicLibraryRepository.shouldSkipAudioSpec(failed, "signature"));
        assertFalse(MusicLibraryRepository.shouldSkipAudioSpec(failed, "changed"));
        assertFalse(MusicLibraryRepository.shouldSkipAudioSpec(feature("signature", "READY", 1), "signature"));
        assertFalse(MusicLibraryRepository.shouldSkipAudioSpec(feature("signature", "FAILED", 0), "signature"));
    }

    private static AudioFeatureEntity feature(String signature, String state, int specVersion) {
        return new AudioFeatureEntity(
                7L,
                signature,
                "",
                "",
                null,
                null,
                "",
                1,
                state,
                specVersion,
                1,
                10L,
                "",
                10L
        );
    }
}
