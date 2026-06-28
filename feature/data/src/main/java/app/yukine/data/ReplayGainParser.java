package app.yukine.data;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReplayGainParser {
    private static final int MAX_SCAN_BYTES = 1024 * 1024;
    private static final Pattern TRACK_GAIN_PATTERN = Pattern.compile(
            "(?i)REPLAYGAIN_TRACK_GAIN[^-+0-9]{0,24}([-+]?\\d+(?:\\.\\d+)?)\\s*dB"
    );
    private static final Pattern ALBUM_GAIN_PATTERN = Pattern.compile(
            "(?i)REPLAYGAIN_ALBUM_GAIN[^-+0-9]{0,24}([-+]?\\d+(?:\\.\\d+)?)\\s*dB"
    );

    private final Context context;

    ReplayGainParser(Context context) {
        this.context = context.getApplicationContext();
    }

    ReplayGain read(Uri uri) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            return ReplayGain.NONE;
        }
        byte[] bytes = readPrefix(uri);
        if (bytes.length == 0) {
            return ReplayGain.NONE;
        }
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        float trackGain = gainFrom(TRACK_GAIN_PATTERN, text);
        float albumGain = gainFrom(ALBUM_GAIN_PATTERN, text);
        if (!hasGain(trackGain) && !hasGain(albumGain)) {
            String utf8Text = new String(bytes, StandardCharsets.UTF_8);
            trackGain = gainFrom(TRACK_GAIN_PATTERN, utf8Text);
            albumGain = gainFrom(ALBUM_GAIN_PATTERN, utf8Text);
        }
        return new ReplayGain(trackGain, albumGain);
    }

    private byte[] readPrefix(Uri uri) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = MAX_SCAN_BYTES;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return new byte[0];
            }
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException | RuntimeException ignored) {
            return new byte[0];
        }
        return output.toByteArray();
    }

    private float gainFrom(Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return 0.0f;
        }
        try {
            return clamp(Float.parseFloat(matcher.group(1).toLowerCase(Locale.ROOT)));
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }

    private float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        if (value < -30.0f) {
            return -30.0f;
        }
        if (value > 30.0f) {
            return 30.0f;
        }
        return value;
    }

    static boolean hasGain(float value) {
        return Math.abs(value) > 0.001f;
    }

    static final class ReplayGain {
        static final ReplayGain NONE = new ReplayGain(0.0f, 0.0f);

        final float trackDb;
        final float albumDb;

        ReplayGain(float trackDb, float albumDb) {
            this.trackDb = trackDb;
            this.albumDb = albumDb;
        }

        boolean hasValue() {
            return hasGain(trackDb) || hasGain(albumDb);
        }
    }
}
