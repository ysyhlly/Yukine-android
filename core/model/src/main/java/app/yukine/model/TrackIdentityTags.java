package app.yukine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Immutable offline identity evidence read from embedded audio tags. */
public final class TrackIdentityTags {
    public static final TrackIdentityTags EMPTY = new TrackIdentityTags("", "", "", "", List.of());

    public final String recordingMusicBrainzId;
    public final String workMusicBrainzId;
    public final String isrc;
    public final String acoustId;
    public final List<String> artistMusicBrainzIds;

    public TrackIdentityTags(
            String recordingMusicBrainzId,
            String workMusicBrainzId,
            String isrc,
            String acoustId,
            List<String> artistMusicBrainzIds
    ) {
        this.recordingMusicBrainzId = uuid(recordingMusicBrainzId);
        this.workMusicBrainzId = uuid(workMusicBrainzId);
        this.isrc = normalizeIsrc(isrc);
        this.acoustId = uuid(acoustId);
        LinkedHashSet<String> artistIds = new LinkedHashSet<>();
        if (artistMusicBrainzIds != null) {
            for (String value : artistMusicBrainzIds) {
                String normalized = uuid(value);
                if (!normalized.isEmpty()) {
                    artistIds.add(normalized);
                }
            }
        }
        this.artistMusicBrainzIds = Collections.unmodifiableList(new ArrayList<>(artistIds));
    }

    public boolean isEmpty() {
        return recordingMusicBrainzId.isEmpty()
                && workMusicBrainzId.isEmpty()
                && isrc.isEmpty()
                && acoustId.isEmpty()
                && artistMusicBrainzIds.isEmpty();
    }

    private static String uuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        try {
            return UUID.fromString(value.trim()).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String normalizeIsrc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(12);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toUpperCase(character));
            }
        }
        return normalized.length() == 12 ? normalized.toString() : "";
    }
}
