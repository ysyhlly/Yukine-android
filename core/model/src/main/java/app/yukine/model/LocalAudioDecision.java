package app.yukine.model;

import java.util.Objects;

public final class LocalAudioDecision {
    private final LocalAudioFormat format;
    private final LocalAudioSupport support;
    private final LocalAudioSkipReason skipReason;
    private final String sampleMimeType;

    public LocalAudioDecision(
            LocalAudioFormat format,
            LocalAudioSupport support,
            LocalAudioSkipReason skipReason,
            String sampleMimeType
    ) {
        this.format = Objects.requireNonNull(format, "format");
        this.support = Objects.requireNonNull(support, "support");
        this.skipReason = skipReason;
        this.sampleMimeType = sampleMimeType == null ? "" : sampleMimeType;
    }

    public LocalAudioFormat format() {
        return format;
    }

    public LocalAudioSupport support() {
        return support;
    }

    public LocalAudioSkipReason skipReason() {
        return skipReason;
    }

    public String sampleMimeType() {
        return sampleMimeType;
    }

    public boolean shouldImport() {
        return support == LocalAudioSupport.SUPPORTED || support == LocalAudioSupport.USB_ONLY;
    }

    public boolean isPlaybackAllowed() {
        return shouldImport();
    }

    public boolean isSkippedAudio() {
        return support == LocalAudioSupport.UNSUPPORTED;
    }
}
