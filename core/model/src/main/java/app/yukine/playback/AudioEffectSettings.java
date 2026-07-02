package app.yukine.playback;

import java.util.Arrays;

public final class AudioEffectSettings {
    public static final int PRESET_CUSTOM = -1;
    public static final AudioEffectSettings DEFAULT =
            new AudioEffectSettings(false, PRESET_CUSTOM, new short[0], (short) 0, (short) 0, 0);

    public final boolean enabled;
    public final int preset;
    public final short[] bandLevels;
    public final short bassBoostStrength;
    public final short virtualizerStrength;
    public final int loudnessGainMb;

    public AudioEffectSettings(
            boolean enabled,
            int preset,
            short[] bandLevels,
            short bassBoostStrength,
            short virtualizerStrength,
            int loudnessGainMb
    ) {
        this.enabled = enabled;
        this.preset = preset;
        this.bandLevels = bandLevels == null ? new short[0] : Arrays.copyOf(bandLevels, bandLevels.length);
        this.bassBoostStrength = clampStrength(bassBoostStrength);
        this.virtualizerStrength = clampStrength(virtualizerStrength);
        this.loudnessGainMb = clampLoudness(loudnessGainMb);
    }

    public AudioEffectSettings withEnabled(boolean enabled) {
        return new AudioEffectSettings(enabled, preset, bandLevels, bassBoostStrength, virtualizerStrength, loudnessGainMb);
    }

    public AudioEffectSettings withPreset(int preset) {
        return new AudioEffectSettings(enabled, preset, new short[0], bassBoostStrength, virtualizerStrength, loudnessGainMb);
    }

    public AudioEffectSettings withBandLevel(int band, short level) {
        int size = Math.max(band + 1, bandLevels.length);
        short[] next = Arrays.copyOf(bandLevels, size);
        next[band] = level;
        return new AudioEffectSettings(enabled, PRESET_CUSTOM, next, bassBoostStrength, virtualizerStrength, loudnessGainMb);
    }

    public AudioEffectSettings withBassBoostStrength(short strength) {
        return new AudioEffectSettings(enabled, preset, bandLevels, strength, virtualizerStrength, loudnessGainMb);
    }

    public AudioEffectSettings withVirtualizerStrength(short strength) {
        return new AudioEffectSettings(enabled, preset, bandLevels, bassBoostStrength, strength, loudnessGainMb);
    }

    public AudioEffectSettings withLoudnessGainMb(int gainMb) {
        return new AudioEffectSettings(enabled, preset, bandLevels, bassBoostStrength, virtualizerStrength, gainMb);
    }

    public String encode() {
        StringBuilder bands = new StringBuilder();
        for (int i = 0; i < bandLevels.length; i++) {
            if (i > 0) {
                bands.append(',');
            }
            bands.append(bandLevels[i]);
        }
        return "enabled=" + enabled
                + ";preset=" + preset
                + ";bands=" + bands
                + ";bass=" + bassBoostStrength
                + ";virtualizer=" + virtualizerStrength
                + ";loudness=" + loudnessGainMb;
    }

    public static AudioEffectSettings decode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }
        boolean enabled = false;
        int preset = PRESET_CUSTOM;
        short[] bands = new short[0];
        short bass = 0;
        short virtualizer = 0;
        int loudness = 0;
        String[] entries = value.split(";");
        for (String entry : entries) {
            int sep = entry.indexOf('=');
            if (sep <= 0) {
                continue;
            }
            String key = entry.substring(0, sep);
            String raw = entry.substring(sep + 1);
            try {
                if ("enabled".equals(key)) {
                    enabled = Boolean.parseBoolean(raw);
                } else if ("preset".equals(key)) {
                    preset = Integer.parseInt(raw);
                } else if ("bands".equals(key)) {
                    bands = parseBands(raw);
                } else if ("bass".equals(key)) {
                    bass = clampStrength(Short.parseShort(raw));
                } else if ("virtualizer".equals(key)) {
                    virtualizer = clampStrength(Short.parseShort(raw));
                } else if ("loudness".equals(key)) {
                    loudness = clampLoudness(Integer.parseInt(raw));
                }
            } catch (NumberFormatException ignored) {
                // Keep defaults for malformed values.
            }
        }
        return new AudioEffectSettings(enabled, preset, bands, bass, virtualizer, loudness);
    }

    private static short[] parseBands(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new short[0];
        }
        String[] parts = raw.split(",");
        short[] levels = new short[parts.length];
        for (int i = 0; i < parts.length; i++) {
            levels[i] = Short.parseShort(parts[i]);
        }
        return levels;
    }

    private static short clampStrength(short value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1000) {
            return 1000;
        }
        return value;
    }

    private static int clampLoudness(int value) {
        if (value < -1200) {
            return -1200;
        }
        if (value > 1200) {
            return 1200;
        }
        return value;
    }
}
