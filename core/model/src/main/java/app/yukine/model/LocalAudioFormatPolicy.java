package app.yukine.model;

import java.util.Locale;
import java.util.Set;

/** Pure format policy shared by ingest, playback preflight and UI presentation. */
public final class LocalAudioFormatPolicy {
    private static final Set<String> ENCRYPTED_EXTENSIONS = Set.of(
            "kgm", "vpr", "ofl", "qmc", "mflac", "mgg", "kgc", "krc"
    );
    private static final Set<String> RECOGNIZED_AUDIO_EXTENSIONS = Set.of(
            "mp3", "aac", "m4a", "mp4", "flac", "wav", "ogg", "oga", "opus",
            "dsf", "dff", "alac", "wma", "ape", "wv", "tta", "aif", "aiff",
            "amr", "mid", "midi"
    );
    private static final Set<String> CONTAINER_PROBE_EXTENSIONS = Set.of(
            "m4a", "mp4", "wav", "ogg", "oga", "opus"
    );

    private LocalAudioFormatPolicy() {
    }

    public static LocalAudioDecision classify(String displayName, String mimeType) {
        return classify(displayName, mimeType, "", false);
    }

    public static LocalAudioDecision classify(
            String displayName,
            String mimeType,
            String sampleMimeType,
            boolean hasVideoTrack
    ) {
        String extension = extension(displayName);
        String providerMime = normalizeMime(mimeType);
        String sampleMime = normalizeMime(sampleMimeType);

        LocalAudioDecision fixedExtensionDecision = fixedExtensionDecision(extension);
        if (fixedExtensionDecision != null && !fixedExtensionDecision.shouldImport()) {
            return fixedExtensionDecision;
        }
        if (fixedExtensionDecision != null
                && fixedExtensionDecision.support() == LocalAudioSupport.USB_ONLY) {
            if ("audio/dst".equals(sampleMime) || "audio/x-dst".equals(sampleMime)) {
                return unsupported(
                        LocalAudioFormat.DFF,
                        LocalAudioSkipReason.UNSUPPORTED_FORMAT,
                        sampleMime
                );
            }
            return fixedExtensionDecision;
        }
        if (hasVideoTrack && ("mp4".equals(extension) || "m4a".equals(extension))) {
            return unsupported(
                    formatForExtension(extension),
                    LocalAudioSkipReason.VIDEO_CONTAINER,
                    sampleMime
            );
        }

        if (!sampleMime.isEmpty()) {
            if (!sampleMimeMatchesContainer(extension, sampleMime)) {
                return unsupported(
                        formatForExtension(extension),
                        LocalAudioSkipReason.UNSUPPORTED_FORMAT,
                        sampleMime
                );
            }
            return decisionForSampleMime(extension, sampleMime);
        }
        if (fixedExtensionDecision != null) {
            return fixedExtensionDecision;
        }
        if (!extension.isEmpty() && !RECOGNIZED_AUDIO_EXTENSIONS.contains(extension)) {
            if (providerMime.startsWith("audio/")) {
                return unsupported(
                        LocalAudioFormat.UNKNOWN_AUDIO,
                        LocalAudioSkipReason.UNKNOWN_AUDIO,
                        providerMime
                );
            }
            return new LocalAudioDecision(
                    LocalAudioFormat.UNKNOWN_AUDIO,
                    LocalAudioSupport.NOT_AUDIO,
                    null,
                    ""
            );
        }
        LocalAudioDecision mimeDecision = decisionForProviderMime(providerMime);
        if (mimeDecision != null) {
            return mimeDecision;
        }
        if (providerMime.startsWith("audio/")) {
            return unsupported(
                    LocalAudioFormat.UNKNOWN_AUDIO,
                    LocalAudioSkipReason.UNKNOWN_AUDIO,
                    providerMime
            );
        }
        return new LocalAudioDecision(
                LocalAudioFormat.UNKNOWN_AUDIO,
                LocalAudioSupport.NOT_AUDIO,
                null,
                ""
        );
    }

    public static LocalAudioDecision classifyTrack(Track track) {
        if (track == null) {
            return unsupported(
                    LocalAudioFormat.UNKNOWN_AUDIO,
                    LocalAudioSkipReason.UNKNOWN_AUDIO,
                    ""
            );
        }
        if (isNonLocalSource(track)) {
            return supported(LocalAudioFormat.OTHER_SOURCE, "");
        }
        String source = firstText(track.dataPath, track.contentUri == null ? "" : track.contentUri.toString());
        LocalAudioDecision extensionDecision = fixedExtensionDecision(extension(source));
        if (extensionDecision != null && !extensionDecision.shouldImport()) {
            return extensionDecision;
        }
        String codec = normalizeCodec(track.codec);
        if (!codec.isEmpty()) {
            LocalAudioDecision codecDecision = decisionForCodec(codec, extension(source));
            if (codecDecision != null) {
                return codecDecision;
            }
        }
        if (extensionDecision != null) {
            return extensionDecision;
        }
        // Old opaque document URIs may not expose an extension until the audio-spec backfill runs.
        return supported(LocalAudioFormat.UNKNOWN_AUDIO, "");
    }

    public static boolean isPlaybackAllowed(Track track) {
        return classifyTrack(track).isPlaybackAllowed();
    }

    public static boolean isRecognizedAudioExtension(String value) {
        return RECOGNIZED_AUDIO_EXTENSIONS.contains(extension(value));
    }

    public static boolean isEncryptedExtension(String value) {
        return ENCRYPTED_EXTENSIONS.contains(extension(value));
    }

    public static boolean requiresContainerProbe(String value) {
        return CONTAINER_PROBE_EXTENSIONS.contains(extension(value));
    }

    public static String extension(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot + 1);
    }

    public static String normalizeMime(String value) {
        if (value == null) {
            return "";
        }
        return substringBefore(value, ";").trim().toLowerCase(Locale.ROOT);
    }

    private static LocalAudioDecision fixedExtensionDecision(String extension) {
        switch (extension) {
            case "mp3":
                return supported(LocalAudioFormat.MP3, "audio/mpeg");
            case "aac":
                return supported(LocalAudioFormat.AAC_ADTS, "audio/aac");
            case "m4a":
            case "mp4":
                return supported(LocalAudioFormat.M4A_AAC, "audio/mp4a-latm");
            case "flac":
                return supported(LocalAudioFormat.FLAC, "audio/flac");
            case "wav":
                return supported(LocalAudioFormat.WAV_PCM, "audio/raw");
            case "ogg":
            case "oga":
                return supported(LocalAudioFormat.OGG_VORBIS, "audio/vorbis");
            case "opus":
                return supported(LocalAudioFormat.OGG_OPUS, "audio/opus");
            case "dsf":
                return usbOnly(LocalAudioFormat.DSF);
            case "dff":
                return usbOnly(LocalAudioFormat.DFF);
            case "alac":
                return unsupported(LocalAudioFormat.ALAC, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "wma":
                return unsupported(LocalAudioFormat.WMA, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "ape":
                return unsupported(LocalAudioFormat.APE, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "wv":
                return unsupported(LocalAudioFormat.WAVPACK, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "tta":
                return unsupported(LocalAudioFormat.TTA, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "aif":
            case "aiff":
                return unsupported(LocalAudioFormat.AIFF, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "amr":
                return unsupported(LocalAudioFormat.AMR, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "mid":
            case "midi":
                return unsupported(LocalAudioFormat.MIDI, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            default:
                if (ENCRYPTED_EXTENSIONS.contains(extension)) {
                    return unsupported(
                            LocalAudioFormat.ENCRYPTED_CACHE,
                            LocalAudioSkipReason.ENCRYPTED_CACHE,
                            ""
                    );
                }
                return null;
        }
    }

    private static LocalAudioDecision decisionForSampleMime(String extension, String sampleMime) {
        switch (sampleMime) {
            case "audio/mpeg":
                return supported(LocalAudioFormat.MP3, sampleMime);
            case "audio/aac":
            case "audio/aac-adts":
                return supported(LocalAudioFormat.AAC_ADTS, sampleMime);
            case "audio/mp4a-latm":
                return supported(
                        "m4a".equals(extension) || "mp4".equals(extension)
                                ? LocalAudioFormat.M4A_AAC
                                : LocalAudioFormat.AAC_ADTS,
                        sampleMime
                );
            case "audio/flac":
                return supported(LocalAudioFormat.FLAC, sampleMime);
            case "audio/vorbis":
                return supported(LocalAudioFormat.OGG_VORBIS, sampleMime);
            case "audio/opus":
                return supported(LocalAudioFormat.OGG_OPUS, sampleMime);
            case "audio/raw":
            case "audio/pcm":
                return "wav".equals(extension)
                        ? supported(LocalAudioFormat.WAV_PCM, sampleMime)
                        : unsupported(
                                LocalAudioFormat.UNKNOWN_AUDIO,
                                LocalAudioSkipReason.UNSUPPORTED_FORMAT,
                                sampleMime
                        );
            case "audio/alac":
                return unsupported(LocalAudioFormat.ALAC, LocalAudioSkipReason.UNSUPPORTED_FORMAT, sampleMime);
            case "audio/x-dsd":
                return "dff".equals(extension)
                        ? usbOnly(LocalAudioFormat.DFF)
                        : usbOnly(LocalAudioFormat.DSF);
            default:
                return unsupported(
                        formatForExtension(extension),
                        LocalAudioSkipReason.UNSUPPORTED_FORMAT,
                        sampleMime
                );
        }
    }

    private static boolean sampleMimeMatchesContainer(String extension, String sampleMime) {
        switch (extension) {
            case "mp3":
                return "audio/mpeg".equals(sampleMime);
            case "aac":
                return "audio/aac".equals(sampleMime)
                        || "audio/aac-adts".equals(sampleMime)
                        || "audio/mp4a-latm".equals(sampleMime);
            case "m4a":
            case "mp4":
                return "audio/mp4a-latm".equals(sampleMime)
                        || "audio/alac".equals(sampleMime);
            case "flac":
                return "audio/flac".equals(sampleMime);
            case "wav":
                return "audio/raw".equals(sampleMime) || "audio/pcm".equals(sampleMime);
            case "ogg":
            case "oga":
                return "audio/vorbis".equals(sampleMime) || "audio/opus".equals(sampleMime);
            case "opus":
                return "audio/opus".equals(sampleMime);
            default:
                return true;
        }
    }

    private static LocalAudioDecision decisionForProviderMime(String mime) {
        switch (mime) {
            case "audio/mpeg":
                return supported(LocalAudioFormat.MP3, mime);
            case "audio/aac":
            case "audio/aac-adts":
                return supported(LocalAudioFormat.AAC_ADTS, mime);
            case "audio/mp4":
            case "audio/m4a":
                return supported(LocalAudioFormat.M4A_AAC, "audio/mp4a-latm");
            case "audio/flac":
                return supported(LocalAudioFormat.FLAC, mime);
            case "audio/wav":
            case "audio/x-wav":
            case "audio/vnd.wave":
                return supported(LocalAudioFormat.WAV_PCM, "audio/raw");
            case "audio/ogg":
                return supported(LocalAudioFormat.OGG_VORBIS, "audio/vorbis");
            case "audio/opus":
                return supported(LocalAudioFormat.OGG_OPUS, mime);
            default:
                return null;
        }
    }

    private static LocalAudioDecision decisionForCodec(String codec, String extension) {
        switch (codec) {
            case "mp3":
            case "mpeg":
                return supported(LocalAudioFormat.MP3, "audio/mpeg");
            case "aac":
            case "mp4a-latm":
                return supported(
                        "m4a".equals(extension) || "mp4".equals(extension)
                                ? LocalAudioFormat.M4A_AAC
                                : LocalAudioFormat.AAC_ADTS,
                        "audio/mp4a-latm"
                );
            case "flac":
                return supported(LocalAudioFormat.FLAC, "audio/flac");
            case "pcm":
            case "raw":
            case "wav":
                return supported(LocalAudioFormat.WAV_PCM, "audio/raw");
            case "vorbis":
            case "ogg":
                return supported(LocalAudioFormat.OGG_VORBIS, "audio/vorbis");
            case "opus":
                return supported(LocalAudioFormat.OGG_OPUS, "audio/opus");
            case "dsd":
            case "x-dsd":
                return "dff".equals(extension)
                        ? usbOnly(LocalAudioFormat.DFF)
                        : usbOnly(LocalAudioFormat.DSF);
            case "dst":
            case "x-dst":
                return unsupported(LocalAudioFormat.DFF, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "audio/dst");
            case "alac":
                return unsupported(LocalAudioFormat.ALAC, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "wma":
                return unsupported(LocalAudioFormat.WMA, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "ape":
                return unsupported(LocalAudioFormat.APE, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "wavpack":
            case "wv":
                return unsupported(LocalAudioFormat.WAVPACK, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "tta":
                return unsupported(LocalAudioFormat.TTA, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "aif":
            case "aiff":
                return unsupported(LocalAudioFormat.AIFF, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "amr":
                return unsupported(LocalAudioFormat.AMR, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            case "mid":
            case "midi":
                return unsupported(LocalAudioFormat.MIDI, LocalAudioSkipReason.UNSUPPORTED_FORMAT, "");
            default:
                return null;
        }
    }

    private static LocalAudioFormat formatForExtension(String extension) {
        LocalAudioDecision decision = fixedExtensionDecision(extension);
        return decision == null ? LocalAudioFormat.UNKNOWN_AUDIO : decision.format();
    }

    private static LocalAudioDecision supported(LocalAudioFormat format, String sampleMime) {
        return new LocalAudioDecision(format, LocalAudioSupport.SUPPORTED, null, sampleMime);
    }

    private static LocalAudioDecision usbOnly(LocalAudioFormat format) {
        return new LocalAudioDecision(format, LocalAudioSupport.USB_ONLY, null, "audio/x-dsd");
    }

    private static LocalAudioDecision unsupported(
            LocalAudioFormat format,
            LocalAudioSkipReason reason,
            String sampleMime
    ) {
        return new LocalAudioDecision(format, LocalAudioSupport.UNSUPPORTED, reason, sampleMime);
    }

    private static boolean isNonLocalSource(Track track) {
        String path = track.dataPath == null ? "" : track.dataPath.toLowerCase(Locale.ROOT);
        if (path.startsWith("stream:")
                || path.startsWith("streaming:")
                || path.startsWith("webdav:")) {
            return true;
        }
        String scheme = track.contentUri == null ? "" : track.contentUri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String normalizeCodec(String value) {
        String normalized = normalizeMime(value);
        return normalized.startsWith("audio/") ? normalized.substring("audio/".length()) : normalized;
    }

    private static String firstText(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private static String substringBefore(String value, String delimiter) {
        int index = value.indexOf(delimiter);
        return index < 0 ? value : value.substring(0, index);
    }
}
