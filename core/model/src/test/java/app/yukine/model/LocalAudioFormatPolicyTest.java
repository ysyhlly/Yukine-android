package app.yukine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LocalAudioFormatPolicyTest {
    @Test
    public void acceptsStableFormatsAndNormalizesAliases() {
        assertSupported("SONG.MP3", "audio/mpeg; charset=binary", LocalAudioFormat.MP3);
        assertSupported("song.aac", "audio/aac", LocalAudioFormat.AAC_ADTS);
        assertSupported("song.flac", "audio/flac", LocalAudioFormat.FLAC);
        assertSupported("song.oga", "audio/ogg", LocalAudioFormat.OGG_VORBIS);
        assertSupported("song.opus", "audio/opus", LocalAudioFormat.OGG_OPUS);
        assertSupported("song.wav", "audio/x-wav", LocalAudioFormat.WAV_PCM);
    }

    @Test
    public void containerSampleMimeOverridesProviderMime() {
        LocalAudioDecision aac = LocalAudioFormatPolicy.classify(
                "song.m4a", "audio/mp4", "audio/mp4a-latm; codecs=mp4a.40.2", false
        );
        LocalAudioDecision alac = LocalAudioFormatPolicy.classify(
                "song.m4a", "audio/mp4", "audio/alac", false
        );
        LocalAudioDecision nonPcmWav = LocalAudioFormatPolicy.classify(
                "song.wav", "audio/wav", "audio/mpeg", false
        );

        assertEquals(LocalAudioFormat.M4A_AAC, aac.format());
        assertTrue(aac.shouldImport());
        assertEquals(LocalAudioFormat.ALAC, alac.format());
        assertFalse(alac.shouldImport());
        assertFalse(nonPcmWav.shouldImport());
    }

    @Test
    public void rejectsVideoMp4AndDstCompressedDff() {
        LocalAudioDecision video = LocalAudioFormatPolicy.classify(
                "clip.mp4", "video/mp4", "audio/mp4a-latm", true
        );
        LocalAudioDecision dst = LocalAudioFormatPolicy.classify(
                "album.dff", "audio/x-dff", "audio/dst", false
        );

        assertEquals(LocalAudioSkipReason.VIDEO_CONTAINER, video.skipReason());
        assertEquals(LocalAudioSkipReason.UNSUPPORTED_FORMAT, dst.skipReason());
        assertFalse(video.shouldImport());
        assertFalse(dst.shouldImport());
    }

    @Test
    public void knownUnsupportedExtensionWinsOverForgedAudioMime() {
        String[] names = {
                "song.alac", "song.wma", "song.ape", "song.wv", "song.tta",
                "song.aiff", "song.amr", "song.mid"
        };
        for (String name : names) {
            LocalAudioDecision decision = LocalAudioFormatPolicy.classify(name, "audio/mpeg");
            assertFalse(name, decision.shouldImport());
            assertEquals(name, LocalAudioSupport.UNSUPPORTED, decision.support());
        }
    }

    @Test
    public void encryptedUnknownAndNonAudioCandidatesAreSeparated() {
        LocalAudioDecision encrypted = LocalAudioFormatPolicy.classify("cache.MFLAC", "audio/flac");
        LocalAudioDecision unknown = LocalAudioFormatPolicy.classify("song.xyz", "audio/mpeg");
        LocalAudioDecision noSuffix = LocalAudioFormatPolicy.classify("recording", "audio/mpeg");
        LocalAudioDecision image = LocalAudioFormatPolicy.classify("cover.jpg", "image/jpeg");

        assertEquals(LocalAudioSkipReason.ENCRYPTED_CACHE, encrypted.skipReason());
        assertEquals(LocalAudioSkipReason.UNKNOWN_AUDIO, unknown.skipReason());
        assertTrue(noSuffix.shouldImport());
        assertEquals(LocalAudioSupport.NOT_AUDIO, image.support());
    }

    @Test
    public void dsfAndUncompressedDffRemainUsbConditional() {
        assertEquals(
                LocalAudioSupport.USB_ONLY,
                LocalAudioFormatPolicy.classify("album.dsf", "audio/x-dsf").support()
        );
        assertEquals(
                LocalAudioSupport.USB_ONLY,
                LocalAudioFormatPolicy.classify("album.dff", "audio/x-dff").support()
        );
    }

    @Test
    public void legacyUnsupportedPathWinsOverIncorrectPersistedCodec() {
        Track track = new Track(
                9L,
                "Legacy",
                "Artist",
                "Album",
                1_000L,
                null,
                "/music/legacy.wma",
                0L,
                null,
                "aac",
                0,
                0,
                0,
                0
        );

        assertFalse(LocalAudioFormatPolicy.isPlaybackAllowed(track));
    }

    @Test
    public void importSummaryIgnoresNonAudioAndAggregatesSkippedFormats() {
        LocalAudioImportSummary summary = new LocalAudioImportSummary.Builder()
                .record(LocalAudioFormatPolicy.classify("one.mp3", "audio/mpeg"))
                .record(LocalAudioFormatPolicy.classify("cover.jpg", "image/jpeg"))
                .record(LocalAudioFormatPolicy.classify("one.wma", "audio/mpeg"))
                .record(LocalAudioFormatPolicy.classify("two.wma", "audio/x-ms-wma"))
                .record(LocalAudioFormatPolicy.classify("three.ape", "audio/ape"))
                .build();

        assertEquals(1, summary.importedCount());
        assertEquals(3, summary.skippedCount());
        assertEquals(Integer.valueOf(2), summary.skippedFormatCounts().get("WMA"));
        assertEquals(Integer.valueOf(1), summary.skippedFormatCounts().get("APE"));
    }

    private static void assertSupported(
            String name,
            String mime,
            LocalAudioFormat expectedFormat
    ) {
        LocalAudioDecision decision = LocalAudioFormatPolicy.classify(name, mime);
        assertEquals(expectedFormat, decision.format());
        assertTrue(decision.shouldImport());
    }
}
