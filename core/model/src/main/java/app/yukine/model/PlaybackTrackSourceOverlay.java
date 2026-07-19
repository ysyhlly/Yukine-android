package app.yukine.model;

/**
 * Applies a resolved transport source without replacing the logical song identity.
 *
 * <p>Playback URLs and provider paths are source state. Queue, favorite, playlist and history
 * references must continue to use the original logical track ID until those callers operate on
 * recording IDs directly.</p>
 */
public final class PlaybackTrackSourceOverlay {
    private PlaybackTrackSourceOverlay() {
    }

    public static Track merge(Track logical, Track source) {
        if (logical == null) {
            return source;
        }
        if (source == null) {
            return logical;
        }
        return new Track(
                logical.id,
                logical.title,
                logical.artist,
                logical.album,
                logical.durationMs > 0L ? logical.durationMs : source.durationMs,
                source.contentUri,
                source.dataPath,
                logical.albumId,
                logical.albumArtUri != null ? logical.albumArtUri : source.albumArtUri,
                firstNonBlank(source.codec, logical.codec),
                source.bitrateKbps > 0 ? source.bitrateKbps : logical.bitrateKbps,
                source.sampleRateHz > 0 ? source.sampleRateHz : logical.sampleRateHz,
                source.bitsPerSample > 0 ? source.bitsPerSample : logical.bitsPerSample,
                source.channelCount > 0 ? source.channelCount : logical.channelCount,
                logical.replayGainTrackDb,
                logical.replayGainAlbumDb,
                logical.identityTags,
                firstNonBlank(source.albumArtist, logical.albumArtist),
                firstNonBlank(source.composer, logical.composer),
                firstNonBlank(source.releaseType, logical.releaseType),
                source.year > 0 ? source.year : logical.year
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
    }
}
