package app.yukine.model;

/** Canonical local-audio formats understood by the library and playback policy. */
public enum LocalAudioFormat {
    MP3("MP3", "mp3"),
    AAC_ADTS("AAC", "aac"),
    M4A_AAC("M4A/AAC", "aac"),
    FLAC("FLAC", "flac"),
    WAV_PCM("WAV/PCM", "pcm"),
    OGG_VORBIS("Ogg/Vorbis", "vorbis"),
    OGG_OPUS("Ogg/Opus", "opus"),
    DSF("DSF", "dsd"),
    DFF("DFF", "dsd"),
    ALAC("ALAC", "alac"),
    WMA("WMA", "wma"),
    APE("APE", "ape"),
    WAVPACK("WavPack", "wavpack"),
    TTA("TTA", "tta"),
    AIFF("AIFF", "aiff"),
    AMR("AMR", "amr"),
    MIDI("MIDI", "midi"),
    ENCRYPTED_CACHE("加密缓存", ""),
    UNKNOWN_AUDIO("未知音频", ""),
    OTHER_SOURCE("其他音源", "");

    private final String displayName;
    private final String canonicalCodec;

    LocalAudioFormat(String displayName, String canonicalCodec) {
        this.displayName = displayName;
        this.canonicalCodec = canonicalCodec;
    }

    public String displayName() {
        return displayName;
    }

    public String canonicalCodec() {
        return canonicalCodec;
    }
}
