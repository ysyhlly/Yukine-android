package app.yukine.data;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import app.yukine.model.LocalAudioDecision;
import app.yukine.model.LocalAudioFormat;
import app.yukine.model.LocalAudioFormatPolicy;
import app.yukine.model.LocalAudioSkipReason;
import app.yukine.model.LocalAudioSupport;

/**
 * Android capability boundary for the pure local-audio format policy.
 *
 * <p>SAF imports use strict probing. MediaStore scans only open ambiguous containers so a large
 * device library does not pay extractor cost for every MP3/FLAC file.</p>
 */
final class LocalAudioCandidateProbe {
    interface ContainerReader {
        ContainerInfo read(Uri uri) throws IOException;
    }

    interface DecoderSupport {
        boolean hasDecoder(String sampleMimeType);
    }

    static final class ContainerInfo {
        final String sampleMimeType;
        final boolean hasVideoTrack;

        ContainerInfo(String sampleMimeType, boolean hasVideoTrack) {
            this.sampleMimeType = sampleMimeType == null ? "" : sampleMimeType;
            this.hasVideoTrack = hasVideoTrack;
        }
    }

    private final ContainerReader containerReader;
    private final DecoderSupport decoderSupport;

    LocalAudioCandidateProbe(Context context) {
        Context appContext = context.getApplicationContext();
        this.containerReader = uri -> readContainer(appContext, uri);
        this.decoderSupport = new PlatformDecoderSupport();
    }

    LocalAudioCandidateProbe(ContainerReader containerReader, DecoderSupport decoderSupport) {
        this.containerReader = containerReader;
        this.decoderSupport = decoderSupport;
    }

    LocalAudioDecision probe(Uri uri, String displayName, String providerMimeType) {
        return probe(uri, displayName, providerMimeType, true);
    }

    LocalAudioDecision probeForMediaStore(Uri uri, String displayName, String providerMimeType) {
        return probe(
                uri,
                displayName,
                providerMimeType,
                LocalAudioFormatPolicy.requiresContainerProbe(displayName)
        );
    }

    private LocalAudioDecision probe(
            Uri uri,
            String displayName,
            String providerMimeType,
            boolean inspectContainer
    ) {
        LocalAudioDecision initial = LocalAudioFormatPolicy.classify(displayName, providerMimeType);
        if (!initial.shouldImport() || initial.support() == LocalAudioSupport.USB_ONLY) {
            return initial;
        }

        LocalAudioDecision resolved = initial;
        if (inspectContainer) {
            if (uri == null) {
                return unreadable(initial.format());
            }
            try {
                ContainerInfo container = containerReader.read(uri);
                if (container.sampleMimeType.isEmpty()) {
                    return container.hasVideoTrack
                            ? unsupported(initial.format(), LocalAudioSkipReason.VIDEO_CONTAINER, "")
                            : unreadable(initial.format());
                }
                resolved = LocalAudioFormatPolicy.classify(
                        displayName,
                        providerMimeType,
                        container.sampleMimeType,
                        container.hasVideoTrack
                );
            } catch (IOException | RuntimeException ignored) {
                return unreadable(initial.format());
            }
        }

        if (!resolved.shouldImport() || resolved.support() == LocalAudioSupport.USB_ONLY) {
            return resolved;
        }
        String sampleMime = resolved.sampleMimeType();
        if (!requiresDecoder(sampleMime) || decoderSupport.hasDecoder(sampleMime)) {
            return resolved;
        }
        return unsupported(
                resolved.format(),
                LocalAudioSkipReason.DEVICE_DECODER_MISSING,
                sampleMime
        );
    }

    private static ContainerInfo readContainer(Context context, Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            String audioMime = "";
            boolean hasVideo = false;
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                String normalized = LocalAudioFormatPolicy.normalizeMime(mime);
                if (audioMime.isEmpty() && normalized.startsWith("audio/")) {
                    audioMime = normalized;
                } else if (normalized.startsWith("video/")) {
                    hasVideo = true;
                }
            }
            return new ContainerInfo(audioMime, hasVideo);
        } finally {
            extractor.release();
        }
    }

    private static boolean requiresDecoder(String sampleMime) {
        String normalized = LocalAudioFormatPolicy.normalizeMime(sampleMime);
        return !normalized.isEmpty()
                && !"audio/raw".equals(normalized)
                && !"audio/pcm".equals(normalized)
                && !"audio/x-dsd".equals(normalized);
    }

    private static LocalAudioDecision unreadable(LocalAudioFormat format) {
        return unsupported(format, LocalAudioSkipReason.UNREADABLE, "");
    }

    private static LocalAudioDecision unsupported(
            LocalAudioFormat format,
            LocalAudioSkipReason reason,
            String sampleMime
    ) {
        return new LocalAudioDecision(format, LocalAudioSupport.UNSUPPORTED, reason, sampleMime);
    }

    private static final class PlatformDecoderSupport implements DecoderSupport {
        private final Map<String, Boolean> cache = new HashMap<>();

        @Override
        public synchronized boolean hasDecoder(String sampleMimeType) {
            String mime = LocalAudioFormatPolicy.normalizeMime(sampleMimeType);
            Boolean cached = cache.get(mime);
            if (cached != null) {
                return cached;
            }
            boolean supported = queryDecoder(mime);
            cache.put(mime, supported);
            return supported;
        }

        private static boolean queryDecoder(String mime) {
            try {
                MediaCodecInfo[] codecInfos =
                        new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
                for (MediaCodecInfo codecInfo : codecInfos) {
                    if (codecInfo.isEncoder()) {
                        continue;
                    }
                    for (String type : codecInfo.getSupportedTypes()) {
                        if (mime.equals(type.toLowerCase(Locale.ROOT))) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (RuntimeException ignored) {
                // A broken vendor codec registry must not hide otherwise standard audio.
                return true;
            }
        }
    }
}
